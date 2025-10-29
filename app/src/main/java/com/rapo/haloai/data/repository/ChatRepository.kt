package com.rapo.haloai.data.repository

import com.rapo.haloai.data.database.dao.ChatDao
import com.rapo.haloai.data.database.entities.ChatEntity
import com.rapo.haloai.data.database.entities.ChatSessionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao
) {
    
    fun getMessages(sessionId: String): Flow<List<ChatEntity>> {
        return chatDao.getMessagesBySession(sessionId)
    }
    
    suspend fun insertMessage(message: ChatEntity): Long {
        return chatDao.insertMessage(message)
    }
    
    suspend fun updateMessage(message: ChatEntity) {
        chatDao.updateMessage(message)
    }
    
    suspend fun deleteMessage(message: ChatEntity) {
        chatDao.deleteMessage(message)
    }
    
    suspend fun deleteSession(sessionId: String) {
        chatDao.deleteSession(sessionId)
    }
    
    suspend fun deleteAllMessages() {
        chatDao.deleteAllMessages()
    }
    
    suspend fun getLastMessage(sessionId: String): ChatEntity? {
        return chatDao.getLastMessage(sessionId)
    }
    
    // Session management
    suspend fun insertSession(session: ChatSessionEntity) {
        chatDao.insertSession(session)
    }
    
    fun getAllSessions(): Flow<List<ChatSessionEntity>> {
        return chatDao.getAllSessions2()
    }
    
    suspend fun getSession(sessionId: String): ChatSessionEntity? {
        return chatDao.getSession(sessionId)
    }
    
    suspend fun deleteSessionWithMessages(sessionId: String) {
        chatDao.deleteSession(sessionId) // Delete messages
        chatDao.deleteSession2(sessionId) // Delete session record
    }
    
    suspend fun updateSessionTimestamp(sessionId: String) {
        chatDao.updateSessionTimestamp(sessionId, System.currentTimeMillis())
    }
}
