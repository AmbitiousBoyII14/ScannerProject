package com.example.live

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import com.jcraft.jsch.ChannelDirectTCPIP

/** One authenticated SOCKS5 UDP association -> one SSH direct-tcpip channel to a provider's badvpn-udpgw.
 * PacketProto length and connection ID are little-endian; IP/port are in network order.
 * Only IPv4 (the TUN family) and unfragmented datagrams. No local DNS or direct UDP fallback. */
internal object UdpGateway {
    fun serve(control:Socket,ch:ChannelDirectTCPIP,ready:(Int)->Unit){
        DatagramSocket(0,InetAddress.getByName("127.0.0.1")).use { udp ->
            val incoming=DataInputStream(ch.inputStream);val outgoing=DataOutputStream(ch.outputStream)
            ch.connect(8000);ready(udp.localPort);control.soTimeout=0
            val routes=ConcurrentHashMap<Int,ByteArray>()
            thread(isDaemon=true,name="udpgw-control"){try{control.getInputStream().read()}catch(_:Exception){}finally{udp.close();ch.disconnect()}}
            thread(isDaemon=true,name="udpgw-return"){try{while(!udp.isClosed){
                val size=incoming.readUnsignedByte() or (incoming.readUnsignedByte() shl 8);require(size in 3..65535);val b=ByteArray(size);incoming.readFully(b)
                if((b[0].toInt() and 1)!=0)continue
                require(size>=9&&(b[0].toInt() and 8)==0);val id=(b[1].toInt() and 255) or ((b[2].toInt() and 255) shl 8)
                if(!b.copyOfRange(3,9).contentEquals(routes[id]))continue
                val reply=byteArrayOf(0,0,0,1)+b.copyOfRange(3,size);if(udp.isConnected)udp.send(DatagramPacket(reply,reply.size))
            }}catch(_:Exception){}finally{udp.close();runCatching{control.close()}}}
            val ids=HashMap<List<Byte>,Int>();val buf=ByteArray(65535)
            while(!udp.isClosed){val p=DatagramPacket(buf,buf.size);udp.receive(p);if(p.address!=control.inetAddress||p.length<10||p.length>65507||buf[0]!=0.toByte()||buf[1]!=0.toByte()||buf[2]!=0.toByte()||buf[3]!=1.toByte())continue
                if(!udp.isConnected)udp.connect(p.socketAddress);val addr=buf.copyOfRange(4,10);val key=addr.toList();if(key !in ids&&ids.size>=256)continue
                val fresh=key !in ids;val id=ids.getOrPut(key){ids.size};routes[id]=addr
                val size=p.length-1;outgoing.writeByte(size);outgoing.writeByte(size ushr 8);outgoing.writeByte(if(fresh)2 else 0);outgoing.writeByte(id);outgoing.writeByte(id ushr 8);outgoing.write(buf,4,p.length-4);outgoing.flush()
            }
        }
    }
}
