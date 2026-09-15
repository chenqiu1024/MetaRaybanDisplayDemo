//
// ConnectView.swift — registration, device list, session lifecycle.
//

import MWDATCore
import SwiftUI

struct ConnectView: View {
  @Bindable var wearablesViewModel: WearablesViewModel
  @Bindable var sessionManager: SessionManager

  var body: some View {
    List {
      Section("Registration") {
        LabeledContent("State", value: String(describing: wearablesViewModel.registrationState))
        Button("Register with Meta AI") {
          Task { await wearablesViewModel.register() }
        }
        Button("Unregister", role: .destructive) {
          Task { await wearablesViewModel.unregister() }
        }
      }

      Section("Session") {
        LabeledContent("State", value: String(describing: sessionManager.sessionState))
        if sessionManager.isSessionPaused {
          Text("Session paused — holding work until it resumes or stops.")
            .font(.footnote)
            .foregroundStyle(.secondary)
        }
        Button("Stop session", role: .destructive) {
          sessionManager.stopSession()
        }
        .disabled(!sessionManager.canStopSession)
      }

      Section("Devices") {
        if wearablesViewModel.deviceItemStates.isEmpty {
          Text("No devices yet — register first, or pair a simulated device in the Mock tab.")
            .foregroundStyle(.secondary)
        }
        ForEach(wearablesViewModel.deviceItemStates) { item in
          VStack(alignment: .leading, spacing: 4) {
            Text(item.deviceName).font(.headline)
            Text(item.deviceTypeValue).font(.caption)
            Text(
              "link: \(String(describing: item.linkState)) · display: \(item.supportsDisplay ? "yes" : "no")"
            )
            .font(.caption)
            .foregroundStyle(.secondary)
            if item.compatibility == .deviceUpdateRequired {
              Button("Firmware update required — open updater") {
                wearablesViewModel.openFirmwareUpdate()
              }
              .font(.caption)
            }
            Button("Start session") {
              sessionManager.startSession(deviceId: item.identifier)
            }
            .disabled(item.linkState != .connected || !sessionManager.canStartSession)
          }
        }
      }
    }
    .navigationTitle("Connect")
    .alert(
      "Error",
      isPresented: Binding(
        get: { wearablesViewModel.errorMessage != nil },
        set: { if !$0 { wearablesViewModel.errorMessage = nil } }
      )
    ) {
      Button("OK") { wearablesViewModel.errorMessage = nil }
    } message: {
      Text(wearablesViewModel.errorMessage ?? "")
    }
    .alert(
      "Session",
      isPresented: Binding(
        get: { sessionManager.message != nil },
        set: { if !$0 { sessionManager.message = nil } }
      )
    ) {
      Button("OK") { sessionManager.message = nil }
    } message: {
      Text(sessionManager.message ?? "")
    }
  }
}
