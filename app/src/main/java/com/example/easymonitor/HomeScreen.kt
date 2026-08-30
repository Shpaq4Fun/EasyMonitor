package com.example.easymonitor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: UiState,
    onRequestPermission: () -> Unit,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onResetState: () -> Unit,
    onUpdateConfig: (SignalProcConfig) -> Unit,
    onSelectRecording: (String) -> Unit,
    onDeleteRecording: (String) -> Unit,
    onSelectOfflineMethod: (OfflineMethod) -> Unit,
    onRunOfflineAnalysis: () -> Unit
) {
    var showSettings by remember { mutableStateOf(value = false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = "EasyMonitor Logo",
                        modifier = Modifier.height(40.dp)
                    )
                },
                actions = {
                    // Recording State Badge Indicator
                    StatusBadge(recordingState = uiState.recordingState)

                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Toggle Settings",
                            tint = if (showSettings) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Permission Banner if missing
            if (!uiState.permissionGranted) {
                PermissionWarningCard(onRequestPermission = onRequestPermission)
            }

            // Error Banner
            uiState.errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(5.dp)
                    )
                }
            }

            // Configurable Settings Panel
            AnimatedVisibility(visible = showSettings) {
                ConfigurationPanel(
                    config = uiState.config,
                    isRecording = uiState.recordingState == RecordingState.RECORDING,
                    onUpdateConfig = onUpdateConfig,
                    onSelectOfflineMethod = onSelectOfflineMethod
                )
            }

            // Central Control / Big Action Button
            RecordingControlsSection(
                uiState = uiState,
                onRequestPermission = onRequestPermission,
                onStartCapture = onStartCapture,
                onStopCapture = onStopCapture,
                onResetState = onResetState
            )

            // Visualization Display Container based on Execution Mode & State
            when (uiState.config.executionMode) {
                ExecutionMode.ONLINE_STREAMING -> {
                    LiveStreamingVisualizationSection(
                        uiState = uiState,
                        onUpdateConfig = onUpdateConfig
                    )
                }

                ExecutionMode.OFFLINE_POST_RECORDING -> {
                    OfflineProcessingVisualizationSection(
                        uiState = uiState,
                        onSelectRecording = onSelectRecording,
                        onDeleteRecording = onDeleteRecording,
                        onRunOfflineAnalysis = onRunOfflineAnalysis
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(recordingState: RecordingState) {
    val (bgColor, textColor, label) = when (recordingState) {
        RecordingState.IDLE -> Triple(Color.LightGray, Color.DarkGray, "IDLE")
        RecordingState.RECORDING -> Triple(Color(0xFFE53935), Color.White, "RECORDING")
        RecordingState.PROCESSING -> Triple(Color(0xFFFFA000), Color.White, "PROCESSING")
        RecordingState.FINISHED -> Triple(Color(0xFF43A047), Color.White, "READY")
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun PermissionWarningCard(onRequestPermission: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Microphone Permission Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Application needs RECORD_AUDIO permission to capture audio signals.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Grant")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ConfigurationPanel(
    config: SignalProcConfig,
    isRecording: Boolean,
    onUpdateConfig: (SignalProcConfig) -> Unit,
    onSelectOfflineMethod: (OfflineMethod) -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "DSP Pipeline Parameters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Execution Mode Selector
            Text("Execution Mode", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExecutionMode.entries.forEach { mode ->
                    FilterChip(
                        selected = config.executionMode == mode,
                        onClick = {
                            if (!isRecording) onUpdateConfig(config.copy(executionMode = mode))
                        },
                        label = { Text(mode.displayName, fontSize = 12.sp) },
                        enabled = !isRecording,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Processing Algorithm Dropdown
            Text("Processing Algorithm", style = MaterialTheme.typography.labelMedium)
            var algorithmExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = algorithmExpanded,
                onExpandedChange = { if (!isRecording) algorithmExpanded = !algorithmExpanded }
            ) {
                val displayName = if (config.executionMode == ExecutionMode.ONLINE_STREAMING) {
                    config.onlineMethod.displayName
                } else {
                    config.offlineMethod.displayName
                }
                OutlinedTextField(
                    value = displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Selected Method") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = algorithmExpanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    enabled = !isRecording
                )
                ExposedDropdownMenu(
                    expanded = algorithmExpanded,
                    onDismissRequest = { algorithmExpanded = false }
                ) {
                    if (config.executionMode == ExecutionMode.ONLINE_STREAMING) {
                        OnlineMethod.entries.forEach { method ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(method.displayName, fontWeight = FontWeight.Bold)
                                        Text(method.description, style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                onClick = {
                                    onUpdateConfig(config.copy(onlineMethod = method))
                                    algorithmExpanded = false
                                }
                            )
                        }
                    } else {
                        OfflineMethod.entries.forEach { method ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(method.displayName, fontWeight = FontWeight.Bold)
                                        Text(method.description, style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                onClick = {
                                    onSelectOfflineMethod(method)
                                    algorithmExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Sample Rate & Frame/Hop Dropdowns
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Sample Rate Dropdown
                var sampleRateExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = sampleRateExpanded,
                    onExpandedChange = { if (!isRecording) sampleRateExpanded = !sampleRateExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = "${config.sampleRate} Hz",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Sample Rate") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sampleRateExpanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        enabled = !isRecording
                    )
                    ExposedDropdownMenu(
                        expanded = sampleRateExpanded,
                        onDismissRequest = { sampleRateExpanded = false }
                    ) {
                        SignalProcConfig.SUPPORTED_SAMPLE_RATES.forEach { rate ->
                            DropdownMenuItem(
                                text = { Text("$rate Hz") },
                                onClick = {
                                    onUpdateConfig(config.copy(sampleRate = rate))
                                    sampleRateExpanded = false
                                }
                            )
                        }
                    }
                }

                // Frame Size Dropdown
                var frameSizeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = frameSizeExpanded,
                    onExpandedChange = { if (!isRecording) frameSizeExpanded = !frameSizeExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = "${config.frameSize} samples",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Frame Size (N)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = frameSizeExpanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        enabled = !isRecording
                    )
                    ExposedDropdownMenu(
                        expanded = frameSizeExpanded,
                        onDismissRequest = { frameSizeExpanded = false }
                    ) {
                        SignalProcConfig.SUPPORTED_FRAME_SIZES.forEach { size ->
                            DropdownMenuItem(
                                text = { Text("$size samples") },
                                onClick = {
                                    val newHop = if (config.hopSize >= size) size / 2 else config.hopSize
                                    onUpdateConfig(config.copy(frameSize = size, hopSize = newHop))
                                    frameSizeExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Hop Size Dropdown & Overlap summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var hopSizeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = hopSizeExpanded,
                    onExpandedChange = { if (!isRecording) hopSizeExpanded = !hopSizeExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = "${config.hopSize} samples",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Hop Size (Step)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = hopSizeExpanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        enabled = !isRecording
                    )
                    ExposedDropdownMenu(
                        expanded = hopSizeExpanded,
                        onDismissRequest = { hopSizeExpanded = false }
                    ) {
                        SignalProcConfig.SUPPORTED_HOP_SIZES.filter { it < config.frameSize }.forEach { hop ->
                            DropdownMenuItem(
                                text = { Text("$hop samples") },
                                onClick = {
                                    onUpdateConfig(config.copy(hopSize = hop))
                                    hopSizeExpanded = false
                                }
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Window Overlap: ${config.overlapPercentage}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Duration: ${
                            String.format(
                                Locale.US,
                                "%.1f",
                                (config.frameSize.toFloat() / config.sampleRate) * 1000
                            )
                        } ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Gain Multiplier Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Gain / Amplification Factor", style = MaterialTheme.typography.labelMedium)
                    Text("${String.format(Locale.US, "%.1f", config.gainMultiplier)}x", fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = config.gainMultiplier,
                    onValueChange = { onUpdateConfig(config.copy(gainMultiplier = it)) },
                    valueRange = 0.5f..5.0f,
                    enabled = !isRecording
                )
            }

            if (config.executionMode == ExecutionMode.OFFLINE_POST_RECORDING && config.offlineMethod == OfflineMethod.CSC) {
                var textValue by remember(config.cscAlphaMax) { mutableStateOf(config.cscAlphaMax.toString()) }
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { newValue ->
                        textValue = newValue
                        val limit = newValue.toIntOrNull()
                        if (limit != null && limit >= 1) {
                            onUpdateConfig(config.copy(cscAlphaMax = limit))
                        }
                    },
                    label = { Text("CSC Alpha Max (Hz)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRecording
                )
            }

            if (config.executionMode == ExecutionMode.OFFLINE_POST_RECORDING && config.offlineMethod == OfflineMethod.SPWVD) {
                var textValue by remember(config.spwvdTargetLen) { mutableStateOf(config.spwvdTargetLen.toString()) }
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { newValue ->
                        textValue = newValue
                        val limit = newValue.toIntOrNull()
                        if (limit != null && limit >= 1) {
                            onUpdateConfig(config.copy(spwvdTargetLen = limit))
                        }
                    },
                    label = { Text("SPWVD Target Length") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRecording
                )
            }
        }
    }
}

@Composable
private fun RecordingControlsSection(
    uiState: UiState,
    onRequestPermission: () -> Unit,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onResetState: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (uiState.recordingState) {
                RecordingState.IDLE -> {
                    Button(
                        onClick = {
                            if (uiState.permissionGranted) onStartCapture() else onRequestPermission()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = CircleShape,
                        modifier = Modifier.size(80.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Start Recording",
                                modifier = Modifier.size(36.dp)
                            )
                            Text("REC", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        text = "Tap to Start Capture (${uiState.config.executionMode.displayName})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                RecordingState.RECORDING -> {
                    val animatedRed by animateColorAsState(targetValue = Color(0xFFD32F2F), label = "Pulse")
                    Button(
                        onClick = onStopCapture,
                        colors = ButtonDefaults.buttonColors(containerColor = animatedRed),
                        shape = CircleShape,
                        modifier = Modifier.size(80.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop Recording",
                                modifier = Modifier.size(36.dp)
                            )
                            Text("STOP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        text = "Recording: ${String.format(Locale.US, "%.1f", uiState.recordedDurationSeconds)} s",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                RecordingState.PROCESSING -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Text(
                            text = "Running calculations...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Calculating ${uiState.config.offlineMethod.displayName} over recorded buffer",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                RecordingState.FINISHED -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onStartCapture,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("New Recording")
                        }
                        OutlinedButton(onClick = onResetState) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Reset")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveStreamingVisualizationSection(
    uiState: UiState,
    onUpdateConfig: (SignalProcConfig) -> Unit
) {
    val liveFrame = uiState.liveFrame
    val recordingState = uiState.recordingState
    val onlineMethod = uiState.config.onlineMethod
    val maxFrequencyLimit = uiState.config.maxFrequencyLimit
    val onUpdateMaxFrequency: (Int) -> Unit = { limit ->
        onUpdateConfig(uiState.config.copy(maxFrequencyLimit = limit))
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val title =
                if (onlineMethod == OnlineMethod.ENVELOPE_SPECTRUM) "Live Envelope Spectrum" else "Live Real-Time Waveform & Metrics"
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Live Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(Color(0xFF121212), shape = RoundedCornerShape(0.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(0.dp))
            ) {
                if (liveFrame != null && (liveFrame.pcmSamples.isNotEmpty() || liveFrame.envelopeSpectrumAmps.isNotEmpty())) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val amps = liveFrame.envelopeSpectrumAmps
                        if (onlineMethod == OnlineMethod.ENVELOPE_SPECTRUM && amps.isNotEmpty()) {
                            val width = size.width
                            val height = size.height
                            val graphHeight = height - 40f // Reserve bottom space for labels
                            val path = Path()
                            val stepX = width / amps.size.toFloat()
                            val maxAmp = liveFrame.maxAmpValue

                            for (i in amps.indices) {
                                val x = i * stepX
                                val normAmp = (amps[i] / maxAmp).coerceIn(0f, 1f)
                                val y = graphHeight - (normAmp * graphHeight * 0.99f)
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }

                            // Draw Grid Lines
                            drawLine(
                                color = Color.White.copy(alpha = 0.15f),
                                start = Offset(width * 0.33f, 0f),
                                end = Offset(width * 0.33f, graphHeight),
                                strokeWidth = 1f
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.15f),
                                start = Offset(width * 0.66f, 0f),
                                end = Offset(width * 0.66f, graphHeight),
                                strokeWidth = 1f
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.15f),
                                start = Offset(0f, graphHeight * 0.25f),
                                end = Offset(width, graphHeight * 0.25f),
                                strokeWidth = 1f
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.15f),
                                start = Offset(0f, graphHeight * 0.5f),
                                end = Offset(width, graphHeight * 0.5f),
                                strokeWidth = 1f
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.15f),
                                start = Offset(0f, graphHeight * 0.75f),
                                end = Offset(width, graphHeight * 0.75f),
                                strokeWidth = 1f
                            )

                            // Draw Spectrum line
                            drawPath(
                                path = path,
                                color = Color(0xFF11C8FF),
                                style = Stroke(width = 2f)
                            )

                            // Draw Horizontal baseline axis
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.5f),
                                start = Offset(0f, graphHeight),
                                end = Offset(width, graphHeight),
                                strokeWidth = 2f
                            )

                            // Draw Peak Markers & Labels
                            val peakCount = uiState.config.envelopePeakCount
                            val strongestPeakIndices = findStrongestPeaks(amps, peakCount)
                            for (peakIdx in strongestPeakIndices) {
                                val px = peakIdx * stepX
                                val normAmp = (amps[peakIdx] / maxAmp).coerceIn(0f, 1f)
                                val py = graphHeight - (normAmp * graphHeight * 0.99f)

                                // Draw vertical dashed line to baseline
                                drawLine(
                                    color = Color(0xFFFFEB3B).copy(alpha = 0.4f),
                                    start = Offset(px, py),
                                    end = Offset(px, graphHeight),
                                    strokeWidth = 1f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                )

                                // Draw dot at peak
                                drawCircle(
                                    color = Color(0xFFFFEB3B),
                                    radius = 5f,
                                    center = Offset(px, py)
                                )

                                // Draw text label
                                val freqHz = liveFrame.envelopeSpectrumFreqs.getOrNull(peakIdx) ?: 0f
                                val label = String.format(Locale.US, "%.1f Hz", freqHz)
                                drawContext.canvas.nativeCanvas.apply {
                                    val textPaint = android.graphics.Paint().apply {
                                        color = android.graphics.Color.YELLOW
                                        textSize = 24f
                                        textAlign = android.graphics.Paint.Align.CENTER
                                        isAntiAlias = true
                                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                                        setShadowLayer(4f, 0f, 2f, android.graphics.Color.BLACK)
                                    }
                                    drawText(label, px, (py - 12f).coerceAtLeast(24f), textPaint)
                                }
                            }

                            // Draw Axis Ticks & Labels
                            drawContext.canvas.nativeCanvas.apply {
                                val paint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.GRAY
                                    textSize = 28f
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    isAntiAlias = true
                                }
                                drawText("0 Hz", 45f, height - 10f, paint)
                                drawText("${maxFrequencyLimit / 3} Hz", width * 0.33f, height - 10f, paint)
                                drawText("${2 * maxFrequencyLimit / 3} Hz", width * 0.66f, height - 10f, paint)
                                drawText("$maxFrequencyLimit Hz", width - 60f, height - 10f, paint)
                            }
                        } else {
                            val samples = liveFrame.pcmSamples
                            val width = size.width
                            val height = size.height
                            val centerY = height / 2f
                            val path = Path()
                            val stepX = width / samples.size.toFloat()

                            for (i in samples.indices) {
                                val x = i * stepX
                                val y = centerY - (samples[i] * centerY * 0.99f)
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }

                            drawPath(
                                path = path,
                                color = Color(0xFF00E676),
                                style = Stroke(width = 2f)
                            )

                            // Center zero-crossing baseline
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.4f),
                                start = Offset(0f, centerY),
                                end = Offset(width, centerY),
                                strokeWidth = 1f
                            )
                        }
                    }
                } else {
                    Text(
                        text = if (recordingState == RecordingState.RECORDING) "Listening for live audio stream..." else "Start recording to stream live audio.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            if (onlineMethod == OnlineMethod.ENVELOPE_SPECTRUM) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var textFreqLimit by remember(maxFrequencyLimit) { mutableStateOf(maxFrequencyLimit.toString()) }
                    OutlinedTextField(
                        value = textFreqLimit,
                        onValueChange = { newValue ->
                            textFreqLimit = newValue
                            val limit = newValue.toIntOrNull()
                            if (limit != null && limit >= 10) {
                                onUpdateMaxFrequency(limit)
                            }
                        },
                        label = { Text("Freq Limit (Hz)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    var textAvgCount by remember(uiState.config.envelopeAverageCount) { mutableStateOf(uiState.config.envelopeAverageCount.toString()) }
                    OutlinedTextField(
                        value = textAvgCount,
                        onValueChange = { newValue ->
                            textAvgCount = newValue
                            val count = newValue.toIntOrNull()
                            if (count != null && count >= 1) {
                                onUpdateConfig(uiState.config.copy(envelopeAverageCount = count))
                            }
                        },
                        label = { Text("Average Count (N)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    var textPeakCount by remember(uiState.config.envelopePeakCount) { mutableStateOf(uiState.config.envelopePeakCount.toString()) }
                    OutlinedTextField(
                        value = textPeakCount,
                        onValueChange = { newValue ->
                            textPeakCount = newValue
                            val count = newValue.toIntOrNull()
                            if (count != null && count in 1..5) {
                                onUpdateConfig(uiState.config.copy(envelopePeakCount = count))
                            }
                        },
                        label = { Text("Peaks (1-5)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineProcessingVisualizationSection(
    uiState: UiState,
    onSelectRecording: (String) -> Unit,
    onDeleteRecording: (String) -> Unit,
    onRunOfflineAnalysis: () -> Unit
) {
    val recordingState = uiState.recordingState
    val offlineResult = uiState.offlineResult
    val durationSec = uiState.recordedDurationSeconds
    val recordings = uiState.recordings
    val selectedId = uiState.selectedRecordingId

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(5.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Offline Post-Recording Results",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (recordingState == RecordingState.RECORDING) {
                // Live preview during recording in offline mode
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Capturing Audio to Memory: ${String.format(Locale.US, "%.1f", durationSec)} s",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Click STOP to save recording, then click Run to calculate.",
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                // Display list of saved recordings if not recording
                if (recordings.isNotEmpty()) {
                    Text(
                        text = "Saved Audio Recordings",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        recordings.forEach { recording ->
                            val isSelected = recording.id == selectedId
                            val timeStr = remember(recording.timestamp) {
                                val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                                sdf.format(java.util.Date(recording.timestamp))
                            }
                            val recDuration = recording.samples.size.toFloat() / recording.sampleRate

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(
                                            alpha = 0.5f
                                        ),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable { onSelectRecording(recording.id) }
                                    .padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Recording - $timeStr",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "${
                                            String.format(
                                                Locale.US,
                                                "%.2f",
                                                recDuration
                                            )
                                        } s | ${recording.sampleRate} Hz | ${recording.samples.size} samples",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isSelected) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.padding(end = 3.dp)
                                        ) {
                                            Text(
                                                text = "Selected",
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onDeleteRecording(recording.id) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete recording",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Prominent button to launch calculations with selected parameters
                    if (selectedId != null) {
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = onRunOfflineAnalysis,
                            enabled = recordingState != RecordingState.PROCESSING,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (recordingState == RecordingState.PROCESSING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Calculating ${uiState.config.offlineMethod.displayName}...")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Run ${uiState.config.offlineMethod.displayName}")
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Record audio in Offline Mode to view post-analysis spectrogram and stats.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }

                if (offlineResult != null) {
                    Spacer(Modifier.height(8.dp))
                    // Display calculated results
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            title = "Duration",
                            value = "${String.format(Locale.US, "%.2f", offlineResult.totalDurationSeconds)} s",
                            subtitle = "${offlineResult.totalSamplesRecorded} samples",
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Dominant Freq",
                            value = "${offlineResult.dominantFrequencyHz.toInt()} Hz",
                            subtitle = "Spectral Peak",
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Batch Time",
                            value = "${offlineResult.processingTimeMs} ms",
                            subtitle = "Calculation Latency",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    uiState.offlineResults.forEach { res ->
                        res.plotImageBytes?.let { bytes ->
                            Spacer(modifier = Modifier.height(10.dp))
                            val title = if (res.methodName.isNotEmpty()) res.methodName else "Python Generated Plot"
                            ZoomablePlotCard(
                                title = title,
                                imageBytes = bytes
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomablePlotCard(
    title: String,
    imageBytes: ByteArray
) {
    var showFullscreen by remember { mutableStateOf(false) }

    val bitmap = remember(imageBytes) {
        try {
            android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = { showFullscreen = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Fullscreen Zoom",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .clickable { showFullscreen = true }
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = title,
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Tap to Zoom",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (showFullscreen) {
            FullscreenPlotViewerDialog(
                title = title,
                bitmap = bitmap,
                onDismiss = { showFullscreen = false }
            )
        }
    }
}

@Composable
private fun FullscreenPlotViewerDialog(
    title: String,
    bitmap: android.graphics.Bitmap,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0F0F0F)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Main Interactive Image Canvas
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    scale = if (scale > 1.2f) 1f else 2.5f
                                    offset = Offset.Zero
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 8f)
                                val maxPanX = (newScale - 1f) * 1200f
                                val maxPanY = (newScale - 1f) * 1200f
                                val newOffsetX = if (newScale > 1f) {
                                    (offset.x + pan.x).coerceIn(-maxPanX, maxPanX)
                                } else 0f
                                val newOffsetY = if (newScale > 1f) {
                                    (offset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                                } else 0f

                                scale = newScale
                                offset = Offset(newOffsetX, newOffsetY)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                    )
                }

                // Top Toolbar
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Pinch to zoom • Drag to pan • Double-tap to reset (${String.format(Locale.US, "%.1fx", scale)})",
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (scale > 1.05f) {
                                IconButton(
                                    onClick = {
                                        scale = 1f
                                        offset = Offset.Zero
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RestartAlt,
                                        contentDescription = "Reset Zoom",
                                        tint = Color.White
                                    )
                                }
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 2.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = Color.Gray)
        }
    }
}

/**
 * Returns a color mapping from dark blue (0.0) -> cyan (0.33) -> yellow (0.66) -> red (1.0).
 */
private fun getHeatmapColor(normalizedValue: Float): Color {
    val v = normalizedValue.coerceIn(0f, 1f)
    return when {
        v < 0.25f -> {
            val factor = v / 0.25f
            Color(0f, 0f, factor * 0.8f, 1f)
        }

        v < 0.5f -> {
            val factor = (v - 0.25f) / 0.25f
            Color(0f, factor, 1f - (factor * 0.2f), 1f)
        }

        v < 0.75f -> {
            val factor = (v - 0.5f) / 0.25f
            Color(factor, 1f, 0f, 1f)
        }

        else -> {
            val factor = (v - 0.75f) / 0.25f
            Color(1f, 1f - factor, 0f, 1f)
        }
    }
}

private fun findStrongestPeaks(amps: FloatArray, peakCount: Int): List<Int> {
    if (amps.size < 3 || peakCount <= 0) return emptyList()
    val peaks = mutableListOf<Pair<Int, Float>>()
    for (i in 1 until amps.size - 1) {
        if (amps[i] > amps[i - 1] && amps[i] > amps[i + 1]) {
            peaks.add(i to amps[i])
        }
    }
    return peaks.sortedByDescending { it.second }.take(peakCount).map { it.first }
}
