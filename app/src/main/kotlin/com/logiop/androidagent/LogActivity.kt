package com.logiop.androidagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.logiop.androidagent.security.AuditLog
import com.logiop.androidagent.ui.theme.AndroidAgentTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shows the decrypted agent action log, proving the encrypted round-trip. */
class LogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AndroidAgentTheme {
                var entries by remember { mutableStateOf<List<String>?>(null) }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    entries = withContext(Dispatchers.IO) { AuditLog(this@LogActivity).readAll() }
                }
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    LogScreen(entries, Modifier.padding(padding))
                }
            }
        }
    }

    @Composable
    private fun LogScreen(entries: List<String>?, modifier: Modifier = Modifier) {
        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = stringResource(R.string.log_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            when {
                entries == null -> Text(stringResource(R.string.log_loading))
                entries.isEmpty() -> Text(stringResource(R.string.log_empty))
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(entries) { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}
