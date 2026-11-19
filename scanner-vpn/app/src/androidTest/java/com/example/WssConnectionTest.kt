package com.example.live

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import libv2ray.Libv2ray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Connect the installed Android client to a disposable TLS/WebSocket-to-OpenSSH gateway.
 * Only the WSS port is reversed to the emulator; the proof service isn't accessible directly. */
@RunWith(AndroidJUnit4::class)
class WssConnectionTest {
    @Test fun rejectedRouteThenAuthenticatedForward(){
        val inst=InstrumentationRegistry.getInstrumentation();val assets=inst.context.assets
        assumeTrue("Run scripts/test-and-build.sh --device to provision the fixture", "ci_gateway.json" in assets.list("").orEmpty())
        val j=assets.open("ci_gateway.json").bufferedReader().use{JSONObject(it.readText())}
        j.put("mode","SSH WS TLS").put("host","127.0.0.1").put("port",22267).put("sni","localhost")
        fun profile(path:String,sub:String)=Profile.read(j.put("payload","GET $path HTTP/1.1\nHost: [host]\nConnection: Upgrade\nUpgrade: websocket$sub").toString())
        val ip="127.0.0.1";val sub="\nSec-WebSocket-Protocol: binary"
        for((p,expected) in listOf(profile("/",sub) to "HTTP 404",profile("/ssh","") to "HTTP 426")){
            val e=assertThrows(Exception::class.java){SshTransport.discover(ip,p.port,p.gateway{_,_->})};assertTrue(failureReason(e,"unknown").contains(expected))
        }
        val p=profile("/ssh",sub);val k=SshTransport.discover(ip,p.port,p.gateway{_,_->});assertEquals(j.getString("hostKey").split(' ').take(2).joinToString(" "),k.first)
        Engine.init(inst.targetContext)
        p.openSsh(ip)!!.use{ssh->assertTrue("HTTP via Xray → SOCKS → SSH/WSS", Libv2ray.measureOutboundDelay(p.config(ip,ssh),"http://127.0.0.1:22268/proof")>=0)}
    }
}
