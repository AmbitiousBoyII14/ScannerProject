package com.example.live

import android.content.Context
import android.os.SystemClock
import libv2ray.Libv2ray
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

internal object LiveState {
    private val profiles=linkedMapOf<String,Profile>()
    private var chosen=""
    @Volatile var status="Idle"
    @Volatile var batchRunning=false
    @Volatile private var stopBatch=false
    @Volatile var ms=-1L
    @Volatile var up=0.0
    @Volatile var down=0.0
    @Volatile var since=0L
    private val logs=ArrayDeque<String>();private val checks=ArrayDeque<String>()
    private val executor=Executors.newSingleThreadExecutor()
    @Synchronized fun save(p:Profile){require(editable() && (profiles.size<12||profiles.containsKey(p.label)));profiles[p.label]=p;chosen=p.label}
    @Synchronized fun select(key:String){if(editable() && profiles.containsKey(key))chosen=key}
    @Synchronized fun active()=profiles[chosen]
    fun editable()=status in setOf("Idle","Failed")&&!batchRunning
    @Synchronized fun log(tag:String,text:String,batch:Boolean=false){val list=if(batch)checks else logs;val stamp=SimpleDateFormat("HH:mm:ss",Locale.US).format(Date());if(list.size>=100)list.removeFirst();list.addLast("$stamp  $tag  $text")}
    @Synchronized fun snapshot()=obj("state" to status,"active" to chosen,"profiles" to arr(*profiles.keys.toTypedArray()),"log" to arr(*logs.toTypedArray()),"checks" to arr(*checks.toTypedArray()),"batch" to batchRunning,"latency" to ms,"up" to up,"down" to down,"elapsed" to if(since>0)(SystemClock.elapsedRealtime()-since)/1000 else 0).toString()
    fun resolve(p:Profile,batch:Boolean=false):String {val ip=InetAddress.getAllByName(p.host).first().hostAddress!!;log("DNS","${p.host} → $ip",batch);return ip}
    @Synchronized fun check(context:Context,all:Boolean){if(!editable()||profiles.isEmpty())return;val items=if(all)profiles.values.toList() else listOfNotNull(active());stopBatch=false;batchRunning=true;checks.clear();executor.execute {try{Engine.init(context);for(p in items){if(stopBatch)break;log("START",p.label,true);try{val cfg=p.config(resolve(p,true));if(stopBatch)break;val delay=Libv2ray.measureOutboundDelay(cfg,CHECK_URL);if(!stopBatch)log("VERIFIED","${p.host}:${p.port} · HTTPS via profile · $delay ms",true)}catch(e:Exception){if(!stopBatch)log("FAILED","${p.host}:${p.port} · check DNS, credentials, TLS and reachability",true)}}}finally{if(stopBatch)log("STOP","Checks cancelled",true);batchRunning=false}}}
    fun cancel(){stopBatch=true}
}
