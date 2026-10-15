package com.decentralprospect.symposium

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun relayCapacityPresetLabelId(preset: RelayCapacityPreset): TextId = when (preset) {
    RelayCapacityPreset.STANDARD -> TextId.CAPACITY_UP_TO_5_SPEAKERS
    RelayCapacityPreset.SPEAKERS_10 -> TextId.CAPACITY_UP_TO_10_SPEAKERS
    RelayCapacityPreset.SPEAKERS_15 -> TextId.CAPACITY_UP_TO_15_SPEAKERS
    RelayCapacityPreset.SPEAKERS_25 -> TextId.CAPACITY_UP_TO_25_SPEAKERS
    RelayCapacityPreset.CUSTOM -> TextId.CUSTOM_CONFIGURATION
}

internal fun relayCapacityPresetDescriptionId(preset: RelayCapacityPreset): TextId = when (preset) {
    RelayCapacityPreset.STANDARD -> TextId.HARDWARE_1_CPU_2_GB
    RelayCapacityPreset.SPEAKERS_10 -> TextId.HARDWARE_2_CPU_4_GB
    RelayCapacityPreset.SPEAKERS_15 -> TextId.HARDWARE_4_CPU_8_GB
    RelayCapacityPreset.SPEAKERS_25 -> TextId.HARDWARE_8_CPU_16_GB
    RelayCapacityPreset.CUSTOM -> TextId.CUSTOM_RELAY_LIMITS
}

private fun displayRelayCapacityPreset(config: RelayCapacityConfig): RelayCapacityPreset {
    return normalizeRelayCapacityConfig(config).preset
}

internal fun relayCapacitySummary(config: RelayCapacityConfig): LocalizedText {
    val normalized = normalizeRelayCapacityConfig(config)
    return when (normalized.preset) {
        RelayCapacityPreset.STANDARD -> LocalizedText(TextId.STANDARD_CAPACITY_SUMMARY)
        RelayCapacityPreset.CUSTOM -> LocalizedText(
            TextId.CUSTOM_CAPACITY_SUMMARY,
            listOf(normalized.maxActivePeers)
        )
        else -> LocalizedText(TextId.CAPACITY_SUMMARY, listOf(normalized.preset.speakers))
    }
}

