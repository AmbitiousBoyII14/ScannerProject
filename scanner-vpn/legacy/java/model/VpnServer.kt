package com.example.model

enum class ServerProtocol(val displayName: String) {
    SSH_WEBSOCKET("SSH + WebSocket"),
    SSH_DIRECT("SSH Direct"),
    WEBSOCKET_TLS("WebSocket (TLS/SNI)"),
    DNS_TUNNEL("DNS Tunnel")
}

data class VpnServer(
    val id: String,
    val name: String,
    val countryName: String,
    val countryCode: String,
    val flagEmoji: String,
    val host: String,
    val port: Int,
    val supportedProtocols: List<ServerProtocol>,
    val defaultProtocol: ServerProtocol,
    val pingMs: Int = -1,
    val serverLoadPercent: Int = 45,
    val isOnline: Boolean = true,
    val isPremium: Boolean = false,
    val city: String = "",
    val wsPath: String = "/dark-ws",
    val sniHost: String = ""
)
