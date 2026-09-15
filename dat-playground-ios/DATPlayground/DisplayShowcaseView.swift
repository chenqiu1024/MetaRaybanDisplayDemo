//
// DisplayShowcaseView.swift — buttons that push each showcase screen to the
// glasses. All Display DSL code lives in ShowcaseDisplays.swift; this file
// stays SwiftUI-only to avoid Text/Button/Image name clashes.
//

import MWDATCore
import SwiftUI

struct DisplayShowcaseView: View {
  @Bindable var viewModel: DisplayShowcaseViewModel
  @Bindable var sessionManager: SessionManager

  private var ready: Bool {
    sessionManager.sessionState == .started && sessionManager.displayState == .started
  }

  var body: some View {
    List {
      Section("Capability") {
        LabeledContent("Display", value: String(describing: sessionManager.displayState))
        LabeledContent("Status", value: viewModel.status)
        Button("Attach display") { sessionManager.attachDisplay() }
          .disabled(sessionManager.sessionState != .started)
        Button("Detach", role: .destructive) { sessionManager.detachDisplay() }
      }

      Section("Send to glasses") {
        Button("1 · Typography card") { Task { await viewModel.sendTypographyCard() } }
        Button("2 · Icon gallery") { Task { await viewModel.sendIconRow() } }
        Button("3 · UIImage + URL images") { Task { await viewModel.sendImages() } }
        Button("4 · Interactive counter (now: \(viewModel.counter))") {
          Task { await viewModel.sendCounter() }
        }
        Button("5 · Video playback") { Task { await viewModel.sendVideo() } }
        Button("Clear display") { Task { await viewModel.clear() } }
      }
      .disabled(!ready)
    }
    .navigationTitle("Display")
  }
}
