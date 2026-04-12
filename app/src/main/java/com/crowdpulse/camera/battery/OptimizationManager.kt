package com.crowdpulse.camera.battery

import android.app.Activity
import android.provider.Settings
import android.util.Log

class OptimizationManager(private val activity: Activity) {

    private var originalBrightness: Float = -1f

    fun onStreamingStarted() {
        Log.d("Optimization", "Applying battery optimizations: dimming screen.")
        val layoutParams = activity.window.attributes
        originalBrightness = layoutParams.screenBrightness
        
        // Dim screen to save battery while camera runs
        layoutParams.screenBrightness = 0.05f 
        activity.window.attributes = layoutParams
    }

    fun onStreamingStopped() {
        Log.d("Optimization", "Restoring screen brightness.")
        val layoutParams = activity.window.attributes
        layoutParams.screenBrightness = originalBrightness
        activity.window.attributes = layoutParams
    }
}
