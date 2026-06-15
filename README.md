# GloscAI Images APP

GloscAI Images 是一款 Android 原生 AI 生图应用。当前工程已按设计稿实现工程式生图、对话式生图、图片库、图片详情/编辑和 API 设置五个核心界面。

## 功能

- 工程式生图：提示词、负向提示词、尺寸、质量、数量、种子和任务状态。
- 对话式生图：多轮消息保存，用户消息可触发生图并回写图片消息。
- 图片编辑/变换：详情页支持局部重绘、变体、超分、扩图入口，编辑结果保存为新图片。
- API 设置：Glosc AI/自定义服务商配置、HTTPS Base URL 校验、API Key 加密保存、模型列表获取。
- 图片管理：本地图片库、收藏、标签、搜索、来源筛选、详情元数据和删除。

## 技术栈

- Kotlin + Android 原生 View
- Room：图片、任务、服务商、对话和消息持久化
- Retrofit + OkHttp：Glosc AI One API / OpenAI 兼容 API
- Glide：本地图片加载
- Android Keystore：API Key 加密存储
- Gradle 8.14.3 / Android Gradle Plugin 8.11.0

## 运行

本机如果没有系统 Java，可使用 Android Studio 自带 JBR：

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
```

生成的 debug APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## API 配置

应用默认服务商为 Glosc AI，默认渠道为 `https://one.gloscai.com/`。网络层会请求 `/v1/models` 获取模型列表，并只使用 `categories` 中包含 `image` 的模型作为图片模型。

首次生成前需要在“设置”页保存 API Key，并点击“获取模型列表”。从 [这里](https://one.gloscai.com/keys) 获取 key。Key 会写入 Android Keystore 加密存储，不会进入 Room 或日志。

## 验证

已验证：

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest
```

当前没有连接的 Android 设备或模拟器，因此尚未执行 `installDebug` 或 UI 自动化启动验证。
