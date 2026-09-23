package com.sonix21.suinode.ui.screens.outbounds

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonix21.suinode.core.Rand
import com.sonix21.suinode.core.jarr
import com.sonix21.suinode.core.jo
import com.sonix21.suinode.core.optStringOrNull
import com.sonix21.suinode.ui.glass.GlassCard
import com.sonix21.suinode.ui.glass.GlassTextField
import com.sonix21.suinode.ui.glass.IconGhostButton
import com.sonix21.suinode.ui.glass.LocalGlass
import com.sonix21.suinode.ui.glass.PrimaryButton
import com.sonix21.suinode.ui.glass.SectionHeader
import com.sonix21.suinode.ui.glass.SelectField
import com.sonix21.suinode.ui.glass.StatusDot
import com.sonix21.suinode.ui.glass.SwitchRow
import com.sonix21.suinode.ui.glass.ToastBus
import com.sonix21.suinode.ui.nav.NavController
import com.sonix21.suinode.ui.screens.PageScaffold
import com.sonix21.suinode.ui.screens.inbounds.TypeChip
import com.sonix21.suinode.ui.screens.rememberRunner
import com.sonix21.suinode.ui.screens.useSession

/** Import outbounds from a share link or a subscription URL. */
@Composable
fun OutboundImportScreen(nav: NavController) {
    val g = LocalGlass.current
    val session = useSession()
    val runner = rememberRunner()

    var link by remember { mutableStateOf("") }
    var addUrlTest by remember { mutableStateOf(false) }
    var imported by remember { mutableStateOf(listOf<org.json.JSONObject>()) }
    var states by remember { mutableStateOf(mapOf<Int, Int>()) } // 0 pending 1 ok 2 fail 3 busy

    PageScaffold(title = "Import Outbounds", subtitle = "share link / subscription", nav = nav, busy = runner.busy,
        draftValue = { jo("link" to link, "urltest" to addUrlTest, "imported" to jarr(imported)) }, primaryAction = {
        if (imported.isNotEmpty()) HeaderPrimaryAction("Add all non-duplicates", loading = runner.busy) {
                    runner.go {
                        var ok = 0; var fail = 0
                        imported.forEachIndexed { i, o ->
                            val dup = session.data.value.outbounds.any { it.optString("tag") == o.optString("tag") }
                            if (dup) fail++ else {
                                val r = session.save("outbounds", "new", o)
                                if (r.isSuccess) { ok++; states = states + (i to 1) } else { fail++; states = states + (i to 2) }
                            }
                        }
                        ToastBus.show("added $ok, skipped/duplicated $fail")
                        if (fail == 0) nav.pop()
                    }
                }
    }) {
        Column(Modifier.weight(1f)) {
            Column(Modifier.androidScroll(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassTextField("Share link or subscription URL", link, { link = it },
                    hint = "vless://…  |  https://example.com/sub/…")

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("Convert", enabled = link.isNotBlank()) {
                        runner.go {
                            imported = emptyList(); states = emptyMap()
                            val isSub = !link.contains("://") ||
                                link.substringAfter("://").substringBefore('/').isBlank() ||
                                listOf("http://", "https://").any { link.startsWith(it) && link.count { c -> c == '/' } >= 3 && !link.startsWith("ss://") }
                            if (!link.startsWith("http")) {
                                // single link convert
                                val env = session.client.postForm("linkConvert", mapOf("link" to link))
                                if (!env.success) throw Exception(env.msg)
                                val ob = env.objObj() ?: throw Exception("empty result")
                                ob.put("id", 0L)
                                imported = listOf(ob)
                            } else {
                                val env = session.client.postForm("subConvert", mapOf("link" to link))
                                if (!env.success) throw Exception(env.msg)
                                val arr = env.objArr() ?: throw Exception("no outbounds returned")
                                val list = mutableListOf<org.json.JSONObject>()
                                val tags = mutableSetOf<String>()
                                for (i in 0 until arr.length()) {
                                    val o = arr.optJSONObject(i)?.deepCopyX2() ?: continue
                                    var tag = o.optString("tag")
                                    if (tag.isBlank()) tag = "out-" + Rand.seq(3)
                                    if (tag in tags) tag = "$tag-${list.size + 1}"
                                    tags.add(tag); o.put("tag", tag); o.put("id", 0L)
                                    list.add(o)
                                }
                                if (addUrlTest && list.isNotEmpty()) {
                                    val t = jo(
                                        "type" to "urltest",
                                        "tag" to ("urltest-" + Rand.seq(3)),
                                        "outbounds" to jarr(list.map { it.optString("tag") }),
                                        "interrupt_exist_connections" to false,
                                        "interval" to "30s",
                                    )
                                    list.add(t)
                                }
                                imported = list
                            }
                            ToastBus.show("${imported.size} outbound(s) ready")
                        }
                    }
                    SwitchRow("Add urltest group", addUrlTest, { addUrlTest = it })
                }

                if (imported.isNotEmpty()) {
                    SectionHeader("Preview")
                    GlassCard(contentPadding = 8.dp) {
                        Column {
                            imported.forEachIndexed { i, o ->
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                    when (states[i] ?: 0) {
                                        1 -> StatusDot(g.green, 8.dp); 2 -> StatusDot(g.err, 8.dp)
                                        else -> StatusDot(g.textFaint, 8.dp)
                                    }
                                    TypeChip(o.optString("type"))
                                    Text(o.optString("tag"), color = g.text, fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                                    Text(
                                        buildString {
                                            o.optStringOrNull("server")?.let { append(it); o.optLongOrX("server_port").takeIf { p -> p > 0 }?.let { append(":$it") } }
                                        }.ifBlank { "-" },
                                        color = g.textFaint, fontSize = 11.sp, maxLines = 1,
                                    )
                                    IconGhostButton(Icons.Filled.Link, {
                                        runner.go {
                                            states = states + (i to 3)
                                            val dup = session.data.value.outbounds.any { it.optString("tag") == o.optString("tag") }
                                            if (dup) { states = states + (i to 2); throw Exception("duplicate tag: ${o.optString("tag")} — skipped") }
                                            val r = session.save("outbounds", "new", o)
                                            states = states + (i to if (r.isSuccess) 1 else 2)
                                            if (r.isFailure) throw Exception(r.exceptionOrNull()?.message ?: "save failed")
                                        }
                                    }, contentDesc = "add")
                                }
                            }
                        }
                    }
                }
            }
        }

    }
}

private fun org.json.JSONObject.deepCopyX2(): org.json.JSONObject = org.json.JSONObject(toString())

@Composable
private fun Modifier.androidScroll(): Modifier =
    this.verticalScroll(rememberScrollState())
