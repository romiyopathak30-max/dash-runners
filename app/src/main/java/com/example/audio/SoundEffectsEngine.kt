package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class SoundCue {
  COIN,
  JUMP,
  ROLL,
  LANE_SWIPE,
  POWERUP,
  HOVERBOARD_ON,
  BOARD_SHIELD,
  STUMBLE,
  CRASH,
  UI_TAP,
}

class SoundEffectsEngine {
  private val executor = Executors.newSingleThreadExecutor()

  @Volatile
  var isMuted: Boolean = false

  private val buffers: Map<SoundCue, ShortArray> by lazy {
    mapOf(
      SoundCue.COIN to synthTone(freqStart = 1175.0, freqEnd = 1760.0, durationMs = 95, volume = 0.28),
      SoundCue.JUMP to synthTone(freqStart = 280.0, freqEnd = 640.0, durationMs = 135, volume = 0.28),
      SoundCue.ROLL to synthTone(freqStart = 320.0, freqEnd = 130.0, durationMs = 110, volume = 0.25),
      SoundCue.LANE_SWIPE to synthTone(freqStart = 420.0, freqEnd = 560.0, durationMs = 65, volume = 0.16),
      SoundCue.POWERUP to synthArpeggio(listOf(523.25, 659.25, 783.99, 1046.50), stepMs = 55, volume = 0.30),
      SoundCue.HOVERBOARD_ON to synthArpeggio(listOf(392.0, 587.33, 783.99), stepMs = 50, volume = 0.28),
      SoundCue.BOARD_SHIELD to synthTone(freqStart = 880.0, freqEnd = 220.0, durationMs = 260, volume = 0.36),
      SoundCue.STUMBLE to synthTone(freqStart = 190.0, freqEnd = 95.0, durationMs = 180, volume = 0.35),
      SoundCue.CRASH to synthCrash(durationMs = 340, volume = 0.42),
      SoundCue.UI_TAP to synthTone(freqStart = 660.0, freqEnd = 820.0, durationMs = 45, volume = 0.18),
    )
  }

  fun play(cue: SoundCue) {
    if (isMuted) return
    executor.execute {
      try {
        val pcm = buffers[cue] ?: return@execute
        val byteLen = pcm.size * 2
        val track =
          AudioTrack.Builder()
            .setAudioAttributes(
              AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            )
            .setAudioFormat(
              AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            )
            .setBufferSizeInBytes(byteLen)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(pcm, 0, pcm.size)
        track.play()
        Thread.sleep((pcm.size * 1000L / SAMPLE_RATE) + 30L)
        track.stop()
        track.release()
      } catch (_: Throwable) {
        // Audio hardware unavailable in headless test environments
      }
    }
  }

  fun release() {
    executor.shutdownNow()
  }

  private fun synthTone(
    freqStart: Double,
    freqEnd: Double,
    durationMs: Int,
    volume: Double,
  ): ShortArray {
    val samples = (SAMPLE_RATE * durationMs) / 1000
    val out = ShortArray(samples)
    var phase = 0.0
    for (i in 0 until samples) {
      val t = i.toDouble() / samples
      val freq = freqStart + (freqEnd - freqStart) * t
      phase += 2.0 * PI * freq / SAMPLE_RATE
      val env = (1.0 - t) * exp(-2.2 * t)
      val sample = sin(phase) * env * volume
      out[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
    return out
  }

  private fun synthArpeggio(freqs: List<Double>, stepMs: Int, volume: Double): ShortArray {
    val stepSamples = (SAMPLE_RATE * stepMs) / 1000
    val out = ShortArray(stepSamples * freqs.size)
    var phase = 0.0
    freqs.forEachIndexed { idx, freq ->
      for (j in 0 until stepSamples) {
        val localT = j.toDouble() / stepSamples
        phase += 2.0 * PI * freq / SAMPLE_RATE
        val env = (1.0 - localT * 0.45)
        val s = sin(phase) * env * volume
        out[idx * stepSamples + j] = (s * Short.MAX_VALUE).toInt().toShort()
      }
    }
    return out
  }

  private fun synthCrash(durationMs: Int, volume: Double): ShortArray {
    val samples = (SAMPLE_RATE * durationMs) / 1000
    val out = ShortArray(samples)
    var seed = 0x5EED1234
    var phase = 0.0
    for (i in 0 until samples) {
      val t = i.toDouble() / samples
      seed = seed * 1664525 + 1013904223
      val noise = ((seed shr 16) and 0xFFFF) / 32768.0 - 1.0
      phase += 2.0 * PI * (140.0 * (1.0 - 0.65 * t)) / SAMPLE_RATE
      val env = exp(-4.5 * t)
      val s = (noise * 0.55 + sin(phase) * 0.45) * env * volume
      out[i] = (s * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
    return out
  }

  private companion object {
    const val SAMPLE_RATE = 22050
  }
}
