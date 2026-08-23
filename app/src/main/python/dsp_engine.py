"""Cyclic Spectral Coherence (CSC) Map Generation.

Optimized for mobile CPU execution (vectorized 2D/3D tensor operations,
float32/complex64 precision for ARM NEON SIMD, headless Agg plotting).
"""

import numpy as np
import io
import json
from matplotlib.figure import Figure
from matplotlib.backends.backend_agg import FigureCanvasAgg
from matplotlib.colors import ListedColormap
import matplotlib.pyplot as plt
from scipy.signal.windows import hann
from scipy.signal import spectrogram
from scipy.fft import fft
from typing import Tuple, Optional, Union
from numpy.typing import NDArray

# Pre-cache colormap to avoid recreating on every call
_jet_colors = plt.colormaps['jet'](np.linspace(0, 1, 256))
_inverted_jet_colors = 1.0 - _jet_colors
_inverted_jet_colors[:, 3] = 1.0
ONE_MINUS_JET_CMAP = ListedColormap(_inverted_jet_colors, name='one_minus_jet')


def _analytic_signal_f32(x: np.ndarray) -> np.ndarray:
    """Fast float32 analytic signal (Hilbert transform) via FFT."""
    n = len(x)
    Xf = fft(x)
    Xf[1:(n + 1) // 2] *= 2.0
    Xf[(n + 2) // 2:] = 0.0
    return np.fft.ifft(Xf).astype(np.complex64)


# def CPS_W(
#     y: NDArray[np.float64],
#     x: NDArray[np.float64],
#     alpha: float,
#     nfft: int,
#     Noverlap: int,
#     Window: Union[NDArray[np.float64], int],
#     opt: str = 'sym',
#     P: Optional[float] = None
# ) -> NDArray[np.complex128]:
#     """Compute Welch's estimate of the (Cross) Cyclic Power Spectrum (vectorized)."""
#     if np.isscalar(Window):
#         Window = hann(int(Window))
#     Window = np.asarray(Window, dtype=np.float64).flatten()
    
#     y = np.asarray(y, dtype=np.complex128).flatten()
#     x = np.asarray(x, dtype=np.complex128).flatten()
    
#     n = len(x)
#     nwind = len(Window)
#     step = nwind - Noverlap
#     K = int((n - Noverlap) / step)
    
#     if opt == 'sym':
#         t = np.arange(n)
#         y = y * np.exp(-1j * np.pi * alpha * t)
#         x = x * np.exp(1j * np.pi * alpha * t)
#     else:
#         t = np.arange(n)
#         x = x * np.exp(2j * np.pi * alpha * t)
        
#     idx = np.arange(nwind)[None, :] + np.arange(K)[:, None] * step
#     yw_frames = y[idx] * Window[None, :]
#     xw_frames = x[idx] * Window[None, :]
    
#     Yw = fft(yw_frames, n=nfft, axis=1)
#     Xw = fft(xw_frames, n=nfft, axis=1)
    
#     CPS = np.sum(Yw * np.conj(Xw), axis=0)
#     KMU = K * np.linalg.norm(Window)**2
#     return CPS / KMU


# def SCoh_W(
#     y: NDArray[np.float64],
#     x: NDArray[np.float64],
#     alpha: float,
#     nfft: int,
#     Noverlap: int,
#     Window: Union[NDArray[np.float64], int],
#     opt: str = 'sym',
#     P: Optional[float] = None
# ) -> NDArray[np.complex128]:
#     """Compute Welch's estimate of the Cyclic Spectral Coherence (CSC)."""
#     if np.isscalar(Window):
#         Window = hann(int(Window))
#     Window = np.asarray(Window, dtype=np.float64).flatten()
    
#     n = len(x)
#     nwind = len(Window)
#     step = nwind - Noverlap
#     K = int((n - Noverlap) / step)
    
#     idx = np.arange(nwind)[None, :] + np.arange(K)[:, None] * step
#     X_frames = (np.asarray(x).flatten()[idx] * Window[None, :]).astype(np.complex128)
    
#     l_vec = np.arange(nwind)
#     m_vec = np.arange(K)
    
#     phase_l = np.exp(1j * np.pi * alpha * l_vec)
#     phase_m = np.exp(-2j * np.pi * alpha * m_vec * step)
    
#     U = fft(X_frames * phase_l.conj()[None, :], n=nfft, axis=1)
#     V = fft(X_frames * phase_l[None, :], n=nfft, axis=1)
    
#     Syx = phase_m @ (U * np.conj(V))
#     Sy = np.sum(np.abs(U)**2, axis=0)
#     Sx = np.sum(np.abs(V)**2, axis=0)
    
#     return Syx / np.sqrt(Sy * Sx)


def spectral_coh(
    x: NDArray[np.float32],
    alphamin: float = 1,
    alphamax: float = 150,
    nfft: int = 512,
    okno: int = 512,
    fs: float = 1,
    nover: int = 450
) -> Tuple[NDArray[np.complex64], NDArray[np.float32], NDArray[np.float32]]:
    """Compute the Cyclic Spectral Coherence matrix using batched 3D tensor FFTs."""
    l = len(x)
    x = np.asarray(x, dtype=np.float32).flatten()
    
    # Window and compute analytic signal in float32
    w_full = hann(l).astype(np.float32)
    x = _analytic_signal_f32(x * w_full)
    
    alpha_unit = 1.0 / l
    bmax = round(alphamax * l / fs)
    bmin = round(alphamin * l / fs)
    num_alphas = bmax - bmin + 1
    
    nwind = okno
    step = nwind - nover
    K = int((l - nover) / step)
    Window = hann(nwind).astype(np.float32)
    
    # Pre-segment all K frames ONCE
    idx = np.arange(nwind)[None, :] + np.arange(K)[:, None] * step
    X_frames = (x[idx] * Window[None, :]).astype(np.complex64)
    
    l_vec = np.arange(nwind, dtype=np.float32)
    m_vec = np.arange(K, dtype=np.float32)
    all_k = np.arange(bmin, bmax + 1, dtype=np.float32)
    all_alphas = all_k * alpha_unit
    
    SC = np.zeros((nfft // 2, num_alphas), dtype=np.complex64)
    batch_size = 32  # Balanced chunk size for mobile L2 cache
    
    for b_start in range(0, num_alphas, batch_size):
        b_end = min(b_start + batch_size, num_alphas)
        alphas_b = all_alphas[b_start:b_end]
        
        # (B, 1, nwind) and (B, K)
        phase_l = np.exp(1j * np.pi * alphas_b[:, None, None] * l_vec[None, None, :]).astype(np.complex64)
        phase_m = np.exp(-2j * np.pi * alphas_b[:, None] * (m_vec[None, :] * step)).astype(np.complex64)
        
        U = fft(X_frames[None, :, :] * phase_l.conj(), n=nfft, axis=2)
        V = fft(X_frames[None, :, :] * phase_l, n=nfft, axis=2)
        
        UV = U * np.conj(V)
        Syx = np.matmul(phase_m[:, None, :], UV).squeeze(1)
        Sy = np.sum(np.abs(U)**2, axis=1)
        Sx = np.sum(np.abs(V)**2, axis=1)
        
        Coh = Syx / np.sqrt(Sy * Sx)
        SC[:, b_start:b_end] = Coh[:, :nfft // 2].T
        
    f = np.linspace(0, fs / 2, nfft // 2, dtype=np.float32)
    a = (all_k * (fs / l)).astype(np.float32)
    
    return SC, f, a


def compute_spectrogram(raw_pcm_bytes, sample_rate, fft_size, hop_size):
    if isinstance(raw_pcm_bytes, (bytes, bytearray)):
        shorts = np.frombuffer(raw_pcm_bytes, dtype=np.int16)
        signal = shorts.astype(np.float32) * (1.0 / 32768.0)
    else:
        signal = np.asarray(raw_pcm_bytes, dtype=np.float32)
        
    noverlap = max(0, fft_size - hop_size)
    f, t, Sxx = spectrogram(signal, fs=sample_rate, nperseg=fft_size, noverlap=noverlap, nfft=fft_size, window=hann(fft_size))
    
    # Fast vectorized log-scale dB
    Sxx_db = np.log10(Sxx + 1e-10)
    
    # Fast headless rendering with FigureCanvasAgg
    fig = Figure(figsize=(8, 4.8), dpi=180)
    canvas = FigureCanvasAgg(fig)
    ax = fig.add_subplot(111)
    im = ax.imshow(
        Sxx_db,
        origin='lower',
        aspect='auto',
        extent=[t[0], t[-1], f[0]/1000, f[-1]/1000],
        cmap='jet',
        interpolation='nearest'
    )
    ax.set_title('Spectrogram (Python)')
    ax.set_xlabel('Time [s]')
    ax.set_ylabel('Frequency [kHz]')
    fig.colorbar(im, ax=ax, label='dB')
    fig.tight_layout()
    
    buf = io.BytesIO()
    canvas.print_png(buf)
    img_bytes = buf.getvalue()
    
    max_idx = np.unravel_index(np.argmax(Sxx, axis=None), Sxx.shape)
    max_bin = max_idx[0]
    dominant_freq_hz = float((max_bin * sample_rate) / fft_size)
    
    return {
        "image_bytes": img_bytes,
        "dominant_freq_hz": dominant_freq_hz
    }


def compute_cyclic_spectral_coherence(raw_pcm_bytes, sample_rate, params_json):
    if isinstance(raw_pcm_bytes, (bytes, bytearray)):
        shorts = np.frombuffer(raw_pcm_bytes, dtype=np.int16)
        signal = shorts.astype(np.float32) * (1.0 / 32768.0)
    else:
        signal = np.asarray(raw_pcm_bytes, dtype=np.float32)
        
    if isinstance(params_json, str):
        params = json.loads(params_json)
    else:
        params = params_json or {}
        
    min_alpha = params.get("min_alpha", 1)
    max_alpha = params.get("max_alpha", 150)
    window = params.get("window", 512)
    overlap = params.get("overlap", 450)
    
    SC, f, a = spectral_coh(
        signal[:min(len(signal), round(3 * sample_rate))],
        alphamin=min_alpha,
        alphamax=max_alpha,
        nfft=window,
        okno=window,
        fs=sample_rate,
        nover=overlap
    )
    csc_map = np.abs(SC)
    
    # Fast headless rendering with FigureCanvasAgg
    fig = Figure(figsize=(8, 4.8), dpi=180)
    canvas = FigureCanvasAgg(fig)
    ax = fig.add_subplot(111)
    im = ax.imshow(
        csc_map,
        origin='lower',
        aspect='auto',
        extent=[a[0], a[-1], f[0]/1000, f[-1]/1000],
        cmap=ONE_MINUS_JET_CMAP,
        interpolation='nearest'
    )
    ax.set_ylabel('Carrier Frequency (f) [kHz]')
    ax.set_xlabel('Cyclic Frequency (alpha) [Hz]')
    ax.set_title('Cyclic Spectral Coherence (CSC) Map')
    fig.colorbar(im, ax=ax, label='Coherence Magnitude')
    fig.tight_layout()
    
    buf = io.BytesIO()
    canvas.print_png(buf)
    img_bytes = buf.getvalue()
    
    max_idx = np.unravel_index(np.argmax(csc_map, axis=None), csc_map.shape)
    max_bin = max_idx[0]
    dominant_freq_hz = float((max_bin * sample_rate) / window)
    
    return {
        "image_bytes": img_bytes,
        "dominant_freq_hz": dominant_freq_hz
    }


def compute_envelope_spectrum_live(raw_pcm_bytes_or_floats, sample_rate, max_freq=300):
    if isinstance(raw_pcm_bytes_or_floats, (bytes, bytearray)):
        shorts = np.frombuffer(raw_pcm_bytes_or_floats, dtype=np.int16)
        signal = shorts.astype(np.float32) * (1.0 / 32768.0)
    else:
        signal = np.asarray(raw_pcm_bytes_or_floats, dtype=np.float32)
    
    n = len(signal)
    if n < 2:
        return {"frequencies": [], "amplitudes": []}
        
    analytic = _analytic_signal_f32(signal)
    env = np.abs(analytic).astype(np.float32)
    env -= np.mean(env)
    
    # Real FFT only
    fft_vals = np.fft.rfft(env)
    
    # Truncate to max_freq before building return lists
    df = float(sample_rate) / n
    k_max = int(float(max_freq) / df) + 1
    k_max = min(k_max, len(fft_vals))
    
    amps = (2.0 / n) * np.abs(fft_vals[:k_max])
    if len(amps) >= 4:
        amps[:4] = 0.0
    freqs = np.arange(k_max, dtype=np.float32) * df
    
    return {
        "frequencies": freqs.tolist(),
        "amplitudes": amps.tolist()
    }
