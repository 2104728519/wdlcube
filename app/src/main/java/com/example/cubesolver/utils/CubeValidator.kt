package com.example.cubesolver.utils

import android.util.Log
import androidx.compose.ui.graphics.Color

object CubeValidator {

    // 6 个面法线向量定义（U, R, F, D, L, B 依次对应 0 到 5），用于手性校验
    private val FACE_NORMALS = arrayOf(
        doubleArrayOf(0.0, 1.0, 0.0),   // U = 0
        doubleArrayOf(1.0, 0.0, 0.0),   // R = 1
        doubleArrayOf(0.0, 0.0, 1.0),   // F = 2
        doubleArrayOf(0.0, -1.0, 0.0),  // D = 3
        doubleArrayOf(-1.0, 0.0, 0.0),  // L = 4
        doubleArrayOf(0.0, 0.0, -1.0)   // B = 5
    )

    private fun getChirality(c1: Int, c2: Int, c3: Int): Double {
        val n1 = FACE_NORMALS[c1]
        val n2 = FACE_NORMALS[c2]
        val n3 = FACE_NORMALS[c3]

        return n1[0] * (n2[1] * n3[2] - n2[2] * n3[1]) -
                n1[1] * (n2[0] * n3[2] - n2[2] * n3[0]) +
                n1[2] * (n2[0] * n3[1] - n2[1] * n3[0])
    }

    private fun getRotationCost(times: Int): Int {
        return when (times % 4) {
            0 -> 0
            1, 3 -> 1
            2 -> 2
            else -> 0
        }
    }

    private fun rotateFaceRight(face: List<Int>): List<Int> {
        return listOf(
            face[6], face[3], face[0],
            face[7], face[4], face[1],
            face[8], face[5], face[2]
        )
    }

    private fun rotateFace(face: List<Int>, times: Int): List<Int> {
        var result = face
        for (i in 0 until (times % 4)) result = rotateFaceRight(result)
        return result
    }

    private fun isOpposite(c1: Int, c2: Int): Boolean {
        return (c1 == 0 && c2 == 3) || (c1 == 3 && c2 == 0) ||
                (c1 == 1 && c2 == 4) || (c1 == 4 && c2 == 1) ||
                (c1 == 2 && c2 == 5) || (c1 == 5 && c2 == 2)
    }

    private fun isValidState(state: IntArray): Boolean {
        val edgeIndices = arrayOf(
            intArrayOf(5, 10), intArrayOf(7, 19), intArrayOf(3, 37), intArrayOf(1, 46),
            intArrayOf(32, 16), intArrayOf(28, 25), intArrayOf(30, 43), intArrayOf(34, 52),
            intArrayOf(23, 12), intArrayOf(21, 41), intArrayOf(48, 14), intArrayOf(50, 39)
        )
        val uniqueEdges = mutableSetOf<Int>()
        for (edge in edgeIndices) {
            val c1 = state[edge[0]]
            val c2 = state[edge[1]]
            if (c1 == c2 || isOpposite(c1, c2)) return false
            uniqueEdges.add((1 shl c1) or (1 shl c2))
        }
        if (uniqueEdges.size != 12) return false

        val cornerIndices = arrayOf(
            intArrayOf(8, 9, 20), intArrayOf(6, 18, 38), intArrayOf(0, 36, 47), intArrayOf(2, 45, 11),
            intArrayOf(29, 26, 15), intArrayOf(27, 44, 24), intArrayOf(33, 53, 42), intArrayOf(35, 17, 51)
        )
        val uniqueCorners = mutableSetOf<Int>()
        for (corner in cornerIndices) {
            val c1 = state[corner[0]]
            val c2 = state[corner[1]]
            val c3 = state[corner[2]]

            if (c1 == c2 || c2 == c3 || c1 == c3) return false
            if (isOpposite(c1, c2) || isOpposite(c2, c3) || isOpposite(c1, c3)) return false

            val det = getChirality(c1, c2, c3)
            if (det > 0.0) return false

            uniqueCorners.add((1 shl c1) or (1 shl c2) or (1 shl c3))
        }
        if (uniqueCorners.size != 8) return false

        return true
    }

