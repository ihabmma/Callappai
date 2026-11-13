package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.configureAppCheck
import com.example.ui.navigation.CallHistoryRoute
import com.example.ui.navigation.DashboardRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.TeamMemoryRoute
import com.example.ui.screens.CallHistoryScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TeamMemoryScreen
import com.example.ui.theme.SmartCallResponderTheme
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check before UI, ViewModels, or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                SmartCallResponderTheme {
                    SmartCallResponderApp()
                }
            }
        }
    }
}

private data class NavItem(
    val route: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartCallResponderApp(
    viewModel: MainViewModel = viewModel()
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val lifecycleOwner = LocalLifecycleOwner.current

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val liveCallState by viewModel.liveCallState.collectAsStateWithLifecycle()
    val callLogs by viewModel.callLogs.collectAsStateWithLifecycle()
    val callerProfiles by viewModel.callerProfiles.collectAsStateWithLifecycle()
    val learningInsights by viewModel.learningInsights.collectAsStateWithLifecycle()
    val trainedRules by viewModel.trainedRules.collectAsStateWithLifecycle()
    val scheduledReminders by viewModel.scheduledReminders.collectAsStateWithLifecycle()
    val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()
    val permissions by viewModel.permissionSummary.collectAsStateWithLifecycle()
    val connectionTestState by viewModel.connectionTestState.collectAsStateWithLifecycle()
    val uiNotice by viewModel.uiNotice.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var pendingEnableAfterPermission by remember { mutableStateOf(false) }

    // Refresh overlay & battery exemption permissions whenever user returns from System Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uiNotice) {
        val message = uiNotice
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearNotice()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshPermissions()
        if (pendingEnableAfterPermission) {
            pendingEnableAfterPermission = false
            viewModel.toggleResponderService(true)
        }
    }

    val navItems = remember {
        listOf(
            NavItem(
                route = DashboardRoute,
                labelRes = R.string.nav_dashboard,
                selectedIcon = Icons.Filled.Dashboard,
                unselectedIcon = Icons.Outlined.Dashboard,
                testTag = "nav_dashboard"
            ),
            NavItem(
                route = CallHistoryRoute,
                labelRes = R.string.nav_history,
                selectedIcon = Icons.Filled.History,
                unselectedIcon = Icons.Outlined.History,
                testTag = "nav_history"
            ),
            NavItem(
                route = TeamMemoryRoute,
                labelRes = R.string.nav_team,
                selectedIcon = Icons.Filled.Groups,
                unselectedIcon = Icons.Outlined.Groups,
                testTag = "nav_team"
            ),
            NavItem(
                route = SettingsRoute,
                labelRes = R.string.nav_settings,
                selectedIcon = Icons.Filled.Settings,
                unselectedIcon = Icons.Outlined.Settings,
                testTag = "nav_settings"
            )
        )
    }

    fun navigateTo(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(id = R.string.app_name),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            val label = stringResource(id = item.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(text = label, maxLines = 1) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            val label = stringResource(id = item.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(text = label, maxLines = 1) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = DashboardRoute
                    ) {
                        composable<DashboardRoute> {
                            DashboardScreen(
                                settings = settings,
                                liveState = liveCallState,
                                permissions = permissions,
                                callLogs = callLogs,
                                trainedRulesCount = trainedRules.size,
                                remindersCount = scheduledReminders.size,
                                onToggleService = { enable ->
                                    if (enable && !permissions.coreGranted) {
                                        pendingEnableAfterPermission = true
                                        permissionLauncher.launch(viewModel.requiredPermissionsList())
                                    } else {
                                        viewModel.toggleResponderService(enable)
                                    }
                                },
                                onRequestPermissions = {
                                    permissionLauncher.launch(viewModel.requiredPermissionsList())
                                },
                                onOpenOverlaySettings = { viewModel.openOverlayPermissionSettings() },
                                onRequestBatteryExemption = { viewModel.requestBatteryOptimizationExemption() },
                                onStartLiveTest = { viewModel.startLiveTestSession() },
                                onStopLiveTest = { viewModel.stopLiveTestSession() },
                                onSendSimulatedUtterance = { text ->
                                    viewModel.sendSimulatedCallerUtterance(text)
                                }
                            )
                        }

                        composable<CallHistoryRoute> {
                            CallHistoryScreen(
                                logs = callLogs,
                                onDeleteLog = { id -> viewModel.deleteCallLog(id) },
                                onClearAll = { viewModel.clearAllCallLogs() }
                            )
                        }

                        composable<TeamMemoryRoute> {
                            TeamMemoryScreen(
                                profiles = callerProfiles,
                                insights = learningInsights,
                                trainedRules = trainedRules,
                                scheduledReminders = scheduledReminders,
                                onSaveProfile = { phone, name, category, notes, deflect ->
                                    viewModel.saveCallerProfile(phone, name, category, notes, deflect)
                                },
                                onDeleteProfile = { phone -> viewModel.deleteCallerProfile(phone) },
                                onAddManualInsight = { text -> viewModel.addManualInsight(text) },
                                onDeleteInsight = { id -> viewModel.deleteInsight(id) },
                                onAddTrainedRule = { kw, reply, cat, strat, end ->
                                    viewModel.addTrainedQaRule(kw, reply, cat, strat, end)
                                },
                                onDeleteTrainedRule = { id -> viewModel.deleteTrainedRule(id) },
                                onCreatePhoneAction = { type, title, h, m, delay ->
                                    viewModel.createPhoneAction(type, title, h, m, delay)
                                },
                                onDeleteReminder = { id -> viewModel.deleteScheduledReminder(id) }
                            )
                        }

                        composable<SettingsRoute> {
                            SettingsScreen(
                                savedSettings = settings,
                                storageStats = storageStats,
                                permissions = permissions,
                                connectionTestState = connectionTestState,
                                onSaveSettings = { updated -> viewModel.saveSettings(updated) },
                                onTestConnection = { draft -> viewModel.testAiConnection(draft) },
                                onResetTestState = { viewModel.resetConnectionTestState() },
                                onTestTts = { draft -> viewModel.testTtsVoice(draft) },
                                onCleanupNow = { viewModel.runStorageCleanupNow() },
                                onOpenOverlaySettings = { viewModel.openOverlayPermissionSettings() },
                                onRequestBatteryExemption = { viewModel.requestBatteryOptimizationExemption() }
                            )
                        }
                    }
                }
            }
        }
    }
}
