package io.github.mecrimino.lor.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Settings(
    val name: String = "",
    val onboarded: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val defaultReminder: ReminderType = ReminderType.NOTIFICATION,
    val nagIntervalMin: Int = 30,
    val nagMax: Int = 5,
    val alarmSound: String? = null,
    val vibrate: Boolean = true,
    val summaryOn: Boolean = false,
    val morningMin: Int = 8 * 60,
    val eveningMin: Int = 20 * 60,
    val batteryTipSeen: Boolean = false,
    val waterGoal: Int = 8,
    val water: Int = 0,
    val waterDay: Long = 0,
) {
    /** Water logged today; yesterday's glasses don't carry over. */
    fun waterToday(today: LocalDate = LocalDate.now()) = if (waterDay == today.toEpochDay()) water else 0
}

@Singleton
class SettingsRepository @Inject constructor(private val store: DataStore<Preferences>) {

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            name = p[NAME] ?: "",
            onboarded = p[ONBOARDED] ?: false,
            theme = enumOr(p[THEME], ThemeMode.SYSTEM),
            defaultReminder = enumOr(p[DEFAULT_REMINDER], ReminderType.NOTIFICATION),
            nagIntervalMin = p[NAG_INTERVAL] ?: 30,
            nagMax = p[NAG_MAX] ?: 5,
            alarmSound = p[ALARM_SOUND],
            vibrate = p[VIBRATE] ?: true,
            summaryOn = p[SUMMARY_ON] ?: false,
            morningMin = p[MORNING] ?: (8 * 60),
            eveningMin = p[EVENING] ?: (20 * 60),
            batteryTipSeen = p[BATTERY_TIP] ?: false,
            waterGoal = p[WATER_GOAL] ?: 8,
            water = p[WATER] ?: 0,
            waterDay = p[WATER_DAY] ?: 0,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setName(name: String) = store.edit { it[NAME] = name.trim() }
    suspend fun setOnboarded() = store.edit { it[ONBOARDED] = true }
    suspend fun setTheme(mode: ThemeMode) = store.edit { it[THEME] = mode.name }
    suspend fun setDefaultReminder(type: ReminderType) = store.edit { it[DEFAULT_REMINDER] = type.name }
    suspend fun setNagInterval(min: Int) = store.edit { it[NAG_INTERVAL] = min.coerceIn(MIN_NAG_INTERVAL, MAX_NAG_INTERVAL) }
    suspend fun setNagMax(max: Int) = store.edit { it[NAG_MAX] = max.coerceIn(0, MAX_NAG_COUNT) }
    suspend fun setAlarmSound(uri: String?) = store.edit { if (uri == null) it.remove(ALARM_SOUND) else it[ALARM_SOUND] = uri }
    suspend fun setVibrate(on: Boolean) = store.edit { it[VIBRATE] = on }
    suspend fun setSummaryOn(on: Boolean) = store.edit { it[SUMMARY_ON] = on }
    suspend fun setMorning(min: Int) = store.edit { it[MORNING] = min }
    suspend fun setEvening(min: Int) = store.edit { it[EVENING] = min }
    suspend fun setBatteryTipSeen() = store.edit { it[BATTERY_TIP] = true }
    suspend fun setWaterGoal(goal: Int) = store.edit { it[WATER_GOAL] = goal.coerceIn(1, 20) }

    /** Design behaviour: each tap logs a glass; tapping once the goal is hit resets to zero. */
    suspend fun tapWater(today: LocalDate = LocalDate.now()) = store.edit {
        val day = today.toEpochDay()
        val water = if (it[WATER_DAY] == day) it[WATER] ?: 0 else 0
        it[WATER_DAY] = day
        it[WATER] = if (water >= (it[WATER_GOAL] ?: 8)) 0 else water + 1
    }

    /** Restores what a backup carries; the alarm sound URI is device-specific, so it stays as is. */
    suspend fun restore(s: Settings) = store.edit {
        it[NAME] = s.name.trim()
        it[THEME] = s.theme.name
        it[DEFAULT_REMINDER] = s.defaultReminder.name
        it[NAG_INTERVAL] = s.nagIntervalMin.coerceIn(MIN_NAG_INTERVAL, MAX_NAG_INTERVAL)
        it[NAG_MAX] = s.nagMax.coerceIn(0, MAX_NAG_COUNT)
        it[VIBRATE] = s.vibrate
        it[SUMMARY_ON] = s.summaryOn
        it[MORNING] = s.morningMin.coerceIn(0, 1439)
        it[EVENING] = s.eveningMin.coerceIn(0, 1439)
        it[WATER_GOAL] = s.waterGoal.coerceIn(1, 20)
    }

    suspend fun clear() = store.edit { it.clear() }

    companion object {
        const val MIN_NAG_INTERVAL = 5
        const val MAX_NAG_INTERVAL = 240
        const val MAX_NAG_COUNT = 20

        private val NAME = stringPreferencesKey("name")
        private val ONBOARDED = booleanPreferencesKey("onboarded")
        private val THEME = stringPreferencesKey("theme")
        private val DEFAULT_REMINDER = stringPreferencesKey("default_reminder")
        private val NAG_INTERVAL = intPreferencesKey("nag_interval_min")
        private val NAG_MAX = intPreferencesKey("nag_max")
        private val ALARM_SOUND = stringPreferencesKey("alarm_sound")
        private val VIBRATE = booleanPreferencesKey("vibrate")
        private val SUMMARY_ON = booleanPreferencesKey("summary_on")
        private val MORNING = intPreferencesKey("morning_min")
        private val EVENING = intPreferencesKey("evening_min")
        private val BATTERY_TIP = booleanPreferencesKey("battery_tip_seen")
        private val WATER_GOAL = intPreferencesKey("water_goal")
        private val WATER = intPreferencesKey("water")
        private val WATER_DAY = longPreferencesKey("water_day")

        private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: fallback
    }
}
