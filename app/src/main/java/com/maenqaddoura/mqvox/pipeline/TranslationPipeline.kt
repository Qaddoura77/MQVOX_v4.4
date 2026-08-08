package com.maenqaddoura.mqvox.pipeline

import android.os.SystemClock
import com.maenqaddoura.mqvox.LanguageSpec
import com.maenqaddoura.mqvox.PipelineState
import com.maenqaddoura.mqvox.inference.AsrEngine
import com.maenqaddoura.mqvox.inference.SynthesizedAudio
import com.maenqaddoura.mqvox.inference.TranslationEngine
import com.maenqaddoura.mqvox.inference.TtsEngine

data class PipelineResult(
    val recognized: String,
    val translated: String,
    val audio: SynthesizedAudio?,
    val asrMs: Long,
    val mtMs: Long,
    val ttsMs: Long,
    val totalMs: Long,
)

class TranslationPipeline(
    private val asr: AsrEngine,
    private val mt: TranslationEngine,
    private val tts: TtsEngine,
) : AutoCloseable {

    fun run(
        samples: FloatArray,
        sampleRate: Int,
        source: LanguageSpec,
        target: LanguageSpec,
        autoSpeak: Boolean,
        state: (PipelineState) -> Unit,
        onRecognized: (String, Long) -> Unit = { _, _ -> },
        onTranslated: (String, Long) -> Unit = { _, _ -> },
    ): PipelineResult {
        val totalStart = SystemClock.elapsedRealtime()

        state(PipelineState.RECOGNIZING)
        var t0 = SystemClock.elapsedRealtime()
        val recognized = asr.transcribe(samples, sampleRate, source)
        val asrMs = SystemClock.elapsedRealtime() - t0
        onRecognized(recognized, asrMs)

        state(PipelineState.TRANSLATING)
        t0 = SystemClock.elapsedRealtime()
        val translated = mt.translate(recognized, source, target)
        val mtMs = SystemClock.elapsedRealtime() - t0
        onTranslated(translated, mtMs)

        var ttsMs = 0L
        val audio = if (autoSpeak) {
            state(PipelineState.SPEAKING)
            t0 = SystemClock.elapsedRealtime()
            val generated = tts.synthesize(translated, target)
            ttsMs = SystemClock.elapsedRealtime() - t0
            generated
        } else null

        state(PipelineState.COMPLETED)
        return PipelineResult(
            recognized = recognized,
            translated = translated,
            audio = audio,
            asrMs = asrMs,
            mtMs = mtMs,
            ttsMs = ttsMs,
            totalMs = SystemClock.elapsedRealtime() - totalStart,
        )
    }

    override fun close() {
        asr.close()
        mt.close()
        tts.close()
    }
}
