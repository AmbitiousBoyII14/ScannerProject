package com.example.live

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LiveProfileTest {
    @Test fun exportVlessOutboundsForLoopbackTest(){
        val dir=System.getenv("XRAY_TEST_DIR");org.junit.Assume.assumeNotNull(dir)
        for((i,mode) in listOf("VLESS TLS","VLESS WS TLS").withIndex()){
            val raw=obj("mode" to mode,"host" to "127.0.0.1","sni" to "localhost","wsHost" to (if(i==1)"route.example.test" else ""),"port" to (22262+i),"secret" to "00000000-0000-4000-8000-000000000001","path" to "/fixture").toString()
            val template=CoreAliasTemplate(JSONObject(raw).put("sni","\$\$").toString());val row=requireNotNull(template.approved("localhost",setOf("localhost","route.example.test")));val cfg=JSONObject(row.config("127.0.0.1"));java.io.File(dir,"client$i.json").writeText(cfg.toString())
        }
    }
    @Test fun separateDialHostAndTlsName(){
        for(proto in listOf("VLESS","VMess","Trojan")){
            val p=Profile.read(obj("mode" to "$proto WS TLS","host" to "192.0.2.80","port" to 443,"wsHost" to "route.test","sni" to "cert.test","path" to "/fixture","mux" to true,"secret" to "00000000-0000-4000-8000-000000000001").toString())
            val out=JSONObject(p.config(p.host)).getJSONArray("outbounds").getJSONObject(0)
            val stream=out.getJSONObject("streamSettings");assertEquals("route.test",stream.getJSONObject("wsSettings").getString("host"));assertEquals("cert.test",stream.getJSONObject("tlsSettings").getString("serverName"));assertTrue(out.getJSONObject("mux").getBoolean("enabled"));assertEquals(proto.lowercase(),out.getString("protocol"))
        }
    }
    @Test fun nativeProfilesProduceExpectedSecureOutbound(){
        val modes=mapOf("VLESS TLS" to "vless","VLESS WS TLS" to "vless","VMess WS TLS" to "vmess","Trojan TLS" to "trojan","Shadowsocks" to "shadowsocks","SOCKS5 TLS" to "socks","HTTP CONNECT TLS" to "http")
        modes.forEach{(mode,protocol)->
            val raw=obj("mode" to mode,"host" to "node.example","port" to 443,"secret" to "00000000-0000-4000-8000-000000000001","user" to "test").toString()
            val outbound=JSONObject(Profile.read(raw).config("192.0.2.9")).getJSONArray("outbounds").getJSONObject(0)
            assertEquals(protocol,outbound.getString("protocol"));if(protocol!="shadowsocks"){val transport=outbound.getJSONObject("streamSettings");assertEquals("tls",transport.getString("security"));assertEquals("node.example",transport.getJSONObject("tlsSettings").getString("serverName"))}
        }
    }
}
