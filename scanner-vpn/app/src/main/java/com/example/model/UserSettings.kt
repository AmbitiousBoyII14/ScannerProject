package com.example.model

data class UserSettings(
    val autoConnectOnAppLaunch: Boolean = false,
    val reconnectOnNetworkChange: Boolean = true,
    val killSwitchEnabled: Boolean = false,
    val bypassLan: Boolean = true,
    val primaryDns: String = "1.1.1.1",
    val secondaryDns: String = "8.8.8.8",
    val mtuSize: Int = 1500,
    val selectedLanguage: String = "English",
    val isProUser: Boolean = false,
    val userEmail: String? = null,
    val accountTier: String = "Free Explorer",
    val customSniDefault: String = ""
)
