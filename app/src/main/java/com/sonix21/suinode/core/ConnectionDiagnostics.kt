package com.sonix21.suinode.core

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLPeerUnverifiedException

class PanelIdentityChanged : SSLPeerUnverifiedException("Panel certificate changed or needs review. Open connection diagnostics before trusting it.")

object ConnectionDiagnostics {
    fun message(error: Exception): String = when (error) {
        is PanelIdentityChanged -> error.message!!
        is UnknownHostException -> "DNS lookup failed. Check the panel hostname and your device's DNS/network connection."
        is SocketTimeoutException -> "Connection timed out. Check panel availability, firewall rules and your connection."
        is ConnectException -> "Connection refused or unreachable. Check the host, port, firewall and panel service."
        is SSLException -> "TLS verification failed. Check certificate validity, hostname and your device clock. Do not disable verification to bypass an unexpected error."
        else -> "Network request failed. Check the connection and panel availability, then retry."
    }
}

object CertificateIdentity {
    fun origin(url: String): String {
        val uri = java.net.URI(url)
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank())
        return "https://${uri.host.lowercase()}:${if (uri.port < 0) 443 else uri.port}"
    }
    fun fingerprint(encoded: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(encoded).joinToString(":") { "%02X".format(it) }
}
