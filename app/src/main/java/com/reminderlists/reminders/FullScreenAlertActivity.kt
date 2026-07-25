package com.reminderlists.reminders

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.reminderlists.R
import com.reminderlists.data.db.AppDatabase
import com.reminderlists.data.db.entity.ReminderEntity
import com.reminderlists.data.reminders.RepeatType
import com.reminderlists.ui.theme.AlertBackground
import com.reminderlists.ui.theme.AlertControl
import com.reminderlists.ui.theme.AlertOnControl
import com.reminderlists.ui.theme.ReminderListsTheme
import com.reminderlists.util.Dates
import com.reminderlists.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

// Full-screen alert (TZ 4.5). Launched over the lockscreen (and directly, unlocked) when a
// reminder fires with Full screen alert on (forced for Period). One orange screen: app name,
// live clock and the reminder Title/Content, with a large black lock circle to drag from near
// the top down to unlock. Unlocked it reveals Postpone options + OK (variant per type), or
// Done/Continue for Period. Any action stops the sound, dismisses the notification and closes.
class FullScreenAlertActivity : ComponentActivity() {

    private var reminder: ReminderEntity? = null
    private var acted = false
    // Set when this fire is a countdown timer (TZ 4.2 c′): the alert then has no row behind it —
    // it renders from the intent payload and Postpone re-arms the timer instead of a reminder.
    private var timer: FirePayload? = null
    private var state by mutableStateOf<AlertUiState?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Honour manifest showWhenLocked/turnScreenOn programmatically too, and keep the
        // screen on while the alert is up.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        applyIntent(intent)

