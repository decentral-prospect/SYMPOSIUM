package com.decentralprospect.symposium

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.decentralprospect.symposium.ui.theme.Dos2000FontFamily
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
internal fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ActionButtonKind = ActionButtonKind.PRIMARY,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    textColorOverride: Color? = null,
    borderColorOverride: Color? = null
) {
    val danger = accessibleDangerColor()
    val (bg, baseTextColor, baseBorderColor) = when (kind) {
        ActionButtonKind.PRIMARY -> Triple(
            AppAccent,
            AppOnAccent,
            Color.Transparent
        )
        ActionButtonKind.SECONDARY -> Triple(
            appGrayControlColor(),
            appTextPrimaryColor(),
            appGrayControlBorderColor()
        )
        ActionButtonKind.DANGER -> Triple(
            danger,
            Color.White,
            danger.copy(alpha = 0.95f)
        )
        ActionButtonKind.GHOST -> Triple(
            Color.Transparent,
            appTextPrimaryColor(),
            appBorderColor(0.72f)
        )
    }
    val textColor = textColorOverride ?: baseTextColor
    val fillModifier = if (kind == ActionButtonKind.PRIMARY) {
        Modifier.background(
            brush = appPrimaryGradient(if (enabled) 1f else 0.35f),
            shape = AppButtonShape
        )
    } else {
        Modifier.background(if (enabled) bg else bg.copy(alpha = 0.35f))
    }

    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(AppButtonShape)
            .then(fillModifier)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (loading) stateDescription = tr(TextId.LOADING)
                if (!enabled) stateDescription = tr(TextId.UNAVAILABLE)
            }
            .clickable(enabled = enabled && !loading, role = Role.Button) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = textColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            } else if (icon != null) {
                Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(18.dp))
            }

            Text(
                text = label,
                color = textColor,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
internal fun QrCodeDialog(
    token: String,
    title: String = tr(TextId.QR_CODE),
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    val context = LocalContext.current
    val bitmap = remember(token) { generateQrBitmapOrNull(token) }
    val containsModeratorKey = remember(token) {
        parseInvitationToken(token)?.moderatorKey?.isNotBlank() == true
    }

    AppDialog(
        title = title,
        onDismiss = onDismiss,
        content = {
            if (bitmap != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = tr(TextId.QR_CODE),
                        modifier = Modifier.size(240.dp)
                    )
                }
            } else {
                Text(
                    text = tr(TextId.COULD_NOT_SHARE_THE_QR_CODE),
                    color = AppError,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                text = if (containsModeratorKey) {
                    tr(TextId.WARNING_THIS_QR_CODE_CONTAINS_AN_E2EE_KEY_AND_MODKEY_SHARE_IT_ONLY_WITH_)
                } else {
                    tr(TextId.WARNING_THIS_QR_CODE_CONTAINS_AN_E2EE_KEY_SHARE_IT_ONLY_WITH_A_TRUSTED_R)
                },
                color = AppError,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        },
        actions = {
            ActionButton(
                label = tr(TextId.COPY),
                onClick = onCopy,
                kind = ActionButtonKind.SECONDARY,
                modifier = Modifier.weight(1f)
            )
            ActionButton(
                label = tr(TextId.SHARE),
                onClick = {
                    val qrBitmap = bitmap
                    if (qrBitmap == null || !shareTemporaryQr(context, qrBitmap, containsModeratorKey)) {
                        Toast.makeText(context, tr(TextId.COULD_NOT_SHARE_THE_QR_CODE), Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = bitmap != null,
                kind = ActionButtonKind.PRIMARY,
                icon = Icons.Filled.Share,
                modifier = Modifier.weight(1f)
            )
        }
    )
}

@Composable
internal fun TopIconAction(
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
    accent: Color = AppAccent,
    backgroundColor: Color = appGrayControlColor(),
    borderColor: Color = appGrayControlBorderColor(),
    contentDescription: String = tr(TextId.ACTION),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled || loading) backgroundColor else backgroundColor.copy(alpha = 0.35f))
            .border(
                1.dp,
                if (enabled || loading) borderColor else borderColor.copy(alpha = 0.35f),
                RoundedCornerShape(12.dp)
            )
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
                if (loading) stateDescription = tr(TextId.LOADING)
                if (!enabled) stateDescription = tr(TextId.UNAVAILABLE)
            }
            .clickable(enabled = enabled && !loading, role = Role.Button) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    dismissEnabled: Boolean = true,
    maxWidth: Dp = 560.dp,
    scrollableContent: Boolean = false,
    avoidKeyboard: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = { if (dismissEnabled) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = !avoidKeyboard)
    ) {
        val compactDialog = avoidKeyboard &&
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val backdropColor = if (isAppLightTheme()) {
            Color(0xD9EEE7E0)
        } else {
            Color(0xA6121418)
        }

        Box(
            modifier = Modifier.fillMaxSize().then(
                if (avoidKeyboard) Modifier.systemBarsPadding().imePadding() else Modifier
            ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backdropColor)
                    .blur(2.dp)
                    .clickable(enabled = dismissEnabled) { onDismiss() }
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = maxWidth)
                    .padding(horizontal = 18.dp, vertical = if (compactDialog) 8.dp else 24.dp)
                    .then(modifier),
                color = appSurfaceColor(),
                shape = CardShape,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .border(1.dp, appBorderColor(0.9f), CardShape)
                        .padding(if (compactDialog) 12.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compactDialog) 8.dp else 16.dp)
                ) {
                    if (!compactDialog) Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = title,
                            color = appTextPrimaryColor(),
                            fontSize = 18.sp,
                            lineHeight = 25.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(end = 54.dp)
                        )

                        if (dismissEnabled) {
                            TopIconAction(
                                icon = Icons.Filled.Close,
                                onClick = onDismiss,
                                accent = appTextSecondaryColor(),
                                contentDescription = tr(TextId.CLOSE_DIALOG),
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth().then(
                            if (scrollableContent) {
                                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                            } else {
                                Modifier
                            }
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        content = content
                    )

                    if (actions != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            content = actions
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SectionCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    backgroundColor: Color = appSurfaceColor(),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = backgroundColor,
        shape = CardShape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, appBorderColor(0.9f), CardShape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = title,
                            color = appTextPrimaryColor(),
                            fontSize = 18.sp,
                            lineHeight = 25.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() }
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                subtitle,
                                color = appTextSecondaryColor(),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                    if (trailing != null) {
                        Spacer(Modifier.width(12.dp))
                        trailing()
                    }
                }

                content()
            }
        )
    }
}

