package io.github.mecrimino.lor.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.mecrimino.lor.data.LorDatabase
import io.github.mecrimino.lor.data.TaskDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): LorDatabase =
        Room.databaseBuilder(context, LorDatabase::class.java, LorDatabase.NAME)
            .addCallback(LorDatabase.SEED)
            .build()

    @Provides
    fun taskDao(db: LorDatabase): TaskDao = db.taskDao()

    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}
