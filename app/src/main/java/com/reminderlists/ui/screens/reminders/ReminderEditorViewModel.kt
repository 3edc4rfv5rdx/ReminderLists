package com.reminderlists.ui.screens.reminders

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reminderlists.R
import com.reminderlists.data.db.dao.SettingsDao
import com.reminderlists.data.db.dao.TagsDao
import com.reminderlists.data.db.entity.ReminderEntity
import com.reminderlists.data.db.entity.ReminderPhotoEntity
import com.reminderlists.data.photo.PhotoManager
import com.reminderlists.data.reminders.ReminderFolder
import com.reminderlists.data.reminders.RemindersRepository
import com.reminderlists.data.reminders.RepeatType
import com.reminderlists.ui.appViewModelFactory
import com.reminderlists.ui.components.SnackEvent
import com.reminderlists.util.Dates
import com.reminderlists.util.Limits
import com.reminderlists.util.SettingsKeys
import com.reminderlists.util.TextFormat
import com.reminderlists.util.Weekdays
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// One photo in the editor: existing rows carry the DB entity, new ones only the file name
// (attached on Save; the file is already imported into photos/).
data class ReminderEditorPhoto(val entity: ReminderPhotoEntity?, val fileName: String)

// Morning / Day / Evening quick times from Settings (TZ 4.2 п. e / TZ 5).
data class TimePresets(val morning: String, val day: String, val evening: String)

