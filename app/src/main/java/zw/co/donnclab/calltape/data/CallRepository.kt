package zw.co.donnclab.calltape.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CallRepository {
    private val _callHistory = MutableStateFlow<List<CallRecord>>(emptyList())
    val callHistory: StateFlow<List<CallRecord>> = _callHistory.asStateFlow()

    var currentSimSlot: Int = 1

    fun addRecord(record: CallRecord) {
        val current = _callHistory.value.toMutableList()
        current.add(0, record)
        _callHistory.value = current
    }

    fun clearHistory() {
        _callHistory.value = emptyList()
    }
}
