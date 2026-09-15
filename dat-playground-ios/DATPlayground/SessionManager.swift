//
// SessionManager.swift
//
// Owns exactly one DeviceSession and the capabilities attached to it.
//
// Lifecycle contract (from the official docs):
//   createSession(deviceSelector:) -> session.start() -> wait for .started
//   -> addDisplay() / addCamera(config:) -> capability.start() -> use
//   -> capability.stop() -> session.stop()
//
// DeviceSessionState is device-driven and never says *why* it changed
// (glasses folded, another experience took over, Bluetooth dropped, ...).
// Everything below keys off the observed state only:
//   .started -> live: capabilities may be attached and used
//   .paused  -> interruption: keep the session, hold work, never restart
//   .stopped -> release every device resource; the user may start again
//

import MWDATCamera
import MWDATCore
import MWDATDisplay
import Observation

@Observable
@MainActor
final class SessionManager {
  var sessionState: DeviceSessionState = .idle
  var displayState: DisplayState = .stopped
  var message: String?

  /// A session may only be (re)started once it is fully inactive — never
  /// while paused, which the SDK treats as a temporary interruption.
  var canStartSession: Bool {
    switch sessionState {
    case .idle, .stopped: true
    case .starting, .started, .paused, .stopping: false
    }
  }

  /// Stopping is allowed while starting, live, or paused so the user can
  /// release the glasses during a long interruption.
  var canStopSession: Bool {
    switch sessionState {
    case .starting, .started, .paused: true
    case .idle, .stopping, .stopped: false
    }
  }

  var isSessionPaused: Bool { sessionState == .paused }

  @ObservationIgnored private let wearables: WearablesInterface
  @ObservationIgnored private(set) var deviceSession: DeviceSession?
  @ObservationIgnored private(set) var display: Display?
  @ObservationIgnored private(set) var camera: Camera?
  @ObservationIgnored private var sessionStateTask: Task<Void, Never>?
  @ObservationIgnored private var sessionErrorTask: Task<Void, Never>?
  @ObservationIgnored private var displayStateToken: AnyListenerToken?

  init(wearables: WearablesInterface) {
    self.wearables = wearables
  }

  // MARK: - Session lifecycle

  /// Creates and starts a session against a user-picked device.
  func startSession(deviceId: DeviceIdentifier) {
    guard deviceSession == nil, canStartSession else {
      if isSessionPaused {
        message = "Session is paused — wait for it to resume, or stop it first"
      } else {
        message = "Session already active"
      }
      return
    }
    do {
      let session = try wearables.createSession(
        deviceSelector: SpecificDeviceSelector(device: deviceId)
      )
      deviceSession = session

      sessionStateTask = Task { [weak self] in
        for await state in session.stateStream() {
          guard let self, !Task.isCancelled else { return }
          self.handleSessionState(state)
        }
      }
      sessionErrorTask = Task { [weak self] in
        for await error in session.errorStream() {
          guard let self, !Task.isCancelled else { return }
          // Errors are informational; stateStream() stays the source of
          // truth for what the session is actually doing.
          self.message = "Session error: \(error.localizedDescription)"
        }
      }

      try session.start()
    } catch DeviceSessionError.datAppOnTheGlassesUpdateRequired {
      message = "The DAT app on the glasses needs an update"
      releaseSession()
      Task { try? await wearables.openDATGlassesAppUpdate() }
    } catch {
      message = "Session failed: \(error.localizedDescription)"
      releaseSession()
    }
  }

  func stopSession() {
    guard let session = deviceSession else { return }
    detachDisplay()
    detachCamera()
    session.stop()  // the .stopped event on stateStream() completes cleanup
  }

  /// Single place that reacts to the device-driven session state. Handles
  /// every DeviceSessionState value and never guesses at the cause.
  private func handleSessionState(_ state: DeviceSessionState) {
    sessionState = state
    switch state {
    case .started:
      // Live, or resumed after a pause. Nothing to restore: capabilities
      // attached before an interruption are kept and pick up on their own.
      break
    case .paused:
      // Interruption. The device keeps the connection alive and streams stop
      // delivering data. Hold work: keep the session and capabilities, do
      // not restart anything, wait for .started or .stopped.
      break
    case .stopped:
      // Inactive and not reconnecting: release every device resource.
      releaseSession()
    case .idle, .starting, .stopping:
      // Transitional; the UI shows them verbatim.
      break
    }
  }

  /// The session, but only while it is live. Sets an explicit message otherwise.
  private func liveSession() -> DeviceSession? {
    guard let session = deviceSession else {
      message = "Start a session first (Connect tab)"
      return nil
    }
    switch sessionState {
    case .started:
      return session
    case .paused:
      message = "Session is paused — waiting for the glasses to resume"
      return nil
    case .idle, .starting, .stopping, .stopped:
      message = "Session is \(sessionState) — wait for it to start"
      return nil
    }
  }

  // MARK: - Display capability

  /// Call only after the session reports .started.
  func attachDisplay() {
    guard let session = liveSession() else { return }
    guard display == nil else { return }
    do {
      let capability = try session.addDisplay()
      display = capability
      displayStateToken = capability.statePublisher.listen { [weak self] state in
        Task { @MainActor [weak self] in
          self?.displayState = state
        }
      }
      capability.start()
    } catch {
      message = "addDisplay failed: \(error.localizedDescription)"
    }
  }

  func detachDisplay() {
    display?.onPlaybackEvent = nil
    display?.stop()
    display = nil
    displayStateToken = nil
    displayState = .stopped
  }

  // MARK: - Camera capability

  func attachCamera(config: StreamConfiguration) -> Camera? {
    guard let session = liveSession() else { return nil }
    if let camera { return camera }
    do {
      guard let newCamera = try session.addCamera(config: config) else {
        message = "addCamera returned nil — session not started?"
        return nil
      }
      camera = newCamera
      return newCamera
    } catch {
      message = "addCamera failed: \(error.localizedDescription)"
      return nil
    }
  }

  func detachCamera() {
    camera?.stop()
    camera = nil
  }

  /// Releases everything tied to the current session: capabilities first
  /// (dropping their listeners and playback handlers), then the observation
  /// tasks and the session handle. Leaves the UI in an explicit .stopped state.
  private func releaseSession() {
    detachDisplay()
    detachCamera()
    sessionStateTask?.cancel()
    sessionErrorTask?.cancel()
    sessionStateTask = nil
    sessionErrorTask = nil
    deviceSession = nil
    sessionState = .stopped
  }
}
