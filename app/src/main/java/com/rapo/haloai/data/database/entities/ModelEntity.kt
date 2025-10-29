package com.rapo.haloai.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "installed_models")
data class ModelEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val format: ModelFormat, // ONNX or GGUF
    val sizeBytes: Long,
    val path: String,
    val status: ModelStatus,
    val tokensPerSecond: Int? = null,
    val parameters: String? = null, // e.g., "7B", "13B"
    val downloadedAt: Long = System.currentTimeMillis()
)

enum class ModelFormat {
    ONNX,
    GGUF,
    UNKNOWN
}

enum class ModelStatus {
    DOWNLOADING,
    INSTALLING,
    READY,
    ERROR
}
