//
// MockShowcaseView.swift — MockDeviceKit controls for no-hardware development.
//

import SwiftUI

struct MockShowcaseView: View {
  @Bindable var viewModel: MockShowcaseViewModel

  var body: some View {
    List {
      Section("Mock environment") {
        LabeledContent("Enabled", value: viewModel.isEnabled ? "yes" : "no")
        LabeledContent("Lifecycle", value: viewModel.lifecycle)
        Button("Enable") { viewModel.enable() }
        Button("Disable", role: .destructive) { viewModel.disable() }
      }

      Section("Simulated glasses") {
        Button("Pair Ray-Ban Meta") { viewModel.pairRayBanMeta() }
          .disabled(!viewModel.isEnabled || viewModel.isPaired)
        Button("Power on + wear") { viewModel.wear() }
          .disabled(!viewModel.isPaired)
        Button("Take off") { viewModel.takeOff() }
          .disabled(!viewModel.isPaired)
      }

      Section {
        Text(
          "Once worn, the simulated device appears in the Connect tab like real "
            + "hardware — start a session there and drive the Display/Camera tabs "
            + "against it."
        )
        .font(.footnote)
        .foregroundStyle(.secondary)
      }
    }
    .navigationTitle("Mock")
    .alert(
      "MockDeviceKit",
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
