package zw.co.donnclab.calltape.data

import java.util.UUID

data class CallRecord(
    val id: String = UUID.randomUUID().toString(),
    val phoneNumber: String,
    val startTime: Long,
    val endTime: Long = 0L,
    val durationSeconds: Long = 0L,
    val simSlotUsed: Int,
    val transcriptLines: String // Stores the formatted dialogue lines
)