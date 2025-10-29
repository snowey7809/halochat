package com.rapo.haloai

import android.app.Application
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HaloAIApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    
    companion object {
        lateinit var instance: HaloAIApplication
            private set
    }
}
