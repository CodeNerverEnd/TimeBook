package com.liubishchev.timebook

import android.app.Application
import com.liubishchev.timebook.timer.Notifications

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
    }
}