// Reminder add/edit form state (TZ 4.2). The type-folder is never chosen here — it
// derives from repeat type + Monthly/Yearly checkboxes (TZ 4.1).
class ReminderEditorViewModel(
    private val repo: RemindersRepository,
    tagsDao: TagsDao,
    settingsDao: SettingsDao,
    application: Application,
    reminderId: Long,
    initialFolder: ReminderFolder,
) : ViewModel() {

    private val appContext = application.applicationContext
    val isEdit = reminderId > 0
    private var existing: ReminderEntity? = null

    // Common top fields (TZ 4.2 пп. 1–6).
    var title by mutableStateOf("")
    var content by mutableStateOf("")
    var tags by mutableStateOf("")
    var date by mutableStateOf("")
    var priority by mutableIntStateOf(0)

    // Firing fields (TZ 4.2 п. 7); the FAB prefill maps the opened folder to the type.
    var active by mutableStateOf(true)
    var fullScreenAlert by mutableStateOf(initialFolder == ReminderFolder.PERIODS)
        private set
    var repeatType by mutableStateOf(
        when (initialFolder) {
            ReminderFolder.DAILY -> RepeatType.DAILY
            ReminderFolder.PERIODS -> RepeatType.PERIOD
            else -> RepeatType.ONE_TIME
        },
    )
        private set

    // Last full-screen choice made outside Period, restored when leaving Period (which forces
    // it on). New PERIODS default: user hasn't chosen, so off.
    private var userFullScreen = false
    var time by mutableStateOf("") // One time / Period
    var monthlyRepeat by mutableStateOf(initialFolder == ReminderFolder.MONTHLY)
        private set
    var yearlyRepeat by mutableStateOf(initialFolder == ReminderFolder.YEARLY)
        private set
    var autoRemove by mutableStateOf(false)
    val dailyTimes = mutableStateListOf<String>()
    var periodFrom by mutableStateOf("")
    var periodTo by mutableStateOf("")
    var weekdaysMask by mutableIntStateOf(Weekdays.ALL) // Every day preset (TZ 4.2)
    var loopSound by mutableStateOf(true)
    // null = Default from Settings; a system Uri, or a file name in sounds/ (TZ 4.2 j / 6.2).
    // The list, preview and file-picker all live inside the shared SoundField (TZ 8).
    var soundUri by mutableStateOf<String?>(null)

    val photos = mutableStateListOf<ReminderEditorPhoto>()
    private var saved = false

    // Validation errors surface through the shared snackbar (TZ 4.2 / 8).
    var snack by mutableStateOf<SnackEvent?>(null)

    // Tag dictionary for the «#» picker (TZ 4.2 п. 3).
    val allTags: StateFlow<List<String>> =
        tagsDao.observeTags()
            .map { list -> list.map { it.name } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val timePresets: StateFlow<TimePresets> =
        combine(
            settingsDao.observe(SettingsKeys.TIME_PRESET_MORNING),
            settingsDao.observe(SettingsKeys.TIME_PRESET_DAY),
            settingsDao.observe(SettingsKeys.TIME_PRESET_EVENING),
        ) { morning, day, evening ->
            TimePresets(
                morning ?: SettingsKeys.DEFAULT_MORNING,
                day ?: SettingsKeys.DEFAULT_DAY,
                evening ?: SettingsKeys.DEFAULT_EVENING,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_PRESETS)

    // The Settings default sound, so the editor's «Default» entry previews what will actually
    // play (TZ 4.2 j / 5), not the raw system ringtone.
    val defaultSound: StateFlow<String?> =
        settingsDao.observe(SettingsKeys.DEFAULT_SOUND_URI)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        if (isEdit) {
            viewModelScope.launch {
                repo.getWithDetails(reminderId)?.let { detail ->
                    val r = detail.reminder
                    existing = r
                    title = r.title
                    content = r.content.orEmpty()
                    tags = detail.tags.joinToString(", ") { it.name }
                    date = r.date.orEmpty()
                    priority = r.priority
                    active = r.active
                    fullScreenAlert = r.fullScreenAlert
                    repeatType = RepeatType.of(r.repeatType)
                    userFullScreen = if (repeatType != RepeatType.PERIOD) r.fullScreenAlert else false
                    time = r.time.orEmpty()
                    monthlyRepeat = r.monthlyRepeat
                    yearlyRepeat = r.yearlyRepeat
                    autoRemove = r.autoRemove
                    dailyTimes += detail.times.map { it.time }.sorted()
                    periodFrom = r.periodFrom.orEmpty()
                    periodTo = r.periodTo.orEmpty()
                    weekdaysMask = r.weekdaysMask ?: Weekdays.ALL
                    loopSound = r.loopSound
                    soundUri = r.soundUri
                    detail.photos.forEach { photos += ReminderEditorPhoto(it, it.filePath) }
                }
            }
        }
    }

    fun setType(type: RepeatType) {
        repeatType = type
        // Period only fires through the full-screen alert — forced on (TZ 4.2 b); leaving
        // Period restores the user's own choice instead of leaving it stuck on.
        fullScreenAlert = if (type == RepeatType.PERIOD) true else userFullScreen
    }

    fun setFullScreen(value: Boolean) {
        if (repeatType != RepeatType.PERIOD) {
            userFullScreen = value
            fullScreenAlert = value
        }
    }

    // Monthly and Yearly repeat are mutually exclusive (TZ 4.1).
    fun onMonthlyRepeatChange(value: Boolean) {
        monthlyRepeat = value
        if (value) yearlyRepeat = false
    }

    fun onYearlyRepeatChange(value: Boolean) {
        yearlyRepeat = value
        if (value) monthlyRepeat = false
    }

    fun addDailyTime(picked: LocalTime) {
        val formatted = Dates.format(picked)
        if (formatted !in dailyTimes) {
            dailyTimes += formatted
            dailyTimes.sort()
        }
    }

    fun removeDailyTime(value: String) {
        dailyTimes.remove(value)
    }

    // Re-pick an existing Daily time (TZ 4.2 d′): drop the old value, add the new one
    // (addDailyTime keeps the list sorted and de-duplicated).
    fun editDailyTime(old: String, picked: LocalTime) {
        dailyTimes.remove(old)
        addDailyTime(picked)
    }

    fun addPhoto(source: Uri) {
        if (photos.size >= Limits.MAX_PHOTOS) return
        viewModelScope.launch {
            val fileName = PhotoManager.importPhoto(appContext, source) ?: return@launch
            photos += ReminderEditorPhoto(null, fileName)
        }
    }

    fun deletePhoto(index: Int) {
        val photo = photos.getOrNull(index) ?: return
        viewModelScope.launch {
            if (photo.entity != null) {
                repo.deletePhoto(photo.entity)
            } else {
                PhotoManager.delete(appContext, photo.fileName)
            }
            photos.remove(photo)
        }
    }

    fun save(onSaved: () -> Unit) {
        val error = validate()
        if (error != null) {
            // A past date/time isn't an input error — it's a warning (orange), like the card's
            // Active toggle; real form errors stay red.
            val message = appContext.getString(error)
            snack = if (error == R.string.error_once_past) {
                SnackEvent.warning(message)
            } else {
                SnackEvent.error(message)
            }
            return
        }

        val now = System.currentTimeMillis()
        val base = existing
        val oneTime = repeatType == RepeatType.ONE_TIME
        val period = repeatType == RepeatType.PERIOD
        val entity = ReminderEntity(
            id = base?.id ?: 0,
            title = title.trim(),
            content = content.trim().takeIf { it.isNotEmpty() },
            priority = priority,
            active = active,
            fullScreenAlert = fullScreenAlert,
            repeatType = repeatType.value,
            date = date.trim().takeIf { oneTime },
            time = time.trim().takeIf { oneTime || period },
            monthlyRepeat = oneTime && monthlyRepeat,
            yearlyRepeat = oneTime && yearlyRepeat,
            autoRemove = oneTime && autoRemove,
            // Stored exactly as entered — a bare day (recurring monthly window) or a full date
            // (one-shot range), TZ 4.2 e″/f″.
            periodFrom = periodFrom.trim().takeIf { period },
            periodTo = periodTo.trim().takeIf { period },
            weekdaysMask = weekdaysMask.takeIf { !oneTime },
            loopSound = loopSound,
            soundUri = soundUri,
            // Recomputed and armed by the repository right after save (TZ 4.10).
            nextFireAt = null,
            createdAt = base?.createdAt ?: now,
            updatedAt = now,
        )
        viewModelScope.launch {
            val id = repo.save(
                reminder = entity,
                dailyTimes = if (repeatType == RepeatType.DAILY) dailyTimes.toList() else emptyList(),
                tags = TextFormat.parseTags(tags),
            )
            photos.filter { it.entity == null }.forEach { repo.addPhoto(id, it.fileName) }
            saved = true
            onSaved()
        }
    }

    // Save validation (TZ 4.2): returns the error string res, or null when the form is valid.
    private fun validate(): Int? = when {
        title.isBlank() -> R.string.error_title_required

        repeatType == RepeatType.ONE_TIME &&
            (Dates.parseDate(date) == null || Dates.parseTime(time) == null) ->
            R.string.error_once_date_time

        // A plain active Once in the past would never fire (Monthly/Yearly roll forward, so
        // they're exempt) — block it instead of silently saving a dead alarm (TZ 4.2).
        repeatType == RepeatType.ONE_TIME && !monthlyRepeat && !yearlyRepeat &&
            active && oncePast() ->
            R.string.error_once_past

        repeatType == RepeatType.DAILY &&
            (dailyTimes.isEmpty() || weekdaysMask == Weekdays.NONE) ->
            R.string.error_daily_fields

        repeatType == RepeatType.PERIOD -> validatePeriod()

        else -> null
    }

    // Period From/To each hold a bare day (recurring monthly window) or a full date (one-shot
    // range); both must be the same kind. From > To is allowed for day numbers — the window
    // spans the month boundary (28→3) — but a date range must not run backwards (TZ 4.2 e″/f″).
    private fun validatePeriod(): Int? {
        if (Dates.parseTime(time) == null || weekdaysMask == Weekdays.NONE) return R.string.error_period_fields
        val from = periodFrom.trim()
        val to = periodTo.trim()
        val fromDate = Dates.parseDate(from)
        val toDate = Dates.parseDate(to)
        val fromValid = fromDate != null || Dates.parseDay(from) != null
        val toValid = toDate != null || Dates.parseDay(to) != null
        if (!fromValid || !toValid) return R.string.error_period_fields
        if ((fromDate != null) != (toDate != null)) return R.string.error_period_mixed
        if (fromDate != null && toDate!!.isBefore(fromDate)) return R.string.error_period_order
        return null
    }

    private fun oncePast(): Boolean {
        val d = Dates.parseDate(date) ?: return false
        val t = Dates.parseTime(time) ?: return false
        return LocalDateTime.of(d, t).isBefore(LocalDateTime.now())
    }

    // Back without Save: drop imported-but-unattached files (crash leftovers are caught
    // by the startup orphan sweep, TZ 8).
    override fun onCleared() {
        if (!saved) {
            photos.filter { it.entity == null }
                .forEach { PhotoManager.fileFor(appContext, it.fileName).delete() }
        }
    }

    companion object {
        private val DEFAULT_PRESETS = TimePresets(
            SettingsKeys.DEFAULT_MORNING,
            SettingsKeys.DEFAULT_DAY,
            SettingsKeys.DEFAULT_EVENING,
        )

        fun factory(reminderId: Long, folder: ReminderFolder): ViewModelProvider.Factory =
            appViewModelFactory { db, app ->
                ReminderEditorViewModel(
                    RemindersRepository(db, app),
                    db.tagsDao(),
                    db.settingsDao(),
                    app,
                    reminderId,
                    folder,
                )
            }
    }
}
