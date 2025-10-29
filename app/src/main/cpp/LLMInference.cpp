#include "LLMInference.h"
#include <android/log.h>
#include <cstring>
#include <chrono>
#include <sstream>
#include <algorithm>
#include <gguf.h>

#define TAG "HaloAI-LLMInference"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

static bool backend_initialized = false;

// Static method to read model metadata using GGUF library (more efficient)
ModelMetadata LLMInference::getModelMetadata(const char* modelPath) {
    ModelMetadata metadata;

    LOGI("Reading metadata from GGUF: %s", modelPath);

    // Use GGUF library directly like SmolLM implementation
    gguf_init_params initParams = { .no_alloc = true, .ctx = nullptr };
    gguf_context* ggufContext = gguf_init_from_file(modelPath, initParams);

    if (!ggufContext) {
        LOGW("Failed to read GGUF metadata, falling back to llama method");
        return getModelMetadataFallback(modelPath);
    }

    // Get context size
    metadata.contextSize = 4096; // default fallback
    int64_t architectureKeyId = gguf_find_key(ggufContext, "general.architecture");

    if (architectureKeyId != -1) {
        std::string architecture = gguf_get_val_str(ggufContext, architectureKeyId);
        std::string contextLengthKey = architecture + ".context_length";
        int64_t contextLengthKeyId = gguf_find_key(ggufContext, contextLengthKey.c_str());

        if (contextLengthKeyId != -1) {
            uint32_t contextLength = gguf_get_val_u32(ggufContext, contextLengthKeyId);
            metadata.contextSize = static_cast<int>(contextLength);
            LOGI("Context size from GGUF: %d", static_cast<int>(contextLength));
        }
    }

    // Get chat template
    int64_t chatTemplateKeyId = gguf_find_key(ggufContext, "tokenizer.chat_template");
    if (chatTemplateKeyId != -1) {
        metadata.chatTemplate = gguf_get_val_str(ggufContext, chatTemplateKeyId);
        LOGI("Chat template from GGUF: %zu chars", metadata.chatTemplate.length());
    } else {
        LOGW("No chat template in GGUF");
    }

    // Try to get architecture
    if (architectureKeyId != -1) {
        metadata.architecture = gguf_get_val_str(ggufContext, architectureKeyId);
        LOGI("Architecture from GGUF: %s", metadata.architecture.c_str());
    }

    metadata.valid = true;

    // Note: We don't free ggufContext as per GGUF library usage
    return metadata;
}

