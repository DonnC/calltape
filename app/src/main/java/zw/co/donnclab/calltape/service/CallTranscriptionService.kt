package zw.co.donnclab.calltape.service

import android.Manifest
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
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import zw.co.donnclab.calltape.MainActivity
import zw.co.donnclab.calltape.data.CallRecord
import zw.co.donnclab.calltape.data.CallRepository
import zw.co.donnclab.calltape.hardware.IPosPrinter
import zw.co.donnclab.calltape.hardware.LoggerPrinterImplI
import zw.co.donnclab.calltape.telecom.CallStateManager
import zw.co.donnclab.calltape.utils.ReceiptFormatter
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
    private var lastAudioLogAt = 0L
    private var lastAudioRms = 0.0
    private val startHandler = Handler(Looper.getMainLooper())
    private val delayedStart = Runnable { startTranscription() }

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
        Log.i(TAG, "Service ready: recordAudio=${hasRecordAudioPermission()}")
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
        startHandler.removeCallbacks(delayedStart)
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
        val prevState = CallStateManager.callState.value
        Log.i(TAG, "handleCallState change request: $prevState -> $state")
        
        // Always ensure CallStateManager is updated
        CallStateManager.callState.value = state
        
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                CallStateManager.statusMessage.value = "Incoming Call..."
                bringUiToFront()
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                CallStateManager.statusMessage.value = "Call Active - Probing call audio"
                bringUiToFront()
                
                // Only start transcription if not already running
                if (recognitionJob == null) {
                    startHandler.removeCallbacks(delayedStart)
                    startHandler.postDelayed(delayedStart, 1000)
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                startHandler.removeCallbacks(delayedStart)
                CallStateManager.statusMessage.value = "Call Ended"
                stopTranscription()
            }
        }
    }

    private fun bringUiToFront() {
        val uiIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        try {
            startActivity(uiIntent)
        } catch (e: Exception) {
            Log.e(TAG, "bringUiToFront failed", e)
        }
    }

    private fun startTranscription() {
        if (recognitionJob != null) return

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Log.i(TAG, "Starting direct call-audio capture: mode=${audioManager.mode}, speaker=${audioManager.isSpeakerphoneOn}, micMute=${audioManager.isMicrophoneMute}")

        val model = VoskModelManager.mainModel ?: run {
            CallStateManager.statusMessage.value = "Error: Models not ready"
            return
        }

        recognitionJob = serviceScope.launch @androidx.annotation.RequiresPermission(android.Manifest.permission.RECORD_AUDIO) {
            val recognizer = VoskModelManager.speakerModel?.let { sm ->
                Recognizer(model, SAMPLE_RATE.toFloat(), sm)
            } ?: Recognizer(model, SAMPLE_RATE.toFloat())

            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)

            audioRecord = openDirectCallAudioRecord(bufferSize)

            if (audioRecord == null) {
                Log.e(TAG, "All audio sources failed!")
                withContext(Dispatchers.Main) { CallStateManager.statusMessage.value = "Direct call audio unavailable" }
                return@launch
            }

            try {
                audioRecord?.startRecording()
                Log.i(TAG, "Recording started. Direct source=${audioRecord?.audioSource} (${audioSourceName(audioRecord?.audioSource ?: -1)})")
                
                withContext(Dispatchers.Main) { 
                    CallStateManager.statusMessage.value = "Transcribing (Source: ${audioRecord?.audioSource})" 
                }

            if (bufferSize <= 0) {
                Log.e(TAG, "AudioRecord returned invalid buffer size: $bufferSize")
                return@launch
            }
            val buffer = ShortArray(bufferSize / 2)
            var lastPartialLogAt = 0L
                while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        var sum = 0.0
                        for (index in 0 until read) {
                            val sample = buffer[index].toDouble()
                            sum += sample * sample
                        }
                        lastAudioRms = sqrt(sum / read)
                        val now = System.currentTimeMillis()
                        if (now - lastAudioLogAt >= 1000) {
                            lastAudioLogAt = now
                            Log.i(TAG, "PCM alive: source=${audioSourceName(audioRecord?.audioSource ?: -1)}, read=$read, rms=${"%.1f".format(lastAudioRms)}")
                        }
                        if (recognizer.acceptWaveForm(buffer, read)) {
                            val res = recognizer.result
                            Log.i(TAG, "Vosk final result: $res")
                            withContext(Dispatchers.Main) { onResult(res) }
                        } else if (now - lastPartialLogAt >= 1000) {
                            lastPartialLogAt = now
                            Log.d(TAG, "Vosk partial result: ${recognizer.partialResult}")
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
        val hadSession = callStartTime != 0L
        val hadRecorder = audioRecord != null
        recognitionJob?.cancel()
        recognitionJob = null
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) { Log.e(TAG, "Failed to stop AudioRecord", e) }

        Log.i(TAG, "Stopping capture: lastRms=${"%.1f".format(lastAudioRms)}, hadRecorder=$hadRecorder, hadSession=$hadSession")

        if (!hadSession) {
            CallStateManager.activePhoneNumber.value = ""
            CallStateManager.liveTranscript.value = ""
            return
        }

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
        callStartTime = 0L
        caller1Vector = null
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

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun openDirectCallAudioRecord(bufferSize: Int): AudioRecord? {
        if (!hasRecordAudioPermission()) {
            Log.e(TAG, "Cannot open call audio: RECORD_AUDIO permission is not granted")
            return null
        }

        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_DOWNLINK,
            MediaRecorder.AudioSource.VOICE_UPLINK,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_RECOGNITION
            // MIC is intentionally not used as a call-audio fallback. A
            // successful microphone capture would otherwise look like a
            // successful call capture while containing only local speech.
        )

        for (source in sources) {
            Log.i(TAG, "Trying direct audio source $source (${audioSourceName(source)})")
            val candidate = try {
                val format = AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
                AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSize)
                    .build()
            } catch (error: Exception) {
                Log.w(TAG, "AudioRecord construction failed for ${audioSourceName(source)}: ${error.message}")
                null
            }

            if (candidate?.state == AudioRecord.STATE_INITIALIZED) {
                Log.i(TAG, "AudioRecord initialized for ${audioSourceName(source)}; probing PCM without speaker routing")
                val probeBuffer = ShortArray((bufferSize / 2).coerceAtLeast(1))
                var peakRms = 0.0
                try {
                    candidate.startRecording()
                    repeat(3) {
                        val read = candidate.read(probeBuffer, 0, probeBuffer.size)
                        if (read > 0) {
                            var sum = 0.0
                            for (index in 0 until read) {
                                val sample = probeBuffer[index].toDouble()
                                sum += sample * sample
                            }
                            peakRms = maxOf(peakRms, sqrt(sum / read))
                        }
                    }
                    candidate.stop()
                } catch (error: Exception) {
                    Log.w(TAG, "PCM probe failed for ${audioSourceName(source)}: ${error.message}")
                }
                Log.i(TAG, "PCM probe for ${audioSourceName(source)}: peakRms=${"%.1f".format(peakRms)}")
                if (peakRms > 1.0) return candidate
                candidate.release()
                Log.w(TAG, "${audioSourceName(source)} initialized but produced near-silent PCM")
                continue
            }
            Log.w(TAG, "AudioRecord rejected source ${audioSourceName(source)} state=${candidate?.state}")
            candidate?.release()
        }
        return null
    }

    private fun audioSourceName(source: Int): String = when (source) {
        MediaRecorder.AudioSource.VOICE_CALL -> "VOICE_CALL"
        MediaRecorder.AudioSource.VOICE_DOWNLINK -> "VOICE_DOWNLINK"
        MediaRecorder.AudioSource.VOICE_UPLINK -> "VOICE_UPLINK"
        MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
        MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
        MediaRecorder.AudioSource.MIC -> "MIC"
        else -> "UNKNOWN"
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
