package com.oasismall.oasisai.util

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    /** Best-effort LAN IPv4 (hotspot / Wi‑Fi), for master phone display. */
    fun getLanIpv4(): String? {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        val candidates = mutableListOf<String>()
        for (ni in interfaces) {
            if (!ni.isUp || ni.isLoopback) continue
            for (addr in ni.inetAddresses.toList()) {
                if (addr.isLoopbackAddress || addr !is Inet4Address) continue
                val host = addr.hostAddress ?: continue
                when {
                    host.startsWith("192.168.") -> return host
                    host.startsWith("10.") -> candidates.add(host)
                    host.startsWith("172.") -> candidates.add(host)
                }
            }
        }
        return candidates.firstOrNull()
    }
}
