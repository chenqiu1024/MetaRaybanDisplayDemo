# DAT Playground (iOS)

路线 A 原生 Demo 的 **iOS 版**：Swift + SwiftUI 手机 App，通过
**Meta Wearables Device Access Toolkit v0.9.0** 展示与 Android 版相同的四类能力。

> 姊妹项目：`dat-playground`（Android 版）、`mrbd-playground`（路线 B Web 版）。

## 四个 Tab = 四类能力（与 Android 版一一对应）

| Tab | 展示的能力 |
|-----|-----------|
| **Connect** | `Wearables.configure()` / `startRegistration()`（跳 Meta AI 注册）、`devicesStream()` 设备列表（连接状态 / 固件兼容性 / 是否带屏）、`createSession` → `stateStream()` 会话生命周期 |
| **Display** | `session.addDisplay()` → 眼镜屏显 DSL：文字样式卡片、内置图标、本地 UIImage + 远程 URL 图片、**交互计数器**（眼镜按钮点击回调回手机再重发内容）、MP4 视频播放、`clearDisplay()` |
| **Camera** | `session.addCamera(config:)`：分辨率（low/medium/high）× 帧率（2/7/15/24/30）可选；`videoCodec: .raw` 时每帧 `makeUIImage()` 直接出图（**比 Android 省掉了手写 HEVC 解码器**）；`capturePhoto(format: .jpeg)` 拍照 |
| **Mock** | `MockDeviceKit`：无真机测试——伪造注册、配对模拟眼镜、模拟 佩戴/摘下/开关机 生命周期 |

## 用 Xcode 打开与构建

比 Android 版省事：**SDK 走 Swift Package Manager，不需要任何 GitHub Token**。

1. 需要 macOS + Xcode 15 以上（部署目标 iOS 17.2，与官方示例一致）
2. 双击 `DATPlayground.xcodeproj` 打开，首次打开时 Xcode 会自动解析
   `meta-wearables-dat-ios` 0.9.0 包（联网下载，无需凭据）
3. Signing & Capabilities 里选择你自己的 Apple ID / Team（免费个人账号即可真机调试）
4. 选一台 iOS 17.2+ 的 iPhone 运行

运行前提：手机装 Meta AI App 并在其中开启 Developer Mode（`MetaAppID = 0` 即为
Developer Mode 配置，已写在 Info.plist）；没有眼镜就先进 **Mock** tab：
Enable → Pair → Power on + wear，然后回 Connect tab 对模拟设备开 Session。

## 与 Android 版的主要差异（学习笔记视角）

- **分发**：SPM 直装 vs GitHub Packages + PAT——iOS 无门槛
- **状态流**：iOS 是 `AsyncStream`（`for await`）+ `AnyListenerToken` 监听器；Android 是 Kotlin Flow
- **相机帧**：iOS 可选 `.raw` 直接 `makeUIImage()`；Android 只有压缩 HEVC，要自己上 MediaCodec
- **Display DSL**：同一套概念，Swift 侧是 result-builder 风格
  （`FlexBox { Text(...) }`，修饰符 `.padding().background(.card).onTap {}`）；
  注意 `Text/Button/Image` 与 SwiftUI 重名——**DSL 代码放在不 import SwiftUI 的文件里**
  （见 `ShowcaseDisplays.swift`）
- **回调 URL**：iOS 注册流程需要 URL scheme 回跳（Info.plist 的
  `CFBundleURLTypes` + `MWDAT.AppLinkURLScheme` + `.onOpenURL` → `handleUrl`）

## 验证状态（诚实声明）

本工程在 Linux 沙盒中生成，无法运行 Xcode/Swift 编译器做端到端构建。已做的验证：
所有 SDK 调用点（模块名、类型名、方法签名、枚举 case）逐一对照官方
`meta-wearables-dat-ios` v0.9.0 的 DisplayAccess / CameraAccess 示例与 AI 插件规则核对；
`project.pbxproj` 按官方示例工程同构改写并通过对象引用完整性检查。
首次构建如有小报错，对照官方示例即可修正。

## 参考

- 官方仓库（本项目模式来源，BSD）: https://github.com/facebook/meta-wearables-dat-ios
- iOS API 参考: https://wearables.developer.meta.com/docs/reference/ios_swift/dat/0.9
