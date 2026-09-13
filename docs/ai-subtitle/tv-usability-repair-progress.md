# AI 字幕修补进度台账

日期：2026-09-13
分支：`feature/ai-bilingual-subtitles`
计划基线：r5（`6ddf9cad2`）
计划文件：`SmartTube/docs/ai-subtitle/tv-usability-repair-plan.md`

## 当前状态（阶段性快照）

- 已完成异步译文主线程刷新、三种显示模式、简繁资源、Provider Custom / 官方 URL、真实翻译连接测试、播放栏入口和手机输入初版。
- 已增强播放页真实状态：等待字幕、正在翻译、译文已显示、失败及非敏感原因。
- 已增强连接测试：结果不再只 toast，改为保留成功 / 失败结果页，并显示非敏感失败原因。
- 已增强手机输入：电视端显示草稿摘要（配置名、Base URL、模型、密钥状态、Prompt 名称/摘要），密钥不回显。
- 已增强手机输入服务：Host / Origin 校验、请求大小限制、5 分钟会话过期、60 秒无轮询失效；关闭后旧链接失效。
- 已修复手机输入 POST body 读取错误、HTML 缺少 `promptName` 输入框的缺陷。
- 已把 `android.overridePathCheck=true` 从仓库工作区还原；后续本地构建按需用 Gradle `-P` 注入，避免把环境专用配置提交。
- 进入 GitHub Release 阶段：不使用本地 APK，推送修复提交后创建 r6 tag，由 GitHub Actions 完成 lint、组装、签名、验签和 Release 发布。

## 8 项问题进度

| # | 问题 | 当前状态 | 证据 / 说明 |
|---|---|---|---|
| 1 | 架构 APK 安装失败 / package info unknown | 待验证 | 尚未取得设备 ABI / 真实安装错误；待用 GitHub CI 检查架构 APK 包内容、签名与哈希。 |
| 2 | 原文 / 双语 / 仅译文模式 | 代码完成 | `AiSubtitleDisplayMode`、持久化迁移与 Bridge 渲染分支已加入；待真机验证。 |
| 3 | 简繁中文覆盖 | 代码完成 | 已新增/扩展 `values`、`values-zh`、`values-zh-rTW` 的 AI 字符串；待 UI 巡检。 |
| 4 | 电视手机输入 | 代码完成 | 本地 HTTP 服务、本地二维码、草稿同步、草稿显示、保存路径、会话失效已加入；编译通过，真机/局域网验证待做。 |
| 5 | 测试连接反馈和真实翻译探测 | 代码完成 | 已执行选中模型最小真实翻译请求；结果页保留成功 / 失败原因。待真机 UI 验证。 |
| 6 | 播放控制栏 AI 入口 | 代码完成 | AI 动作入口、设置/状态和真实运行状态已接入；待真机验证。 |
| 7 | 异步刷新与真实状态 | 代码完成 | 异步译文成功后主线程重绘；新增等待 / 翻译中 / 已显示 / 失败状态并有单元测试。 |
| 8 | 官方预设 Base URL 与 Custom | 代码完成 | 预设默认 URL、Custom 类型与兼容协议选择已加入；Custom 默认 URL 为空的行为已更新测试。 |

## 下一批动作

1. 提交并推送修补结果。
2. 创建并推送 `ai-subtitle-test-2026.09.13-r6`，让 CI 完成 lint、组装、签名、验签和 Release 发布。
3. 用 CI run 和 Release 校验结果更新台账，并提供下载链接与哈希。
4. 用户提供设备 ABI / 真实安装错误后完成安装诊断。
5. 真机验收：播放状态、三种模式、手机输入、连接测试、中文界面和旧配置迁移。

## 验证台账

- 2026-09-13 编译：`./gradlew :common:compileStbetaDebugJavaWithJavac :smarttubetv:compileStbetaDebugJavaWithJavac` => `BUILD SUCCESSFUL in 52s`。
- 2026-09-13 Bridge 状态单测：`AiSubtitleCueBridgeModeTest` => `BUILD SUCCESSFUL in 51s`。
- 2026-09-13 全量 common 单测：`:common:testStbetaDebugUnitTest` => `BUILD SUCCESSFUL in 49s`；解析 41 个 XML：tests=316, failures=0, errors=0, skipped=10。
- 2026-09-13 本地预检：`./gradlew :smarttubetv:lintStbetaRelease :smarttubetv:assembleStbetaRelease` => `BUILD SUCCESSFUL in 1m 46s`；本地产物未签名，仅作预检，不用于发布。
- 2026-09-13 CI 分支验证：run `34746345513` 在 `:common:lintStbetaRelease` 失败；报告定位到手机输入服务使用 API 19 的 `Closeable` 转型和 `StandardCharsets#UTF_8`。已改为显式 `ServerSocket` / `Socket` 关闭和 `Charset.forName("UTF-8")`。
- 2026-09-13 修复回归：`ProviderPresetTest` 旧契约误要求 `CUSTOM` 默认 URL 非空；已按“Custom 必须用户输入 URL”的契约更新并通过。
- 真机验收：待用户设备信息与实测。
- Release：计划 `ai-subtitle-test-2026.09.13-r6`，由 GitHub Actions 签名/验签并发布；完成后补记 run ID 与资产哈希，不覆盖旧 tag。

## 注意事项

- 不使用在线二维码服务；二维码必须本地生成。
- 手机输入链接不得包含 API Key，也不得把明文密钥回传或写入 URL。
- KissTranslator 只作只读行为参考，不复制 GPL 代码。
- 不覆盖旧 Release tag；发布前核对哈希与签名。
- `gradle.properties` 不保留 `android.overridePathCheck=true`；该属性仅为本地构建 workaround。