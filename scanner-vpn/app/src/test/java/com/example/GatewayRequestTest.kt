package com.example.live

import org.junit.Assert.*
import org.junit.Test

class GatewayRequestTest {
    private val base="GET /private?session=test HTTP/1.1[crlf]Host: [host]:[port][crlf]Connection: Upgrade[crlf]Upgrade: websocket"
    @Test fun parseOneUpgrade(){val r=GatewayRequest.parse(base,"example.test",443,"/",true);assertEquals("example.test:443",r.authority);assertEquals("/private?session=test",r.path)}
    @Test fun binaryHandshakeOwnsNonceAndVersion(){
        val text=(base+"[crlf]Sec-WebSocket-Version: 13[crlf]Sec-WebSocket-Key: AAAAAAAAAAAAAAAAAAAAAA==").replace("[crlf]","\\[crlf\\]")
        assertTrue(GatewayRequest.parse(text,"example.test",443,"/",true).headers.isEmpty())
        assertThrows(IllegalArgumentException::class.java){GatewayRequest.parse(text.replace(": 13",": 12"),"example.test",443,"/",true)}
    }
    @Test fun rawDecisionDoesNotConsumeFirstSshBytes(){
        val r="HTTP/1.1 101 Switching Protocols\r\nConnection: Upgrade\r\n"
        val input=(r+"\r\nSSH-2.0-test").byteInputStream().buffered()
        assertTrue(mayUseRaw(readGatewayHeaders(input)));assertEquals("SSH-2.0-test",input.reader().readText())
        for (accept in listOf("", "wrong")) assertFalse(mayUseRaw((r+"sEc-WeBsOcKeT-AcCePt: $accept\r\n\r\n").toByteArray()))
        assertFalse(mayUseRaw("HTTP/1.1 403 Forbidden\r\n\r\n".toByteArray()))
        assertThrows(GatewayFailure::class.java){mayUseRaw((r+" Sec-WebSocket-Accept: wrong\r\n\r\n").toByteArray())}
    }
    @Test fun rejectAmbiguousFraming(){for(extra in listOf("[crlf]Host: another.example","[crlf]Transfer-Encoding: chunked","[crlf][crlf]GET / HTTP/1.1"))assertThrows(IllegalArgumentException::class.java){GatewayRequest.parse(base+extra,"example.test",443,"/",true)}}
}
