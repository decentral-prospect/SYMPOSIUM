package com.decentralprospect.symposium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayCapacityConfigTest {
    @Test
    fun preJoinTimeoutUsesValidGoDurationSyntax() {
        assertEquals("15s", relayDurationSeconds(15))
    }

    @Test
    fun relayUpdateWarningIsShownOnlyForInstalledOutdatedServer() {
        val server = InstallServer(
            id = "server-id",
            name = "Relay",
            ip = "192.0.2.10",
            username = "admin",
            password = "",
            installed = true,
            relayVersionState = RelayVersionState.OUTDATED
        )

        assertTrue(serverNeedsRelayUpdate(server))
        assertTrue(!serverNeedsRelayUpdate(server.copy(relayVersionState = RelayVersionState.CURRENT)))
        assertTrue(!serverNeedsRelayUpdate(server.copy(installed = false)))
    }

    @Test
    fun outdatedRelayCannotBeSelectedForRoomCreation() {
        val readyServer = InstallServer(
            id = "server-id",
            name = "Relay",
            ip = "192.0.2.10",
            username = "admin",
            password = "",
            installed = true,
            relayTlsPin = "sha256/example",
            adminToken = "token",
            relayVersionState = RelayVersionState.CURRENT
        )

        assertTrue(canCreateMeetingOnServer(readyServer))
        assertTrue(
            !canCreateMeetingOnServer(
                readyServer.copy(relayVersionState = RelayVersionState.OUTDATED)
            )
        )
        assertTrue(
            !canCreateMeetingOnServer(
                readyServer.copy(relayVersionState = RelayVersionState.UNKNOWN)
            )
        )
    }

    @Test
    fun newServerUsesSmallestPresetByDefault() {
        val server = InstallServer(
            id = "server-id",
            name = "Relay",
            ip = "192.0.2.10",
            username = "admin",
            password = "",
            installed = false
        )

        assertEquals(RelayCapacityPreset.STANDARD, server.relayCapacity.preset)
        assertEquals(100, server.relayCapacity.maxActivePeers)
        assertEquals(TextId.STANDARD_CAPACITY_SUMMARY, relayCapacitySummary(server.relayCapacity).id)
    }

    @Test
    fun legacyStandardConfigurationIsNormalizedToCurrentDefault() {
        val legacyStandard = RelayCapacityConfig(
            preset = RelayCapacityPreset.CUSTOM,
            maxActivePeers = 100,
            maxPendingLobbyPeers = 250,
            pendingIceCandidatesPerPeer = 64,
            pendingIceBytesPerPeer = 256L * 1024,
            pendingIceBytesTotal = 16L * 1024 * 1024,
            joinRatePerMinute = 12,
            maxConcurrentJoins = 16
        )

        val normalized = normalizeRelayCapacityConfig(legacyStandard)

        assertEquals(RelayCapacityConfig.forPreset(RelayCapacityPreset.STANDARD), normalized)
        assertEquals(TextId.STANDARD_CAPACITY_SUMMARY, relayCapacitySummary(legacyStandard).id)
    }

    @Test
    fun explicitCustomConfigurationRemainsCustom() {
        val custom = RelayCapacityConfig.forPreset(RelayCapacityPreset.STANDARD).copy(
            preset = RelayCapacityPreset.CUSTOM,
            maxActivePeers = 777,
            maxPendingLobbyPeers = 1_200
        )

        assertEquals(custom, normalizeRelayCapacityConfig(custom))
    }

    @Test
    fun presetLabelsAndSummariesDescribeSpeakers() {
        assertEquals(TextId.CAPACITY_UP_TO_5_SPEAKERS, relayCapacityPresetLabelId(RelayCapacityPreset.STANDARD))
        assertEquals(TextId.CAPACITY_UP_TO_10_SPEAKERS, relayCapacityPresetLabelId(RelayCapacityPreset.SPEAKERS_10))
        assertEquals(
            TextId.CAPACITY_UP_TO_15_SPEAKERS,
            relayCapacityPresetLabelId(RelayCapacityPreset.SPEAKERS_15)
        )
        assertEquals(
            TextId.CAPACITY_UP_TO_25_SPEAKERS,
            relayCapacityPresetLabelId(RelayCapacityPreset.SPEAKERS_25)
        )
        assertEquals(
            TextId.HARDWARE_1_CPU_2_GB,
            relayCapacityPresetDescriptionId(RelayCapacityPreset.STANDARD)
        )
        for (preset in listOf(RelayCapacityPreset.SPEAKERS_10, RelayCapacityPreset.SPEAKERS_15, RelayCapacityPreset.SPEAKERS_25)) {
            assertEquals(
                LocalizedText(TextId.CAPACITY_SUMMARY, listOf(preset.speakers)),
                relayCapacitySummary(RelayCapacityConfig.forPreset(preset))
            )
        }
    }

    @Test
    fun hardwarePresetsScaleCapacityAndKeepSafetyLimitsIndependent() {
        val presets = RelayCapacityPreset.entries.filter { it != RelayCapacityPreset.CUSTOM }
            .map(RelayCapacityConfig::forPreset)

        assertEquals(listOf(100, 200, 400, 1_000), presets.map { it.maxActivePeers })
        assertEquals(listOf(200, 400, 800, 2_000), presets.map { it.maxPendingLobbyPeers })
        assertEquals(listOf(16, 32, 64, 128), presets.map { it.maxConcurrentJoins })
        assertTrue(presets.zipWithNext().all { (lower, higher) ->
            lower.pendingIceBytesTotal < higher.pendingIceBytesTotal
        })
        assertTrue(presets.all { it.maxPendingLobbyPeers != it.maxActivePeers })
        assertTrue(presets.all { it.validationError() == null })
        presets.forEach { config ->
            val audience = config.preset.listeners + config.preset.speakers
            assertTrue(config.maxActivePeers >= audience * 1.25)
            assertTrue(config.maxPendingLobbyPeers >= audience)
            assertTrue(config.maxPreJoinConnections >= audience)
            assertTrue(config.pendingIceBytesTotal >= config.maxConcurrentJoins * config.pendingIceBytesPerPeer)
        }
    }

    @Test
    fun previousPresetsKeepTheirTierWhenLoaded() {
        val oldNames = listOf("STANDARD", "RAM_4_GB", "CPU_2_RAM_8_GB", "CPU_4_RAM_16_GB")
        val tiers = listOf(RelayCapacityPreset.STANDARD, RelayCapacityPreset.SPEAKERS_10,
            RelayCapacityPreset.SPEAKERS_15, RelayCapacityPreset.SPEAKERS_25)
        oldNames.zip(tiers).forEachIndexed { index, (name, preset) ->
            assertEquals(preset, relayCapacityPresetFromStoredName(name))
            val previous = RelayCapacityConfig(
                preset = RelayCapacityPreset.CUSTOM,
                maxActivePeers = listOf(250, 500, 1_000, 2_000)[index],
                maxPendingLobbyPeers = listOf(500, 1_000, 2_000, 4_000)[index],
                pendingIceCandidatesPerPeer = 64,
                pendingIceBytesPerPeer = 256L * 1024,
                pendingIceBytesTotal = (16L shl index) * 1024 * 1024,
                joinRatePerMinute = 12,
                maxConcurrentJoins = listOf(16, 16, 32, 64)[index]
            )
            assertEquals(RelayCapacityConfig.forPreset(preset), normalizeRelayCapacityConfig(previous))
        }
        RelayCapacityPreset.entries.forEach { assertEquals(it, relayCapacityPresetFromStoredName(it.name)) }
        assertEquals(RelayCapacityPreset.CUSTOM, relayCapacityPresetFromStoredName("unknown"))
    }

    @Test
    fun mediaPortRangeScalesWithAudienceAndStaysWithinUdpBounds() {
        for (preset in RelayCapacityPreset.entries.filter { it != RelayCapacityPreset.CUSTOM }) {
            val config = RelayCapacityConfig.forPreset(preset)
            for (start in listOf(30_000, 41_999, 65_300)) {
                val ports = relayIceUdpPortRange(start..start + 199, config)
                assertTrue(ports.first >= 1 && ports.last <= 65_535)
                assertTrue(ports.count() >= 4 * (config.maxActivePeers + config.maxConcurrentJoins))
            }
        }
        val config = RelayCapacityConfig.forPreset(RelayCapacityPreset.STANDARD)
        assertEquals(30_000..40_000, relayIceUdpPortRange(30_000..40_000, config))
        val custom = config.copy(preset = RelayCapacityPreset.CUSTOM, maxActivePeers = 100_000)
        val ports = relayIceUdpPortRange(65_300..65_499, custom)
        assertTrue(ports.first >= 1 && ports.last <= 65_535)
    }

    @Test
    fun customConfigurationRejectsUnsafeRetainedIceBudget() {
        val custom = RelayCapacityConfig.forPreset(RelayCapacityPreset.STANDARD).copy(
            preset = RelayCapacityPreset.CUSTOM,
            pendingIceBytesPerPeer = 2L * 1024,
            pendingIceBytesTotal = 2L * 1024
        )

        assertEquals(
            TextId.ICE_MEMORY_PER_PEER_RANGE,
            custom.validationError()
        )
    }
}
