package com.example.live

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/** Bundled, offline preview. The VPN/network bridge is deliberately not installed here. */
class BatchLabActivity : Activity() {
    private lateinit var web: WebView
    private var pending: String? = null

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        if (getSharedPreferences("display", MODE_PRIVATE).getBoolean("privateScreen", false))
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        web = WebView(this)
        web.setBackgroundColor(0xff161917.toInt())
        web.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest) =
                if (request.url.toString()=="https://preview.invalid/lab.html") WebResourceResponse("text/html","UTF-8",assets.open("batch-lab.html")) else WebResourceResponse("text/plain", "UTF-8", null)
        }
        web.addJavascriptInterface(Files(), "PreviewFiles")
        val frame = android.widget.FrameLayout(this)
        frame.setBackgroundColor(0xff171b19.toInt())
        frame.addView(web)
        frame.setOnApplyWindowInsetsListener { v,i ->
            if(Build.VERSION.SDK_INT>=30){val b=i.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime());v.setPadding(b.left,b.top,b.right,b.bottom)}
            else v.setPadding(i.systemWindowInsetLeft,i.systemWindowInsetTop,i.systemWindowInsetRight,i.systemWindowInsetBottom)
            i
        }
        setContentView(frame)
        web.loadUrl("https://preview.invalid/lab.html")
    }

    private fun js(code: String) { runOnUiThread { if (!isDestroyed) web.evaluateJavascript(code, null) } }
    private fun notice(text: String) = js("document.getElementById('notice').textContent=" + JSONObject.quote(text))

    inner class Files {
        @JavascriptInterface fun importList() = pick(Intent.ACTION_OPEN_DOCUMENT, 71)
        @JavascriptInterface fun exportList(text: String) { runOnUiThread { pending = text; pick(Intent.ACTION_CREATE_DOCUMENT, 72) } }
    }
    private fun pick(action: String, request: Int) { runOnUiThread {
        val intent = Intent(action).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain")
        if (request == 72) intent.putExtra(Intent.EXTRA_TITLE, "output.txt")
        try { startActivityForResult(intent, request) } catch (_: android.content.ActivityNotFoundException) { pending=null; notice("No document picker available.") }
    } }
    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        val report = pending; pending = null
        val uri = data?.data ?: return
        if (result != RESULT_OK || uri.scheme != "content") return
        Thread {
            try {
                if (request == 71) {
                    val bytes = ByteArrayOutputStream()
                    requireNotNull(contentResolver.openInputStream(uri)).use { input ->
                        val buf=ByteArray(8192)
                        while(true){val n=input.read(buf);if(n<0)break;require(bytes.size()+n<=1024*1024);bytes.write(buf,0,n)}
                    }
                    js("receiveLabList("+JSONObject.quote(bytes.toString("UTF-8"))+")")
                } else if (request == 72) {
                    requireNotNull(report)
                    requireNotNull(contentResolver.openOutputStream(uri,"wt")).bufferedWriter().use { it.write(report) }
                    notice("Saved output.txt.")
                }
            } catch (_:Exception) { notice(if(request==71) "Could not import. Choose a text file up to 1 MiB." else "Could not save. Preview and export again.") }
        }.start()
    }
    override fun onDestroy() { web.removeJavascriptInterface("PreviewFiles"); web.destroy(); super.onDestroy() }
}
