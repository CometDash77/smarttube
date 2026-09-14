# M07 断点至 M09 完成 Implementation Plan

> **For agentic workers:** 后续获得执行授权后使用 executing-plans 串行推进；本次仅编写计划，不实施、不运行构建、不提交或发布。默认不派生代理。每个阶段维护代码、自动验证、CI、设备验收四种独立状态。

**Goal:** 从现有 M07 Task E 未完成工作恢复，补齐真实运行缺口，完成 M08 上下文和流式管线，最后完成 M09 发布候选与上游回归验收。

**Architecture:** 复用 feature-owned Controller/Bridge、TranslationSession、TranslationScheduler、Provider、PromptRenderer 和缓存身份；修复数据桥接后再扩展。新能力不进入 PlaybackFragment、Renderer 或子模块。

**Tech Stack:** Java、Android API 17、Gradle 7.5、现有 OkHttp 3.12.13、JUnit 4；JDK 17 主验证，既有 Robolectric/JDK 11 补充通道。

日期：2026-09-14。用户最新范围：**只写计划，目标覆盖到 M09 结束**。本文是后续执行入口；M07 原计划保留历史，本文件覆盖其过时恢复顺序。M08/M09 分别见 [M08-plan.md](M08-plan.md) 与 [M09-plan.md](M09-plan.md)。

## 1. 真实断点和证据等级

已读取任务 `01a09a8e-3189-7bb3-924c-7aa7dc3c404d` 最后交接并对照本地源码、Git 与计划；本次没有重跑历史测试。

| 范围 | 当前状态 | 证据与限制 |
|---|---|---|
| Task A | 已提交 `717618f86` | 历史 settings 74 tests；不是本次验证 |
| Task B | 已提交 `738325822` | 历史 source/segmentation/integration 79 tests；实际轨匹配仍有缺口 |
| Task C | 已提交 `1ce3d8b2e` | 历史 6 suites / 62 tests / 0 failures / 0 errors / 0 skipped |
| Task D | HEAD `38604559d` | 历史 6 suites / 67 tests / 0 failures / 0 errors / 0 skipped；部分批次承诺需复核 |
| Task E | 部分实现、未提交、未验证 | 审查开始时 10 个生产/资源文件 + 2 个文档文件修改，无新增 Task E 测试 |
| M08/M09 | 路线图存在，执行计划原先不存在 | 本轮只补计划，不代表实现完成 |

工作区的旧 AGENTS 基线“master/干净”已过时；后续以实际 Git 状态为准。progress.md 下半部还保留 M04 暂停和 planned next action；不能据此退回 M04 重做。旧报告中的通过数字不等于当前脏工作区通过。本次 `git diff --check` 通过，但不能检测 Java 语法错误。

## 2. 全局约束

- 保留全部现有未提交改动，不 reset、覆盖复制回源目录或 cherry-pick 参考分支。
- 原文、三种显示模式、取消、generation/epoch 隔离、配对令牌、密钥保留/替换/清除必须持续可用。
- KissTranslator 只读，fixture 独立编写；不能复制其 GPL 实现或测试。
- 不新建第二套会话、Provider 配置、通用队列或协议模型；M08 新能力先采用 [复用调查](../research/2026-09-14-m08-reuse.md) 的边界。
- 所有拟定参数都是本计划初始值，必须有边界测试；不把性能目标写成已测结果。
- Ponytail full：不预留后续字段，不为测试单独保留生产兼容层；复用已有测试覆盖，只补缺失行为。每里程碑一份报告，子任务用章节记录，不再拆报告文件。
- 默认串行执行。普通实现选择记录后继续，未出现新的必要信息缺口时不逐项问是否继续。
- M07 原有真机检查保持“实现后执行”；设备缺失不阻塞 M08 独立实现与自动检查，但不得把 M07/M09 完整验收标为通过。
- M09 必须收齐真实设备与精确 SHA 的 CI 证据才叫完整结束；只能完成代码时准确写“实现/自动检查完成，设备待验”。

## 3. 审查确认的修复项

下表是静态证据，不冒充已运行的故障复现。AI 路径统一指 `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/`。

