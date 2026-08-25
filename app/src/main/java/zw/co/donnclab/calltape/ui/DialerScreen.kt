package zw.co.donnclab.calltape.ui

import android.content.Context
import android.content.Intent
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
import androidx.core.net.toUri
import zw.co.donnclab.calltape.ui.theme.CallTapeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val telecomManager = remember { context.getSystemService(TelecomManager::class.java) }
    var phoneNumber by remember { mutableStateOf("") }

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

            // Phone number display
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

            // Dial pad
            DialPad(
                onDigitClick = { digit -> if (phoneNumber.length < 15) phoneNumber += digit }
            )

            Spacer(modifier = Modifier.weight(1f))

            // SIM Call Buttons
            SIMCallButtons(
                telecomManager = telecomManager,
                phoneNumber = phoneNumber,
                context = context
            )
            
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

    val letters = listOf(
        listOf("", "ABC", "DEF"),
        listOf("GHI", "JKL", "MNO"),
        listOf("PQRS", "TUV", "WXYZ"),
        listOf("", "+", "")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        digits.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEachIndexed { colIndex, digit ->
                    DialButton(
                        digit = digit, 
                        subtext = letters[rowIndex][colIndex],
                        onClick = { onDigitClick(digit) }
                    )
                }
            }
        }
    }
}

@Composable
fun DialButton(digit: String, subtext: String, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.size(width = 90.dp, height = 70.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
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
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun SIMCallButtons(
    telecomManager: TelecomManager?,
    phoneNumber: String,
    context: Context
) {
    val accounts = remember {
        try {
            telecomManager?.callCapablePhoneAccounts ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    if (accounts.isEmpty() || accounts.size == 1) {
        val account = accounts.firstOrNull()
        Button(
            onClick = { initiateCall(context, phoneNumber, account) },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.outline
            ),
            enabled = phoneNumber.isNotEmpty()
        ) {
            Icon(Icons.Rounded.Call, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Initiate Call", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            accounts.forEachIndexed { index, account ->
                Button(
                    onClick = { initiateCall(context, phoneNumber, account) },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        contentColor = Color.White,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.outline
                    ),
                    enabled = phoneNumber.isNotEmpty()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SIM ${index + 1}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun initiateCall(context: Context, phoneNumber: String, accountHandle: PhoneAccountHandle?) {
    if (phoneNumber.isEmpty()) return

    val intent = Intent(Intent.ACTION_CALL).apply {
        data = "tel:${Uri.encode(phoneNumber)}".toUri()
        if (accountHandle != null) {
            putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accountHandle)
        }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    
    try {
        context.startActivity(intent)
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
