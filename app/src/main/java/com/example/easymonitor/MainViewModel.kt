package com.example.easymonitor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Main UI state holding current configuration, recording status, and processing results.
 */
data class UiState(
    val config: SignalProcConfig = SignalProcConfig(),
    val recordingState: RecordingState = RecordingState.IDLE,
    val permissionGranted: Boolean = false,
    val liveFrame: LiveAudioFrame? = null,
    val offlineResult: OfflineAnalysisResult? = null,
    val offlineResults: List<OfflineAnalysisResult> = emptyList(),
    val recordingResults: Map<String, List<OfflineAnalysisResult>> = emptyMap(),
    val recordedDurationSeconds: Float = 0f,
    val errorMessage: String? = null,
    val recordings: List<AudioRecording> = emptyList(),
    val selectedRecordingId: String? = null
)

/**
 * ViewModel managing audio capture lifecycle, real-time signal calculations,
 * and offline batch signal processing calculations.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val audioCaptureManager = AudioCaptureManager(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var recordingJob: Job? = null
    private val recordedSamplesBuffer = mutableListOf<Float>()
    private var runningMaxEnvelopeAmp = 0f
    private val envelopeHistory = mutableListOf<FloatArray>()
    private var previousEnvelopeAverageCount = 0

    init {
        checkPermissionStatus()
        PythonBridge.initialize(application)
    }

    /**
     * Updates permission status in UI State.
     */
    fun checkPermissionStatus() {
        val granted = audioCaptureManager.hasAudioPermission()
        _uiState.update { it.copy(permissionGranted = granted) }
    }

    /**
     * Updates signal processing configuration.
     */
    fun updateConfig(newConfig: SignalProcConfig) {
        val oldConfig = _uiState.value.config

        // Block structural changes during active recording
        if (_uiState.value.recordingState == RecordingState.RECORDING) {
            if (oldConfig.sampleRate != newConfig.sampleRate ||
                oldConfig.frameSize != newConfig.frameSize ||
                oldConfig.hopSize != newConfig.hopSize ||
                oldConfig.executionMode != newConfig.executionMode
            ) {
                return
            }
        }

        _uiState.update { it.copy(config = newConfig) }
    }

    /**
     * Sets offline method in configuration.
     */
    fun selectOfflineMethod(method: OfflineMethod) {
        if (_uiState.value.recordingState == RecordingState.RECORDING) return
        val currentConfig = _uiState.value.config
        val newConfig = currentConfig.copy(offlineMethod = method)
        _uiState.update { it.copy(config = newConfig) }
    }

    /**
     * Explicitly launches offline batch analysis for the selected recording using current parameters.
     */
    fun runOfflineAnalysis() {
        if (_uiState.value.recordingState == RecordingState.RECORDING ||
            _uiState.value.recordingState == RecordingState.PROCESSING
        ) return
        val selectedId = _uiState.value.selectedRecordingId ?: return
        val currentConfig = _uiState.value.config
        runAnalysisForSelected(selectedId, currentConfig)
    }

    /**
     * Helper to run batch analysis on a specific saved recording.
     */
    private fun runAnalysisForSelected(recordingId: String, config: SignalProcConfig) {
        val recording = _uiState.value.recordings.firstOrNull { it.id == recordingId } ?: return
        _uiState.update { it.copy(recordingState = RecordingState.PROCESSING) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                performOfflineBatchProcessing(recording.samples, config)
            }
            _uiState.update { state ->
                val existingList = state.recordingResults[recordingId] ?: emptyList()
                val updatedList = listOf(result) + existingList
                val updatedMap = state.recordingResults.toMutableMap().apply {
                    put(recordingId, updatedList)
                }
                state.copy(
                    recordingState = RecordingState.FINISHED,
                    offlineResult = result,
                    offlineResults = if (state.selectedRecordingId == recordingId) updatedList else state.offlineResults,
                    recordingResults = updatedMap
                )
            }
        }
    }

    /**
     * Selects a recording and displays already calculated results, if any.
     */
    fun selectRecording(id: String) {
        if (_uiState.value.recordingState == RecordingState.RECORDING) return
        _uiState.update { state ->
            val results = state.recordingResults[id] ?: emptyList()
            state.copy(
                selectedRecordingId = id,
                offlineResult = results.firstOrNull(),
                offlineResults = results
            )
        }
    }

    /**
     * Deletes a recording from the list.
     */
    fun deleteRecording(id: String) {
        if (_uiState.value.recordingState == RecordingState.RECORDING) return
        _uiState.update { state ->
            val updated = state.recordings.filter { it.id != id }
            val nextSelected = if (state.selectedRecordingId == id) {
                updated.firstOrNull()?.id
            } else {
                state.selectedRecordingId
            }
            val updatedResults = state.recordingResults.toMutableMap().apply {
                remove(id)
            }
            state.copy(
                recordings = updated,
                selectedRecordingId = nextSelected,
                offlineResult = if (nextSelected == null) null else state.recordingResults[nextSelected]?.firstOrNull(),
                offlineResults = if (nextSelected == null || nextSelected != state.selectedRecordingId) emptyList() else state.recordingResults[nextSelected]
                    ?: emptyList(),
                recordingResults = updatedResults
            )
        }
    }

    /**
     * Starts audio capture with the current parameters.
     */
    fun startCapture() {
        if (_uiState.value.recordingState == RecordingState.RECORDING) return

        recordedSamplesBuffer.clear()
        runningMaxEnvelopeAmp = 0f
        envelopeHistory.clear()
        previousEnvelopeAverageCount = 0
        val currentConfig = _uiState.value.config

        _uiState.update {
            it.copy(
                recordingState = RecordingState.RECORDING,
                liveFrame = null,
                offlineResult = null,
                offlineResults = emptyList(),
                recordedDurationSeconds = 0f,
                errorMessage = null
            )
        }

        recordingJob = viewModelScope.launch {
            audioCaptureManager.startRecording(
                sampleRate = currentConfig.sampleRate,
                frameSize = currentConfig.frameSize,
                gainMultiplier = currentConfig.gainMultiplier
            ).catch { exception ->
                _uiState.update {
                    it.copy(
                        recordingState = RecordingState.IDLE,
                        errorMessage = "Capture failed: ${exception.localizedMessage}"
                    )
                }
            }.collect { pcmChunk ->
                // Access active config at each iteration dynamically
                val activeConfig = _uiState.value.config

                // Accumulate samples for potential offline processing
                for (sample in pcmChunk) {
                    recordedSamplesBuffer.add(sample)
                }

                val totalRecordedSamples = recordedSamplesBuffer.size
                val duration = totalRecordedSamples.toFloat() / activeConfig.sampleRate

                // Compute live metrics and envelope spectrum for real-time visualization on a background thread
                val frame = withContext(Dispatchers.Default) {
                    if (activeConfig.executionMode == ExecutionMode.ONLINE_STREAMING &&
                        activeConfig.onlineMethod == OnlineMethod.ENVELOPE_SPECTRUM
                    ) {
                        try {
                            val spec = PythonBridge.getLiveEnvelopeSpectrum(
                                pcmChunk,
                                activeConfig.sampleRate,
                                activeConfig.maxFrequencyLimit
                            )
                            val base = computeLiveFrame(pcmChunk, activeConfig.sampleRate)

                            val amps = spec.second

                            // If the averaging count limit has changed, clear the buffer
                            val maxHistorySize = activeConfig.envelopeAverageCount
                            if (envelopeHistory.isNotEmpty() && maxHistorySize != previousEnvelopeAverageCount) {
                                envelopeHistory.clear()
                            }
                            previousEnvelopeAverageCount = maxHistorySize

                            if (envelopeHistory.isNotEmpty() && envelopeHistory.first().size != amps.size) {
                                envelopeHistory.clear()
                            }
                            envelopeHistory.add(amps)
                            while (envelopeHistory.size > maxHistorySize) {
                                envelopeHistory.removeAt(0)
                            }

                            val averaged = FloatArray(amps.size) { i ->
                                var sum = 0f
                                for (h in envelopeHistory) {
                                    sum += h[i]
                                }
                                sum / envelopeHistory.size
                            }

                            val currentMax = averaged.maxOrNull() ?: 0f
                            if (runningMaxEnvelopeAmp < 1e-7f || currentMax > runningMaxEnvelopeAmp) {
                                runningMaxEnvelopeAmp = currentMax
                            } else {
                                runningMaxEnvelopeAmp = runningMaxEnvelopeAmp * 0.7f + currentMax * 0.3f
                            }
                            val scalingMax = runningMaxEnvelopeAmp.coerceAtLeast(1e-7f)

                            base.copy(
                                envelopeSpectrumFreqs = spec.first,
                                envelopeSpectrumAmps = averaged,
                                maxAmpValue = scalingMax
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                            computeLiveFrame(pcmChunk, activeConfig.sampleRate)
                        }
                    } else {
                        computeLiveFrame(pcmChunk, activeConfig.sampleRate)
                    }
                }

                _uiState.update {
                    it.copy(
                        liveFrame = frame,
                        recordedDurationSeconds = duration
                    )
                }
            }
        }
    }

    /**
     * Stops audio capture, saves to recordings list, and triggers offline batch processing.
     */
    fun stopCapture() {
        if (_uiState.value.recordingState != RecordingState.RECORDING) return

        audioCaptureManager.stopRecording()
        recordingJob?.cancel()
        recordingJob = null

        val currentConfig = _uiState.value.config
        val samplesArray = recordedSamplesBuffer.toFloatArray()

        if (currentConfig.executionMode == ExecutionMode.OFFLINE_POST_RECORDING && samplesArray.isNotEmpty()) {
            val newRecording = AudioRecording(
                sampleRate = currentConfig.sampleRate,
                samples = samplesArray
            )
            _uiState.update { state ->
                state.copy(
                    recordings = state.recordings + newRecording,
                    selectedRecordingId = newRecording.id,
                    recordingState = RecordingState.PROCESSING,
                    offlineResult = null,
                    offlineResults = emptyList()
                )
            }
            runAnalysisForSelected(newRecording.id, currentConfig)
        } else {
            _uiState.update { it.copy(recordingState = RecordingState.FINISHED) }
        }
    }

    /**
     * Resets recording state back to IDLE.
     */
    fun resetState() {
        _uiState.update {
            it.copy(
                recordingState = RecordingState.IDLE,
                liveFrame = null,
                offlineResult = null,
                offlineResults = emptyList(),
                recordingResults = emptyMap(),
                recordedDurationSeconds = 0f,
                errorMessage = null,
                selectedRecordingId = null
            )
        }
    }

    /**
     * Prepares live audio frame data.
     */
    private fun computeLiveFrame(pcmSamples: FloatArray, sampleRate: Int): LiveAudioFrame {
        return LiveAudioFrame(
            pcmSamples = pcmSamples
        )
    }

    /**
     * Performs multi-step batch signal analysis based on selected algorithm (STFT, Wavelet, Bandpass, Peak Detection).
     */
    private fun performOfflineBatchProcessing(
        samples: FloatArray,
        config: SignalProcConfig
    ): OfflineAnalysisResult {
        val startTime = System.currentTimeMillis()
        val totalSamples = samples.size.toLong()
        val durationSec = samples.size.toFloat() / config.sampleRate

        val methodName = when (config.offlineMethod) {
            OfflineMethod.STFT -> "Spectrogram (STFT)"
            OfflineMethod.REASSIGNED_SPECTROGRAM -> "Reassigned Spectrogram"
            OfflineMethod.SPWVD -> "SPWVD (Smoothed WVD)"
            OfflineMethod.CSC -> "Cyclic Spectral Coherence (CSC)"
        }

        if (samples.isEmpty()) {
            return OfflineAnalysisResult(
                totalDurationSeconds = 0f,
                totalSamplesRecorded = 0,
                averageRmsEnergy = 0f,
                peakAmplitude = 0f,
                dominantFrequencyHz = 0f,
                processingTimeMs = System.currentTimeMillis() - startTime,
                methodName = methodName
            )
        }

        // Overall signal stats
        var totalSq = 0.0
        var globalPeak = 0f
        for (s in samples) {
            val absVal = abs(s)
            if (absVal > globalPeak) globalPeak = absVal
            totalSq += (s * s).toDouble()
        }
        val avgRms = sqrt(totalSq / samples.size).toFloat()

        // Downsampled waveform overview for GUI summary chart (200 points)
        val summarySize = 200
        val step = maxOf(1, samples.size / summarySize)
        val waveformSummary = FloatArray(minOf(summarySize, (samples.size + step - 1) / step))
        for (i in waveformSummary.indices) {
            val idx = i * step
            var maxAmp = 0f
            for (j in idx until minOf(idx + step, samples.size)) {
                val a = abs(samples[j])
                if (a > maxAmp) maxAmp = a
            }
            waveformSummary[i] = maxAmp
        }

        // Batch processing: call PythonBridge for STFT/Reassigned/SPWVD/CSC computations
        var spectrogramFrames: List<FloatArray> = emptyList()
        var plotImageBytes: ByteArray? = null
        var dominantFreqHz = 0f

        when (config.offlineMethod) {
            OfflineMethod.STFT -> {
                try {
                    val pyResult = PythonBridge.getSpectrogram(
                        samples = samples,
                        sampleRate = config.sampleRate,
                        fftSize = config.frameSize,
                        hopSize = config.hopSize
                    )
                    spectrogramFrames = pyResult.spectrogram
                    plotImageBytes = pyResult.imageBytes
                    dominantFreqHz = pyResult.dominantFreqHz
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            OfflineMethod.REASSIGNED_SPECTROGRAM -> {
                try {
                    val pyResult = PythonBridge.getReassignedSpectrogram(
                        samples = samples,
                        sampleRate = config.sampleRate,
                        fftSize = config.frameSize,
                        hopSize = config.hopSize
                    )
                    spectrogramFrames = pyResult.spectrogram
                    plotImageBytes = pyResult.imageBytes
                    dominantFreqHz = pyResult.dominantFreqHz
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            OfflineMethod.SPWVD -> {
                try {
                    val pyResult = PythonBridge.getSpwvd(
                        samples = samples,
                        sampleRate = config.sampleRate,
                        fftSize = config.frameSize,
                        hopSize = config.hopSize
                    )
                    spectrogramFrames = pyResult.spectrogram
                    plotImageBytes = pyResult.imageBytes
                    dominantFreqHz = pyResult.dominantFreqHz
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            OfflineMethod.CSC -> {
                try {
                    val paramsJson =
                        "{\"min_alpha\":1,\"max_alpha\":${config.cscAlphaMax},\"window\":${config.frameSize},\"overlap\":${config.frameSize - config.hopSize}}"
                    val pyResult = PythonBridge.getCsc(
                        samples = samples,
                        sampleRate = config.sampleRate,
                        paramsJson = paramsJson
                    )
                    spectrogramFrames = pyResult.cscMap
                    plotImageBytes = pyResult.imageBytes
                    dominantFreqHz = pyResult.dominantFreqHz
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val processingTimeMs = System.currentTimeMillis() - startTime

        return OfflineAnalysisResult(
            totalDurationSeconds = durationSec,
            totalSamplesRecorded = totalSamples,
            averageRmsEnergy = avgRms,
            peakAmplitude = globalPeak,
            dominantFrequencyHz = dominantFreqHz,
            processingTimeMs = processingTimeMs,
            spectrogramFrames = spectrogramFrames,
            processedWaveformSummary = waveformSummary,
            plotImageBytes = plotImageBytes,
            methodName = methodName
        )
    }
}
