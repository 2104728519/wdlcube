package com.example.cubesolver.utils

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.ColorUtils as AndroidColorUtils

object ColorUtils {

    // 仅用作兜底参考
    val STANDARD_CUBE_COLORS = listOf(
        Color(0xFFFFFFFF), Color(0xFFD32F2F), Color(0xFF388E3C),
        Color(0xFFFBC02D), Color(0xFFF57C00), Color(0xFF1976D2)
    )

    /**
     * 从打乱的 48 个周边色块中，通过 K-Means 聚类提取出最纯正的 6 种动态平均色。
     * 完美无视中心块上的所有 Logo、污渍或反光。
     */
    fun extractDynamicPalette(faces: List<List<Color>>): List<Color> {
        val outerColors = faces.flatMap { face -> face.filterIndexed { index, _ -> index != 4 } }
        val labColors = outerColors.map { colorToLab(it) }

        // K-Means++ 初始化，寻找空间中相距最远的 6 个点作为种子
        val centroids = mutableListOf<DoubleArray>()
        if (labColors.isEmpty()) return STANDARD_CUBE_COLORS
        
        centroids.add(labColors[0])
        while (centroids.size < 6) {
            var maxDist = -1.0
            var bestPt = labColors[0]
            for (pt in labColors) {
                val dist = centroids.minOf { colorDistance(pt, it) }
                if (dist > maxDist) { maxDist = dist; bestPt = pt }
            }
            centroids.add(bestPt)
        }

        // K-Means 迭代，将周边色块精准分类到 6 个颜色家族
        val assignments = IntArray(48)
        repeat(10) {
            for (i in 0 until 48) {
                var minDist = Double.MAX_VALUE
                var bestC = 0
                for (c in 0 until 6) {
                    val d = colorDistance(labColors[i], centroids[c])
                    if (d < minDist) { minDist = d; bestC = c }
                }
                assignments[i] = bestC
            }
            for (c in 0 until 6) {
                var sl = 0.0; var sa = 0.0; var sb = 0.0
                var count = 0
                for (i in 0 until 48) {
                    if (assignments[i] == c) {
                        sl += labColors[i][0]; sa += labColors[i][1]; sb += labColors[i][2]
                        count++
                    }
                }
                if (count > 0) centroids[c] = doubleArrayOf(sl / count, sa / count, sb / count)
            }
        }
        
        return centroids.map { Color(AndroidColorUtils.LABToColor(it[0], it[1], it[2])) }
    }

    /**
     * 核心改进算法：K-Means 无视 Logo 提取 + 匈牙利算法完美色彩对齐
     */
    fun resolveColorsWithHungarian(capturedFaces: List<List<Color>>): List<List<Color>> {
        val purePalette = extractDynamicPalette(capturedFaces)

        // 处理带有 Logo 干扰的物理中心块，将其映射到提取出的纯净底色中
        val centers = capturedFaces.map { it[4] }
        val centerCost = Array(6) { i ->
            DoubleArray(6) { j -> colorDistance(colorToLab(centers[i]), colorToLab(purePalette[j])) }
        }
        val centerMatch = HungarianAlgorithm.solve(centerCost)
        
        // 组装面序色板（这 6 个颜色代表了魔方 6 个面真实的底色）
        val faceToPalette = Array(6) { purePalette[0] }
        for (i in 0 until 6) faceToPalette[i] = purePalette[centerMatch[i]]

        // 使用匈牙利算法将周边块均匀分配到 6 种底色上，确保每种颜色分配 8 块
        val allOuterColors = capturedFaces.flatMap { face -> face.filterIndexed { index, _ -> index != 4 } }
        val costMatrix = Array(48) { i ->
            DoubleArray(48) { j ->
                val targetColor = faceToPalette[j / 8]
                colorDistance(colorToLab(allOuterColors[i]), colorToLab(targetColor))
            }
        }
        val matching = HungarianAlgorithm.solve(costMatrix)

        var colorIndex = 0
        return capturedFaces.mapIndexed { faceIndex, _ ->
            val faceColors = mutableListOf<Color>()
            for (i in 0 until 9) {
                if (i == 4) faceColors.add(faceToPalette[faceIndex]) // 中心块被强制纠正为纯净底色
                else faceColors.add(faceToPalette[matching[colorIndex++] / 8])
            }
            faceColors
        }
    }

