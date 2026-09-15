//
// DisplayShowcaseViewModel.swift
//
// Sends the showcase screens to the glasses through the shared SessionManager.
// Every send(_:) replaces the whole screen on the glasses — one root view per
// send is the core mental model.
//

import MWDATCore
import MWDATDisplay
import Observation

@Observable
@MainActor
final class DisplayShowcaseViewModel {
  var status: String = "Idle"
  var counter: Int = 0

  @ObservationIgnored private let sessionManager: SessionManager

  init(sessionManager: SessionManager) {
    self.sessionManager = sessionManager
  }

  /// The display, but only while both the session and the capability are
  /// live. Sets an explicit status otherwise — never sends during a pause.
  private func liveDisplay() -> Display? {
    guard let display = sessionManager.display else {
      status = "Display not attached"
      return nil
    }
    guard sessionManager.sessionState == .started else {
      status = "Session \(sessionManager.sessionState) — holding"
      return nil
    }
    guard sessionManager.displayState == .started else {
      status = "Display \(sessionManager.displayState) — not ready"
      return nil
    }
    return display
  }

  private func send(_ label: String, _ view: some DisplayableView) async {
    guard let display = liveDisplay() else { return }
    do {
      try await display.send(view)
      status = "Sent: \(label)"
    } catch {
      status = (error as? DisplayError)?.description ?? error.localizedDescription
    }
  }

  func sendTypographyCard() async {
    await send("typography", ShowcaseDisplays.typographyCard())
  }

  func sendIconRow() async {
    await send("icons", ShowcaseDisplays.iconRow())
  }

  func sendImages() async {
    await send("images", ShowcaseDisplays.imagesCard())
  }

  func sendCounter() async {
    let value = counter
    await send(
      "counter(\(value))",
      ShowcaseDisplays.counterCard(
        value: value,
        onMinus: { [weak self] in
          Task { @MainActor in await self?.setCounter(value - 1) }
        },
        onPlus: { [weak self] in
          Task { @MainActor in await self?.setCounter(value + 1) }
        },
        onReset: { [weak self] in
          Task { @MainActor in await self?.setCounter(0) }
        }
      )
    )
  }

  private func setCounter(_ newValue: Int) async {
    counter = newValue
    await sendCounter() // re-render the glasses screen with the new value
  }

  /// Video playback with player-state observation. On .ended we move to the
  /// typography card, mirroring the official sample's flow.
  func sendVideo() async {
    guard let display = liveDisplay() else { return }

    display.onPlaybackEvent = { [weak self] event in
      if event.type == .ended || event.type == .stopped {
        Task { @MainActor [weak self] in
          self?.sessionManager.display?.onPlaybackEvent = nil
          await self?.sendTypographyCard()
        }
      }
    }

    do {
      try await display.send(
        VideoPlayer(
          provider: .uri(Self.tutorialVideoUrl),
          codec: .mp4,
          onError: { [weak self] error in
            Task { @MainActor [weak self] in
              self?.status = "Video error: \(error.localizedDescription)"
            }
          }
        )
      )
      status = "Video playing"
    } catch {
      status = (error as? DisplayError)?.description ?? error.localizedDescription
    }
  }

  func clear() async {
    guard let display = liveDisplay() else { return }
    do {
      try await display.clearDisplay()
      status = "Display cleared"
    } catch {
      status = "Clear failed: \(error.localizedDescription)"
    }
  }

  // Small MP4 published by Meta for the official DisplayAccess samples.
  private static let tutorialVideoUrl =
    "https://github.com/facebook/meta-wearables-dat-android/raw/refs/heads/assets/video_266x150_faststart.mp4"
}
