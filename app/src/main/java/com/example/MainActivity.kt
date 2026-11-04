package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.local.AdaptiveVisualLearner
import com.example.data.local.AndroidNetworkMonitor
import com.example.data.local.LearnedDatasetManager
import com.example.data.local.LocalVisualForecastStore
import com.example.data.local.SharedPrefsCloudConsentStore
import com.example.data.remote.FirebaseGeminiRepository
import com.example.data.remote.VisualForecastRepository
import com.example.security.attemptAutoSignIn
import com.example.security.authStateFlow
import com.example.security.configureAppCheck
import com.example.ui.components.CloudConsentDialog
import com.example.ui.navigation.FreshScanRoutes
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.AdvancedAnalysisScreen
import com.example.ui.screens.CameraScreen
import com.example.ui.screens.ConservationGuideScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ProcessingScreen
import com.example.ui.screens.ResultsScreen
import com.example.ui.screens.SignInScreen
import com.example.ui.theme.FreshScanTheme
import com.example.ui.viewmodel.AnalysisProcessState
import com.example.ui.viewmodel.FreshScanViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        // STEP 2: Initialize UI with Google Sign-In Auth Gate and Collective Cloud Sync
        setContent {
            FreshScanTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FreshScanAuthRoot()
                }
            }
        }
    }
}

@Composable
fun FreshScanAuthRoot() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var allowLocalWithoutAccount by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            val credentialManager = CredentialManager.create(context)
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = coroutineScope
            )
        }
    }

    val user = currentUser
    if (user == null && !allowLocalWithoutAccount) {
        SignInScreen(
            onAuthSuccess = {},
            onContinueLocalOnly = { allowLocalWithoutAccount = true }
        )
    } else {
        val factory = remember(context) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val appContext = context.applicationContext
                    val localForecastStore = LocalVisualForecastStore(appContext)
                    val adaptiveLearner = AdaptiveVisualLearner(
                        visualForecastStore = localForecastStore
                    )
                    val repo = FirebaseGeminiRepository(
                        networkMonitor = AndroidNetworkMonitor(appContext),
                        adaptiveLearner = adaptiveLearner
                    )
                    val consentStore = SharedPrefsCloudConsentStore(appContext)
                    val datasetManager = LearnedDatasetManager(appContext)
                    val databaseId = appContext.getString(R.string.firestore_database_id)
                    val firestore = FirebaseFirestore.getInstance(databaseId)
                    val forecastRepository = VisualForecastRepository(
                        db = firestore,
                        localStore = localForecastStore
                    )
                    return FreshScanViewModel(
                        repository = repo,
                        consentStore = consentStore,
                        localVisualForecastStore = localForecastStore,
                        adaptiveVisualLearner = adaptiveLearner,
                        learnedDatasetManager = datasetManager,
                        visualForecastRepository = forecastRepository
                    ) as T
                }
            }
        }
        val viewModel: FreshScanViewModel = viewModel(factory = factory)
        val lifecycleOwner = LocalLifecycleOwner.current

        LaunchedEffect(user?.uid, user?.email) {
            viewModel.bindAuthenticatedUser(
                userId = user?.uid,
                email = user?.email
            )
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> viewModel.resumeCloudSyncInForeground()
                    Lifecycle.Event.ON_STOP -> viewModel.pauseCloudSyncInBackground()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        FreshScanApp(viewModel = viewModel)
    }
}

