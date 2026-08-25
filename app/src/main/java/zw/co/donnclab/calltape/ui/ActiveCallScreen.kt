package zw.co.donnclab.calltape.ui

import android.telephony.TelephonyManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import zw.co.donnclab.calltape.telecom.CallStateManager

@Composable
fun ActiveCallScreen() {
    val callState by CallStateManager.callState.collectAsState()
    val liveTranscript by CallStateManager.liveTranscript.collectAsState()
    val phoneNumber by CallStateManager.activePhoneNumber.collectAsState()
    
    val stateText = when (callState) {
        TelephonyManager.CALL_STATE_RINGING -> "Incoming Call: $phoneNumber"
        TelephonyManager.CALL_STATE_OFFHOOK -> "Call Active: $phoneNumber"
        TelephonyManager.CALL_STATE_IDLE -> "Call Ended"
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
            // Note: Speaker and End Call might need system-level implementation or specific hardware APIs on restricted ROMs
            Button(
                onClick = { /* Implement if possible */ },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) { Text("Speaker") }
            
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                onClick = { /* Implement if possible */ }
            ) { Text("End Call") }
        }
    }
}
