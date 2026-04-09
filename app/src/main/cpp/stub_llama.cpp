#include <jni.h>
#include <android/log.h>
#include <string>
#include <atomic>

#define LOG_TAG "ElysiumNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::atomic<bool> g_model_loaded{false};
static std::atomic<bool> g_is_generating{false};

// Helper to convert JNI string to C++ string
static std::string jstring_to_string(JNIEnv* env, jstring jStr) {
    if (!jStr) return "";
    const char* chars = env->GetStringUTFChars(jStr, nullptr);
    std::string str(chars);
    env->ReleaseStringUTFChars(jStr, chars);
    return str;
}

static jstring string_to_jstring(JNIEnv* env, const std::string& str) {
    return env->NewStringUTF(str.c_str());
}

extern "C" {

/**
 * Initialize the llama.cpp backend (stub)
 */
JNIEXPORT void JNICALL
Java_com_elysium_code_ai_LlamaEngine_nativeInit(JNIEnv* env, jobject thiz) {
    LOGI("Initializing llama.cpp backend (STUB)");
}

/**
 * Load a GGUF model from file path (stub)
 * Returns true on success
 */
JNIEXPORT jboolean JNICALL
Java_com_elysium_code_ai_LlamaEngine_nativeLoadModel(
    JNIEnv* env, jobject thiz,
    jstring modelPath,
    jint nCtx,
    jint nThreads,
    jint nGpuLayers,
    jboolean useMmap,
    jboolean useMlock
) {
    std::string path = jstring_to_string(env, modelPath);
    LOGI("Loading model from: %s (STUB - simulating load)", path.c_str());
    LOGI("  Context: %d, Threads: %d, GPULayers: %d", nCtx, nThreads, nGpuLayers);
    g_model_loaded = true;
    return JNI_TRUE;
}

/**
 * Unload model (stub)
 */
JNIEXPORT void JNICALL
Java_com_elysium_code_ai_LlamaEngine_nativeUnloadModel(JNIEnv* env, jobject thiz) {
    LOGI("Unloading model (STUB)");
    g_model_loaded = false;
}

/**
 * Check if model is loaded (stub)
 */
JNIEXPORT jboolean JNICALL
Java_com_elysium_code_ai_LlamaEngine_nativeIsModelLoaded(JNIEnv* env, jobject thiz) {
    return g_model_loaded ? JNI_TRUE : JNI_FALSE;
}

/**
 * Check if generation is in progress (stub)
 */
JNIEXPORT jboolean JNICALL
Java_com_elysium_code_ai_LlamaEngine_nativeIsGenerating(JNIEnv* env, jobject thiz) {
    return g_is_generating ? JNI_TRUE : JNI_FALSE;
}

/**
 * Perform inference (stub)
 */
JNIEXPORT jstring JNICALL
Java_com_elysium_code_ai_LlamaEngine_nativeInfer(
    JNIEnv* env, jobject thiz,
    jstring prompt,
    jint maxTokens,
    jfloat temperature,
    jfloat topP,
    jint topK,
    jfloat repeatPenalty,
    jobject callback
) {
    std::string prompt_str = jstring_to_string(env, prompt);
    LOGI("Starting inference (STUB) with prompt: %s", prompt_str.c_str());
    LOGI("  Max tokens: %d, Temperature: %f", maxTokens, temperature);
    
    g_is_generating = true;
    
    // Simulate token streaming through callback
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    
    if (onTokenMethod) {
        for (int i = 0; i < 5; i++) {
            jstring token = env->NewStringUTF(" token");
            env->CallVoidMethod(callback, onTokenMethod, token);
            env->DeleteLocalRef(token);
        }
    }
    
    jmethodID onCompleteMethod = env->GetMethodID(callbackClass, "onComplete", "(Ljava/lang/String;)V");
    if (onCompleteMethod) {
        jstring result = env->NewStringUTF("[STUB RESPONSE: Inference not implemented]");
        env->CallVoidMethod(callback, onCompleteMethod, result);
        env->DeleteLocalRef(result);
    }
    
    env->DeleteLocalRef(callbackClass);
    
    g_is_generating = false;
    
    return env->NewStringUTF("[STUB: Inference disabled - compile with real llama.cpp]");
}

/**
 * Cancel inference (stub)
 */
JNIEXPORT void JNICALL
Java_com_elysium_code_ai_LlamaEngine_nativeCancelInference(JNIEnv* env, jobject thiz) {
    LOGI("Canceling inference (STUB)");
    g_is_generating = false;
}

/**
 * Get model info (stub)
 */
JNIEXPORT jstring JNICALL
Java_com_elysium_code_ai_LlamaEngine_nativeGetModelInfo(JNIEnv* env, jobject thiz) {
    return env->NewStringUTF("{\"model\": \"Gemma 4 E4B (STUB)\", \"status\": \"Stub implementation - compile with real llama.cpp\"}");
}

/**
 * Tokenize text (stub)
 */
JNIEXPORT jint JNICALL
Java_com_elysium_code_ai_LlamaEngine_nativeTokenize(JNIEnv* env, jobject thiz, jstring text) {
    std::string text_str = jstring_to_string(env, text);
    // Rough estimate: ~1 token per 4 characters
    return (int)(text_str.length() / 4);
}

/**
 * Get context size (stub)
 */
JNIEXPORT jint JNICALL
Java_com_elysium_code_ai_LlamaEngine_nativeGetContextSize(JNIEnv* env, jobject thiz) {
    return 4096;
}

/**
 * Cleanup (stub)
 */
JNIEXPORT void JNICALL
Java_com_elysium_code_ai_LlamaEngine_nativeCleanup(JNIEnv* env, jobject thiz) {
    LOGI("Cleaning up llama.cpp backend (STUB)");
    g_model_loaded = false;
    g_is_generating = false;
}

} // extern "C"
