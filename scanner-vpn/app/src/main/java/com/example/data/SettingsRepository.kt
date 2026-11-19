package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("dark_tunnel_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    private fun loadSettings(): UserSettings {
        return UserSettings(
            autoConnectOnAppLaunch = prefs.getBoolean("auto_connect", false),
            reconnectOnNetworkChange = prefs.getBoolean("reconnect_network", true),
            killSwitchEnabled = prefs.getBoolean("kill_switch", false),
            bypassLan = prefs.getBoolean("bypass_lan", true),
            primaryDns = prefs.getString("primary_dns", "1.1.1.1") ?: "1.1.1.1",
            secondaryDns = prefs.getString("secondary_dns", "8.8.8.8") ?: "8.8.8.8",
            mtuSize = prefs.getInt("mtu_size", 1500),
            selectedLanguage = prefs.getString("language", "English") ?: "English",
            isProUser = prefs.getBoolean("is_pro_user", false),
            userEmail = prefs.getString("user_email", null),
            accountTier = prefs.getString("account_tier", "Free Explorer") ?: "Free Explorer"
        )
    }

    fun updateSettings(newSettings: UserSettings) {
        prefs.edit().apply {
            putBoolean("auto_connect", newSettings.autoConnectOnAppLaunch)
            putBoolean("reconnect_network", newSettings.reconnectOnNetworkChange)
            putBoolean("kill_switch", newSettings.killSwitchEnabled)
            putBoolean("bypass_lan", newSettings.bypassLan)
            putString("primary_dns", newSettings.primaryDns)
            putString("secondary_dns", newSettings.secondaryDns)
            putInt("mtu_size", newSettings.mtuSize)
            putString("language", newSettings.selectedLanguage)
            putBoolean("is_pro_user", newSettings.isProUser)
            putString("user_email", newSettings.userEmail)
            putString("account_tier", newSettings.accountTier)
        }.apply()
        _settings.value = newSettings
    }

    fun setProStatus(isPro: Boolean, email: String? = null) {
        val updated = _settings.value.copy(
            isProUser = isPro,
            userEmail = email ?: _settings.value.userEmail,
            accountTier = if (isPro) "Dark Pro VIP" else "Free Explorer"
        )
        updateSettings(updated)
    }
}
