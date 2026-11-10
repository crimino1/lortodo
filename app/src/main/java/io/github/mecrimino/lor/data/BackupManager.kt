package io.github.mecrimino.lor.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class BackupData(
    val settings: Settings,
    val categories: List<Category>,
    val tasks: List<Task>,
    val subtasks: List<Subtask>,
)

/** JSON export/import. Backup files come from outside the app, so parsing rejects anything malformed before any data is touched. */
@Singleton
class BackupManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: TaskDao,
    private val repo: TaskRepository,
    private val settings: SettingsRepository,
) {
    suspend fun export(uri: Uri) {
        val json = encode(BackupData(settings.current(), dao.allCategories(), dao.allTasks(), dao.allSubtasks()))
        withContext(Dispatchers.IO) {
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Couldn't open file")
            out.use { it.write(json.toByteArray()) }
        }
    }

    suspend fun import(uri: Uri) {
        val text = withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(uri) ?: error("Couldn't open file")
            input.use { stream ->
                val bytes = stream.readNBytesCompat(MAX_BYTES + 1)
                require(bytes.size <= MAX_BYTES) { "File is too large to be a LOR backup" }
                String(bytes)
            }
        }
        restore(decode(text))
    }

    suspend fun restore(data: BackupData) {
        val now = System.currentTimeMillis()
        // A restore must not set off every old reminder at once: past-due ones stay quiet.
        val tasks = data.tasks.map { t ->
            val due = t.dueAtMillis()
            val snoozedAhead = (t.snoozedUntil ?: 0) > now
            if (t.isDone || due == null || due > now || snoozedAhead) t
            else t.copy(firedCount = SILENCED, snoozedUntil = null, lastFiredAt = null)
        }
        repo.replaceAll(data.categories, tasks, data.subtasks) // throws before settings change if the swap fails
        settings.restore(data.settings)
        repo.rescheduleAll() // restored nag settings and summary times
    }

    companion object {
        private const val VERSION = 1
        private const val MAX_BYTES = 50 * 1024 * 1024

        private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (out.size() < limit) {
                val n = read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }

        fun encode(d: BackupData): String {
            val subtasksByTask = d.subtasks.groupBy { it.taskId }
            return JSONObject()
                .put("app", "lor")
                .put("version", VERSION)
                .put(
                    "settings",
                    JSONObject()
                        .put("name", d.settings.name)
                        .put("theme", d.settings.theme.name)
                        .put("defaultReminder", d.settings.defaultReminder.name)
                        .put("nagIntervalMin", d.settings.nagIntervalMin)
                        .put("nagMax", d.settings.nagMax)
                        .put("vibrate", d.settings.vibrate)
                        .put("summaryOn", d.settings.summaryOn)
                        .put("morningMin", d.settings.morningMin)
                        .put("eveningMin", d.settings.eveningMin)
                        .put("waterGoal", d.settings.waterGoal),
                )
                .put(
                    "categories",
                    JSONArray(d.categories.map { c ->
                        JSONObject().put("id", c.id).put("name", c.name).put("color", c.color).put("icon", c.icon)
                    }),
                )
                .put(
                    "tasks",
                    JSONArray(d.tasks.map { t ->
                        JSONObject()
                            .put("id", t.id)
                            .put("title", t.title)
                            .put("notes", t.notes)
                            .put("dueEpochDay", t.dueEpochDay)
                            .putOpt("dueMinute", t.dueMinute)
                            .put("priority", t.priority.name)
                            .putOpt("categoryId", t.categoryId)
                            .put("repeat", t.repeat.name)
                            .put("repeatDays", t.repeatDays)
                            .put("reminder", t.reminder.name)
                            .put("status", t.status.name)
                            .put("createdAt", t.createdAt)
                            .putOpt("completedAt", t.completedAt)
                            .putOpt("snoozedUntil", t.snoozedUntil)
                            .put("firedCount", t.firedCount)
                            .putOpt("lastFiredAt", t.lastFiredAt)
                            .putOpt("nextOccurrenceId", t.nextOccurrenceId)
                            .put(
                                "subtasks",
                                JSONArray(subtasksByTask[t.id].orEmpty().map { s ->
                                    JSONObject().put("title", s.title).put("done", s.done).put("position", s.position)
                                }),
                            )
                    }),
                )
                .toString(2)
        }

        /** Throws on anything invalid. Unknown fields are ignored so older apps can read newer files where possible. */
        fun decode(text: String): BackupData {
            val root = JSONObject(text)
            require(root.optString("app") == "lor") { "Not a LOR backup" }
            require(root.getInt("version") <= VERSION) { "Backup was made by a newer version of LOR" }

            val s = root.getJSONObject("settings")
            val defaults = Settings()
            val settings = defaults.copy(
                name = s.optString("name", "").take(40),
                theme = enumValueOf(s.optString("theme", defaults.theme.name)),
                defaultReminder = enumValueOf(s.optString("defaultReminder", defaults.defaultReminder.name)),
                nagIntervalMin = s.optInt("nagIntervalMin", defaults.nagIntervalMin),
                nagMax = s.optInt("nagMax", defaults.nagMax),
                vibrate = s.optBoolean("vibrate", defaults.vibrate),
                summaryOn = s.optBoolean("summaryOn", defaults.summaryOn),
                morningMin = s.optInt("morningMin", defaults.morningMin),
                eveningMin = s.optInt("eveningMin", defaults.eveningMin),
                waterGoal = s.optInt("waterGoal", defaults.waterGoal),
            )

            val categories = root.getJSONArray("categories").objects().map {
                val id = it.getLong("id")
                require(id > 0) { "Bad list id" }
                Category(id, it.getString("name"), it.getInt("color"), it.optString("icon", "star"))
            }
            val categoryIds = categories.map { it.id }.toSet()
            require(categoryIds.size == categories.size) { "Duplicate lists in backup" }

            val subtasks = mutableListOf<Subtask>()
            val tasks = root.getJSONArray("tasks").objects().map { o ->
                val id = o.getLong("id")
                require(id > 0) { "Bad task id" }
                val title = o.getString("title").trim()
                require(title.isNotEmpty()) { "Task without a title" }
                val day = o.getLong("dueEpochDay")
                LocalDate.ofEpochDay(day) // throws if out of range
                val minute = o.longOrNull("dueMinute")?.toInt()
                require(minute == null || minute in 0..1439) { "Bad due time" }
                o.optJSONArray("subtasks")?.objects()?.forEach { sub ->
                    subtasks += Subtask(taskId = id, title = sub.getString("title"), done = sub.optBoolean("done"), position = sub.optInt("position"))
                }
                Task(
                    id = id,
                    title = title,
                    notes = o.optString("notes", ""),
                    dueEpochDay = day,
                    dueMinute = minute,
                    priority = enumValueOf(o.optString("priority", "LOW")),
                    categoryId = o.longOrNull("categoryId")?.takeIf { it in categoryIds },
                    repeat = enumValueOf(o.optString("repeat", "NONE")),
                    repeatDays = o.optInt("repeatDays") and 0x7F,
                    reminder = enumValueOf(o.optString("reminder", "NONE")),
                    status = enumValueOf(o.optString("status", "PENDING")),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    completedAt = o.longOrNull("completedAt"),
                    snoozedUntil = o.longOrNull("snoozedUntil"),
                    firedCount = o.optInt("firedCount"),
                    lastFiredAt = o.longOrNull("lastFiredAt"),
                    nextOccurrenceId = o.longOrNull("nextOccurrenceId"),
                )
            }
            require(tasks.map { it.id }.toSet().size == tasks.size) { "Duplicate tasks in backup" }
            return BackupData(settings, categories, tasks, subtasks)
        }

        private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
        private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
    }
}
