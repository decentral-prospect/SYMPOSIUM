package com.decentralprospect.symposium

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

internal enum class ParticipantIndicator(val icon: ImageVector, val description: TextId) {
    PINNED(Icons.Filled.PushPin, TextId.PINNED),
    HAND(Icons.Filled.PanTool, TextId.HAND_RAISED),
    MUTED(Icons.Filled.MicOff, TextId.MIC_OFF),
    CAMERA_OFF(Icons.Filled.VideocamOff, TextId.VIDEO_OFF)
}

internal fun participantIndicators(
    pinned: Boolean = false,
    handRaised: Boolean = false,
    audioMuted: Boolean = false,
    videoOff: Boolean = false
): List<ParticipantIndicator> = buildList {
    if (pinned) add(ParticipantIndicator.PINNED)
    if (handRaised) add(ParticipantIndicator.HAND)
    if (audioMuted) add(ParticipantIndicator.MUTED)
    if (videoOff) add(ParticipantIndicator.CAMERA_OFF)
}

@Composable
internal fun ParticipantStatusGroup(
    indicators: List<ParticipantIndicator>,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    compact: Boolean = true,
    onVideo: Boolean = true
) {
    if (indicators.isEmpty()) return
    val content: @Composable () -> Unit = {
        indicators.forEach { indicator ->
            Icon(
                imageVector = indicator.icon,
                contentDescription = tr(indicator.description),
                tint = when (indicator) {
                    ParticipantIndicator.PINNED, ParticipantIndicator.HAND -> accessibleAccentColor()
                    else -> if (onVideo) Color.White else callTextPrimaryColor()
                },
                modifier = Modifier.size(if (compact) 16.dp else 18.dp)
            )
        }
    }
    val groupModifier = modifier
        .clip(RoundedCornerShape(6.dp))
        .background(if (onVideo) Color.Black.copy(alpha = 0.64f) else Color.Transparent)
        .padding(if (compact) 4.dp else 6.dp)
    if (vertical) {
        Column(
            modifier = groupModifier,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) { content() }
    } else {
        Row(
            modifier = groupModifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) { content() }
    }
}

@Composable
internal fun VideoToolButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onVideo: Boolean = true
) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (onVideo) Color.White else callIconColor(),
            modifier = Modifier.size(20.dp)
        )
    }
}
