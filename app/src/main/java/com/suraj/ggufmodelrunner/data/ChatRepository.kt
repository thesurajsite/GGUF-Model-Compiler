package com.suraj.ggufmodelrunner.data

import android.content.Context
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class ChatRepository(context: Context) {

    private val appContext = context.applicationContext
    private val bridge = LlamaBridge()

    // llama_context is NOT safe for concurrent native calls.
    // Route every load/generate/free through one dedicated thread
    // so calls from different coroutines never overlap.
    private val llamaDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "llama-native-thread")
    }.asCoroutineDispatcher()

    suspend fun initModel(
        assetName: String = "model.gguf",
        nCtx: Int = 2048,
        nThreads: Int = 4
    ): Result<Unit> = withContext(llamaDispatcher) {
        runCatching {
            val modelFile = copyAssetToFilesDirIfNeeded(assetName)
            val loaded = bridge.load(modelFile.absolutePath, nCtx, nThreads)
            check(loaded) { "Native loadModel() failed — check Logcat for details" }
        }
    }

    // General Format
//    suspend fun sendMessage(prompt: String, maxTokens: Int = 256): Result<String> =
//        withContext(llamaDispatcher) {
//            runCatching { bridge.generateText(prompt, maxTokens) }
//        }

    // Format for running Qwen Model
    suspend fun sendMessage(prompt: String, maxTokens: Int = 256): Result<String> =
        withContext(llamaDispatcher) {
            runCatching {
                val formattedPrompt = formatAsChatML(prompt)
                val raw = bridge.generateText(formattedPrompt, maxTokens)
                raw.substringBefore("<|im_end|>").trim() // cut off if model over-generates
            }
        }

    // Format for running Qwen Model
    private fun formatAsChatML(userMessage: String): String {
        return """
        |<|im_start|>system
        |You are a helpful assistant.<|im_end|>
        |<|im_start|>user
        |$userMessage<|im_end|>
        |<|im_start|>assistant
        |
    """.trimMargin()
    }

    fun isModelLoaded(): Boolean = bridge.isLoaded

    suspend fun close() {
        withContext(llamaDispatcher) { bridge.unload() }
        llamaDispatcher.close()
    }

    // Native code needs a real filesystem path, not an APK asset,
    // so copy the .gguf out of assets/ once, on first run.
    private fun copyAssetToFilesDirIfNeeded(assetName: String): File {
        val outFile = File(appContext.filesDir, assetName)
        val marker = File(appContext.filesDir, "$assetName.copied")

        if (outFile.exists() && marker.exists()) return outFile

        appContext.assets.open(assetName).use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output, bufferSize = 1 shl 20) // 1MB buffer
            }
        }
        marker.createNewFile()
        return outFile
    }
}