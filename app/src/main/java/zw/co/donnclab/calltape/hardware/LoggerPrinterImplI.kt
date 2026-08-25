package zw.co.donnclab.calltape.hardware

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import zw.co.donnclab.calltape.data.CallRecord
import zw.co.donnclab.calltape.utils.ReceiptFormatter

object LoggerPrinterImplI : IPosPrinter {
    private val TAG = "CallTape-LogPrinter"
    private val _virtualPaperRoll = MutableStateFlow<List<String>>(emptyList())
    val virtualPaperRoll: StateFlow<List<String>> = _virtualPaperRoll.asStateFlow()

    override fun printLiveHeader(phoneNumber: String, simSlot: String, startTime: Long) {
        val header = ReceiptFormatter.buildLiveHeader(phoneNumber, simSlot, startTime)
        Log.d(TAG, "\n$header")
        appendToRoll(header)
    }

    override fun printLiveLine(formattedLine: String) {
        Log.d(TAG, formattedLine)
        appendToRoll(formattedLine)
    }

    override fun printLiveFooter(durationSeconds: Long) {
        val footer = ReceiptFormatter.buildLiveFooter(durationSeconds)
        Log.d(TAG, "\n$footer")
        appendToRoll(footer)
        cutPaper()
    }

    override fun printFullTranscript(record: CallRecord) {
        val hollywoodReceipt = ReceiptFormatter.buildFullReceipt(record)
        Log.d(TAG, "\n$hollywoodReceipt")
        appendToRoll(hollywoodReceipt)
        cutPaper()
    }

    override fun cutPaper() {
        val cutMark = "---------------- [PAPER CUT] ----------------"
        Log.d(TAG, cutMark)
        appendToRoll(cutMark)
    }

    private fun appendToRoll(text: String) {
        val current = _virtualPaperRoll.value.toMutableList()
        current.add(text)
        _virtualPaperRoll.value = current
    }
}
