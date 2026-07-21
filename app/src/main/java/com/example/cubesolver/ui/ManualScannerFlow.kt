package com.example.cubesolver.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.cubesolver.utils.ColorUtils
import com.example.cubesolver.utils.CubeValidator

@Composable
fun ManualScannerFlow(
    modifier: Modifier = Modifier,
    onResultFound: (List<List<Color>>) -> Unit,
    onBackToHome: () -> Unit
) {
    // 拦截系统返回键并回到主菜单
    BackHandler {
        onBackToHome()
    }

    var currentFaceIndex by remember { mutableIntStateOf(0) }
    val capturedFaces = remember { mutableStateListOf<List<Color>>() }

    var validStates by remember { mutableStateOf<List<List<List<Color>>>>(emptyList()) }
    var isChoosingState by remember { mutableStateOf(false) }
    var hasUserChosenState by remember { mutableStateOf(false) }

    if (isChoosingState) {
        StateSelectionScreen(
            modifier = modifier,
            validStates = validStates,
            onStateSelected = { selectedFaces ->
                isChoosingState = false
                hasUserChosenState = true
                onResultFound(selectedFaces)
            }
        )
    } else {
        CameraScanner(
            modifier = modifier,
            currentFaceIndex = currentFaceIndex,
            onBack = onBackToHome,
            onFaceCaptured = { colors ->
                capturedFaces.add(colors)

                if (currentFaceIndex < 5) {
                    currentFaceIndex++
                } else {
                    // 六个面扫描完成后，统一通过 Hungarian 算法解析颜色并验证魔方合法状态
                    val resolvedFaces = ColorUtils.resolveColorsWithHungarian(capturedFaces)
                    val dynamicCenterColors = resolvedFaces.map { it[4] }
                    val states = CubeValidator.resolveAllValidStates(resolvedFaces, dynamicCenterColors)

                    if (states.size > 1) {
                        validStates = states
                        isChoosingState = true
                        hasUserChosenState = false
                    } else {
                        val finalFaces = if (states.isNotEmpty()) states[0] else resolvedFaces
                        onResultFound(finalFaces)
                    }
                }
            }
        )
    }
}
