package io.github.mecrimino.lor

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.mecrimino.lor.data.BackupManager
import io.github.mecrimino.lor.data.LorDatabase
import io.github.mecrimino.lor.data.SettingsRepository
import io.github.mecrimino.lor.data.TaskRepository
import io.github.mecrimino.lor.reminders.Notifier
import io.github.mecrimino.lor.reminders.ReminderScheduler
import java.io.File

/** The real object graph over an in-memory database; only Hilt is skipped. */
class TestEnv {
    val context: Context = ApplicationProvider.getApplicationContext()
    val db: LorDatabase = Room.inMemoryDatabaseBuilder(context, LorDatabase::class.java)
        .addCallback(LorDatabase.SEED)
        .allowMainThreadQueries()
        .build()
    val dao = db.taskDao()
    val settings = SettingsRepository(
        PreferenceDataStoreFactory.create { File(context.cacheDir, "test-${System.nanoTime()}.preferences_pb") }
    )
    val scheduler = ReminderScheduler(context, settings)
    val notifier = Notifier(context).also { it.createChannels() }
    val repo = TaskRepository(context, dao, scheduler, notifier)
    val backup = BackupManager(context, dao, repo, settings)
}
