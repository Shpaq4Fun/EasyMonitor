# EasyMonitor 🎧📊

**EasyMonitor** is an advanced, Android-based digital signal processing (DSP) and acoustic vibration monitoring application. It combines native **Kotlin + Jetpack Compose Material 3** UI with an embedded **Python DSP Engine** (powered by [Chaquopy](https://chaquo.com/chaquopy/)), enabling both real-time streaming spectrum diagnostics and advanced offline time-frequency distributions directly on mobile devices.

---

## 🚀 Key Features

### 1. 🔴 Online / Live Streaming Mode
- **Live Envelope Spectrum**: Real-time demodulation via analytic signal computation (Hilbert transform) and real FFT to detect periodic impacts, bearing faults, or cyclic machinery modulation.
- **Dynamic Spectral Averaging**: Moving-average spectral smoothing with configurable window count ($N$) to suppress random noise.
- **Automated Peak Tracking**: Real-time peak detection with customizable peak counts ($1-5$), frequency markers, baseline drop lines, and frequency text labels.
- **Interactive Oscilloscope**: Live zero-crossing PCM waveform rendering at up to 60 FPS.
- **Configurable Freq Limits**: Custom upper-frequency boundary zoom ($10 - 500\text{ Hz}$).

### 2. 🔵 Offline / Post-Recording Mode
Record raw audio into memory buffers and compute comprehensive batch time-frequency representations:
- **STFT (Short-Time Fourier Transform Spectrogram)**: Standard time-frequency decomposition with Hann windowing and logarithmic dB color scaling.
- **Reassigned Spectrogram**: Super-resolution time-frequency localization using the Auger-Flandrin method with time- and frequency-derivative STFT operators ($h(t)$, $dh(t)$, $t \cdot h(t)$).
- **SPWVD (Smoothed Pseudo Wigner-Ville Distribution)**: Quadratic time-frequency distribution with independent time and frequency smoothing windows to eliminate cross-term interference artifacts.
- **CSC (Cyclic Spectral Coherence)**: High-performance vectorized 3D tensor cyclostationary analysis mapping carrier frequencies ($f$) against cyclic frequencies ($\alpha$).

### 3. 🛠 Recording & Analysis Management
- Multi-recording history list with timestamp, sample rate, and duration metadata.
- Re-run analysis on any saved recording across different algorithms and FFT parameter sets.
- Zoomable & pannable high-resolution (180 DPI) headless Matplotlib heatmaps rendered with customized colormaps (`jet`, `one_minus_jet`).
- Comprehensive metrics extraction: RMS Energy, Peak Amplitude, Dominant Peak Frequency, and Execution Time ($ms$).

---

## 🏗 System Architecture

```
                                  ┌───────────────────────────────────┐
                                  │       AudioCaptureManager         │
                                  │   (AudioRecord PCM Stream / Flow) │
                                  └─────────────────┬─────────────────┘
                                                    │
                                                    ▼
                                  ┌───────────────────────────────────┐
                                  │          MainViewModel            │
                                  │  (Kotlin Coroutines & Flow State) │
                                  └─────────┬───────────────┬─────────┘
                                            │               │
                        ┌───────────────────┘               └──────────────────┐
                        ▼                                                      ▼
        ┌───────────────────────────────┐                      ┌───────────────────────────────┐
        │       Jetpack Compose UI      │                      │     PythonBridge (Chaquopy)   │
        │   - HomeScreen / Canvas       │                      │   - Java-Python JNI interface │
        │   - Config Drawer & Badges    │                      └───────────────┬───────────────┘
        │   - Interactive Zoomable View │                                      │
        └───────────────────────────────┘                                      ▼
                                                               ┌───────────────────────────────┐
                                                               │         dsp_engine.py         │
                                                               │  - NumPy & SciPy SIMD Ops     │
                                                               │  - STFT / Reassigned Spec     │
                                                               │  - Vectorized CSC & SPWVD     │
                                                               │  - Agg Headless Plotting      │
                                                               └───────────────────────────────┘
```

---

## ⚙️ Configurable Parameters

| Parameter | Options / Range | Description |
| :--- | :--- | :--- |
| **Sample Rate** | 8 kHz, 16 kHz, 22.05 kHz, 44.1 kHz, 48 kHz | Audio hardware sampling frequency |
| **Frame Size ($N$)** | 128 to 176,400 samples | STFT / FFT analysis window size |
| **Hop Size ($H$)** | 64 to 88,200 samples | Frame stride between consecutive windows |
| **Gain Multiplier** | $0.5\times$ to $5.0\times$ | Digital software pre-amplification factor |
| **Envelope Max Freq**| $10\text{ Hz}$ to $500\text{ Hz}$ | Display frequency cutoff for live envelope |
| **CSC $\alpha_{\max}$** | $1\text{ Hz}$ to $500\text{ Hz}$ | Maximum cyclic frequency limit for CSC maps |
| **Average Count** | $1$ to $50$ frames | Number of frames used in moving average |
| **Peak Markers** | $1$ to $5$ | Number of strongest peaks labeled live |

---

## 📦 Tech Stack & Dependencies

- **Platform**: Android (Min SDK 28 / Target SDK 37)
- **Language**: Kotlin 2.0+ & Python 3.10
- **UI Framework**: Jetpack Compose (BOM 2024.09.00), Material Design 3
- **Python-Android Bridge**: [Chaquopy 16.0.0](https://chaquo.com/chaquopy/)
- **Embedded Python Packages**:
  - `numpy`
  - `scipy`
  - `matplotlib`
  - `neurokit2`

---

## 🔧 Building & Setup

### Prerequisites
- **Android Studio Ladybug (or newer)**
- **JDK 17** (or standard Android Studio JDK)
- Android device or emulator running **Android 9.0 (API 28)** or higher

### Build Instructions
1. Clone the repository:
   ```bash
   git clone https://github.com/YourUsername/EasyMonitor.git
   cd EasyMonitor
   ```
2. Open the project in **Android Studio**.
3. Allow Gradle to sync and download Chaquopy dependencies and Python wheels.
4. Connect an Android device (or launch an emulator) with microphone support enabled.
5. Click **Run** (`Shift + F10`) or assemble the APK via terminal:
   ```bash
   ./gradlew assembleDebug
   ```

---

## 📄 License
This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
