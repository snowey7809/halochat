package com.rapo.haloai.data.database.dao

import androidx.room.*
import com.rapo.haloai.data.database.entities.ChatEntity
import com.rapo.haloai.data.database.entities.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesBySession(sessionId: String): Flow<List<ChatEntity>>
    
    @Query("""
        SELECT DISTINCT sessionId 
        FROM chat_messages 
        ORDER BY (
            SELECT MAX(timestamp) 
            FROM chat_messages m2 
            WHERE m2.sessionId = chat_messages.sessionId
        ) DESC
    """)
    fun getAllSessions(): Flow<List<String>>
    
    @Insert
    suspend fun insertMessage(message: ChatEntity): Long
    
    @Update
    suspend fun updateMessage(message: ChatEntity)
    
    @Delete
    suspend fun deleteMessage(message: ChatEntity)
    
    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)
    
    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()
    
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(sessionId: String): ChatEntity?
    
    // Session management
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)
    
    @Query("SELECT * FROM chat_sessions ORDER BY lastMessageAt DESC")
    fun getAllSessions2(): Flow<List<ChatSessionEntity>>
    
    @Query("SELECT * FROM chat_sessions WHERE sessionId = :sessionId")
    suspend fun getSession(sessionId: String): ChatSessionEntity?
    
    @Query("DELETE FROM chat_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession2(sessionId: String)
    
    @Query("UPDATE chat_sessions SET lastMessageAt = :timestamp WHERE sessionId = :sessionId")
    suspend fun updateSessionTimestamp(sessionId: String, timestamp: Long)
}
