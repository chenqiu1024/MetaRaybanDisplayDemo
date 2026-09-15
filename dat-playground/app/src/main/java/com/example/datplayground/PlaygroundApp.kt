package com.example.datplayground

import android.app.Application
import android.util.Log
import com.meta.wearable.dat.core.Wearables

/**
 * DAT Playground — a portfolio-style demo of the Meta Wearables Device Access
 * Toolkit (v0.9.0): registration, device sessions, the glasses Display DSL,
 * camera streaming / photo capture, and MockDeviceKit.
 *
 * Patterns follow Meta's official samples (BSD license):
 * https://github.com/facebook/meta-wearables-dat-android
 */
class PlaygroundApp : Application() {

  override fun onCreate() {
    super.onCreate()
    // Initialize the SDK exactly once, as early as possible.
    Wearables.initialize(this).onFailure { error, _ ->
      Log.e(TAG, "Failed to initialize DAT: ${error.description}")
    }
  }

  private companion object {
    const val TAG = "DATPlayground"
  }
}
