#include <jni.h>
#include <algorithm>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

#include "whisper.h"
#include "ctranslate2/translator.h"
#include "sentencepiece_processor.h"

namespace {

std::string jstring_to_utf8(JNIEnv* env, jstring value) {
    if (!value) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string out(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return out;
}

jstring utf8_to_jstring(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

struct WhisperHandle {
    whisper_context* ctx = nullptr;
    int threads = 4;
    ~WhisperHandle() { if (ctx) whisper_free(ctx); }
};

struct MtHandle {
    std::unique_ptr<ctranslate2::Translator> translator;
    sentencepiece::SentencePieceProcessor source_sp;
    sentencepiece::SentencePieceProcessor target_sp;
};

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_maenqaddoura_mqvox_inference_WhisperCppAsr_nativeCreate(
        JNIEnv* env, jobject, jstring model_path_j, jint threads) {
    try {
        const std::string model_path = jstring_to_utf8(env, model_path_j);
        auto h = std::make_unique<WhisperHandle>();
        whisper_context_params cp = whisper_context_default_params();
        cp.use_gpu = false;
        h->ctx = whisper_init_from_file_with_params(model_path.c_str(), cp);
        if (!h->ctx) return 0;
        h->threads = std::max(1, static_cast<int>(threads));
        return reinterpret_cast<jlong>(h.release());
    } catch (...) {
        return 0;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_maenqaddoura_mqvox_inference_WhisperCppAsr_nativeTranscribe(
        JNIEnv* env, jobject, jlong handle, jfloatArray samples_j, jstring language_j) {
    auto* h = reinterpret_cast<WhisperHandle*>(handle);
    if (!h || !h->ctx || !samples_j) return utf8_to_jstring(env, "");

    const jsize n = env->GetArrayLength(samples_j);
    std::vector<float> samples(static_cast<size_t>(n));
    env->GetFloatArrayRegion(samples_j, 0, n, samples.data());
    const std::string language = jstring_to_utf8(env, language_j);

    whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.n_threads = h->threads;
    p.translate = false;
    p.no_context = true;
    p.no_timestamps = true;
    p.detect_language = false;
    p.single_segment = false;
    p.print_progress = false;
    p.print_realtime = false;
    p.print_timestamps = false;
    p.suppress_blank = true;
    p.temperature = 0.0f;
    p.language = language.c_str();

    if (whisper_full(h->ctx, p, samples.data(), static_cast<int>(samples.size())) != 0)
        return utf8_to_jstring(env, "");

    std::string out;
    const int segments = whisper_full_n_segments(h->ctx);
    for (int i = 0; i < segments; ++i) {
        const char* text = whisper_full_get_segment_text(h->ctx, i);
        if (text) out += text;
    }
    return utf8_to_jstring(env, out);
}

extern "C" JNIEXPORT void JNICALL
Java_com_maenqaddoura_mqvox_inference_WhisperCppAsr_nativeDestroy(
        JNIEnv*, jobject, jlong handle) {
    delete reinterpret_cast<WhisperHandle*>(handle);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_maenqaddoura_mqvox_inference_Ct2OpusTranslator_nativeCreate(
        JNIEnv* env, jobject, jstring model_dir_j, jstring source_spm_j,
        jstring target_spm_j, jint threads) {
    try {
        auto h = std::make_unique<MtHandle>();
        const std::string model_dir = jstring_to_utf8(env, model_dir_j);
        const std::string source_spm = jstring_to_utf8(env, source_spm_j);
        const std::string target_spm = jstring_to_utf8(env, target_spm_j);

        auto s1 = h->source_sp.Load(source_spm);
        auto s2 = h->target_sp.Load(target_spm);
        if (!s1.ok() || !s2.ok()) return 0;

        ctranslate2::ReplicaPoolConfig cfg;
        cfg.num_threads_per_replica = std::max(1, static_cast<int>(threads));
        h->translator = std::make_unique<ctranslate2::Translator>(
            model_dir,
            ctranslate2::Device::CPU,
            ctranslate2::ComputeType::DEFAULT,
            std::vector<int>{0},
            false,
            cfg
        );
        return reinterpret_cast<jlong>(h.release());
    } catch (...) {
        return 0;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_maenqaddoura_mqvox_inference_Ct2OpusTranslator_nativeTranslate(
        JNIEnv* env, jobject, jlong handle, jstring text_j, jstring prefix_j) {
    auto* h = reinterpret_cast<MtHandle*>(handle);
    if (!h || !h->translator) return utf8_to_jstring(env, "");

    try {
        std::string input = jstring_to_utf8(env, text_j);
        const std::string prefix = jstring_to_utf8(env, prefix_j);
        if (!prefix.empty()) input = prefix + " " + input;

        std::vector<std::string> pieces;
        auto status = h->source_sp.Encode(input, &pieces);
        if (!status.ok()) return utf8_to_jstring(env, "");

        // Models converted from Transformers expect tokenizer special tokens explicitly.
        pieces.push_back("</s>");
        const std::vector<std::vector<std::string>> batch{pieces};
        const auto results = h->translator->translate_batch(batch);
        if (results.empty()) return utf8_to_jstring(env, "");

        std::vector<std::string> clean;
        for (const auto& token : results.front().output()) {
            if (token == "</s>" || token == "<pad>" || token == ">>ara<<") continue;
            clean.push_back(token);
        }

        std::string output;
        auto decode_status = h->target_sp.Decode(clean, &output);
        if (!decode_status.ok()) return utf8_to_jstring(env, "");
        return utf8_to_jstring(env, output);
    } catch (...) {
        return utf8_to_jstring(env, "");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_maenqaddoura_mqvox_inference_Ct2OpusTranslator_nativeDestroy(
        JNIEnv*, jobject, jlong handle) {
    delete reinterpret_cast<MtHandle*>(handle);
}