@Composable
fun FreshScanApp(
    viewModel: FreshScanViewModel,
    navController: NavHostController = rememberNavController()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.transientBannerMessage) {
        val msg = uiState.transientBannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBannerMessage()
        }
    }

    // Navegar automáticamente de Processing a Results cuando el análisis concluye con éxito
    LaunchedEffect(uiState.processState) {
        if (uiState.processState is AnalysisProcessState.Success) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute == FreshScanRoutes.ROUTE_PROCESSING) {
                navController.navigate(FreshScanRoutes.ROUTE_RESULTS) {
                    popUpTo(FreshScanRoutes.ROUTE_CAMERA) { inclusive = false }
                }
            }
        }
    }

    val navigateAfterScanStart: () -> Unit = {
        if (viewModel.uiState.value.processState is AnalysisProcessState.Success) {
            navController.navigate(FreshScanRoutes.ROUTE_RESULTS) {
                popUpTo(FreshScanRoutes.ROUTE_CAMERA) { inclusive = false }
            }
        } else {
            navController.navigate(FreshScanRoutes.ROUTE_PROCESSING)
        }
    }

    if (uiState.showCloudConsentDialog) {
        CloudConsentDialog(
            onAccept = {
                viewModel.onCloudConsentAccepted(
                    onNavigateToProcessing = navigateAfterScanStart
                )
            },
            onDecline = {
                viewModel.onCloudConsentDeclined()
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { _ ->
        NavHost(
            navController = navController,
            startDestination = FreshScanRoutes.ROUTE_CAMERA
        ) {
            composable(FreshScanRoutes.ROUTE_CAMERA) {
                CameraScreen(
                    capturedBitmap = uiState.currentBitmap,
                    isCameraPermissionGranted = uiState.isCameraPermissionGranted,
                    lensFacingBack = uiState.lensFacingBack,
                    flashMode = uiState.flashMode,
                    hasFlashUnit = uiState.hasFlashUnit,
                    onPermissionResult = viewModel::updateCameraPermission,
                    onToggleLens = viewModel::toggleCameraLens,
                    onCycleFlash = viewModel::cycleFlashMode,
                    onHasFlashUnitChanged = viewModel::setHasFlashUnit,
                    onPhotoReady = { bmp ->
                        viewModel.onPhotoCapturedOrPicked(bmp)
                        if (bmp != null) {
                            viewModel.requestAutomaticAnalysis(
                                onNavigateToProcessing = navigateAfterScanStart
                            )
                        }
                    },
                    onRetakePhoto = viewModel::discardCurrentPhoto,
                    onConfirmAndAnalyze = {
                        viewModel.requestAutomaticAnalysis(
                            onNavigateToProcessing = navigateAfterScanStart
                        )
                    },
                    onNavigateBack = {
                        viewModel.discardCurrentPhoto()
                    },
                    onOpenConservationGuide = {
                        navController.navigate(FreshScanRoutes.ROUTE_CONSERVATION_GUIDE)
                    },
                    onOpenAbout = {
                        navController.navigate(FreshScanRoutes.ROUTE_ABOUT)
                    },
                    engineMode = uiState.engineMode,
                    onEngineModeChange = viewModel::setEngineMode
                )
            }

            composable(FreshScanRoutes.ROUTE_HOME) {
                HomeScreen(
                    onStartCameraScan = {
                        viewModel.discardCurrentPhoto()
                        val popped = navController.popBackStack(FreshScanRoutes.ROUTE_CAMERA, inclusive = false)
                        if (!popped) {
                            navController.navigate(FreshScanRoutes.ROUTE_CAMERA)
                        }
                    },
                    onOpenConservationGuide = {
                        navController.navigate(FreshScanRoutes.ROUTE_CONSERVATION_GUIDE)
                    },
                    onOpenAbout = {
                        navController.navigate(FreshScanRoutes.ROUTE_ABOUT)
                    }
                )
            }

            composable(FreshScanRoutes.ROUTE_PROCESSING) {
                ProcessingScreen(
                    bitmap = uiState.currentBitmap,
                    processState = uiState.processState,
                    onCancelAnalysis = viewModel::cancelOngoingAnalysis,
                    onRetryAnalysis = {
                        val currentAnalysis = uiState.currentAnalysis
                        if (currentAnalysis != null && uiState.storageConditionsDraft.hasAnyInput()) {
                            viewModel.requestAdvancedAnalysis(uiState.storageConditionsDraft)
                        } else {
                            viewModel.requestAutomaticAnalysis()
                        }
                    },
                    onReturnToCamera = {
                        viewModel.discardCurrentPhoto()
                        val popped = navController.popBackStack(FreshScanRoutes.ROUTE_CAMERA, inclusive = false)
                        if (!popped) {
                            navController.navigate(FreshScanRoutes.ROUTE_CAMERA)
                        }
                    },
                    onAnalysisCompleted = {
                        val currentRoute = navController.currentBackStackEntry?.destination?.route
                        if (currentRoute == FreshScanRoutes.ROUTE_PROCESSING) {
                            navController.navigate(FreshScanRoutes.ROUTE_RESULTS) {
                                popUpTo(FreshScanRoutes.ROUTE_CAMERA) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }

            composable(FreshScanRoutes.ROUTE_RESULTS) {
                val analysis = uiState.currentAnalysis
                if (analysis != null) {
                    ResultsScreen(
                        bitmap = uiState.currentBitmap,
                        analysis = analysis,
                        hasPreviousAutomaticResult = uiState.previousAutomaticAnalysis != null,
                        onImproveAnalysisClick = {
                            navController.navigate(FreshScanRoutes.ROUTE_ADVANCED_ANALYSIS)
                        },
                        onRestorePreviousResult = viewModel::restorePreviousAutomaticAnalysis,
                        onCorrectProduct = { newName, newRipeness, reanalyze ->
                            viewModel.correctRecognizedProduct(
                                newProductName = newName,
                                newRipenessState = newRipeness,
                                reanalyzeWithGemini = reanalyze,
                                targetProductIndex = uiState.selectedProductIndex
                            )
                        },
                        onNewScanClick = {
                            viewModel.discardCurrentPhoto()
                            val popped = navController.popBackStack(FreshScanRoutes.ROUTE_CAMERA, inclusive = false)
                            if (!popped) {
                                navController.navigate(FreshScanRoutes.ROUTE_CAMERA)
                            }
                        },
                        selectedProductIndex = uiState.selectedProductIndex,
                        onSelectProductIndex = viewModel::selectProduct,
                        isGeminiRefiningInBackground = uiState.isGeminiRefiningInBackground
                    )
                } else {
                    LaunchedEffect(Unit) {
                        val popped = navController.popBackStack(FreshScanRoutes.ROUTE_CAMERA, inclusive = false)
                        if (!popped) {
                            navController.navigate(FreshScanRoutes.ROUTE_CAMERA)
                        }
                    }
                }
            }

            composable(FreshScanRoutes.ROUTE_ADVANCED_ANALYSIS) {
                val selectedProduct = uiState.selectedProduct ?: uiState.currentAnalysis
                AdvancedAnalysisScreen(
                    productName = selectedProduct?.displayProductName ?: "Alimento",
                    initialConditions = uiState.storageConditionsDraft,
                    onSubmitAdvancedAnalysis = { conditions ->
                        viewModel.requestAdvancedAnalysis(
                            conditions = conditions,
                            onNavigateToProcessing = navigateAfterScanStart
                        )
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(FreshScanRoutes.ROUTE_CONSERVATION_GUIDE) {
                ConservationGuideScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(FreshScanRoutes.ROUTE_ABOUT) {
                AboutScreen(
                    hasCloudConsent = uiState.hasCloudConsent,
                    onRevokeCloudConsent = viewModel::revokeCloudConsent,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
