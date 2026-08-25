package zw.co.donnclab.calltape.hardware

interface PosPrinter {
    fun printLine(text: String)
    fun printFullTranscript(transcript: String)
    fun cutPaper()
}
