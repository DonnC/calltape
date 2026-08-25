package zw.co.donnclab.calltape.telecom

import android.telephony.TelephonyManager
import kotlinx.coroutines.flow.MutableStateFlow

object CallStateManager {
    /**
     * Track TelephonyManager states:
     * TelephonyManager.CALL_STATE_IDLE
     * TelephonyManager.CALL_STATE_RINGING
     * TelephonyManager.CALL_STATE_OFFHOOK
     */
    val callState = MutableStateFlow(TelephonyManager.CALL_STATE_IDLE)
    
    val activePhoneNumber = MutableStateFlow("")
    
    val liveTranscript = MutableStateFlow("")
}
