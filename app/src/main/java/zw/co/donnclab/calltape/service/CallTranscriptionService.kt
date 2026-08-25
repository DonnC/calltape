package zw.co.donnclab.calltape.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.SpeakerModel
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import zw.co.donnclab.calltape.data.CallRecord
import zw.co.donnclab.calltape.data.CallRepository
import zw.co.donnclab.calltape.hardware.IPosPrinter
import zw.co.donnclab.calltape.hardware.LoggerPrinterImplI
import zw.co.donnclab.calltape.utils.ReceiptFormatter
import java.io.File
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

    private var mainModel: Model? = null
    private var speakerModel: SpeakerModel? = null

    private val printer: IPosPrinter = LoggerPrinterImplI

    private var callStartTime: Long = 0
    private var transcriptBuffer = StringBuilder()
    private var caller1Vector: DoubleArray? = null
    private var currentPhoneNumber: String = "Unknown"

    private val callStateCallback by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    // In Android 12+, phone number requires READ_CALL_LOG.
                    // We rely on the Intent that started the call to set this, or leave as Unknown.
                    handleCallState(state, "Unknown")
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
                    handleCallState(state, phoneNumber ?: "Unknown")
                }
            }
        } else {
            null
        }
    }

    override fun onCreate() {
        super.onCreate()
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        createNotificationChannel()
        startForegroundService()
        registerCallStateListener()

        loadVoskModels()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            if (it.hasExtra("EXTRA_PHONE_NUMBER")) {
                currentPhoneNumber = it.getStringExtra("EXTRA_PHONE_NUMBER") ?: "Unknown"
            }
            if (it.hasExtra("EXTRA_SIM_SLOT")) {
                CallRepository.currentSimSlot = it.getIntExtra("EXTRA_SIM_SLOT", 1)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CallTape Active")
            .setContentText("Listening and ready to transcribe...")
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

    private fun loadVoskModels() {
        Log.d(TAG, "Unpacking Vosk main speech model from assets...")
        StorageService.unpack(this, "model-en-us", "model",
            { model ->
                this.mainModel = model
                Log.d(TAG, "Main model loaded successfully.")

                Log.d(TAG, "Unpacking Vosk speaker model from assets...")
                StorageService.unpack(this, "spk-model", "spk",
                    { _ ->
                        this.speakerModel = SpeakerModel(File(filesDir, "spk").absolutePath)
                        Log.d(TAG, "Speaker model loaded successfully.")
                    },
                    { exception ->
                        Log.e(TAG, "Failed to load speaker model. Diarization will be disabled.", exception)
                    }
                )
            },
            { exception ->
                Log.e(TAG, "Failed to load main speech model. Transcription cannot start.", exception)
            }
        )
    }

    private fun handleCallState(state: Int, incomingNumber: String) {
        if (incomingNumber != "Unknown" && incomingNumber.isNotBlank()) {
            currentPhoneNumber = incomingNumber
        }

        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                Log.d(TAG, "Call Off-hook. Starting transcription.")
                startTranscription()
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                Log.d(TAG, "Call Idle. Stopping transcription.")
                stopTranscription()
            }
            TelephonyManager.CALL_STATE_RINGING -> {
                Log.d(TAG, "Call Ringing: $currentPhoneNumber")
            }
        }
    }

    private fun startTranscription() {
        callStartTime = System.currentTimeMillis()
        transcriptBuffer.setLength(0)
        caller1Vector = null

        val currentSimSlot = CallRepository.currentSimSlot

        // Output the live header to the thermal printer via the new interface
        printer.printLiveHeader(currentPhoneNumber, "SIM $currentSimSlot", callStartTime)

        try {
            mainModel?.let { m ->
                val recognizer = speakerModel?.let { sm ->
                    Recognizer(m, 16000.0f, sm)
                } ?: Recognizer(m, 16000.0f)

                speechService = SpeechService(recognizer, 16000.0f)
                speechService?.startListening(this)

                Log.d(TAG, "SpeechService listening engine started.")
            } ?: run {
                Log.e(TAG, "Models are not fully unpacked yet, cannot start transcription engine.")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start Vosk speech service", e)
        }
    }

    private fun stopTranscription() {
        if (speechService == null) return

        speechService?.stop()
        speechService = null

        val callEndTime = System.currentTimeMillis()
        val durationSeconds = (callEndTime - callStartTime) / 1000

        // Push the footer to the printer and trigger the paper cut
        printer.printLiveFooter(durationSeconds)

        val finalTranscriptLines = transcriptBuffer.toString()

        if (finalTranscriptLines.isNotEmpty()) {
            val record = CallRecord(
                phoneNumber = currentPhoneNumber,
                startTime = callStartTime,
                endTime = callEndTime,
                durationSeconds = durationSeconds,
                transcriptLines = finalTranscriptLines,
                simSlotUsed = "SIM ${CallRepository.currentSimSlot}"
            )
            CallRepository.addRecord(record)
            Log.d(TAG, "Call record formatted and saved to in-memory repository.")
        }
    }

    // RecognitionListener implementation
    override fun onResult(hypothesis: String) {
        val json = JSONObject(hypothesis)
        val text = json.optString("text").trim()
        if (text.isEmpty()) return

        val spk = json.optJSONArray("spk")
        val speakerLabel = if (spk != null && spk.length() > 0) {
            val currentVector = DoubleArray(spk.length()) { spk.getDouble(it) }
            identifySpeaker(currentVector)
        } else {
            "Unknown"
        }

        // Delegate the layout wrapping and timestamping to the ReceiptFormatter
        val formattedLine = ReceiptFormatter.formatLiveLine(speakerLabel.uppercase(), text)

        transcriptBuffer.append(formattedLine)
        printer.printLiveLine(formattedLine)

        Log.d(TAG, "Finalized Line: $formattedLine")
    }

    override fun onPartialResult(hypothesis: String) {
        // Ignored for the thermal printer to avoid wasting paper on unfinalized guesses
    }

    override fun onFinalResult(hypothesis: String) {
        onResult(hypothesis)
    }

    override fun onError(exception: Exception) {
        Log.e(TAG, "Vosk Engine Error", exception)
    }

    override fun onTimeout() {
        Log.d(TAG, "Vosk Engine Timeout")
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