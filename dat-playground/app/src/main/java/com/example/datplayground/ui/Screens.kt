package com.example.datplayground.ui

import androidx.compose.foundation.AndroidExternalSurface
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.datplayground.camera.CameraShowcaseViewModel
import com.example.datplayground.display.DisplayShowcaseViewModel
import com.example.datplayground.mock.MockShowcaseViewModel
import com.example.datplayground.session.GlassesSessionManager
import com.example.datplayground.wearables.WearablesRepository
import com.meta.wearable.dat.camera.types.StreamState
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.DeviceCompatibility
import com.meta.wearable.dat.core.types.LinkState
import com.meta.wearable.dat.display.types.DisplayState
import com.meta.wearable.dat.mockdevice.api.GlassesModel

private val TABS = listOf("Connect", "Display", "Camera", "Mock")

@Composable
fun PlaygroundRoot(
    repository: WearablesRepository,
    onRegister: () -> Unit,
    onUnregister: () -> Unit,
) {
  var tab by remember { mutableIntStateOf(0) }
  val snackbar = remember { SnackbarHostState() }
  val manager = remember { GlassesSessionManager.getInstance() }

  val managerMessage by manager.message.collectAsStateWithLifecycle()
  LaunchedEffect(managerMessage) {
    managerMessage?.let {
      snackbar.showSnackbar(it)
      manager.consumeMessage()
    }
  }

  MaterialTheme {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
          NavigationBar {
            TABS.forEachIndexed { index, title ->
              NavigationBarItem(
                  selected = tab == index,
                  onClick = { tab = index },
                  icon = {},
                  label = { Text(title) },
              )
            }
          }
        },
    ) { padding ->
      Column(Modifier.padding(padding).padding(16.dp)) {
        when (tab) {
          0 -> ConnectScreen(repository, manager, onRegister, onUnregister)
          1 -> DisplayScreen(manager)
          2 -> CameraScreen()
          3 -> MockScreen()
        }
      }
    }
  }
}

// ---------- Tab 1: registration, devices, session ----------

@Composable
private fun ConnectScreen(
    repository: WearablesRepository,
    manager: GlassesSessionManager,
    onRegister: () -> Unit,
    onUnregister: () -> Unit,
) {
  val registration by repository.registrationState.collectAsStateWithLifecycle()
  val devices by repository.devicesMetadata.collectAsStateWithLifecycle()
  val sessionState by manager.sessionState.collectAsStateWithLifecycle()

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("Registration: $registration", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = onRegister) { Text("Register with Meta AI") }
      OutlinedButton(onClick = onUnregister) { Text("Unregister") }
    }

    Text("Session: $sessionState", style = MaterialTheme.typography.titleMedium)
    OutlinedButton(onClick = { manager.stopSession() }) { Text("Stop session") }

    Text("Devices", style = MaterialTheme.typography.titleMedium)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      items(devices.entries.toList().size) { index ->
        val (id, device) = devices.entries.toList()[index]
        Card {
          Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(device.name, style = MaterialTheme.typography.titleSmall)
            Text(device.deviceType.description)
            Text(
                "link=${device.linkState} · display=${device.isDisplayCapable()}" +
                    if (device.compatibility == DeviceCompatibility.DEVICE_UPDATE_REQUIRED)
                        " · FIRMWARE UPDATE REQUIRED"
                    else "",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                enabled =
                    device.linkState == LinkState.CONNECTED &&
                        sessionState != DeviceSessionState.STARTED,
                onClick = { manager.startSession(id) },
            ) {
              Text("Start session")
            }
          }
        }
      }
    }
  }
}

// ---------- Tab 2: display showcase ----------

