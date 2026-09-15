//
// ShowcaseDisplays.swift
//
// Everything the Display DSL can put on the glasses, as reusable builders:
// text styles, icons, layout, local UIImage + remote URL images, and an
// interactive counter whose taps are routed back to the phone.
//
// NOTE: this file deliberately does NOT import SwiftUI — the Display DSL
// reuses the names Text / Button / Image, and mixing the two imports makes
// every one of them ambiguous.
//

import MWDATDisplay
import UIKit

enum ShowcaseDisplays {

  /// Demo 1 — text styles, secondary colors, card background.
  static func typographyCard() -> FlexBox {
    FlexBox(direction: .column, spacing: 12) {
      FlexBox(direction: .column) {
        Text("DAT Playground", style: .heading)
        Text("Text styles on the glasses display", style: .body)
        Text("meta style · secondary color", style: .meta, color: .secondary)
      }
      .padding(24)
      .background(.card)
    }
  }

  /// Demo 2 — built-in icons and row layout with alignment.
  static func iconRow() -> FlexBox {
    FlexBox(direction: .column, spacing: 12) {
      FlexBox(direction: .column) {
        Text("Built-in icons", style: .heading)
        FlexBox(direction: .row, spacing: 16, alignment: .center, crossAlignment: .center) {
          Icon(name: .gear, style: .filled)
          Icon(name: .checkmark, style: .filled)
          Icon(name: .videoCamera, style: .filled)
          Icon(name: .triangleRightVerticalLine, style: .filled)
        }
        Text("IconName enum only — no custom icon strings", style: .meta, color: .secondary)
      }
      .padding(24)
      .background(.card)
    }
  }

  /// Demo 3 — a locally generated UIImage next to a remote HTTPS image.
  static func imagesCard() -> FlexBox {
    FlexBox(direction: .column, spacing: 12) {
      FlexBox(direction: .column) {
        Text("Images", style: .heading)
        FlexBox(direction: .row, spacing: 12) {
          FlexBox(direction: .column) {
            Image(image: makeGradientImage(), sizePreset: .fill, cornerRadius: .medium)
            Text("local UIImage", style: .meta, color: .secondary)
          }
          .flexGrow(1)
          FlexBox(direction: .column) {
            Image(
              uri: "https://www.facebook.com/assets/wearables_dat_display/tire.png",
              sizePreset: .fill,
              cornerRadius: .medium
            )
            Text("remote URL", style: .meta, color: .secondary)
          }
          .flexGrow(1)
        }
      }
      .padding(24)
      .background(.card)
    }
  }

  /// Demo 4 — interactivity. Button taps on the glasses run these closures on
  /// the phone; the caller updates its state and pushes a fresh screen back.
  /// That round trip IS the interaction model.
  static func counterCard(
    value: Int,
    onMinus: @escaping @Sendable () -> Void,
    onPlus: @escaping @Sendable () -> Void,
    onReset: @escaping @Sendable () -> Void
  ) -> FlexBox {
    FlexBox(direction: .column, spacing: 12) {
      FlexBox(direction: .column) {
        Text("Counter", style: .heading)
        Text("\(value)", style: .body)
        Text("tap card to reset", style: .meta, color: .secondary)
      }
      .padding(24)
      .background(.card)
      .onTap(onReset)

      FlexBox(direction: .row, spacing: 8, alignment: .center, crossAlignment: .center) {
        ButtonGroup {
          Button(label: "-1", style: .secondary, onClick: onMinus)
          Button(label: "+1", style: .primary, iconName: .checkmark, onClick: onPlus)
        }
      }
    }
  }

  /// A cyan gradient rendered on the phone and shipped to the glasses as a bitmap.
  private static func makeGradientImage() -> UIImage {
    let size = CGSize(width: 200, height: 200)
    let renderer = UIGraphicsImageRenderer(size: size)
    return renderer.image { context in
      let colors = [
        UIColor(red: 0, green: 0.83, blue: 1, alpha: 1).cgColor,
        UIColor(red: 0, green: 0.16, blue: 0.31, alpha: 1).cgColor,
      ]
      let gradient = CGGradient(
        colorsSpace: CGColorSpaceCreateDeviceRGB(),
        colors: colors as CFArray,
        locations: [0, 1]
      )!
      context.cgContext.drawLinearGradient(
        gradient,
        start: .zero,
        end: CGPoint(x: size.width, y: size.height),
        options: []
      )
    }
  }
}
