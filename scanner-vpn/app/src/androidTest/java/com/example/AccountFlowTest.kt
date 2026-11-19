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
        fun until(expr:String){repeat(200){if(js(expr)=="true")return;Thread.sleep(100)};fail("UI condition: $expr; state="+js("JSON.stringify([document.readyState,document.title,typeof receiveAccount,typeof Client,document.hasFocus(),document.getElementById('hint')?.textContent,document.getElementById('events')?.textContent?.slice(-220)])"))}
        try{ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use{server->
            val received=ArrayBlockingQueue<String>(1);server.soTimeout=60000
            thread(isDaemon=true){runCatching{server.accept().use{s->val r=s.getInputStream().bufferedReader();received.put(r.readLine());while(!r.readLine().isNullOrEmpty()){};s.getOutputStream().write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\n\r\n".toByteArray())}}}
            until("typeof receiveAccount==='function'")
            // Real clipboard → import review → native Xray JSON. Never connect to the example host.
            val link="vless://00000000-0000-4000-8000-000000000001@192.0.2.9:443/?type=ws&host=route.example&security=tls&sni=cert.example&path=%2Ffixture&allowInsecure=1#demo"
            inst.runOnMainSync{ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("share fixture",link))}
            js("document.querySelectorAll('#flags input')[7].click();document.querySelector('#edit').click();document.querySelector('#body .tools button').click();document.querySelector('#body .dismiss').click()")
            until("document.getElementById('coreShare')?.value.endsWith('#demo')===true")
            js("document.querySelector('#body .apply').click()");until("document.querySelector('#body label').hidden===false")
            js("document.querySelector('#body input').click();document.querySelector('#body .apply').click();document.querySelectorAll('#body .tools button')[1].click()")
            until("document.querySelector('#body pre')!==null")
            val config=org.json.JSONObject(org.json.JSONTokener(js("document.querySelector('#body pre').textContent")).nextValue() as String)
            val outbound=config.getJSONArray("outbounds").getJSONObject(0);val stream=outbound.getJSONObject("streamSettings")
            assertEquals("192.0.2.9",outbound.getJSONObject("settings").getJSONArray("vnext").getJSONObject(0).getString("address"));assertEquals("/fixture",stream.getJSONObject("wsSettings").getString("path"));assertEquals("route.example",stream.getJSONObject("wsSettings").getString("host"));assertEquals("cert.example",stream.getJSONObject("tlsSettings").getString("serverName"))
            js("document.querySelector('#dismiss').click();document.querySelectorAll('#flags input')[7].click()")
            val account="127.0.0.1:${server.localPort}@fixture:a@:https://example.test/password"
            inst.runOnMainSync{ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("fixture",account))}
            js("document.getElementById('paste').click()");until("document.getElementById('account').value.endsWith('example.test/password')");assertEquals("\"text\"",js("document.getElementById('account').type"))
            js("document.querySelector('[data-tool=payload]').click()");js("document.querySelector('#body select').value='ws';document.querySelector('#body textarea').value='GET /fixture HTTP/1.1[crlf]Host: [host][crlf]Connection: Upgrade[crlf]Upgrade: websocket';document.querySelector('#body .apply').click()")
            until("!document.getElementById('sheet').open")
            js("document.querySelector('[data-tool=sni]').click();document.querySelector('#body input').value='localhost';document.querySelector('#body .apply').click()");until("document.getElementById('profile').textContent==='SSH WS TLS'")
            js("document.querySelectorAll('#flags input')[2].click()");until("document.getElementById('profile').textContent==='SSH WS+Raw TLS'")
            // Local 403 fixture is deliberately plain HTTP.
            js("document.querySelectorAll('#flags input')[1].click();document.querySelector('[data-tool=dns]').click();document.querySelectorAll('#body input')[1].value='9.9.9.9';document.querySelector('#body .apply').click()");js("document.querySelector('[data-tool=udp]').click();document.querySelector('#body .apply').click();document.getElementById('remember').click();document.getElementById('connect').click()")
            assertEquals("GET /fixture HTTP/1.1",received.poll(15,TimeUnit.SECONDS));until("document.getElementById('events').textContent.includes('HTTP 403')");assertEquals("false",js("document.getElementById('events').textContent.includes('example.test/password')"))
            js("document.getElementById('copyLog').click()")
            var copied="";for(n in 0..30){inst.runOnMainSync{copied=ctx.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text.toString()};if("HTTP 403" in copied)break;Thread.sleep(100)}
            assertTrue(copied.contains("HTTP 403"));assertFalse(copied.contains("example.test/password"))
            js("document.getElementById('clearLog').click();document.querySelector('#body .apply').click()");until("document.getElementById('copyLog').disabled && !document.getElementById('events').textContent.includes('HTTP 403')")
            js("location.reload()");until("typeof receiveAccount==='function' && document.getElementById('remember').checked && document.getElementById('account').value.endsWith('example.test/password')");js("document.getElementById('remember').click()");until("Client.rememberedSsh()==='' ")


        }}finally{inst.runOnMainSync{a.finish()}}
    }
}
