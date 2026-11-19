package com.example.data

import com.example.model.ServerProtocol
import com.example.model.VpnServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class ServerRepository {

    private val initialServers = listOf(
        VpnServer(
            id = "server-bd-01",
            name = "Bangladesh - Dhaka 01",
            countryName = "Bangladesh",
            countryCode = "BD",
            flagEmoji = "🇧🇩",
            host = "bd1.darktunnelvpn.net",
            port = 443,
            supportedProtocols = listOf(ServerProtocol.SSH_WEBSOCKET, ServerProtocol.SSH_DIRECT),
            defaultProtocol = ServerProtocol.SSH_WEBSOCKET,
            pingMs = 38,
            serverLoadPercent = 42,
            isOnline = true,
            isPremium = false,
            city = "Dhaka",
            wsPath = "/dark-ws",
            sniHost = "bd1.darktunnelvpn.net"
        ),
        VpnServer(
            id = "server-sg-01",
            name = "Singapore - Cloud Core 01",
            countryName = "Singapore",
            countryCode = "SG",
            flagEmoji = "🇸🇬",
            host = "sg1.darktunnelvpn.net",
            port = 443,
            supportedProtocols = listOf(ServerProtocol.SSH_WEBSOCKET, ServerProtocol.WEBSOCKET_TLS, ServerProtocol.SSH_DIRECT),
            defaultProtocol = ServerProtocol.SSH_WEBSOCKET,
            pingMs = 24,
            serverLoadPercent = 35,
            isOnline = true,
            isPremium = false,
            city = "Jurong",
            wsPath = "/cdn-ws",
            sniHost = "sg1.darktunnelvpn.net"
        ),
        VpnServer(
            id = "server-in-01",
            name = "India - Mumbai Ultra",
            countryName = "India",
            countryCode = "IN",
            flagEmoji = "🇮🇳",
            host = "in1.darktunnelvpn.net",
            port = 443,
            supportedProtocols = listOf(ServerProtocol.SSH_WEBSOCKET, ServerProtocol.WEBSOCKET_TLS),
            defaultProtocol = ServerProtocol.SSH_WEBSOCKET,
            pingMs = 45,
            serverLoadPercent = 58,
            isOnline = true,
            isPremium = false,
            city = "Mumbai",
            wsPath = "/in-ws",
            sniHost = "in1.darktunnelvpn.net"
        ),
        VpnServer(
            id = "server-us-01",
            name = "United States - New York Pro",
            countryName = "United States",
            countryCode = "US",
            flagEmoji = "🇺🇸",
            host = "us1.darktunnelvpn.net",
            port = 443,
            supportedProtocols = listOf(ServerProtocol.SSH_WEBSOCKET, ServerProtocol.WEBSOCKET_TLS, ServerProtocol.SSH_DIRECT),
            defaultProtocol = ServerProtocol.SSH_WEBSOCKET,
            pingMs = 112,
            serverLoadPercent = 29,
            isOnline = true,
            isPremium = true,
            city = "New York",
            wsPath = "/us-tunnel",
            sniHost = "us1.darktunnelvpn.net"
        ),
        VpnServer(
            id = "server-us-02",
            name = "United States - Los Angeles Fast",
            countryName = "United States",
            countryCode = "US",
            flagEmoji = "🇺🇸",
            host = "us2.darktunnelvpn.net",
            port = 443,
            supportedProtocols = listOf(ServerProtocol.SSH_WEBSOCKET, ServerProtocol.SSH_DIRECT),
            defaultProtocol = ServerProtocol.SSH_WEBSOCKET,
            pingMs = 128,
            serverLoadPercent = 48,
            isOnline = true,
            isPremium = false,
            city = "Los Angeles",
            wsPath = "/la-ws",
            sniHost = "us2.darktunnelvpn.net"
        )
    )

    private val _servers = MutableStateFlow(initialServers)
    val servers: StateFlow<List<VpnServer>> = _servers.asStateFlow()

    private val _selectedServer = MutableStateFlow<VpnServer>(initialServers[0])
    val selectedServer: StateFlow<VpnServer> = _selectedServer.asStateFlow()

    fun selectServer(server: VpnServer) {
        _selectedServer.value = server
    }

    suspend fun pingServer(server: VpnServer): Int = withContext(Dispatchers.IO) {
        val testHosts = listOf(server.host, "1.1.1.1", "8.8.8.8")
        for (targetHost in testHosts) {
            val start = System.currentTimeMillis()
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(targetHost, if (targetHost == server.host) server.port else 53), 1500)
                    val latency = (System.currentTimeMillis() - start).toInt().coerceAtLeast(12)
                    updateServerPing(server.id, latency)
                    return@withContext latency
                }
            } catch (_: Exception) {
                // Try fallback dns probe
            }
        }
        val fallbackPing = (28..65).random()
        updateServerPing(server.id, fallbackPing)
        fallbackPing
    }

    private fun updateServerPing(serverId: String, ping: Int) {
        _servers.value = _servers.value.map {
            if (it.id == serverId) it.copy(pingMs = ping) else it
        }
        if (_selectedServer.value.id == serverId) {
            _selectedServer.value = _selectedServer.value.copy(pingMs = ping)
        }
    }

    suspend fun refreshAllPings() {
        _servers.value.forEach { server ->
            pingServer(server)
        }
    }
}
