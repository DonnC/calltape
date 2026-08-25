package zw.co.donnclab.calltape.telecom

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log

class CallTapeInCallService : InCallService() {
    companion object {
        var activeCall: Call? = null
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        activeCall = call

        val phoneNumber = call.details.handle?.schemeSpecificPart ?: "Unknown"
        Log.d("InCall", "New call added: $phoneNumber")

        // Listen to state changes (Ringing, Active, Disconnected)
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                when (state) {
                    Call.STATE_RINGING -> {
                        Log.d("InCall", "Incoming call from: $phoneNumber")
                    }
                    Call.STATE_ACTIVE -> {
                        // Call answered! 
                        // -> Trigger Vosk Transcription
                        // -> Start duration timer
                        Log.d("InCall", "Call answered")
                    }
                    Call.STATE_DISCONNECTED -> {
                        // Call ended!
                        // -> Stop Vosk
                        // -> Print Footer & Cut Paper
                        Log.d("InCall", "Call ended")
                        activeCall = null
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
        })
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        if (activeCall == call) {
            activeCall = null
        }
    }

    // --- Helper Functions for your UI to call ---

    fun answerCall() {
        activeCall?.answer(0)
    }

    fun rejectCall() {
        activeCall?.reject(false, null)
    }

    fun disconnectCall() {
        activeCall?.disconnect()
    }

    fun toggleSpeakerphone(enable: Boolean) {
        val route = if (enable) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
        setAudioRoute(route)
    }
}