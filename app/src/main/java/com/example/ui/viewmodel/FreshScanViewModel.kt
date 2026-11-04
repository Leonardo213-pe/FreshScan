package com.example.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.estimator.ShelfLifeEstimator
import com.example.data.local.AdaptiveVisualLearner
import com.example.data.local.CloudConsentStore
import com.example.data.local.FlashModeOption
import com.example.data.local.ImageProcessor
import com.example.data.local.LearnedDatasetManager
import com.example.data.local.LocalVisualForecastStore
import com.example.data.remote.FirebaseGeminiRepository
import com.example.data.remote.VisualForecastRepository
import com.example.domain.model.AnalysisEngineMode
import com.example.domain.model.FoodAnalysis
import com.example.domain.model.FoodProduct
import com.example.domain.model.RipenessState
import com.example.domain.model.StorageConditions
import com.example.domain.model.VisualForecastRule
import com.example.domain.repository.FoodAnalysisRepository
import com.example.domain.usecase.AnalyzeFoodUseCase
import com.example.domain.usecase.ImproveAnalysisUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

sealed class AnalysisProcessState {
    data object Idle : AnalysisProcessState()
    data object Loading : AnalysisProcessState()
    data class Success(val analysis: FoodAnalysis) : AnalysisProcessState()
    data class Error(val message: String) : AnalysisProcessState()
}

data class FreshScanUiState(
    val currentBitmap: Bitmap? = null,
    val isCameraPermissionGranted: Boolean = false,
    val lensFacingBack: Boolean = true,
    val flashMode: FlashModeOption = FlashModeOption.OFF,
    val hasFlashUnit: Boolean = false,
    val hasCloudConsent: Boolean = false,
    val showCloudConsentDialog: Boolean = false,
    val engineMode: AnalysisEngineMode = AnalysisEngineMode.GEMINI_EVOLUTIONARY,
    val processState: AnalysisProcessState = AnalysisProcessState.Idle,
    val currentAnalysis: FoodAnalysis? = null,
    val selectedProductIndex: Int = 0,
    val previousAutomaticAnalysis: FoodAnalysis? = null,
    val isGeminiRefiningInBackground: Boolean = false,
    val storageConditionsDraft: StorageConditions = StorageConditions(),
    val savedLearnedSamplesCount: Int = 0,
    val lastSavedSampleSessionId: String? = null,
    val syncedForecastRulesCount: Int = 0,
    val isCloudSyncActive: Boolean = false,
    val connectedUserEmail: String? = null,
    val lastCloudSyncStatus: String? = null,
    val transientBannerMessage: String? = null
) {
    val selectedProduct: FoodAnalysis?
        get() {
            val root = currentAnalysis ?: return null
            val list = root.allProducts
            return list.getOrElse(selectedProductIndex.coerceIn(0, (list.size - 1).coerceAtLeast(0))) { root }
        }
}

