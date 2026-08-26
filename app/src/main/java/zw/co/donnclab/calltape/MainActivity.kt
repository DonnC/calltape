package zw.co.donnclab.calltape

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telephony.TelephonyManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import zw.co.donnclab.calltape.service.CallTranscriptionService
import zw.co.donnclab.calltape.service.VoskModelManager
import zw.co.donnclab.calltape.telecom.CallStateManager
import zw.co.donnclab.calltape.ui.ActiveCallScreen
import zw.co.donnclab.calltape.ui.DialerScreen
import zw.co.donnclab.calltape.ui.HomeScreen
import zw.co.donnclab.calltape.ui.RecorderScreen
import zw.co.donnclab.calltape.ui.theme.CallTapeTheme
import zw.co.donnclab.calltape.viewmodel.CallViewModel

class MainActivity : ComponentActivity() {
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        android.util.Log.i("MainActivity", "Permissions result: $result")
        if (result.values.all { it }) {
            startTranscriptionService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.i("MainActivity", "onCreate: Requesting permissions")

        requestPermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.CALL_PHONE,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.ANSWER_PHONE_CALLS,
                Manifest.permission.MANAGE_OWN_CALLS
            )
        )

        VoskModelManager.init(this)
        requestDefaultDialer()

        setContent {
            CallTapeTheme {
                MainAppRouter()
            }
        }
    }

    private val dialerRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        android.util.Log.i("MainActivity", "Dialer role result: ${result.resultCode}")
    }

    private fun requestDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER) == false) {
                val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER)
                dialerRoleLauncher.launch(intent)
            }
        }
    }

    private fun startTranscriptionService() {
        android.util.Log.i("MainActivity", "startTranscriptionService called")
        val intent = Intent(this, CallTranscriptionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

enum class ScreenTab(val title: String, val icon: ImageVector) {
    HISTORY("History", Icons.Default.List),
    DIALER("Dialer", Icons.Default.Phone),
    ACTIVE_CALL("In-Call", Icons.Default.Call),
    RECORDER("Recorder", Icons.Default.Mic)
}

@Composable
fun MainAppRouter(
    viewModel: CallViewModel = viewModel()
) {
    val callState by CallStateManager.callState.collectAsState()
    var selectedTab by remember { mutableStateOf(ScreenTab.HISTORY) }

    LaunchedEffect(callState) {
        android.util.Log.i("MainActivity", "LaunchedEffect Triggered: callState=$callState")
        when (callState) {
            TelephonyManager.CALL_STATE_OFFHOOK, 
            TelephonyManager.CALL_STATE_RINGING -> {
                android.util.Log.i("MainActivity", "Switching to ACTIVE_CALL tab")
                selectedTab = ScreenTab.ACTIVE_CALL
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                android.util.Log.i("MainActivity", "Switching to HISTORY tab")
                selectedTab = ScreenTab.HISTORY
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                ScreenTab.entries.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                ScreenTab.HISTORY -> HomeScreen(
                    viewModel = viewModel,
                    onNavigateToDialer = { selectedTab = ScreenTab.DIALER }
                )
                ScreenTab.DIALER -> DialerScreen(
                    viewModel = viewModel,
                    onBack = { selectedTab = ScreenTab.HISTORY }
                )
                ScreenTab.ACTIVE_CALL -> ActiveCallScreen()
                ScreenTab.RECORDER -> RecorderScreen()
            }
        }
    }
}