    private fun isSolvable(state: IntArray): Boolean {
        val cornerIndices = arrayOf(
            intArrayOf(8, 9, 20), intArrayOf(6, 18, 38), intArrayOf(0, 36, 47), intArrayOf(2, 45, 11),
            intArrayOf(29, 26, 15), intArrayOf(27, 44, 24), intArrayOf(33, 53, 42), intArrayOf(35, 17, 51)
        )
        val solvedCorners = arrayOf(
            setOf(0, 1, 2), setOf(0, 2, 4), setOf(0, 4, 5), setOf(0, 5, 1),
            setOf(3, 2, 1), setOf(3, 4, 2), setOf(3, 5, 4), setOf(3, 1, 5)
        )

        var cornerOrientationSum = 0
        val cornerPermutation = IntArray(8)

        for (i in 0 until 8) {
            val c1 = state[cornerIndices[i][0]]
            val c2 = state[cornerIndices[i][1]]
            val c3 = state[cornerIndices[i][2]]
            val p = solvedCorners.indexOf(setOf(c1, c2, c3))
            if (p == -1) return false
            cornerPermutation[i] = p

            if (c1 == 0 || c1 == 3) cornerOrientationSum += 0
            else if (c2 == 0 || c2 == 3) cornerOrientationSum += 1
            else if (c3 == 0 || c3 == 3) cornerOrientationSum += 2
        }
        if (cornerOrientationSum % 3 != 0) return false

        val edgeIndices = arrayOf(
            intArrayOf(5, 10), intArrayOf(7, 19), intArrayOf(3, 37), intArrayOf(1, 46),
            intArrayOf(32, 16), intArrayOf(28, 25), intArrayOf(30, 43), intArrayOf(34, 52),
            intArrayOf(23, 12), intArrayOf(21, 41), intArrayOf(48, 14), intArrayOf(50, 39)
        )
        val solvedEdges = arrayOf(
            setOf(0, 1), setOf(0, 2), setOf(0, 4), setOf(0, 5),
            setOf(3, 1), setOf(3, 2), setOf(3, 4), setOf(3, 5),
            setOf(2, 1), setOf(2, 4), setOf(5, 1), setOf(5, 4)
        )
        val primaryColors = intArrayOf(0, 0, 0, 0, 3, 3, 3, 3, 2, 2, 5, 5)

        var edgeOrientationSum = 0
        val edgePermutation = IntArray(12)

        for (i in 0 until 12) {
            val c1 = state[edgeIndices[i][0]]
            val c2 = state[edgeIndices[i][1]]
            val p = solvedEdges.indexOf(setOf(c1, c2))
            if (p == -1) return false
            edgePermutation[i] = p

            val expectedPrimary = primaryColors[p]
            if (c1 == expectedPrimary) edgeOrientationSum += 0
            else if (c2 == expectedPrimary) edgeOrientationSum += 1
            else return false
        }
        if (edgeOrientationSum % 2 != 0) return false

        var cornerInversions = 0
        for (i in 0 until 7) {
            for (j in i + 1 until 8) {
                if (cornerPermutation[i] > cornerPermutation[j]) cornerInversions++
            }
        }
        var edgeInversions = 0
        for (i in 0 until 11) {
            for (j in i + 1 until 12) {
                if (edgePermutation[i] > edgePermutation[j]) edgeInversions++
            }
        }
        if (cornerInversions % 2 != edgeInversions % 2) return false

        return true
    }

    /**
     * 找出所有【视觉上不同、且物理可解】的合法重构状态
     */
    fun resolveAllValidStates(faces: List<List<Color>>, standardColors: List<Color>): List<List<List<Color>>> {
        val intFaces = faces.map { face ->
            face.map { color -> findClosestColorIndex(color, standardColors) }
        }

        val validStatesMap = mutableMapOf<List<List<Color>>, Int>()

        // 移除手动添加初始状态的逻辑，避免 SnapshotStateList 与标准 List 在 Map 中因类型不一致产生重复项。
        // 嵌套循环已涵盖所有旋转角度（包括 0 度），由循环统一处理更具一致性。
        for (u in 0..3) {
            val rU = rotateFace(intFaces[0], u)
            val costU = getRotationCost(u)
            for (r in 0..3) {
                val rR = rotateFace(intFaces[1], r)
                val costR = costU + getRotationCost(r)
                for (f in 0..3) {
                    val rF = rotateFace(intFaces[2], f)
                    val costF = costR + getRotationCost(f)
                    for (d in 0..3) {
                        val rD = rotateFace(intFaces[3], d)
                        val costD = costF + getRotationCost(d)
                        for (l in 0..3) {
                            val rL = rotateFace(intFaces[4], l)
                            val costL = costD + getRotationCost(l)
                            for (b in 0..3) {
                                val rB = rotateFace(intFaces[5], b)
                                val costB = costL + getRotationCost(b)

                                val stateArray = (rU + rR + rF + rD + rL + rB).toIntArray()

                                if (isValidState(stateArray) && isSolvable(stateArray)) {
                                    val stateFaces = listOf(rU, rR, rF, rD, rL, rB).map { face ->
                                        face.map { c -> standardColors[c] }
                                    }

                                    val currentMinCost = validStatesMap[stateFaces] ?: Int.MAX_VALUE
                                    if (costB < currentMinCost) {
                                        validStatesMap[stateFaces] = costB
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val resultList = validStatesMap.entries.sortedBy { it.value }.map { it.key }
        Log.i("CubeValidator", "重构分析完成：发现 ${resultList.size} 种空间手性合法的状态")
        return resultList
    }

    fun resolveTrueState(faces: List<List<Color>>, standardColors: List<Color>): List<List<Color>>? {
        return resolveAllValidStates(faces, standardColors).firstOrNull()
    }

    private fun findClosestColorIndex(color: Color, palette: List<Color>): Int {
        var minDistance = Float.MAX_VALUE
        var bestIndex = 0
        val r1 = color.red
        val g1 = color.green
        val b1 = color.blue

        for (i in palette.indices) {
            val c2 = palette[i]
            val dr = r1 - c2.red
            val dg = g1 - c2.green
            val db = b1 - c2.blue
            val dist = dr * dr + dg * dg + db * db
            if (dist < minDistance) {
                minDistance = dist
                bestIndex = i
            }
        }
        return bestIndex
    }
}