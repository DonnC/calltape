package zw.co.donnclab.calltape.utils

import android.annotation.SuppressLint
import zw.co.donnclab.calltape.data.CallRecord
import java.text.SimpleDateFormat
import java.util.Date

object ReceiptFormatter {
    @SuppressLint("SimpleDateFormat")
    private val timeFormat = SimpleDateFormat("HH:mm:ss")
    @SuppressLint("SimpleDateFormat")
    private val fullDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    fun buildLiveHeader(phoneNumber: String, simSlot: String, startTime: Long): String {
        return """
            ========================================
            *** $phoneNumber LIVE LOG ***
            ========================================
            STARTED     : ${fullDateFormat.format(Date(startTime))}
            SOURCE      : $simSlot
            ========================================
        """.trimIndent()
    }

    @SuppressLint("DefaultLocale")
    fun buildLiveFooter(durationSeconds: Long): String {
        val durationStr = String.format("%02d:%02d", durationSeconds / 60, durationSeconds % 60)
        val endTime = timeFormat.format(Date())
        return """
            $endTime  [CALL TERMINATED // DURATION $durationStr]
            ========================================
            *** END OF TRANSMISSION ***
            ========================================
        """.trimIndent()
    }

    @SuppressLint("DefaultLocale")
    fun buildFullReceipt(record: CallRecord): String {
        val durationStr = String.format("%02d:%02d", record.durationSeconds / 60, record.durationSeconds % 60)
        
        return """
            ========================================
            *** ${record.phoneNumber} LOG ***
            ========================================
            STARTED     : ${fullDateFormat.format(Date(record.startTime))}
            ENDED       : ${fullDateFormat.format(Date(record.endTime))}
            TIMESTAMP   : $durationStr
            SOURCE      : ${record.simSlotUsed}
            ========================================
            
            ${record.transcriptLines}
            
            ========================================
            *** END OF TRANSMISSION ***
            ========================================
            [CUT PAPER]
        """.trimIndent()
    }
}