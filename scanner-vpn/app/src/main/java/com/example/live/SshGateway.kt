package com.example.live

import com.jcraft.jsch.Proxy
import com.jcraft.jsch.SocketFactory
import com.neovisionaries.ws.client.*
import java.io.*
import java.net.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** One TLS socket or RFC 6455 binary byte stream per SSH session, including discovery. */
internal class SshGateway(private val server:String,private val path:String,private val tls:Boolean,
    private val ws:Boolean,private val event:(String,String)->Unit):Proxy {
    private var raw:Socket?=null
    private var websocket:WebSocket?=null
    private lateinit var source:InputStream
    private lateinit var sink:OutputStream
    @Volatile private var ended=false
    private val chunks=ArrayBlockingQueue<ByteArray>(16)
    override fun connect(factory:SocketFactory?,host:String,port:Int,timeout:Int){
        try {
            if(ws){
                val uri=URI("${if(tls) "wss" else "ws"}://$server:$port$path")
                event("WS","Opening ${uri.scheme} transport to $server:$port")
                val w=WebSocketFactory().setConnectionTimeout(timeout).setSocketTimeout(timeout).createSocket(uri)
                websocket=w
                w.setFrameQueueSize(16).addListener(object:WebSocketAdapter(){
                    override fun onBinaryMessage(socket:WebSocket,bytes:ByteArray){if(bytes.isEmpty())return;if(bytes.size>1048576){close();return};while(!ended&&!chunks.offer(bytes,200,TimeUnit.MILLISECONDS)){} }
                    override fun onTextMessage(socket:WebSocket,text:String){close()}
                    override fun onError(socket:WebSocket,cause:WebSocketException){end()}
                    override fun onDisconnected(socket:WebSocket,s:WebSocketFrame?,c:WebSocketFrame?,byServer:Boolean){end()}
                }).connect();w.socket.soTimeout=0
                (w.socket as? SSLSocket)?.let{event("TLS","${it.session.protocol} · ${it.session.cipherSuite}")};event("WS","HTTP upgrade accepted; binary WebSocket open")
                source=object:InputStream(){var part=ByteArray(0);var pos=0
                    override fun read():Int{val b=ByteArray(1);return if(read(b)<0)-1 else b[0].toInt() and 255}
                    override fun read(b:ByteArray,off:Int,len:Int):Int{if(len==0)return 0;while(pos==part.size){if(ended&&chunks.isEmpty())return -1;part=chunks.poll(12,TimeUnit.SECONDS)?:throw SocketTimeoutException();pos=0};val n=minOf(len,part.size-pos);part.copyInto(b,off,pos,pos+n);pos+=n;return n}}
                sink=object:OutputStream(){override fun write(value:Int)=write(byteArrayOf(value.toByte()));override fun write(b:ByteArray,off:Int,len:Int){if(ended)throw EOFException();if(len>0)w.sendBinary(b.copyOfRange(off,off+len))}}
            }else{
                event("TLS","Opening TLS transport to $server:$port")
                val tcp=Socket();raw=tcp;tcp.connect(InetSocketAddress(host,port),timeout);tcp.soTimeout=timeout
                val sock=(SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(tcp,server,port,true) as SSLSocket;raw=sock
                sock.sslParameters=sock.sslParameters.apply{endpointIdentificationAlgorithm="HTTPS"};sock.startHandshake();event("TLS","${sock.session.protocol} · ${sock.session.cipherSuite}");source=sock.inputStream;sink=sock.outputStream
            }
        }catch(e:Exception){close();throw e}
    }
    override fun getSocket():Socket?=raw
    override fun getInputStream()=source
    override fun getOutputStream()=sink
    private fun end(){ended=true;chunks.offer(ByteArray(0))}
    override fun close(){end();runCatching{websocket?.socket?.close()};runCatching{raw?.close()}}
}
