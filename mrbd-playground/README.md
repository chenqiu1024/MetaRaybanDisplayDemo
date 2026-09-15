# MRBD Playground

一个 portfolio 式的最小 demo，展示 **Meta Ray-Ban Display** 眼镜上 Web App 能做到的各种功能。
纯 HTML/CSS/JS，零依赖、零框架、零构建步骤。

## 演示的能力

| 屏幕 | 展示的能力 |
|------|-----------|
| UI 组件 | 卡片 / Tabs / Toast / 进度条 / 骨架屏 / 徽章（暗色 + 焦点态设计系统） |
| D-pad 与焦点 | Neural Band / 镜腿触控 → 方向键事件，焦点环，键值实时日志 |
| Pinch 拖拽画板 | `body { touch-action: none }` 开启连续拖拽流 + Pointer Events 画图、滑杆 |
| IMU 传感器 | `DeviceOrientationEvent` / `DeviceMotionEvent`：罗盘、水平仪、加速度、角速度（无传感器时自动演示模式） |
| 定位 GPS | `navigator.geolocation`（来自配对手机） |
| 文本输入 | 标准 `<input>` / `<textarea>` → 聚焦+轻点唤起镜上手写/语音 composer |
| Canvas 渲染 | Canvas 2D 星空动画 + FPS 计数（按需启停，符合性能规范） |
| 本地存储 | `localStorage` 持久化计数器和便签 |
| 网络请求 | `fetch` + 内存缓存 + 加载/错误态（Open-Meteo 免费天气 API） |
| 关于 / 环境 | 运行时能力自检（WebGL、SW、DPR、UA…） |

## 本地运行

```bash
# 任意静态服务器即可
python3 -m http.server 8000
# 打开 http://localhost:8000 ，方向键 = D-pad，Enter = pinch，Esc = 返回手势
```

传感器/定位可用 Chrome DevTools → More tools → Sensors 模拟。
也可安装 Chrome 商店的 “Meta Ray-Ban Display Simulator” 扩展预览 600×600 加色显示效果。

## 部署到眼镜

1. 托管到任意公开 HTTPS URL（Vercel 免费版即可：`vercel --yes`）
2. 手机 Meta AI App → Devices → Display Glasses settings → App connections → Web apps → Add a web app，填入名称和 URL
   （或生成 `fb-viewapp://web_app_deep_link?appName=...&appUrl=...` 深链二维码扫码添加）
3. 眼镜 App Library 里打开

## 参考

- Web Apps 官方文档: https://wearables.developer.meta.com/docs/develop/webapps
- 官方 AI 工具链（本项目的模板与设计规范来源，BSD）: https://github.com/facebookincubator/meta-wearables-webapp
