package com.rapo.haloai.data.model

import android.content.Context
import com.rapo.haloai.data.database.entities.ModelEntity
import com.rapo.haloai.data.database.entities.ModelFormat
import com.rapo.haloai.data.database.entities.ModelStatus
import com.rapo.haloai.data.repository.ModelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Import models from local storage (SD card, Downloads folder, etc.)
 */
@Singleton
class LocalModelImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepository: ModelRepository
) {
    
    private val modelsDir = File(context.filesDir, "models")
    
    init {
        modelsDir.mkdirs()
    }
    
    /**
     * Import a model file from an external path (like Downloads or SD card)
     */
    suspend fun importModelFromPath(
        sourcePath: String,
        format: ModelFormat
    ): Result<ModelEntity> = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(sourcePath)
            
            if (!sourceFile.exists()) {
                return@withContext Result.failure(Exception("Source file does not exist: $sourcePath"))
            }
            
            if (!sourceFile.canRead()) {
                return@withContext Result.failure(Exception("Cannot read source file"))
            }
            
            // Use the source file directly if it's already in our models directory
            val fileName = sourceFile.name
            val destFile = if (sourceFile.parent == modelsDir.absolutePath) {
                sourceFile
            } else {
                // Copy to app's models directory
                val dest = File(modelsDir, fileName)
                if (dest.exists()) {
                    dest.delete() // Delete existing file to allow re-import
                }
                sourceFile.copyTo(dest, overwrite = true)
                dest
            }
            
            if (!destFile.exists() || destFile.length() == 0L) {
                return@withContext Result.failure(Exception("Failed to copy file or file is empty"))
            }
            
            // Create model entity with unique ID
            val modelId = "${System.currentTimeMillis()}_$fileName"
            val model = ModelEntity(
                id = modelId,
                name = fileName.substringBeforeLast("."),
                format = format,
                sizeBytes = destFile.length(),
                path = destFile.absolutePath,
                status = ModelStatus.READY
            )
            
            // Save to database
            modelRepository.insertModel(model)
            
            Result.success(model)
            
        } catch (e: Exception) {
            Result.failure(Exception("Import failed: ${e.message}"))
        }
    }
    
    /**
     * Import model from internal app storage
     */
    suspend fun importModelFromInternalStorage(
        fileName: String,
        format: ModelFormat
    ): Result<ModelEntity> {
        val modelsDir = File(context.filesDir, "models")
        val file = File(modelsDir, fileName)
        
        return importModelFromPath(file.absolutePath, format)
    }
    
    /**
     * List available models in the models directory
     */
    fun getAvailableModels(): List<File> {
        return modelsDir.listFiles { _, name ->
            name.endsWith(".onnx", ignoreCase = true) || 
            name.endsWith(".gguf", ignoreCase = true) ||
            name.endsWith(".ggml", ignoreCase = true)
        }?.toList() ?: emptyList()
    }
    
    /**
     * Detect model format from file extension
     */
    fun detectFormat(fileName: String): ModelFormat {
        return when {
            fileName.endsWith(".onnx", ignoreCase = true) -> ModelFormat.ONNX
            fileName.endsWith(".gguf", ignoreCase = true) -> ModelFormat.GGUF
            fileName.endsWith(".ggml", ignoreCase = true) -> ModelFormat.GGUF
            else -> ModelFormat.UNKNOWN
        }
    }
    
    /**
     * Get model file size in human readable format
     */
    fun formatModelSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}
