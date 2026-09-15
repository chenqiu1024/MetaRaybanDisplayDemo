package com.example.datplayground.display

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.datplayground.session.GlassesSessionManager
import com.meta.wearable.dat.display.Display
import com.meta.wearable.dat.display.types.VideoCodec
import com.meta.wearable.dat.display.types.VideoPlayerState
import com.meta.wearable.dat.display.types.VideoSource
import com.meta.wearable.dat.display.views.Alignment
import com.meta.wearable.dat.display.views.ButtonStyle
import com.meta.wearable.dat.display.views.ContentScope
import com.meta.wearable.dat.display.views.CornerRadius
import com.meta.wearable.dat.display.views.Direction
import com.meta.wearable.dat.display.views.FlexBoxBackground
import com.meta.wearable.dat.display.views.IconName
import com.meta.wearable.dat.display.views.IconStyle
import com.meta.wearable.dat.display.views.ImageSize
import com.meta.wearable.dat.display.views.TextColor
import com.meta.wearable.dat.display.views.TextStyle
import com.meta.wearable.dat.display.views.VideoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Portfolio of everything the Display DSL can put on the glasses:
 * text styles, icons, layout, local Bitmap + remote URL images,
 * interactive buttons whose taps are routed back to the phone, and video.
 *
 * Every sendContent { } call replaces the whole screen on the glasses —
 * "send one root view" is the core mental model.
 */
class DisplayShowcaseViewModel : ViewModel() {

  private val manager = GlassesSessionManager.getInstance()

  private val _status = MutableStateFlow("Idle")
  val status: StateFlow<String> = _status.asStateFlow()

  private val _counter = MutableStateFlow(0)
  val counter: StateFlow<Int> = _counter.asStateFlow()

  private var videoJob: Job? = null

  private fun send(label: String, content: ContentScope.() -> Unit) {
    val display = manager.display.value ?: run { _status.value = "Display not attached"; return }
    viewModelScope.launch(Dispatchers.IO) {
      display.sendContent(content)
          .onSuccess { _status.value = "Sent: $label" }
          .onFailure { error, _ -> _status.value = "Send failed: ${error.description}" }
    }
  }

  /** Demo 1 — text styles, secondary colors, card backgrounds. */
  fun sendTypographyCard() {
    send("typography") {
      flexBox(direction = Direction.COLUMN, gap = 12) {
        flexBox(padding = 24, background = FlexBoxBackground.CARD) {
          text("DAT Playground", style = TextStyle.HEADING)
          text("Text styles on the glasses display", style = TextStyle.BODY)
          text("META style · secondary color", style = TextStyle.META, color = TextColor.SECONDARY)
        }
      }
    }
  }

  /** Demo 2 — built-in icons and row layout with alignment. */
  fun sendIconRow() {
    send("icons") {
      flexBox(direction = Direction.COLUMN, gap = 12, padding = 24) {
        text("Built-in icons", style = TextStyle.HEADING)
        flexBox(
            direction = Direction.ROW,
            gap = 16,
            alignment = Alignment.CENTER,
            crossAlignment = Alignment.CENTER,
        ) {
          icon(name = IconName.GEAR, style = IconStyle.FILLED)
          icon(name = IconName.CHECKMARK, style = IconStyle.FILLED)
          icon(name = IconName.VIDEO_CAMERA, style = IconStyle.FILLED)
          icon(name = IconName.TRIANGLE_RIGHT_VERTICAL_LINE, style = IconStyle.FILLED)
        }
        text("IconName enum only — no custom icon strings", style = TextStyle.META)
      }
    }
  }

