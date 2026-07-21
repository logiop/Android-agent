package com.logiop.androidagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.logiop.androidagent.memory.SkillCompiler
import com.logiop.androidagent.memory.SkillStore
import com.logiop.androidagent.memory.Trajectory
import com.logiop.androidagent.ui.theme.AndroidAgentTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Human-in-the-loop confirmation of a learned skill (SUGILITE/LearnAct style):
 * the deduced intent pattern is shown editable, the demonstrated steps read-only,
 * and the user confirms/corrects before the skill is saved (in QUARANTINE).
 */
class SkillReviewActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PENDING_ID = "pending_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val pendingId = intent.getLongExtra(EXTRA_PENDING_ID, -1L)
        val store = SkillStore(this)

        setContent {
            AndroidAgentTheme {
                val scope = rememberCoroutineScope()
                var trajectory by remember { mutableStateOf<Trajectory?>(null) }
                var pattern by remember { mutableStateOf("") }
                var missing by remember { mutableStateOf(false) }

                LaunchedEffect(pendingId) {
                    val pending = withContext(Dispatchers.IO) { store.loadPending(pendingId) }
                    if (pending == null) {
                        missing = true
                    } else {
                        trajectory = pending.trajectory
                        pattern = SkillCompiler.autoDraft(pending.trajectory).intentPattern
                    }
                }

                LaunchedEffect(missing) { if (missing) finish() }

                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    ReviewScreen(
                        modifier = Modifier.padding(padding),
                        trajectory = trajectory,
                        pattern = pattern,
                        onPatternChange = { pattern = it },
                        onSave = {
                            val traj = trajectory ?: return@ReviewScreen
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val draft = SkillCompiler.autoDraft(traj).copy(intentPattern = pattern)
                                    store.compileAndSave(draft)
                                    store.deletePending(pendingId)
                                }
                                finish()
                            }
                        },
                        onCancel = {
                            scope.launch {
                                withContext(Dispatchers.IO) { store.deletePending(pendingId) }
                                finish()
                            }
                        },
                    )
                }
            }
        }
    }

    @Composable
    private fun ReviewScreen(
        trajectory: Trajectory?,
        pattern: String,
        onPatternChange: (String) -> Unit,
        onSave: () -> Unit,
        onCancel: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.skill_review_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.skill_review_hint),
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = pattern,
                onValueChange = onPatternChange,
                label = { Text(stringResource(R.string.skill_intent_pattern)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.skill_target_app, trajectory?.targetApp.orEmpty()),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.skill_steps_header),
                style = MaterialTheme.typography.titleSmall,
            )
            trajectory?.steps?.forEachIndexed { idx, step ->
                Text(
                    text = "${idx + 1}. ${step.actionType} \"${step.argument}\"" +
                        if (step.irreversible) "  ⚠" else "",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.skill_discard))
                }
                Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.skill_save))
                }
            }
        }
    }
}
