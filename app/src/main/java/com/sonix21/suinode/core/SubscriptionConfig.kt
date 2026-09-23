package com.sonix21.suinode.core

import org.json.JSONArray
import org.json.JSONObject
import org.snakeyaml.engine.v2.api.*
import org.snakeyaml.engine.v2.common.FlowStyle

/** Subscription extensions are STRING settings: JSON for sing-box, YAML for Clash. */
object SubscriptionConfig {
    fun parse(raw: String, clash: Boolean): JSONObject {
        if (raw.isBlank()) return JSONObject()
        require(raw.length <= 1_000_000) { "Subscription extension is too large" }
        if (!clash) return JSONObject(raw)
        val settings = LoadSettings.builder().setAllowDuplicateKeys(false)
            .setMaxAliasesForCollections(50).setCodePointLimit(1_000_000).build()
        val value = Load(settings).loadFromString(raw) ?: return JSONObject()
        require(value is Map<*, *>) { "Clash extension must be a YAML mapping" }
        return toJson(value, 0, intArrayOf(100_000)) as JSONObject
    }

    private fun toJson(value: Any?, depth: Int, budget: IntArray): Any {
        require(depth <= 50) { "Extension nesting is too deep" }
        require(--budget[0] >= 0) { "Extension has too many expanded values" }
        return when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> JSONObject().also { out -> value.forEach { (k, v) ->
                require(k is String) { "Extension keys must be strings" }
                out.put(k, toJson(v, depth + 1, budget))
            } }
            is List<*> -> JSONArray().also { out -> value.forEach { out.put(toJson(it, depth + 1, budget)) } }
            is String, is Boolean, is Number -> value
            else -> error("Unsupported YAML value")
        }
    }

    private fun plain(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> value.keys().asSequence().associateWith { plain(value.opt(it)) }
        is JSONArray -> (0 until value.length()).map { plain(value.opt(it)) }
        else -> value
    }

    fun encode(o: JSONObject, clash: Boolean): String = when {
        o.length() == 0 -> ""
        clash -> Dump(DumpSettings.builder().setDefaultFlowStyle(FlowStyle.BLOCK).build()).dumpToString(plain(o))
        else -> o.toString(2)
    }

    fun validateSettings(settings: JSONObject) {
        parse(settings.optString("subJsonExt"), false)
        parse(settings.optString("subClashExt"), true)
    }

    val defaultRules: List<JSONObject> get() = listOf(
        jo("action" to "sniff"),
        jo("clash_mode" to "Direct", "action" to "route", "outbound" to "direct"),
        jo("clash_mode" to "Global", "action" to "route", "outbound" to "proxy"),
    )
    private fun same(a: JSONObject, b: JSONObject): Boolean =
        a.length() == b.length() && b.keys().asSequence().all { a.opt(it) == b.opt(it) }

    fun selectedDefaults(o: JSONObject): Set<String> = defaultRules.mapIndexedNotNull { i, d ->
        i.toString().takeIf { o.optJSONArray("rules")?.objList()?.any { same(it, d) } == true }
    }.toSet()

    fun defaults(o: JSONObject, selected: Set<String>) {
        val known = defaultRules
        val rules = o.optJSONArray("rules")?.objList().orEmpty().filterNot { r -> known.any { same(r, it) } }.toMutableList()
        known.forEachIndexed { i, r -> if (i.toString() in selected) rules.add(r) }
        val ordered = rules.sortedBy { when {
            it.optString("protocol") == "dns" -> 0
            it.optString("action") == "sniff" -> 1
            it.optString("clash_mode") == "Direct" -> 2
            it.optString("clash_mode") == "Global" -> 3
            else -> 4
        } }
        if (ordered.isEmpty()) o.remove("rules") else o.put("rules", jarr(ordered))
    }

    fun jsonOption(o: JSONObject, key: String, enabled: Boolean) {
        if (enabled) {
            if (!o.has(key)) o.put(key, jsonDefault(key))
            if (key == "dns") {
                val rules = o.optJSONArray("rules") ?: jarr(listOf(jo("action" to "sniff"))).also { o.put("rules", it) }
                if (rules.objList().none { it.optString("protocol") == "dns" && it.optString("action") == "hijack-dns" })
                    o.put("rules", jarr(listOf(jo("protocol" to "dns", "action" to "hijack-dns")) + rules.objList()))
            }
        } else {
            o.remove(key)
            if (key == "dns") {
                val rules = o.optJSONArray("rules")?.objList().orEmpty()
                    .filterNot { it.optString("protocol") == "dns" && it.optString("action") == "hijack-dns" }
                if (rules.isEmpty()) o.remove("rules") else o.put("rules", jarr(rules))
                o.remove("default_domain_resolver")
                updateRuleSets(o)
            }
        }
    }

    fun jsonDefault(key: String): Any = when (key) {
        "log" -> jo("level" to "info", "timestamp" to true)
        "inbounds" -> JSONArray("""[
          {"type":"tun","address":["172.19.0.1/30","fdfe:dcba:9876::1/126"],"mtu":9000,
           "auto_route":true,"strict_route":false,"stack":"mixed","exclude_package":[],
           "platform":{"http_proxy":{"enabled":true,"server":"127.0.0.1","server_port":2080}}},
          {"type":"mixed","listen":"127.0.0.1","listen_port":2080,"users":[]}]""")
        "experimental" -> JSONObject("""{
          "clash_api":{"external_controller":"127.0.0.1:9090","external_ui":"ui","secret":"",
          "external_ui_download_url":"https://mirror.ghproxy.com/https://github.com/MetaCubeX/Yacd-meta/archive/gh-pages.zip",
          "external_ui_download_detour":"direct","default_mode":"rule"},
          "cache_file":{"enabled":true,"store_fakeip":false}}""")
        "dns" -> JSONObject("""{
          "servers":[{"type":"tcp","tag":"proxy-dns","server":"8.8.8.8","server_port":53,"detour":"proxy","domain_resolver":"local-dns"},
          {"tag":"direct-dns","type":"local"},{"tag":"local-dns","type":"local"}],
          "rules":[{"clash_mode":"Global","source_ip_cidr":["172.19.0.0/30","fdfe:dcba:9876::1/126"],"action":"route","server":"proxy-dns"},
          {"clash_mode":"Direct","action":"route","server":"direct-dns"},
          {"source_ip_cidr":["172.19.0.0/30","fdfe:dcba:9876::1/126"],"action":"route","server":"proxy-dns"}],
          "final":"local-dns","strategy":"prefer_ipv4"}""")
        else -> error("Unknown JSON option")
    }

    private fun matches(r: JSONObject, target: String, dns: Boolean) = r.has("rule_set") &&
        if (dns) r.optString("server") == "direct-dns"
        else if (target == "reject") r.optString("action") == "reject"
        else r.optString("outbound") == target

    fun routeTags(o: JSONObject, target: String, dns: Boolean = false): Set<String> {
        val holder = if (dns) o.optJSONObject("dns") else o
        return holder?.optJSONArray("rules")?.objList()?.firstOrNull { matches(it, target, dns) }
            ?.optJSONArray("rule_set")?.strList()?.toSet().orEmpty()
    }

    fun setRouteTags(o: JSONObject, target: String, tags: Set<String>, dns: Boolean = false) {
        val holder = if (dns) o.getJSONObject("dns") else o
        val rules = holder.optJSONArray("rules")?.objList().orEmpty().toMutableList()
        val index = rules.indexOfFirst { matches(it, target, dns) }
        if (tags.isEmpty()) { if (index >= 0) rules.removeAt(index) }
        else if (index >= 0) rules[index].put("rule_set", jarr(tags))
        else rules.add(jo("rule_set" to jarr(tags), "action" to if (target == "reject") "reject" else "route").also {
            if (dns) it.put("server", "direct-dns") else if (target != "reject") it.put("outbound", target)
        })
        if (rules.isEmpty()) holder.remove("rules") else holder.put("rules", jarr(rules))
        updateRuleSets(o)
    }

    // Matches the bundled frontend rulesetCatalog.ts; no download occurs in this editor.
    val catalog: List<JSONObject> get() {
        val names = listOf(
            "geosite-ads" to "category-ads-all", "geosite-private" to "private",
            "geosite-ir" to "category-ir", "geosite-cn" to "cn", "geosite-vn" to "vn",
            "geoip-private" to "private", "geoip-ir" to "ir", "geoip-cn" to "cn", "geoip-vn" to "vn",
            "geosite-google" to "google", "geoip-google" to "google", "geosite-google-play" to "google-play",
            "geosite-youtube" to "youtube", "geosite-twitter" to "twitter", "geoip-twitter" to "twitter",
            "geosite-telegram" to "telegram", "geoip-telegram" to "telegram",
            "geosite-netflix" to "netflix", "geoip-netflix" to "netflix",
            "geosite-openai" to "openai", "geosite-reddit" to "reddit")
        return names.map { (tag, name) -> jo("tag" to tag, "type" to "remote", "format" to "binary",
            "url" to if (tag == "geosite-vn") "https://github.com/Thaomtam/Geosite-vn/raw/rule-set/Geosite-vn.srs"
            else "https://testingcf.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@sing/geo/" + tag.substringBefore("-") + "/" + name + ".srs") }
    }

    fun updateRuleSets(o: JSONObject) {
        val used = (o.optJSONArray("rules")?.objList().orEmpty() +
            o.optJSONObject("dns")?.optJSONArray("rules")?.objList().orEmpty())
            .flatMap { it.optJSONArray("rule_set")?.strList().orEmpty() }.toSet()
        val existing = o.optJSONArray("rule_set")?.objList().orEmpty()
        val known = catalog
        val knownTags = known.map { it.optString("tag") }.toSet()
        val result = known.filter { it.optString("tag") in used }.map { entry ->
            existing.firstOrNull { it.optString("tag") == entry.optString("tag") } ?: entry
        } + existing.filter { it.optString("tag") !in knownTags }
        if (result.isEmpty()) o.remove("rule_set") else o.put("rule_set", jarr(result))
    }

    fun clashOption(o: JSONObject, key: String, enabled: Boolean) {
        val defaults = JSONObject("""{
          "mixed-port":7890,"allow-lan":false,"mode":"rule","log-level":"info","external-controller":"127.0.0.1:9090",
          "tun":{"enable":true,"stack":"system","auto-route":true,"auto-detect-interface":true,"dns-hijack":["any:53"]},
          "dns":{"enable":true,"ipv6":false,"enhanced-mode":"fake-ip","fake-ip-range":"198.18.0.1/16",
          "default-nameserver":["8.8.8.8","1.1.1.1"],"nameserver":["https://doh.pub/dns-query","https://1.0.0.1/dns-query"],
          "fallback":["tcp://9.9.9.9:53"],"fake-ip-filter":["*.lan","localhost","*.local"]},
          "rules":["GEOIP,Private,DIRECT","MATCH,Proxy"]}""")
        val keys = listOf(key) + when (key) { "mixed-port" -> listOf("allow-lan"); "rules" -> listOf("mode"); else -> emptyList() }
        keys.forEach { if (enabled) { if (!o.has(it)) o.put(it, defaults.get(it)) } else o.remove(it) }
        if (enabled && key in listOf("tun", "dns")) o.getJSONObject(key).put("enable", true)
    }

    val clashRules: List<Pair<String, String>> get() =
        listOf("Private" to "Private", "LAN" to "LAN", "Ads" to "Ads", "China" to "CN",
            "Iran" to "CATEGORY-IR", "Vietnam" to "CATEGORY-VN", "Japan" to "JP")
            .flatMap { (label, code) -> listOf(label + " · Direct" to "GEOIP," + code + ",DIRECT",
                label + " · Block" to "GEOIP," + code + ",REJECT") }

    fun setClashRules(o: JSONObject, selected: Set<String>) {
        val old = o.optJSONArray("rules")?.strList().orEmpty()
        val known = clashRules.map { it.second }.toSet()
        // Only replace the preset rules this control owns; custom rules retain their order.
        val custom = old.filter { it !in known && it != "MATCH,Proxy" }
        o.put("rules", jarr(clashRules.map { it.second }.filter { it in selected } + custom + "MATCH,Proxy"))
    }
}
