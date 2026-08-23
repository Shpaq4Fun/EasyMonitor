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
    f, t, Sxx = spectrogram(signal, fs=sample_rate, nperseg=fft_size, noverlap=noverlap, nfft=fft_size)
    
    # Fast vectorized log-scale dB
    Sxx_db = 10 * np.log10(np.abs(Sxx) + 1e-10)
    
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
    
    max_idx = np.unravel_index(np.argmax(np.abs(Sxx), axis=None), Sxx.shape)
    max_bin = max_idx[0]
    dominant_freq_hz = float((max_bin * sample_rate) / fft_size)
    
    return {
        "image_bytes": img_bytes,
        "dominant_freq_hz": dominant_freq_hz
    }


def compute_reassigned_spectrogram(raw_pcm_bytes, sample_rate, fft_size=512, hop_size=256):
    if isinstance(raw_pcm_bytes, (bytes, bytearray)):
        shorts = np.frombuffer(raw_pcm_bytes, dtype=np.int16)
        signal = shorts.astype(np.float32) * (1.0 / 32768.0)
    else:
        signal = np.asarray(raw_pcm_bytes, dtype=np.float32)

    n_fft = int(fft_size) if fft_size > 0 else 512
    step = int(hop_size) if hop_size > 0 else 256
    noverlap = max(0, n_fft - step)

    # Standard Gaussian analysis window h(t), derivative dh(t), and time-ramped th(t)
    std = n_fft / 6.0
    M = np.arange(n_fft, dtype=np.float32) - (n_fft - 1) / 2.0
    w = np.exp(-0.5 * (M / std) ** 2).astype(np.float32)
    dw = (- (M / (std ** 2)) * w).astype(np.float32)
    tw = (M * w).astype(np.float32)

    # Compute the 3 component STFTs
    f, t, S = spectrogram(signal, fs=sample_rate, window=w, nperseg=n_fft, noverlap=noverlap, mode='complex', detrend=False)
    _, _, St = spectrogram(signal, fs=sample_rate, window=tw, nperseg=n_fft, noverlap=noverlap, mode='complex', detrend=False)
    _, _, Sdt = spectrogram(signal, fs=sample_rate, window=dw, nperseg=n_fft, noverlap=noverlap, mode='complex', detrend=False)

    eps = 1e-12
    mag_sq = (np.abs(S) ** 2).astype(np.float32)

    # Reassigned coordinates (Auger-Flandrin formulation)
    t_hat = t[None, :] + np.real(St / (S + eps)) / float(sample_rate)
    f_hat = f[:, None] - np.imag(Sdt / (S + eps)) / (2.0 * np.pi)

    # Reassign energy into standard (f, t) grid
    num_f = len(f)
    num_t = len(t)
    dt = float(t[1] - t[0]) if num_t > 1 else 1.0 / float(sample_rate)
    df = float(f[1] - f[0]) if num_f > 1 else 1.0

    t_idx = np.round((t_hat - t[0]) / dt).astype(np.int32)
    f_idx = np.round((f_hat - f[0]) / df).astype(np.int32)

    valid = (t_idx >= 0) & (t_idx < num_t) & (f_idx >= 0) & (f_idx < num_f)
    reassigned_matrix = np.zeros((num_f, num_t), dtype=np.float32)
    np.add.at(reassigned_matrix, (f_idx[valid], t_idx[valid]), mag_sq[valid])

    # Convert energy to dB
    Sxx_db = 10.0 * np.log10(reassigned_matrix + 1e-10)

    # Headless rendering with FigureCanvasAgg
    fig = Figure(figsize=(8, 4.8), dpi=180)
    canvas = FigureCanvasAgg(fig)
    ax = fig.add_subplot(111)

    t_start = float(t[0]) if len(t) > 0 else 0.0
    t_end = float(t[-1]) if len(t) > 1 else max(1.0, float(len(signal)) / sample_rate)

    im = ax.imshow(
        Sxx_db,
        origin='lower',
        aspect='auto',
        extent=[t_start, t_end, float(f[0]) / 1000.0, float(f[-1]) / 1000.0],
        cmap='jet',
        interpolation='nearest'
    )

    ax.set_title('Reassigned Spectrogram')
    ax.set_xlabel('Time [s]')
    ax.set_ylabel('Frequency [kHz]')
    fig.colorbar(im, ax=ax, label='dB')
    fig.tight_layout()

    buf = io.BytesIO()
    canvas.print_png(buf)
    img_bytes = buf.getvalue()

    max_idx = np.unravel_index(np.argmax(reassigned_matrix, axis=None), reassigned_matrix.shape)
    max_bin = max_idx[0]
    dominant_freq_hz = float(f[max_bin]) if max_bin < len(f) else 0.0

    return {
        "image_bytes": img_bytes,
        "dominant_freq_hz": dominant_freq_hz
    }


