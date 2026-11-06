package io.github.mecrimino.lor

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.mecrimino.lor.reminders.Notifier
import javax.inject.Inject

@HiltAndroidApp
class LorApp : Application() {
    @Inject lateinit var notifier: Notifier

    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
    }
}
