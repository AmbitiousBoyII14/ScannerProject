package com.example.live

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.*
import android.view.WindowManager
import android.webkit.*
import android.widget.Toast

class LiveActivity:Activity(){
    private lateinit var web:WebView
    override fun onCreate(saved:Bundle?){super.onCreate(saved);applySettings();web=WebView(this);web.settings.apply{javaScriptEnabled=true;allowFileAccess=false;allowContentAccess=false;blockNetworkLoads=true;domStorageEnabled=false};web.webViewClient=object:WebViewClient(){override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest?)=true;override fun shouldInterceptRequest(view:WebView?,request:WebResourceRequest):WebResourceResponse{val path=request.url.path;return if(request.url.host=="app.invalid"&&path in listOf("/index.html","/style.css","/ui.js")){val mime=when(path){"/index.html"->"text/html";"/ui.js"->"text/javascript";else->"text/css"};WebResourceResponse(mime,"utf-8",assets.open("live$path"))}else WebResourceResponse("text/plain","utf-8",null)}};web.addJavascriptInterface(Bridge(),"Client");setContentView(web);web.setOnApplyWindowInsetsListener{v,i->v.setPadding(i.systemWindowInsetLeft,i.systemWindowInsetTop,i.systemWindowInsetRight,i.systemWindowInsetBottom);i};web.loadUrl("https://app.invalid/index.html")}
    private val prefs by lazy { getSharedPreferences("display",MODE_PRIVATE) }
    private fun applySettings(){
        fun flag(key:String,mask:Int){if(prefs.getBoolean(key,false))window.addFlags(mask) else window.clearFlags(mask)}
        flag("privateScreen",WindowManager.LayoutParams.FLAG_SECURE)
        flag("keepAwake",WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    private val prefKeys=listOf("privateScreen","keepAwake","followLogs","showRates")
    private fun defaultValue(key:String)=key=="followLogs"||key=="showRates"
    private fun importDocument(uri:android.net.Uri){
        Thread {
            try {
                val text=contentResolver.openInputStream(uri)!!.use { stream ->
                    val out=java.io.ByteArrayOutputStream();val buf=ByteArray(4096)
                    while(true){val n=stream.read(buf);if(n<0)break;require(out.size()+n<=65536);out.write(buf,0,n)}
                    out.toString("UTF-8").removePrefix("\uFEFF")
                }
                runOnUiThread{if(!isDestroyed)web.evaluateJavascript("receiveBatch("+org.json.JSONObject.quote(text)+")",null)}
            } catch(e:Exception){runOnUiThread{Toast.makeText(this,"Could not import. Use a UTF-8 text file under 64 KiB.",Toast.LENGTH_LONG).show()}}
        }.start()
    }
    inner class Bridge{
        @JavascriptInterface fun snapshot()=LiveState.snapshot()
        @JavascriptInterface fun save(raw:String):Boolean=runCatching{LiveState.save(Profile.read(raw));true}.getOrDefault(false)
        @JavascriptInterface fun choose(key:String)=LiveState.select(key)
        @JavascriptInterface fun checks(all:Boolean)=LiveState.check(applicationContext,all)
        @JavascriptInterface fun checkList(text:String)=LiveState.check(applicationContext,true,text)
        @JavascriptInterface fun preferences()=org.json.JSONObject().apply {prefKeys.forEach{put(it,prefs.getBoolean(it,defaultValue(it)))}}.toString()
        @JavascriptInterface fun preference(key:String,value:Boolean){if(key in prefKeys){prefs.edit().putBoolean(key,value).apply();runOnUiThread{applySettings()}}}
        @JavascriptInterface fun importBatch(){runOnUiThread{try{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/*"),43)}catch(e:android.content.ActivityNotFoundException){Toast.makeText(this@LiveActivity,"No document picker installed",Toast.LENGTH_SHORT).show()}}}
        @JavascriptInterface fun cancel()=LiveState.cancel()
        @JavascriptInterface fun connect(){runOnUiThread{if(!LiveState.editable()||LiveState.active()==null)return@runOnUiThread;try{val consent=VpnService.prepare(this@LiveActivity);if(consent!=null)startActivityForResult(consent,42) else startTunnel()}catch(e:Exception){LiveState.log("ERROR","Android could not request VPN access")}}}
        @JavascriptInterface fun disconnect(){runOnUiThread{if(LiveState.status in listOf("Starting","Connected"))startService(Intent(this@LiveActivity,TunnelService::class.java).setAction("stop"))}}
    }
    private fun startTunnel(){if(!LiveState.editable()||LiveState.active()==null)return;LiveState.status="Starting";LiveState.log("START",LiveState.active()!!.label);try{val i=Intent(this,TunnelService::class.java).setAction("connect");if(Build.VERSION.SDK_INT>=26)startForegroundService(i) else startService(i)}catch(e:Exception){LiveState.status="Failed";LiveState.log("ERROR","Android blocked VPN startup")}}
    override fun onActivityResult(request:Int,result:Int,data:Intent?){super.onActivityResult(request,result,data);if(request==43 && result==RESULT_OK)data?.data?.let(::importDocument);if(request==42){if(result==RESULT_OK)startTunnel() else LiveState.log("STOP","VPN permission not granted")}}
    override fun onDestroy(){web.removeJavascriptInterface("Client");web.destroy();super.onDestroy()}
}
