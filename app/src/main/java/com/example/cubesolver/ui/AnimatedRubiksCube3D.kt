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
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnimatedRubiksCube3D(
    faces: List<List<Color>>,
    rotationMatrix: Matrix3x3,
    onRotationChanged: (Matrix3x3) -> Unit,
    animatingMove: String?,
    animationProgress: Float,
    modifier: Modifier = Modifier
) {
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
                            0 -> Vector3(u, d, v)     // U
                            1 -> Vector3(d, -v, -u)   // R
                            2 -> Vector3(u, -v, d)    // F
                            3 -> Vector3(u, -d, -v)   // D
                            4 -> Vector3(-d, -v, u)   // L
                            5 -> Vector3(-u, -v, -d)  // B
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

    val currentRotationMatrix by rememberUpdatedState(rotationMatrix)
    val currentOnRotationChanged by rememberUpdatedState(onRotationChanged)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var isDrag = false
                    
                    var pointerId0 = down.id
                    var pointerId1: androidx.compose.ui.input.pointer.PointerId? = null
                    
                    var lastPos0 = down.position
                    var lastPos1: Offset? = null

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedPointers = event.changes.filter { it.pressed }
                        
                        if (pressedPointers.isEmpty()) break

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
                                // 处理双指顺逆时针旋转逻辑
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
                                            // 使用负值纠正旋转方向，确保魔方随手指顺滑旋转
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
    ) {
        val width = size.width
        val height = size.height
        val scale = minOf(width, height) * 1.1f

        val projectedQuads = quads.mapNotNull { quad ->
            val localVerts = if (animatingMove != null) {
                val moveChar = animatingMove[0]
                val isAffected = isStickerInLayer(quad.faceIndex, quad.stickerIndex, moveChar)
                if (isAffected) {
                    val isCounter = animatingMove.contains("'")
                    val isDouble = animatingMove.contains("2")
                    var targetRads = Math.PI.toFloat() / 2f
                    if (isDouble) targetRads = Math.PI.toFloat()
                    if (isCounter) targetRads = -targetRads

                    val currentRads = targetRads * animationProgress
                    quad.vertices.map { v -> rotateLocal(v, moveChar, currentRads) }
                } else {
                    quad.vertices
                }
            } else {
                quad.vertices
            }

            val rotVerts = localVerts.map { v ->
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

private fun rotateLocal(v: Vector3, faceChar: Char, angle: Float): Vector3 {
    val cosA = cos(angle)
    val sinA = sin(angle)
    return when (faceChar) {
        'U' -> Vector3(v.x * cosA - v.z * sinA, v.y, v.x * sinA + v.z * cosA)
        'D' -> Vector3(v.x * cosA + v.z * sinA, v.y, -v.x * sinA + v.z * cosA)
        'R' -> Vector3(v.x, v.y * cosA + v.z * sinA, -v.y * sinA + v.z * cosA)
        'L' -> Vector3(v.x, v.y * cosA - v.z * sinA, v.y * sinA + v.z * cosA)
        'F' -> Vector3(v.x * cosA + v.y * sinA, -v.x * sinA + v.y * cosA, v.z)
        'B' -> Vector3(v.x * cosA - v.y * sinA, v.x * sinA + v.y * cosA, v.z)
        else -> v
    }
}
