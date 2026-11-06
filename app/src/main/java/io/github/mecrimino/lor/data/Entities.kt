package io.github.mecrimino.lor.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class Priority { LOW, MEDIUM, HIGH }
enum class RepeatType { NONE, DAILY, WEEKLY, MONTHLY, CUSTOM }
enum class ReminderType { NONE, NOTIFICATION, ALARM }
enum class TaskStatus { PENDING, DONE }

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val icon: String,
) {
    companion object {
        /** Design's stand-in for tasks whose list was deleted. */
        val Other = Category(id = -1, name = "Other", color = 0xFF5B6472.toInt(), icon = "star")
    }
}

/**
 * Due date is required, time is optional ("Any time" in the design). Stored as wall-clock values, so a
 * task due at 09:00 stays at 09:00 across timezone changes.
 */
@Entity(
    tableName = "tasks",
    foreignKeys = [ForeignKey(Category::class, ["id"], ["categoryId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("categoryId"), Index("dueEpochDay")],
)
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val dueEpochDay: Long,
    val dueMinute: Int? = null,
    val priority: Priority = Priority.LOW,
    val categoryId: Long? = null,
    val repeat: RepeatType = RepeatType.NONE,
    /** CUSTOM repeat: bit 0 = Monday ... bit 6 = Sunday. */
    val repeatDays: Int = 0,
    val reminder: ReminderType = ReminderType.NONE,
    val status: TaskStatus = TaskStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val snoozedUntil: Long? = null,
    /** Reminders fired since the due time: 1 = the initial one, more = nags. */
    val firedCount: Int = 0,
    val lastFiredAt: Long? = null,
    /** The repeat spawned on completion; removed again if this task is un-completed. */
    val nextOccurrenceId: Long? = null,
) {
    val dueDate: LocalDate get() = LocalDate.ofEpochDay(dueEpochDay)
    val dueTime: LocalTime? get() = dueMinute?.let { LocalTime.of(it / 60, it % 60) }
    val isDone: Boolean get() = status == TaskStatus.DONE

    fun dueAtMillis(zone: ZoneId = ZoneId.systemDefault()): Long? =
        dueTime?.let { dueDate.atTime(it).atZone(zone).toInstant().toEpochMilli() }
}

@Entity(
    tableName = "subtasks",
    foreignKeys = [ForeignKey(Task::class, ["id"], ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taskId")],
)
data class Subtask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long = 0,
    val title: String,
    val done: Boolean = false,
    val position: Int = 0,
)
