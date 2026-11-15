package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.repository.TrackingRepository
import com.example.service.BootCompletedReceiver
import com.example.service.DeviceIdentityStore
import com.example.service.LocationTrackingService
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.GuardScreen
import com.example.ui.screens.MonitorScreen
import com.example.ui.screens.PcLinkScreen
import com.example.ui.theme.DeepSlate
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.SentinelTheme
import com.example.ui.viewmodel.SentinelViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val autoResumeFromBoot = intent?.getBooleanExtra(
            BootCompletedReceiver.EXTRA_AUTO_START_TRACKING,
            false
        ) == true
        setContent {
            SentinelTheme {
                AppNavigation(autoResumeFromBoot = autoResumeFromBoot)
            }
        }
    }
}

private data class NavTab(
    val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String,
)

@Composable
fun AppNavigation(autoResumeFromBoot: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            val credentialManager = CredentialManager.create(context)
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                scope = scope,
                onAuthSuccess = {},
                onUnauthenticated = {},
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            SentinelMainHost(
                currentUserId = user.uid,
                userEmail = user.email,
                autoResumeFromBoot = autoResumeFromBoot,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentinelMainHost(
    currentUserId: String,
    userEmail: String?,
    autoResumeFromBoot: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel: SentinelViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val projectId = app.getString(R.string.project_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                SentinelViewModel(
                    repository = TrackingRepository(db, Firebase.auth),
                    identityStore = DeviceIdentityStore(app),
                    currentUserId = currentUserId,
                    firebaseProjectId = projectId,
                    firestoreDatabaseId = databaseId,
                )
            }
        }
    )

    val guardConfig by viewModel.guardConfig.collectAsStateWithLifecycle()
    val serviceState by viewModel.serviceState.collectAsStateWithLifecycle()
    val devicesState by viewModel.devicesState.collectAsStateWithLifecycle()
    val pingsState by viewModel.pingsState.collectAsStateWithLifecycle()
    val selectedDeviceId by viewModel.selectedDeviceId.collectAsStateWithLifecycle()

    fun checkLocationPerm(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun checkNotificationPerm(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun checkBackgroundLocationPerm(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    var hasLocationPermission by remember { mutableStateOf(checkLocationPerm()) }
    var hasNotificationPermission by remember { mutableStateOf(checkNotificationPerm()) }
    var hasBackgroundLocationPermission by remember { mutableStateOf(checkBackgroundLocationPerm()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasLocationPermission = checkLocationPerm()
                hasNotificationPermission = checkNotificationPerm()
                hasBackgroundLocationPermission = checkBackgroundLocationPerm()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        hasBackgroundLocationPermission = checkBackgroundLocationPerm()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasLocationPermission = checkLocationPerm()
        hasNotificationPermission = checkNotificationPerm()
        hasBackgroundLocationPermission = checkBackgroundLocationPerm()
        val anyGranted = results.values.any { it }
        if (hasLocationPermission && anyGranted) {
            LocationTrackingService.startTracking(context)
        }
    }

    LaunchedEffect(hasLocationPermission, autoResumeFromBoot) {
        if (hasLocationPermission && !serviceState.isRunning) {
            val store = DeviceIdentityStore(context)
            if (autoResumeFromBoot || store.isTrackingArmed()) {
                LocationTrackingService.startTracking(context)
            }
        }
    }

    fun requestRequiredPermissions() {
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        permissionLauncher.launch(perms)
    }

    LaunchedEffect(guardConfig.actionMessage) {
        val msg = guardConfig.actionMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionMessage()
        }
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = remember {
        listOf(
            NavTab(R.string.nav_guard, Icons.Filled.Shield, Icons.Outlined.Shield, "tab_guard"),
            NavTab(R.string.nav_monitor, Icons.Filled.GpsFixed, Icons.Outlined.GpsFixed, "tab_monitor"),
            NavTab(R.string.nav_pc_link, Icons.Filled.Computer, Icons.Outlined.Computer, "tab_pc_link"),
        )
    }

    val activeMapsUrl = remember(serviceState.latitude, serviceState.longitude, devicesState) {
        val lat = serviceState.latitude
        val lng = serviceState.longitude
        if (lat != null && lng != null) {
            viewModel.buildGoogleMapsUrl(lat, lng)
        } else {
            val local = (devicesState as? UiState.Success)?.data
                ?.firstOrNull { it.id == viewModel.localDeviceId }
            if (local != null && (local.latitude != 0.0 || local.longitude != 0.0)) {
                viewModel.buildGoogleMapsUrl(local.latitude, local.longitude)
            } else {
                null
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightNavy)
    ) {
        val useRail = maxWidth >= 680.dp

        Scaffold(
            containerColor = MidnightNavy,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = DeepSlate,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(if (serviceState.isRunning) EmeraldShield else MaterialTheme.colorScheme.secondary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    },
                    navigationIcon = {
                        if (!userEmail.isNullOrBlank()) {
                            Text(
                                text = userEmail.substringBefore("@"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (serviceState.isRunning) {
                                    LocationTrackingService.stopTracking(context)
                                }
                                signOutUser(credentialManager, scope)
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = stringResource(R.string.action_sign_out),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = DeepSlate,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            val isSelected = selectedTab == index
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { selectedTab = index },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.titleRes)
                                    )
                                },
                                label = { Text(stringResource(tab.titleRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = EmeraldShield,
                                    selectedTextColor = EmeraldShield,
                                    indicatorColor = EmeraldContainer
                                ),
                                modifier = Modifier.testTag(tab.tag)
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
                if (useRail) {
                    NavigationRail(containerColor = DeepSlate) {
                        tabs.forEachIndexed { index, tab ->
                            val isSelected = selectedTab == index
                            NavigationRailItem(
                                selected = isSelected,
                                onClick = { selectedTab = index },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.titleRes)
                                    )
                                },
                                label = { Text(stringResource(tab.titleRes)) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> GuardScreen(
                            guardConfig = guardConfig,
                            serviceState = serviceState,
                            hasLocationPermission = hasLocationPermission,
                            hasNotificationPermission = hasNotificationPermission,
                            hasBackgroundLocationPermission = hasBackgroundLocationPermission,
                            onRequestPermissions = ::requestRequiredPermissions,
                            onRequestBackgroundLocation = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasLocationPermission) {
                                    backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                } else {
                                    requestRequiredPermissions()
                                }
                            },
                            onStartTracking = {
                                if (hasLocationPermission) {
                                    LocationTrackingService.startTracking(context)
                                } else {
                                    requestRequiredPermissions()
                                }
                            },
                            onStopTracking = { LocationTrackingService.stopTracking(context) },
                            onPingNow = { LocationTrackingService.pingNow(context) },
                            onToggleTheftAlert = { enabled ->
                                viewModel.toggleTheftAlert(enabled)
                                if (serviceState.isRunning) {
                                    LocationTrackingService.notifyConfigUpdated(context)
                                }
                            },
                            onSelectInterval = { sec ->
                                viewModel.updateInterval(sec)
                                if (serviceState.isRunning) {
                                    LocationTrackingService.notifyConfigUpdated(context)
                                }
                            },
                            onRenameDevice = viewModel::updateDeviceName,
                        )
                        1 -> MonitorScreen(
                            devicesState = devicesState,
                            pingsState = pingsState,
                            selectedDeviceId = selectedDeviceId,
                            localDeviceId = viewModel.localDeviceId,
                            onSelectDevice = viewModel::selectDevice,
                            onDeleteDevice = viewModel::deleteDevice,
                        )
                        else -> PcLinkScreen(
                            guardConfig = guardConfig,
                            serviceState = serviceState,
                            consoleUrl = viewModel.buildFirestoreConsoleUrl(),
                            mapsUrl = activeMapsUrl,
                        )
                    }
                }
            }
        }
    }
}
