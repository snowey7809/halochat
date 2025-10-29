#include "LLMInference.h"
#include <android/log.h>
#include <cstring>
#include <chrono>

#define TAG "HaloAI-LLMInference"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

static bool backend_initialized = false;

// Static method to read model metadata without loading full model
ModelMetadata LLMInference::getModelMetadata(const char* modelPath) {
    ModelMetadata metadata;
    
    LOGI("Reading metadata from: %s", modelPath);
    
    // Initialize GGUF context with no_alloc = true (doesn't load weights)
    ggml_init_params params = {
        .mem_size = 1024 * 1024,  // 1MB for metadata only
        .mem_buffer = nullptr,
        .no_alloc = true
    };
    
    struct ggml_context* meta_ctx = ggml_init(params);
    if (!meta_ctx) {
        LOGE("Failed to create metadata context");
        return metadata;
    }
    
    // Load model metadata
    llama_model* model = llama_model_load_from_file(modelPath, llama_model_default_params());
    if (!model) {
        LOGE("Failed to load model metadata");
        ggml_free(meta_ctx);
        return metadata;
    }
    
    // Get context size from model
    metadata.contextSize = llama_model_n_ctx_train(model);
    if (metadata.contextSize <= 0) {
        metadata.contextSize = 4096; // fallback
    }
    LOGI("Model context size: %d", metadata.contextSize);
    
    // Get chat template
    const char* template_str = llama_model_chat_template(model, nullptr);
    if (template_str) {
        metadata.chatTemplate = template_str;
        LOGI("Chat template found: %zu chars", metadata.chatTemplate.length());
    } else {
        LOGW("No chat template in model");
    }
    
    // Get architecture name
    char arch_buf[128];
    int arch_len = llama_model_desc(model, arch_buf, sizeof(arch_buf));
    if (arch_len > 0) {
        metadata.architecture = std::string(arch_buf, arch_len);
        LOGI("Architecture: %s", metadata.architecture.c_str());
    }
    
    metadata.valid = true;
    
    // Cleanup
    llama_model_free(model);
    ggml_free(meta_ctx);
    
    return metadata;
}

bool LLMInference::_isValidUtf8(const char* str) {
    if (!str) return true;
    const unsigned char* bytes = (const unsigned char*)str;
    while (*bytes != 0x00) {
        int num;
        if ((*bytes & 0x80) == 0x00) {
            num = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            num = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            num = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            num = 4;
        } else {
            return false;
        }
        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) {
                return false;
            }
            bytes += 1;
        }
    }
    return true;
}

