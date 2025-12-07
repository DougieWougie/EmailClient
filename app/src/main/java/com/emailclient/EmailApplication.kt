package com.emailclient

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class EmailApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize app-level components here
    }
}
