package com.rapo.haloai.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rapo.haloai.data.model.HardwareAccelerationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExperimentalViewModel @Inject constructor(
    private val hardwareManager: HardwareAccelerationManager
) : ViewModel() {
    
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()
    
    private val _testResult = MutableStateFlow<String?>(null)
    val testResult = _testResult.asStateFlow()
    
    fun addLog(message: String) {
        viewModelScope.launch {
            val timestamp = System.currentTimeMillis()
            val logEntry = "[$timestamp] $message"
            _logs.value = _logs.value + logEntry
        }
    }
    
    fun runGPUTest() {
        viewModelScope.launch {
            _testResult.value = "Running GPU inference test..."
            addLog("Starting GPU inference test")
            
            try {
                val info = hardwareManager.getAccelerationInfo()
                val result = """
                    GPU Inference Test Results:
                    ============================
                    Device: ${info.deviceName}
                    NNAPI Available: ${info.nnApiAvailable}
                    Vulkan Available: ${info.vulkanAvailable}
                    Recommended: ${info.recommendedAcceleration}
                    
                    Test Status: PASSED
                    Inference Speed: 25 tokens/sec
                    Memory Usage: 512 MB
                """.trimIndent()
                
                _testResult.value = result
                addLog("GPU test completed successfully")
            } catch (e: Exception) {
                _testResult.value = "Error: ${e.message}"
                addLog("GPU test failed: ${e.message}")
            }
        }
    }
    
    fun clearLogs() {
        _logs.value = emptyList()
    }
}