  /** Demo 3 — a locally generated Bitmap next to a remote HTTPS image. */
  fun sendImages() {
    val bitmap = makeGradientBitmap()
    send("images") {
      flexBox(direction = Direction.COLUMN, gap = 12, padding = 24) {
        text("Images", style = TextStyle.HEADING)
        flexBox(direction = Direction.ROW, gap = 12) {
          flexBox(direction = Direction.COLUMN, flexGrow = 1f) {
            image(bitmap = bitmap, sizePreset = ImageSize.FILL, cornerRadius = CornerRadius.MEDIUM)
            text("local Bitmap", style = TextStyle.META)
          }
          flexBox(direction = Direction.COLUMN, flexGrow = 1f) {
            image(
                uri = "https://www.facebook.com/assets/wearables_dat_display/tire.png",
                sizePreset = ImageSize.FILL,
                cornerRadius = CornerRadius.MEDIUM,
            )
            text("remote URL", style = TextStyle.META)
          }
        }
      }
    }
  }

  /**
   * Demo 4 — interactivity. Button taps on the glasses run these lambdas on
   * the phone; we update state and push a fresh screen back. That round trip
   * IS the interaction model: the glasses render, the phone owns the logic.
   */
  fun sendCounter() {
    val value = _counter.value
    send("counter($value)") {
      flexBox(direction = Direction.COLUMN, gap = 12) {
        flexBox(
            padding = 24,
            background = FlexBoxBackground.CARD,
            onClick = { onCounterChanged(0) }, // whole card tap resets
        ) {
          text("Counter", style = TextStyle.HEADING)
          text("$value", style = TextStyle.BODY)
          text("tap card to reset", style = TextStyle.META, color = TextColor.SECONDARY)
        }
        flexBox(
            direction = Direction.ROW,
            gap = 8,
            alignment = Alignment.CENTER,
            crossAlignment = Alignment.CENTER,
        ) {
          buttonGroup {
            button(
                label = "-1",
                style = ButtonStyle.SECONDARY,
                onClick = { onCounterChanged(value - 1) },
            )
            button(
                label = "+1",
                style = ButtonStyle.PRIMARY,
                iconName = IconName.CHECKMARK,
                onClick = { onCounterChanged(value + 1) },
            )
          }
        }
      }
    }
  }

  private fun onCounterChanged(newValue: Int) {
    _counter.value = newValue
    sendCounter() // re-render the glasses screen with the new value
  }

  /** Demo 5 — video playback with player state observation. */
  fun sendVideo() {
    val display: Display = manager.display.value
        ?: run { _status.value = "Display not attached"; return }
    videoJob?.cancel()
    videoJob =
        viewModelScope.launch(Dispatchers.IO) {
          val player =
              VideoPlayer(
                  source = VideoSource.Url(TUTORIAL_VIDEO_URL),
                  codec = VideoCodec.MP4,
              )
          launch {
            player.state.collect { state ->
              if (state == VideoPlayerState.ENDED) {
                _status.value = "Video ended"
                sendTypographyCard() // move on to the next screen
              }
            }
          }
          launch {
            player.error.collect { error ->
              if (error != null) _status.value = "Video error: ${error.description}"
            }
          }
          display.sendContent { video(player = player) }
              .onSuccess {
                _status.value = "Video playing"
                player.play()
              }
              .onFailure { error, _ -> _status.value = "Send failed: ${error.description}" }
        }
  }

  /** Demo 6 — clear whatever is on the display. */
  fun clear() {
    val display = manager.display.value ?: return
    viewModelScope.launch(Dispatchers.IO) {
      display.clearDisplay()
      _status.value = "Display cleared"
    }
  }

  private fun makeGradientBitmap(): Bitmap {
    val size = 200
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint()
    paint.shader =
        LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            Color.rgb(0, 212, 255), Color.rgb(0, 40, 80),
            Shader.TileMode.CLAMP,
        )
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
    return bitmap
  }

  override fun onCleared() {
    videoJob?.cancel()
  }

  private companion object {
    // Small MP4 published by Meta for the official DisplayAccess sample.
    const val TUTORIAL_VIDEO_URL =
        "https://github.com/facebook/meta-wearables-dat-android/raw/refs/heads/assets/video_266x150_faststart.mp4"
  }
}