@Composable
internal fun CollapsibleSectionCard(
    title: String,
    subtitle: String? = null,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        color = appSurfaceColor(),
        shape = CardShape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, appBorderColor(0.9f), CardShape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        color = appTextPrimaryColor(),
                        fontSize = 17.sp,
                        lineHeight = 24.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(subtitle, color = appTextSecondaryColor(), fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }

                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = AppAccent
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
internal fun InfoBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = color,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun CopyValueRow(
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(InnerCardShape)
            .background(appSurfaceElevatedColor())
            .border(1.dp, Color.Transparent, InnerCardShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = appTextSecondaryColor(), fontSize = 12.sp)
            Text(value.ifBlank { "—" }, color = appTextPrimaryColor(), fontSize = 14.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onCopy) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = tr(TextId.COPY),
                tint = AppAccent
            )
        }
    }
}

@Composable
private fun InvitationTokenInput(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    isError: Boolean = false
) {
    val context = LocalContext.current
    val scanOptions = remember {
        ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("")
            .setBeepEnabled(false)
            .setOrientationLocked(true)
    }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.trim()?.takeIf(String::isNotEmpty)?.let(onValueChange)
    }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scanner.launch(scanOptions)
        } else {
            Toast.makeText(
                context,
                tr(TextId.CAMERA_PERMISSION_DENIED),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            minLines = 2,
            maxLines = 4,
            label = { Text(hint) },
            isError = isError,
            shape = AppButtonShape,
            colors = textFieldColors()
        )
        IconButton(
            onClick = {
                if (
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    scanner.launch(scanOptions)
                } else {
                    cameraPermission.launch(Manifest.permission.CAMERA)
                }
            },
            modifier = Modifier
                .size(52.dp)
                .clip(AppButtonShape)
                .background(appGrayControlColor())
                .border(1.dp, appGrayControlBorderColor(), AppButtonShape)
        ) {
            Icon(
                imageVector = Icons.Filled.QrCodeScanner,
                contentDescription = tr(TextId.QR_CODE),
                tint = AppAccent
            )
        }
    }
}

@Composable
internal fun HomeConnectionCard(
    connectLink: String,
    onConnectLinkChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    parsed: InvitationTokenPayload?,
    tlsTrust: InvitationTlsTrust?,
    reconnectMode: Boolean,
    onAcceptChangedTlsPin: () -> Unit,
    onConnectClick: () -> Unit
) {
    SectionCard(
        title = tr(TextId.JOIN_CONFERENCE)
    ) {
        InvitationTokenInput(connectLink, onConnectLinkChange, tr(TextId.CONNECTION_LINK))

        if (parsed != null && tlsTrust != null) {
            InvitationSecurityCard(parsed, tlsTrust, onAcceptChangedTlsPin)
        }

        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(tr(TextId.NAME)) },
            colors = textFieldColors()
        )

        ActionButton(
            label = tr(TextId.CONNECT),
            onClick = onConnectClick,
            enabled = !reconnectMode && parsed != null && tlsTrust?.mayConnect == true,
            modifier = Modifier.fillMaxWidth(),
            textColorOverride = AppOnAccent
        )
    }
}

