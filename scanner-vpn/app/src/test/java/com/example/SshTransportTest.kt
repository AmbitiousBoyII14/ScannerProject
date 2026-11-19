package com.example.live

import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.net.*
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.concurrent.thread

class SshTransportTest {
    private fun profile(key:String,password:String):Profile=Profile.read(obj("mode" to "SSH","host" to "127.0.0.1","port" to 22257,"user" to System.getenv("SSH_TEST_USER"),"secret" to password,"hostKey" to key).toString())
    @Test fun realSshForwardingWithLiteralUrlPassword(){
        val key=System.getenv("SSH_TEST_KEY");assumeNotNull(key)
        val discovered=SshTransport.discover("127.0.0.1",22257);assertEquals(key.split(" ").take(2).joinToString(" "),discovered.first);assertTrue(discovered.second.isNotBlank())
        val pass=System.getenv("SSH_TEST_PASSWORD");val p=profile(key,pass)
        // Authentication and host verification fail closed; no local proxy on failure.
        assertThrows(Exception::class.java){profile(key,"not-the-password").openSsh("127.0.0.1")?.close()}
        assertThrows(Exception::class.java){profile(System.getenv("SSH_TEST_OTHER_KEY"),pass).openSsh("127.0.0.1")?.close()}
        ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use {echo->
            echo.soTimeout=6000
            val server=thread(isDaemon=true){echo.accept().use {it.getOutputStream().write(it.getInputStream().read())}}
            p.openSsh("127.0.0.1")!!.use {ssh->
                val cfg=p.config("127.0.0.1",ssh);assertFalse(cfg.contains(pass));assertTrue(cfg.contains("rewriteNetwork"))
                Socket("127.0.0.1",ssh.port).use {socket->
                    socket.soTimeout=5000;val o=DataOutputStream(socket.getOutputStream());val i=DataInputStream(socket.getInputStream());o.write(byteArrayOf(5,1,2));assertEquals(0x0502,i.readUnsignedShort());val token=ssh.token.toByteArray();o.write(byteArrayOf(1,3,118,112,110,token.size.toByte()));o.write(token);assertEquals(0x0100,i.readUnsignedShort());o.write(byteArrayOf(5,1,0,1,127,0,0,1));o.writeShort(echo.localPort);val reply=ByteArray(10);i.readFully(reply);assertEquals(0,reply[1].toInt());o.write(73);assertEquals(73,i.read());
                }
            };server.join(2000)
        }
    }
}
