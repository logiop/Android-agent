package com.logiop.androidagent

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.logiop.androidagent.agent.Whitelist
import com.logiop.androidagent.ui.theme.AndroidAgentTheme

/** Lets the user choose which apps the agent is allowed to control. */
class WhitelistActivity : ComponentActivity() {

    private data class AppEntry(val label: String, val packageName: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val whitelist = Whitelist(this)
        val apps = loadLaunchableApps()

        setContent {
            AndroidAgentTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    WhitelistScreen(
                        modifier = Modifier.padding(padding),
                        apps = apps,
                        isAllowed = { whitelist.isAllowed(it) },
                        onToggle = { pkg, allow ->
                            if (allow) whitelist.add(pkg) else whitelist.remove(pkg)
                        },
                    )
                }
            }
        }
    }

    private fun loadLaunchableApps(): List<AppEntry> {
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(main, 0)
            .map { AppEntry(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    @Composable
    private fun WhitelistScreen(
        apps: List<AppEntry>,
        isAllowed: (String) -> Boolean,
        onToggle: (String, Boolean) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = stringResource(R.string.whitelist_hint),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(apps) { app ->
                    var checked by remember { mutableStateOf(isAllowed(app.packageName)) }
                    Row(
                        label = app.label,
                        checked = checked,
                        onCheckedChange = {
                            checked = it
                            onToggle(app.packageName, it)
                        },
                    )
                }
            }
        }
    }

    @Composable
    private fun Row(
        label: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
