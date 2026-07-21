package com.example.cubesolver.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.example.cubesolver.utils.ColorUtils

class CubeAnalyzer(
    private val getLayoutSize: () -> IntSize,
    private val onColorsExtracted: (List<Color>) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastAnalyzeTime = 0L

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        val layoutSize = getLayoutSize()

        // 限制为 5 FPS 刷新率，减少计算开销
        if (currentTime - lastAnalyzeTime >= 200 && layoutSize.width > 0) {
            lastAnalyzeTime = currentTime

            try {
                val originalBitmap = imageProxy.toBitmap()
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees.toFloat()

                val isLandscape = originalBitmap.width > originalBitmap.height
                val needsRotation = rotationDegrees == 90f || rotationDegrees == 270f
                val actualRotation = if (needsRotation && !isLandscape) 0f else rotationDegrees

                val matrix = Matrix()
                matrix.postRotate(actualRotation)

                val maxEdge = maxOf(originalBitmap.width, originalBitmap.height)
                val scale = 240f / maxEdge
                matrix.postScale(scale, scale)

                val processedBitmap = Bitmap.createBitmap(
                    originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
                )

                val tempColors = ColorUtils.extractColorsFromBitmap(processedBitmap, layoutSize)
                onColorsExtracted(tempColors)

                if (processedBitmap != originalBitmap) {
                    processedBitmap.recycle()
                }
                originalBitmap.recycle()

            } catch (e: Exception) {
                Log.e("CubeAnalyzer", "图像分析失败", e)
            }
        }
        imageProxy.close()
    }
}
