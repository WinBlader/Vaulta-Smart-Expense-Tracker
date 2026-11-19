package com.waleed.vaulta.android

import android.app.Application
import com.waleed.vaulta.android.data.CurrencyManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class Vaulta: Application() {
    override fun onCreate() {
        super.onCreate()
        CurrencyManager.init(this)
    }
}
