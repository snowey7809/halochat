#pragma once
#include "llama.h"
#include "ggml.h"
#include <string>
#include <vector>
#include <cstring>
#include <utility>
#include <sstream>
#include <cctype>

// Model metadata structure
struct ModelMetadata {
    int contextSize = 4096;
    std::string chatTemplate = "";
    std::string architecture = "";
    bool valid = false;
};

class LLMInference {
private:
    // llama.cpp core types
    llama_context* _ctx = nullptr;
    llama_model* _model = nullptr;
    llama_sampler* _sampler = nullptr;
    llama_token _currToken = 0;
    
    // Chat message storage
    std::vector<llama_chat_message> _messages;
    std::vector<char> _formattedMessages;
    std::vector<llama_token> _promptTokens;
    int _prevLen = 0;
    const char* _chatTemplate = nullptr;
    const char* _modelChatTemplate = nullptr; // Store original model template
    
    // Response tracking
    std::string _response;
    std::string _utf8Cache;
    bool _storeChats = true;
    
    // Metrics
    int64_t _responseGenerationTime = 0;
    long _responseNumTokens = 0;
    int _nCtxUsed = 0;
    
    // Settings
    int _threads = 4;
    int _contextLength = 4096;
    float _temperature = 0.8f;
    
    // UTF-8 validation helper
    bool _isValidUtf8(const char* str);

public:
    LLMInference() = default;
    ~LLMInference();
    
    // Static metadata reading (before loading model)
    static ModelMetadata getModelMetadata(const char* modelPath);
    static ModelMetadata getModelMetadataFallback(const char* modelPath);
    
    // Model lifecycle
    bool loadModel(const char* modelPath, int threads, int contextLength,
                   float temperature, bool storeChats);
    void startFreshConversation(); // Clears context without losing model
    void freeModel();
    bool isReady() const { return _model != nullptr && _ctx != nullptr; }
    
    // Chat management
    void addChatMessage(const char* message, const char* role);
    void addSystemPrompt(const char* prompt) { addChatMessage(prompt, "system"); }
    void addUserMessage(const char* message) { addChatMessage(message, "user"); }
    void addAssistantMessage(const char* message) { addChatMessage(message, "assistant"); }
    void clearMessages();
    
    // Generation lifecycle
    bool startCompletion(const char* query);
    std::string completionLoop();  // Returns token piece or "[EOG]"
    void stopCompletion();

    // Response post-processing
    std::string postProcessResponse(const std::string& rawResponse);
    
    // Metrics
    float getResponseGenerationTime() const;
    int getContextSizeUsed() const;
    int getResponseNumTokens() const { return _responseNumTokens; }
    
    // Info
    std::string getModelInfo() const;
};
