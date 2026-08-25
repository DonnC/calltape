package zw.co.donnclab.calltape

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import zw.co.donnclab.calltape.service.VoskModelManager
import zw.co.donnclab.calltape.telecom.CallTapeInCallService
import zw.co.donnclab.calltape.ui.ActiveCallScreen
import zw.co.donnclab.calltape.ui.DialerScreen
import zw.co.donnclab.calltape.ui.HomeScreen
import zw.co.donnclab.calltape.ui.theme.CallTapeTheme
import zw.co.donnclab.calltape.viewmodel.CallViewModel

sealed interface Screen {
    data object Home : Screen
    data object Dialer : Screen
}

class MainActivity : ComponentActivity() {
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            requestDefaultDialer()
        }
    }

    private val dialerRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Models init after permissions & dialer setup are complete
        VoskModelManager.init(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.CALL_PHONE,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_PHONE_STATE
            )
        )

        setContent {
            CallTapeTheme {
                MainAppRouter()
            }
        }
    }

    private fun requestDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == false) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                dialerRoleLauncher.launch(intent)
                return
            }
        } else {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (packageName != telecomManager.defaultDialerPackage) {
                val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                    .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                dialerRoleLauncher.launch(intent)
                return
            }
        }
        VoskModelManager.init(this)
    }
}


@Composable
fun MainAppRouter(
    viewModel: CallViewModel = viewModel()
) {
    val activeCall by CallTapeInCallService.activeCallState.collectAsState()

    var showDialer by remember { mutableStateOf(false) }

    if (activeCall != null) {
        ActiveCallScreen(call = activeCall!!)
    } else if (showDialer) {
        DialerScreen(
            viewModel = viewModel,
            onBack = { showDialer = false }
        )
    } else {
        HomeScreen(
            viewModel = viewModel,
            onNavigateToDialer = { showDialer = true }
        )
    }
}
