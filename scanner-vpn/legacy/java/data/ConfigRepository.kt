package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.ServerProtocol
import com.example.model.VpnConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class ConfigRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("dark_tunnel_configs", Context.MODE_PRIVATE)
    private val keyConfigs = "saved_configs_json"
    private val keyDefaultId = "default_config_id"

    private val _configs = MutableStateFlow<List<VpnConfig>>(emptyList())
    val configs: StateFlow<List<VpnConfig>> = _configs.asStateFlow()

    private val _activeConfig = MutableStateFlow<VpnConfig?>(null)
    val activeConfig: StateFlow<VpnConfig?> = _activeConfig.asStateFlow()

    init {
        loadConfigs()
    }

    private fun loadConfigs() {
        val raw = prefs.getString(keyConfigs, null)
        val list = mutableListOf<VpnConfig>()
        if (!raw.isNullOrBlank()) {
            try {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    list.add(VpnConfig.fromJson(array.getJSONObject(i)))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (list.isEmpty()) {
            // Seed sample high-performance configs
            val defaultWs = VpnConfig(
                id = "cfg-default-ws",
                name = "SSH + WS Cloudflare CDN",
                protocol = ServerProtocol.SSH_WEBSOCKET,
                host = "sg1.darktunnelvpn.net",
                port = 443,
                username = "tunnel_free_user",
                password = "",
                wsPayloadPath = "/dark-ws",
                customPayload = "GET / HTTP/1.1[crlf]Host: [host][crlf]Upgrade: websocket[crlf]Connection: Upgrade[crlf][crlf]",
                sniServerName = "sg1.darktunnelvpn.net",
                isDefault = true,
                notes = "Optimized for low-latency WebSocket over TLS"
            )
            val directSsh = VpnConfig(
                id = "cfg-direct-ssh",
                name = "Direct OpenSSH Port 22",
                protocol = ServerProtocol.SSH_DIRECT,
                host = "bd1.darktunnelvpn.net",
                port = 22,
                username = "ssh_admin",
                password = "",
                isDefault = false,
                notes = "Direct encrypted port 22 tunnel"
            )
            list.add(defaultWs)
            list.add(directSsh)
            saveListToPrefs(list)
        }

        _configs.value = list
        val defaultId = prefs.getString(keyDefaultId, list.firstOrNull { it.isDefault }?.id ?: list.firstOrNull()?.id)
        _activeConfig.value = list.firstOrNull { it.id == defaultId } ?: list.firstOrNull()
    }

    fun saveConfig(config: VpnConfig) {
        val current = _configs.value.toMutableList()
        val index = current.indexOfFirst { it.id == config.id }
        if (index >= 0) {
            current[index] = config
        } else {
            current.add(0, config)
        }

        if (config.isDefault) {
            for (i in current.indices) {
                if (current[i].id != config.id) {
                    current[i] = current[i].copy(isDefault = false)
                }
            }
            prefs.edit().putString(keyDefaultId, config.id).apply()
            _activeConfig.value = config
        }

        saveListToPrefs(current)
        _configs.value = current
        if (_activeConfig.value?.id == config.id) {
            _activeConfig.value = config
        }
    }

    fun deleteConfig(configId: String) {
        val current = _configs.value.filter { it.id != configId }
        saveListToPrefs(current)
        _configs.value = current
        if (_activeConfig.value?.id == configId) {
            _activeConfig.value = current.firstOrNull()
        }
    }

    fun setDefaultConfig(configId: String) {
        val current = _configs.value.map {
            it.copy(isDefault = (it.id == configId))
        }
        prefs.edit().putString(keyDefaultId, configId).apply()
        saveListToPrefs(current)
        _configs.value = current
        _activeConfig.value = current.firstOrNull { it.id == configId }
    }

    fun selectActiveConfig(config: VpnConfig) {
        _activeConfig.value = config
    }

    fun importConfigFromJson(jsonString: String, passwordAttempt: String = ""): Result<VpnConfig> {
        return try {
            val trimmed = jsonString.trim()
            val obj = JSONObject(trimmed)
            val config = VpnConfig.fromJson(obj)
            if (config.isPasswordProtected && config.configPassphrase.isNotEmpty()) {
                if (passwordAttempt != config.configPassphrase) {
                    return Result.failure(IllegalArgumentException("Password required or incorrect passphrase"))
                }
            }
            // Generate fresh ID so it doesn't overwrite accidentally
            val imported = config.copy(
                id = "cfg-imp-" + System.currentTimeMillis(),
                name = if (config.name.contains("(Imported)")) config.name else "${config.name} (Imported)",
                isDefault = false
            )
            saveConfig(imported)
            Result.success(imported)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun exportConfigToJson(config: VpnConfig): String {
        return config.toJson().toString(2)
    }

    fun clearAllConfigs() {
        prefs.edit().clear().apply()
        loadConfigs()
    }

    private fun saveListToPrefs(list: List<VpnConfig>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs.edit().putString(keyConfigs, array.toString()).apply()
    }
}