| ID | 代码证据 | 影响 | 安排 |
|---|---|---|---|
| R1 | integration/AiSubtitleCueBridge.java:388 字符串中出现真实换行 | 普通 Java 字符串未闭合，阻止编译 | M07-R0 |
| R2 | Runtime.applySchedulingToBridge 同时调用 onSegmentationChanged，后者无条件 dropSession | 手机只改提前量也清缓存、取消请求、重新拉字幕 | M07-R1 |
| R3 | Bridge 构造器只读取提前量/节流；setSourceAdapter 未应用已存分段值 | 重启/新建 adapter 后退回 60/200/80 | M07-R1 |
| R4 | onSegmentationChanged 重建 active session；onTimelineReady 只校验 trackId | 暂停/关闭时设置可能启动工作；同轨旧源结果可能覆盖新参数结果 | M07-R1 |
| R5 | PhoneInputServer 把五个设置保存放在 targetLanguage 非空分支；long 强转 int 后校验 | 空语言时设置不保存；超范围整数可能截断成合法值 | M07-R2 |
| R6 | Bridge 固定 source + translation；无顺序设置 | Task E 明列的译文优先缺失 | M07-R2 |
| R7 | source/SmartTubeSubtitleSourceAdapter.matchSubtitle 同语言取首项；测试明确期待 first entry wins | 人工/ASR 同语言时可翻译错轨 | M07-R3 |
| R8 | 两协议的 systemPrompt 为硬编码；Resolution.getPrompt 未沿 Runtime 进入请求 | Prompt 身份变了，实际请求指令却没变 | M07-R4，M08 前置 |
| R9 | Task D 勾选 prefix/tail，但生产为整 unit 结果；现有测试是草稿/覆盖错误终止 | 不能声称证明了连续前缀与尾部修复 | M07-R5，核实并补齐 |
| R10 | InMemoryTranslationCache 使用无限 HashMap；调度 mWork 也积累历史单元 | “窗口有界”不等于长视频内存有界 | M09-B，M08 复用时保留限制 |

还需定向验证：source adapter 三个参数在异步解析时应取一致快照；无有效 Provider 时设置不能触发 null-provider scheduler 异常；缩小窗口之后不得继续派发窗口外的 pending/retry；源格式已有 fmt 非 VTT 时不能无条件送 VttParser。

## 4. M07-R0：恢复可编译基线

**修改：** AI/integration/AiSubtitleCueBridge.java。**测试：** 现有 integration/AiSubtitleCueBridgeModeTest.java。

- [x] 核对 HEAD、12 个原脏文件和本次新增计划文件；保存审阅 diff，不生成包含密钥的快照。
- [x] 最小修复恢复 `return new Cue(source + "\n" + translation);`；不顺手格式化整个 Bridge。实际编译还暴露第二个阻断：`RuleSentenceBreaker.splitText/splitOversize` 仍是静态方法却引用新的实例字段 `mMaxChars`，本次一并改为实例方法。
- [x] 在新的 ASCII 临时副本 `C:\tmp\smartube-m07-m09` 编译并执行 mode 测试；首次真实结果是两个编译阻断（未闭合字符串 + mMaxChars 静态引用），修复后套件通过，旧临时目录产物未使用。
- [x] 此修复并入 Task E 提交，不单独为一行语法修复建立里程碑。

## 5. M07-R1：设置应用与生命周期

**修改：** AI/integration/{AiSubtitleRuntime,AiSubtitleCueBridge,AiSubtitleController}.java、AI/source/SmartTubeSubtitleSourceAdapter.java、AI/scheduler/TranslationScheduler.java。
**测试：** TEST/integration/{AiSubtitleCueBridgeSessionTest,AiSubtitleControllerTest}.java、TEST/source/SmartTubeSubtitleSourceAdapterTest.java、TEST/scheduler/TranslationSchedulerTest.java；TEST 为 `common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/`。

