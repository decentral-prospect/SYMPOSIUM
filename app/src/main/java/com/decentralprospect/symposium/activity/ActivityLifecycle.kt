package com.decentralprospect.symposium

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.decentralprospect.symposium.ui.theme.MyApplicationTheme

internal fun MainActivity.initializeMainActivityAfterCreate() {
    loadLanguagePrefs()
    loadTelemetryPrivacyPrefs()
    loadWelcomeNewsPrompt()
    loadAppearancePrefs()
    startTelemetry()
    if (!secretProtectionChoicePromptVisibleState) {
        requestNotificationPermissionIfNeeded()
    }
    ensureCallServiceBound()

    setContent {
        val callRuntime = callRuntimeState

        MyApplicationTheme(themeMode = appThemeModeState) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                AppScreen(
                    appVersion = APP_VERSION,
                    expectedRelayVersion = EXPECTED_RELAY_VERSION,
                    reconnectMode = callRuntime?.reconnectMode ?: false,
                    pictureInPictureMode = pictureInPictureModeState,
                    telemetryEnabled = telemetryEnabledState,
                    welcomeNewsPromptVisible = welcomeNewsPromptVisibleState,
                    secretProtectionEnabled = secretProtectionEnabledState,
                    secretProtectionChoicePromptVisible = secretProtectionChoicePromptVisibleState,
                    themeMode = appThemeModeState,
                    appLanguage = appLanguageState,
                    onThemeModeChange = { mode ->
                        setAppThemeMode(mode, showToast = true)
                    },
                    onAppLanguageChange = { language ->
                        setAppLanguage(language)
                    },
                    onTelemetryEnabledChange = { enabled ->
                        setExternalTelemetryEnabled(enabled, showToast = true)
                        callRuntimeState?.setExternalTelemetryEnabled(enabled, showToast = false)
                    },
                    onWelcomeNewsPromptDismiss = {
                        dismissWelcomeNewsPrompt()
                    },
                    onSecretProtectionEnabledChange = { enabled ->
                        setSecretProtectionEnabled(enabled, showToast = true)
                    },
                    onInitialSecretProtectionChoice = { enabled ->
                        resolveInitialSecretProtectionChoice(enabled)
                    },
                    onAdminSessionAccess = { hasAdminSessionAccess() },
                    onRequestBind = { binder ->
                        attachCallUiBinder(binder)
                    },
                    onConnect = { url, room, roomInstance, username, tlsPin, modKey, e2eeSecret ->
                        connectViaCallService(url, room, roomInstance, username, tlsPin, modKey, e2eeSecret)
                    },
                    onCancelReconnect = {
                        callRuntimeState?.stopReconnectMode()
                        callRuntimeState?.disconnect()
                    },
                    onDisconnect = {
                        callRuntimeState?.disconnect()
                    },
                    onToggleSpeaker = {
                        callRuntimeState?.toggleSpeakerphone()
                        callRuntimeState?.speakerphoneOn ?: false
                    },
                    onSetAudioRoute = { route ->
                        callRuntimeState?.setAudioRouteFromUi(route)
                    },
                    onToggleMic = {
                        callRuntimeState?.toggleMic()
                        callRuntimeState?.micEnabledState ?: false
                    },
                    onToggleVideo = {
                        callRuntimeState?.toggleVideo()
                        callRuntimeState?.videoEnabledState ?: false
                    },
                    onToggleOutput = {
                        callRuntimeState?.toggleOutput()
                        callRuntimeState?.outputEnabled ?: true
                    },
                    onSwitchCamera = {
                        callRuntimeState?.switchCamera()
                    },
                    onApproveLobbyPeer = { peerId ->
                        callRuntimeState?.sendModeratorTargetCommand("lobby-approve", peerId)
                    },
                    onRejectLobbyPeer = { peerId ->
                        callRuntimeState?.sendModeratorTargetCommand("lobby-reject", peerId)
                    },
                    onKickPeer = { peerId ->
                        callRuntimeState?.sendModeratorTargetCommand("kick", peerId)
                    },
                    onMutePeer = { peerId ->
                        callRuntimeState?.sendModeratorTargetCommand("mute", peerId)
                    },
                    onUnmutePeer = { peerId ->
                        callRuntimeState?.sendModeratorTargetCommand("unmute", peerId)
                    },
                    onSetMuteAll = { enabled ->
                        callRuntimeState?.sendSetMuteAll(enabled)
                    },
                    onSetHandRaised = { enabled ->
                        callRuntimeState?.sendSetHandRaised(enabled)
                    },
                    onLowerPeerHand = { peerId ->
                        callRuntimeState?.sendLowerPeerHand(peerId)
                    },
                    onSetMicAudioEnabled = { enabled ->
                        callRuntimeState?.sendSelfMediaState(audioEnabled = enabled)
                    },
                    onInstall = { ip, login, pass, expectedSshHostKeyPin, deploymentProfile, relayCapacity, logger ->
                        withAdminAccessOperation {
                            performRelayInstallationWithTelemetry(
                                serverIp = ip,
                                login = login,
                                password = pass,
                                expectedSshHostKeyPin = expectedSshHostKeyPin,
                                existingProfile = deploymentProfile,
                                relayCapacity = relayCapacity,
                                logger = logger
                            )
                        }
                    },
                    onRemoveRelay = { ip, login, pass, expectedSshHostKeyPin, deploymentProfile, logger ->
                        withAdminAccessOperation {
                            remoteInstaller.removeRelayFromServer(
                                serverIp = ip,
                                login = login,
                                password = pass,
                                expectedSshHostKeyPin = expectedSshHostKeyPin,
                                deploymentProfile = deploymentProfile,
                                logger = logger
                            )
                        }
                    },
                    onObserveSshHostKeyPin = { ip ->
                        remoteInstaller.observeSshHostKeyPin(ip)
                    },
                    onEnrollSshKey = { ip, login, pass, expectedSshHostKeyPin ->
                        withAdminAccessOperation {
                            remoteInstaller.enrollSshKey(
                                serverIp = ip,
                                login = login,
                                password = pass,
                                expectedSshHostKeyPin = expectedSshHostKeyPin
                            )
                        }
                    },
                    onDeleteSshKey = { ip, login ->
                        remoteInstaller.deleteSshKey(ip, login)
                    },
                    onProbeServer = { ip, login, pass, expectedSshHostKeyPin ->
                        withAdminAccessOperation {
                            remoteInstaller.probeInstallationState(
                                serverIp = ip,
                                login = login,
                                password = pass,
                                expectedSshHostKeyPin = expectedSshHostKeyPin
                            )
                        }
                    },
                    onSetRoomOpenState = { ip: String, httpsPort: Int?, relayTlsPin: String?, adminToken: String?, roomName: String, open: Boolean ->
                        withAdminAccessOperation {
                            remoteInstaller.setRoomOpenStateOverHttps(
                                serverIp = ip,
                                httpsPort = httpsPort,
                                relayTlsPin = relayTlsPin,
                                adminToken = adminToken,
                                roomName = roomName,
                                open = open
                            )
                        }
                    },
                    onCreateRoom = { ip: String, httpsPort: Int?, relayTlsPin: String?, adminToken: String?, roomName: String, muteAllByDefault: Boolean, admitWithoutApproval: Boolean ->
                        withAdminAccessOperation {
                            remoteInstaller.setRoomOpenStateOverHttps(
                                serverIp = ip,
                                httpsPort = httpsPort,
                                relayTlsPin = relayTlsPin,
                                adminToken = adminToken,
                                roomName = roomName,
                                open = true,
                                muteAllByDefault = muteAllByDefault,
                                admitWithoutApproval = admitWithoutApproval
                            )
                        }
                    },
                    onSetRoomSettings = { ip: String, httpsPort: Int?, relayTlsPin: String?, adminToken: String?, roomName: String, muteAllByDefault: Boolean, admitWithoutApproval: Boolean ->
                        withAdminAccessOperation {
                            remoteInstaller.setRoomSettingsOverHttps(
                                serverIp = ip,
                                httpsPort = httpsPort,
                                relayTlsPin = relayTlsPin,
                                adminToken = adminToken,
                                roomName = roomName,
                                muteAllByDefault = muteAllByDefault,
                                admitWithoutApproval = admitWithoutApproval
                            )
                        }
                    },
                    onRotateModeratorKey = { ip: String, httpsPort: Int?, relayTlsPin: String?, adminToken: String?, roomName: String ->
                        withAdminAccessOperation {
                            remoteInstaller.rotateModeratorKeyOverHttps(
                                serverIp = ip,
                                httpsPort = httpsPort,
                                relayTlsPin = relayTlsPin,
                                adminToken = adminToken,
                                roomName = roomName
                            )
                        }
                    },
                    onFetchOpenRooms = { ip: String, httpsPort: Int?, relayTlsPin: String?, adminToken: String? ->
                        withAdminAccessOperation {
                            remoteInstaller.fetchOpenRoomsOverHttps(
                                serverIp = ip,
                                httpsPort = httpsPort,
                                relayTlsPin = relayTlsPin,
                                adminToken = adminToken
                            )
                        }
                    },
                    onFetchRelayVersion = { ip: String, httpsPort: Int?, relayTlsPin: String?, adminToken: String? ->
                        withAdminAccessOperation {
                            remoteInstaller.fetchRelayVersionOverHttps(
                                serverIp = ip,
                                httpsPort = httpsPort,
                                relayTlsPin = relayTlsPin,
                                adminToken = adminToken
                            )
                        }
                    }
                )
            }
        }
    }
}

private suspend fun <T> MainActivity.withAdminAccessOperation(block: suspend () -> T): T {
    check(hasAdminSessionAccess()) { tr(TextId.ACCESS_TO_PROTECTED_DATA_WAS_NOT_CONFIRMED) }
    return block()
}
