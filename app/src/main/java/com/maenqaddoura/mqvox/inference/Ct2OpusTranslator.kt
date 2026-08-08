package com.maenqaddoura.mqvox.inference

import com.maenqaddoura.mqvox.LanguageSpec
import java.io.File

/**
 * Dedicated OPUS-MT translation through CTranslate2 INT8 on ARM64.
 * Only one direction is kept resident at a time to limit Moto G54 RAM use.
 */
class Ct2OpusTranslator(private val modelRoot: File, private val threads: Int = 4) : TranslationEngine {
    private var handle: Long = 0L
    private var direction: String? = null

    @Synchronized
    fun warmup(source: LanguageSpec, target: LanguageSpec) {
        ensureDirection(source, target)
    }

    @Synchronized
    override fun translate(text: String, source: LanguageSpec, target: LanguageSpec): String {
        require(text.isNotBlank()) { "Nothing to translate" }
        ensureDirection(source, target)
        val prefix = if (source.id == "en" && target.id == "ar") ">>ara<<" else ""
        val result = nativeTranslate(handle, text, prefix).trim()
        check(result.isNotBlank()) { "Offline OPUS-MT returned an empty translation" }
        return result
    }

    private fun ensureDirection(source: LanguageSpec, target: LanguageSpec) {
        val key = "${source.id}-${target.id}"
        require(key == "ar-en" || key == "en-ar") {
            "MQVOX v4 currently supports Arabic ↔ English only"
        }
        if (handle != 0L && direction == key) return
        if (handle != 0L) nativeDestroy(handle)

        val dir = File(modelRoot, key)
        val modelDir = File(dir, "ct2")
        val sourceSpm = File(dir, "source.spm")
        val targetSpm = File(dir, "target.spm")
        check(modelDir.isDirectory) { "MT model missing: ${modelDir.absolutePath}" }
        check(sourceSpm.isFile) { "MT tokenizer missing: ${sourceSpm.absolutePath}" }
        check(targetSpm.isFile) { "MT tokenizer missing: ${targetSpm.absolutePath}" }

        handle = nativeCreate(
            modelDir.absolutePath,
            sourceSpm.absolutePath,
            targetSpm.absolutePath,
            threads
        )
        check(handle != 0L) { "Failed to load OPUS-MT direction $key" }
        direction = key
    }

    @Synchronized
    override fun close() {
        if (handle != 0L) nativeDestroy(handle)
        handle = 0L
        direction = null
    }

    private external fun nativeCreate(modelDir: String, sourceSpm: String, targetSpm: String, threads: Int): Long
    private external fun nativeTranslate(handle: Long, text: String, targetPrefix: String): String
    private external fun nativeDestroy(handle: Long)

    companion object {
        init { System.loadLibrary("mqvox_native") }
    }
}
