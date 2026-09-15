# 在 Meta Ray-Ban Display 上写出第一个带屏显的应用 —— 一个初学者的开发学习笔记

> 记录时间：2026 年 8 月。从「这眼镜能自己写 App 吗」的好奇开始，到做出两个功能演示 Demo——跑在镜片上的 Web 应用「MRBD Playground」（路线 B）和延伸到镜片的原生 Android 应用「DAT Playground」（路线 A），把一路查到的资料和踩过的坑都记在这里。

---

## 〇、起点：这块屏幕到底是什么

先搞清楚硬件，不然后面很多设计约束会显得莫名其妙。

Meta Ray-Ban Display 是 Meta 在 2025 年 9 月 Connect 大会发布的智能眼镜（799 美元，含 Meta Neural Band 腕带）。和普通 Ray-Ban Meta 最大的区别是**右镜片里藏了一块全彩显示屏**：

- 分辨率 **600 × 600**，约 42 PPD（每度像素）
- 视场角约 **20°**，单目（只有右眼能看到）
- 亮度最高 **5000 nits**，漏光只有约 2%，旁人几乎看不到你在看什么
- 整机 69g，眼镜续航约 6 小时
- 随附的 **Meta Neural Band** 是一条 sEMG（表面肌电）腕带，靠读取手腕肌肉电信号识别手指微动作，续航约 18 小时

我一开始以为交互靠触屏或语音，实际上**主力输入是这条腕带**：拇指捏合、滑动，眼镜就能收到指令——这直接决定了后面所有 UI 怎么写。

## 一、查资料：第三方到底能不能开发？两条路线

这是我研究最久的问题，因为 2025 年刚发布时答案是「不能」。转折点在 **2026 年 5 月**：Meta 官方博客发了《The wait is over: Build for display glasses starting today》，正式向第三方开发者开放屏显应用，Developer Preview 滚动放量。整理下来一共两条路线：

### 路线 A：Wearables Device Access Toolkit（原生移动 SDK）

- 平台：iOS（Swift，SPM 直装）/ Android（Kotlin，GitHub Packages 分发），仓库 `meta-wearables-dat-android` / `-ios`，我深入研究时已到 **v0.9.0**（2026-08-03）
- 定位：**把你现有的手机 App 延伸到眼镜上**——App 跑在手机，眼镜是它的传感器和「第二块屏」
- 四大能力模块：`mwdat-core`（注册 + 设备会话）、`mwdat-camera`（相机流 + 拍照）、`mwdat-display`（**用 Kotlin DSL 往镜片上画 UI**，0.8 起新增）、`mwdat-mockdevice`（无真机模拟测试）
- 分发：通过 release channel，最多 100 名测试用户
- 仓库自带 Claude Code / Cursor / Copilot 的 AI 插件和完整示例 App（CameraAccess、DisplayAccess）

### 路线 B：Web Apps（本文主角）

这是让我最意外的一条路：**在眼镜上画 UI，用的居然就是普通的 HTML/CSS/JavaScript**。

- 应用就是一个网页，托管在任意公开 HTTPS URL 上，眼镜里的 WebView 以 600×600 渲染
- 没有任何私有框架，不用装 SDK，不用买开发机——市售眼镜就能测
- 用户在手机 Meta AI App 里：Devices → Display Glasses settings → App connections → Web apps → Add a web app，填名字和 URL 即可；也可以生成 `fb-viewapp://web_app_deep_link?appName=...&appUrl=...` 格式的深链二维码，扫码直接添加
- 测试阶段可以用密码/口令页做个软门禁

对初学者来说结论是：**想做一个「住在眼镜里」的独立应用，走 Web Apps（自由渲染整个 600×600）；想让现有手机 App 延伸到眼镜——用它的相机、往镜片上推卡片式 UI——走原生 Toolkit（受限组件 DSL + 独占的相机能力）。** 我两条都走了一遍：先 Web（第四课），再原生（第五课）。

官方资料入口（都不用登录）：

- 文档：`https://wearables.developer.meta.com/docs/develop/webapps`
- 官方 AI 工具链（含全部设计规范、模板、示例，BSD 协议）：`https://github.com/facebookincubator/meta-wearables-webapp`
- 还有个公开 MCP 端点 `https://mcp.developer.meta.com/wearables`，AI 编程工具可以直接调 `search_webapps_docs` 查最新文档——第一次见到官方把文档做成 MCP 给 AI 用的

## 二、三个颠覆网页开发直觉的「物理设定」

把官方 display-guidelines 啃完，有三件事必须先刻进脑子里：

### 1. 黑色 = 透明

