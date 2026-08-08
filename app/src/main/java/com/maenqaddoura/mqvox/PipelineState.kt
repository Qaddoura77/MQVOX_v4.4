package com.maenqaddoura.mqvox

enum class PipelineState(val label: String) {
    READY("Ready"), LISTENING("Listening…"), RECOGNIZING("Recognizing…"),
    TRANSLATING("Translating…"), SPEAKING("Speaking…"), COMPLETED("Completed"), ERROR("Error")
}
