package com.example.model

import org.json.JSONObject

data class VpnConfig(
    val id: String,
    val name: String,
    val protocol: ServerProtocol = ServerProtocol.SSH_WEBSOCKET,
    val host: String = "",
    val port: Int = 443,
    val username: String = "",
    val password: String = "",
    val wsPayloadPath: String = "/dark-ws",
    val customPayload: String = "",
    val sniServerName: String = "",
    val customDnsPrimary: String = "1.1.1.1",
    val customDnsSecondary: String = "8.8.8.8",
    val isDefault: Boolean = false,
    val isPasswordProtected: Boolean = false,
    val configPassphrase: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val notes: String = ""
) {
    fun validate(): ConfigValidationResult {
        if (name.isBlank()) {
            return ConfigValidationResult(false, "Config name cannot be empty")
        }
        if (host.isBlank()) {
            return ConfigValidationResult(false, "Server host/IP is required")
        }
        if (port !in 1..65535) {
            return ConfigValidationResult(false, "Port must be between 1 and 65535")
        }
        if (protocol == ServerProtocol.SSH_DIRECT || protocol == ServerProtocol.SSH_WEBSOCKET) {
            if (username.isBlank()) {
                return ConfigValidationResult(false, "SSH Username is required for SSH tunneling")
            }
        }
        return ConfigValidationResult(true, "Configuration is valid")
    }

    fun formatPayload(targetHost: String, targetPort: Int): String {
        if (customPayload.isNotBlank()) {
            return customPayload
                .replace("[crlf]", "\r\n")
                .replace("[lf]", "\n")
                .replace("[cr]", "\r")
                .replace("[host]", targetHost)
                .replace("[port]", targetPort.toString())
                .replace("[host_port]", "$targetHost:$targetPort")
                .replace("[protocol]", "HTTP/1.1")
                .replace("[ua]", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
        }
        val path = if (wsPayloadPath.isNotBlank()) wsPayloadPath else "/dark-ws"
        return "GET $path HTTP/1.1\r\n" +
                "Host: $targetHost\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
                "Sec-WebSocket-Version: 13\r\n\r\n"
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("protocol", protocol.name)
            put("host", host)
            put("port", port)
            put("username", username)
            put("password", password)
            put("wsPayloadPath", wsPayloadPath)
            put("customPayload", customPayload)
            put("sniServerName", sniServerName)
            put("customDnsPrimary", customDnsPrimary)
            put("customDnsSecondary", customDnsSecondary)
            put("isDefault", isDefault)
            put("isPasswordProtected", isPasswordProtected)
            put("configPassphrase", configPassphrase)
            put("createdAt", createdAt)
            put("notes", notes)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): VpnConfig {
            val protocolName = json.optString("protocol", ServerProtocol.SSH_WEBSOCKET.name)
            val protocol = try {
                ServerProtocol.valueOf(protocolName)
            } catch (e: Exception) {
                ServerProtocol.SSH_WEBSOCKET
            }
            return VpnConfig(
                id = json.optString("id", System.currentTimeMillis().toString()),
                name = json.optString("name", "Imported Config"),
                protocol = protocol,
                host = json.optString("host", ""),
                port = json.optInt("port", 443),
                username = json.optString("username", ""),
                password = json.optString("password", ""),
                wsPayloadPath = json.optString("wsPayloadPath", "/dark-ws"),
                customPayload = json.optString("customPayload", ""),
                sniServerName = json.optString("sniServerName", ""),
                customDnsPrimary = json.optString("customDnsPrimary", "1.1.1.1"),
                customDnsSecondary = json.optString("customDnsSecondary", "8.8.8.8"),
                isDefault = json.optBoolean("isDefault", false),
                isPasswordProtected = json.optBoolean("isPasswordProtected", false),
                configPassphrase = json.optString("configPassphrase", ""),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                notes = json.optString("notes", "")
            )
        }
    }
}

data class ConfigValidationResult(
    val isValid: Boolean,
    val message: String
)
