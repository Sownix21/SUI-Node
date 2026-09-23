package com.sonix21.suinode.data

import com.sun.net.httpserver.HttpsServer
import com.sun.net.httpserver.HttpsConfigurator
import kotlinx.coroutines.runBlocking
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

class CertificateGateTest {
    @Test fun rejectedCertificateSendsNoAuthenticatedHttpRequest() = runBlocking {
        exercise(false)
    }

    @Test fun reviewedCertificateAllowsNormalApiV2Request() = runBlocking {
        exercise(true)
    }

    private suspend fun exercise(trusted: Boolean) {
        val held = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            setKeyEntry("test", held.keyPair.private, "test-only".toCharArray(), arrayOf(held.certificate))
        }
        val km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, "test-only".toCharArray()) }
        val ssl = SSLContext.getInstance("TLS").apply { init(km.keyManagers, null, null) }
        val requests = AtomicInteger()
        val server = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            httpsConfigurator = HttpsConfigurator(ssl)
            createContext("/apiv2/load") { exchange ->
                requests.incrementAndGet()
                val bytes = """{"success":true,"obj":{"clients":[],"inbounds":[]}}""".toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        val previous = ConnectionSecurity.verifyIdentity
        val client = SuiClient(Panel("test", "Test", "https://127.0.0.1:${server.address.port}/", token = "fake-unit-token", allowInsecure = true))
        var inspected = false
        ConnectionSecurity.verifyIdentity = { _, fingerprint ->
            inspected = true
            assertEquals(com.sonix21.suinode.core.CertificateIdentity.fingerprint(held.certificate.encoded), fingerprint)
            trusted
        }
        try {
            val result = runCatching { client.get("load") }
            assertTrue("Identity gate was not reached: ${result.exceptionOrNull()}", inspected)
            if (trusted) {
                assertTrue(result.getOrThrow().success)
                assertEquals(1, requests.get())
            } else {
                assertTrue(result.exceptionOrNull() is com.sonix21.suinode.core.PanelIdentityChanged)
                assertEquals(0, requests.get())
            }
        } finally {
            client.logout()
            ConnectionSecurity.verifyIdentity = previous
            server.stop(0)
        }
    }
}
