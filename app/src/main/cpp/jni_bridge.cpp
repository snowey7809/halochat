#include <jni.h>
#include <android/log.h>
#include "LLMInference.h"

#define TAG "HaloAI-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// HaloAI Default JNI Bridge - Using your app's default signatures

// Get model metadata before loading (lightweight)
extern "C" JNIEXPORT jobject JNICALL
Java_com_rapo_haloai_data_model_GGUFModelRuntime_getModelMetadata(
    JNIEnv* env,
    jobject /* this */,
    jstring modelPath
) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("getModelMetadata called: %s", path);

    ModelMetadata metadata = LLMInference::getModelMetadata(path);
    env->ReleaseStringUTFChars(modelPath, path);

    // Create Java object to return metadata
    // Find the ModelMetadata class
    jclass metadataClass = env->FindClass("com/rapo/haloai/data/model/ModelMetadata");
    if (!metadataClass) {
        LOGE("Failed to find ModelMetadata class");
        return nullptr;
    }

    // Get constructor
    jmethodID constructor = env->GetMethodID(metadataClass, "<init>", "(ILjava/lang/String;Ljava/lang/String;Z)V");
    if (!constructor) {
        LOGE("Failed to find ModelMetadata constructor");
        return nullptr;
    }

    // Create strings
    jstring chatTemplate = env->NewStringUTF(metadata.chatTemplate.c_str());
    jstring architecture = env->NewStringUTF(metadata.architecture.c_str());

    // Create object
    jobject metadataObj = env->NewObject(
        metadataClass,
        constructor,
        metadata.contextSize,
        chatTemplate,
        architecture,
        metadata.valid
    );

    env->DeleteLocalRef(chatTemplate);
    env->DeleteLocalRef(architecture);
    env->DeleteLocalRef(metadataClass);

    return metadataObj;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_rapo_haloai_data_model_GGUFModelRuntime_initModel(
    JNIEnv* env,
    jobject /* this */,
    jstring modelPath,
    jint threads,
    jint contextLength
) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("initModel called: %s", path);

    auto* llm = new LLMInference();
    bool success = llm->loadModel(path, threads, contextLength, 0.8f, true);

    env->ReleaseStringUTFChars(modelPath, path);

    if (!success) {
        LOGE("Model loading failed");
        delete llm;
        return 0;
    }

    LOGI("Model loaded successfully, handle: %p", llm);
    return reinterpret_cast<jlong>(llm);
}

// Add chat message manually
extern "C" JNIEXPORT void JNICALL
Java_com_rapo_haloai_data_model_GGUFModelRuntime_addChatMessage(
    JNIEnv* env,
    jobject /* this */,
    jlong handle,
    jstring message,
    jstring role
) {
    auto* llm = reinterpret_cast<LLMInference*>(handle);
    if (!llm) return;

    const char* msgCstr = env->GetStringUTFChars(message, nullptr);
    const char* roleCstr = env->GetStringUTFChars(role, nullptr);

    llm->addChatMessage(msgCstr, roleCstr);

    env->ReleaseStringUTFChars(message, msgCstr);
    env->ReleaseStringUTFChars(role, roleCstr);
}

// Convenience methods for your Kotlin code
extern "C" JNIEXPORT void JNICALL
Java_com_rapo_haloai_data_model_GGUFModelRuntime_addSystemPrompt(
    JNIEnv* env,
    jobject /* this */,
    jlong handle,
    jstring prompt
) {
    auto* llm = reinterpret_cast<LLMInference*>(handle);
    if (!llm) return;

    const char* promptCstr = env->GetStringUTFChars(prompt, nullptr);
    llm->addSystemPrompt(promptCstr);
    env->ReleaseStringUTFChars(prompt, promptCstr);
}

extern "C" JNIEXPORT void JNICALL
Java_com_rapo_haloai_data_model_GGUFModelRuntime_addUserMessage(
    JNIEnv* env,
    jobject /* this */,
    jlong handle,
    jstring message
) {
    auto* llm = reinterpret_cast<LLMInference*>(handle);
    if (!llm) return;

    const char* messageCstr = env->GetStringUTFChars(message, nullptr);
    llm->addUserMessage(messageCstr);
    env->ReleaseStringUTFChars(message, messageCstr);
}

