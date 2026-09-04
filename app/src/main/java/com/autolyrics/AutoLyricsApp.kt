package com.autolyrics

import android.app.Application
import android.content.Intent

import com.autolyrics.media.MediaTracker

class AutoLyricsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MediaTracker.init(this)
        // CarAppService manages its own lifecycle via Android Auto binding
    }
}
