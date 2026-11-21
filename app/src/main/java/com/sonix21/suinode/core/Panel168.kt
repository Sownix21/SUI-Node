package com.sonix21.suinode.core

import org.json.JSONObject
import java.net.URLEncoder

/** APIv2 / subscription conventions from s-ui 1.6.8. */
object Panel168 {
    fun inboundEnabled(inbound: JSONObject): Boolean = inbound.opt("enable") != false

    fun obfuscationType(obfs: JSONObject, type: String) {
        require(type in setOf("salamander", "gecko"))
        obfs.put("type", type)
        if (type != "gecko") {
            obfs.remove("min_packet_size")
            obfs.remove("max_packet_size")
        }
    }

    fun applyEch(server: JSONObject, client: JSONObject, lines: List<String>) {
        // Validate both halves before changing either; never leave mismatched keys/config.
        val keys = Panel161.pemBlock(lines, "ECH KEYS")
        val configs = Panel161.pemBlock(lines, "ECH CONFIGS")
        require(keys.size > 2 && configs.size > 2) { "Panel returned empty ECH material" }
        server.put("key", jarr(keys)); server.remove("key_path")
        client.put("config", jarr(configs)); client.remove("config_path")
        client.remove("query_server_name")
    }

    fun hasEchConfig(ech: JSONObject): Boolean =
        ech.optJSONArray("config")?.strList()?.any { it.isNotBlank() } == true

    fun subscriptionUrl(base: String, name: String, format: String? = null): String {
        require(format == null || format in setOf("json", "clash", "happ"))
        val segment = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
        return base.trimEnd('/') + "/" + segment + (format?.let { "?format=$it" } ?: "")
    }

    // Happ requires the URL as-is, not an encoded URL parameter.
    fun happImport(base: String, name: String) = "happ://add/" + subscriptionUrl(base, name, "happ")

    fun lastOnline(client: JSONObject): String = client.optLongOr("onlineAt")
        .takeIf { it > 0 && it <= Long.MAX_VALUE / 1000 }
        ?.let { Fmt.dateTime(it) } ?: "Not recorded"
}
