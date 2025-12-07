package com.emailclient

import android.app.Application
import com.emailclient.util.WorkManagerHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class EmailApplication : Application() {

    @Inject
    lateinit var workManagerHelper: WorkManagerHelper

    override fun onCreate() {
        super.onCreate()

        // Schedule periodic email sync
        workManagerHelper.schedulePeriodicSync()
    }
}
