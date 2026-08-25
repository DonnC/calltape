package zw.co.donnclab.calltape.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import org.vosk.Model
import org.vosk.SpeakerModel
import org.vosk.android.StorageService
import java.io.File
import java.io.FileOutputStream

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

                // Unpack Speaker Model Manually to bypass the StorageService crash
                try {
                    val spkDir = File(context.filesDir, "spk-model")
                    copyAssetFolder(context, "spk-model", spkDir.absolutePath)

                    // Initialize it properly as a SpeakerModel
                    speakerModel = SpeakerModel(spkDir.absolutePath)
                    Log.d("VoskManager", "Speaker model loaded successfully.")
                    loadingStatus.value = "Ready"
                } catch (e: Exception) {
                    Log.e("VoskManager", "Failed to load speaker model, continuing without diarization.", e)
                    loadingStatus.value = "Ready (No Diarization)"
                } finally {
                    isReady.value = true
                }
            },
            { exception ->
                Log.e("VoskManager", "Critical: Failed to load main speech model.", exception)
                loadingStatus.value = "Failed to load models."
            }
        )
    }

    /**
     * Helper function to manually copy the speaker model from the APK assets
     * into the device's internal storage without triggering the Vosk C++ crash.
     */
    private fun copyAssetFolder(context: Context, assetPath: String, destPath: String) {
        val assets = context.assets
        val destFile = File(destPath)

        val files = assets.list(assetPath)
        if (files.isNullOrEmpty()) {
            assets.open(assetPath).use { inStream ->
                FileOutputStream(destFile).use { outStream ->
                    inStream.copyTo(outStream)
                }
            }
        } else {
            destFile.mkdirs()
            for (file in files) {
                copyAssetFolder(context, "$assetPath/$file", "$destPath/$file")
            }
        }
    }
}