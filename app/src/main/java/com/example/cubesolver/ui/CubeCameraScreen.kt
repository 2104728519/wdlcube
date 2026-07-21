package com.example.cubesolver.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

enum class ScanMode { MANUAL, AUTO }

@Composable
fun CubeCameraScreen(
    modifier: Modifier = Modifier,
    scanMode: ScanMode,
    onScanComplete: (List<List<Color>>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    if (!hasCameraPermission) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("授予相机权限")
            }
        }
    } else {
        when (scanMode) {
            ScanMode.MANUAL -> {
                ManualScannerFlow(
                    modifier = modifier,
                    onResultFound = onScanComplete,
                    onBackToHome = onBack
                )
            }
            ScanMode.AUTO -> {
                AutoScannerFlow(
                    modifier = modifier,
                    onResultFound = onScanComplete,
                    onBackToHome = onBack
                )
            }
        }
    }
}