这块屏是**加色式（additive）光波导**：像素只会「发光叠加」在真实世界上，不会「遮挡」。所以：

- `#000000` 不发光 → **完全透明**，透出真实世界。页面背景就该用纯黑；
- 但卡片、按钮这些 UI 表面**绝不能用纯黑**（会直接隐形），要用深灰（`#0a0a0f` ~ `#1C1E21`）才能「看起来存在」；
- 白色最亮最显眼，正文用白色/浅灰，对比度按 WCAG AA（正文 4.5:1）。

写惯了「暗色模式」的人也要转个弯：这不是审美选择，是物理约束。

### 2. 没有鼠标、没有触屏，只有「焦点」

输入模型完全是遥控器式的：

- **D-pad**：腕带手势或镜腿触控条 → 翻译成标准键盘**方向键事件**
- **Pinch（捏合）**：触发的是 Enter/click，但作用对象是**当前持有焦点的元素**（`document.activeElement`），不是「点哪里选哪里」
- **拇指+中指捏合**：返回手势 → Escape
- 没有自由光标；Pointer Lock 明确不支持

所以整个 App 必须围绕**可聚焦元素**来组织：每个可交互元素挂 `class="focusable"`（非 button 再加 `tabindex="0"`），自己写方向键的焦点移动逻辑，焦点态必须有明显的视觉反馈（焦点环）。在桌面浏览器里用方向键 + Enter + Esc 就能 1:1 模拟。

### 3. 视口永远是 600 × 600

```html
<meta name="viewport" content="width=600, height=600, initial-scale=1.0, user-scalable=no">
<meta name="mrbd-web-app-capable" content="yes">
```

第二个 meta 是 MRBD 应用的身份标识。另外官方规范还要求：四周留 8dp 安全边距（贴边元素会被回弹动画裁掉）、按钮高度 88dp、图标用 Unicode 或内联 PNG（禁外部图标字体）、favicon 必须是大于 52×52 的 PNG（推荐 128×128，不支持 SVG）。

性能预算也是硬指标，毕竟是眼镜级芯片：**首屏 < 3s、JS gzip 后 < 500KB、稳定 60fps、内存 < 128MB、首屏请求 < 10 个**。所以官方推荐第一课就用 vanilla JS，别上 React。

## 三、搭环境：比想象中简单太多

我准备好了要装一堆 SDK，结果整个「环境」是：

1. 一个浏览器（把窗口当 600×600 用，方向键模拟 D-pad）
2. Chrome DevTools → More tools → **Sensors** 面板（模拟地理位置和设备朝向）
3. 可选：Chrome 商店的 **Meta Ray-Ban Display Simulator** 扩展——模拟加色混合（黑色变透明的效果）、叠加真实环境背景、屏上 D-pad、录屏、还带自动 QA 检查（视口 meta、焦点样式、溢出检测）
4. 可选：把官方仓库装成 AI 编程工具的插件（`/plugin marketplace add https://github.com/facebook/meta-wearables-webapp`），里面 12 个 skill 覆盖建项目到部署全流程

本地跑起来就是 `python3 -m http.server`，没有构建步骤。

## 四、动手：MRBD Playground，一个「功能橱窗」Demo

为了把能力一次性都摸一遍，我做了个 portfolio 式的 Demo：首页一个功能目录，每个条目进去演示一种能力。完整代码就 5 个核心文件（`index.html` + `styles.css` + `app.js` + `sw.js` + `manifest.webmanifest`），零依赖，`app.js` gzip 后 7.6KB。

### 4.1 应用骨架：屏幕栈 + 焦点管理 + 动作分发

官方模板的架构值得照抄，三板斧：

**① 每个「屏幕」是一个 div，用 hidden 切换，自己维护历史栈：**

```js
function navigateTo(screenId) {
  onScreenExit(state.currentScreen);          // 离开时停掉传感器/动画！
  state.screenHistory.push(state.currentScreen);
  Object.keys(screens).forEach(id => screens[id].classList.add('hidden'));
  screens[screenId].classList.remove('hidden');
  onScreenEnter(screenId);
  focusFirst(screens[screenId]);              // 进屏自动聚焦第一个元素
}
```

**② 全局 keydown 实现 D-pad 焦点巡航（带循环回绕）：**

```js
switch (e.key) {
  case 'ArrowUp':   moveFocus('up');   e.preventDefault(); break;
  case 'ArrowDown': moveFocus('down'); e.preventDefault(); break;
  case 'Enter':     document.activeElement.click(); e.preventDefault(); break;
  case 'Escape':    navigateBack();    e.preventDefault(); break;
}
```

