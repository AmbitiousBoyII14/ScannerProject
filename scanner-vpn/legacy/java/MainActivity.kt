package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainViewModel
import com.example.ui.components.BottomNavBar
import com.example.ui.components.NavDestination
import com.example.ui.screens.ConfigManagerScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ServerListScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkTunnelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DarkTunnelTheme {
                DarkTunnelApp()
            }
        }
    }
}

@Composable
fun DarkTunnelApp(
    viewModel: MainViewModel = viewModel()
) {
    val currentNav by viewModel.currentNav.collectAsStateWithLifecycle()
    val vpnState by viewModel.vpnState.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val selectedServer by viewModel.selectedServer.collectAsStateWithLifecycle()
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val activeConfig by viewModel.activeConfig.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isPinging by viewModel.isPinging.collectAsStateWithLifecycle()

    // Notification permission launcher for Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Predictive back / BackHandler: Return to Home if in another tab
    BackHandler(enabled = currentNav != NavDestination.HOME) {
        viewModel.navigateTo(NavDestination.HOME)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkBackground,
        bottomBar = {
            BottomNavBar(
                currentDestination = currentNav,
                onNavigate = { viewModel.navigateTo(it) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkBackground)
        ) {
            when (currentNav) {
                NavDestination.HOME -> {
                    HomeScreen(
                        vpnState = vpnState,
                        selectedServer = selectedServer,
                        activeConfig = activeConfig,
                        onConnectToggle = { viewModel.toggleVpn() },
                        onNavigate = { viewModel.navigateTo(it) }
                    )
                }
                NavDestination.SERVERS -> {
                    ServerListScreen(
                        servers = servers,
                        selectedServer = selectedServer,
                        isPinging = isPinging,
                        onSelectServer = { viewModel.selectServer(it) },
                        onRefreshPings = { viewModel.refreshPings() }
                    )
                }
                NavDestination.CONFIGS -> {
                    ConfigManagerScreen(
                        configs = configs,
                        activeConfig = activeConfig,
                        onSelectConfig = { viewModel.selectConfig(it) },
                        onSaveConfig = { viewModel.saveConfig(it) },
                        onDeleteConfig = { viewModel.deleteConfig(it) },
                        onSetDefaultConfig = { viewModel.setDefaultConfig(it) },
                        onImportJson = { json, pass -> viewModel.importConfig(json, pass) },
                        onExportJson = { cfg -> viewModel.exportConfig(cfg) }
                    )
                }
                NavDestination.HISTORY -> {
                    HistoryScreen(
                        history = history,
                        onClearHistory = { viewModel.clearHistory() }
                    )
                }
                NavDestination.SETTINGS -> {
                    SettingsScreen(
                        settings = settings,
                        onUpdateSettings = { viewModel.updateSettings(it) },
                        onClearAllData = { viewModel.clearAllData() }
                    )
                }
            }
        }
    }
}
