#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <mutex>
#include <set>
#include <map>
#include "llama.h"

// Setup Android Logcat macros
#define TAG "LlamaBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Global pointers and mutex to protect the engine
llama_model* g_model = nullptr;
llama_context* g_ctx = nullptr;
std::mutex g_mutex;

// Helper functions for batch management
static void batch_add(struct llama_batch & batch, llama_token id, llama_pos pos, const std::vector<llama_seq_id> & seq_ids, bool logits) {
    batch.token   [batch.n_tokens] = id;
    batch.pos     [batch.n_tokens] = pos;
    batch.n_seq_id[batch.n_tokens] = seq_ids.size();
    for (size_t i = 0; i < seq_ids.size(); ++i) {
        batch.seq_id[batch.n_tokens][i] = seq_ids[i];
    }
    batch.logits  [batch.n_tokens] = logits;
    batch.n_tokens++;
}

static void batch_clear(struct llama_batch & batch) {
    batch.n_tokens = 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_psimandan_neuread_voice_LlamaEngine_initModel(JNIEnv *env, jobject thiz, jstring modelPath) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Initializing LLaMA model from: %s", path);

    llama_backend_init();

    llama_model_params model_params = llama_model_default_params();
    g_model = llama_model_load_from_file(path, model_params);

    if (g_model == nullptr) {
        LOGE("CRITICAL: Failed to load GGUF model");
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 4096;
    ctx_params.n_batch = 4096;
    ctx_params.n_threads = 4;
    ctx_params.n_threads_batch = 4;

    g_ctx = llama_init_from_model(g_model, ctx_params);

    if (g_ctx == nullptr) {
        LOGE("CRITICAL: Failed to create LLaMA context");
        llama_model_free(g_model);
        g_model = nullptr;
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }

    env->ReleaseStringUTFChars(modelPath, path);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_psimandan_neuread_voice_LlamaEngine_generateSpeechTokens(JNIEnv *env, jobject thiz, jstring prompt) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_model == nullptr || g_ctx == nullptr) {
        LOGE("Model is not initialized!");
        return env->NewIntArray(0);
    }

    const char *prompt_chars = env->GetStringUTFChars(prompt, nullptr);
    std::string text_prompt(prompt_chars);
    env->ReleaseStringUTFChars(prompt, prompt_chars);

    const struct llama_vocab * vocab = llama_model_get_vocab(g_model);

    LOGI("Clearing KV cache");
    llama_memory_seq_rm(llama_get_memory(g_ctx), -1, -1, -1);

    std::vector<llama_token> tokens_list(text_prompt.length() + 32);
    int n_tokens = llama_tokenize(vocab, text_prompt.c_str(), text_prompt.length(), tokens_list.data(), tokens_list.size(), false, true);
    if (n_tokens < 0) {
        tokens_list.resize(-n_tokens);
        n_tokens = llama_tokenize(vocab, text_prompt.c_str(), text_prompt.length(), tokens_list.data(), tokens_list.size(), false, true);
    }
    tokens_list.resize(n_tokens);

    uint32_t n_batch_size = std::max((uint32_t)n_tokens, (uint32_t)2048);
    llama_batch batch = llama_batch_init(n_batch_size, 0, 1);

    for (size_t i = 0; i < tokens_list.size(); i++) {
        batch_add(batch, tokens_list[i], i, { 0 }, false);
    }
    if (batch.n_tokens > 0) {
        batch.logits[batch.n_tokens - 1] = true;
    }

    if (llama_decode(g_ctx, batch) != 0) {
        LOGE("llama_decode() failed");
        llama_batch_free(batch);
        return env->NewIntArray(0);
    }

    // 4. Generation Loop
    std::vector<int> generated_speech_tokens;
    int n_cur = batch.n_tokens;
    const int max_new_tokens = 4096;

    // Sampler chain optimization to eliminate "mumbles and screams"
    auto sparams = llama_sampler_chain_default_params();
    llama_sampler * smpl = llama_sampler_chain_init(sparams);
    if (smpl == nullptr) {
        LOGE("Failed to initialize sampler chain");
        llama_batch_free(batch);
        return env->NewIntArray(0);
    }

    // 1. Accept prompt to avoid immediate repetition
    for (size_t i = 0; i < (size_t)n_tokens; i++) {
        llama_sampler_accept(smpl, tokens_list[i]);
    }

    // 2. Penalties: Aggressive repetition penalty to break "mumbles"
    // Increased repeat to 2.5 and freq/presence to 1.2
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(256, 2.50f, 1.20f, 1.20f));

    // 3. Filter candidates to prevent "screams" (low-probability noise)
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_min_p(0.10f, 1)); // Increased min_p to 0.10

    // 4. Greedy sampling for absolute stability
    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());

    const int SPEECH_START_ID = 151671;
    const int SPEECH_END_ID   = 217207;

    llama_token last_token = -1;
    int repeat_count = 0;

    while (n_cur < llama_n_ctx(g_ctx) - 1 && generated_speech_tokens.size() < (size_t)max_new_tokens) {
        llama_token id = llama_sampler_sample(smpl, g_ctx, -1);

        if (id == LLAMA_TOKEN_NULL) break;

        // Log token to identify what "i" actually is
        if (n_cur % 50 == 0) {
            LOGI("Generating... current token: %d (pos %d)", (int)id, n_cur);
        }

        bool is_speech = (id >= SPEECH_START_ID && id <= SPEECH_END_ID);
        bool is_eog = llama_vocab_is_eog(vocab, id) || id == 151643 || id == 151645 || id == 151670;

        if (is_eog || !is_speech) {
            LOGI("Stopping: EOG=%d, Speech=%d, token=%d", is_eog, is_speech, (int)id);
            break;
        }

        llama_sampler_accept(smpl, id);

        // Immediate repetition protection (Tightened to 4)
        if (id == last_token) {
            repeat_count++;
        } else {
            repeat_count = 0;
        }
        last_token = id;

        if (repeat_count > 4) {
            LOGI("Hard break: token %d repeated %d times", (int)id, repeat_count);
            break;
        }

        // Loop protection: check for A-B-A-B style mumbles
        if (generated_speech_tokens.size() >= 12) {
            std::set<int> tiny_window;
            for (int i = 0; i < 12; ++i) {
                tiny_window.insert(generated_speech_tokens[generated_speech_tokens.size() - 1 - i]);
            }
            if (tiny_window.size() <= 3) {
                LOGI("Stutter break (Loop): only %zu unique in last 12", tiny_window.size());
                goto break_loop;
            }
        }

        // Stutter protection: check if any token is overly dominant in a window
        if (generated_speech_tokens.size() >= 30) {
            std::map<int, int> counts;
            int window = 30;
            for (int i = 0; i < window; ++i) {
                counts[generated_speech_tokens[generated_speech_tokens.size() - 1 - i]]++;
            }
            for (auto const& [val, count] : counts) {
                if (count > 18) { // >60% same token in window
                    LOGI("Stutter break: token %d appeared %d times in last %d", val, count, window);
                    goto break_loop;
                }
            }
        }

        // Diversity check: Global stagnation protection
        if (generated_speech_tokens.size() >= 80) {
            std::set<int> unique_tokens;
            int window = 80;
            for (int i = 0; i < window; ++i) {
                unique_tokens.insert(generated_speech_tokens[generated_speech_tokens.size() - 1 - i]);
            }
            if (unique_tokens.size() < 12) {
                LOGI("Diversity break: only %zu unique tokens in last %d", unique_tokens.size(), window);
                break;
            }
        }

        generated_speech_tokens.push_back((int)id);

        batch_clear(batch);
        batch_add(batch, id, n_cur, { 0 }, true);

        if (llama_decode(g_ctx, batch) != 0) break;
        n_cur++;
    }
break_loop:
    llama_sampler_free(smpl);
    llama_batch_free(batch);

    jintArray result = env->NewIntArray(generated_speech_tokens.size());
    if (!generated_speech_tokens.empty()) {
        env->SetIntArrayRegion(result, 0, generated_speech_tokens.size(), (const jint*)generated_speech_tokens.data());
    }
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_psimandan_neuread_voice_LlamaEngine_freeModel(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    llama_backend_free();
}
