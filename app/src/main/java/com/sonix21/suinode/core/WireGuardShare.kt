package com.sonix21.suinode.core

import org.json.JSONObject

/** Mirrors WgQrCode.vue: export the peer’s client configuration, never the server’s key. */
object WireGuardShare {
    fun peerConfig(endpoint: JSONObject, peerIndex: Int, panelHost: String): String? {
        if (!EndpointConfig.canOfferQr(endpoint)) return null
        val peer = endpoint.optJSONArray("peers")?.optJSONObject(peerIndex) ?: return null
        val ext = endpoint.optJSONObject("ext") ?: return null
        val serverKey = ext.optString("public_key").takeIf { it.isNotBlank() } ?: return null
        val peerKey = peer.optString("public_key").takeIf { it.isNotBlank() } ?: return null
        val keys = ext.optJSONArray("keys") ?: return null
        val privateKey = (0 until keys.length()).mapNotNull { keys.optJSONObject(it) }
            .firstOrNull { it.optString("public_key") == peerKey }?.optString("private_key")
            ?.takeIf { it.isNotBlank() } ?: return null
        val addresses = peer.optJSONArray("allowed_ips")?.strList()?.joinToString(", ")
            ?.takeIf { it.isNotBlank() } ?: return null
        val host = ext.optString("server").ifBlank { panelHost }.trim().removeSurrounding("[", "]")
        val port = endpoint.optInt("listen_port")
        if (host.isBlank() || port !in 1..65535) return null
        val dns = ext.optString("dns").ifBlank { "1.1.1.1, 9.9.9.9" }
        return buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = $privateKey")
            appendLine("Address = $addresses")
            appendLine("DNS = $dns")
            endpoint.optLong("mtu").takeIf { it > 0 }?.let { appendLine("MTU = $it") }
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = $serverKey")
            appendLine("AllowedIPs = 0.0.0.0/0, ::/0")
            appendLine("Endpoint = ${if (':' in host) "[$host]" else host}:$port")
            peer.optString("pre_shared_key").takeIf { it.isNotBlank() }?.let { appendLine("PresharedKey = $it") }
            peer.optLong("persistent_keepalive_interval").takeIf { it > 0 }?.let { appendLine("PersistentKeepalive = $it") }
        }.trimEnd()
    }
}
