//
// WearablesViewModel.swift
//
// SDK-level state that exists independent of any session: registration with
// Meta AI and the list of known devices with live link-state metadata.
// Adapted from the official DisplayAccess sample (BSD license).
//

import MWDATCore
import Observation
import SwiftUI

@Observable
@MainActor
final class DeviceItemState: Identifiable {
  let identifier: DeviceIdentifier
  var deviceName: String
  var deviceTypeValue: String
  var linkState: LinkState
  var compatibility: Compatibility
  var supportsDisplay: Bool

  @ObservationIgnored private var linkStateToken: AnyListenerToken?

  nonisolated var id: DeviceIdentifier { identifier }

  init(device: Device) {
    self.identifier = device.identifier
    self.deviceName = device.nameOrId()
    self.deviceTypeValue = device.deviceType().rawValue
    self.linkState = device.linkState
    self.compatibility = device.compatibility()
    self.supportsDisplay = device.supportsDisplay()

    linkStateToken = device.addLinkStateListener { [weak self] _ in
      Task { @MainActor [weak self] in
        guard let self else { return }
        self.linkState = device.linkState
        self.compatibility = device.compatibility()
        self.deviceName = device.nameOrId()
      }
    }
  }
}

@Observable
@MainActor
final class WearablesViewModel {
  var deviceItemStates: [DeviceItemState] = []
  var registrationState: RegistrationState
  var errorMessage: String?

  @ObservationIgnored private var registrationTask: Task<Void, Never>?
  @ObservationIgnored private var deviceStreamTask: Task<Void, Never>?
  let wearables: WearablesInterface

  init(wearables: WearablesInterface) {
    self.wearables = wearables
    self.registrationState = wearables.registrationState

    deviceStreamTask = Task { [weak self] in
      guard let wearables = self?.wearables else { return }
      for await deviceIds in wearables.devicesStream() {
        guard let self else { return }
        self.deviceItemStates = deviceIds.compactMap { deviceId in
          guard let device = wearables.deviceForIdentifier(deviceId) else { return nil }
          return DeviceItemState(device: device)
        }
      }
    }

    registrationTask = Task { [weak self] in
      guard let wearables = self?.wearables else { return }
      for await state in wearables.registrationStateStream() {
        guard let self else { return }
        self.registrationState = state
      }
    }
  }

  /// Opens the Meta AI flow that connects this app to the user's glasses.
  func register() async {
    guard registrationState != .registering else { return }
    do {
      try await wearables.startRegistration()
    } catch {
      errorMessage = error.localizedDescription
    }
  }

  func unregister() async {
    do {
      try await wearables.startUnregistration()
    } catch {
      errorMessage = error.localizedDescription
    }
  }

  /// Firmware update flow — offer when a device reports .deviceUpdateRequired.
  func openFirmwareUpdate() {
    Task {
      try? await wearables.openFirmwareUpdate()
    }
  }
}
