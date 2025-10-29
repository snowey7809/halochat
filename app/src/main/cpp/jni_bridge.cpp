#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <chrono>
#include <android/log.h>
#include "llama.h"

#define TAG "HaloAI-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

// Context wrapper to hold model and generation state
struct llama_context_wrapper {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    llama_sampler* sampler = nullptr;
    bool initialized = false;
};

// Initialize the llama backend once
static bool backend_initialized = false;

extern "C" JNIEXPORT jlong JNICALL
Java_com_rapo_haloai_data_model_GGUFModelRuntime_initModel(
    JNIEnv* env,
    jobject /* this */,
    jstring modelPath,
    jint threads,
    jint contextLength
) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Initializing GGUF model from: %s (threads=%d, context=%d)", path, threads, contextLength);
    
    // Initialize backend once
    if (!backend_initialized) {
        llama_backend_init();
        backend_initialized = true;
        LOGI("Llama backend initialized");
    }
    
    auto* wrapper = new llama_context_wrapper();

    // Load model
    llama_model_params model_params = llama_model_default_params();
    model_params.use_mmap = true;
    model_params.use_mlock = false;
    wrapper->model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!wrapper->model) {
        LOGE("Failed to load model from path");
        delete wrapper;
        return 0;
    }
    
    LOGI("Model loaded successfully");

    // Create context with user-specified parameters
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = contextLength;           // User-specified context
    ctx_params.n_batch = contextLength / 4;     // Batch size = context / 4
    ctx_params.n_threads = threads;             // User-specified threads
    ctx_params.n_threads_batch = threads;       // Match threads
    
    wrapper->ctx = llama_init_from_model(wrapper->model, ctx_params);

    if (!wrapper->ctx) {
        LOGE("Failed to create context");
        llama_model_free(wrapper->model);
        delete wrapper;
        return 0;
    }
    
    LOGI("Context created successfully");

    // Create sampler chain for text generation
    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    wrapper->sampler = llama_sampler_chain_init(sampler_params);
    
    if (!wrapper->sampler) {
        LOGE("Failed to create sampler");
        llama_free(wrapper->ctx);
        llama_model_free(wrapper->model);
        delete wrapper;
        return 0;
    }
    
    // Add sampling strategies
    llama_sampler_chain_add(wrapper->sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(wrapper->sampler, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(wrapper->sampler, llama_sampler_init_temp(0.8f));
    llama_sampler_chain_add(wrapper->sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    
    LOGI("Sampler chain configured");

    wrapper->initialized = true;
    LOGI("Model initialization complete");
    
    return reinterpret_cast<jlong>(wrapper);
}

extern "C" JNIEXPORT void JNICALL
Java_com_rapo_haloai_data_model_GGUFModelRuntime_generateText(
    JNIEnv* env,
    jobject /* this */,
    jlong handle,
    jstring prompt,
    jint maxTokens,
    jobject callback
) {
    auto* wrapper = reinterpret_cast<llama_context_wrapper*>(handle);
    
    if (!wrapper || !wrapper->initialized || !wrapper->ctx || !wrapper->model || !wrapper->sampler) {
        LOGE("Invalid context handle or uninitialized model");
        return;
    }

    const char* prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    if (!prompt_cstr) {
        LOGE("Failed to get prompt string");
        return;
    }
    
    LOGI("Generating text for prompt (max_tokens: %d)", maxTokens);

    // Tokenize the prompt using modern API
    const int prompt_len = strlen(prompt_cstr);
    std::vector<llama_token> tokens(prompt_len + 256); // Extra space for special tokens
    
    int n_tokens = llama_tokenize(
        llama_model_get_vocab(wrapper->model),
        prompt_cstr,
        prompt_len,
        tokens.data(),
        tokens.size(),
        true,  // add_special (BOS, etc.)
        false  // parse_special
    );
    
    if (n_tokens < 0) {
        // Buffer was too small, resize and retry
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(
            llama_model_get_vocab(wrapper->model),
            prompt_cstr,
            prompt_len,
            tokens.data(),
            tokens.size(),
            true,
            false
        );
        if (n_tokens < 0) {
            LOGE("Failed to tokenize prompt even after resize");
            env->ReleaseStringUTFChars(prompt, prompt_cstr);
            return;
        }
    }
    
    // NOW we can release the string
    env->ReleaseStringUTFChars(prompt, prompt_cstr);
    tokens.resize(n_tokens);
    
    LOGI("Tokenized prompt into %zu tokens", tokens.size());
    
    // CRITICAL: Clear KV cache before each generation
    // This prevents context overflow by starting fresh each time
    llama_kv_cache_clear(wrapper->ctx);
    LOGI("KV cache cleared");
    
    // CRITICAL: Reset sampler state for fresh generation
    // This clears the sampler's internal state from previous generations
    llama_sampler_reset(wrapper->sampler);
    LOGI("Sampler state reset for fresh generation");
    
    // Get context info
    int n_ctx = llama_n_ctx(wrapper->ctx);
    LOGI("Context size: %d", n_ctx);
    
    // Check if prompt fits in context
    if (static_cast<int>(tokens.size()) + maxTokens > n_ctx) {
        LOGW("Warning: prompt (%zu) + max_tokens (%d) = %zu exceeds context size (%d)", 
             tokens.size(), maxTokens, tokens.size() + maxTokens, n_ctx);
        LOGW("Consider increasing context length in settings or reducing max tokens");
    }

    // Get callback method from Kotlin Function1<String, Unit>
    jclass callbackClass = env->GetObjectClass(callback);
    if (!callbackClass) {
        LOGE("Failed to get callback class");
        return;
    }
    
    jmethodID callbackMethod = env->GetMethodID(callbackClass, "invoke", "(Ljava/lang/Object;)Ljava/lang/Object;");
    if (!callbackMethod) {
        LOGE("Failed to find callback method");
        env->DeleteLocalRef(callbackClass);
        return;
    }

    // Evaluate the prompt tokens in a single batch
    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    
    if (llama_decode(wrapper->ctx, batch) != 0) {
        LOGE("Failed to decode prompt batch");
        env->DeleteLocalRef(callbackClass);
        return;
    }
    
    LOGI("Prompt decoded, starting generation (max %d tokens)", maxTokens);

    // Generate tokens one by one
    int generated = 0;
    std::vector<llama_token> output_tokens;
    output_tokens.reserve(maxTokens);
    
    auto start_time = std::chrono::steady_clock::now();
    
    for (int i = 0; i < maxTokens; ++i) {
        // Sample next token using the sampler chain
        llama_token new_token = llama_sampler_sample(wrapper->sampler, wrapper->ctx, -1);
        
        // Accept the sampled token
        llama_sampler_accept(wrapper->sampler, new_token);

        // Check for EOS
        llama_vocab* vocab_ptr = const_cast<llama_vocab*>(llama_model_get_vocab(wrapper->model));
        if (llama_vocab_is_eog(vocab_ptr, new_token)) {
            LOGI("End of generation (EOS) at token %d", generated);
            break;
        }
        
        // Log progress every 10 tokens
        if (generated > 0 && generated % 10 == 0) {
            auto now = std::chrono::steady_clock::now();
            auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(now - start_time).count();
            float tokens_per_sec = (generated * 1000.0f) / elapsed;
            LOGI("Progress: %d tokens in %lld ms (%.2f tok/s)", generated, elapsed, tokens_per_sec);
        }

        // Convert token to text using modern API
        const struct llama_vocab* vocab = llama_model_get_vocab(wrapper->model);
        char piece[256];
        const int n_chars = llama_token_to_piece(
            vocab,
            new_token,
            piece,
            sizeof(piece),
            0,     // lstrip
            false  // special
        );
        
        if (n_chars > 0 && n_chars < static_cast<int>(sizeof(piece))) {
            piece[n_chars] = '\0';
            
            // Send token to Kotlin callback
            jstring token_str = env->NewStringUTF(piece);
            if (token_str) {
                env->CallObjectMethod(callback, callbackMethod, token_str);
                // Check for exceptions
                if (env->ExceptionCheck()) {
                    LOGE("Exception in callback");
                    env->ExceptionClear();
                    env->DeleteLocalRef(token_str);
                    break;
                }
                env->DeleteLocalRef(token_str);
            }
        } else if (n_chars < 0) {
            LOGW("Token to piece conversion failed for token %d", new_token);
        }

        output_tokens.push_back(new_token);
        generated++;

        // Prepare next batch with the new token
        llama_batch next_batch = llama_batch_get_one(&new_token, 1);
        
        if (llama_decode(wrapper->ctx, next_batch) != 0) {
            LOGE("Failed to decode token at position %d", i);
            break;
        }
    }
    
    // Cleanup
    env->DeleteLocalRef(callbackClass);
    
    LOGI("Generation complete: %d tokens generated", generated);
}

extern "C" JNIEXPORT void JNICALL
Java_com_rapo_haloai_data_model_GGUFModelRuntime_freeModel(
    JNIEnv* env,
    jobject /* this */,
    jlong handle
) {
    auto* wrapper = reinterpret_cast<llama_context_wrapper*>(handle);
    
    if (wrapper) {
        LOGI("Freeing model resources");
        
        if (wrapper->sampler) {
            llama_sampler_free(wrapper->sampler);
            wrapper->sampler = nullptr;
        }
        
        if (wrapper->ctx) {
            llama_free(wrapper->ctx);
            wrapper->ctx = nullptr;
        }
        
        if (wrapper->model) {
            llama_model_free(wrapper->model);
            wrapper->model = nullptr;
        }
        
        wrapper->initialized = false;
        delete wrapper;
        
        LOGI("Model resources freed successfully");
    }
}

// Additional utility methods

extern "C" JNIEXPORT jstring JNICALL
Java_com_rapo_haloai_data_model_GGUFModelRuntime_getModelInfo(
    JNIEnv* env,
    jobject /* this */,
    jlong handle
) {
    auto* wrapper = reinterpret_cast<llama_context_wrapper*>(handle);
    
    if (!wrapper || !wrapper->model) {
        return env->NewStringUTF("Model not loaded");
    }
    
    char info[512];
    const llama_vocab* vocab_info = llama_model_get_vocab(wrapper->model);
    snprintf(info, sizeof(info), 
        "Model loaded | Context size: %d | Vocab size: %d",
        llama_n_ctx(wrapper->ctx),
        llama_vocab_n_tokens(vocab_info)
    );
    
    return env->NewStringUTF(info);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_rapo_haloai_data_model_GGUFModelRuntime_isModelLoaded(
    JNIEnv* env,
    jobject /* this */,
    jlong handle
) {
    auto* wrapper = reinterpret_cast<llama_context_wrapper*>(handle);
    return (wrapper && wrapper->initialized && wrapper->model && wrapper->ctx) ? JNI_TRUE : JNI_FALSE;
}
