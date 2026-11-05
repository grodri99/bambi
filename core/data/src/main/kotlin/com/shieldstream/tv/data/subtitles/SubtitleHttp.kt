package com.shieldstream.tv.data.subtitles

import com.shieldstream.tv.model.tr
import android.util.Log
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubtitleHttp @Inject constructor(base: OkHttpClient) {
    val client: OkHttpClient = base.newBuilder()
        .dns(FallbackDns(base))
        .build()
}

private class FallbackDns(bootstrap: OkHttpClient) : Dns {
    private val resolvers: List<Pair<String, Dns>> = listOf(
        doh(bootstrap, "Google", "https://dns.google/dns-query", "8.8.8.8", "8.8.4.4"),
        doh(bootstrap, "Quad9", "https://dns.quad9.net/dns-query", "9.9.9.9", "149.112.112.112"),
        doh(bootstrap, "Cloudflare", "https://cloudflare-dns.com/dns-query", "1.1.1.1", "1.0.0.1"),
    )

    override fun lookup(hostname: String): List<InetAddress> {
        try {
            return Dns.SYSTEM.lookup(hostname)
        } catch (e: UnknownHostException) {
            Log.i("Subtitles", "DNS del sistema no resuelve $hostname; probando DNS-over-HTTPS")
        }
        for ((name, resolver) in resolvers) {
            try {
                return resolver.lookup(hostname).also { Log.i("Subtitles", "$hostname resuelto vía $name") }
            } catch (e: Exception) {
                Log.w("Subtitles", "DoH $name falló para $hostname: ${e.message}")
                if (e.message?.contains("NXDOMAIN") == true) break
            }
        }
        throw UnknownHostException(tr("No se pudo resolver $hostname (ni por DNS-over-HTTPS)", "Couldn't resolve $hostname (not even via DNS-over-HTTPS)"))
    }

    private companion object {
        fun doh(bootstrap: OkHttpClient, name: String, url: String, vararg ips: String): Pair<String, Dns> =
            name to DnsOverHttps.Builder()
                .client(bootstrap)
                .url(url.toHttpUrl())
                .bootstrapDnsHosts(ips.map { InetAddress.getByName(it) })
                .build()
    }
}
