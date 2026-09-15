# DAT Playground

路线 A 的 portfolio 式最小 Demo：一个 Android（Kotlin + Compose）手机 App，
通过 **Meta Wearables Device Access Toolkit v0.9.0** 展示原生 SDK 的全部四类能力。

> 姊妹项目：`mrbd-playground`（路线 B，跑在眼镜 600×600 屏上的 Web App）。

## 四个 Tab = 四类能力

| Tab | 展示的能力 |
|-----|-----------|
| **Connect** | `Wearables.initialize` / `startRegistration`（跳 Meta AI 注册）、`Wearables.devices` + `devicesMetadata` 设备列表（连接状态 / 固件兼容性 / 是否带屏）、`createSession` → `DeviceSessionState` 生命周期 |
| **Display** | `session.addDisplay()` → 眼镜屏显 DSL：文字样式卡片、内置图标、本地 Bitmap + 远程 URL 图片、**交互计数器**（眼镜上的按钮点击回调回手机，手机重发内容）、MP4 视频播放、`clearDisplay()` |
| **Camera** | `session.addCamera(StreamConfiguration)`：分辨率（LOW/MEDIUM/HIGH）× 帧率（2/7/15/24/30）可选，接收**压缩 HEVC 帧流**并用 MediaCodec 解码到 Surface 预览，`capturePhoto()` 拍照 |
| **Mock** | `MockDeviceKit`：无真机测试——伪造注册、配对 5 种眼镜型号、模拟 佩戴/摘下/开关机 生命周期 |

## 用 Android Studio 打开与构建

工程是标准 Gradle 结构（含 wrapper，Gradle 8.14.1 / AGP 8.11.1 / Kotlin 2.2.21，需要 JDK 17+，
Android Studio 直接 **Open** 本目录即可）。构建前只差一步：

1. **必须提供 GitHub Token**——mwdat SDK 从 GitHub Packages 分发，匿名无法拉取。
   任何 GitHub 账号的 PAT（classic，勾选 `read:packages` 权限）都行：
   在工程根目录建 `local.properties`，加一行 `github_token=ghp_xxx`
   （或导出环境变量 `GITHUB_TOKEN`）。没有这一步，Gradle sync 会在解析
   `com.meta.wearable:mwdat-*` 时报 401。
2. Developer Mode 下 `APPLICATION_ID`/`CLIENT_TOKEN` 保持 `0` 即可（手机 Meta AI App 里开启
   Developer Mode）；上生产才需要 Wearables Developer Center 的真实凭据
3. 命令行：`./gradlew :app:assembleDebug`

运行：手机需装 Meta AI App；没有眼镜就先进 **Mock** tab：Enable → Pair → Power on + wear，
然后回 Connect tab 对模拟设备开 Session。

### 验证状态（诚实声明）

本工程在无法访问 Google Maven / GitHub Packages 的沙盒环境中生成，因此**未经过端到端编译**。
已做的验证：全部 Kotlin/KTS 通过 ktlint 解析；39 个 `com.meta.wearable.*` import 与官方
示例逐一比对全部命中；所有 SDK 调用点的参数形态（`StreamConfiguration`、Display DSL、
`AndroidExternalSurface` 等）与官方 DisplayAccess / CameraAccess 示例（v0.9.0）逐一核对一致；
Gradle 配置与 wrapper 直接取自官方示例。首次 sync/build 如遇小的编译报错，多半是 SDK
小版本 API 变动，对照官方示例即可修正。

## 关键 API 流程（本项目的主线）

```
Wearables.initialize(context)                    // Application.onCreate，一次
Wearables.startRegistration(activity)            // 跳 Meta AI，用户授权
Wearables.registrationState == REGISTERED        // 等注册完成
Wearables.createSession(SpecificDeviceSelector)  // 选设备建会话
session.start() → DeviceSessionState.STARTED     // 等会话就绪
session.addDisplay() → DisplayState.STARTED      // 挂显示能力
display.sendContent { flexBox { text(...) } }    // DSL 发一屏内容
session.addCamera(config); camera.stream.start() // 挂相机能力
...
session.removeDisplay(); camera.stop(); session.stop()
```

## 参考

- 官方仓库（本项目模式来源，BSD）: https://github.com/facebook/meta-wearables-dat-android
- API 参考: https://wearables.developer.meta.com/docs/reference/android/dat/0.9
- iOS 版（Swift Package，无需 token）: https://github.com/facebook/meta-wearables-dat-ios