- [x] Bridge 保存最后应用的三个分段参数（`mSegmentTargetChars/mSegmentMaxChars/mLongSentenceChars`）；context 构造器从 AiSubtitleData 读取，`setSourceAdapter` 先 `configureSegmentation` 后 `triggerTimelineLoad`。相同分段参数立即 return。
- [x] Runtime 分开应用调度和分段变化；两者都按相同值短路。手机与 TV 走同一应用入口；只改 lookahead/throttle 不调用 dropSession、不清缓存、不增加 generation。
- [x] 调度新增功能内入口 `TranslationScheduler.onSettingsChanged(long positionMs, long nowMs)`：调用 `dispatch(nowMs, true)`，不设置 mPaused=false；设置回调不再冒用 resume。
- [x] 真正分段变化：记录原暂停状态 → `dropSession()`（取消调度、清缓存、使旧源 load 失效）→ 更新参数 → 新 generation → 仅在 enabled 且 Provider 有效时 `ensureActiveSession` 并重载；恢复原暂停状态，不默认 active。
- [x] 源 load 捕获局部单调序号 `mLoadSequence` 与 `SourceTrackId`；响应不符即丢弃。`dropSession()` 与 `rebindSessionIfIdentityChanged()` 递增序号，故 close、换轨、换视频、重分段都使旧 load 失效；同轨 A→B→A 靠单调序号隔离。未新建会话系统。
- [x] Bridge 的设置应用全部在 `synchronized` 内一次读完参数；下载与解析在适配器网络线程完成。Controller 既有 `disposeSourceFetch()` 仍在重发前释放旧订阅（未改动）。
- [x] 行为测试：`schedulingChangeWhilePausedStartsNoWork`、`segmentationChangeStartsANewGenerationAndKeepsThePausedState`、`anUnconfiguredProviderToleratesLifecycleAndSettingsChanges`、`aNewSourceAdapterReceivesTheStoredSegmentationLimits`、`aStaleTimelineLoadCannotOverwriteANewerOne`、`schedulingChangeKeepsTheSessionAndItsCachedTranslations`、`identicalSchedulingChangeIsIgnored`（均在 `AiSubtitleCueBridgeSessionTest`）。

目标控制流（设计伪代码，非现有 API）：

```text
apply(values):
  if schedule differs: update limits; dispatch immediately only if already active
  if segmentation identical: stop
  remember paused/enabled; invalidate old load and generation; clear affected cache
  configure adapter with one immutable parameter snapshot
  if enabled AND provider valid AND track valid: load with captured ownership
  response: require current load + generation + track before accepting
```

## 6. M07-R2：完整设置路径与 Task E 提交

**修改：** AI/settings/AiSubtitleData.java、AI/settings/ui/AiSubtitleSettingsPresenter.java、AI/settings/remote/AiSubtitlePhoneInputServer.java、Bridge、三套 ai_subtitle_strings.xml。
**测试：** TEST/settings/AiSubtitleDataTest.java、TEST/settings/remote/AiSubtitlePhoneInputServerTest.java、TEST/integration/AiSubtitleCueBridgeModeTest.java、scheduler 测试。

