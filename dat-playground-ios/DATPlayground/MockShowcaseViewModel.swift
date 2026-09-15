//
// MockShowcaseViewModel.swift
//
// MockDeviceKit showcase — the whole DAT stack without physical glasses:
// fake registration, a simulated paired device, and glasses lifecycle events
// (power / fold / don). Once "worn", the mock device shows up in the Connect
// tab exactly like real hardware.
//

import MWDATMockDevice
import Observation

@Observable
@MainActor
final class MockShowcaseViewModel {
  var isEnabled: Bool = false
  var isPaired: Bool = false
  var lifecycle: String = "—"
  var message: String?

  @ObservationIgnored private var device: MockGlasses?

  /// Fakes registration and connectivity (auto-initializes Wearables if needed).
  func enable() {
    MockDeviceKit.shared.enable()
    isEnabled = true
    message = "Mock environment enabled"
  }

  func disable() {
    MockDeviceKit.shared.disable()
    device = nil
    isEnabled = false
    isPaired = false
    lifecycle = "—"
    message = "Mock environment disabled"
  }

  func pairRayBanMeta() {
    do {
      device = try MockDeviceKit.shared.pairGlasses(model: .rayBanMeta)
      isPaired = true
      message = "Paired simulated Ray-Ban Meta"
    } catch {
      message = "Pair failed: \(error.localizedDescription)"
    }
  }

  /// Simulate wearing the glasses: power on, unfold, put on.
  func wear() {
    guard let device else { return }
    device.powerOn()
    device.unfold()
    device.don()
    lifecycle = "powered on · unfolded · worn"
  }

  /// Simulate taking them off and folding them.
  func takeOff() {
    guard let device else { return }
    device.doff()
    device.fold()
    device.powerOff()
    lifecycle = "off · folded · powered off"
  }
}
