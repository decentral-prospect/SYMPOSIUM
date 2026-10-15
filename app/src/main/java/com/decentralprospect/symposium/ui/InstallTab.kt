package com.decentralprospect.symposium

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

internal enum class InstallView { GRID, ADD, DETAIL }

private data class PendingSshHostKeyTrust(
    val ip: String,
    val username: String,
    val password: String,
    val observedPin: String
)

@Composable
fun InstallTab(
    expectedRelayVersion: String,
    onAdminSessionAccess: () -> Boolean,
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
    onFetchRelayVersion: suspend (ip: String, httpsPort: Int?, relayTlsPin: String?, adminToken: String?) -> RemoteInstaller.RelayVersionResult
) {
    val context = LocalContext.current
    val store = remember(context) { InstallServersStore(context) }
    val conferenceE2eeSecrets = remember(context) { ConferenceE2eeSecretStore(context) }
    val servers = remember { mutableStateListOf<InstallServer>() }
    val installScope = rememberCoroutineScope()
    val installLogListState = rememberLazyListState()

    var qrDialogLink by remember { mutableStateOf<String?>(null) }
    var qrDialogTitle by remember { mutableStateOf(tr(TextId.QR_CODE)) }

    var view by remember { mutableStateOf(InstallView.GRID) }
    var selectedServerId by remember { mutableStateOf<String?>(null) }

    var addIp by remember { mutableStateOf("") }
    var addUser by remember { mutableStateOf("") }
    var addPassword by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }
    var addLoading by remember { mutableStateOf(false) }
    var addSuccessMessage by remember { mutableStateOf<String?>(null) }
    var pendingSshHostKeyTrust by remember { mutableStateOf<PendingSshHostKeyTrust?>(null) }


    val installLogs = remember { mutableStateListOf<String>() }
    var installing by remember { mutableStateOf(false) }
    var installSuccess by remember { mutableStateOf(false) }
    var installError by remember { mutableStateOf<String?>(null) }
    var roomActionName by remember { mutableStateOf("") }
    var showAddRoomDialog by remember { mutableStateOf(false) }
    var roomDialogName by remember { mutableStateOf("") }
    var roomSettingsDialogName by remember { mutableStateOf<String?>(null) }
    var roomSettingsMuteAllByDefault by remember { mutableStateOf(false) }
    var roomSettingsAdmitWithoutApproval by remember { mutableStateOf(false) }
    var roomActionLoading by remember { mutableStateOf(false) }
    var pendingModeratorLinkRotationRoom by remember { mutableStateOf<String?>(null) }
    var pendingE2eeKeyRotationRoom by remember { mutableStateOf<String?>(null) }
    var pendingDeleteServerId by remember { mutableStateOf<String?>(null) }
    var removingRelay by remember { mutableStateOf(false) }
    var updatingRelay by remember { mutableStateOf(false) }
    var installLogsExpanded by remember(selectedServerId) { mutableStateOf(false) }

    var roomsExpanded by remember(selectedServerId) { mutableStateOf(false) }
    var expandedRoomName by remember(selectedServerId) { mutableStateOf<String?>(null) }

    val selectedServer = servers.firstOrNull { it.id == selectedServerId }
    val pendingDeleteServer = servers.firstOrNull { it.id == pendingDeleteServerId }

    BackHandler(enabled = showAddRoomDialog || roomSettingsDialogName != null || pendingModeratorLinkRotationRoom != null || pendingE2eeKeyRotationRoom != null || pendingDeleteServer != null || pendingSshHostKeyTrust != null || qrDialogLink != null || view != InstallView.GRID) {
        when {
            qrDialogLink != null -> qrDialogLink = null
            pendingModeratorLinkRotationRoom != null -> {
                if (!roomActionLoading) pendingModeratorLinkRotationRoom = null
            }
            pendingE2eeKeyRotationRoom != null -> pendingE2eeKeyRotationRoom = null
            pendingSshHostKeyTrust != null -> {
                pendingSshHostKeyTrust = null
                addLoading = false
            }
            showAddRoomDialog -> {
                if (!roomActionLoading) {
                    showAddRoomDialog = false
                    roomDialogName = ""
                }
            }
            roomSettingsDialogName != null -> if (!roomActionLoading) roomSettingsDialogName = null
            pendingDeleteServer != null -> pendingDeleteServerId = null
            view != InstallView.GRID -> {
                view = InstallView.GRID
                selectedServerId = null
                addIp = ""
                addUser = ""
                addPassword = ""
                addError = null
            }
        }
    }

    suspend fun <T> withAdminOperation(block: suspend () -> T): T {
        check(onAdminSessionAccess()) { tr(TextId.ACCESS_TO_PROTECTED_DATA_WAS_NOT_CONFIRMED) }
        return block()
    }

    LaunchedEffect(Unit) {
        val loaded = runCatching { store.load() }.getOrElse {
            installError = tr(TextId.COULD_NOT_OPEN_PROTECTED_DATA)
            return@LaunchedEffect
        }
        // Remove legacy passwords from disk before performing network migration.
        store.save(loaded)
        var migrationFailures = 0
        val needsMigration = loaded.any {
            !it.sshKeyInstalled && it.password.isNotBlank() && !it.sshHostKeyPin.isNullOrBlank()
        }
        val prepared = if (needsMigration) {
            runCatching {
                withAdminOperation {
                    loaded.map { server ->
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
                }
            }.getOrElse {
                installError = it.message ?: tr(TextId.COULD_NOT_CONFIRM_ACCESS)
                loaded.map { it.copy(password = "") }
            }
        } else {
            loaded.map { it.copy(password = "") }
        }
        store.save(prepared)
        servers.clear()
        servers.addAll(store.load())
        if (migrationFailures > 0) {
            installError = tr(TextId.THE_OLD_PASSWORD_COULD_NOT_BE_REPLACED_WITH_AN_SSH_KEY_FOR_SOME_SERVERS_)
        }
    }

    LaunchedEffect(installLogs.size) {
        val lastLogIndex = installLogs.lastIndex
        if (lastLogIndex >= 0) {
            installLogListState.animateScrollToItem(lastLogIndex)
        }
    }

    LaunchedEffect(addSuccessMessage) {
        if (addSuccessMessage != null) {
            delay(2200)
            addSuccessMessage = null
        }
    }

    fun persistServers() {
        store.save(servers)
        val redacted = store.load()
        servers.clear()
        servers.addAll(redacted)
    }

    fun copyToClipboard(label: String, value: String) {
        copySensitiveText(context, label, value)
        Toast.makeText(context, tr(TextId.COPIED), Toast.LENGTH_SHORT).show()
    }

    fun updateServer(updated: InstallServer) {
        val index = servers.indexOfFirst { it.id == updated.id }
        if (index >= 0) {
            val redacted = store.upsert(updated)
            servers.clear()
            servers.addAll(redacted)
        }
    }

    fun updateServer(serverId: String, transform: (InstallServer) -> InstallServer) {
        val index = servers.indexOfFirst { it.id == serverId }
        if (index >= 0) {
            val redacted = store.upsert(transform(servers[index]))
            servers.clear()
            servers.addAll(redacted)
        }
    }

    fun relayVersionStateFor(actualVersion: String): RelayVersionState {
        return if (canonicalVersion(actualVersion) == canonicalVersion(expectedRelayVersion)) {
            RelayVersionState.CURRENT
        } else {
            RelayVersionState.OUTDATED
        }
    }

    fun markRelayVersion(serverId: String, result: Result<RemoteInstaller.RelayVersionResult>) {
        updateServer(serverId) { current ->
            result.fold(
                onSuccess = { info ->
                    val actualVersion = canonicalVersion(info.version)
                    current.copy(
                        installed = true,
                        relayVersion = actualVersion,
                        relayVersionState = relayVersionStateFor(actualVersion)
                    )
                },
                onFailure = {
                    current.copy(
                        installed = false,
                        openRooms = emptyList(),
                        relayVersion = null,
                        relayVersionState = RelayVersionState.NOT_INSTALLED
                    )
                }
            )
        }
    }

    suspend fun syncServerState(server: InstallServer, reportErrors: Boolean = false) {
        updateServer(server.id) { current ->
            current.copy(relayVersionState = RelayVersionState.UNKNOWN)
        }

        val hasHttpsCredentials =
            !server.relayTlsPin.isNullOrBlank() && server.adminTokenAvailable
        val directVersionResult = if (hasHttpsCredentials) {
            runCatching {
                onFetchRelayVersion(
                    server.ip,
                    server.httpsPort,
                    server.relayTlsPin,
                    store.readAdminToken(server.id)
                )
            }
        } else {
            Result.failure(IllegalStateException("Relay credentials are not synchronized yet"))
        }

        if (directVersionResult.isSuccess) {
            markRelayVersion(server.id, directVersionResult)
            runCatching {
                onFetchOpenRooms(server.ip, server.httpsPort, server.relayTlsPin, store.readAdminToken(server.id))
            }.onSuccess { result ->
                updateServer(server.id) { current ->
                    current.copy(
                        openRooms = result.openRooms,
                        sshHostKeyPin = current.sshHostKeyPin ?: result.observedSshHostKeyPin,
                        relayTlsPin = result.relayInfo?.pin ?: current.relayTlsPin,
                        httpsPort = result.relayInfo?.httpsPort ?: current.httpsPort,
                        adminToken = result.relayInfo?.adminToken ?: current.adminToken
                    )
                }
                if (reportErrors) installError = null
            }.onFailure {
                if (reportErrors) installError = it.message ?: tr(TextId.COULD_NOT_REFRESH_OPEN_ROOMS)
            }
            return
        }

        val probeResult = runCatching {
            onProbeServer(server.ip, server.username, server.password, server.sshHostKeyPin)
        }
        val probed = probeResult.getOrElse {
            updateServer(server.id) { current ->
                current.copy(
                    relayVersionState = if (current.installed) {
                        RelayVersionState.UNKNOWN
                    } else {
                        RelayVersionState.NOT_INSTALLED
                    }
                )
            }
            if (reportErrors) {
                installError = it.message ?: tr(TextId.COULD_NOT_CHECK_THE_SERVER_OVER_SSH)
            }
            return
        }

        if (probed.installationInProgress) {
            updateServer(server.id) { current ->
                current.copy(
                    relayVersionState = RelayVersionState.UNKNOWN,
                    sshHostKeyPin = current.sshHostKeyPin ?: probed.observedSshHostKeyPin
                )
            }
            return
        }

        if (!probed.installed) {
            updateServer(server.id) { current ->
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
            if (reportErrors) installError = null
            return
        }

        val relayInfo = probed.relayInfo
        updateServer(server.id) { current ->
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
        val discoveredToken = relayInfo?.adminToken ?: store.readAdminToken(server.id)
        if (discoveredPin.isNullOrBlank() || discoveredToken.isNullOrBlank()) {
            if (reportErrors) installError = tr(TextId.SYMPOSIUMRELAY_WAS_FOUND_BUT_ITS_SETTINGS_COULD_NOT_BE_SYNCHRONIZED)
            return
        }

        val discoveredVersionResult = runCatching {
            onFetchRelayVersion(server.ip, discoveredPort, discoveredPin, discoveredToken)
        }
        if (discoveredVersionResult.isSuccess) {
            markRelayVersion(server.id, discoveredVersionResult)
        }

        runCatching {
            onFetchOpenRooms(server.ip, discoveredPort, discoveredPin, discoveredToken)
        }.onSuccess { result ->
            updateServer(server.id) { current ->
                current.copy(
                    openRooms = result.openRooms,
                    relayTlsPin = result.relayInfo?.pin ?: current.relayTlsPin,
                    httpsPort = result.relayInfo?.httpsPort ?: current.httpsPort,
                    adminToken = result.relayInfo?.adminToken ?: current.adminToken
                )
            }
        }
        if (reportErrors) installError = null
    }

    fun refreshRelayVersion(server: InstallServer) {
        installScope.launch {
            runCatching { withAdminOperation { syncServerState(server) } }
                .onFailure { installError = it.message ?: tr(TextId.COULD_NOT_REFRESH_THE_SERVER_STATUS) }
        }
    }

    fun deleteServer(server: InstallServer) {
        onDeleteSshKey(server.ip, server.username)
        conferenceE2eeSecrets.removeAll(
            host = server.ip,
            port = server.httpsPort,
            rooms = server.openRooms
        )
        servers.removeAll { it.id == server.id }
        persistServers()
        selectedServerId = null
        installError = null
        installSuccess = false
        installLogs.clear()
        addSuccessMessage = null
        pendingDeleteServerId = null
        view = InstallView.GRID
    }

    LaunchedEffect(selectedServerId) {
        installLogs.clear()
        installLogsExpanded = false
        installError = null
        installSuccess = false
        installing = false
        updatingRelay = false
        roomActionLoading = false

        val server = selectedServer ?: return@LaunchedEffect
        runCatching { withAdminOperation { syncServerState(server, reportErrors = true) } }
            .onFailure { installError = it.message ?: tr(TextId.COULD_NOT_REFRESH_THE_SERVER_STATUS) }
    }

    fun finishAddServerAfterHostKeyTrust(trust: PendingSshHostKeyTrust) {
        addLoading = true
        addError = null

        installScope.launch {
            if (!onAdminSessionAccess()) {
                addError = tr(TextId.ACCESS_TO_PROTECTED_DATA_WAS_NOT_CONFIRMED)
                addLoading = false
                return@launch
            }
            runCatching {
                onEnrollSshKey(trust.ip, trust.username, trust.password, trust.observedPin)
            }.getOrElse {
                addError = it.message ?: tr(TextId.COULD_NOT_CONFIGURE_SSH_KEY_ACCESS)
                addLoading = false
                return@launch
            }

            val probe = runCatching {
                onProbeServer(
                    trust.ip,
                    trust.username,
                    "",
                    trust.observedPin
                )
            }

            val probed = probe.getOrElse {
                addError = it.message ?: tr(TextId.COULD_NOT_CONNECT_TO_THE_SERVER)
                addLoading = false
                return@launch
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

            servers.add(newServer)
            persistServers()
            syncServerState(newServer)

            selectedServerId = null
            view = InstallView.GRID
            addSuccessMessage = tr(TextId.SERVER_ADDED_SUCCESSFULLY)

            addIp = ""
            addUser = ""
            addPassword = ""
            addLoading = false
        }
    }

    fun addServer() {
        if (addLoading) return

        val ip = normalizeIpInput(addIp)
        val user = addUser.trim()

        if (ip.isBlank() || user.isBlank() || addPassword.isBlank()) {
            addError = tr(TextId.ENTER_THE_IP_ADDRESS_USER_AND_PASSWORD)
            return
        }

        if (!isValidIpAddress(ip)) {
            addError = tr(TextId.ENTER_A_VALID_IP_ADDRESS)
            return
        }

        addLoading = true
        addError = null

        val password = addPassword
        installScope.launch {
            val observedPin = runCatching {
                onObserveSshHostKeyPin(ip)
            }.getOrElse {
                addError = it.message ?: tr(TextId.COULD_NOT_RETRIEVE_THE_SERVER_SSH_KEY)
                addLoading = false
                return@launch
            }

            pendingSshHostKeyTrust = PendingSshHostKeyTrust(
                ip = ip,
                username = user,
                password = password,
                observedPin = observedPin
            )
            addLoading = false
        }
    }

    fun startInstallation(server: InstallServer) {
        if (installing) return
        if (server.ip.isBlank() || server.username.isBlank() || !server.sshKeyInstalled) {
            installError = tr(TextId.A_CONFIGURED_SSH_KEY_IS_REQUIRED_FOR_INSTALLATION)
            installSuccess = false
            return
        }

        installScope.launch {
            installError = null
            installSuccess = false
            installing = true
            installLogs.clear()
            installLogsExpanded = true

            val result = runCatching {
                onInstall(
                    server.ip.trim(),
                    server.username.trim(),
                    server.password,
                    server.sshHostKeyPin,
                    store.hydrateDeploymentProfile(server),
                    server.relayCapacity
                ) { line ->
                    withContext(Dispatchers.Main) { installLogs.add(line) }
                }
            }

            val r = result.getOrElse {
                installError = it.message ?: tr(TextId.INSTALLATION_COULD_NOT_BE_COMPLETED)
                installing = false
                installSuccess = false
                installLogsExpanded = installLogs.isNotEmpty()
                return@launch
            }

            installing = false
            installLogsExpanded = false
            installSuccess = r.success

            if (!r.success) {
                installError = installError ?: tr(TextId.INSTALLATION_FAILED_CODE, r.exitStatus)
                return@launch
            }

            val sshPin = server.sshHostKeyPin ?: r.observedSshHostKeyPin
            val relayPin = r.relayInfo?.pin
            val httpsPort = r.relayInfo?.httpsPort
            val adminToken = r.relayInfo?.adminToken

            updateServer(server.id) { current ->
                current.copy(
                    installed = true,
                    sshHostKeyPin = sshPin,
                    relayTlsPin = relayPin ?: current.relayTlsPin,
                    httpsPort = httpsPort ?: current.httpsPort,
                    adminToken = adminToken ?: current.adminToken,
                    deploymentProfile = r.deploymentProfile ?: current.deploymentProfile,
                    relayCapacity = r.deploymentProfile?.relayCapacity ?: current.relayCapacity,
                    openRooms = r.openRooms,
                    relayVersion = expectedRelayVersion,
                    relayVersionState = RelayVersionState.CURRENT
                )
            }
        }
    }

    fun removeRelay(server: InstallServer) {
        if (removingRelay) return
        installScope.launch {
            installError = null
            installSuccess = false
            removingRelay = true
            installLogs.clear()
            installLogsExpanded = true

            val result = runCatching {
                onRemoveRelay(
                    server.ip.trim(),
                    server.username.trim(),
                    server.password,
                    server.sshHostKeyPin,
                    store.hydrateDeploymentProfile(server)
                ) { line ->
                    withContext(Dispatchers.Main) { installLogs.add(line) }
                }
            }

            val r = result.getOrElse {
                installError = it.message ?: tr(TextId.COULD_NOT_REMOVE_SYMPOSIUMRELAY)
                removingRelay = false
                installLogsExpanded = installLogs.isNotEmpty()
                return@launch
            }

            updateServer(server.id) { current ->
                current.copy(
                    installed = false,
                    openRooms = emptyList(),
                    relayTlsPin = null,
                    httpsPort = null,
                    adminToken = null,
                    deploymentProfile = null,
                    relayVersion = null,
                    relayVersionState = RelayVersionState.NOT_INSTALLED,
                    sshHostKeyPin = current.sshHostKeyPin ?: r.observedSshHostKeyPin
                )
            }

            removingRelay = false
            installLogsExpanded = false
        }
    }

    fun updateRelay(server: InstallServer) {
        if (installing || removingRelay || updatingRelay) return
        if (server.ip.isBlank() || server.username.isBlank() || !server.sshKeyInstalled) {
            installError = tr(TextId.A_CONFIGURED_SSH_KEY_IS_REQUIRED_FOR_UPDATING)
            installSuccess = false
            return
        }

        installScope.launch {
            installError = null
            installSuccess = false
            updatingRelay = true
            installLogs.clear()
            installLogsExpanded = true

            if (!onAdminSessionAccess()) {
                installError = tr(TextId.ACCESS_TO_PROTECTED_DATA_WAS_NOT_CONFIRMED)
                updatingRelay = false
                return@launch
            }
            val removalResult = runCatching {
                onRemoveRelay(
                    server.ip.trim(),
                    server.username.trim(),
                    server.password,
                    server.sshHostKeyPin,
                    store.hydrateDeploymentProfile(server)
                ) { line ->
                    withContext(Dispatchers.Main) { installLogs.add(line) }
                }
            }

            val removal = removalResult.getOrElse {
                installError = it.message ?: tr(TextId.COULD_NOT_REMOVE_THE_OLD_SYMPOSIUMRELAY_VERSION)
                updatingRelay = false
                installLogsExpanded = installLogs.isNotEmpty()
                return@launch
            }

            val sshPin = server.sshHostKeyPin ?: removal.observedSshHostKeyPin

            val installResult = runCatching {
                onInstall(
                    server.ip.trim(),
                    server.username.trim(),
                    server.password,
                    sshPin,
                    store.hydrateDeploymentProfile(server),
                    server.relayCapacity
                ) { line ->
                    withContext(Dispatchers.Main) { installLogs.add(line) }
                }
            }

            val installed = installResult.getOrElse {
                installError = it.message ?: tr(TextId.COULD_NOT_INSTALL_THE_NEW_SYMPOSIUMRELAY_VERSION)
                updatingRelay = false
                installLogsExpanded = installLogs.isNotEmpty()
                return@launch
            }

            updatingRelay = false
            installLogsExpanded = false
            installSuccess = installed.success

            if (!installed.success) {
                installError = tr(TextId.UPDATE_FAILED_CODE, installed.exitStatus)
                return@launch
            }

            updateServer(server.id) { current ->
                current.copy(
                    installed = true,
                    sshHostKeyPin = sshPin ?: installed.observedSshHostKeyPin,
                    relayTlsPin = installed.relayInfo?.pin ?: current.relayTlsPin,
                    httpsPort = installed.relayInfo?.httpsPort ?: current.httpsPort,
                    adminToken = installed.relayInfo?.adminToken ?: current.adminToken,
                    deploymentProfile = installed.deploymentProfile ?: current.deploymentProfile,
                    relayCapacity = installed.deploymentProfile?.relayCapacity ?: current.relayCapacity,
                    openRooms = installed.openRooms,
                    relayVersion = expectedRelayVersion,
                    relayVersionState = RelayVersionState.CURRENT
                )
            }
        }
    }

    fun applyRoomAction(server: InstallServer, open: Boolean, onSuccess: (() -> Unit)? = null) {
        if (roomActionLoading) return
        if (!server.installed) return
        val name = roomActionName.trim()
        if (name.isBlank()) {
            installError = tr(TextId.ENTER_A_ROOM_NAME_2)
            return
        }
        val existingRoom = server.openRooms.firstOrNull { it.name == name }
        roomActionLoading = true
        installScope.launch {
            val result = runCatching {
                onSetRoomOpenState(server.ip, server.httpsPort, server.relayTlsPin, store.readAdminToken(server.id), name, open)
            }
            val r = result.getOrElse {
                installError = it.message ?: tr(TextId.ROOM_MANAGEMENT_ERROR)
                roomActionLoading = false
                return@launch
            }
            updateServer(server.id) { current ->
                current.copy(
                    openRooms = r.openRooms,
                    sshHostKeyPin = current.sshHostKeyPin ?: r.observedSshHostKeyPin,
                    relayTlsPin = r.relayInfo?.pin ?: current.relayTlsPin,
                    httpsPort = r.relayInfo?.httpsPort ?: current.httpsPort,
                    adminToken = r.relayInfo?.adminToken ?: current.adminToken
                )
            }
            if (open) {
                r.openRooms.firstOrNull { it.name == name }?.let { openedRoom ->
                    conferenceE2eeSecrets.rotate(
                        server.ip,
                        server.httpsPort,
                        openedRoom.name,
                        openedRoom.roomInstance
                    )
                }
            } else {
                existingRoom?.let { closedRoom ->
                    conferenceE2eeSecrets.remove(
                        server.ip,
                        server.httpsPort,
                        closedRoom.name,
                        closedRoom.roomInstance
                    )
                }
            }
            installError = null
            roomActionName = ""
            roomActionLoading = false
            onSuccess?.invoke()
        }
    }

    fun createRoom(server: InstallServer) {
        if (roomActionLoading || !server.installed) return
        if (!serverAllowsRoomCreation(server)) {
            installError = if (serverNeedsRelayUpdate(server)) {
                tr(TextId.UPDATE_SELECTED_RELAY_FIRST)
            } else {
                tr(TextId.RELAY_VERSION_COULD_NOT_BE_VERIFIED)
            }
            return
        }
        val name = roomDialogName.trim()
        if (name.isBlank()) {
            installError = tr(TextId.ENTER_A_ROOM_NAME_2)
            return
        }
        roomActionLoading = true
        roomActionName = name
        installScope.launch {
            val result = runCatching {
                onCreateRoom(
                    server.ip,
                    server.httpsPort,
                    server.relayTlsPin,
                    store.readAdminToken(server.id),
                    name,
                    false,
                    false
                )
            }
            val created = result.getOrElse {
                installError = it.message ?: tr(TextId.COULD_NOT_CREATE_THE_ROOM)
                roomActionLoading = false
                return@launch
            }
            updateServer(server.id) { current ->
                current.copy(
                    openRooms = created.openRooms,
                    relayTlsPin = created.relayInfo?.pin ?: current.relayTlsPin,
                    httpsPort = created.relayInfo?.httpsPort ?: current.httpsPort,
                    adminToken = created.relayInfo?.adminToken ?: current.adminToken
                )
            }
            created.openRooms.firstOrNull { it.name == name }?.let { createdRoom ->
                conferenceE2eeSecrets.rotate(
                    server.ip,
                    server.httpsPort,
                    createdRoom.name,
                    createdRoom.roomInstance
                )
            }
            installError = null
            roomActionName = ""
            roomDialogName = ""
            showAddRoomDialog = false
            roomActionLoading = false
        }
    }

    fun rotateRoomE2eeKey(server: InstallServer, roomName: String) {
        val room = server.openRooms.firstOrNull { it.name == roomName }
        if (room == null || !isValidRoomInstance(room.roomInstance)) {
            pendingE2eeKeyRotationRoom = null
            installError = tr(TextId.REFRESH_ROOM_LIST)
            return
        }
        runCatching {
            conferenceE2eeSecrets.rotate(
                server.ip,
                server.httpsPort,
                room.name,
                room.roomInstance
            )
        }.onSuccess {
            pendingE2eeKeyRotationRoom = null
            Toast.makeText(context, tr(TextId.E2EE_KEY_REFRESHED), Toast.LENGTH_SHORT).show()
        }.onFailure {
            pendingE2eeKeyRotationRoom = null
            installError = it.message
        }
    }

    fun saveRoomSettings(server: InstallServer, roomName: String) {
        if (roomActionLoading || !server.installed) return
        roomActionLoading = true
        roomActionName = roomName
        installScope.launch {
            val result = runCatching {
                onSetRoomSettings(
                    server.ip,
                    server.httpsPort,
                    server.relayTlsPin,
                    store.readAdminToken(server.id),
                    roomName,
                    roomSettingsMuteAllByDefault,
                    roomSettingsAdmitWithoutApproval
                )
            }
            val updated = result.getOrElse {
                installError = it.message ?: tr(TextId.COULD_NOT_SAVE_THE_ROOM_SETTINGS)
                roomActionLoading = false
                return@launch
            }
            updateServer(server.id) { current ->
                current.copy(
                    openRooms = updated.openRooms,
                    relayTlsPin = updated.relayInfo?.pin ?: current.relayTlsPin,
                    httpsPort = updated.relayInfo?.httpsPort ?: current.httpsPort,
                    adminToken = updated.relayInfo?.adminToken ?: current.adminToken
                )
            }
            installError = null
            roomActionName = ""
            roomSettingsDialogName = null
            roomActionLoading = false
            Toast.makeText(context, tr(TextId.ROOM_SETTINGS_SAVED), Toast.LENGTH_SHORT).show()
        }
    }

    fun rotateModeratorLink(server: InstallServer, roomName: String) {
        if (roomActionLoading || !server.installed) return
        roomActionLoading = true
        roomActionName = roomName
        installScope.launch {
            val result = runCatching {
                onRotateModeratorKey(
                    server.ip,
                    server.httpsPort,
                    server.relayTlsPin,
                    store.readAdminToken(server.id),
                    roomName
                )
            }
            val rotated = result.getOrElse {
                installError = it.message ?: tr(TextId.COULD_NOT_REFRESH_THE_MODERATOR_LINK)
                roomActionLoading = false
                pendingModeratorLinkRotationRoom = null
                return@launch
            }
            updateServer(server.id) { current ->
                current.copy(
                    openRooms = rotated.openRooms,
                    relayTlsPin = rotated.relayInfo?.pin ?: current.relayTlsPin,
                    httpsPort = rotated.relayInfo?.httpsPort ?: current.httpsPort,
                    adminToken = rotated.relayInfo?.adminToken ?: current.adminToken
                )
            }
            installError = null
            roomActionName = ""
            roomActionLoading = false
            pendingModeratorLinkRotationRoom = null
            Toast.makeText(context, tr(TextId.MODERATOR_LINK_REFRESHED), Toast.LENGTH_SHORT).show()
        }
    }

    fun refreshOpenRooms(server: InstallServer) {
        if (!server.installed) return
        installScope.launch {
            val result = runCatching {
                onFetchOpenRooms(server.ip, server.httpsPort, server.relayTlsPin, store.readAdminToken(server.id))
            }
            val r = result.getOrElse {
                installError = it.message ?: tr(TextId.COULD_NOT_REFRESH_OPEN_ROOMS)
                return@launch
            }
            updateServer(server.id) { current ->
                current.copy(
                    openRooms = r.openRooms,
                    sshHostKeyPin = current.sshHostKeyPin ?: r.observedSshHostKeyPin,
                    relayTlsPin = r.relayInfo?.pin ?: current.relayTlsPin,
                    httpsPort = r.relayInfo?.httpsPort ?: current.httpsPort,
                    adminToken = r.relayInfo?.adminToken ?: current.adminToken
                )
            }
            installError = null
        }
    }

    fun guestLink(server: InstallServer, room: OpenRoomInfo): String? {
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

    fun guestQrLink(server: InstallServer, room: OpenRoomInfo): String? {
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

    fun moderatorLink(server: InstallServer, room: OpenRoomInfo): String? {
        val pin = server.relayTlsPin?.trim()
        val modKey = store.readModeratorKey(server.id, room).orEmpty().trim()
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

    fun moderatorQrLink(server: InstallServer, room: OpenRoomInfo): String? {
        val pin = server.relayTlsPin?.trim()
        val modKey = store.readModeratorKey(server.id, room).orEmpty().trim()
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

    fun missingPinError() {
        installError = tr(TextId.REINSTALL_SYMPOSIUMRELAY)
        Toast.makeText(context, tr(TextId.TLS_PIN_IS_MISSING), Toast.LENGTH_SHORT).show()
    }

    fun missingModKeyError() {
        installError = tr(TextId.RECREATE_THE_ROOM)
        Toast.makeText(context, tr(TextId.MODERATOR_KEY_IS_MISSING), Toast.LENGTH_SHORT).show()
    }

    fun missingRoomVersionError() {
        val message = tr(TextId.THE_SERVER_DID_NOT_CONFIRM_THE_ROOM_VERSION_REINSTALL_SYMPOSIUMRELAY)
        installError = message
        Toast.makeText(context, trExternal(message), Toast.LENGTH_LONG).show()
    }

    pendingSshHostKeyTrust?.let { trust ->
        AppDialog(
            title = tr(TextId.TRUST_SSH_KEY),
            onDismiss = {
                pendingSshHostKeyTrust = null
                addLoading = false
            },
            content = {
                Text(
                    text = tr(TextId.FIRST_SERVER_CONNECTION, trust.ip),
                    color = appTextPrimaryColor(),
                    fontSize = 14.sp,
                    lineHeight = 19.sp
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(InnerCardShape)
                        .background(appSurfaceElevatedColor())
                        .border(1.dp, Color.Transparent, InnerCardShape)
                        .padding(12.dp)
                ) {
                    Text(
                        text = trust.observedPin,
                        color = appTextPrimaryColor(),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
                Text(
                    text = tr(TextId.IF_THE_SERVER_WAS_REINSTALLED_OR_YOU_DO_NOT_TRUST_THE_NETWORK_VERIFY_THE),
                    color = appTextSecondaryColor(),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            actions = {
                ActionButton(
                    label = tr(TextId.COPY),
                    onClick = { copyToClipboard("ssh_host_key_pin", trust.observedPin) },
                    kind = ActionButtonKind.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    label = tr(TextId.CONTINUE),
                    onClick = {
                        pendingSshHostKeyTrust?.let { confirmed ->
                            pendingSshHostKeyTrust = null
                            finishAddServerAfterHostKeyTrust(confirmed)
                        }
                    },
                    kind = ActionButtonKind.PRIMARY,
                    modifier = Modifier.weight(1f)
                )
            }
        )
    }

    if (showAddRoomDialog && selectedServer != null) {
        HomeCreateRoomDialog(
            server = selectedServer,
            roomName = roomDialogName,
            error = installError,
            loading = roomActionLoading,
            onRoomNameChange = { roomDialogName = it },
            onDismiss = {
                if (!roomActionLoading) {
                    showAddRoomDialog = false
                    roomDialogName = ""
                }
            },
            onCreate = { createRoom(selectedServer) }
        )
    }

    roomSettingsDialogName?.let { roomName ->
        val room = selectedServer?.openRooms?.firstOrNull { it.name == roomName }
        if (selectedServer != null && room != null) {
            AppDialog(
                title = tr(TextId.ROOM_SETTINGS),
                onDismiss = { if (!roomActionLoading) roomSettingsDialogName = null },
                dismissEnabled = !roomActionLoading,
                content = {
                    Text(room.name, color = appTextPrimaryColor(), fontWeight = FontWeight.SemiBold)
                    RoomSettingSwitch(
                        title = tr(TextId.MUTE_MICROPHONES_ON_ENTRY),
                        subtitle = tr(TextId.NEW_PARTICIPANTS_JOIN_WITH_THEIR_MICROPHONE_MUTED),
                        checked = roomSettingsMuteAllByDefault,
                        enabled = !roomActionLoading,
                        onCheckedChange = { roomSettingsMuteAllByDefault = it }
                    )
                    RoomSettingSwitch(
                        title = tr(TextId.ADMIT_WITHOUT_APPROVAL),
                        subtitle = tr(TextId.PARTICIPANTS_JOIN_IMMEDIATELY_WITHOUT_ENTERING_THE_WAITING_ROOM),
                        checked = roomSettingsAdmitWithoutApproval,
                        enabled = !roomActionLoading,
                        onCheckedChange = { roomSettingsAdmitWithoutApproval = it }
                    )
                },
                actions = {
                    ActionButton(
                        label = tr(TextId.SAVE),
                        onClick = { saveRoomSettings(selectedServer, room.name) },
                        enabled = !roomActionLoading && (
                            roomSettingsMuteAllByDefault != room.muteAllByDefault ||
                                roomSettingsAdmitWithoutApproval != room.admitWithoutApproval
                            ),
                        loading = roomActionLoading,
                        modifier = Modifier.weight(1f)
                    )
                }
            )
        }
    }

    if (pendingModeratorLinkRotationRoom != null && selectedServer != null) {
        val roomName = pendingModeratorLinkRotationRoom.orEmpty()
        AppDialog(
            title = tr(TextId.REFRESH_MODERATOR_LINK_2),
            onDismiss = {
                if (!roomActionLoading) pendingModeratorLinkRotationRoom = null
            },
            dismissEnabled = !roomActionLoading,
            content = {
                Text(
                    text = tr(TextId.ROTATE_ROOM_LINK_CONFIRMATION, roomName),
                    color = appTextPrimaryColor(),
                    fontSize = 14.sp,
                    lineHeight = 19.sp
                )
            },
            actions = {
                ActionButton(
                    label = tr(TextId.REFRESH_LINK),
                    onClick = { rotateModeratorLink(selectedServer, roomName) },
                    kind = ActionButtonKind.DANGER,
                    enabled = !roomActionLoading,
                    loading = roomActionLoading,
                    modifier = Modifier.weight(1f)
                )
            }
        )
    }

    if (pendingE2eeKeyRotationRoom != null && selectedServer != null) {
        val roomName = pendingE2eeKeyRotationRoom.orEmpty()
        AppDialog(
            title = tr(TextId.REFRESH_E2EE_KEY),
            onDismiss = { pendingE2eeKeyRotationRoom = null },
            content = {
                Text(
                    text = tr(TextId.ROTATE_E2EE_KEY_CONFIRMATION, roomName),
                    color = appTextPrimaryColor(),
                    fontSize = 14.sp,
                    lineHeight = 19.sp
                )
            },
            actions = {
                ActionButton(
                    label = tr(TextId.REFRESH_E2EE_KEY),
                    onClick = { rotateRoomE2eeKey(selectedServer, roomName) },
                    kind = ActionButtonKind.DANGER,
                    modifier = Modifier.weight(1f)
                )
            }
        )
    }

    pendingDeleteServer?.let { serverToDelete ->
        AppDialog(
            title = tr(TextId.DELETE_SERVER),
            onDismiss = { pendingDeleteServerId = null },
            content = {
                Text(
                    text = tr(TextId.REMOVE_SERVER_CONFIRMATION, serverDisplayAddress(serverToDelete)),
                    color = appTextPrimaryColor(),
                    fontSize = 14.sp,
                    lineHeight = 19.sp
                )
            },
            actions = {
                ActionButton(
                    label = tr(TextId.DELETE),
                    onClick = { deleteServer(serverToDelete) },
                    kind = ActionButtonKind.DANGER,
                    modifier = Modifier.weight(1f)
                )
            }
        )
    }

    if (qrDialogLink != null) {
        val link = qrDialogLink ?: ""

        QrCodeDialog(
            token = link,
            title = qrDialogTitle,
            onDismiss = { qrDialogLink = null },
            onCopy = { copyToClipboard("room_deep_link", link) }
        )
    }

    when (view) {
        InstallView.GRID -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Spacer(Modifier.height(6.dp))

                        if (addSuccessMessage != null) {
                            SuccessMessage(addSuccessMessage ?: "")
                        }
                            ActionButton(
                                label = tr(TextId.ADD_SERVER),
                                onClick = { view = InstallView.ADD },
                                modifier = Modifier.fillMaxWidth(),
                                kind = ActionButtonKind.PRIMARY
                            )
                    }
                }

                if (servers.isEmpty()) {
                    item {
                        SectionCard(
                            title = tr(TextId.NOTHING_HERE_YET),
                            subtitle = tr(TextId.ADD_A_SERVER_TO_START_INSTALLATION)
                        ) {}
                    }
                } else {
                    items(servers, key = { it.id }) { server ->
                        ServerListCard(
                            server = server,
                            onClick = {
                                selectedServerId = server.id
                                view = InstallView.DETAIL
                            },
                            onDelete = { pendingDeleteServerId = server.id }
                        )
                    }
                }
            }
        }

        InstallView.ADD -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    SectionCard(
                        title = tr(TextId.NEW_SERVER)
                    ) {
                        OutlinedTextField(
                            value = addIp,
                            onValueChange = { addIp = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(tr(TextId.IP_ADDRESS)) },
                            colors = textFieldColors()
                        )

                        OutlinedTextField(
                            value = addUser,
                            onValueChange = { addUser = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(tr(TextId.USER)) },
                            colors = textFieldColors()
                        )

                        OutlinedTextField(
                            value = addPassword,
                            onValueChange = { addPassword = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            label = { Text(tr(TextId.PASSWORD)) },
                            colors = textFieldColors()
                        )

                        Text(
                            text = sshPasswordStorageNotice(),
                            color = appTextSecondaryColor(),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )

                        if (addError != null) {
                            ErrorMessage(addError ?: "")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ActionButton(
                                label = tr(TextId.BACK),
                                onClick = {
                                    view = InstallView.GRID
                                    addIp = ""
                                    addUser = ""
                                    addPassword = ""
                                    addError = null
                                },
                                modifier = Modifier.weight(1f),
                                kind = ActionButtonKind.SECONDARY
                            )
                            ActionButton(
                                label = tr(TextId.ADD),
                                onClick = { addServer() },
                                modifier = Modifier.weight(1f),
                                enabled = !addLoading,
                                loading = addLoading
                            )
                        }
                    }
                }
            }
        }

        InstallView.DETAIL -> {
            val server = selectedServer
            if (server == null) {
                view = InstallView.GRID
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        ActionButton(
                            label = tr(TextId.BACK_TO_SERVERS),
                            onClick = {
                                view = InstallView.GRID
                                selectedServerId = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            kind = ActionButtonKind.SECONDARY
                        )
                    }

                    item {
                        SectionCard(
                            title = serverDisplayAddress(server),
                            trailing = {
                                InfoBadge(
                                    label = serverStateLabel(server),
                                    color = serverStateColor(server)
                                )
                            },
                            backgroundColor = appSurfaceElevatedColor()
                        ) {
                            Text(
                                text = tr(TextId.OPEN_ROOMS_COUNT, openRoomsLabel(server.openRooms.size)),
                                color = appTextSecondaryColor(),
                                fontSize = 14.sp
                            )

                            if (!server.installed) {
                                RelayAdvancedSettingsButton(
                                    config = server.relayCapacity,
                                    enabled = !installing && !removingRelay && !updatingRelay,
                                    onSave = { capacity ->
                                        updateServer(server.id) { current ->
                                            current.copy(relayCapacity = capacity)
                                        }
                                    }
                                )
                                ActionButton(
                                    label = tr(TextId.INSTALL_SYMPOSIUMRELAY),
                                    onClick = { startInstallation(server) },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !installing && !removingRelay && !updatingRelay,
                                    loading = installing
                                )
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    ActionButton(
                                        label = tr(TextId.REMOVE_SYMPOSIUMRELAY),
                                        onClick = { removeRelay(server) },
                                        modifier = Modifier.weight(1f),
                                        kind = ActionButtonKind.DANGER,
                                        enabled = !removingRelay && !installing && !updatingRelay,
                                        loading = removingRelay
                                    )

                                    if (server.relayVersionState == RelayVersionState.OUTDATED) {
                                        ActionButton(
                                            label = tr(TextId.REFRESH),
                                            onClick = { updateRelay(server) },
                                            modifier = Modifier.weight(1f),
                                            enabled = !removingRelay && !installing && !updatingRelay,
                                            loading = updatingRelay,
                                            icon = Icons.Filled.Refresh
                                        )
                                    }
                                }
                            }

                            if (installError != null) {
                                ErrorMessage(installError ?: "")
                            }

                            if (installSuccess) {
                                SuccessMessage(tr(TextId.OPERATION_COMPLETED_SUCCESSFULLY))
                            }
                        }
                    }

                    if (server.installed) {
                        item {
                            CollapsibleSectionCard(
                                title = tr(TextId.ROOMS),
                                subtitle = tr(TextId.OPEN_ROOMS_COUNT, openRoomsLabel(server.openRooms.size)),
                                expanded = roomsExpanded,
                                onToggle = { roomsExpanded = !roomsExpanded }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = tr(TextId.OPEN_ROOMS),
                                        color = appTextPrimaryColor(),
                                        fontSize = 15.sp,
                                        lineHeight = 20.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TopIconAction(
                                            icon = Icons.Filled.Refresh,
                                            onClick = { refreshOpenRooms(server) },
                                            enabled = !roomActionLoading,
                                            contentDescription = tr(TextId.REFRESH_ROOM_LIST)
                                        )
                                        TopIconAction(
                                            icon = Icons.Filled.Add,
                                            onClick = {
                                                roomDialogName = ""
                                                showAddRoomDialog = true
                                            },
                                            enabled = !roomActionLoading && serverAllowsRoomCreation(server),
                                            contentDescription = tr(TextId.ADD_ROOM)
                                        )
                                    }
                                }

                                if (!serverAllowsRoomCreation(server)) {
                                    Text(
                                        text = if (serverNeedsRelayUpdate(server)) {
                                            tr(TextId.UPDATE_RELAY_TO_ADD_ROOM)
                                        } else {
                                            tr(TextId.ROOM_AVAILABLE_AFTER_VERSION_CHECK)
                                        },
                                        color = AppWarning,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp
                                    )
                                }

                                if (server.openRooms.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(InnerCardShape)
                                            .background(appSurfaceColor().copy(alpha = 0.72f))
                                            .border(1.dp, appGrayControlBorderColor(), InnerCardShape)
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = tr(TextId.THERE_ARE_NO_OPEN_ROOMS),
                                            color = appTextSecondaryColor(),
                                            fontSize = 13.sp
                                        )
                                    }
                                } else {
                                    server.openRooms.forEach { openRoom ->
                                        RoomItemCard(
                                            room = openRoom,
                                            expanded = expandedRoomName == openRoom.name,
                                            loading = roomActionLoading && roomActionName == openRoom.name,
                                            onToggle = {
                                                expandedRoomName =
                                                    if (expandedRoomName == openRoom.name) null else openRoom.name
                                            },
                                            onCopyGuest = {
                                                val link = guestLink(server, openRoom)
                                                when {
                                                    server.relayTlsPin.isNullOrBlank() -> missingPinError()
                                                    verifiedRoomRequiredVersion(openRoom) == null -> missingRoomVersionError()
                                                    link != null -> copyToClipboard("guest_https_room_link", link)
                                                }
                                            },
                                            onQrGuest = {
                                                val link = guestQrLink(server, openRoom)
                                                when {
                                                    server.relayTlsPin.isNullOrBlank() -> missingPinError()
                                                    verifiedRoomRequiredVersion(openRoom) == null -> missingRoomVersionError()
                                                    link != null -> {
                                                        qrDialogTitle = tr(TextId.GUEST_QR_CODE)
                                                        qrDialogLink = link
                                                    }
                                                }
                                            },
                                            onCopyModerator = {
                                                val link = moderatorLink(server, openRoom)
                                                when {
                                                    server.relayTlsPin.isNullOrBlank() -> missingPinError()
                                                    !openRoom.moderatorKeyAvailable -> missingModKeyError()
                                                    verifiedRoomRequiredVersion(openRoom) == null -> missingRoomVersionError()
                                                    link != null -> copyToClipboard("moderator_https_room_link", link)
                                                }
                                            },
                                            onQrModerator = {
                                                val link = moderatorQrLink(server, openRoom)
                                                when {
                                                    server.relayTlsPin.isNullOrBlank() -> missingPinError()
                                                    !openRoom.moderatorKeyAvailable -> missingModKeyError()
                                                    verifiedRoomRequiredVersion(openRoom) == null -> missingRoomVersionError()
                                                    link != null -> {
                                                        qrDialogTitle = tr(TextId.MODERATOR_QR_CODE)
                                                        qrDialogLink = link
                                                    }
                                                }
                                            },
                                            onRotateModerator = {
                                                pendingModeratorLinkRotationRoom = openRoom.name
                                            },
                                            onRotateE2ee = {
                                                pendingE2eeKeyRotationRoom = openRoom.name
                                            },
                                            onEditSettings = {
                                                roomSettingsDialogName = openRoom.name
                                                roomSettingsMuteAllByDefault = openRoom.muteAllByDefault
                                                roomSettingsAdmitWithoutApproval = openRoom.admitWithoutApproval
                                            },
                                            onClose = {
                                                roomActionName = openRoom.name
                                                applyRoomAction(server, open = false)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (installLogs.isNotEmpty()) {
                        item {
                            CollapsibleSectionCard(
                                title = when {
                                    installing -> tr(TextId.INSTALLING_SYMPOSIUMRELAY)
                                    removingRelay -> tr(TextId.REMOVING_SYMPOSIUMRELAY)
                                    updatingRelay -> tr(TextId.UPDATING_SYMPOSIUMRELAY)
                                    else -> tr(TextId.LATEST_OPERATION_LOGS)
                                },
                                expanded = installLogsExpanded,
                                onToggle = { installLogsExpanded = !installLogsExpanded }
                            ) {
                                LazyColumn(
                                    state = installLogListState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 220.dp, max = 320.dp)
                                        .clip(InnerCardShape)
                                        .background(appSurfaceElevatedColor())
                                        .border(1.dp, Color.Transparent, InnerCardShape)
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(installLogs) { line ->
                                        Text(line, color = appTextPrimaryColor(), fontSize = 13.sp, lineHeight = 18.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ErrorMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(InnerCardShape)
            .background(AppError.copy(alpha = 0.12f))
            .border(1.dp, AppError.copy(alpha = 0.55f), InnerCardShape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(text, color = appTextPrimaryColor(), fontSize = 13.sp)
    }
}

@Composable
internal fun SuccessMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(InnerCardShape)
            .background(AppSuccess.copy(alpha = 0.14f))
            .border(1.dp, AppSuccess, InnerCardShape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(text, color = AppSuccess, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}

@Composable
internal fun ServerListCard(
    server: InstallServer,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = appSurfaceElevatedColor(),
        shape = CardShape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .border(1.dp, appGrayControlBorderColor(), CardShape)
                .clickable(onClick = onClick)
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = serverDisplayAddress(server),
                            color = appTextPrimaryColor(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = tr(TextId.OPEN_ROOMS_COUNT, openRoomsLabel(server.openRooms.size)),
                            color = appTextSecondaryColor(),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = tr(TextId.DELETE_SERVER),
                            tint = AppError
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.weight(1f))

                    InfoBadge(
                        label = serverStateLabel(server),
                        color = serverStateColor(server),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
        }
    }
}

@Composable
internal fun RoomsSectionCard(
    roomNames: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    actionsEnabled: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        color = appRoomSurfaceColor(),
        shape = CardShape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, Color.Transparent, CardShape)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(InnerCardShape)
                    .clickable(onClick = onToggle)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(AppButtonShape)
                        .background(AppAccent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.GridView,
                        contentDescription = null,
                        tint = AppAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = tr(TextId.ROOMS),
                        color = appTextPrimaryColor(),
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = tr(TextId.OPEN_ROOMS_COUNT, openRoomsLabel(roomNames.size)),
                        color = appTextSecondaryColor(),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = tr(if (expanded) TextId.COLLAPSE_ROOMS else TextId.EXPAND_ROOMS),
                    tint = appTextSecondaryColor(),
                    modifier = Modifier.size(24.dp)
                )
            }

            AnimatedVisibility(visible = !expanded) {
                Text(
                    text = when {
                        roomNames.isEmpty() -> tr(TextId.THERE_ARE_NO_OPEN_ROOMS)
                        roomNames.size <= 2 -> roomNames.joinToString(" · ")
                        else -> roomNames.take(2).joinToString(" · ") + " · +${roomNames.size - 2}"
                    },
                    color = appTextSecondaryColor(),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ActionButton(
                            label = tr(TextId.REFRESH),
                            onClick = onRefresh,
                            modifier = Modifier.weight(1f),
                            kind = ActionButtonKind.SECONDARY,
                            enabled = actionsEnabled,
                            icon = Icons.Filled.Refresh
                        )
                        ActionButton(
                            label = tr(TextId.ADD_ROOM),
                            onClick = onAdd,
                            modifier = Modifier.weight(1f),
                            kind = ActionButtonKind.SECONDARY,
                            enabled = actionsEnabled,
                            icon = Icons.Filled.Add
                        )
                    }

                    content()
                }
            }
        }
    }
}

@Composable
internal fun RoomItemCard(
    room: OpenRoomInfo,
    expanded: Boolean,
    loading: Boolean,
    onToggle: () -> Unit,
    onCopyGuest: () -> Unit,
    onQrGuest: () -> Unit,
    onCopyModerator: () -> Unit,
    onQrModerator: () -> Unit,
    onRotateModerator: () -> Unit,
    onRotateE2ee: () -> Unit,
    onEditSettings: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = appRoomSurfaceColor(),
        shape = InnerCardShape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, appGrayControlBorderColor(), InnerCardShape)
                .animateContentSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(InnerCardShape)
                    .clickable(onClick = onToggle)
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(AppButtonShape)
                        .background(AppAccent.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.MeetingRoom,
                        contentDescription = null,
                        tint = AppAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = room.name,
                        color = appTextPrimaryColor(),
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    )
                    Text(
                        text = if (!room.moderatorKeyAvailable) tr(TextId.MODERATOR_LINK_UNAVAILABLE) else tr(TextId.LINKS_ARE_READY),
                        color = if (!room.moderatorKeyAvailable) AppError else appTextSecondaryColor(),
                        fontSize = 12.sp
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = tr(if (expanded) TextId.COLLAPSE_ROOM else TextId.EXPAND_ROOM),
                    tint = appTextSecondaryColor(),
                    modifier = Modifier.size(22.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RoomLinkActionsRow(
                        title = tr(TextId.GUEST),
                        subtitle = tr(TextId.CONNECTION_LINK_WITHOUT_MODERATOR_PERMISSIONS),
                        badgeColor = appTextSecondaryColor(),
                        onCopy = onCopyGuest,
                        onQr = onQrGuest,
                        enabled = !loading
                    )

                    RoomLinkActionsRow(
                        title = tr(TextId.MODERATOR),
                        subtitle = tr(TextId.LINK_WITH_ROOM_MANAGEMENT_PERMISSIONS),
                        badgeColor = AppAccent,
                        onCopy = onCopyModerator,
                        onQr = onQrModerator,
                        enabled = !loading
                    )

                    ActionButton(
                        label = tr(TextId.REFRESH_MODERATOR_LINK),
                        onClick = onRotateModerator,
                        modifier = Modifier.fillMaxWidth(),
                        kind = ActionButtonKind.SECONDARY,
                        enabled = !loading
                    )
                    ActionButton(
                        label = tr(TextId.REFRESH_E2EE_KEY),
                        onClick = onRotateE2ee,
                        modifier = Modifier.fillMaxWidth(),
                        kind = ActionButtonKind.SECONDARY,
                        enabled = !loading
                    )

                    ActionButton(
                        label = tr(TextId.ROOM_SETTINGS),
                        onClick = onEditSettings,
                        modifier = Modifier.fillMaxWidth(),
                        kind = ActionButtonKind.SECONDARY,
                        enabled = !loading
                    )

                    ActionButton(
                        label = tr(TextId.CLOSE_ROOM),
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth(),
                        kind = ActionButtonKind.SECONDARY,
                        textColorOverride = AppError,
                        enabled = !loading,
                        loading = loading
                    )
                }
            }
        }
    }
}

@Composable
internal fun RoomLinkActionsRow(
    title: String,
    subtitle: String,
    badgeColor: Color,
    onCopy: () -> Unit,
    onQr: () -> Unit,
    enabled: Boolean,
    comfortable: Boolean = false
) {
    if (comfortable) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(appRoomSurfaceColor())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(badgeColor)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        color = appTextPrimaryColor(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            color = appTextSecondaryColor(),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionButton(
                    label = tr(TextId.COPY),
                    onClick = onCopy,
                    modifier = Modifier.weight(1f),
                    kind = ActionButtonKind.SECONDARY,
                    icon = Icons.Filled.ContentCopy,
                    enabled = enabled
                )
                ActionButton(
                    label = tr(TextId.QR_CODE),
                    onClick = onQr,
                    modifier = Modifier.weight(1f),
                    kind = ActionButtonKind.SECONDARY,
                    icon = Icons.Filled.QrCode2,
                    enabled = enabled
                )
            }
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(appSurfaceElevatedColor().copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(50))
                .background(badgeColor)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = title,
                color = appTextPrimaryColor(),
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = appTextSecondaryColor(),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        RoomLinkIconButton(
            icon = Icons.Filled.ContentCopy,
            contentDescription = tr(TextId.COPY),
            onClick = onCopy,
            enabled = enabled
        )
        RoomLinkIconButton(
            icon = Icons.Filled.QrCode2,
            contentDescription = tr(TextId.QR_CODE),
            onClick = onQr,
            enabled = enabled
        )
    }
}

@Composable
private fun RoomLinkIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(38.dp)
            .clip(AppButtonShape)
            .background(appSurfaceColor())
            .border(1.dp, appGrayControlBorderColor(), AppButtonShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) appTextPrimaryColor() else appTextMutedColor(),
            modifier = Modifier.size(18.dp)
        )
    }
}