- [x] 保留 lookahead 0/30/60/90/120 秒默认 90、throttle 5/15/30 秒默认 30。缺失旧存储取默认；读取到非预设或非法分段持久值立即修复写回默认（`getLookaheadSeconds`/`getScheduleThrottleSeconds`/`repairSegmentLimitsIfNeeded`）。
- [x] 手机 `applyForm` 先解析校验全部字段再写任何一项：`parseBoundedInt` 按 long 判断 int 范围后收窄；target>=1、max>=target、longSentence>0。空目标语言沿用当前语言，其余设置照常保存（`anEmptyTargetLanguageStillSavesTheOtherSettings`）。
- [x] 五字段经 `AiSubtitleData.setSchedulingLimits` 走同一 `SharedPreferences.Editor` 批次；沿用现有 Profile/Prompt 保存与回滚路径。无效输入不修改其中任何一项，并以独立 `invalid` 状态返回，不再与版本冲突混用同一提示。
- [x] 补双语顺序：`AiSubtitleData.isTranslationFirst/setTranslationFirst`（默认 false）；TV「双语顺序」两个选项，手机同一字段 `bilingualOrder`；只改 BILINGUAL 拼接顺序，SOURCE/TRANSLATION_ONLY 不受影响。`AiSubtitleCueBridge.setTranslationFirst` 走既有 refresh 通道立即重绘，不发翻译请求、不清缓存（`bilingualOrderChangesPresentationWithoutNewRequests`）。
- [x] TV 三个分段 preset 保留；手机自定义合法值重开原样显示，TV 非预设值多出一条只读「自定义 (a/b, long c)」选项，不悄悄改写为默认。新增文案覆盖 en/zh/zh-TW。
- [x] 扩展既有真实 socket 测试：新增越界值拒绝、非法分段拒绝、空目标语言仍保存其余设置、保存值与页面渲染一致四项，均走真实 HTTP 路由并断言草稿/存储状态；沿用既有的配对令牌、版本冲突、secret 不回传断言。
- [x] `lookaheadWindowSelectsExactlyTheSpecifiedUnits`：位置 10s、unit 起点 10/25/40/70/100/101s 且均短于 1s；30s 窗口派发 10/25/40，90s 派发 10/25/40/70/100，均不含 101；逐个完成 fake request，保持单并发。
- [x] 覆盖：0 前瞻当前句、100 次 tick 去重、90→30 缩窗（`shrinkingTheWindowStopsFurtherDispatchBeyondIt`）、节流不延迟 seek（`throttlingNeverDelaysSeek`）、暂停中改设置不派发（`settingsChangeNeverResumesAPausedScheduler`）、缓存命中零网络（`cacheHitDoesNotIssueANewRequest`）。
- [x] 运行 settings/source/segmentation/scheduler/integration 定向套件并记录本机 XML 计数（见 §13 执行记录），未沿用 C/D 数字。

## 7. M07-R3：字幕源匹配修正

**修改：** AI/source/SmartTubeSubtitleSourceAdapter.java、AI/integration/AiSubtitleController.java；测试为现有 adapter/controller 套件。

- [x] 沿实际选中 Exo format identity 核对：`ExoFormatItem` 明确注释字幕不可按 formatId 比较（non-constant），MPD `AdaptationSet@lang` 取 `MediaSubtitle.getName()`（无名字时取 languageCode），故语言分量本身即字幕显示名。新增人工/ASR 同语言 fixture 并交换列表顺序，断言仍选中被选中的那条轨。
- [x] 匹配顺序改为显示名精确 → 语言码精确 → 去括号归一化名；任一阶段命中多条即返回 source-only，绝不取第一项。只用 controller 既有 track identity，未新增宿主钩子。残留限制（identity 只带语言码且人工/ASR 同码时无法区分）记入 M07 报告与设备验收步骤。
- [x] `toVttUrl` 改为解析 query 参数、丢弃既有 `fmt` 并追加 `fmt=vtt`，保留 fragment 与其他参数原样（含百分号转义）；覆盖无 query、已有 query、已有非 VTT fmt、转义签名参数、fragment 五类断言。
- [x] fixture 覆盖空字幕内容/空轨列表、重叠（裁剪不丢句）、同文本不同时间、语言名不规范（多段括号 + auto-generated）、轨切换迟到（Bridge 源 load 序号反序投递）。未重做 Task B。

## 8. M07-R4：选中 Prompt 真正进入网络请求

**修改：** AI/translation/{TranslationRequest,TranslationProfileResolver}.java、AI/integration/{AiSubtitleRuntime,AiSubtitleCueBridge}.java、AI/scheduler/TranslationScheduler.java、两协议 Adapter；复用 AI/prompt/PromptRenderer.java。

