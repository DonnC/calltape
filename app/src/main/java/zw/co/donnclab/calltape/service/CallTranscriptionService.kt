package zw.co.donnclab.calltape.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
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
    private var speechService: SpeechService? = null
    private val printer: IPosPrinter = LoggerPrinterImplI

    private var callStartTime: Long = 0
    private var transcriptBuffer = StringBuilder()
    private var caller1Vector: DoubleArray? = null

    companion object {
        private const val TAG = "CallTranscriptionService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "call_transcription_channel"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "CallTranscriptionService: onCreate")
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        createNotificationChannel()
        startForegroundService()
        registerCallStateListener()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand received")
        
        val stateString = intent?.getStringExtra("state")
        val number = intent?.getStringExtra("number")
        
        if (stateString != null) {
            Log.i(TAG, "Processing state from intent: $stateString, number: $number")
            if (!number.isNullOrEmpty()) {
                CallStateManager.activePhoneNumber.value = number
            }
            
            val state = when (stateString) {
                TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
                TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
                TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
                else -> -1
            }
            if (state != -1) {
                handleCallState(state)
            }
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTranscription()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Transcription Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Call Transcription Active")
            .setContentText("Monitoring for calls...")
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
                override fun onCallStateChanged(state: Int) {
                    handleCallState(state)
                }
            })
        } else {
            @Suppress("DEPRECATION")
            telephonyManager.listen(object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    if (!phoneNumber.isNullOrEmpty()) {
                        CallStateManager.activePhoneNumber.value = phoneNumber
                    }
                    handleCallState(state)
                }
            }, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun handleCallState(state: Int) {
        Log.i(TAG, "handleCallState: $state")
        CallStateManager.callState.value = state
        
        val stateMsg = when(state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> "Call Started"
            TelephonyManager.CALL_STATE_RINGING -> "Incoming Call"
            TelephonyManager.CALL_STATE_IDLE -> "Call Ended"
            else -> "Call State: $state"
        }
        Toast.makeText(this, stateMsg, Toast.LENGTH_SHORT).show()

        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK, 
            TelephonyManager.CALL_STATE_RINGING -> {
                Log.d(TAG, "Call ACTIVE/RINGING. Bringing UI to foreground.")
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
                startActivity(intent)
                
                if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                    startTranscription()
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                Log.d(TAG, "Call IDLE. Stopping Transcription.")
                stopTranscription()
            }
        }
    }

    private fun startTranscription() {
        callStartTime = System.currentTimeMillis()
        transcriptBuffer.setLength(0)
        CallStateManager.liveTranscript.value = ""
        caller1Vector = null

        printer.printLiveHeader(
            CallStateManager.activePhoneNumber.value.ifEmpty { "Unknown" },
            CallRepository.currentSimSlot.toString(),
            callStartTime
        )

        try {
            if (VoskModelManager.isReady.value) {
                VoskModelManager.mainModel?.let { m ->
                    Log.i(TAG, "Starting Vosk SpeechService...")
                    val recognizer = VoskModelManager.speakerModel?.let { sm ->
                        Recognizer(m, 16000.0f, sm)
                    } ?: Recognizer(m, 16000.0f)

                    speechService = SpeechService(recognizer, 16000.0f)
                    speechService?.startListening(this)
                    Log.i(TAG, "SpeechService started successfully.")
                    Toast.makeText(this, "Transcription Started", Toast.LENGTH_SHORT).show()
                } ?: run {
                    Log.e(TAG, "Vosk Models not ready! mainModel is null.")
                }
            } else {
                Log.w(TAG, "Vosk Models not ready yet. Status: ${VoskModelManager.loadingStatus.value}")
                Toast.makeText(this, "Vosk Models loading...", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vosk startup failed", e)
        }
    }

    private fun stopTranscription() {
        speechService?.stop()
        speechService = null

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
        
        // Reset state
        CallStateManager.activePhoneNumber.value = ""
        CallStateManager.liveTranscript.value = ""
    }

    override fun onResult(hypothesis: String) {
        val json = JSONObject(hypothesis)
        val text = json.optString("text").trim()
        if (text.isEmpty()) return

        val spk = json.optJSONArray("spk")
        val speakerLabel = if (spk != null && spk.length() > 0) {
            val vector = DoubleArray(spk.length()) { spk.getDouble(it) }
            identifySpeaker(vector)
        } else "Unknown"

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
        val c1v = caller1Vector ?: run {
            caller1Vector = currentVector
            return "Caller 1"
        }
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
