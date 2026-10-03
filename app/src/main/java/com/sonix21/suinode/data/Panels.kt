package com.sonix21.suinode.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import com.sonix21.suinode.core.VaultRepository

/** A managed s-ui panel connection. */
data class Panel(
    val id: String,
    var name: String,
    var url: String,            // full base url incl. web path, e.g. https://host:2095/app/
    var authMode: AuthMode = AuthMode.TOKEN, // retained only for reading older saved profiles
    var token: String = "",     // apiv2 token (TOKEN mode)
    var username: String = "",  // session credentials (SESSION mode)
    var password: String = "",
    var allowInsecure: Boolean = false,
    var colorIndex: Int = 0,
    var group: String = "",
    var favorite: Boolean = false,
    var memo: String = "",
    var readOnly: Boolean = false,
    var offlineOverview: Boolean = false,
) {
    enum class AuthMode { TOKEN, SESSION }

    override fun toString(): String = "Panel([redacted])"

    fun toJson(): JSONObject = org.json.JSONObject()
        .put("id", id).put("name", name).put("url", url)
        .put("authMode", authMode.name).put("token", token)
        
        .put("allowInsecure", allowInsecure).put("colorIndex", colorIndex)
        .put("group", group).put("favorite", favorite).put("memo", memo).put("readOnly", readOnly)
        .put("offlineOverview", offlineOverview)

    companion object {
        fun fromJson(o: JSONObject): Panel = Panel(
            id = o.optString("id"),
            name = o.optString("name"),
            url = o.optString("url"),
            authMode = AuthMode.TOKEN,
            token = o.optString("token"),
            username = "",
            password = "",
            allowInsecure = o.optBoolean("allowInsecure", false),
            colorIndex = o.optInt("colorIndex", 0),
            group = o.optString("group"), favorite = o.optBoolean("favorite"),
            memo = o.optString("memo"), readOnly = o.optBoolean("readOnly"),
            offlineOverview = o.optBoolean("offlineOverview"),
        )
    }
}

/** Normalizes user-entered base URLs into canonical form with trailing slash. */
object Urls {
    fun normalize(raw: String): String? {
        var u = raw.trim()
        if (u.isEmpty()) return null
        if (u.contains("://") && !u.startsWith("http://", ignoreCase = true) && !u.startsWith("https://", ignoreCase = true)) return null
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://" + u
        // strip any /apiv2 or /api suffix users may paste
        u = u.removeSuffix("/")
        for (suffix in listOf("/apiv2", "/api")) {
            if (u.lowercase().endsWith(suffix)) u = u.dropLast(suffix.length)
        }
        if (!u.contains("://")) return null
        val parsed = runCatching { java.net.URI(u) }.getOrNull() ?: return null
        if (parsed.scheme !in setOf("http", "https") || parsed.host.isNullOrBlank()) return null
        if (parsed.userInfo != null || parsed.fragment != null || parsed.query != null) return null
        return "$u/"
    }

    fun host(url: String): String? = try {
        java.net.URI(url.removeSuffix("/")).host
    } catch (_: Exception) { null }

    fun apiBase(url: String): String {
        val clean = normalize(url) ?: throw IllegalArgumentException("Invalid panel URL")
        return clean + "apiv2/"
    }
}

/** Device-bound encrypted storage. The deprecated library is used only to migrate old data. */
class PanelStore(context: Context) {
    // Never retain an Activity through the app-wide stores or lazy migration callbacks.
    private val context: android.app.Application = context.applicationContext as android.app.Application
    private val vault by lazy { KeystoreVault(this.context) }
    private val repository by lazy {
        VaultRepository(vault::read, vault::write, vault::key, ::readLegacy, ::clearLegacy)
    }

    @Synchronized fun load(): MutableList<Panel> = guarded {
        val arr = repository.load().getJSONArray("panels")
        (0 until arr.length()).map { Panel.fromJson(arr.getJSONObject(it)) }.toMutableList()
    }

    @Synchronized fun save(panels: List<Panel>) = guarded {
        val document = repository.load()
        val arr = JSONArray()
        panels.forEach { arr.put(it.toJson()) }
        document.put("panels", arr)
        val ids=panels.map{it.id}.toSet()
        listOf("offlineOverviews","pendingSaves","vpsConfigs","vpsRenewals").forEach { section ->
            document.optJSONObject(section)?.let { records -> records.keys().asSequence().toList().filter { it !in ids }.forEach(records::remove) }
        }
        panels.filterNot{it.offlineOverview}.forEach{document.optJSONObject("offlineOverviews")?.remove(it.id)}
        repository.save(document)
    }

    @Synchronized fun activeId(): String = guarded { repository.load().getString("activePanel") }
    @Synchronized fun setActive(id: String) = guarded {
        val document = repository.load()
        if (document.getString("activePanel") != id) {
            document.put("activePanel", id)
            repository.save(document)
        }
    }

