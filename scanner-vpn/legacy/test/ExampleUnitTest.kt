package com.example

import com.example.model.ServerProtocol
import com.example.model.VpnConfig
import com.example.ui.components.formatBytes
import com.example.ui.components.formatDuration
import com.example.ui.components.formatSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testConfigValidation_validConfig() {
        val config = VpnConfig(
            id = "cfg-1",
            name = "Singapore Tunnel",
            protocol = ServerProtocol.SSH_WEBSOCKET,
            host = "sg1.darktunnelvpn.net",
            port = 443,
            username = "vpnuser"
        )
        val result = config.validate()
        assertTrue(result.isValid)
    }

    @Test
    fun testConfigValidation_emptyHost() {
        val config = VpnConfig(
            id = "cfg-2",
            name = "Test",
            protocol = ServerProtocol.SSH_WEBSOCKET,
            host = "",
            port = 443,
            username = "vpnuser"
        )
        val result = config.validate()
        assertFalse(result.isValid)
    }

    @Test
    fun testConfigValidation_invalidPort() {
        val config = VpnConfig(
            id = "cfg-3",
            name = "Test",
            protocol = ServerProtocol.SSH_WEBSOCKET,
            host = "test.server.com",
            port = 70000,
            username = "vpnuser"
        )
        val result = config.validate()
        assertFalse(result.isValid)
    }

    @Test
    fun testPayloadMacroReplacement() {
        val config = VpnConfig(
            id = "cfg-payload-test",
            name = "Payload Test",
            customPayload = "GET / HTTP/1.1[crlf]Host: [host][crlf]X-Port: [port][crlf]Upgrade: websocket[crlf][crlf]"
        )
        val formatted = config.formatPayload("bd1.darktunnelvpn.net", 443)
        assertTrue(formatted.contains("Host: bd1.darktunnelvpn.net\r\n"))
        assertTrue(formatted.contains("X-Port: 443\r\n"))
        assertTrue(formatted.endsWith("\r\n\r\n"))
        assertFalse(formatted.contains("[crlf]"))
    }

    @Test
    fun testFormatDuration() {
        assertEquals("00:00:00", formatDuration(0L))
        assertEquals("00:01:05", formatDuration(65L))
        assertEquals("01:00:00", formatDuration(3600L))
        assertEquals("01:01:01", formatDuration(3661L))
    }

    @Test
    fun testFormatBytes() {
        assertEquals("500 B", formatBytes(500L))
        assertEquals("1 KB", formatBytes(1024L))
        assertEquals("1.0 MB", formatBytes(1024L * 1024L))
        assertEquals("1.00 GB", formatBytes(1024L * 1024L * 1024L))
    }

    @Test
    fun testFormatSpeed() {
        assertEquals("50 B/s", formatSpeed(50L))
        assertEquals("2 KB/s", formatSpeed(2048L))
        assertEquals("1.5 MB/s", formatSpeed((1.5 * 1024 * 1024).toLong()))
    }
}
