package com.sonix21.suinode.core

import org.json.JSONObject

object Panel163 {
    val http2Keys = listOf("idle_timeout", "keep_alive_period", "stream_receive_window", "connection_receive_window", "max_concurrent_streams")
    val quicOnlyKeys = listOf("initial_packet_size", "disable_path_mtu_discovery")
    val clientAuth = listOf("no", "request", "require-any", "verify-if-given", "require-and-verify")
    fun httpVersion(o: JSONObject, version: Int) {
        require(version in 0..3)
        o.put("version", version)
        if (version == 0) o.remove("disable_version_fallback")
        if (version != 3) quicOnlyKeys.forEach(o::remove)
        if (version == 1) http2Keys.forEach(o::remove)
    }
    fun fragment(o: JSONObject, enabled: Boolean) {
        if (enabled) o.put("fragment", true)
        else listOf("fragment", "fragment_fallback_delay", "record_fragment").forEach(o::remove)
    }
    val serverMutualKeys = listOf("client_authentication", "client_certificate", "client_certificate_path", "client_certificate_public_key_sha256")
    val clientMutualKeys = listOf("client_certificate", "client_certificate_path", "client_key", "client_key_path")
    fun mutualSource(server: JSONObject?, client: JSONObject, text: Boolean) {
        if (text) {
            server?.remove("client_certificate_path"); server?.put("client_certificate", org.json.JSONArray())
            client.remove("client_certificate_path"); client.remove("client_key_path")
            client.put("client_certificate", org.json.JSONArray()); client.put("client_key", org.json.JSONArray())
        } else {
            server?.remove("client_certificate"); server?.put("client_certificate_path", org.json.JSONArray())
            client.remove("client_certificate"); client.remove("client_key")
            client.put("client_certificate_path", ""); client.put("client_key_path", "")
        }
    }
    fun disableMutual(server: JSONObject?, client: JSONObject) {
        serverMutualKeys.forEach { server?.remove(it) }
        clientMutualKeys.forEach(client::remove)
    }
}
