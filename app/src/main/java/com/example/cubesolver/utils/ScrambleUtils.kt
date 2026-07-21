package com.example.cubesolver.utils

import androidx.compose.ui.graphics.Color
import com.example.cubesolver.ui.CubeSimulator

object ScrambleUtils {

    // 定义标准魔方配色（U-白, R-红, F-绿, D-黄, L-橙, B-蓝）
    // 顺序必须与 CubeSolver.faceLabels 保持一致，以确保解法逻辑与 UI 颜色映射正确
    private val STANDARD_CUBE_FACES: List<List<Color>> = listOf(
        List(9) { Color(0xFFFFFFFF) },
        List(9) { Color(0xFFD32F2F) },
        List(9) { Color(0xFF388E3C) },
        List(9) { Color(0xFFFBC02D) },
        List(9) { Color(0xFFF57C00) },
        List(9) { Color(0xFF1976D2) }
    )

    private val faces = arrayOf("U", "D", "L", "R", "F", "B")
    private val modifiers = arrayOf("", "'", "2")

    /**
     * 生成随机打乱公式
     */
    fun generateScrambleFormula(length: Int = 20): String {
        val scramble = mutableListOf<String>()
        var lastFace = -1

        repeat(length) {
            var faceIndex: Int
            // 避免生成无效步骤（如连续转动同一个面）
            do {
                faceIndex = (0..5).random()
            } while (faceIndex == lastFace)

            val move = faces[faceIndex] + modifiers[(0..2).random()]
            scramble.add(move)
            lastFace = faceIndex
        }
        return scramble.joinToString(" ")
    }

    /**
     * 将打乱公式应用到标准魔方，返回最终的颜色布局
     */
    fun getScrambledState(formula: String): List<List<Color>> {
        var currentState = STANDARD_CUBE_FACES
        val moves = formula.split(" ").filter { it.isNotEmpty() }

        for (move in moves) {
            currentState = CubeSimulator.applyMove(currentState, move)
        }
        return currentState
    }
}