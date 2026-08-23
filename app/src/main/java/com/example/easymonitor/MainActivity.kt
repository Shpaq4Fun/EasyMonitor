package com.example.easymonitor

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.easymonitor.ui.theme.EasyMonitorTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            // Permission Request Launcher
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) { isGranted ->
                viewModel.checkPermissionStatus()
                if (isGranted) {
                    Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this,
                        "Microphone permission is required for signal capture",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            EasyMonitorTheme {
                HomeScreen(
                    uiState = uiState,
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onStartCapture = {
                        if (uiState.permissionGranted) {
                            viewModel.startCapture()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onStopCapture = {
                        viewModel.stopCapture()
                    },
                    onResetState = viewModel::resetState,
                    onUpdateConfig = viewModel::updateConfig,
                    onSelectRecording = viewModel::selectRecording,
                    onDeleteRecording = viewModel::deleteRecording,
                    onSelectOfflineMethod = viewModel::selectOfflineMethod,
                    onRunOfflineAnalysis = viewModel::runOfflineAnalysis
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkPermissionStatus()
    }
}
