package com.rapo.haloai.data.model

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages hardware acceleration for AI inference
 * Supports NNAPI, Vulkan, and device-specific accelerators
 */
@Singleton
class HardwareAccelerationManager @Inject constructor(
    private val context: Context
) {
    
    enum class AccelerationType {
        NNAPI,
        VULKAN,
        GPU_DIRECT,
        CPU,
        UNKNOWN
    }
    
    private val nnApiAvailable: Boolean by lazy {
        // Check if NNAPI is available
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
    }
    
    private val vulkanAvailable: Boolean by lazy {
        // Check if Vulkan is available
        try {
            Class.forName("android.hardware.vulkan.VulkanDevice")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }
    
    /**
     * Get the best available acceleration type
     */
    fun getBestAccelerationType(): AccelerationType {
        return when {
            vulkanAvailable -> AccelerationType.VULKAN
            nnApiAvailable -> AccelerationType.NNAPI
            else -> AccelerationType.CPU
        }
    }
    
    /**
     * Get device-specific acceleration info
     */
    fun getAccelerationInfo(): AccelerationInfo {
        val deviceName = android.os.Build.MODEL
        val sdkVersion = android.os.Build.VERSION.SDK_INT
        
        return AccelerationInfo(
            deviceName = deviceName,
            sdkVersion = sdkVersion,
            nnApiAvailable = nnApiAvailable,
            vulkanAvailable = vulkanAvailable,
            recommendedAcceleration = getBestAccelerationType()
        )
    }
    
    /**
     * Create ONNX session options with hardware acceleration
     */
    fun createONNXSessionOptions(): Map<String, Any> {
        return mutableMapOf<String, Any>().apply {
            when (getBestAccelerationType()) {
                AccelerationType.NNAPI -> {
                    put("executionProvider", "NNAPI")
                    put("useNNAPI", true)
                }
                AccelerationType.VULKAN -> {
                    put("executionProvider", "Vulkan")
                    put("useVulkanCompute", true)
                }
                else -> {
                    put("executionProvider", "CPU")
                }
            }
        }
    }
    
    data class AccelerationInfo(
        val deviceName: String,
        val sdkVersion: Int,
        val nnApiAvailable: Boolean,
        val vulkanAvailable: Boolean,
        val recommendedAcceleration: AccelerationType
    )
}
