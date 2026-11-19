package com.example.live

import com.jcraft.jsch.Proxy
import com.jcraft.jsch.SocketFactory
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.handshake.ServerHandshake
import java.io.*
import java.net.*
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** Dial the account address. TLS authenticates the configured server name before any HTTP or SSH data. */
internal class SshGateway(private val peer:String,private val tls:Boolean,private val request:GatewayRequest?,private val binary:Boolean,private val event:(String,String)->Unit):Proxy {
    private var raw:Socket?=null
    private var client:WebSocketClient?=null
    private lateinit var source:InputStream
    private lateinit var sink:OutputStream
    @Volatile private var ended=false
    private val chunks=ArrayBlockingQueue<ByteArray>(16)
    override fun connect(factory:SocketFactory?,host:String,port:Int,timeout:Int){
        var stage="TCP connect"
        try{
            val tcp=Socket();raw=tcp;tcp.connect(InetSocketAddress(host,port),timeout);tcp.soTimeout=timeout
            event("TCP","Connected to ${tcp.inetAddress.hostAddress}:$port")
            val socket=if(tls){stage="TLS handshake";event("TLS","Verifying server name $peer");val s=(SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(tcp,peer,port,true) as SSLSocket;raw=s;s.sslParameters=s.sslParameters.apply{endpointIdentificationAlgorithm="HTTPS"};s.startHandshake();event("TLS","${s.session.protocol} · ${s.session.cipherSuite}");s}else tcp
            source=socket.inputStream;sink=socket.outputStream
            val req=request ?: return
            stage="HTTP upgrade";event("HTTP",if(binary)"Requesting binary WebSocket upgrade" else "Sending HTTP upgrade for a raw SSH stream")
            if(!binary){sink.write(req.raw());sink.flush();val buf=ByteArrayOutputStream();var tail=0
                while(tail!=0x0d0a0d0a){val b=source.read();if(b<0||buf.size()>=16384)throw GatewayFailure("Incomplete or oversized HTTP response headers");tail=(tail shl 8) or b;buf.write(b)}
                val lines=buf.toString("US-ASCII").split("\r\n");val code=Regex("HTTP/1\\.[01] ([0-9]{3})(?: .*|$)").matchEntire(lines.first())?.groupValues?.get(1)?:throw GatewayFailure("Gateway did not return an HTTP response")
                if(code!="101")throw GatewayFailure("HTTP $code · gateway did not switch protocols")
                if(lines.none{it.substringBefore(':').equals("upgrade",true)&&it.substringAfter(':').trim().equals(req.upgrade,true)})throw GatewayFailure("HTTP 101 without the requested Upgrade header")
                event("HTTP","HTTP 101 · waiting for raw SSH identification");return
            }
            val uri=URI("${if(tls) "wss" else "ws"}://${req.authority}${req.path}");var problem="WebSocket handshake rejected; check gateway and path"
            val w=object:WebSocketClient(uri,Draft_6455(emptyList(),emptyList<org.java_websocket.protocols.IProtocol>()+org.java_websocket.protocols.Protocol(""),1048576),req.headers+mapOf("Host" to req.authority),timeout){
                override fun onOpen(h:ServerHandshake){event("WS","HTTP ${h.httpStatus} · binary WebSocket open")}
                override fun onMessage(text:String){end();closeConnection(1003,"Binary SSH stream required")}
                override fun onMessage(data:ByteBuffer){val bytes=ByteArray(data.remaining());data.get(bytes);if(bytes.isNotEmpty())while(!ended&&!chunks.offer(bytes,200,TimeUnit.MILLISECONDS)){} }
                override fun onClose(code:Int,reason:String?,remote:Boolean){val status=Regex("(?i)(?:status(?: code)?(?: received)?[: ]+)([0-9]{3})").find(reason.orEmpty())?.groupValues?.get(1);if(status!=null)problem="HTTP $status · WebSocket upgrade refused";end()}
                override fun onError(e:Exception){end()}
            };client=w;w.setSocket(socket);if(!w.connectBlocking(timeout.toLong(),TimeUnit.MILLISECONDS))throw GatewayFailure(problem);socket.soTimeout=0
            source=object:InputStream(){var part=ByteArray(0);var pos=0
                override fun read():Int{val b=ByteArray(1);return if(read(b)<0)-1 else b[0].toInt() and 255}
                override fun read(b:ByteArray,off:Int,len:Int):Int{if(len==0)return 0;while(pos==part.size){if(ended&&chunks.isEmpty())return -1;part=chunks.poll(12,TimeUnit.SECONDS)?:throw SocketTimeoutException();pos=0};val n=minOf(len,part.size-pos);part.copyInto(b,off,pos,pos+n);pos+=n;return n}}
            sink=object:OutputStream(){override fun write(v:Int)=write(byteArrayOf(v.toByte()));override fun write(b:ByteArray,off:Int,len:Int){if(ended)throw EOFException();if(len>0)w.send(b.copyOfRange(off,off+len))}}
        }catch(e:Exception){val reason=failureReason(e,"$stage failed");event("ERROR",reason);close();throw e}
    }
    override fun getSocket():Socket?=if(binary&&request!=null)null else raw
    override fun getInputStream()=source
    override fun getOutputStream()=sink
    private fun end(){ended=true;chunks.offer(ByteArray(0))}
    override fun close(){end();runCatching{raw?.close()};runCatching{client?.close()}}
}
