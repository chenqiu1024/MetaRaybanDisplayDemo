package com.example.datplayground.session

import android.util.Log
import com.meta.wearable.dat.camera.Camera
import com.meta.wearable.dat.camera.addCamera
import com.meta.wearable.dat.camera.removeCamera
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.SpecificDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.DeviceSessionError
import com.meta.wearable.dat.display.Display
import com.meta.wearable.dat.display.addDisplay
import com.meta.wearable.dat.display.removeDisplay
import com.meta.wearable.dat.display.types.DisplayState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns exactly one [DeviceSession] and the capabilities attached to it.
 *
 * Lifecycle contract (from the official docs):
 *   createSession(selector) -> session.start() -> wait for STARTED
 *   -> addDisplay() / addCamera(config) -> wait for capability STARTED
 *   -> ... use ... -> removeDisplay()/camera.stop() -> session.stop()
 */
class GlassesSessionManager private constructor() {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  private var session: DeviceSession? = null
  private var stateJob: Job? = null
  private var errorJob: Job? = null
  private var displayStateJob: Job? = null

  private val _sessionState = MutableStateFlow(DeviceSessionState.STOPPED)
  val sessionState: StateFlow<DeviceSessionState> = _sessionState.asStateFlow()

  private val _display = MutableStateFlow<Display?>(null)
  val display: StateFlow<Display?> = _display.asStateFlow()

  private val _displayState = MutableStateFlow(DisplayState.STOPPED)
  val displayState: StateFlow<DisplayState> = _displayState.asStateFlow()

  private val _camera = MutableStateFlow<Camera?>(null)
  val camera: StateFlow<Camera?> = _camera.asStateFlow()

  private val _message = MutableStateFlow<String?>(null)
  val message: StateFlow<String?> = _message.asStateFlow()

  /** Creates and starts a session against a user-picked device. */
  fun startSession(deviceId: DeviceIdentifier) {
    if (session != null) {
      _message.value = "Session already active"
      return
    }
    Wearables.createSession(SpecificDeviceSelector(deviceId))
        .fold(
            onSuccess = { newSession ->
              session = newSession
              stateJob =
                  scope.launch {
                    newSession.state.collect { state ->
                      _sessionState.value = state
                      if (state == DeviceSessionState.STOPPED) cleanupAfterStop()
                    }
                  }
              errorJob =
                  scope.launch {
                    newSession.errors.collect { error ->
                      // DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED -> offer
                      // Wearables.openDATGlassesAppUpdate(activity) in real apps.
                      _message.value = "Session error: ${error.description}"
                      if (error == DeviceSessionError.DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED) {
                        Log.w(TAG, "Glasses-side DAT app update required")
                      }
                    }
                  }
              newSession.start() // async; failures surface through session.errors
            },
            onFailure = { error, _ ->
              _message.value = "createSession failed: ${error.description}"
            },
        )
  }

  fun stopSession() {
    val current = session ?: return
    detachDisplay()
    detachCamera()
    current.stop()
  }

  // ---- Display capability ----

  /** Call only after the session reports [DeviceSessionState.STARTED]. */
  fun attachDisplay() {
    val current = session ?: run { _message.value = "No active session"; return }
    if (_display.value != null) return
    current.addDisplay()
        .fold(
            onSuccess = { newDisplay ->
              _display.value = newDisplay
              displayStateJob =
                  scope.launch {
                    newDisplay.state.collect { state -> _displayState.value = state }
                  }
            },
            onFailure = { error, _ ->
              _message.value = "addDisplay failed: ${error.description}"
            },
        )
  }

  fun detachDisplay() {
    val current = session ?: return
    if (_display.value == null) return
    displayStateJob?.cancel()
    displayStateJob = null
    current.removeDisplay().onFailure { error, _ ->
      _message.value = "removeDisplay failed: ${error.description}"
    }
    _display.value = null
    _displayState.value = DisplayState.STOPPED
  }

  // ---- Camera capability ----

  fun attachCamera(configuration: StreamConfiguration) {
    val current = session ?: run { _message.value = "No active session"; return }
    if (_camera.value != null) return
    current.addCamera(configuration)
        .fold(
            onSuccess = { newCamera -> _camera.value = newCamera },
            onFailure = { error, _ ->
              _message.value = "addCamera failed: ${error.description}"
            },
        )
  }

  fun detachCamera() {
    val current = session ?: return
    _camera.value?.let {
      it.stop()
      current.removeCamera().onFailure { error, _ ->
        _message.value = "removeCamera failed: ${error.description}"
      }
      _camera.value = null
    }
  }

  fun consumeMessage() {
    _message.value = null
  }

  private fun cleanupAfterStop() {
    stateJob?.cancel()
    errorJob?.cancel()
    displayStateJob?.cancel()
    stateJob = null
    errorJob = null
    displayStateJob = null
    _display.value = null
    _displayState.value = DisplayState.STOPPED
    _camera.value = null
    session = null
  }

  companion object {
    private const val TAG = "GlassesSessionManager"

    @Volatile private var instance: GlassesSessionManager? = null

    fun getInstance(): GlassesSessionManager =
        instance ?: synchronized(this) { instance ?: GlassesSessionManager().also { instance = it } }
  }
}
