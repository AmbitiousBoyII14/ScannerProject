package com.example.model

enum class TunnelStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

enum class NetworkType {
    WIFI,
    CELLULAR,
    ETHERNET,
    OFFLINE
}

data class VpnState(
    val status: TunnelStatus = TunnelStatus.DISCONNECTED,
    val errorMessage: String? = null,
    val currentIp: String = "---.---.---.---",
    val originalIp: String = "Detecting...",
    val uploadSpeedBps: Long = 0L,
    val downloadSpeedBps: Long = 0L,
    val totalBytesSent: Long = 0L,
    val totalBytesReceived: Long = 0L,
    val durationSeconds: Long = 0L,
    val networkType: NetworkType = NetworkType.WIFI,
    val connectedServerName: String? = null,
    val connectedServerLocation: String? = null,
    val connectedProtocol: String? = null
)