**③ 交互全部走 `data-action` 声明式分发**，一个 click 委托搞定：

```html
<button class="nav-item focusable primary" data-action="sensors-start">启动</button>
```

```js
document.addEventListener('click', e => {
  const el = e.target.closest('[data-action]');
  if (el) handleAction(el.dataset.action, el);
});
```

为什么监听 click 就够了？因为 pinch 在系统层就被翻译成了对焦点元素的 click/Enter——这是我觉得这个平台对 Web 开发者最友好的设计：**无障碍做得好的网页，天生就是合格的眼镜应用**。

### 4.2 逐个功能的踩坑笔记

**连续拖拽（画板/滑杆）——要「整页报名」。** 默认没有自由光标，想拿到腕带的连续滑动流，必须在**随页面初始加载的 CSS** 里写：

```css
body { touch-action: none; }
```

坑点：这个值只在页面加载时从 `<body>` 上读一次，运行时用 JS 改、或写在某个子元素上，都**不生效**。开启后用标准 Pointer Events 接流（`pointerdown/move/up`），坐标自己用 `clientX/Y` 算增量，别依赖 `movementX/Y`（官方明说要上机验证）。我的画板 + 笔刷滑杆就是这么写的，桌面上鼠标拖、眼镜上捏住拖，同一套代码。

**文本输入——composer 是白嫖的。** 标准 `<input>` / `<textarea>` 在眼镜上「聚焦 + 轻点」会自动唤起系统的手写+语音输入面板，写完提交回字段，触发标准 `input`/`change` 事件。注意：

- 必须**用户亲手**聚焦后再 tap，程序调 `.focus()` 不会唤起
- `type=password` 等类型不会唤起 composer，别指望它做密码框
- 读值只能靠 `input`/`change` 事件——没有物理键盘，`keydown` 里什么都收不到
- 每个字段给 `placeholder`，那是用户唯一的提示

**IMU 传感器——就是标准 Web API。** 罗盘/水平仪/加速度全靠 `DeviceOrientationEvent`（alpha=航向、beta=俯仰、gamma=横滚）和 `DeviceMotionEvent`（加速度计+陀螺仪）。两个规矩：权限请求必须由**用户手势**（按钮）触发，`requestPermission()` 在非手势上下文直接失效；离开页面必须 `removeEventListener`（省电是硬要求）。我还加了个兜底：启动 1.5 秒收不到真实数据就切「演示模式」放合成数据——官方 checklist 里明确要求 demo mode。

**GPS——眼镜没有 GPS 芯片。** `navigator.geolocation` 拿到的是**配对手机**的定位，首次定位可能要几秒，精度 5–50 米，超时给足 15 秒。

**Canvas / WebGL——都支持，但要克制。** 渲染可以用 DOM、Canvas 2D、WebGL 三套。我写了个星空动画 + FPS 计——headless 实测稳定 62fps。规矩：动画**按需启停**（我的「启动/停止」按钮不是装饰，onScreenExit 时强制 cancelAnimationFrame），空闲时不许跑循环动画。

**存储与离线——浏览器基本盘都在。** `localStorage` 直接可用（我的计数器和便签刷新后原样恢复）；Service Worker + Cache API 做离线壳缓存。我在 SW 里踩了个小坑：拦截了跨域 API 请求导致本地联调 503，后来改成只处理同源静态资源，跨域请求直接放行。

**网络——fetch/WebSocket 都是标准姿势。** 注意 CORS（眼镜 WebView 同样受同源策略约束，选 API 要挑带 CORS 头的，比如免 key 的 Open-Meteo），加载态/错误态/内存缓存老老实实做全，Wi-Fi 断续是常态。

### 4.3 部署上镜

1. `vercel --yes` 推到公开 HTTPS（任何静态托管都行），记得关掉 Vercel 的部署保护，眼镜浏览器没法登录
2. 用官方 skill 生成深链二维码，手机扫码 → Meta AI App 自动添加
3. 眼镜 App Library 里打开，URL 不变的话之后每次部署自动生效

## 五、路线 A 上手：把手机 App 延伸到镜片里

Web Demo 跑通之后我回头补了原生这条路，做了第二个 Demo「**DAT Playground**」——一个 Kotlin + Jetpack Compose 的 Android App，四个 Tab 对应 SDK 的四个能力模块。这条路的心智模型和 Web 完全相反：**App 的大脑永远在手机上，眼镜只是一组通过蓝牙会话租借来的「能力」**。

### 5.1 环境与接入：三道门

原生路线的接入比 Web 麻烦，有三道门要过：

