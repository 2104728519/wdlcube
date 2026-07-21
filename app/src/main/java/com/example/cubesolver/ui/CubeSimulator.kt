package com.example.cubesolver.ui

import androidx.compose.ui.graphics.Color

// 公用 3D 贴纸坐标定位函数，供模拟器与动画渲染器复用
fun getStickerCoord(faceIndex: Int, stickerIndex: Int): Vector3 {
    val r = stickerIndex / 3
    val c = stickerIndex % 3
    val u = (c - 1).toFloat()
    val v = (r - 1).toFloat()
    val d = 1.5f

    return when (faceIndex) {
        0 -> Vector3(u, d, v)     // U (白)
        1 -> Vector3(d, -v, -u)   // R (红)
        2 -> Vector3(u, -v, d)    // F (绿)
        3 -> Vector3(u, -d, -v)   // D (黄)
        4 -> Vector3(-d, -v, u)   // L (橙)
        5 -> Vector3(-u, -v, -d)  // B (蓝)
        else -> Vector3(0f, 0f, 0f)
    }
}

fun isStickerInLayer(faceIndex: Int, stickerIndex: Int, moveChar: Char): Boolean {
    val coord = getStickerCoord(faceIndex, stickerIndex)
    return when (moveChar) {
        'U' -> coord.y > 0.5f
        'D' -> coord.y < -0.5f
        'R' -> coord.x > 0.5f
        'L' -> coord.x < -0.5f
        'F' -> coord.z > 0.5f
        'B' -> coord.z < -0.5f
        else -> false
    }
}

// 3D 物理旋转仿真器
object CubeSimulator {
    private val originalCoords = Array(54) { index ->
        getStickerCoord(index / 9, index % 9)
    }

    fun applyMove(faces: List<List<Color>>, move: String): List<List<Color>> {
        if (move.isEmpty()) return faces
        val baseChar = move[0]
        val isCounter = move.contains("'")
        val isDouble = move.contains("2")

        var repeat = 1
        if (isDouble) repeat = 2
        if (isCounter) repeat = 3

        var currentState = faces
        repeat(repeat) {
            currentState = applySingleClockwiseMove(currentState, baseChar)
        }
        return currentState
    }

    private fun applySingleClockwiseMove(faces: List<List<Color>>, faceChar: Char): List<List<Color>> {
        val flatColors = faces.flatten().toTypedArray()
        val nextColors = flatColors.clone()

        for (i in 0 until 54) {
            val coord = originalCoords[i]
            val isAffected = when (faceChar) {
                'U' -> coord.y > 0.5f
                'D' -> coord.y < -0.5f
                'R' -> coord.x > 0.5f
                'L' -> coord.x < -0.5f
                'F' -> coord.z > 0.5f
                'B' -> coord.z < -0.5f
                else -> false
            }

            if (isAffected) {
                val rotated = when (faceChar) {
                    'U' -> Vector3(-coord.z, coord.y, coord.x)
                    'D' -> Vector3(coord.z, coord.y, -coord.x)
                    'R' -> Vector3(coord.x, coord.z, -coord.y)
                    'L' -> Vector3(coord.x, -coord.z, coord.y)
                    'F' -> Vector3(coord.y, -coord.x, coord.z)
                    'B' -> Vector3(-coord.y, coord.x, coord.z)
                    else -> coord
                }

                val targetIndex = findClosestStickerIndex(rotated)
                if (targetIndex != -1) {
                    nextColors[targetIndex] = flatColors[i]
                }
            }
        }

        return List(6) { f ->
            List(9) { s -> nextColors[f * 9 + s] }
        }
    }

    private fun findClosestStickerIndex(target: Vector3): Int {
        var minDistance = Float.MAX_VALUE
        var bestIndex = -1
        for (i in 0 until 54) {
            val orig = originalCoords[i]
            val dx = target.x - orig.x
            val dy = target.y - orig.y
            val dz = target.z - orig.z
            val dist = dx * dx + dy * dy + dz * dz
            if (dist < minDistance) {
                minDistance = dist
                bestIndex = i
            }
        }
        return if (minDistance < 0.1f) bestIndex else -1
    }
}
