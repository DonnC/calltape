package zw.co.donnclab.calltape.ui

import android.content.Context
import android.content.Intent
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
    val status by CallStateManager.statusMessage.collectAsState()
    
    LaunchedEffect(callState) {
        android.util.Log.i("ActiveCallScreen", "UI State Change: callState=$callState, status=$status")
    }
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
        Text(status, color = Color.Yellow, style = MaterialTheme.typography.bodyMedium)
        Text("Audio Mode: ${audioManager.mode}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
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
                            android.util.Log.i("ActiveCallScreen", "Answer Clicked")
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
                            android.util.Log.i("ActiveCallScreen", "Decline Clicked")
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
                            // ROMs vary on which mode works for speakerphone hijack
                            if (isSpeakerOn) {
                                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                                audioManager.isSpeakerphoneOn = true
                            } else {
                                audioManager.mode = AudioManager.MODE_NORMAL
                                audioManager.isSpeakerphoneOn = false
                            }
                            
                            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVolume, AudioManager.FLAG_SHOW_UI)
                            
                            android.util.Log.i("ActiveCallScreen", "Manual Speaker Toggle: $isSpeakerOn, Mode: ${audioManager.mode}")
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
                            android.util.Log.i("ActiveCallScreen", "End Call Clicked")
                            telecomManager.endCall()
                        } catch (e: SecurityException) {
                            android.util.Log.e("ActiveCallScreen", "Failed to end call", e)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("End Call") }
            }
        }
        
        // Debug hijacking button
        if (callState == TelephonyManager.CALL_STATE_OFFHOOK && liveTranscript.isEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    // Send intent to service to force start transcription
                    val intent = Intent(context, zw.co.donnclab.calltape.service.CallTranscriptionService::class.java).apply {
                        putExtra("state", "OFFHOOK")
                    }
                    context.startService(intent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Magenta)
            ) { Text("Force Mic Hijack") }
        }
    }
}
