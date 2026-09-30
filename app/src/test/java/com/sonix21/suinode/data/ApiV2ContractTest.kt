package com.sonix21.suinode.data

import com.sun.net.httpserver.HttpServer
import com.sonix21.suinode.core.*
import com.sonix21.suinode.ui.screens.inbounds.IN_TYPES
import com.sonix21.suinode.ui.screens.inbounds.createDefaultInbound
import com.sonix21.suinode.ui.screens.outbounds.OUT_TYPES
import com.sonix21.suinode.ui.screens.outbounds.createDefaultOutbound
import com.sonix21.suinode.ui.screens.services.createDefaultService
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList

/** Wire fixtures match apiService.go, model.Client, and InboundService.Get/GetAll. */
class ApiV2ContractTest {
    private lateinit var server: HttpServer
    private lateinit var session: PanelSession
    private data class Request(val method: String, val path: String, val query: String?, val token: String?, val body: String)
    private val requests = CopyOnWriteArrayList<Request>()
    private var reply: (Request) -> String = { envelope(snapshot()) }

    private fun envelope(obj: JSONObject) = jo("success" to true, "msg" to "", "obj" to obj).toString()
    private fun snapshot() = jo(
        "config" to jo("dns" to jo("servers" to JSONArray()), "route" to jo("rules" to JSONArray())),
        "clients" to jarr(listOf(jo("id" to 7, "name" to "alice", "enable" to true, "inbounds" to jarr(listOf(3)), "up" to 1024, "down" to 4096))),
        "inbounds" to jarr(listOf(jo("id" to 3, "type" to "vless", "tag" to "vless-edge", "listen_port" to 443, "users" to jarr(listOf("alice"))))),
        "outbounds" to jarr(listOf(jo("id" to 1, "type" to "direct", "tag" to "direct"))),
        "endpoints" to JSONArray(), "services" to JSONArray(), "tls" to JSONArray(),
        "onlines" to jo("user" to jarr(listOf("alice")), "inbound" to jarr(listOf("vless-edge")), "outbound" to JSONArray()),
        "subURI" to "https://panel.test/sub/", "enableTraffic" to true, "os" to "linux")

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val request = Request(exchange.requestMethod, exchange.requestURI.path, exchange.requestURI.rawQuery,
                exchange.requestHeaders.getFirst("Token"), exchange.requestBody.bufferedReader().readText())
            requests.add(request)
            val bytes = reply(request).toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        session = PanelSession(Panel("test", "Test", "http://127.0.0.1:${server.address.port}/app/", token = "contract-test-token"))
        StatusStore.reset()
    }

    @After fun stop() { session.client.logout(); server.stop(0); StatusStore.reset() }

    @Test fun sessionsUseApiV2ArrayAndEncodedFilters() = runBlocking {
        reply = { jo("success" to true, "obj" to jarr(listOf(
            jo("id" to "older", "createdAt" to 10, "up" to 1, "down" to 2),
            jo("id" to "newer", "createdAt" to 20, "up" to 3, "down" to 4, "domain" to "example.test")
        ))).toString() }
        val rows = session.client.sessions("user", "alice & bob")
        assertEquals(listOf("newer", "older"), rows.map { it.getString("id") })
        assertEquals("/app/apiv2/sessions", requests.single().path)
        assertEquals("GET", requests.single().method)
        assertEquals("resource=user&tag=alice & bob", URLDecoder.decode(requests.single().query, "UTF-8"))
        assertEquals("contract-test-token", requests.single().token)
    }

    @Test fun sessionDisconnectIsExplicitUserFormAndReadOnlyBlocksIt() = runBlocking {
        reply = { jo("success" to true).toString() }
        assertTrue(session.client.closeUserSessions("alice & bob").success)
        assertEquals("/app/apiv2/closeSessions", requests.single().path)
        assertEquals("POST", requests.single().method)
        assertEquals("u=alice & bob", URLDecoder.decode(requests.single().body, "UTF-8"))
        assertTrue(runCatching { session.client.closeUserSessions(" ") }.isFailure)
        val readOnly = SuiClient(session.panel.copy(readOnly = true))
        try { assertTrue(runCatching { readOnly.closeUserSessions("alice") }.isFailure) }
        finally { readOnly.logout() }
        assertEquals(1, requests.size)
    }

    @Test fun unsupportedSessionsAndMalformedSessionDataAreNotShownAsEmpty() = runBlocking {
        reply = { jo("success" to false, "msg" to "unknown action: sessions").toString() }
        assertTrue(runCatching { session.client.sessions("user") }.isFailure)
        reply = { envelope(jo("unexpected" to true)) }
        assertTrue(runCatching { session.client.sessions("endpoint", "wg") }.isFailure)
        reply = { jo("success" to true, "obj" to JSONObject.NULL).toString() }
        assertTrue(session.client.sessions("user").isEmpty())
    }

    @Test fun certificateProbeUsesDomainAndPortFormOnApiV2() = runBlocking {
        reply = { envelope(jo("leafHash" to "fixture-hash")) }
        val result = session.client.postForm("getCertPing", mapOf("domain" to "example.test", "port" to "443"))
        assertEquals("fixture-hash", result.objObj()?.getString("leafHash"))
        assertEquals("/app/apiv2/getCertPing", requests.single().path)
        assertEquals("domain=example.test&port=443", requests.single().body)
        assertEquals("POST", requests.single().method)
    }

    @Test fun settingsRoundTripIgnores161BookkeepingAndPreservesSubscriptionStrings() = runBlocking {
        val stored = jo("webPort" to "2095", "subURI" to "https://vpn.example/sub/",
            "subJsonExt" to "{\"custom\":{\"keep\":true}}", "subClashExt" to "tun:\n  enable: true\n",
            "subEncode" to "false", "migratedSingBox114" to "true", "migratedEndpointTls" to "true",
            "someLaterBookkeepingRow" to "1", "maintenance" to "false", "secret" to "private")
        reply = { request ->
            if (request.method == "GET") envelope(stored)
            else {
                val form = request.body.split('&').associate { entry ->
                    val pair = entry.split('=', limit = 2)
                    URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
                }
                val sent = JSONObject(requireNotNull(form["data"]))
                // Reproduce the 1.6.1 server's refusal of migration bookkeeping.
                if (sent.has("migratedSingBox114") || sent.has("someLaterBookkeepingRow"))
                    jo("success" to false, "msg" to "unknown setting: migratedSingBox114").toString()
                else envelope(jo("settings" to stored))
            }
        }
        val fetched = requireNotNull(session.client.get("settings").objObj())
        assertEquals(setOf("webPort", "subURI", "subJsonExt", "subClashExt", "subEncode"), fetched.keys().asSequence().toSet())
        // Also protect a raw caller or old cached draft that did not use the filtered GET.
        val response = session.client.postForm("save", mapOf("object" to "settings", "action" to "set", "data" to stored.toString()))
        assertTrue(response.success)
        val returned = requireNotNull(response.objObj()).getJSONObject("settings")
        assertEquals(fetched.toString(), returned.toString())
        assertEquals(stored.getString("subJsonExt"), returned.getString("subJsonExt"))
        assertEquals(stored.getString("subClashExt"), returned.getString("subClashExt"))
        assertEquals("false", returned.getString("subEncode"))
        assertTrue(stored.has("migratedSingBox114"))
        assertTrue(requests.all { it.path.startsWith("/app/apiv2/") })
        assertEquals(1, requests.count { it.method == "POST" })
    }

    @Test fun maintenanceUsesApiV2FormAndLoadsIntentionalStopState() = runBlocking {
        reply = { envelope(snapshot().put("maintenance", true)) }
        session.load().getOrThrow()
        assertTrue(session.data.value.maintenance)
        requests.clear()
        assertTrue(session.client.postForm("maintenance", mapOf("enable" to "false")).success)
        assertEquals("POST", requests.single().method)
        assertEquals("/app/apiv2/maintenance", requests.single().path)
        assertEquals("enable=false", requests.single().body)
        assertEquals("contract-test-token", requests.single().token)
    }

    @Test fun monitoringFetchesFullRecordsInBoundedGetOnlyBatches() = runBlocking {
        reply = { r ->
            val ids = r.query?.substringAfter("id=")?.let { URLDecoder.decode(it, "UTF-8").split(',').map(String::toLong) }
            envelope(jo("clients" to jarr((ids ?: (1L..205L).toList()).map { id ->
                jo("id" to id, "name" to "fixture-$id").apply {
                    if (ids != null) put("delayStart", true).put("resetDays", 30)
                }
            })))
        }
        val clients = session.client.fullClients()
        assertEquals(205, clients.length())
        assertTrue((0 until clients.length()).all { clients.getJSONObject(it).getBoolean("delayStart") })
        assertEquals(4, requests.size)
        assertTrue(requests.all { it.method == "GET" && it.path.endsWith("/apiv2/clients") })
    }

    @Test fun incompleteFullClientBatchFailsInsteadOfPretendingItIsComplete() = runBlocking {
        reply = { r -> envelope(jo("clients" to if (r.query == null) jarr(listOf(jo("id" to 1))) else JSONArray())) }
        assertTrue(runCatching { session.client.fullClients() }.isFailure)
    }

    @Test fun nullClientCollectionMeansEmptyPanel() = runBlocking {
        reply = { envelope(jo("clients" to JSONObject.NULL)) }
        assertEquals(0, session.client.fullClients().length())
        assertEquals(1, requests.size)
    }

    @Test fun fullLoadPopulatesCollectionsAndUsesTokenOnly() = runBlocking {
        assertTrue(session.load().isSuccess)
        assertTrue(session.data.value.loaded)
        assertEquals("alice", session.data.value.clients.single().getString("name"))
        assertEquals("vless-edge", session.data.value.inbounds.single().getString("tag"))
        assertEquals(listOf("alice"), session.data.value.onlines.user)
        assertEquals("/app/apiv2/load", requests.single().path)
        assertEquals("contract-test-token", requests.single().token)
        assertNull(requests.single().query)
    }

    @Test fun nullCollectionsClearPreviousDataAndNoLocalClockCursorIsSent() = runBlocking {
        session.load().getOrThrow()
        reply = { envelope(snapshot().put("clients", JSONObject.NULL).put("inbounds", JSONObject.NULL)) }
        session.load().getOrThrow()
        assertTrue(session.data.value.clients.isEmpty())
        assertTrue(session.data.value.inbounds.isEmpty())
        assertTrue(requests.all { it.query == null })
    }

    @Test fun invalidTokenIsAnErrorEvenWithHttp200AndKeepsLastGoodData() = runBlocking {
        session.load().getOrThrow()
        reply = { """{"success":false,"msg":"invalid token","obj":null}""" }
        assertTrue(session.load().isFailure)
        assertEquals("alice", session.data.value.clients.single().getString("name"))
        assertEquals(ApiTokenError.MESSAGE, session.syncError.value)
    }

    @Test fun missingCollectionsAreFetchedBeforeMarkingLoaded() = runBlocking {
        reply = { r ->
            if (r.path.endsWith("/load")) envelope(jo("onlines" to JSONObject()))
            else {
                val key = r.path.substringAfterLast('/')
                envelope(jo(key to snapshot().get(key)))
            }
        }
        assertTrue(session.load().isSuccess)
        assertTrue(requests.any { it.path.endsWith("/clients") })
        assertTrue(requests.any { it.path.endsWith("/inbounds") })
        assertEquals(1, session.data.value.clients.size)
    }

    @Test fun saveRequiresInitialGetAndSerializesStringPayloadAsJson() = runBlocking {
        assertTrue(session.save("outbounds", "del", "edge \"one\"").isFailure)
        assertTrue(requests.isEmpty())
        session.load().getOrThrow()
        reply = { envelope(jo("outbounds" to JSONArray())) }
        session.save("outbounds", "del", "edge \"one\"").getOrThrow()
        val sent = requests.last()
        val fields = sent.body.split("&").associate {
            val parts = it.split("=", limit = 2)
            URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
        }
        assertEquals("POST", sent.method)
        assertEquals("/app/apiv2/save", sent.path)
        assertEquals(JSONObject.quote("edge \"one\""), fields["data"])
        assertEquals("del", fields["action"])
        assertTrue(session.data.value.outbounds.isEmpty())
    }

    @Test fun detailGetRetainsFieldsMissingFromClientSummary() = runBlocking {
        session.load().getOrThrow()
        val full = snapshot().getJSONArray("clients").getJSONObject(0)
            .put("config", jo("vless" to jo("uuid" to "keep-this-uuid")))
            .put("links", jarr(listOf(jo("type" to "external", "uri" to "vless://keep"))))
            .put("autoReset", true).put("resetDays", 30).put("futureField", "preserve")
        reply = { envelope(jo("clients" to jarr(listOf(full)))) }
        val fetched = session.fetchRecord("clients", 7)
        assertEquals("id=7", requests.last().query)
        assertEquals("keep-this-uuid", fetched.getJSONObject("config").getJSONObject("vless").getString("uuid"))
        assertTrue(fetched.getBoolean("autoReset"))
        assertEquals("preserve", fetched.getString("futureField"))
    }

    @Test fun collectionEditorsReadWrappedArraysAndSelectTheRequestedId() = runBlocking {
        for (collection in listOf("tls", "outbounds", "endpoints", "services")) {
            reply = { envelope(jo(collection to jarr(listOf(
                jo("id" to 1, "name" to "other"),
                jo("id" to 9, "name" to "selected", "future" to jo("keep" to true))
            )))) }
            val record = session.fetchRecord(collection, 9)
            assertEquals("selected", record.getString("name"))
            assertTrue(record.getJSONObject("future").getBoolean("keep"))
            assertEquals("/app/apiv2/$collection", requests.last().path)
            assertNull(requests.last().query)
        }
    }

    @Test fun missingOrMalformedEditorRecordsFailInsteadOfReturningBlank() = runBlocking {
        reply = { envelope(jo("tls" to JSONArray())) }
        assertTrue(runCatching { session.fetchRecord("tls", 9) }.isFailure)
        reply = { """{"success":false,"msg":"not authorized","obj":{"tls":[{"id":9}]}}""" }
        assertEquals("not authorized", runCatching { session.fetchRecord("tls", 9) }.exceptionOrNull()?.message)
    }

    @Test fun webSessionEndpointsAreRejectedBeforeAnyNetworkRequest() = runBlocking {
        assertTrue(runCatching { session.client.postForm("changePass", emptyMap()) }.isFailure)
        assertTrue(runCatching { session.client.get("tokens") }.isFailure)
        assertTrue(runCatching { session.client.get("legacy:load") }.isFailure)
        assertTrue(requests.isEmpty())
    }

    @Test fun databaseDownloadRejectsJsonErrorEnvelope() = runBlocking {
        reply = { """{"success":false,"msg":"invalid token","obj":null}""" }
        assertEquals(ApiTokenError.MESSAGE, runCatching { session.client.download("getdb") }.exceptionOrNull()?.message)
    }

    @Test fun databaseStreamValidatesBeforeOpeningDestination() = runBlocking {
        reply = { """{"success":false,"msg":"invalid token","obj":null}""" }
        var opened = false
        val result = runCatching { session.client.downloadDatabase { opened = true; java.io.ByteArrayOutputStream() } }
        assertTrue(result.isFailure)
        assertFalse(opened)
    }

    @Test fun databaseStreamsAllBytesToDestination() = runBlocking {
        val payload = "SQLite format 3\u0000" + "x".repeat(100000)
        reply = { payload }
        val output = java.io.ByteArrayOutputStream()
        val count = session.client.downloadDatabase { output }
        assertEquals(payload.toByteArray().size.toLong(), count)
        assertArrayEquals(payload.toByteArray(), output.toByteArray())
        assertEquals("/app/apiv2/getdb", requests.single().path)
    }

    @Test fun statusRetainsSystemInfoAndRequestsDatabaseCounts() = runBlocking {
        var first = true
        reply = {
            val obj = jo("cpu" to 23.5, "mem" to jo("current" to 1024, "total" to 4096), "db" to jo("clients" to 1))
            if (first) { obj.put("sys", jo("hostName" to "test-server")); first = false }
            envelope(obj)
        }
        assertTrue(StatusStore.poll(session))
        assertTrue(StatusStore.poll(session))
        assertEquals("test-server", StatusStore.status.value!!.getJSONObject("sys").getString("hostName"))
        assertEquals(23.5f, StatusStore.history.value["h-cpu"]!!.last(), 0.01f)
        assertTrue(URLDecoder.decode(requests.last().query, "UTF-8").contains("db"))
    }

    @Test fun rejectedSaveDoesNotReplaceGoodCollections() = runBlocking {
        session.load().getOrThrow()
        reply = { """{"success":false,"msg":"invalid inbound","obj":{"inbounds":null}}""" }
        assertTrue(session.save("inbounds", "edit", jo("id" to 3)).isFailure)
        assertEquals("vless-edge", session.data.value.inbounds.single().getString("tag"))
        assertEquals("invalid inbound", session.lastError)
    }

    @Test fun malformedEnvelopeAndConfigProduceActionableErrors() = runBlocking {
        reply = { """{"status":"ok"}""" }
        assertFalse(session.client.get("load").success)
        assertTrue(session.client.get("load").msg.isNotBlank())
        reply = { envelope(snapshot().put("config", "not-a-config")) }
        assertTrue(session.load().isFailure)
        assertFalse(session.data.value.loaded)
    }

    @Test fun inboundGetEditPostKeepsUnknownFieldsAndNestedTransport() = runBlocking {
        session.load().getOrThrow()
        val full = jo("id" to 3, "type" to "vless", "tag" to "edge", "future_option" to "keep",
            "transport" to jo("type" to "ws", "path" to "/old", "headers" to jo("X-Custom" to "keep")))
        reply = { envelope(jo("inbounds" to jarr(listOf(full)))) }
        val fetched = session.fetchRecord("inbounds", 3)
        assertEquals("id=3", requests.last().query)
        fetched.getJSONObject("transport").put("path", "/new")
        session.save("inbounds", "edit", fetched).getOrThrow()
        val encoded = requests.last().body.split('&').first { it.startsWith("data=") }.substringAfter('=')
        val sent = JSONObject(URLDecoder.decode(encoded, "UTF-8"))
        assertEquals("keep", sent.getString("future_option"))
        assertEquals("/new", sent.getJSONObject("transport").getString("path"))
        assertEquals("keep", sent.getJSONObject("transport").getJSONObject("headers").getString("X-Custom"))
    }

    @Test fun everyProtocolDefaultContainsItsDiscriminator() {
        IN_TYPES.forEach { assertEquals(it, createDefaultInbound(it).getString("type")) }
        OUT_TYPES.forEach { assertEquals(it, createDefaultOutbound(it).getString("type")) }
        listOf("derp", "resolved", "ssm-api", "ocm", "ccm").forEach { assertEquals(it, createDefaultService(it).getString("type")) }
        assertEquals("۱۲۳", Digits.toFa("۱۲۳"))
        assertEquals("۱۲۳", Digits.toFa("123"))
    }
}
