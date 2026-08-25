package zw.co.donnclab.calltape.data

data class CallRecord(
    val id: Long = 0,
    val phoneNumber: String,
    val timestamp: Long,
    val durationSeconds: Long,
    val transcript: String,
    val simSlotUsed: String
)
