package com.reminderlists.reminders

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Honour manifest showWhenLocked/turnScreenOn programmatically too, and keep the
        // screen on while the alert is up.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        var state by mutableStateOf<AlertUiState?>(null)
        loadReminder(reminderId(), onLoaded = { state = it }, onMissing = ::finish)

        setContent {
            ReminderListsTheme {
                state?.let { s ->
                    FullScreenAlert(
                        state = s,
                        onPostpone = { millis -> act { ReminderScheduler.postpone(this, db(), s.id, System.currentTimeMillis() + millis) } },
                        onOk = { act { ReminderScheduler.confirmOk(db(), s.id) } },
                        onDone = { act { ReminderScheduler.periodDone(this, db(), s.id) } },
                        onContinue = { act { ReminderScheduler.periodContinue(db(), s.id) } },
                    )
                }
            }
        }
    }

    private fun reminderId(): Long = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)

    private fun db() = AppDatabase.get(this)

    private fun loadReminder(id: Long, onLoaded: (AlertUiState) -> Unit, onMissing: () -> Unit) {
        if (id <= 0) {
            onMissing()
            return
        }
        lifecycleScope.launch {
            val reminder = withContext(Dispatchers.IO) { db().remindersDao().get(id) }
            if (reminder == null) onMissing() else onLoaded(alertStateOf(reminder))
        }
    }

    // Run a reminder action off the main thread, then tear down the fire: stop the looping
    // sound, dismiss the notification and close the alert (TZ 4.5).
    private fun act(block: suspend () -> Unit) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { block() }
            SoundService.stop(this@FullScreenAlertActivity)
            ReminderNotifier.cancel(this@FullScreenAlertActivity, reminderId())
            finish()
        }
    }

    companion object {
        // Launched straight from AlarmReceiver so the alert grabs the whole screen at once, even
        // when the device is unlocked (the notification's full-screen intent alone only takes
        // over on the lockscreen). The setAlarmClock fire grants the background-activity-start
        // window; singleInstance dedupes against the full-screen-intent launch (TZ 4.5).
        fun start(context: Context, reminderId: Long) {
            val intent = Intent(context, FullScreenAlertActivity::class.java)
                .putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}

// What the screen needs from a reminder, plus its Postpone/action variant (TZ 4.5).
private data class AlertUiState(
    val id: Long,
    val title: String,
    val content: String?,
    val variant: AlertVariant,
)

private enum class AlertVariant { ONCE, DAILY, MONTHLY_YEARLY, PERIOD, INTERVAL }

private fun alertStateOf(r: ReminderEntity): AlertUiState =
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
    )

@Composable
private fun FullScreenAlert(
    state: AlertUiState,
    onPostpone: (Long) -> Unit,
    onOk: () -> Unit,
    onDone: () -> Unit,
    onContinue: () -> Unit,
) {
    var unlocked by remember { mutableStateOf(false) }
    val context = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxSize().background(AlertBackground)) {
        val screenHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = AlertControl,
            )
            LiveClock()
            ReminderText(state, dimmed = !unlocked)
            if (unlocked) {
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
        modifier = Modifier.padding(vertical = 16.dp),
    )
}

@Composable
private fun ReminderText(state: AlertUiState, dimmed: Boolean) {
    val alpha by animateFloatAsState(if (dimmed) 0.45f else 1f, label = "reminderAlpha")
    Column(
        Modifier.fillMaxWidth().alpha(alpha).padding(vertical = 24.dp),
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
            modifier = Modifier.padding(top = 8.dp),
        )
        state.content?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                color = AlertControl,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

// Big black circle sitting ~1/5 from the top: a white lock in its upper half and a large white
// down-triangle in its lower half. Drag it down to ~4/5 of the screen to unlock, guarding
// against dismissing the reminder by accident (TZ 4.5 state 1).
@Composable
private fun BoxScope.LockCircle(screenHeightPx: Float, onUnlock: () -> Unit) {
    val circlePx = with(LocalDensity.current) { CIRCLE_SIZE.toPx() }
    val baseY = screenHeightPx * 0.2f - circlePx / 2f
    val thresholdPx = screenHeightPx * 0.6f
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
                    onVerticalDrag = { _, dy -> drag = (drag + dy).coerceIn(0f, thresholdPx) },
                    onDragEnd = { if (drag >= thresholdPx) onUnlock() else drag = 0f },
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
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        for ((labelRes, millis) in postponeOptions(state.variant)) {
            AlertButton(stringResource(labelRes)) { onPostpone(millis) }
        }
    }
    AlertButton(
        text = stringResource(R.string.action_ok),
        onClick = onOk,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    )
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
