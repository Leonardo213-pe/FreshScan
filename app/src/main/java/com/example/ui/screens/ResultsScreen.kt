package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ImageProcessor
import com.example.domain.model.FoodAnalysis
import com.example.domain.model.NormalizedBoundingBox
import com.example.domain.model.RipenessState
import com.example.domain.model.ShelfLifeEstimate
import com.example.ui.components.ManualProductCorrectionDialog
import com.example.ui.components.RipenessStatusBadge
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    bitmap: Bitmap?,
    analysis: FoodAnalysis,
    hasPreviousAutomaticResult: Boolean,
    onImproveAnalysisClick: () -> Unit,
    onRestorePreviousResult: () -> Unit,
    onCorrectProduct: (String, RipenessState?, Boolean) -> Unit,
    onNewScanClick: () -> Unit,
    selectedProductIndex: Int = 0,
    onSelectProductIndex: (Int) -> Unit = {},
    isGeminiRefiningInBackground: Boolean = false,
    modifier: Modifier = Modifier
) {
    val allItems = remember(analysis) { analysis.allProducts }
    var internalSelectedIndex by rememberSaveable(analysis.sessionId) { mutableIntStateOf(0) }
    val activeIndex = (if (selectedProductIndex in allItems.indices) selectedProductIndex else internalSelectedIndex)
        .coerceIn(0, (allItems.size - 1).coerceAtLeast(0))
    val activeItem = allItems.getOrElse(activeIndex) { analysis }

    val selectItem: (Int) -> Unit = { idx ->
        internalSelectedIndex = idx
        onSelectProductIndex(idx)
    }

    var showCorrectionDialog by rememberSaveable { mutableStateOf(false) }
    var showFullDetails by rememberSaveable { mutableStateOf(false) }
    val imageProcessor = remember { ImageProcessor() }

    val selectedCropBitmap = remember(bitmap, activeItem.boundingBox, allItems.size) {
        if (bitmap != null && allItems.size > 1 && activeItem.boundingBox != null && activeItem.boundingBox.isValid) {
            imageProcessor.cropToBoundingBox(bitmap, activeItem.boundingBox, paddingFraction = 0.16f)
        } else {
            null
        }
    }

    BackHandler {
        if (hasPreviousAutomaticResult && analysis.isAdvancedAnalysis) {
            onRestorePreviousResult()
        } else {
            onNewScanClick()
        }
    }

    if (showCorrectionDialog) {
        ManualProductCorrectionDialog(
            currentProductName = activeItem.displayProductName,
            currentRipeness = activeItem.ripenessState,
            onDismiss = { showCorrectionDialog = false },
            onConfirmCorrection = { correctedName, correctedRipeness, reanalyzeWithCloud ->
                showCorrectionDialog = false
                onCorrectProduct(correctedName, correctedRipeness, reanalyzeWithCloud)
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (allItems.size > 1) {
                            "Producto ${activeIndex + 1} de ${allItems.size}"
                        } else if (analysis.isAdvancedAnalysis) {
                            "Estimación Ajustada"
                        } else {
                            "Resultado"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (hasPreviousAutomaticResult && analysis.isAdvancedAnalysis) {
                                onRestorePreviousResult()
                            } else {
                                onNewScanClick()
                            }
                        },
                        modifier = Modifier.testTag("results_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .testTag("results_lazy_column"),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(24.dp)
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (bitmap != null) {
                                HighlightedProduceImage(
                                    bitmap = bitmap,
                                    detectedItems = allItems,
                                    selectedIndex = activeIndex,
                                    onSelectIndex = selectItem,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (allItems.size > 1) 240.dp else 200.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .testTag("results_food_image")
                                )
                            }

                            if (allItems.size > 1) {
                                Text(
                                    text = "Se detectaron ${allItems.size} productos. Toque un recuadro en la foto o elija abajo para inspeccionar o corregir cada uno:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("detected_products_selector_row")
                                ) {
                                    itemsIndexed(allItems) { idx, item ->
                                        FilterChip(
                                            selected = idx == activeIndex,
                                            onClick = { selectItem(idx) },
                                            label = {
                                                Text(
                                                    text = "#${idx + 1} ${item.displayProductName} · ${item.ripenessState.displayName}",
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            modifier = Modifier.testTag("product_selector_chip_$idx")
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (selectedCropBitmap != null) {
                                        Image(
                                            bitmap = selectedCropBitmap.asImageBitmap(),
                                            contentDescription = "Recorte del producto seleccionado",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .border(
                                                    width = 1.5.dp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .testTag("selected_product_crop_preview")
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        if (allItems.size > 1) {
                                            Text(
                                                text = "Elemento #${activeIndex + 1} seleccionado",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Text(
                                            text = activeItem.displayProductName,
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.testTag("product_name_title")
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = { showCorrectionDialog = true },
                                    modifier = Modifier.testTag("correct_product_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (allItems.size > 1) "Corregir #${activeIndex + 1}" else "Corregir")
                                }
                            }

                            RipenessStatusBadge(
                                state = activeItem.ripenessState,
                                hasDeterioration = activeItem.hasVisibleDeterioration
                            )

                            if (isGeminiRefiningInBackground) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            text = "Completando detalles visuales...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            )

                            val shelfLife = activeItem.shelfLifeEstimate
                            if (shelfLife.isValidatedEstimate &&
                                shelfLife.remainingShelfLifeDaysMin != null &&
                                shelfLife.remainingShelfLifeDaysMax != null
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Vida útil estimada",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${shelfLife.remainingShelfLifeDaysMin} a ${shelfLife.remainingShelfLifeDaysMax} días",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.testTag("shelf_life_days_value")
                                        )
                                    }

                                    if (shelfLife.daysToRipenMin != null &&
                                        shelfLife.daysToRipenMax != null &&
                                        shelfLife.daysToRipenMax > 0
                                    ) {
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "Madurez óptima en",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "${shelfLife.daysToRipenMin}–${shelfLife.daysToRipenMax} días",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                if (shelfLife.roomTempDaysMin != null && shelfLife.refrigeratedDaysMin != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = "Temp. ambiente",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "${shelfLife.roomTempDaysMin}–${shelfLife.roomTempDaysMax} días",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = "En refrigeración",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "${shelfLife.refrigeratedDaysMin}–${shelfLife.refrigeratedDaysMax} días",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = shelfLife.predictionLimitations.firstOrNull()
                                        ?: ShelfLifeEstimate.UNRELIABLE_SHELF_LIFE_MESSAGE,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("shelf_life_unavailable_text")
                                )
                            }
                        }
                    }
                }

                if (allItems.size > 1) {
                    item {
                        MultiProductSummaryCard(
                            fullBitmap = bitmap,
                            imageProcessor = imageProcessor,
                            items = allItems,
                            selectedIndex = activeIndex,
                            onSelectIndex = selectItem
                        )
                    }
                }

                if (activeItem.hasVisibleDeterioration) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("deterioration_warning_card")
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = stringResource(R.string.results_deterioration_alert_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val signsText = activeItem.deteriorationSigns.joinToString(" • ")
                                    Text(
                                        text = signsText.ifBlank {
                                            stringResource(R.string.results_deterioration_safety_advice)
                                        },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                if (activeItem.recommendations.isNotEmpty()) {
                    item {
                        MinimalSectionCard(title = stringResource(R.string.results_recommendations_title)) {
                            activeItem.recommendations.take(3).forEach { rec ->
                                MinimalBulletItem(text = rec)
                            }
                        }
                    }
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(18.dp)
                            )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showFullDetails = !showFullDetails }
                                    .testTag("toggle_details_button"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (showFullDetails) "Ocultar observaciones y notas" else "Ver observaciones y notas técnicas",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = if (showFullDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (showFullDetails) {
                                Spacer(modifier = Modifier.height(12.dp))
                                if (activeItem.visualObservations.isNotEmpty()) {
                                    Text(
                                        text = stringResource(R.string.results_visual_observations_title),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    activeItem.visualObservations.forEach { obs ->
                                        MinimalBulletItem(text = obs)
                                    }
                                }

                                activeItem.shelfLifeEstimate.generalStorageReference?.let { ref ->
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = stringResource(R.string.results_general_reference_title),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = ref,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = activeItem.safetyWarning,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (activeItem.isSupportedForAdvancedFeatures && !activeItem.isAdvancedAnalysis) {
                            OutlinedButton(
                                onClick = onImproveAnalysisClick,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("improve_analysis_button")
                            ) {
                                Icon(imageVector = Icons.Default.Tune, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (allItems.size > 1) {
                                        "Ajustar almacenamiento de ${activeItem.displayProductName}"
                                    } else {
                                        stringResource(R.string.results_improve_analysis_cta)
                                    }
                                )
                            }
                        }

                        if (hasPreviousAutomaticResult && analysis.isAdvancedAnalysis) {
                            TextButton(
                                onClick = onRestorePreviousResult,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("restore_automatic_analysis_button")
                            ) {
                                Text(stringResource(R.string.results_back_to_automatic))
                            }
                        }

                        Button(
                            onClick = onNewScanClick,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("scan_another_button")
                        ) {
                            Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.results_scan_another),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun MultiProductSummaryCard(
    fullBitmap: Bitmap?,
    imageProcessor: ImageProcessor,
    items: List<FoodAnalysis>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                shape = RoundedCornerShape(18.dp)
            )
            .testTag("multi_product_summary_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Comparativa por Unidad Detectada (${items.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            items.forEachIndexed { idx, item ->
                val isSelected = idx == selectedIndex
                val shelf = item.shelfLifeEstimate
                val daysText = if (shelf.remainingShelfLifeDaysMin != null && shelf.remainingShelfLifeDaysMax != null) {
                    "${shelf.remainingShelfLifeDaysMin}–${shelf.remainingShelfLifeDaysMax} días"
                } else {
                    "Sin estimación"
                }
                val itemThumb = remember(fullBitmap, item.boundingBox) {
                    if (fullBitmap != null && item.boundingBox != null && item.boundingBox.isValid) {
                        imageProcessor.cropToBoundingBox(fullBitmap, item.boundingBox, paddingFraction = 0.12f)
                    } else {
                        null
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectIndex(idx) }
                        .testTag("multi_product_row_$idx")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (itemThumb != null) {
                                Image(
                                    bitmap = itemThumb.asImageBitmap(),
                                    contentDescription = item.displayProductName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "#${idx + 1} ${item.displayProductName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = "${item.ripenessState.displayName} • $daysText",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = if (isSelected) "Activo" else "Inspeccionar",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightedProduceImage(
    bitmap: Bitmap,
    detectedItems: List<FoodAnalysis>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeItem = detectedItems.getOrNull(selectedIndex)
    val activeBox = activeItem?.boundingBox
    val hasValidActiveBox = activeBox != null && activeBox.isValid

    val animSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val animXmin by animateFloatAsState(
        targetValue = if (hasValidActiveBox) activeBox!!.xmin else 0.1f,
        animationSpec = animSpec,
        label = "res_xmin"
    )
    val animYmin by animateFloatAsState(
        targetValue = if (hasValidActiveBox) activeBox!!.ymin else 0.1f,
        animationSpec = animSpec,
        label = "res_ymin"
    )
    val animXmax by animateFloatAsState(
        targetValue = if (hasValidActiveBox) activeBox!!.xmax else 0.9f,
        animationSpec = animSpec,
        label = "res_xmax"
    )
    val animYmax by animateFloatAsState(
        targetValue = if (hasValidActiveBox) activeBox!!.ymax else 0.9f,
        animationSpec = animSpec,
        label = "res_ymax"
    )

    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val containerWidthPx = constraints.maxWidth.toFloat()
        val containerHeightPx = constraints.maxHeight.toFloat()

        val bmpW = bitmap.width.toFloat().coerceAtLeast(1f)
        val bmpH = bitmap.height.toFloat().coerceAtLeast(1f)
        val scale = maxOf(containerWidthPx / bmpW, containerHeightPx / bmpH)
        val scaledW = bmpW * scale
        val scaledH = bmpH * scale
        val offsetX = (containerWidthPx - scaledW) / 2f
        val offsetY = (containerHeightPx - scaledH) / 2f

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.cd_captured_food_photo),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            detectedItems.forEachIndexed { idx, item ->
                val box: NormalizedBoundingBox? = item.boundingBox
                if (box != null && box.isValid && idx != selectedIndex) {
                    val left = (offsetX + box.xmin * scaledW).coerceIn(0f, size.width)
                    val top = (offsetY + box.ymin * scaledH).coerceIn(0f, size.height)
                    val right = (offsetX + box.xmax * scaledW).coerceIn(left + 12f, size.width)
                    val bottom = (offsetY + box.ymax * scaledH).coerceIn(top + 12f, size.height)

                    val boxColor = when {
                        item.hasVisibleDeterioration -> Color(0xFFE53935)
                        item.ripenessState == RipenessState.RIPE -> Color(0xFF43A047)
                        item.ripenessState == RipenessState.RIPENING -> Color(0xFFFB8C00)
                        item.ripenessState == RipenessState.UNRIPE -> Color(0xFF7CB342)
                        item.ripenessState == RipenessState.OVERRIPE -> Color(0xFFF4511E)
                        else -> Color(0xFF26A69A)
                    }

                    drawRoundRect(
                        color = boxColor.copy(alpha = 0.65f),
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            if (hasValidActiveBox && activeItem != null) {
                val left = (offsetX + animXmin * scaledW).coerceIn(0f, size.width)
                val top = (offsetY + animYmin * scaledH).coerceIn(0f, size.height)
                val right = (offsetX + animXmax * scaledW).coerceIn(left + 12f, size.width)
                val bottom = (offsetY + animYmax * scaledH).coerceIn(top + 12f, size.height)

                val activeColor = when {
                    activeItem.hasVisibleDeterioration -> Color(0xFFE53935)
                    activeItem.ripenessState == RipenessState.RIPE -> Color(0xFF43A047)
                    activeItem.ripenessState == RipenessState.RIPENING -> Color(0xFFFB8C00)
                    activeItem.ripenessState == RipenessState.UNRIPE -> Color(0xFF7CB342)
                    activeItem.ripenessState == RipenessState.OVERRIPE -> Color(0xFFF4511E)
                    else -> Color(0xFF26A69A)
                }

                drawRoundRect(
                    color = activeColor.copy(alpha = 0.20f),
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
                )
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    style = Stroke(width = 3.5.dp.toPx())
                )
            }
        }

        detectedItems.forEachIndexed { idx, item ->
            val box = item.boundingBox
            if (box != null && box.isValid) {
                val isSelected = idx == selectedIndex
                val leftPx = (offsetX + box.xmin * scaledW).coerceIn(0f, containerWidthPx - 48f)
                val topPx = (offsetY + box.ymin * scaledH).coerceIn(0f, containerHeightPx - 36f)
                val rightPx = (offsetX + box.xmax * scaledW).coerceIn(leftPx + 36f, containerWidthPx)
                val bottomPx = (offsetY + box.ymax * scaledH).coerceIn(topPx + 36f, containerHeightPx)

                val boxW = with(density) { (rightPx - leftPx).toDp() }
                val boxH = with(density) { (bottomPx - topPx).toDp() }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                        .size(width = boxW, height = boxH)
                        .clickable { onSelectIndex(idx) }
                        .testTag("bounding_box_tap_target_$idx")
                )

                val badgeColor = when {
                    item.hasVisibleDeterioration -> Color(0xFFC62828)
                    item.ripenessState == RipenessState.RIPE -> Color(0xFF2E7D32)
                    item.ripenessState == RipenessState.RIPENING -> Color(0xFFEF6C00)
                    item.ripenessState == RipenessState.UNRIPE -> Color(0xFF558B2F)
                    item.ripenessState == RipenessState.OVERRIPE -> Color(0xFFD84315)
                    else -> Color(0xFF00695C)
                }

                val labelText = if (detectedItems.size > 1) {
                    "#${idx + 1} ${item.displayProductName}"
                } else {
                    "${item.displayProductName} · ${item.ripenessState.displayName}"
                }

                Surface(
                    color = if (isSelected) badgeColor else Color.Black.copy(alpha = 0.72f),
                    contentColor = Color.White,
                    shape = CircleShape,
                    shadowElevation = if (isSelected) 4.dp else 1.dp,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = leftPx.roundToInt() + with(density) { 6.dp.roundToPx() },
                                y = topPx.roundToInt() + with(density) { 6.dp.roundToPx() }
                            )
                        }
                        .clickable { onSelectIndex(idx) }
                        .testTag("bounding_box_badge_$idx")
                ) {
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MinimalSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
private fun MinimalBulletItem(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp, end = 10.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
