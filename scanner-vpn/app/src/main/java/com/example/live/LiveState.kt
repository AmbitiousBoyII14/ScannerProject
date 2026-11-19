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
    private val outcomes=mutableListOf<String>()
    private var total=0;private var completed=0
    private val executor=Executors.newSingleThreadExecutor()
    @Synchronized fun save(p:Profile){require(editable() && (profiles.size<12||profiles.containsKey(p.label)));profiles[p.label]=p;chosen=p.label}
    @Synchronized fun select(key:String){if(editable() && profiles.containsKey(key))chosen=key}
    @Synchronized fun active()=profiles[chosen]
    fun editable()=status in setOf("Idle","Failed")&&!batchRunning
    @Synchronized fun log(tag:String,text:String,batch:Boolean=false){val list=if(batch)checks else logs;val stamp=SimpleDateFormat("HH:mm:ss",Locale.US).format(Date());if(list.size>=100)list.removeFirst();list.addLast("$stamp  $tag  $text");DebugFile.event("${if(batch) "BATCH" else "VPN"}  $tag  $text")}
    @Synchronized fun sessionLogText()=logs.joinToString("\n")
    @Synchronized fun clearSessionLog(){logs.clear()}
    @Synchronized fun snapshot()=obj("debugFile" to DebugFile.status,"state" to status,"activeDns" to (active()?.dns?:""),"activeSni" to (active()?.sni?:""),"activeDns2" to (active()?.dns2?:""),"activeUdp" to (active()?.udpGw?:""),"activePayload" to (active()?.payload?:""),"activePath" to (active()?.path?:"/"),"active" to chosen,"profiles" to arr(*profiles.keys.toTypedArray()),"log" to arr(*logs.toTypedArray()),"results" to arr(*outcomes.toTypedArray()),"total" to total,"completed" to completed,"checks" to arr(*checks.toTypedArray()),"batch" to batchRunning,"latency" to ms,"up" to up,"down" to down,"elapsed" to if(since>0)(SystemClock.elapsedRealtime()-since)/1000 else 0).toString()
    fun resolve(p:Profile,ms:Int):String {val ip=NetworkChecks.resolve(p.host,ms).hostAddress!!;log("DNS","${p.host} → $ip");return ip}
    private data class Job(val host:String,val port:Int,val profile:Profile?){val endpoint get()="$host:$port"}
    @Synchronized fun check(context:Context,all:Boolean,text:String?=null,mode:String?=null):Boolean {
        if(!editable())return false
        val jobs=if(text!=null){
            val targets=runCatching{BatchTarget.parse(text)}.getOrNull()?:return false
            targets.flatMap{t->val saved=profiles.values.filter{it.mode==mode && it.host==t.host && (t.port==null||it.port==t.port)}
                if(saved.isEmpty())listOf(Job(t.host,t.port?:443,null)) else saved.map{Job(it.host,it.port,it)}
            }.distinct()
        } else (if(all)profiles.values.toList() else listOfNotNull(active())).map{Job(it.host,it.port,it)}
        if(jobs.isEmpty())return false
        val policy=CheckPolicy.read(context);total=jobs.size;completed=0;outcomes.clear();checks.clear();stopBatch=false;batchRunning=true
        executor.execute {try{for(job in jobs){if(stopBatch)break
            val p=job.profile;log("START","${job.endpoint} · ${p?.mode?:"TLS reachability"}",true)
            val (tag,detail)=try{policy.run({stopBatch},{n->log("RETRY","${job.endpoint} · retry $n of ${policy.retries}",true)}){
                val addr=NetworkChecks.resolve(job.host,policy.ms);val ip=addr.hostAddress!!;log("DNS","${job.host} → $ip",true)
                if(stopBatch)throw java.util.concurrent.CancellationException()
                if(p==null){val start=SystemClock.elapsedRealtime();NetworkChecks.tls(job.host,addr,job.port,policy.ms)
                    "REACHABLE" to "TLS verified · ${SystemClock.elapsedRealtime()-start} ms"
                }else{Engine.init(context);val delay=p.openSsh(ip,HostPins.verifier(context,p){!stopBatch},policy.ms){t,m->log(t,m,true)}.use{ssh->ssh?.report{tag,msg->log(tag,msg,true)};Libv2ray.measureOutboundDelay(p.config(ip,ssh,policy.seconds),CHECK_URL).also{check(it>=0)}}
                    "VERIFIED" to "${p.mode} · HTTPS $delay ms"}
            }}catch(e:Exception){"FAILED" to failureReason(e,"${p?.mode?:"TLS"} check did not complete.")}
            if(stopBatch)break
            synchronized(this){outcomes.add("$tag  ${job.endpoint}  $detail");completed++;log(tag,"${job.endpoint} · $detail",true)}
        }}finally{if(stopBatch)log("STOP","Checks cancelled",true);batchRunning=false}}
        return true
    }
    fun cancel(){stopBatch=true}
}