class FreshScanViewModel(
    private val repository: FoodAnalysisRepository = FirebaseGeminiRepository(),
    private val consentStore: CloudConsentStore? = null,
    private val shelfLifeEstimator: ShelfLifeEstimator = ShelfLifeEstimator(),
    private val localVisualForecastStore: LocalVisualForecastStore = LocalVisualForecastStore(),
    private val adaptiveVisualLearner: AdaptiveVisualLearner = AdaptiveVisualLearner(
        shelfLifeEstimator = shelfLifeEstimator,
        visualForecastStore = localVisualForecastStore
    ),
    private val learnedDatasetManager: LearnedDatasetManager? = null,
    private val visualForecastRepository: VisualForecastRepository? = null,
    private val imageProcessor: ImageProcessor = ImageProcessor()
) : ViewModel() {

    private val analyzeFoodUseCase = AnalyzeFoodUseCase(repository)
    private val improveAnalysisUseCase = ImproveAnalysisUseCase(repository)

    private val _uiState = MutableStateFlow(
        FreshScanUiState(
            hasCloudConsent = consentStore?.hasAcceptedCloudProcessing() ?: true,
            savedLearnedSamplesCount = learnedDatasetManager?.getSavedSampleCount() ?: 0,
            syncedForecastRulesCount = localVisualForecastStore.getRulesCount()
        )
    )
    val uiState: StateFlow<FreshScanUiState> = _uiState.asStateFlow()

    private var activeAnalysisJob: Job? = null
    private var cloudSyncObserverJob: Job? = null
    private var activeSyncedUserId: String? = null
    private var activeSyncedUserEmail: String? = null
    private val isRequestInFlight = AtomicBoolean(false)
    private var pendingAdvancedRequest: Boolean = false

    init {
        repository.warmUp()
    }

    fun bindAuthenticatedUser(userId: String?, email: String?) {
        activeSyncedUserEmail = email
        _uiState.update {
            it.copy(
                connectedUserEmail = email,
                isCloudSyncActive = !userId.isNullOrBlank()
            )
        }
        if (userId.isNullOrBlank() || visualForecastRepository == null) {
            cloudSyncObserverJob?.cancel()
            cloudSyncObserverJob = null
            activeSyncedUserId = null
            return
        }
        if (activeSyncedUserId == userId && cloudSyncObserverJob?.isActive == true) {
            return
        }
        activeSyncedUserId = userId
        startRealtimeForecastListener()
    }

    fun pauseCloudSyncInBackground() {
        cloudSyncObserverJob?.cancel()
        cloudSyncObserverJob = null
    }

    fun resumeCloudSyncInForeground() {
        val userId = activeSyncedUserId
        if (!userId.isNullOrBlank() && visualForecastRepository != null && cloudSyncObserverJob?.isActive != true) {
            startRealtimeForecastListener()
        }
    }

    private fun startRealtimeForecastListener() {
        val forecastRepo = visualForecastRepository ?: return
        cloudSyncObserverJob?.cancel()
        cloudSyncObserverJob = viewModelScope.launch {
            forecastRepo.observeCollectiveForecasts().collectLatest { rules ->
                _uiState.update {
                    it.copy(
                        syncedForecastRulesCount = rules.size,
                        isCloudSyncActive = true,
                        lastCloudSyncStatus = "Memoria evolutiva sincronizada (${rules.size} patrones)"
                    )
                }
            }
        }
    }

    fun syncForecastsFromCloudNow() {
        val forecastRepo = visualForecastRepository ?: return
        viewModelScope.launch {
            forecastRepo.syncCollectiveForecasts()
                .onSuccess {
                    val total = localVisualForecastStore.getRulesCount()
                    _uiState.update {
                        it.copy(
                            syncedForecastRulesCount = total,
                            lastCloudSyncStatus = "Sincronización completada ($total patrones)"
                        )
                    }
                }
        }
    }

    fun updateCameraPermission(granted: Boolean) {
        _uiState.update { it.copy(isCameraPermissionGranted = granted) }
    }

    fun toggleCameraLens() {
        _uiState.update { it.copy(lensFacingBack = !it.lensFacingBack) }
    }

    fun cycleFlashMode() {
        _uiState.update { it.copy(flashMode = it.flashMode.next()) }
    }

    fun setHasFlashUnit(hasFlash: Boolean) {
        _uiState.update { it.copy(hasFlashUnit = hasFlash) }
    }

    fun setEngineMode(mode: AnalysisEngineMode) {
        _uiState.update { it.copy(engineMode = mode) }
    }

    fun selectProduct(index: Int) {
        val current = _uiState.value.currentAnalysis ?: return
        val items = current.allProducts
        val safeIdx = index.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        _uiState.update { it.copy(selectedProductIndex = safeIdx) }
    }

    fun onPhotoCapturedOrPicked(bitmap: Bitmap?) {
        cancelOngoingAnalysis()
        _uiState.update {
            it.copy(
                currentBitmap = bitmap,
                processState = AnalysisProcessState.Idle,
                currentAnalysis = null,
                selectedProductIndex = 0,
                previousAutomaticAnalysis = null,
                isGeminiRefiningInBackground = false,
                transientBannerMessage = null
            )
        }
    }

    fun discardCurrentPhoto() {
        cancelOngoingAnalysis()
        _uiState.update {
            it.copy(
                currentBitmap = null,
                processState = AnalysisProcessState.Idle,
                currentAnalysis = null,
                selectedProductIndex = 0,
                previousAutomaticAnalysis = null,
                isGeminiRefiningInBackground = false
            )
        }
    }

    fun requestAutomaticAnalysis(onNavigateToProcessing: () -> Unit = {}): Boolean {
        val state = _uiState.value
        val bitmap = state.currentBitmap ?: return false

        if (state.engineMode == AnalysisEngineMode.LEARNED_MEMORY_OFFLINE) {
            executeLearnedMemoryOfflineAnalysis(bitmap, onNavigateToProcessing)
            return true
        }

        if (!state.hasCloudConsent) {
            pendingAdvancedRequest = false
            _uiState.update { it.copy(showCloudConsentDialog = true) }
            return false
        }

        executeGeminiEvolutionaryAnalysis(bitmap, onNavigateToProcessing)
        return true
    }

    fun requestAdvancedAnalysis(
        conditions: StorageConditions,
        onNavigateToProcessing: () -> Unit = {}
    ): Boolean {
        val state = _uiState.value
        val bitmap = state.currentBitmap ?: return false
        val baseAnalysis = state.currentAnalysis ?: return false

        _uiState.update { it.copy(storageConditionsDraft = conditions) }

        if (!state.hasCloudConsent && state.engineMode != AnalysisEngineMode.LEARNED_MEMORY_OFFLINE) {
            pendingAdvancedRequest = true
            _uiState.update { it.copy(showCloudConsentDialog = true) }
            return false
        }

        executeAdvancedAnalysis(bitmap, baseAnalysis, conditions, onNavigateToProcessing)
        return true
    }

    fun onCloudConsentAccepted(onNavigateToProcessing: () -> Unit = {}) {
        consentStore?.setAcceptedCloudProcessing(true)
        _uiState.update {
            it.copy(
                hasCloudConsent = true,
                showCloudConsentDialog = false
            )
        }
        val state = _uiState.value
        val bitmap = state.currentBitmap ?: return
        if (pendingAdvancedRequest && state.currentAnalysis != null) {
            pendingAdvancedRequest = false
            executeAdvancedAnalysis(
                bitmap = bitmap,
                baseAnalysis = state.currentAnalysis,
                conditions = state.storageConditionsDraft,
                onProceedToProcessing = onNavigateToProcessing
            )
        } else {
            requestAutomaticAnalysis(onNavigateToProcessing)
        }
    }

    fun onCloudConsentDeclined() {
        pendingAdvancedRequest = false
        _uiState.update {
            it.copy(
                showCloudConsentDialog = false,
                transientBannerMessage = "Se requiere autorización para el análisis con Gemini en la nube."
            )
        }
    }

    fun revokeCloudConsent() {
        consentStore?.setAcceptedCloudProcessing(false)
        _uiState.update {
            it.copy(
                hasCloudConsent = false,
                transientBannerMessage = "Consentimiento de procesamiento en la nube revocado."
            )
        }
    }

    private fun executeLearnedMemoryOfflineAnalysis(
        bitmap: Bitmap,
        onProceedToProcessing: () -> Unit
    ) {
        if (!isRequestInFlight.compareAndSet(false, true)) return
        onProceedToProcessing()
        activeAnalysisJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(processState = AnalysisProcessState.Loading) }
                val learnedAnalysis = adaptiveVisualLearner.evaluateFromLearnedMemory(
                    bitmap = bitmap
                )
                if (learnedAnalysis.isReliableIdentification) {
                    recordAndSyncLearnedForecast(bitmap, learnedAnalysis)
                }
                _uiState.update {
                    it.copy(
                        currentAnalysis = learnedAnalysis,
                        selectedProductIndex = 0,
                        processState = AnalysisProcessState.Success(learnedAnalysis),
                        isGeminiRefiningInBackground = false
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        processState = AnalysisProcessState.Error(
                            e.localizedMessage ?: "Error al consultar la memoria aprendida."
                        )
                    )
                }
            } finally {
                isRequestInFlight.set(false)
            }
        }
    }

    private fun executeGeminiEvolutionaryAnalysis(
        bitmap: Bitmap,
        onProceedToProcessing: () -> Unit
    ) {
        if (!isRequestInFlight.compareAndSet(false, true)) return
        onProceedToProcessing()
        activeAnalysisJob = viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        processState = AnalysisProcessState.Loading,
                        isGeminiRefiningInBackground = false
                    )
                }
                val result = analyzeFoodUseCase(bitmap = bitmap)
                result.onSuccess { analysis ->
                    recordAndSyncLearnedForecast(bitmap, analysis)
                    _uiState.update {
                        it.copy(
                            currentAnalysis = analysis,
                            selectedProductIndex = 0,
                            processState = AnalysisProcessState.Success(analysis),
                            isGeminiRefiningInBackground = false
                        )
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            processState = AnalysisProcessState.Error(
                                err.localizedMessage ?: "Error al analizar la imagen con Gemini."
                            ),
                            isGeminiRefiningInBackground = false
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        isGeminiRefiningInBackground = false,
                        processState = AnalysisProcessState.Error(
                            e.localizedMessage ?: "No se pudo completar el análisis."
                        )
                    )
                }
            } finally {
                isRequestInFlight.set(false)
            }
        }
    }

    private fun executeAdvancedAnalysis(
        bitmap: Bitmap,
        baseAnalysis: FoodAnalysis,
        conditions: StorageConditions,
        onProceedToProcessing: () -> Unit
    ) {
        if (!isRequestInFlight.compareAndSet(false, true)) return
        onProceedToProcessing()

        val selectedIdx = _uiState.value.selectedProductIndex
        val allProducts = baseAnalysis.allProducts
        val targetItem = allProducts.getOrElse(selectedIdx) { baseAnalysis }

        activeAnalysisJob = viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        previousAutomaticAnalysis = it.previousAutomaticAnalysis ?: baseAnalysis,
                        processState = AnalysisProcessState.Loading
                    )
                }

                val targetBitmap = if (allProducts.size > 1 && targetItem.boundingBox != null && targetItem.boundingBox.isValid) {
                    imageProcessor.cropToBoundingBox(bitmap, targetItem.boundingBox, paddingFraction = 0.16f)
                } else {
                    bitmap
                }

                val result = improveAnalysisUseCase(
                    bitmap = targetBitmap,
                    previousAnalysis = targetItem,
                    storageConditions = conditions
                )

                result.onSuccess { improvedItem ->
                    val preservedItem = improvedItem.copy(
                        boundingBox = targetItem.boundingBox ?: improvedItem.boundingBox,
                        isAdvancedAnalysis = true,
                        appliedStorageConditions = conditions
                    )
                    val updatedRoot = if (allProducts.size > 1) {
                        replaceProductAtIndex(baseAnalysis, selectedIdx, preservedItem).copy(
                            isAdvancedAnalysis = true,
                            appliedStorageConditions = conditions
                        )
                    } else {
                        preservedItem
                    }
                    recordAndSyncLearnedForecast(targetBitmap, preservedItem)
                    _uiState.update {
                        it.copy(
                            currentAnalysis = updatedRoot,
                            processState = AnalysisProcessState.Success(updatedRoot)
                        )
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            processState = AnalysisProcessState.Error(
                                err.localizedMessage ?: "Error al recalcular la estimación avanzada."
                            )
                        )
                    }
                }
            } finally {
                isRequestInFlight.set(false)
            }
        }
    }

    fun correctRecognizedProduct(
        newProductName: String,
        newRipenessState: RipenessState? = null,
        reanalyzeWithGemini: Boolean = false,
        targetProductIndex: Int = _uiState.value.selectedProductIndex
    ) {
        val cleaned = newProductName.trim()
        if (cleaned.isEmpty()) return

        val state = _uiState.value
        val rootAnalysis = state.currentAnalysis ?: return
        val allItems = rootAnalysis.allProducts
        val safeIdx = targetProductIndex.coerceIn(0, (allItems.size - 1).coerceAtLeast(0))
        val targetItem = allItems.getOrElse(safeIdx) { rootAnalysis }

        val effectiveRipeness = newRipenessState ?: if (targetItem.ripenessState == RipenessState.UNDETERMINED) {
            RipenessState.RIPE
        } else {
            targetItem.ripenessState
        }

        val matchedProduct = FoodProduct.findMatchingProduct(cleaned)
        val recalculatedShelfLife = shelfLifeEstimator.estimate(
            matchedProduct = matchedProduct,
            isReliableIdentification = true,
            multipleOrAmbiguousItems = false,
            ripenessState = effectiveRipeness,
            hasVisibleDeterioration = targetItem.hasVisibleDeterioration,
            deteriorationSigns = targetItem.deteriorationSigns,
            geminiShelfLifeMin = targetItem.shelfLifeEstimate.remainingShelfLifeDaysMin,
            geminiShelfLifeMax = targetItem.shelfLifeEstimate.remainingShelfLifeDaysMax,
            geminiRipeningDaysMin = targetItem.shelfLifeEstimate.daysToRipenMin,
            geminiRipeningDaysMax = targetItem.shelfLifeEstimate.daysToRipenMax,
            geminiLimitations = targetItem.shelfLifeEstimate.predictionLimitations,
            storageConditions = targetItem.appliedStorageConditions
        )

        val updatedTarget = targetItem.copy(
            rawProductName = cleaned,
            manuallyCorrectedName = cleaned,
            matchedCatalogProduct = matchedProduct,
            isReliableIdentification = true,
            ripenessState = effectiveRipeness,
            shelfLifeEstimate = recalculatedShelfLife
        )

        val updatedRoot = replaceProductAtIndex(rootAnalysis, safeIdx, updatedTarget)
        _uiState.update {
            it.copy(
                currentAnalysis = updatedRoot,
                selectedProductIndex = safeIdx,
                processState = AnalysisProcessState.Success(updatedRoot),
                transientBannerMessage = "Aprendizaje actualizado: $cleaned (${effectiveRipeness.displayName})"
            )
        }

        val fullBitmap = state.currentBitmap
        if (fullBitmap != null) {
            val cropBmp = if (allItems.size > 1 && updatedTarget.boundingBox != null && updatedTarget.boundingBox.isValid) {
                imageProcessor.cropToBoundingBox(fullBitmap, updatedTarget.boundingBox, paddingFraction = 0.16f)
            } else {
                fullBitmap
            }
            recordAndSyncLearnedForecast(cropBmp, updatedTarget)
        }

        if (reanalyzeWithGemini && fullBitmap != null && state.hasCloudConsent) {
            viewModelScope.launch {
                _uiState.update { it.copy(isGeminiRefiningInBackground = true) }
                val cropBmp = if (allItems.size > 1 && updatedTarget.boundingBox != null && updatedTarget.boundingBox.isValid) {
                    imageProcessor.cropToBoundingBox(fullBitmap, updatedTarget.boundingBox, paddingFraction = 0.16f)
                } else {
                    fullBitmap
                }
                val cloudReanalysis = repository.analyzeFood(
                    bitmap = cropBmp,
                    storageConditions = updatedTarget.appliedStorageConditions,
                    manualProductOverride = cleaned,
                    regionBox = null
                )
                cloudReanalysis.onSuccess { refinedItem ->
                    val resolvedRipeness = newRipenessState ?: refinedItem.ripenessState
                    val recalcRefinedShelf = shelfLifeEstimator.estimate(
                        matchedProduct = matchedProduct ?: refinedItem.matchedCatalogProduct,
                        isReliableIdentification = true,
                        multipleOrAmbiguousItems = false,
                        ripenessState = resolvedRipeness,
                        hasVisibleDeterioration = refinedItem.hasVisibleDeterioration,
                        deteriorationSigns = refinedItem.deteriorationSigns,
                        geminiShelfLifeMin = refinedItem.shelfLifeEstimate.remainingShelfLifeDaysMin,
                        geminiShelfLifeMax = refinedItem.shelfLifeEstimate.remainingShelfLifeDaysMax,
                        geminiRipeningDaysMin = refinedItem.shelfLifeEstimate.daysToRipenMin,
                        geminiRipeningDaysMax = refinedItem.shelfLifeEstimate.daysToRipenMax,
                        geminiLimitations = refinedItem.shelfLifeEstimate.predictionLimitations,
                        storageConditions = updatedTarget.appliedStorageConditions
                    )
                    val mergedItem = refinedItem.copy(
                        rawProductName = cleaned,
                        manuallyCorrectedName = cleaned,
                        matchedCatalogProduct = matchedProduct ?: refinedItem.matchedCatalogProduct,
                        ripenessState = resolvedRipeness,
                        shelfLifeEstimate = recalcRefinedShelf,
                        boundingBox = updatedTarget.boundingBox ?: refinedItem.boundingBox,
                        detectedItems = emptyList()
                    )
                    val currentRoot = _uiState.value.currentAnalysis ?: updatedRoot
                    val finalRoot = replaceProductAtIndex(currentRoot, safeIdx, mergedItem)
                    recordAndSyncLearnedForecast(cropBmp, mergedItem)
                    _uiState.update {
                        it.copy(
                            currentAnalysis = finalRoot,
                            processState = AnalysisProcessState.Success(finalRoot),
                            isGeminiRefiningInBackground = false
                        )
                    }
                }.onFailure {
                    _uiState.update { it.copy(isGeminiRefiningInBackground = false) }
                }
            }
        }
    }

    private fun replaceProductAtIndex(
        rootAnalysis: FoodAnalysis,
        index: Int,
        updatedItem: FoodAnalysis
    ): FoodAnalysis {
        val currentList = rootAnalysis.allProducts.toMutableList()
        if (index !in currentList.indices) return updatedItem
        currentList[index] = updatedItem.copy(detectedItems = emptyList())

        return if (currentList.size == 1) {
            updatedItem.copy(detectedItems = listOf(updatedItem.copy(detectedItems = emptyList())))
        } else {
            val primary = if (index == 0) updatedItem else rootAnalysis
            primary.copy(
                multipleOrAmbiguousItems = true,
                detectedItems = currentList
            )
        }
    }

    private fun recordAndSyncLearnedForecast(
        bitmap: Bitmap,
        analysis: FoodAnalysis
    ) {
        if (!analysis.isReliableIdentification) return
        viewModelScope.launch {
            try {
                val itemsToRecord = analysis.allProducts.filter { it.isReliableIdentification }
                for (item in itemsToRecord) {
                    val itemBitmap = if (itemsToRecord.size > 1 && item.boundingBox != null && item.boundingBox.isValid) {
                        imageProcessor.cropToBoundingBox(bitmap, item.boundingBox, paddingFraction = 0.12f)
                    } else {
                        bitmap
                    }
                    val features = adaptiveVisualLearner.extractVisualFeatureVector(itemBitmap)
                    adaptiveVisualLearner.learnFromValidatedGeminiResult(itemBitmap, item)
                    val rule = VisualForecastRule.fromValidatedAnalysis(
                        userId = "local_device",
                        featureVector = features,
                        analysis = item
                    )
                    localVisualForecastStore.upsertLocalRule(rule)
                    visualForecastRepository?.publishOrConfirmValidatedForecast(features, item)
                }

                learnedDatasetManager?.saveLabeledSample(bitmap, analysis)?.onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            savedLearnedSamplesCount = learnedDatasetManager.getSavedSampleCount(),
                            lastSavedSampleSessionId = analysis.sessionId
                        )
                    }
                }

                _uiState.update {
                    it.copy(syncedForecastRulesCount = localVisualForecastStore.getRulesCount())
                }
            } catch (_: Exception) {
            }
        }
    }

    fun restorePreviousAutomaticAnalysis() {
        val previous = _uiState.value.previousAutomaticAnalysis ?: return
        _uiState.update {
            it.copy(
                currentAnalysis = previous,
                previousAutomaticAnalysis = null,
                processState = AnalysisProcessState.Success(previous)
            )
        }
    }

    fun updateStorageDraft(conditions: StorageConditions) {
        _uiState.update { it.copy(storageConditionsDraft = conditions) }
    }

    fun clearBannerMessage() {
        _uiState.update { it.copy(transientBannerMessage = null) }
    }

    fun cancelOngoingAnalysis() {
        activeAnalysisJob?.cancel()
        activeAnalysisJob = null
        isRequestInFlight.set(false)
        _uiState.update {
            it.copy(
                isGeminiRefiningInBackground = false,
                processState = if (it.processState is AnalysisProcessState.Loading) {
                    AnalysisProcessState.Idle
                } else {
                    it.processState
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        cancelOngoingAnalysis()
        cloudSyncObserverJob?.cancel()
    }

    companion object {
        fun provideFactory(
            repository: FoodAnalysisRepository,
            consentStore: CloudConsentStore,
            learnedDatasetManager: LearnedDatasetManager? = null,
            localVisualForecastStore: LocalVisualForecastStore = LocalVisualForecastStore(),
            visualForecastRepository: VisualForecastRepository? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val estimator = ShelfLifeEstimator()
                val learner = AdaptiveVisualLearner(
                    shelfLifeEstimator = estimator,
                    visualForecastStore = localVisualForecastStore
                )
                return FreshScanViewModel(
                    repository = repository,
                    consentStore = consentStore,
                    shelfLifeEstimator = estimator,
                    localVisualForecastStore = localVisualForecastStore,
                    adaptiveVisualLearner = learner,
                    learnedDatasetManager = learnedDatasetManager,
                    visualForecastRepository = visualForecastRepository
                ) as T
            }
        }
    }
}
