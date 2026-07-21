package com.example.cubesolver.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput

data class Vector3(val x: Float, val y: Float, val z: Float)
data class Quad(val vertices: List<Vector3>, val color: Color, val faceIndex: Int, val stickerIndex: Int)
data class ProjectedQuad(val points: List<Offset>, val color: Color, val z: Float, val faceIndex: Int, val stickerIndex: Int)

class Matrix3x3(
    val m: FloatArray = floatArrayOf(
        1f, 0f, 0f,
        0f, 1f, 0f,
        0f, 0f, 1f
    )
) {
    operator fun times(other: Matrix3x3): Matrix3x3 {
        val r = FloatArray(9)
        r[0] = m[0] * other.m[0] + m[1] * other.m[3] + m[2] * other.m[6]
        r[1] = m[0] * other.m[1] + m[1] * other.m[4] + m[2] * other.m[7]
        r[2] = m[0] * other.m[2] + m[1] * other.m[5] + m[2] * other.m[8]

        r[3] = m[3] * other.m[0] + m[4] * other.m[3] + m[5] * other.m[6]
        r[4] = m[3] * other.m[1] + m[4] * other.m[4] + m[5] * other.m[7]
        r[5] = m[3] * other.m[2] + m[4] * other.m[5] + m[5] * other.m[8]

        r[6] = m[6] * other.m[0] + m[7] * other.m[3] + m[8] * other.m[6]
        r[7] = m[6] * other.m[1] + m[7] * other.m[4] + m[8] * other.m[7]
        r[8] = m[6] * other.m[2] + m[7] * other.m[5] + m[8] * other.m[8]
        return Matrix3x3(r)
    }

    fun transform(v: Vector3): Vector3 {
        return Vector3(
            m[0] * v.x + m[1] * v.y + m[2] * v.z,
            m[3] * v.x + m[4] * v.y + m[5] * v.z,
            m[6] * v.x + m[7] * v.y + m[8] * v.z
        )
    }

    companion object {
        fun rotationX(angleRad: Float): Matrix3x3 {
            val c = kotlin.math.cos(angleRad)
            val s = kotlin.math.sin(angleRad)
            return Matrix3x3(
                floatArrayOf(
                    1f, 0f, 0f,
                    0f, c, -s,
                    0f, s, c
                )
            )
        }

        fun rotationY(angleRad: Float): Matrix3x3 {
            val c = kotlin.math.cos(angleRad)
            val s = kotlin.math.sin(angleRad)
            return Matrix3x3(
                floatArrayOf(
                    c, 0f, s,
                    0f, 1f, 0f,
                    -s, 0f, c
                )
            )
        }

        fun rotationZ(angleRad: Float): Matrix3x3 {
            val c = kotlin.math.cos(angleRad)
            val s = kotlin.math.sin(angleRad)
            return Matrix3x3(
                floatArrayOf(
                    c, -s, 0f,
                    s,  c, 0f,
                    0f, 0f, 1f
                )
            )
        }
    }
}