// Fallback metadata reading using llama (keeps compatibility)
ModelMetadata LLMInference::getModelMetadataFallback(const char* modelPath) {
    ModelMetadata metadata;

    LOGI("Fallback: Reading metadata with llama: %s", modelPath);

    // Initialize GGML context with no_alloc = true (doesn't load weights)
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
    
    // Completely bypass chat templates - they're causing issues
    // Use raw text approach to get natural responses
    LOGI("Using raw text approach - completely bypassing chat templates");
    _chatTemplate = nullptr; // Explicitly null to avoid template processing
    
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

// Clear conversation history and reset context for a fresh conversation
void LLMInference::startFreshConversation() {
    LOGI("Starting fresh conversation - clearing messages and context");

    // Clear all conversation messages
    clearMessages();

    // Clear formatted message buffer
    _formattedMessages.clear();
    _formattedMessages.resize(_contextLength);

    // Reset other state
    _prevLen = 0;

    LOGI("Fresh conversation started");
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

    // Always clear previous state for fresh responses (no conversation continuity)
    _prevLen = 0;
    _formattedMessages.clear();
    _formattedMessages.resize(llama_n_ctx(_ctx));

    // Reset generation metrics
    _responseGenerationTime = 0;
    _responseNumTokens = 0;
    _response.clear();
    _utf8Cache.clear();

    // Only clear previous messages when explicitly starting fresh conversation
    // (_storeChats=true means maintain conversation history)

    // Use raw text approach - completely bypass templates
    std::string rawPrompt;
    if (_chatTemplate != nullptr) {
        // Template mode
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
            LOGE("Chat template application failed with error code: %d", newLen);

            // Fallback to raw text
            rawPrompt = std::string(_messages[0].content);
            LOGW("Using raw text fallback for template failure");
        } else {
            // Get prompt (only new part)
            rawPrompt = std::string(_formattedMessages.begin() + _prevLen,
                                   _formattedMessages.begin() + newLen);
        }
    } else {
        // Completely fresh raw generation - try a different approach
        std::string cleanQuery = std::string(query);
        // Remove excessive whitespace/newlines
        cleanQuery.erase(
            std::remove_if(cleanQuery.begin(), cleanQuery.end(),
                [](unsigned char c) { return c == '\n' || c == '\r' || c == '\t'; }),
            cleanQuery.end()
        );

        // Try a more explicit system prompt to break template addiction
        // Based on GitHub discussions, some models are hard-coded to respond in certain formats
        rawPrompt = "You are a helpful assistant. User says: " + cleanQuery + ". Respond naturally as a human would, without any formatting, headers, or special tokens. Just give a direct answer.";
        LOGI("Using explicit anti-template prompt: %s", rawPrompt.c_str());
    }

    // Tokenize the raw prompt
    _promptTokens.clear();
    _promptTokens.resize(rawPrompt.length() + 256);

    int n_tokens = llama_tokenize(
        llama_model_get_vocab(_model),
        rawPrompt.c_str(),
        rawPrompt.length(),
        _promptTokens.data(),
        _promptTokens.size(),
        false,  // add_special - let the model handle it
        false
    );

    if (n_tokens < 0) {
        _promptTokens.resize(-n_tokens);
        n_tokens = llama_tokenize(
            llama_model_get_vocab(_model),
            rawPrompt.c_str(),
            rawPrompt.length(),
            _promptTokens.data(),
            _promptTokens.size(),
            false,
            false
        );
    }

    if (n_tokens < 0) {
        LOGE("Raw text tokenization failed");
        return false;
    }

    _promptTokens.resize(n_tokens);
    
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
        // Flush any remaining UTF-8 cache
        if (!_utf8Cache.empty()) {
            _response += _utf8Cache;
            std::string result = _utf8Cache;
            _utf8Cache.clear();
            if (_storeChats) {
                addChatMessage((_response).c_str(), "assistant");
            }
            // No more tokens to decode
            return result;
        }
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

        // Always emit the token piece - trie to build valid UTF-8 progressively
        // If it's complete UTF-8, emit it immediately
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
        } else {
            // Not complete UTF-8 yet, but emit anyway as tokens should align to char boundaries
            _response += _utf8Cache;
            std::string result = _utf8Cache;
            _utf8Cache.clear();

            // Decode next token
            llama_batch next_batch = llama_batch_get_one(&_currToken, 1);
            llama_decode(_ctx, next_batch);

            return result;
        }
    }

    // No valid text piece, just decode next token
    llama_batch next_batch = llama_batch_get_one(&_currToken, 1);
    llama_decode(_ctx, next_batch);

    return "";
}