- [x] `TranslationProfileResolver.Resolution` 的 `PromptProfile` 经 `AiSubtitleRuntime.applyToBridge` → `AiSubtitleCueBridge.onProviderChanged(provider, profile, prompt)` → `TranslationScheduler` 构造参数冻结；请求构建不再读活动设置。
- [x] `TranslationRequest` 只增加必填 `renderedPrompt`，未预留 M08 context 字段；全部构造调用方（Scheduler、连接测试、测试）已更新，未保留第二条 baseline 构造路径。`toString` 只输出 prompt 长度，避免字幕文本进入日志。
- [x] `TranslationScheduler.submitLocked` 在网络调用前用 `PromptRenderer.render` 填五个变量（context 为空字符串）；渲染失败直接置 FAILED 并保留原文，零网络（`anUnrenderablePromptIsTerminalWithoutTouchingTheNetwork`）。`PromptRenderer` 改为区分「键缺失」与「显式空值」，使空 context 不再被判为 malformed。
- [x] OpenAI `messages[0].role=system` 与 Anthropic 顶层 `system` 使用 `request.getRenderedPrompt()`；`sourceText` 仍只承载 unit 原文。
- [x] 两协议 body 测试：system/user 内容断言、Prompt A/B 产生不同 body、中文+引号+换行 JSON 转义、未知变量拒绝、空白 prompt 拒绝构造；迟到结果隔离沿用既有 session 套件；密钥不进入 `HttpRequest.toString`/`TranslationRequest.toString`。
- [x] 与 M07-R3 合并为一个「请求反映用户实际选择」的提交，归属已发现的 M05/M07 接线回归；M08 上下文建立在这条路径上。

## 9. M07-R5：部分结果和原计划覆盖复核

复核结论（2026-09-14，本地静态证据 + 新增自动断言；不是设备复现）：

- [x] 复核 D3 手动重试入口：**此前没有生产调用**。`TranslationScheduler.hasFailed/getFailureMessage` 当时只有测试引用，且 `requestCurrentUnitLocked` 对 `state != PENDING` 直接返回，所以 FAILED 单元在同一 epoch 内永远无法重试。本次补上真实入口：`TranslationScheduler.retryFailed(positionMs, nowMs)` 只把当前窗口内的终态失败重排为 PENDING 并给出全新尝试预算（普通 tick 不重置），`AiSubtitleCueBridge.retryFailed()` 转接，TV 设置新增「重试失败的翻译」。断言：`manualRetryRequeuesTerminalFailuresWithAFreshBudget`、`manualRetryLeavesUnitsOutsideTheWindowFailed`（真实请求计数 + 窗口边界）。
- [x] 沿现有生产路径核实 indexed/boundary 输出契约：**没有可执行生产模式**，证据见下方范围修订。普通单 unit 模式继续整句 final，未因内置 Prompt 名称新增第二套批次运行模式。
- [ ] 批次修复（连续前缀保留 + 只为未覆盖尾部派发一次修复）：**未实施**，范围修订见下；保留未完成标记。
- [x] `AiSegmentationCoordinator` 复核：其 `Acceptance.fromFallback` 确实可能返回原文分段，不能当“翻译成功”。当前它没有任何生产调用者，因此不存在把 fallback 当成功或被 renderer 同步调用 `TailRequester` 的路径；结论记入 M07 报告。
- [ ] 使用 `BoundaryProtocol.encodeItem` 的独立 fixture：**未编写**；在范围修订被接受前，为不存在的生产模式编写 fixture 只会制造无调用者的测试。
- [x] 已写出具体范围修订与理由，保留未完成标记；未删除路线图硬性退出条件（M07 退出条件 7 保持未满足状态）。
- [x] M07 报告整理 A–E、修正提交与代码 SHA（见 `worker-reports/M07-report.md`）；精确 SHA CI 因未授权推送而待办；无设备，原 Task F 清单原样保存，继续 M08 代码/自动检查。

### R5 范围修订：M07 退出条件 7「Partial-batch repair」

**原假设：** 生产路径存在 indexed/boundary 批次模式，D4–D6 只需复用 `BoundaryProtocolParser`/`BoundaryValidator`/`AiSegmentationCoordinator` 把连续覆盖映射成已有的 unit/final result。

**实际情况（本地证据）：**

