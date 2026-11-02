package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.FutureStoragePlan
import com.example.domain.model.PreparationState
import com.example.domain.model.StorageConditions
import com.example.domain.model.StorageEnvironment
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdvancedAnalysisScreen(
    productName: String,
    initialConditions: StorageConditions,
    onSubmitAdvancedAnalysis: (StorageConditions) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onNavigateBack()
    }

    var environment by rememberSaveable { mutableStateOf(initialConditions.storageEnvironment) }
    var temperature by rememberSaveable {
        mutableFloatStateOf(initialConditions.approximateTemperatureCelsius ?: 20f)
    }
    var daysStoredText by rememberSaveable {
        mutableStateOf(initialConditions.daysAlreadyStored?.toString() ?: "0")
    }
    var preparationState by rememberSaveable { mutableStateOf(initialConditions.preparationState) }
    var futurePlan by rememberSaveable { mutableStateOf(initialConditions.futureStoragePlan) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.advanced_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("advanced_back_button")
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
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = productName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.advanced_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.advanced_storage_env_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StorageEnvironment.entries.forEach { env ->
                            FilterChip(
                                selected = environment == env,
                                onClick = {
                                    environment = env
                                    if (env == StorageEnvironment.REFRIGERATED && temperature > 10f) {
                                        temperature = 4f
                                    } else if (env == StorageEnvironment.ROOM_TEMPERATURE && temperature < 12f) {
                                        temperature = 21f
                                    }
                                },
                                label = { Text(env.displayName) },
                                modifier = Modifier.testTag("env_chip_${env.name}")
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${stringResource(R.string.advanced_temperature_label)}: ${temperature.roundToInt()} °C",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = temperature,
                        onValueChange = { temperature = it },
                        valueRange = 0f..38f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("temperature_slider")
                    )
                }

                OutlinedTextField(
                    value = daysStoredText,
                    onValueChange = { value ->
                        if (value.length <= 2 && value.all { it.isDigit() }) {
                            daysStoredText = value
                        }
                    },
                    label = { Text(stringResource(R.string.advanced_days_stored_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("days_stored_input")
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.advanced_prep_state_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PreparationState.entries.forEach { prep ->
                            FilterChip(
                                selected = preparationState == prep,
                                onClick = { preparationState = prep },
                                label = { Text(prep.displayName) },
                                modifier = Modifier.testTag("prep_chip_${prep.name}")
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.advanced_future_plan_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FutureStoragePlan.entries.forEach { plan ->
                            FilterChip(
                                selected = futurePlan == plan,
                                onClick = { futurePlan = plan },
                                label = { Text(plan.displayName) },
                                modifier = Modifier.testTag("plan_chip_${plan.name}")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        onSubmitAdvancedAnalysis(
                            StorageConditions(
                                approximateTemperatureCelsius = temperature,
                                storageEnvironment = environment,
                                daysAlreadyStored = daysStoredText.toIntOrNull() ?: 0,
                                preparationState = preparationState,
                                futureStoragePlan = futurePlan
                            )
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("submit_advanced_analysis_button")
                ) {
                    Text(
                        text = stringResource(R.string.advanced_recalculate_button),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
