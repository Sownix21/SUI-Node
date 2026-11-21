package com.sonix21.suinode.ui.screens.shared

import androidx.compose.runtime.Composable
import com.sonix21.suinode.core.*
import com.sonix21.suinode.ui.glass.*

@Composable
fun Hysteria2ObfsFields(j: J) {
    SwitchRow("Obfuscation", j.obj("obfs") != null, { on ->
        if (on) j["obfs"] = jo("type" to "salamander", "password" to "") else j.remove("obfs")
    })
    j.obj("obfs")?.let { obfs ->
        SelectField("Obfuscation type", obfs.str("type", "salamander"),
            listOf(Opt("Salamander", "salamander"), Opt("Gecko", "gecko")), clearable = false,
            onChange = { Panel168.obfuscationType(obfs.o, it ?: "salamander") })
        GlassTextField("Obfuscated password", obfs.str("password"), { obfs.setStr("password", it, blankRemoves = false) })
        if (obfs.str("type") == "gecko") {
            NumberField("Minimum packet size", obfs.long("min_packet_size").takeIf { it > 0 },
                hint = "512", onChange = { obfs.setLong("min_packet_size", it) })
            NumberField("Maximum packet size", obfs.long("max_packet_size").takeIf { it > 0 },
                hint = "1200", onChange = { obfs.setLong("max_packet_size", it) })
        }
    }
}
