package com.example.cubesolver.ui.result

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.cubesolver.ui.StateSelectionScreen

@Composable
fun ResultScreenWrapper(
    initialFaces: List<List<Color>>,
    scrambleFormula: String? = null,
    initialHasUserChosenState: Boolean = false,
    onRetry: () -> Unit,
    onExit: () -> Unit
) {
    val editableFaces = remember(initialFaces) {
        mutableStateListOf<List<Color>>().apply { addAll(initialFaces) }
    }

    var validStates by remember { mutableStateOf<List<List<List<Color>>>>(emptyList()) }
    var isChoosingState by remember { mutableStateOf(false) }

    // 使用 initialHasUserChosenState 作为初始值，并跟随 initialFaces 变化进行重置
    var hasUserChosenState by remember(initialFaces) {
        mutableStateOf(initialHasUserChosenState)
    }

    if (isChoosingState) {
        StateSelectionScreen(
            validStates = validStates,
            onStateSelected = { selectedFaces ->
                editableFaces.clear()
                editableFaces.addAll(selectedFaces)
                isChoosingState = false
                hasUserChosenState = true
            }
        )
    } else {
        ResultScreen(
            faces = editableFaces,
            hasUserChosenState = hasUserChosenState,
            scrambleFormula = scrambleFormula,
            onFaceColorChanged = { faceIndex, stickerIndex, newColor ->
                if (faceIndex in 0..5 && stickerIndex in 0..8) {
                    val updatedFace = editableFaces[faceIndex].toMutableList()
                    updatedFace[stickerIndex] = newColor
                    editableFaces[faceIndex] = updatedFace
                    // 手动修改颜色后重置状态，允许重新校验
                    hasUserChosenState = false
                }
            },
            onFacesReoriented = { reorientedFaces ->
                editableFaces.clear()
                editableFaces.addAll(reorientedFaces)
            },
            onMultipleStatesFound = { states ->
                validStates = states
                isChoosingState = true
            },
            onRetry = onRetry,
            onExit = onExit
        )
    }
}
