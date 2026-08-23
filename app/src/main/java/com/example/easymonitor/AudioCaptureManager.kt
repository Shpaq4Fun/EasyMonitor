package com.example.easymonitor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Helper class managing microphone permissions and raw PCM audio capture using AudioRecord.
 */
class AudioCaptureManager(private val context: Context) {

    private val _isRecording = AtomicBoolean(false)
    val isRecording: Boolean get() = _isRecording.get()

    private var audioRecord: AudioRecord? = null

    /**
     * Checks if RECORD_AUDIO permission has been granted.
     */
    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Starts audio capture and emits raw PCM float samples (-1.0f to 1.0f) as a Flow.
     *
     * @param sampleRate Sampling frequency in Hz (e.g. 44100).
     * @param frameSize Number of audio samples per emitted frame chunk.
     * @param gainMultiplier Gain factor applied to normalized audio samples.
     */
    @SuppressLint("MissingPermission")
    fun startRecording(
        sampleRate: Int = 44100,
        frameSize: Int = 1024,
        gainMultiplier: Float = 1.0f
    ): Flow<FloatArray> = flow {
        if (!hasAudioPermission()) {
            throw SecurityException("RECORD_AUDIO permission not granted.")
        }

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if ((minBufferSize == AudioRecord.ERROR) || (minBufferSize == AudioRecord.ERROR_BAD_VALUE)) {
            throw IllegalStateException("Invalid AudioRecord parameters: minBufferSize error.")
        }

        // Ensure buffer size is at least twice the requested frame size or minBufferSize
        val bufferSize = maxOf(minBufferSize, frameSize * 2 * 4)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("Failed to initialize AudioRecord.")
        }

        audioRecord = recorder
        _isRecording.set(true)

        try {
            recorder.startRecording()

            val shortBuffer = ShortArray(frameSize)

            while (currentCoroutineContext().isActive && _isRecording.get()) {
                val readShorts = recorder.read(shortBuffer, 0, frameSize)

                if (readShorts > 0) {
                    val floatSamples = FloatArray(readShorts)
                    for (i in 0 until readShorts) {
                        // Normalize 16-bit PCM integer (-32768..32767) to float (-1.0..1.0)
                        val normalized = (shortBuffer[i] / 32768.0f) * gainMultiplier
                        floatSamples[i] = normalized.coerceIn(-1.0f, 1.0f)
                    }
                    emit(floatSamples)
                } else if (readShorts < 0) {
                    // Handle read errors gracefully
                    break
                }
            }
        } finally {
            _isRecording.set(false)
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
            } catch (ignored: Exception) {
                // Ignore stop exceptions on cleanup
            } finally {
                recorder.release()
                if (audioRecord == recorder) {
                    audioRecord = null
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Signals the recording loop to stop.
     */
    fun stopRecording() {
        _isRecording.set(false)
    }
}
