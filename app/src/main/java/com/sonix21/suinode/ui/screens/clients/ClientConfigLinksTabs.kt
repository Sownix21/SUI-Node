package com.sonix21.suinode.ui.screens.clients

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.sonix21.suinode.core.*
import com.sonix21.suinode.ui.glass.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonix21.suinode.core.J
import com.sonix21.suinode.core.jo
import com.sonix21.suinode.ui.glass.GhostButton
import com.sonix21.suinode.ui.glass.GlassCard
import com.sonix21.suinode.ui.glass.GlassTextField
import com.sonix21.suinode.ui.glass.IconGhostButton
import com.sonix21.suinode.ui.glass.LocalGlass
import com.sonix21.suinode.ui.glass.SectionHeader
import org.json.JSONArray
import org.json.JSONObject

/** Per-protocol credential editor (mirrors panel Client modal "Config" tab). */
@Composable
fun ConfigTab(j: J) {
    val g = LocalGlass.current
    val config = j.o.optJSONObject("config") ?: JSONObject().also { j.o.put("config", it) }
    val cfg = J(config)

    GlassCard(contentPadding = 14.dp) {
        SectionHeader("Protocol credentials") {
            GhostButton("Reset all", tint = g.orange) {
                for (k in config.keys().asSequence().toList()) shuffleKey(cfg, k)
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!config.has("snell")) GhostButton("Add Snell credentials") {
                config.put("snell", jo("name" to j.str("name"), "userkey" to Rand.seq(32)))
            }
            config.keys().asSequence().sorted().forEach { key ->
                val entry = config.optJSONObject(key) ?: return@forEach
                GlassCard(corner = 16.dp, contentPadding = 12.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(key, color = g.violet, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        IconGhostButton(Icons.Filled.Refresh, { shuffleKey(cfg, key) }, contentDesc = "regenerate")
                    }
                    Spacer(Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (entry.has("userkey"))
                            GlassTextField("User key", entry.optString("userkey"), { v -> entry.put("userkey", v) }, obscure = true)
                        if (entry.has("password"))
                            GlassTextField("Password", entry.optString("password"), { v -> entry.put("password", v) })
                        if (entry.has("uuid"))
                            GlassTextField("UUID", entry.optString("uuid"), { v -> entry.put("uuid", v) })
                        if (key == "vless")
                            GlassTextField("Flow", entry.optString("flow"), { v -> if (v.isBlank()) entry.remove("flow") else entry.put("flow", v) }, hint = "empty / xtls-rprx-vision")
                        if (key == "hysteria")
                            GlassTextField("Auth", entry.optString("auth_str"), { v -> entry.put("auth_str", v) })
                    }
                }
            }
        }
    }
}

/** Links tab: read-only local links + external link/subscription management. */
@Composable
fun LinksTab(j: J) {
    val g = LocalGlass.current

    fun linksList(): MutableList<JSONObject> {
        val arr = j.o.optJSONArray("links") ?: JSONArray().also { j.o.put("links", it) }
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.toMutableList()
    }

    fun setLinks(list: List<JSONObject>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        j.o.put("links", arr)
    }

    GlassCard(contentPadding = 14.dp) {
        SectionHeader("Local share links") {
            Text("read-only", color = g.textFaint, fontSize = 11.sp)
        }
        val locals = linksList().filter { it.optString("type") == "local" }
        if (locals.isEmpty()) {
            Text("no local links yet — assign compatible inbounds first", color = g.textFaint, fontSize = 12.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                locals.forEachIndexed { i, l ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}. ${l.optString("remark")}", color = g.teal, fontSize = 11.sp, modifier = Modifier.weight(0.32f))
                        Text(l.optString("uri"), color = g.textDim, fontSize = 10.5.sp, maxLines = 2, modifier = Modifier.weight(0.68f))
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    GlassCard(contentPadding = 14.dp) {
        SectionHeader("External links & subscriptions") {
            IconGhostButton(Icons.Filled.AddLink, {
                setLinks(linksList() + listOf(jo("type" to "external", "uri" to "")))
            }, contentDesc = "add external link")
        }
        Spacer(Modifier.height(8.dp))

        val rows = linksList()
        var idxShift = 0
        rows.forEachIndexed { index, l ->
            val type = l.optString("type")
            if (type == "local") return@forEachIndexed
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassTextField(
                    label = if (type == "sub") "External subscription" else "External link",
                    value = l.optString("uri"),
                    onValueChange = { v -> l.put("uri", v); },
                    hint = if (type == "sub") "https://example.com/sub/abc" else "vless://…",
                    modifier = Modifier.weight(1f),
                )
                IconGhostButton(Icons.Rounded.DeleteOutline, {
                    setLinks(linksList().filterIndexed { i, _ -> i != index })
                }, tint = g.err, contentDesc = "remove")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
            GhostButton("+ Subscription URL") {
                setLinks(linksList() + jo("type" to "sub", "uri" to ""))
            }
        }
    }
}
