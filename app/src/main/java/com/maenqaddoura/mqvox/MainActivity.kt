package com.maenqaddoura.mqvox

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.maenqaddoura.mqvox.audio.PcmPlayer
import com.maenqaddoura.mqvox.audio.PcmRecorder
import com.maenqaddoura.mqvox.inference.Ct2OpusTranslator
import com.maenqaddoura.mqvox.inference.SherpaSupertonicTts
import com.maenqaddoura.mqvox.inference.WhisperCppAsr
import com.maenqaddoura.mqvox.pipeline.TranslationPipeline
import com.maenqaddoura.mqvox.util.AssetInstaller
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var sourceSpinner: Spinner
    private lateinit var targetSpinner: Spinner
    private lateinit var status: TextView
    private lateinit var sourceText: TextView
    private lateinit var translationText: TextView
    private lateinit var mic: Button
    private lateinit var autoSpeak: SwitchMaterial

    private val recorder by lazy { PcmRecorder(this) }
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile private var pipeline: TranslationPipeline? = null
    @Volatile private var lastTarget: LanguageSpec? = null

    private val requestMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginRecording() else toast("Microphone permission is required")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sourceSpinner = findViewById(R.id.sourceLanguage)
        targetSpinner = findViewById(R.id.targetLanguage)
        status = findViewById(R.id.statusText)
        sourceText = findViewById(R.id.sourceText)
        translationText = findViewById(R.id.translationText)
        mic = findViewById(R.id.micButton)
        autoSpeak = findViewById(R.id.autoSpeak)

        val labels = LanguageRegistry.all.map { "${it.nativeName} · ${it.englishName}" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        sourceSpinner.adapter = adapter
        targetSpinner.adapter = adapter
        sourceSpinner.setSelection(0)
        targetSpinner.setSelection(1)

        findViewById<Button>(R.id.swapButton).setOnClickListener {
            val a = sourceSpinner.selectedItemPosition
            sourceSpinner.setSelection(targetSpinner.selectedItemPosition)
            targetSpinner.setSelection(a)
        }

        mic.setOnClickListener {
            if (recorder.isRecording()) endRecordingAndTranslate() else ensureMicAndStart()
        }

        findViewById<Button>(R.id.clearButton).setOnClickListener {
            sourceText.text = ""
            translationText.text = ""
            setState(PipelineState.READY)
        }

        findViewById<Button>(R.id.copyButton).setOnClickListener {
            val cm = getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("MQVOX translation", translationText.text))
            toast("Copied")
        }

        findViewById<Button>(R.id.replayButton).setOnClickListener { replay() }
        preloadOfflinePipeline()
    }

    private fun preloadOfflinePipeline() {
        mic.isEnabled = false
        status.text = "Preparing MQVOX v4 offline models… first launch may take a few minutes"
        executor.execute {
            try {
                getPipeline()
                runOnUiThread {
                    mic.isEnabled = true
                    status.text = "Ready · Arabic ↔ English · offline"
                }
            } catch (t: Throwable) {
                runOnUiThread { mic.isEnabled = false; fail(t) }
            }
        }
    }

    private fun ensureMicAndStart() {
        if (pipeline == null) { toast("Offline models are still preparing"); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) beginRecording()
        else requestMic.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun beginRecording() = runCatching {
        recorder.start()
        setState(PipelineState.LISTENING)
        mic.text = "■  Stop"
    }.onFailure { fail(it) }.let { Unit }

    private fun endRecordingAndTranslate() {
        val audio = recorder.stop()
        mic.text = "🎙  Speak"
        val src = LanguageRegistry.all[sourceSpinner.selectedItemPosition]
        val dst = LanguageRegistry.all[targetSpinner.selectedItemPosition]
        if (src.id == dst.id) { toast("Choose two different languages"); setState(PipelineState.READY); return }

        sourceText.text = ""
        translationText.text = ""
        executor.execute {
            try {
                val result = getPipeline().run(
                    samples = audio,
                    sampleRate = recorder.sampleRate,
                    source = src,
                    target = dst,
                    autoSpeak = autoSpeak.isChecked,
                    state = { s -> runOnUiThread { setState(s) } },
                    onRecognized = { text, ms ->
                        runOnUiThread {
                            sourceText.textDirection = if (src.direction == Direction.RTL) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
                            sourceText.text = text
                            status.text = "ASR ${formatSeconds(ms)} s · Translating…"
                        }
                    },
                    onTranslated = { text, ms ->
                        runOnUiThread {
                            translationText.textDirection = if (dst.direction == Direction.RTL) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
                            translationText.text = text
                            status.text = if (autoSpeak.isChecked) "MT ${formatSeconds(ms)} s · Speaking…" else "MT ${formatSeconds(ms)} s"
                        }
                    }
                )
                lastTarget = dst
                runOnUiThread {
                    result.audio?.let { PcmPlayer.play(it.samples, it.sampleRate) }
                    status.text = buildString {
                        append("ASR ${formatSeconds(result.asrMs)} s")
                        append(" · MT ${formatSeconds(result.mtMs)} s")
                        if (result.ttsMs > 0) append(" · TTS ${formatSeconds(result.ttsMs)} s")
                        append(" · TOTAL ${formatSeconds(result.totalMs)} s")
                    }
                }
            } catch (t: Throwable) {
                runOnUiThread { fail(t) }
            }
        }
    }

    @Synchronized
    private fun getPipeline(): TranslationPipeline {
        pipeline?.let { return it }
        val root = File(filesDir, "mqvox-models-v4")
        AssetInstaller.installTree(this, "models", root)

        val mt = Ct2OpusTranslator(File(root, "mt"), threads = 4)
        // Warm the default Arabic -> English model so the first translation does not pay model-load latency.
        mt.warmup(LanguageRegistry.byId("ar"), LanguageRegistry.byId("en"))

        return TranslationPipeline(
            WhisperCppAsr(File(root, "asr/whisper/ggml-small-q5_1.bin").absolutePath, threads = 4),
            mt,
            SherpaSupertonicTts(File(root, "tts/supertonic-3"))
        ).also { pipeline = it }
    }

    private fun replay() {
        val text = translationText.text.toString()
        val lang = lastTarget ?: return
        if (text.isBlank()) return
        executor.execute {
            runCatching {
                val root = File(filesDir, "mqvox-models-v4/tts/supertonic-3")
                SherpaSupertonicTts(root).use { engine ->
                    val audio = engine.synthesize(text, lang)
                    runOnUiThread { PcmPlayer.play(audio.samples, audio.sampleRate) }
                }
            }.onFailure { runOnUiThread { fail(it) } }
        }
    }

    private fun formatSeconds(ms: Long) = String.format(Locale.US, "%.1f", ms / 1000.0)
    private fun setState(s: PipelineState) { status.text = s.label }
    private fun fail(t: Throwable) { setState(PipelineState.ERROR); toast(t.message ?: t.javaClass.simpleName) }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        pipeline?.close()
        executor.shutdownNow()
        super.onDestroy()
    }
}
