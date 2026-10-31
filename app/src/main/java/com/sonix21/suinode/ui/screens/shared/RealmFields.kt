package com.sonix21.suinode.ui.screens.shared

import androidx.compose.runtime.Composable
import com.sonix21.suinode.core.*
import com.sonix21.suinode.ui.glass.*

@Composable
fun RealmFields(holder: J, outbound: Boolean) {
    SwitchRow("Hysteria2 realm (NAT traversal)", holder.has("realm"), { Panel167.realm(holder.o, it, outbound) })
    if (!holder.has("realm")) return
    val r = holder.ensureObj("realm")
    GlassTextField("Realm server URL", r.str("server_url"), { r.setStr("server_url", it, false) }, hint = "https://realm.example.com")
    GlassTextField("Realm ID", r.str("realm_id"), { r.setStr("realm_id", it, false) })
    GlassTextField("Realm token", r.str("token"), { r.setStr("token", it) }, obscure = true)
    CsvField("STUN servers", r.strs("stun_servers"), { r.setStrs("stun_servers", it, false) }, hint = "stun.l.google.com:19302")
    SelectField("IP version", r.int("ip_version"), listOf(Opt("All", 0), Opt("IPv4", 4), Opt("IPv6", 6)),
        onChange = { Panel167.realmIp(r.o, it ?: 0) }, clearable = false)
    SwitchRow("Port mapping", r.obj("port_mapping")?.bool("enabled") == true,
        { if (it) r["port_mapping"] = jo("enabled" to true) else r.remove("port_mapping") }, enabled = r.int("ip_version") != 6)
    if (r.has("port_mapping")) {
        val p = r.ensureObj("port_mapping")
        GlassTextField("Port mapping timeout", p.str("timeout"), { p.setStr("timeout", it) }, hint = "10s")
        GlassTextField("Port mapping lifetime", p.str("lifetime"), { p.setStr("lifetime", it) }, hint = "10m")
    }
}
