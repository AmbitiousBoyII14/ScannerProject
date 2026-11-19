package com.example.live

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.*
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.Toast

class LiveActivity:Activity(){
    private lateinit var web:WebView
    override fun onCreate(saved:Bundle?){super.onCreate(saved);applySettings();web=WebView(this);web.settings.apply{javaScriptEnabled=true;allowFileAccess=false;allowContentAccess=false;blockNetworkLoads=true;domStorageEnabled=false};web.webViewClient=object:WebViewClient(){override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest?)=true;override fun shouldInterceptRequest(view:WebView?,request:WebResourceRequest):WebResourceResponse{val path=request.url.path;return if(request.url.host=="app.invalid"&&path in listOf("/index.html","/style.css","/ui.js")){val mime=when(path){"/index.html"->"text/html";"/ui.js"->"text/javascript";else->"text/css"};WebResourceResponse(mime,"utf-8",assets.open("live$path"))}else WebResourceResponse("text/plain","utf-8",null)}};web.addJavascriptInterface(Bridge(),"Client");val frame=android.widget.FrameLayout(this);frame.setBackgroundColor(android.graphics.Color.WHITE);frame.addView(web);setContentView(frame);window.decorView.systemUiVisibility=window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        frame.setOnApplyWindowInsetsListener{v,i->if(Build.VERSION.SDK_INT>=30){val b=i.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout() or android.view.WindowInsets.Type.ime());v.setPadding(b.left,b.top,b.right,b.bottom)}else v.setPadding(i.systemWindowInsetLeft,i.systemWindowInsetTop,i.systemWindowInsetRight,i.systemWindowInsetBottom);i};frame.requestApplyInsets();web.loadUrl("https://app.invalid/index.html");if(!DebugFile.init(this)&&saved==null)web.post{askForFolder()}}
    private fun askForFolder(){
        android.app.AlertDialog.Builder(this).setTitle("Save Scanner/debug.txt?").setMessage("Choose or create Scanner in internal storage, or select Documents and Scanner will be created inside it. Debug logs include server addresses and connection results, never account passwords. Android requires your approval once.")
          .setPositiveButton("Choose folder"){_,_->try{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION),44)}catch(e:Exception){Toast.makeText(this,"No folder picker available",Toast.LENGTH_LONG).show()}}
          .setNegativeButton("Not now",null).show()
    }
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
    private val pins by lazy { getSharedPreferences("known_ssh_servers", MODE_PRIVATE) }
    private var enrolling=false // Accessed only on the UI thread.
    private fun enrollmentResult(message:String,ok:Boolean){
        enrolling=false
        LiveState.log(if(ok) "PROFILE" else "SSH",message)
        if(!isFinishing&&!isDestroyed)web.evaluateJavascript("sshResult("+org.json.JSONObject.quote(message)+",$ok)",null)
    }
    private fun enroll(raw:String){
        if(enrolling)return
        enrolling=true
        try{
            val profile=Profile.read(raw,forDiscovery=true);require(profile.isSsh && LiveState.editable())
            val endpoint=if(profile.mode=="SSH")"${profile.host}:${profile.port}" else profile.label+" · "+java.security.MessageDigest.getInstance("SHA-256").digest((profile.sni+"\n"+profile.path+"\n"+profile.payload).toByteArray()).take(8).joinToString(""){"%02x".format(it)}
            fun save(key:String){
                try{val j=org.json.JSONObject(raw).put("hostKey",key);LiveState.save(Profile.read(j.toString()));pins.edit().putString(endpoint,key).apply();enrollmentResult("SSH profile selected.",true)}
                catch(e:Exception){enrollmentResult("Could not save the profile. Stop active connections and check the fields.",false)}
            }
            val existing=pins.getString(endpoint,null)
            if(existing!=null){save(existing);return}
            Thread {
                try{LiveState.log("KEY","Fetching host key via ${profile.label} before authentication");val (key,fp)=SshTransport.discover(LiveState.resolve(profile),profile.port,profile.gateway{t,m->LiveState.log(t,m)})
                    runOnUiThread{if(!isFinishing&&!isDestroyed){
                        android.app.AlertDialog.Builder(this).setTitle("Trust this SSH server?")
                            .setMessage("${profile.label}\n\nKey type: ${key.substringBefore(' ')}\nFingerprint: $fp\n\nCompare this fingerprint with your provider before trusting it. No password has been sent.")
                            .setPositiveButton("Trust & save"){_,_->save(key)}.setNegativeButton("Cancel"){_,_->enrollmentResult("Server not trusted. No profile saved.",false)}
                            .setOnCancelListener{enrollmentResult("Server not trusted.",false)}.show()
                    }}
                }catch(e:Exception){runOnUiThread{enrollmentResult(failureReason(e,"No SSH identification or host key received via ${profile.mode}.")+" No password was sent.",false)}}
            }.start()
        }catch(e:Exception){enrollmentResult("Check the account format, server, port and username. Stop active sessions before saving.",false)}
    }
    inner class Bridge{
        @JavascriptInterface fun clearSessionLog()=LiveState.clearSessionLog()
        @JavascriptInterface fun copySessionLog(){val text=LiveState.sessionLogText();if(text.isEmpty())return;runOnUiThread{
            val clip=android.content.ClipData.newPlainText("Scanner VPN session log",text);clip.description.extras=android.os.PersistableBundle().apply{putBoolean("android.content.extra.IS_SENSITIVE",true)};getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(clip)
            Toast.makeText(this@LiveActivity,"Session log copied",Toast.LENGTH_SHORT).show()
        }}
        @JavascriptInterface fun copyAccount(value:String){if(value.isEmpty()||value.length>16384)return;runOnUiThread{
            val clip=android.content.ClipData.newPlainText("SSH account",value);if(Build.VERSION.SDK_INT>=24)clip.description.extras=android.os.PersistableBundle().apply{putBoolean("android.content.extra.IS_SENSITIVE",true)};getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(clip)
        }}
        @JavascriptInterface fun pasteAccount(){runOnUiThread{
            val clip=getSystemService(android.content.ClipboardManager::class.java).primaryClip
            val value=clip?.takeIf{it.itemCount>0}?.getItemAt(0)?.text?.toString()?.takeIf{it.length<=16384}
            if(value!=null)web.evaluateJavascript("receiveAccount("+org.json.JSONObject.quote(value)+")",null) else Toast.makeText(this@LiveActivity,"Clipboard has no account text",Toast.LENGTH_SHORT).show()
        }}
        @JavascriptInterface fun selectedAccount()=LiveState.active()?.accountLine().orEmpty()
        @JavascriptInterface fun validPayload(text:String,binary:Boolean)=runCatching{GatewayRequest.parse(text,"example.test",443,"/",binary);true}.getOrDefault(false)
        @JavascriptInterface fun stopFile(){DebugFile.disable()}
        @JavascriptInterface fun chooseFolder(){runOnUiThread{askForFolder()}}
        @JavascriptInterface fun enrollSsh(raw:String){runOnUiThread{enroll(raw)}}
        @JavascriptInterface fun snapshot()=LiveState.snapshot()
        @JavascriptInterface fun save(raw:String):Boolean=runCatching{LiveState.save(Profile.read(raw));true}.getOrDefault(false)
        @JavascriptInterface fun choose(key:String)=LiveState.select(key)
        @JavascriptInterface fun checks(all:Boolean)=LiveState.check(applicationContext,all)
        @JavascriptInterface fun checkList(text:String,mode:String)=LiveState.check(applicationContext,true,text,mode)
        @JavascriptInterface fun preferences()=org.json.JSONObject().apply {prefKeys.forEach{put(it,prefs.getBoolean(it,defaultValue(it)))}}.toString()
        @JavascriptInterface fun preference(key:String,value:Boolean){if(key in prefKeys){prefs.edit().putBoolean(key,value).apply();runOnUiThread{applySettings()}}}
        @JavascriptInterface fun importBatch(){runOnUiThread{try{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/*"),43)}catch(e:android.content.ActivityNotFoundException){Toast.makeText(this@LiveActivity,"No document picker installed",Toast.LENGTH_SHORT).show()}}}
        @JavascriptInterface fun cancel()=LiveState.cancel()
        @JavascriptInterface fun connect(){runOnUiThread{if(!LiveState.editable()||LiveState.active()==null)return@runOnUiThread;try{val consent=VpnService.prepare(this@LiveActivity);if(consent!=null)startActivityForResult(consent,42) else startTunnel()}catch(e:Exception){LiveState.log("ERROR","Android could not request VPN access")}}}
        @JavascriptInterface fun disconnect(){runOnUiThread{if(LiveState.status in listOf("Starting","Connected"))startService(Intent(this@LiveActivity,TunnelService::class.java).setAction("stop"))}}
    }
    private fun startTunnel(){if(!LiveState.editable()||LiveState.active()==null)return;LiveState.status="Starting";LiveState.log("START",LiveState.active()!!.label);try{val i=Intent(this,TunnelService::class.java).setAction("connect");if(Build.VERSION.SDK_INT>=26)startForegroundService(i) else startService(i)}catch(e:Exception){LiveState.status="Failed";LiveState.log("ERROR","Android blocked VPN startup")}}
    override fun onActivityResult(request:Int,result:Int,data:Intent?){super.onActivityResult(request,result,data);if(request==44&&result==RESULT_OK)data?.data?.let{uri->try{require(uri.authority=="com.android.externalstorage.documents");contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION);DebugFile.configure(uri)}catch(e:Exception){Toast.makeText(this,"Choose a folder on local device storage.",Toast.LENGTH_LONG).show()}};if(request==43 && result==RESULT_OK)data?.data?.let(::importDocument);if(request==42){if(result==RESULT_OK)startTunnel() else LiveState.log("STOP","VPN permission not granted")}}
    override fun onDestroy(){web.removeJavascriptInterface("Client");web.destroy();super.onDestroy()}
}
