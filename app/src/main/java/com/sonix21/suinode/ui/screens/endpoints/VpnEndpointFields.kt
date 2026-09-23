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
        GlassTextField("Interface name", j.str("name"), { j.setStr("name", it) })
        GlassTextField("Username", j.str("username"), { j.setStr("username", it) })
        GlassTextField("Password", j.str("password"), { j.setStr("password", it) }, obscure = true)
        GlassTextField("Authentication group", j.str("auth_group"), { j.setStr("auth_group", it) })
        GlassTextField("Cookie", j.str("cookie"), { j.setStr("cookie", it) }, obscure = true)
        NumberField("MTU", j.long("mtu").takeIf { it > 0 }, onChange = { j.setLong("mtu", it) })
        DurationField("UDP timeout", j.optStringOrNull("udp_timeout"), 'm', fallbackSeconds = 300,
            onChange = { j.setOrRemove("udp_timeout", it) })
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
            NumberField("Maximum clients", j.long("max_clients").takeIf { it > 0 }, onChange = { j.setLong("max_clients", it) })
        } else {
            GlassTextField("Server", j.str("server"), { j.setStr("server", it) })
            NumberField("Server port", j.long("server_port"), onChange = { j.setLong("server_port", it) })
        }
        SelectField("Mode", j.str("mode", "tls"), listOf(Opt("TLS", "tls"), Opt("Static key", "static_key")),
            onChange = { it?.let { EndpointConfig.setMode(j.o, it) } }, clearable = false)
        SelectField("Network", j.str("network", "udp"),
            (if (server) listOf("udp", "tcp") else listOf("udp", "tcp", "udp4", "udp6", "tcp4", "tcp6")).map { Opt(it.uppercase(), it) },
            onChange = { it?.let { j.o.put("network", it) } }, clearable = false)
        GlassTextField("Interface name", j.str("name"), { j.setStr("name", it) })
        if (server || staticKey) CsvField("Local addresses (CIDR)", j.strs("address"), { j.setStrs("address", it) })
        if (staticKey) {
            GlassTextField("Static key path on panel server", j.str("static_key_path"), { j.setStr("static_key_path", it) })
            SelectField("Key direction", j.optStringOrNull("key_direction"), listOf(Opt("Server", "server"), Opt("Client", "client")),
                onChange = { j.setOrRemove("key_direction", it) })
            if (!server) {
                GlassTextField("Peer IPv4 address", j.str("peer_address"), { j.setStr("peer_address", it) })
                GlassTextField("Peer IPv6 address", j.str("peer_address_ipv6"), { j.setStr("peer_address_ipv6", it) })
            }
        } else if (!server) {
            GlassTextField("Username", j.str("username"), { j.setStr("username", it) })
            GlassTextField("Password", j.str("password"), { j.setStr("password", it) }, obscure = true)
        }
        NumberField("MTU", j.long("mtu").takeIf { it > 0 }, onChange = { j.setLong("mtu", it) })
        if (staticKey) GlassTextField("Cipher", j.str("cipher"), { j.setStr("cipher", it) })
        else {
            CsvField("Data ciphers", j.strs("data_ciphers"), { j.setStrs("data_ciphers", it) })
            GlassTextField("Fallback data cipher", j.str("data_ciphers_fallback"), { j.setStr("data_ciphers_fallback", it) })
        }
        GlassTextField("Authentication digest", j.str("auth"), { j.setStr("auth", it) })
        SwitchRow("System interface", j.bool("system"), { j.setBool("system", it, onlyTrue = true) })
        if (server) SwitchRow("Allow duplicate common names", j.bool("duplicate_cn"), { j.setBool("duplicate_cn", it, onlyTrue = true) })
    }
}