@Composable
internal fun HomeConnectScreen(
    connectLink: String,
    onConnectLinkChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    parsed: InvitationTokenPayload?,
    linkError: String? = null,
    tlsTrust: InvitationTlsTrust?,
    reconnectMode: Boolean,
    onAcceptChangedTlsPin: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val invalidLink = connectLink.isNotBlank() && parsed == null

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(30.dp))
        SymposiumWordmark()
        Spacer(Modifier.height(44.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = tr(TextId.JOIN_CONFERENCE),
                color = appTextPrimaryColor(),
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = tr(TextId.YOU_CAN_LEAVE_THE_NAME_FIELD_EMPTY),
                color = appTextSecondaryColor(),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(tr(TextId.NAME)) },
                shape = AppButtonShape,
                colors = textFieldColors()
            )

            InvitationTokenInput(
                value = connectLink,
                onValueChange = onConnectLinkChange,
                hint = tr(TextId.PASTE_THE_MEETING_LINK),
                isError = invalidLink
            )

            if (invalidLink) {
                Text(
                    text = linkError ?: tr(TextId.THE_LINK_WAS_NOT_RECOGNIZED_PASTE_A_SYMPOSIUM_LINK),
                    color = AppError,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            } else if (parsed != null && tlsTrust != null) {
                InvitationSecurityCard(
                    parsed = parsed,
                    tlsTrust = tlsTrust,
                    onAcceptChangedTlsPin = onAcceptChangedTlsPin
                )
            }

            Spacer(Modifier.height(2.dp))
            ActionButton(
                label = tr(TextId.CONNECT),
                onClick = onConnect,
                enabled = !reconnectMode && parsed != null && tlsTrust?.mayConnect == true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun InvitationSecurityCard(
    parsed: InvitationTokenPayload,
    tlsTrust: InvitationTlsTrust,
    onAcceptChangedTlsPin: () -> Unit
) {
    val fingerprint = remember(parsed.e2eeSecret) {
        conferenceE2eeFingerprint(parsed.e2eeSecret)
    }
    var detailsExpanded by remember(
        tlsTrust.endpoint,
        tlsTrust.presentedPin,
        parsed.e2eeSecret
    ) { mutableStateOf(false) }
    val isChanged = tlsTrust.state == InvitationTlsTrustState.CHANGED
    val borderColor = when (tlsTrust.state) {
        InvitationTlsTrustState.FIRST_CONNECTION,
        InvitationTlsTrustState.CHANGED_ACCEPTED -> AppWarning
        InvitationTlsTrustState.MATCHED -> AppSuccess
        InvitationTlsTrustState.CHANGED -> AppError
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(InnerCardShape)
            .background(appSurfaceElevatedColor())
            .border(1.dp, borderColor.copy(alpha = 0.8f), InnerCardShape)
            .animateContentSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        when (tlsTrust.state) {
            InvitationTlsTrustState.FIRST_CONNECTION -> Text(
                text = tr(TextId.TLS_FIRST_CONNECTION, tlsTrust.endpoint.authority),
                color = AppWarning,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            InvitationTlsTrustState.MATCHED -> Text(
                text = tr(TextId.TLS_PIN_MATCHED),
                color = AppSuccess,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            InvitationTlsTrustState.CHANGED -> {
                Text(
                    text = tr(TextId.TLS_PIN_CHANGED_WARNING),
                    color = AppError,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                InvitationSecurityDetailRow(tr(TextId.SAVED_TLS_PIN), tlsTrust.savedPin.orEmpty())
            }
            InvitationTlsTrustState.CHANGED_ACCEPTED -> {
                InvitationSecurityDetailRow(tr(TextId.PREVIOUSLY_SAVED_TLS_PIN), tlsTrust.savedPin.orEmpty())
                Text(
                    text = tr(TextId.TLS_PIN_ACCEPTED_FOR_ATTEMPT),
                    color = AppWarning,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = tr(TextId.ROOM_LABEL, parsed.room),
                color = appTextPrimaryColor(),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            InfoBadge(
                label = if (parsed.moderatorKey.isBlank()) tr(TextId.GUEST) else tr(TextId.MODERATOR),
                color = if (parsed.moderatorKey.isBlank()) appTextSecondaryColor() else AppAccent
            )
        }

        Text(
            text = tr(TextId.SERVER_AND_PORT, tlsTrust.endpoint.authority),
            color = appTextSecondaryColor(),
            fontSize = 12.sp,
            lineHeight = 17.sp
        )

        InvitationDetailsToggle(
            expanded = detailsExpanded,
            onClick = { detailsExpanded = !detailsExpanded }
        )

        AnimatedVisibility(
            visible = detailsExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (parsed.requiredVersion.isNotBlank()) {
                    InvitationSecurityDetailRow(tr(TextId.ROOM_VERSION), parsed.requiredVersion)
                }
                InvitationSecurityDetailRow(tr(TextId.TLS_PIN_FROM_LINK), tlsTrust.presentedPin)
                InvitationSecurityDetailRow(tr(TextId.E2EE_KEY_FINGERPRINT), fingerprint)
            }
        }

        if (isChanged) {
            ActionButton(
                label = tr(TextId.ACCEPT_NEW_TLS_PIN),
                onClick = onAcceptChangedTlsPin,
                kind = ActionButtonKind.DANGER,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun InvitationDetailsToggle(
    expanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(AppButtonShape)
            .background(appGrayControlColor())
            .border(1.dp, appGrayControlBorderColor(), AppButtonShape)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = tr(if (expanded) TextId.EXPANDED_ALT else TextId.COLLAPSED_ALT)
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = tr(TextId.TLS_AND_E2EE_DETAILS),
            color = appTextPrimaryColor(),
            fontSize = 10.sp,
            lineHeight = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp)
        )
        Icon(
            imageVector = if (expanded) {
                Icons.Filled.KeyboardArrowUp
            } else {
                Icons.Filled.KeyboardArrowDown
            },
            contentDescription = null,
            tint = AppAccent,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun InvitationSecurityDetailRow(label: String, value: String) {
    Text(
        text = "$label: $value",
        color = appTextSecondaryColor(),
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
}


internal data class PendingHomeSshHostKeyTrust(
    val ip: String,
    val username: String,
    val password: String,
    val observedPin: String
)

@Composable
internal fun HomeMainScreen(
    servers: List<InstallServer>,
    reconnectMode: Boolean,
    roomsRefreshing: Boolean,
    onOpenConnect: () -> Unit,
    onOpenCreateMeeting: () -> Unit,
    onRefreshRooms: () -> Unit,
    onMeetingClick: (InstallServer, OpenRoomInfo) -> Unit,
    onUpdateServer: (InstallServer) -> Unit,
    modifier: Modifier = Modifier
) {
    val meetings = servers.flatMap { server ->
        server.openRooms.map { room -> server to room }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val hasMeetings = meetings.isNotEmpty()
        val centeredHeroTop = ((maxHeight - 250.dp) / 2).coerceAtLeast(32.dp)
        val heroTopPadding by animateDpAsState(
            targetValue = if (hasMeetings) 32.dp else centeredHeroTop,
            animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
            label = "homeHeroTopPadding"
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .padding(top = heroTopPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    SymposiumWordmark()

                    Text(
                        text = tr(TextId.NOT_A_PHONE_CONVERSATION),
                        color = appTextSecondaryColor(),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                }

                Spacer(Modifier.height(2.dp))

                HeroActionButton(
                    label = tr(TextId.CONNECT),
                    prompt = ">_",
                    primary = true,
                    enabled = !reconnectMode,
                    onClick = onOpenConnect,
                    modifier = Modifier.fillMaxWidth()
                )
                HeroActionButton(
                    label = tr(TextId.CREATE_MEETING),
                    prompt = "+",
                    primary = false,
                    enabled = true,
                    onClick = onOpenCreateMeeting,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AnimatedVisibility(
                visible = hasMeetings,
                enter = fadeIn(tween(durationMillis = 220, delayMillis = 210)) +
                    expandVertically(tween(durationMillis = 360, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(durationMillis = 140)) +
                    shrinkVertically(tween(durationMillis = 320, easing = FastOutSlowInEasing))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr(TextId.OPEN_ROOMS),
                            color = appTextPrimaryColor(),
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        RefreshIconButton(
                            onClick = onRefreshRooms,
                            enabled = !roomsRefreshing,
                            loading = roomsRefreshing
                        )
                    }

                    meetings.forEach { (server, room) ->
                        HomeMeetingRow(
                            server = server,
                            room = room,
                            onClick = { onMeetingClick(server, room) },
                            onUpdateServer = { onUpdateServer(server) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RefreshIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
    contentDescription: String = tr(TextId.REFRESH_ROOM_LIST),
    loadingContentDescription: String = tr(TextId.REFRESHING_ROOM_LIST)
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(AppButtonShape)
            .background(
                brush = appPrimaryGradient(if (enabled || loading) 1f else 0.35f),
                shape = AppButtonShape
            )
            .border(1.dp, Color.Transparent, AppButtonShape)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                this.contentDescription = if (loading) loadingContentDescription else contentDescription
                if (loading) stateDescription = tr(TextId.LOADING)
                if (!enabled) stateDescription = tr(TextId.UNAVAILABLE)
            }
            .clickable(enabled = enabled && !loading, role = Role.Button) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = AppOnAccent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = AppOnAccent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PlainRefreshButton(
    onClick: () -> Unit,
    enabled: Boolean,
    loading: Boolean
) {
    val iconColor = if (isAppLightTheme()) appTextPrimaryColor() else Color.White

    Box(
        modifier = Modifier
            .size(42.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = tr(
                    if (loading) TextId.REFRESHING_SERVER_LIST else TextId.REFRESH_SERVER_LIST
                )
                if (loading) stateDescription = tr(TextId.LOADING)
                if (!enabled) stateDescription = tr(TextId.UNAVAILABLE)
            }
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = iconColor,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(23.dp)
            )
        }
    }
}


@Composable
private fun CompactTextButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
    backgroundColor: Color = appGrayControlColor(),
    contentColor: Color = appTextPrimaryColor(),
    borderColor: Color = appGrayControlBorderColor()
) {
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(AppButtonShape)
            .background(if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.35f))
            .border(1.dp, if (enabled) borderColor else borderColor.copy(alpha = 0.35f), AppButtonShape)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (loading) stateDescription = tr(TextId.LOADING)
                if (!enabled) stateDescription = tr(TextId.UNAVAILABLE)
            }
            .clickable(enabled = enabled && !loading, role = Role.Button) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = contentColor,
                strokeWidth = 2.dp,
                modifier = Modifier.size(17.dp)
            )
        } else {
            Text(
                text = label,
                color = contentColor,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HeroActionButton(
    label: String,
    prompt: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = if (primary) AppAccent else appGrayControlColor()
    val border = if (primary) Color.Transparent else appGrayControlBorderColor()
    val textColor = if (primary) AppOnAccent else appTextPrimaryColor()
    val promptColor = textColor
    val fillModifier = if (primary) {
        Modifier.background(
            brush = appPrimaryGradient(if (enabled) 1f else 0.35f),
            shape = AppButtonShape
        )
    } else {
        Modifier.background(if (enabled) background else background.copy(alpha = 0.3f))
    }

    val buttonModifier = if (primary) {
        modifier.shadow(
            elevation = 18.dp,
            shape = AppButtonShape,
            clip = false,
            ambientColor = AppAccent.copy(alpha = 0.5f),
            spotColor = AppAccentEnd.copy(alpha = 0.65f)
        )
    } else {
        modifier
    }

    Box(
        modifier = buttonModifier
            .heightIn(min = 54.dp)
            .clip(AppButtonShape)
            .then(fillModifier)
            .border(1.dp, if (enabled) border else border.copy(alpha = 0.35f), AppButtonShape)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (!enabled) stateDescription = tr(TextId.UNAVAILABLE)
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(56.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = prompt,
                color = promptColor,
                fontSize = 23.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }

        val labelModifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)

        Text(
            text = label,
            color = textColor,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = labelModifier
        )
    }
}

@Composable
private fun SymposiumWordmark(modifier: Modifier = Modifier) {
    Text(
        text = tr(TextId.BRAND_WORDMARK),
        color = appTextPrimaryColor(),
        fontFamily = Dos2000FontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 42.sp,
        lineHeight = 46.sp,
        letterSpacing = 0.2.sp,
        modifier = modifier
    )
}

@Composable
private fun HomeMeetingRow(
    server: InstallServer,
    room: OpenRoomInfo,
    onClick: () -> Unit,
    onUpdateServer: () -> Unit
) {
    val updateRequired = serverNeedsRelayUpdate(server)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = appRoomSurfaceColor(),
        shape = InnerCardShape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, appGrayControlBorderColor(), InnerCardShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = room.name,
                    color = appTextPrimaryColor(),
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = tr(TextId.IP_VALUE, serverDisplayAddress(server)),
                    color = AppAccent,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = tr(TextId.TAP_TO_OPEN),
                    color = appTextSecondaryColor(),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (updateRequired) {
                    Text(
                        text = tr(TextId.SERVER_REQUIRES_UPDATE),
                        color = AppWarning,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier.size(42.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.MeetingRoom,
                        contentDescription = tr(TextId.OPEN_ROOM),
                        tint = appTextPrimaryColor(),
                        modifier = Modifier.size(24.dp)
                    )
                }
                if (updateRequired) {
                    CompactTextButton(
                        label = tr(TextId.REFRESH),
                        onClick = onUpdateServer,
                        backgroundColor = AppWarning,
                        contentColor = Color.White,
                        borderColor = AppWarning
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomeConnectDialog(
    connectLink: String,
    onConnectLinkChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    parsed: InvitationTokenPayload?,
    tlsTrust: InvitationTlsTrust?,
    reconnectMode: Boolean,
    onDismiss: () -> Unit,
    onAcceptChangedTlsPin: () -> Unit,
    onConnect: () -> Unit
) {
    AppDialog(
        title = tr(TextId.CONNECT),
        onDismiss = onDismiss,
        content = {
            InvitationTokenInput(connectLink, onConnectLinkChange, tr(TextId.MEETING_LINK))

            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(tr(TextId.NAME_OPTIONAL)) },
                colors = textFieldColors()
            )

            if (parsed == null && connectLink.isNotBlank()) {
                ErrorMessage(tr(TextId.THE_LINK_WAS_NOT_RECOGNIZED_PASTE_A_SYMPOSIUM_LINK_2))
            }

            if (parsed != null && tlsTrust != null) {
                InvitationSecurityCard(parsed, tlsTrust, onAcceptChangedTlsPin)
            }
        },
        actions = {
            ActionButton(
                label = tr(TextId.CONNECT),
                onClick = onConnect,
                enabled = !reconnectMode && parsed != null && tlsTrust?.mayConnect == true,
                modifier = Modifier.weight(1f),
                textColorOverride = AppOnAccent
            )
        }
    )
}

internal fun canCreateMeetingOnServer(server: InstallServer): Boolean {
    return serverAllowsRoomCreation(server) &&
        !server.relayTlsPin.isNullOrBlank() &&
        server.adminTokenAvailable
}

internal fun serverAllowsRoomCreation(server: InstallServer): Boolean {
    return server.installed && server.relayVersionState == RelayVersionState.CURRENT
}

internal fun serverNeedsRelayUpdate(server: InstallServer): Boolean {
    return server.installed && server.relayVersionState == RelayVersionState.OUTDATED
}

@Composable
internal fun HomeCreateMeetingDialog(
    servers: List<InstallServer>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onAddServer: () -> Unit,
    onOpenServerGuide: () -> Unit,
    onRefresh: () -> Unit,
    onServerClick: (InstallServer) -> Unit,
    onUpdateServer: (InstallServer) -> Unit
) {
    AppDialog(
        title = tr(TextId.CREATE_MEETING),
        onDismiss = onDismiss,
        dismissEnabled = true,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = tr(TextId.SELECT_THE_SERVER_WHERE_THE_NEW_ROOM_WILL_BE_OPENED),
                    color = appTextSecondaryColor(),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactTextButton(
                        label = tr(TextId.WHERE_CAN_I_GET_A_SERVER),
                        onClick = onOpenServerGuide,
                        enabled = !loading,
                        backgroundColor = AppError,
                        contentColor = Color.White,
                        borderColor = AppError
                    )

                    Spacer(Modifier.width(12.dp))

                    PlainRefreshButton(
                        onClick = onRefresh,
                        enabled = !loading,
                        loading = loading
                    )
                }

                if (servers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(InnerCardShape)
                            .background(appSurfaceElevatedColor())
                            .border(1.dp, Color.Transparent, InnerCardShape)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = tr(TextId.THERE_ARE_NO_SERVERS_YET_ADD_ONE_TO_CREATE_A_MEETING),
                            color = appTextSecondaryColor(),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                } else {
                    servers.forEach { server ->
                        HomeServerPickerRow(
                            server = server,
                            enabled = !loading,
                            onClick = { onServerClick(server) },
                            onUpdate = { onUpdateServer(server) }
                        )
                    }
                }
            }
        },
        actions = {
            ActionButton(
                label = tr(TextId.ADD_SERVER),
                onClick = onAddServer,
                kind = ActionButtonKind.PRIMARY,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

@Composable
internal fun HomeServerGuideDialog(
    onDismiss: () -> Unit
) {
    val guideContentMaxHeight = (LocalConfiguration.current.screenHeightDp.dp - 260.dp)
        .coerceIn(220.dp, 540.dp)

    AppDialog(
        title = tr(TextId.WHERE_CAN_I_GET_A_SERVER),
        onDismiss = onDismiss,
        dismissEnabled = true,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = guideContentMaxHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = tr(TextId.A_CONFERENCE_REQUIRES_A_REGULAR_VPS_A_SMALL_VIRTUAL_SERVER_WITH_A_PUBLIC),
                    color = appTextSecondaryColor(),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                ServerGuideBlock(
                    title = tr(TextId.WHAT_SHOULD_I_BUY),
                    body = tr(TextId.A_VPS_OR_CLOUD_SERVER_RUNNING_DEBIAN_OR_UBUNTU_MINIMUM_1_VCPU_AND_2_GB_R),
                    color = AppAccent
                )

                ServerGuideBlock(
                    title = tr(TextId.WHERE_SHOULD_I_BUY_IT),
                    body = tr(TextId.CHOOSE_A_MAJOR_VPS_PROVIDER_THAT_IS_NOT_RESTRICTED_IN_THE_REGIONS_WHERE_),
                    color = AppSuccess
                )

                ServerGuideBlock(
                    title = tr(TextId.WHICH_PROVIDERS_SHOULD_I_AVOID),
                    body = tr(TextId.AVOID_PROVIDERS_THAT_ARE_RESTRICTED_IN_PARTICIPANTS_REGIONS_FOR_EXAMPLE_),
                    color = AppError
                )

                ServerGuideBlock(
                    title = tr(TextId.HOW_TO_CONFIGURE_IT_WHEN_BUYING),
                    body = tr(TextId.CHOOSE_DEBIAN_OR_UBUNTU_ENABLE_IPV4_AND_SAVE_THE_IP_ADDRESS_LOGIN_AND_PA),
                    color = AppWarning
                )

                ServerGuideBlock(
                    title = tr(TextId.WHAT_DO_I_DO_NEXT),
                    body = tr(TextId.AFTER_PURCHASE_TAP_ADD_AND_ENTER_THE_IP_ADDRESS_LOGIN_AND_PASSWORD_CONFI),
                    color = appTextSecondaryColor()
                )
            }
        },
        actions = {
            ActionButton(
                label = tr(TextId.GOT_IT),
                onClick = onDismiss,
                kind = ActionButtonKind.PRIMARY,
                modifier = Modifier.weight(1f)
            )
        }
    )
}

@Composable
private fun ServerGuideBlock(
    title: String,
    body: String,
    color: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(InnerCardShape)
            .background(appSurfaceElevatedColor())
            .border(1.dp, Color.Transparent, InnerCardShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InfoBadge(label = title, color = color)
        Text(
            text = body,
            color = appTextPrimaryColor(),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun HomeServerPickerRow(
    server: InstallServer,
    enabled: Boolean,
    onClick: () -> Unit,
    onUpdate: () -> Unit
) {
    val updateRequired = serverNeedsRelayUpdate(server)
    val problem = when {
        !server.installed -> tr(TextId.SYMPOSIUMRELAY_IS_NOT_INSTALLED_TAP_TO_INSTALL_IT)
        server.relayTlsPin.isNullOrBlank() -> tr(TextId.THE_TLS_PIN_IS_MISSING_TAP_TO_REINSTALL_RELAY)
        !server.adminTokenAvailable -> tr(TextId.THE_ADMIN_TOKEN_IS_MISSING_TAP_TO_REINSTALL_RELAY)
        server.relayVersionState == RelayVersionState.UNKNOWN ->
            tr(TextId.VERSION_CHECK_FAILED_RETRY)
        else -> null
    }

    val actionLabel = when {
        problem == null -> tr(TextId.SELECT)
        !server.installed -> tr(TextId.INSTALL)
        server.relayTlsPin.isNullOrBlank() || !server.adminTokenAvailable -> tr(TextId.FIX)
        server.relayVersionState == RelayVersionState.UNKNOWN -> tr(TextId.CHECK)
        else -> tr(TextId.FIX)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = appSurfaceElevatedColor(),
        shape = InnerCardShape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, appGrayControlBorderColor(), InnerCardShape)
                .clickable(enabled = enabled, role = Role.Button) { onClick() }
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = serverDisplayAddress(server),
                    color = if (enabled) appTextPrimaryColor() else appTextSecondaryColor(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = problem ?: if (updateRequired) {
                        tr(TextId.SERVER_REQUIRES_UPDATE)
                    } else {
                        tr(TextId.OPEN_ROOMS_COUNT, openRoomsLabel(server.openRooms.size))
                    },
                    color = if (problem == null && !updateRequired) appTextSecondaryColor() else AppWarning,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(10.dp))
            if (updateRequired && problem == null) {
                CompactTextButton(
                    label = tr(TextId.REFRESH),
                    onClick = onUpdate,
                    enabled = enabled,
                    backgroundColor = AppWarning,
                    contentColor = Color.White,
                    borderColor = AppWarning
                )
            } else {
                InfoBadge(
                    label = actionLabel,
                    color = if (problem == null) AppAccent else AppWarning
                )
            }
        }
    }
}

@Composable
internal fun HomeAddServerDialog(
    ip: String,
    username: String,
    password: String,
    error: String?,
    loading: Boolean,
    onIpChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onAdd: () -> Unit
) {
    AppDialog(
        title = tr(TextId.ADD_SERVER),
        onDismiss = onDismiss,
        dismissEnabled = !loading,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = ip,
                    onValueChange = onIpChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(tr(TextId.IP_ADDRESS)) },
                    colors = textFieldColors()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(tr(TextId.LOGIN)) },
                    colors = textFieldColors()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
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
                if (error != null) {
                    ErrorMessage(error)
                }
            }
        },
        actions = {
            ActionButton(
                label = tr(TextId.ADD),
                onClick = onAdd,
                enabled = !loading,
                loading = loading,
                modifier = Modifier.weight(1f)
            )
        }
    )
}

@Composable
internal fun HomeSshHostKeyTrustDialog(
    trust: PendingHomeSshHostKeyTrust,
    loading: Boolean,
    onCopy: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        title = tr(TextId.TRUST_SSH_KEY),
        onDismiss = onDismiss,
        dismissEnabled = !loading,
        content = {
            Text(
                text = tr(TextId.FIRST_SERVER_CONNECTION_SHORT, trust.ip),
                color = appTextPrimaryColor(),
                fontSize = 14.sp,
                lineHeight = 19.sp
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(InnerCardShape)
                    .background(appBackgroundColor())
                    .border(1.dp, appBorderColor(), InnerCardShape)
                    .padding(12.dp)
            ) {
                Text(trust.observedPin, color = appTextPrimaryColor(), fontSize = 13.sp, lineHeight = 18.sp)
            }
        },
        actions = {
            ActionButton(
                label = tr(TextId.COPY),
                onClick = onCopy,
                kind = ActionButtonKind.SECONDARY,
                enabled = !loading,
                modifier = Modifier.weight(1f)
            )
            ActionButton(
                label = tr(TextId.TRUST),
                onClick = onConfirm,
                enabled = !loading,
                loading = loading,
                modifier = Modifier.weight(1f)
            )
        }
    )
}

@Composable
internal fun HomeInstallRelayDialog(
    server: InstallServer,
    logs: List<String>,
    error: String?,
    loading: Boolean,
    onDismiss: () -> Unit,
    onCapacityChange: (RelayCapacityConfig) -> Unit,
    onInstall: () -> Unit
) {
    val updating = serverNeedsRelayUpdate(server)
    val logListState = rememberLazyListState()
    val visibleLogCount = logs.takeLast(120).size

    LaunchedEffect(logs.size) {
        if (visibleLogCount > 0) {
            logListState.animateScrollToItem(visibleLogCount - 1)
        }
    }

    AppDialog(
        title = if (updating) tr(TextId.UPDATE_SYMPOSIUMRELAY) else tr(TextId.INSTALL_SYMPOSIUMRELAY),
        onDismiss = onDismiss,
        dismissEnabled = !loading,
        scrollableContent = true,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoBadge(label = serverDisplayAddress(server), color = AppAccent)
                Text(
                    text = if (updating) {
                        tr(TextId.UPDATE_RELAY_TO_CONTINUE)
                    } else {
                        tr(TextId.A_MEETING_CANNOT_BE_CREATED_ON_THIS_SERVER_YET_INSTALL_SYMPOSIUMRELAY_FI)
                    },
                    color = appTextSecondaryColor(),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                if (!updating) {
                    RelayAdvancedSettingsButton(
                        config = server.relayCapacity,
                        enabled = !loading,
                        onSave = onCapacityChange
                    )
                }
                if (server.username.isBlank() || !server.sshKeyInstalled) {
                    ErrorMessage(
                        if (updating) {
                            tr(TextId.A_CONFIGURED_SSH_KEY_IS_REQUIRED_FOR_UPDATING)
                        } else {
                            tr(TextId.A_CONFIGURED_SSH_KEY_IS_REQUIRED_FOR_INSTALLATION_ADD_THE_SERVER_AGAIN)
                        }
                    )
                }
                if (error != null) {
                    ErrorMessage(error)
                }
                if (logs.isNotEmpty()) {
                    LazyColumn(
                        state = logListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 240.dp)
                            .clip(InnerCardShape)
                            .background(appSurfaceElevatedColor())
                            .border(1.dp, Color.Transparent, InnerCardShape),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(logs.takeLast(120)) { line ->
                            Text(
                                text = line,
                                color = appTextSecondaryColor(),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        },
        actions = {
            ActionButton(
                label = if (updating) tr(TextId.UPDATE_SYMPOSIUMRELAY) else tr(TextId.INSTALL_SYMPOSIUMRELAY),
                onClick = onInstall,
                enabled = !loading && server.username.isNotBlank() && server.sshKeyInstalled,
                loading = loading,
                modifier = Modifier.weight(1f)
            )
        }
    )
}

@Composable
internal fun HomeCreateRoomDialog(
    server: InstallServer,
    roomName: String,
    error: String?,
    loading: Boolean,
    onRoomNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreate: () -> Unit
) {
    val canCreate = !loading && roomName.isNotBlank() && serverAllowsRoomCreation(server)
    AppDialog(
        title = tr(TextId.NEW_MEETING),
        onDismiss = onDismiss,
        dismissEnabled = !loading,
        scrollableContent = true,
        content = {
            if (!serverAllowsRoomCreation(server)) {
                ErrorMessage(
                    if (serverNeedsRelayUpdate(server)) {
                        tr(TextId.UPDATE_SELECTED_RELAY_FIRST)
                    } else {
                        tr(TextId.WAIT_FOR_RELAY_VERSION_CHECK)
                    }
                )
            }
            OutlinedTextField(
                value = roomName,
                onValueChange = onRoomNameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !loading,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canCreate) onCreate() }),
                label = { Text(tr(TextId.ROOM_NAME)) },
                colors = textFieldColors()
            )
            if (error != null) {
                ErrorMessage(error)
            }
        },
        actions = {
            ActionButton(
                label = tr(TextId.CREATE),
                onClick = onCreate,
                enabled = canCreate,
                loading = loading,
                modifier = Modifier.weight(1f)
            )
        }
    )
}

@Composable
internal fun HomeMeetingDialog(
    server: InstallServer,
    room: OpenRoomInfo,
    loading: Boolean,
    muteAllByDefault: Boolean,
    admitWithoutApproval: Boolean,
    onMuteAllByDefaultChange: (Boolean) -> Unit,
    onAdmitWithoutApprovalChange: (Boolean) -> Unit,
    onSaveSettings: () -> Unit,
    onCopyGuest: () -> Unit,
    onQrGuest: () -> Unit,
    onCopyModerator: () -> Unit,
    onQrModerator: () -> Unit,
    onRotateModeratorLink: () -> Unit,
    onRotateE2eeKey: () -> Unit,
    onCloseMeeting: () -> Unit,
    onDismiss: () -> Unit
) {
    var settingsExpanded by remember(server.id, room.name) { mutableStateOf(false) }
    AppDialog(
        title = room.name,
        onDismiss = onDismiss,
        dismissEnabled = !loading,
        scrollableContent = true,
        maxWidth = 680.dp,
        content = {
            RoomLinkActionsRow(
                title = tr(TextId.GUEST),
                subtitle = tr(TextId.CONNECTION_LINK_WITHOUT_MODERATOR_PERMISSIONS),
                badgeColor = appTextSecondaryColor(),
                onCopy = onCopyGuest,
                onQr = onQrGuest,
                enabled = !loading,
                comfortable = true
            )
            RoomLinkActionsRow(
                title = tr(TextId.MODERATOR),
                subtitle = if (!room.moderatorKeyAvailable) {
                    tr(TextId.MODERATOR_KEY_WAS_NOT_RECEIVED)
                } else {
                    tr(TextId.LINK_WITH_ROOM_MANAGEMENT_PERMISSIONS)
                },
                badgeColor = AppAccent,
                onCopy = onCopyModerator,
                onQr = onQrModerator,
                enabled = !loading && room.moderatorKeyAvailable,
                comfortable = true
            )
            RoomSettingsToggle(
                expanded = settingsExpanded,
                onClick = { settingsExpanded = !settingsExpanded }
            )
            AnimatedVisibility(visible = settingsExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RoomSettingSwitch(
                        title = tr(TextId.MUTE_MICROPHONES_ON_ENTRY),
                        subtitle = tr(TextId.NEW_PARTICIPANTS_JOIN_WITH_THEIR_MICROPHONE_MUTED),
                        checked = muteAllByDefault,
                        enabled = !loading,
                        onCheckedChange = onMuteAllByDefaultChange
                    )
                    RoomSettingSwitch(
                        title = tr(TextId.ADMIT_WITHOUT_APPROVAL),
                        subtitle = tr(TextId.PARTICIPANTS_JOIN_IMMEDIATELY_WITHOUT_ENTERING_THE_WAITING_ROOM),
                        checked = admitWithoutApproval,
                        enabled = !loading,
                        onCheckedChange = onAdmitWithoutApprovalChange
                    )
                    ActionButton(
                        label = tr(TextId.SAVE_SETTINGS),
                        onClick = onSaveSettings,
                        enabled = !loading && (
                            muteAllByDefault != room.muteAllByDefault ||
                                admitWithoutApproval != room.admitWithoutApproval
                            ),
                        loading = loading,
                        kind = ActionButtonKind.SECONDARY,
                        modifier = Modifier.fillMaxWidth()
                    )
                    RoomDialogTextAction(
                        label = tr(TextId.CHANGE_MODERATOR_LINK),
                        onClick = onRotateModeratorLink,
                        enabled = !loading
                    )
                    RoomDialogTextAction(
                        label = tr(TextId.REFRESH_E2EE_KEY),
                        onClick = onRotateE2eeKey,
                        enabled = !loading
                    )
                }
            }
        },
        actions = {
            ActionButton(
                label = tr(TextId.CLOSE_ROOM),
                onClick = onCloseMeeting,
                kind = ActionButtonKind.GHOST,
                textColorOverride = AppError,
                borderColorOverride = AppError.copy(alpha = 0.35f),
                enabled = !loading,
                loading = loading,
                modifier = Modifier.weight(1f)
            )
        }
    )
}

@Composable
private fun RoomSettingsToggle(expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(InnerCardShape)
            .background(appSurfaceElevatedColor())
            .semantics(mergeDescendants = true) {
                stateDescription = tr(if (expanded) TextId.EXPANDED else TextId.COLLAPSED)
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = tr(TextId.ROOM_SETTINGS),
            color = appTextPrimaryColor(),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = appTextSecondaryColor()
        )
    }
}

@Composable
internal fun RoomSettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(InnerCardShape)
            .background(appSurfaceElevatedColor())
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(title, color = appTextPrimaryColor(), fontSize = 14.sp)
            Text(
                subtitle,
                color = appTextSecondaryColor(),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun RoomDialogTextAction(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(AppButtonShape)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (!enabled) stateDescription = tr(TextId.UNAVAILABLE)
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = null,
            tint = if (enabled) appTextSecondaryColor() else appTextMutedColor(),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = if (enabled) appTextSecondaryColor() else appTextMutedColor(),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

internal fun isInitialConnectionFailure(status: String): Boolean {
    val normalized = status.trim().lowercase()
    return normalized.contains("invalid") ||
        normalized.contains("failed") ||
        normalized.contains("server error")
}

internal fun initialConnectionStatusMessage(status: String): String {
    val normalized = status.trim().lowercase()
    return when {
        normalized.contains("invalid e2ee") -> tr(TextId.THE_LINK_CONTAINS_AN_INVALID_ENCRYPTION_KEY)
        normalized.contains("invalid tls") -> tr(TextId.THE_SECURE_SERVER_CONNECTION_COULD_NOT_BE_VERIFIED)
        normalized.contains("e2ee initialization failed") -> tr(TextId.END_TO_END_ENCRYPTION_COULD_NOT_BE_ENABLED)
        normalized.contains("server error") -> tr(TextId.THE_SERVER_REJECTED_THE_CONNECTION)
        normalized.contains("reconnecting") -> tr(TextId.RETRYING_THE_CONNECTION)
        normalized.contains("connecting") -> tr(TextId.ESTABLISHING_A_SECURE_CONNECTION)
        else -> tr(TextId.PREPARING_THE_CONNECTION)
    }
}

@Composable
internal fun ConnectingRoomView(
    roomName: String,
    status: String,
    onCancel: () -> Unit
) {
    val failed = isInitialConnectionFailure(status)
    val accent = if (failed) accessibleDangerColor() else accessibleAccentColor()
    val transition = rememberInfiniteTransition(label = "room_connection")
    val pulse by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "room_connection_pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appBackgroundColor())
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .graphicsLayer {
                            val scale = if (failed) 1f else pulse
                            scaleX = scale
                            scaleY = scale
                            alpha = if (failed) 0.42f else 0.3f + (pulse - 0.88f)
                        }
                        .border(2.dp, accent, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (failed) tr(TextId.COULD_NOT_CONNECT) else tr(TextId.CONNECTING_TO_ROOM),
                    color = appTextPrimaryColor(),
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (roomName.isNotBlank()) {
                    Text(
                        text = roomName,
                        color = accent,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = initialConnectionStatusMessage(status),
                    color = appTextSecondaryColor(),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
            }

            ActionButton(
                label = if (failed) tr(TextId.GO_BACK) else tr(TextId.CANCEL_CONNECTION),
                onClick = onCancel,
                kind = ActionButtonKind.SECONDARY,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun LobbyWaitingView(onCancel: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appBackgroundColor())
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        SectionCard(
            title = tr(TextId.WAITING_FOR_APPROVAL),
            subtitle = tr(TextId.YOU_JOINED_AS_A_GUEST_A_MODERATOR_MUST_ADMIT_YOU_TO_THE_ROOM)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(color = AppAccent, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                Text(
                    text = tr(TextId.WAITING_FOR_THE_MODERATOR),
                    color = appTextPrimaryColor(),
                    fontSize = 14.sp
                )
            }
            ActionButton(
                label = tr(TextId.CANCEL_CONNECTION),
                onClick = onCancel,
                kind = ActionButtonKind.SECONDARY,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
