package com.example.live

import java.io.IOException
import java.net.URI

internal class GatewayFailure(val detail:String):IOException(detail)
internal data class GatewayRequest(val path:String,val authority:String,val headers:Map<String,String>,val upgrade:String,val binaryProtocol:Boolean){
    fun raw()=buildString{append("GET $path HTTP/1.1\r\nHost: $authority\r\nConnection: Upgrade\r\nUpgrade: $upgrade\r\n");headers.forEach{(k,v)->append("$k: $v\r\n")};append("\r\n")}.toByteArray(Charsets.US_ASCII)
    companion object {
        fun parse(text:String,name:String,port:Int,path:String,binary:Boolean):GatewayRequest {
            val source=text.ifBlank{"GET $path HTTP/1.1\nHost: [host]\nConnection: Upgrade\nUpgrade: websocket"}
            require(source.length<=8192)
            val lines=source.replace("[crlf]","\n",true).replace("\\r\\n","\n").replace("\r\n","\n").trimEnd('\n').split('\n')
            val target=Regex("GET (/[^ ]*) HTTP/1\\.1").matchEntire(lines.first())?.groupValues?.get(1)?:throw IllegalArgumentException()
            require(target.length<=1024 && target.all{it.code in 33..126} && URI(target).let{it.rawAuthority==null&&it.rawFragment==null})
            val h=linkedMapOf<String,String>();for(line in lines.drop(1)){require(line.contains(':')&&line.all{it.code in 32..126});val k=line.substringBefore(':').lowercase();val v=line.substringAfter(':').trim();require(k in setOf("host","connection","upgrade","user-agent","origin","sec-websocket-protocol") || k.matches(Regex("x-[a-z0-9-]{1,50}")));require(k !in h&&v.isNotEmpty());h[k]=v}
            val authority=(h.remove("host")?:"[host]").replace("[host]",name,true).replace("[port]",port.toString(),true)
            require(authority.matches(Regex("[A-Za-z0-9.-]+(:[0-9]{1,5})?")));require(h.remove("connection").equals("upgrade",true));val up=h.remove("upgrade")?.lowercase();require(up=="websocket"||(!binary&&up=="ssh"));val sub=h.remove("sec-websocket-protocol");require(sub==null||(binary&&sub=="binary"));return GatewayRequest(target,authority,h,up!!,sub!=null)
        }
    }
}
