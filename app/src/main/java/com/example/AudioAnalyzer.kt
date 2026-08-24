package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.app.ActivityCompat
import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.io.TarsosDSPAudioFormat
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchProcessor
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class PitchEvent(
    val pitchHz: Float,
    val probability: Float,
    val noteNumber: Int,
    val noteName: String,
    val isSilence: Boolean
)

// A fast Cooley-Tukey Radix-2 FFT implementation
object SimpleFFT {
    fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        if (n <= 1) return
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
            var m = n / 2
            while (m >= 1 && j >= m) { j -= m; m /= 2 }
            j += m
        }
        var l = 1
        while (l < n) {
            val step = 2 * l
            val pi = Math.PI.toFloat()
            for (m in 0 until l) {
                val wr = cos(-pi * m / l)
                val wi = sin(-pi * m / l)
                for (i in m until n step step) {
                    val idx = i + l
                    val tr = wr * re[idx] - wi * im[idx]
                    val ti = wr * im[idx] + wi * re[idx]
                    re[idx] = re[i] - tr
                    im[idx] = im[i] - ti
                    re[i] += tr
                    im[i] += ti
                }
            }
            l = step
        }
    }
}

class AudioAnalyzer {
    private var isRecording = false
    private var thread: Thread? = null

    private val _pitchFlow = MutableStateFlow(PitchEvent(0f, 0f, 0, "", true))
    val pitchFlow: StateFlow<PitchEvent> = _pitchFlow.asStateFlow()

    private val _fftFlow = MutableStateFlow(FloatArray(0))
    val fftFlow: StateFlow<FloatArray> = _fftFlow.asStateFlow()

    fun start() {
        if (isRecording) return
        isRecording = true

        thread = Thread {
            val sampleRate = 22050
            val bufferSize = 1024
            
            // Check for android permissions isn't necessary here as we wrap it in compose, but AudioRecord complains if not explicitly checked in some IDEs. 
            // In runtime, Compose permission handles it. We just suppress the warning.
            try {
                @Suppress("MissingPermission")
                val audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize * 2 // ShortArray takes 2 bytes per sample
                )

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    return@Thread
                }

                audioRecord.startRecording()

                val pdh = PitchDetectionHandler { result, _ ->
                    val pitchInHz = result.pitch
                    val probability = result.probability

                    if (pitchInHz != -1f && probability > 0.8f) {
                        val noteNumber = (12 * log2(pitchInHz / 440.0) + 69).roundToInt()
                        val noteName = getNoteName(noteNumber)
                        _pitchFlow.value = PitchEvent(pitchInHz, probability, noteNumber, noteName, false)
                    } else {
                        _pitchFlow.value = PitchEvent(0f, 0f, 0, "", true)
                    }
                }

                val pitchProcessor = PitchProcessor(PitchEstimationAlgorithm.YIN, sampleRate.toFloat(), bufferSize, pdh)
                val format = TarsosDSPAudioFormat(sampleRate.toFloat(), 16, 1, true, false)
                
                val shortBuffer = ShortArray(bufferSize)
                val floatBuffer = FloatArray(bufferSize)
                
                // Buffers for FFT
                val re = FloatArray(bufferSize)
                val im = FloatArray(bufferSize)
                val magnitudes = FloatArray(bufferSize / 2)

                while (isRecording) {
                    val read = audioRecord.read(shortBuffer, 0, bufferSize)
                    if (read > 0) {
                        for (i in 0 until read) {
                            val sample = shortBuffer[i] / 32768f
                            floatBuffer[i] = sample
                            
                            // Apply Hann window for FFT
                            val multiplier = 0.5f * (1f - cos(2.0 * Math.PI * i / (bufferSize - 1))).toFloat()
                            re[i] = sample * multiplier
                            im[i] = 0f
                        }
                        
                        // Process Pitch
                        val audioEvent = AudioEvent(format)
                        audioEvent.floatBuffer = floatBuffer // Assign buffer directly
                        pitchProcessor.process(audioEvent)
                        
                        // Process Spectrogram FFT
                        SimpleFFT.fft(re, im)
                        for (i in 0 until bufferSize / 2) {
                            magnitudes[i] = sqrt(re[i] * re[i] + im[i] * im[i])
                        }
                        _fftFlow.value = magnitudes.clone()
                    }
                }

                audioRecord.stop()
                audioRecord.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        thread?.start()
    }

    fun stop() {
        isRecording = false
        thread = null
    }

    private fun getNoteName(noteNumber: Int): String {
        val notes = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val octave = (noteNumber / 12) - 1
        val noteIndex = noteNumber % 12
        if (noteIndex < 0 || noteIndex >= notes.size) return ""
        return "${notes[noteIndex]}$octave"
    }
}
