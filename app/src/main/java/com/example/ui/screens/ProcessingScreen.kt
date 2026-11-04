package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.viewmodel.AnalysisProcessState
import kotlinx.coroutines.delay

@Composable
fun ProcessingScreen(
    bitmap: Bitmap?,
    processState: AnalysisProcessState,
    onCancelAnalysis: () -> Unit,
    onRetryAnalysis: () -> Unit,
    onReturnToCamera: () -> Unit,
    onAnalysisCompleted: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler {
        onCancelAnalysis()
        onReturnToCamera()
    }

    var elapsedSeconds by remember(processState) { mutableIntStateOf(0) }

    LaunchedEffect(processState, bitmap) {
        when (processState) {
            is AnalysisProcessState.Success -> {
                onAnalysisCompleted()
            }
            is AnalysisProcessState.Idle -> {
                if (bitmap != null) {
                    delay(250L)
                    onRetryAnalysis()
                }
            }
            is AnalysisProcessState.Loading -> {
                elapsedSeconds = 0
                while (true) {
                    delay(1_000L)
                    elapsedSeconds++
                }
            }
            is AnalysisProcessState.Error -> Unit
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "scan_line")
    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_y"
    )

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 2.dp,
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    if (bitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(18.dp))
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Imagen en análisis",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (processState is AnalysisProcessState.Loading) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val y = size.height * scanProgress
                                    drawLine(
                                        color = Color(0xFF66BB6A),
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = 3.dp.toPx()
                                    )
                                }
                            }
                        }
                    }

                    when (processState) {
                        is AnalysisProcessState.Loading, AnalysisProcessState.Idle -> {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("processing_loading_indicator")
                            )
                            Text(
                                text = stringResource(R.string.processing_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (elapsedSeconds > 0) {
                                    "${stringResource(R.string.processing_subtitle)} (${elapsedSeconds} s)"
                                } else {
                                    stringResource(R.string.processing_subtitle)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        onCancelAnalysis()
                                        onReturnToCamera()
                                    },
                                    modifier = Modifier.testTag("cancel_processing_button")
                                ) {
                                    Text(stringResource(R.string.processing_cancel))
                                }
                                if (elapsedSeconds >= 8) {
                                    OutlinedButton(
                                        onClick = onRetryAnalysis,
                                        modifier = Modifier.testTag("early_retry_processing_button")
                                    ) {
                                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                                        Text(" Reintentar")
                                    }
                                }
                            }
                        }

                        is AnalysisProcessState.Error -> {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = stringResource(R.string.error_generic_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = processState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("processing_error_message")
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onReturnToCamera,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("error_back_button")
                                ) {
                                    Text("Volver")
                                }
                                Button(
                                    onClick = onRetryAnalysis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("retry_analysis_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                                    Text(" " + stringResource(R.string.error_retry_button))
                                }
                            }
                        }

                        is AnalysisProcessState.Success -> {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }
}
