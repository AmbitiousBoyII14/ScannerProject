package com.example.live

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
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
        // Exercise the production service start/stop, not only the isolated delay checker.
        val ctx=inst.targetContext
        fun shell(cmd:String)=ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(cmd)).bufferedReader().use{it.readText()}
        fun proofFromShellUid():Boolean {
            val fds=inst.uiAutomation.executeShellCommandRw("toybox nc -w 4 192.0.2.148 22269")
            return ParcelFileDescriptor.AutoCloseInputStream(fds[0]).use { response ->
                ParcelFileDescriptor.AutoCloseOutputStream(fds[1]).use { output ->
                    runCatching {output.write("GET /proof HTTP/1.0\r\nHost: fixture\r\n\r\n".toByteArray());output.flush()}
                    response.bufferedReader().readLine()?.contains("204 No Content")==true
                }
            }
        }
        assertFalse("Proof socket should be inaccessible without SSH",proofFromShellUid())
        shell("appops set ${ctx.packageName} ACTIVATE_VPN allow") // emulator equivalent of consent
        ctx.getSharedPreferences("debug_storage",0).edit().putBoolean("disabled",true).commit()
        val activity=inst.startActivitySync(Intent(ctx,LiveActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LiveActivity
        try{
            assertNull("Emulator VPN consent",VpnService.prepare(activity));LiveState.save(p);activity.Bridge().connect()
            for(n in 0..200){if(LiveState.status in setOf("Connected","Failed"))break;Thread.sleep(150)}
            assertEquals(LiveState.sessionLogText().takeLast(650),"Connected",LiveState.status)
            assertTrue("A second UID must receive the SSH-only 204 through the Android TUN",proofFromShellUid())
        }finally{
            activity.Bridge().disconnect();for(n in 0..50){if(LiveState.status in setOf("Idle","Failed"))break;Thread.sleep(100)}
            inst.runOnMainSync{activity.finish()}
        }

    }
}
