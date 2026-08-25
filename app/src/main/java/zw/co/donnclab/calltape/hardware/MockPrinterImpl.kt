package zw.co.donnclab.calltape.hardware

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MockPrinterImpl : PosPrinter {
    private val TAG = "CallTape-MockPrinter"
    private val _virtualPaperRoll = MutableStateFlow<List<String>>(emptyList())
    val virtualPaperRoll: StateFlow<List<String>> = _virtualPaperRoll.asStateFlow()

    override fun printLine(text: String) {
        Log.d(TAG, "[PRINT LINE]: $text")
        val current = _virtualPaperRoll.value.toMutableList()
        current.add(text)
        _virtualPaperRoll.value = current
    }

    override fun printFullTranscript(transcript: String) {
        Log.d(TAG, "================ [PRINT RECEIPT] ================")
        Log.d(TAG, transcript)
        Log.d(TAG, "=================================================")
        cutPaper()
    }

    override fun cutPaper() {
        Log.d(TAG, "---------------- [PAPER CUT] ----------------")
    }
}
