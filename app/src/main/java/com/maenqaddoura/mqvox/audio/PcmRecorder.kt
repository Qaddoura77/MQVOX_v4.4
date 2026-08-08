package com.maenqaddoura.mqvox.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class PcmRecorder(private val context: Context) {
    val sampleRate = 16000
    private val recording = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var worker: Thread? = null
    private val pcm = ByteArrayOutputStream()

    @SuppressLint("MissingPermission")
    fun start() {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "Microphone permission not granted"
        }
        pcm.reset()
        val min = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(min > 0) { "AudioRecord buffer unavailable" }
        val r = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, min * 2)
        check(r.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord initialization failed" }
        audioRecord = r
        recording.set(true)
        r.startRecording()
        worker = Thread {
            val buf = ByteArray(min)
            while (recording.get()) {
                val n = r.read(buf, 0, buf.size)
                if (n > 0) synchronized(pcm) { pcm.write(buf, 0, n) }
            }
        }.also { it.name = "mqvox-audio-record"; it.start() }
    }

    fun stop(): FloatArray {
        recording.set(false)
        audioRecord?.let { runCatching { it.stop() } }
        worker?.join(1500)
        audioRecord?.release(); audioRecord = null; worker = null
        val b = synchronized(pcm) { pcm.toByteArray() }
        val n = b.size / 2
        return FloatArray(n) { i ->
            val lo = b[i*2].toInt() and 0xff
            val hi = b[i*2+1].toInt()
            val s = (hi shl 8) or lo
            s.toShort() / 32768.0f
        }
    }

    fun isRecording() = recording.get()
}
