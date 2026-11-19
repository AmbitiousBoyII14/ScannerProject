package com.example.live

import android.content.Context
import java.net.*
import java.util.concurrent.*
import javax.net.ssl.*

internal data class CheckPolicy(val seconds:Int=10,val auto:Boolean=false,val retries:Int=2){
    init { require(seconds in 2..60 && retries in 1..5) }
    val ms get()=seconds*1000
    companion object {
        fun read(c:Context)=c.getSharedPreferences("connections",0).let{CheckPolicy(it.getInt("timeout",10),it.getBoolean("auto",false),it.getInt("retries",2))}
    }
    fun save(c:Context)=c.getSharedPreferences("connections",0).edit().putInt("timeout",seconds).putBoolean("auto",auto).putInt("retries",retries).commit()
    fun <T> run(cancel:()->Boolean,retry:(Int)->Unit,attempt:()->T):T {
        var used=0
        while(!cancel()){
            try{return attempt()}catch(e:Exception){
                if(!auto || used>=retries || cancel() || !transient(e))throw e
                retry(++used);repeat(10){if(cancel())throw CancellationException();Thread.sleep(100)}
            }
        };throw CancellationException()
    }
    private fun transient(e:Exception):Boolean {
        val causes=generateSequence<Throwable>(e){it.cause}.take(8).toList()
        if(causes.any{it is SSLException || it is GatewayFailure || it is com.jcraft.jsch.JSchHostKeyException})return false
        return causes.any{it is SocketTimeoutException || it is UnknownHostException || it is ConnectException || it is NoRouteToHostException}
    }
}

internal data class BatchTarget(val host:String,val port:Int?){
    companion object {
        fun parse(text:String):List<BatchTarget>{
            require(text.length<=65536)
            return text.lowercase().split(Regex("[\\s,;]+" )).filter{it.isNotEmpty()}.map{entry->
                val host=entry.substringBefore(':').trimEnd('.');require(host.length in 1..253 && host.split('.').all{it.matches(Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"))})
                val port=if(':' in entry)entry.substringAfter(':').also{require(it.matches(Regex("[0-9]{1,5}")))}.toInt().also{require(it in 1..65535)} else null
                BatchTarget(host,port)
            }.distinct().also{require(it.size in 1..100)}
        }
    }
}

internal object NetworkChecks {
    // DNS may block in the OS; don't let a stuck resolver spawn unlimited threads or queued lookups.
    private val pool=ThreadPoolExecutor(0,2,30,TimeUnit.SECONDS,SynchronousQueue<Runnable>())
    fun resolve(host:String,ms:Int):InetAddress {
        val call=try{pool.submit(Callable{InetAddress.getAllByName(host).first()})}catch(_:RejectedExecutionException){throw SocketTimeoutException("DNS busy")}
        try{return call.get(ms.toLong(),TimeUnit.MILLISECONDS)}catch(_:TimeoutException){throw SocketTimeoutException("DNS timeout")}catch(e:ExecutionException){throw (e.cause as? Exception ?: e)}finally{call.cancel(true)}
    }
    fun tls(host:String,ip:InetAddress,port:Int,ms:Int){
        Socket().use{tcp->tcp.connect(InetSocketAddress(ip,port),ms);tcp.soTimeout=ms
            ((SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(tcp,host,port,true) as SSLSocket).use{s->
                s.sslParameters=s.sslParameters.apply{endpointIdentificationAlgorithm="HTTPS"};s.startHandshake()
            }
        }
    }
}
