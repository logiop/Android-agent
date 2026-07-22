package com.logiop.androidagent.overlay

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.logiop.androidagent.MainActivity
import com.logiop.androidagent.R
import com.logiop.androidagent.agent.AgentLoop
import com.logiop.androidagent.agent.SkillReplayer
import com.logiop.androidagent.agent.Whitelist
import com.logiop.androidagent.brain.Brain
import com.logiop.androidagent.hands.AgentAccessibilityService
import com.logiop.androidagent.hands.AppLauncher
import com.logiop.androidagent.SkillReviewActivity
import com.logiop.androidagent.hands.Command
import com.logiop.androidagent.hands.CommandInterpreter
import com.logiop.androidagent.memory.Skill
import com.logiop.androidagent.memory.SkillMatcher
import com.logiop.androidagent.memory.SkillStore
import com.logiop.androidagent.memory.SkillWithSteps
import com.logiop.androidagent.memory.Trajectory
import com.logiop.androidagent.memory.TrustState
import com.logiop.androidagent.security.AuditLog
import com.logiop.androidagent.voice.VoiceRecognizer
import kotlin.math.abs

/**
 * Foreground service that shows a draggable floating bubble on top of every app.
 *
 * Started only after the user has passed biometric authentication (see
 * [com.logiop.androidagent.MainActivity]). Tapping the bubble is a placeholder
 * for the future voice/agent trigger; the bubble is removed when the service is
 * stopped from its notification action or from the app.
 */
class OverlayService : Service() {

    companion object {
        @Volatile
        var isRunning = false
            private set

        const val ACTION_STOP = "com.logiop.androidagent.overlay.STOP"

        private const val TAG = "AndroidAgent"
        private const val CHANNEL_ID = "android_agent_overlay"
        private const val NOTIFICATION_ID = 1001

        /** Movement (px) beyond which a gesture counts as a drag rather than a tap. */
        private const val TOUCH_SLOP = 12

        /** How long (ms) the bubble stays red after signalling an error. */
        private const val ERROR_RESET_DELAY_MS = 2500L
    }

    /** Visual states of the bubble, encoded as background tints. */
    private enum class BubbleState(val color: Int) {
        IDLE(0xFF6650A4.toInt()),
        LISTENING(0xFF2E7D32.toInt()),
        THINKING(0xFF1565C0.toInt()),
        ERROR(0xFFC62828.toInt()),
    }

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var confirmView: View? = null

