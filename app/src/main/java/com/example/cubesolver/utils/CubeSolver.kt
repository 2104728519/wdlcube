package com.example.cubesolver.utils

import android.content.Context
import android.os.Process
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 搜索状态密封类，用于 UI 层观察搜索进度
 */
sealed class SearchStatus {
    data class Searching(val nodesSearched: Long, val currentDepth: Int) : SearchStatus()
    data class NewSolution(val solution: String, val steps: Int) : SearchStatus()
    data class Finished(val solution: String) : SearchStatus()
    data class Error(val message: String) : SearchStatus()
}

/**
 * 回调接口，供 Native 层同步进度与中断信号
 */
interface SearchCallback {
    fun onProgress(nodesSearched: Long, currentDepth: Int)
    fun onNewSolutionFound(solution: String, steps: Int)
    fun isCancelled(): Boolean
}

object CubeSolver {

    init {
        System.loadLibrary("min2phase_native")
    }

    private external fun initNative(cachePath: String): Boolean

    private external fun solveNative(
        facelets: String,
        maxDepth: Int,
        probeMax: Long,
        probeMin: Long,
        verbose: Int,
        callback: SearchCallback?
    ): String

    private val faceLabels = listOf('U', 'R', 'F', 'D', 'L', 'B')

    suspend fun asyncInit(context: Context) = withContext(Dispatchers.IO) {
        val cacheFile = File(context.cacheDir, "min2phase_cache.bin")
        initNative(cacheFile.absolutePath)
    }

    suspend fun solve(
        faces: List<List<Color>>,
        maxDepth: Int = 21,
        useOptimal: Boolean = false
    ): String = withContext(Dispatchers.Default) {
        val dynamicPalette = faces.map { it[4] }
        val sb = StringBuilder(54)

        for (faceIndex in 0..5) {
            val face = faces[faceIndex]
            for (color in face) {
                val closestIndex = findClosestColorIndex(color, dynamicPalette)
                sb.append(faceLabels[closestIndex])
            }
        }

        val cubeString = sb.toString()
        val probeMax = 10000000L
        val probeMin = 0L

        val mask = if (useOptimal) {
            0x4 or 0x8 // APPEND_LENGTH | OPTIMAL_SOLUTION
        } else {
            0x4
        }

        solveNative(cubeString, maxDepth, probeMax, probeMin, mask, null)
    }

    /**
     * 最优解协程流，支持进度回调和即时中断
     */
    fun solveOptimalFlow(faces: List<List<Color>>, initialMaxDepth: Int = 20): Flow<SearchStatus> = callbackFlow {
        val dynamicPalette = faces.map { it[4] }
        val sb = StringBuilder(54)
        for (faceIndex in 0..5) {
            for (color in faces[faceIndex]) {
                sb.append(faceLabels[findClosestColorIndex(color, dynamicPalette)])
            }
        }
        val cubeString = sb.toString()

        val callback = object : SearchCallback {
            override fun onNewSolutionFound(solution: String, steps: Int) {
                trySend(SearchStatus.NewSolution(solution, steps))
            }

            override fun onProgress(nodesSearched: Long, currentDepth: Int) {
                trySend(SearchStatus.Searching(nodesSearched, currentDepth))
            }

            override fun isCancelled(): Boolean {
                return !isActive
            }
        }

        val searchThread = Thread {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND)

                val probeMax = 1000000000L
                val probeMin = 0L
                val mask = 0x4 or 0x8 // APPEND_LENGTH | OPTIMAL_SOLUTION

                val result = solveNative(cubeString, initialMaxDepth, probeMax, probeMin, mask, callback)

                if (isActive) {
                    if (result.contains("Error")) {
                        trySend(SearchStatus.Error(result))
                    } else {
                        trySend(SearchStatus.Finished(result))
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    trySend(SearchStatus.Error("Error: ${e.message}"))
                }
            } finally {
                close()
            }
        }

        searchThread.priority = Thread.MAX_PRIORITY
        searchThread.start()

        awaitClose {
        }
    }

    /**
     * 多核心持续深度搜索。
     * mask 传入 0x4 (APPEND_LENGTH) 与 0x10 (CONTINUOUS_SQUEEZE)，解锁 150ms 窗口，利用多核心进入持续下探状态。
     */
    fun solveSqueezeFlow(faces: List<List<Color>>, initialMaxDepth: Int = 20): Flow<SearchStatus> = callbackFlow {
        val dynamicPalette = faces.map { it[4] }
        val sb = StringBuilder(54)
        for (faceIndex in 0..5) {
            for (color in faces[faceIndex]) {
                sb.append(faceLabels[findClosestColorIndex(color, dynamicPalette)])
            }
        }
        val cubeString = sb.toString()

        val callback = object : SearchCallback {
            override fun onNewSolutionFound(solution: String, steps: Int) {
                trySend(SearchStatus.NewSolution(solution, steps))
            }

            override fun onProgress(nodesSearched: Long, currentDepth: Int) {
                trySend(SearchStatus.Searching(nodesSearched, currentDepth))
            }

            override fun isCancelled(): Boolean {
                return !isActive // 协程取消时，通知 Native 层停止多核心搜索
            }
        }

        val searchThread = Thread {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND)

                val probeMax = 1000000000L
                val probeMin = 0L
                val mask = 0x4 or 0x10 // APPEND_LENGTH | CONTINUOUS_SQUEEZE

                val result = solveNative(cubeString, initialMaxDepth, probeMax, probeMin, mask, callback)

                if (isActive) {
                    if (result.contains("Error")) {
                        trySend(SearchStatus.Error(result))
                    } else {
                        trySend(SearchStatus.Finished(result))
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    trySend(SearchStatus.Error("Error: ${e.message}"))
                }
            } finally {
                close()
            }
        }

        searchThread.priority = Thread.MAX_PRIORITY
        searchThread.start()

        awaitClose {
        }
    }

    private fun findClosestColorIndex(color: Color, palette: List<Color>): Int {
        var minDistance = Float.MAX_VALUE
        var bestIndex = 0
        for (i in palette.indices) {
            val c2 = palette[i]
            val dr = color.red - c2.red
            val dg = color.green - c2.green
            val db = color.blue - c2.blue
            val dist = dr * dr + dg * dg + db * db
            if (dist < minDistance) {
                minDistance = dist
                bestIndex = i
            }
        }
        return bestIndex
    }
}
