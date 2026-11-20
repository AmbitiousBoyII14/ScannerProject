package com.example

import android.content.*
import android.view.*
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.live.LiveActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.*
import java.util.concurrent.*
import kotlin.concurrent.thread

/** Real Android clipboard, bundled WebView, JS bridge and gateway discovery, no external server. */
@RunWith(AndroidJUnit4::class)
class AccountFlowTest {
    @Test fun pasteConfigureAndObserveGatewayRefusal(){
        val inst=InstrumentationRegistry.getInstrumentation();val ctx=inst.targetContext
        ctx.getSharedPreferences("debug_storage",0).edit().putBoolean("disabled",true).commit()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand("appops set ${ctx.packageName} ACTIVATE_VPN allow")).use{it.readBytes()}
        val a=inst.startActivitySync(Intent(ctx,LiveActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun find(v:View):WebView? {if(v is WebView)return v;if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let{return it};return null}
        inst.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
        inst.runOnMainSync{find(a.window.decorView)?.apply{isFocusableInTouchMode=true;requestFocus()}}
        fun js(expr:String):String{val result=ArrayBlockingQueue<String>(1);inst.runOnMainSync{find(a.window.decorView)!!.evaluateJavascript(expr){result.offer(it?:"null")}};return requireNotNull(result.poll(5,TimeUnit.SECONDS))}
        fun until(expr:String){repeat(200){if(js(expr)=="true")return;Thread.sleep(100)};fail("UI condition: $expr; state="+js("JSON.stringify([document.readyState,document.title,typeof receiveAccount,typeof Client,document.hasFocus(),document.querySelector('#body .hint')?.textContent,document.getElementById('hint')?.textContent,document.getElementById('events')?.textContent?.slice(-220)])"))}
        try{ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use{server->
            val received=ArrayBlockingQueue<String>(1);server.soTimeout=60000
            thread(isDaemon=true){runCatching{server.accept().use{s->val r=s.getInputStream().bufferedReader();received.put(r.readLine());while(!r.readLine().isNullOrEmpty()){};s.getOutputStream().write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\n\r\n".toByteArray())}}}
            until("typeof receiveAccount==='function'")
            // Real clipboard → import review → native Xray JSON. Never connect to the example host.
            val link="vless://00000000-0000-4000-8000-000000000001@192.0.2.9:443/?type=ws&host=route.example&security=tls&sni=cert.example&path=%2Ffixture&allowInsecure=1#demo"
            inst.runOnMainSync{ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("share fixture",link))}
            assertEquals("8",js("document.querySelectorAll('#flags input').length"))
            js("document.querySelector('#plugins').click();[...document.querySelectorAll('#popup button')].find(b=>b.textContent==='V2ray Settings').click();[...document.querySelectorAll('#detailTools button')].find(b=>b.textContent==='Paste').click();document.querySelector('.pasteLink').click()")
            until("document.getElementById('coreShare')?.value.endsWith('#demo')===true")
            js("document.querySelector('#apply').click()");until("document.querySelector('#error').textContent.includes('verified TLS')")
            js("document.querySelector('#fields input[type=checkbox]').click();document.querySelector('#apply').click();document.querySelector('#detailTools button').click()")
            until("document.querySelector('#content pre')!==null")
            val config=org.json.JSONObject(org.json.JSONTokener(js("document.querySelector('#content pre').textContent")).nextValue() as String)
            val outbound=config.getJSONArray("outbounds").getJSONObject(0);val stream=outbound.getJSONObject("streamSettings")
            assertEquals("192.0.2.9",outbound.getJSONObject("settings").getJSONArray("vnext").getJSONObject(0).getString("address"));assertEquals("/fixture",stream.getJSONObject("wsSettings").getString("path"));assertEquals("route.example",stream.getJSONObject("wsSettings").getString("host"));assertEquals("cert.example",stream.getJSONObject("tlsSettings").getString("serverName"))
            js("document.querySelector('#back').click();document.querySelector('#burger').click();[...document.querySelectorAll('#drawer button')].find(b=>b.textContent==='SSH Settings').click()")
            val account="127.0.0.1:${server.localPort}@fixture:a@:https://example.test/password"
            inst.runOnMainSync{ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("fixture",account))}
            js("[...document.querySelectorAll('#content button')].find(b=>b.textContent==='Paste').click()");until("document.getElementById('sshLine').value.endsWith('example.test/password')")
            js("document.getElementById('remember').click();document.querySelector('#content .apply').click()")
            fun drawer(name:String){js("document.querySelector('#burger').click();[...document.querySelectorAll('#drawer button')].find(b=>b.textContent==='$name').click()")}
            drawer("Payload");js("document.querySelector('#fields select').value='WebSocket or raw SSH';document.querySelector('#fields textarea').value='GET /fixture HTTP/1.1[crlf]Host: [host][crlf]Connection: Upgrade[crlf]Upgrade: websocket';document.querySelector('#apply').click()")
            drawer("SNI");js("document.querySelector('#fields input').value='localhost';document.querySelector('#apply').click()")
            // Local 403 fixture uses HTTP without TLS; changing SSL preserves payload framing.
            js("[...document.querySelectorAll('#flags input')].find(i=>i.value==='SSL').click()")
            drawer("DNS Custom");js("document.querySelectorAll('#fields input')[1].value='9.9.9.9';document.querySelector('#apply').click()")
            drawer("UDPGW SSH");js("document.querySelector('#apply').click();document.querySelector('#connect').click()")
            assertEquals("GET /fixture HTTP/1.1",received.poll(15,TimeUnit.SECONDS));until("document.getElementById('events').textContent.includes('HTTP 403')");assertEquals("false",js("document.getElementById('events').textContent.includes('example.test/password')"))
            js("document.getElementById('copy').click()")
            var copied="";for(n in 0..30){inst.runOnMainSync{copied=ctx.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text.toString()};if("HTTP 403" in copied)break;Thread.sleep(100)}
            assertTrue(copied.contains("HTTP 403"));assertFalse(copied.contains("example.test/password"))
            js("document.querySelector('#plugins').click();document.querySelector('#apply').click()");until("!document.getElementById('events').textContent.includes('HTTP 403')")
            js("location.reload()");until("typeof receiveAccount==='function' && document.getElementById('account').value.endsWith('example.test/password')");drawer("SSH Settings");until("document.getElementById('remember').checked");js("document.getElementById('remember').click()");until("Client.rememberedSsh()==='' ")
            // No HTTPS server is bound on the emulator loopback's default port. The direct
            // website check must report a network failure, never reuse the saved SSH login.
            js("document.querySelector('#back').click()");drawer("Response checker");js("document.querySelector('#targets').value='127.0.0.1\\ninvalid:123\\n127.0.0.1';document.querySelector('#scan').click()")
            until("document.querySelector('#progress').textContent.includes('1/1 completed') && !document.querySelector('#report').hidden")
            assertTrue(js("document.querySelector('#progress').textContent").contains("Skipped 1 invalid"))
            val report=java.io.File(ctx.filesDir,"output.txt").readText();assertTrue(report.contains("[Failed · 1]"));assertFalse(report.contains("example.test/password"))
            // Import-only Batch returns to the home profile; Connect sends a tagged profile to Log.
            js("document.querySelector('[data-tab=batch]').click()")
            assertEquals("1",js("document.querySelectorAll('#batch button').length"))
            js("receiveBatch('outside.example');document.querySelector('#account').value='\$\$:22267@fixture:example-only';cfg.sshSni='localhost';F().V2ray=false;F().SSL=true;F()['Use Payload']=false;document.querySelector('#connect').click()")
            until("!document.querySelector('#log').hidden && document.querySelector('#runStatus').textContent.includes('1/1') && !document.querySelector('#batchReport').hidden")
            assertTrue(js("document.querySelector('#events').textContent").contains("SKIPPED"));assertFalse(js("document.querySelector('#events').textContent").contains("example-only"))


        }}finally{inst.runOnMainSync{a.finish()}}
    }
}
