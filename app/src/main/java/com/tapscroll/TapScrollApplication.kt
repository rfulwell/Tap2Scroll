package com.tapscroll

import android.app.Application
import com.tapscroll.data.PreferenceStore

/**
 * Application class for TapToScroll
 */
class TapScrollApplication : Application() {

    lateinit var preferenceStore: PreferenceStore
        private set

    override fun onCreate() {
        super.onCreate()
        
        // Initialize preference store
        preferenceStore = PreferenceStore(applicationContext)
    }
}
