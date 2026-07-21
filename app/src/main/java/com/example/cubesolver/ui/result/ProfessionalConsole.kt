package com.example.cubesolver.ui.result

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ProfessionalConsole(
    modifier: Modifier = Modifier,
    faces: List<List<Color>>,
    solutionText: String,
    isStateValid: Boolean,
    scrambleFormula: String?,
    originalFaces: List<List<Color>>,
    dynamicPalette: List<Color>,
    isManualEditMode: Boolean,
    onManualEditModeChange: (Boolean) -> Unit,
    selectedColorIndex: Int,
    onSelectedColorIndexChange: (Int) -> Unit,
    onFaceColorChanged: (faceIndex: Int, stickerIndex: Int, newColor: Color) -> Unit,
    onFacesReoriented: (List<List<Color>>) -> Unit,
    onRetry: () -> Unit,
    onShowPlayer: () -> Unit,
    isSearchingOptimal: Boolean,
    isSearchingShorter: Boolean,
    searchedNodes: Long,
    currentSearchDepth: Int,
    optimalStatusMsg: String,
    searchStartTime: Long,
    onStartOptimalSearch: () -> Unit,
    onStopOptimalSearch: () -> Unit,
    onStartSqueezeSearch: () -> Unit,
    onStopSqueezeSearch: () -> Unit,
    onPaletteColorApplied: (index: Int, oldColor: Color, newColor: Color) -> Unit
) {
    var activeTab by remember { mutableIntStateOf(0) }
    var showColorPicker by remember { mutableStateOf(false) }

    var sliderRed by remember { mutableFloatStateOf(255f) }
    var sliderGreen by remember { mutableFloatStateOf(255f) }
    var sliderBlue by remember { mutableFloatStateOf(255f) }

    LaunchedEffect(selectedColorIndex, dynamicPalette) {
        if (selectedColorIndex < dynamicPalette.size) {
            val targetColor = dynamicPalette[selectedColorIndex]
            sliderRed = targetColor.red * 255f
            sliderGreen = targetColor.green * 255f
            sliderBlue = targetColor.blue * 255f
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("解步与分析", style = MaterialTheme.typography.labelLarge) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("颜色微调", style = MaterialTheme.typography.labelLarge) }
                )
            }

            // 移除 heightIn 和 padding(16.dp)，由内部自行管理自然高度
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                if (activeTab == 0) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!scrambleFormula.isNullOrBlank()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(" 打乱: ", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = scrambleFormula,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (isStateValid) "建议解法步骤：" else "检测到不合法的色块拼接：",
                                    color = if (isStateValid) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = solutionText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 18.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (isStateValid) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (isSearchingShorter) onStopSqueezeSearch() else onStartSqueezeSearch()
                                    },
                                    enabled = !isSearchingOptimal,
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isSearchingShorter) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(if (isSearchingShorter) " 停止" else "寻找更少步解", style = MaterialTheme.typography.labelLarge)
                                }

                                Button(
                                    onClick = {
                                        if (isSearchingOptimal) onStopOptimalSearch() else onStartOptimalSearch()
                                    },
                                    enabled = !isSearchingShorter,
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isSearchingOptimal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.weight(1.1f).height(40.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(if (isSearchingOptimal) "停止最优解" else "寻找最优解", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }

                        AnimatedVisibility(visible = isSearchingOptimal || isSearchingShorter || optimalStatusMsg.isNotEmpty()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF0F0F11),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .border(0.5.dp, Color(0xFF00FF00).copy(alpha = 0.3f), MaterialTheme.shapes.medium)
                                        .padding(8.dp)
                                ) {
                                    Text("> 运行状态: $optimalStatusMsg", color = Color(0xFF00FF00), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                    if (isSearchingOptimal || isSearchingShorter) {
                                        val depthLabel = if (isSearchingOptimal) "搜索深度（三轴协同最优解 IDA*）" else "搜索深度（多线程双阶段 IDA*）"
                                        Text("> $depthLabel: ≤ $currentSearchDepth 步", color = Color.Yellow, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)

                                        val formattedNodes = NumberFormat.getNumberInstance(Locale.US).format(searchedNodes)
                                        Text("> 已遍历节点: $formattedNodes", color = Color.Cyan, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)

                                        val elapsedSeconds = (System.currentTimeMillis() - searchStartTime) / 1000.0
                                        if (elapsedSeconds > 0.5) {
                                            val nodesPerSec = (searchedNodes / elapsedSeconds) / 10000.0
                                            Text(String.format(Locale.US, "> 演算速度: %.2f 万节点/秒", nodesPerSec), color = Color(0xFF00FFCC), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { onFacesReoriented(originalFaces) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                shape = MaterialTheme.shapes.small,
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.height(32.dp).weight(1f)
                            ) {
                                Text("重置色调", style = MaterialTheme.typography.labelSmall)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = { showColorPicker = !showColorPicker },
                                colors = ButtonDefaults.buttonColors(containerColor = if (showColorPicker) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant, contentColor = if (showColorPicker) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant),
                                shape = MaterialTheme.shapes.small,
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.height(32.dp).weight(1f)
                            ) {
                                Text(if (showColorPicker) "收起 " else "调色", style = MaterialTheme.typography.labelSmall)
                            }

                            if (isStateValid) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { onManualEditModeChange(!isManualEditMode) },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isManualEditMode) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer, contentColor = if (isManualEditMode) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer),
                                    shape = MaterialTheme.shapes.small,
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier.height(32.dp).weight(1f)
                                ) {
                                    Text(if (isManualEditMode) "锁定 " else "编辑 ", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            dynamicPalette.forEachIndexed { index, color ->
                                val isSelected = index == selectedColorIndex
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 6.dp)
                                        .size(if (isSelected) 34.dp else 28.dp)
                                        .background(color, CircleShape)
                                        .border(if (isSelected) 2.5.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                                        .clickable { onSelectedColorIndexChange(index) }
                                )
                            }
                        }

                        if (showColorPicker && selectedColorIndex < dynamicPalette.size) {
                            val previewColor = Color(sliderRed / 255f, sliderGreen / 255f, sliderBlue / 255f)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(previewColor, MaterialTheme.shapes.small)
                                                .border(1.5.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))

                                        Button(
                                            onClick = {
                                                if (selectedColorIndex < dynamicPalette.size) {
                                                    val oldColor = dynamicPalette[selectedColorIndex]
                                                    onPaletteColorApplied(selectedColorIndex, oldColor, previewColor)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            shape = MaterialTheme.shapes.small,
                                            modifier = Modifier.weight(1f).height(32.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("应用（同时修改所有色块）", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }

                                    RGBSliderRow(label = "红", value = sliderRed, color = Color.Red, onValueChange = { sliderRed = it })
                                    RGBSliderRow(label = "绿", value = sliderGreen, color = Color.Green, onValueChange = { sliderGreen = it })
                                    RGBSliderRow(label = "蓝", value = sliderBlue, color = Color.Blue, onValueChange = { sliderBlue = it })
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRetry,
                    enabled = !isSearchingOptimal && !isSearchingShorter,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (scrambleFormula != null) " 重置" else "重新扫描", style = MaterialTheme.typography.labelLarge)
                }

                if (isStateValid) {
                    Button(
                        onClick = onShowPlayer,
                        enabled = !isSearchingOptimal && !isSearchingShorter,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("3D 还原演示 ", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun RGBSliderRow(
    label: String,
    value: Float,
    color: Color,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: ${value.toInt()}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(42.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color.copy(alpha = 0.5f),
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.weight(1f)
        )
    }
}