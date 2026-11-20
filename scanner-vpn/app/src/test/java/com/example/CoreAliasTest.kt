package com.example.live

import org.junit.Assert.*
import org.junit.Test

class CoreAliasTest {
    @Test fun oneFixedGatewayAndOnlyCertificateNames(){
        val t=CoreAliasTemplate(obj("mode" to "VMess WS TLS","host" to "gateway.example","secret" to "00000000-0000-4000-8000-000000000001","port" to 8443,"sni" to "\$\$","wsHost" to "\$\$","path" to "/\$\$/stream").toString());val names=setOf("one.example","two.example")
        for(n in names){val row=requireNotNull(t.approved(n,names));assertEquals(n,row.sni);assertEquals(n,row.wsHost);assertEquals("/$n/stream",row.path);assertEquals("gateway.example",row.host);assertEquals(8443,row.port)}
        assertNull(t.approved("other.example",names));assertNull(t.approved("other.example",setOf("*.example")))
    }
}
