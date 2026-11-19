package com.example.live

import java.net.InetAddress

/** Offline longest-prefix match against provider edge ranges, not the origin or HTTP response. */
internal object CdnRanges {
    private data class Entry(val provider:String,val cidr:String,val bytes:ByteArray,val bits:Int){
        fun matches(ip:ByteArray):Boolean {
            if(ip.size!=bytes.size)return false
            val full=bits/8
            for(i in 0 until full)if(ip[i]!=bytes[i])return false
            val rem=bits%8
            return rem==0 || ((ip[full].toInt() xor bytes[full].toInt()) and (255 shl (8-rem)))==0
        }
    }
    private val table by lazy {
        CdnRanges::class.java.getResourceAsStream("/cdn-ranges.tsv")?.bufferedReader()?.useLines { lines ->
            lines.filter{it.isNotBlank()&&!it.startsWith('#')}.map{line->
                val (provider,cidr)=line.split('\t');val (literal,prefix)=cidr.split('/')
                require(literal.matches(Regex("[0-9a-f:.]+")))
                val bytes=InetAddress.getByName(literal).address
                Entry(provider,cidr,bytes,prefix.toInt().also{require(it in 0..bytes.size*8)})
            }.sortedByDescending{it.bits}.toList()
        }.orEmpty()
    }
    fun label(address:InetAddress):String {
        if(address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress || (address.address.size==16 && (address.address[0].toInt() and 254)==252))return "Non-CDN · local/private address"
        if(table.isEmpty())return "CDN unknown · range table unavailable"
        val entry=table.firstOrNull{it.matches(address.address)}?:return "No known CDN range"
        return "CDN: ${entry.provider} · ${entry.cidr}"
    }
}
