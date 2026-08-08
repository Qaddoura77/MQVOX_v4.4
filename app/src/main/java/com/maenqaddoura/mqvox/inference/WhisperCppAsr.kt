package com.maenqaddoura.mqvox.inference

import com.maenqaddoura.mqvox.LanguageSpec

/**
 * whisper.cpp multilingual Small Q5_1 backend.
 * The manually selected source language is forced into Whisper; language
 * auto-detection is deliberately disabled for speed and short-utterance reliability.
 */
class WhisperCppAsr(modelPath: String, threads: Int = 4) : AsrEngine {
    private var handle: Long = nativeCreate(modelPath, threads)

    init {
        check(handle != 0L) { "Failed to load whisper.cpp model: $modelPath" }
    }

    override fun transcribe(samples: FloatArray, sampleRate: Int, language: LanguageSpec): String {
        require(samples.isNotEmpty()) { "No microphone samples captured" }
        require(sampleRate == 16000) { "MQVOX ASR expects 16 kHz mono audio" }
        val text = nativeTranscribe(handle, samples, language.whisperCode).trim()
        check(text.isNotBlank()) { "No speech recognized" }
        return text
    }

    override fun close() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0L
        }
    }

    private external fun nativeCreate(modelPath: String, threads: Int): Long
    private external fun nativeTranscribe(handle: Long, samples: FloatArray, language: String): String
    private external fun nativeDestroy(handle: Long)

    companion object {
        init { System.loadLibrary("mqvox_native") }
    }
}
