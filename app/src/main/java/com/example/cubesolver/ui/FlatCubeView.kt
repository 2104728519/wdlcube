package com.example.cubesolver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun FlatCubeView(
    faces: List<List<Color>>,
    onStickerClick: (faceIndex: Int, stickerIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val faceSize = Modifier.size(72.dp)
        val spacing = 4.dp

        Row {
            Box(modifier = faceSize)
            Spacer(modifier = Modifier.width(spacing))
            Face2D(faceIndex = 0, colors = faces[0], onStickerClick = onStickerClick, modifier = faceSize)
            Spacer(modifier = Modifier.width(spacing))
            Box(modifier = faceSize)
            Spacer(modifier = Modifier.width(spacing))
            Box(modifier = faceSize)
        }
        Spacer(modifier = Modifier.height(spacing))

        Row {
            Face2D(faceIndex = 4, colors = faces[4], onStickerClick = onStickerClick, modifier = faceSize)
            Spacer(modifier = Modifier.width(spacing))
            Face2D(faceIndex = 2, colors = faces[2], onStickerClick = onStickerClick, modifier = faceSize)
            Spacer(modifier = Modifier.width(spacing))
            Face2D(faceIndex = 1, colors = faces[1], onStickerClick = onStickerClick, modifier = faceSize)
            Spacer(modifier = Modifier.width(spacing))
            Face2D(faceIndex = 5, colors = faces[5], onStickerClick = onStickerClick, modifier = faceSize)
        }
        Spacer(modifier = Modifier.height(spacing))

        Row {
            Box(modifier = faceSize)
            Spacer(modifier = Modifier.width(spacing))
            Face2D(faceIndex = 3, colors = faces[3], onStickerClick = onStickerClick, modifier = faceSize)
            Spacer(modifier = Modifier.width(spacing))
            Box(modifier = faceSize)
            Spacer(modifier = Modifier.width(spacing))
            Box(modifier = faceSize)
        }
    }
}

@Composable
fun Face2D(
    faceIndex: Int,
    colors: List<Color>,
    onStickerClick: (faceIndex: Int, stickerIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xFF333333), shape = RoundedCornerShape(4.dp))
            .border(1.dp, Color.Black, shape = RoundedCornerShape(4.dp))
            .padding(2.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        for (r in 0..2) {
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (c in 0..2) {
                    val index = r * 3 + c
                    val isCenter = index == 4
                    val color = colors[index]

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(1.dp)
                            .background(color, shape = RoundedCornerShape(2.dp))
                            .border(
                                width = if (isCenter) 2.dp else 0.5.dp,
                                color = if (isCenter) Color.Black else Color.DarkGray,
                                shape = RoundedCornerShape(2.dp)
                            )
                            .clickable { onStickerClick(faceIndex, index) }
                    )
                }
            }
        }
    }
}