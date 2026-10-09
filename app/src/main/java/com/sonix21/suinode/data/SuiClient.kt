package com.sonix21.suinode.data

import android.annotation.SuppressLint
import com.sonix21.suinode.core.Panel161
import kotlinx.coroutines.ensureActive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** APIv2 response. obj is endpoint-specific and may be null. */
data class Envelope(val success: Boolean, val msg: String, val obj: Any?) {
    fun objObj(): JSONObject? = obj as? JSONObject
    fun objArr(): JSONArray? = obj as? JSONArray
    fun objStrList(): List<String> =
        (obj as? JSONArray)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
    companion object {
        fun fail(msg: String) = Envelope(false, msg, null)
    }
}

data class CheckResult(val ok: Boolean?, val delay: Long?, val error: String?) {
    val success: Boolean get() = ok == true
}

/** Token-only APIv2 transport. Body reads must also happen off the UI thread. */
class SuiClient(private val panel: Panel) {
    private val base = Urls.apiBase(panel.url)
    private val clients = HashMap<Boolean, OkHttpClient>()

    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private fun client(longTimeout: Boolean): OkHttpClient {
        synchronized(clients) { clients[longTimeout]?.let { return it } }
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .callTimeout(if (longTimeout) 300 else 45, TimeUnit.SECONDS)
            .readTimeout(if (longTimeout) 240 else 30, TimeUnit.SECONDS)
            .writeTimeout(if (longTimeout) 240 else 60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .addNetworkInterceptor { chain ->
                if (chain.request().url.isHttps) {
                    // Read the actual negotiated leaf. OkHttp's cleaned chain can be empty
                    // with an explicitly insecure profile's custom trust manager.
                    val certificate = (chain.connection()?.socket() as? javax.net.ssl.SSLSocket)
                        ?.session?.peerCertificates?.firstOrNull()
                        ?: throw javax.net.ssl.SSLPeerUnverifiedException("No verified TLS peer")
                    val fingerprint = com.sonix21.suinode.core.CertificateIdentity.fingerprint(certificate.encoded)
                    if (ConnectionSecurity.verifyIdentity?.invoke(panel, fingerprint) == false)
                        throw com.sonix21.suinode.core.PanelIdentityChanged()
                }
                // Connection/TLS establishment precedes network interceptors. No HTTP headers
                // (including Token) are sent until the identity check above permits proceed.
                chain.proceed(chain.request())
            }
        if (panel.allowInsecure) {
            runCatching {
                val tm = object : X509TrustManager {
                    override fun checkClientTrusted(c: Array<X509Certificate>, a: String) {}
                    override fun checkServerTrusted(c: Array<X509Certificate>, a: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
                val ssl = SSLContext.getInstance("TLS")
                ssl.init(null, arrayOf<TrustManager>(tm), SecureRandom())
                builder.sslSocketFactory(ssl.socketFactory, tm)
                    .hostnameVerifier { _, _ -> true }
            }
        }
        val c = builder.build()
        synchronized(clients) { clients[longTimeout] = c }
        return c
    }

    // ------------------------------------------------------------------ core

    private fun url(action: String): String {
        require(action in GET_ACTIONS || action in POST_ACTIONS) { "Unsupported APIv2 action: $action" }
        require(panel.token.isNotBlank()) { "API token required. Edit this panel profile to add a token." }
        return base + action
    }

    private suspend fun execute(bld: Request.Builder, longTimeout: Boolean = false): Response =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try { client(longTimeout).newCall(bld.build()).execute() }
            catch (e: com.sonix21.suinode.core.PanelIdentityChanged) { throw e }
            catch (e: IOException) { throw IOException(com.sonix21.suinode.core.ConnectionDiagnostics.message(e)) }
        }

    private fun parseEnvelope(body: String?): Envelope? {
        if (body.isNullOrBlank()) return null
        return try {
            val o = JSONObject(body)
            if (o.opt("success") !is Boolean) return null
            val message = o.optString("msg").let { if (panel.token.isNotBlank()) it.replace(panel.token, "[redacted]") else it }
            Envelope(o.optBoolean("success", false), com.sonix21.suinode.core.ApiTokenError.explain(message), o.opt("obj"))
        } catch (_: Exception) { null }
    }

    private suspend fun callApi(
        buildRequest: () -> Request.Builder,
        longTimeout: Boolean,
    ): Envelope = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val request = buildRequest().header(TOKEN_HEADER, panel.token).header("Accept", "application/json")
        execute(request, longTimeout).use { resp ->
            val body = resp.body?.string()
            val envelope = parseEnvelope(body)
            if (!resp.isSuccessful) Envelope.fail(envelope?.msg?.takeIf { it.isNotBlank() }
                ?: "HTTP ${resp.code}: check the panel URL, web path and API token")
            else envelope ?: Envelope.fail("Expected APIv2 JSON. Check the panel URL and web path.")
        }
    }

    suspend fun get(action: String, params: Map<String, String?> = emptyMap()): Envelope {
        check(!panel.readOnly || action != "checkOutbound") { "Read-only safety mode blocks active latency probes for this panel" }
        require(action in GET_ACTIONS) { "Unsupported APIv2 GET: $action" }
        val ub = url(action).toHttpUrlOrNull()?.newBuilder() ?: throw IOException("bad url")
        for ((k, v) in params) if (!v.isNullOrEmpty()) ub.addQueryParameter(k, v)
        val httpUrl = ub.build()
        val response = callApi({ Request.Builder().url(httpUrl).get() }, longTimeout = false)
        return if (action == "settings" && response.success && response.objObj() != null)
            response.copy(obj = Panel161.settingsPayload(requireNotNull(response.objObj()))) else response
    }

    suspend fun postForm(action: String, fields: Map<String, String?>): Envelope {
        check(!panel.readOnly) { "Read-only safety mode is enabled. Disable it in this panel's local profile before making changes." }
        require(action in POST_ACTIONS) { "Unsupported APIv2 POST: $action" }
        val settingsSave = action == "save" && fields["object"] == "settings"
        val requestFields = if (settingsSave) fields + ("data" to Panel161.settingsPayload(
            JSONObject(requireNotNull(fields["data"]) { "Settings data is required" })).toString()) else fields
        val form: RequestBody = FormBody.Builder().apply {
            for ((k, v) in requestFields) if (v != null) add(k, v)
        }.build()
        val httpUrl = url(action).toHttpUrlOrNull() ?: throw IOException("bad url")
        val send: suspend () -> Envelope = {
            val response = callApi({ Request.Builder().url(httpUrl).post(form) }, longTimeout = false)
            val settings = response.objObj()?.optJSONObject("settings")
            if (settingsSave && response.success && settings != null) response.copy(obj = JSONObject(response.objObj().toString())
                .put("settings", Panel161.settingsPayload(settings))) else response
        }
        if (action == "save") {
            val response = SaveGuard.save(this,panel,requestFields,send)
            if (response.success && fields["object"] == "clients")
                com.sonix21.suinode.core.ClientAlertFreshness.changed(panel.id)
            return response
        }
        if (action !in setOf("linkConvert","subConvert","getCertPing")) SaveGuard.requireNoPending(panel)
        val response = send()
        if (response.success && action == "resetTraffic")
            com.sonix21.suinode.core.ClientAlertFreshness.changed(panel.id)
        return response
    }

    /** GetAll omits delayStart/reset/config fields. Fetch bounded full-record batches. */
    suspend fun fullClients(): JSONArray {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
        fun records(response: Envelope): List<JSONObject> {
            if (!response.success) {
                if (com.sonix21.suinode.core.ApiTokenError.matches(response.msg)) throw com.sonix21.suinode.core.RejectedApiToken()
                throw IOException("Unable to read APIv2 clients")
            }
            val body = response.objObj() ?: throw IOException("Invalid APIv2 clients response")
            if (!body.has("clients")) throw IOException("Missing APIv2 clients collection")
            val raw = body.opt("clients")
            if (raw == JSONObject.NULL) return emptyList()
            val array = raw as? JSONArray ?: throw IOException("Invalid APIv2 clients collection")
            return (0 until array.length()).map { array.optJSONObject(it) ?: throw IOException("Invalid client record") }
        }
        val ids = records(get("clients")).map {
            it.optLong("id", -1).also { id -> if (id <= 0) throw IOException("Invalid client identifier") }
        }.distinct()
        val result = JSONArray()
        for (batch in ids.chunked(100)) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (System.nanoTime() >= deadline) throw java.net.SocketTimeoutException("Full client retrieval timed out")
            val full = records(get("clients", mapOf("id" to batch.joinToString(","))))
            if (full.map { it.optLong("id") }.toSet() != batch.toSet())
                throw IOException("Client list changed during retrieval. Refresh and retry.")
            full.forEach(result::put)
        }
        return result
    }

