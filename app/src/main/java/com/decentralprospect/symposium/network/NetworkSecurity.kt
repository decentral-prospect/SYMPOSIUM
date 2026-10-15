package com.decentralprospect.symposium

import okhttp3.OkHttpClient
import okio.ByteString.Companion.toByteString
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

internal fun CallRuntime.parseHostPort(input: String): HostPort {
    val endpoint = requireNotNull(parseRelayEndpoint(input)) { "Invalid relay endpoint" }
    return HostPort(endpoint.host, endpoint.port)
}

internal fun CallRuntime.normalizeTlsPin(pin: String): String = normalizeRelayTlsPin(pin)

internal class SpkiPinTrustManager(expectedPinRaw: String) : X509TrustManager {
    private val expectedPin = normalizeRelayTlsPin(expectedPinRaw)

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("Client certificates are not accepted")
    }
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty()) {
            throw CertificateException("Empty server certificate chain")
        }

        val leaf = chain[0]
        leaf.checkValidity()
        val leafPin = spkiPin(leaf)
        if (secureEquals(leafPin, expectedPin)) {
            return
        }

        throw CertificateException(
            "Leaf SPKI pin mismatch. Expected: $expectedPin; leaf: $leafPin"
        )
    }

    private fun spkiPin(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.publicKey.encoded)
        val b64 = digest.toByteString().base64()
        return "sha256/$b64"
    }

    private fun secureEquals(a: String, b: String): Boolean {
        return MessageDigest.isEqual(
            a.toByteArray(Charsets.UTF_8),
            b.toByteArray(Charsets.UTF_8)
        )
    }
}

internal fun CallRuntime.pinnedOkHttpClient(host: String, tlsPin: String): OkHttpClient {
    val normalizedPin = normalizeTlsPin(tlsPin)
    val key = "$host|$normalizedPin"

    return pinnedClients.getOrPut(key) {
        val pinTrustManager = SpkiPinTrustManager(normalizedPin)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(pinTrustManager), SecureRandom())

        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, pinTrustManager)
            .pingInterval(15, TimeUnit.SECONDS)
            .build()
    }
}
