//
// CameraShowcaseView.swift — stream configuration, live preview, photo capture.
//

import MWDATCamera
import SwiftUI

struct CameraShowcaseView: View {
  @Bindable var viewModel: CameraShowcaseViewModel

  var body: some View {
    List {
      Section("Stream") {
        LabeledContent("State", value: String(describing: viewModel.streamState))
        LabeledContent("Frames", value: "\(viewModel.frameCount)")

        Picker("Resolution", selection: $viewModel.resolution) {
          Text("low 360×640").tag(StreamingResolution.low)
          Text("medium 504×896").tag(StreamingResolution.medium)
          Text("high 720×1280").tag(StreamingResolution.high)
        }
        Picker("Frame rate", selection: $viewModel.frameRate) {
          ForEach([2, 7, 15, 24, 30], id: \.self) { fps in
            Text("\(fps) fps").tag(UInt(fps))
          }
        }

        Button(viewModel.isPreparing ? "Checking camera permission…" : "Start stream") {
          Task { await viewModel.startStreaming() }
        }
        .disabled(viewModel.streamState != .stopped || viewModel.isPreparing)
        Button("Capture photo") { viewModel.capturePhoto() }
          .disabled(viewModel.streamState != .streaming)
        Button("Stop", role: .destructive) { viewModel.stopStreaming() }
      }

      Section("Live preview") {
        if let image = viewModel.previewImage {
          Image(uiImage: image)
            .resizable()
            .scaledToFit()
        } else {
          Text("No frames yet — start a session (Connect tab), then start the stream.")
            .foregroundStyle(.secondary)
        }
      }

      if let photo = viewModel.capturedPhoto {
        Section("Captured photo") {
          Image(uiImage: photo)
            .resizable()
            .scaledToFit()
        }
      }
    }
    .navigationTitle("Camera")
    .alert(
      "Camera",
      isPresented: Binding(
        get: { viewModel.message != nil },
        set: { if !$0 { viewModel.message = nil } }
      )
    ) {
      Button("OK") { viewModel.message = nil }
    } message: {
      Text(viewModel.message ?? "")
    }
  }
}
