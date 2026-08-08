package com.maenqaddoura.mqvox

enum class Direction { LTR, RTL }

data class LanguageSpec(
    val id: String,
    val englishName: String,
    val nativeName: String,
    val bcp47: String,
    val direction: Direction,
    val whisperCode: String,
    val ttsLanguageCode: String,
)

/**
 * V4 intentionally proves Arabic <-> English before language expansion.
 * Future languages are added here and by adding matching ASR/MT/TTS assets;
 * the pipeline and UI do not require redesign.
 */
object LanguageRegistry {
    val all = listOf(
        LanguageSpec("ar", "Arabic", "العربية", "ar", Direction.RTL, "ar", "ar"),
        LanguageSpec("en", "English", "English", "en", Direction.LTR, "en", "en"),
    )

    fun byId(id: String) = all.first { it.id == id }
}
