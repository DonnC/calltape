package zw.co.donnclab.calltape.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import zw.co.donnclab.calltape.data.CallRepository
import zw.co.donnclab.calltape.service.CallTranscriptionService
import zw.co.donnclab.calltape.ui.theme.CallTapeTheme
import zw.co.donnclab.calltape.viewmodel.CallViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialerScreen(
    viewModel: CallViewModel = viewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val telecomManager = remember { context.getSystemService(TelecomManager::class.java) }
    var phoneNumber by remember { mutableStateOf("") }
    val selectedSimSlot by viewModel.selectedSimSlot.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("New Call", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = phoneNumber.ifEmpty { " " },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        maxLines = 1
                    )
                    
                    if (phoneNumber.isNotEmpty()) {
                        IconButton(
                            onClick = { phoneNumber = phoneNumber.dropLast(1) }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Backspace, 
                                contentDescription = "Backspace",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            DialPad(
                onDigitClick = { digit -> if (phoneNumber.length < 15) phoneNumber += digit }
            )

            Spacer(modifier = Modifier.weight(1f))

            val hasPhonePermissions = remember(context) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
            }

            if (hasPhonePermissions) {
                SimSelectionAndCallButton(
                    telecomManager = telecomManager,
                    phoneNumber = phoneNumber,
                    selectedSimSlot = selectedSimSlot,
                    onSimSelect = { viewModel.selectSimSlot(it) },
                    context = context
                )
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Phone permissions are required to make calls.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
fun DialPad(onDigitClick: (String) -> Unit) {
    val digits = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("*", "0", "#")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        digits.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { digit ->
                    DialButton(
                        digit = digit,
                        subtext = if (digit == "0") "+" else "",
                        onClick = { onDigitClick(digit) },
                        onLongClick = if (digit == "0") { { onDigitClick("+") } } else null
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialButton(
    digit: String, 
    subtext: String = "", 
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    OutlinedCard(
        modifier = Modifier.size(width = 90.dp, height = 70.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = digit, 
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (subtext.isNotEmpty()) {
                    Text(
                        text = subtext,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun SimSelectionAndCallButton(
    telecomManager: TelecomManager?,
    phoneNumber: String,
    selectedSimSlot: Int,
    onSimSelect: (Int) -> Unit,
    context: Context
) {
    val accounts = remember {
        try {
            telecomManager?.callCapablePhoneAccounts ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            listOf(1, 2).forEach { slot ->
                val isSelected = selectedSimSlot == slot
                Button(
                    onClick = { onSimSelect(slot) },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    colors = if (isSelected) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                ) {
                    Text(
                        text = "SIM $slot",
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Large Call Button
        Button(
            onClick = {
                val accountHandle = if (accounts.isNotEmpty()) {
                    // Try to match the selected slot with available accounts
                    // If slot is 1, take index 0. If slot is 2, take index 1 if available, else index 0.
                    val index = if (selectedSimSlot == 1) 0 else (if (accounts.size > 1) 1 else 0)
                    accounts[index]
                } else {
                    null
                }
                initiateCall(context, phoneNumber, accountHandle, selectedSimSlot)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50),
                contentColor = Color.White
            ),
            enabled = phoneNumber.isNotEmpty()
        ) {
            Icon(
                Icons.Rounded.Call, 
                contentDescription = "Call",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

private fun initiateCall(context: Context, phoneNumber: String, accountHandle: PhoneAccountHandle?, simSlot: Int) {
    if (phoneNumber.isEmpty()) return

    CallRepository.currentSimSlot = simSlot

    val serviceIntent = Intent(context, CallTranscriptionService::class.java).apply {
        putExtra("EXTRA_PHONE_NUMBER", phoneNumber)
        putExtra("EXTRA_SIM_SLOT", "SIM ${simSlot + 1}")
    }
    context.startService(serviceIntent) // Updates variables in onStartCommand

    val callIntent = Intent(Intent.ACTION_CALL).apply {
        data = "tel:${Uri.encode(phoneNumber)}".toUri()
        if (accountHandle != null) {
            putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accountHandle)
        }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(callIntent)
    } catch (e: SecurityException) {
        // Handle missing permission
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun DialerScreenPreview() {
    CallTapeTheme {
        DialerScreen(onBack = {})
    }
}
