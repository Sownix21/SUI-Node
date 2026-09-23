package com.sonix21.suinode.core

import org.json.JSONArray
import org.json.JSONObject

/** Create a JSONObject from pairs; null values are skipped. */
fun jo(vararg pairs: Pair<String, Any?>): JSONObject {
    val o = JSONObject()
    for ((k, v) in pairs) if (v != null) o.put(k, v)
    return o
}

fun jarr(items: Iterable<Any?>): JSONArray {
    val a = JSONArray()
    for (i in items) if (i != null) a.put(i)
    return a
}

fun JSONObject.deepCopy(): JSONObject = JSONObject(this.toString())

fun JSONArray.deepCopy(): JSONArray {
    val out = JSONArray()
    for (i in 0 until length()) {
        val v = opt(i)
        when (v) {
            is JSONObject -> out.put(v.deepCopy())
            is JSONArray -> out.put(v.deepCopy())
            null -> out.put(JSONObject.NULL)
            else -> out.put(v)
        }
    }
    return out
}

/** Put value or remove key when value == null. Mirrors JS `obj.k = undefined`. */
fun JSONObject.setOrRemove(key: String, value: Any?) {
    if (value == null) remove(key) else put(key, value)
}

fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key) else null

fun JSONObject.optLongOr(key: String, def: Long = 0L): Long =
    if (has(key) && !isNull(key)) {
        when (val v = opt(key)) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull() ?: def
            else -> def
        }
    } else def

fun JSONObject.optIntOr(key: String, def: Int = 0): Int =
    if (has(key) && !isNull(key)) {
        when (val v = opt(key)) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: def
            else -> def
        }
    } else def

fun JSONObject.optBoolOr(key: String, def: Boolean = false): Boolean =
    if (has(key) && !isNull(key)) optBoolean(key) else def

fun JSONObject.optObj(key: String): JSONObject? = optJSONObject(key)

fun JSONObject.optArr(key: String): JSONArray? = optJSONArray(key)

// JSONObject conveniences for editor code that does not need a mutable J wrapper.
fun JSONObject.str(key: String, def: String = ""): String = optStringOrNull(key) ?: def
fun JSONObject.long(key: String, def: Long = 0L): Long = optLongOr(key, def)
fun JSONObject.bool(key: String, def: Boolean = false): Boolean = optBoolOr(key, def)
fun JSONObject.arr(key: String): JSONArray? = optJSONArray(key)
fun JSONObject.obj(key: String): J? = optJSONObject(key)?.let(::J)
fun JSONObject.setStrs(key: String, values: List<String>, emptyRemoves: Boolean = true) {
    setOrRemove(key, if (values.isEmpty() && emptyRemoves) null else jarr(values))
}

/** String list from a JSONArray of primitives. */
fun JSONArray.strList(): List<String> = (0 until length()).map { optString(it) }

/** Public compatibility alias used by the protocol editors. */
fun JSONArray.strListX(): List<String> = strList()

fun JSONArray.longList(): List<Long> =
    (0 until length()).map { when (val v = opt(it)) { is Number -> v.toLong(); is String -> v.toLongOrNull() ?: 0L; else -> 0L } }

