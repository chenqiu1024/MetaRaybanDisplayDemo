//
// CameraShowcaseViewModel.swift
//
// Camera capability showcase: configure the stream (resolution / frame rate),
// watch StreamState transitions, preview frames, and capture a still photo.
//
// iOS niceness vs Android: with videoCodec .raw every VideoFrame can be turned
// into a UIImage with makeUIImage() — no manual HEVC/MediaCodec decoding.
//

import MWDATCamera
import MWDATCore
import Observation
import UIKit

@Observable
@MainActor
final class CameraShowcaseViewModel {
  var streamState: StreamState = .stopped
  var resolution: StreamingResolution = .medium
  var frameRate: UInt = 24
  var frameCount: Int = 0
  var previewImage: UIImage?
  var capturedPhoto: UIImage?
  var message: String?

  @ObservationIgnored private let sessionManager: SessionManager
  @ObservationIgnored private var stateToken: AnyListenerToken?
  @ObservationIgnored private var frameToken: AnyListenerToken?
  @ObservationIgnored private var photoToken: AnyListenerToken?

  init(sessionManager: SessionManager) {
    self.sessionManager = sessionManager
  }

  func startStreaming() {
    // Never (re)start while the stream is paused or still winding down —
    // the device decides when a paused stream resumes.
    guard streamState == .stopped else {
      message = "Stream is \(streamState) — wait for it to stop before starting again"
      return
    }
    let config = StreamConfiguration(
      videoCodec: .raw, // raw frames -> frame.makeUIImage() just works
      resolution: resolution,
      frameRate: frameRate
    )
    guard let camera = sessionManager.attachCamera(config: config) else { return }
    let stream = camera.stream

    stateToken = stream.statePublisher.listen { [weak self] state in
      Task { @MainActor [weak self] in
        self?.streamState = state
      }
    }
    frameToken = stream.videoFramePublisher.listen { [weak self] frame in
      guard let image = frame.makeUIImage() else { return }
      Task { @MainActor [weak self] in
        guard let self else { return }
        self.previewImage = image
        self.frameCount += 1
      }
    }
    photoToken = stream.photoDataPublisher.listen { [weak self] photoData in
      let image = UIImage(data: photoData.data)
      Task { @MainActor [weak self] in
        guard let self else { return }
        self.capturedPhoto = image
        self.message = image == nil ? "Photo decode failed" : "Photo captured"
      }
    }

    stream.start()
  }

  func capturePhoto() {
    guard let camera = sessionManager.camera else { return }
    // Result arrives asynchronously through photoDataPublisher.
    if !camera.stream.capturePhoto(format: .jpeg) {
      message = "capturePhoto refused — is the stream running?"
    }
  }

  func stopStreaming() {
    stateToken = nil
    frameToken = nil
    photoToken = nil
    sessionManager.detachCamera()
    streamState = .stopped
    frameCount = 0
    previewImage = nil
  }
}