1. **拉依赖要 GitHub Token。** SDK 从 GitHub Packages 分发，任何一个带 `read:packages` 权限的 PAT 都行，写进 `local.properties` 的 `github_token`。iOS 反而简单——Swift Package Manager 直接加仓库 URL，不用 token。
2. **Manifest 三件套。** `BLUETOOTH` / `BLUETOOTH_CONNECT` / `INTERNET` 权限，加上两个 `<meta-data>`：`APPLICATION_ID` 和 `CLIENT_TOKEN`。开发阶段在手机 Meta AI App 里打开 Developer Mode，这两个值填 `0` 就能跑；上生产才需要去 Wearables Developer Center 注册拿真凭据。
3. **用户要先「注册」。** `Wearables.startRegistration(activity)` 会跳到 Meta AI App 让用户授权你的 App 连接眼镜——类似 OAuth。之后才能建会话。

### 5.2 一切都是「会话 + 能力」

整条 API 主线背下来就是一句话：**建会话 → 等 STARTED → 挂能力 → 等能力 STARTED → 用 → 拆能力 → 关会话**：

```kotlin
Wearables.initialize(context)                       // Application.onCreate，一次
Wearables.startRegistration(activity)               // 跳 Meta AI 授权
// 等 Wearables.registrationState == REGISTERED
val session = Wearables.createSession(
    SpecificDeviceSelector(deviceId)).getOrThrow()   // 也可 AutoDeviceSelector { it.isDisplayCapable() }
session.start()                                      // 等 session.state == STARTED
val display = session.addDisplay().getOrThrow()      // 挂显示能力，等 DisplayState.STARTED
val camera  = session.addCamera(config).getOrThrow() // 挂相机能力
// ... 用完：
session.removeDisplay(); camera.stop(); session.stop()
```

所有东西都是 Kotlin Flow：`session.state`、`session.errors`、`stream.state`、`display.state`……写起来就是一堆 `collect`。会话随时可能被系统暂停（用户摘眼镜、折叠镜腿、蓝牙断开、别的体验抢占设备），**唯一正确的姿势是被动响应状态流，不要试图强行重启**。

### 5.3 惊喜：原生也能画屏了——但画法完全不同

我原以为原生路线只有相机（0.7 时代确实如此），结果 0.8/0.9 加入了完整的 **Display 能力**：用一套 Kotlin DSL 从手机往镜片上推 UI：

```kotlin
display.sendContent {
  flexBox(gap = 12, padding = 24, background = FlexBoxBackground.CARD) {
    text("骑行导航", style = TextStyle.HEADING)
    text("200 米后右转", style = TextStyle.BODY, color = TextColor.SECONDARY)
    buttonGroup {
      button(label = "完成", style = ButtonStyle.PRIMARY,
             iconName = IconName.CHECKMARK, onClick = { onDone() })
    }
  }
}
```

和 Web 路线的对比非常有意思：

- **不是自由像素，是受限组件。** 只有 `flexBox` / `text` / `image` / `icon` / `button` / `video` 这几种积木，样式全是枚举（`TextStyle.HEADING`、`CornerRadius.MEDIUM`、图标只能用 `IconName` 内置枚举，不能自定义）。想画任意图形？用 `image(bitmap = ...)` 塞一张自己绘制的 Bitmap。
- **每次 `sendContent` 整屏替换**，只能有一个根节点——没有局部更新、没有动画 API。
- **交互是一次「星际往返」**：用户在眼镜上 pinch 按钮 → 回调在手机上执行 → 手机改状态 → 再 `sendContent` 推新的一屏。我的 Demo 里做了个计数器，+1 按钮的每次点击都走完这条链路——延迟能接受，但设计交互时必须意识到这个回路。
- 视频播放是独立根节点：`video(player = VideoPlayer(VideoSource.Url(...), VideoCodec.MP4))`，发送成功后 `player.play()`，靠 `player.state` 流等 `ENDED` 再切下一屏。

### 5.4 相机：你拿到的不是画面，是 H.265 码流

这是原生路线最大的「初学者惊吓」：`camera.stream.videoStream` 这个 Flow 吐出来的 `VideoFrame` **不是 Bitmap，是压缩的 HEVC 访问单元**（`buffer` + `width/height` + `presentationTimeUs` + `isCodecConfig`）。想在手机上预览，得自己起一个 `MediaCodec` HEVC 解码器渲染到 Surface——官方示例就是这么做的，我的 Demo 里也照着实现了一个精简版。