fun JSONArray.objList(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

fun JSONArray.toJsonObjectList(): MutableList<JSONObject> = objList().toMutableList()

/** Pretty print like JSON.stringify(x, null, 2). */
fun Any?.prettyJson(): String = when (this) {
    is JSONObject -> toString(2)
    is JSONArray -> toString(2)
    else -> toString()
}

/** Merge src into dst recursively (objects merged, arrays replaced). */
fun JSONObject.mergeFrom(src: JSONObject) {
    for (key in src.keys()) {
        val nv = src.opt(key)
        val ov = opt(key)
        if (nv is JSONObject && ov is JSONObject) ov.mergeFrom(nv) else put(key, nv ?: JSONObject.NULL)
    }
}

/**
 * A thin mutable wrapper over [JSONObject] giving JS-like ergonomics while
 * preserving every unknown field (lossless round-trip with the panel).
 */
class J(val o: JSONObject) {
    // Subscribe the calling composition (including parent conditions) to JSON editor changes.
    init { com.sonix21.suinode.ui.glass.JsonEditSignal.revision }
    operator fun get(k: String): Any? = if (o.has(k) && !o.isNull(k)) o.opt(k) else null
    operator fun set(k: String, value: Any?) = o.setOrRemove(k, value)
    fun has(k: String) = o.has(k)
    fun remove(k: String) = o.remove(k)

    fun optStringOrNull(k: String): String? = o.optStringOrNull(k)
    fun optLongOr(k: String, def: Long = 0L): Long = o.optLongOr(k, def)
    fun optIntOr(k: String, def: Int = 0): Int = o.optIntOr(k, def)
    fun optBoolOr(k: String, def: Boolean = false): Boolean = o.optBoolOr(k, def)
    fun setOrRemove(k: String, value: Any?) = o.setOrRemove(k, value)

    fun str(k: String, def: String = ""): String = o.optStringOrNull(k) ?: def
    fun int(k: String, def: Int = 0) = o.optIntOr(k, def)
    fun long(k: String, def: Long = 0L) = o.optLongOr(k, def)
    fun bool(k: String, def: Boolean = false) = o.optBoolOr(k, def)

    fun obj(k: String): J? = o.optObj(k)?.let(::J)
    fun ensureObj(k: String): J {
        var x = o.optObj(k)
        if (x == null) { x = JSONObject(); o.put(k, x) }
        return J(x)
    }
    fun arr(k: String): JSONArray? = o.optArr(k)
    fun ensureArr(k: String): JSONArray {
        var x = o.optArr(k)
        if (x == null) { x = JSONArray(); o.put(k, x) }
        return x
    }
    fun strs(k: String): List<String> = o.optArr(k)?.strList() ?: emptyList()

    /** set string; removes key when blank if [blankRemoves] */
    fun setStr(k: String, v: String?, blankRemoves: Boolean = true) {
        o.setOrRemove(k, if (v.isNullOrBlank() && blankRemoves) null else v)
    }
    fun setInt(k: String, v: Int?, positiveOnly: Boolean = true) {
        o.setOrRemove(k, if (v == null || (positiveOnly && v <= 0)) null else v)
    }
    fun setLong(k: String, v: Long?, positiveOnly: Boolean = true) {
        o.setOrRemove(k, if (v == null || (positiveOnly && v <= 0L)) null else v)
    }
    fun setNum(k: String, v: Double?, positiveOnly: Boolean = true) {
        o.setOrRemove(k, if (v == null || (positiveOnly && v <= 0.0)) null else v)
    }
    fun setBool(k: String, v: Boolean?, onlyTrue: Boolean = false) {
        o.setOrRemove(k, when {
            v == null -> null
            onlyTrue && !v -> null
            else -> v
        })
    }
    fun setStrs(k: String, v: List<String>, emptyRemoves: Boolean = true) {
        o.setOrRemove(k, if (v.isEmpty() && emptyRemoves) null else jarr(v))
    }
    /** Duration string like "5s"/"5m"; null/<=0 removes unless fallback provided. */
    fun setDur(k: String, secondsValue: Long?, unit: Char = 's', fallback: String? = null) {
        o.setOrRemove(k, when {
            secondsValue != null && secondsValue > 0 -> "$secondsValue$unit"
            fallback != null -> fallback
            else -> null
        })
    }
    fun durSeconds(k: String, def: Long = 0): Long {
        val s = str(k)
        if (s.isBlank()) return def
        val num = s.trimEnd('s', 'm', 'h', 'd').toDoubleOrNull() ?: return def
        return num.toLong()
    }
}

fun J(obj: JSONObject?): J? = obj?.let(::J)
