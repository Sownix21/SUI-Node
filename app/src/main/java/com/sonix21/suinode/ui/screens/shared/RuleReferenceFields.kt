package com.sonix21.suinode.ui.screens.shared

import androidx.compose.runtime.Composable
import com.sonix21.suinode.core.J
import com.sonix21.suinode.ui.glass.*

/** Rule references use inbound tags and authenticated client names, never database IDs. */
@Composable
fun RuleReferenceFields(rule: J, inbounds: List<String>, clients: List<String>) {
    ReferenceSelection("Inbounds", "inbound", rule, inbounds)
    ReferenceSelection("Clients", "auth_user", rule, clients)
}

@Composable
private fun ReferenceSelection(label: String, key: String, rule: J, available: List<String>) {
    JsonEditSignal.revision
    SwitchRow("Match " + label.lowercase(), rule.has(key), {
        if (it) rule.setStrs(key, emptyList(), emptyRemoves = false) else rule.remove(key)
    })
    if (rule.has(key)) {
        val selected = when (val raw = rule[key]) {
            is String -> setOf(raw)
            else -> rule.strs(key).toSet()
        }
        val current = available.filter { it.isNotBlank() }.distinct()
        val options = current.map { Opt(it, it) } + (selected - current.toSet()).map { Opt(it + " (not currently listed)", it) }
        MultiSelectField(label, selected, options,
            { rule.setStrs(key, it.toList(), emptyRemoves = false) }, searchable = true)
    }
}
