package com.example.cubesolver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cubesolver.utils.ScrambleUtils
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartScan: (ScanMode) -> Unit,
    onStartScramble: (List<List<Color>>, String) -> Unit
) {
    val uriHandler = LocalUriHandler.current

    val standardFaces = remember {
        listOf(
            List(9) { Color(0xFFFFFFFF) }, // U
            List(9) { Color(0xFFD32F2F) }, // R
            List(9) { Color(0xFF388E3C) }, // F
            List(9) { Color(0xFFFBC02D) }, // D
            List(9) { Color(0xFFF57C00) }, // L
            List(9) { Color(0xFF1976D2) }  // B
        )
    }
    var rotationMatrix by remember { mutableStateOf(Matrix3x3()) }

    var showGuideSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        while (true) {
            rotationMatrix = Matrix3x3.rotationY(0.01f) * Matrix3x3.rotationX(0.005f) * rotationMatrix
            delay(16)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
        Box(modifier = Modifier.fillMaxSize().padding(bottom = 200.dp)) {
            RubiksCube3D(
                faces = standardFaces,
                rotationMatrix = rotationMatrix,
                onRotationChanged = {},
                onStickerClick = { _, _ -> },
                modifier = Modifier.fillMaxSize()
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = { uriHandler.openUri("https://github.com/2104728519/cubesolver") },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Code,
                    contentDescription = "GitHub Repository",
                    tint = Color.White
                )
            }

            IconButton(
                onClick = { showGuideSheet = true },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "使用说明",
                    tint = Color.White
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = "魔方求解器-WDL",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Text(
                text = "扫描 · 求解",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 48.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { onStartScan(ScanMode.MANUAL) },
                    modifier = Modifier.weight(1f).height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C))
                ) {
                    Text("经典扫描", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onStartScan(ScanMode.AUTO) },
                    modifier = Modifier.weight(1f).height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                ) {
                    Text("AI 扫描", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    val formula = ScrambleUtils.generateScrambleFormula(20)
                    val scrambledFaces = ScrambleUtils.getScrambledState(formula)
                    onStartScramble(scrambledFaces, formula)
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("随机打乱", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(48.dp))
        }

        if (showGuideSheet) {
            ModalBottomSheet(
                onDismissRequest = { showGuideSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxHeight(0.9f)
            ) {
                UserGuideContent()
            }
        }
    }
}
