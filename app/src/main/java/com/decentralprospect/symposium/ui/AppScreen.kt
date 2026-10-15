package com.decentralprospect.symposium

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import androidx.compose.foundation.layout.height
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner

@Composable
internal fun AppScreen(
    appVersion: String,
    expectedRelayVersion: String = appVersion,
    reconnectMode: Boolean,
    pictureInPictureMode: Boolean = false,
    telemetryEnabled: Boolean = false,
    welcomeNewsPromptVisible: Boolean = false,
    secretProtectionEnabled: Boolean = true,
    secretProtectionChoicePromptVisible: Boolean = false,
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    appLanguage: AppLanguage = AppLanguage.SYSTEM,
    onThemeModeChange: (AppThemeMode) -> Unit = {},
    onAppLanguageChange: (AppLanguage) -> Unit = {},
    onTelemetryEnabledChange: (Boolean) -> Unit = {},
    onWelcomeNewsPromptDismiss: () -> Unit = {},
    onSecretProtectionEnabledChange: (Boolean) -> Unit = {},
    onInitialSecretProtectionChoice: (Boolean) -> Unit = {},
    onAdminSessionAccess: () -> Boolean,
    onRequestBind: (UiBinder) -> Unit,
    onConnect: (String, String, String, String, String, String, String) -> Unit,
    onCancelReconnect: () -> Unit,
    onDisconnect: () -> Unit,
    onToggleSpeaker: () -> Boolean,
    onSetAudioRoute: (String) -> Unit = {},
    onToggleMic: () -> Boolean,
    onToggleVideo: () -> Boolean,
    onToggleOutput: () -> Boolean,
    onSwitchCamera: () -> Unit,
    onApproveLobbyPeer: (String) -> Unit = {},
    onRejectLobbyPeer: (String) -> Unit = {},
    onKickPeer: (String) -> Unit = {},
    onMutePeer: (String) -> Unit = {},
    onUnmutePeer: (String) -> Unit = {},
    onSetMuteAll: (Boolean) -> Unit = {},
    onInstall: suspend (ip: String, login: String, password: String, expectedSshHostKeyPin: String?, deploymentProfile: DeploymentProfile?, relayCapacity: RelayCapacityConfig, logger: suspend (String) -> Unit) -> RemoteInstaller.InstallResult,
    onRemoveRelay: suspend (ip: String, login: String, password: String, expectedSshHostKeyPin: String?, deploymentProfile: DeploymentProfile?, logger: suspend (String) -> Unit) -> RemoteInstaller.RelayRemovalResult,
    onObserveSshHostKeyPin: suspend (ip: String) -> String,
    onEnrollSshKey: suspend (ip: String, login: String, password: String, expectedSshHostKeyPin: String?) -> Unit,
    onDeleteSshKey: (ip: String, login: String) -> Unit,
    onProbeServer: suspend (ip: String, login: String, password: String, expectedSshHostKeyPin: String?) -> RemoteInstaller.ProbeResult,
    onSetRoomOpenState: suspend (ip: String, httpsPort: Int?, relayTlsPin: String?, adminToken: String?, roomName: String, open: Boolean) -> RemoteInstaller.RoomAdminResult,
    onCreateRoom: suspend (ip: String, httpsPort: Int?, relayTlsPin: String?, adminToken: String?, roomName: String, muteAllByDefault: Boolean, admitWithoutApproval: Boolean) -> RemoteInstaller.RoomAdminResult,
    onSetRoomSettings: suspend (ip: String, httpsPort: Int?, relayTlsPin: String?, adminToken: String?, roomName: String, muteAllByDefault: Boolean, admitWithoutApproval: Boolean) -> RemoteInstaller.RoomAdminResult,
    onRotateModeratorKey: suspend (ip: String, httpsPort: Int?, relayTlsPin: String?, adminToken: String?, roomName: String) -> RemoteInstaller.RoomAdminResult,
    onFetchOpenRooms: suspend (ip: String, httpsPort: Int?, relayTlsPin: String?, adminToken: String?) -> RemoteInstaller.RoomAdminResult,
    onFetchRelayVersion: suspend (ip: String, httpsPort: Int?, relayTlsPin: String?, adminToken: String?) -> RemoteInstaller.RelayVersionResult,
    onSetHandRaised: (Boolean) -> Unit = {},
    onLowerPeerHand: (String) -> Unit = {},
    onSetMicAudioEnabled: (Boolean) -> Unit = {},
    initialAccessibilityFontScale: AccessibilityFontScale = AccessibilityFontScale.SYSTEM,
    onAccessibilityFontScaleChange: (AccessibilityFontScale) -> Unit = {},
    checkForAppUpdate: suspend (String) -> String? = AppUpdateChecker::findAvailableVersion
) {
    val context = LocalContext.current
    val platformDensity = LocalDensity.current

    var accessibilityFontScale by remember { mutableStateOf(initialAccessibilityFontScale) }

    var inCallUi by remember { mutableStateOf(false) }
    var moderatorPanelOpen by remember { mutableStateOf(false) }
    var dismissedModeratorBubblePeerIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var pipOffsetX by remember { mutableStateOf(0f) }
    var pipOffsetY by remember { mutableStateOf(0f) }
    var pipExpanded by remember { mutableStateOf(false) }
    var pinnedPeerId by remember { mutableStateOf<String?>(null) }
    var minimizedCallPreviewVisible by remember { mutableStateOf(true) }
    var wasInPictureInPictureMode by remember { mutableStateOf(false) }

    var connectLink by remember { mutableStateOf("") }
    var usernameInput by remember { mutableStateOf("") }
    var connectingToRoom by remember { mutableStateOf(false) }
    var connectingRoomName by remember { mutableStateOf("") }

    val homeScope = rememberCoroutineScope()
    val homeServersStore = remember(context) { InstallServersStore(context) }
    val conferenceE2eeSecrets = remember(context) { ConferenceE2eeSecretStore(context) }
    val relayTlsPinTrustStore = remember(context) { RelayTlsPinTrustStore(context) }
    val homeServers = remember { mutableStateListOf<InstallServer>() }
    val homeVersionCheckTokens = remember { mutableStateMapOf<String, String>() }
    val homeVersionsChecking = homeVersionCheckTokens.isNotEmpty()
    var acceptedInvitationTlsPin by remember { mutableStateOf<AcceptedInvitationTlsPin?>(null) }
    var pendingInvitationTrust by remember { mutableStateOf<PendingInvitationTrust?>(null) }
    var relayTlsPinTrustRevision by remember { mutableStateOf(0) }
    var showConnectDialog by remember { mutableStateOf(false) }
    var showCreateMeetingDialog by remember { mutableStateOf(false) }
    var showHomeServerGuideDialog by remember { mutableStateOf(false) }
    var showHomeAddServerDialog by remember { mutableStateOf(false) }
    var pendingHomeSshHostKeyTrust by remember { mutableStateOf<PendingHomeSshHostKeyTrust?>(null) }
    var homeQrDialogLink by remember { mutableStateOf<String?>(null) }
    var homeQrDialogTitle by remember { mutableStateOf(tr(TextId.QR_CODE)) }

    var homeAddIp by remember { mutableStateOf("") }
    var homeAddUser by remember { mutableStateOf("") }
    var homeAddPassword by remember { mutableStateOf("") }
    var homeAddError by remember { mutableStateOf<String?>(null) }
    var homeAddLoading by remember { mutableStateOf(false) }

    val homeInstallLogs = remember { mutableStateListOf<String>() }
    var selectedHomeInstallServerId by remember { mutableStateOf<String?>(null) }
    var homeInstallLoading by remember { mutableStateOf(false) }
    var homeInstallError by remember { mutableStateOf<String?>(null) }
    var homeInstallReturnToCreateRoom by remember { mutableStateOf(false) }

    var selectedHomeCreateServerId by remember { mutableStateOf<String?>(null) }
    var homeCreateRoomName by remember { mutableStateOf("") }
    var homeRoomError by remember { mutableStateOf<String?>(null) }
    var homeRoomLoading by remember { mutableStateOf(false) }

    var selectedHomeMeetingServerId by remember { mutableStateOf<String?>(null) }
    var selectedHomeMeetingRoomName by remember { mutableStateOf<String?>(null) }
    var homeEditMuteAllByDefault by remember { mutableStateOf(false) }
    var homeEditAdmitWithoutApproval by remember { mutableStateOf(false) }
    var confirmHomeCloseMeeting by remember { mutableStateOf(false) }
    var confirmHomeModeratorLinkRotation by remember { mutableStateOf(false) }
    var confirmHomeE2eeKeyRotation by remember { mutableStateOf(false) }
    var homeServersRefreshing by remember { mutableStateOf(false) }

    var status by remember { mutableStateOf("offline") }
    var connected by remember { mutableStateOf(false) }
    var micEnabled by remember { mutableStateOf(false) }
    var videoEnabled by remember { mutableStateOf(false) }
    var outputOn by remember { mutableStateOf(true) }
    var speakerOn by remember { mutableStateOf(false) }
    var audioRoute by remember { mutableStateOf(AudioOutputRoute.EARPIECE) }
    var headsetAvailable by remember { mutableStateOf(false) }
    var peerId by remember { mutableStateOf("—") }
    var iceState by remember { mutableStateOf("—") }
    var pcState by remember { mutableStateOf("—") }
    var cameraDebug by remember { mutableStateOf("camera: idle") }

    var localRole by remember { mutableStateOf(ROLE_GUEST) }
    var lobbyWaiting by remember { mutableStateOf(false) }
    val peerMuteStates = remember { mutableStateMapOf<String, Boolean>() }
    val peerHandStates = remember { mutableStateMapOf<String, Boolean>() }
    val peerAudioEnabledStates = remember { mutableStateMapOf<String, Boolean>() }
    val peerVideoEnabledStates = remember { mutableStateMapOf<String, Boolean>() }
    var selfHandRaised by remember { mutableStateOf(false) }
    var muteAllEnabled by remember { mutableStateOf(false) }
    var selfMutedByModerator by remember { mutableStateOf(false) }
    val pendingLobbyState = remember { mutableStateListOf<LobbyPeerStatus>() }

    val pendingLobbyIds = pendingLobbyState.map { it.peerId }.toSet()

    val rootView = LocalView.current
    val keepScreenOn = inCallUi || connected || lobbyWaiting || connectingToRoom || reconnectMode
    DisposableEffect(rootView, keepScreenOn) {
        val previousValue = rootView.keepScreenOn
        rootView.keepScreenOn = keepScreenOn
        onDispose {
            if (rootView.keepScreenOn == keepScreenOn) {
                rootView.keepScreenOn = previousValue
            }
        }
    }

    LaunchedEffect(pendingLobbyIds) {
        dismissedModeratorBubblePeerIds =
            if (pendingLobbyIds.isEmpty()) {
                emptySet()
            } else {
                dismissedModeratorBubblePeerIds.intersect(pendingLobbyIds)
            }
    }

    val peersState = remember { mutableStateListOf<PeerStatus>() }
    var currentScreen by remember { mutableStateOf(RootScreen.HOME) }
    var callViewMode by remember { mutableStateOf(CallViewMode.FOCUS) }
    var availableUpdateVersion by rememberSaveable { mutableStateOf<String?>(null) }
    var updateNotificationDismissed by rememberSaveable { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var lastBackPressAt by remember { mutableStateOf(0L) }

    val parsedConnectLink = remember(connectLink) { parseInvitationToken(connectLink) }
    val connectLinkVersionMismatch = remember(connectLink) {
        invitationTokenRequiredVersionMismatch(connectLink)
    }
    val connectLinkError = connectLinkVersionMismatch?.let { requiredVersion ->
        tr(TextId.REQUIRED_CLIENT_VERSION, requiredVersion)
    }
    val parsedRelayEndpoint = remember(parsedConnectLink?.ip) {
        parsedConnectLink?.ip?.let(::parseRelayEndpoint)
    }
    val installedRelayTlsPin = parsedRelayEndpoint?.let { endpoint ->
        homeServers.firstNotNullOfOrNull { server ->
            val parsedServerEndpoint = parseRelayEndpoint(server.ip)
            val serverEndpoint = server.httpsPort?.let { port ->
                parsedServerEndpoint?.copy(port = port)
            } ?: parsedServerEndpoint
            server.relayTlsPin?.takeIf { serverEndpoint == endpoint }
        }
    }
    val invitationTlsTrust = remember(
        parsedConnectLink,
        parsedRelayEndpoint,
        installedRelayTlsPin,
        acceptedInvitationTlsPin,
        relayTlsPinTrustRevision
    ) {
        val payload = parsedConnectLink
        val endpoint = parsedRelayEndpoint
        if (payload == null || endpoint == null) {
            null
        } else {
            val savedPin = relayTlsPinTrustStore.get(endpoint) ?: installedRelayTlsPin
            val replacementAccepted = acceptedInvitationTlsPin?.let { accepted ->
                accepted.endpoint == endpoint && accepted.pin == payload.tlsPin
            } == true
            runCatching {
                assessInvitationTlsTrust(
                    endpoint = endpoint,
                    presentedPin = payload.tlsPin,
                    savedPin = savedPin,
                    changedPinAccepted = replacementAccepted
                )
            }.getOrNull()
        }
    }
    val selectedHomeCreateServer = homeServers.firstOrNull { it.id == selectedHomeCreateServerId }
    val selectedHomeInstallServer = homeServers.firstOrNull { it.id == selectedHomeInstallServerId }
    val selectedHomeMeetingServer = homeServers.firstOrNull { it.id == selectedHomeMeetingServerId }
    val selectedHomeMeetingRoom = selectedHomeMeetingServer?.openRooms?.firstOrNull { it.name == selectedHomeMeetingRoomName }

    fun loadHomeServersFromStore() {
        val loaded = runCatching { homeServersStore.load() }.getOrElse { return }
        homeServers.clear()
        homeServers.addAll(loaded)
    }

    fun persistHomeServers() {
        homeServersStore.save(homeServers)
        val redacted = homeServersStore.load()
        homeServers.clear()
        homeServers.addAll(redacted)
    }

    suspend fun <T> withAdminOperation(block: suspend () -> T): T {
        check(onAdminSessionAccess()) { tr(TextId.ACCESS_TO_PROTECTED_DATA_WAS_NOT_CONFIRMED) }
        return block()
    }

    fun Throwable.isSpkiPinMismatch(): Boolean =
        generateSequence(this) { it.cause }
            .any { it.message?.contains("SPKI pin mismatch", ignoreCase = true) == true }

    fun updateHomeServer(serverId: String, transform: (InstallServer) -> InstallServer) {
        val index = homeServers.indexOfFirst { it.id == serverId }
        if (index >= 0) {
            val redacted = homeServersStore.upsert(transform(homeServers[index]))
            homeServers.clear()
            homeServers.addAll(redacted)
        }
    }

    fun homeRelayVersionStateFor(actualVersion: String): RelayVersionState {
        return if (canonicalVersion(actualVersion) == canonicalVersion(expectedRelayVersion)) {
            RelayVersionState.CURRENT
        } else {
            RelayVersionState.OUTDATED
        }
    }

    fun copyHomeValue(label: String, value: String) {
        copySensitiveText(context, label, value)
        Toast.makeText(context, tr(TextId.COPIED), Toast.LENGTH_SHORT).show()
    }

    fun clearHomeAddServerForm() {
        homeAddIp = ""
        homeAddUser = ""
        homeAddPassword = ""
        homeAddError = null
        homeAddLoading = false
    }

    fun clearConnectForm() {
        connectLink = ""
        usernameInput = ""
        acceptedInvitationTlsPin = null
    }

    suspend fun syncHomeServer(server: InstallServer) {
        updateHomeServer(server.id) { current ->
            current.copy(relayVersionState = RelayVersionState.UNKNOWN)
        }

        val hasHttpsCredentials =
            !server.relayTlsPin.isNullOrBlank() && server.adminTokenAvailable
        val directVersionResult = if (hasHttpsCredentials) {
            runCatching {
                onFetchRelayVersion(server.ip, server.httpsPort, server.relayTlsPin, homeServersStore.readAdminToken(server.id))
            }
        } else {
            Result.failure(IllegalStateException("Relay credentials are not synchronized yet"))
        }

        if (directVersionResult.isSuccess) {
            val version = canonicalVersion(directVersionResult.getOrThrow().version)
            updateHomeServer(server.id) { current ->
                current.copy(
                    installed = true,
                    relayVersion = version,
                    relayVersionState = homeRelayVersionStateFor(version)
                )
            }

            runCatching {
                onFetchOpenRooms(server.ip, server.httpsPort, server.relayTlsPin, homeServersStore.readAdminToken(server.id))
            }.onSuccess { result ->
                updateHomeServer(server.id) { current ->
                    current.copy(
                        openRooms = result.openRooms,
                        sshHostKeyPin = current.sshHostKeyPin ?: result.observedSshHostKeyPin,
                        relayTlsPin = result.relayInfo?.pin ?: current.relayTlsPin,
                        httpsPort = result.relayInfo?.httpsPort ?: current.httpsPort,
                        adminToken = result.relayInfo?.adminToken ?: current.adminToken
                    )
                }
            }
            return
        }

        val probeResult = runCatching {
            onProbeServer(server.ip, server.username, server.password, server.sshHostKeyPin)
        }
        val probed = probeResult.getOrElse {
            updateHomeServer(server.id) { current ->
                current.copy(
                    relayVersionState = if (current.installed) {
                        RelayVersionState.UNKNOWN
                    } else {
                        RelayVersionState.NOT_INSTALLED
                    }
                )
            }
            return
        }

        if (probed.installationInProgress) {
            updateHomeServer(server.id) { current ->
                current.copy(
                    relayVersionState = RelayVersionState.UNKNOWN,
                    sshHostKeyPin = current.sshHostKeyPin ?: probed.observedSshHostKeyPin
                )
            }
            return
        }

        if (!probed.installed) {
            updateHomeServer(server.id) { current ->
                if (current.installed) {
                    current.copy(
                        installed = false,
                        relayVersionState = RelayVersionState.UNKNOWN,
                        sshHostKeyPin = current.sshHostKeyPin ?: probed.observedSshHostKeyPin
                    )
                } else {
                    current.copy(
                        installed = false,
                        openRooms = emptyList(),
                        relayTlsPin = null,
                        httpsPort = null,
                        adminToken = null,
                        deploymentProfile = null,
                        relayVersion = null,
                        relayVersionState = RelayVersionState.NOT_INSTALLED,
                        sshHostKeyPin = current.sshHostKeyPin ?: probed.observedSshHostKeyPin
                    )
                }
            }
            return
        }

        val relayInfo = probed.relayInfo
        updateHomeServer(server.id) { current ->
            current.copy(
                installed = true,
                openRooms = probed.openRooms,
                sshHostKeyPin = current.sshHostKeyPin ?: probed.observedSshHostKeyPin,
                relayTlsPin = relayInfo?.pin ?: current.relayTlsPin,
                httpsPort = relayInfo?.httpsPort ?: current.httpsPort,
                adminToken = relayInfo?.adminToken ?: current.adminToken,
                deploymentProfile = probed.deploymentProfile ?: current.deploymentProfile,
                relayCapacity = probed.deploymentProfile?.relayCapacity ?: current.relayCapacity,
                relayVersionState = RelayVersionState.UNKNOWN
            )
        }

        val discoveredPin = relayInfo?.pin ?: server.relayTlsPin
        val discoveredPort = relayInfo?.httpsPort ?: server.httpsPort
        val discoveredToken = relayInfo?.adminToken ?: homeServersStore.readAdminToken(server.id)
        if (discoveredPin.isNullOrBlank() || discoveredToken.isNullOrBlank()) return

        runCatching {
            onFetchRelayVersion(server.ip, discoveredPort, discoveredPin, discoveredToken)
        }.onSuccess { info ->
            updateHomeServer(server.id) { current ->
                current.copy(
                    installed = true,
                    relayVersion = canonicalVersion(info.version),
                    relayVersionState = homeRelayVersionStateFor(info.version)
                )
            }
        }

        runCatching {
            onFetchOpenRooms(server.ip, discoveredPort, discoveredPin, discoveredToken)
        }.onSuccess { result ->
            updateHomeServer(server.id) { current ->
                current.copy(
                    openRooms = result.openRooms,
                    relayTlsPin = result.relayInfo?.pin ?: current.relayTlsPin,
                    httpsPort = result.relayInfo?.httpsPort ?: current.httpsPort,
                    adminToken = result.relayInfo?.adminToken ?: current.adminToken
                )
            }
        }
    }

    fun refreshHomeServer(server: InstallServer) {
        homeScope.launch {
            runCatching { withAdminOperation { syncHomeServer(server) } }
                .onFailure {
                    Toast.makeText(
                        context,
                        trExternal(it.message ?: tr(TextId.COULD_NOT_REFRESH_THE_SERVER)),
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    fun checkHomeRelayVersion(server: InstallServer) {
        if (!server.installed ||
            server.relayTlsPin.isNullOrBlank() ||
            !server.adminTokenAvailable
        ) {
            return
        }

        val token = UUID.randomUUID().toString()
        homeVersionCheckTokens[server.id] = token
        val previousVersion = server.relayVersion
        val previousState = server.relayVersionState

        homeScope.launch {
            val result = runCatching {
                onFetchRelayVersion(
                    server.ip,
                    server.httpsPort,
                    server.relayTlsPin,
                    homeServersStore.readAdminToken(server.id)
                )
            }
            if (homeVersionCheckTokens[server.id] != token) return@launch
            homeVersionCheckTokens.remove(server.id)
            updateHomeServer(server.id) { current ->
                result.fold(
                    onSuccess = { info ->
                        val version = canonicalVersion(info.version)
                        current.copy(
                            installed = true,
                            relayVersion = version,
                            relayVersionState = homeRelayVersionStateFor(version)
                        )
                    },
                    onFailure = {
                        current.copy(
                            relayVersion = previousVersion,
                            relayVersionState = previousState
                        )
                    }
                )
            }
        }
    }

    fun checkHomeRelayVersions() {
        homeServers.toList().forEach(::checkHomeRelayVersion)
    }

    fun refreshHomeServers() {
        if (homeServersRefreshing) return
        homeServersRefreshing = true
        loadHomeServersFromStore()
        val snapshot = homeServers.toList()
        // A legacy password may be present only in memory long enough to replace it with a key.
        // Saving immediately strips every password from the encrypted store.
        persistHomeServers()
        if (snapshot.isEmpty()) {
            homeServersRefreshing = false
            return
        }
        val needsProtectedWork = snapshot.any { server ->
            server.sshKeyInstalled ||
                (!server.sshKeyInstalled &&
                    server.password.isNotBlank() &&
                    !server.sshHostKeyPin.isNullOrBlank())
        }
        if (!needsProtectedWork) {
            homeServersRefreshing = false
            return
        }
        homeScope.launch {
            try {
                withAdminOperation {
                    var migrationFailures = 0
                    val prepared = snapshot.map { server ->
                        val keyInstalled = if (
                            !server.sshKeyInstalled &&
                            server.password.isNotBlank() &&
                            !server.sshHostKeyPin.isNullOrBlank()
                        ) {
                            runCatching {
                                onEnrollSshKey(
                                    server.ip,
                                    server.username,
                                    server.password,
                                    server.sshHostKeyPin
                                )
                            }.isSuccess.also { if (!it) migrationFailures++ }
                        } else {
                            server.sshKeyInstalled
                        }
                        server.copy(password = "", sshKeyInstalled = keyInstalled)
                    }
                    prepared.forEach { preparedServer ->
                        val index = homeServers.indexOfFirst { it.id == preparedServer.id }
                        if (index >= 0) homeServers[index] = preparedServer
                    }
                    persistHomeServers()
                    if (migrationFailures > 0) {
                        Toast.makeText(
                            context,
                            tr(TextId.THE_OLD_PASSWORD_COULD_NOT_BE_REPLACED_WITH_AN_SSH_KEY_FOR_SOME_SERVERS_),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    prepared.filter { it.sshKeyInstalled }.forEach { server -> syncHomeServer(server) }
                }
            } catch (error: Exception) {
                Toast.makeText(
                    context,
                    trExternal(error.message ?: tr(TextId.COULD_NOT_REFRESH_THE_SERVERS)),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                homeServersRefreshing = false
            }
        }
    }

    fun startHomeAddServer() {
        if (homeAddLoading) return

        val ip = normalizeIpInput(homeAddIp)
        val user = homeAddUser.trim()

        if (ip.isBlank() || user.isBlank() || homeAddPassword.isBlank()) {
            homeAddError = tr(TextId.ENTER_THE_IP_ADDRESS_LOGIN_AND_PASSWORD)
            return
        }

        if (!isValidIpAddress(ip)) {
            homeAddError = tr(TextId.ENTER_A_VALID_IP_ADDRESS)
            return
        }

        if (homeServers.any { it.ip == ip }) {
            homeAddError = tr(TextId.THIS_SERVER_HAS_ALREADY_BEEN_ADDED)
            return
        }

        homeAddError = null
        homeAddLoading = true
        homeScope.launch {
            val observedPin = runCatching { onObserveSshHostKeyPin(ip) }.getOrElse {
                homeAddError = it.message ?: tr(TextId.COULD_NOT_RETRIEVE_THE_SERVER_SSH_KEY)
                homeAddLoading = false
                return@launch
            }

            pendingHomeSshHostKeyTrust = PendingHomeSshHostKeyTrust(
                ip = ip,
                username = user,
                password = homeAddPassword,
                observedPin = observedPin
            )
            homeAddLoading = false
        }
    }

    fun finishHomeAddServerAfterTrust(trust: PendingHomeSshHostKeyTrust) {
        if (homeAddLoading) return
        homeAddLoading = true
        homeAddError = null
        homeScope.launch {
            runCatching {
                withAdminOperation {
                    val keyEnrollment = runCatching {
                        onEnrollSshKey(trust.ip, trust.username, trust.password, trust.observedPin)
                    }
                    keyEnrollment.getOrElse {
                        homeAddError = it.message ?: tr(TextId.COULD_NOT_CONFIGURE_SSH_KEY_ACCESS)
                        homeAddLoading = false
                        pendingHomeSshHostKeyTrust = null
                        showHomeAddServerDialog = true
                        return@withAdminOperation
                    }

                    val probe = runCatching {
                        onProbeServer(trust.ip, trust.username, "", trust.observedPin)
                    }
                    val probed = probe.getOrElse {
                        homeAddError = it.message ?: tr(TextId.COULD_NOT_CONNECT_TO_THE_SERVER)
                        homeAddLoading = false
                        pendingHomeSshHostKeyTrust = null
                        showHomeAddServerDialog = true
                        return@withAdminOperation
                    }

                    val newServer = InstallServer(
                        id = UUID.randomUUID().toString(),
                        name = trust.ip,
                        ip = trust.ip,
                        username = trust.username,
                        password = "",
                        sshKeyInstalled = true,
                        installed = probed.installed,
                        sshHostKeyPin = trust.observedPin,
                        httpsPort = probed.relayInfo?.httpsPort,
                        relayTlsPin = probed.relayInfo?.pin,
                        adminToken = probed.relayInfo?.adminToken,
                        deploymentProfile = probed.deploymentProfile,
                        relayCapacity = probed.deploymentProfile?.relayCapacity
                            ?: RelayCapacityConfig.forPreset(RelayCapacityPreset.STANDARD),
                        openRooms = probed.openRooms,
                        relayVersionState = if (probed.installed) RelayVersionState.UNKNOWN else RelayVersionState.NOT_INSTALLED
                    )

                    homeServers.add(newServer)
                    persistHomeServers()
                    pendingHomeSshHostKeyTrust = null
                    showHomeAddServerDialog = false
                    clearHomeAddServerForm()
                    if (newServer.installed) syncHomeServer(newServer)
                }
            }.onFailure {
                homeAddError = it.message ?: tr(TextId.ACCESS_TO_PROTECTED_DATA_WAS_NOT_CONFIRMED)
                homeAddLoading = false
            }
        }
    }

    fun installRelayFromHome(server: InstallServer) {
        if (homeInstallLoading) return
        val updating = serverNeedsRelayUpdate(server)
        if (server.ip.isBlank() || server.username.isBlank() || !server.sshKeyInstalled) {
            homeInstallError = if (updating) {
                tr(TextId.A_CONFIGURED_SSH_KEY_IS_REQUIRED_FOR_UPDATING)
            } else {
                tr(TextId.A_CONFIGURED_SSH_KEY_IS_REQUIRED_FOR_INSTALLATION)
            }
            return
        }

        homeInstallError = null
        homeInstallLoading = true
        homeInstallLogs.clear()
        homeScope.launch {
            if (!onAdminSessionAccess()) {
                homeInstallError = tr(TextId.ACCESS_TO_PROTECTED_DATA_WAS_NOT_CONFIRMED)
                homeInstallLoading = false
                return@launch
            }

            var sshPin = server.sshHostKeyPin
            if (updating) {
                val removalResult = runCatching {
                    onRemoveRelay(
                        server.ip.trim(),
                        server.username.trim(),
                        server.password,
                        server.sshHostKeyPin,
                        homeServersStore.hydrateDeploymentProfile(server)
                    ) { line ->
                        withContext(Dispatchers.Main) { homeInstallLogs.add(line) }
                    }
                }
                val removal = removalResult.getOrElse {
                    homeInstallError = it.message ?: tr(TextId.COULD_NOT_REMOVE_THE_OLD_SYMPOSIUMRELAY_VERSION)
                    homeInstallLoading = false
                    return@launch
                }
                if (!removal.success) {
                    homeInstallError = tr(TextId.COULD_NOT_REMOVE_THE_OLD_SYMPOSIUMRELAY_VERSION)
                    homeInstallLoading = false
                    return@launch
                }
                sshPin = server.sshHostKeyPin ?: removal.observedSshHostKeyPin
            }

            val result = runCatching {
                onInstall(
                    server.ip.trim(),
                    server.username.trim(),
                    server.password,
                    sshPin,
                    homeServersStore.hydrateDeploymentProfile(server),
                    server.relayCapacity
                ) { line ->
                    withContext(Dispatchers.Main) { homeInstallLogs.add(line) }
                }
            }

            val installed = result.getOrElse {
                homeInstallError = it.message ?: if (updating) {
                    tr(TextId.COULD_NOT_INSTALL_THE_NEW_SYMPOSIUMRELAY_VERSION)
                } else {
                    tr(TextId.COULD_NOT_INSTALL_SYMPOSIUMRELAY)
                }
                homeInstallLoading = false
                return@launch
            }

            if (!installed.success) {
                homeInstallError = tr(
                    if (updating) TextId.UPDATE_FAILED_CODE else TextId.INSTALLATION_FAILED_CODE,
                    installed.exitStatus
                )
                homeInstallLoading = false
                return@launch
            }

            val finalRelayPin = installed.relayInfo?.pin ?: server.relayTlsPin
            val finalAdminToken = installed.relayInfo?.adminToken ?: homeServersStore.readAdminToken(server.id)

            updateHomeServer(server.id) { current ->
                current.copy(
                    installed = true,
                    sshHostKeyPin = current.sshHostKeyPin ?: installed.observedSshHostKeyPin,
                    relayTlsPin = finalRelayPin ?: current.relayTlsPin,
                    httpsPort = installed.relayInfo?.httpsPort ?: current.httpsPort,
                    adminToken = finalAdminToken ?: current.adminToken,
                    deploymentProfile = installed.deploymentProfile ?: current.deploymentProfile,
                    relayCapacity = installed.deploymentProfile?.relayCapacity ?: current.relayCapacity,
                    openRooms = installed.openRooms,
                    relayVersion = expectedRelayVersion,
                    relayVersionState = RelayVersionState.CURRENT
                )
            }

            if (finalRelayPin.isNullOrBlank() || finalAdminToken.isNullOrBlank()) {
                homeInstallError = tr(TextId.RELAY_WAS_INSTALLED_BUT_THE_APP_DID_NOT_RECEIVE_A_TLS_PIN_OR_ADMIN_TOKEN)
                homeInstallLoading = false
                return@launch
            }

            homeInstallLoading = false
            homeInstallError = null
            homeInstallLogs.clear()
            selectedHomeInstallServerId = null
            if (homeInstallReturnToCreateRoom) {
                selectedHomeCreateServerId = server.id
                homeCreateRoomName = ""
                homeRoomError = null
            }
            homeInstallReturnToCreateRoom = false
            Toast.makeText(
                context,
                tr(if (updating) TextId.SYMPOSIUMRELAY_UPDATED else TextId.SYMPOSIUMRELAY_INSTALLED),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun createHomeRoom(server: InstallServer) {
        if (homeRoomLoading) return
        val activeServer = runCatching { homeServersStore.load() }
            .getOrNull()
            ?.firstOrNull { it.id == server.id }
            ?: server
        val activeServerIndex = homeServers.indexOfFirst { it.id == activeServer.id }
        if (activeServerIndex >= 0) homeServers[activeServerIndex] = activeServer
        val roomName = homeCreateRoomName.trim()
        if (roomName.isBlank()) {
            homeRoomError = tr(TextId.ENTER_A_ROOM_NAME)
            return
        }
        if (activeServer.openRooms.any { it.name.equals(roomName, ignoreCase = true) }) {
            homeRoomError = tr(TextId.A_ROOM_WITH_THIS_NAME_IS_ALREADY_OPEN)
            return
        }
        if (!activeServer.installed || activeServer.relayTlsPin.isNullOrBlank() || !activeServer.adminTokenAvailable) {
            homeRoomError = tr(TextId.A_MEETING_CANNOT_BE_CREATED_ON_THIS_SERVER_BECAUSE_SYMPOSIUMRELAY_IS_NOT)
            return
        }
        if (!serverAllowsRoomCreation(activeServer)) {
            homeRoomError = if (serverNeedsRelayUpdate(activeServer)) {
                tr(TextId.UPDATE_SELECTED_RELAY_FIRST)
            } else {
                tr(TextId.RELAY_VERSION_COULD_NOT_BE_VERIFIED)
            }
            return
        }

        homeRoomError = null
        homeRoomLoading = true
        homeScope.launch {
            var roomServer = activeServer
            val result = runCatching {
                withAdminOperation {
                    try {
                        onCreateRoom(
                            roomServer.ip,
                            roomServer.httpsPort,
                            roomServer.relayTlsPin,
                            homeServersStore.readAdminToken(roomServer.id),
                            roomName,
                            false,
                            false
                        )
                    } catch (error: Exception) {
                        if (!error.isSpkiPinMismatch() || !roomServer.sshKeyInstalled) throw error

                        val probed = onProbeServer(
                            roomServer.ip,
                            roomServer.username,
                            roomServer.password,
                            roomServer.sshHostKeyPin
                        )
                        val relayInfo = probed.relayInfo ?: throw error
                        roomServer = roomServer.copy(
                            installed = probed.installed,
                            sshHostKeyPin = roomServer.sshHostKeyPin ?: probed.observedSshHostKeyPin,
                            relayTlsPin = relayInfo.pin,
                            httpsPort = relayInfo.httpsPort,
                            adminToken = relayInfo.adminToken,
                            deploymentProfile = probed.deploymentProfile ?: roomServer.deploymentProfile,
                            relayCapacity = probed.deploymentProfile?.relayCapacity ?: roomServer.relayCapacity,
                            openRooms = probed.openRooms
                        )
                        updateHomeServer(roomServer.id) { roomServer }

                        onCreateRoom(
                            roomServer.ip,
                            roomServer.httpsPort,
                            roomServer.relayTlsPin,
                            homeServersStore.readAdminToken(roomServer.id),
                            roomName,
                            false,
                            false
                        )
                    }
                }
            }

            val roomResult = result.getOrElse {
                homeRoomError = it.message ?: tr(TextId.COULD_NOT_CREATE_THE_MEETING)
                homeRoomLoading = false
                return@launch
            }

            updateHomeServer(roomServer.id) { current ->
                current.copy(
                    openRooms = roomResult.openRooms,
                    sshHostKeyPin = current.sshHostKeyPin ?: roomResult.observedSshHostKeyPin,
                    relayTlsPin = roomResult.relayInfo?.pin ?: current.relayTlsPin,
                    httpsPort = roomResult.relayInfo?.httpsPort ?: current.httpsPort,
                    adminToken = roomResult.relayInfo?.adminToken ?: current.adminToken
                )
            }

            roomResult.openRooms.firstOrNull { it.name == roomName }?.let { createdRoom ->
                conferenceE2eeSecrets.rotate(
                    roomServer.ip,
                    roomServer.httpsPort,
                    createdRoom.name,
                    createdRoom.roomInstance
                )
            }

            homeRoomLoading = false
            showCreateMeetingDialog = false
            selectedHomeCreateServerId = null
            homeCreateRoomName = ""
            homeRoomError = null
        }
    }

    fun saveHomeRoomSettings(server: InstallServer, room: OpenRoomInfo) {
        if (homeRoomLoading) return
        homeRoomLoading = true
        homeScope.launch {
            val result = runCatching {
                onSetRoomSettings(
                    server.ip,
                    server.httpsPort,
                    server.relayTlsPin,
                    homeServersStore.readAdminToken(server.id),
                    room.name,
                    homeEditMuteAllByDefault,
                    homeEditAdmitWithoutApproval
                )
            }
            val roomResult = result.getOrElse {
                Toast.makeText(
                    context,
                    trExternal(it.message ?: tr(TextId.COULD_NOT_SAVE_THE_ROOM_SETTINGS)),
                    Toast.LENGTH_SHORT
                ).show()
                homeRoomLoading = false
                return@launch
            }
            updateHomeServer(server.id) { current ->
                current.copy(openRooms = roomResult.openRooms)
            }
            homeRoomLoading = false
            Toast.makeText(context, tr(TextId.ROOM_SETTINGS_SAVED), Toast.LENGTH_SHORT).show()
        }
    }

    fun closeHomeMeeting(server: InstallServer, room: OpenRoomInfo) {
        if (homeRoomLoading) return
        homeRoomLoading = true
        homeScope.launch {
            val result = runCatching {
                onSetRoomOpenState(server.ip, server.httpsPort, server.relayTlsPin, homeServersStore.readAdminToken(server.id), room.name, false)
            }

            val roomResult = result.getOrElse {
                Toast.makeText(
                    context,
                    trExternal(it.message ?: tr(TextId.COULD_NOT_CLOSE_THE_ROOM)),
                    Toast.LENGTH_SHORT
                ).show()
                homeRoomLoading = false
                return@launch
            }

            updateHomeServer(server.id) { current ->
                current.copy(
                    openRooms = roomResult.openRooms,
                    sshHostKeyPin = current.sshHostKeyPin ?: roomResult.observedSshHostKeyPin,
                    relayTlsPin = roomResult.relayInfo?.pin ?: current.relayTlsPin,
                    httpsPort = roomResult.relayInfo?.httpsPort ?: current.httpsPort,
                    adminToken = roomResult.relayInfo?.adminToken ?: current.adminToken
                )
            }

            conferenceE2eeSecrets.remove(server.ip, server.httpsPort, room.name, room.roomInstance)

            homeRoomLoading = false
            selectedHomeMeetingServerId = null
            selectedHomeMeetingRoomName = null
            confirmHomeCloseMeeting = false
        }
    }

    fun rotateHomeModeratorLink(server: InstallServer, room: OpenRoomInfo) {
        if (homeRoomLoading) return
        homeRoomLoading = true
        homeScope.launch {
            val result = runCatching {
                onRotateModeratorKey(
                    server.ip,
                    server.httpsPort,
                    server.relayTlsPin,
                    homeServersStore.readAdminToken(server.id),
                    room.name
                )
            }

            val roomResult = result.getOrElse {
                Toast.makeText(
                    context,
                    trExternal(it.message ?: tr(TextId.COULD_NOT_REFRESH_THE_MODERATOR_LINK)),
                    Toast.LENGTH_SHORT
                ).show()
                homeRoomLoading = false
                confirmHomeModeratorLinkRotation = false
                return@launch
            }

            updateHomeServer(server.id) { current ->
                current.copy(
                    openRooms = roomResult.openRooms,
                    relayTlsPin = roomResult.relayInfo?.pin ?: current.relayTlsPin,
                    httpsPort = roomResult.relayInfo?.httpsPort ?: current.httpsPort,
                    adminToken = roomResult.relayInfo?.adminToken ?: current.adminToken
                )
            }

            homeRoomLoading = false
            confirmHomeModeratorLinkRotation = false
            Toast.makeText(context, tr(TextId.MODERATOR_LINK_REFRESHED), Toast.LENGTH_SHORT).show()
        }
    }

    fun rotateHomeE2eeKey(server: InstallServer, room: OpenRoomInfo) {
        runCatching {
            conferenceE2eeSecrets.rotate(
                server.ip,
                server.httpsPort,
                room.name,
                room.roomInstance
            )
        }.onSuccess {
            confirmHomeE2eeKeyRotation = false
            Toast.makeText(context, tr(TextId.E2EE_KEY_REFRESHED), Toast.LENGTH_SHORT).show()
        }.onFailure {
            confirmHomeE2eeKeyRotation = false
            Toast.makeText(context, trExternal(it.message ?: tr(TextId.COULD_NOT_CREATE_THE_MEETING)), Toast.LENGTH_SHORT).show()
        }
    }

    fun homeGuestLink(server: InstallServer, room: OpenRoomInfo): String? {
        val pin = server.relayTlsPin?.trim()
        val requiredVersion = verifiedRoomRequiredVersion(room)
        if (pin.isNullOrBlank() || requiredVersion == null || !isValidRoomInstance(room.roomInstance)) return null
        val e2eeSecret = conferenceE2eeSecrets.getOrCreate(server.ip, server.httpsPort, room.name, room.roomInstance)
        return buildInvitationToken(
            server.ip,
            server.httpsPort,
            room.name,
            room.roomInstance,
            pin,
            e2eeSecret,
            requiredVersion = requiredVersion
        )
    }

    fun homeGuestQrLink(server: InstallServer, room: OpenRoomInfo): String? {
        val pin = server.relayTlsPin?.trim()
        val requiredVersion = verifiedRoomRequiredVersion(room)
        if (pin.isNullOrBlank() || requiredVersion == null || !isValidRoomInstance(room.roomInstance)) return null
        val e2eeSecret = conferenceE2eeSecrets.getOrCreate(server.ip, server.httpsPort, room.name, room.roomInstance)
        return buildInvitationToken(
            server.ip,
            server.httpsPort,
            room.name,
            room.roomInstance,
            pin,
            e2eeSecret,
            requiredVersion = requiredVersion
        )
    }

    fun homeModeratorLink(server: InstallServer, room: OpenRoomInfo): String? {
        val pin = server.relayTlsPin?.trim()
        val modKey = homeServersStore.readModeratorKey(server.id, room).orEmpty().trim()
        val requiredVersion = verifiedRoomRequiredVersion(room)
        if (pin.isNullOrBlank() || modKey.isBlank() || requiredVersion == null || !isValidRoomInstance(room.roomInstance)) return null
        val e2eeSecret = conferenceE2eeSecrets.getOrCreate(server.ip, server.httpsPort, room.name, room.roomInstance)
        return buildInvitationToken(
            server.ip,
            server.httpsPort,
            room.name,
            room.roomInstance,
            pin,
            e2eeSecret,
            requiredVersion = requiredVersion,
            moderatorKey = modKey
        )
    }

    fun showMissingLinkDataError(
        server: InstallServer,
        room: OpenRoomInfo,
        requireModeratorKey: Boolean = false
    ) {
        val message = when {
            !isValidRoomInstance(room.roomInstance) -> tr(TextId.REFRESH_ROOM_LIST)
            verifiedRoomRequiredVersion(room) == null ->
                tr(TextId.THE_SERVER_DID_NOT_CONFIRM_THE_ROOM_VERSION_REINSTALL_SYMPOSIUMRELAY)
            server.relayTlsPin.isNullOrBlank() -> tr(TextId.TLS_PIN_IS_MISSING)
            requireModeratorKey && !room.moderatorKeyAvailable -> tr(TextId.MODERATOR_KEY_IS_MISSING)
            else -> tr(TextId.UNABLE_TO_CREATE_THE_ROOM_LINK)
        }
        Toast.makeText(context, trExternal(message), Toast.LENGTH_LONG).show()
    }

    fun cancelPendingRoomConnection() {
        pendingInvitationTrust = null
        acceptedInvitationTlsPin = null
        connectingToRoom = false
        connectingRoomName = ""
        inCallUi = false
        showConnectDialog = true
        if (reconnectMode) {
            onCancelReconnect()
        } else {
            onDisconnect()
        }
    }

    LaunchedEffect(initialAccessibilityFontScale) {
        accessibilityFontScale = initialAccessibilityFontScale
    }

    LaunchedEffect(appVersion) {
        availableUpdateVersion = checkForAppUpdate(appVersion)
    }

    if (secretProtectionChoicePromptVisible) {
        InitialSecretProtectionDialog(
            onEnable = { onInitialSecretProtectionChoice(true) },
            onDecline = { onInitialSecretProtectionChoice(false) }
        )
    } else if (welcomeNewsPromptVisible) {
        WelcomeNewsDialog(
            onDismiss = onWelcomeNewsPromptDismiss,
            onOpenSite = {
                onWelcomeNewsPromptDismiss()
                openExternalIntent(
                    context,
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://symposium-dp.app"))
                )
            },
            onOpenTelegram = {
                onWelcomeNewsPromptDismiss()
                openExternalIntent(
                    context,
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/symposium_app"))
                )
            }
        )
    }

    availableUpdateVersion
        ?.takeIf {
            !secretProtectionChoicePromptVisible &&
                !welcomeNewsPromptVisible &&
                !updateNotificationDismissed
        }
        ?.let { remoteVersion ->
            UpdateAvailableDialog(
                currentVersion = appVersion,
                availableVersion = remoteVersion,
                onDismiss = { updateNotificationDismissed = true },
                onOpenSite = {
                    updateNotificationDismissed = true
                    openExternalIntent(
                        context,
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://symposium-dp.app"))
                    )
                },
                onOpenTelegram = {
                    updateNotificationDismissed = true
                    openExternalIntent(
                        context,
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/symposium_app"))
                    )
                }
            )
        }

    BackHandler {
        when {
            moderatorPanelOpen -> moderatorPanelOpen = false
            inCallUi && callViewMode == CallViewMode.FULLSCREEN -> callViewMode = CallViewMode.FOCUS
            inCallUi && connected -> {
                inCallUi = false
                minimizedCallPreviewVisible = true
            }
            connectingToRoom -> cancelPendingRoomConnection()
            showConnectDialog -> {
                showConnectDialog = false
                clearConnectForm()
            }
            currentScreen == RootScreen.MENU -> currentScreen = RootScreen.HOME
            currentScreen != RootScreen.HOME -> currentScreen = RootScreen.MENU
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressAt < 1800) {
                    (context as? Activity)?.finish()
                } else {
                    lastBackPressAt = now
                    Toast.makeText(context, tr(TextId.TAP_AGAIN_TO_EXIT), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val latestReconnectMode by rememberUpdatedState(reconnectMode)
    val latestPendingInvitationTrust by rememberUpdatedState(pendingInvitationTrust)
    val uiStateBinder = remember {
        object : UiBinder {
            override fun setStatus(s: String) { mainHandler.post { status = s } }
            override fun setConnected(on: Boolean) {
                mainHandler.post {
                    val shouldOpenCall = on && !connected && (
                        connectingToRoom ||
                            lobbyWaiting ||
                            latestReconnectMode ||
                            connectingRoomName.isNotBlank()
                        )
                    connected = on
                    if (on) {
                        if (shouldOpenCall) {
                            lobbyWaiting = false
                            inCallUi = true
                            minimizedCallPreviewVisible = true
                        }
                        connectingToRoom = false
                        connectingRoomName = ""
                        clearConnectForm()
                    }
                }
            }
            override fun setMic(on: Boolean) { mainHandler.post { micEnabled = on } }
            override fun setVideo(on: Boolean) { mainHandler.post { videoEnabled = on } }
            override fun setOutput(on: Boolean) { mainHandler.post { outputOn = on } }
            override fun setSpeaker(on: Boolean) { mainHandler.post { speakerOn = on } }
            override fun setAudioRoute(route: String, headsetAvailableValue: Boolean) {
                mainHandler.post {
                    val parsedRoute = audioOutputRouteFromRaw(route)
                    audioRoute = parsedRoute
                    headsetAvailable = headsetAvailableValue
                    speakerOn = parsedRoute == AudioOutputRoute.SPEAKER
                }
            }
            override fun setPeerId(id: String) { mainHandler.post { peerId = id } }
            override fun setIceState(s: String) { mainHandler.post { iceState = s } }
            override fun setPcState(s: String) { mainHandler.post { pcState = s } }
            override fun setCameraDebug(s: String) { mainHandler.post { cameraDebug = s } }
            override fun setPeerAudioEnabled(peerId: String, enabled: Boolean) {
                mainHandler.post {
                    val id = peerId.trim()
                    if (id.isNotBlank()) {
                        peerAudioEnabledStates[id] = enabled
                    }
                }
            }
            override fun setPeerVideoEnabled(peerId: String, enabled: Boolean) {
                mainHandler.post {
                    val id = peerId.trim()
                    if (id.isNotBlank()) {
                        peerVideoEnabledStates[id] = enabled
                    }
                }
            }
            override fun updatePeers(peers: List<PeerStatus>) {
                mainHandler.post {
                    peersState.clear()
                    peersState.addAll(peers)

                    val liveIds = peers.map { it.peerId }.toSet()
                    peerHandStates.keys.toList().forEach { id ->
                        if (id !in liveIds && id != peerId) peerHandStates.remove(id)
                    }
                    peerMuteStates.keys.toList().forEach { id ->
                        if (id !in liveIds && id != peerId) peerMuteStates.remove(id)
                    }
                    peerAudioEnabledStates.keys.toList().forEach { id ->
                        if (id !in liveIds && id != peerId) peerAudioEnabledStates.remove(id)
                    }
                    peerVideoEnabledStates.keys.toList().forEach { id ->
                        if (id !in liveIds && id != peerId) peerVideoEnabledStates.remove(id)
                    }
                }
            }
            override fun setRole(role: String) {
                mainHandler.post { localRole = role.ifBlank { ROLE_GUEST } }
            }
            override fun setLobbyWaiting(waiting: Boolean) {
                mainHandler.post {
                    lobbyWaiting = waiting
                    if (waiting) {
                        connectingToRoom = false
                        connectingRoomName = ""
                        clearConnectForm()
                        inCallUi = false
                        connected = false
                    }
                }
            }
            override fun onRelayConnectionAuthenticated() {
                val authenticated = latestPendingInvitationTrust ?: return
                mainHandler.post {
                    runCatching {
                        conferenceE2eeSecrets.save(
                            authenticated.endpoint.host,
                            authenticated.endpoint.port,
                            authenticated.room,
                            authenticated.roomInstance,
                            authenticated.e2eeSecret
                        )
                        relayTlsPinTrustStore.save(authenticated.endpoint, authenticated.tlsPin)
                    }.onSuccess {
                        if (pendingInvitationTrust == authenticated) {
                            pendingInvitationTrust = null
                        }
                        if (
                            acceptedInvitationTlsPin?.endpoint == authenticated.endpoint &&
                            acceptedInvitationTlsPin?.pin == authenticated.tlsPin
                        ) {
                            acceptedInvitationTlsPin = null
                        }
                        relayTlsPinTrustRevision += 1
                    }.onFailure { error ->
                        Toast.makeText(
                            context,
                            tr(TextId.SAVE_TRUST_DATA_FAILED, error.message.orEmpty()),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            override fun updateLobbyPending(peers: List<LobbyPeerStatus>) {
                mainHandler.post {
                    pendingLobbyState.clear()
                    pendingLobbyState.addAll(peers)
                }
            }
            override fun setMuteAll(enabled: Boolean) {
                mainHandler.post {
                    val wasEnabled = muteAllEnabled
                    muteAllEnabled = enabled

                    if (enabled && !wasEnabled) {
                        peerMuteStates.clear()
                    }
                }
            }
            override fun setForcedMute(on: Boolean) {
                mainHandler.post {
                    selfMutedByModerator = on
                    if (on) micEnabled = false
                    val id = peerId.trim()
                    if (id.isNotBlank() && id != "—") {
                        peerMuteStates[id] = on
                    }
                }
            }
            override fun setPeerMuted(peerId: String, muted: Boolean) {
                mainHandler.post {
                    val id = peerId.trim()
                    if (id.isNotBlank()) {
                        peerMuteStates[id] = muted
                    }
                }
            }
            override fun setSelfHandRaised(on: Boolean) {
                mainHandler.post {
                    selfHandRaised = on
                    val id = peerId.trim()
                    if (id.isNotBlank() && id != "—") {
                        peerHandStates[id] = on
                    }
                }
            }
            override fun setPeerHandRaised(targetPeerId: String, raised: Boolean) {
                mainHandler.post {
                    val id = targetPeerId.trim()
                    if (id.isNotBlank()) {
                        peerHandStates[id] = raised
                        if (id == peerId) selfHandRaised = raised
                    }
                }
            }
        }
    }

    LaunchedEffect(peerId, selfHandRaised) {
        val id = peerId.trim()
        if (id.isNotBlank() && id != "—") {
            peerHandStates[id] = selfHandRaised
        }
    }

    LaunchedEffect(peerId, selfMutedByModerator) {
        val id = peerId.trim()
        if (id.isNotBlank() && id != "—") {
            peerMuteStates[id] = selfMutedByModerator
        }
    }

    LaunchedEffect(peerId, videoEnabled) {
        val id = peerId.trim()
        if (id.isNotBlank() && id != "—") {
            peerVideoEnabledStates[id] = videoEnabled
        }
    }

    LaunchedEffect(pictureInPictureMode) {
        if (pictureInPictureMode) {
            moderatorPanelOpen = false
            wasInPictureInPictureMode = true
        } else if (wasInPictureInPictureMode) {
            wasInPictureInPictureMode = false
            if (connected) {
                currentScreen = RootScreen.HOME
                inCallUi = true
            }
        }
    }

    LaunchedEffect(connected, reconnectMode, connectingToRoom, showConnectDialog) {
        if (connected || (reconnectMode && !connectingToRoom && !showConnectDialog)) {
            lobbyWaiting = false
            inCallUi = true
            minimizedCallPreviewVisible = true
        }
        if (!connected && !reconnectMode && !lobbyWaiting && !connectingToRoom) {
            inCallUi = false
            pinnedPeerId = null
            moderatorPanelOpen = false
            callViewMode = CallViewMode.FOCUS
            pipOffsetX = 0f
            pipOffsetY = 0f
            pipExpanded = false
            minimizedCallPreviewVisible = true
            localRole = ROLE_GUEST
            selfMutedByModerator = false
            selfHandRaised = false
            muteAllEnabled = false
            pendingLobbyState.clear()
            peerMuteStates.clear()
            peerHandStates.clear()
            peerAudioEnabledStates.clear()
            peerVideoEnabledStates.clear()
        }
    }

    LaunchedEffect(videoEnabled) {
        if (!videoEnabled) {
            pipExpanded = false
        }
    }

    LaunchedEffect(uiStateBinder) { onRequestBind(uiStateBinder) }

    LaunchedEffect(currentScreen) {
        if (currentScreen == RootScreen.HOME) {
            loadHomeServersFromStore()
            checkHomeRelayVersions()
        }
    }

    LaunchedEffect(showCreateMeetingDialog) {
        if (showCreateMeetingDialog) checkHomeRelayVersions()
    }

    LaunchedEffect(selectedHomeMeetingServerId, selectedHomeMeetingRoomName) {
        val server = selectedHomeMeetingServer ?: return@LaunchedEffect
        if (selectedHomeMeetingRoomName != null) checkHomeRelayVersion(server)
    }

    val lifecycleOwner = LocalView.current.findViewTreeLifecycleOwner()
    DisposableEffect(lifecycleOwner, currentScreen) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && currentScreen == RootScreen.HOME) {
                loadHomeServersFromStore()
                checkHomeRelayVersions()
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
        }
    }

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = platformDensity.density,
            fontScale = platformDensity.fontScale * accessibilityFontScale.multiplier
        ),
        LocalAccessibilityFontScale provides accessibilityFontScale
    ) {
        if (showCreateMeetingDialog) {
            HomeCreateMeetingDialog(
                servers = homeServers,
                loading = homeServersRefreshing || homeVersionsChecking,
                onDismiss = { showCreateMeetingDialog = false },
                onAddServer = {
                    homeAddError = null
                    showHomeAddServerDialog = true
                },
                onOpenServerGuide = { showHomeServerGuideDialog = true },
                onRefresh = { refreshHomeServers() },
                onServerClick = { server ->
                    homeCreateRoomName = ""
                    homeRoomError = null
                    when {
                        canCreateMeetingOnServer(server) -> {
                            selectedHomeCreateServerId = server.id
                        }
                        server.installed &&
                            server.relayVersionState == RelayVersionState.UNKNOWN &&
                            !server.relayTlsPin.isNullOrBlank() &&
                            server.adminTokenAvailable -> {
                            checkHomeRelayVersion(server)
                        }
                        else -> {
                            homeInstallError = null
                            homeInstallLogs.clear()
                            homeInstallReturnToCreateRoom = true
                            selectedHomeInstallServerId = server.id
                        }
                    }
                },
                onUpdateServer = { server ->
                    homeInstallError = null
                    homeInstallLogs.clear()
                    homeInstallReturnToCreateRoom = true
                    selectedHomeInstallServerId = server.id
                }
            )
        }

        if (showHomeServerGuideDialog) {
            HomeServerGuideDialog(
                onDismiss = { showHomeServerGuideDialog = false }
            )
        }

        if (showHomeAddServerDialog) {
            HomeAddServerDialog(
                ip = homeAddIp,
                username = homeAddUser,
                password = homeAddPassword,
                error = homeAddError,
                loading = homeAddLoading,
                onIpChange = { homeAddIp = it },
                onUsernameChange = { homeAddUser = it },
                onPasswordChange = { homeAddPassword = it },
                onDismiss = {
                    showHomeAddServerDialog = false
                    clearHomeAddServerForm()
                },
                onAdd = { startHomeAddServer() }
            )
        }

        pendingHomeSshHostKeyTrust?.let { trust ->
            HomeSshHostKeyTrustDialog(
                trust = trust,
                loading = homeAddLoading,
                onCopy = { copyHomeValue("ssh_host_key_pin", trust.observedPin) },
                onConfirm = { finishHomeAddServerAfterTrust(trust) },
                onDismiss = {
                    pendingHomeSshHostKeyTrust = null
                    homeAddLoading = false
                }
            )
        }

        selectedHomeInstallServer?.let { server ->
            HomeInstallRelayDialog(
                server = server,
                logs = homeInstallLogs,
                error = homeInstallError,
                loading = homeInstallLoading,
                onDismiss = {
                    if (!homeInstallLoading) {
                        val returnToCreate = homeInstallReturnToCreateRoom
                        selectedHomeInstallServerId = null
                        homeInstallError = null
                        homeInstallLogs.clear()
                        homeInstallReturnToCreateRoom = false
                        if (returnToCreate) showCreateMeetingDialog = true
                    }
                },
                onCapacityChange = { capacity ->
                    updateHomeServer(server.id) { current -> current.copy(relayCapacity = capacity) }
                },
                onInstall = { installRelayFromHome(server) }
            )
        }

        selectedHomeCreateServer?.let { server ->
            HomeCreateRoomDialog(
                server = server,
                roomName = homeCreateRoomName,
                error = homeRoomError,
                loading = homeRoomLoading,
                onRoomNameChange = { homeCreateRoomName = it },
                onDismiss = {
                    if (!homeRoomLoading) {
                        selectedHomeCreateServerId = null
                        homeCreateRoomName = ""
                        homeRoomError = null
                    }
                },
                onCreate = { createHomeRoom(server) }
            )
        }

        if (selectedHomeMeetingServer != null && selectedHomeMeetingRoom != null) {
            val server = selectedHomeMeetingServer
            val room = selectedHomeMeetingRoom
            HomeMeetingDialog(
                server = server,
                room = room,
                loading = homeRoomLoading,
                muteAllByDefault = homeEditMuteAllByDefault,
                admitWithoutApproval = homeEditAdmitWithoutApproval,
                onMuteAllByDefaultChange = { homeEditMuteAllByDefault = it },
                onAdmitWithoutApprovalChange = { homeEditAdmitWithoutApproval = it },
                onSaveSettings = { saveHomeRoomSettings(server, room) },
                onCopyGuest = {
                    val link = homeGuestLink(server, room)
                    if (link == null) {
                        showMissingLinkDataError(server, room)
                    } else {
                        copyHomeValue("guest_room_link", link)
                    }
                },
                onQrGuest = {
                    val link = homeGuestQrLink(server, room)
                    if (link == null) {
                        showMissingLinkDataError(server, room)
                    } else {
                        homeQrDialogTitle = tr(TextId.GUEST_QR_CODE)
                        homeQrDialogLink = link
                    }
                },
                onCopyModerator = {
                    val link = homeModeratorLink(server, room)
                    if (link == null) {
                        showMissingLinkDataError(server, room, requireModeratorKey = true)
                    } else {
                        copyHomeValue("moderator_room_link", link)
                    }
                },
                onQrModerator = {
                    val link = homeModeratorLink(server, room)
                    if (link == null) {
                        showMissingLinkDataError(server, room, requireModeratorKey = true)
                    } else {
                        homeQrDialogTitle = tr(TextId.MODERATOR_QR_CODE)
                        homeQrDialogLink = link
                    }
                },
                onRotateModeratorLink = { confirmHomeModeratorLinkRotation = true },
                onRotateE2eeKey = { confirmHomeE2eeKeyRotation = true },
                onCloseMeeting = { confirmHomeCloseMeeting = true },
                onDismiss = {
                    selectedHomeMeetingServerId = null
                    selectedHomeMeetingRoomName = null
                    confirmHomeCloseMeeting = false
                    confirmHomeModeratorLinkRotation = false
                    confirmHomeE2eeKeyRotation = false
                }
            )
        }

        if (confirmHomeCloseMeeting && selectedHomeMeetingServer != null && selectedHomeMeetingRoom != null) {
            val server = selectedHomeMeetingServer
            val room = selectedHomeMeetingRoom
            AppDialog(
                title = tr(TextId.CLOSE_THE_ROOM),
                onDismiss = { confirmHomeCloseMeeting = false },
                dismissEnabled = !homeRoomLoading,
                content = {
                    Text(
                        text = tr(
                            TextId.CLOSE_ROOM_CONFIRMATION,
                            room.name,
                            serverDisplayAddress(server)
                        ),
                        color = appTextPrimaryColor(),
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    )
                },
                actions = {
                    ActionButton(
                        label = tr(TextId.CLOSE_ROOM),
                        onClick = {
                            confirmHomeCloseMeeting = false
                            closeHomeMeeting(server, room)
                        },
                        kind = ActionButtonKind.DANGER,
                        enabled = !homeRoomLoading,
                        loading = homeRoomLoading,
                        modifier = Modifier.weight(1f)
                    )
                }
            )
        }

        if (confirmHomeModeratorLinkRotation && selectedHomeMeetingServer != null && selectedHomeMeetingRoom != null) {
            val server = selectedHomeMeetingServer
            val room = selectedHomeMeetingRoom
            AppDialog(
                title = tr(TextId.REFRESH_MODERATOR_LINK_2),
                onDismiss = { confirmHomeModeratorLinkRotation = false },
                dismissEnabled = !homeRoomLoading,
                content = {
                    Text(
                        text = tr(TextId.ROTATE_MODERATOR_LINK_CONFIRMATION, room.name),
                        color = appTextPrimaryColor(),
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    )
                },
                actions = {
                    ActionButton(
                        label = tr(TextId.REFRESH_LINK),
                        onClick = { rotateHomeModeratorLink(server, room) },
                        kind = ActionButtonKind.DANGER,
                        enabled = !homeRoomLoading,
                        loading = homeRoomLoading,
                        modifier = Modifier.weight(1f)
                    )
                }
            )
        }

        if (confirmHomeE2eeKeyRotation && selectedHomeMeetingServer != null && selectedHomeMeetingRoom != null) {
            val server = selectedHomeMeetingServer
            val room = selectedHomeMeetingRoom
            AppDialog(
                title = tr(TextId.REFRESH_E2EE_KEY),
                onDismiss = { confirmHomeE2eeKeyRotation = false },
                dismissEnabled = !homeRoomLoading,
                content = {
                    Text(
                        text = tr(TextId.ROTATE_E2EE_KEY_CONFIRMATION, room.name),
                        color = appTextPrimaryColor(),
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    )
                },
                actions = {
                    ActionButton(
                        label = tr(TextId.REFRESH_E2EE_KEY),
                        onClick = { rotateHomeE2eeKey(server, room) },
                        kind = ActionButtonKind.DANGER,
                        enabled = !homeRoomLoading,
                        modifier = Modifier.weight(1f)
                    )
                }
            )
        }

        homeQrDialogLink?.let { link ->
            QrCodeDialog(
                token = link,
                title = homeQrDialogTitle,
                onDismiss = { homeQrDialogLink = null },
                onCopy = { copyHomeValue("room_qr_link", link) }
            )
        }

        val effectivePeerMuteStates = peerMuteStates.toMutableMap().apply {
            val id = peerId.trim()
            if (id.isNotBlank() && id != "—") {
                this[id] = selfMutedByModerator || this[id] == true
            }
        }.toMap()

        val effectivePeerMicOffStates = peersState.associate { peer ->
            val serverMuted = effectivePeerMuteStates[peer.peerId] == true
            val userMicOff = peerAudioEnabledStates[peer.peerId] == false
            peer.peerId to (serverMuted || userMicOff || !peer.audioAttached)
        }

        val effectivePeerVideoOffStates = peersState.associate { peer ->
            val userVideoOff = peerVideoEnabledStates[peer.peerId] == false
            peer.peerId to (userVideoOff || !peer.videoAttached)
        }

        Scaffold(
            containerColor = appBackgroundColor(),
            bottomBar = {
                if (pictureInPictureMode) {
                    Unit
                } else if (currentScreen == RootScreen.HOME && connected && inCallUi) {
                    ControlBar(
                        speakerOn = speakerOn,
                        micEnabled = micEnabled,
                        micLocked = selfMutedByModerator,
                        audioRoute = audioRoute,
                        headsetAvailable = headsetAvailable,
                        handRaised = selfHandRaised,
                        outputOn = outputOn,
                        onSpeaker = { speakerOn = onToggleSpeaker() },
                        onAudioRouteSelected = { route ->
                            audioRoute = route
                            speakerOn = route == AudioOutputRoute.SPEAKER
                            onSetAudioRoute(route.toWireValue())
                        },
                        onMic = {
                            if (!selfMutedByModerator) {
                                val next = onToggleMic()
                                micEnabled = next

                                val id = peerId.trim()
                                if (id.isNotBlank() && id != "—") {
                                    peerAudioEnabledStates[id] = next
                                }

                                onSetMicAudioEnabled(next)
                            }
                        },
                        onHand = {
                            val next = !selfHandRaised
                            selfHandRaised = next
                            val id = peerId.trim()
                            if (id.isNotBlank() && id != "—") peerHandStates[id] = next
                            onSetHandRaised(next)
                        },
                        onOutput = { outputOn = onToggleOutput() },
                        onDisconnect = onDisconnect,
                        videoEnabled = videoEnabled,
                        onVideo = {
                            videoEnabled = onToggleVideo()
                            val id = peerId.trim()
                            if (id.isNotBlank() && id != "—") {
                                peerVideoEnabledStates[id] = videoEnabled
                            }
                        },
                        showModeratorButton = localRole == ROLE_MODERATOR,
                        moderatorPendingCount = pendingLobbyState.size,
                        moderatorMuteAllEnabled = muteAllEnabled,
                        showModeratorBubble = localRole == ROLE_MODERATOR &&
                                !moderatorPanelOpen &&
                                pendingLobbyIds.any { it !in dismissedModeratorBubblePeerIds },
                        onModeratorBubbleDismiss = {
                            dismissedModeratorBubblePeerIds = dismissedModeratorBubblePeerIds + pendingLobbyIds
                        },
                        onModerator = {
                            if (moderatorPanelOpen) {
                                moderatorPanelOpen = false
                            } else {
                                dismissedModeratorBubblePeerIds = dismissedModeratorBubblePeerIds + pendingLobbyIds
                                moderatorPanelOpen = true
                            }
                        }
                    )
                } else if (
                    currentScreen == RootScreen.HOME &&
                    (connectingToRoom || lobbyWaiting)
                ) {
                    Unit
                } else {
                    MinimalBottomNav(
                        menuActive = currentScreen != RootScreen.HOME,
                        onHome = {
                            currentScreen = RootScreen.HOME
                            if (showConnectDialog) {
                                showConnectDialog = false
                                clearConnectForm()
                            }
                        },
                        onMenu = {
                            if (showConnectDialog) {
                                showConnectDialog = false
                                clearConnectForm()
                            }
                            currentScreen = RootScreen.MENU
                        }
                    )
                }
            }
        ) { inner ->

            if (connectingToRoom && currentScreen == RootScreen.HOME) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner)
                        .background(appBackgroundColor())
                ) {
                    ConnectingRoomView(
                        roomName = connectingRoomName,
                        status = status,
                        onCancel = { cancelPendingRoomConnection() }
                    )
                }
                return@Scaffold
            }

            if (lobbyWaiting && currentScreen == RootScreen.HOME) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner)
                        .background(DarkBackground)
                ) {
                    LobbyWaitingView(
                        onCancel = {
                            lobbyWaiting = false
                            onDisconnect()
                        }
                    )
                }
                return@Scaffold
            }

            if (pictureInPictureMode || (inCallUi && currentScreen == RootScreen.HOME)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner)
                        .background(DarkBackground)
                ) {
                    CallInProgressView(
                        peers = peersState,
                        selfPeerId = peerId,
                        mode = callViewMode,
                        pinnedPeerId = pinnedPeerId,
                        peerHandStates = peerHandStates.toMap(),
                        peerMicOffStates = effectivePeerMicOffStates,
                        peerVideoOffStates = effectivePeerVideoOffStates,
                        onPin = { pinnedPeerId = it },
                        onModeChange = { callViewMode = it },
                        onSwitchCamera = onSwitchCamera,
                        pipOffsetX = pipOffsetX,
                        pipOffsetY = pipOffsetY,
                        onMovePip = { dx, dy ->
                            pipOffsetX += dx
                            pipOffsetY += dy
                        },
                        pipExpanded = pipExpanded,
                        onTogglePipExpanded = { pipExpanded = !pipExpanded },
                        pictureInPictureMode = pictureInPictureMode
                    )

                    ModeratorPanelOverlay(
                        visible = moderatorPanelOpen && !pictureInPictureMode,
                        pendingPeers = pendingLobbyState,
                        peers = peersState,
                        selfPeerId = peerId,
                        peerMuteStates = effectivePeerMuteStates,
                        peerMicOffStates = effectivePeerMicOffStates,
                        peerHandStates = peerHandStates.toMap(),
                        muteAllEnabled = muteAllEnabled,
                        onApprove = { id ->
                            pendingLobbyState.removeAll { it.peerId == id }
                            onApproveLobbyPeer(id)
                        },
                        onReject = { id ->
                            pendingLobbyState.removeAll { it.peerId == id }
                            onRejectLobbyPeer(id)
                        },
                        onKick = onKickPeer,
                        onMute = { id ->
                            peerMuteStates[id] = true
                            onMutePeer(id)
                        },
                        onUnmute = { id ->
                            peerMuteStates[id] = false
                            onUnmutePeer(id)
                        },
                        onLowerHand = { id ->
                            peerHandStates[id] = false
                            onLowerPeerHand(id)
                        },
                        onSetMuteAll = { enabled ->
                            val wasEnabled = muteAllEnabled
                            muteAllEnabled = enabled

                            if (enabled && !wasEnabled) {
                                peerMuteStates.clear()
                            }

                            onSetMuteAll(enabled)
                        },
                        onClose = { moderatorPanelOpen = false }
                    )

                    ReconnectOverlay(
                        visible = reconnectMode && !pictureInPictureMode,
                        onCancel = {
                            inCallUi = false
                            pinnedPeerId = null
                            moderatorPanelOpen = false
                            callViewMode = CallViewMode.FOCUS
                            pipOffsetX = 0f
                            pipOffsetY = 0f
                            pipExpanded = false
                            onCancelReconnect()
                        }
                    )
                }
                return@Scaffold
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    when (currentScreen) {
                    RootScreen.HOME -> {
                        if (showConnectDialog) {
                            HomeConnectScreen(
                                connectLink = connectLink,
                                onConnectLinkChange = { connectLink = it },
                                username = usernameInput,
                                onUsernameChange = { usernameInput = it },
                                parsed = parsedConnectLink,
                                linkError = connectLinkError,
                                tlsTrust = invitationTlsTrust,
                                reconnectMode = reconnectMode,
                                onAcceptChangedTlsPin = {
                                    invitationTlsTrust
                                        ?.takeIf { it.state == InvitationTlsTrustState.CHANGED }
                                        ?.let { trust ->
                                            acceptedInvitationTlsPin = AcceptedInvitationTlsPin(
                                                endpoint = trust.endpoint,
                                                pin = trust.presentedPin
                                            )
                                        }
                                },
                                onConnect = {
                                    parsedConnectLink?.let { payload ->
                                        val trust = invitationTlsTrust ?: return@let
                                        if (!trust.mayConnect) {
                                            Toast.makeText(
                                                context,
                                                tr(TextId.CONFIRM_NEW_TLS_PIN_FIRST),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            return@let
                                        }
                                        val finalUsername = usernameInput.trim().ifBlank { payload.username.trim() }
                                        pendingInvitationTrust = PendingInvitationTrust(
                                            endpoint = trust.endpoint,
                                            tlsPin = trust.presentedPin,
                                            room = payload.room,
                                            roomInstance = payload.roomInstance,
                                            e2eeSecret = payload.e2eeSecret
                                        )
                                        showConnectDialog = false
                                        connectLink = ""
                                        connectingToRoom = true
                                        connectingRoomName = payload.room
                                        status = "connecting…"
                                        inCallUi = false
                                        onConnect(
                                            payload.ip,
                                            payload.room,
                                            payload.roomInstance,
                                            finalUsername,
                                            payload.tlsPin,
                                            payload.moderatorKey,
                                            payload.e2eeSecret
                                        )
                                    }
                                }
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(bottom = 28.dp)
                            ) {
                                item {
                                    HomeMainScreen(
                                        servers = homeServers,
                                        reconnectMode = reconnectMode,
                                        roomsRefreshing = homeServersRefreshing || homeVersionsChecking,
                                        onOpenConnect = {
                                            showCreateMeetingDialog = false
                                            showConnectDialog = true
                                        },
                                        onOpenCreateMeeting = {
                                            showConnectDialog = false
                                            showCreateMeetingDialog = true
                                        },
                                        onRefreshRooms = {
                                            refreshHomeServers()
                                        },
                                        onMeetingClick = { server, room ->
                                            selectedHomeMeetingServerId = server.id
                                            selectedHomeMeetingRoomName = room.name
                                            homeEditMuteAllByDefault = room.muteAllByDefault
                                            homeEditAdmitWithoutApproval = room.admitWithoutApproval
                                        },
                                        onUpdateServer = { server ->
                                            homeInstallError = null
                                            homeInstallLogs.clear()
                                            homeInstallReturnToCreateRoom = false
                                            selectedHomeInstallServerId = server.id
                                        },
                                        modifier = Modifier.fillParentMaxHeight()
                                    )
                                }
                            }
                        }
                    }

                    RootScreen.MENU -> {
                        MenuScreen(
                            onServers = {
                                currentScreen = RootScreen.SERVERS
                            },
                            onSettings = { currentScreen = RootScreen.SETTINGS },
                            onAbout = { currentScreen = RootScreen.ABOUT }
                        )
                    }

                    RootScreen.SERVERS -> {
                        InstallTab(
                            expectedRelayVersion = expectedRelayVersion,
                            onAdminSessionAccess = onAdminSessionAccess,
                            onInstall = onInstall,
                            onRemoveRelay = onRemoveRelay,
                            onObserveSshHostKeyPin = onObserveSshHostKeyPin,
                            onEnrollSshKey = onEnrollSshKey,
                            onDeleteSshKey = onDeleteSshKey,
                            onProbeServer = onProbeServer,
                            onSetRoomOpenState = onSetRoomOpenState,
                            onCreateRoom = onCreateRoom,
                            onSetRoomSettings = onSetRoomSettings,
                            onRotateModeratorKey = onRotateModeratorKey,
                            onFetchOpenRooms = onFetchOpenRooms,
                            onFetchRelayVersion = onFetchRelayVersion
                        )
                    }

                    RootScreen.SETTINGS -> {
                        SettingsScreen(
                            themeMode = themeMode,
                            onThemeModeChange = onThemeModeChange,
                            appLanguage = appLanguage,
                            onAppLanguageChange = onAppLanguageChange,
                            telemetryEnabled = telemetryEnabled,
                            onTelemetryEnabledChange = onTelemetryEnabledChange,
                            secretProtectionEnabled = secretProtectionEnabled,
                            onSecretProtectionEnabledChange = onSecretProtectionEnabledChange,
                            accessibilityFontScale = accessibilityFontScale,
                            onAccessibilityFontScaleChange = { scale ->
                                accessibilityFontScale = scale
                                onAccessibilityFontScaleChange(scale)
                            }
                        )
                    }
                    RootScreen.ABOUT -> AboutScreen(appVersion = appVersion)
                }

                    ReconnectOverlay(
                        visible = reconnectMode,
                        onCancel = {
                            inCallUi = false
                            pinnedPeerId = null
                            moderatorPanelOpen = false
                            callViewMode = CallViewMode.FOCUS
                            pipOffsetX = 0f
                            pipOffsetY = 0f
                            pipExpanded = false
                            onCancelReconnect()
                        }
                    )
                }

                if (connected && !inCallUi && !pictureInPictureMode) {
                    MinimizedCallPreview(
                        peers = peersState,
                        selfPeerId = peerId,
                        pinnedPeerId = pinnedPeerId,
                        peerHandStates = peerHandStates.toMap(),
                        peerMicOffStates = effectivePeerMicOffStates,
                        peerVideoOffStates = effectivePeerVideoOffStates,
                        visible = minimizedCallPreviewVisible,
                        onExpand = {
                            inCallUi = true
                            minimizedCallPreviewVisible = true
                            currentScreen = RootScreen.HOME
                        },
                        onDismiss = { minimizedCallPreviewVisible = false },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun MinimalBottomNav(
    menuActive: Boolean,
    onHome: () -> Unit,
    onMenu: () -> Unit
) {
    val navShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)

    Surface(
        color = appBottomNavColor(),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(navShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, appGrayControlBorderColor(0.55f), navShape)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavIconButton(active = !menuActive, icon = Icons.Outlined.Home, label = tr(TextId.HOME), onClick = onHome, modifier = Modifier.weight(1f))
            NavIconButton(active = menuActive, icon = Icons.Outlined.Settings, label = tr(TextId.SETTINGS), onClick = onMenu, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun NavIconButton(active: Boolean, icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = 62.dp)
            .clip(AppButtonShape)
            .semantics {
                role = Role.Button
                contentDescription = label
                stateDescription = tr(if (active) TextId.SELECTED else TextId.NOT_SELECTED)
            }
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) AppAccent else appTextMutedColor(),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.size(3.dp))
        Text(
            text = label,
            color = if (active) AppAccent else appTextMutedColor(),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun WelcomeNewsDialog(
    onDismiss: () -> Unit,
    onOpenSite: () -> Unit,
    onOpenTelegram: () -> Unit
) {
    AppDialog(
        title = tr(TextId.STAY_UP_TO_DATE),
        onDismiss = onDismiss,
        content = {
            Text(
                text = tr(TextId.FOLLOW_SYMPOSIUM_NEWS),
                color = appTextSecondaryColor(),
                fontSize = 14.sp,
                lineHeight = 19.sp
            )
        },
        actions = {
            ActionButton(
                label = tr(TextId.OPEN_WEBSITE),
                onClick = onOpenSite,
                kind = ActionButtonKind.PRIMARY,
                modifier = Modifier.weight(1f)
            )
            ActionButton(
                label = tr(TextId.TELEGRAM),
                onClick = onOpenTelegram,
                kind = ActionButtonKind.SECONDARY,
                modifier = Modifier.weight(1f)
            )
        }
    )
}

@Composable
internal fun InitialSecretProtectionDialog(
    onEnable: () -> Unit,
    onDecline: () -> Unit
) {
    AppDialog(
        title = tr(TextId.DATA_PROTECTION),
        onDismiss = {},
        dismissEnabled = false,
        content = {
            Text(
                text = tr(TextId.SYMPOSIUM_CAN_PROTECT_APP_DATA_WITH_THE_DEVICE_PASSWORD_OR_BIOMETRICS),
                color = appTextSecondaryColor(),
                fontSize = 14.sp,
                lineHeight = 19.sp
            )
        },
        actions = {
            ActionButton(
                label = tr(TextId.ENABLE),
                onClick = onEnable,
                kind = ActionButtonKind.PRIMARY,
                modifier = Modifier.weight(1f)
            )
            ActionButton(
                label = tr(TextId.NOT_NOW),
                onClick = onDecline,
                kind = ActionButtonKind.SECONDARY,
                modifier = Modifier.weight(1f)
            )
        }
    )
}

@Composable
internal fun UpdateAvailableDialog(
    currentVersion: String,
    availableVersion: String,
    onDismiss: () -> Unit,
    onOpenSite: () -> Unit,
    onOpenTelegram: () -> Unit
) {
    AppDialog(
        title = tr(TextId.UPDATE_AVAILABLE),
        onDismiss = onDismiss,
        content = {
            Text(
                text = tr(TextId.A_NEW_VERSION_OF_SYMPOSIUM_IS_AVAILABLE),
                color = appTextPrimaryColor(),
                fontSize = 15.sp,
                lineHeight = 21.sp
            )
            Text(
                text = tr(TextId.INSTALLED_VERSION_LABEL, currentVersion),
                color = appTextSecondaryColor(),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Text(
                text = tr(TextId.AVAILABLE_VERSION_LABEL, availableVersion),
                color = appTextSecondaryColor(),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Text(
                text = tr(TextId.YOU_CAN_UPDATE_THE_APP_ON_THE_SYMPOSIUM_WEBSITE_RELEASE_NEWS_IS_AVAILABL),
                color = appTextSecondaryColor(),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        },
        actions = {
            ActionButton(
                label = tr(TextId.OPEN_WEBSITE),
                onClick = onOpenSite,
                kind = ActionButtonKind.PRIMARY,
                modifier = Modifier.weight(1f)
            )
            ActionButton(
                label = tr(TextId.TELEGRAM),
                onClick = onOpenTelegram,
                kind = ActionButtonKind.SECONDARY,
                modifier = Modifier.weight(1f)
            )
        }
    )
}

@Composable
internal fun SettingsScreen(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    appLanguage: AppLanguage,
    onAppLanguageChange: (AppLanguage) -> Unit,
    telemetryEnabled: Boolean,
    onTelemetryEnabledChange: (Boolean) -> Unit,
    secretProtectionEnabled: Boolean,
    onSecretProtectionEnabledChange: (Boolean) -> Unit,
    accessibilityFontScale: AccessibilityFontScale,
    onAccessibilityFontScaleChange: (AccessibilityFontScale) -> Unit
) {
    val themeOptions = AppThemeMode.values()
    val languageOptions = AppLanguage.values()
    val fontScaleOptions = AccessibilityFontScale.values()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item {
            SettingsDropdownCard(
                title = tr(TextId.THEME),
                selectedValue = tr(themeMode.labelId),
                options = themeOptions.map { tr(it.labelId) },
                selectedIndex = themeOptions.indexOf(themeMode),
                onSelect = { onThemeModeChange(themeOptions[it]) }
            )
        }

        item {
            SettingsDropdownCard(
                title = tr(TextId.LANGUAGE),
                selectedValue = tr(appLanguage.labelId),
                options = languageOptions.map { tr(it.labelId) },
                selectedIndex = languageOptions.indexOf(appLanguage),
                onSelect = { onAppLanguageChange(languageOptions[it]) }
            )
        }

        item {
            SettingsDropdownCard(
                title = tr(TextId.FONT_SIZE),
                selectedValue = tr(accessibilityFontScale.labelId),
                options = fontScaleOptions.map { tr(it.labelId) },
                selectedIndex = fontScaleOptions.indexOf(accessibilityFontScale),
                onSelect = { onAccessibilityFontScaleChange(fontScaleOptions[it]) }
            )
        }

        item {
            SettingsToggleCard(
                title = tr(TextId.PROTECTION_AT_STARTUP),
                subtitle = tr(TextId.REQUIRE_THE_DEVICE_SCREEN_LOCK_AT_STARTUP_AND_USE_IT_TO_PROTECT_SSH_KEYS),
                checked = secretProtectionEnabled,
                onCheckedChange = onSecretProtectionEnabledChange
            )
        }

        item {
            SettingsToggleCard(
                title = tr(TextId.ANONYMOUS_DIAGNOSTICS),
                subtitle = tr(TextId.TELEMETRY_OFF_BY_DEFAULT),
                checked = telemetryEnabled,
                onCheckedChange = onTelemetryEnabledChange
            )
        }
    }
}

@Composable
private fun SettingsToggleCard(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = appSurfaceElevatedColor(),
        shape = CardShape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 60.dp)
                .border(1.dp, appGrayControlBorderColor(), CardShape)
                .semantics(mergeDescendants = true) {
                    role = Role.Switch
                    stateDescription = tr(if (checked) TextId.ON else TextId.OFF)
                }
                .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = appTextPrimaryColor(),
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium
                )
                subtitle?.let {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = it,
                        color = appTextSecondaryColor(),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            androidx.compose.material3.Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AppAccent,
                    checkedBorderColor = AppAccent,
                    uncheckedThumbColor = appTextSecondaryColor(),
                    uncheckedTrackColor = appSurfaceColor(),
                    uncheckedBorderColor = appFieldBorderColor()
                )
            )
        }
    }
}

@Composable
private fun SettingsDropdownCard(
    title: String,
    selectedValue: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = appSurfaceElevatedColor(),
        shape = CardShape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.border(1.dp, appGrayControlBorderColor(), CardShape)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        stateDescription = tr(if (expanded) TextId.EXPANDED else TextId.COLLAPSED)
                    }
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = title,
                    color = appTextPrimaryColor(),
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = selectedValue,
                    color = appTextSecondaryColor(),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = appTextSecondaryColor(),
                    modifier = Modifier.size(20.dp)
                )
            }

            if (expanded) {
                Column(
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    options.forEachIndexed { index, option ->
                        val selected = index == selectedIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .clip(AppButtonShape)
                                .background(appSurfaceColor())
                                .semantics(mergeDescendants = true) {
                                    role = Role.RadioButton
                                    stateDescription = tr(if (selected) TextId.SELECTED else TextId.NOT_SELECTED)
                                }
                                .clickable(role = Role.RadioButton) {
                                    onSelect(index)
                                    expanded = false
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = option,
                                color = appTextPrimaryColor(),
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                            )
                            if (selected) {
                                Text(
                                    text = "✓",
                                    color = appTextSecondaryColor(),
                                    fontSize = 15.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MenuScreen(
    onServers: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MenuNavButton(
            label = tr(TextId.MANAGE_SERVERS),
            subtitle = tr(TextId.RELAY_INSTALLATION_AND_ROOM_MANAGEMENT),
            icon = Icons.Filled.GridView,
            onClick = onServers
        )
        MenuNavButton(
            label = tr(TextId.SETTINGS),
            subtitle = tr(TextId.THEME_LANGUAGE_AND_PRIVACY),
            icon = Icons.Filled.Settings,
            onClick = onSettings
        )
        MenuNavButton(
            label = tr(TextId.ABOUT),
            subtitle = tr(TextId.VERSION_AND_APP_INFORMATION),
            icon = Icons.Filled.Info,
            onClick = onAbout
        )
    }
}

@Composable
internal fun MenuNavButton(
    label: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val lightTheme = isAppLightTheme()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { role = Role.Button }
            .clickable(role = Role.Button, onClick = onClick),
        color = appMenuItemColor(),
        shape = InnerCardShape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 74.dp)
                .border(1.dp, appGrayControlBorderColor(), InnerCardShape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(AppButtonShape)
                    .background(if (lightTheme) AppAccentEnd else AppAccent.copy(alpha = 0.24f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = label,
                    color = appTextPrimaryColor(),
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = appTextSecondaryColor(),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = appTextSecondaryColor(),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
internal fun AboutScreen(appVersion: String) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = tr(TextId.ABOUT),
            color = appTextPrimaryColor(),
            fontSize = 22.sp,
        )
        Text(tr(TextId.VERSION_LABEL, appVersion), color = appTextSecondaryColor(), fontSize = 14.sp)

        Text(
            text = tr(TextId.NEWS_AND_UPDATES),
            color = appTextPrimaryColor(),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp)
        )

        MenuNavButton(
            label = tr(TextId.TELEGRAM_CHANNEL),
            subtitle = tr(TextId.TELEGRAM_CHANNEL_ADDRESS),
            icon = Icons.Filled.Campaign,
            onClick = {
                openExternalIntent(
                    context,
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/symposium_app"))
                )
            }
        )

        MenuNavButton(
            label = tr(TextId.OFFICIAL_WEBSITE),
            subtitle = tr(TextId.OFFICIAL_WEBSITE_ADDRESS),
            icon = Icons.Filled.Language,
            onClick = {
                openExternalIntent(
                    context,
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://symposium-dp.app"))
                )
            }
        )

        Text(
            text = tr(TextId.SUPPORT),
            color = appTextPrimaryColor(),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp)
        )

        MenuNavButton(
            label = tr(TextId.TELEGRAM_SUPPORT),
            subtitle = tr(TextId.TELEGRAM_SUPPORT_ADDRESS),
            icon = Icons.Filled.SupportAgent,
            onClick = {
                openExternalIntent(
                    context,
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://t.me/symposium_support_bot")
                    )
                )
            }
        )

        MenuNavButton(
            label = tr(TextId.EMAIL),
            subtitle = tr(TextId.SUPPORT_EMAIL_ADDRESS),
            icon = Icons.Filled.Email,
            onClick = {
                openExternalIntent(
                    context,
                    Intent(
                        Intent.ACTION_SENDTO,
                        Uri.parse("mailto:support@symposium-dp.app")
                    )
                )
            }
        )
    }
}

private fun openExternalIntent(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(
                context,
                tr(TextId.COULD_NOT_OPEN_THE_APP),
                Toast.LENGTH_SHORT
            ).show()
        }
}
