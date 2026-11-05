package com.shieldstream.tv.network

import android.util.Log
import okhttp3.Call
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.Protocol
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

object CloudflareFallbackDns : Dns {
    private val failed = Collections.newSetFromMap(ConcurrentHashMap<InetAddress, Boolean>())
    @Volatile private var extras: List<InetAddress> = emptyList()

    override fun lookup(hostname: String): List<InetAddress> {
        val primary = Dns.SYSTEM.lookup(hostname)
        if (primary.none { isCloudflare(it) }) return primary
        val alternatives = extras.ifEmpty {
            POOL_HOSTS.flatMap { h -> runCatching { Dns.SYSTEM.lookup(h) }.getOrDefault(emptyList()) }
                .filterIsInstance<Inet4Address>().distinct().also { extras = it }
        }
        val all = (primary + alternatives).distinct()
        return all.filter { it !in failed } + all.filter { it in failed }
    }

    val failureListener = object : EventListener() {
        override fun connectFailed(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?, ioe: java.io.IOException) {
            val ip = inetSocketAddress.address ?: return
            if (isCloudflare(ip) && failed.add(ip)) Log.w("CfDns", "IP de Cloudflare bloqueada: ${ip.hostAddress} (${call.request().url.host})")
        }
    }

    private val POOL_HOSTS = listOf("apibay.org", "api.knaben.org", "aiostreams.elfhosted.com")

    private val ranges = listOf(
        "104.16.0.0/13", "104.24.0.0/14", "172.64.0.0/13", "188.114.96.0/20", "162.158.0.0/15", "141.101.64.0/18",
        "108.162.192.0/18", "190.93.240.0/20", "198.41.128.0/17", "173.245.48.0/20", "103.21.244.0/22",
        "103.22.200.0/22", "103.31.4.0/22", "131.0.72.0/22", "197.234.240.0/22",
    ).map { cidr ->
        val (ip, bits) = cidr.split('/')
        ipToInt(InetAddress.getByName(ip).address) to bits.toInt()
    }

    private fun isCloudflare(addr: InetAddress): Boolean {
        if (addr !is Inet4Address) return false
        val v = ipToInt(addr.address)
        return ranges.any { (net, bits) -> val mask = if (bits == 0) 0 else -1 shl (32 - bits); (v and mask) == (net and mask) }
    }

    private fun ipToInt(b: ByteArray): Int =
        ((b[0].toInt() and 0xff) shl 24) or ((b[1].toInt() and 0xff) shl 16) or ((b[2].toInt() and 0xff) shl 8) or (b[3].toInt() and 0xff)
}
