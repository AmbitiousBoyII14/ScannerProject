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
    override fun onCreate(saved:Bundle?){super.onCreate(saved);applySettings();web=WebView(this);web.settings.apply{javaScriptEnabled=true;allowFileAccess=false;allowContentAccess=false;blockNetworkLoads=true;domStorageEnabled=false};web.webViewClient=object:WebViewClient(){override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest?)=true;override fun shouldInterceptRequest(view:WebView?,request:WebResourceRequest):WebResourceResponse{val path=request.url.path;return if(request.url.host=="app.invalid"&&path in listOf("/index.html","/style.css","/ui.js","/core-editor.js")){val mime=when(path){"/index.html"->"text/html";"/ui.js","/core-editor.js"->"text/javascript";else->"text/css"};WebResourceResponse(mime,"utf-8",assets.open("live$path"))}else WebResourceResponse("text/plain","utf-8",null)}};web.addJavascriptInterface(Bridge(),"Client");val frame=android.widget.FrameLayout(this);frame.setBackgroundColor(0xff17181c.toInt());web.setBackgroundColor(0xff17181c.toInt());window.statusBarColor=0xff17181c.toInt();frame.addView(web);setContentView(frame);window.decorView.systemUiVisibility=window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        frame.setOnApplyWindowInsetsListener{v,i->if(Build.VERSION.SDK_INT>=30){val b=i.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout() or android.view.WindowInsets.Type.ime());v.setPadding(b.left,b.top,b.right,b.bottom)}else v.setPadding(i.systemWindowInsetLeft,i.systemWindowInsetTop,i.systemWindowInsetRight,i.systemWindowInsetBottom);i};frame.requestApplyInsets();web.loadUrl("https://app.invalid/index.html");DebugFile.init(this)}
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
    private fun importDocument(uri:android.net.Uri,config:Boolean=false){
        Thread {
            try {
                val text=contentResolver.openInputStream(uri)!!.use { stream ->
                    val out=java.io.ByteArrayOutputStream();val buf=ByteArray(4096)
                    while(true){val n=stream.read(buf);if(n<0)break;require(out.size()+n<=(if(config)65536 else WebsiteInput.maxBytes));out.write(buf,0,n)}
                    out.toString("UTF-8").removePrefix("\uFEFF")
                }
                runOnUiThread{if(!isDestroyed)web.evaluateJavascript((if(config)"receiveConfig(" else "receiveBatch(")+org.json.JSONObject.quote(text)+")",null)}
            } catch(e:Exception){runOnUiThread{Toast.makeText(this,if(config)"Use a UTF-8 config under 64 KiB." else "Use a UTF-8 domain list up to 1 MiB.",Toast.LENGTH_LONG).show()}}
        }.start()
    }
    override fun onStart(){super.onStart();FloatingShortcut.screen(this,true)}
    override fun onStop(){FloatingShortcut.screen(this,false);super.onStop()}
    private fun syncShortcut(){if(!isDestroyed)web.evaluateJavascript("if(typeof syncFloating==='function')syncFloating()",null)}
    override fun onResume(){super.onResume();HostPins.foreground=this;syncShortcut()}
    private fun setShortcut(on:Boolean){
        if(on && !android.provider.Settings.canDrawOverlays(this)){
            try{startActivityForResult(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:$packageName")),45)}
            catch(_:android.content.ActivityNotFoundException){Toast.makeText(this,"Overlay permission settings unavailable",Toast.LENGTH_LONG).show();syncShortcut()};return
        }
        prefs.edit().putBoolean(FloatingShortcut.KEY,on).apply();FloatingShortcut.refresh(this);syncShortcut()
    }
    override fun onPause(){if(HostPins.foreground===this)HostPins.foreground=null;super.onPause()}
    private fun enroll(raw:String){
        val ok=runCatching {val p=Profile.read(raw,forDiscovery=true);require(p.isSsh);LiveState.save(p)}.isSuccess
        val message=if(ok)"SSH profile selected." else "Could not select SSH profile. Check the account fields."
        LiveState.log("PROFILE",message);web.evaluateJavascript("sshResult("+org.json.JSONObject.quote(message)+",$ok)",null)
    }
    private fun toast(msg:String){Toast.makeText(this,msg,Toast.LENGTH_LONG).show()}
    private var pendingExport:String?=null
    @Deprecated("Classic activity back navigation") override fun onBackPressed(){web.evaluateJavascript("typeof nativeBack==='function' && nativeBack()"){if(it!="true")moveTaskToBack(true)}}
    private val remembered by lazy { RememberedSsh(applicationContext) }
    inner class Bridge{
        @JavascriptInterface fun forgetHostKey(host:String,port:Int){runOnUiThread{
            if(!LiveState.editable() || port !in 1..65535 || !host.matches(Regex("[a-zA-Z0-9.-]{1,253}")))return@runOnUiThread
            val id="${host.lowercase()}:$port"
            android.app.AlertDialog.Builder(this@LiveActivity).setTitle("Forget pinned host key?").setMessage("Only the SSH key for $id will be removed. Your login and settings will be kept.")
                .setNegativeButton("Cancel",null).setPositiveButton("Forget key"){_,_->
                    val ok=LiveState.editable() && HostPins.forget(this@LiveActivity,id)
                    Toast.makeText(this@LiveActivity,if(ok)"Verify the fingerprint on your next connection." else "Could not forget key.",Toast.LENGTH_LONG).show()
                }.show()
        }}
        @JavascriptInterface fun rememberSsh(raw:String)=runCatching{remembered.save(raw);true}.getOrDefault(false)
        @JavascriptInterface fun rememberedSsh()=runCatching{remembered.load()}.getOrDefault("")
        @JavascriptInterface fun forgetSsh()=runCatching{remembered.forget();true}.getOrDefault(false)
        @JavascriptInterface fun clearSessionLog()=LiveState.clearSessionLog()
        @JavascriptInterface fun copySessionLog(){val text=LiveState.sessionLogText();if(text.isEmpty())return;runOnUiThread{
            val clip=android.content.ClipData.newPlainText("Scanner VPN session log",text);clip.description.extras=android.os.PersistableBundle().apply{putBoolean("android.content.extra.IS_SENSITIVE",true)};getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(clip)
            Toast.makeText(this@LiveActivity,"Session log copied",Toast.LENGTH_SHORT).show()
        }}
        @JavascriptInterface fun copyAccount(value:String){if(value.isEmpty()||value.length>16384)return;runOnUiThread{
            val clip=android.content.ClipData.newPlainText("SSH account",value);if(Build.VERSION.SDK_INT>=24)clip.description.extras=android.os.PersistableBundle().apply{putBoolean("android.content.extra.IS_SENSITIVE",true)};getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(clip)
        }}
        @JavascriptInterface fun pasteAccount()=paste(false)
        @JavascriptInterface fun pasteCoreLink()=paste(true)
        private fun paste(core:Boolean){runOnUiThread{
            val clip=getSystemService(android.content.ClipboardManager::class.java).primaryClip
            val value=clip?.takeIf{it.itemCount>0}?.getItemAt(0)?.text?.toString()?.takeIf{it.length<=16384}
            if(value!=null)web.evaluateJavascript((if(core)"receiveCoreLink(" else "receiveAccount(")+org.json.JSONObject.quote(value)+")",null) else Toast.makeText(this@LiveActivity,"Clipboard has no usable text",Toast.LENGTH_SHORT).show()
        }}
        @JavascriptInterface fun selectedCore()=LiveState.active()?.coreDraft().orEmpty()
        @JavascriptInterface fun previewCore(raw:String)=runCatching{val p=Profile.read(raw);require(!p.isSsh);org.json.JSONObject(p.config(p.host,seconds=CheckPolicy.read(this@LiveActivity).seconds)).toString(2)}.getOrDefault("")
        @JavascriptInterface fun selectedAccount()=LiveState.active()?.accountLine().orEmpty()
        @JavascriptInterface fun validPayload(text:String,binary:Boolean)=runCatching{GatewayRequest.parse(text,"example.test",443,"/",binary);true}.getOrDefault(false)
        @JavascriptInterface fun stopFile(){DebugFile.disable()}
        @JavascriptInterface fun chooseFolder(){runOnUiThread{askForFolder()}}
        @JavascriptInterface fun enrollSsh(raw:String){runOnUiThread{enroll(raw)}}
        @JavascriptInterface fun snapshot()=LiveState.snapshot()
        @JavascriptInterface fun save(raw:String):Boolean=runCatching{LiveState.save(Profile.read(raw));true}.getOrDefault(false)
        @JavascriptInterface fun choose(key:String)=LiveState.select(key)
        @JavascriptInterface fun checks(all:Boolean)=LiveState.check(applicationContext,all)
        @JavascriptInterface fun checkWebsites(text:String,template:String)=LiveState.websites(applicationContext,text,template)
        @JavascriptInterface fun hasReport()=java.io.File(filesDir,"output.txt").isFile
        @JavascriptInterface fun exportReport(){if(!hasReport())return;runOnUiThread{try{startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain").putExtra(Intent.EXTRA_TITLE,"output.txt"),49)}catch(_:Exception){toast("No document picker available")}}}
        @JavascriptInterface fun checkList(text:String,mode:String)=LiveState.check(applicationContext,true,text,mode)
        @JavascriptInterface fun floatingEnabled()=FloatingShortcut.enabled(this@LiveActivity)
        @JavascriptInterface fun setFloating(on:Boolean){runOnUiThread{setShortcut(on)}}
        @JavascriptInterface fun backgroundVpn(){runOnUiThread{if(LiveState.status=="Connected")moveTaskToBack(true)}}
        @JavascriptInterface fun notificationOptions(){runOnUiThread{
            if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED && (!prefs.getBoolean("askedNotices",false)||shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS))){
                prefs.edit().putBoolean("askedNotices",true).apply();requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),46)
            }else{val i=if(Build.VERSION.SDK_INT>=26)Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,packageName) else Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:$packageName"));runCatching{startActivity(i)}}
        }}
        @JavascriptInterface fun connectionPolicy()=CheckPolicy.read(this@LiveActivity).let{obj("seconds" to it.seconds,"auto" to it.auto,"retries" to it.retries).toString()}
        @JavascriptInterface fun setConnectionPolicy(seconds:Int,auto:Boolean,retries:Int)=runCatching{CheckPolicy(seconds,auto,retries).save(this@LiveActivity)}.getOrDefault(false)
        @JavascriptInterface fun preferences()=org.json.JSONObject().apply {prefKeys.forEach{put(it,prefs.getBoolean(it,defaultValue(it)))}}.toString()
        @JavascriptInterface fun preference(key:String,value:Boolean){if(key in prefKeys){prefs.edit().putBoolean(key,value).apply();runOnUiThread{applySettings()}}}
        @JavascriptInterface fun clearProfiles(){LiveState.clearProfiles()}
        @JavascriptInterface fun leaveApp(){runOnUiThread{if(LiveState.editable())finish()}}
        @JavascriptInterface fun validConfig(raw:String)=runCatching{Profile.read(raw,forDiscovery=true);true}.getOrDefault(false)
        @JavascriptInterface fun importConfig(){runOnUiThread{try{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),47)}catch(_:Exception){toast("No document picker available")}}}
        @JavascriptInterface fun writeDocument(kind:String,text:String){if(kind !in listOf("log","profile") || text.length>262144)return;runOnUiThread{
            val suffix=if(kind=="log")"txt" else "json";pendingExport=text
            try{startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(if(kind=="log")"text/plain" else "application/json").putExtra(Intent.EXTRA_TITLE,"Scanner-$kind.$suffix"),48)}catch(_:Exception){pendingExport=null;toast("No document picker available")}
        }}
        @JavascriptInterface fun importBatch(){runOnUiThread{try{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/*"),43)}catch(e:android.content.ActivityNotFoundException){Toast.makeText(this@LiveActivity,"No document picker installed",Toast.LENGTH_SHORT).show()}}}
        @JavascriptInterface fun cancel()=LiveState.cancel()
        @JavascriptInterface fun connect(){runOnUiThread{if(!LiveState.editable()||LiveState.active()==null)return@runOnUiThread;try{val consent=VpnService.prepare(this@LiveActivity);if(consent!=null)startActivityForResult(consent,42) else startTunnel()}catch(e:Exception){LiveState.log("ERROR","Android could not request VPN access")}}}
        @JavascriptInterface fun disconnect(){runOnUiThread{if(LiveState.status in listOf("Starting","Connected"))startService(Intent(this@LiveActivity,TunnelService::class.java).setAction("stop"))}}
    }
    private fun startTunnel(){if(!LiveState.editable()||LiveState.active()==null)return;LiveState.status="Starting";LiveState.log("START",LiveState.active()!!.label);try{val i=Intent(this,TunnelService::class.java).setAction("connect");if(Build.VERSION.SDK_INT>=26)startForegroundService(i) else startService(i)}catch(e:Exception){LiveState.status="Failed";LiveState.log("ERROR","Android blocked VPN startup")}}
    override fun onActivityResult(request:Int,result:Int,data:Intent?){super.onActivityResult(request,result,data);if(request==49&&result==RESULT_OK)data?.data?.let{uri->Thread{val ok=runCatching{java.io.File(filesDir,"output.txt").inputStream().use{src->requireNotNull(contentResolver.openOutputStream(uri,"wt")).use{src.copyTo(it)}}}.isSuccess;runOnUiThread{toast(if(ok)"Report exported" else "Could not export report")}}.start()};if(request==47&&result==RESULT_OK)data?.data?.let{importDocument(it,true)};if(request==48){val text=pendingExport;pendingExport=null;val uri=data?.data;if(result==RESULT_OK&&uri!=null&&text!=null)Thread{val ok=runCatching{requireNotNull(contentResolver.openOutputStream(uri,"wt")).bufferedWriter().use{it.write(text)}}.isSuccess;runOnUiThread{toast(if(ok)"File saved" else "Could not write file")}}.start()};if(request==45){val ok=android.provider.Settings.canDrawOverlays(this);setShortcut(ok);if(!ok)Toast.makeText(this,"Floating icon is off. Background VPN does not need this permission.",Toast.LENGTH_LONG).show()};if(request==44&&result==RESULT_OK)data?.data?.let{uri->try{require(uri.authority=="com.android.externalstorage.documents");contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION);DebugFile.configure(uri)}catch(e:Exception){Toast.makeText(this,"Choose a folder on local device storage.",Toast.LENGTH_LONG).show()}};if(request==43 && result==RESULT_OK)data?.data?.let(::importDocument);if(request==42){if(result==RESULT_OK)startTunnel() else LiveState.log("STOP","VPN permission not granted")}}
    override fun onDestroy(){web.removeJavascriptInterface("Client");web.destroy();super.onDestroy()}
}
