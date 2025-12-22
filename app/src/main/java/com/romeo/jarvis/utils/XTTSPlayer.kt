package com.romeo.jarvis.utils

import android.media.*
import kotlin.math.sqrt

class XTTSPlayer(
    private val onLevel: (Float) -> Unit, // waveform callback 0..1
    private val onDone: () -> Unit
) {
    private var track: AudioTrack? = null

    fun playWavBytes(wav: ByteArray) {
        // Parse WAV header (simple 44-byte header)
        if (wav.size <= 44) return
        val pcm = wav.copyOfRange(44, wav.size)

        val sampleRate = 22050 // XTTS default; adjust if your server differs
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            maxOf(minBuf, pcm.size),
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        track?.play()

        // Stream + RMS analysis
        var i = 0
        val frame = 1024
        while (i < pcm.size) {
            val end = minOf(i + frame, pcm.size)
            track?.write(pcm, i, end - i)
            onLevel(rms16(pcm, i, end))
            i = end
        }

        track?.stop()
        track?.release()
        track = null
        onDone()
    }

    private fun rms16(buf: ByteArray, s: Int, e: Int): Float {
        var sum = 0.0
        var n = 0
        var i = s
        while (i + 1 < e) {
            val lo = buf[i].toInt() and 0xFF
            val hi = buf[i + 1].toInt()
            val v = (hi shl 8) or lo
            sum += v * v
            n++
            i += 2
        }
        return if (n == 0) 0f else (sqrt(sum / n) / 32768.0).toFloat().coerceIn(0f, 1f)
    }
}