package com.example.leanangletracker

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface as ComposeSurface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.leanangletracker.ui.intro.IntroScreen
import com.example.leanangletracker.ui.intro.IntroStage
import com.example.leanangletracker.ui.navigation.AppRoute
import com.example.leanangletracker.ui.navigation.ScreenDirection
import com.example.leanangletracker.ui.premium.PremiumScreen
import com.example.leanangletracker.ui.settings.SettingsScreen
import com.example.leanangletracker.ui.theme.LeanAngleTrackerTheme
import com.example.leanangletracker.ui.tracking.LeanAngleScreen
import com.example.leanangletracker.ui.tracking.RideHistoryScreen
import com.example.leanangletracker.ui.tracking.RideDetailScreen
import com.example.leanangletracker.ui.tracking.TrackingPermissionDialog
import com.example.leanangletracker.ui.calibration.CalibrationScreen
import kotlinx.coroutines.delay
import androidx.core.content.ContextCompat
import com.example.leanangletracker.billing.PremiumBillingManager
import com.example.leanangletracker.map.OpenStreetMapConfig

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var premiumBillingManager: PremiumBillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        OpenStreetMapConfig.initialize(this)

        premiumBillingManager = PremiumBillingManager(this) { entitlements ->
            viewModel.setPremiumEntitlements(
                isAutomationPackPurchased = entitlements.isAutomationPackPurchased,
                isPremiumSubscribed = entitlements.isPremiumSubscribed
            )
        }
        premiumBillingManager.start()

        enableEdgeToEdge()
        setContent {
            LeanAngleTrackerTheme {
                ComposeSurface(modifier = Modifier.fillMaxSize()) {
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    val premiumBillingState by premiumBillingManager.state.collectAsStateWithLifecycle()
                    var routeUiState by rememberSaveable(stateSaver = RouteUiState.Saver) {
                        mutableStateOf(RouteUiState())
                    }
                    var showTrackingPermissionDialog by rememberSaveable { mutableStateOf(false) }

                    val permissionsLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) {
                        val locationGranted = hasLocationPermission()
                        viewModel.onLocationPermissionResult(locationGranted)
                        if (locationGranted) viewModel.startTracking()
                    }
                    val gpxImportLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.GetContent()
                    ) { uri ->
                        uri?.let(viewModel::importGpx)
                    }

                    if (showTrackingPermissionDialog) {
                        TrackingPermissionDialog(
                            onConfirm = {
                                showTrackingPermissionDialog = false
                                val missingPermissions = missingTrackingPermissions()
                                if (missingPermissions.isEmpty()) {
                                    viewModel.onLocationPermissionResult(true)
                                    viewModel.startTracking()
                                } else {
                                    permissionsLauncher.launch(missingPermissions)
                                }
                            },
                            onDismiss = { showTrackingPermissionDialog = false }
                        )
                    }

                    // Handle Foreground Service for tracking
                    LaunchedEffect(
                        state.tracking.trackingStarted,
                        state.tracking.trackLengthKm,
                        state.tracking.elapsedTimeMs / 1000
                    ) {
                        val intent = Intent(this@MainActivity, TrackingService::class.java)
                        if (state.tracking.trackingStarted) {
                            intent.putExtra(TrackingService.EXTRA_DISTANCE, state.tracking.trackLengthKm)
                            intent.putExtra(TrackingService.EXTRA_TIME, state.tracking.elapsedTimeMs)
                            ContextCompat.startForegroundService(this@MainActivity, intent)
                        } else {
                            stopService(intent)
                        }
                    }

                    // Auto-open last saved ride and handle back stack requirements
                    LaunchedEffect(state.lastSavedRideId) {
                        state.lastSavedRideId?.let { id ->
                            viewModel.loadFullSession(id)
                            routeUiState = routeUiState.copy(
                                showHistory = true,
                                selectedRideId = id
                            )
                        }
                    }

                    LaunchedEffect(routeUiState.introStage) {
                        if (routeUiState.introStage != IntroStage.LOADING) return@LaunchedEffect
                        delay(800)
                        routeUiState = routeUiState.copy(introStage = IntroStage.LEGAL)
                    }

                    val route = resolveRoute(
                        introStage = routeUiState.introStage,
                        showSettings = routeUiState.showSettings,
                        showPremium = routeUiState.showPremium,
                        showHistory = routeUiState.showHistory,
                        selectedRideId = routeUiState.selectedRideId,
                        isCalibrated = state.calibration.isCalibrated,
                        calibrationCompletionPending = state.calibration.completionPending
                    )

                    // Keep screen on while on the tracking screen
                    LaunchedEffect(route) {
                        if (route is AppRoute.Tracking) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }

                    BackHandler(enabled = route is AppRoute.Settings || route is AppRoute.Premium || route is AppRoute.TrackReview || route is AppRoute.RideDetail || route is AppRoute.Calibration) {
                        when (route) {
                            AppRoute.Settings -> routeUiState = routeUiState.copy(showSettings = false)
                            AppRoute.Premium -> routeUiState = routeUiState.copy(showPremium = false)
                            AppRoute.TrackReview -> routeUiState = routeUiState.copy(showHistory = false)
                            is AppRoute.RideDetail -> {
                                routeUiState = routeUiState.copy(selectedRideId = null, showHistory = true)
                            }
                            AppRoute.Calibration -> {
                                if (state.calibration.isCalibrated) {
                                    // Normally handled by resolveRoute
                                }
                            }
                            else -> Unit
                        }
                    }

                    AnimatedContent(
                        targetState = route,
                        transitionSpec = {
                            val forward = targetState.index() > initialState.index()
                            val effectiveDirection = if (forward) {
                                targetState.screen.screenEntryDirection
                            } else {
                                initialState.screen.screenEntryDirection
                            }

                            if (
                                initialState is AppRoute.Calibration &&
                                targetState is AppRoute.Tracking
                            ) {
                                fadeIn(animationSpec = tween(durationMillis = 500, delayMillis = 100)) togetherWith
                                    fadeOut(animationSpec = tween(durationMillis = 300))
                            } else if (effectiveDirection == ScreenDirection.HORIZONTAL) {
                                slideInHorizontally(
                                    animationSpec = tween(300),
                                    initialOffsetX = { fullWidth -> if (forward) fullWidth else -fullWidth }
                                ) togetherWith slideOutHorizontally(
                                    animationSpec = tween(300),
                                    targetOffsetX = { fullWidth -> if (forward) -fullWidth else fullWidth }
                                )
                            } else {
                                slideInVertically(
                                    animationSpec = tween(300),
                                    initialOffsetY = { fullHeight -> if (forward) -fullHeight else fullHeight }
                                ) togetherWith slideOutVertically(
                                    animationSpec = tween(300),
                                    targetOffsetY = { fullHeight -> if (forward) -fullHeight else fullHeight }
                                )
                            }
                        },
                        contentKey = { it::class },
                        label = "app_route"
                    ) { currentRoute ->
                        when (currentRoute) {
                            is AppRoute.Intro -> renderIntroRoute(
                                stage = currentRoute.stage,
                                onAction = {
                                    when (currentRoute.stage) {
                                        IntroStage.LEGAL -> {
                                            routeUiState = routeUiState.copy(introStage = IntroStage.ATTACH_PROMPT)
                                        }
                                        IntroStage.ATTACH_PROMPT -> {
                                            routeUiState = routeUiState.copy(introStage = IntroStage.TRANSITION_OUT)
                                        }
                                        else -> {}
                                    }
                                },
                                onTransitionFinished = {
                                    routeUiState = routeUiState.copy(introStage = IntroStage.DONE)
                                }
                            )

                            AppRoute.Tracking -> {
                                // Handle Recovery Dialog only when Tracking screen is active
                                state.pendingRecovery?.let { recovery ->
                                    AlertDialog(
                                        onDismissRequest = { viewModel.resolveRecovery(false) },
                                        title = { Text(stringResource(R.string.dialog_recovery_title)) },
                                        text = { Text(stringResource(R.string.dialog_recovery_message)) },
                                        confirmButton = {
                                            TextButton(onClick = {
                                                viewModel.resolveRecovery(true)
                                            }) {
                                                Text(stringResource(R.string.dialog_recovery_continue))
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = {
                                                viewModel.resolveRecovery(false)
                                            }) {
                                                Text(stringResource(R.string.dialog_recovery_save))
                                            }
                                        }
                                    )
                                }

                                LeanAngleScreen(
                                    trackingState = state.tracking,
                                    onOpenSettings = { routeUiState = routeUiState.copy(showSettings = true) },
                                    onOpenPremium = { routeUiState = routeUiState.copy(showPremium = true) },
                                    onOpenHistory = { routeUiState = routeUiState.copy(showHistory = true) },
                                    onStartTracking = {
                                        if (missingTrackingPermissions().isEmpty()) {
                                            viewModel.onLocationPermissionResult(true)
                                            viewModel.startTracking()
                                        } else {
                                            showTrackingPermissionDialog = true
                                        }
                                    },
                                    onFinishRide = {
                                        viewModel.finishRide()
                                    },
                                    onStartCalibration = {
                                        routeUiState = routeUiState.copy(showSettings = false)
                                        viewModel.startCalibration()
                                    },
                                    onTogglePause = viewModel::togglePauseTracking,
                                    onResetGaugeExtrema = viewModel::resetGaugeExtrema,
                                    onSetSensorSamplingRate = viewModel::setSensorSamplingRate,
                                    onAutoResumeIndicatorDismissed = viewModel::dismissAutoResumePremiumShortcut,
                                    appTourState = if (
                                        state.pendingRecovery == null && state.offerExtendSession == null
                                    ) {
                                        state.appTour
                                    } else {
                                        AppTourUiState()
                                    },
                                    onAcceptAppTourOffer = viewModel::acceptAppTourOffer,
                                    onDeclineAppTourOffer = viewModel::completeAppTour,
                                    onPreviousAppTourPage = viewModel::showPreviousAppTourPage,
                                    onNextAppTourPage = viewModel::showNextAppTourPage,
                                    onFinishAppTour = viewModel::completeAppTour,
                                    offerExtend = state.offerExtendSession,
                                    onConfirmExtend = viewModel::confirmExtendRide,
                                    showAdBanner = !state.settings.isPremiumSubscribed
                                )
                            }

                            AppRoute.Calibration -> CalibrationScreen(
                                calibrationState = state.calibration,
                                offerAppTour = state.appTour.offerPending,
                                onStartUprightMeasurement = viewModel::startUprightMeasurement,
                                onFinishCalibration = viewModel::finishCalibrationFlow
                            )

                            AppRoute.Settings -> SettingsScreen(
                                state = state.settings,
                                onBack = { routeUiState = routeUiState.copy(showSettings = false) },
                                onSetHistoryWindow = viewModel::setHistoryWindowSeconds,
                                onSetRecorderIntervalMs = viewModel::setRecorderIntervalMs,
                                onResetGaugeExtrema = viewModel::resetGaugeExtrema,
                                onStartAppTour = {
                                    routeUiState = routeUiState.copy(showSettings = false)
                                    viewModel.startAppTour()
                                },
                                onStartCalibration = {
                                    routeUiState = routeUiState.copy(showSettings = false)
                                    viewModel.startCalibration()
                                },
                                onToggleAutoResume = { enabled ->
                                    if (shouldOpenPremiumForAutomationToggle(
                                            enabled = enabled,
                                            hasAutomationAccess = state.settings.hasAutomationAccess
                                        )
                                    ) {
                                        routeUiState = routeUiState.copy(showPremium = true)
                                    } else {
                                        viewModel.setAutoResumeEnabled(enabled)
                                    }
                                },
                                onOpenPremium = { routeUiState = routeUiState.copy(showPremium = true) },
                                onToggleAutoPause = { enabled ->
                                    if (shouldOpenPremiumForAutomationToggle(
                                            enabled = enabled,
                                            hasAutomationAccess = state.settings.hasAutomationAccess
                                        )
                                    ) {
                                        routeUiState = routeUiState.copy(showPremium = true)
                                    } else {
                                        viewModel.setAutoPauseEnabled(enabled)
                                    }
                                }
                            )

                            AppRoute.Premium -> PremiumScreen(
                                isAutomationPackPurchased = state.settings.isAutomationPackPurchased,
                                isPremiumSubscribed = state.settings.isPremiumSubscribed,
                                billingState = premiumBillingState,
                                onBack = { routeUiState = routeUiState.copy(showPremium = false) },
                                onBuyAutomationPack = {
                                    premiumBillingManager.launchAutomationPackPurchase(this@MainActivity)
                                },
                                onSubscribe = {
                                    premiumBillingManager.launchSubscriptionPurchase(this@MainActivity)
                                },
                                onRestorePurchases = premiumBillingManager::refreshPurchases,
                                onManageSubscription = ::openPremiumSubscriptionManagement
                            )

                            AppRoute.TrackReview -> RideHistoryScreen(
                                rideHistory = state.rideHistory,
                                onSelectRide = { id -> 
                                    viewModel.loadFullSession(id)
                                    routeUiState = routeUiState.copy(selectedRideId = id) 
                                },
                                onBack = { routeUiState = routeUiState.copy(showHistory = false) },
                                onDeleteRide = viewModel::deleteRide,
                                onImportGpx = { gpxImportLauncher.launch("*/*") },
                                importState = state.gpxImport,
                                onImportErrorConsumed = viewModel::consumeGpxImportError,
                                onCombineRides = viewModel::combineRides
                            )

                            is AppRoute.RideDetail -> {
                                val summary = state.rideHistory.find { it.rideId == currentRoute.rideId }
                                if (summary != null) {
                                    RideDetailScreen(
                                        rideSummary = summary,
                                        fullSession = state.expandedRides[currentRoute.rideId],
                                        onBack = { routeUiState = routeUiState.copy(selectedRideId = null, showHistory = true) },
                                        onUpdateName = { viewModel.updateRideName(summary, it) },
                                        onDelete = { 
                                            viewModel.deleteRide(summary)
                                            routeUiState = routeUiState.copy(selectedRideId = null, showHistory = true)
                                        },
                                        isPremiumSubscribed = state.settings.isPremiumSubscribed
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::premiumBillingManager.isInitialized) premiumBillingManager.refreshPurchases()
        
        // Initial check based on current display rotation
        val currentRotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }

        if (currentRotation == Surface.ROTATION_90 || currentRotation == Surface.ROTATION_270) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

        // Also nudge via OrientationEventListener to catch cases where display rotation hasn't updated yet
        val listener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val isLandscape = (orientation in 60..120) || (orientation in 240..300)
                if (isLandscape) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                }
                disable() // Stop listening after the initial nudge on resume
            }
        }
        if (listener.canDetectOrientation()) {
            listener.enable()
        }
    }

    override fun onDestroy() {
        if (::premiumBillingManager.isInitialized) premiumBillingManager.close()
        super.onDestroy()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun missingTrackingPermissions(): Array<String> = buildList {
        if (!hasLocationPermission()) {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private fun resolveRoute(
        introStage: IntroStage,
        showSettings: Boolean,
        showPremium: Boolean,
        showHistory: Boolean,
        selectedRideId: Long?,
        isCalibrated: Boolean,
        calibrationCompletionPending: Boolean
    ): AppRoute {
        if (introStage != IntroStage.DONE) {
            return AppRoute.Intro(introStage)
        }
        if (!isCalibrated || calibrationCompletionPending) {
            return AppRoute.Calibration
        }
        if (selectedRideId != null) {
            return AppRoute.RideDetail(selectedRideId)
        }
        if (showHistory) {
            return AppRoute.TrackReview
        }
        if (showPremium) {
            return AppRoute.Premium
        }
        if (showSettings) {
            return AppRoute.Settings
        } else {
            return AppRoute.Tracking
        }
    }

    private fun openPremiumSubscriptionManagement() {
        val uri = Uri.parse(
            "https://play.google.com/store/account/subscriptions" +
                "?sku=${BuildConfig.PREMIUM_SUBSCRIPTION_PRODUCT_ID}&package=$packageName"
        )
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    @Composable
    private fun renderIntroRoute(
        stage: IntroStage,
        onAction: () -> Unit,
        onTransitionFinished: () -> Unit
    ) {
        IntroScreen(stage = stage, onAction = onAction, onTransitionFinished = onTransitionFinished)
    }
}

private data class RouteUiState(
    val introStage: IntroStage = IntroStage.LOADING,
    val showSettings: Boolean = false,
    val showPremium: Boolean = false,
    val showHistory: Boolean = false,
    val selectedRideId: Long? = null
) {
    companion object {
        val Saver: Saver<RouteUiState, Any> = listSaver(
            save = { listOf(it.introStage.name, it.showSettings, it.showPremium, it.showHistory, it.selectedRideId ?: -1L) },
            restore = {
                val rideId = it[4] as Long
                RouteUiState(
                    introStage = IntroStage.valueOf(it[0] as String),
                    showSettings = it[1] as Boolean,
                    showPremium = it[2] as Boolean,
                    showHistory = it[3] as Boolean,
                    selectedRideId = if (rideId == -1L) null else rideId
                )
            }
        )
    }
}

internal fun shouldOpenPremiumForAutomationToggle(
    enabled: Boolean,
    hasAutomationAccess: Boolean
): Boolean = enabled && !hasAutomationAccess
