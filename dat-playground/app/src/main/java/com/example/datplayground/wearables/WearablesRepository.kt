package com.example.datplayground.wearables

import android.app.Activity
import android.content.Context
import android.util.Log
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Device
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Observes SDK-level state that exists independent of any session:
 * registration with Meta AI, the set of known devices, and per-device
 * metadata (name, type, link state, compatibility, display capability).
 *
 * Adapted from the official DisplayAccess sample (BSD license).
 */
class WearablesRepository(
    private val applicationContext: Context,
    private val scope: CoroutineScope,
) {

  private val _registrationState =
      MutableStateFlow<RegistrationState>(RegistrationState.UNAVAILABLE)
  val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

  private val _devicesMetadata = MutableStateFlow<Map<DeviceIdentifier, Device>>(emptyMap())
  val devicesMetadata: StateFlow<Map<DeviceIdentifier, Device>> = _devicesMetadata.asStateFlow()

  private val _lastError = MutableStateFlow<String?>(null)
  val lastError: StateFlow<String?> = _lastError.asStateFlow()

  private val handler = CoroutineExceptionHandler { _, t ->
    Log.e(TAG, "Wearables monitoring failed", t)
  }

  private var monitoringStarted = false

  fun startMonitoring() {
    if (monitoringStarted) return
    monitoringStarted = true

    scope.launch(handler) {
      Wearables.registrationState.collect { state -> _registrationState.value = state }
    }
    scope.launch(handler) {
      Wearables.registrationErrorStream.collect { error ->
        _lastError.value = error.getLocalizedDescription(applicationContext)
      }
    }
    scope.launch(handler) {
      Wearables.devices.collect { identifiers -> onDevicesChanged(identifiers) }
    }
  }

  private fun onDevicesChanged(identifiers: Set<DeviceIdentifier>) {
    val known = _devicesMetadata.value.keys
    val removed = known - identifiers
    if (removed.isNotEmpty()) {
      _devicesMetadata.update { current -> current.filterKeys { it !in removed } }
    }
    (identifiers - known).forEach { id ->
      scope.launch(handler) {
        Wearables.devicesMetadata[id]?.collect { device ->
          _devicesMetadata.update { current -> current + (id to device) }
        }
      }
    }
  }

  /** Opens the Meta AI app flow that connects this app to the user's glasses. */
  fun startRegistration(activity: Activity) {
    if (_registrationState.value == RegistrationState.REGISTERED) return
    Wearables.startRegistration(activity)
  }

  fun startUnregistration(activity: Activity) {
    if (_registrationState.value != RegistrationState.REGISTERED) return
    Wearables.startUnregistration(activity)
  }

  companion object {
    private const val TAG = "PlaygroundWearablesRepo"

    @Volatile private var instance: WearablesRepository? = null

    fun getInstance(applicationContext: Context): WearablesRepository =
        instance
            ?: synchronized(this) {
              instance
                  ?: WearablesRepository(
                          applicationContext,
                          CoroutineScope(SupervisorJob() + Dispatchers.Main),
                      )
                      .also { instance = it }
            }
  }
}