extern "C" JNIEXPORT void JNICALL
Java_com_rapo_haloai_data_model_GGUFModelRuntime_addAssistantMessage(
    JNIEnv* env,
    jobject /* this */,
    jlong handle,
    jstring message
) {
    auto* llm = reinterpret_cast<LLMInference*>(handle);
    if (!llm) return;

    const char* messageCstr = env->GetStringUTFChars(message, nullptr);
    llm->addAssistantMessage(messageCstr);
    env->ReleaseStringUTFChars(message, messageCstr);
}

// Start completion (prepare prompt)
extern "C" JNIEXPORT void JNICALL
Java_com_rapo_haloai_data_model_GGUFModelRuntime_startCompletion(
    JNIEnv* env,
    jobject /* this */,
    jlong handle,
    jstring prompt
) {
    auto* llm = reinterpret_cast<LLMInference*>(handle);
    if (!llm) return;

    const char* promptCstr = env->GetStringUTFChars(prompt, nullptr);

    try {
        if (!llm->startCompletion(promptCstr)) {
            env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),
                         "Failed to start completion");
        }
    } catch (std::runtime_error& error) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
    }

    env->ReleaseStringUTFChars(prompt, promptCstr);
}

// Generate one token (call in loop from Kotlin)
extern "C" JNIEXPORT jstring JNICALL
Java_com_rapo_haloai_data_model_GGUFModelRuntime_completionLoop(
    JNIEnv* env,
    jobject /* this */,
    jlong handle
) {
    auto* llm = reinterpret_cast<LLMInference*>(handle);
    if (!llm) return nullptr;

    try {
        std::string piece = llm->completionLoop();
        return env->NewStringUTF(piece.c_str());
    } catch (std::runtime_error& error) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
        return nullptr;
    }
}

// Stop completion (finalize)
extern "C" JNIEXPORT void JNICALL
Java_com_rapo_haloai_data_model_GGUFModelRuntime_stopCompletion(
    JNIEnv* env,
    jobject /* this */,
    jlong handle
) {
    auto* llm = reinterpret_cast<LLMInference*>(handle);
    if (!llm) return;

    llm->stopCompletion();
}

// Get generation metrics
extern "C" JNIEXPORT jfloat JNICALL
Java_com_rapo_haloai_data_model_GGUFModelRuntime_getResponseGenerationSpeed(
    JNIEnv* env,
    jobject /* this */,
    jlong handle
) {
    auto* llm = reinterpret_cast<LLMInference*>(handle);
    return llm ? llm->getResponseGenerationTime() : 0.0f;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_rapo_haloai_data_model_GGUFModelRuntime_getContextSizeUsed(
    JNIEnv* env,
    jobject /* this */,
    jlong handle
) {
    auto* llm = reinterpret_cast<LLMInference*>(handle);
    return llm ? llm->getContextSizeUsed() : 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_rapo_haloai_data_model_GGUFModelRuntime_clearMessages(
    JNIEnv* env,
    jobject /* this */,
    jlong handle
) {
    auto* llm = reinterpret_cast<LLMInference*>(handle);
    if (llm) {
        llm->clearMessages();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_rapo_haloai_data_model_GGUFModelRuntime_freeModel(
    JNIEnv* env,
    jobject /* this */,
    jlong handle
) {
    auto* llm = reinterpret_cast<LLMInference*>(handle);
    if (llm) {
        LOGI("Freeing model");
        delete llm;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_rapo_haloai_data_model_GGUFModelRuntime_getModelInfo(
    JNIEnv* env,
    jobject /* this */,
    jlong handle
) {
    auto* llm = reinterpret_cast<LLMInference*>(handle);
    if (!llm) {
        return env->NewStringUTF("Model not loaded");
    }

    std::string info = llm->getModelInfo();
    return env->NewStringUTF(info.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_rapo_haloai_data_model_GGUFModelRuntime_startFreshConversation(
    JNIEnv* env,
    jobject /* this */,
    jlong handle
) {
    auto* llm = reinterpret_cast<LLMInference*>(handle);
    if (llm) {
        llm->startFreshConversation();
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_rapo_haloai_data_model_GGUFModelRuntime_isModelLoaded(
    JNIEnv* env,
    jobject /* this */,
    jlong handle
) {
    auto* llm = reinterpret_cast<LLMInference*>(handle);
    return (llm && llm->isReady()) ? JNI_TRUE : JNI_FALSE;
}
