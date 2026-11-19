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
    override fun onCreate(saved:Bundle?){super.onCreate(saved);window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);web=WebView(this);web.settings.apply{javaScriptEnabled=true;allowFileAccess=false;allowContentAccess=false;blockNetworkLoads=true;domStorageEnabled=false};web.webViewClient=object:WebViewClient(){override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest?)=true;override fun shouldInterceptRequest(view:WebView?,request:WebResourceRequest):WebResourceResponse{val path=request.url.path;return if(request.url.host=="app.invalid"&&path in listOf("/index.html","/style.css","/ui.js")){val mime=when(path){"/index.html"->"text/html";"/ui.js"->"text/javascript";else->"text/css"};WebResourceResponse(mime,"utf-8",assets.open("live$path"))}else WebResourceResponse("text/plain","utf-8",null)}};web.addJavascriptInterface(Bridge(),"Client");setContentView(web);web.setOnApplyWindowInsetsListener{v,i->v.setPadding(i.systemWindowInsetLeft,i.systemWindowInsetTop,i.systemWindowInsetRight,i.systemWindowInsetBottom);i};web.loadUrl("https://app.invalid/index.html")}
    inner class Bridge{
        @JavascriptInterface fun snapshot()=LiveState.snapshot()
        @JavascriptInterface fun save(raw:String):Boolean=runCatching{LiveState.save(Profile.read(raw));true}.getOrDefault(false)
        @JavascriptInterface fun choose(key:String)=LiveState.select(key)
        @JavascriptInterface fun checks(all:Boolean)=LiveState.check(applicationContext,all)
        @JavascriptInterface fun cancel()=LiveState.cancel()
        @JavascriptInterface fun connect(){runOnUiThread{if(!LiveState.editable()||LiveState.active()==null)return@runOnUiThread;try{val consent=VpnService.prepare(this@LiveActivity);if(consent!=null)startActivityForResult(consent,42) else startTunnel()}catch(e:Exception){LiveState.log("ERROR","Android could not request VPN access")}}}
        @JavascriptInterface fun disconnect(){runOnUiThread{if(LiveState.status in listOf("Starting","Connected"))startService(Intent(this@LiveActivity,TunnelService::class.java).setAction("stop"))}}
    }
    private fun startTunnel(){if(!LiveState.editable()||LiveState.active()==null)return;LiveState.status="Starting";LiveState.log("START",LiveState.active()!!.label);try{val i=Intent(this,TunnelService::class.java).setAction("connect");if(Build.VERSION.SDK_INT>=26)startForegroundService(i) else startService(i)}catch(e:Exception){LiveState.status="Failed";LiveState.log("ERROR","Android blocked VPN startup")}}
    override fun onActivityResult(request:Int,result:Int,data:Intent?){super.onActivityResult(request,result,data);if(request==42){if(result==RESULT_OK)startTunnel() else LiveState.log("STOP","VPN permission not granted")}}
    override fun onDestroy(){web.removeJavascriptInterface("Client");web.destroy();super.onDestroy()}
}
