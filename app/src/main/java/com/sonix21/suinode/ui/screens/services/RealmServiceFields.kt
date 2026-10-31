package com.sonix21.suinode.ui.screens.services

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.sonix21.suinode.core.*
import com.sonix21.suinode.ui.glass.*

@Composable
internal fun RealmServiceFields(j: J) {
    GlassCard(contentPadding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader("Hysteria Realm users")
            GhostButton("Add user") {
                j.ensureArr("users").put(jo("name" to "", "token" to Rand.seq(32)))
                JsonEditSignal.bump()
            }
            val users = j.ensureArr("users")
            for (i in 0 until users.length()) {
                val user = users.optJSONObject(i)?.let(::J) ?: continue
                GlassTextField("Name", user.str("name"), { user.setStr("name", it, false) })
                GlassTextField("Token", user.str("token"), { user.setStr("token", it, false) }, obscure = true)
                GhostButton("Generate token") { user["token"] = Rand.seq(32); JsonEditSignal.bump() }
                NumberField("Maximum realms", user.long("max_realms").takeIf { user.has("max_realms") },
                    onChange = { user["max_realms"] = it?.coerceAtLeast(0) })
                GhostButton("Remove user") { users.remove(i); JsonEditSignal.bump() }
            }
        }
    }
}
