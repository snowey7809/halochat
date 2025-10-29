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
    
    // Expose metrics
    fun getGenerationSpeed(): Float {
        return if (isModelLoaded && modelHandle != 0L) {
            getResponseGenerationSpeed(modelHandle)
        } else 0f
    }
    
    fun getContextUsage(): Int {
        return if (isModelLoaded && modelHandle != 0L) {
            getContextSizeUsed(modelHandle)
        } else 0
    }

    private external fun getModelMetadata(modelPath: String): ModelMetadata
    private external fun initModel(modelPath: String, threads: Int, contextLength: Int): Long
    private external fun addChatMessage(handle: Long, message: String, role: String)
    private external fun startCompletion(handle: Long, prompt: String)
    private external fun completionLoop(handle: Long): String
    private external fun stopCompletion(handle: Long)
    private external fun getResponseGenerationSpeed(handle: Long): Float
    private external fun getContextSizeUsed(handle: Long): Int
    private external fun freeModel(handle: Long)
    
    // Public method to read model metadata before loading
    fun readMetadata(modelPath: String): ModelMetadata {
        return getModelMetadata(modelPath)
    }

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
                
                if (!isModelLoaded || modelHandle == 0L) {
                    Log.e(TAG, "Model not initialized!")
                    throw IllegalStateException("Model not initialized")
                }
                
                // Start completion
                startCompletion(modelHandle, prompt)
                Log.d(TAG, "Completion started")
                
                var tokenCount = 0
                
                // Generation loop - call completionLoop until EOS
                while (tokenCount < maxTokens) {
                    val piece = completionLoop(modelHandle)
                    
                    // Check for special markers
                    when (piece) {
                        "[EOG]" -> {
                            Log.d(TAG, "End of generation at $tokenCount tokens")
                            break
                        }
                        "[ERROR]" -> {
                            Log.e(TAG, "Generation error")
                            throw IllegalStateException("Generation error")
                        }
                        else -> {
                            if (piece.isNotEmpty()) {
                                trySend(piece).isSuccess
                            }
                        }
                    }
                    
                    tokenCount++
                    
                    if (tokenCount % 20 == 0) {
                        Log.d(TAG, "Generated $tokenCount tokens so far")
                    }
                }
                
                // Stop completion
                stopCompletion(modelHandle)
                
                val speed = getResponseGenerationSpeed(modelHandle)
                val contextUsed = getContextSizeUsed(modelHandle)
                Log.d(TAG, "Generation complete: $tokenCount tokens, $speed tok/s, context: $contextUsed")
                
                close()
            } catch (e: Exception) {
                Log.e(TAG, "Error in generateResponse", e)
                try {
                    stopCompletion(modelHandle)
                } catch (ignored: Exception) {}
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
