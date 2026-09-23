package com.sonix21.suinode.core

import org.json.JSONObject

object Panel161 {
    val spoofMethods = listOf("wrong-sequence", "wrong-checksum", "wrong-ack", "wrong-md5", "wrong-timestamp")
    val protectedSettings = setOf("secret", "config", "version", "globalResetLast", "maintenance")
    // SettingService.defaultValueMap minus protectedSettings. 1.6.1 leaked migration
    // bookkeeping rows in GET, which its own Save rejected; 1.6.2 fixes that GET.
    val writableSettings = setOf(
        "webListen", "webDomain", "webPort", "webCertFile", "webKeyFile", "webPath", "webURI",
        "sessionMaxAge", "trafficAge", "statsBucketSeconds", "timeLocation",
        "subListen", "subPort", "subPath", "subDomain", "subCertFile", "subKeyFile", "subUpdates",
        "subEncode", "subShowInfo", "subURI", "subJsonExt", "subClashExt",
        "subClashNoDefGrp", "subClashSprtAll", "subClashUdp", "globalReset",
    )
    fun settingsPayload(value: JSONObject): JSONObject = JSONObject().apply {
        value.keys().forEach { key -> if (key in writableSettings) {
            val setting = value.get(key)
            require(setting is String) { "Panel setting $key must be a string" }
            put(key, setting)
        } }
    }
    fun setSpoof(tls: JSONObject, value: String?) {
        if (value == null) { tls.remove("spoof"); tls.remove("spoof_method"); return }
        require(!tls.has("reality")) { "TLS spoofing cannot be combined with REALITY" }
        tls.put("spoof", value)
        if (value.isBlank()) tls.remove("spoof_method")
        else if (tls.optString("spoof_method").isBlank()) tls.put("spoof_method", "wrong-sequence")
    }
    fun coreStoppedUnexpectedly(sbd: JSONObject): Boolean = sbd.opt("running") == false && sbd.opt("maintenance") != true
    fun pemBlock(lines: List<String>, type: String): List<String> {
        val start = lines.indexOf("-----BEGIN $type-----")
        val end = lines.indexOf("-----END $type-----")
        require(start >= 0 && end > start) { "Panel returned invalid $type material" }
        return lines.subList(start, end + 1)
    }
}
