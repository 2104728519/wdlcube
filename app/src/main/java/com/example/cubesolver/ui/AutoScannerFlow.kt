package com.example.cubesolver.ui

import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.cubesolver.camera.YoloNcnn
import com.example.cubesolver.utils.ColorUtils
import com.example.cubesolver.utils.CubeValidator
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoScannerFlow(
    modifier: Modifier = Modifier,
    onResultFound: (List<List<Color>>) -> Unit,
    onBackToHome: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? Activity

    val sharedPrefs = remember { context.getSharedPreferences("cube_solver_preferences", Context.MODE_PRIVATE) }

    var facing by remember { mutableIntStateOf(1) }
    var sizeId by remember { mutableIntStateOf(1) }
    var fpsCap by remember { mutableFloatStateOf(sharedPrefs.getInt("yolo_fps_cap", 15).toFloat()) }
    var probThreshold by remember { mutableFloatStateOf(sharedPrefs.getFloat("yolo_prob_threshold", 80f)) }

    // 控制 UI 显隐的状态
    var isControlsVisible by remember { mutableStateOf(false) }

    val availableModels = remember {
        try {
            context.assets.list("")?.filter { it.endsWith(".ncnn.param") }?.map { it.removeSuffix(".ncnn.param") } ?: listOf("yolov8n_pose")
        } catch (e: Exception) {
            listOf("yolov8n_pose")
        }
    }

    var selectedModel by remember(availableModels) {
        val savedModel = sharedPrefs.getString("yolo_model", "")
        if (!savedModel.isNullOrEmpty() && availableModels.contains(savedModel)) {
            mutableStateOf(savedModel)
        } else {
            mutableStateOf(availableModels.firstOrNull() ?: "yolov8n_pose")
        }
    }

    var showModelMenu by remember { mutableStateOf(false) }

    // 录入状态机变量
    var currentFaceIndex by remember { mutableIntStateOf(0) }
    val capturedFaces = remember { mutableStateListOf<List<Color>>() }
    var validStates by remember { mutableStateOf<List<List<List<Color>>>>(emptyList()) }
    var isChoosingState by remember { mutableStateOf(false) }

    var latestColors by remember { mutableStateOf<List<Color>?>(null) }
    var isCubeDetected by remember { mutableStateOf(false) }

    // 转向引导箭头的位移动画
    val infiniteTransition = rememberInfiniteTransition(label = "ArrowAnim")
    val arrowSlide by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrowSlide"
    )

    BackHandler { onBackToHome() }

    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    LaunchedEffect(sizeId, selectedModel) {
        sharedPrefs.edit().putString("yolo_model", selectedModel).apply()
        YoloNcnn.loadModel(context.assets, 0, sizeId, selectedModel)
    }

    LaunchedEffect(fpsCap) {
        val fps = fpsCap.toInt()
        sharedPrefs.edit().putInt("yolo_fps_cap", fps).apply()
        YoloNcnn.setFpsCap(fps)
    }

    LaunchedEffect(probThreshold) {
        sharedPrefs.edit().putFloat("yolo_prob_threshold", probThreshold).apply()
        YoloNcnn.setProbThreshold(probThreshold / 100f)
    }

    // 实时轮询 C++ 提色结果 (约 10 FPS)
    LaunchedEffect(Unit) {
        while (isActive) {
            isCubeDetected = YoloNcnn.isCubeDetected()
            val colorsArgb = YoloNcnn.getLatestColors()
            if (colorsArgb != null && colorsArgb.size == 9) {
                latestColors = colorsArgb.map { Color(it) }
            } else {
                latestColors = null
            }
            delay(100)
        }
    }

    DisposableEffect(lifecycleOwner, facing) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> YoloNcnn.openCamera(facing)
                Lifecycle.Event.ON_PAUSE -> YoloNcnn.closeCamera()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            YoloNcnn.openCamera(facing)
        }

        onDispose {
            YoloNcnn.closeCamera()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (isChoosingState) {
        StateSelectionScreen(
            modifier = modifier,
            validStates = validStates,
            onStateSelected = { selectedFaces ->
                isChoosingState = false
                onResultFound(selectedFaces)
            }
        )
    } else {
        Scaffold(
            topBar = {
                AnimatedVisibility(
                    visible = isControlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    TopAppBar(
                        title = { Text("AI 扫描 - ${currentFaceIndex + 1}/6", color = Color.White) },
                        navigationIcon = {
                            IconButton(onClick = onBackToHome) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                            }
                        },
                        actions = {
                            Box {
                                TextButton(onClick = { showModelMenu = true }) {
                                    Text(text = selectedModel, color = Color.Yellow, fontSize = 14.sp)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "切换", tint = Color.Yellow)
                                }
                                DropdownMenu(expanded = showModelMenu, onDismissRequest = { showModelMenu = false }) {
                                    availableModels.forEach { modelName ->
                                        DropdownMenuItem(
                                            text = { Text(modelName) },
                                            onClick = { selectedModel = modelName; showModelMenu = false }
                                        )
                                    }
                                }
                            }
                            TextButton(onClick = { sizeId = 1 - sizeId }) {
                                Text(text = if (sizeId == 0) "320P" else "640P", color = Color.Green, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.5f))
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(paddingValues)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null 
                    ) {
                        isControlsVisible = !isControlsVisible
                    }
            ) {
                // YOLO 摄像头画面预览
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            holder.setFormat(PixelFormat.RGBA_8888)
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {}
                                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                    YoloNcnn.setOutputWindow(holder.surface)
                                }
                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    YoloNcnn.setOutputWindow(null)
                                }
                            })
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // 引导箭头绘制层
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    // 参考 CameraScanner 的布局逻辑，假定一个虚拟的引导区域
                    val gridSize = minOf(canvasWidth, canvasHeight) * 0.7f
                    val left = (canvasWidth - gridSize) / 2
                    val top = (canvasHeight - gridSize) / 2

                    val showLeftArrow = currentFaceIndex == 1 || currentFaceIndex == 3 || currentFaceIndex == 5
                    val showUpArrow = currentFaceIndex == 2 || currentFaceIndex == 4

                    val arrowOffset = 40.dp.toPx()
                    val arrowLength = 36.dp.toPx()
                    val arrowHeadSize = 12.dp.toPx()
                    val arrowShaftWidth = 8.dp.toPx()
                    val slidePx = arrowSlide.dp.toPx()

                    if (showLeftArrow) {
                        val cyAbove = top - arrowOffset
                        val cyBelow = top + gridSize + arrowOffset
                        val cxCenter = left + gridSize / 2f
                        drawLeftArrow(cxCenter + slidePx, cyAbove, arrowLength, arrowHeadSize, arrowShaftWidth)
                        drawLeftArrow(cxCenter + slidePx, cyBelow, arrowLength, arrowHeadSize, arrowShaftWidth)
                    }

                    if (showUpArrow) {
                        val cxLeft = left - arrowOffset
                        val cxRight = left + gridSize + arrowOffset
                        val cyCenter = top + gridSize / 2f
                        drawUpArrow(cxLeft, cyCenter + slidePx, arrowLength, arrowHeadSize, arrowShaftWidth)
                        drawUpArrow(cxRight, cyCenter + slidePx, arrowLength, arrowHeadSize, arrowShaftWidth)
                    }
                }

                // 顶部翻转指示语
                Text(
                    text = SCAN_INSTRUCTIONS[currentFaceIndex],
                    color = Color.Yellow,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .padding(top = 8.dp)
                )

                // 底部控制区：包含预览小窗、快门和配置项
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AnimatedVisibility(
                        visible = isControlsVisible,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "上限: ${fpsCap.toInt()} FPS", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(85.dp))
                                    Slider(
                                        value = fpsCap, onValueChange = { fpsCap = it },
                                        valueRange = 5f..30f, steps = 24,
                                        colors = SliderDefaults.colors(thumbColor = Color.Green, activeTrackColor = Color.Green.copy(alpha = 0.7f)),
                                        modifier = Modifier.weight(1f).height(24.dp)
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "置信度: ${probThreshold.toInt()}%", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(85.dp))
                                    Slider(
                                        value = probThreshold, onValueChange = { probThreshold = it },
                                        valueRange = 50f..90f, steps = 39,
                                        colors = SliderDefaults.colors(thumbColor = Color.Cyan, activeTrackColor = Color.Cyan.copy(alpha = 0.7f)),
                                        modifier = Modifier.weight(1f).height(24.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 左下角：实时提色小窗 (3x3)
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Column(
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                    .padding(4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                for (r in 0..2) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        for (c in 0..2) {
                                            val idx = r * 3 + c
                                            val cellColor = if (isCubeDetected && latestColors != null) latestColors!![idx] else Color.DarkGray
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp) // 尺寸较原先缩减一倍，避免遮挡主要画面
                                                    .background(cellColor, RoundedCornerShape(1.dp))
                                                    .border(0.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(1.dp))
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (isCubeDetected && latestColors != null) {
                                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                                        vm.defaultVibrator
                                    } else {
                                        @Suppress("DEPRECATION")
                                        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                                    } else {
                                        @Suppress("DEPRECATION")
                                        vibrator.vibrate(50)
                                    }

                                    capturedFaces.add(latestColors!!.toList())

                                    if (currentFaceIndex < 5) {
                                        currentFaceIndex++
                                    } else {
                                        // 6 个面全部扫描完成，使用 Hungarian 算法解析颜色
                                        val resolvedFaces = ColorUtils.resolveColorsWithHungarian(capturedFaces)
                                        val dynamicCenterColors = resolvedFaces.map { it[4] }
                                        val states = CubeValidator.resolveAllValidStates(resolvedFaces, dynamicCenterColors)

                                        if (states.size > 1) {
                                            validStates = states
                                            isChoosingState = true
                                        } else {
                                            val finalFaces = if (states.isNotEmpty()) states[0] else resolvedFaces
                                            onResultFound(finalFaces)
                                        }
                                    }
                                }
                            },
                            enabled = isCubeDetected && latestColors != null,
                            shape = CircleShape,
                            modifier = Modifier.size(72.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Green,
                                disabledContainerColor = Color.Gray
                            )
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "记录当前面", modifier = Modifier.size(36.dp), tint = Color.Black)
                        }

                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                            this@Row.AnimatedVisibility(
                                visible = isControlsVisible,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                FloatingActionButton(
                                    onClick = { facing = 1 - facing },
                                    shape = CircleShape,
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                ) {
                                    Icon(Icons.Default.Cameraswitch, contentDescription = "切换摄像头", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 绘制向左的实体箭头辅助函数
private fun DrawScope.drawLeftArrow(cx: Float, cy: Float, length: Float, headSize: Float, shaftWidth: Float) {
    val path = Path().apply {
        moveTo(cx - length / 2f, cy)
        lineTo(cx - length / 2f + headSize, cy - headSize)
        lineTo(cx - length / 2f + headSize, cy - shaftWidth / 2f)
        lineTo(cx + length / 2f, cy - shaftWidth / 2f)
        lineTo(cx + length / 2f, cy + shaftWidth / 2f)
        lineTo(cx - length / 2f + headSize, cy + shaftWidth / 2f)
        lineTo(cx - length / 2f + headSize, cy + headSize)
        close()
    }
    drawPath(path, color = Color.Yellow)
    drawPath(path, color = Color.Black, style = Stroke(width = 2.dp.toPx()))
}

// 绘制向上的实体箭头辅助函数
private fun DrawScope.drawUpArrow(cx: Float, cy: Float, length: Float, headSize: Float, shaftWidth: Float) {
    val path = Path().apply {
        moveTo(cx, cy - length / 2f)
        lineTo(cx - headSize, cy - length / 2f + headSize)
        lineTo(cx - shaftWidth / 2f, cy - length / 2f + headSize)
        lineTo(cx - shaftWidth / 2f, cy + length / 2f)
        lineTo(cx + shaftWidth / 2f, cy + length / 2f)
        lineTo(cx + shaftWidth / 2f, cy - length / 2f + headSize)
        lineTo(cx + headSize, cy - length / 2f + headSize)
        close()
    }
    drawPath(path, color = Color.Yellow)
    drawPath(path, color = Color.Black, style = Stroke(width = 2.dp.toPx()))
}
