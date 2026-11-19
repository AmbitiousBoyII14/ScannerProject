package com.example.live

import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.Closeable
import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import kotlin.concurrent.thread

/** An authenticated, loopback-only SOCKS adapter between the TUN engine and SSH direct-tcpip.
 * No shell, exec, remote listeners, UDP associations, or unverified host keys. */
internal class SshTransport private constructor(private val session:Session):Closeable {
    private val listener=ServerSocket(0,16,InetAddress.getByName("127.0.0.1"))
    val port get()=listener.localPort
    val token=UUID.randomUUID().toString()
    private val clients=ConcurrentHashMap.newKeySet<Socket>()
    private val slots=Semaphore(48)
    @Volatile private var closed=false

    companion object {
        fun open(ip:String,port:Int,user:String,password:String,hostKey:String):SshTransport {
            val ssh=JSch();val alias="scanner-profile"
            val fields=hostKey.trim().split(Regex("\\s+"));require(fields.size>=2)
            ssh.setKnownHosts("$alias ${fields[0]} ${fields[1]}\n".byteInputStream())
            val session=ssh.getSession(user,ip,port)
            session.setHostKeyAlias(alias)
            session.setConfig("server_host_key",if(fields[0]=="ssh-rsa")"rsa-sha2-512,rsa-sha2-256" else fields[0])
            session.setConfig("StrictHostKeyChecking","yes")
            session.setConfig("PreferredAuthentications","password")
            session.setConfig("MaxAuthTries","1")
            session.setPassword(password.toByteArray(Charsets.UTF_8))
            session.setServerAliveInterval(15000);session.setServerAliveCountMax(2)
            try {session.connect(10000);return SshTransport(session).also {it.listen()}}
            catch(e:Exception){session.disconnect();throw e}
        }
    }
    private fun listen()=thread(isDaemon=true,name="ssh-accept") {
        while(!closed){val client=try{listener.accept()}catch(_:Exception){break}
            clients.add(client)
            if(closed||!slots.tryAcquire()){clients.remove(client);client.close();continue}
            thread(isDaemon=true,name="ssh-forward"){
                try{client.use {serve(it)}}catch(_:Exception){/* One failed stream does not end the tunnel. */}
                finally{clients.remove(client);slots.release()}
            }
        }
    }
    private fun serve(socket:Socket){
        socket.soTimeout=5000
        val input=DataInputStream(socket.getInputStream());val output=socket.getOutputStream()
        fun bytes(n:Int)=ByteArray(n).also(input::readFully)
        if(input.readUnsignedByte()!=5)return
        if(2.toByte() !in bytes(input.readUnsignedByte())){output.write(byteArrayOf(5,-1));return}
        output.write(byteArrayOf(5,2));if(input.readUnsignedByte()!=1)return
        val localUser=bytes(input.readUnsignedByte());val key=bytes(input.readUnsignedByte())
        if(!localUser.contentEquals("vpn".toByteArray())||!MessageDigest.isEqual(key,token.toByteArray())){output.write(byteArrayOf(1,1));return}
        output.write(byteArrayOf(1,0));if(input.readUnsignedByte()!=5)return
        val cmd=input.readUnsignedByte();input.readUnsignedByte()
        fun reply(code:Int)=output.write(byteArrayOf(5,code.toByte(),0,1,0,0,0,0,0,0))
        if(cmd!=1){reply(7);return}
        val dest=when(input.readUnsignedByte()){
            1->InetAddress.getByAddress(bytes(4)).hostAddress
            3->String(bytes(input.readUnsignedByte()),Charsets.US_ASCII)
            4->InetAddress.getByAddress(bytes(16)).hostAddress
            else->{reply(8);return}
        }
        val dstPort=input.readUnsignedShort();val ch=session.openChannel("direct-tcpip") as ChannelDirectTCPIP
        try{
            ch.setHost(dest);ch.setPort(dstPort);ch.setOrgIPAddress("127.0.0.1");ch.setOrgPort(socket.port)
            val remoteIn=ch.inputStream;val remoteOut=ch.outputStream
            try{ch.connect(8000)}catch(_:Exception){reply(5);return}
            reply(0);socket.soTimeout=0
            thread(isDaemon=true,name="ssh-upload"){try{remoteOut.use {input.copyTo(it)}}catch(_:Exception){ch.disconnect()}}
            remoteIn.copyTo(output)
        }finally{ch.disconnect()}
    }
    override fun close(){closed=true;runCatching{listener.close()};clients.forEach{runCatching{it.close()}};session.disconnect()}
}