1. `BoundaryProtocolParser`、`BoundaryValidator`、`DeterministicSegmentationFallback` 的唯一其他引用是 `AiSegmentationCoordinator` 自己；`AiSegmentationCoordinator`、`StatisticalSentenceBreaker`、`SegmentationMetrics`、`AsrTimingEstimator` 在生产代码中没有调用者，只被 `segmentation/BoundaryProtocolTest.java` 引用。
2. `BoundaryProtocol.encodeItem` 全仓库（含测试）没有任何调用者；现有测试直接手写 `"v2|0-0|ONE"` 字面量。
3. 生产传输返回整段自由文本：两个协议适配器都读取单一翻译字符串，不做边界解析；`TranslationScheduler` 逐个 `TranslationUnit` 派发，响应覆盖整个 unit 或失败，没有“批次部分成功”的响应形态。
4. 内置 indexed Prompt（`builtin.subtitle.indexed`）要求模型“返回带 unit 索引的译文”，并不要求 `v2|start-end|text` 线格式，因此即使选中它，输出也不会被 `BoundaryProtocolParser` 接受。
5. 索引基准在请求端与校验端不一致：请求把 `unit_index` 填成该 unit 的首个**绝对** segment 索引，而 `BoundaryValidator` 按 `sourceTexts` 的**位置**校验并要求首项 `startIndex == requestedStart`。
6. 桥接与缓存对每个 unit 只暴露一段文本（`AiSubtitleCueBridge.findOrRequest` → `getCachedTranslation(unit)`），尾部子 unit 的译文没有可以呈现的装配路径。

**结论：** 在没有生产触发形态、没有一致索引契约、也没有呈现路径的前提下实现批次修复，属于为不存在的调用者预建机制，正是本计划与 Ponytail 明确禁止的做法。因此 M07 退出条件 7 拆成两部分记录：**Source-Only Fallback 已实现并有断言**；**partial-batch repair 未实现**，保持未完成标记。

**重启条件（确切的解除阻塞前提）：**

1. 有一个 Prompt 的输出契约就是 `v2|start-end|text` 线格式（新增内置 Prompt Profile 或版本化现有 indexed Prompt，并按既有机制让 contentHash/version 参与缓存身份）。
2. 请求与校验统一索引基准（要么请求下发 unit 区间，要么校验前把绝对索引平移为位置）。
3. 明确尾部修复的呈现方式：要么 `TranslationResult` 增加可表达子区间的覆盖，要么桥接增加按 segment 装配 unit 文本的路径。

在此之前的设备验收步骤：若某视频出现“选了 A 轨却翻译了同语言的另一条轨”，记录该视频的 track identity 与实际字幕列表（人工/ASR 的显示名与语言码），作为是否需要第 3 条宿主数据的证据。

## 10. 顺序、提交边界与恢复规则

| 顺序 | 单元 | 退出后进入 |
|---|---|---|
| 1 | R0/R1/R2 完成 Task E | R3 字幕源正确性 |
| 2 | R3、R4、R5 各自可审阅修正 | M07 实现与自动验证收口 |
| 3 | M07 精确 SHA CI；Task F 按设备条件执行 | M08-A → F |
| 4 | M08 上下文、传输、两协议、草稿、回退、优化决策 | M09-A → F |
| 5 | M09 性能/安全/上游审计、完整设备矩阵、发布候选报告 | 只有全部必需证据齐全才完成 M09 |

执行中断时记录当前代码 SHA、脏文件、最后通过测试、实际失败、下一条命令和设备/CI状态。每个功能闭环作为提交单元；章节是顺序而非强制提交数，不能只交付无人调用的接口。推送、真实收费 Provider 调用、安装、发布沿用执行时已有授权，本次不触发。

## 11. 验证操作模板

本地避免中文路径 protoc 故障：复制 SmartTube 与已 checkout 子模块到新的 ASCII 临时目录（例如系统临时目录下 smartube-m07-m09），不要依赖 junction 或仅 overridePathCheck；比较源代码清单/哈希，排除旧 build 输出。禁止把临时副本覆盖回源工作区。JDK 路径从实际安装获取，不能用当前 JDK 21 跑 Gradle 7.5。

在副本根目录按受影响包运行：

```powershell
.\gradlew.bat :common:testStbetaDebugUnitTest --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.*' --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.integration.*' --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.scheduler.*' --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.source.*' --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation.*'
```

R4 加跑 prompt/translation/provider；M08/M09 见分计划。Robolectric 使用仓库既有 JDK 11 通道，不能把 skipped 写成通过。XML 汇总只统计该次执行实际生成/覆盖的 suite，防止陈旧报告混入。定向通过后不反复全量；里程碑候选由现有 `.github/workflows/ai-subtitle-validation.yml` 对精确 SHA 跑完整 common、lint、assemble。

