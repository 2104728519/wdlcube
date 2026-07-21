
package com.example.cubesolver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun UserGuideContent() {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "功能介绍与操作指南",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

        LazyColumn(
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                GuideSection(title = "主界面 (Home Screen)", icon = Icons.Outlined.Home) {
                    GuideBullet("魔方预览：主界面中央展示 3D 魔方模型。")
                    GuideBullet("实体扫描：启动相机功能，用于采集真实魔方的表面颜色。")
                    GuideBullet("随机打乱：由系统生成 20 步打乱公式作用于标准魔方，供在没有实体魔方时进行模拟。")
                }
            }

            item {
                GuideSection(title = "相机录入 (Camera Scanner)", icon = Icons.Outlined.CameraAlt) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("⚠️ 翻转录入顺序规范（若不按此顺序将导致解法错误）", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(Modifier.height(8.dp))
                            Text("请保持魔方中心轴相对不动，按以下屏幕提示的顺序翻转录入：", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            Text("1. 正对顶面 (U)\n2. 魔方整体向左转 90° (录入原右侧面 R)\n3. 魔方整体向上翻 90° (录入原底面 F)\n4. 魔方整体向左转 90° (录入原右侧面 D)\n5. 魔方整体向上翻 90° (录入原底面 L)\n6. 魔方整体向左转 90° (录入原 B 面)", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    GuideBullet("动作指示：九宫格边缘有滑动箭头，指示下一步应该“左转”或“上翻”魔方。")
                    GuideBullet("暗光补光：提供补光灯手动开关。")
                    GuideBullet("振动反馈（易忽略）：单面录入成功时，设备会产生 50 毫秒的微振动，无需看屏幕即可确认录入状态。")
                    GuideBullet("数据提取：后台通过 K-Means 聚类和匈牙利算法，自动过滤中心块 Logo 和表面反光，提取出 6 种标准色。")
                }
            }

            item {
                GuideSection(title = "多状态选择 (State Selection)", icon = Icons.Outlined.Layers) {
                    GuideBullet("生成多状态：当环境光线不佳导致颜色混淆，或因魔方对称导致推导出多种“物理可解”状态时，系统会生成备选状态列表。")
                    GuideBullet("状态比对：滑动 3D 模型进行手动比对，点击“换下一个”切换方案，在确认与手中实物一致后，点击“就是这个”进入求解。")
                }
            }

            item {
                GuideSection(title = "结果与编辑 (Result Screen)", icon = Icons.Outlined.Edit) {
                    GuideBullet("3D 视角重置（易忽略）：单指滑动改变整体视角；双指按顺/逆时针方向滑动，可在平面内旋转 Z 轴；双击模型可直接重置至默认透视角。")
                    GuideBullet("2D 展开图切换（易忽略）：点击“2D/3D”按钮可以切换为 2D 展开图，支持一屏直接查看并手动编辑魔方的所有 6 个面。")
                    GuideBullet("调色板联动修改（易忽略）：在“颜色微调”面板中选择底部的某个代表色块，调整 RGB 后点击【应用】，会一键修改整个魔方上所有对应的色块，无需手动逐个点击。")
                    GuideBullet("手动单色修改：开启‘编辑’后，在底部选择对应颜色，点击 2D/3D 魔方的特定色块即可修改单个色块的颜色。")
                    GuideBullet("步数压缩：点击“寻找更少步解”可调用多核多线程，在 150 毫秒计算窗口内对已有步骤进行搜索压缩。")
                }
            }

            item {
                GuideSection(title = "3D 还原演示 (Cube Player)", icon = Icons.Outlined.PlayArrow) {
                    GuideBullet("步骤条跳转（易忽略）：顶部横向步骤条支持滑动，点击特定步数的标签可以直接使 3D 模型跳转至对应步数的魔方状态。")
                    GuideBullet("还原控制：提供重置(↺)、单步后退(⏮)、自动播放/暂停(▶/⏸)、单步前进(⏭)。")
                    GuideBullet("无级调速：支持在 250 毫秒至 1800 毫秒范围内调节单步动画过渡时间。")
                }
            }
        }
    }
}

@Composable
private fun GuideSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        content()
    }
}

@Composable
private fun GuideBullet(text: String) {
    Row(modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)) {
        Text(text = "•", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, lineHeight = 22.sp)
    }
}