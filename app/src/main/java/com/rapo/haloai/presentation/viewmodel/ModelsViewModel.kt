package com.rapo.haloai.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rapo.haloai.data.database.entities.ModelFormat
import com.rapo.haloai.data.model.HuggingFaceModelDownloader
import com.rapo.haloai.data.model.LocalModelImporter
import com.rapo.haloai.data.repository.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val modelDownloader: HuggingFaceModelDownloader,
    private val modelImporter: LocalModelImporter
) : ViewModel() {
    
    private val _models = MutableStateFlow<List<com.rapo.haloai.data.database.entities.ModelEntity>>(emptyList())
    val models = _models.asStateFlow()
    
    private val _downloadProgress = MutableStateFlow<Pair<String, Int>?>(null)
    val downloadProgress = _downloadProgress.asStateFlow()
    
    private val _importStatus = MutableStateFlow<String?>(null)
    val importStatus = _importStatus.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()
    
    init {
        loadModels()
    }
    
    private fun loadModels() {
        viewModelScope.launch {
            modelRepository.getAllModels().collect { modelList ->
                _models.value = modelList
            }
        }
    }
    
    fun downloadModel(modelId: String, fileName: String, format: ModelFormat) {
        viewModelScope.launch {
            try {
                _errorMessage.value = null
                modelDownloader.downloadModel(modelId, fileName, format)
                    .onCompletion { error ->
                        _downloadProgress.value = null
                        if (error != null) {
                            _errorMessage.value = "Download failed: ${error.message}"
                        } else {
                            _importStatus.value = "Model downloaded successfully"
                        }
                        loadModels()
                    }
                    .collect { progress ->
                        _downloadProgress.value = Pair(modelId, progress.progress)
                    }
            } catch (e: Exception) {
                _errorMessage.value = "Download error: ${e.message}"
                _downloadProgress.value = null
            }
        }
    }
    
    fun importModelFromPath(sourcePath: String) {
        viewModelScope.launch {
            try {
                _errorMessage.value = null
                val format = modelImporter.detectFormat(sourcePath)
                if (format == ModelFormat.UNKNOWN) {
                    _errorMessage.value = "Unsupported file format. Please use .gguf or .onnx files"
                    return@launch
                }
                
                val result = modelImporter.importModelFromPath(sourcePath, format)
                if (result.isSuccess) {
                    _importStatus.value = "Model imported: ${result.getOrNull()?.name}"
                    loadModels()
                } else {
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "Import failed"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Import error: ${e.message}"
            }
        }
    }
    
    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            val model = _models.value.find { it.id == modelId }
            if (model != null) {
                modelRepository.deleteModel(model)
            }
        }
    }
    
    fun getAvailableLocalModels(): List<File> {
        return modelImporter.getAvailableModels()
    }
    
    fun clearImportStatus() {
        _importStatus.value = null
    }
    
    fun clearDownloadProgress() {
        _downloadProgress.value = null
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}