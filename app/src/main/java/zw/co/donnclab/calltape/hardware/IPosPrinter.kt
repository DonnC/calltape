package zw.co.donnclab.calltape.hardware

import zw.co.donnclab.calltape.data.CallRecord

interface IPosPrinter {
    fun printLiveHeader(phoneNumber: String, simSlot: String, startTime: Long)
    fun printLiveLine(formattedLine: String)
    fun printLiveFooter(durationSeconds: Long)

    fun printFullTranscript(record: CallRecord)
    fun cutPaper()
}
