package zw.co.donnclab.calltape.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import zw.co.donnclab.calltape.data.CallRecord
import zw.co.donnclab.calltape.ui.theme.CallTapeTheme
import zw.co.donnclab.calltape.viewmodel.CallViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: CallViewModel,
    onNavigateToDialer: () -> Unit
) {
    val callHistory by viewModel.callHistory.collectAsState()

    HistoryContent(
        callHistory = callHistory,
        onClearHistory = { viewModel.clearHistory() },
        onPrintRecord = { viewModel.printTranscript(it) },
        onNavigateToDialer = onNavigateToDialer
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryContent(
    callHistory: List<CallRecord>,
    onClearHistory: () -> Unit,
    onPrintRecord: (CallRecord) -> Unit,
    onNavigateToDialer: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "History",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    ) 
                },
                actions = {
                    if (callHistory.isNotEmpty()) {
                        IconButton(onClick = onClearHistory) {
                            Icon(
                                Icons.Rounded.Delete, 
                                contentDescription = "Clear History",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToDialer,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large
            ) {
                Icon(
                    Icons.Rounded.Add, 
                    contentDescription = "New Call",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { innerPadding ->
        if (callHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No records found",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your call transcripts will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(callHistory) { record ->
                    CallRecordCard(
                        record = record,
                        onPrint = { onPrintRecord(record) }
                    )
                }
            }
        }
    }
}

@Composable
fun CallRecordCard(
    record: CallRecord,
    onPrint: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.phoneNumber,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Text(
                        text = formatTimestamp(record.timestamp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                
                OutlinedIconButton(
                    onClick = onPrint,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Icon(
                        Icons.Rounded.Print, 
                        contentDescription = "Print",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoChip(
                    icon = Icons.Rounded.Schedule,
                    text = formatDuration(record.durationSeconds)
                )
                InfoChip(
                    icon = Icons.Rounded.SettingsInputAntenna,
                    text = record.simSlotUsed
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = record.transcript,
                    style = MaterialTheme.typography.bodySmall.copy(
                        lineHeight = 18.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(10.dp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun InfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                icon, 
                contentDescription = null, 
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, remainingSeconds)
}

private fun formatTimestamp(timestamp: Long): String {
    val formatter = DateTimeFormatter.ofPattern("EEEE, MMM dd • HH:mm", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
    return formatter.format(Instant.ofEpochMilli(timestamp))
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun HistoryScreenPreview() {
    CallTapeTheme {
        val mockRecords = listOf(
            CallRecord(
                phoneNumber = "+263 77 123 4567",
                timestamp = System.currentTimeMillis(),
                durationSeconds = 125,
                transcript = "Hello, this is a test transcript for the call tape application. I am speaking clearly and demonstrating the UI features for the new POS dashboard design.",
                simSlotUsed = "SIM 1"
            ),
            CallRecord(
                phoneNumber = "+263 71 987 6543",
                timestamp = System.currentTimeMillis() - 3600000,
                durationSeconds = 45,
                transcript = "Quick call to confirm the meeting tomorrow at 10 AM. We will discuss the new project requirements and the hardware integration.",
                simSlotUsed = "SIM 2"
            )
        )
        HistoryContent(
            callHistory = mockRecords,
            onClearHistory = {},
            onPrintRecord = {},
            onNavigateToDialer = {}
        )
    }
}
