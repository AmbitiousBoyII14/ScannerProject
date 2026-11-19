package com.example.live

import java.util.concurrent.TimeUnit
import java.io.*
import java.net.*
import javax.net.ssl.*

/** Direct HTTPS observations only. The URL authority, verified TLS name and Host are identical. */
private fun nowMs()=TimeUnit.NANOSECONDS.toMillis(System.nanoTime())

internal object WebsiteInput {
    const val maxBytes=1024*1024
    private val label=Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")
    fun path(template:String):String {
        val prefix="https://\$\$"
        require(template.length<=1024 && (template==prefix || template.startsWith("$prefix/")))
        return template.removePrefix(prefix).ifEmpty{"/"}.also { p->
            require(p.none{it.code !in 33..126} && !p.contains("\$\$") && !p.startsWith("//") && URI(p).let{it.rawQuery==null && it.rawFragment==null})
        }
    }
    data class ListInput(val hosts:Set<String>,val skipped:Int)
    fun hosts(text:String):ListInput {
        require(text.toByteArray().size<=maxBytes)
        val hosts=linkedSetOf<String>();var skipped=0
        text.removePrefix("\uFEFF").lineSequence().forEach { line->
            val h=line.trim().trimEnd('.').lowercase();if(h.isNotEmpty()){
                if(h.length<=253 && h.split('.').all(label::matches))hosts.add(h) else skipped++
            }
        };return ListInput(hosts,skipped)
    }
    fun head(host:String,ip:InetAddress,path:String,ms:Int,port:Int=443,factory:SSLSocketFactory=SSLSocketFactory.getDefault() as SSLSocketFactory):Int = Socket().use { tcp->
        tcp.connect(InetSocketAddress(ip,port),ms);tcp.soTimeout=ms
        (factory.createSocket(tcp,host,port,true) as SSLSocket).use { tls->
            tls.sslParameters=tls.sslParameters.apply{endpointIdentificationAlgorithm="HTTPS"};tls.startHandshake()
            tls.outputStream.write("HEAD $path HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII));tls.outputStream.flush()
            finalStatus(tls.inputStream,ms)
        }
    }
    // Ignore informational responses such as 103 Early Hints, but never follow redirects.
    internal fun finalStatus(stream:InputStream,ms:Int):Int {
        val input=stream.buffered();val deadline=System.nanoTime()+ms*1_000_000L;var remaining=32768
        fun line():String {
            val chars=StringBuilder()
            while(chars.length<4096 && remaining-- > 0){
                if(System.nanoTime()>=deadline)throw SocketTimeoutException()
                val c=input.read();if(c<0)break;if(c==10)return chars.toString().removeSuffix("\r");chars.append(c.toChar())
            };throw IOException("Incomplete HTTP response")
        }
        while(true){
            val code=Regex("HTTP/1\\.[01] ([1-5][0-9]{2})(?: .*)?").matchEntire(line())?.groupValues?.get(1)?.toInt()?:throw IOException("Invalid HTTP response")
            if(code>=200 || code==101)return code
            while(line().isNotEmpty()) { /* interim headers */ }
        }
    }

}

/** Stream full result groups to disk; only a small tail per group is sent across the WebView bridge. */
internal class WebsiteBatch(val hosts:Set<String>,private val path:String,private val skipped:Int,
    private val resolve:(String,Int)->InetAddress=NetworkChecks::resolve,
    private val head:(String,InetAddress,String,Int)->Int={h,ip,path,ms->WebsiteInput.head(h,ip,path,ms)}){
    private val groups=listOf("HTTP 2xx","HTTP 3xx","HTTP 4xx","HTTP 5xx","HTTP 1xx","Failed")
    private val counts=IntArray(groups.size);private val recent=groups.map{ArrayDeque<String>()};private val cdnRows=ArrayDeque<String>()
    private val started=nowMs();private var current="";private var done=0;private var cdns=0;private var state="Running"
    @Synchronized fun snapshot():org.json.JSONObject {
        val elapsed=nowMs()-started
        return obj("state" to state,"host" to current,"total" to hosts.size,"done" to done,"failed" to counts.last(),"cdn" to cdns,"skipped" to skipped,"eta" to if(done==0||state!="Running") -1L else (hosts.size-done)*elapsed/done/1000,
            "groups" to arr(*groups.indices.filter{counts[it]>0}.map{obj("name" to groups[it],"count" to counts[it],"rows" to arr(*recent[it].toTypedArray()))}.plus(if(cdns>0)listOf(obj("name" to "Known CDN matches","count" to cdns,"rows" to arr(*cdnRows.toTypedArray())))else emptyList()).toTypedArray()))
    }
    fun run(dir:File,policy:CheckPolicy,cancel:()->Boolean){
        val parts=File(dir,"batch-parts");val tmp=File(dir,"output.tmp")
        try{
            parts.deleteRecursively();check(parts.mkdir())
            for(h in hosts){if(cancel())break;synchronized(this){current=h};var network="IP unresolved · CDN unknown";var cdn=false;val before=nowMs()
                val (group,message)=try{val code=policy.run(cancel,{},{
                    val ip=resolve(h,policy.ms);val label=CdnRanges.label(ip);cdn=label.startsWith("CDN:");network="${ip.hostAddress} · $label";if(cancel())throw InterruptedIOException();head(h,ip,path,policy.ms)
                });groups.indexOf("HTTP ${code/100}xx") to "HTTP $code"}catch(e:Exception){groups.lastIndex to failureReason(e,"No valid HTTP response.")}
                if(cancel())break
                val row="$h\t$message\t${nowMs()-before} ms\t$network"
                File(parts,"$group").appendText("$row\n");if(cdn)File(parts,"cdn").appendText("$row\n")
                synchronized(this){done++;counts[group]++;if(cdn){cdns++;cdnRows.addFirst(row);if(cdnRows.size>20)cdnRows.removeLast()};recent[group].addFirst(row);if(recent[group].size>20)recent[group].removeLast()}
            }
            val finalState=if(cancel())"Stopped" else "Complete"
            tmp.outputStream().buffered().use { out->
                fun line(s:String){out.write((s+"\n").toByteArray())}
                line("Scanner VPN · HTTPS HEAD $path");line("$finalState · $done/${hosts.size} · Connected ${done-counts.last()} · Failed ${counts.last()} · CDN matches $cdns · Invalid lines skipped $skipped")
                (groups+"Known CDN matches").forEachIndexed { i,name->line("\n[$name · ${counts.getOrNull(i)?:cdns}]");val f=File(parts,if(i==groups.size)"cdn" else "$i");if(f.exists())f.inputStream().use{it.copyTo(out)} }
            };check(tmp.renameTo(File(dir,"output.txt")));synchronized(this){state=finalState}
        }catch(_:Exception){synchronized(this){state="Report write failed"}}finally{parts.deleteRecursively();tmp.delete();synchronized(this){current=""}}
    }
}
