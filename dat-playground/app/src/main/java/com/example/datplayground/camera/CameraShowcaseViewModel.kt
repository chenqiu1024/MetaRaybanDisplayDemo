package com.example.datplayground.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.datplayground.session.GlassesSessionManager
import com.meta.wearable.dat.camera.Stream
import com.meta.wearable.dat.camera.types.PhotoData
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamState
import com.meta.wearable.dat.camera.types.VideoFrame
import com.meta.wearable.dat.camera.types.VideoQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CameraUiState(
    val streamState: StreamState = StreamState.STOPPED,
    val quality: VideoQuality = VideoQuality.MEDIUM,
    val frameRate: Int = 24,
    val frameCount: Long = 0,
    val lastFrameBytes: Int = 0,
    val resolution: String = "--",
    val photo: Bitmap? = null,
    val message: String? = null,
)

/**
 * Camera capability showcase: configure the stream (quality / frame rate),
 * watch StreamState transitions, count the compressed HEVC frames, render
 * them into a Surface via [HevcDecoder], and capture a still photo.
 */
class CameraShowcaseViewModel : ViewModel() {

  private val manager = GlassesSessionManager.getInstance()

  private val _uiState = MutableStateFlow(CameraUiState())
  val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

  private var stateJob: Job? = null
  private var frameJob: Job? = null
  private val decoder = HevcDecoder()
  @Volatile private var previewSurface: Surface? = null
  @Volatile private var decoderStarted = false

  fun setQuality(quality: VideoQuality) = _uiState.update { it.copy(quality = quality) }

  fun setFrameRate(fps: Int) = _uiState.update { it.copy(frameRate = fps) }

  /** Called by the Compose AndroidExternalSurface when the preview surface exists. */
  fun onSurfaceReady(surface: Surface?) {
    previewSurface = surface
  }

  fun startStreaming() {
    val state = _uiState.value
    manager.attachCamera(
        StreamConfiguration(
            videoQuality = state.quality,
            frameRate = state.frameRate,
            compressVideo = true, // frames arrive as compressed HEVC access units
        )
    )
    val camera = manager.camera.value ?: return
    val stream: Stream = camera.stream

    stateJob =
        viewModelScope.launch {
          stream.state.collect { s -> _uiState.update { it.copy(streamState = s) } }
        }
    frameJob =
        viewModelScope.launch(Dispatchers.Default) {
          stream.videoStream.collect { frame -> onFrame(frame) }
        }

    stream.start().onFailure { error, _ ->
      _uiState.update { it.copy(message = "Stream start failed: ${error.description}") }
      stopStreaming()
    }
  }

  private fun onFrame(frame: VideoFrame) {
    if (!frame.isCompressed) return
    val buffer = frame.buffer
    val bytes = ByteArray(buffer.remaining())
    val position = buffer.position()
    buffer.get(bytes)
    buffer.position(position)

    _uiState.update {
      it.copy(
          frameCount = it.frameCount + 1,
          lastFrameBytes = bytes.size,
          resolution = "${frame.width}x${frame.height}",
      )
    }

    val surface = previewSurface
    if (surface != null) {
      if (!decoderStarted) {
        decoderStarted = true
        decoder.start(frame.width, frame.height, surface)
      }
      decoder.decodeFrame(bytes, frame.presentationTimeUs, frame.isCodecConfig)
    }
  }

  fun capturePhoto() {
    val camera = manager.camera.value ?: return
    viewModelScope.launch {
      camera.stream
          .capturePhoto()
          .onSuccess { photoData ->
            val bitmap =
                withContext(Dispatchers.Default) {
                  when (photoData) {
                    is PhotoData.Bitmap -> photoData.bitmap
                    is PhotoData.HEIC -> {
                      val bytes = ByteArray(photoData.data.remaining())
                      photoData.data.get(bytes)
                      BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                  }
                }
            _uiState.update { it.copy(photo = bitmap, message = "Photo captured") }
          }
          .onFailure { error, _ ->
            _uiState.update { it.copy(message = "Capture failed: ${error.description}") }
          }
    }
  }

  fun stopStreaming() {
    stateJob?.cancel()
    frameJob?.cancel()
    stateJob = null
    frameJob = null
    decoder.stop()
    decoderStarted = false
    manager.detachCamera()
    _uiState.update { it.copy(streamState = StreamState.STOPPED, frameCount = 0) }
  }

  fun consumeMessage() = _uiState.update { it.copy(message = null) }

  override fun onCleared() {
    stopStreaming()
  }
}