## 12. 最终交付定义

- M07、M08、M09 各一份 worker report，包含真实 diff 清单、代码 SHA/文档 SHA、CI run/产物哈希、测试计数、设备结果与剩余限制。
- M08 持久缓存/摘要默认不实施，在 M08 报告写明缺少收益证据及重启条件即可；没有运行实验就注明未实验，可选实验不阻塞 M09。
- M09 无未解决的播放/字幕/密钥/过期响应正确性缺陷；上游补丁清单与实际 diff 一致；设备矩阵不能全靠 fake provider 替代。
- 本次规划只更改文档；所有执行复选框保持未完成。

---

## 13. 执行记录（2026-09-14）

本节是 R0–R5 的实际执行结果；上方各节的复选框已按本记录勾选。详细证据见
[worker-reports/M07-report.md](../worker-reports/M07-report.md)。

| 项 | 结果 |
|---|---|
| 起始断点 | `38604559d6f343ca09913e4767dff2131b8267e5`（M07 Task D），工作区 10 个生产/资源文件 + 2 个文档文件未提交 |
| R0 编译基线 | 实际编译暴露两个阻断（未闭合字符串 + `RuleSentenceBreaker` 静态方法引用实例字段），均已修复 |
| R3 提交 | `a73ce61cb` — `fix(ai-subtitle): resolve the selected subtitle track instead of the first match` |
| R0/R1/R2/R4 提交 | `468d77839` — `feat(ai-subtitle): complete M07 Task E settings wiring and send the selected prompt` |
| R5 文档提交 | 本文件 + `progress.md` + `worker-reports/M07-report.md` |
| 验证环境 | 新 ASCII 副本 `C:\tmp\smartube-m07-m09`（含已 checkout 子模块，无 junction、无 overridePathCheck、未复用旧构建产物） |
| JDK 17 全量 common | `gradlew.bat :common:testStbetaDebugUnitTest` → BUILD SUCCESSFUL；45 suites，tests=388，failures=0，errors=0，skipped=20 |
| JDK 11 设置通道 | `gradlew.bat :common:testStbetaDebugUnitTest --tests '....ai.subtitle.settings.*'` → BUILD SUCCESSFUL；11 suites，tests=78，failures=0，errors=0，skipped=0 |
| 运行中发现并修复的真实失败 | 编译阻断 2 处；JDK 11 上 `explicitSaveCarriesVersionAndConflictKeepsDraft` 与 `failedProviderSaveRollsBackPrompt` 因表单缺少新增必填字段失败 |
| CI | 未运行：本提示未授权推送，精确 SHA 的 Actions 运行待授权 |
| 设备 | 无设备；M07 Task F 全部条目保持待验，未标通过 |

### 与计划不符之处的修订

1. **R2 的手机拒绝语义**：计划只要求「无效输入不修改其中任何一项」。执行中发现无效值与版本冲突共用同一条 409 提示，手机页面会把「输入无效」显示成「页面已过期」。最小修订：`applyForm` 返回三态（applied/stale/invalid），无效值返回独立的 `invalid` 状态并新增对应提示；未改动既有冲突路径。
2. **R4 的 PromptRenderer 空值语义**：计划要求把 `context` 填成空字符串，但既有渲染器把「空值」等同于「缺失变量」，会让任何引用 `{{context}}` 的 Prompt 直接判为 malformed。最小修订：渲染器改为区分「键缺失」与「显式空值」；`validate()` 行为不变，既有测试仍通过。
3. **R5 的批次修复**：见 §9 的范围修订；保留未完成标记，未删除 M07 退出条件 7。

### 下一步入口

按 §10 顺序进入 **M08-A**：先读 [M08-plan.md](M08-plan.md) 与 [m08-reuse 调查](../research/2026-09-14-m08-reuse.md)，再读本记录与 M07 报告。M07 的精确 SHA CI 与设备验收保持待办，不阻塞 M08 实现与自动检查。M08 的上下文必须建立在本次落地的
`PromptRenderer.render` + `TranslationRequest.renderedPrompt` 路径上，不要另建一条请求构建路径。
