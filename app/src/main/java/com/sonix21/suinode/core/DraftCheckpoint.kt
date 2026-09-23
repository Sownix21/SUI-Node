package com.sonix21.suinode.core

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

object JsonCanonical {
    fun text(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + text(value.opt(it)) }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { text(value.opt(it)) }
        is String -> JSONObject.quote(value)
        else -> value.toString()
    }
}

class DraftCheckpoint(private val value: () -> Any?) {
    private var baseline = digest()
    private fun digest() = MessageDigest.getInstance("SHA-256").digest(JsonCanonical.text(value()).toByteArray(Charsets.UTF_8))
    fun changed(): Boolean = !MessageDigest.isEqual(baseline, digest())
    fun accept() { baseline = digest() }
}

object DraftRegistry {
    private val checkpoints = mutableSetOf<DraftCheckpoint>()
    @Synchronized fun register(checkpoint: DraftCheckpoint) { checkpoints.add(checkpoint) }
    @Synchronized fun remove(checkpoint: DraftCheckpoint) { checkpoints.remove(checkpoint) }
    @Synchronized fun saved() { checkpoints.forEach { it.accept() } }
}
