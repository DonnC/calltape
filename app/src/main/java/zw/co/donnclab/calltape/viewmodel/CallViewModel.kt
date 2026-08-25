package zw.co.donnclab.calltape.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zw.co.donnclab.calltape.data.CallRecord
import zw.co.donnclab.calltape.data.CallRepository
import zw.co.donnclab.calltape.hardware.LoggerPrinterImplI

class CallViewModel : ViewModel() {
    val callHistory: StateFlow<List<CallRecord>> = CallRepository.callHistory

    private val _selectedSimSlot = MutableStateFlow(1)
    val selectedSimSlot: StateFlow<Int> = _selectedSimSlot.asStateFlow()

    fun selectSimSlot(slot: Int) {
        _selectedSimSlot.value = slot
    }

    fun clearHistory() {
        CallRepository.clearHistory()
    }

    fun printTranscript(record: CallRecord) {
        viewModelScope.launch {
            LoggerPrinterImplI.printFullTranscript(record)
        }
    }
}