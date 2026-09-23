package com.sonix21.suinode

import android.app.Application
import com.sonix21.suinode.core.AppPrefs
import com.sonix21.suinode.data.Panels

class NodeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        APP.prefs = AppPrefs(this)
        com.sonix21.suinode.data.ConnectionSecurity.init(this)
        com.sonix21.suinode.data.SaveGuard.init(this)
        com.sonix21.suinode.data.OfflineOverview.init(this)
        Panels.init(this)
        com.sonix21.suinode.data.MonitoringScheduler.reconcile(this)
    }
}

object APP {
    lateinit var prefs: AppPrefs
}
