package com.decentralprospect.symposium

import android.Manifest
import android.app.AlertDialog
import android.app.KeyguardManager
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Rational
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    internal val remoteInstaller by lazy { RemoteInstaller(applicationContext) }

    internal var telemetryEnabledState by mutableStateOf(false)
    internal var welcomeNewsPromptVisibleState by mutableStateOf(false)
    internal var appThemeModeState by mutableStateOf(AppThemeMode.SYSTEM)
    internal var appLanguageState by mutableStateOf(AppLanguage.SYSTEM)
    internal var secretProtectionEnabledState by mutableStateOf(true)
    internal var secretProtectionChoicePromptVisibleState by mutableStateOf(false)
    internal var relayInstallStartedAtMs = 0L
    internal var relayInstallLastStage = "not_started"

    internal var callRuntimeState by mutableStateOf<CallRuntime?>(null)
    internal var pictureInPictureModeState by mutableStateOf(false)

    private var boundCallService: CallService? = null
    private var callServiceBound = false
    private var pendingUiBinder: UiBinder? = null
    private var lastAttachedBinder: UiBinder? = null
    private var pendingConnect: PendingConnect? = null
    private var adminSessionAccessGranted = false
    private var appUiInitialized = false
    private var lastObscuredTouchWarningAtMs = 0L
    private val legacySshMigrationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    internal var telemetrySessionId: String = UUID.randomUUID().toString().replace("-", "").take(16)
    internal var localRole: String = ROLE_GUEST
    internal var reconnectMode = false
    internal var reconnectAttemptCount = 0
    internal var publishPcState: String = "new"
    internal var subscribePcState: String = "new"
    internal var publishIceState: String = "new"
    internal var subscribeIceState: String = "new"
    internal var conferenceConnectStartedAtMs = 0L
    internal var conferenceMediaOnlineAtMs = 0L
    internal var conferenceEndedSent = false
    internal var reconnectStartedAtMs = 0L
    internal var reconnectsInSession = 0
    internal var maxPingMsInSession = 0L
    internal var slowPingCountInSession = 0
    internal var criticalPingCountInSession = 0
    internal var pingReconnectCountInSession = 0
    internal var wsErrorCountInSession = 0
    internal var iceBadStateCountInSession = 0
    internal var pcBadStateCountInSession = 0
    internal var audioErrorCountInSession = 0
    internal var cameraErrorCountInSession = 0
    internal var statsWarningCountInSession = 0
    internal var lastPingTelemetryAtMs = 0L
    internal var lastStatsTelemetryAtMs = 0L

    private data class PendingConnect(
        val url: String,
        val room: String,
        val roomInstance: String,
        val username: String,
        val tlsPin: String,
        val modKey: String,
        val e2eeSecret: String
    )

    private val permissionRequester = object : CallPermissionRequester {
        override fun requestPostNotifications() {
            if (Build.VERSION.SDK_INT >= 33) {
                val granted = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        override fun requestRecordAudio(startServiceAfterGrant: Boolean) {
            callRuntimeState?.pendingStartCallServiceAfterMicPermission = startServiceAfterGrant
            requestRecordAudio.launch(Manifest.permission.RECORD_AUDIO)
        }

        override fun requestCamera() {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val callService = (service as CallService.LocalBinder).service()
            boundCallService = callService
            callServiceBound = true
            val runtime = callService.runtime
            // Consent can be changed while binding is still in progress. Reload it before the
            // runtime starts handling a pending connection so diagnostics cannot stay stale.
            runtime.loadTelemetryPrivacyPrefs()
            runtime.attachPermissionRequester(permissionRequester)
            callRuntimeState = runtime
            runtime.recoverVideoCaptureAfterForeground()
            pendingUiBinder?.let { binder ->
                runtime.attachUiBinder(binder)
                lastAttachedBinder = binder
            }
            pendingConnect?.let { pending ->
                pendingConnect = null
                runtime.connect(
                    pending.url,
                    pending.room,
                    pending.roomInstance,
                    pending.username,
                    pending.tlsPin,
                    pending.modKey,
                    pending.e2eeSecret
                )
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            callRuntimeState?.detachPermissionRequester(permissionRequester)
            boundCallService = null
            callServiceBound = false
            callRuntimeState = null
        }
    }

    internal val requestRecordAudio = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        callRuntimeState?.onRecordAudioPermissionResult(granted)
    }

    internal val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        callRuntimeState?.onCameraPermissionResult(granted)
    }

    internal val requestPostNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        debugLog(TAG, "POST_NOTIFICATIONS granted=$granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.decorView.filterTouchesWhenObscured = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(true)
        }
        authenticateAdminSessionAtStartup()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val obscured = event.flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED != 0
        val partiallyObscured = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            event.flags and MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED != 0
        if (obscured || partiallyObscured) {
            val now = System.currentTimeMillis()
            if (now - lastObscuredTouchWarningAtMs > 3_000L) {
                lastObscuredTouchWarningAtMs = now
                Toast.makeText(
                    this,
                    tr(TextId.TOUCH_BLOCKED_ANOTHER_APP_IS_COVERING_SYMPOSIUM),
                    Toast.LENGTH_LONG
                ).show()
            }
            return false
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onStart() {
        super.onStart()
        if (!appUiInitialized) return
        ensureCallServiceBound()
        callRuntimeState?.attachPermissionRequester(permissionRequester)
        callRuntimeState?.recoverVideoCaptureAfterForeground()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val runtime = callRuntimeState
        val callIsActive = runtime?.connectedUiState == true || runtime?.reconnectMode == true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !callIsActive || isInPictureInPictureMode) {
            return
        }

        val params = buildPictureInPictureParams(
            VideoTracksStore.focusedFrameAspectRatio() ?: 1f
        )
        runCatching { enterPictureInPictureMode(params) }
            .onFailure { error -> diagnosticWarning(TAG, "Unable to enter picture-in-picture: ${error.message}") }
    }

    internal fun updatePictureInPictureAspectRatio(aspectRatio: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !isInPictureInPictureMode) return
        runCatching { setPictureInPictureParams(buildPictureInPictureParams(aspectRatio)) }
            .onFailure { error -> diagnosticWarning(TAG, "Unable to resize picture-in-picture: ${error.message}") }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPictureInPictureParams(aspectRatio: Float): PictureInPictureParams {
        val safeAspectRatio = aspectRatio
            .takeIf { it.isFinite() && it > 0f }
            ?.coerceIn(0.5f, 2f)
            ?: 1f
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational((safeAspectRatio * 1_000).roundToInt(), 1_000))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setSeamlessResizeEnabled(true)
                }
            }
            .build()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pictureInPictureModeState = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        callRuntimeState?.detachPermissionRequester(permissionRequester)
    }

    override fun onDestroy() {
        lastAttachedBinder?.let { binder -> callRuntimeState?.detachUiBinder(binder) }
        lastAttachedBinder = null
        if (callServiceBound) {
            runCatching { unbindService(serviceConnection) }
            callServiceBound = false
        }
        boundCallService = null
        callRuntimeState = null
        adminSessionAccessGranted = false
        legacySshMigrationScope.cancel()
        super.onDestroy()
    }

    internal fun requestNotificationPermissionIfNeeded() {
        permissionRequester.requestPostNotifications()
    }

    private fun authenticateAdminSessionAtStartup() {
        loadLanguagePrefs()
        loadSecretProtectionPrefs()
        if (!secretProtectionEnabledState) {
            grantAdminSessionAndInitializeApp()
            return
        }

        requestDeviceAuthentication(
            onSuccess = {
                if (migrateAuthenticatedSecrets()) {
                    grantAdminSessionAndInitializeApp()
                } else {
                    Toast.makeText(
                        this,
                        tr(TextId.COULD_NOT_OPEN_PROTECTED_DATA),
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            },
            onError = { finish() }
        )
    }

    internal fun requestDeviceAuthentication(
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val biometricManager = BiometricManager.from(this)
        val authenticationAvailable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS ||
                getSystemService(KeyguardManager::class.java).isDeviceSecure
        }
        if (!authenticationAvailable) {
            onError()
            return
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError()
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(tr(TextId.ACCESS_PROTECTED_DATA))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowedAuthenticators(authenticators)
                } else {
                    @Suppress("DEPRECATION")
                    setDeviceCredentialAllowed(true)
                }
            }
            .build()
        prompt.authenticate(promptInfo)
    }

    internal fun migrateAuthenticatedSecrets(): Boolean = runCatching {
        InstallServersStore(applicationContext).load()
        ConferenceE2eeSecretStore(applicationContext).migrateAll()
        RelayTlsPinTrustStore(applicationContext).migrateAll()
    }.isSuccess

    private fun grantAdminSessionAndInitializeApp() {
        if (appUiInitialized || isFinishing || isDestroyed) return
        adminSessionAccessGranted = true
        appUiInitialized = true
        initializeMainActivityAfterCreate()
        migrateLegacySshCredentialsForSession()
        showRootSecurityWarningIfNeeded()
    }

    private fun showRootSecurityWarningIfNeeded() {
        val evidence = collectRootWarningEvidence()
        if (!evidence.shouldWarn) return

        val prefs = getSharedPreferences(ROOT_WARNING_PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getInt(ROOT_WARNING_ACKNOWLEDGED_VERSION, -1) == BuildConfig.VERSION_CODE) return

        window.decorView.post {
            if (isFinishing || isDestroyed) return@post
            AlertDialog.Builder(this)
                .setTitle(tr(TextId.SECURITY_WARNING))
                .setMessage(
                    tr(TextId.SIGNS_OF_ROOT_ACCESS_WERE_DETECTED_ON_THIS_DEVICE_APPS_WITH_ROOT_ACCESS_)
                )
                .setPositiveButton(tr(TextId.I_UNDERSTAND)) { _, _ ->
                    prefs.edit()
                        .putInt(ROOT_WARNING_ACKNOWLEDGED_VERSION, BuildConfig.VERSION_CODE)
                        .apply()
                }
                .setCancelable(true)
                .show()
        }
    }

    internal fun hasAdminSessionAccess(): Boolean = adminSessionAccessGranted

    internal fun migrateLegacySshCredentialsForSession() {
        val servers = runCatching { InstallServersStore(applicationContext).load() }
            .getOrElse { return }
            .filter { server ->
                server.sshKeyInstalled &&
                    server.ip.isNotBlank() &&
                    server.username.isNotBlank() &&
                    !server.sshHostKeyPin.isNullOrBlank()
            }

        legacySshMigrationScope.launch {
            servers.forEach { server ->
                runCatching {
                    remoteInstaller.migrateLegacySshCredential(
                        serverIp = server.ip,
                        login = server.username,
                        expectedSshHostKeyPin = server.sshHostKeyPin
                    )
                }
            }
        }
    }

    internal fun ensureCallServiceBound() {
        if (callServiceBound) return
        val intent = Intent(this, CallService::class.java).apply {
            action = CallService.ACTION_BIND_ONLY
        }
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    internal fun attachCallUiBinder(binder: UiBinder) {
        pendingUiBinder = binder
        lastAttachedBinder = binder
        callRuntimeState?.attachUiBinder(binder)
    }

    internal fun connectViaCallService(
        url: String,
        room: String,
        roomInstance: String,
        username: String,
        tlsPin: String,
        modKey: String,
        e2eeSecret: String
    ) {
        val runtime = callRuntimeState
        if (runtime != null) {
            runtime.connect(url, room, roomInstance, username, tlsPin, modKey, e2eeSecret)
        } else {
            pendingConnect = PendingConnect(url, room, roomInstance, username, tlsPin, modKey, e2eeSecret)
            ensureCallServiceBound()
        }
    }

    private companion object {
        const val ROOT_WARNING_PREFS_NAME = "root_warning_preferences"
        const val ROOT_WARNING_ACKNOWLEDGED_VERSION = "acknowledged_version"
    }
}
