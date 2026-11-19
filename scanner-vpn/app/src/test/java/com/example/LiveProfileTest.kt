package com.example.live

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LiveProfileTest {
    @Test fun nativeProfilesProduceExpectedSecureOutbound(){
        val modes=mapOf("VLESS TLS" to "vless","VLESS WS TLS" to "vless","VMess WS TLS" to "vmess","Trojan TLS" to "trojan","Shadowsocks" to "shadowsocks","SOCKS5 TLS" to "socks","HTTP CONNECT TLS" to "http")
        modes.forEach{(mode,protocol)->
            val raw=obj("mode" to mode,"host" to "node.example","port" to 443,"secret" to "00000000-0000-4000-8000-000000000001","user" to "test").toString()
            val outbound=JSONObject(Profile.read(raw).config("192.0.2.9")).getJSONArray("outbounds").getJSONObject(0)
            assertEquals(protocol,outbound.getString("protocol"));if(protocol!="shadowsocks"){val transport=outbound.getJSONObject("streamSettings");assertEquals("tls",transport.getString("security"));assertEquals("node.example",transport.getJSONObject("tlsSettings").getString("serverName"))}
        }
    }
}