其他要点：分辨率三档（LOW 360×640 / MEDIUM 504×896 / HIGH 720×1280），帧率只能选 2/7/15/24/30；蓝牙带宽有限，**低档位反而画质更稳**；`capturePhoto()` 拿到的才是直接可解码的 JPEG 字节。另外别忘了：**音频不在 SDK 里**——官方示例录视频时的声音来自手机麦克风，眼镜的麦克风和扬声器目前并没有开放给 DAT。

### 5.5 没有眼镜也能全流程开发：MockDeviceKit

`mwdat-mockdevice` 是我见过做得最认真的模拟层：`MockDeviceKit.getInstance(context).enable()` 直接伪造注册状态，`pairGlasses(GlassesModel.RAYBAN_META)` 配出一台假眼镜（5 种型号可选，含 `META_GLASSES`），然后 `powerOn()/unfold()/don()` 模拟开机、展开、戴上——这台假设备会像真机一样出现在 `Wearables.devices` 里，Display/Camera 能力照常可挂，还能给假相机喂本地视频文件（要 h.265 编码）当作「眼镜看到的画面」。配合权限模拟（强制 Denied 测拒绝分支），CI 里跑仪器测试都没问题。

### 5.6 两条路线的最终对照表

| | 路线 A · 原生 DAT | 路线 B · Web Apps |
|---|---|---|
| 应用位置 | 手机 App（眼镜是外设） | 眼镜内 WebView（手机只是入口） |
| 屏显方式 | 组件 DSL，整屏替换 | 自由 HTML/CSS/Canvas/WebGL |
| 相机 | ✅ 视频流 + 拍照（独占） | ❌ 无 |
| 传感器/GPS | ❌（用手机自己的） | ✅ 眼镜 IMU + 手机 GPS |
| 输入 | 眼镜按钮回调回手机 | D-pad + pinch + 手写/语音 composer |
| 上手成本 | PAT + Manifest + 注册流 | 一个 HTTPS URL |
| 无真机开发 | MockDeviceKit | 浏览器 + 模拟器扩展 |
| 分发 | Release channel（≤100 测试者） | URL / 二维码直加 |

## 六、两条路都走完之后的感受

**惊喜的部分：** 门槛低到离谱。会写网页 + 会做键盘无障碍，你就已经会写眼镜应用了；黑色=透明的加色显示反而让暗色 UI 有种「悬浮在世界上」的质感；官方把整套东西（设计规范、性能预算、AI 插件、模拟器）打包得非常完整，能看出是认真想让独立开发者进场。

**克制预期的部分：** 能力被泾渭分明地切开了——Web App 拿不到**相机**，原生 App 画屏只能用受限组件 DSL，而**眼镜的麦克风和扬声器两边都还没开放**；Developer Preview 阶段没有应用商店，分发靠 URL 和百人测试通道；审核、变现、权限政策都还没成型。这是 2007 年 iPhone「webapp only」时代的既视感——但对想早早占位的人来说，现在恰恰是最好的练手窗口。

**给下一个初学者的路线图：** 先在浏览器里把 600×600 + 方向键的 Web 原型跑通 → 装模拟器扩展看加色效果 → DevTools Sensors 联调传感器 → 需要相机或想接入现有 App 时，再开原生项目、用 MockDeviceKit 走完全流程 → 最后上真机。整条链路在买眼镜之前一分钱不用花。

---

## 参考资料

- Meta 官方博客: Build for display glasses starting today — developers.meta.com/blog/build-for-display-glasses/
- Web Apps 官方文档 — wearables.developer.meta.com/docs/develop/webapps
- 官方 Web App AI 工具链（模板/设计规范/性能规范/示例来源，BSD）— github.com/facebookincubator/meta-wearables-webapp
- 原生 SDK（Android，v0.9.0，含 DisplayAccess / CameraAccess 示例与 AI 插件规则）— github.com/facebook/meta-wearables-dat-android
- 原生 SDK（iOS，Swift Package）— github.com/facebook/meta-wearables-dat-ios
- 原生 API 参考 — wearables.developer.meta.com/docs/reference/android/dat/0.9
- Wearables 开发者中心 — developers.meta.com/wearables/
- 硬件规格报道 — uploadvr.com（Ray-Ban Display 发布报道）、roadtovr.com
- SDK 开放报道 — Road to VR / Next Reality / gHacks（2026-05）

*文中代码来自两个配套 Demo——「MRBD Playground」（Web，路线 B）与「DAT Playground」（Android 原生，路线 A），架构均基于 Meta 官方 BSD 协议示例改写；硬件参数与 API 行为以官方文档和上述来源为准，Developer Preview 阶段细节可能随版本变化。*
