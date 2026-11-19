package com.example.live

import org.junit.Assert.*
import org.junit.Test

class GatewayRequestTest {
    private val base="GET /private?session=test HTTP/1.1[crlf]Host: [host]:[port][crlf]Connection: Upgrade[crlf]Upgrade: websocket"
    @Test fun parseOneUpgrade(){val r=GatewayRequest.parse(base,"example.test",443,"/",true);assertEquals("example.test:443",r.authority);assertEquals("/private?session=test",r.path)}
    @Test fun rejectAmbiguousFraming(){for(extra in listOf("[crlf]Host: another.example","[crlf]Transfer-Encoding: chunked","[crlf][crlf]GET / HTTP/1.1"))assertThrows(IllegalArgumentException::class.java){GatewayRequest.parse(base+extra,"example.test",443,"/",true)}}
}
