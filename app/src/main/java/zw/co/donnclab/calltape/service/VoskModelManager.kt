package zw.co.donnclab.calltape.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.vosk.Model
import org.vosk.SpeakerModel
import org.vosk.android.StorageService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// --- 4. Vosk Model Manager ---
object VoskModelManager {
    var mainModel: Model? = null
    var speakerModel: SpeakerModel? = null
    val isReady = MutableStateFlow(false)
    val loadingStatus = MutableStateFlow("Initializing...")

    fun init(context: Context) {
        if (isReady.value) return

        loadingStatus.value = "Unpacking Speech Model..."
        StorageService.unpack(context, "model-en-us", "model",
            { model ->
                mainModel = model
                loadingStatus.value = "Unpacking Speaker Model..."
                
                StorageService.unpack(context, "spk-model", "spk",
                    { spkModel ->
                        speakerModel = SpeakerModel(File(context.filesDir, "spk").absolutePath)
                        loadingStatus.value = "Ready"
                        isReady.value = true
                    },
                    { exception ->
                        Log.e("VoskManager", "Failed to load speaker model, continuing without diarization.", exception)
                        loadingStatus.value = "Ready (No Diarization)"
                        isReady.value = true
                    }
                )
            },
            { exception ->
                Log.e("VoskManager", "Critical: Failed to load main speech model.", exception)
                loadingStatus.value = "Failed to load models."
            }
        )
    }
}