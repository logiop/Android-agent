package com.logiop.androidagent

import android.Manifest
import android.content.Intent
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
import com.logiop.androidagent.overlay.OverlayService
import com.logiop.androidagent.ui.theme.AndroidAgentTheme

class MainActivity : FragmentActivity() {

    private val overlayGranted = mutableStateOf(false)
    private val agentRunning = mutableStateOf(false)

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best effort */ }

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
                        agentRunning = agentRunning.value,
                        onGrantOverlay = ::openOverlaySettings,
                        onActivate = ::authenticateAndStart,
                        onDeactivate = ::stopOverlay,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        overlayGranted.value = Settings.canDrawOverlays(this)
        agentRunning.value = OverlayService.isRunning
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
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
                    startOverlay()
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

    private fun startOverlay() {
        ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
        agentRunning.value = true
    }

    private fun stopOverlay() {
        stopService(Intent(this, OverlayService::class.java))
        agentRunning.value = false
    }

    private fun toast(message: CharSequence) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

@Composable
fun AgentScreen(
    overlayGranted: Boolean,
    agentRunning: Boolean,
    onGrantOverlay: () -> Unit,
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

        if (!overlayGranted) {
            Button(
                onClick = onGrantOverlay,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.grant_overlay))
            }
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
    }
}

@Preview(showBackground = true)
@Composable
fun AgentScreenPreview() {
    AndroidAgentTheme {
        AgentScreen(
            overlayGranted = true,
            agentRunning = false,
            onGrantOverlay = {},
            onActivate = {},
            onDeactivate = {},
        )
    }
}
