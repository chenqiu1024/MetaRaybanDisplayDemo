package com.example.datplayground

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.example.datplayground.ui.PlaygroundRoot
import com.example.datplayground.wearables.WearablesRepository

class MainActivity : ComponentActivity() {

  private val repository by lazy { WearablesRepository.getInstance(applicationContext) }

  private val permissionLauncher =
      registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // BLUETOOTH_CONNECT is required before the SDK can see devices.
        repository.startMonitoring()
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))

    setContent {
      PlaygroundRoot(
          repository = repository,
          onRegister = { repository.startRegistration(this) },
          onUnregister = { repository.startUnregistration(this) },
      )
    }
  }
}