@Composable
fun RubiksCube3D(
    faces: List<List<Color>>,
    rotationMatrix: Matrix3x3,
    onRotationChanged: (Matrix3x3) -> Unit,
    onStickerClick: (faceIndex: Int, stickerIndex: Int) -> Unit,
    onDoubleClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val latestProjectedQuads = remember { mutableListOf<ProjectedQuad>() }
    val currentOnStickerClick by rememberUpdatedState(onStickerClick)
    val currentRotationMatrix by rememberUpdatedState(rotationMatrix)
    val currentOnRotationChanged by rememberUpdatedState(onRotationChanged)
    val currentOnDoubleClick by rememberUpdatedState(onDoubleClick)

    var lastTapTime by remember { mutableLongStateOf(0L) }

    val flatColors = faces.flatMap { it }
    val quads = remember(flatColors) {
        val list = mutableListOf<Quad>()
        val t = 2f / 3f
        val gap = 0.015f

        for (f in 0..5) {
            for (r in 0..2) {
                for (c in 0..2) {
                    val u0 = (c - 1) * t - t / 2 + gap
                    val u1 = (c - 1) * t + t / 2 - gap
                    val v0 = (r - 1) * t - t / 2 + gap
                    val v1 = (r - 1) * t + t / 2 - gap

                    val corners = listOf(Pair(u0, v0), Pair(u1, v0), Pair(u1, v1), Pair(u0, v1))
                    val vertices = corners.map { (u, v) ->
                        val d = 1.0f
                        when (f) {
                            0 -> Vector3(u, d, v)     // U(顶)
                            1 -> Vector3(d, -v, -u)   // R(右)
                            2 -> Vector3(u, -v, d)    // F(前)
                            3 -> Vector3(u, -d, -v)   // D(底)
                            4 -> Vector3(-d, -v, u)   // L(左)
                            5 -> Vector3(-u, -v, -d)  // B(后)
                            else -> Vector3(0f, 0f, 0f)
                        }
                    }
                    val stickerIndex = r * 3 + c
                    list.add(Quad(vertices, faces[f][stickerIndex], f, stickerIndex))
                }
            }
        }
        list
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var isDrag = false
                    var totalDragAmount = Offset.Zero
                    
                    var pointerId0 = down.id
                    var pointerId1: androidx.compose.ui.input.pointer.PointerId? = null
                    
                    var lastPos0 = down.position
                    var lastPos1: Offset? = null

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedPointers = event.changes.filter { it.pressed }
                        
                        if (pressedPointers.isEmpty()) {
                            if (!isDrag && pointerId1 == null) {
                                val tapPoint = down.position
                                val currentTime = System.currentTimeMillis()
                                
                                if (currentTime - lastTapTime < 350L) {
                                    currentOnDoubleClick()
                                    lastTapTime = 0L // 重置，防止连续点击触发多次
                                } else {
                                    lastTapTime = currentTime
                                    
                                    val clickedQuad = latestProjectedQuads
                                        .asReversed()
                                        .firstOrNull { pq -> isPointInQuad(tapPoint, pq.points) }

                                    if (clickedQuad != null) {
                                        currentOnStickerClick(clickedQuad.faceIndex, clickedQuad.stickerIndex)
                                    }
                                }
                            }
                            break
                        }

                        val p0 = pressedPointers.firstOrNull { it.id == pointerId0 }
                        var p1 = pointerId1?.let { id -> pressedPointers.firstOrNull { it.id == id } }

                        if (p0 == null) {
                            if (p1 != null) {
                                pointerId0 = p1.id
                                lastPos0 = p1.position
                                pointerId1 = null
                                lastPos1 = null
                            } else {
                                break
                            }
                        } else {
                            val currentPos0 = p0.position
                            
                            val otherPointers = event.changes.filter { it.pressed && it.id != pointerId0 }
                            if (otherPointers.isNotEmpty() && pointerId1 == null) {
                                val newFinger = otherPointers.first()
                                pointerId1 = newFinger.id
                                lastPos1 = newFinger.position
                                isDrag = true 
                            }

                            p1 = pointerId1?.let { id -> pressedPointers.firstOrNull { it.id == id } }

                            if (p1 != null) {
                                val currentPos1 = p1.position
                                val prevPos0 = p0.previousPosition
                                val prevPos1 = p1.previousPosition

                                val isFirstFrameOfTwoFingers = (prevPos1 == currentPos1 || prevPos0 == currentPos0)
                                if (!isFirstFrameOfTwoFingers) {
                                    val vCurr = currentPos1 - currentPos0
                                    val vPrev = prevPos1 - prevPos0

                                    if (vCurr.getDistance() > 0.1f && vPrev.getDistance() > 0.1f) {
                                        val thetaCurr = kotlin.math.atan2(vCurr.y, vCurr.x)
                                        val thetaPrev = kotlin.math.atan2(vPrev.y, vPrev.x)
                                        var deltaTheta = thetaCurr - thetaPrev
                                        
                                        if (deltaTheta > Math.PI) deltaTheta -= (2 * Math.PI).toFloat()
                                        if (deltaTheta < -Math.PI) deltaTheta += (2 * Math.PI).toFloat()

                                        if (kotlin.math.abs(deltaTheta) < 0.5f) {
                                            // 使用 -deltaTheta 纠正方向
                                            currentOnRotationChanged(Matrix3x3.rotationZ(-deltaTheta) * currentRotationMatrix)
                                        }
                                    }
                                }
                                
                                p0.consume()
                                p1.consume()
                                
                                lastPos0 = currentPos0
                                lastPos1 = currentPos1
                            } else {
                                val dragAmount = currentPos0 - lastPos0
                                lastPos0 = currentPos0

                                totalDragAmount += dragAmount
                                if (totalDragAmount.getDistance() > 10f) {
                                    isDrag = true
                                }
                                if (isDrag) {
                                    val canvasWidth = size.width.toFloat()
                                    val canvasHeight = size.height.toFloat()

                                    val angleY = (dragAmount.x / canvasWidth) * Math.PI.toFloat() * 1.2f
                                    val angleX = (dragAmount.y / canvasHeight) * Math.PI.toFloat() * 1.2f

                                    val dM = Matrix3x3.rotationX(angleX) * Matrix3x3.rotationY(angleY)
                                    currentOnRotationChanged(dM * currentRotationMatrix)
                                    p0.consume()
                                }
                            }
                        }
                    }
                }
            }
    ) {
        val width = size.width
        val height = size.height
        val scale = minOf(width, height) * 1.1f

        val projectedQuads = quads.mapNotNull { quad ->
            val rotVerts = quad.vertices.map { v ->
                rotationMatrix.transform(v)
            }

            val cameraDist = 5f
            val projPoints = rotVerts.map { v ->
                val factor = scale / (cameraDist - v.z)
                Offset(width / 2f + v.x * factor, height / 2f - v.y * factor)
            }

            val p0 = projPoints[0]
            val p1 = projPoints[1]
            val p2 = projPoints[2]
            val cross = (p1.x - p0.x) * (p2.y - p0.y) - (p1.y - p0.y) * (p2.x - p0.x)
            if (cross <= 0) return@mapNotNull null

            val avgZ = rotVerts.map { it.z }.average().toFloat()
            ProjectedQuad(projPoints, quad.color, avgZ, quad.faceIndex, quad.stickerIndex)
        }

        val sortedQuads = projectedQuads.sortedBy { it.z }

        latestProjectedQuads.clear()
        latestProjectedQuads.addAll(sortedQuads)

        for (pq in sortedQuads) {
            val path = Path().apply {
                moveTo(pq.points[0].x, pq.points[0].y)
                lineTo(pq.points[1].x, pq.points[1].y)
                lineTo(pq.points[2].x, pq.points[2].y)
                lineTo(pq.points[3].x, pq.points[3].y)
                close()
            }

            drawPath(path, pq.color)
            drawPath(
                path = path,
                color = Color.Black,
                style = Stroke(width = 6f, join = StrokeJoin.Round)
            )
        }
    }
}

private fun isPointInQuad(p: Offset, vertices: List<Offset>): Boolean {
    if (vertices.size < 4) return false
    var intersectCount = 0
    for (i in 0 until 4) {
        val v1 = vertices[i]
        val v2 = vertices[(i + 1) % 4]
        if (((v1.y > p.y) != (v2.y > p.y)) &&
            (p.x < (v2.x - v1.x) * (p.y - v1.y) / (v2.y - v1.y) + v1.x)
        ) {
            intersectCount++
        }
    }
    return intersectCount % 2 != 0
}
