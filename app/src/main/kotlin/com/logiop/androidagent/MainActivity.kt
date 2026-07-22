package com.logiop.androidagent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.logiop.androidagent.brain.ModelRepository
import com.logiop.androidagent.hands.AgentAccessibilityService
import com.logiop.androidagent.overlay.OverlayService
import com.logiop.androidagent.ui.theme.AndroidAgentTheme

class MainActivity : FragmentActivity() {

    companion object {
        const val EXTRA_REQUEST_MIC = "com.logiop.androidagent.REQUEST_MIC"
        const val EXTRA_IMPORT_MODEL = "com.logiop.androidagent.IMPORT_MODEL"
    }

    private val overlayGranted = mutableStateOf(false)
    private val micGranted = mutableStateOf(false)
    private val accessibilityEnabled = mutableStateOf(false)
    private val modelReady = mutableStateOf(false)
    private val agentRunning = mutableStateOf(false)

    /** Set when the microphone request should be followed by starting the overlay. */
    private var startOverlayAfterMic = false

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best effort */ }

    private val requestMic =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            micGranted.value = granted
            if (startOverlayAfterMic) {
                startOverlayAfterMic = false
                // Start the overlay even if the mic was denied; the bubble
                // signals the missing permission when the user tries to talk.
                startOverlay()
            }
        }

    private val pickModel =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importModel(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            AndroidAgentTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AgentScreen(
                        modifier = Modifier.padding(innerPadding),
                        overlayGranted = overlayGranted.value,
                        micGranted = micGranted.value,
                        accessibilityEnabled = accessibilityEnabled.value,
                        modelReady = modelReady.value,
                        agentRunning = agentRunning.value,
                        onGrantOverlay = ::openOverlaySettings,
                        onGrantMic = ::requestMicPermission,
                        onGrantAccessibility = ::openAccessibilitySettings,
                        onImportModel = ::launchModelPicker,
                        onManageWhitelist = ::openWhitelist,
                        onShowLog = ::openLog,
                        onShowSkills = ::openSkills,
                        onActivate = ::authenticateAndStart,
                        onDeactivate = ::stopOverlay,
                    )
                }
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        overlayGranted.value = Settings.canDrawOverlays(this)
        micGranted.value = hasMicPermission()
        accessibilityEnabled.value = AgentAccessibilityService.isEnabled(this)
        modelReady.value = ModelRepository.isReady(this)
        agentRunning.value = OverlayService.isRunning
    }

    /** Handles requests forwarded from the overlay bubble (grant mic / import model). */
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_REQUEST_MIC, false) == true && !hasMicPermission()) {
            requestMicPermission()
        }
        if (intent?.getBooleanExtra(EXTRA_IMPORT_MODEL, false) == true && !ModelRepository.isReady(this)) {
            launchModelPicker()
        }
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun requestMicPermission() {
        requestMic.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun launchModelPicker() {
        // Accept any file type; MediaPipe .task bundles have no standard MIME.
        pickModel.launch(arrayOf("*/*"))
    }

    private fun openWhitelist() {
        startActivity(Intent(this, WhitelistActivity::class.java))
    }

    private fun openLog() {
        startActivity(Intent(this, LogActivity::class.java))
    }

    private fun openSkills() {
        startActivity(Intent(this, SkillsActivity::class.java))
    }

    private fun importModel(uri: Uri) {
        toast(getString(R.string.model_importing))
        Thread {
            val ok = ModelRepository.importFrom(this, uri)
            runOnUiThread {
                modelReady.value = ModelRepository.isReady(this)
                toast(getString(if (ok) R.string.model_imported else R.string.model_import_failed))
            }
        }.start()
    }

    private fun authenticateAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            toast(getString(R.string.overlay_permission_required))
            return
        }

        // Device credential can only be combined with a biometric on API 30+.
        val allowDeviceCredential = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val authenticators =
            if (allowDeviceCredential) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_STRONG

        if (BiometricManager.from(this).canAuthenticate(authenticators)
            != BiometricManager.BIOMETRIC_SUCCESS
        ) {
            toast(getString(R.string.biometric_unavailable))
            return
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    ensureMicThenStartOverlay()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    toast(getString(R.string.auth_failed, errString))
                }
            },
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_title))
            .setSubtitle(getString(R.string.biometric_subtitle))
            .setAllowedAuthenticators(authenticators)
            .apply {
                if (!allowDeviceCredential) {
                    setNegativeButtonText(getString(R.string.cancel))
                }
            }
            .build()

        prompt.authenticate(info)
    }

    private fun ensureMicThenStartOverlay() {
        if (hasMicPermission()) {
            startOverlay()
        } else {
            startOverlayAfterMic = true
            requestMicPermission()
        }
    }

    private fun startOverlay() {
        ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
        agentRunning.value = true
    }

    private fun stopOverlay() {
        stopService(Intent(this, OverlayService::class.java))
        agentRunning.value = false
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun toast(message: CharSequence) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

@Composable
fun AgentScreen(
    overlayGranted: Boolean,
    micGranted: Boolean,
    accessibilityEnabled: Boolean,
    modelReady: Boolean,
    agentRunning: Boolean,
    onGrantOverlay: () -> Unit,
    onGrantMic: () -> Unit,
    onGrantAccessibility: () -> Unit,
    onImportModel: () -> Unit,
    onManageWhitelist: () -> Unit,
    onShowLog: () -> Unit,
    onShowSkills: () -> Unit,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )

        val statusText = when {
            !overlayGranted -> stringResource(R.string.status_needs_overlay)
            agentRunning -> stringResource(R.string.status_active)
            else -> stringResource(R.string.status_ready)
        }
        Text(text = statusText, style = MaterialTheme.typography.bodyLarge)

        Text(
            text = if (micGranted) {
                stringResource(R.string.mic_status_ready)
            } else {
                stringResource(R.string.mic_status_denied)
            },
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            text = if (accessibilityEnabled) {
                stringResource(R.string.accessibility_status_ready)
            } else {
                stringResource(R.string.accessibility_status_off)
            },
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            text = if (modelReady) {
                stringResource(R.string.model_status_ready)
            } else {
                stringResource(R.string.model_status_missing)
            },
            style = MaterialTheme.typography.bodyMedium,
        )

        if (!overlayGranted) {
            Button(
                onClick = onGrantOverlay,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.grant_overlay))
            }
        }

        if (!micGranted) {
            OutlinedButton(
                onClick = onGrantMic,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.grant_mic))
            }
        }

        if (!accessibilityEnabled) {
            OutlinedButton(
                onClick = onGrantAccessibility,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.grant_accessibility))
            }
        }

        OutlinedButton(
            onClick = onImportModel,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.import_model))
        }

        OutlinedButton(
            onClick = onManageWhitelist,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.manage_whitelist))
        }

        OutlinedButton(
            onClick = onShowLog,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.show_log))
        }

        OutlinedButton(
            onClick = onShowSkills,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.show_skills))
        }

        Button(
            onClick = onActivate,
            enabled = overlayGranted && !agentRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.activate_agent))
        }

        OutlinedButton(
            onClick = onDeactivate,
            enabled = agentRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.deactivate_agent))
        }

        Text(
            text = "build ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AgentScreenPreview() {
    AndroidAgentTheme {
        AgentScreen(
            overlayGranted = true,
            micGranted = false,
            accessibilityEnabled = false,
            modelReady = false,
            agentRunning = false,
            onGrantOverlay = {},
            onGrantMic = {},
            onGrantAccessibility = {},
            onImportModel = {},
            onManageWhitelist = {},
            onShowLog = {},
            onShowSkills = {},
            onActivate = {},
            onDeactivate = {},
        )
    }
}
