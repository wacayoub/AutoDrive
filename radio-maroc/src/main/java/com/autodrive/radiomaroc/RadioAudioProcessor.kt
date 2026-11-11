package com.autodrive.radiomaroc

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal object RadioAudioMeter {
    data class Levels(val bass: Float, val mid: Float, val treble: Float, val measuredAt: Long = System.nanoTime())

    @Volatile var latest: Levels? = null
}

/** Processes only the PCM decoded by the integrated radio player. */
@OptIn(UnstableApi::class)
internal class RadioAudioProcessor : BaseAudioProcessor() {
    @Volatile var profile = SoundProfile.NORMAL

    @Volatile var hasPcm = false
        private set

    private var lowPass = FloatArray(0)
    private var highPass = FloatArray(0)
    private var bassCoefficient = 0f
    private var trebleCoefficient = 0f
    private var ramp = 0f
    private var bassGain = 1f
    private var midGain = 1f
    private var trebleGain = 1f
    private var bassEnergy = 0f
    private var midEnergy = 0f
    private var trebleEnergy = 0f
    private var samples = 0
    private var meterWindow = 1

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat =
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) inputAudioFormat else AudioFormat.NOT_SET

    override fun onFlush() {
        val format = inputAudioFormat
        hasPcm = outputAudioFormat != AudioFormat.NOT_SET
        resetMeter()
        if (!hasPcm) return
        lowPass = FloatArray(format.channelCount)
        highPass = FloatArray(format.channelCount)
        bassCoefficient = coefficient(200, format.sampleRate)
        trebleCoefficient = coefficient(3_500, format.sampleRate)
        ramp = coefficient(30, format.sampleRate)
        meterWindow = maxOf(1, format.sampleRate / 25 * format.channelCount)
        val selected = profile
        bassGain = selected.bass
        midGain = selected.mid
        trebleGain = selected.treble
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val output = replaceOutputBuffer(inputBuffer.remaining())
        val selected = profile
        while (inputBuffer.hasRemaining()) {
            bassGain += (selected.bass - bassGain) * ramp
            midGain += (selected.mid - midGain) * ramp
            trebleGain += (selected.treble - trebleGain) * ramp
            for (channel in lowPass.indices) {
                val sample = inputBuffer.getShort()
                val value = sample / 32768f
                val bass = lowPass[channel] + (value - lowPass[channel]) * bassCoefficient
                val upper = highPass[channel] + (value - highPass[channel]) * trebleCoefficient
                lowPass[channel] = bass
                highPass[channel] = upper
                val low = bass * bassGain
                val mid = (upper - bass) * midGain
                val high = (value - upper) * trebleGain
                bassEnergy += low * low
                midEnergy += mid * mid
                trebleEnergy += high * high
                samples++
                val mixed = ((low + mid + high) * 32768f).roundToInt()
                output.putShort(mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }
            if (samples >= meterWindow) {
                RadioAudioMeter.latest = RadioAudioMeter.Levels(
                    sqrt(bassEnergy / samples), sqrt(midEnergy / samples), sqrt(trebleEnergy / samples),
                )
                resetEnergy()
            }
        }
        output.flip()
    }

    override fun onReset() {
        hasPcm = false
        resetMeter()
    }

    private fun resetMeter() {
        RadioAudioMeter.latest = null
        resetEnergy()
    }

    private fun resetEnergy() {
        bassEnergy = 0f
        midEnergy = 0f
        trebleEnergy = 0f
        samples = 0
    }

    private fun coefficient(frequency: Int, sampleRate: Int): Float =
        (1.0 - exp(-2.0 * PI * frequency / sampleRate)).toFloat()
}
