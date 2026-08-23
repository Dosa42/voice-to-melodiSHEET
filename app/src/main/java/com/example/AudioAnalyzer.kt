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
import kotlin.math.log2
import kotlin.math.roundToInt

data class PitchEvent(
    val pitchHz: Float,
    val probability: Float,
    val noteNumber: Int,
    val noteName: String,
    val isSilence: Boolean
)

class AudioAnalyzer {
    private var isRecording = false
    private var thread: Thread? = null

    private val _pitchFlow = MutableStateFlow(PitchEvent(0f, 0f, 0, "", true))
    val pitchFlow: StateFlow<PitchEvent> = _pitchFlow.asStateFlow()

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

                while (isRecording) {
                    val read = audioRecord.read(shortBuffer, 0, bufferSize)
                    if (read > 0) {
                        for (i in 0 until read) {
                            floatBuffer[i] = shortBuffer[i] / 32768f
                        }
                        
                        val audioEvent = AudioEvent(format)
                        audioEvent.floatBuffer = floatBuffer // Assign buffer directly
                        pitchProcessor.process(audioEvent)
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