    fun protection(): String = guarded { vault.protection() }
    fun localRecord(section: String, panelId: String): JSONObject? = guarded {
        require(section in setOf("pendingSaves", "offlineOverviews"))
        repository.load().optJSONObject(section)?.optJSONObject(panelId)
    }
    fun saveLocalRecord(section: String, panelId: String, record: JSONObject?) = guarded {
        require(section in setOf("pendingSaves", "offlineOverviews"))
        val document = repository.load()
        val records = document.optJSONObject(section) ?: JSONObject().also { document.put(section,it) }
        if (record == null) records.remove(panelId) else records.put(panelId,record)
        repository.save(document)
    }
    fun inspectIdentity(url: String): JSONObject = guarded {
        val origin = com.sonix21.suinode.core.CertificateIdentity.origin(url)
        repository.load().optJSONObject("identities")?.optJSONObject(origin) ?: JSONObject()
    }
    fun checkIdentity(panel: Panel, fingerprint: String): Boolean = guarded {
        val origin = com.sonix21.suinode.core.CertificateIdentity.origin(panel.url)
        val document = repository.load()
        val identities = document.optJSONObject("identities") ?: JSONObject().also { document.put("identities", it) }
        val identity = identities.optJSONObject(origin) ?: JSONObject()
        if (identity.optString("trusted") == fingerprint) true
        else if (!identities.has(origin) && !panel.allowInsecure) {
            identities.put(origin, JSONObject().put("trusted", fingerprint).put("firstSeen", System.currentTimeMillis() / 1000))
            repository.save(document)
            true
        } else {
            if (identity.optString("pending") != fingerprint) {
                identity.put("pending", fingerprint)
                identities.put(origin, identity)
                repository.save(document)
            }
            false
        }
    }
    fun trustIdentity(url: String, reviewedFingerprint: String) = guarded {
        val origin = com.sonix21.suinode.core.CertificateIdentity.origin(url)
        val document = repository.load()
        val identity = document.getJSONObject("identities").getJSONObject(origin)
        check(reviewedFingerprint.isNotBlank() && identity.optString("pending") == reviewedFingerprint)
        identity.put("trusted", reviewedFingerprint).put("reviewedAt", System.currentTimeMillis() / 1000)
        identity.remove("pending")
        repository.save(document)
    }

    fun monitorConfig(): com.sonix21.suinode.core.MonitorConfig = guarded {
        com.sonix21.suinode.core.MonitorConfig.fromJson(repository.load().optJSONObject("monitorConfig"))
    }
    fun vpsConfig(panelId: String): com.sonix21.suinode.core.VpsQuotaConfig = guarded {
        com.sonix21.suinode.core.VpsQuotaConfig.fromJson(repository.load().optJSONObject("vpsConfigs")?.optJSONObject(panelId))
    }
    fun vpsRenewal(panelId: String): com.sonix21.suinode.core.VpsRenewal = guarded {
        com.sonix21.suinode.core.VpsRenewal.fromJson(repository.load().optJSONObject("vpsRenewals")?.optJSONObject(panelId))
    }
    fun saveVpsRenewal(panelId: String, config: com.sonix21.suinode.core.VpsRenewal) = guarded {
        config.validate()
        val document = repository.load()
        val configs = document.optJSONObject("vpsRenewals") ?: JSONObject().also { document.put("vpsRenewals", it) }
        configs.put(panelId, config.toJson()); repository.save(document)
    }
    fun saveVpsConfig(panelId: String, config: com.sonix21.suinode.core.VpsQuotaConfig) = guarded {
        config.validate()
        val document = repository.load()
        val configs = document.optJSONObject("vpsConfigs") ?: JSONObject().also { document.put("vpsConfigs", it) }
        configs.put(panelId, config.toJson())
        repository.save(document)
    }
    fun saveMonitorConfig(config: com.sonix21.suinode.core.MonitorConfig) = guarded {
        val document = repository.load().put("monitorConfig", config.toJson())
        repository.save(document)
    }
    fun monitorState(): JSONObject = guarded { repository.load().optJSONObject("monitorState") ?: JSONObject() }
    fun <T> updateMonitorState(update: (JSONObject) -> T): T = guarded {
        val document = repository.load()
        val state = document.optJSONObject("monitorState") ?: JSONObject()
        val result = update(state)
        document.put("monitorState", state)
        repository.save(document)
        result
    }

    private fun legacyExists(): Boolean {
        val directory = java.io.File(context.applicationInfo.dataDir, "shared_prefs")
        return java.io.File(directory, "$LEGACY.xml").exists() || java.io.File(directory, "$LEGACY.xml.bak").exists()
    }

    @Suppress("DEPRECATION")
    private fun readLegacy(): JSONObject? {
        if (!legacyExists()) return null
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val prefs = EncryptedSharedPreferences.create(context, LEGACY, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        return JSONObject().put("version", 1)
            .put("panels", JSONArray(prefs.getString("panels", "[]")))
            .put("activePanel", prefs.getString("activePanel", "") ?: "")
    }

    private fun clearLegacy() {
        if (legacyExists()) check(context.deleteSharedPreferences(LEGACY)) { "Legacy vault cleanup failed" }
    }

    private inline fun <T> guarded(block: () -> T): T = synchronized(storageLock) { try { block() } catch (_: Exception) {
        // No underlying exception message, JSON, path, URL or credential is disclosed.
        throw java.io.IOException("Secure storage is unavailable. Unlock the device and retry. Existing vault data has been retained; do not clear app data.")
    } }

    private companion object {
        const val LEGACY = "sui_panels_secure"
        val storageLock = Any()
    }
}
