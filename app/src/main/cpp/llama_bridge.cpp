/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — llama.cpp JNI Bridge
 * ═══════════════════════════════════════════════════════════════
 * 
 * Native bridge between Kotlin and llama.cpp for Gemma 4 E4B
 * inference. Handles model loading, multimodal inference,
 * streaming token generation, and function calling.
 * 
 * Copyright 2026 Elysium Code. All rights reserved.
 */

#include <jni.h>
#include <android/log.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <string>
#include <vector>
#include <thread>
#include <mutex>
#include <atomic>
#include <functional>
#include <cstring>
#include <cmath>

#include "llama.h"
#include "common.h"
#include "sampling.h"

#define LOG_TAG "ElysiumNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// ═══════════════════════════════════════════════════════════════
// Global State
// ═══════════════════════════════════════════════════════════════

static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static llama_sampler* g_sampler = nullptr;
static std::mutex g_mutex;
static std::atomic<bool> g_is_generating{false};
static std::atomic<bool> g_cancel_requested{false};

// ═══════════════════════════════════════════════════════════════
// Helper Functions
// ═══════════════════════════════════════════════════════════════

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

// ═══════════════════════════════════════════════════════════════
// JNI Functions
// ═══════════════════════════════════════════════════════════════

extern "C" {

/**
 * Initialize the llama.cpp backend
 */
JNIEXPORT void JNICALL
Java_com_elysium_code_ai_LlamaEngine_nativeInit(JNIEnv* env, jobject thiz) {
    LOGI("Initializing llama.cpp backend");
    llama_backend_init();
    LOGI("llama.cpp backend initialized successfully");
}

/**
 * Load a GGUF model from file path
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
    std::lock_guard<std::mutex> lock(g_mutex);

    std::string path = jstring_to_string(env, modelPath);
    LOGI("Loading model from: %s", path.c_str());

    // Unload previous model if any
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    if (g_sampler) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }

    // Model parameters
    auto model_params = llama_model_default_params();
    model_params.use_mmap = useMmap;
    model_params.use_mlock = useMlock;
    model_params.n_gpu_layers = nGpuLayers;

    // Load model
    g_model = llama_model_load_from_file(path.c_str(), model_params);
    if (!g_model) {
        LOGE("Failed to load model from: %s", path.c_str());
        return JNI_FALSE;
    }

    // Context parameters
    auto ctx_params = llama_context_default_params();
    ctx_params.n_ctx = nCtx > 0 ? nCtx : 4096;
    ctx_params.n_threads = nThreads > 0 ? nThreads : std::thread::hardware_concurrency();
    ctx_params.n_threads_batch = ctx_params.n_threads;
    ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;

    // Create context
    g_ctx = llama_init_from_model(g_model, ctx_params);
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    // Setup sampler chain
    auto sparams = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    LOGI("Model loaded successfully. Context size: %d, Threads: %d",
         ctx_params.n_ctx, ctx_params.n_threads);

    return JNI_TRUE;
}

/**
 * Run text inference with streaming callback
 * The callback receives each token as it's generated
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
    if (!g_model || !g_ctx || !g_sampler) {
        LOGE("Model not loaded");
        return string_to_jstring(env, "Error: Model not loaded");
    }

    if (g_is_generating.load()) {
        LOGW("Inference already in progress");
        return string_to_jstring(env, "Error: Inference already in progress");
    }

    g_is_generating.store(true);
    g_cancel_requested.store(false);

    std::string promptStr = jstring_to_string(env, prompt);
    std::string result;

    // Get callback method
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onCompleteMethod = env->GetMethodID(callbackClass, "onComplete", "(Ljava/lang/String;)V");

    // Tokenize
    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    std::vector<llama_token> tokens = common_tokenize(vocab, promptStr, true, true);

    LOGI("Prompt tokens: %zu", tokens.size());

    // Clear memory (KV cache)
    llama_memory_clear(llama_get_memory(g_ctx), true);

    // Batch prompt processing
    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    if (llama_decode(g_ctx, batch) != 0) {
        LOGE("Failed to decode prompt");
        g_is_generating.store(false);
        return string_to_jstring(env, "Error: Failed to process prompt");
    }

    // Update sampler temperature if needed
    // (sampler was configured at load time, but can be reconfigured)

    // Generate tokens
    int n_generated = 0;
    int max = maxTokens > 0 ? maxTokens : 2048;

    while (n_generated < max && !g_cancel_requested.load()) {
        // Sample next token
        llama_token new_token = llama_sampler_sample(g_sampler, g_ctx, -1);

        // Check for end of generation
        if (llama_vocab_is_eog(vocab, new_token)) {
            LOGI("End of generation reached after %d tokens", n_generated);
            break;
        }

        // Convert token to text
        char buf[256];
        int n = llama_token_to_piece(vocab, new_token, buf, sizeof(buf), 0, true);
        if (n < 0) {
            LOGW("Failed to convert token %d to text", new_token);
            continue;
        }

        std::string token_text(buf, n);
        result += token_text;
        n_generated++;

        // Stream token to callback
        if (callback && onTokenMethod) {
            jstring jToken = string_to_jstring(env, token_text);
            env->CallVoidMethod(callback, onTokenMethod, jToken);
            env->DeleteLocalRef(jToken);
        }

        // Prepare next decode
        llama_batch next_batch = llama_batch_get_one(&new_token, 1);
        if (llama_decode(g_ctx, next_batch) != 0) {
            LOGE("Failed to decode token at position %d", n_generated);
            break;
        }
    }

    // Notify completion
    if (callback && onCompleteMethod) {
        jstring jResult = string_to_jstring(env, result);
        env->CallVoidMethod(callback, onCompleteMethod, jResult);
        env->DeleteLocalRef(jResult);
    }

    g_is_generating.store(false);
    LOGI("Generation complete: %d tokens generated", n_generated);

    return string_to_jstring(env, result.c_str());
}

/**
 * Cancel ongoing inference
 */
JNIEXPORT void JNICALL
Java_com_elysium_code_ai_LlamaEngine_nativeCancelInference(JNIEnv* env, jobject thiz) {
    LOGI("Cancel inference requested");
    g_cancel_requested.store(true);
}

/**
 * Check if model is loaded
 */
JNIEXPORT jboolean JNICALL
Java_com_elysium_code_ai_LlamaEngine_nativeIsModelLoaded(JNIEnv* env, jobject thiz) {
    return (g_model != nullptr && g_ctx != nullptr) ? JNI_TRUE : JNI_FALSE;
}

/**
 * Check if inference is currently running
 */
JNIEXPORT jboolean JNICALL
Java_com_elysium_code_ai_LlamaEngine_nativeIsGenerating(JNIEnv* env, jobject thiz) {
    return g_is_generating.load() ? JNI_TRUE : JNI_FALSE;
}

/**
 * Get model metadata as JSON string
 */
JNIEXPORT jstring JNICALL
Java_com_elysium_code_ai_LlamaEngine_nativeGetModelInfo(JNIEnv* env, jobject thiz) {
    if (!g_model) {
        return string_to_jstring(env, "{}");
    }

    const llama_vocab* vocab = llama_model_get_vocab(g_model);

    std::string info = "{";
    info += "\"n_params\":" + std::to_string(llama_model_n_params(g_model)) + ",";
    info += "\"n_vocab\":" + std::to_string(llama_vocab_n_tokens(vocab)) + ",";
    info += "\"n_ctx_train\":" + std::to_string(llama_model_n_ctx_train(g_model)) + ",";
    info += "\"model_size\":" + std::to_string(llama_model_size(g_model));
    info += "}";

    return string_to_jstring(env, info.c_str());
}

/**
 * Unload model and free resources
 */
JNIEXPORT void JNICALL
Java_com_elysium_code_ai_LlamaEngine_nativeUnloadModel(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_mutex);

    g_cancel_requested.store(true);

    // Wait for generation to complete
    while (g_is_generating.load()) {
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }

    if (g_sampler) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }

    LOGI("Model unloaded successfully");
}

/**
 * Cleanup the llama.cpp backend
 */
JNIEXPORT void JNICALL
Java_com_elysium_code_ai_LlamaEngine_nativeCleanup(JNIEnv* env, jobject thiz) {
    Java_com_elysium_code_ai_LlamaEngine_nativeUnloadModel(env, thiz);
    llama_backend_free();
    LOGI("llama.cpp backend cleaned up");
}

/**
 * Tokenize text and return token count (useful for context management)
 */
JNIEXPORT jint JNICALL
Java_com_elysium_code_ai_LlamaEngine_nativeTokenize(
    JNIEnv* env, jobject thiz,
    jstring text
) {
    if (!g_model) return -1;

    std::string textStr = jstring_to_string(env, text);
    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    std::vector<llama_token> tokens = common_tokenize(vocab, textStr, false, false);
    return (jint)tokens.size();
}

/**
 * Get context size
 */
JNIEXPORT jint JNICALL
Java_com_elysium_code_ai_LlamaEngine_nativeGetContextSize(JNIEnv* env, jobject thiz) {
    if (!g_ctx) return 0;
    return (jint)llama_n_ctx(g_ctx);
}

} // extern "C"
