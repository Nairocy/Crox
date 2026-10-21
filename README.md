# Crox

> Android AI 对话客户端 — Kotlin · Jetpack Compose

一个轻量的 Android 聊天前端：SSE 流式输出、Markdown 渲染、可导入的表情贴纸，
会话与设置全部保存在本机。

## 功能

- **流式对话**：OkHttp 长连接接收 SSE，逐字上屏；支持停止生成、重新生成
- **Markdown 渲染**：标题、列表、表格、代码块，代码块可一键复制
- **表情贴纸**：可从本地目录导入的表情包系统，按词随机贴图
- **会话管理**：会话与图片保存在应用私有目录（`filesDir/sessions/*.json`、`filesDir/images/*.jpg`）
- **设置持久化**：SharedPreferences（`crox_settings`）——API token、温度、max tokens、系统提示词、主题等
- **主题**：跟随系统 / 浅色 / 深色
- **图片输入**：选择图片后压缩再随消息发送

## 技术栈

| 项 | 版本 / 选型 |
|---|---|
| 语言 | Kotlin（JVM 17） |
| UI | Jetpack Compose（BOM `2026.09.00`）+ Material 3 |
| 网络 | OkHttp `5.5.0`（SSE 长连接，read timeout 置 0） |
| 序列化 | kotlinx-serialization-json `1.11.0` |
| 架构 | 单 Activity + ViewModel + 文件/SharedPreferences 持久化 |
| SDK | minSdk 26 · compileSdk 36 · targetSdk 36 |

## 构建

用 Android Studio 打开，或命令行：

```bash
# 需要在仓库根目录自建 local.properties（本仓库不包含）
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

./gradlew assembleDebug
```

Release 签名：`keystore.properties` 与 `.jks` 均**不在仓库内**（见 `.gitignore`）。
没有该文件时签名配置会被跳过，只能产出 debug 包。

## 配置 API 端点

请求基址写在 `app/src/main/java/com/nairocy/crox/net/ApiClient.kt` 的 `BASE` 常量里，
默认指向作者自建的网关（`https://ai.nairocy.com`）。

**要接自己的服务，改这一行即可**；服务端需要实现同样的 SSE 事件格式
（`onDelta` 逐字增量 + `onDone` 结束），请求体为 OpenAI 风格的
`{messages, max_tokens, temperature, top_p, stream:true}` 加上项目自定义字段。

> 也就是说：这个仓库是「客户端」，不自带模型服务。

## 隐私

- API token 只写入 SharedPreferences，不写入日志、不硬编码在代码里
- 会话内容只存在应用私有目录，卸载即清除

## 许可证

[MIT](LICENSE) © 2026 Nairocy
