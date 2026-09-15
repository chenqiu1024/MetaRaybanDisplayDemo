//
// DATPlaygroundApp.swift
//
// DAT Playground (iOS) — a portfolio-style demo of the Meta Wearables Device
// Access Toolkit v0.9.0: registration, device sessions, the glasses Display
// DSL, camera streaming / photo capture, and MockDeviceKit.
//
// Patterns follow Meta's official iOS samples (BSD license):
// https://github.com/facebook/meta-wearables-dat-ios
//

import MWDATCore
import SwiftUI

@main
struct DATPlaygroundApp: App {
  @State private var wearablesViewModel: WearablesViewModel
  @State private var sessionManager: SessionManager
  @State private var displayShowcaseViewModel: DisplayShowcaseViewModel
  @State private var cameraShowcaseViewModel: CameraShowcaseViewModel
  @State private var mockShowcaseViewModel = MockShowcaseViewModel()

  init() {
    do {
      try Wearables.configure()
    } catch {
      NSLog("[DATPlayground] Failed to configure Wearables SDK: \(error)")
    }
    let wearables = Wearables.shared
    let manager = SessionManager(wearables: wearables)
    self._wearablesViewModel = State(wrappedValue: WearablesViewModel(wearables: wearables))
    self._sessionManager = State(wrappedValue: manager)
    self._displayShowcaseViewModel =
      State(wrappedValue: DisplayShowcaseViewModel(sessionManager: manager))
    self._cameraShowcaseViewModel =
      State(wrappedValue: CameraShowcaseViewModel(sessionManager: manager))
  }

  var body: some Scene {
    WindowGroup {
      TabView {
        NavigationStack {
          ConnectView(wearablesViewModel: wearablesViewModel, sessionManager: sessionManager)
        }
        .tabItem { Label("Connect", systemImage: "personalhotspot") }

        NavigationStack {
          DisplayShowcaseView(
            viewModel: displayShowcaseViewModel,
            sessionManager: sessionManager
          )
        }
        .tabItem { Label("Display", systemImage: "eyeglasses") }

        NavigationStack {
          CameraShowcaseView(viewModel: cameraShowcaseViewModel)
        }
        .tabItem { Label("Camera", systemImage: "camera") }

        NavigationStack {
          MockShowcaseView(viewModel: mockShowcaseViewModel)
        }
        .tabItem { Label("Mock", systemImage: "wand.and.stars") }
      }
      // Meta AI calls back into the app after registration — route the URL
      // to the SDK or registration never completes.
      .onOpenURL { url in
        Task {
          _ = try? await Wearables.shared.handleUrl(url)
        }
      }
    }
  }
}