        setContent {
            ReminderListsTheme {
                state?.let { s ->
                    FullScreenAlert(
                        state = s,
                        onPostpone = { millis ->
                            val spec = timer
                            act {
                                if (spec != null) {
                                    TimerAlarm.start(this, spec, System.currentTimeMillis() + millis)
                                } else {
                                    ReminderScheduler.postpone(this, db(), s.id, System.currentTimeMillis() + millis)
                                }
                            }
                        },
                        // A timer leaves no trace to acknowledge — OK just tears the alert down.
                        onOk = { act { if (timer == null) ReminderScheduler.confirmOk(db(), s.id) } },
                        onDone = { act { ReminderScheduler.periodDone(this, db(), s.id) } },
                        onContinue = { act { ReminderScheduler.periodContinue(db(), s.id) } },
                    )
                }
            }
        }
    }

    // singleInstance means a second fire arriving while this alert is alive (typically left in
    // the background without acting) is delivered here instead of a fresh activity — without
    // this the screen would keep showing the previous reminder and its stale payload.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acted = false
        applyIntent(intent)
    }

    // Build the alert state for whichever fire this intent carries: a timer renders straight
    // from its payload, a reminder is loaded by id.
    private fun applyIntent(intent: Intent) {
        val spec = FirePayload.from(intent)?.takeIf { it.isTimer }
        timer = spec
        // Only a real new fire (launched by AlarmReceiver) re-locks the circle. Re-entering the
        // same alert — tapping its notification after leaving it — keeps the state the user
        // left it in, instead of locking an alert they had already unlocked.
        val fresh = intent.getBooleanExtra(EXTRA_FRESH_FIRE, false)
        // Kept, not cleared: dropping it to null would take the alert out of composition and
        // reset the unlocked circle even when the very same fire is re-opened.
        val previous = state
        // The shade entry is not cancelled here: while the sound plays it is the sound
        // service's foreground notification and would come straight back (TZ 4.10). It is
        // hidden behind the alert anyway, and goes away when the circle is unlocked — which is
        // also when the sound stops — or when an action closes the alert.
        if (spec != null) {
            val r = spec.asReminder()
            reminder = r
            state = alertStateOf(r, fireKey(r.id, fresh, previous))
        } else {
            loadReminder(
                intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L),
                onLoaded = { r ->
                    reminder = r
                    state = alertStateOf(r, fireKey(r.id, fresh, previous))
                },
                onMissing = ::finish,
            )
        }
    }

    // The value the unlocked/locked state is keyed on: a new one means "lock again". Carried
    // over from the previous state when the same alert is merely re-opened.
    private fun fireKey(id: Long, fresh: Boolean, previous: AlertUiState?): Long =
        if (!fresh && previous != null && previous.id == id) previous.firedAt
        else System.currentTimeMillis()

    // Coming back to the foreground (e.g. the screen was turned off and on again while the
    // alert stayed up): drop the shade entry re-posted by onStop below. Only meaningful once
    // the sound service has released the notification; while it holds it, it stays.
    override fun onStart() {
        super.onStart()
        if (!acted) reminder?.let { ReminderNotifier.cancel(this, it.id) }
    }

    // Left without acting (Home, back gesture, screen off): put a shade entry back so the
    // pending fire stays reachable — notifyAlertPending carries no full-screen intent, so it
    // can't relaunch the alert (an FSI here would relight the screen the user just turned off).
    override fun onStop() {
        super.onStop()
        if (!acted && !isChangingConfigurations) {
            reminder?.let { ReminderNotifier.notifyAlertPending(this, timer ?: FirePayload.of(it)) }
        }
    }

    private fun reminderId(): Long = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)

    private fun db() = AppDatabase.get(this)

    private fun loadReminder(id: Long, onLoaded: (ReminderEntity) -> Unit, onMissing: () -> Unit) {
        if (id <= 0) {
            onMissing()
            return
        }
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { db().remindersDao().get(id) }
            if (loaded == null) onMissing() else onLoaded(loaded)
        }
    }

    // Run a reminder action off the main thread, then tear down the fire: stop the looping
    // sound, dismiss the notification and close the alert (TZ 4.5).
    private fun act(block: suspend () -> Unit) {
        acted = true
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { block() }
            SoundService.stop(this@FullScreenAlertActivity)
            ReminderNotifier.cancel(this@FullScreenAlertActivity, reminderId())
            finish()
        }
    }

    companion object {
        // Set only on launches coming from a fire, never on the notification's own intent.
        private const val EXTRA_FRESH_FIRE = "fresh_fire"

        // Launched straight from AlarmReceiver so the alert grabs the whole screen at once, even
        // when the device is unlocked (the notification's full-screen intent alone only takes
        // over on the lockscreen). This background activity start is only allowed while the
        // "Display over other apps" grant is held (asked on first launch) — without it the
        // system silently drops the launch and the heads-up notification is all that shows;
        // singleInstance dedupes against the full-screen-intent launch (TZ 4.5).
        fun start(context: Context, reminderId: Long) {
            launch(
                context,
                Intent(context, FullScreenAlertActivity::class.java)
                    .putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId),
                "reminder $reminderId",
            )
        }

        // Same launch for a countdown fire, with the payload instead of a row id (TZ 4.2 c′).
        fun startTimer(context: Context, spec: FirePayload) {
            launch(
                context,
                spec.putInto(Intent(context, FullScreenAlertActivity::class.java))
                    .putExtra(ReminderScheduler.EXTRA_REMINDER_ID, TimerAlarm.TIMER_ID),
                "timer",
            )
        }

        // The overlay grant is what makes this background start legal; without it the system
        // drops the launch silently and only the heads-up notification shows. Logged (TZ 8
        // local logging) so "the alert didn't open" can be told apart from "it was blocked".
        private fun launch(context: Context, intent: Intent, what: String) {
            val overlay = Settings.canDrawOverlays(context)
            Logger.i("Full-screen alert start for $what, overlay grant=$overlay")
            try {
                // Marks this as an actual fire, unlike the notification's own intent: only a
                // fire re-locks an alert the user has already unlocked (TZ 4.5).
                context.startActivity(
                    intent.putExtra(EXTRA_FRESH_FIRE, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (e: Exception) {
                Logger.e("Full-screen alert start refused for $what", e)
            }
        }
    }
}

// What the screen needs from a reminder, plus its Postpone/action variant (TZ 4.5).
private data class AlertUiState(
    val id: Long,
    val title: String,
    val content: String?,
    val variant: AlertVariant,
    // Distinguishes two fires of the same reminder, so a fire arriving at an already-unlocked
    // alert re-locks it instead of silently reusing the unlocked screen.
    val firedAt: Long,
)

private enum class AlertVariant { ONCE, DAILY, MONTHLY_YEARLY, PERIOD, INTERVAL }

private fun alertStateOf(r: ReminderEntity, firedAt: Long): AlertUiState =
    AlertUiState(
        id = r.id,
        title = r.title,
        content = r.content?.takeIf { it.isNotBlank() },
        variant = when (RepeatType.of(r.repeatType)) {
            RepeatType.PERIOD -> AlertVariant.PERIOD
            RepeatType.DAILY -> AlertVariant.DAILY
            RepeatType.INTERVAL -> AlertVariant.INTERVAL
            RepeatType.ONE_TIME -> if (r.monthlyRepeat || r.yearlyRepeat) AlertVariant.MONTHLY_YEARLY else AlertVariant.ONCE
        },
        firedAt = firedAt,
    )

@Composable
private fun FullScreenAlert(
    state: AlertUiState,
    onPostpone: (Long) -> Unit,
    onOk: () -> Unit,
    onDone: () -> Unit,
    onContinue: () -> Unit,
) {
    // Keyed on the fire: a new one arriving at this alert starts locked again.
    var unlocked by remember(state) { mutableStateOf(false) }
    val context = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxSize().background(AlertBackground)) {
        val screenHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = AlertControl,
            )
            LiveClock()
            ReminderText(state, dimmed = !unlocked)
            if (unlocked) {
                // Drop the whole «Postpone for» block down so it doesn't crowd the reminder text.
                Spacer(Modifier.height(40.dp))
                ActionArea(state, onPostpone, onOk, onDone, onContinue)
            }
        }
        if (!unlocked) {
            // Unlocking silences the sound right away — the reminder is acknowledged even before
            // the user picks Postpone/OK (TZ 4.5).
            LockCircle(
                screenHeightPx = screenHeightPx,
                onUnlock = {
                    unlocked = true
                    SoundService.stop(context)
                    // The sound service just released the fire's notification; clear it so the
                    // shade isn't left with an entry for an alert the user is already acting on.
                    ReminderNotifier.cancel(context, state.id)
                },
            )
        }
    }
}

