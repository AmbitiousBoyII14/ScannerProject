package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.ServerProtocol
import com.example.model.VpnConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Dark Tunnel VPN Pro", appName)
    }

    @Test
    fun testConfigSerialization_toJsonAndFromJson() {
        val original = VpnConfig(
            id = "cfg-test",
            name = "Custom Dhaka Node",
            protocol = ServerProtocol.SSH_WEBSOCKET,
            host = "bd1.darktunnelvpn.net",
            port = 443,
            username = "myuser",
            password = "mypassword",
            wsPayloadPath = "/dark-ws",
            customPayload = "GET / HTTP/1.1[crlf]Host: [host][crlf]Upgrade: websocket[crlf][crlf]",
            sniServerName = "bd1.darktunnelvpn.net",
            isDefault = true,
            isPasswordProtected = true,
            configPassphrase = "secret123"
        )
        val json = original.toJson()
        val restored = VpnConfig.fromJson(json)

        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(original.host, restored.host)
        assertEquals(original.port, restored.port)
        assertEquals(original.username, restored.username)
        assertEquals(original.password, restored.password)
        assertEquals(original.wsPayloadPath, restored.wsPayloadPath)
        assertEquals(original.customPayload, restored.customPayload)
        assertEquals(original.sniServerName, restored.sniServerName)
        assertEquals(original.isDefault, restored.isDefault)
        assertEquals(original.isPasswordProtected, restored.isPasswordProtected)
        assertEquals(original.configPassphrase, restored.configPassphrase)
    }
}
