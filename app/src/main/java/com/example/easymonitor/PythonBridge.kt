package com.example.easymonitor

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

object PythonBridge {

    fun initialize(context: Context) {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
    }

    fun getSpectrogram(
        samples: FloatArray,
        sampleRate: Int,
        fftSize: Int,
        hopSize: Int
    ): PythonSpectrogramResult {
        val py = Python.getInstance()
        val module = py.getModule("dsp_engine")
        val resultPy = module.callAttr(
            "compute_spectrogram",
            samples,
            sampleRate,
            fftSize,
            hopSize
        )
        val resultMap = resultPy.asMap()
        
        val imageBytes = resultMap[PyObject.fromJava("image_bytes")]?.toJava(ByteArray::class.java)
        val dominantFreqHz = resultMap[PyObject.fromJava("dominant_freq_hz")]?.toFloat() ?: 0f

        return PythonSpectrogramResult(
            spectrogram = emptyList(),
            imageBytes = imageBytes,
            frequencies = FloatArray(0),
            times = FloatArray(0),
            dominantFreqHz = dominantFreqHz
        )
    }

    fun getReassignedSpectrogram(
        samples: FloatArray,
        sampleRate: Int,
        fftSize: Int,
        hopSize: Int
    ): PythonSpectrogramResult {
        val py = Python.getInstance()
        val module = py.getModule("dsp_engine")
        val resultPy = module.callAttr(
            "compute_reassigned_spectrogram",
            samples,
            sampleRate,
            fftSize,
            hopSize
        )
        val resultMap = resultPy.asMap()

        val imageBytes = resultMap[PyObject.fromJava("image_bytes")]?.toJava(ByteArray::class.java)
        val dominantFreqHz = resultMap[PyObject.fromJava("dominant_freq_hz")]?.toFloat() ?: 0f

        return PythonSpectrogramResult(
            spectrogram = emptyList(),
            imageBytes = imageBytes,
            frequencies = FloatArray(0),
            times = FloatArray(0),
            dominantFreqHz = dominantFreqHz
        )
    }

    fun getSpwvd(
        samples: FloatArray,
        sampleRate: Int,
        fftSize: Int,
        hopSize: Int,
        targetLen: Int
    ): PythonSpectrogramResult {
        val py = Python.getInstance()
        val module = py.getModule("dsp_engine")
        val resultPy = module.callAttr(
            "compute_spwvd",
            samples,
            sampleRate,
            fftSize,
            hopSize,
            targetLen
        )
        val resultMap = resultPy.asMap()

        val imageBytes = resultMap[PyObject.fromJava("image_bytes")]?.toJava(ByteArray::class.java)
        val dominantFreqHz = resultMap[PyObject.fromJava("dominant_freq_hz")]?.toFloat() ?: 0f

        return PythonSpectrogramResult(
            spectrogram = emptyList(),
            imageBytes = imageBytes,
            frequencies = FloatArray(0),
            times = FloatArray(0),
            dominantFreqHz = dominantFreqHz
        )
    }

    fun getCsc(
        samples: FloatArray,
        sampleRate: Int,
        paramsJson: String
    ): PythonCscResult {
        val py = Python.getInstance()
        val module = py.getModule("dsp_engine")
        val resultPy = module.callAttr(
            "compute_cyclic_spectral_coherence",
            samples,
            sampleRate,
            paramsJson
        )
        val resultMap = resultPy.asMap()

        val imageBytes = resultMap[PyObject.fromJava("image_bytes")]?.toJava(ByteArray::class.java)
        val dominantFreqHz = resultMap[PyObject.fromJava("dominant_freq_hz")]?.toFloat() ?: 0f

        return PythonCscResult(
            cscMap = emptyList(),
            imageBytes = imageBytes,
            carrierFreqs = FloatArray(0),
            cyclicFreqs = FloatArray(0),
            dominantFreqHz = dominantFreqHz
        )
    }

    fun getLiveEnvelopeSpectrum(
        samples: FloatArray,
        sampleRate: Int,
        maxFreq: Int
    ): Pair<FloatArray, FloatArray> {
        val py = Python.getInstance()
        val module = py.getModule("dsp_engine")
        val resultPy = module.callAttr(
            "compute_envelope_spectrum_live",
            samples,
            sampleRate,
            maxFreq
        )
        val resultMap = resultPy.asMap()

        val frequenciesPy = resultMap[PyObject.fromJava("frequencies")]?.asList() ?: emptyList()
        val frequencies = FloatArray(frequenciesPy.size) { i -> frequenciesPy[i].toFloat() }

        val amplitudesPy = resultMap[PyObject.fromJava("amplitudes")]?.asList() ?: emptyList()
        val amplitudes = FloatArray(amplitudesPy.size) { i -> amplitudesPy[i].toFloat() }

        return Pair(frequencies, amplitudes)
    }
}

data class PythonSpectrogramResult(
    val spectrogram: List<FloatArray>,
    val imageBytes: ByteArray?,
    val frequencies: FloatArray,
    val times: FloatArray,
    val dominantFreqHz: Float
)

data class PythonCscResult(
    val cscMap: List<FloatArray>,
    val imageBytes: ByteArray?,
    val carrierFreqs: FloatArray,
    val cyclicFreqs: FloatArray,
    val dominantFreqHz: Float
)
