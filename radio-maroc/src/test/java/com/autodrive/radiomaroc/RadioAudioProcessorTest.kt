package com.autodrive.radiomaroc

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(UnstableApi::class)
class RadioAudioProcessorTest {
    private val sampleRate = 48_000

    @Test fun normalPassesSamplesAndMeasuresTheRealSignal() {
        val processor = stereoProcessor()
        val input = tone(85)
        assertArrayEquals(input, processor.process(input))
        val levels = requireNotNull(RadioAudioMeter.latest)
        assertTrue(levels.bass > .15f)
        assertTrue(levels.bass > levels.mid * 2)
        assertTrue(levels.bass > levels.treble * 10)
        processor.flush()
        assertEquals(null, RadioAudioMeter.latest)
        assertArrayEquals(ShortArray(input.size), processor.process(ShortArray(input.size)))
        assertEquals(0f, requireNotNull(RadioAudioMeter.latest).bass, 0f)
    }

    @Test fun profilesChangeTheActivePcmWithoutReconfiguringTheProcessor() {
        val processor = stereoProcessor()
        val low = tone(85)
        val high = tone(10_000)
        val normalLow = processor.process(low).peakAtEnd()
        val normalHigh = processor.process(high).peakAtEnd()
        processor.profile = SoundProfile.BASS
        assertTrue(processor.process(low).peakAtEnd() > normalLow * .85)
        assertTrue(processor.process(high).peakAtEnd() < normalHigh * .70)
        processor.profile = SoundProfile.VOICE
        assertTrue(processor.process(low).peakAtEnd() < normalLow * .65)
        processor.profile = SoundProfile.NORMAL
        assertEquals(normalHigh, processor.process(high).peakAtEnd())
    }

    private fun stereoProcessor() = RadioAudioProcessor().apply {
        configure(AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
        flush()
    }

    private fun tone(frequency: Int) = ShortArray(sampleRate / 5) { sample ->
        val frame = sample / 2
        val channel = if (sample % 2 == 0) 1 else -1
        (10_000 * sin(2 * PI * frequency * frame / sampleRate) * channel).roundToInt().toShort()
    }

    private fun RadioAudioProcessor.process(samples: ShortArray): ShortArray {
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        for (sample in samples) input.putShort(sample)
        input.flip()
        queueInput(input)
        val output = getOutput()
        return ShortArray(output.remaining() / 2) { output.getShort() }
    }

    private fun ShortArray.peakAtEnd(): Int = takeLast(2_000).maxOf { kotlin.math.abs(it.toInt()) }
}
