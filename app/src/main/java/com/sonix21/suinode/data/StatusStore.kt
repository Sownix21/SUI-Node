package com.sonix21.suinode.data

import com.sonix21.suinode.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

/** Holds polled server status + computed network speeds + rolling chart history. */
object StatusStore {
    val status = MutableStateFlow<JSONObject?>(null)
    val downSpeed = MutableStateFlow(0L)
    val upSpeed = MutableStateFlow(0L)
    val history = MutableStateFlow<Map<String, List<Float>>>(emptyMap())

    private const val WINDOW = 24

    private var lastRecv = -1L
    private var lastSent = -1L
    private var lastPrecv = -1L
    private var lastPsent = -1L
    private var lastTs = 0L
    private var gotSys = false
    private var lastRead = -1L
    private var lastWrite = -1L
    private var lastDiskTs = 0L
    val error = MutableStateFlow<String?>(null)

    fun reset() {
        status.value = null
        error.value = null
        lastRead = -1; lastWrite = -1; lastDiskTs = 0
        lastRecv = -1; lastSent = -1; lastPrecv = -1; lastPsent = -1; lastTs = 0
        downSpeed.value = 0; upSpeed.value = 0
        history.value = emptyMap()
        gotSys = false
    }

    private fun push(key: String, v: Float) {
        val cur = history.value.toMutableMap()
        val list = cur[key]?.toMutableList() ?: mutableListOf()
        list.add(v)
        while (list.size > WINDOW) list.removeAt(0)
        cur[key] = list
        history.value = cur
    }

    /** Poll once; returns false on failure. */
    suspend fun poll(session: PanelSession): Boolean {
        return try {
            val resources = StringBuilder("cpu,mem,dsk,swp,net,dio,sbd,db")
            if (!gotSys) resources.append(",sys")
            val env = session.client.get("status", mapOf("r" to resources.toString()))
            if (!env.success) { error.value = env.msg; return false }
            val obj = env.objObj() ?: run { error.value = "The panel returned an invalid status response"; return false }
            if (obj.has("sys")) gotSys = true

            if (obj.has("cpu")) {
                val cpu = when (val c = obj.opt("cpu")) {
                    is Number -> c.toDouble().toFloat()
                    else -> 0f
                }
                push("h-cpu", cpu)
            }
            obj.optObj("mem")?.let {
                val total = it.optLongOr("total"); val used = it.optLongOr("current")
                if (total > 0) push("h-mem", used * 100f / total)
            }
            obj.optObj("net")?.let { net ->
                val recv = net.optLongOr("recv"); val sent = net.optLongOr("sent")
                val precv = net.optLongOr("precv"); val psent = net.optLongOr("psent")
                val now = System.nanoTime() / 1_000_000
                if (lastRecv >= 0 && now > lastTs) {
                    val dt = ((now - lastTs) / 1000.0).coerceAtLeast(0.001)
                    val dDown = ((recv - lastRecv).coerceAtLeast(0) / dt).toLong()
                    val dUp = ((sent - lastSent).coerceAtLeast(0) / dt).toLong()
                    downSpeed.value = dDown; upSpeed.value = dUp
                    push("h-net-down", dDown.toFloat() / 1024f)
                    push("h-net-up", dUp.toFloat() / 1024f)
                    push("hp-down", ((precv - lastPrecv).coerceAtLeast(0) / dt).toFloat())
                    push("hp-up", ((psent - lastPsent).coerceAtLeast(0) / dt).toFloat())
                }
                lastRecv = recv; lastSent = sent; lastPrecv = precv; lastPsent = psent; lastTs = now
            }
            obj.optObj("dio")?.let {
                val read = it.optLongOr("read"); val write = it.optLongOr("write")
                val now = System.nanoTime() / 1_000_000
                if (lastRead >= 0 && now > lastDiskTs) {
                    val dt = (now - lastDiskTs) / 1000.0
                    push("h-dio-read", ((read - lastRead).coerceAtLeast(0) / dt / 1048576).toFloat())
                    push("h-dio-write", ((write - lastWrite).coerceAtLeast(0) / dt / 1048576).toFloat())
                }
                lastRead = read; lastWrite = write; lastDiskTs = now
            }

            val merged = status.value?.deepCopy() ?: JSONObject()
            obj.keys().forEach { merged.put(it, obj.get(it)) }
            status.value = merged
            error.value = null
            true
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { error.value = e.message ?: "Status unavailable"; false }
    }
}
