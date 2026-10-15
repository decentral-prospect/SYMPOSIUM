package com.decentralprospect.symposium

import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSink
import org.webrtc.VideoTrack
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

@Composable
private fun CallParticipantsHeader(
    participantCount: Int,
    mode: CallViewMode,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggleMode: () -> Unit
) {
    var searchVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Surface(color = callBackgroundColor()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = tr(TextId.PARTICIPANTS_COUNT, participantCount),
                    color = callTextPrimaryColor(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                SecureCallBadge()
                CallHeaderIconButton(
                    icon = if (searchVisible) Icons.Filled.Close else Icons.Filled.Search,
                    contentDescription = tr(if (searchVisible) TextId.CLOSE_SEARCH else TextId.SEARCH_PARTICIPANTS),
                    selected = searchVisible,
                    onClick = {
                        searchVisible = !searchVisible
                        if (!searchVisible) {
                            onQueryChange("")
                            focusManager.clearFocus()
                        }
                    }
                )
                CallHeaderIconButton(
                    icon = if (mode == CallViewMode.GRID) Icons.Filled.CenterFocusStrong else Icons.Filled.GridView,
                    contentDescription = tr(if (mode == CallViewMode.GRID) TextId.SHOW_SPEAKER else TextId.SHOW_GRID),
                    onClick = {
                        focusManager.clearFocus()
                        onToggleMode()
                    }
                )
            }
            if (searchVisible) {
                ParticipantSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                )
            }
        }
    }
}

@Composable
internal fun ParticipantsTopBar(
    peers: List<PeerStatus>,
    selfPeerId: String,
    pinnedPeerId: String?,
    peerHandStates: Map<String, Boolean>,
    peerMicOffStates: Map<String, Boolean>,
    peerVideoOffStates: Map<String, Boolean>,
    onPin: (String?) -> Unit,
    query: String
) {
    val visiblePeers = filterParticipants(peers, query)
    Surface(color = callBackgroundColor()) {
        if (visiblePeers.isEmpty()) {
            Text(
                text = tr(TextId.NO_PARTICIPANTS_FOUND),
                color = callTextSecondaryColor(),
                fontSize = 12.sp,
                modifier = Modifier.padding(12.dp)
            )
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(visiblePeers, key = { it.peerId }) { peer ->
                    val isPinned = peer.peerId == pinnedPeerId
                    TopBarPeerVideoTile(
                        peer = peer,
                        selfPeerId = selfPeerId,
                        pinned = isPinned,
                        handRaised = peerHandStates[peer.peerId] == true,
                        audioMuted = peerMicOffStates[peer.peerId] == true,
                        videoOff = peerVideoOffStates[peer.peerId] == true,
                        modifier = Modifier.width(108.dp),
                        onClick = { onPin(if (isPinned) null else peer.peerId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CallHeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (selected) accessibleAccentColor() else callControlBackgroundColor())
            .border(1.dp, callBorderColor(0.75f), CircleShape)
            .semantics { this.selected = selected }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) AppOnAccent else callIconColor(),
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun ParticipantSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        trailingIcon = if (query.isNotBlank()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = tr(TextId.CLEAR_SEARCH))
                }
            }
        } else {
            null
        },
        placeholder = { Text(tr(TextId.SEARCH_PARTICIPANTS), fontSize = 13.sp) },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = callTextPrimaryColor(),
            unfocusedTextColor = callTextPrimaryColor(),
            focusedBorderColor = accessibleAccentColor(),
            unfocusedBorderColor = callBorderColor(),
            focusedContainerColor = callFieldBackgroundColor(),
            unfocusedContainerColor = callFieldBackgroundColor(),
            focusedLeadingIconColor = accessibleAccentColor(),
            unfocusedLeadingIconColor = callTextSecondaryColor(),
            focusedPlaceholderColor = callTextSecondaryColor(),
            unfocusedPlaceholderColor = callTextSecondaryColor()
        )
    )
}

private fun filterParticipants(peers: List<PeerStatus>, query: String): List<PeerStatus> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return peers
    return peers.filter { peer ->
        displayName(peer).contains(normalized, ignoreCase = true) ||
            peer.peerId.contains(normalized, ignoreCase = true)
    }
}