def _spwvd_fast(x: np.ndarray, fs: float, n_freq: int = 256, g_len: int = 31, h_len: int = 127):
    """Compute Smoothed Pseudo Wigner-Ville Distribution (SPWVD) efficiently."""
    n = len(x)
    # Analytic signal
    z = _analytic_signal_f32(x)
    
    # Time smoothing window g (length g_len, odd)
    g = hann(g_len).astype(np.float32)
    g = g / np.sum(g)
    g_half = g_len // 2
    
    # Frequency smoothing window h (length h_len, odd)
    h = hann(h_len).astype(np.float32)
    h = h / h[h_len // 2]
    h_half = h_len // 2
    
    t_indices = np.arange(n)
    tfr = np.zeros((n_freq, n), dtype=np.float32)
    
    for ti, t_val in enumerate(t_indices):
        tau_max = min(t_val, n - 1 - t_val, h_half)
        if tau_max < 1:
            continue
            
        r_tau = np.zeros(n_freq, dtype=np.complex64)
        tau_range = np.arange(-tau_max, tau_max + 1)
        
        # Local windowed instantaneous correlation
        for tau in tau_range:
            g_tau_sum = 0.0
            # Time smoothing over mu in [-g_half, g_half]
            mu_min = max(-g_half, -t_val + abs(tau))
            mu_max = min(g_half, n - 1 - t_val - abs(tau))
            if mu_min <= mu_max:
                mu_vals = np.arange(mu_min, mu_max + 1)
                t_mu = t_val + mu_vals
                z_plus = z[t_mu + tau]
                z_minus = np.conj(z[t_mu - tau])
                g_weights = g[mu_vals + g_half]
                g_tau_sum = np.sum(g_weights * z_plus * z_minus)
                
            h_weight = h[tau + h_half]
            r_tau[tau % n_freq] = h_weight * g_tau_sum
            
        spec = np.real(fft(r_tau))
        tfr[:, ti] = spec
        
    f = np.linspace(0, fs / 2.0, n_freq // 2, dtype=np.float32)
    t = np.arange(n, dtype=np.float32) / fs
    return f, t, tfr[:n_freq // 2, :]


def compute_spwvd(raw_pcm_bytes, sample_rate, fft_size=512, hop_size=256):
    if isinstance(raw_pcm_bytes, (bytes, bytearray)):
        shorts = np.frombuffer(raw_pcm_bytes, dtype=np.int16)
        signal = shorts.astype(np.float32) * (1.0 / 32768.0)
    else:
        signal = np.asarray(raw_pcm_bytes, dtype=np.float32)

    # Cap processing length for fast responsive UI
    max_dur = 2.0
    max_samples = min(len(signal), int(max_dur * sample_rate))
    proc_signal = signal[:max_samples]

    target_len = 500
    if len(proc_signal) > target_len:
        step_ds = int(np.ceil(len(proc_signal) / target_len))
        proc_signal = proc_signal[::step_ds]
        effective_fs = sample_rate / step_ds
    else:
        effective_fs = sample_rate

    # Attempt computation via neurokit2 first, fallback to self-contained SPWVD
    spwvd_matrix = None
    f = None
    t = None
    try:
        import neurokit2 as nk
        f, t, spwvd_matrix = nk.signal_timefrequency(
            proc_signal,
            sampling_rate=effective_fs,
            method="spwvd",
            show=False
        )
    except Exception:
        pass

    if spwvd_matrix is None:
        try:
            f, t, spwvd_matrix = _spwvd_fast(proc_signal, effective_fs, n_freq=min(fft_size, 256))
        except Exception as e:
            # Fallback to standard spectrogram if any failure
            from scipy.signal import spectrogram
            f, t, spwvd_matrix = spectrogram(proc_signal, fs=effective_fs, nperseg=min(len(proc_signal), 128))

    # Convert to log scale / dB similar to spectrogram
    spwvd_plot = np.asarray(spwvd_matrix, dtype=np.float32)
    # Clip negative values if any from cross-terms or numerical artifacts before log
    spwvd_plot = np.maximum(spwvd_plot, 0)
    spwvd_db = 10.0 * np.log10(spwvd_plot + 1e-10)

    # Headless rendering matching spectrogram aesthetics
    fig = Figure(figsize=(8, 4.8), dpi=180)
    canvas = FigureCanvasAgg(fig)
    ax = fig.add_subplot(111)
    im = ax.imshow(
        spwvd_db,
        origin='lower',
        aspect='auto',
        extent=[float(t[0]), float(t[-1]), float(f[0]) / 1000.0, float(f[-1]) / 1000.0],
        cmap='jet',
        interpolation='nearest'
    )
    ax.set_title('SPWVD (Smoothed Pseudo Wigner-Ville Distribution)')
    ax.set_xlabel('Time [s]')
    ax.set_ylabel('Frequency [kHz]')
    fig.colorbar(im, ax=ax, label='dB')
    fig.tight_layout()

    buf = io.BytesIO()
    canvas.print_png(buf)
    img_bytes = buf.getvalue()

    max_idx = np.unravel_index(np.argmax(spwvd_plot, axis=None), spwvd_plot.shape)
    max_freq_bin = max_idx[0]
    dominant_freq_hz = float(f[max_freq_bin]) if max_freq_bin < len(f) else 0.0

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
