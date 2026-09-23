package com.sonix21.suinode.core

import org.json.JSONArray
import org.json.JSONObject

object ServiceConfig {
    val types = linkedMapOf("derp" to "DERP relay", "resolved" to "Resolved DNS", "ssm-api" to "Shadowsocks API",
        "ocm" to "Codex multiplexer", "ccm" to "Claude multiplexer", "api" to "Core API", "oom-killer" to "OOM killer")
    val tlsTypes = setOf("derp", "ssm-api", "ocm", "ccm", "api")
    fun create(type: String): JSONObject = when (type) {
        "derp" -> jo("config_path" to "", "tls_id" to 0)
        "resolved" -> jo("listen" to "::", "listen_port" to 53)
        "ssm-api" -> jo("listen" to "::", "listen_port" to 8080, "tls_id" to 0, "servers" to JSONObject())
        "ocm", "ccm" -> jo("listen" to "::", "listen_port" to 8080, "tls_id" to 0, "users" to JSONArray())
        "api" -> jo("listen" to "127.0.0.1", "listen_port" to 9090, "tls_id" to 0)
        "oom-killer" -> JSONObject()
        else -> throw IllegalArgumentException("Unsupported service type: $type")
    }.put("type", type)

    fun pathRows(servers: JSONObject?): List<Pair<String, String>> = buildList {
        servers?.keys()?.forEach { path ->
            when (val value = servers.opt(path)) {
                is JSONArray -> value.strList().forEach { add(path to it) }
                is String -> add(path to value)
            }
        }
    }

    /** Repeated paths map to arrays, as in the web panel; never overwrite siblings. */
    fun paths(rows: List<Pair<String, String>>): JSONObject = JSONObject().apply {
        rows.forEach { (path, tag) ->
            when (val old = opt(path)) {
                null -> put(path, tag)
                is JSONArray -> old.put(tag)
                else -> put(path, JSONArray().put(old).put(tag))
            }
        }
    }

    fun dashboardEnabled(o: JSONObject): Boolean = when (val d = o.opt("dashboard")) {
        is Boolean -> d
        is String -> d.isNotEmpty()
        is JSONObject -> d.optBoolean("enabled")
        else -> false
    }
    fun dashboardObject(o: JSONObject): JSONObject = o.optJSONObject("dashboard") ?: jo("enabled" to true).also {
        (o.opt("dashboard") as? String)?.takeIf(String::isNotBlank)?.let { path -> it.put("path", path) }
        o.put("dashboard", it)
    }
    fun setDashboardPath(o: JSONObject, path: String) {
        val d = o.optJSONObject("dashboard")
        if (d != null) d.setOrRemove("path", path.takeIf(String::isNotBlank))
        else o.put("dashboard", path.takeIf(String::isNotBlank) ?: true)
    }
    fun setDashboardDownload(o: JSONObject, enabled: Boolean) {
        if (enabled) dashboardObject(o).put("download_url", "")
        else o.optJSONObject("dashboard")?.let { d ->
            listOf("download_url", "http_client", "update_interval").forEach(d::remove)
            // Preserve extension fields even when the download controls are disabled.
            if (d.keys().asSequence().all { it == "enabled" || it == "path" })
                o.put("dashboard", d.optString("path").takeIf(String::isNotBlank) ?: true)
        }
    }
    fun payload(o: JSONObject): JSONObject = o.deepCopy().apply {
        if (optString("type") == "oom-killer") listOf("listen", "listen_port", "tls_id", "tls", "detour",
            "tcp_fast_open", "tcp_multi_path", "udp_fragment", "udp_timeout", "disable_tcp_keep_alive",
            "tcp_keep_alive", "tcp_keep_alive_interval").forEach(::remove)
    }
}
