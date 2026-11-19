package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ConfigRepository
import com.example.data.HistoryRepository
import com.example.data.ServerRepository
import com.example.data.SettingsRepository
import com.example.model.ConnectionHistory
import com.example.model.TunnelStatus
import com.example.model.UserSettings
import com.example.model.VpnConfig
import com.example.model.VpnServer
import com.example.model.VpnState
import com.example.ui.components.NavDestination
import com.example.vpn.VpnManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val serverRepo = ServerRepository()
    private val configRepo = ConfigRepository(application)
    private val historyRepo = HistoryRepository(application)
    private val settingsRepo = SettingsRepository(application)

    val vpnState: StateFlow<VpnState> = VpnManager.vpnState
    val servers: StateFlow<List<VpnServer>> = serverRepo.servers
    val selectedServer: StateFlow<VpnServer> = serverRepo.selectedServer
    val configs: StateFlow<List<VpnConfig>> = configRepo.configs
    val activeConfig: StateFlow<VpnConfig?> = configRepo.activeConfig
    val history: StateFlow<List<ConnectionHistory>> = historyRepo.history
    val settings: StateFlow<UserSettings> = settingsRepo.settings

    private val _currentNav = MutableStateFlow(NavDestination.HOME)
    val currentNav: StateFlow<NavDestination> = _currentNav.asStateFlow()

    private val _isPinging = MutableStateFlow(false)
    val isPinging: StateFlow<Boolean> = _isPinging.asStateFlow()

    init {
        VpnManager.initialize(application)
        refreshPings()
    }

    fun navigateTo(destination: NavDestination) {
        _currentNav.value = destination
    }

    fun selectServer(server: VpnServer) {
        serverRepo.selectServer(server)
    }

    fun refreshPings() {
        viewModelScope.launch {
            _isPinging.value = true
            serverRepo.refreshAllPings()
            _isPinging.value = false
        }
    }

    fun selectConfig(config: VpnConfig) {
        configRepo.selectActiveConfig(config)
    }

    fun saveConfig(config: VpnConfig) {
        configRepo.saveConfig(config)
    }

    fun deleteConfig(configId: String) {
        configRepo.deleteConfig(configId)
    }

    fun setDefaultConfig(configId: String) {
        configRepo.setDefaultConfig(configId)
    }

    fun importConfig(jsonString: String, passwordAttempt: String): Result<VpnConfig> {
        return configRepo.importConfigFromJson(jsonString, passwordAttempt)
    }

    fun exportConfig(config: VpnConfig): String {
        return configRepo.exportConfigToJson(config)
    }

    fun clearHistory() {
        historyRepo.clearHistory()
    }

    fun updateSettings(newSettings: UserSettings) {
        settingsRepo.updateSettings(newSettings)
    }

    fun clearAllData() {
        configRepo.clearAllConfigs()
        historyRepo.clearHistory()
        settingsRepo.updateSettings(UserSettings())
    }

    fun toggleVpn() {
        val currentStatus = vpnState.value.status
        if (currentStatus == TunnelStatus.CONNECTED || currentStatus == TunnelStatus.CONNECTING) {
            VpnManager.stopVpn(getApplication())
        } else {
            val server = selectedServer.value
            val config = activeConfig.value ?: VpnConfig(
                id = "default-active",
                name = "${server.name} Default",
                host = server.host,
                port = server.port,
                protocol = server.defaultProtocol
            )
            VpnManager.startVpn(getApplication(), server, config)
        }
    }
}