bool LLMInference::loadModel(const char* modelPath, int threads, int contextLength,
                              float temperature, bool storeChats) {
    LOGI("Loading model: %s (threads=%d, ctx=%d, temp=%.2f)", 
         modelPath, threads, contextLength, temperature);
    
    // Initialize backend once
    if (!backend_initialized) {
        llama_backend_init();
        ggml_backend_load_all();
        backend_initialized = true;
        LOGI("Backend initialized with GPU/NPU support");
    }
    
    // Store settings
    _threads = threads;
    _contextLength = contextLength;
    _temperature = temperature;
    _storeChats = storeChats;
    
    // Load model
    llama_model_params model_params = llama_model_default_params();
    model_params.use_mmap = true;
    model_params.use_mlock = false;
    _model = llama_model_load_from_file(modelPath, model_params);
    if (!_model) {
        LOGE("Failed to load model from %s", modelPath);
        return false;
    }
    LOGI("Model loaded");
    
    // Create context
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = contextLength;
    ctx_params.n_batch = contextLength;
    ctx_params.n_threads = threads;
    ctx_params.n_threads_batch = threads;
    ctx_params.no_perf = false;
    
    _ctx = llama_init_from_model(_model, ctx_params);
    if (!_ctx) {
        LOGE("Failed to create context");
        llama_model_free(_model);
        _model = nullptr;
        return false;
    }
    LOGI("Context created");
    
    // Create sampler
    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    sampler_params.no_perf = true;
    _sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(_sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(_sampler, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(_sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    LOGI("Sampler configured");
    
    // Get chat template
    _chatTemplate = llama_model_chat_template(_model, nullptr);
    if (!_chatTemplate) {
        LOGW("No chat template found, using default format");
        _chatTemplate = "{% for message in messages %}{{ message['role'] }}: {{ message['content'] }}\n{% endfor %}assistant:";
    }
    
    // Initialize message storage
    _formattedMessages.resize(contextLength);
    _messages.clear();
    _prevLen = 0;
    
    LOGI("Model initialization complete");
    return true;
}

void LLMInference::addChatMessage(const char* message, const char* role) {
    _messages.push_back({strdup(role), strdup(message)});
}

void LLMInference::clearMessages() {
    for (auto& msg : _messages) {
        free(const_cast<char*>(msg.role));
        free(const_cast<char*>(msg.content));
    }
    _messages.clear();
    _prevLen = 0;
}

bool LLMInference::startCompletion(const char* query) {
    if (!isReady()) {
        LOGE("Model not ready");
        return false;
    }
    
    // Reset metrics
    if (!_storeChats) {
        _prevLen = 0;
        _formattedMessages.clear();
        _formattedMessages.resize(llama_n_ctx(_ctx));
    }
    _responseGenerationTime = 0;
    _responseNumTokens = 0;
    _response.clear();
    _utf8Cache.clear();
    
    // Add user message
    addChatMessage(query, "user");
    
    // Apply chat template
    int newLen = llama_chat_apply_template(
        _chatTemplate,
        _messages.data(),
        _messages.size(),
        true,  // add_generation_prompt
        _formattedMessages.data(),
        _formattedMessages.size()
    );
    
    if (newLen > (int)_formattedMessages.size()) {
        _formattedMessages.resize(newLen);
        newLen = llama_chat_apply_template(
            _chatTemplate,
            _messages.data(),
            _messages.size(),
            true,
            _formattedMessages.data(),
            _formattedMessages.size()
        );
    }
    
    if (newLen < 0) {
        LOGE("Chat template application failed");
        return false;
    }
    
    // Get prompt (only new part)
    std::string prompt(_formattedMessages.begin() + _prevLen, 
                      _formattedMessages.begin() + newLen);
    LOGI("Prompt: %zu chars (total: %d, prev: %d)", prompt.length(), newLen, _prevLen);
    
    // Tokenize
    _promptTokens.clear();
    _promptTokens.resize(prompt.length() + 256);
    
    int n_tokens = llama_tokenize(
        llama_model_get_vocab(_model),
        prompt.c_str(),
        prompt.length(),
        _promptTokens.data(),
        _promptTokens.size(),
        _prevLen == 0,  // add_special only for first message
        false
    );
    
    if (n_tokens < 0) {
        _promptTokens.resize(-n_tokens);
        n_tokens = llama_tokenize(
            llama_model_get_vocab(_model),
            prompt.c_str(),
            prompt.length(),
            _promptTokens.data(),
            _promptTokens.size(),
            _prevLen == 0,
            false
        );
    }
    
    if (n_tokens < 0) {
        LOGE("Tokenization failed");
        return false;
    }
    
    _promptTokens.resize(n_tokens);
    LOGI("Tokenized: %zu tokens", _promptTokens.size());
    
    // Clear KV cache
    llama_memory_t mem = llama_get_memory(_ctx);
    if (mem) {
        llama_memory_clear(mem, false);
        LOGI("KV cache cleared");
    }
    
    // Reset sampler
    llama_sampler_reset(_sampler);
    
    // Check context usage
    _nCtxUsed = (int)(llama_memory_seq_pos_max(mem, 0) + 1);
    int n_ctx = llama_n_ctx(_ctx);
    LOGI("Context usage: %d / %d", _nCtxUsed, n_ctx);
    
    if (_nCtxUsed + (int)_promptTokens.size() + 512 > n_ctx) {
        LOGE("Context overflow: %d + %zu + 512 > %d", 
             _nCtxUsed, _promptTokens.size(), n_ctx);
        return false;
    }
    
    // Decode prompt
    llama_batch batch = llama_batch_get_one(_promptTokens.data(), _promptTokens.size());
    if (llama_decode(_ctx, batch) != 0) {
        LOGE("Failed to decode prompt");
        return false;
    }
    
    LOGI("Generation started");
    return true;
}

std::string LLMInference::completionLoop() {
    if (!isReady()) {
        return "[ERROR]";
    }
    
    auto start = std::chrono::steady_clock::now();
    
    // Sample token
    _currToken = llama_sampler_sample(_sampler, _ctx, -1);
    llama_sampler_accept(_sampler, _currToken);
    
    // Check for EOS
    if (llama_vocab_is_eog(llama_model_get_vocab(_model), _currToken)) {
        LOGI("End of generation (%d tokens)", _responseNumTokens);
        if (_storeChats) {
            addChatMessage(_response.c_str(), "assistant");
        }
        return "[EOG]";
    }
    
    // Convert to text
    char piece[256];
    int n_chars = llama_token_to_piece(
        llama_model_get_vocab(_model),
        _currToken,
        piece,
        sizeof(piece),
        0,
        false
    );
    
    auto end = std::chrono::steady_clock::now();
    _responseGenerationTime += std::chrono::duration_cast<std::chrono::microseconds>(end - start).count();
    _responseNumTokens++;
    
    if (n_chars > 0 && n_chars < (int)sizeof(piece)) {
        piece[n_chars] = '\0';
        _utf8Cache += piece;
        
        // Validate UTF-8
        if (_isValidUtf8(_utf8Cache.c_str())) {
            _response += _utf8Cache;
            std::string result = _utf8Cache;
            _utf8Cache.clear();
            
            // Decode next token
            llama_batch next_batch = llama_batch_get_one(&_currToken, 1);
            if (llama_decode(_ctx, next_batch) != 0) {
                LOGE("Decode failed");
                return "[ERROR]";
            }
            
            return result;
        }
    }
    
    // Decode next token even if UTF-8 incomplete
    llama_batch next_batch = llama_batch_get_one(&_currToken, 1);
    llama_decode(_ctx, next_batch);
    
    return "";
}

void LLMInference::stopCompletion() {
    if (_storeChats && !_response.empty()) {
        addChatMessage(_response.c_str(), "assistant");
    }
    
    _prevLen = llama_chat_apply_template(
        _chatTemplate,
        _messages.data(),
        _messages.size(),
        false,
        nullptr,
        0
    );
    
    LOGI("Generation stopped. Response: %zu chars, %ld tokens", 
         _response.length(), _responseNumTokens);
}

float LLMInference::getResponseGenerationTime() const {
    return _responseNumTokens > 0 ? 
           (float)_responseNumTokens / (_responseGenerationTime / 1e6f) : 0.0f;
}

int LLMInference::getContextSizeUsed() const {
    return _nCtxUsed;
}

std::string LLMInference::getModelInfo() const {
    if (!isReady()) {
        return "Model not loaded";
    }
    
    char info[512];
    snprintf(info, sizeof(info),
        "Context: %d | Vocab: %d | Threads: %d",
        llama_n_ctx(_ctx),
        llama_vocab_n_tokens(llama_model_get_vocab(_model)),
        _threads
    );
    return std::string(info);
}

void LLMInference::freeModel() {
    clearMessages();
    
    if (_sampler) {
        llama_sampler_free(_sampler);
        _sampler = nullptr;
    }
    
    if (_ctx) {
        llama_free(_ctx);
        _ctx = nullptr;
    }
    
    if (_model) {
        llama_model_free(_model);
        _model = nullptr;
    }
    
    LOGI("Model resources freed");
}

LLMInference::~LLMInference() {
    freeModel();
}
