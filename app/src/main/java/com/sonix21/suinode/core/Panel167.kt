package com.sonix21.suinode.core

import org.json.JSONObject

object Panel167 {
    fun realm(o: JSONObject, enabled: Boolean, outbound: Boolean) {
        if (!enabled) { o.remove("realm"); return }
        if (!o.has("realm")) o.put("realm", jo("server_url" to "", "realm_id" to "", "stun_servers" to jarr(emptyList<String>())))
        if (outbound) listOf("server", "server_port", "server_ports", "hop_interval").forEach(o::remove)
    }
    fun realmIp(o: JSONObject, version: Int) {
        require(version in listOf(0, 4, 6))
        if (version == 0) o.remove("ip_version") else o.put("ip_version", version)
        if (version == 6) o.remove("port_mapping")
    }
    fun clientCertificate(tls: JSONObject, pem: List<String>) {
        val cert = Panel161.pemBlock(pem, "CERTIFICATE")
        val key = Panel161.pemBlock(pem, "PRIVATE KEY")
        listOf("client_certificate_path", "client_key_path", "client_key_password").forEach(tls::remove)
        tls.put("client_certificate", jarr(cert)).put("client_key", jarr(key))
    }
    val cbcCiphers = listOf("AES-256-CBC", "AES-192-CBC", "AES-128-CBC", "CAMELLIA-256-CBC", "CAMELLIA-192-CBC", "CAMELLIA-128-CBC", "ARIA-256-CBC", "ARIA-192-CBC", "ARIA-128-CBC", "SEED-CBC", "SM4-CBC", "CAST5-CBC", "BF-CBC", "DES-EDE3-CBC", "DES-EDE-CBC", "DES-CBC")
    val staticCiphers = cbcCiphers + "NONE"
    val tlsCiphers = listOf("AES-256-GCM", "AES-192-GCM", "AES-128-GCM", "CHACHA20-POLY1305") + cbcCiphers + listOf("AES-256-CFB", "AES-192-CFB", "AES-128-CFB", "AES-256-OFB", "AES-192-OFB", "AES-128-OFB")
    val digests = listOf("SHA256", "SHA384", "SHA512", "SHA224", "SHA1", "RIPEMD160", "MD5", "NONE")
}