    private lateinit var voice: VoiceRecognizer
    private lateinit var brain: Brain
    private lateinit var auditLog: AuditLog
    private lateinit var skillStore: SkillStore
    private lateinit var agentLoop: AgentLoop
    private lateinit var replayer: SkillReplayer
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        voice = VoiceRecognizer(this)
        brain = Brain(this)
        auditLog = AuditLog(this)
        skillStore = SkillStore(this)
        agentLoop = AgentLoop(this, brain, Whitelist(this), auditLog, agentHost)
        replayer = SkillReplayer(this, skillStore, auditLog, brain, replayHost)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()
        if (bubbleView == null) {
            addBubble()
        }
        isRunning = true
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        mainHandler.removeCallbacksAndMessages(null)
        agentLoop.stop()
        replayer.stop()
        dismissConfirmation()
        voice.destroy()
        brain.close()
        bubbleView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        bubbleView = null
    }

    private fun startAsForeground() {
        createChannel()

        val stopIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .addAction(0, getString(R.string.notification_stop), stopPending)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun addBubble() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null)

        @Suppress("DEPRECATION")
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 240
        }

        view.setOnTouchListener(DragTouchListener(params) { onBubbleTapped() })

        windowManager.addView(view, params)
        bubbleView = view
    }

    private fun onBubbleTapped() {
        if (voice.isListening) {
            voice.cancel()
            setBubbleState(BubbleState.IDLE)
            return
        }

        if (!hasMicPermission()) {
            // Never stay silent: surface the missing permission and route the
            // user to the app where it can be granted.
            signalError(getString(R.string.mic_permission_needed))
            openAppForMicPermission()
            return
        }

        startVoiceCapture()
    }

    private fun startVoiceCapture() {
        setBubbleState(BubbleState.LISTENING)
        voice.start(object : VoiceRecognizer.Callbacks {
            override fun onResult(text: String) {
                setBubbleState(BubbleState.IDLE)
                handleCommand(text)
            }

            override fun onError(message: String) {
                signalError(message)
            }
        })
    }

    private fun handleCommand(text: String) {
        when (val command = CommandInterpreter.interpret(text)) {
            is Command.OpenApp -> {
                val opened = AppLauncher.openApp(this, command.query)
                auditLog.record("open_app \"${command.query}\" ok=$opened")
                val message = if (opened) {
                    getString(R.string.cmd_opening, command.query)
                } else {
                    getString(R.string.cmd_app_not_found, command.query)
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }

            is Command.GoogleSearch -> {
                auditLog.record("search \"${command.query}\"")
                AppLauncher.googleSearch(this, command.query)
                Toast.makeText(
                    this,
                    getString(R.string.cmd_searching, command.query),
                    Toast.LENGTH_LONG,
                ).show()
            }

            is Command.Freeform -> handleFreeformOrSkill(command.text)
        }
    }

    /**
     * A free-form command is handed to the agent loop: it plans with the LLM,
     * executes safe navigation directly, and runs control actions only inside
     * whitelisted apps, asking for confirmation before irreversible ones.
     */
    /**
     * A free-form command: if a learned skill matches, replay it (no LLM);
     * otherwise fall back to the LLM loop. Deterministic shortcuts are already
     * handled before this point.
     */
    private fun handleFreeformOrSkill(text: String) {
        val match = findMatchingSkill(text)
        if (match != null) {
            startReplay(match.first, text, match.second)
        } else {
            handleFreeform(text)
        }
    }

    private fun findMatchingSkill(text: String): Pair<SkillWithSteps, Map<String, String>>? {
        var best: Skill? = null
        var bestSlots: Map<String, String>? = null
        var bestRank = -1
        for (skill in skillStore.listSkills()) {
            val slots = SkillMatcher.match(text, skill.intentPattern) ?: continue
            val rank = (if (skill.trustState == TrustState.TRUSTED) 1000 else 0) + skill.successCount
            if (rank > bestRank) {
                bestRank = rank
                best = skill
                bestSlots = slots
            }
        }
        val chosen = best ?: return null
        val full = skillStore.loadWithSteps(chosen.id) ?: return null
        return full to (bestSlots ?: emptyMap())
    }

    private fun startReplay(skill: SkillWithSteps, command: String, slots: Map<String, String>) {
        if (skill.skill.trustState == TrustState.QUARANTINE) {
            // Supervised mode until the skill is promoted: confirm before running.
            showConfirmation(getString(R.string.replay_supervised, skill.skill.intentPattern)) { ok ->
                if (ok) {
                    replayer.replay(skill, command, slots)
                } else {
                    toastLong(getString(R.string.agent_cancelled))
                }
            }
        } else {
            replayer.replay(skill, command, slots)
        }
    }

    private val replayHost = object : SkillReplayer.Host {
        override fun onInfo(message: String) {
            toastLong(message)
        }

        override fun onFinished(message: String) {
            setBubbleState(BubbleState.IDLE)
            toastLong(message)
        }

        override fun requestConfirmation(description: String, onResult: (Boolean) -> Unit) {
            showConfirmation(description, onResult)
        }

        override fun onFallback(command: String) {
            agentLoop.start(command)
        }
    }

    private fun handleFreeform(text: String) {
        val service = AgentAccessibilityService.instance
        if (service == null) {
            signalError(getString(R.string.accessibility_needed))
            openAccessibilitySettings()
            return
        }
        if (!brain.isModelAvailable()) {
            signalError(getString(R.string.model_missing))
            openAppForModel()
            return
        }
        agentLoop.start(text)
    }

    private val agentHost = object : AgentLoop.Host {
        override fun onThinking() {
            setBubbleState(BubbleState.THINKING)
        }

        override fun onInfo(message: String) {
            toastLong(message)
        }

        override fun onFinished(message: String) {
            setBubbleState(BubbleState.IDLE)
            toastLong(message)
        }

        override fun requestConfirmation(description: String, onResult: (Boolean) -> Unit) {
            showConfirmation(description, onResult)
        }

        override fun onLearnable(command: String, trajectory: Trajectory) {
            val pendingId = skillStore.savePending(trajectory)
            startActivity(
                Intent(this@OverlayService, SkillReviewActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(SkillReviewActivity.EXTRA_PENDING_ID, pendingId)
                },
            )
        }
    }

    private fun showConfirmation(description: String, onResult: (Boolean) -> Unit) {
        dismissConfirmation()
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_confirm, null)
        view.findViewById<TextView>(R.id.confirm_message).text =
            getString(R.string.confirm_message, description)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.6f
        }

        view.findViewById<Button>(R.id.confirm_ok).setOnClickListener {
            dismissConfirmation()
            onResult(true)
        }
        view.findViewById<Button>(R.id.confirm_cancel).setOnClickListener {
            dismissConfirmation()
            onResult(false)
        }

        windowManager.addView(view, params)
        confirmView = view
    }

    private fun dismissConfirmation() {
        confirmView?.let { view -> runCatching { windowManager.removeView(view) } }
        confirmView = null
    }

    private fun openAccessibilitySettings() {
        startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun openAppForModel() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(MainActivity.EXTRA_IMPORT_MODEL, true)
            },
        )
    }

    private fun toastLong(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun signalError(message: String) {
        setBubbleState(BubbleState.ERROR)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        mainHandler.postDelayed({ setBubbleState(BubbleState.IDLE) }, ERROR_RESET_DELAY_MS)
    }

    private fun setBubbleState(state: BubbleState) {
        bubbleView?.backgroundTintList = ColorStateList.valueOf(state.color)
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun openAppForMicPermission() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_REQUEST_MIC, true)
        }
        startActivity(intent)
    }

    /** Moves the bubble while dragging and reports a tap when the finger barely moved. */
    private inner class DragTouchListener(
        private val params: WindowManager.LayoutParams,
        private val onTap: () -> Unit,
    ) : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var dragging = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragging = false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (abs(dx) > TOUCH_SLOP || abs(dy) > TOUCH_SLOP) {
                        dragging = true
                    }
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    windowManager.updateViewLayout(v, params)
                }

                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        v.performClick()
                        onTap()
                    }
                }
            }
            return true
        }
    }
}
