package com.maenqaddoura.mqvox.inference

import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import com.maenqaddoura.mqvox.LanguageSpec
import java.io.File

/**
 * Supertonic 3 offline TTS backend used by MQVOX v4 for Arabic and English.
 * The OpenRAIL-M model is accepted for this personal/offline test checkpoint but remains
 * subject to licence review before any proprietary commercial release.
 */
class SherpaSupertonicTts(private val modelDir: File) : TtsEngine {
    private var tts: OfflineTts? = null

    private fun engine(): OfflineTts {
        tts?.let { return it }
        fun f(name: String) = File(modelDir, name).also { check(it.isFile) { "TTS asset missing: ${it.absolutePath}" } }.absolutePath
        val cfg = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                supertonic = OfflineTtsSupertonicModelConfig(
                    durationPredictor = f("duration_predictor.int8.onnx"),
                    textEncoder = f("text_encoder.int8.onnx"),
                    vectorEstimator = f("vector_estimator.int8.onnx"),
                    vocoder = f("vocoder.int8.onnx"),
                    ttsJson = f("tts.json"),
                    unicodeIndexer = f("unicode_indexer.bin"),
                    voiceStyle = f("voice.bin")
                ),
                numThreads = 4,
                debug = false,
                provider = "cpu"
            ),
            maxNumSentences = 1,
            silenceScale = 0.2f
        )
        return OfflineTts(config = cfg).also { tts = it }
    }

    override fun synthesize(text: String, language: LanguageSpec, speed: Float): SynthesizedAudio {
        val lang = language.ttsLanguageCode
        val config = GenerationConfig(speed = speed, sid = 0, numSteps = 6, extra = mapOf("lang" to lang))
        val a = engine().generateWithConfig(text, config)
        check(a.samples.isNotEmpty()) { "Offline TTS generated no audio" }
        return SynthesizedAudio(a.samples, a.sampleRate)
    }

    override fun close() { tts?.release(); tts = null }
}
