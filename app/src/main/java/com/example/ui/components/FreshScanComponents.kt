package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.FoodProduct
import com.example.domain.model.RipenessState
import com.example.ui.theme.StatusAlertRed
import com.example.ui.theme.StatusOverripeBrown
import com.example.ui.theme.StatusRipeGreen
import com.example.ui.theme.StatusRipeningAmber
import com.example.ui.theme.StatusUnripeTeal

@Composable
fun FreshScanLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    val primary = MaterialTheme.colorScheme.primary
    val container = MaterialTheme.colorScheme.primaryContainer

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.56f)) {
            val w = this.size.width
            val h = this.size.height
            val leafPath = Path().apply {
                moveTo(w * 0.18f, h * 0.82f)
                cubicTo(w * 0.15f, h * 0.32f, w * 0.48f, h * 0.12f, w * 0.86f, h * 0.14f)
                cubicTo(w * 0.88f, h * 0.52f, w * 0.68f, h * 0.85f, w * 0.18f, h * 0.82f)
                close()
            }
            drawPath(
                path = leafPath,
                color = primary.copy(alpha = 0.2f)
            )
            drawPath(
                path = leafPath,
                color = primary,
                style = Stroke(width = w * 0.085f, cap = StrokeCap.Round)
            )
            drawLine(
                color = primary,
                start = Offset(w * 0.16f, h * 0.84f),
                end = Offset(w * 0.68f, h * 0.32f),
                strokeWidth = w * 0.075f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun BotanicalHeroIllustration(
    modifier: Modifier = Modifier
) {
    FreshScanLogoMark(modifier = modifier, size = 72.dp)
}

private data class BadgeVisualStyle(
    val bgColor: Color,
    val fgColor: Color,
    val label: String,
    val icon: ImageVector
)

@Composable
fun RipenessStatusBadge(
    state: RipenessState,
    hasDeterioration: Boolean,
    modifier: Modifier = Modifier
) {
    val style = if (hasDeterioration) {
        BadgeVisualStyle(
            bgColor = StatusAlertRed.copy(alpha = 0.14f),
            fgColor = StatusAlertRed,
            label = "Posible deterioro visible",
            icon = Icons.Default.WarningAmber
        )
    } else {
        when (state) {
            RipenessState.UNRIPE -> BadgeVisualStyle(
                bgColor = StatusUnripeTeal.copy(alpha = 0.14f),
                fgColor = StatusUnripeTeal,
                label = state.displayName,
                icon = Icons.Default.Schedule
            )
            RipenessState.RIPENING -> BadgeVisualStyle(
                bgColor = StatusRipeningAmber.copy(alpha = 0.14f),
                fgColor = StatusRipeningAmber,
                label = state.displayName,
                icon = Icons.Default.Schedule
            )
            RipenessState.RIPE -> BadgeVisualStyle(
                bgColor = StatusRipeGreen.copy(alpha = 0.14f),
                fgColor = StatusRipeGreen,
                label = state.displayName,
                icon = Icons.Default.CheckCircle
            )
            RipenessState.OVERRIPE -> BadgeVisualStyle(
                bgColor = StatusOverripeBrown.copy(alpha = 0.14f),
                fgColor = StatusOverripeBrown,
                label = state.displayName,
                icon = Icons.Default.WarningAmber
            )
            RipenessState.UNDETERMINED -> BadgeVisualStyle(
                bgColor = MaterialTheme.colorScheme.surfaceVariant,
                fgColor = MaterialTheme.colorScheme.onSurfaceVariant,
                label = state.displayName,
                icon = Icons.Default.Eco
            )
        }
    }

    Surface(
        color = style.bgColor,
        contentColor = style.fgColor,
        shape = CircleShape,
        modifier = modifier.testTag("ripeness_status_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = style.fgColor
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = style.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = style.fgColor
            )
        }
    }
}

@Composable
fun CloudConsentDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = {
            Text(
                text = stringResource(R.string.consent_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = stringResource(R.string.consent_dialog_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                modifier = Modifier.testTag("consent_accept_button")
            ) {
                Text(stringResource(R.string.consent_accept_button))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDecline,
                modifier = Modifier.testTag("consent_decline_button")
            ) {
                Text(stringResource(R.string.consent_decline_button))
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualProductCorrectionDialog(
    currentProductName: String,
    onDismiss: () -> Unit,
    onConfirmCorrection: (String, Boolean) -> Unit
) {
    var inputName by remember { mutableStateOf(currentProductName) }
    var reanalyzeWithGemini by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.manual_correction_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = inputName,
                    onValueChange = { inputName = it },
                    label = { Text(stringResource(R.string.manual_correction_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_product_input")
                )

                Text(
                    text = "Catálogo rápido:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FoodProduct.SUPPORTED_CATALOG.take(8).forEach { item ->
                        FilterChip(
                            selected = inputName.equals(item.canonicalName, ignoreCase = true),
                            onClick = { inputName = item.canonicalName },
                            label = {
                                Text(
                                    text = item.canonicalName.substringBefore(" /"),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { reanalyzeWithGemini = !reanalyzeWithGemini }
                ) {
                    Checkbox(
                        checked = reanalyzeWithGemini,
                        onCheckedChange = { reanalyzeWithGemini = it },
                        modifier = Modifier.testTag("reanalyze_with_cloud_checkbox")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Re-evaluar madurez visual con IA para este producto",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleaned = inputName.trim()
                    if (cleaned.isNotEmpty()) {
                        onConfirmCorrection(cleaned, reanalyzeWithGemini)
                    }
                },
                enabled = inputName.trim().isNotEmpty(),
                modifier = Modifier.testTag("confirm_manual_correction_button")
            ) {
                Text(stringResource(R.string.manual_correction_apply))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_manual_correction_button")
            ) {
                Text(stringResource(R.string.manual_correction_cancel))
            }
        }
    )
}
