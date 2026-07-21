package com.example.cubesolver.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CubePlayerScreen(
    modifier: Modifier = Modifier,
    initialFaces: List<List<Color>>,
    solutionText: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    val moves = remember(solutionText) {
        if (solutionText.isBlank() || solutionText.contains("Error") || solutionText.contains("错误")) {
            emptyList()
        } else {
            solutionText.replace(Regex("\\(\\d+f\\)"), "").trim()
                .split(Regex("\\s+")).filter { it.isNotEmpty() }
        }
    }

    val statesList = remember(initialFaces, moves) {
        val list = mutableListOf<List<List<Color>>>()
        list.add(initialFaces)
        var temp = initialFaces
        for (move in moves) {
            temp = CubeSimulator.applyMove(temp, move)
            list.add(temp)
        }
        list
    }

    var currentStep by remember { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackSpeedMs by remember { mutableLongStateOf(800L) }

    var animatingMove by remember { mutableStateOf<String?>(null) }
    var animationProgress by remember { mutableFloatStateOf(0f) }

    val initialRotation = remember {
        val radX = Math.toRadians(25.0).toFloat()
        val radY = Math.toRadians(-45.0).toFloat()
        Matrix3x3.rotationX(radX) * Matrix3x3.rotationY(radY)
    }
    var rotationMatrix by remember { mutableStateOf(initialRotation) }

    suspend fun animateToStep(targetStep: Int) {
        if (targetStep == currentStep) return

        if (targetStep > currentStep && targetStep - 1 in moves.indices) {
            val move = moves[targetStep - 1]
            try {
                animatingMove = move
                animationProgress = 0f

                val animDuration = (playbackSpeedMs * 0.7f).coerceIn(150f, 600f).toLong()
                val startTime = System.currentTimeMillis()

                while (true) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val progress = (elapsed.toFloat() / animDuration).coerceAtMost(1f)
                    animationProgress = progress
                    if (progress >= 1f) break
                    kotlinx.coroutines.delay(16)
                }
                currentStep = targetStep
            } finally {
                animatingMove = null
                animationProgress = 0f
            }
        } else {
            currentStep = targetStep
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying && currentStep < moves.size) {
                animateToStep(currentStep + 1)
                val idleDelay = (playbackSpeedMs * 0.3f).coerceAtLeast(50f).toLong()
                kotlinx.coroutines.delay(idleDelay)
            }
            if (currentStep >= moves.size) {
                isPlaying = false
            }
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(currentStep) {
        if (moves.isNotEmpty()) {
            val targetIndex = (currentStep - 1).coerceIn(0, moves.size - 1)
            listState.animateScrollToItem(targetIndex)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            TopAppBar(
                title = { Text("3D 解步演示", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )

            if (moves.isNotEmpty()) {
                LazyRow(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(moves) { index, move ->
                        val isCurrent = index == currentStep
                        FilterChip(
                            selected = isCurrent,
                            onClick = {
                                isPlaying = false
                                animatingMove = null
                                animationProgress = 0f
                                currentStep = index
                            },
                            label = { Text("${index + 1}. $move") }
                        )
                    }
                }
            }

            Text(
                text = when {
                    currentStep == 0 -> "当前状态：初始混乱"
                    currentStep >= moves.size -> "🎉 已复原完成！"
                    else -> "正在执行第 $currentStep / ${moves.size} 步: 【${moves[currentStep - 1]}】"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                val displayStateFaces = statesList[currentStep.coerceIn(0, statesList.size - 1)]
                AnimatedRubiksCube3D(
                    faces = displayStateFaces,
                    rotationMatrix = rotationMatrix,
                    onRotationChanged = { newMatrix -> rotationMatrix = newMatrix },
                    animatingMove = animatingMove,
                    animationProgress = animationProgress,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("速度", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = playbackSpeedMs.toFloat(),
                            onValueChange = { playbackSpeedMs = it.toLong() },
                            valueRange = 250f..1800f,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(onClick = {
                            isPlaying = false
                            animatingMove = null
                            animationProgress = 0f
                            currentStep = 0
                            rotationMatrix = initialRotation
                        }) {
                            Text("↺", style = MaterialTheme.typography.titleLarge)
                        }

                        FilledTonalIconButton(
                            onClick = {
                                isPlaying = false
                                animatingMove = null
                                animationProgress = 0f
                                if (currentStep > 0) currentStep--
                            },
                            enabled = currentStep > 0
                        ) {
                            Text("⏮", style = MaterialTheme.typography.titleLarge)
                        }

                        Button(
                            onClick = {
                                if (isPlaying) {
                                    isPlaying = false
                                    animatingMove = null
                                    animationProgress = 0f
                                } else {
                                    isPlaying = true
                                }
                            },
                            modifier = Modifier.width(120.dp)
                        ) {
                            Text(if (isPlaying) " ⏸" else " ▶")
                        }

                        FilledTonalIconButton(
                            onClick = {
                                isPlaying = false
                                animatingMove = null
                                animationProgress = 0f
                                scope.launch { animateToStep(currentStep + 1) }
                            },
                            enabled = currentStep < moves.size
                        ) {
                            Text("⏭", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
    }
}
