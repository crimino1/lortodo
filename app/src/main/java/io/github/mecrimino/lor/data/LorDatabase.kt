package io.github.mecrimino.lor.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    // ponytail: screens filter this one list in memory; fine for a personal to-do list, push filters into SQL if lists reach tens of thousands.
    @Query("SELECT * FROM tasks ORDER BY dueEpochDay, dueMinute IS NULL, dueMinute, id")
    fun observeTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE dueEpochDay = :epochDay ORDER BY dueMinute IS NULL, dueMinute, id")
    fun observeDay(epochDay: Long): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTask(id: Long): Task?

    @Query("SELECT * FROM tasks")
    suspend fun allTasks(): List<Task>

    @Query("SELECT * FROM tasks WHERE status = 'PENDING' AND reminder != 'NONE'")
    suspend fun pendingWithReminder(): List<Task>

    @Query("SELECT COUNT(*) FROM tasks WHERE dueEpochDay = :epochDay AND status = 'PENDING'")
    suspend fun pendingCount(epochDay: Long): Int

    @Insert
    suspend fun insertTask(task: Task): Long

    @Insert
    suspend fun insertTasks(tasks: List<Task>)

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)

    @Query("DELETE FROM tasks WHERE status = 'DONE'")
    suspend fun clearDone()

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()

    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY position, id")
    suspend fun subtasks(taskId: Long): List<Subtask>

    @Query("SELECT * FROM subtasks")
    suspend fun allSubtasks(): List<Subtask>

    @Insert
    suspend fun insertSubtasks(subtasks: List<Subtask>)

    @Query("DELETE FROM subtasks WHERE taskId = :taskId")
    suspend fun deleteSubtasks(taskId: Long)

    @Transaction
    suspend fun replaceSubtasks(taskId: Long, subtasks: List<Subtask>) {
        deleteSubtasks(taskId)
        insertSubtasks(subtasks.mapIndexed { i, s -> s.copy(id = 0, taskId = taskId, position = i) })
    }

    @Query("SELECT * FROM categories ORDER BY id")
    fun observeCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY id")
    suspend fun allCategories(): List<Category>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: Category): Long

    @Update
    suspend fun updateCategory(category: Category)

    @Insert
    suspend fun insertCategories(categories: List<Category>)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    /** Wipes and refills everything atomically: a failed restore must leave existing data intact. */
    @Transaction
    suspend fun replaceAll(categories: List<Category>, tasks: List<Task>, subtasks: List<Subtask>) {
        deleteAllTasks()
        deleteAllCategories()
        insertCategories(categories)
        insertTasks(tasks)
        insertSubtasks(subtasks)
    }
}

@Database(entities = [Category::class, Task::class, Subtask::class], version = 1)
abstract class LorDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        const val NAME = "lor.db"
        const val EXERCISE_ID = 3L
        const val FOOD_ID = 4L
        const val WATER_ID = 5L

        /** The design's starter lists. */
        val DEFAULT_CATEGORIES = listOf(
            Category(1, "Work", 0xFF2F6BFF.toInt(), "work"),
            Category(2, "Education", 0xFF7C5CFF.toInt(), "study"),
            Category(EXERCISE_ID, "Exercise", 0xFFFF6A2B.toInt(), "fitness"),
            Category(FOOD_ID, "Food", 0xFF1FA971.toInt(), "food"),
            Category(WATER_ID, "Water", 0xFF0EA5C6.toInt(), "water"),
            Category(6, "Personal", 0xFFE2468F.toInt(), "person"),
        )

        val SEED = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                DEFAULT_CATEGORIES.forEach {
                    db.execSQL(
                        "INSERT INTO categories (id, name, color, icon) VALUES (?, ?, ?, ?)",
                        arrayOf<Any>(it.id, it.name, it.color, it.icon),
                    )
                }
            }
        }
    }
}
