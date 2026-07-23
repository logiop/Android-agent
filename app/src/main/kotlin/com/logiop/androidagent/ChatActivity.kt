package com.logiop.androidagent

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.logiop.androidagent.memory.ChatMessage
import com.logiop.androidagent.memory.ChatRole
import com.logiop.androidagent.memory.ChatStore
import com.logiop.androidagent.overlay.OverlayService
import com.logiop.androidagent.ui.theme.AndroidAgentTheme
import com.logiop.androidagent.voice.VoiceRecognizer

/**
 * In-app chat: type or dictate a command and see the transcript of commands and
 * agent outcomes. Commands run through the same OverlayService pipeline; the
 * transcript is persisted in [ChatStore] (SQLite).
 */
class ChatActivity : ComponentActivity() {

    private lateinit var voice: VoiceRecognizer
    private val input = mutableStateOf("")
    private val listening = mutableStateOf(false)

    private val requestMic =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startListening()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ChatStore.init(this)
        voice = VoiceRecognizer(this)

        setContent {
            AndroidAgentTheme {
                val messages by ChatStore.flow.collectAsState()
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    ChatScreen(
                        modifier = Modifier.padding(padding),
                        messages = messages,
                        input = input,
                        listening = listening.value,
                        onSend = ::submit,
                        onMic = ::onMic,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voice.destroy()
    }

    private fun submit(text: String) {
        val command = text.trim()
        if (command.isEmpty()) return
        input.value = ""
        val service = OverlayService.instance
        if (OverlayService.isRunning && service != null) {
            // The service records the user message and the outcome.
            service.submitCommand(command)
        } else {
            ChatStore.append(ChatRole.USER, command)
            ChatStore.append(ChatRole.AGENT, getString(R.string.chat_agent_inactive))
        }
    }

    private fun onMic() {
        if (voice.isListening) {
            voice.cancel()
            listening.value = false
            return
        }
        if (hasMicPermission()) startListening() else requestMic.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startListening() {
        listening.value = true
        voice.start(object : VoiceRecognizer.Callbacks {
            override fun onResult(text: String) {
                input.value = text
                listening.value = false
            }

            override fun onError(message: String) {
                listening.value = false
                Toast.makeText(this@ChatActivity, message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}

@Composable
private fun ChatScreen(
    messages: List<ChatMessage>,
    input: MutableState<String>,
    listening: Boolean,
    onSend: (String) -> Unit,
    onMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        Text(
            text = stringResource(R.string.chat_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )

        val listState = rememberLazyListState()
        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
        }

        if (messages.isEmpty()) {
            Text(
                text = stringResource(R.string.chat_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages) { message -> MessageBubble(message) }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input.value,
                onValueChange = { input.value = it },
                placeholder = { Text(stringResource(R.string.chat_hint)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedButton(onClick = onMic) {
                Text(if (listening) stringResource(R.string.chat_listening) else stringResource(R.string.chat_mic))
            }
            Button(onClick = { onSend(input.value) }) {
                Text(stringResource(R.string.chat_send))
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == ChatRole.USER
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Text(
            text = message.text,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