@Composable
private fun DisplayScreen(manager: GlassesSessionManager) {
  val viewModel: DisplayShowcaseViewModel = viewModel()
  val sessionState by manager.sessionState.collectAsStateWithLifecycle()
  val displayState by manager.displayState.collectAsStateWithLifecycle()
  val status by viewModel.status.collectAsStateWithLifecycle()
  val counter by viewModel.counter.collectAsStateWithLifecycle()
  val ready = displayState == DisplayState.STARTED

  Column(Modifier.verticalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
    Text("Display: $displayState · $status", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(
          enabled = sessionState == DeviceSessionState.STARTED,
          onClick = { manager.attachDisplay() },
      ) {
        Text("Attach display")
      }
      OutlinedButton(onClick = { manager.detachDisplay() }) { Text("Detach") }
    }

    Button(enabled = ready, onClick = viewModel::sendTypographyCard, modifier = Modifier.fillMaxWidth()) {
      Text("1 · Typography card")
    }
    Button(enabled = ready, onClick = viewModel::sendIconRow, modifier = Modifier.fillMaxWidth()) {
      Text("2 · Icon gallery")
    }
    Button(enabled = ready, onClick = viewModel::sendImages, modifier = Modifier.fillMaxWidth()) {
      Text("3 · Bitmap + URL images")
    }
    Button(enabled = ready, onClick = viewModel::sendCounter, modifier = Modifier.fillMaxWidth()) {
      Text("4 · Interactive counter (now: $counter)")
    }
    Button(enabled = ready, onClick = viewModel::sendVideo, modifier = Modifier.fillMaxWidth()) {
      Text("5 · Video playback")
    }
    OutlinedButton(enabled = ready, onClick = viewModel::clear, modifier = Modifier.fillMaxWidth()) {
      Text("Clear display")
    }
  }
}

// ---------- Tab 3: camera showcase ----------

@Composable
private fun CameraScreen() {
  val viewModel: CameraShowcaseViewModel = viewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  Column(Modifier.verticalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
    Text(
        "Stream: ${state.streamState} · ${state.resolution} · " +
            "${state.frameCount} frames · last ${state.lastFrameBytes} B",
        style = MaterialTheme.typography.titleSmall,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      VideoQuality.entries.forEach { quality ->
        FilterChip(
            selected = state.quality == quality,
            onClick = { viewModel.setQuality(quality) },
            label = { Text(quality.name) },
        )
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf(2, 7, 15, 24, 30).forEach { fps ->
        FilterChip(
            selected = state.frameRate == fps,
            onClick = { viewModel.setFrameRate(fps) },
            label = { Text("${fps}fps") },
        )
      }
    }

    // The HEVC decoder renders straight into this Surface.
    AndroidExternalSurface(
        modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f),
    ) {
      onSurface { surface, _, _ ->
        viewModel.onSurfaceReady(surface)
        surface.onDestroyed { viewModel.onSurfaceReady(null) }
      }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(
          enabled = state.streamState == StreamState.STOPPED,
          onClick = viewModel::startStreaming,
      ) {
        Text("Start stream")
      }
      Button(
          enabled = state.streamState == StreamState.STREAMING,
          onClick = viewModel::capturePhoto,
      ) {
        Text("Capture photo")
      }
      OutlinedButton(onClick = viewModel::stopStreaming) { Text("Stop") }
    }

    state.photo?.let { photo ->
      Text("Captured photo:", style = MaterialTheme.typography.titleSmall)
      Image(
          bitmap = photo.asImageBitmap(),
          contentDescription = "Captured photo",
          modifier = Modifier.fillMaxWidth().height(220.dp),
      )
    }
  }
}

// ---------- Tab 4: MockDeviceKit ----------

@Composable
private fun MockScreen() {
  val viewModel: MockShowcaseViewModel = viewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  Column(Modifier.verticalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
    Text(
        "Mock: ${if (state.isEnabled) "enabled" else "disabled"} · " +
            "paired=${state.pairedModel ?: "none"} · ${state.lifecycle}",
        style = MaterialTheme.typography.titleSmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = viewModel::enable) { Text("Enable") }
      OutlinedButton(onClick = viewModel::disable) { Text("Disable") }
    }
    Text("Pair a simulated model:", style = MaterialTheme.typography.titleSmall)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      GlassesModel.entries.forEach { model ->
        OutlinedButton(
            enabled = state.isEnabled,
            onClick = { viewModel.pair(model) },
            modifier = Modifier.fillMaxWidth(),
        ) {
          Text(model.name)
        }
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(enabled = state.pairedModel != null, onClick = viewModel::wear) {
        Text("Power on + wear")
      }
      OutlinedButton(enabled = state.pairedModel != null, onClick = viewModel::takeOff) {
        Text("Take off")
      }
    }
    Text(
        "Once worn, the mock device appears in the Connect tab like real " +
            "hardware — start a session there and drive the Display/Camera tabs " +
            "against it.",
        style = MaterialTheme.typography.bodySmall,
    )
  }
}
