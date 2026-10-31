package com.sonix21.suinode.ui.screens.endpoints

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.sonix21.suinode.core.*
import com.sonix21.suinode.ui.glass.*
import com.sonix21.suinode.ui.screens.shared.CsvField
import org.json.JSONObject

@Composable
private fun EndpointOption(j: J, label: String, key: String, initial: Any, extra: List<String> = emptyList(), content: @Composable () -> Unit) {
    val enabled = (listOf(key) + extra).any(j::has)
    SwitchRow(label, enabled, { if (it) j[key] = initial else (listOf(key) + extra).forEach(j::remove) })
    if (enabled) content()
}

@Composable
private fun OptionalName(j: J) = EndpointOption(j, "Custom interface name", "name", "") {
    GlassTextField("Interface name", j.str("name"), { j.setStr("name", it, false) })
}

@Composable
private fun OptionalMtu(j: J) = EndpointOption(j, "Custom MTU", "mtu", 1500) {
    NumberField("MTU", j.long("mtu"), onChange = { j["mtu"] = it ?: 1500 })
}

@Composable
private fun EndpointSection(title: String, content: @Composable () -> Unit) {
    GlassCard(contentPadding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(title)
            content()
        }
    }
}

@Composable
internal fun OpenConnectFields(j: J) {
    EndpointSection("OpenConnect") {
        GlassTextField("Server", j.str("server"), { j.setStr("server", it) })
        SelectField("VPN flavor", j.str("flavor", "anyconnect"), listOf(
            Opt("Cisco AnyConnect", "anyconnect"), Opt("Palo Alto GlobalProtect", "gp"),
            Opt("Fortinet", "fortinet"), Opt("F5 BIG-IP", "f5"),
            Opt("Juniper Pulse", "pulse"), Opt("Junos Network Connect", "nc")),
            onChange = { it?.let { j.o.put("flavor", it) } }, clearable = false)
        OptionalName(j)
        GlassTextField("Username", j.str("username"), { j.setStr("username", it) })
        GlassTextField("Password", j.str("password"), { j.setStr("password", it) }, obscure = true)
        GhostButton("Generate password") { j["password"] = Rand.seq(16); JsonEditSignal.bump() }
        EndpointOption(j, "Authentication group", "auth_group", "") {
            GlassTextField("Group", j.str("auth_group"), { j.setStr("auth_group", it, false) })
        }
        EndpointOption(j, "Cookie authentication", "cookie", "") {
            GlassTextField("Cookie", j.str("cookie"), { j.setStr("cookie", it, false) }, obscure = true)
        }
        OptionalMtu(j)
        EndpointOption(j, "Custom UDP timeout", "udp_timeout", "5m") {
            DurationField("UDP timeout", j.optStringOrNull("udp_timeout"), 'm', fallbackSeconds = 300,
                onChange = { j.setOrRemove("udp_timeout", it) })
        }
        SwitchRow("System interface", j.bool("system"), { j.setBool("system", it, onlyTrue = true) })
        SwitchRow("Disable UDP", j.bool("no_udp"), { j.setBool("no_udp", it, onlyTrue = true) })
        SwitchRow("Disable IPv6", j.bool("ipv6_disabled"), { j.setBool("ipv6_disabled", it, onlyTrue = true) })
    }
}

@Composable
internal fun OpenVpnFields(j: J) {
    val server = j.str("type") == "openvpn-server"
    val staticKey = j.str("mode", "tls") == "static_key"
    EndpointSection(if (server) "OpenVPN server" else "OpenVPN client") {
        if (server) {
            GlassTextField("Listen address", j.str("listen"), { j.setStr("listen", it) })
            NumberField("Listen port", j.long("listen_port"), onChange = { j.setLong("listen_port", it) })
            EndpointOption(j, "Limit clients", "max_clients", 100) {
                NumberField("Maximum clients", j.long("max_clients"), onChange = { j["max_clients"] = it ?: 100 })
            }
        } else {
            GlassTextField("Server", j.str("server"), { j.setStr("server", it) })
            NumberField("Server port", j.long("server_port"), onChange = { j.setLong("server_port", it) })
        }
        SelectField("Mode", j.str("mode", "tls"), listOf(Opt("TLS", "tls"), Opt("Static key", "static_key")),
            onChange = { it?.let { EndpointConfig.setMode(j.o, it) } }, clearable = false)
        SelectField("Network", j.str("network", "udp"),
            (if (server) listOf("udp", "tcp") else listOf("udp", "tcp", "udp4", "udp6", "tcp4", "tcp6")).map { Opt(it.uppercase(), it) },
            onChange = { it?.let { j.o.put("network", it) } }, clearable = false)
        OptionalName(j)
        if (server || staticKey) CsvField("Local addresses (CIDR)", j.strs("address"), { j.setStrs("address", it) })
        if (staticKey) {
            GlassTextField("Static key path on panel server", j.str("static_key_path"), { j.setStr("static_key_path", it) })
            SelectField("Key direction", j.optStringOrNull("key_direction"), listOf(Opt("Server", "server"), Opt("Client", "client")),
                onChange = { j.setOrRemove("key_direction", it) })
            if (!server) {
                GlassTextField("Peer IPv4 address", j.str("peer_address"), { j.setStr("peer_address", it) })
                EndpointOption(j, "IPv6 peer", "peer_address_ipv6", "") {
                    GlassTextField("Peer IPv6 address", j.str("peer_address_ipv6"), { j.setStr("peer_address_ipv6", it, false) })
                }
            }
        } else if (!server) {
            EndpointOption(j, "Username/password authentication", "username", "", listOf("password")) {
                GlassTextField("Username", j.str("username"), { j.setStr("username", it, false) })
                GlassTextField("Password", j.str("password"), { j.setStr("password", it, false) }, obscure = true)
            }
        }
        OptionalMtu(j)
        if (staticKey) SelectField("Cipher", j.str("cipher"), (Panel167.staticCiphers + j.str("cipher")).filter(String::isNotBlank).distinct().map { Opt(it, it) },
            onChange = { j.setStr("cipher", it) }, clearable = false)
        else EndpointOption(j, "Custom data ciphers", "data_ciphers", jarr(listOf("AES-256-GCM", "AES-128-GCM", "CHACHA20-POLY1305")), listOf("data_ciphers_fallback")) {
            MultiSelectField("Data ciphers", j.strs("data_ciphers").toSet(), (Panel167.tlsCiphers + j.strs("data_ciphers")).distinct().map { Opt(it, it) },
                { selected -> j.setStrs("data_ciphers", j.strs("data_ciphers").filter { it in selected } + selected.filter { it !in j.strs("data_ciphers") }, false) }, searchable = true)
            SelectField("Fallback data cipher", j.optStringOrNull("data_ciphers_fallback"),
                (Panel167.tlsCiphers + j.str("data_ciphers_fallback")).filter(String::isNotBlank).distinct().map { Opt(it, it) },
                onChange = { j.setStr("data_ciphers_fallback", it) })
        }
        EndpointOption(j, "Custom authentication digest", "auth", "SHA256") {
            SelectField("Authentication digest", j.str("auth"), (Panel167.digests + j.str("auth")).distinct().map { Opt(it, it) },
                onChange = { j.setStr("auth", it) }, clearable = false)
        }
        SwitchRow("System interface", j.bool("system"), { j.setBool("system", it, onlyTrue = true) })
        if (server) SwitchRow("Allow duplicate common names", j.bool("duplicate_cn"), { j.setBool("duplicate_cn", it, onlyTrue = true) })
    }
}
