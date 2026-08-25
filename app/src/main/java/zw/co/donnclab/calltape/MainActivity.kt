package zw.co.donnclab.calltape

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import zw.co.donnclab.calltape.service.CallTranscriptionService
import zw.co.donnclab.calltape.ui.DialerScreen
import zw.co.donnclab.calltape.ui.HomeScreen
import zw.co.donnclab.calltape.ui.theme.CallTapeTheme
import zw.co.donnclab.calltape.viewmodel.CallViewModel

sealed interface Screen {
    data object Home : Screen
    data object Dialer : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CallTapeTheme {
                val context = androidx.compose.ui.platform.LocalContext.current
                val requiredPermissions = arrayOf(
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.RECORD_AUDIO
                )

                var permissionsGranted by remember {
                    mutableStateOf(requiredPermissions.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    })
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { results ->
                    permissionsGranted = results.values.all { it }
                    if (permissionsGranted) {
                        startTranscriptionService()
                    } else {
                        Toast.makeText(context, "All permissions are required for CallTape to function", Toast.LENGTH_LONG).show()
                    }
                }

                LaunchedEffect(Unit) {
                    if (!permissionsGranted) {
                        permissionLauncher.launch(requiredPermissions)
                    } else {
                        startTranscriptionService()
                    }
                }

                if (permissionsGranted) {
                    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
                    val callViewModel: CallViewModel = viewModel()

                    when (currentScreen) {
                        is Screen.Home -> {
                            HomeScreen(
                                viewModel = callViewModel,
                                onNavigateToDialer = { currentScreen = Screen.Dialer }
                            )
                        }
                        is Screen.Dialer -> {
                            BackHandler {
                                currentScreen = Screen.Home
                            }
                            DialerScreen(
                                viewModel = callViewModel,
                                onBack = { currentScreen = Screen.Home }
                            )
                        }
                    }
                } else {
                    PermissionRequiredScreen {
                        permissionLauncher.launch(requiredPermissions)
                    }
                }
            }
        }
    }

    private fun startTranscriptionService() {
        val serviceIntent = Intent(this, CallTranscriptionService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }
}

@Composable
fun PermissionRequiredScreen(onRequestPermissions: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Permissions Required",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "CallTape needs Phone and Microphone permissions to record and transcribe your calls.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRequestPermissions) {
                Text("Grant Permissions")
            }
        }
    }
}
