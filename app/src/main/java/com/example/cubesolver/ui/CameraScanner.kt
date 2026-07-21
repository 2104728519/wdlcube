package com.example.cubesolver.ui

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.cubesolver.camera.CubeAnalyzer
import kotlinx.coroutines.awaitCancellation
import java.util.concurrent.Executors

val SCAN_INSTRUCTIONS = listOf(
    "第 1 步 (顶面 U) - 请对准并录入您选定的【顶面】",
    "第 2 步 (左转) - 保持当前姿势，魔方整体【向左转 90°】 (录入原右侧面 R)",
    "第 3 步 (上翻) - 保持当前姿势，魔方整体【向上翻 90°】 (录入原下侧面 F)",
    "第 4 步 (左转) - 保持当前姿势，魔方整体【向左转 90°】 (录入原右侧面 D)",
    "第 5 步 (上翻) - 保持当前姿势，魔方整体【向上翻 90°】 (录入原下侧面 L)",
    "第 6 步 (左转) - 保持当前姿势，魔方整体【向左转 90°】 (录入原右侧面 B)"
)

@Composable
fun CameraScanner(
    modifier: Modifier = Modifier,
    currentFaceIndex: Int,
    onBack: () -> Unit,
    onFaceCaptured: (List<Color>) -> Unit
) {
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var previewLayoutSize by remember { mutableStateOf(IntSize.Zero) }
    val detectedColors = remember { mutableStateListOf<Color>().apply { repeat(9) { add(Color.Transparent) } } }

    var isFlashOn by remember { mutableStateOf(false) }
    var cameraInstance by remember { mutableStateOf<Camera?>(null) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

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

    LaunchedEffect(isFlashOn) {
        cameraInstance?.cameraControl?.enableTorch(isFlashOn)
    }

    LaunchedEffect(previewViewRef) {
        val previewView = previewViewRef ?: return@LaunchedEffect
        val cameraProvider = cameraProviderFuture.get()
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val mainHandler = Handler(Looper.getMainLooper())

        val previewUseCase = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalysis.setAnalyzer(
            analysisExecutor,
            CubeAnalyzer(
                getLayoutSize = { previewLayoutSize },
                onColorsExtracted = { tempColors ->
                    mainHandler.post {
                        if (tempColors.size == 9) {
                            for (i in 0 until 9) detectedColors[i] = tempColors[i]
                        }
                    }
                }
            )
        )

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        try {
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, previewUseCase, imageAnalysis)
            cameraInstance = camera
            awaitCancellation()
        } catch (exc: Exception) {
            Log.e("CubeSolver", "CameraX binding failed", exc)
        } finally {
            cameraProvider.unbindAll()
            cameraInstance?.cameraControl?.enableTorch(false)
            analysisExecutor.shutdown()
        }
    }

    Column(
        modifier = modifier.fillMaxSize().background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 8.dp, end = 16.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White
                )
            }

            Text(
                text = "录入进度: ${currentFaceIndex + 1} / 6",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = SCAN_INSTRUCTIONS[currentFaceIndex],
            color = Color.Yellow,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { coordinates -> previewLayoutSize = coordinates.size }
        ) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        previewViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .clickable { isFlashOn = !isFlashOn }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isFlashOn) "🔦 补光灯: 开" else "🔦 补光灯: 关",
                    color = if (isFlashOn) Color.Yellow else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val gridSize = minOf(canvasWidth, canvasHeight) * 0.75f
                val left = (canvasWidth - gridSize) / 2
                val top = (canvasHeight - gridSize) / 2

                drawRect(color = Color.White, topLeft = Offset(left, top), size = Size(gridSize, gridSize), style = Stroke(width = 3.dp.toPx()))
                val cellSize = gridSize / 3f

                for (i in 1..2) {
                    drawLine(Color.LightGray, Offset(left + i * cellSize, top), Offset(left + i * cellSize, top + gridSize), 1.5.dp.toPx())
                    drawLine(Color.LightGray, Offset(left, top + i * cellSize), Offset(left + gridSize, top + i * cellSize), 1.5.dp.toPx())
                }

                for (row in 0..2) {
                    for (col in 0..2) {
                        val centerX = left + cellSize * (col + 0.5f)
                        val centerY = top + cellSize * (row + 0.5f)
                        val index = row * 3 + col

                        val isCenter = index == 4
                        val currentColor = detectedColors.getOrElse(index) { Color.Transparent }
                        val circleRadius = if (isCenter) 8.dp.toPx() else 14.dp.toPx()

                        if (!isCenter) {
                            drawCircle(Color.Black, circleRadius + 1.5.dp.toPx(), Offset(centerX, centerY), style = Stroke(1.dp.toPx()))
                            drawCircle(Color.White, circleRadius, Offset(centerX, centerY), style = Stroke(2.dp.toPx()))
                        }
                        drawCircle(currentColor, circleRadius - 1.dp.toPx(), Offset(centerX, centerY))
                    }
                }

                val showLeftArrow = currentFaceIndex == 1 || currentFaceIndex == 3 || currentFaceIndex == 5
                val showUpArrow = currentFaceIndex == 2 || currentFaceIndex == 4

                val arrowOffset = 36.dp.toPx()
                val arrowLength = 32.dp.toPx()
                val arrowHeadSize = 10.dp.toPx()
                val arrowShaftWidth = 6.dp.toPx()
                val slidePx = arrowSlide.dp.toPx()

                if (showLeftArrow) {
                    // 在九宫格上下方绘制向左的箭头以提示旋转动作
                    val cyAbove = top - arrowOffset
                    val cyBelow = top + gridSize + arrowOffset
                    val cxCenter = left + gridSize / 2f

                    drawLeftArrow(
                        cx = cxCenter + slidePx,
                        cy = cyAbove,
                        length = arrowLength,
                        headSize = arrowHeadSize,
                        shaftWidth = arrowShaftWidth
                    )
                    drawLeftArrow(
                        cx = cxCenter + slidePx,
                        cy = cyBelow,
                        length = arrowLength,
                        headSize = arrowHeadSize,
                        shaftWidth = arrowShaftWidth
                    )
                }

                if (showUpArrow) {
                    // 在九宫格左右侧绘制向上的箭头以提示翻转动作
                    val cxLeft = left - arrowOffset
                    val cxRight = left + gridSize + arrowOffset
                    val cyCenter = top + gridSize / 2f

                    drawUpArrow(
                        cx = cxLeft,
                        cy = cyCenter + slidePx,
                        length = arrowLength,
                        headSize = arrowHeadSize,
                        shaftWidth = arrowShaftWidth
                    )
                    drawUpArrow(
                        cx = cxRight,
                        cy = cyCenter + slidePx,
                        length = arrowLength,
                        headSize = arrowHeadSize,
                        shaftWidth = arrowShaftWidth
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Button(
                onClick = {
                    if (detectedColors.size == 9) {
                        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                            vibratorManager?.defaultVibrator
                        } else {
                            @Suppress("DEPRECATION")
                            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator?.vibrate(50)
                        }

                        onFaceCaptured(detectedColors.toList())
                    }
                },
                modifier = Modifier.fillMaxWidth(0.8f).height(56.dp)
            ) {
                Text(text = "记录此面", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// 使用黄色填充与黑色边缘，提升在亮/暗背景下的视觉对比度
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
