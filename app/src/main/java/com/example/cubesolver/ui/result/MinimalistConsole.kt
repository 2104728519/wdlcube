package com.example.cubesolver.ui.result

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun MinimalistConsole(
    modifier: Modifier = Modifier,
    isStateValid: Boolean,
    solutionText: String,
    scrambleFormula: String?,
    isSearchingOptimal: Boolean,
    isSearchingShorter: Boolean,
    onRetry: () -> Unit,
    onShowPlayer: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 移除高度限制和滚动，使公式内容自然展开，方便用户阅读完整步骤
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!scrambleFormula.isNullOrBlank()) {
                    Text(
                        text = "打乱: $scrambleFormula",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = if (isStateValid) "解法: $solutionText" else "错误: $solutionText",
                    color = if (isStateValid) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRetry,
                    enabled = !isSearchingOptimal && !isSearchingShorter,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (scrambleFormula != null) "重置" else "重新扫描", style = MaterialTheme.typography.labelLarge)
                }

                if (isStateValid) {
                    Button(
                        onClick = onShowPlayer,
                        enabled = !isSearchingOptimal && !isSearchingShorter,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("3D 还原演示", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
