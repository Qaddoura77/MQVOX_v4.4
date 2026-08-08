package com.maenqaddoura.mqvox.inference

import com.maenqaddoura.mqvox.LanguageSpec

data class SynthesizedAudio(val samples: FloatArray, val sampleRate: Int)
interface AsrEngine : AutoCloseable { fun transcribe(samples: FloatArray, sampleRate: Int, language: LanguageSpec): String }
interface TranslationEngine : AutoCloseable { fun translate(text: String, source: LanguageSpec, target: LanguageSpec): String }
interface TtsEngine : AutoCloseable { fun synthesize(text: String, language: LanguageSpec, speed: Float = 1f): SynthesizedAudio }
