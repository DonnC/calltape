package zw.co.donnclab.calltape.ui

import android.content.Context
import android.media.AudioManager
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import zw.co.donnclab.calltape.telecom.CallStateManager

@Composable
fun ActiveCallScreen() {
    val context = LocalContext.current
    val callState by CallStateManager.callState.collectAsState()
    val liveTranscript by CallStateManager.liveTranscript.collectAsState()
    val phoneNumber by CallStateManager.activePhoneNumber.collectAsState()
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val telecomManager = remember { context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager }

    var isSpeakerOn by remember { mutableStateOf(audioManager.isSpeakerphoneOn) }

    val stateText = when (callState) {
        TelephonyManager.CALL_STATE_RINGING -> "Incoming Call: $phoneNumber"
        TelephonyManager.CALL_STATE_OFFHOOK -> "Call Active: $phoneNumber"
        TelephonyManager.CALL_STATE_IDLE -> "Call Ended"
        else -> "Connecting..."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(stateText, color = Color.White, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(32.dp))

        // Virtual Receipt Output
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.DarkGray)
                .padding(8.dp)
        ) {
            Text(liveTranscript, color = Color.Green, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (callState == TelephonyManager.CALL_STATE_RINGING) {
                Button(
                    onClick = {
                        try {
                            telecomManager.acceptRingingCall()
                        } catch (e: SecurityException) {
                            android.util.Log.e("ActiveCallScreen", "Failed to answer call", e)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
                ) { Text("Answer") }

                Button(
                    onClick = {
                        try {
                            telecomManager.endCall()
                        } catch (e: SecurityException) {
                            android.util.Log.e("ActiveCallScreen", "Failed to end call", e)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Decline") }
            } else {
                Button(
                    onClick = {
                        isSpeakerOn = !isSpeakerOn
                        try {
                            audioManager.mode = AudioManager.MODE_IN_CALL
                            audioManager.isSpeakerphoneOn = isSpeakerOn
                            android.util.Log.i("ActiveCallScreen", "Speaker toggled: $isSpeakerOn")
                        } catch (e: Exception) {
                            android.util.Log.e("ActiveCallScreen", "Failed to toggle speaker", e)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSpeakerOn) Color.Blue else Color.Gray
                    )
                ) { Text(if (isSpeakerOn) "Speaker ON" else "Speaker OFF") }

                Button(
                    onClick = {
                        try {
                            telecomManager.endCall()
                        } catch (e: SecurityException) {
                            android.util.Log.e("ActiveCallScreen", "Failed to end call", e)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("End Call") }
            }
        }
    }
}
