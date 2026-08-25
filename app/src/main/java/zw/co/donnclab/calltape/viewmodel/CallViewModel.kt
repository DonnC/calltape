package zw.co.donnclab.calltape.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import zw.co.donnclab.calltape.data.CallRecord
import zw.co.donnclab.calltape.data.CallRepository
import zw.co.donnclab.calltape.hardware.MockPrinterImpl
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CallViewModel : ViewModel() {
    val callHistory: StateFlow<List<CallRecord>> = CallRepository.callHistory

    fun clearHistory() {
        CallRepository.clearHistory()
    }

    fun printTranscript(record: CallRecord) {
        viewModelScope.launch {
            MockPrinterImpl.printFullTranscript(record.transcript)
            MockPrinterImpl.cutPaper()
        }
    }
}
