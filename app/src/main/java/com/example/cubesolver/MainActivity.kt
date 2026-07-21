package com.example.cubesolver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.example.cubesolver.ui.CubeCameraScreen
import com.example.cubesolver.ui.HomeScreen
import com.example.cubesolver.ui.ScanMode
import com.example.cubesolver.ui.result.ResultScreenWrapper
import com.example.cubesolver.ui.theme.CubesolverTheme
import com.example.cubesolver.utils.CubeSolver
import com.example.cubesolver.utils.ScrambleUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// 应用页面状态，使用密封类以支持携带扫描模式参数
sealed class AppState {
    object Home : AppState()
    data class Scanning(val mode: ScanMode) : AppState()
    object Solving : AppState()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 后台异步初始化 C++ 求解器，避免阻塞主线程
        lifecycleScope.launch(Dispatchers.Default) {
            CubeSolver.asyncInit(applicationContext)
        }

        setContent {
            CubesolverTheme {
                var currentState by remember { mutableStateOf<AppState>(AppState.Home) }
                var currentFaces by remember { mutableStateOf<List<List<Color>>>(emptyList()) }
                var currentScramble by remember { mutableStateOf<String?>(null) }
                // 记录上一次使用的扫描模式，以便在结果页点击"重新扫描"时返回对应的模式
                var lastScanMode by remember { mutableStateOf(ScanMode.MANUAL) }

                // 记录当前状态是否已经过扫描端的去重选择
                var scanChoiceCompleted by remember { mutableStateOf(false) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (val state = currentState) {
                            is AppState.Home -> {
                                HomeScreen(
                                    onStartScan = { mode ->
                                        lastScanMode = mode
                                        scanChoiceCompleted = false
                                        currentState = AppState.Scanning(mode)
                                    },
                                    onStartScramble = { faces, formula ->
                                        currentFaces = faces
                                        currentScramble = formula
                                        scanChoiceCompleted = false // 打乱模式不需要提前确认
                                        currentState = AppState.Solving
                                    }
                                )
                            }
                            is AppState.Scanning -> {
                                CubeCameraScreen(
                                    scanMode = state.mode,
                                    onScanComplete = { faces ->
                                        currentFaces = faces
                                        currentScramble = null
                                        scanChoiceCompleted = true // 扫描端已完成了可能的状态确认
                                        currentState = AppState.Solving
                                    },
                                    onBack = { currentState = AppState.Home }
                                )
                            }
                            is AppState.Solving -> {
                                ResultScreenWrapper(
                                    initialFaces = currentFaces,
                                    scrambleFormula = currentScramble,
                                    initialHasUserChosenState = scanChoiceCompleted,
                                    onRetry = {
                                        // 根据此前是打乱还是扫描决定“重试”去向
                                        if (currentScramble != null) {
                                            val f = ScrambleUtils.generateScrambleFormula()
                                            currentFaces = ScrambleUtils.getScrambledState(f)
                                            currentScramble = f
                                            scanChoiceCompleted = false
                                        } else {
                                            currentState = AppState.Scanning(lastScanMode)
                                        }
                                    },
                                    onExit = { currentState = AppState.Home }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
