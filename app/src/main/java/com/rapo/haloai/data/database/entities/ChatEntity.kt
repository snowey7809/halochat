package com.rapo.haloai.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val modelId: String? = null,
    val responseTimeMs: Long? = null, // Time taken to generate response
    val tokenCount: Int? = null, // Number of tokens generated
    val tokensPerSecond: Float? = null, // Tokens/sec performance metric
    val stopReason: String? = null // Why generation stopped (EOS, max_tokens, error)
)
