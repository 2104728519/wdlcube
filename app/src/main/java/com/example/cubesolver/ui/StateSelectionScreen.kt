package com.example.cubesolver.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun StateSelectionScreen(
    modifier: Modifier = Modifier,
    validStates: List<List<List<Color>>>,
    onStateSelected: (List<List<Color>>) -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    val currentFaces = validStates[currentIndex]

    val initialRotation = remember {
        val radX = Math.toRadians(25.0).toFloat()
        val radY = Math.toRadians(-45.0).toFloat()
        Matrix3x3.rotationX(radX) * Matrix3x3.rotationY(radY)
    }
    var rotationMatrix by remember { mutableStateOf(initialRotation) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "检测到多个可能的状态",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "状态 ${currentIndex + 1} / ${validStates.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )

            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                RubiksCube3D(
                    faces = currentFaces,
                    rotationMatrix = rotationMatrix,
                    onRotationChanged = { rotationMatrix = it },
                    onStickerClick = { _, _ -> },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Text(
                text = "请滑动 3D 模型比对，确认与手中魔方一致。",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        currentIndex = (currentIndex + 1) % validStates.size
                        rotationMatrix = initialRotation
                    },
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Text("换下一个")
                }

                Button(
                    onClick = { onStateSelected(currentFaces) },
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Text("就是这个", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}