std::string LLMInference::postProcessResponse(const std::string& rawResponse) {
    std::string processed = rawResponse;

    // Remove Llama chat template special tokens and other formatting artifacts
    // Patterns to remove:
    // <|start_header_id|>role<|end_header_id|>
    // <|eot_id|>
    // {{{{
    // |_|
    // Lines that look like timestamps or metadata: "Little • snow •, [29-10-2025...]"
    // Repeated or malformed formatting

    // Remove timestamp-like patterns that appear in responses
    size_t pos = 0;
    while ((pos = processed.find("Little • snow •, [")) != std::string::npos) {
        size_t endBracket = processed.find("]", pos);
        if (endBracket != std::string::npos) {
            // Remove the entire metadata line including newline
            size_t lineEnd = endBracket;
            if (lineEnd + 1 < processed.length() && processed[lineEnd + 1] == '\n') {
                lineEnd++;
            }
            if (lineEnd + 1 < processed.length() && processed[lineEnd + 1] == '\r') {
                lineEnd++;
            }
            processed.erase(pos, lineEnd - pos + 1);
        } else {
            pos += 15;
        }
    }

    // Remove <|start_header_id|>... <|end_header_id|> patterns
    pos = 0;
    while ((pos = processed.find("<|start_header_id|>", pos)) != std::string::npos) {
        size_t endHeader = processed.find("<|end_header_id|>", pos);
        if (endHeader != std::string::npos) {
            endHeader += 17; // length of "<|end_header_id|>"
            // Remove the entire header block including any trailing newlines
            size_t blockEnd = endHeader;
            while (blockEnd < processed.length() && (processed[blockEnd] == '\n' || processed[blockEnd] == '\r')) {
                blockEnd++;
            }
            processed.erase(pos, blockEnd - pos);
        } else {
            pos += 18; // length of "<|start_header_id|>"
        }
    }

    // Remove any remaining individual template tokens
    std::vector<std::string> tokensToRemove = {
        "<|end_header_id|>",
        "<|eot_id|>",
        "<|start_header_id|>",
        "<|eot_id|>",
        "`python",
        "`",
        "<|eot_id|>",
        "_<|start_header_id|>_",
        "<|start_header_id|>_"
    };

    for (const auto& token : tokensToRemove) {
        pos = 0;
        while ((pos = processed.find(token, pos)) != std::string::npos) {
            processed.erase(pos, token.length());
        }
    }

    // Remove malformed brackets and separators like `_"}}}` `}}}}` and similar junk
    std::vector<std::string> junkPatterns = {
        "_\"}}",  // Common malformed ending patterns
        "}}}}",
        "{{{{",
        "|>>_",
        "_|\">",
        "|\">",
        "|>",
        "|\"\"/>",
        "|\">",
        "_|\">",
        "|\">|\">",
        "\"}}`",
        "`\"",
        "_\"}}}}",
        "}}}}`",
        "\"}}}}"
    };

    for (const auto& junk : junkPatterns) {
        pos = 0;
        while ((pos = processed.find(junk, pos)) != std::string::npos) {
            processed.erase(pos, junk.length());
        }
    }

    // Remove multiple curly braces {{{{ (4 or more)
    pos = 0;
    while (pos < processed.length()) {
        if (processed[pos] == '{') {
            size_t braceEnd = pos;
            while (braceEnd < processed.length() && processed[braceEnd] == '{') {
                braceEnd++;
            }
            if (braceEnd - pos >= 4) { // Remove groups of 4 or more braces
                processed.erase(pos, braceEnd - pos);
            } else {
                pos = braceEnd;
            }
        } else {
            pos++;
        }
    }

    // Clean up excessive newlines - replace 3+ consecutive newlines with 2
    pos = 0;
    while ((pos = processed.find("\n\n\n", pos)) != std::string::npos) {
        processed.replace(pos, 3, "\n\n");
    }

    // Remove lines that contain only special characters or are clearly junk
    std::stringstream ss(processed);
    std::string line;
    std::string cleaned;
    bool firstLine = true;

    while (std::getline(ss, line)) {
        // Skip lines that are mostly special characters or template remnants
        bool isJunk = false;

        // Check if line contains only special chars and whitespace
        bool hasAlpha = false;
        int specialCount = 0;

        for (char c : line) {
            if (isalnum(c) || c == '.' || c == ',' || c == '!' || c == '?' || c == ':') {
                hasAlpha = true;
            } else if (!isspace(c) && !isalnum(c)) {
                specialCount++;
            }
        }

        // Skip lines that are all special characters or template remnants
        if (line.find("<|") != std::string::npos ||
            line.find("|>") != std::string::npos ||
            (specialCount > line.length() / 2 && !hasAlpha) ||
            line.find("}}}}") != std::string::npos ||
            line.find("{{{") != std::string::npos) {
            isJunk = true;
        }

        if (!isJunk && !line.empty()) {
            if (!firstLine) cleaned += "\n";
            cleaned += line;
            firstLine = false;
        }
    }

    processed = cleaned;

    // Final cleanup - trim leading/trailing whitespace and newlines
    while (!processed.empty() && (processed[0] == '\n' || processed[0] == '\r' || processed[0] == ' ')) {
        processed.erase(0, 1);
    }
    while (!processed.empty() && (processed.back() == '\n' || processed.back() == '\r' || processed.back() == ' ')) {
        processed.pop_back();
    }

    // If the response starts with a template-like pattern, try to extract just the content
    if (processed.length() > 200) { // Only for long responses that might be malformed
        // Look for the first meaningful content after cleanup
        size_t firstRealContent = processed.find_first_not_of(" \t\n\r");
        if (firstRealContent != std::string::npos) {
            // If we find a pattern like "I am", "You are", "Here's", "Let me", etc., extract from there
            std::vector<std::string> starters = {"I am", "You are", "Here's", "Here is", "Let me", "This is", "To help", "First,", "Second,", "Finally,"};
            for (const auto& starter : starters) {
                size_t startPos = processed.find(starter, firstRealContent);
                if (startPos != std::string::npos && startPos < processed.length() / 3) {
                    // Make sure it's at the start of a word/line
                    if (startPos == 0 || processed[startPos-1] == '\n' || processed[startPos-1] == ' ') {
                        processed = processed.substr(startPos);
                        break;
                    }
                }
            }
        }
    }

    return processed;
}

void LLMInference::stopCompletion() {
    // Post-process the response to remove any artifacts (though we bypassed templates)
    std::string cleanResponse = postProcessResponse(_response);
    _response = cleanResponse;

    if (_storeChats && !_response.empty()) {
        addChatMessage(_response.c_str(), "assistant");
    }

    // No template to apply since we're using raw text
    _prevLen = 0;

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
