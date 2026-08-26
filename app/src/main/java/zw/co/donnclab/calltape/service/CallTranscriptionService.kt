package zw.co.donnclab.calltape.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import zw.co.donnclab.calltape.MainActivity
import zw.co.donnclab.calltape.data.CallRecord
import zw.co.donnclab.calltape.data.CallRepository
import zw.co.donnclab.calltape.hardware.IPosPrinter
import zw.co.donnclab.calltape.hardware.LoggerPrinterImplI
import zw.co.donnclab.calltape.telecom.CallStateManager
import zw.co.donnclab.calltape.utils.ReceiptFormatter
import java.io.IOException
import kotlin.math.sqrt

class CallTranscriptionService : Service(), RecognitionListener {

    private lateinit var telephonyManager: TelephonyManager
    private var audioRecord: AudioRecord? = null
    private var recognitionJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val printer: IPosPrinter = LoggerPrinterImplI

    private var callStartTime: Long = 0
    private var transcriptBuffer = StringBuilder()
    private var caller1Vector: DoubleArray? = null

    companion object {
        private const val TAG = "CallTranscriptionService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "call_transcription_channel"
        private const val SAMPLE_RATE = 16000
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "CallTranscriptionService: onCreate")
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        createNotificationChannel()
        startForegroundService()
        registerCallStateListener()
        startStatePoller()
        CallStateManager.statusMessage.value = "Service Monitoring"
    }

    private fun startStatePoller() {
        serviceScope.launch {
            while (isActive) {
                val currentState = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        telephonyManager.callState
                    } else {
                        @Suppress("DEPRECATION")
                        telephonyManager.callState
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Poller failed to get call state")
                    -1
                }

                if (currentState != -1 && currentState != CallStateManager.callState.value) {
                    Log.i(TAG, "Poller detected state change: ${CallStateManager.callState.value} -> $currentState")
                    withContext(Dispatchers.Main) {
                        handleCallState(currentState)
                    }
                }
                delay(500) // Poll every half second
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand received")
        val stateString = intent?.getStringExtra("state")
        val number = intent?.getStringExtra("number")
        if (stateString != null) {
            if (!number.isNullOrEmpty()) CallStateManager.activePhoneNumber.value = number
            val state = when (stateString) {
                TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
                TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
                TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
                else -> -1
            }
            if (state != -1) handleCallState(state)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopTranscription()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Call Transcription", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CallTape Active")
            .setContentText("Monitoring calls...")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun registerCallStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyManager.registerTelephonyCallback(mainExecutor, object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) { handleCallState(state) }
            })
        } else {
            @Suppress("DEPRECATION")
            telephonyManager.listen(object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    if (!phoneNumber.isNullOrEmpty()) CallStateManager.activePhoneNumber.value = phoneNumber
                    handleCallState(state)
                }
            }, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun handleCallState(state: Int) {
        Log.i(TAG, "handleCallState: $state (Previous: ${CallStateManager.callState.value})")
        CallStateManager.callState.value = state
        
        // Always try to bring UI to front on any active telephony state
        if (state == TelephonyManager.CALL_STATE_RINGING || state == TelephonyManager.CALL_STATE_OFFHOOK) {
            Log.d(TAG, "Bringing MainActivity to foreground for state $state")
            val uiIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            try {
                startActivity(uiIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MainActivity", e)
            }
        }

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                CallStateManager.statusMessage.value = "Incoming Call..."
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                CallStateManager.statusMessage.value = "Call Active - Routing Audio"
                // Delay to allow ROM to establish audio
                Handler(Looper.getMainLooper()).postDelayed({ startTranscription() }, 1500)
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                CallStateManager.statusMessage.value = "Call Ended"
                stopTranscription()
            }
            else -> {
                CallStateManager.statusMessage.value = "Call State: $state"
            }
        }
    }

    private fun startTranscription() {
        if (recognitionJob != null) return
        
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            // Force Speakerphone ON immediately
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true
            audioManager.isMicrophoneMute = false
            
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVol, AudioManager.FLAG_SHOW_UI)
            
            Log.i(TAG, "StartTranscription: Audio Mode Forced to ${audioManager.mode}, Speaker=${audioManager.isSpeakerphoneOn}")
        } catch (e: Exception) { Log.e(TAG, "Initial audio setup failed", e) }

        val model = VoskModelManager.mainModel ?: run {
            CallStateManager.statusMessage.value = "Error: Models not ready"
            return
        }

        recognitionJob = serviceScope.launch {
            val recognizer = VoskModelManager.speakerModel?.let { sm ->
                Recognizer(model, SAMPLE_RATE.toFloat(), sm)
            } ?: Recognizer(model, SAMPLE_RATE.toFloat())

            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            
            val sources = listOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.CAMCORDER,
                MediaRecorder.AudioSource.MIC
            )

            for (source in sources) {
                Log.i(TAG, "Attempting AudioRecord with source: $source")
                audioRecord = try {
                    AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
                } catch (e: SecurityException) {
                    Log.w(TAG, "SecurityException for source $source")
                    null
                }
                
                if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                    Log.i(TAG, "AudioRecord success with source: $source")
                    break
                } else {
                    audioRecord?.release()
                    audioRecord = null
                }
            }

            if (audioRecord == null) {
                Log.e(TAG, "All audio sources failed!")
                withContext(Dispatchers.Main) { CallStateManager.statusMessage.value = "Error: Mic Blocked" }
                return@launch
            }

            try {
                audioRecord?.startRecording()
                Log.i(TAG, "Recording started. Source=${audioRecord?.audioSource}")
                
                withContext(Dispatchers.Main) { 
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    audioManager.isSpeakerphoneOn = true
                    CallStateManager.statusMessage.value = "Transcribing (Source: ${audioRecord?.audioSource})" 
                }

                val buffer = ShortArray(bufferSize)
                while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        if (recognizer.acceptWaveForm(buffer, read)) {
                            val res = recognizer.result
                            withContext(Dispatchers.Main) { onResult(res) }
                        }
                    } else if (read < 0) {
                        Log.e(TAG, "AudioRecord error: $read")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Recording loop error", e)
            } finally {
                recognizer.close()
            }
        }
        
        callStartTime = System.currentTimeMillis()
        transcriptBuffer.setLength(0)
        CallStateManager.liveTranscript.value = ""
        printer.printLiveHeader(CallStateManager.activePhoneNumber.value.ifEmpty { "Unknown" }, CallRepository.currentSimSlot.toString(), callStartTime)
    }

    private fun stopTranscription() {
        Log.i(TAG, "stopTranscription called")
        recognitionJob?.cancel()
        recognitionJob = null
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) { Log.e(TAG, "Failed to stop AudioRecord", e) }

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_NORMAL

        val callEndTime = System.currentTimeMillis()
        val durationSeconds = (callEndTime - callStartTime) / 1000
        printer.printLiveFooter(durationSeconds)

        val finalLines = transcriptBuffer.toString()
        if (finalLines.isNotEmpty()) {
            CallRepository.addRecord(
                CallRecord(
                    phoneNumber = CallStateManager.activePhoneNumber.value.ifEmpty { "Unknown" },
                    startTime = callStartTime,
                    endTime = callEndTime,
                    durationSeconds = durationSeconds,
                    simSlotUsed = CallRepository.currentSimSlot,
                    transcriptLines = finalLines
                )
            )
        }
        CallStateManager.activePhoneNumber.value = ""
        CallStateManager.liveTranscript.value = ""
    }

    override fun onResult(hypothesis: String) {
        val json = JSONObject(hypothesis)
        val text = json.optString("text").trim()
        if (text.isEmpty()) return
        val spk = json.optJSONArray("spk")
        val speakerLabel = if (spk != null && spk.length() > 0) identifySpeaker(DoubleArray(spk.length()) { spk.getDouble(it) }) else "Unknown"
        val formattedLine = ReceiptFormatter.formatLiveLine(speakerLabel.uppercase(), text)
        transcriptBuffer.append(formattedLine)
        printer.printLiveLine(formattedLine)
        CallStateManager.liveTranscript.value = transcriptBuffer.toString()
    }

    override fun onFinalResult(hypothesis: String) { onResult(hypothesis) }
    override fun onPartialResult(hypothesis: String) {}
    override fun onError(e: Exception) { Log.e(TAG, "Vosk Error", e) }
    override fun onTimeout() {}

    private fun identifySpeaker(currentVector: DoubleArray): String {
        val c1v = caller1Vector ?: run { caller1Vector = currentVector; return "Caller 1" }
        var dot = 0.0; var nA = 0.0; var nB = 0.0
        for (i in c1v.indices) {
            dot += c1v[i] * currentVector[i]
            nA += c1v[i] * c1v[i]
            nB += currentVector[i] * currentVector[i]
        }
        val sim = if (nA > 0 && nB > 0) dot / (sqrt(nA) * sqrt(nB)) else 0.0
        return if (sim >= 0.65) "Caller 1" else "Caller 2"
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
