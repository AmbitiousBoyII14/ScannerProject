package com.example.live

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Indexed by the original SSH hostname and port, never by DNS answer, mode or username. */
internal object HostPins {
    @Volatile var foreground:LiveActivity?=null
    private fun prefs(ctx:Context)=ctx.getSharedPreferences("known_ssh_servers",Context.MODE_PRIVATE)
    private fun entries(ctx:Context,id:String)=prefs(ctx).all.filterKeys {
        // 1.9 saved either host:port or "SSH MODE · host:port · routeHash".
        it==id || (it.startsWith("SSH ") && it.split(" · ").getOrNull(1)==id)
    }.mapValues { it.value as String }
    private fun fingerprint(key:String):String {
        val blob=Base64.decode(key.split(' ')[1],Base64.DEFAULT)
        return "SHA256:"+Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(blob),Base64.NO_WRAP or Base64.NO_PADDING)
    }
    @Synchronized fun forget(ctx:Context,id:String):Boolean {
        val edit=prefs(ctx).edit();entries(ctx,id).keys.forEach(edit::remove);return edit.commit()
    }
    fun verifier(ctx:Context,p:Profile,active:()->Boolean):(String)->Boolean = { key ->
        val id="${p.host}:${p.port}"
        val before=synchronized(this){entries(ctx,id)}
        val old=before.values.map{it.split(' ').take(2).joinToString(" ")}.distinct()
        // Conflicting legacy route pins are never silently collapsed to one trusted key.
        if (!active()) false else if(old==listOf(key) && before.keys==setOf(id)) true else {
            val approved=old==listOf(key) || confirm(id,old,key,active)
            if(!approved) throw GatewayFailure("SSH host key not approved for $id. No password was sent.")
            synchronized(this){
                if(!active() || before!=entries(ctx,id)) false else {
                    val edit=prefs(ctx).edit();before.keys.forEach(edit::remove);edit.putString(id,key).commit()
                }
            }
        }
    }
    private fun confirm(id:String,old:List<String>,key:String,active:()->Boolean):Boolean {
        val a=foreground ?: throw GatewayFailure("Open the VPN screen to verify the SSH host key for $id. No password was sent.")
        val handler=Handler(Looper.getMainLooper());val answer=ArrayBlockingQueue<Boolean>(1)
        val done=AtomicBoolean(false);var dialog:AlertDialog?=null // main thread only
        handler.post {
            if(done.get() || a!==foreground || a.isFinishing || a.isDestroyed || !active()) answer.offer(false) else {
                val previous=old.joinToString("\n"){"Old: ${fingerprint(it)}"}
                dialog=AlertDialog.Builder(a).setTitle(if(old.isEmpty())"Trust SSH host $id?" else "Host key changed for $id")
                    .setMessage(listOf(previous,"New: ${fingerprint(key)}","Compare with your provider before accepting. No password has been sent.").filter{it.isNotEmpty()}.joinToString("\n\n"))
                    .setNegativeButton("Cancel"){_,_->answer.offer(false)}
                    .setPositiveButton(if(old.isEmpty())"Trust key" else "Accept New Key"){_,_->answer.offer(true)}
                    .setOnDismissListener{answer.offer(false)}.show()
            }
        }
        try {
            val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(60)
            while(active() && a===foreground && System.nanoTime()<deadline){answer.poll(200,TimeUnit.MILLISECONDS)?.let{return it && active() && a===foreground}}
            return false
        }finally{done.set(true);handler.post{dialog?.dismiss()}}
    }
}
