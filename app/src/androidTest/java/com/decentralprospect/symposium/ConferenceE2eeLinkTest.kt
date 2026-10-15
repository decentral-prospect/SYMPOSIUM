package com.decentralprospect.symposium

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class ConferenceE2eeLinkTest {

    private val roomInstance = "test-room-instance"

    @Test
    fun linksAreRejectedWithoutCrash() {
        assertNull(parseInvitationToken("http:"))
        assertNull(parseInvitationToken("https://relay.example/connect"))
        assertNull(parseInvitationToken("symposium://connect"))
    }

    @Test
    fun malformedAndOversizedTokensAreRejectedWithoutCrash() {
        val malformed = listOf(
            "abcde",
            "a:bcd",
            "SPT1.",
            "SPT1.not-base64!",
            "SPT2.AAAA"
        )

        malformed.forEach { value -> assertNull(value, parseInvitationToken(value)) }
        assertNull(parseInvitationToken("x".repeat(MAX_INVITATION_TOKEN_LENGTH + 1)))
    }

    @Test
    fun tamperedInvitationTokenIsRejected() {
        val secret = generateConferenceE2eeSecret()
        val pin = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        val token = buildInvitationToken(
            "relay.example",
            443,
            "room",
            roomInstance,
            pin,
            secret,
            requiredVersion = APP_VERSION
        )

        val replacement = if (token.last() == 'A') 'B' else 'A'
        assertNull(parseInvitationToken(token.dropLast(1) + replacement))
    }

    @Test
    fun generatedInvitationTokenRoundTripsAndIsOpaque() {
        val secret = generateConferenceE2eeSecret()
        val pin = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        val token = buildInvitationToken(
            "relay.example",
            38443,
            "room",
            roomInstance,
            pin,
            secret,
            requiredVersion = APP_VERSION
        )

        val parsed = parseInvitationToken(token)
        assertEquals("relay.example:38443", parsed?.ip)
        assertEquals(APP_VERSION, parsed?.requiredVersion)
        assertEquals(secret, parsed?.e2eeSecret)
        assertFalse(token.contains("relay.example"))
        assertFalse(token.contains("room"))
        assertFalse(token.contains(secret))
    }

    @Test
    fun invitationEncodingIsUrlSafeAndRejectsNonCanonicalForms() {
        val token = buildInvitationToken(
            ip = "relay.example",
            httpsPort = 443,
            room = "room",
            roomInstance = roomInstance,
            relayTlsPin = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            e2eeSecret = generateConferenceE2eeSecret(),
            requiredVersion = APP_VERSION
        )

        assertTrue(token.matches(Regex("SPT1\\.[A-Za-z0-9_-]+")))
        assertTrue(parseInvitationToken(token) != null)
        assertNull(parseInvitationToken(token + "="))
        assertNull(parseInvitationToken(token + "\nignored"))
        assertNull(parseInvitationToken(token + "!"))
    }

    @Test
    fun invitationWithAnotherRequiredVersionIsBlockedBeforeConnection() {
        val incompatibleVersion = if (APP_VERSION == "0.3.5") "0.3.4" else "0.3.5"
        val token = buildInvitationToken(
            ip = "relay.example",
            httpsPort = 443,
            room = "versioned-room",
            roomInstance = roomInstance,
            relayTlsPin = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            e2eeSecret = generateConferenceE2eeSecret(),
            requiredVersion = incompatibleVersion
        )

        assertNull(parseInvitationToken(token))
        assertEquals(incompatibleVersion, invitationTokenRequiredVersionMismatch(token))
    }

    @Test
    fun roomVersionComesOnlyFromServerAndNeverFromClientFallback() {
        val serverVersion = if (APP_VERSION == "0.3.4") "0.3.5" else "0.3.4"
        val legacyRooms = parseOpenRoomsJson(
            """{"openRooms":[{"room":"legacy-room"}]}"""
        )
        assertTrue(legacyRooms.isEmpty())

        val serverRoom = parseOpenRoomsJson(
            """{"openRooms":[{"room":"versioned-room","roomInstance":"$roomInstance","requiredVersion":"$serverVersion"}]}"""
        ).single()
        val requiredVersion = requireNotNull(verifiedRoomRequiredVersion(serverRoom))
        val token = buildInvitationToken(
            ip = "relay.example",
            httpsPort = 443,
            room = serverRoom.name,
            roomInstance = serverRoom.roomInstance,
            relayTlsPin = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            e2eeSecret = generateConferenceE2eeSecret(),
            requiredVersion = requiredVersion
        )

        assertEquals(serverVersion, invitationTokenRequiredVersionMismatch(token))
    }

    @Test
    fun tokensForTheSameInvitationAreUnique() {
        val secret = generateConferenceE2eeSecret()
        val first = buildInvitationToken(
            ip = "relay.example",
            httpsPort = 443,
            room = "private-room",
            roomInstance = roomInstance,
            relayTlsPin = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            e2eeSecret = secret,
            requiredVersion = APP_VERSION
        )
        val second = buildInvitationToken(
            ip = "relay.example",
            httpsPort = 443,
            room = "private-room",
            roomInstance = roomInstance,
            relayTlsPin = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            e2eeSecret = secret,
            requiredVersion = APP_VERSION
        )

        assertFalse(first == second)
        assertEquals(secret, parseInvitationToken(first)?.e2eeSecret)
        assertEquals(secret, parseInvitationToken(second)?.e2eeSecret)
    }

    @Test
    fun legacyLinksAreRejected() {
        val legacy = "symposium://connect?ip=relay.example&room=room&tlsPin=sha256%2FAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA%3D"
        assertNull(parseInvitationToken(legacy))
    }

    @Test
    fun generatedSecretIsCanonicalAndFullEntropy() {
        val secret = generateConferenceE2eeSecret()
        assertEquals(43, secret.length)
        assertEquals(secret, normalizeConferenceE2eeSecret(secret))
        assertEquals(CONFERENCE_E2EE_SECRET_BYTES, decodeConferenceE2eeSecret(secret).size)

        val another = generateConferenceE2eeSecret()
        assertFalse(secret == another)
    }

    @Test
    fun oversizedRelayRoomNameRejectsTheWholeResponse() {
        val valid = "a".repeat(128)
        val oversized = "b".repeat(129)
        val response = """{"openRooms":[
            {"room":"$valid","roomInstance":"$roomInstance","requiredVersion":"$APP_VERSION"},
            {"room":"$oversized","roomInstance":"$roomInstance","requiredVersion":"$APP_VERSION"}
        ]}"""

        assertTrue(parseOpenRoomsJson(response).isEmpty())
        assertEquals(
            valid,
            parseOpenRoomsJson(
                """{"openRooms":[{"room":"$valid","roomInstance":"$roomInstance","requiredVersion":"$APP_VERSION"}]}"""
            ).single().name
        )
    }

    @Test
    fun recreatedRoomInstanceGetsAnIndependentE2eeSecret() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferencesName = "conference_e2ee_instance_test_${System.nanoTime()}"
        val store = ConferenceE2eeSecretStore(context, preferencesName)
        val room = "recreated-room"
        val firstInstance = "first-room-instance"
        val secondInstance = "second-room-instance"

        try {
            val firstSecret = store.getOrCreate("relay.example", 443, room, firstInstance)
            val secondSecret = store.getOrCreate("relay.example", 443, room, secondInstance)
            assertFalse(firstSecret == secondSecret)
            assertEquals(firstSecret, store.get("relay.example", 443, room, firstInstance))
            assertEquals(secondSecret, store.get("relay.example", 443, room, secondInstance))
        } finally {
            store.remove("relay.example", 443, room, firstInstance)
            store.remove("relay.example", 443, room, secondInstance)
            context.deleteSharedPreferences(preferencesName)
        }
    }

    @Test
    fun importedSecretIsReusedForTheCanonicalRelayEndpoint() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferencesName = "conference_e2ee_import_test_${System.nanoTime()}"
        val store = ConferenceE2eeSecretStore(context, preferencesName)
        val room = "imported-${System.nanoTime()}"
        val secret = generateConferenceE2eeSecret()

        try {
            store.save("relay.example:38443", null, room, roomInstance, secret)
            assertEquals(secret, store.getOrCreate("relay.example", 38443, room, roomInstance))
        } finally {
            store.remove("relay.example", 38443, room, roomInstance)
            context.deleteSharedPreferences(preferencesName)
        }
    }

    @Test
    fun encryptedPreferencesMetadataDoesNotCountAsAConferenceSecret() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferencesName = "conference_e2ee_presence_test_${System.nanoTime()}"
        val store = ConferenceE2eeSecretStore(context, preferencesName)
        val room = "presence-${System.nanoTime()}"

        try {
            assertFalse(store.hasEncryptedSecrets())

            store.save("presence.example", 443, room, roomInstance, generateConferenceE2eeSecret())
            assertTrue(store.hasEncryptedSecrets())

            store.removeAll(
                "presence.example",
                443,
                listOf(OpenRoomInfo(name = room, roomInstance = roomInstance))
            )
            assertFalse(store.hasEncryptedSecrets())
        } finally {
            context.deleteSharedPreferences(preferencesName)
        }
    }

    @Test
    fun invitationValuesRemainUntrustedUntilConnectionSuccess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tlsPreferences = "relay_tls_trust_test_${System.nanoTime()}"
        val e2eePreferences = "conference_e2ee_trust_test_${System.nanoTime()}"
        val tlsStore = RelayTlsPinTrustStore(context, tlsPreferences)
        val e2eeStore = ConferenceE2eeSecretStore(context, e2eePreferences)
        val endpoint = RelayEndpoint("new-relay.example", 38443)
        val room = "pending-room"
        val pin = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        val secret = generateConferenceE2eeSecret()
        val token = buildInvitationToken(
            endpoint.host,
            endpoint.port,
            room,
            roomInstance,
            pin,
            secret,
            requiredVersion = APP_VERSION
        )

        try {
            val parsed = parseInvitationToken(token)
            assertEquals(pin, parsed?.tlsPin)
            assertNull(tlsStore.get(endpoint))
            assertNull(e2eeStore.get(endpoint.host, endpoint.port, room, roomInstance))

            e2eeStore.save(endpoint.host, endpoint.port, room, roomInstance, secret)
            tlsStore.save(endpoint, pin)

            assertEquals(pin, tlsStore.get(endpoint))
            assertEquals(secret, e2eeStore.get(endpoint.host, endpoint.port, room, roomInstance))
        } finally {
            tlsStore.remove(endpoint)
            e2eeStore.remove(endpoint.host, endpoint.port, room, roomInstance)
            context.deleteSharedPreferences(tlsPreferences)
            context.deleteSharedPreferences(e2eePreferences)
        }
    }

    @Test
    fun e2eeFingerprintIsStableAndDoesNotRevealTheSecret() {
        val secret = generateConferenceE2eeSecret()
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(decodeConferenceE2eeSecret(secret))
            .joinToString(":") { byte -> "%02X".format(byte.toInt() and 0xff) }

        val fingerprint = conferenceE2eeFingerprint(secret)
        assertEquals(expected, fingerprint)
        assertFalse(fingerprint.contains(secret))
    }
}
