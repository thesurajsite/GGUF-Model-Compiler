#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"

#define TAG "LlamaAndroid"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct LlamaSession {
    llama_model   *model = nullptr;
    llama_context *ctx   = nullptr;
};

extern "C"
JNIEXPORT jlong JNICALL
Java_com_suraj_ggufmodelrunner_data_LlamaBridge_loadModel(
        JNIEnv *env, jobject, jstring modelPath, jint nCtx, jint nThreads) {

    const char *path = env->GetStringUTFChars(modelPath, nullptr);

    llama_model_params model_params = llama_model_default_params();
    llama_model *model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!model) {
        LOGE("Failed to load model");
        return 0;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = nCtx > 0 ? nCtx : 2048;
    ctx_params.n_threads = nThreads > 0 ? nThreads : 4;
    ctx_params.n_threads_batch = ctx_params.n_threads;

    llama_context *ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        LOGE("Failed to create context");
        llama_model_free(model);
        return 0;
    }

    auto *session = new LlamaSession{model, ctx};
    return reinterpret_cast<jlong>(session);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_suraj_ggufmodelrunner_data_LlamaBridge_generate(
        JNIEnv *env, jobject, jlong sessionPtr, jstring prompt, jint maxTokens) {

    auto *session = reinterpret_cast<LlamaSession *>(sessionPtr);
    if (!session || !session->ctx) return env->NewStringUTF("");

    const char *promptChars = env->GetStringUTFChars(prompt, nullptr);
    std::string promptStr(promptChars);
    env->ReleaseStringUTFChars(prompt, promptChars);

    const llama_vocab *vocab = llama_model_get_vocab(session->model);

    int nPromptTokens = -llama_tokenize(vocab, promptStr.c_str(), (int32_t)promptStr.size(),
                                         nullptr, 0, true, true);
    std::vector<llama_token> tokens(nPromptTokens);
    llama_tokenize(vocab, promptStr.c_str(), (int32_t)promptStr.size(),
                    tokens.data(), (int32_t)tokens.size(), true, true);

    llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t)tokens.size());

    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    std::string result;
    int nDecoded = 0;

    while (nDecoded < maxTokens) {
        if (llama_decode(session->ctx, batch) != 0) {
            LOGE("llama_decode failed");
            break;
        }

        llama_token newToken = llama_sampler_sample(sampler, session->ctx, -1);
        if (llama_vocab_is_eog(vocab, newToken)) break;

        char buf[256];
        int n = llama_token_to_piece(vocab, newToken, buf, sizeof(buf), 0, true);
        if (n > 0) result.append(buf, n);

        batch = llama_batch_get_one(&newToken, 1);
        nDecoded++;
    }

    llama_sampler_free(sampler);
    return env->NewStringUTF(result.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_suraj_ggufmodelrunner_data_LlamaBridge_freeModel(
        JNIEnv *env, jobject, jlong sessionPtr) {

    auto *session = reinterpret_cast<LlamaSession *>(sessionPtr);
    if (session) {
        if (session->ctx)   llama_free(session->ctx);
        if (session->model) llama_model_free(session->model);
        delete session;
    }
}