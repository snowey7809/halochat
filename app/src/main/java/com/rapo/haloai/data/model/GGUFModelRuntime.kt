package com.rapo.haloai.data.model

import android.util.Log
import com.rapo.haloai.data.database.entities.ModelEntity
import com.rapo.haloai.data.database.entities.ModelFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GGUFModelRuntime @Inject constructor() : ModelRuntime {

    private var isModelLoaded = false
    private var modelPath: String? = null
    private var modelHandle: Long = 0
    
    var threads: Int = 4
    var contextLength: Int = 1535

    private external fun initModel(modelPath: String, threads: Int, contextLength: Int): Long
    private external fun generateText(handle: Long, prompt: String, maxTokens: Int, callback: (String) -> Unit)
    private external fun freeModel(handle: Long)

    companion object {
        private const val TAG = "GGUFModelRuntime"
        init {
            try {
                System.loadLibrary("haloai_native")
                Log.i(TAG, "Successfully loaded HaloAI native library with llama.cpp support.")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load HaloAI native library: ${e.message}")
                Log.e(TAG, "GGUF models will not be available.")
            }
        }
    }

    override suspend fun initializeModel(model: ModelEntity): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "initializeModel called")
                Log.d(TAG, "Model format: ${model.format}")
                Log.d(TAG, "Model path: ${model.path}")
                
                if (model.format != ModelFormat.GGUF) {
                    Log.e(TAG, "Invalid format: ${model.format}")
                    return@withContext Result.failure(IllegalArgumentException("Model format is not GGUF"))
                }
                
                // Check if file exists
                val file = java.io.File(model.path)
                if (!file.exists()) {
                    Log.e(TAG, "Model file does not exist: ${model.path}")
                    return@withContext Result.failure(RuntimeException("Model file not found: ${model.path}"))
                }
                
                if (!file.canRead()) {
                    Log.e(TAG, "Cannot read model file: ${model.path}")
                    return@withContext Result.failure(RuntimeException("Cannot read model file"))
                }
                
                Log.d(TAG, "File exists and readable, size: ${file.length()} bytes")
                Log.d(TAG, "Calling native initModel with threads=$threads, context=$contextLength...")
                
                modelHandle = initModel(model.path, threads, contextLength)
                
                Log.d(TAG, "Native initModel returned handle: $modelHandle")
                
                if (modelHandle == 0L) {
                    Log.e(TAG, "initModel returned 0 (failed)")
                    return@withContext Result.failure(RuntimeException("Failed to initialize GGUF model - check native logs"))
                }
                
                modelPath = model.path
                isModelLoaded = true
                Log.d(TAG, "Model initialized successfully")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Exception in initializeModel", e)
                Result.failure(e)
            }
        }
    }

    override fun generateResponse(prompt: String, maxTokens: Int): Flow<String> {
        return callbackFlow {
            try {
                Log.d(TAG, "generateResponse called")
                Log.d(TAG, "isModelLoaded: $isModelLoaded, handle: $modelHandle")
                
                if (!isModelLoaded || modelHandle == 0L) {
                    Log.e(TAG, "Model not initialized!")
                    throw IllegalStateException("Model not initialized")
                }
                
                // Use the prompt as-is from ViewModel (already formatted)
                // ViewModel handles conversation history and role formatting
                val fullPrompt = prompt
                val limitedTokens = maxTokens
                
                Log.d(TAG, "Calling native generateText with maxTokens=$limitedTokens (requested: $maxTokens)")
                
                var tokenCount = 0
                generateText(modelHandle, fullPrompt, limitedTokens) { token ->
                    tokenCount++
                    if (tokenCount % 20 == 0) {
                        Log.d(TAG, "Generated $tokenCount tokens so far")
                    }
                    trySend(token).isSuccess
                }
                
                Log.d(TAG, "Native generateText completed with $tokenCount tokens")
                close()
            } catch (e: Exception) {
                Log.e(TAG, "Error in generateResponse", e)
                close(e)
            }
        }.flowOn(Dispatchers.Default)
    }

    override suspend fun stopGeneration() { /* No-op */ }

    override suspend fun release() {
        withContext(Dispatchers.IO) {
            if (modelHandle != 0L) {
                freeModel(modelHandle)
                modelHandle = 0L
            }
            isModelLoaded = false
            modelPath = null
        }
    }

    override fun getPerformanceMetrics(): PerformanceMetrics {
        return PerformanceMetrics(0f, 0L, 0L, "GGUF")
    }

    override fun isReady(): Boolean = isModelLoaded
}
