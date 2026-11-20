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
    @Synchronized fun clearProfiles(){if(editable()){profiles.clear();chosen=""}}
    @Synchronized fun select(key:String){if(editable() && profiles.containsKey(key))chosen=key}
    @Synchronized fun active()=profiles[chosen]
    fun editable()=status in setOf("Idle","Failed")&&!batchRunning
    @Synchronized fun log(tag:String,text:String,batch:Boolean=false){val list=if(batch)checks else logs;val stamp=SimpleDateFormat("HH:mm:ss",Locale.US).format(Date());if(list.size>=500)list.removeFirst();list.addLast("$stamp  $tag  $text");DebugFile.event("${if(batch) "BATCH" else "VPN"}  $tag  $text")}
    @Synchronized fun sessionLogText()=logs.joinToString("\n")
    @Synchronized fun clearSessionLog(){logs.clear()}
    @Synchronized fun snapshot()=obj("aliasKind" to aliasKind,"aliasStatus" to aliasStatus,"webBatch" to (webBatch?.snapshot() ?: org.json.JSONObject.NULL),"debugFile" to DebugFile.status,"state" to status,"activeDns" to (active()?.dns?:""),"activeSni" to (active()?.sni?:""),"activeDns2" to (active()?.dns2?:""),"activeUdp" to (active()?.udpGw?:""),"activePayload" to (active()?.payload?:""),"activePath" to (active()?.path?:"/"),"active" to chosen,"profiles" to arr(*profiles.keys.toTypedArray()),"log" to arr(*logs.toTypedArray()),"results" to arr(*outcomes.toTypedArray()),"total" to total,"completed" to completed,"checks" to arr(*checks.toTypedArray()),"batch" to batchRunning,"latency" to ms,"up" to up,"down" to down,"elapsed" to if(since>0)(SystemClock.elapsedRealtime()-since)/1000 else 0).toString()
    private var webBatch:WebsiteBatch?=null
    @Volatile private var aliasStatus=""
    @Volatile private var aliasKind="alias"
    @Synchronized fun aliases(context:Context,raw:String,text:String):String {
        if(!editable())return "Stop the current tunnel or batch first."
        val t=runCatching{CoreAliasTemplate(raw)}.getOrElse{return "Set up a TLS VLESS, VMess or Trojan profile with a fixed server and \$\$ in SNI, WS Host or WS path."}
        return runAliases(context,t,text)
    }
    @Synchronized fun sshAliases(context:Context,raw:String,text:String):String {
        if(!editable())return "Stop the current tunnel or batch first."
        val j=runCatching{org.json.JSONObject(raw)}.getOrElse{return "Invalid SSH account template."}
        if(j.optString("mode")!="SSH TLS" || j.optString("sni").isBlank() || j.optString("host")!="\$\$")return "Set a fixed SNI, turn on SSL and turn off Use Payload for this SSH account batch."
        val pin=HostPins.canonicalPin(context,j.optString("sni").trim().lowercase()+":"+j.optInt("port"))?:return "First connect normally with the SNI hostname as the account host and verify its SSH key."
        val template=runCatching{SshAccountTemplate(raw,pin)}.getOrElse{return "Invalid SSH account, port or SNI."}
        return runAliases(context,template,text)
    }
    private fun runAliases(context:Context,t:AliasTemplate,text:String):String {
        val ssh=t is SshAccountTemplate
        val input=runCatching{WebsiteInput.hosts(text)}.getOrElse{return "Use a domain list up to 1 MiB."}
        if(input.hosts.isEmpty() || input.skipped!=0)return "Enter bare domain names, one per line (no port or URL)."
        val policy=CheckPolicy.read(context).copy(auto=true,retries=2);val file=java.io.File(context.filesDir,"output.txt")
        total=input.hosts.size;completed=0;outcomes.clear();checks.clear();webBatch=null;stopBatch=false;batchRunning=true;aliasKind=if(ssh)"sshAlias" else "alias";aliasStatus="Checking gateway certificate"
        executor.execute {
            val parts=listOf("Connected","Failed","Skipped").associateWith{java.io.File(context.cacheDir,"alias-$it.txt")};val start=SystemClock.elapsedRealtime();var connected=0;var failed=0;var skipped=0
            fun result(tag:String,host:String,detail:String){
                parts.getValue(tag).appendText("$host\t$detail\n");synchronized(this){if(outcomes.size>=80)outcomes.removeAt(0);outcomes.add("${tag.uppercase()}  $host  $detail");completed++;log(tag.uppercase(),"$host · $detail",true)}
            }
            val tmp=java.io.File(context.filesDir,"alias-output.tmp")
            try{
                parts.values.forEach{it.writeText("")};val ip=NetworkChecks.resolve(t.base.host,policy.ms);val names=t.certificateNames(ip,policy.ms);val edge="${ip.hostAddress} · ${CdnRanges.label(ip)}"
                Engine.init(context)
                for(host in input.hosts){if(stopBatch)break
                    val row=t.approved(host,names);if(row==null){skipped++;result("Skipped",host,"Not an explicit gateway certificate name (SNI / WS Host / list entry).");continue}
                    var attempt=0
                    try{val delay=policy.run({stopBatch},{},{attempt++;val eta=if(completed==0)"estimating" else "${(SystemClock.elapsedRealtime()-start)/1000*(total-completed)/completed}s";aliasStatus="${completed+1}/$total · $host · attempt $attempt/3 · Connected $connected · Failed $failed · ETA $eta";log("ATTEMPT","$host · $attempt/3",true)
                        val dial=if(ssh)NetworkChecks.resolve(row.host,policy.ms) else ip
                        if(dial!=ip)throw GatewayFailure("Alias does not resolve to the verified gateway address. No login attempted.")
                        val ms=try{row.openSsh(dial.hostAddress!!,timeout=policy.ms).use{transport->Libv2ray.measureOutboundDelay(row.config(dial.hostAddress!!,transport,policy.seconds),context.getString(com.example.R.string.vpn_health_url))}}catch(e:Exception){throw aliasFailure(e)};check(ms>=0);ms});if(stopBatch)break;connected++;result("Connected",host,"HTTPS through ${row.mode} · $delay ms · $edge")
                    }catch(e:Exception){if(stopBatch)break;failed++;result("Failed",host,failureReason(e,"Outbound did not verify traffic.")+" · $edge")}
                }
                val summary="${if(stopBatch)"Stopped" else "Complete"} · $completed/$total · Connected $connected · Failed $failed · Skipped $skipped"
                tmp.outputStream().buffered().use{out->out.write(("${if(ssh)"SSH" else "V2ray"} TLS alias batch · $summary\nGateway: ${t.base.host}:${t.base.port} · $edge\n").toByteArray());parts.forEach{(group,f)->out.write("\n[$group]\n".toByteArray());f.inputStream().use{it.copyTo(out)}}};check(tmp.renameTo(file));aliasStatus="$summary · output.txt saved"
            }catch(e:Exception){aliasStatus="Batch stopped: "+failureReason(e,"Could not verify the fixed gateway certificate, build a row or save the report.");log("FAILED",aliasStatus,true)}finally{parts.values.forEach{it.delete()};tmp.delete();batchRunning=false}
        };return ""
    }
    @Synchronized fun websites(context:Context,text:String,template:String):String {
        if(!editable())return "Stop the current tunnel or batch first."
        val path=runCatching{WebsiteInput.path(template.trim())}.getOrElse{return "Use https://\$\$/ followed by an optional path (no port, credentials, query or fragment)."}
        val input=runCatching{WebsiteInput.hosts(text)}.getOrElse{return "Use a UTF-8 list up to 1 MiB."}
        if(input.hosts.isEmpty())return "No valid domain lines found. Enter one domain per line without a URL or port."
        aliasStatus="";val batch=WebsiteBatch(input.hosts,path,input.skipped);webBatch=batch;stopBatch=false;batchRunning=true
        checks.clear();outcomes.clear();total=0;completed=0;val policy=CheckPolicy.read(context)
        executor.execute{try{batch.run(context.filesDir,policy){stopBatch}}finally{batchRunning=false}};return ""
    }
    fun resolve(p:Profile,ms:Int):String {val addr=NetworkChecks.resolve(p.host,ms);val ip=addr.hostAddress!!;log("DNS","${p.host} → $ip");log("CDN","$ip · ${CdnRanges.label(addr)}");return ip}
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
        aliasStatus="";webBatch=null;val policy=CheckPolicy.read(context);total=jobs.size;completed=0;outcomes.clear();checks.clear();stopBatch=false;batchRunning=true
        executor.execute {try{for(job in jobs){if(stopBatch)break
            val p=job.profile;log("START","${job.endpoint} · ${p?.mode?:"TLS reachability"}",true)
            var network="DNS unresolved"
            val (tag,detail)=try{policy.run({stopBatch},{n->log("RETRY","${job.endpoint} · retry $n of ${policy.retries}",true)}){
                network="DNS unresolved";val addr=NetworkChecks.resolve(job.host,policy.ms);val ip=addr.hostAddress!!;log("DNS","${job.host} → $ip",true);network="$ip · ${CdnRanges.label(addr)}";log("CDN",network,true)
                if(stopBatch)throw java.util.concurrent.CancellationException()
                if(p==null){val start=SystemClock.elapsedRealtime();NetworkChecks.tls(job.host,addr,job.port,policy.ms)
                    "REACHABLE" to "TLS verified · ${SystemClock.elapsedRealtime()-start} ms"
                }else{Engine.init(context);val delay=p.openSsh(ip,HostPins.verifier(context,p){!stopBatch},policy.ms){t,m->log(t,m,true)}.use{ssh->ssh?.report{tag,msg->log(tag,msg,true)};Libv2ray.measureOutboundDelay(p.config(ip,ssh,policy.seconds),CHECK_URL).also{check(it>=0)}}
                    "VERIFIED" to "${p.mode} · HTTPS $delay ms"}
            }}catch(e:Exception){"FAILED" to failureReason(e,"${p?.mode?:"TLS"} check did not complete.")}
            if(stopBatch)break
            synchronized(this){outcomes.add("$tag  ${job.endpoint}  $detail · $network");completed++;log(tag,"${job.endpoint} · $detail",true)}
        }}finally{if(stopBatch)log("STOP","Checks cancelled",true);batchRunning=false}}
        return true
    }
    fun cancel(){stopBatch=true}
}
