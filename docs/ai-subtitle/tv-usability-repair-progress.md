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
- 已发布 `ai-subtitle-test-2026.09.13-r6`：不使用本地 APK，GitHub Actions 完成 lint、组装、签名、验签、发布和四架构资产校验。等待真机安装验收。

## 8 项问题进度

| # | 问题 | 当前状态 | 证据 / 说明 |
|---|---|---|---|
| 1 | 架构 APK 安装失败 / package info unknown | CI 验证通过；真机安装待确认 | run `34746986404` 已签名、验签并发布四架构 APK 与 `SHA256SUMS.txt`；仍需用户提供设备 ABI / 安装实测结果。 |
| 2 | 原文 / 双语 / 仅译文模式 | 代码完成 | `AiSubtitleDisplayMode`、持久化迁移与 Bridge 渲染分支已加入；待真机验证。 |
| 3 | 简繁中文覆盖 | 代码完成 | 已新增/扩展 `values`、`values-zh`、`values-zh-rTW` 的 AI 字符串；待 UI 巡检。 |
| 4 | 电视手机输入 | M07 自动验证通过；局域网/真机待验 | 已改为本地编辑/显式保存、`?k=` 请求鉴权、严格版本冲突、Provider/协议/语言/Prompt/密钥动作；settings focused tests 74/74 通过。 |
| 5 | 测试连接反馈和真实翻译探测 | M07 自动验证通过；真机 UI 待验 | 保存后走现有 `testTranslationConnection`；`/test` fake-provider 成功与 401 终态已测，电视回调主线程化并在取消后抑制迟到结果。 |
| 6 | 播放控制栏 AI 入口 | 代码完成 | AI 动作入口、设置/状态和真实运行状态已接入；待真机验证。 |
| 7 | 异步刷新与真实状态 | 代码完成 | 异步译文成功后主线程重绘；新增等待 / 翻译中 / 已显示 / 失败状态并有单元测试。 |
| 8 | 官方预设 Base URL 与 Custom | 代码完成 | 预设默认 URL、Custom 类型与兼容协议选择已加入；Custom 默认 URL 为空的行为已更新测试。 |

## 下一批动作

1. Task A 已实现并通过 focused 自动检查；提交后按计划进入 Task B。
2. M07 实现/自动检查完成后，推送候选并走现有 CI/签名流程。
3. 用户按设备架构安装候选 APK 后统一真机验收；r6 不作为 M07 修复验证。

## 验证台账

- 2026-09-13 编译：`./gradlew :common:compileStbetaDebugJavaWithJavac :smarttubetv:compileStbetaDebugJavaWithJavac` => `BUILD SUCCESSFUL in 52s`。
- 2026-09-13 Bridge 状态单测：`AiSubtitleCueBridgeModeTest` => `BUILD SUCCESSFUL in 51s`。
- 2026-09-13 全量 common 单测：`:common:testStbetaDebugUnitTest` => `BUILD SUCCESSFUL in 49s`；解析 41 个 XML：tests=316, failures=0, errors=0, skipped=10。
- 2026-09-13 本地预检：`./gradlew :smarttubetv:lintStbetaRelease :smarttubetv:assembleStbetaRelease` => `BUILD SUCCESSFUL in 1m 46s`；本地产物未签名，仅作预检，不用于发布。
- 2026-09-13 CI 分支验证：run `34746345513` 在 `:common:lintStbetaRelease` 失败；报告定位到手机输入服务使用 API 19 的 `Closeable` 转型和 `StandardCharsets#UTF_8`。已改为显式 `ServerSocket` / `Socket` 关闭和 `Charset.forName("UTF-8")`。
- 2026-09-13 CI 分支验证：run 34746701488 全部通过（common 单测、release lint、release assemble、JDK 11 设置测试、GitHub 签名/验签、APK 上传）；可创建 r6 tag。
- 2026-09-13 修复回归：`ProviderPresetTest` 旧契约误要求 `CUSTOM` 默认 URL 非空；已按“Custom 必须用户输入 URL”的契约更新并通过。
- 2026-09-13 M07 Task A focused settings：`gradlew :common:testStbetaDebugUnitTest --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.*'` => `BUILD SUCCESSFUL in 1m 9s`；11 个 XML，tests=74，failures=0，errors=0，skipped=0。该结果针对未提交工作区，不含 CI/真机验收。
- 真机验收：待用户设备信息与实测。
- Release：`ai-subtitle-test-2026.09.13-r6` 已发布于 https://github.com/CometDash77/smarttube/releases/tag/ai-subtitle-test-2026.09.13-r6 ；发布 run `34746986404` 成功，目标提交 `cc1e1838ab4e34fcdab48d5fc40e95c723da7c40`。
- Release SHA256：arm64-v8a `627fc2cb5d7d2f2a326f52a4e07049d7830ae5e97320cb1a15c037f175f51dbc`；armeabi-v7a `1fb25f1a7d3c43f9f18139ef3f5c88cec2eb8a7d4548a20a1b5131cacf68e57d`；universal `98ee44bfd8a0f183f2d6ebbad517395ed6fb50f2353d176257067b57c9502cad`；x86 `695ee273ddba683ea95f3c0bc8611f8fb72027b6a1d013f55b74aaf31351f8e9`。

## 注意事项

- 不使用在线二维码服务；二维码必须本地生成。
- 手机输入链接不得包含 API Key，也不得把明文密钥回传或写入 URL。
- KissTranslator 只作只读行为参考，不复制 GPL 代码。
- 不覆盖旧 Release tag；发布前核对哈希与签名。
- `gradle.properties` 不保留 `android.overridePathCheck=true`；该属性仅为本地构建 workaround。