@Composable
private fun LiveClock() {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1_000L)
        }
    }
    Text(
        Dates.format(now),
        style = MaterialTheme.typography.displayLarge,
        color = AlertControl,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun ReminderText(state: AlertUiState, dimmed: Boolean) {
    val alpha by animateFloatAsState(if (dimmed) 0.45f else 1f, label = "reminderAlpha")
    Column(
        Modifier.fillMaxWidth().alpha(alpha).padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.alert_reminder_label),
            style = MaterialTheme.typography.headlineSmall,
            color = AlertControl,
        )
        Text(
            state.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = AlertControl,
            modifier = Modifier.padding(top = 4.dp),
        )
        state.content?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                color = AlertControl,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

// Big black circle sitting ~1/5 from the top: a white lock in its upper half and a large white
// down-triangle in its lower half. Drag it down past mid-screen to unlock, guarding against
// dismissing the reminder by accident (TZ 4.5 state 1). Unlock fires the moment the threshold
// is crossed, mid-drag — requiring a release past the threshold made attempts that stopped a
// hair short reset to zero and read as "unlock didn't work".
@Composable
private fun BoxScope.LockCircle(screenHeightPx: Float, onUnlock: () -> Unit) {
    val circlePx = with(LocalDensity.current) { CIRCLE_SIZE.toPx() }
    val baseY = screenHeightPx * 0.2f - circlePx / 2f
    val thresholdPx = screenHeightPx * 0.5f
    var drag by remember { mutableStateOf(0f) }
    val offset by animateFloatAsState(drag, label = "lockOffset")
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .size(CIRCLE_SIZE)
            .graphicsLayer { translationY = baseY + offset }
            .background(AlertControl, CircleShape)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dy ->
                        val before = drag
                        drag = (drag + dy).coerceIn(0f, thresholdPx)
                        if (before < thresholdPx && drag >= thresholdPx) onUnlock()
                    },
                    onDragEnd = { drag = 0f },
                    onDragCancel = { drag = 0f },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = AlertOnControl, modifier = Modifier.size(72.dp))
            DownTriangle(Modifier.size(width = 96.dp, height = 60.dp))
        }
    }
}