@Composable
internal fun ParticipantsSidePanel(
    peers: List<PeerStatus>,
    selfPeerId: String,
    pinnedPeerId: String?,
    peerHandStates: Map<String, Boolean>,
    peerMicOffStates: Map<String, Boolean>,
    peerVideoOffStates: Map<String, Boolean>,
    onPin: (String?) -> Unit,
    query: String,
    modifier: Modifier = Modifier
) {
    val visiblePeers = filterParticipants(peers, query)

    Surface(modifier = modifier, color = callBackgroundColor()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, callBorderColor())
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (visiblePeers.isEmpty()) {
                Text(
                    text = tr(TextId.NO_PARTICIPANTS_FOUND),
                    color = callTextSecondaryColor(),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(6.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(visiblePeers, key = { it.peerId }) { peer ->
                        val isPinned = peer.peerId == pinnedPeerId
                        SidePanelPeerRow(
                            peer = peer,
                            selfPeerId = selfPeerId,
                            pinned = isPinned,
                            handRaised = peerHandStates[peer.peerId] == true,
                            audioMuted = peerMicOffStates[peer.peerId] == true,
                            videoOff = peerVideoOffStates[peer.peerId] == true,
                            onClick = { onPin(if (isPinned) null else peer.peerId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SidePanelPeerRow(
    peer: PeerStatus,
    selfPeerId: String,
    pinned: Boolean,
    handRaised: Boolean,
    audioMuted: Boolean,
    videoOff: Boolean,
    onClick: () -> Unit
) {
    val speaking = peer.audioAttached && !audioMuted && peer.audioLevel >= 0.035f
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(callFieldBackgroundColor())
            .border(
                1.dp,
                if (pinned || speaking) accessibleAccentColor() else callBorderColor(),
                shape
            )
            .clickable(onClick = onClick)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AspectFittedPeerCard(
            peer = peer,
            selfPeerId = selfPeerId,
            bigName = false,
            handRaised = handRaised,
            audioMuted = audioMuted,
            videoOff = videoOff,
            showLabel = false,
            showStatusOverlay = false,
            modifier = Modifier.size(64.dp)
        )

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = displayName(peer),
                color = callTextPrimaryColor(),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                ParticipantStatusGroup(
                    indicators = participantIndicators(
                        handRaised = handRaised,
                        audioMuted = audioMuted || !peer.audioAttached,
                        videoOff = videoOff || !peer.videoAttached
                    ),
                    onVideo = false
                )
                NetworkQualityIndicator(
                    pingMs = peer.pingMs,
                    modifier = Modifier.size(width = 22.dp, height = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun SecureCallBadge() {
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(AppSuccess.copy(alpha = 0.14f))
            .semantics(mergeDescendants = true) {
                contentDescription = tr(TextId.END_TO_END_ENCRYPTION_IS_ON)
            }
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = AppSuccess,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = tr(TextId.E2EE_LABEL),
            color = AppSuccess,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun ReconnectOverlay(
    visible: Boolean,
    onCancel: () -> Unit
) {
    if (!visible) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .graphicsLayer { alpha = 1f }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .clip(RoundedCornerShape(16.dp))
                .background(callSurfaceColor().copy(alpha = 0.94f))
                .border(1.dp, callBorderColor(), RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = AppAccent)
            Spacer(Modifier.height(12.dp))
            Text(tr(TextId.RECONNECTING), color = callIconColor(), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))

            ActionButton(
                label = tr(TextId.CANCEL),
                onClick = onCancel,
                kind = ActionButtonKind.SECONDARY
            )
        }
    }
}

@Composable
internal fun ModeratorFloatingButton(
    pendingCount: Int,
    raisedHandsCount: Int,
    muteAllEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = if (muteAllEnabled) Color.White else AppOnAccent

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (muteAllEnabled) AppError else AppAccent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Settings, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            Text(tr(TextId.MOD), color = contentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (pendingCount > 0) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(pendingCount.toString(), color = contentColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
            if (raisedHandsCount > 0) {
                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(contentColor.copy(alpha = 0.16f))
                        .padding(horizontal = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        tr(TextId.RAISED_HANDS_COUNT, raisedHandsCount),
                        color = contentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}


@Composable
internal fun ModeratorPanelOverlay(
    visible: Boolean,
    pendingPeers: List<LobbyPeerStatus>,
    peers: List<PeerStatus>,
    selfPeerId: String,
    peerMuteStates: Map<String, Boolean>,
    peerMicOffStates: Map<String, Boolean>,
    peerHandStates: Map<String, Boolean>,
    muteAllEnabled: Boolean,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onKick: (String) -> Unit,
    onMute: (String) -> Unit,
    onUnmute: (String) -> Unit,
    onLowerHand: (String) -> Unit,
    onSetMuteAll: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    if (!visible) return

    var participantQuery by remember { mutableStateOf("") }
    val normalizedQuery = participantQuery.trim()
    val sortedPending = pendingPeers
        .filter { peer ->
            normalizedQuery.isEmpty() ||
                displayName(peer).contains(normalizedQuery, ignoreCase = true) ||
                peer.peerId.contains(normalizedQuery, ignoreCase = true)
        }
        .sortedBy { displayName(it).lowercase() }
    val sortedPeers = peers
        .filter { it.peerId != selfPeerId }
        .filter { peer ->
            normalizedQuery.isEmpty() ||
                displayName(peer).contains(normalizedQuery, ignoreCase = true) ||
                peer.peerId.contains(normalizedQuery, ignoreCase = true)
        }
        .sortedWith(
            compareByDescending<PeerStatus> { peerHandStates[it.peerId] == true }
                .thenBy { displayName(it).lowercase() }
        )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.48f))
            .padding(horizontal = 12.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f),
            color = callSurfaceColor(),
            shape = CardShape,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .border(1.dp, callBorderColor(), CardShape)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = tr(TextId.MODERATION_PANEL),
                            color = callTextPrimaryColor(),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = tr(
                                TextId.LOBBY_PARTICIPANT_HAND_COUNTS,
                                pendingPeers.size,
                                peers.size,
                                peerHandStates.count { it.value && it.key != selfPeerId }
                            ),
                            color = callTextSecondaryColor(),
                            fontSize = 12.sp
                        )
                    }

                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = tr(TextId.CLOSE),
                            tint = callTextSecondaryColor()
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(InnerCardShape)
                        .background(callFieldBackgroundColor().copy(alpha = 0.78f))
                        .border(1.dp, callBorderColor().copy(alpha = 0.65f), InnerCardShape)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = tr(TextId.MUTE_ALL),
                            color = callTextPrimaryColor(),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )

                        Text(
                            text = if (muteAllEnabled) {
                                tr(TextId.ALL_GUESTS_ARE_MUTED)
                            } else {
                                tr(TextId.GUESTS_CAN_SPEAK)
                            },
                            color = callTextSecondaryColor(),
                            fontSize = 11.sp
                        )
                    }

                    ModeratorSmallToggleButton(
                        active = muteAllEnabled,
                        onClick = { onSetMuteAll(!muteAllEnabled) }
                    )
                }

                ParticipantSearchField(
                    query = participantQuery,
                    onQueryChange = { participantQuery = it },
                    modifier = Modifier.fillMaxWidth()
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    item {
                        ModeratorSectionHeader(
                            title = tr(TextId.WAITING_TO_JOIN),
                            count = sortedPending.size
                        )
                    }

                    if (sortedPending.isEmpty()) {
                        item {
                            ModeratorEmptyRow(tr(TextId.THE_LOBBY_IS_EMPTY))
                        }
                    } else {
                        items(sortedPending, key = { "pending:${it.peerId}" }) { p ->
                            LobbyPeerRowCompact(
                                peer = p,
                                onApprove = { onApprove(p.peerId) },
                                onReject = { onReject(p.peerId) }
                            )
                        }
                    }

                    item {
                        Spacer(Modifier.height(2.dp))
                        ModeratorSectionHeader(
                            title = tr(TextId.PARTICIPANTS),
                            count = sortedPeers.size
                        )
                    }

                    if (sortedPeers.isEmpty()) {
                        item {
                            ModeratorEmptyRow(tr(TextId.THERE_ARE_NO_OTHER_PARTICIPANTS_YET))
                        }
                    } else {
                        items(sortedPeers, key = { "peer:${it.peerId}" }) { p ->
                            val muted = peerMuteStates[p.peerId] ?: muteAllEnabled
                            val micOff = peerMicOffStates[p.peerId] == true
                            val handRaised = peerHandStates[p.peerId] == true

                            ModeratorPeerRowCompact(
                                peer = p,
                                muted = muted,
                                micOff = micOff,
                                handRaised = handRaised,
                                onKick = { onKick(p.peerId) },
                                onMute = { onMute(p.peerId) },
                                onUnmute = { onUnmute(p.peerId) },
                                onLowerHand = { onLowerHand(p.peerId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ModeratorSmallToggleButton(
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (active) AppError.copy(alpha = 0.18f)
                else callControlBackgroundColor().copy(alpha = 0.85f)
            )
            .border(
                width = 1.dp,
                color = if (active) AppError.copy(alpha = 0.65f) else callBorderColor(),
                shape = RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (active) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = null,
                tint = if (active) AppError else callIconColor(),
                modifier = Modifier.size(16.dp)
            )

            Text(
                text = if (active) tr(TextId.ON_2) else tr(TextId.OFF_2),
                color = if (active) callTextPrimaryColor() else callIconColor(),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
internal fun ModeratorSectionHeader(
    title: String,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = callTextPrimaryColor(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )

        InfoBadge(
            label = count.toString(),
            color = callTextSecondaryColor()
        )
    }
}

@Composable
internal fun ModeratorEmptyRow(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(InnerCardShape)
            .background(callFieldBackgroundColor())
            .border(1.dp, callBorderColor(), InnerCardShape)
            .padding(12.dp)
    ) {
        Text(
            text = text,
            color = callTextSecondaryColor(),
            fontSize = 13.sp
        )
    }
}

@Composable
internal fun LobbyPeerRowCompact(
    peer: LobbyPeerStatus,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(InnerCardShape)
            .background(callFieldBackgroundColor())
            .border(1.dp, callBorderColor(), InnerCardShape)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = displayName(peer),
                color = callTextPrimaryColor(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = peer.peerId,
                color = callTextSecondaryColor(),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        ActionButton(
            label = tr(TextId.YES),
            onClick = onApprove,
            modifier = Modifier.width(64.dp),
            kind = ActionButtonKind.PRIMARY
        )

        ActionButton(
            label = tr(TextId.NO),
            onClick = onReject,
            modifier = Modifier.width(68.dp),
            kind = ActionButtonKind.DANGER
        )
    }
}

@Composable
internal fun ModeratorPeerRowCompact(
    peer: PeerStatus,
    muted: Boolean,
    micOff: Boolean,
    handRaised: Boolean,
    onKick: () -> Unit,
    onMute: () -> Unit,
    onUnmute: () -> Unit,
    onLowerHand: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(InnerCardShape)
            .background(callFieldBackgroundColor())
            .border(
                width = 1.dp,
                color = if (handRaised) AppAccent.copy(alpha = 0.75f) else callBorderColor(),
                shape = InnerCardShape
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (handRaised) {
                        Text("✋", fontSize = 14.sp)
                    }

                    Text(
                        text = displayName(peer),
                        color = callTextPrimaryColor(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = peer.peerId,
                    color = callTextSecondaryColor(),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InfoBadge(
                    label = if (micOff) tr(TextId.MIC_OFF) else tr(TextId.MIC_ON),
                    color = if (micOff) AppError else AppSuccess
                )

                InfoBadge(
                    label = if (muted) tr(TextId.MUTED) else tr(TextId.OPEN),
                    color = if (muted) AppError else AppSuccess
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionButton(
                label = if (muted) tr(TextId.UNMUTE) else tr(TextId.MUTE),
                onClick = if (muted) onUnmute else onMute,
                modifier = Modifier.weight(1f),
                kind = ActionButtonKind.SECONDARY,
                icon = if (muted) Icons.Filled.Mic else Icons.Filled.MicOff
            )

            if (handRaised) {
                ActionButton(
                    label = tr(TextId.LOWER_HAND),
                    onClick = onLowerHand,
                    modifier = Modifier.weight(1f),
                    kind = ActionButtonKind.SECONDARY
                )
            }

            ActionButton(
                label = tr(TextId.REMOVE),
                onClick = onKick,
                modifier = Modifier.weight(1f),
                kind = ActionButtonKind.DANGER
            )
        }
    }
}

@Composable
internal fun LobbyPeerRow(
    peer: LobbyPeerStatus,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(InnerCardShape)
            .background(callFieldBackgroundColor())
            .border(1.dp, callBorderColor(), InnerCardShape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(displayName(peer), color = callTextPrimaryColor(), fontWeight = FontWeight.SemiBold)
            Text(peer.peerId, color = callTextSecondaryColor(), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ActionButton(label = tr(TextId.ADMIT), onClick = onApprove, modifier = Modifier.width(102.dp), kind = ActionButtonKind.PRIMARY)
        ActionButton(label = tr(TextId.REJECT), onClick = onReject, modifier = Modifier.width(106.dp), kind = ActionButtonKind.DANGER)
    }
}

@Composable
internal fun ModeratorPeerRow(
    peer: PeerStatus,
    muted: Boolean,
    micOff: Boolean,
    handRaised: Boolean,
    onKick: () -> Unit,
    onMute: () -> Unit,
    onUnmute: () -> Unit,
    onLowerHand: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(InnerCardShape)
            .background(callFieldBackgroundColor())
            .border(1.dp, callBorderColor(), InnerCardShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(displayName(peer), color = callTextPrimaryColor(), fontWeight = FontWeight.SemiBold)
                Text(peer.peerId, color = callTextSecondaryColor(), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (handRaised) {
                    InfoBadge(label = tr(TextId.HAND_RAISED), color = AppAccent)
                }
                InfoBadge(
                    label = if (micOff) tr(TextId.MIC_OFF) else tr(TextId.MIC_ON),
                    color = if (micOff) AppError else AppSuccess
                )
                InfoBadge(label = if (muted) tr(TextId.MUTED) else tr(TextId.CAN_SPEAK), color = if (muted) AppError else AppSuccess)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                label = if (muted) tr(TextId.UNMUTE_2) else tr(TextId.MUTE_2),
                onClick = if (muted) onUnmute else onMute,
                modifier = Modifier.weight(1f),
                kind = ActionButtonKind.SECONDARY,
                icon = if (muted) Icons.Filled.Mic else Icons.Filled.MicOff
            )
            ActionButton(
                label = tr(TextId.REMOVE),
                onClick = onKick,
                modifier = Modifier.weight(1f),
                kind = ActionButtonKind.DANGER
            )
        }
        if (handRaised) {
            ActionButton(
                label = tr(TextId.LOWER_HAND),
                onClick = onLowerHand,
                modifier = Modifier.fillMaxWidth(),
                kind = ActionButtonKind.SECONDARY
            )
        }
    }
}

@Composable
internal fun CallInProgressView(
    peers: List<PeerStatus>,
    selfPeerId: String,
    mode: CallViewMode,
    pinnedPeerId: String?,
    peerHandStates: Map<String, Boolean>,
    peerMicOffStates: Map<String, Boolean>,
    peerVideoOffStates: Map<String, Boolean>,
    onPin: (String?) -> Unit,
    onModeChange: (CallViewMode) -> Unit,
    onSwitchCamera: () -> Unit,
    pipOffsetX: Float,
    pipOffsetY: Float,
    onMovePip: (dx: Float, dy: Float) -> Unit,
    pipExpanded: Boolean,
    onTogglePipExpanded: () -> Unit,
    pictureInPictureMode: Boolean = false
) {
    var participantQuery by remember { mutableStateOf("") }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val focusManager = LocalFocusManager.current

    if (pictureInPictureMode) {
        PictureInPictureCallView(
            peers = peers,
            selfPeerId = selfPeerId,
            pinnedPeerId = pinnedPeerId,
            peerMicOffStates = peerMicOffStates,
            peerVideoOffStates = peerVideoOffStates
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(callFieldBackgroundColor())) {
        if (mode != CallViewMode.FULLSCREEN) {
            CallParticipantsHeader(
                participantCount = peers.size,
                mode = mode,
                query = participantQuery,
                onQueryChange = { participantQuery = it },
                onToggleMode = {
                    onModeChange(if (mode == CallViewMode.GRID) CallViewMode.FOCUS else CallViewMode.GRID)
                }
            )
        }
        if (mode == CallViewMode.FOCUS && !isLandscape) {
            ParticipantsTopBar(
                peers = peers,
                selfPeerId = selfPeerId,
                pinnedPeerId = pinnedPeerId,
                peerHandStates = peerHandStates,
                peerMicOffStates = peerMicOffStates,
                peerVideoOffStates = peerVideoOffStates,
                onPin = onPin,
                query = participantQuery
            )
        }
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (mode == CallViewMode.GRID) {
                    CallGridStage(
                        peers = filterParticipants(peers, participantQuery),
                        selfPeerId = selfPeerId,
                        peerHandStates = peerHandStates,
                        peerMicOffStates = peerMicOffStates,
                        peerVideoOffStates = peerVideoOffStates,
                        onSelectPeer = { id ->
                            focusManager.clearFocus()
                            participantQuery = ""
                            onPin(id)
                            onModeChange(CallViewMode.FOCUS)
                        },
                        pinnedPeerId = pinnedPeerId
                    )
                } else {
                    CallFocusStage(
                        peers = peers,
                        selfPeerId = selfPeerId,
                        pinnedPeerId = pinnedPeerId,
                        peerHandStates = peerHandStates,
                        peerMicOffStates = peerMicOffStates,
                        peerVideoOffStates = peerVideoOffStates,
                        pipOffsetX = pipOffsetX,
                        pipOffsetY = pipOffsetY,
                        onMovePip = onMovePip,
                        pipExpanded = pipExpanded,
                        onTogglePipExpanded = onTogglePipExpanded,
                        onSwitchCamera = onSwitchCamera,
                        isLandscape = isLandscape,
                        fullscreen = mode == CallViewMode.FULLSCREEN,
                        onToggleFullscreen = { id ->
                            focusManager.clearFocus()
                            participantQuery = ""
                            if (mode == CallViewMode.FULLSCREEN) {
                                onModeChange(CallViewMode.FOCUS)
                            } else {
                                onPin(id)
                                onModeChange(CallViewMode.FULLSCREEN)
                            }
                        },
                        onUnpin = { onPin(null) }
                    )
                }
            }
            if (mode == CallViewMode.FOCUS && isLandscape) {
                ParticipantsSidePanel(
                    peers = peers,
                    selfPeerId = selfPeerId,
                    pinnedPeerId = pinnedPeerId,
                    peerHandStates = peerHandStates,
                    peerMicOffStates = peerMicOffStates,
                    peerVideoOffStates = peerVideoOffStates,
                    onPin = onPin,
                    query = participantQuery,
                    modifier = Modifier
                        .width(if (configuration.screenWidthDp >= 800) 220.dp else 184.dp)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
internal fun DraggablePip(
    modifier: Modifier = Modifier,
    onDragDelta: (dx: Float, dy: Float) -> Unit,
    content: @Composable () -> Unit
) {
    val currentOnDragDelta by rememberUpdatedState(onDragDelta)
    Box(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                currentOnDragDelta(dragAmount.x, dragAmount.y)
            }
        }
    ) { content() }
}

@Composable
private fun rememberActiveSpeakerPeer(
    peers: List<PeerStatus>,
    selfPeerId: String,
    peerMicOffStates: Map<String, Boolean>
): PeerStatus? {
    val remotePeers = peers.filter { it.peerId != selfPeerId }
    val candidates = remotePeers.ifEmpty { peers }
    val fallbackPeerId = candidates.firstOrNull()?.peerId
    val samples = candidates.map { peer ->
        ActiveSpeakerSample(
            peerId = peer.peerId,
            audioLevel = peer.audioLevel,
            eligible = peer.audioAttached && peerMicOffStates[peer.peerId] != true
        )
    }
    val selector = remember(selfPeerId) { StableActiveSpeakerSelector() }
    val selectedPeerId = remember(samples, fallbackPeerId, selector) {
        selector.select(samples, fallbackPeerId, SystemClock.elapsedRealtime())
    }
    return peers.firstOrNull { it.peerId == selectedPeerId }
        ?: candidates.firstOrNull()
}

@Composable
private fun PictureInPictureCallView(
    peers: List<PeerStatus>,
    selfPeerId: String,
    pinnedPeerId: String?,
    peerMicOffStates: Map<String, Boolean>,
    peerVideoOffStates: Map<String, Boolean>
) {
    val peer = pinnedPeerId?.let { id -> peers.firstOrNull { it.peerId == id } }
        ?: rememberActiveSpeakerPeer(peers, selfPeerId, peerMicOffStates)
    val context = LocalContext.current
    val videoTracks = VideoTracksStore.snapshot()
    val track = peer?.let { selected ->
        videoTracks[selected.peerId]
            ?: if (selected.peerId == selfPeerId) videoTracks["__self__"] else null
    }
    val measuredAspectRatio = peer?.let { VideoTracksStore.frameAspectRatio(it.peerId) }
    val frameAspectRatio = measuredAspectRatio ?: 1f
    val waitingForVideo = peer?.let { selected ->
        selected.videoAttached && peerVideoOffStates[selected.peerId] != true &&
            (track == null || measuredAspectRatio == null)
    } == true

    LaunchedEffect(peer?.peerId, frameAspectRatio) {
        VideoTracksStore.setFocusedPeer(peer?.peerId)
        (context as? MainActivity)?.updatePictureInPictureAspectRatio(frameAspectRatio)
    }

    Box(modifier = Modifier.fillMaxSize().background(callBackgroundColor())) {
        if (waitingForVideo) {
            if (track != null) {
                FrameAspectRatioProbe(track = track, peerId = peer.peerId)
            }
            VideoLoadingIndicator(modifier = Modifier.fillMaxSize())
        } else if (peer != null) {
            PeerVideoTile(
                peer = peer,
                selfPeerId = selfPeerId,
                bigName = false,
                audioMuted = peerMicOffStates[peer.peerId] == true,
                videoOff = peerVideoOffStates[peer.peerId] == true,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 0.dp,
                showStatusOverlay = false,
                showTileBorder = false,
                renderedVideoFit = RenderedVideoFit.FILL_FRAME
            )
        }
    }
}

@Composable
internal fun MinimizedCallPreview(
    peers: List<PeerStatus>,
    selfPeerId: String,
    pinnedPeerId: String?,
    peerHandStates: Map<String, Boolean>,
    peerMicOffStates: Map<String, Boolean>,
    peerVideoOffStates: Map<String, Boolean>,
    visible: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(999.dp))
                .background(callSurfaceColor())
                .border(1.dp, accessibleAccentColor().copy(alpha = 0.7f), RoundedCornerShape(999.dp))
                .clickable(onClick = onExpand)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Videocam, contentDescription = null, tint = accessibleAccentColor(), modifier = Modifier.size(18.dp))
                Text(
                    text = tr(TextId.RETURN_TO_CALL),
                    color = callTextPrimaryColor(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }
        }
        return
    }

    val peer = pinnedPeerId?.let { id -> peers.firstOrNull { it.peerId == id } }
        ?: rememberActiveSpeakerPeer(peers, selfPeerId, peerMicOffStates)
    val shape = RoundedCornerShape(16.dp)
    val videoTracks = VideoTracksStore.snapshot()
    val track = peer?.let { selected ->
        videoTracks[selected.peerId]
            ?: if (selected.peerId == selfPeerId) videoTracks["__self__"] else null
    }
    val measuredAspectRatio = peer?.let { VideoTracksStore.frameAspectRatio(it.peerId) }
    val frameAspectRatio = measuredAspectRatio ?: 1f
    val waitingForVideo = peer?.let { selected ->
        selected.videoAttached && peerVideoOffStates[selected.peerId] != true &&
            (track == null || measuredAspectRatio == null)
    } == true
    val previewWidth = when {
        frameAspectRatio < 0.8f -> 132.dp
        frameAspectRatio > 1.2f -> 220.dp
        else -> 176.dp
    }

    LaunchedEffect(peer?.peerId) {
        VideoTracksStore.setFocusedPeer(peer?.peerId)
    }

    Box(
        modifier = modifier
            .width(previewWidth)
            .aspectRatio(frameAspectRatio)
            .clip(shape)
            .background(callSurfaceColor())
            .border(1.dp, accessibleAccentColor().copy(alpha = 0.8f), shape)
            .clickable(onClick = onExpand)
    ) {
        if (waitingForVideo) {
            if (track != null) {
                FrameAspectRatioProbe(track = track, peerId = peer.peerId)
            }
            VideoLoadingIndicator(modifier = Modifier.matchParentSize())
        } else if (peer != null) {
            PeerVideoTile(
                peer = peer,
                selfPeerId = selfPeerId,
                bigName = false,
                handRaised = peerHandStates[peer.peerId] == true,
                audioMuted = peerMicOffStates[peer.peerId] == true,
                videoOff = peerVideoOffStates[peer.peerId] == true,
                modifier = Modifier.matchParentSize(),
                cornerRadius = 0.dp,
                showTileBorder = false,
                renderedVideoFit = RenderedVideoFit.FILL_FRAME,
                useTextureView = true
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.72f))
                .border(1.dp, Color.White.copy(alpha = 0.24f), CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = tr(TextId.HIDE_THUMBNAIL),
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun SelfVideoPip(
    peer: PeerStatus,
    selfPeerId: String,
    handRaised: Boolean,
    audioMuted: Boolean,
    expanded: Boolean,
    isLandscape: Boolean,
    onToggleExpanded: () -> Unit,
    onSwitchCamera: () -> Unit,
    maximumWidth: Dp,
    maximumHeight: Dp,
    modifier: Modifier = Modifier
) {
    val videoTracks = VideoTracksStore.snapshot()
    val track = videoTracks[peer.peerId] ?: videoTracks["__self__"]
    val measuredAspectRatio = VideoTracksStore.frameAspectRatio(peer.peerId)
    val frameAspectRatio = measuredAspectRatio ?: (9f / 16f)
    val waitingForVideo = track == null || measuredAspectRatio == null
    val controlsBesideVideo = frameAspectRatio < 0.85f && maximumHeight >= 136.dp
    val availableVideoWidth = (maximumWidth - if (controlsBesideVideo) 48.dp else 0.dp).coerceAtLeast(1.dp)
    val availableVideoHeight = (maximumHeight - if (controlsBesideVideo) 0.dp else 48.dp).coerceAtLeast(1.dp)
    val targetWidth = when {
        controlsBesideVideo && expanded -> 116.dp
        controlsBesideVideo -> 80.dp
        expanded -> if (isLandscape) 208.dp else 188.dp
        else -> if (isLandscape) 160.dp else 136.dp
    }
    val targetHeight = if (expanded) 232.dp else 164.dp
    val fittedSize = fitFrameWithin(
        maximumWidth = minOf(targetWidth, availableVideoWidth).value,
        maximumHeight = minOf(targetHeight, availableVideoHeight).value,
        frameAspectRatio = frameAspectRatio
    )
    val videoWidth = fittedSize.width.dp
    val videoHeight = fittedSize.height.dp
    val indicators = participantIndicators(handRaised = handRaised, audioMuted = audioMuted)
    val shape = RoundedCornerShape(12.dp)
    val video: @Composable () -> Unit = {
        Box(modifier = Modifier.size(videoWidth, videoHeight)) {
            if (waitingForVideo) {
                if (track != null) FrameAspectRatioProbe(track = track, peerId = peer.peerId)
                VideoLoadingIndicator(modifier = Modifier.fillMaxSize())
            } else {
                PeerVideoTile(
                    peer = peer,
                    selfPeerId = selfPeerId,
                    bigName = false,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 0.dp,
                    showStatusOverlay = false,
                    showNameInTile = false,
                    showTileBorder = false,
                    useTextureView = true
                )
            }
        }
    }
    val controls: @Composable () -> Unit = {
        VideoToolButton(
            icon = Icons.Filled.Cameraswitch,
            contentDescription = tr(TextId.SWITCH_CAMERA),
            onClick = onSwitchCamera,
            onVideo = false
        )
        VideoToolButton(
            icon = if (expanded) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
            contentDescription = tr(if (expanded) TextId.SHRINK_THUMBNAIL else TextId.ENLARGE_THUMBNAIL),
            onClick = onToggleExpanded,
            onVideo = false
        )
    }
    Surface(
        modifier = modifier.clip(shape).border(1.dp, callBorderColor(), shape),
        color = callSurfaceColor(),
        shape = shape
    ) {
        if (controlsBesideVideo) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                video()
                Column(
                    modifier = Modifier.width(48.dp).height(videoHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    ParticipantStatusGroup(indicators = indicators, onVideo = false)
                    controls()
                }
            }
        } else {
            Column(
                modifier = Modifier.width(maxOf(videoWidth, minOf(136.dp, maximumWidth))),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                video()
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        ParticipantStatusGroup(indicators = indicators, vertical = true, onVideo = false)
                    }
                    controls()
                }
            }
        }
    }
}

@Composable
internal fun CallFocusStage(
    peers: List<PeerStatus>,
    selfPeerId: String,
    pinnedPeerId: String?,
    peerHandStates: Map<String, Boolean>,
    peerMicOffStates: Map<String, Boolean>,
    peerVideoOffStates: Map<String, Boolean>,
    pipOffsetX: Float,
    pipOffsetY: Float,
    onMovePip: (dx: Float, dy: Float) -> Unit,
    pipExpanded: Boolean,
    onTogglePipExpanded: () -> Unit,
    onSwitchCamera: () -> Unit,
    isLandscape: Boolean = false,
    fullscreen: Boolean = false,
    onToggleFullscreen: (String) -> Unit,
    onUnpin: () -> Unit
) {
    var pipSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val self = peers.firstOrNull { it.peerId == selfPeerId }
    val activeSpeaker = rememberActiveSpeakerPeer(peers, selfPeerId, peerMicOffStates)
    val pinned = pinnedPeerId?.let { id -> peers.firstOrNull { it.peerId == id } }
    val mainPeer = pinned ?: activeSpeaker
    val selfVideoEnabled = self != null && self.videoAttached && peerVideoOffStates[self.peerId] != true
    val mainCameraSwitch = onSwitchCamera.takeIf { mainPeer?.peerId == selfPeerId && selfVideoEnabled }

    LaunchedEffect(mainPeer?.peerId) {
        VideoTracksStore.setFocusedPeer(mainPeer?.peerId)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (mainPeer != null) {
            if (fullscreen) {
                PeerVideoTile(
                    peer = mainPeer,
                    selfPeerId = selfPeerId,
                    bigName = true,
                    handRaised = peerHandStates[mainPeer.peerId] == true,
                    audioMuted = peerMicOffStates[mainPeer.peerId] == true,
                    videoOff = peerVideoOffStates[mainPeer.peerId] == true,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 0.dp,
                    showNameInTile = false,
                    showTileBorder = false,
                )
                PeerUsernameRow(
                    name = displayName(mainPeer),
                    pingMs = mainPeer.pingMs,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(
                        start = if (mainPeer.peerId == selfPeerId) 116.dp else 68.dp,
                        bottom = 12.dp, end = 12.dp
                    )
                )
            } else {
                AspectFittedPeerCard(
                    peer = mainPeer,
                    selfPeerId = selfPeerId,
                    bigName = true,
                    handRaised = peerHandStates[mainPeer.peerId] == true,
                    audioMuted = peerMicOffStates[mainPeer.peerId] == true,
                    videoOff = peerVideoOffStates[mainPeer.peerId] == true,
                    modifier = Modifier.fillMaxSize(),
                    labelHeight = 34.dp,
                    videoOverlay = {
                        FocusVideoActions(
                            fullscreen = false,
                            pinned = pinned != null,
                            onToggleFullscreen = { onToggleFullscreen(mainPeer.peerId) },
                            onUnpin = onUnpin,
                            onSwitchCamera = mainCameraSwitch
                        )
                    }
                )
            }
        }

        if (fullscreen) {
            FocusVideoActions(
                fullscreen = true,
                pinned = false,
                onToggleFullscreen = { onToggleFullscreen(mainPeer?.peerId.orEmpty()) },
                onUnpin = onUnpin,
                onSwitchCamera = mainCameraSwitch
            )
        }
        if (
            self != null && selfVideoEnabled && mainPeer?.peerId != self.peerId
        ) {
            val bottomInset = if (fullscreen) 64.dp else 12.dp
            val horizontalRange = (with(density) { maxWidth.toPx() } - pipSize.width).coerceAtLeast(0f)
            val verticalRange = (with(density) { maxHeight.toPx() } - pipSize.height).coerceAtLeast(0f)
            val offsetX = pipOffsetX.coerceIn(-horizontalRange, 0f)
            val offsetY = pipOffsetY.coerceIn(-verticalRange, 0f)
            DraggablePip(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .onSizeChanged { pipSize = it }
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = bottomInset),
                onDragDelta = { dx, dy ->
                    onMovePip(
                        (offsetX + dx).coerceIn(-horizontalRange, 0f) - pipOffsetX,
                        (offsetY + dy).coerceIn(-verticalRange, 0f) - pipOffsetY
                    )
                }
            ) {
                SelfVideoPip(
                    peer = self,
                    selfPeerId = selfPeerId,
                    handRaised = peerHandStates[self.peerId] == true,
                    audioMuted = peerMicOffStates[self.peerId] == true,
                    expanded = pipExpanded,
                    isLandscape = isLandscape,
                    onToggleExpanded = onTogglePipExpanded,
                    onSwitchCamera = onSwitchCamera,
                    maximumWidth = (maxWidth - 24.dp).coerceAtLeast(1.dp),
                    maximumHeight = (maxHeight - bottomInset - 12.dp).coerceAtLeast(1.dp)
                )
            }
        }
    }
}

@Composable
private fun BoxScope.FocusVideoActions(
    fullscreen: Boolean,
    pinned: Boolean,
    onToggleFullscreen: () -> Unit,
    onUnpin: () -> Unit,
    onSwitchCamera: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.64f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VideoToolButton(
            icon = if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
            contentDescription = tr(if (fullscreen) TextId.COLLAPSE_VIDEO else TextId.EXPAND_VIDEO),
            onClick = onToggleFullscreen
        )
        if (pinned) {
            VideoToolButton(
                icon = Icons.Filled.PushPin,
                contentDescription = tr(TextId.UNPIN_VIDEO),
                onClick = onUnpin
            )
        }
        if (onSwitchCamera != null) {
            VideoToolButton(
                icon = Icons.Filled.Cameraswitch,
                contentDescription = tr(TextId.SWITCH_CAMERA),
                onClick = onSwitchCamera
            )
        }
    }
}

@Composable
internal fun PeerUsernameRow(
    name: String,
    pingMs: Long?,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.68f))
            .padding(
                horizontal = if (compact) 6.dp else 8.dp,
                vertical = if (compact) 4.dp else 5.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (compact) 11.sp else 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (pingMs != null) {
            Spacer(Modifier.width(if (compact) 4.dp else 8.dp))
            NetworkQualityIndicator(
                pingMs = pingMs,
                modifier = if (compact) {
                    Modifier.size(width = 18.dp, height = 12.dp)
                } else {
                    Modifier.size(width = 25.dp, height = 16.dp)
                }
            )
        }
    }
}

@Composable
internal fun NetworkQualityIndicator(
    pingMs: Long?,
    modifier: Modifier = Modifier.size(width = 25.dp, height = 16.dp)
) {
    val bars = networkSignalBars(pingMs)
    val activeColor = when (bars) {
        1 -> AppError
        2 -> Color(0xFFFFC107)
        3, 4 -> Color.White
        5 -> AppSuccess
        else -> Color.White.copy(alpha = 0.28f)
    }
    val description = if (pingMs == null) {
        tr(TextId.NETWORK_QUALITY_UNAVAILABLE)
    } else {
        tr(TextId.NETWORK_QUALITY, bars, pingMs)
    }

    Canvas(
        modifier = modifier
            .semantics { contentDescription = description }
    ) {
        val gap = 1.5.dp.toPx()
        val barWidth = (size.width - gap * 4f) / 5f
        val minimumHeight = 4.dp.toPx()
        val heightStep = (size.height - minimumHeight) / 4f

        repeat(5) { index ->
            val barHeight = minimumHeight + heightStep * index
            drawRoundRect(
                color = if (index < bars) activeColor else Color.White.copy(alpha = 0.16f),
                topLeft = androidx.compose.ui.geometry.Offset(
                    x = index * (barWidth + gap),
                    y = size.height - barHeight
                ),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                    x = barWidth / 2f,
                    y = barWidth / 2f
                )
            )
        }
    }
}

@Composable
private fun FrameAspectRatioProbe(
    track: VideoTrack,
    peerId: String
) {
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val sink = remember(track, peerId) {
        val delivered = AtomicBoolean(false)
        VideoSink { frame ->
            val width = frame.rotatedWidth
            val height = frame.rotatedHeight
            if (width > 0 && height > 0 && delivered.compareAndSet(false, true)) {
                val aspectRatio = width.toFloat() / height.toFloat()
                mainHandler.post {
                    VideoTracksStore.setFrameAspectRatio(peerId, aspectRatio)
                }
            }
        }
    }

    DisposableEffect(track, sink) {
        track.addSink(sink)
        onDispose { track.removeSink(sink) }
    }
}

@Composable
private fun VideoLoadingIndicator(
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = accessibleAccentColor(),
            strokeWidth = 2.dp,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
internal fun AspectFittedPeerCard(
    peer: PeerStatus,
    selfPeerId: String,
    bigName: Boolean,
    handRaised: Boolean,
    audioMuted: Boolean,
    videoOff: Boolean,
    modifier: Modifier = Modifier,
    showPin: Boolean = false,
    showLabel: Boolean = true,
    showStatusOverlay: Boolean = true,
    labelHeight: Dp = 30.dp,
    minimumLabelWidth: Dp = 112.dp,
    compactLabel: Boolean = false,
    compactPreview: Boolean = false,
    onClick: (() -> Unit)? = null,
    useTextureView: Boolean = false,
    videoOverlay: (@Composable BoxScope.() -> Unit)? = null
) {
    val videoTracks = VideoTracksStore.snapshot()
    val track = videoTracks[peer.peerId]
        ?: if (peer.peerId == selfPeerId) videoTracks["__self__"] else null
    val expectsVideo = !videoOff && (peer.videoAttached || track != null)
    val hasRenderableVideo = track != null && !videoOff
    val measuredAspectRatio = VideoTracksStore.frameAspectRatio(peer.peerId)
    val rawAspectRatio = if (hasRenderableVideo) {
        measuredAspectRatio ?: 1f
    } else {
        1f
    }
    val frameAspectRatio = rawAspectRatio
        .takeIf { it.isFinite() && it > 0f }
        ?.coerceIn(0.01f, 100f)
        ?: 1f

    BoxWithConstraints(
        modifier = modifier.let { current ->
            if (onClick != null) current.clickable(onClick = onClick) else current
        },
        contentAlignment = Alignment.Center
    ) {
        val boundedWidth = if (maxWidth.value.isFinite()) maxWidth else 320.dp
        val boundedHeight = if (maxHeight.value.isFinite()) maxHeight else 240.dp
        val effectiveLabelHeight = if (showLabel) labelHeight else 0.dp
        val maximumVideoHeight = (boundedHeight - effectiveLabelHeight).coerceAtLeast(1.dp)
        val useStatusRails = compactPreview && boundedWidth >= 96.dp &&
            (frameAspectRatio < 0.85f || !hasRenderableVideo)
        val fittedSize = fitFrameWithin(
            maximumWidth = (boundedWidth - if (useStatusRails) 56.dp else 0.dp).value,
            maximumHeight = maximumVideoHeight.value,
            frameAspectRatio = frameAspectRatio
        )
        val videoWidth = fittedSize.width.dp
        val videoHeight = fittedSize.height.dp
        val overlayFitsVideo = videoWidth >= 156.dp && videoHeight >= 120.dp
        val videoRegionWidth = if (compactPreview) boundedWidth else videoWidth
        val videoRegionHeight = if (compactPreview) maximumVideoHeight else videoHeight
        val labelWidth = if (showLabel) {
            maxOf(videoRegionWidth, minimumLabelWidth.coerceAtMost(boundedWidth))
        } else {
            videoWidth
        }
        val cardWidth = maxOf(videoWidth, labelWidth)

        Column(
            modifier = Modifier.width(cardWidth),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.width(videoRegionWidth).height(videoRegionHeight),
                contentAlignment = Alignment.Center
            ) {
                if (expectsVideo && (track == null || measuredAspectRatio == null)) {
                    if (track != null) FrameAspectRatioProbe(track = track, peerId = peer.peerId)
                    VideoLoadingIndicator(modifier = Modifier.size(videoWidth, videoHeight))
                } else {
                    PeerVideoTile(
                        peer = peer,
                        selfPeerId = selfPeerId,
                        bigName = bigName,
                        handRaised = handRaised,
                        audioMuted = audioMuted,
                        videoOff = videoOff,
                        showPin = showPin,
                        showStatusOverlay = showStatusOverlay && !compactPreview,
                        modifier = Modifier.size(videoWidth, videoHeight),
                        onClick = null,
                        showNameInTile = false,
                        renderedVideoFit = RenderedVideoFit.FILL_FRAME,
                        useTextureView = useTextureView
                    )
                }
                if (compactPreview && showStatusOverlay) {
                    ParticipantStatusGroup(
                        indicators = participantIndicators(pinned = showPin, handRaised = handRaised),
                        modifier = Modifier.align(if (useStatusRails) Alignment.CenterStart else Alignment.TopStart)
                            .padding(4.dp),
                        vertical = useStatusRails,
                        onVideo = !useStatusRails
                    )
                    ParticipantStatusGroup(
                        indicators = participantIndicators(
                            audioMuted = audioMuted || !peer.audioAttached,
                            videoOff = !expectsVideo
                        ),
                        modifier = Modifier.align(if (useStatusRails) Alignment.CenterEnd else Alignment.TopEnd)
                            .padding(4.dp),
                        vertical = useStatusRails,
                        onVideo = !useStatusRails
                    )
                }
                if (overlayFitsVideo) videoOverlay?.invoke(this)
            }

            if (showLabel) {
                PeerUsernameRow(
                    name = displayName(peer),
                    pingMs = peer.pingMs,
                    compact = compactLabel,
                    modifier = Modifier
                        .width(labelWidth)
                        .height(labelHeight)
                )
            }
        }
        if (!overlayFitsVideo) videoOverlay?.invoke(this)
    }
}

@Composable
internal fun TopBarPeerVideoTile(
    peer: PeerStatus,
    selfPeerId: String,
    pinned: Boolean,
    handRaised: Boolean,
    audioMuted: Boolean,
    videoOff: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    AspectFittedPeerCard(
        peer = peer,
        selfPeerId = selfPeerId,
        bigName = false,
        handRaised = handRaised,
        audioMuted = audioMuted,
        videoOff = videoOff,
        showPin = pinned,
        modifier = modifier
            .height(98.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(callFieldBackgroundColor())
            .border(1.dp, if (pinned) accessibleAccentColor() else callBorderColor(), RoundedCornerShape(12.dp))
            .semantics { selected = pinned },
        labelHeight = 28.dp,
        minimumLabelWidth = 108.dp,
        compactLabel = true,
        compactPreview = true,
        onClick = onClick
    )
}

@Composable
internal fun GridPeerVideoTile(
    peer: PeerStatus,
    selfPeerId: String,
    pinnedPeerId: String?,
    handRaised: Boolean,
    audioMuted: Boolean,
    videoOff: Boolean,
    tileHeight: Dp,
    onClick: () -> Unit,
) {
    AspectFittedPeerCard(
        peer = peer,
        selfPeerId = selfPeerId,
        bigName = true,
        handRaised = handRaised,
        audioMuted = audioMuted,
        videoOff = videoOff,
        showPin = peer.peerId == pinnedPeerId,
        modifier = Modifier
            .fillMaxWidth()
            .height(tileHeight),
        labelHeight = 30.dp,
        minimumLabelWidth = 136.dp,
        compactLabel = true,
        onClick = onClick,
    )
}

@Composable
internal fun CallGridStage(
    peers: List<PeerStatus>,
    selfPeerId: String,
    peerHandStates: Map<String, Boolean>,
    peerMicOffStates: Map<String, Boolean>,
    peerVideoOffStates: Map<String, Boolean>,
    onSelectPeer: (String) -> Unit,
    pinnedPeerId: String?,
) {
    if (peers.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(tr(TextId.NO_PARTICIPANTS_FOUND), color = callTextSecondaryColor(), fontSize = 13.sp)
        }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val minimumTileWidth = when {
            maxWidth < 360.dp -> 136.dp
            maxWidth < 560.dp -> 164.dp
            else -> 188.dp
        }
        val columnCount = ((maxWidth - 12.dp) / (minimumTileWidth + 8.dp)).toInt()
            .coerceAtLeast(1).coerceAtMost(peers.size)
        val tileWidth = (maxWidth - 20.dp - 8.dp * (columnCount - 1)) / columnCount
        val tileHeight = minOf(tileWidth, (maxHeight - 20.dp).coerceAtLeast(96.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(columnCount),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(10.dp)
        ) {
            items(peers, key = { it.peerId }) { peer ->
                GridPeerVideoTile(
                    peer = peer,
                    selfPeerId = selfPeerId,
                    pinnedPeerId = pinnedPeerId,
                    tileHeight = tileHeight,
                    handRaised = peerHandStates[peer.peerId] == true,
                    audioMuted = peerMicOffStates[peer.peerId] == true,
                    videoOff = peerVideoOffStates[peer.peerId] == true,
                    onClick = { onSelectPeer(peer.peerId) },
                )
            }
        }
    }
}

internal enum class RenderedVideoFit { FILL_FRAME, PORTRAIT_INSIDE_FRAME }

@Composable
internal fun RenderedVideoLayer(
    track: VideoTrack,
    peerId: String,
    isSelf: Boolean,
    fit: RenderedVideoFit,
    modifier: Modifier = Modifier,
    useTextureView: Boolean = false
) {
    BoxWithConstraints(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val frameAspectRatio = VideoTracksStore.frameAspectRatio(peerId)
            ?.takeIf { it.isFinite() && it > 0f }
            ?: 1f
        val containerAspectRatio = if (maxHeight.value > 0f && maxHeight.value.isFinite()) {
            maxWidth.value / maxHeight.value
        } else {
            frameAspectRatio
        }
        val videoModifier = when (fit) {
            RenderedVideoFit.FILL_FRAME -> if (frameAspectRatio >= containerAspectRatio) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(frameAspectRatio)
            } else {
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(frameAspectRatio)
            }
            RenderedVideoFit.PORTRAIT_INSIDE_FRAME -> Modifier
                .fillMaxHeight()
                .aspectRatio(frameAspectRatio)
        }

        WebRtcVideoSurface(
            track = track,
            isSelf = isSelf,
            modifier = videoModifier,
            useTextureView = useTextureView,
            onFrameAspectRatio = { ratio ->
                VideoTracksStore.setFrameAspectRatio(peerId, ratio)
            }
        )
    }
}

@Composable
internal fun PeerVideoTile(
    peer: PeerStatus,
    selfPeerId: String,
    bigName: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    handRaised: Boolean = false,
    audioMuted: Boolean = false,
    videoOff: Boolean = false,
    showPin: Boolean = false,
    cornerRadius: Dp = 14.dp,
    showStatusOverlay: Boolean = true,
    showNameInTile: Boolean = true,
    showTileBorder: Boolean = true,
    renderedVideoFit: RenderedVideoFit = RenderedVideoFit.FILL_FRAME,
    useTextureView: Boolean = false
) {
    val name = displayName(peer)
    val base = inactiveVideoColor()

    val level = if (peer.audioAttached && !audioMuted) peer.audioLevel.coerceIn(0f, 1f) else 0f
    val glowColor = AppAccent
    val shape = RoundedCornerShape(cornerRadius)
    val pingMs = peer.pingMs

    val videoTracks: Map<String, VideoTrack> = VideoTracksStore.snapshot()
    val track = videoTracks[peer.peerId] ?: if (peer.peerId == selfPeerId) videoTracks["__self__"] else null
    val hasVideo = track != null && !videoOff
    val micOff = audioMuted || !peer.audioAttached

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (hasVideo) Color.Black else base)
            .let { m -> if (showTileBorder) m.border(1.dp, callBorderColor(), shape) else m }
            .let { m -> if (onClick != null) m.clickable { onClick() } else m }
            .drawBehind {
                if (level > 0f) {
                    val a1 = (0.06f + 0.20f * level).coerceIn(0f, 0.30f)
                    val a2 = (0.03f + 0.10f * level).coerceIn(0f, 0.18f)
                    val outerRadius = (cornerRadius + 4.dp).toPx()
                    val innerRadius = (cornerRadius + 2.dp).toPx()
                    drawRoundRect(
                        color = glowColor.copy(alpha = a2),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(outerRadius, outerRadius),
                        style = Stroke(width = 14.dp.toPx())
                    )
                    drawRoundRect(
                        color = glowColor.copy(alpha = a1),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(innerRadius, innerRadius),
                        style = Stroke(width = 6.dp.toPx())
                    )
                }
            }
    ) {
        if (hasVideo) {
            androidx.compose.runtime.key(peer.peerId, track!!.id()) {
                RenderedVideoLayer(
                    track = track,
                    peerId = peer.peerId,
                    isSelf = peer.peerId == selfPeerId,
                    fit = renderedVideoFit,
                    modifier = Modifier.matchParentSize().semantics {
                        contentDescription = tr(TextId.PARTICIPANT_VIDEO, name)
                    },
                    useTextureView = useTextureView
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(base)
            )
        }

        BoxWithConstraints(
            modifier = Modifier.matchParentSize().padding(if (bigName) 8.dp else 4.dp)
        ) {
            if (showStatusOverlay) {
                ParticipantStatusGroup(
                    indicators = participantIndicators(
                        pinned = showPin,
                        handRaised = handRaised,
                        audioMuted = micOff,
                        videoOff = !hasVideo
                    ),
                    modifier = Modifier.align(Alignment.TopStart),
                    vertical = maxWidth < 96.dp,
                    compact = !bigName
                )
            }
            if (!hasVideo) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.86f),
                        modifier = Modifier.size(if (bigName) 34.dp else 24.dp)
                    )
                    if (bigName || showNameInTile) {
                        Text(
                            text = name,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = if (bigName) 22.sp else 14.sp,
                            maxLines = if (bigName) 2 else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (showStatusOverlay && showNameInTile && hasVideo) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.58f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = if (bigName) 14.sp else 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (pingMs != null) {
                        Spacer(Modifier.width(8.dp))
                        NetworkQualityIndicator(
                            pingMs = pingMs,
                            modifier = Modifier.size(
                                width = if (bigName) 25.dp else 22.dp,
                                height = if (bigName) 16.dp else 14.dp
                            )
                        )
                    }
                }
            } else if (showStatusOverlay && showNameInTile && pingMs != null) {
                NetworkQualityIndicator(
                    pingMs = pingMs,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }
        }
    }
}

@Composable
internal fun WebRtcVideoSurface(
    track: VideoTrack,
    isSelf: Boolean,
    modifier: Modifier = Modifier,
    useTextureView: Boolean = false,
    onFrameAspectRatio: (Float) -> Unit = {}
) {
    if (useTextureView) {
        WebRtcVideoTexture(track, isSelf, modifier, onFrameAspectRatio)
        return
    }

    val context = LocalContext.current
    val mirror = isSelf && VideoTracksStore.isSelfPreviewMirrored()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val latestOnFrameAspectRatio by rememberUpdatedState(onFrameAspectRatio)
    val rendererEvents = remember {
        object : RendererCommon.RendererEvents {
            override fun onFirstFrameRendered() = Unit

            override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {
                if (videoWidth <= 0 || videoHeight <= 0) return
                val rotated = rotation % 180 != 0
                val displayWidth = if (rotated) videoHeight else videoWidth
                val displayHeight = if (rotated) videoWidth else videoHeight
                val aspectRatio = displayWidth.toFloat() / displayHeight.toFloat()
                mainHandler.post { latestOnFrameAspectRatio(aspectRatio) }
            }
        }
    }

    val renderer = remember {
        SurfaceViewRenderer(context).apply {
            init(VideoRenderContext.eglBase.eglBaseContext, rendererEvents)
            setEnableHardwareScaler(false)
            setMirror(mirror)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            clearImage()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { renderer },
        update = { view ->
            view.setMirror(mirror)
            view.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        }
    )

    DisposableEffect(track, renderer) {
        track.addSink(renderer)

        onDispose {
            track.removeSink(renderer)
            renderer.clearImage()
        }
    }

    DisposableEffect(renderer) {
        onDispose {
            renderer.clearImage()
            renderer.release()
        }
    }
}
