package zw.co.donnclab.calltape.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import zw.co.donnclab.calltape.data.CallRecord
import zw.co.donnclab.calltape.data.CallRepository
import zw.co.donnclab.calltape.hardware.MockPrinterImpl
import zw.co.donnclab.calltape.hardware.PosPrinter
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.SpeakerModel
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.IOException
import kotlin.math.sqrt

class CallTranscriptionService : Service(), RecognitionListener {

    private companion object {
        const val TAG = "CallTranscriptionService"
        const val CHANNEL_ID = "CallTranscriptionChannel"
        const val NOTIFICATION_ID = 1
        const val SIMILARITY_THRESHOLD = 0.65
    }

    private lateinit var telephonyManager: TelephonyManager
    private var speechService: SpeechService? = null
    private var model: Model? = null
    private var speakerModel: SpeakerModel? = null
    private val printer: PosPrinter = MockPrinterImpl

    private var callStartTime: Long = 0
    private var transcriptBuffer = StringBuilder()
    private var caller1Vector: DoubleArray? = null
    private var currentPhoneNumber: String = "Unknown"

    private val callStateCallback by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleCallState(state)
                }
            }
        } else {
            null
        }
    }

    private val phoneStateListener by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleCallState(state)
                }
            }
        } else {
            null
        }
    }

    override fun onCreate() {
        super.onCreate()
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        createNotificationChannel()
        startForegroundService()
        registerCallStateListener()
        loadVoskModel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Call Transcription Active")
            .setContentText("Listening for calls...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Transcription Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun registerCallStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            callStateCallback?.let {
                telephonyManager.registerTelephonyCallback(mainExecutor, it)
            }
        } else {
            phoneStateListener?.let {
                @Suppress("DEPRECATION")
                telephonyManager.listen(it, PhoneStateListener.LISTEN_CALL_STATE)
            }
        }
    }

    private fun unregisterCallStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            callStateCallback?.let {
                telephonyManager.unregisterTelephonyCallback(it)
            }
        } else {
            phoneStateListener?.let {
                @Suppress("DEPRECATION")
                telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
            }
        }
    }

    private fun loadVoskModel() {
        // TODO: Load the actual models from assets or storage
        // model = Model(this, "model-en-us")
        // speakerModel = SpeakerModel("model-spk")
        Log.d(TAG, "Loading Vosk models...")
    }

    private fun handleCallState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                Log.d(TAG, "Call Off-hook")
                startTranscription()
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                Log.d(TAG, "Call Idle")
                stopTranscription()
            }
            TelephonyManager.CALL_STATE_RINGING -> {
                Log.d(TAG, "Call Ringing")
            }
        }
    }

    private fun startTranscription() {
        callStartTime = System.currentTimeMillis()
        transcriptBuffer.setLength(0)
        caller1Vector = null

        try {
            model?.let { m ->
                val recognizer = speakerModel?.let { sm ->
                    Recognizer(m, 16000.0f, sm)
                } ?: Recognizer(m, 16000.0f)
                
                speechService = SpeechService(recognizer, 16000.0f)
                speechService?.startListening(this)
                Log.d(TAG, "Transcription started")
            } ?: run {
                Log.e(TAG, "Model not loaded, cannot start transcription")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start speech service", e)
        }
    }

    private fun stopTranscription() {
        speechService?.stop()
        speechService = null

        val durationSeconds = (System.currentTimeMillis() - callStartTime) / 1000
        val finalTranscript = transcriptBuffer.toString()

        if (finalTranscript.isNotEmpty()) {
            val record = CallRecord(
                phoneNumber = currentPhoneNumber,
                timestamp = callStartTime,
                durationSeconds = durationSeconds,
                transcript = finalTranscript,
                simSlotUsed = "SIM 1" // TODO: Detect actual SIM slot
            )
            CallRepository.addRecord(record)
            printer.cutPaper()
            Log.d(TAG, "Call record saved and paper cut")
        }
    }

    // RecognitionListener implementation
    override fun onResult(hypothesis: String) {
        val json = JSONObject(hypothesis)
        val text = json.optString("text")
        if (text.isEmpty()) return

        val spk = json.optJSONArray("spk")
        val speakerLabel = if (spk != null) {
            val currentVector = DoubleArray(spk.length()) { spk.getDouble(it) }
            identifySpeaker(currentVector)
        } else {
            "Unknown"
        }

        val finalizedLine = "$speakerLabel: $text"
        transcriptBuffer.append(finalizedLine).append("\n")
        printer.printLine(finalizedLine)
        Log.d(TAG, "Result: $finalizedLine")
    }

    override fun onPartialResult(hypothesis: String) {
        // Not used for final printing
    }

    override fun onFinalResult(hypothesis: String) {
        onResult(hypothesis)
    }

    override fun onError(exception: Exception) {
        Log.e(TAG, "Vosk Error", exception)
    }

    override fun onTimeout() {
        Log.d(TAG, "Vosk Timeout")
    }

    private fun identifySpeaker(currentVector: DoubleArray): String {
        val c1v = caller1Vector
        if (c1v == null) {
            caller1Vector = currentVector
            return "Caller 1"
        }

        val similarity = cosineSimilarity(c1v, currentVector)
        return if (similarity >= SIMILARITY_THRESHOLD) {
            "Caller 1"
        } else {
            "Caller 2"
        }
    }

    private fun cosineSimilarity(v1: DoubleArray, v2: DoubleArray): Double {
        if (v1.size != v2.size) return 0.0
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        return if (normA > 0 && normB > 0) {
            dotProduct / (sqrt(normA) * sqrt(normB))
        } else {
            0.0
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterCallStateListener()
        speechService?.shutdown()
    }
}
