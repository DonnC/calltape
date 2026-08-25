package zw.co.donnclab.calltape.telecom

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import zw.co.donnclab.calltape.data.CallRecord
import zw.co.donnclab.calltape.data.CallRepository
import zw.co.donnclab.calltape.hardware.IPosPrinter
import zw.co.donnclab.calltape.hardware.LoggerPrinterImplI
import zw.co.donnclab.calltape.service.VoskModelManager
import zw.co.donnclab.calltape.utils.ReceiptFormatter
import java.io.IOException
import kotlin.math.sqrt

class CallTapeInCallService : InCallService(), RecognitionListener {

    companion object {
        const val TAG = "CallTapeInCallService"
        val activeCallState = MutableStateFlow<Call?>(null)
        val activeTranscript = MutableStateFlow("") // Feeds the UI Live Transcript

        var instance: CallTapeInCallService? = null
    }

    private val printer: IPosPrinter = LoggerPrinterImplI
    private var speechService: SpeechService? = null

    private var currentPhoneNumber: String = "Unknown"
    private var callStartTime: Long = 0
    private var transcriptBuffer = StringBuilder()
    private var caller1Vector: DoubleArray? = null

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            activeCallState.value = call

            val number = call.details.handle?.schemeSpecificPart ?: "Unknown"

            when (state) {
                Call.STATE_ACTIVE -> {
                    Log.d(TAG, "Call is ACTIVE. Starting Vosk Engine.")
                    startTranscription()
                }
                Call.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Call DISCONNECTED. Stopping Engine.")
                    stopTranscription()
                }

                Call.STATE_RINGING -> {
                    Log.d("InCall", "Incoming call from: $number")
                }

                Call.STATE_AUDIO_PROCESSING -> {
                    Log.d("InCall", "Call audio processing..")
                }

                Call.STATE_CONNECTING -> {
                    Log.d("InCall", "Call state connecting..")
                }

                Call.STATE_DIALING -> {
                    Log.d("InCall", "Call dialing..")
                }

                Call.STATE_DISCONNECTING -> {
                    Log.d("InCall", "Call disconnecting..")
                }

                Call.STATE_HOLDING -> {
                    Log.d("InCall", "Call on hold!")
                }

                Call.STATE_NEW -> {
                    Log.d("InCall", "New call")
                }

                Call.STATE_PULLING_CALL -> {
                    Log.d("InCall", "Pulling call")
                }

                Call.STATE_SELECT_PHONE_ACCOUNT -> {
                    Log.d("InCall", "Call select phone account")
                }

                Call.STATE_SIMULATED_RINGING -> {
                    Log.d("InCall", "Call simulated ringing")
                }
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        instance = this

        val number = call.details.handle?.schemeSpecificPart ?: "Unknown"
        currentPhoneNumber = number
        activeCallState.value = call

        call.registerCallback(callCallback)
        Log.d(TAG, "New call registered: $currentPhoneNumber")
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        if (activeCallState.value == call) {
            activeCallState.value = null
        }
        instance = null
    }

    private fun startTranscription() {
        callStartTime = System.currentTimeMillis()
        transcriptBuffer.setLength(0)
        activeTranscript.value = ""
        caller1Vector = null

        printer.printLiveHeader(currentPhoneNumber, CallRepository.currentSimSlot.toString(), callStartTime)

        try {
            VoskModelManager.mainModel?.let { m ->
                val recognizer = VoskModelManager.speakerModel?.let { sm ->
                    Recognizer(m, 16000.0f, sm)
                } ?: Recognizer(m, 16000.0f)

                speechService = SpeechService(recognizer, 16000.0f)
//                speechService?.addListener(this)
                speechService?.startListening(this)

                Log.d(TAG, "SpeechService listening.")
            } ?: Log.e(TAG, "Cannot transcribe. Models not loaded.")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start Vosk", e)
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
                    phoneNumber = currentPhoneNumber,
                    startTime = callStartTime,
                    endTime = callEndTime,
                    durationSeconds = durationSeconds,
                    simSlotUsed = CallRepository.currentSimSlot,
                    transcriptLines = finalLines
                )
            )
        }
    }

    // --- Vosk Callbacks ---
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
        activeTranscript.value = transcriptBuffer.toString()
    }

    override fun onPartialResult(hypothesis: String) {}
    override fun onFinalResult(hypothesis: String) { onResult(hypothesis) }
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

    // --- Telecom Controls ---
    fun setSpeakerphoneOn(isOn: Boolean) {
        val route = if (isOn) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
        setAudioRoute(route)
    }

    fun endActiveCall() {
        activeCallState.value?.disconnect()
    }
}