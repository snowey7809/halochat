package com.rapo.haloai.data.model

import android.content.Context
import android.util.Log
import com.rapo.haloai.data.database.entities.ModelEntity
import com.rapo.haloai.data.database.entities.ModelFormat
import com.rapo.haloai.data.database.entities.ModelStatus
import com.rapo.haloai.data.repository.ModelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HuggingFaceModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepository: ModelRepository
) {
    private val TAG = "HuggingFaceDownloader"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val modelsDir = File(context.filesDir, "models").apply {
        if (!exists()) {
            mkdirs()
            Log.d(TAG, "Created models directory: $absolutePath")
        }
    }
    
    fun downloadModel(
        modelId: String,
        fileName: String,
        format: ModelFormat
    ): Flow<DownloadProgress> = flow {
        Log.d(TAG, "Starting download: $modelId / $fileName")
        
        val file = File(modelsDir, fileName)
        val uniqueId = "${System.currentTimeMillis()}_$modelId"
        val model = ModelEntity(
            id = uniqueId,
            name = fileName.substringBeforeLast("."),
            format = format,
            sizeBytes = 0L,
            path = file.absolutePath,
            status = ModelStatus.DOWNLOADING
        )
        
        try {
            // Insert model record
            withContext(Dispatchers.IO) {
                modelRepository.insertModel(model)
            }
            Log.d(TAG, "Model record created")
            
            val downloadUrl = getDownloadUrl(modelId, fileName)
            Log.d(TAG, "Download URL: $downloadUrl")
            
            val request = Request.Builder()
                .url(downloadUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
            
            val body = response.body ?: throw Exception("No response body")
            val contentLength = body.contentLength()
            
            if (contentLength <= 0) {
                throw Exception("Invalid content length: $contentLength")
            }
            
            Log.d(TAG, "File size: ${contentLength / (1024 * 1024)} MB")
            
            // Download with progress
            var totalRead = 0L
            var lastEmittedProgress = -1
            
            withContext(Dispatchers.IO) {
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            
                            val progress = ((totalRead * 100) / contentLength).toInt()
                            
                            // Emit only when progress changes
                            if (progress != lastEmittedProgress) {
                                emit(DownloadProgress(progress, totalRead, contentLength))
                                lastEmittedProgress = progress
                                
                                // Update DB every 5%
                                if (progress % 5 == 0) {
                                    modelRepository.updateModel(
                                        model.copy(
                                            sizeBytes = contentLength,
                                            status = ModelStatus.DOWNLOADING
                                        )
                                    )
                                }
                            }
                        }
                        
                        output.flush()
                    }
                }
            }
            
            Log.d(TAG, "Download finished. File size: ${file.length()} bytes")
            
            // Verify download
            if (!file.exists()) {
                throw Exception("File does not exist after download")
            }
            
            if (file.length() == 0L) {
                throw Exception("Downloaded file is empty (0 bytes)")
            }
            
            if (file.length() != contentLength) {
                Log.w(TAG, "File size mismatch: expected $contentLength, got ${file.length()}")
            }
            
            // Mark as ready
            withContext(Dispatchers.IO) {
                modelRepository.updateModel(
                    model.copy(
                        sizeBytes = file.length(),
                        status = ModelStatus.READY,
                        path = file.absolutePath
                    )
                )
            }
            
            emit(DownloadProgress(100, file.length(), file.length()))
            Log.d(TAG, "Model installed: ${file.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            
            // Cleanup
            withContext(Dispatchers.IO) {
                try {
                    if (file.exists()) {
                        val deleted = file.delete()
                        Log.d(TAG, "Cleanup: deleted=$deleted")
                    }
                    modelRepository.updateModel(model.copy(status = ModelStatus.ERROR))
                } catch (ce: Exception) {
                    Log.e(TAG, "Cleanup failed", ce)
                }
            }
            
            throw Exception("Download failed: ${e.message}")
        }
    }
    
    private suspend fun getDownloadUrl(modelId: String, fileName: String): String {
        return "https://huggingface.co/$modelId/resolve/main/$fileName"
    }
}

data class DownloadProgress(
    val progress: Int,
    val bytesDownloaded: Long,
    val totalBytes: Long
)
