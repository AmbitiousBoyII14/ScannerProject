package com.example.live

import java.nio.ByteBuffer
import org.java_websocket.drafts.Draft
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.enums.HandshakeState
import org.java_websocket.handshake.*
import org.java_websocket.protocols.Protocol

/** Observe the status before the library discards non-101 responses. Never expose response
 * headers, URLs, bodies or remote reason phrases. The normal RFC 6455 validation still runs. */
internal class GatewayDraft(private val binaryProtocol:Boolean,private val failure:(String)->Unit):
    Draft_6455(emptyList(),if(binaryProtocol) listOf(Protocol("binary")) else listOf(Protocol("")),1048576){
    override fun copyInstance():Draft=GatewayDraft(binaryProtocol,failure)
    override fun translateHandshake(buf:ByteBuffer):Handshakedata {
        val copy=buf.duplicate();if(copy.remaining()>=13){
            val prefix=ByteArray(13);copy.get(prefix)
            val code=Regex("HTTP/1\\.[01] ([0-9]{3})[ \\r]").matchEntire(String(prefix,Charsets.US_ASCII))?.groupValues?.get(1)
            if(code!=null&&code!="101")failure(when(code){
                "200"->"HTTP 200 · gateway returned a page, not a WebSocket upgrade. Check the provider's Host and GET path."
                "404"->"HTTP 404 · WebSocket route not found. Check the provider's Host and GET path."
                "401","403"->"HTTP $code · gateway refused the upgrade before SSH authentication."
                else->"HTTP $code · expected 101 Switching Protocols."
            })
        }
        return super.translateHandshake(buf)
    }
    override fun acceptHandshakeAsClient(req:ClientHandshake,res:ServerHandshake):HandshakeState {
        val accepted=super.acceptHandshakeAsClient(req,res)
        if(accepted!=HandshakeState.MATCHED)failure(when{
            !res.hasFieldValue("Sec-WebSocket-Accept")->"HTTP 101 without Sec-WebSocket-Accept · not a valid binary WebSocket. Check whether the provider specifies raw SSH after upgrade."
            else->"HTTP 101 received but WebSocket validation failed (upgrade headers, accept key or subprotocol)."
        });return accepted
    }
}
