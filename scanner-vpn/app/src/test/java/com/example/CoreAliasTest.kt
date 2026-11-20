package com.example.live

import org.junit.Assert.*
import org.junit.Test

class CoreAliasTest {
    @Test fun sshUpgradeFieldsUseOneCertifiedGateway(){
        val raw=obj("mode" to "SSH WS+Raw TLS","host" to "vpn.example","sni" to "\$\$","port" to 443,"user" to "demo","secret" to "literal:\$\$","payload" to "GET /\$\$ HTTP/1.1[crlf]Host: \$\$[crlf]Connection: Upgrade[crlf]Upgrade: websocket")
        val t=SshAccountTemplate(raw.toString());val allowed=setOf("vpn.example","row.example");val p=requireNotNull(t.approved("row.example",allowed));assertEquals("vpn.example",p.host);assertEquals("row.example",p.sni);assertTrue(p.payload.contains("GET /row.example"));assertTrue(p.accountLine().endsWith("literal:\$\$"));assertNull(t.approved("other.example",allowed));assertNull(SshAccountTemplate(raw.put("payload",p.payload.replace("Host: row.example","Host: other.example")).toString()).approved("row.example",allowed));assertThrows(IllegalArgumentException::class.java){SshAccountTemplate(raw.put("host","\$\$").toString())}
    }
    @Test fun oneFixedGatewayAndOnlyCertificateNames(){
        val t=CoreAliasTemplate(obj("mode" to "VMess WS TLS","host" to "gateway.example","secret" to "00000000-0000-4000-8000-000000000001","port" to 8443,"sni" to "\$\$","wsHost" to "\$\$","path" to "/\$\$/stream").toString());val names=setOf("one.example","two.example")
        for(n in names){val row=requireNotNull(t.approved(n,names));assertEquals(n,row.sni);assertEquals(n,row.wsHost);assertEquals("/$n/stream",row.path);assertEquals("gateway.example",row.host);assertEquals(8443,row.port)}
        assertNull(t.approved("other.example",names));assertNull(t.approved("other.example",setOf("*.example")))
    }
}
