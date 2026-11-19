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
    @Test(timeout=100_000) fun rejectedRouteThenAuthenticatedForward(){
        assertTrue(CdnRanges.label(java.net.InetAddress.getByName("172.67.161.217")).contains("Cloudflare"))
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
        fun proofFromShellUid()=shell("sh /data/local/tmp/scanner-proof.sh").contains("204 No Content")
        assertFalse("Proof socket should be inaccessible without SSH",proofFromShellUid())
        shell("appops set ${ctx.packageName} ACTIVATE_VPN allow") // emulator equivalent of consent
        ctx.getSharedPreferences("debug_storage",0).edit().putBoolean("disabled",true).commit()
        val activity=inst.startActivitySync(Intent(ctx,LiveActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LiveActivity
        try{
            // Reachability checks are independent of any saved login or running VPN.
            val bridge=activity.Bridge();val oldPolicy=CheckPolicy.read(ctx)
            try{
                assertTrue(bridge.setConnectionPolicy(4,true,1));assertEquals(CheckPolicy(4,true,1),CheckPolicy.read(ctx))
                assertTrue(bridge.checkList("localhost:22267\n127.0.0.1:1","SSH WS TLS"))
                for(n in 0..160){if(!LiveState.batchRunning)break;Thread.sleep(100)}
                val batch=JSONObject(bridge.snapshot());assertTrue(batch.getJSONArray("results").getString(0).contains("Non-CDN"));assertEquals(2,batch.getInt("completed"));val rows=batch.getJSONArray("results")
                assertTrue(rows.toString(),rows.getString(0).startsWith("REACHABLE"));assertTrue(rows.getString(1).startsWith("FAILED"));assertTrue(batch.getJSONArray("checks").toString().contains("retry 1 of 1"))
            }finally{oldPolicy.save(ctx)}
            assertNull("Emulator VPN consent",VpnService.prepare(activity));LiveState.save(p)
            val id="${p.host}:${p.port}";val pins=ctx.getSharedPreferences("known_ssh_servers",0);HostPins.forget(ctx,id)
            fun tapTrust(title:String,button:String){
                repeat(150){
                    val root=inst.uiAutomation.rootInActiveWindow
                    if(root?.findAccessibilityNodeInfosByText(title)?.isNotEmpty()==true){
                        val node=root.findAccessibilityNodeInfosByText(button).firstOrNull{it.isClickable}
                        if(node?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)==true)return
                    };Thread.sleep(100)
                };fail("No SSH trust dialog: $title; "+LiveState.sessionLogText().takeLast(420))
            }
            activity.Bridge().connect();tapTrust("Trust SSH host", "Cancel")
            for(n in 0..100){if(LiveState.status=="Failed")break;Thread.sleep(100)}
            assertEquals("Failed",LiveState.status);assertFalse(pins.contains(id))
            val legacy="${p.label} · legacy-route";pins.edit().putString(legacy,j.getString("otherKey")).commit()
            LiveState.clearSessionLog();activity.Bridge().connect();tapTrust("Host key changed", "Accept New Key")
            for(n in 0..200){if(LiveState.status in setOf("Connected","Failed"))break;Thread.sleep(150)}
            assertEquals(LiveState.sessionLogText().takeLast(650),"Connected",LiveState.status)
            assertEquals(k.first,pins.getString(id,null));assertFalse(pins.contains(legacy))
            assertEquals("Trust and authenticate on one TCP connection",1,LiveState.sessionLogText().lines().count{it.contains("  TCP  Connected to")})
            assertTrue("A second UID must receive the SSH-only 204 through the Android TUN",proofFromShellUid())
        }finally{
            activity.Bridge().disconnect();for(n in 0..50){if(LiveState.status in setOf("Idle","Failed"))break;Thread.sleep(100)}
            inst.runOnMainSync{activity.finish()}
        }

    }
}
