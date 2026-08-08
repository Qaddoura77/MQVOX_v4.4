package com.maenqaddoura.mqvox.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.roundToInt

object PcmPlayer {
    fun play(samples: FloatArray, sampleRate: Int, volume: Float = 1.0f) {
        if (samples.isEmpty()) return
        val shorts = ShortArray(samples.size) { i ->
            (samples[i].coerceIn(-1f, 1f) * 32767f * volume.coerceIn(0f, 1f)).roundToInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(shorts.size * 2)
            .build()
        track.write(shorts, 0, shorts.size)
        track.setNotificationMarkerPosition(shorts.size)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack) { t.stop(); t.release() }
            override fun onPeriodicNotification(t: AudioTrack) = Unit
        })
        track.play()
    }
}
