package com.example.easymonitor

import kotlin.math.log10

/**
 * Defines the dual execution modes supported by the signal processing pipeline.
 */
enum class ExecutionMode(val displayName: String) {
    ONLINE_STREAMING(
        displayName = "Online / Live Streaming",
    ),
    OFFLINE_POST_RECORDING(
        displayName = "Offline / Post-Recording",
    )
}

/**
 * Signal processing algorithm options available in offline batch processing.
 */
enum class OfflineMethod(val displayName: String, val description: String) {
    STFT(
        displayName = "STFT (Spectrogram)",
        description = "Short-Time Fourier Transform for time-frequency analysis."
    ),
    CSC(
        displayName = "Cyclic Spectral Coherence",
        description = "Cyclostationary Analysis for modulation detection."
    )
}

/**
 * Signal processing algorithm options available in online streaming.
 */
enum class OnlineMethod(val displayName: String, val description: String) {
    ENVELOPE_SPECTRUM(
        displayName = "Envelope Spectrum",
        description = "Demodulated spectrum using Hilbert transform and FFT."
    )
}

/**
 * Recording and processing state machine enum.
 */
enum class RecordingState {
    IDLE,
    RECORDING,
    PROCESSING,
    FINISHED
}

/**
 * Data class representing all editable signal processing parameters.
 */
data class SignalProcConfig(
    val sampleRate: Int = 44100,
    val frameSize: Int = 1024,
    val hopSize: Int = 512,
    val offlineMethod: OfflineMethod = OfflineMethod.STFT,
    val onlineMethod: OnlineMethod = OnlineMethod.ENVELOPE_SPECTRUM,
    val executionMode: ExecutionMode = ExecutionMode.ONLINE_STREAMING,
    val gainMultiplier: Float = 1.0f,
    val maxFrequencyLimit: Int = 300,
    val cscAlphaMax: Int = 150,
    val envelopeAverageCount: Int = 10,
    val envelopePeakCount: Int = 3
) {
    val overlapPercentage: Int
        get() = if (frameSize > 0) (((frameSize - hopSize).toFloat() / frameSize) * 100).toInt().coerceIn(0, 99) else 0

    companion object {
        val SUPPORTED_SAMPLE_RATES = listOf(8000, 16000, 22050, 44100, 48000)
        val SUPPORTED_FRAME_SIZES = listOf(128, 256, 512, 1024, 2048, 4096, 8192, 16384, 44100, 88200, 176400)
        val SUPPORTED_HOP_SIZES = listOf(64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 22050, 44100, 88200)
    }
}

/**
 * Represents a single real-time audio frame emitted during live streaming.
 */
data class LiveAudioFrame(
    val timestampMs: Long = System.currentTimeMillis(),
    val pcmSamples: FloatArray = floatArrayOf(),
    val envelopeSpectrumFreqs: FloatArray = floatArrayOf(),
    val envelopeSpectrumAmps: FloatArray = floatArrayOf(),
    val maxAmpValue: Float = 1.0f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as LiveAudioFrame
        return (timestampMs == other.timestampMs) &&
                pcmSamples.contentEquals(other.pcmSamples) &&
                envelopeSpectrumFreqs.contentEquals(other.envelopeSpectrumFreqs) &&
                envelopeSpectrumAmps.contentEquals(other.envelopeSpectrumAmps) &&
                (maxAmpValue == other.maxAmpValue)
    }

    override fun hashCode(): Int {
        var result = timestampMs.hashCode()
        result = 31 * result + pcmSamples.contentHashCode()
        result = 31 * result + envelopeSpectrumFreqs.contentHashCode()
        result = 31 * result + envelopeSpectrumAmps.contentHashCode()
        result = 31 * result + maxAmpValue.hashCode()
        return result
    }
}

/**
 * Comprehensive analysis result generated after offline batch computation.
 */
data class OfflineAnalysisResult(
    val totalDurationSeconds: Float,
    val totalSamplesRecorded: Long,
    val averageRmsEnergy: Float,
    val peakAmplitude: Float,
    val dominantFrequencyHz: Float,
    val processingTimeMs: Long,
    val spectrogramFrames: List<FloatArray> = emptyList(),
    val processedWaveformSummary: FloatArray = floatArrayOf(),
    val plotImageBytes: ByteArray? = null,
    val methodName: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as OfflineAnalysisResult
        return totalDurationSeconds == other.totalDurationSeconds &&
                totalSamplesRecorded == other.totalSamplesRecorded &&
                averageRmsEnergy == other.averageRmsEnergy &&
                peakAmplitude == other.peakAmplitude &&
                dominantFrequencyHz == other.dominantFrequencyHz &&
                processingTimeMs == other.processingTimeMs &&
                spectrogramFrames == other.spectrogramFrames &&
                methodName == other.methodName &&
                processedWaveformSummary.contentEquals(other.processedWaveformSummary) &&
                ((plotImageBytes == null && other.plotImageBytes == null) || (plotImageBytes != null && other.plotImageBytes != null && plotImageBytes.contentEquals(
                    other.plotImageBytes
                )))
    }

    override fun hashCode(): Int {
        var result = totalDurationSeconds.hashCode()
        result = 31 * result + totalSamplesRecorded.hashCode()
        result = 31 * result + averageRmsEnergy.hashCode()
        result = 31 * result + peakAmplitude.hashCode()
        result = 31 * result + dominantFrequencyHz.hashCode()
        result = 31 * result + processingTimeMs.hashCode()
        result = 31 * result + spectrogramFrames.hashCode()
        result = 31 * result + methodName.hashCode()
        result = 31 * result + processedWaveformSummary.contentHashCode()
        result = 31 * result + (plotImageBytes?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Represents a saved offline audio recording.
 */
data class AudioRecording(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val sampleRate: Int,
    val samples: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioRecording
        return id == other.id &&
                timestamp == other.timestamp &&
                sampleRate == other.sampleRate &&
                samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + sampleRate
        result = 31 * result + samples.contentHashCode()
        return result
    }
}
