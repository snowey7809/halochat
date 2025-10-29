package com.rapo.haloai.data.model

import com.rapo.haloai.data.database.entities.ModelEntity
import kotlinx.coroutines.flow.Flow

/**
 * Runtime interface for executing AI models
 */
interface ModelRuntime {
    
    /**
     * Initialize a model for inference
     */
    suspend fun initializeModel(model: ModelEntity): Result<Unit>
    
    /**
     * Generate response from a prompt
     * @param prompt Input text prompt
     * @return Flow of tokens as they are generated
     */
    fun generateResponse(prompt: String, maxTokens: Int = 512): Flow<String>
    
    /**
     * Stop the current generation
     */
    suspend fun stopGeneration()
    
    /**
     * Release model resources
     */
    suspend fun release()
    
    /**
     * Get performance metrics
     */
    fun getPerformanceMetrics(): PerformanceMetrics
    
    /**
     * Check if model is loaded and ready
     */
    fun isReady(): Boolean
}

data class PerformanceMetrics(
    val tokensPerSecond: Float,
    val averageLatency: Long,
    val memoryUsageMB: Long,
    val hardwareAccelerator: String? = null
)
