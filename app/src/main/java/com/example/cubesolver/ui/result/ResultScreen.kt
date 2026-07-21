package com.example.cubesolver.ui.result

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.cubesolver.ui.CubePlayerScreen
import com.example.cubesolver.ui.FlatCubeView
import com.example.cubesolver.ui.Matrix3x3
import com.example.cubesolver.ui.RubiksCube3D
import com.example.cubesolver.ui.UserGuideContent
import com.example.cubesolver.utils.CubeSolver
import com.example.cubesolver.utils.CubeValidator
import com.example.cubesolver.utils.SearchStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    modifier: Modifier = Modifier,
    faces: List<List<Color>>,
    hasUserChosenState: Boolean,
    scrambleFormula: String? = null,
    onFaceColorChanged: (faceIndex: Int, stickerIndex: Int, newColor: Color) -> Unit,
    onFacesReoriented: (List<List<Color>>) -> Unit,
    onMultipleStatesFound: (List<List<List<Color>>>) -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit
) {
    var showPlayer by remember { mutableStateOf(false) }

    var showGuideSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    BackHandler {
        if (showPlayer) {
            showPlayer = false
        } else if (showGuideSheet) {
            showGuideSheet = false
        } else {
            onExit()
        }
    }

    val context = LocalContext.current
    val sharedPrefs = remember {
        context.getSharedPreferences("cube_solver_preferences", Context.MODE_PRIVATE)
    }

    var solutionText by remember { mutableStateOf("正在计算求解步骤...") }
    var isStateValid by remember { mutableStateOf(false) }
    var selectedColorIndex by remember { mutableIntStateOf(0) }
    var isFlatView by remember { mutableStateOf(false) }
    var isManualEditMode by remember { mutableStateOf(true) }

    var isMinimalMode by remember {
        mutableStateOf(sharedPrefs.getBoolean("is_minimal_mode", false))
    }

    val originalFaces = remember { faces.map { it.toList() } }
    // 持久化的 6 色调色盘状态，由初始扫描结果的中心块初始化
    val paletteColors = remember(originalFaces) {
        mutableStateListOf<Color>().apply {
            addAll(originalFaces.map { it[4] })
        }
    }

    var isSearchingOptimal by remember { mutableStateOf(false) }
    var optimalSearchJob by remember { mutableStateOf<Job?>(null) }
    var searchedNodes by remember { mutableLongStateOf(0L) }
    var currentSearchDepth by remember { mutableIntStateOf(0) }
    var optimalStatusMsg by remember { mutableStateOf("") }
    var searchStartTime by remember { mutableLongStateOf(0L) }

    var isSearchingShorter by remember { mutableStateOf(false) }
    var shorterSearchJob by remember { mutableStateOf<Job?>(null) }

    val coroutineScope = rememberCoroutineScope()

    val initialRotation = remember {
        val radX = Math.toRadians(25.0).toFloat()
        val radY = Math.toRadians(-45.0).toFloat()
        Matrix3x3.rotationX(radX) * Matrix3x3.rotationY(radY)
    }
    var editRotationMatrix by remember { mutableStateOf(initialRotation) }
    val flatColors = faces.flatMap { it }

    LaunchedEffect(flatColors) {
        isSearchingOptimal = false
        optimalSearchJob?.cancel()
        isSearchingShorter = false
        shorterSearchJob?.cancel()
        optimalStatusMsg = ""

        solutionText = "正在分析魔方状态..."
        val dynamicCenterColors = faces.map { it[4] }

        val resolvedStates = CubeValidator.resolveAllValidStates(faces, dynamicCenterColors)
        val pureFaces = faces.map { it.toList() }
        val isCurrentlyValid = resolvedStates.contains(pureFaces)

        if (resolvedStates.isNotEmpty()) {
            if (resolvedStates.size > 1 && !hasUserChosenState) {
                onMultipleStatesFound(resolvedStates)
            } else {
                val resolved = if (isCurrentlyValid) pureFaces else resolvedStates[0]

                if (!isStateValid) isManualEditMode = false
                isStateValid = true
                if (resolved != pureFaces) {
                    onFacesReoriented(resolved)
                    // 面对齐重构后，同步更新调色盘颜色顺序，以对齐标准面
                    paletteColors.clear()
                    paletteColors.addAll(resolved.map { it[4] })
                }
                solutionText = CubeSolver.solve(resolved)
            }
        } else {
            isStateValid = false
            isManualEditMode = true
            val rawResult = CubeSolver.solve(faces)
            solutionText = when {
                rawResult.contains("Error 1") -> "错误: 颜色数量不正确"
                rawResult.contains("Error 2") -> "错误: 棱块不完整或存在重复"
                rawResult.contains("Error 3") -> "错误: 发现被翻转的单个棱块"
                rawResult.contains("Error 4") -> "错误: 角块不完整或存在重复"
                rawResult.contains("Error 5") -> "错误: 发现被扭转的单个角块"
                rawResult.contains("Error 6") -> "错误: 存在对称性错误"
                rawResult.contains("Error 7") -> "未能在限制步数内找到解法"
                rawResult.contains("Error 8") -> "计算超时"
                else -> "发生未知的魔方状态错误"
            }
        }
    }

    if (showPlayer) {
        CubePlayerScreen(
            modifier = modifier,
            initialFaces = faces,
            solutionText = solutionText,
            onBack = { showPlayer = false }
        )
    } else {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            // 允许页面整体上下滑动
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onExit) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回主菜单",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Text(
                        text = if (scrambleFormula != null) "随机打乱" else " 扫描结果",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.weight(1f))

                    IconButton(onClick = { showGuideSheet = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "使用说明",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    FilledTonalButton(
                        onClick = {
                            val nextMode = !isMinimalMode
                            isMinimalMode = nextMode
                            sharedPrefs.edit().putBoolean("is_minimal_mode", nextMode).apply()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(if (isMinimalMode) "专业模式" else "极简模式", style = MaterialTheme.typography.labelMedium)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledTonalButton(
                        onClick = { isFlatView = !isFlatView },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(if (isFlatView) "3D" else "2D", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (!isStateValid && !isMinimalMode) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            "⚠️ 魔方状态不合法，请调整色块",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // 使用 aspectRatio(1f) 确保模型呈正方形，防止显示被挤压
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isFlatView) {
                        FlatCubeView(
                            faces = faces,
                            onStickerClick = { faceIndex, stickerIndex ->
                                if (!isMinimalMode && isManualEditMode && !isSearchingOptimal && !isSearchingShorter && selectedColorIndex < paletteColors.size) {
                                    onFaceColorChanged(faceIndex, stickerIndex, paletteColors[selectedColorIndex])
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        RubiksCube3D(
                            faces = faces,
                            rotationMatrix = editRotationMatrix,
                            onRotationChanged = { editRotationMatrix = it },
                            onStickerClick = { faceIndex, stickerIndex ->
                                if (!isMinimalMode && isManualEditMode && !isSearchingOptimal && !isSearchingShorter && selectedColorIndex < paletteColors.size) {
                                    onFaceColorChanged(faceIndex, stickerIndex, paletteColors[selectedColorIndex])
                                }
                            },
                            onDoubleClick = { editRotationMatrix = initialRotation },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                if (isMinimalMode) {
                    MinimalistConsole(
                        isStateValid = isStateValid,
                        solutionText = solutionText,
                        scrambleFormula = scrambleFormula,
                        isSearchingOptimal = isSearchingOptimal,
                        isSearchingShorter = isSearchingShorter,
                        onRetry = onRetry,
                        onShowPlayer = { showPlayer = true }
                    )
                } else {
                    ProfessionalConsole(
                        faces = faces,
                        solutionText = solutionText,
                        isStateValid = isStateValid,
                        scrambleFormula = scrambleFormula,
                        originalFaces = originalFaces,
                        dynamicPalette = paletteColors,
                        isManualEditMode = isManualEditMode,
                        onManualEditModeChange = { isManualEditMode = it },
                        selectedColorIndex = selectedColorIndex,
                        onSelectedColorIndexChange = { selectedColorIndex = it },
                        onFaceColorChanged = onFaceColorChanged,
                        onFacesReoriented = { reorientedFaces ->
                            onFacesReoriented(reorientedFaces)
                            // 用户手动点击“重置色调”时，同步恢复调色盘初始色
                            paletteColors.clear()
                            paletteColors.addAll(reorientedFaces.map { it[4] })
                        },
                        onRetry = onRetry,
                        onShowPlayer = { showPlayer = true },
                        isSearchingOptimal = isSearchingOptimal,
                        isSearchingShorter = isSearchingShorter,
                        searchedNodes = searchedNodes,
                        currentSearchDepth = currentSearchDepth,
                        optimalStatusMsg = optimalStatusMsg,
                        searchStartTime = searchStartTime,
                        onStartOptimalSearch = {
                            if (isSearchingShorter) {
                                shorterSearchJob?.cancel()
                                isSearchingShorter = false
                            }
                            isSearchingOptimal = true
                            searchedNodes = 0L
                            searchStartTime = System.currentTimeMillis()
                            currentSearchDepth = 0
                            optimalStatusMsg = "ovo"

                            val currentSteps = solutionText.replace(Regex("\\(\\d+f\\)"), "").trim().split(Regex("\\s+")).count { it.isNotBlank() }
                            val initialMaxDepth = if (currentSteps > 0) currentSteps - 1 else 20

                            optimalSearchJob = coroutineScope.launch {
                                CubeSolver.solveOptimalFlow(faces, initialMaxDepth).collect { status ->
                                    when (status) {
                                        is SearchStatus.Searching -> {
                                            searchedNodes = status.nodesSearched
                                            currentSearchDepth = status.currentDepth
                                        }
                                        is SearchStatus.NewSolution -> {
                                            solutionText = status.solution
                                            optimalStatusMsg = "当前解法已缩短至 ${status.steps} 步"
                                        }
                                        is SearchStatus.Finished -> {
                                            isSearchingOptimal = false
                                            optimalStatusMsg = "极限界限到达！此魔方最优步数已确定。"
                                        }
                                        is SearchStatus.Error -> {
                                            isSearchingOptimal = false
                                            if (status.message.contains("Error 7") || status.message.contains("Error 8")) {
                                                optimalStatusMsg = "全空间遍历结束，已无法找到更短路径。"
                                            } else {
                                                optimalStatusMsg = "搜索异常中止: ${status.message}"
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        onStopOptimalSearch = {
                            optimalSearchJob?.cancel()
                            isSearchingOptimal = false
                            optimalStatusMsg = "已手动停止，当前保留最优解法。"
                        },
                        onStartSqueezeSearch = {
                            if (isSearchingOptimal) {
                                optimalSearchJob?.cancel()
                                isSearchingOptimal = false
                            }
                            isSearchingShorter = true
                            searchedNodes = 0L
                            searchStartTime = System.currentTimeMillis()
                            currentSearchDepth = 0
                            optimalStatusMsg = "ovo"

                            val currentSteps = solutionText.replace(Regex("\\(\\d+f\\)"), "").trim().split(Regex("\\s+")).count { it.isNotBlank() }
                            if (currentSteps <= 1) {
                                optimalStatusMsg = "步数已缩减至物理极限，无法继续压榨步数。"
                                isSearchingShorter = false
                            } else {
                                val targetSteps = currentSteps - 1
                                shorterSearchJob = coroutineScope.launch {
                                    CubeSolver.solveSqueezeFlow(faces, targetSteps).collect { status ->
                                        when (status) {
                                            is SearchStatus.Searching -> {
                                                searchedNodes = status.nodesSearched
                                                currentSearchDepth = status.currentDepth
                                            }
                                            is SearchStatus.NewSolution -> {
                                                solutionText = status.solution
                                                val compressedSteps = status.steps
                                                optimalStatusMsg = "已成功缩短至 $compressedSteps 步"
                                            }
                                            is SearchStatus.Finished -> {
                                                isSearchingShorter = false
                                                optimalStatusMsg = "全分支深度搜寻完毕，已无法压缩更少步数。"
                                            }
                                            is SearchStatus.Error -> {
                                                isSearchingShorter = false
                                                if (status.message.contains("Error 7") || status.message.contains("SHORT_DEPTH")) {
                                                    optimalStatusMsg = "未能找到更短的计算解法。"
                                                } else {
                                                    optimalStatusMsg = "中断: ${status.message}"
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        onStopSqueezeSearch = {
                            shorterSearchJob?.cancel()
                            isSearchingShorter = false
                            optimalStatusMsg = "步数压榨已暂停，当前保留最短步骤。"
                        },
                        // 应用微调色时同步更新魔方块和调色盘
                        onPaletteColorApplied = { index, oldColor, newColor ->
                            paletteColors[index] = newColor
                            for (faceIndex in 0..5) {
                                for (stickerIndex in 0..8) {
                                    if (faces[faceIndex][stickerIndex] == oldColor) {
                                        onFaceColorChanged(faceIndex, stickerIndex, newColor)
                                    }
                                }
                            }
                        }
                    )
                }

                // 底部留白，确保滑到最底端不会紧贴屏幕边缘
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (showGuideSheet) {
            ModalBottomSheet(
                onDismissRequest = { showGuideSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxHeight(0.9f)
            ) {
                UserGuideContent()
            }
        }
    }
}