package com.decentralprospect.symposium

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Principal
import java.security.PublicKey
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Date

class SpkiPinTrustManagerTest {

    @Test
    fun pinnedCertificateLaterInUnvalidatedChainDoesNotAuthenticateAttackerLeaf() {
        val attacker = fakeCertificate()
        val pinned = fakeCertificate()
        val manager = SpkiPinTrustManager(pinOf(pinned.publicKey))

        try {
            manager.checkServerTrusted(arrayOf(attacker, pinned), "RSA")
            fail("attacker-controlled leaf was accepted because a later certificate matched the pin")
        } catch (expected: java.security.cert.CertificateException) {
            assertTrue(expected.message.orEmpty().contains("Leaf SPKI pin mismatch"))
        }
    }

    @Test
    fun validPinnedLeafIsAcceptedAndExpiredPinnedLeafIsRejected() {
        val valid = fakeCertificate()
        SpkiPinTrustManager(pinOf(valid.publicKey))
            .checkServerTrusted(arrayOf(valid), "RSA")

        val expired = fakeCertificate(notAfter = Date(System.currentTimeMillis() - 1_000L))
        try {
            SpkiPinTrustManager(pinOf(expired.publicKey))
                .checkServerTrusted(arrayOf(expired), "RSA")
            fail("expired pinned leaf was accepted")
        } catch (_: CertificateExpiredException) {
            // Expected.
        }
    }

    private fun pinOf(key: PublicKey): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.encoded)
        return "sha256/${Base64.getEncoder().encodeToString(digest)}"
    }

    private fun fakeCertificate(
        notBefore: Date = Date(System.currentTimeMillis() - 60_000L),
        notAfter: Date = Date(System.currentTimeMillis() + 60_000L)
    ): X509Certificate {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair().public
        return FakeX509Certificate(key, notBefore, notAfter)
    }

    private class FakeX509Certificate(
        private val key: PublicKey,
        private val validFrom: Date,
        private val validUntil: Date
    ) : X509Certificate() {
        override fun checkValidity() = checkValidity(Date())

        override fun checkValidity(date: Date) {
            if (date.before(validFrom)) throw CertificateNotYetValidException()
            if (date.after(validUntil)) throw CertificateExpiredException()
        }

        override fun getVersion(): Int = 3
        override fun getSerialNumber(): BigInteger = BigInteger.ONE
        override fun getIssuerDN(): Principal = Principal { "CN=issuer" }
        override fun getSubjectDN(): Principal = Principal { "CN=subject" }
        override fun getNotBefore(): Date = validFrom
        override fun getNotAfter(): Date = validUntil
        override fun getTBSCertificate(): ByteArray = byteArrayOf(1)
        override fun getSignature(): ByteArray = byteArrayOf(1)
        override fun getSigAlgName(): String = "NONE"
        override fun getSigAlgOID(): String = "0.0"
        override fun getSigAlgParams(): ByteArray? = null
        override fun getIssuerUniqueID(): BooleanArray? = null
        override fun getSubjectUniqueID(): BooleanArray? = null
        override fun getKeyUsage(): BooleanArray? = null
        override fun getBasicConstraints(): Int = -1
        override fun getEncoded(): ByteArray = throw CertificateEncodingException("not needed")
        override fun verify(key: PublicKey) = Unit
        override fun verify(key: PublicKey, sigProvider: String) = Unit
        override fun toString(): String = "FakeX509Certificate"
        override fun getPublicKey(): PublicKey = key
        override fun hasUnsupportedCriticalExtension(): Boolean = false
        override fun getCriticalExtensionOIDs(): MutableSet<String>? = null
        override fun getNonCriticalExtensionOIDs(): MutableSet<String>? = null
        override fun getExtensionValue(oid: String): ByteArray? = null
    }
}