    suspend fun postEmpty(action: String): Envelope = postForm(action, emptyMap())

    suspend fun sessions(resource: String, tag: String? = null): List<JSONObject> {
        require(resource in setOf("user", "inbound", "outbound", "endpoint")) { "Invalid session resource" }
        val response = get("sessions", mapOf("resource" to resource, "tag" to tag))
        check(response.success) { response.msg }
        return com.sonix21.suinode.core.LiveSessions.parse(response.obj)
    }

    suspend fun closeUserSessions(user: String): Envelope {
        require(user.isNotBlank()) { "Select a client before closing sessions" }
        return postForm("closeSessions", mapOf("u" to user))
    }

    suspend fun uploadDb(file: File): Envelope {
        check(!panel.readOnly) { "Read-only safety mode blocks database restore" }
        SaveGuard.requireNoPending(panel)
        val media = "application/octet-stream".toMediaType()
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("db", file.name, file.asRequestBody(media))
            .build()
        val httpUrl = url("importdb").toHttpUrlOrNull() ?: throw IOException("bad url")
        return callApi({ Request.Builder().url(httpUrl).post(body) }, longTimeout = true)
    }

    /** Opens the destination only after checking the response; never buffers the whole database. */
    suspend fun downloadDatabase(
        params: Map<String, String?> = emptyMap(),
        destination: () -> java.io.OutputStream,
    ): Long = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val ub = url("getdb").toHttpUrlOrNull()?.newBuilder() ?: throw IOException("Invalid panel URL")
        for ((k, v) in params) if (!v.isNullOrEmpty()) ub.addQueryParameter(k, v)
        execute(Request.Builder().url(ub.build()).header(TOKEN_HEADER, panel.token).get(), true).use { resp ->
            if (!resp.isSuccessful) throw IOException("Download failed (HTTP ${resp.code})")
            val body = resp.body ?: throw IOException("Empty download")
            val signature = "SQLite format 3\u0000".toByteArray()
            val source = body.source()
            if (!source.request(signature.size.toLong()) ||
                !source.peek().readByteArray(signature.size.toLong()).contentEquals(signature)) {
                val sample = source.readUtf8(minOf(source.buffer.size, 4096L))
                throw IOException(parseEnvelope(sample)?.msg?.takeIf { it.isNotBlank() }
                    ?: "The panel did not return a SQLite database")
            }
            destination().use { output ->
                val buffer = ByteArray(32 * 1024)
                var total = 0L
                while (true) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val count = source.read(buffer)
                    if (count == -1) break
                    output.write(buffer, 0, count)
                    total += count
                }
                total
            }
        }
    }

    suspend fun download(action: String, params: Map<String, String?> = emptyMap()): ByteArray {
        require(action == "getdb") { "APIv2 supports database downloads only" }
        val output = java.io.ByteArrayOutputStream()
        downloadDatabase(params) { output }
        return output.toByteArray()
    }

    suspend fun testConnection(): Envelope {
        val env = get("load")
        if (!env.success) return env
        val obj = env.objObj()
        return if (obj != null && obj.has("clients") && obj.has("inbounds")) env
            else Envelope.fail("APIv2 load did not return clients and inbounds")
    }

    fun logout() {
        val retired = synchronized(clients) { clients.values.toList().also { clients.clear() } }
        retired.forEach { http ->
            http.dispatcher.cancelAll()
            // TLS close_notify can write to the socket. Never evict on the Android UI
            // thread: an exception here would also mask a successful GET in a finally block.
            http.dispatcher.executorService.execute { http.connectionPool.evictAll() }
        }
    }

    companion object {
        const val TOKEN_HEADER = "Token"
        val GET_ACTIONS = setOf("load", "inbounds", "outbounds", "endpoints", "services", "tls", "clients",
            "config", "users", "settings", "stats", "status", "onlines", "sessions", "logs", "changes", "keypairs", "getdb", "checkOutbound")
        val POST_ACTIONS = setOf("save", "restartApp", "restartSb", "maintenance", "resetTraffic", "linkConvert", "subConvert", "importdb", "getCertPing", "closeSessions")
    }
}
