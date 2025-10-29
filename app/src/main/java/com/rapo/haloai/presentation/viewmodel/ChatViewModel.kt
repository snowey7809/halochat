package com.rapo.haloai.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rapo.haloai.data.database.entities.ChatEntity
import com.rapo.haloai.data.database.entities.ChatSessionEntity
import com.rapo.haloai.data.database.entities.ModelEntity
import java.util.UUID
import com.rapo.haloai.data.model.ModelManager
import com.rapo.haloai.data.model.ModelRuntime
import com.rapo.haloai.data.repository.ChatRepository
import com.rapo.haloai.data.repository.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val modelManager: ModelManager
) : ViewModel() {
    
    private val _messages = MutableStateFlow<List<ChatEntity>>(emptyList())
    val messages = _messages.asStateFlow()
    
    private val _currentMessage = MutableStateFlow("")
    val currentMessage = _currentMessage.asStateFlow()
    
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()
    
    private val _streamedResponse = MutableStateFlow("")
    val streamedResponse = _streamedResponse.asStateFlow()
    
    private val _selectedModel = MutableStateFlow<ModelEntity?>(null)
    val selectedModel = _selectedModel.asStateFlow()
    
    private val _models = MutableStateFlow<List<ModelEntity>>(emptyList())
    val models = _models.asStateFlow()
    
    private val _generationSettings = MutableStateFlow(GenerationSettings())
    val generationSettings = _generationSettings.asStateFlow()
    
    private val _showReloadModelDialog = MutableStateFlow(false)
    val showReloadModelDialog = _showReloadModelDialog.asStateFlow()
    
    private var pendingSettingsChange: (() -> Unit)? = null
    
    private var generationJob: kotlinx.coroutines.Job? = null
    
    // Real-time metrics
    private val _generationSpeed = MutableStateFlow(0f)
    val generationSpeed = _generationSpeed.asStateFlow()
    
    private val _contextUsed = MutableStateFlow(0)
    val contextUsed = _contextUsed.asStateFlow()
    
    // Session management
    private val _currentSessionId = MutableStateFlow(UUID.randomUUID().toString())
    val currentSessionId = _currentSessionId.asStateFlow()
    
    private val _sessions = MutableStateFlow<List<ChatSessionEntity>>(emptyList())
    val sessions = _sessions.asStateFlow()
    
    private var currentRuntime: ModelRuntime? = null
    
    companion object {
        private const val TAG = "ChatViewModel"
    }
    
    init {
        loadChatHistory()
        loadModels()
        loadSessions()
        createInitialSession()
    }
    
    private fun loadChatHistory() {
        viewModelScope.launch {
            _currentSessionId.collect { sessionId ->
                chatRepository.getMessages(sessionId).collect { messageList ->
                    _messages.value = messageList
                }
            }
        }
    }
    
    private fun loadSessions() {
        viewModelScope.launch {
            chatRepository.getAllSessions().collect { sessionList ->
                _sessions.value = sessionList
            }
        }
    }
    
    private fun createInitialSession() {
        viewModelScope.launch {
            val sessionId = _currentSessionId.value
            if (chatRepository.getSession(sessionId) == null) {
                chatRepository.insertSession(
                    ChatSessionEntity(
                        sessionId = sessionId,
                        title = "New Chat"
                    )
                )
            }
        }
    }
    
    private fun loadModels() {
        viewModelScope.launch {
            modelRepository.getAllModels().collect { modelList ->
                _models.value = modelList
                if (_selectedModel.value == null) {
                    _selectedModel.value = modelList.firstOrNull { it.status.name == "READY" }
                }
            }
        }
    }
    
    fun updateMessage(text: String) {
        _currentMessage.value = text
    }
    
    fun sendMessage() {
        val message = _currentMessage.value.trim()
        if (message.isEmpty() || _isGenerating.value) return
        
        val model = _selectedModel.value
        if (model == null) {
            return
        }
        
        viewModelScope.launch {
            val sessionId = _currentSessionId.value
            
            // Save user message
            val userMessage = ChatEntity(
                sessionId = sessionId,
                role = "user",
                content = message
            )
            chatRepository.insertMessage(userMessage)
            
            // Auto-generate title for first message
            if (_messages.value.isEmpty()) {
                val title = generateSessionTitle(message)
                chatRepository.insertSession(
                    ChatSessionEntity(
                        sessionId = sessionId,
                        title = title
                    )
                )
            } else {
                // Update session timestamp
                chatRepository.updateSessionTimestamp(sessionId)
            }
            
            _currentMessage.value = ""
            _isGenerating.value = true
            _streamedResponse.value = ""
            
            // Cancel any existing generation
            generationJob?.cancel()
            
            // Start new generation with job tracking
            generationJob = viewModelScope.launch {
                generateAIResponse(message, model)
            }
        }
    }
    
    private suspend fun generateAIResponse(prompt: String, model: ModelEntity) {
        try {
            Log.d(TAG, "Starting generation with model: ${model.name}")
            Log.d(TAG, "Model path: ${model.path}")
            
            // Load model if not already loaded
            val runtime = modelManager.getRuntimeForModel(model)
            if (!runtime.isReady()) {
                Log.d(TAG, "Loading model...")
                
                // Apply settings to runtime if it's GGUF
                if (runtime is com.rapo.haloai.data.model.GGUFModelRuntime) {
                    runtime.threads = _generationSettings.value.threads
                    runtime.contextLength = _generationSettings.value.contextLength
                    Log.d(TAG, "Applied settings: threads=${runtime.threads}, context=${runtime.contextLength}")
                }
                
                val loadResult = modelManager.loadModel(model)
                if (loadResult.isFailure) {
                    val error = loadResult.exceptionOrNull()
                    Log.e(TAG, "Model load failed", error)
                    throw Exception("Failed to load model: ${error?.message}")
                }
                Log.d(TAG, "Model loaded successfully")
            }
            
            val currentRuntime = modelManager.getCurrentRuntime()
            if (currentRuntime == null) {
                Log.e(TAG, "Runtime is null after loading")
                throw Exception("Model runtime not available")
            }
            
            // Generate response with streaming and track performance
            Log.d(TAG, "Starting generation...")
            val startTime = System.currentTimeMillis()
            var assistantMessage = ""
            var tokenCount = 0
            val maxTokens = _generationSettings.value.maxTokens
            var stopReason = "unknown"
            
            // Build conversation history for context-aware responses
            val recentMessages = _messages.value.takeLast(5) // Last 5 messages for context
            
            // Build prompt with system message and conversation history
            val systemPrompt = _generationSettings.value.systemPrompt
            val fullPrompt = buildString {
                // Add system prompt first
                if (systemPrompt.isNotBlank()) {
                    append(systemPrompt)
                    append("\n\n")
                }
                
                // Add conversation history in Q&A format
                if (recentMessages.isNotEmpty()) {
                    recentMessages.forEach { msg ->
                        when (msg.role) {
                            "user" -> {
                                append("Q: ${msg.content}\n")
                            }
                            "assistant" -> {
                                // Remove metadata footer if present
                                val content = msg.content.substringBefore("\n\n---\n")
                                append("A: $content\n\n")
                            }
                        }
                    }
                }
                
                // Add current user question
                append("Q: $prompt\n")
                append("A:")
            }
            
            Log.d(TAG, "Full prompt: ${fullPrompt.length} chars, includes ${recentMessages.size} previous messages, max tokens: $maxTokens")
            
            currentRuntime.generateResponse(fullPrompt, maxTokens = maxTokens)
                .catch { e ->
                    if (e is kotlinx.coroutines.CancellationException) {
                        Log.d(TAG, "Generation cancelled by user")
                        stopReason = "cancelled"
                        throw e
                    } else {
                        Log.e(TAG, "Error during generation", e)
                        stopReason = "error: ${e.message}"
                        throw Exception("Generation error: ${e.message}")
                    }
                }
                .collect { token ->
                    assistantMessage += token
                    _streamedResponse.value = assistantMessage
                    tokenCount++
                    if (tokenCount % 10 == 0) {
                        Log.d(TAG, "Generated $tokenCount tokens (${assistantMessage.length} chars)")
                    }
                }
            
            Log.d(TAG, "Token generation finished. Total tokens: $tokenCount")
            Log.d(TAG, "Response length: ${assistantMessage.length} chars")
            
            // Determine stop reason
            stopReason = when {
                tokenCount >= maxTokens -> {
                    Log.w(TAG, "Hit max tokens limit: $tokenCount >= $maxTokens")
                    "max_tokens_reached"
                }
                tokenCount < maxTokens -> {
                    Log.d(TAG, "EOS token received at $tokenCount tokens (limit was $maxTokens)")
                    "eos_token"
                }
                else -> "completed"
            }
            
            val endTime = System.currentTimeMillis()
            val responseTimeMs = endTime - startTime
            val tokensPerSecond = if (responseTimeMs > 0) (tokenCount * 1000f) / responseTimeMs else 0f
            
            Log.d(TAG, "Generation complete: $tokenCount tokens in ${responseTimeMs}ms (${String.format("%.2f", tokensPerSecond)} tok/s) - Stop: $stopReason")
            
            // Append metadata footer to message
            val stopReasonText = when (stopReason) {
                "eos_token" -> "🛑 Stopped: End of sequence"
                "max_tokens_reached" -> "⚠️ Stopped: Max tokens limit ($maxTokens)"
                else -> "✓ Generation completed"
            }
            
            val metadataFooter = "\n\n---\n*$stopReasonText • ${tokenCount} tokens • ${String.format("%.1f", responseTimeMs / 1000f)}s • ${String.format("%.1f", tokensPerSecond)} tok/s*"
            val finalMessage = assistantMessage + metadataFooter
            
            // Save AI response with performance metrics
            val aiMessage = ChatEntity(
                sessionId = _currentSessionId.value,
                role = "assistant",
                content = finalMessage,
                modelId = model.id,
                responseTimeMs = responseTimeMs,
                tokenCount = tokenCount,
                tokensPerSecond = tokensPerSecond,
                stopReason = stopReason
            )
            chatRepository.insertMessage(aiMessage)
            
            // Update session timestamp
            chatRepository.updateSessionTimestamp(_currentSessionId.value)
            
            // Update metrics
            _generationSpeed.value = tokensPerSecond
            if (currentRuntime is com.rapo.haloai.data.model.GGUFModelRuntime) {
                try {
                    _contextUsed.value = currentRuntime.getContextUsage()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get context usage", e)
                }
            }
            
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.d(TAG, "Generation cancelled - cleaning up")
            // Don't save cancelled responses
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error in generateAIResponse", e)
            // Handle error - show error message
            val errorMessage = ChatEntity(
                sessionId = _currentSessionId.value,
                role = "assistant",
                content = "❌ Error: ${e.message}\n\nPlease check logcat for details.",
                modelId = model.id
            )
            chatRepository.insertMessage(errorMessage)
        } finally {
            _isGenerating.value = false
            _streamedResponse.value = ""
            generationJob = null
        }
    }
    
    fun autoConfigureFromModel() {
        val model = _selectedModel.value ?: return
        
        viewModelScope.launch {
            try {
                val runtime = modelManager.getRuntimeForModel(model)
                if (runtime is com.rapo.haloai.data.model.GGUFModelRuntime) {
                    val metadata = runtime.readMetadata(model.path)
                    
                    Log.d(TAG, "Auto-configuring from model metadata")
                    Log.d(TAG, "Detected context: ${metadata.contextSize}")
                    
                    // Use model's optimal settings
                    pendingSettingsChange = {
                        _generationSettings.value = _generationSettings.value.copy(
                            contextLength = metadata.contextSize,
                            maxTokens = metadata.contextSize / 2 // Half for response
                        )
                        viewModelScope.launch {
                            modelManager.unloadModel()
                        }
                    }
                    _showReloadModelDialog.value = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auto-config failed", e)
            }
        }
    }
    
    fun stopGeneration() {
        generationJob?.cancel()
        _isGenerating.value = false
        _streamedResponse.value = ""
        Log.d(TAG, "Generation stopped by user")
    }
    
    fun updateMessage(message: ChatEntity, newContent: String) {
        viewModelScope.launch {
            val updatedMessage = message.copy(content = newContent)
            chatRepository.updateMessage(updatedMessage)
        }
    }
    
    fun deleteMessage(message: ChatEntity) {
        viewModelScope.launch {
            chatRepository.deleteMessage(message)
        }
    }
    
    fun createNewChat() {
        viewModelScope.launch {
            val newSessionId = UUID.randomUUID().toString()
            chatRepository.insertSession(
                ChatSessionEntity(
                    sessionId = newSessionId,
                    title = "New Chat"
                )
            )
            _currentSessionId.value = newSessionId
        }
    }
    
    fun switchToSession(sessionId: String) {
        _currentSessionId.value = sessionId
    }
    
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            chatRepository.deleteSessionWithMessages(sessionId)
            // If deleting current session, create new one
            if (_currentSessionId.value == sessionId) {
                createNewChat()
            }
        }
    }
    
    fun clearChat() {
        viewModelScope.launch {
            chatRepository.deleteSession(_currentSessionId.value)
            _messages.value = emptyList()
        }
    }
    
    fun selectModel(model: ModelEntity) {
        _selectedModel.value = model
        viewModelScope.launch {
            modelManager.unloadModel()
        }
    }
    
    fun updateMaxTokens(value: Int) {
        _generationSettings.value = _generationSettings.value.copy(maxTokens = value)
    }
    
    fun updateSystemPrompt(value: String) {
        _generationSettings.value = _generationSettings.value.copy(systemPrompt = value)
    }
    
    fun updateTemperature(value: Float) {
        _generationSettings.value = _generationSettings.value.copy(temperature = value)
    }
    
    fun updateThreads(value: Int) {
        pendingSettingsChange = {
            _generationSettings.value = _generationSettings.value.copy(threads = value)
            viewModelScope.launch {
                modelManager.unloadModel()
            }
        }
        _showReloadModelDialog.value = true
    }
    
    fun updateContextLength(value: Int) {
        pendingSettingsChange = {
            _generationSettings.value = _generationSettings.value.copy(contextLength = value)
            viewModelScope.launch {
                modelManager.unloadModel()
            }
        }
        _showReloadModelDialog.value = true
    }
    
    fun resetSettings() {
        pendingSettingsChange = {
            _generationSettings.value = GenerationSettings()
            viewModelScope.launch {
                modelManager.unloadModel()
            }
        }
        _showReloadModelDialog.value = true
    }
    
    fun confirmModelReload() {
        pendingSettingsChange?.invoke()
        pendingSettingsChange = null
        _showReloadModelDialog.value = false
    }
    
    fun cancelModelReload() {
        pendingSettingsChange = null
        _showReloadModelDialog.value = false
    }
    
    private fun generateSessionTitle(firstMessage: String): String {
        // Generate a short title from the first message (max 30 chars)
        return firstMessage.take(30).trim().let { 
            if (firstMessage.length > 30) "$it..." else it
        }.ifEmpty { "New Chat" }
    }
    
    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            modelManager.unloadModel()
        }
    }
}

data class GenerationSettings(
    val maxTokens: Int = 2048, // Max tokens per response
    val temperature: Float = 0.7f,
    val threads: Int = 4,
    val contextLength: Int = 4096, // Increased from 1535 to handle longer responses
    val systemPrompt: String = ""
)