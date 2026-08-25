package zw.co.donnclab.calltape.ui

import android.telecom.Call
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import zw.co.donnclab.calltape.telecom.CallTapeInCallService

@Composable
fun ActiveCallScreen(call: Call?) {
    var isSpeakerOn by remember { mutableStateOf(false) }
    val liveTranscript by CallTapeInCallService.activeTranscript.collectAsState()
    val callState = call?.state
    
    val stateText = when (callState) {
        Call.STATE_DIALING -> "Dialing..."
        Call.STATE_RINGING -> "Incoming Call..."
        Call.STATE_ACTIVE -> "Call Active"
        Call.STATE_DISCONNECTED -> "Call Ended"
        else -> "Connecting..."
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(stateText, color = Color.White, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(32.dp))

        // Virtual Receipt Output
        Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.DarkGray).padding(8.dp)) {
            Text(liveTranscript, color = Color.Green, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = if (isSpeakerOn) Color.Blue else Color.Gray),
                onClick = {
                    isSpeakerOn = !isSpeakerOn
                    CallTapeInCallService.instance?.setSpeakerphoneOn(isSpeakerOn)
                }
            ) { Text("Speaker") }
            
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                onClick = { CallTapeInCallService.instance?.endActiveCall() }
            ) { Text("End Call") }
        }
    }
}