@Composable
private fun DownTriangle(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width / 2f, size.height)
            close()
        }
        drawPath(path, AlertOnControl)
    }
}

// State 2: Postpone options + OK, or Done/Continue for Period (TZ 4.5). All buttons are black
// with white labels.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionArea(
    state: AlertUiState,
    onPostpone: (Long) -> Unit,
    onOk: () -> Unit,
    onDone: () -> Unit,
    onContinue: () -> Unit,
) {
    if (state.variant == AlertVariant.PERIOD) {
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AlertButton(stringResource(R.string.alert_done), onClick = onDone)
            AlertButton(stringResource(R.string.alert_continue), onClick = onContinue)
        }
        return
    }

    Text(
        stringResource(R.string.alert_postpone_title),
        style = MaterialTheme.typography.headlineSmall,
        color = AlertControl,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
    // Fixed grouping: the minute options on one row, the hour(+day) options on the next, then
    // a full-width OK — instead of an arbitrary FlowRow wrap (TZ 4.5).
    val (minutes, longer) = postponeOptions(state.variant).partition { it.second < HOUR }
    PostponeRow(minutes, onPostpone)
    if (longer.isNotEmpty()) {
        PostponeRow(longer, onPostpone, Modifier.padding(top = 8.dp))
    }
    AlertButton(
        text = stringResource(R.string.action_ok),
        onClick = onOk,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    )
}

// One row of equal-width postpone buttons (TZ 4.5).
@Composable
private fun PostponeRow(
    options: List<Pair<Int, Long>>,
    onPostpone: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((labelRes, millis) in options) {
            PostponeButton(stringResource(labelRes), Modifier.weight(1f)) { onPostpone(millis) }
        }
    }
}

// A postpone button: the amount stacked over the unit on two centered lines (e.g. «10 / мин»),
// lowercased and in a smaller type so the value fits the narrow buttons (TZ 4.5).
@Composable
private fun PostponeButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val parts = label.lowercase().split(" ", limit = 2)
    Button(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AlertControl, contentColor = AlertOnControl),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(parts[0], style = MaterialTheme.typography.titleMedium, color = AlertOnControl)
            parts.getOrNull(1)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = AlertOnControl)
            }
        }
    }
}

@Composable
private fun AlertButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = AlertControl, contentColor = AlertOnControl),
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = AlertOnControl)
    }
}

// Postpone intervals per variant (TZ 4.5): Daily and Monthly/Yearly drop 1 DAY.
private fun postponeOptions(variant: AlertVariant): List<Pair<Int, Long>> {
    val base = listOf(
        R.string.postpone_10_min to 10 * MINUTE,
        R.string.postpone_20_min to 20 * MINUTE,
        R.string.postpone_30_min to 30 * MINUTE,
        R.string.postpone_1_hour to HOUR,
        R.string.postpone_3_hours to 3 * HOUR,
    )
    return if (variant == AlertVariant.ONCE) base + (R.string.postpone_1_day to DAY) else base
}

private val CIRCLE_SIZE = 192.dp
private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR
