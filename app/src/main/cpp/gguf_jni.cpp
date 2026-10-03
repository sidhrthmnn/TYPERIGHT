#include <jni.h>
#include <llama.h>
#include <algorithm>
#include <chrono>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>

namespace {
std::mutex inferenceMutex;
std::once_flag backendInit;
using Model = std::unique_ptr<llama_model, decltype(&llama_model_free)>;
Model cachedModel(nullptr, llama_model_free);
std::string cachedPath;
std::string bytes(JNIEnv *env, jbyteArray value) {
    const auto length = env->GetArrayLength(value);
    std::string result(length, '\0');
    env->GetByteArrayRegion(value, 0, length, reinterpret_cast<jbyte *>(result.data()));
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_GgufNative_release(JNIEnv *env, jobject, jbyteArray modelPath) {
    std::lock_guard<std::mutex> lock(inferenceMutex);
    if (cachedPath == bytes(env, modelPath)) {
        cachedModel.reset();
        cachedPath.clear();
    }
}
}

// Byte arrays preserve standard UTF-8 (including emoji), unlike JNI modified UTF-8.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_GgufNative_generate(JNIEnv *env, jobject, jbyteArray modelPath,
                                     jbyteArray promptBytes, jobject cancellation) {
    try {
        std::lock_guard<std::mutex> lock(inferenceMutex);
        std::call_once(backendInit, [] { llama_backend_init(); });
        const auto cancellationClass = env->GetObjectClass(cancellation);
        const auto isCancelled = env->GetMethodID(cancellationClass, "isCancelled", "()Z");
        const auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(180);
        const auto check = [&] {
            if (env->CallBooleanMethod(cancellation, isCancelled))
                throw std::runtime_error("Local polish cancelled");
            if (std::chrono::steady_clock::now() >= deadline)
                throw std::runtime_error("Local polish timed out; try a shorter selection");
        };
        check();
        const auto path = bytes(env, modelPath);
        const auto prompt = bytes(env, promptBytes);
        auto modelParams = llama_model_default_params();
        modelParams.n_gpu_layers = 0;
        modelParams.load_mode = LLAMA_LOAD_MODE_MMAP;
        // Gemma 4's large per-layer embeddings are lookup tables; read their rows on demand.
        modelParams.lazy_mode = LLAMA_LAZY_MODE_ON;
        if (cachedPath != path || !cachedModel) {
            cachedModel.reset();
            cachedPath.clear();
            cachedModel.reset(llama_model_load_from_file(path.c_str(), modelParams));
            if (cachedModel) cachedPath = path;
        }
        auto *model = cachedModel.get();
        if (!model) throw std::runtime_error("Cannot load local model; check available memory");
        check();
        const auto *vocab = llama_model_get_vocab(model);
        const int count = -llama_tokenize(vocab, prompt.data(), prompt.size(), nullptr, 0, true, true);
        constexpr int maxOutput = 384;
        constexpr int contextSize = 2048;
        if (count <= 0 || count > contextSize - maxOutput)
            throw std::runtime_error("Selection is too long for local polish; select less text");
        std::vector<llama_token> tokens(count);
        if (llama_tokenize(vocab, prompt.data(), prompt.size(), tokens.data(), count, true, true) != count)
            throw std::runtime_error("Cannot tokenize local polish input");
        auto params = llama_context_default_params();
        params.n_ctx = contextSize;
        params.n_batch = 128;
        params.n_ubatch = 128;
        params.n_threads = std::max(1u, std::min(4u, std::thread::hardware_concurrency()));
        params.n_threads_batch = params.n_threads;
        using Context = std::unique_ptr<llama_context, decltype(&llama_free)>;
        Context ctx(llama_init_from_model(model, params), llama_free);
        if (!ctx) throw std::runtime_error("Not enough memory for local polish");
        using Sampler = std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)>;
        Sampler sampler(llama_sampler_init_greedy(), llama_sampler_free);
        for (int offset = 0; offset < count; offset += 128) {
            check();
            auto batch = llama_batch_get_one(tokens.data() + offset, std::min(128, count - offset));
            if (llama_decode(ctx.get(), batch)) throw std::runtime_error("Local prompt evaluation failed");
        }
        std::string output;
        bool complete = false;
        for (int i = 0; i < maxOutput; ++i) {
            check();
            auto token = llama_sampler_sample(sampler.get(), ctx.get(), -1);
            if (llama_vocab_is_eog(vocab, token)) { complete = true; break; }
            std::vector<char> piece(256);
            int length = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
            if (length < 0) {
                piece.resize(-length);
                length = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
            }
            if (length < 0) throw std::runtime_error("Cannot decode local model output");
            output.append(piece.data(), length);
            auto batch = llama_batch_get_one(&token, 1);
            if (llama_decode(ctx.get(), batch)) throw std::runtime_error("Local generation failed");
        }
        if (!complete) throw std::runtime_error("Local output exceeded its limit; select less text");
        auto result = env->NewByteArray(output.size());
        if (result) env->SetByteArrayRegion(result, 0, output.size(), reinterpret_cast<const jbyte *>(output.data()));
        return result;
    } catch (const std::exception &error) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
        return nullptr;
    } catch (...) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Local inference failed");
        return nullptr;
    }
}