@Composable
internal fun RelayAdvancedSettingsButton(
    config: RelayCapacityConfig,
    enabled: Boolean,
    onSave: (RelayCapacityConfig) -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val normalizedConfig = remember(config) { normalizeRelayCapacityConfig(config) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton(
            label = tr(TextId.CONFIGURATION),
            onClick = { visible = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            kind = ActionButtonKind.SECONDARY
        )
        Text(
            text = relayCapacitySummary(normalizedConfig).resolve(),
            color = appTextSecondaryColor(),
            fontSize = 12.sp
        )
    }
    if (visible) {
        RelayCapacityDialog(
            initialConfig = normalizedConfig,
            onDismiss = { visible = false },
            onSave = {
                onSave(it)
                visible = false
            }
        )
    }
}

@Composable
private fun RelayCapacityDialog(
    initialConfig: RelayCapacityConfig,
    onDismiss: () -> Unit,
    onSave: (RelayCapacityConfig) -> Unit
) {
    val normalizedInitialConfig = remember(initialConfig) {
        normalizeRelayCapacityConfig(initialConfig)
    }
    var selectedPreset by remember(normalizedInitialConfig) {
        mutableStateOf(displayRelayCapacityPreset(normalizedInitialConfig))
    }
    var customBase by remember(normalizedInitialConfig) { mutableStateOf(normalizedInitialConfig) }
    var maxActivePeers by multilineRemember(normalizedInitialConfig.maxActivePeers.toString())
    var maxPendingLobbyPeers by multilineRemember(normalizedInitialConfig.maxPendingLobbyPeers.toString())
    var pendingIceCandidates by multilineRemember(normalizedInitialConfig.pendingIceCandidatesPerPeer.toString())
    var pendingIceKiB by multilineRemember((normalizedInitialConfig.pendingIceBytesPerPeer / 1024).toString())
    var pendingIceTotalMiB by multilineRemember((normalizedInitialConfig.pendingIceBytesTotal / (1024 * 1024)).toString())
    var joinRate by multilineRemember(normalizedInitialConfig.joinRatePerMinute.toString())
    var concurrentJoins by multilineRemember(normalizedInitialConfig.maxConcurrentJoins.toString())

    fun applyPreset(preset: RelayCapacityPreset) {
        selectedPreset = preset
        if (preset == RelayCapacityPreset.CUSTOM) return
        val value = RelayCapacityConfig.forPreset(preset)
        customBase = value
        maxActivePeers = value.maxActivePeers.toString()
        maxPendingLobbyPeers = value.maxPendingLobbyPeers.toString()
        pendingIceCandidates = value.pendingIceCandidatesPerPeer.toString()
        pendingIceKiB = (value.pendingIceBytesPerPeer / 1024).toString()
        pendingIceTotalMiB = (value.pendingIceBytesTotal / (1024 * 1024)).toString()
        joinRate = value.joinRatePerMinute.toString()
        concurrentJoins = value.maxConcurrentJoins.toString()
    }

    val parsed = if (selectedPreset == RelayCapacityPreset.CUSTOM) {
        runCatching {
            customBase.copy(
                preset = selectedPreset,
                maxActivePeers = maxActivePeers.toInt(),
                maxPendingLobbyPeers = maxPendingLobbyPeers.toInt(),
                pendingIceCandidatesPerPeer = pendingIceCandidates.toInt(),
                pendingIceBytesPerPeer = Math.multiplyExact(pendingIceKiB.toLong(), 1024L),
                pendingIceBytesTotal = Math.multiplyExact(pendingIceTotalMiB.toLong(), 1024L * 1024L),
                joinRatePerMinute = joinRate.toInt(),
                maxConcurrentJoins = concurrentJoins.toInt()
            )
        }.getOrNull()
    } else {
        RelayCapacityConfig.forPreset(selectedPreset)
    }
    val validationError = if (parsed == null) {
        tr(TextId.ENTER_WHOLE_NUMBERS)
    } else {
        parsed.validationError()?.let(::tr)
    }

    AppDialog(
        title = tr(TextId.RELAY_CONFIGURATION),
        onDismiss = onDismiss,
        maxWidth = 640.dp,
        scrollableContent = true,
        avoidKeyboard = true,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = tr(TextId.SPEAKER_DEFINITION),
                    color = appTextSecondaryColor(),
                    fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresetButton(RelayCapacityPreset.STANDARD, selectedPreset, Modifier.weight(1f), ::applyPreset)
                    PresetButton(RelayCapacityPreset.SPEAKERS_10, selectedPreset, Modifier.weight(1f), ::applyPreset)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresetButton(RelayCapacityPreset.SPEAKERS_15, selectedPreset, Modifier.weight(1f), ::applyPreset)
                    PresetButton(RelayCapacityPreset.SPEAKERS_25, selectedPreset, Modifier.weight(1f), ::applyPreset)
                }
                PresetButton(
                    RelayCapacityPreset.CUSTOM,
                    selectedPreset,
                    Modifier.fillMaxWidth(),
                    ::applyPreset
                )
                Text(
                    text = if (selectedPreset == RelayCapacityPreset.CUSTOM) {
                        tr(relayCapacityPresetDescriptionId(selectedPreset))
                    } else {
                        tr(
                            TextId.SERVER_REQUIREMENTS,
                            tr(relayCapacityPresetDescriptionId(selectedPreset))
                        )
                    },
                    color = appTextPrimaryColor(),
                    fontSize = 14.sp
                )
                if (selectedPreset != RelayCapacityPreset.CUSTOM) {
                    Text(
                        text = tr(TextId.LISTENER_CAPACITY, selectedPreset.listeners),
                        color = appTextSecondaryColor(),
                        fontSize = 12.sp
                    )
                }
                AnimatedVisibility(visible = selectedPreset == RelayCapacityPreset.CUSTOM) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CapacityNumberField(tr(TextId.MAX_ACTIVE_PARTICIPANTS), maxActivePeers) {
                            maxActivePeers = it
                        }
                        CapacityNumberField(tr(TextId.MAX_LOBBY_PARTICIPANTS), maxPendingLobbyPeers) {
                            maxPendingLobbyPeers = it
                        }
                        CapacityNumberField(tr(TextId.PENDING_ICE_CANDIDATES), pendingIceCandidates) {
                            pendingIceCandidates = it
                        }
                        CapacityNumberField(tr(TextId.ICE_MEMORY_PER_PARTICIPANT_KIB), pendingIceKiB) {
                            pendingIceKiB = it
                        }
                        CapacityNumberField(tr(TextId.TOTAL_RELAY_ICE_MEMORY_MIB), pendingIceTotalMiB) {
                            pendingIceTotalMiB = it
                        }
                        CapacityNumberField(tr(TextId.JOIN_ATTEMPTS_PER_MINUTE), joinRate) {
                            joinRate = it
                        }
                        CapacityNumberField(tr(TextId.MAX_CONCURRENT_JOINS), concurrentJoins) {
                            concurrentJoins = it
                        }
                    }
                }
                if (validationError != null) ErrorMessage(validationError)
            }
        },
        actions = {
            ActionButton(
                label = tr(TextId.CANCEL),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                kind = ActionButtonKind.SECONDARY
            )
            ActionButton(
                label = tr(TextId.SAVE),
                onClick = { parsed?.let(onSave) },
                modifier = Modifier.weight(1f),
                enabled = parsed != null && validationError == null
            )
        }
    )
}

@Composable
private fun PresetButton(
    preset: RelayCapacityPreset,
    selectedPreset: RelayCapacityPreset,
    modifier: Modifier,
    onSelect: (RelayCapacityPreset) -> Unit
) {
    ActionButton(
        label = tr(relayCapacityPresetLabelId(preset)),
        onClick = { onSelect(preset) },
        modifier = modifier.semantics { selected = preset == selectedPreset },
        kind = if (preset == selectedPreset) ActionButtonKind.PRIMARY else ActionButtonKind.GHOST
    )
}

@Composable
private fun CapacityNumberField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> onValueChange(next.filter(Char::isDigit).take(12)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = textFieldColors()
    )
}

@Composable
private fun multilineRemember(initial: String) = remember(initial) { mutableStateOf(initial) }
