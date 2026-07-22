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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.logiop.androidagent.memory.Skill
import com.logiop.androidagent.memory.SkillStats
import com.logiop.androidagent.memory.SkillStore
import com.logiop.androidagent.ui.theme.AndroidAgentTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lists the learned skills and lets the user delete them. */
class SkillsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val store = SkillStore(this)

        setContent {
            AndroidAgentTheme {
                val scope = rememberCoroutineScope()
                var skills by remember { mutableStateOf<List<Skill>?>(null) }

                suspend fun reload() {
                    skills = withContext(Dispatchers.IO) { store.listSkills() }
                }

                LaunchedEffect(Unit) { reload() }

                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    SkillsScreen(
                        modifier = Modifier.padding(padding),
                        skills = skills,
                        onDelete = { id ->
                            scope.launch {
                                withContext(Dispatchers.IO) { store.deleteSkill(id) }
                                reload()
                            }
                        },
                    )
                }
            }
        }
    }

    @Composable
    private fun SkillsScreen(
        skills: List<Skill>?,
        onDelete: (Long) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = stringResource(R.string.skills_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            when {
                skills == null -> Text(stringResource(R.string.log_loading))
                skills.isEmpty() -> Text(stringResource(R.string.skills_empty))
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(skills) { skill ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(skill.intentPattern, style = MaterialTheme.typography.bodyLarge)
                                val recompile = if (SkillStats.needsRecompile(skill.successCount, skill.failCount)) {
                                    " · " + stringResource(R.string.skill_needs_recompile)
                                } else {
                                    ""
                                }
                                Text(
                                    text = "${skill.targetApp} · ${skill.trustState.name.lowercase()} · " +
                                        "ok ${skill.successCount} / ko ${skill.failCount}$recompile",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            TextButton(onClick = { onDelete(skill.id) }) {
                                Text(stringResource(R.string.skill_delete))
                            }
                        }
                    }
                }
            }
        }
    }
}
