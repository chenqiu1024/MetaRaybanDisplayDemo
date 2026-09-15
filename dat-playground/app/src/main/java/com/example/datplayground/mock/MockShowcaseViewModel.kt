package com.example.datplayground.mock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.GlassesModel
import com.meta.wearable.dat.mockdevice.api.MockGlasses
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MockUiState(
    val isEnabled: Boolean = false,
    val pairedModel: GlassesModel? = null,
    val lifecycle: String = "—",
    val message: String? = null,
)

/**
 * MockDeviceKit showcase — the whole DAT stack without physical glasses:
 * fake registration, a simulated paired device, and glasses lifecycle events
 * (power / fold / don). Once "worn", the mock device shows up in
 * Wearables.devices exactly like real hardware, so the Connect/Display/Camera
 * tabs work against it unchanged.
 */
class MockShowcaseViewModel(application: Application) : AndroidViewModel(application) {

  private val mockDeviceKit = MockDeviceKit.getInstance(application.applicationContext)

  private val _uiState = MutableStateFlow(MockUiState())
  val uiState: StateFlow<MockUiState> = _uiState.asStateFlow()

  private var device: MockGlasses? = null

  /** Auto-initializes Wearables if needed and fakes RegistrationState.REGISTERED. */
  fun enable() {
    mockDeviceKit.enable()
    _uiState.update { it.copy(isEnabled = true, message = "Mock environment enabled") }
  }

  fun disable() {
    mockDeviceKit.disable()
    device = null
    _uiState.update { MockUiState(message = "Mock environment disabled") }
  }

  fun pair(model: GlassesModel) {
    viewModelScope.launch {
      mockDeviceKit
          .pairGlasses(model)
          .fold(
              onSuccess = { glasses ->
                device = glasses
                _uiState.update { it.copy(pairedModel = model, message = "Paired $model") }
              },
              onFailure = { error, _ ->
                _uiState.update { it.copy(message = "Pair failed: $error") }
              },
          )
    }
  }

  /** Simulate wearing the glasses: power on, unfold, put on. */
  fun wear() {
    val glasses = device ?: return
    glasses.powerOn()
    glasses.unfold()
    glasses.don()
    _uiState.update { it.copy(lifecycle = "powered on · unfolded · worn") }
  }

  /** Simulate taking them off and folding them. */
  fun takeOff() {
    val glasses = device ?: return
    glasses.doff()
    glasses.fold()
    glasses.powerOff()
    _uiState.update { it.copy(lifecycle = "off · folded · powered off") }
  }

  fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
