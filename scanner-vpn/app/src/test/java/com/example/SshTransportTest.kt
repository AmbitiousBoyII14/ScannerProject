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
    @Test fun gatewayDiscoveryAndAuthentication(){
        val cert=System.getenv("GATEWAY_TEST_CERT");assumeNotNull(cert)
        val old=javax.net.ssl.SSLContext.getDefault()
        try{
            val ks=java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());ks.load(null,null)
            java.io.File(cert).inputStream().use{ks.setCertificateEntry("fixture",java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(it))}
            val tm=javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());tm.init(ks)
            javax.net.ssl.SSLContext.setDefault(javax.net.ssl.SSLContext.getInstance("TLS").apply{init(null,tm.trustManagers,null)})
            for((mode,port) in listOf("SSH WS" to 22258,"SSH WS TLS" to 22259,"SSH TLS" to 22260)){
                val p=Profile.read(obj("mode" to mode,"host" to "localhost","port" to port,"user" to System.getenv("SSH_TEST_USER"),"secret" to System.getenv("SSH_TEST_PASSWORD"),"hostKey" to System.getenv("SSH_TEST_KEY")).toString())
                val key=SshTransport.discover(p.host,p.port,p.gateway{_,_->});assertEquals(System.getenv("SSH_TEST_KEY").split(" ").take(2).joinToString(" "),key.first)
                p.openSsh("127.0.0.1")!!.use{assertTrue(it.port>0)}
            }
        }finally{javax.net.ssl.SSLContext.setDefault(old)}
    }
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
