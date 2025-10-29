package com.rapo.haloai.data.model

import com.rapo.haloai.data.database.entities.ModelEntity
import com.rapo.haloai.data.database.entities.ModelFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central manager for model operations
 */
@Singleton
class ModelManager @Inject constructor(
    private val onnxRuntime: ONNXModelRuntime,
    private val ggufRuntime: GGUFModelRuntime
) {
    
    private var currentRuntime: ModelRuntime? = null
    
    fun getRuntimeForModel(model: ModelEntity): ModelRuntime {
        return when (model.format) {
            ModelFormat.ONNX -> onnxRuntime
            ModelFormat.GGUF -> ggufRuntime
            else -> throw IllegalArgumentException("Unsupported model format: ${model.format}")
        }
    }
    
    suspend fun loadModel(model: ModelEntity): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // Release previous runtime
                currentRuntime?.release()
                
                val runtime = getRuntimeForModel(model)
                val result = runtime.initializeModel(model)
                
                if (result.isSuccess) {
                    currentRuntime = runtime
                }
                
                result
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    fun getCurrentRuntime(): ModelRuntime? = currentRuntime
    
    suspend fun unloadModel() {
        currentRuntime?.release()
        currentRuntime = null
    }
}
