package zw.co.donnclab.calltape.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Recognizer
import zw.co.donnclab.calltape.service.VoskModelManager
import zw.co.donnclab.calltape.utils.ReceiptFormatter
import kotlin.math.sqrt

private const val RECORDER_SAMPLE_RATE = 16_000
private const val RECORDER_TAG = "CallTape-Recorder"

/**
 * A deliberately simple microphone/Vosk diagnostic screen.
 *
 * It uses the plain MIC source, not call audio routing. If this screen cannot
 * transcribe a person speaking directly into the POS, call transcription will
 * not be debuggable yet.
 */
@Composable
fun RecorderScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modelsReady by VoskModelManager.isReady.collectAsState()
    val loadingStatus by VoskModelManager.loadingStatus.collectAsState()
    val model = VoskModelManager.mainModel

    var recordingJob by remember { mutableStateOf<Job?>(null) }
    var recorder by remember { mutableStateOf<AudioRecord?>(null) }
    var transcript by remember { mutableStateOf("") }
    var partialTranscript by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready") }
    var rms by remember { mutableFloatStateOf(0f) }
    var framesRead by remember { mutableIntStateOf(0) }

    fun stopRecording() {
        status = "Stopping..."
        try {
            recorder?.stop()
        } catch (_: IllegalStateException) {
            // The recorder may already have stopped because the activity left.
        }
        recorder?.release()
        recorder = null
        recordingJob?.cancel()
        recordingJob = null
        if (status == "Stopping...") status = "Stopped"
    }

    fun startRecording() {
        if (recordingJob != null) return
        Log.i(RECORDER_TAG, "Start requested")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(RECORDER_TAG, "RECORD_AUDIO permission is not granted")
            status = "RECORD_AUDIO permission is not granted"
            return
        }
        if (model == null) {
            Log.e(RECORDER_TAG, "Vosk model is not loaded")
            status = "Speech model is still loading"
            return
        }

        val minimumBuffer = AudioRecord.getMinBufferSize(
            RECORDER_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minimumBuffer <= 0) {
            Log.e(RECORDER_TAG, "Invalid AudioRecord minimum buffer: $minimumBuffer")
            status = "Device rejected 16 kHz microphone format ($minimumBuffer)"
            return
        }

        transcript = ""
        partialTranscript = ""
        rms = 0f
        framesRead = 0
        status = "Opening microphone..."

        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minimumBuffer * 2
            )
        } catch (error: Exception) {
            status = "AudioRecord construction failed: ${error.message ?: "unknown error"}"
            return
        }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(RECORDER_TAG, "AudioRecord rejected MIC source; state=${audioRecord.state}")
            audioRecord.release()
            status = "Microphone could not be initialized"
            return
        }

        recorder = audioRecord
        Log.i(RECORDER_TAG, "AudioRecord initialized: source=${audioRecord.audioSource}, buffer=$minimumBuffer")
        recordingJob = scope.launch(Dispatchers.IO) {
            val recognizer = Recognizer(model, RECORDER_SAMPLE_RATE.toFloat())
            val buffer = ShortArray(minimumBuffer / 2)
            var lastPcmLogAt = 0L
            var zeroReadCount = 0
            try {
                audioRecord.startRecording()
                Log.i(RECORDER_TAG, "Recording started")
                withContext(Dispatchers.Main) { status = "Recording microphone + transcribing" }

                while (isActive && audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read <= 0) {
                        zeroReadCount++
                        val now = System.currentTimeMillis()
                        if (now - lastPcmLogAt >= 1000) {
                            lastPcmLogAt = now
                            Log.w(RECORDER_TAG, "AudioRecord read returned $read; zeroReadCount=$zeroReadCount")
                        }
                        continue
                    }

                    var sum = 0.0
                    var peak = 0
                    var nonZeroSamples = 0
                    for (index in 0 until read) {
                        val rawSample = buffer[index].toInt()
                        if (rawSample != 0) nonZeroSamples++
                        peak = maxOf(peak, kotlin.math.abs(rawSample))
                        val sample = rawSample.toDouble()
                        sum += sample * sample
                    }
                    val level = sqrt(sum / read).toFloat()
                    val now = System.currentTimeMillis()
                    if (now - lastPcmLogAt >= 1000) {
                        lastPcmLogAt = now
                        Log.i(
                            RECORDER_TAG,
                            "PCM alive: read=$read, rms=${"%.1f".format(level)}, peak=$peak, nonZero=$nonZeroSamples/$read"
                        )
                    }

                    if (recognizer.acceptWaveForm(buffer, read)) {
                        val text = JSONObject(recognizer.result).optString("text").trim()
                        Log.i(RECORDER_TAG, "Vosk final: '$text'")
                        if (text.isNotEmpty()) {
                            val line = ReceiptFormatter.formatLiveLine("MIC", text)
                            withContext(Dispatchers.Main) {
                                transcript += line
                                partialTranscript = ""
                            }
                        }
                    } else {
                        val partial = JSONObject(recognizer.partialResult).optString("partial").trim()
                        if (partial.isNotEmpty()) {
                            Log.d(RECORDER_TAG, "Vosk partial: '$partial'")
                            withContext(Dispatchers.Main) { partialTranscript = partial }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        rms = level
                        framesRead += read
                    }
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    status = "Recording failed: ${error.message ?: "unknown error"}"
                }
            } finally {
                val finalText = JSONObject(recognizer.finalResult).optString("text").trim()
                Log.i(RECORDER_TAG, "Vosk final-on-stop: '$finalText'")
                if (finalText.isNotEmpty()) {
                    val line = ReceiptFormatter.formatLiveLine("MIC", finalText)
                    withContext(Dispatchers.Main) {
                        transcript += line
                        partialTranscript = ""
                    }
                }
                recognizer.close()
                withContext(Dispatchers.Main) {
                    recordingJob = null
                    if (status.startsWith("Recording")) status = "Stopped"
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                recorder?.stop()
            } catch (_: Exception) {
            }
            recorder?.release()
            recordingJob?.cancel()
        }
    }

    val isRecording = recordingJob != null
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Microphone Recorder", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Speak directly into the POS. This isolates microphone capture and Vosk from cellular-call routing.",
            style = MaterialTheme.typography.bodyMedium
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Status: $status")
                Text("Input level: ${"%.0f".format(rms)} / 32768")
                Text("Frames read: $framesRead")
                Text("Model: ${if (modelsReady && model != null) "loaded" else loadingStatus}")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(Color.DarkGray)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((rms / 32768f).coerceIn(0f, 1f))
                            .height(8.dp)
                            .background(if (rms > 100f) Color.Green else Color.Red)
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { startRecording() }, enabled = !isRecording) { Text("Start test") }
            OutlinedButton(onClick = { stopRecording() }, enabled = isRecording) { Text("Stop") }
        }

        Surface(modifier = Modifier.fillMaxWidth(), color = Color.DarkGray) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = transcript.ifEmpty { "Transcript output will appear here..." },
                    color = Color.Green,
                    fontFamily = FontFamily.Monospace
                )
                if (partialTranscript.isNotEmpty()) {
                    Text(
                        text = "\n[partial] $partialTranscript",
                        color = Color.Yellow,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
