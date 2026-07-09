package com.suraj.ggufmodelrunner.data

class LlamaBridge {

    companion object {
        init {
            System.loadLibrary("llama-android") // loads libllama-android.so
        }
    }

    private var sessionPtr: Long = 0L

    // --- Native (JNI) methods — names/signatures must match llama-android.cpp exactly ---
    private external fun loadModel(modelPath: String, nCtx: Int, nThreads: Int): Long
    private external fun generate(sessionPtr: Long, prompt: String, maxTokens: Int): String
    private external fun freeModel(sessionPtr: Long)

    // --- Kotlin-friendly wrappers ---
    fun load(modelPath: String, nCtx: Int = 2048, nThreads: Int = 4): Boolean {
        if (sessionPtr != 0L) unload()
        sessionPtr = loadModel(modelPath, nCtx, nThreads)
        return sessionPtr != 0L
    }

    fun generateText(prompt: String, maxTokens: Int = 256): String {
        check(sessionPtr != 0L) { "Model not loaded. Call load() first." }
        return generate(sessionPtr, prompt, maxTokens)
    }

    fun unload() {
        if (sessionPtr != 0L) {
            freeModel(sessionPtr)
            sessionPtr = 0L
        }
    }

    val isLoaded: Boolean
        get() = sessionPtr != 0L
}