package com.sonix21.suinode.core

import org.json.JSONArray
import org.json.JSONObject

/** Source: 1.6.1 types/endpoints.ts, OpenVpn.vue and database/model/endpoints.go. */
object EndpointConfig {
    val types = linkedMapOf("wireguard" to "WireGuard", "warp" to "WARP", "tailscale" to "Tailscale",
        "openconnect" to "OpenConnect", "openvpn-client" to "OpenVPN client", "openvpn-server" to "OpenVPN server")
    val tlsTypes = setOf("openconnect", "openvpn-client", "openvpn-server")

    fun create(type: String): JSONObject = when (type) {
        "wireguard" -> jo("type" to type, "address" to jarr(listOf("10.0.0.2/32", "fe80::2/128")),
            "private_key" to "", "listen_port" to 0, "peers" to JSONArray(), "ext" to jo("public_key" to "", "keys" to JSONArray()))
        "warp" -> jo("type" to type, "address" to JSONArray(), "private_key" to "", "listen_port" to 0, "mtu" to 1420,
            "peers" to jarr(listOf(jo("address" to "", "port" to 0, "public_key" to ""))), "ext" to JSONObject())
        "tailscale" -> jo("type" to type, "domain_resolver" to "local")
        "openconnect" -> jo("type" to type, "server" to "", "flavor" to "anyconnect")
        "openvpn-client" -> jo("type" to type, "server" to "", "server_port" to 1194, "mode" to "tls", "network" to "udp")
        "openvpn-server" -> jo("type" to type, "listen" to "::", "listen_port" to 1194,
            "mode" to "tls", "network" to "udp", "address" to jarr(listOf("10.8.0.1/24")), "tls" to jo("verify_client_certificate" to "none"))
        else -> throw IllegalArgumentException("Unsupported endpoint type: $type")
    }

    fun setMode(o: JSONObject, mode: String) {
        require(mode in setOf("tls", "static_key"))
        o.put("mode", mode)
        if (mode == "static_key") {
            o.remove("data_ciphers"); o.remove("data_ciphers_fallback")
        } else o.remove("cipher")
        if (mode == "static_key" && (o.optString("cipher").isBlank() || o.optString("cipher").contains("GCM", true)))
            o.put("cipher", "AES-256-CBC")
    }

    /** Prepare a copy, preserving unknown extension options. TLS has no role in static mode. */
    fun payload(o: JSONObject): JSONObject = o.deepCopy().apply {
        remove("tls_id")
        if (optString("type").startsWith("openvpn-") && optString("mode", "tls") == "static_key") remove("tls")
        optJSONObject("tls")?.let(::pruneEmpty)
        if (optJSONObject("tls")?.length() == 0) remove("tls")
    }

    private fun pruneEmpty(o: JSONObject) {
        o.keys().asSequence().toList().forEach { key ->
            val value = o.opt(key)
            if (value is JSONObject) pruneEmpty(value)
            if (value == null || value == JSONObject.NULL || value == "" ||
                (value is JSONArray && value.length() == 0) || (value is JSONObject && value.length() == 0)) o.remove(key)
        }
    }

    fun canOfferQr(o: JSONObject) = o.optString("type") == "wireguard" &&
        (o.optJSONArray("peers")?.length() ?: 0) > 0

    fun validate(o: JSONObject): String? {
        val type = o.optString("type")
        if (type in setOf("openconnect", "openvpn-client") && o.optString("server").isBlank()) return "Server is required"
        if (type == "openvpn-client" && o.optInt("server_port") !in 1..65535) return "Server port must be 1–65535"
        if (type == "openvpn-server" && o.optInt("listen_port") !in 1..65535) return "Listen port must be 1–65535"
        if (type.startsWith("openvpn-")) {
            val mode = o.optString("mode", "tls")
            val network = o.optString("network", "udp")
            if (type == "openvpn-server" && network !in setOf("tcp", "udp")) return "OpenVPN server accepts TCP or UDP"
            if ((type == "openvpn-server" || mode == "static_key") && (o.optJSONArray("address")?.length() ?: 0) == 0)
                return "At least one local address is required"
            if (mode == "static_key") {
                if (o.optString("static_key_path").isBlank()) return "Static key path is required"
                if (!o.optString("cipher").endsWith("-CBC", true)) return "Static key mode requires a CBC cipher"
                if (type == "openvpn-client" && o.optString("peer_address").isBlank() && o.optString("peer_address_ipv6").isBlank())
                    return "Static key mode requires a peer address"
            }
        }
        return null
    }

}
