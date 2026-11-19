package com.example.vpn

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.model.NetworkType
import com.example.model.TunnelStatus
import com.example.model.VpnConfig
import com.example.model.VpnServer
import com.example.model.VpnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object VpnManager {

    private val _vpnState = MutableStateFlow(VpnState())
    val vpnState: StateFlow<VpnState> = _vpnState.asStateFlow()

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var durationJob: Job? = null
    private var lastBytesIn = 0L
    private var lastBytesOut = 0L

    fun initialize(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager = cm

        updateNetworkType(cm)

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch { updateNetworkType(cm) }
            }

            override fun onLost(network: Network) {
                scope.launch {
                    _vpnState.update { it.copy(networkType = NetworkType.OFFLINE) }
                }
            }
        }
        networkCallback = callback
        try {
            cm.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateNetworkType(cm: ConnectivityManager) {
        val activeNetwork = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNetwork)
        val type = when {
            caps == null -> NetworkType.OFFLINE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.WIFI
        }
        _vpnState.update { it.copy(networkType = type) }
    }

    fun startVpn(context: Context, server: VpnServer, config: VpnConfig) {
        _vpnState.update {
            it.copy(
                status = TunnelStatus.CONNECTING,
                errorMessage = null,
                connectedServerName = server.name,
                connectedServerLocation = "${server.countryName} (${server.flagEmoji})",
                connectedProtocol = config.protocol.displayName,
                durationSeconds = 0L,
                uploadSpeedBps = 0L,
                downloadSpeedBps = 0L,
                totalBytesSent = 0L,
                totalBytesReceived = 0L
            )
        }

        val intent = Intent(context, DarkTunnelVpnService::class.java).apply {
            action = DarkTunnelVpnService.ACTION_CONNECT
            putExtra(DarkTunnelVpnService.EXTRA_SERVER_HOST, config.host.ifBlank { server.host })
            putExtra(DarkTunnelVpnService.EXTRA_SERVER_PORT, if (config.port > 0) config.port else server.port)
            putExtra(DarkTunnelVpnService.EXTRA_SERVER_NAME, server.name)
            putExtra(DarkTunnelVpnService.EXTRA_SERVER_COUNTRY, server.countryCode)
            putExtra(DarkTunnelVpnService.EXTRA_SERVER_FLAG, server.flagEmoji)
            putExtra(DarkTunnelVpnService.EXTRA_PROTOCOL, config.protocol.name)
            putExtra(DarkTunnelVpnService.EXTRA_USERNAME, config.username)
            putExtra(DarkTunnelVpnService.EXTRA_PASSWORD, config.password)
            putExtra(DarkTunnelVpnService.EXTRA_WS_PATH, config.wsPayloadPath)
            putExtra(DarkTunnelVpnService.EXTRA_CUSTOM_PAYLOAD, config.customPayload)
            putExtra(DarkTunnelVpnService.EXTRA_SNI, config.sniServerName.ifBlank { server.sniHost })
            putExtra(DarkTunnelVpnService.EXTRA_DNS_PRIMARY, config.customDnsPrimary)
            putExtra(DarkTunnelVpnService.EXTRA_DNS_SECONDARY, config.customDnsSecondary)
        }
        context.startService(intent)
    }

    fun stopVpn(context: Context) {
        _vpnState.update { it.copy(status = TunnelStatus.DISCONNECTING) }
        val intent = Intent(context, DarkTunnelVpnService::class.java).apply {
            action = DarkTunnelVpnService.ACTION_DISCONNECT
        }
        context.startService(intent)
    }

    fun onConnected(currentIp: String) {
        durationJob?.cancel()
        lastBytesIn = 0L
        lastBytesOut = 0L
        _vpnState.update {
            it.copy(
                status = TunnelStatus.CONNECTED,
                errorMessage = null,
                currentIp = currentIp
            )
        }
        startDurationTimer()
    }

    fun onDisconnected(error: String? = null) {
        durationJob?.cancel()
        _vpnState.update {
            it.copy(
                status = if (error != null) TunnelStatus.ERROR else TunnelStatus.DISCONNECTED,
                errorMessage = error,
                uploadSpeedBps = 0L,
                downloadSpeedBps = 0L,
                currentIp = "---.---.---.---"
            )
        }
    }

    fun updateTraffic(bytesSent: Long, bytesReceived: Long) {
        val upSpeed = (bytesSent - lastBytesOut).coerceAtLeast(0L)
        val downSpeed = (bytesReceived - lastBytesIn).coerceAtLeast(0L)
        lastBytesOut = bytesSent
        lastBytesIn = bytesReceived

        _vpnState.update {
            it.copy(
                totalBytesSent = bytesSent,
                totalBytesReceived = bytesReceived,
                uploadSpeedBps = upSpeed,
                downloadSpeedBps = downSpeed
            )
        }
    }

    private fun startDurationTimer() {
        durationJob = scope.launch {
            while (true) {
                delay(1000)
                _vpnState.update { it.copy(durationSeconds = it.durationSeconds + 1) }
            }
        }
    }
}