    private fun colorToLab(color: Color): DoubleArray {
        val lab = DoubleArray(3)
        AndroidColorUtils.colorToLAB(color.toArgb(), lab)
        return lab
    }

    private fun colorDistance(lab1: DoubleArray, lab2: DoubleArray): Double {
        val dl = lab1[0] - lab2[0]
        val da = lab1[1] - lab2[1]
        val db = lab1[2] - lab2[2]
        return dl * dl + da * da + db * db
    }

    fun extractColorsFromBitmap(bitmap: Bitmap, viewSize: IntSize): List<Color> {
        val bitmapWidth = bitmap.width.toFloat()
        val bitmapHeight = bitmap.height.toFloat()
        val viewWidth = viewSize.width.toFloat()
        val viewHeight = viewSize.height.toFloat()

        val gridSize = minOf(viewWidth, viewHeight) * 0.75f
        val left = (viewWidth - gridSize) / 2
        val top = (viewHeight - gridSize) / 2
        val cellSize = gridSize / 3f

        val scale = maxOf(viewWidth / bitmapWidth, viewHeight / bitmapHeight)
        val scaledWidth = bitmapWidth * scale
        val scaledHeight = bitmapHeight * scale
        val cropLeft = (scaledWidth - viewWidth) / 2f
        val cropTop = (scaledHeight - viewHeight) / 2f

        val result = mutableListOf<Color>()
        for (row in 0..2) {
            for (col in 0..2) {
                val viewCenterX = left + cellSize * (col + 0.5f)
                val viewCenterY = top + cellSize * (row + 0.5f)

                val scaledX = viewCenterX + cropLeft
                val scaledY = viewCenterY + cropTop

                val relX = (scaledX / scaledWidth).coerceIn(0f, 1f)
                val relY = (scaledY / scaledHeight).coerceIn(0f, 1f)

                val bmpX = (relX * bitmapWidth).toInt().coerceIn(0, bitmapWidth.toInt() - 1)
                val bmpY = (relY * bitmapHeight).toInt().coerceIn(0, bitmapHeight.toInt() - 1)

                val pixel = bitmap.getPixel(bmpX, bmpY)
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)

                result.add(Color(r, g, b))
            }
        }
        return result
    }
}

/**
 * 经典二分图最小权完美匹配解法 (Kuhn-Munkres / 匈牙利算法)
 * 时间复杂度 O(N^3)，处理 N=48 耗时小于 1 毫秒
 */
object HungarianAlgorithm {
    fun solve(costMatrix: Array<DoubleArray>): IntArray {
        val n = costMatrix.size
        val u = DoubleArray(n + 1)
        val v = DoubleArray(n + 1)
        val p = IntArray(n + 1)
        val way = IntArray(n + 1)

        for (i in 1..n) {
            p[0] = i
            var j0 = 0
            val minv = DoubleArray(n + 1) { Double.MAX_VALUE }
            val used = BooleanArray(n + 1)
            do {
                used[j0] = true
                val i0 = p[j0]
                var delta = Double.MAX_VALUE
                var j1 = 0
                for (j in 1..n) {
                    if (!used[j]) {
                        val cur = costMatrix[i0 - 1][j - 1] - u[i0] - v[j]
                        if (cur < minv[j]) {
                            minv[j] = cur
                            way[j] = j0
                        }
                        if (minv[j] < delta) {
                            delta = minv[j]
                            j1 = j
                        }
                    }
                }
                for (j in 0..n) {
                    if (used[j]) {
                        u[p[j]] += delta
                        v[j] -= delta
                    } else {
                        minv[j] -= delta
                    }
                }
                j0 = j1
            } while (p[j0] != 0)

            do {
                val j1 = way[j0]
                p[j0] = p[j1]
                j0 = j1
            } while (j0 != 0)
        }

        val result = IntArray(n)
        for (j in 1..n) {
            if (p[j] > 0) {
                result[p[j] - 1] = j - 1
            }
        }
        return result
    }
}
