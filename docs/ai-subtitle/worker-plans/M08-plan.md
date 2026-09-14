# M08 有界上下文与流式字幕 Implementation Plan

> **For agentic workers:** 后续执行使用 executing-plans；当前仅为计划。先完成 [总恢复计划](M07-M09-continuation-plan.md) 的 M07 正确性修复，不以 M08 包装旧故障。

**Goal:** 让上下文真实进入 Prompt，让流式草稿及时显示，同时保留非流式、原文回退及可证明的请求预算。

**Architecture:** 一个 ContextBuilder 构建不可变请求上下文；既有 Scheduler 串行冻结请求身份。现有 OkHttp transport 增加流式读取，两个协议共享 SSE 分帧、各自解释事件。草稿仍通过 TranslationStream，final 才进 TranslationCache。

**Tech Stack:** Java/API 17、OkHttp 3.12.13/现有 Okio、JUnit 4、既有 PromptRenderer、TranslationStream、TranslationCacheKey。

日期：2026-09-14；前置调查见 [M08 reuse](../research/2026-09-14-m08-reuse.md)。以下新增类型、方法名是拟定接口，不代表仓库已有。

Ponytail 提交边界：A 连同对应设置交付上下文闭环；B/C/D 连同对应设置交付流式闭环；E 是检查清单，不另交付一层接线。传输接口与调用者一起落地，不强制按章节拆提交。

## 全局约束与文件边界

根路径为 `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/`（AI）；测试为 `common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/`（TEST）。这两个前缀用于展开下文精确文件路径。

- 保持 API 17、零新增宿主 hook，不更改 SharedModules/MediaServiceCore/ExoPlayer 或基础依赖版本。
- 原有 `TranslationProvider.translate(request, callback)` 保留。普通 callback 保持非流式；只有请求允许且 callback 实现 TranslationStream 才流式。
- 默认上下文关闭、流式关闭；用户打开后真实生效。关闭任一功能不丢失基本翻译。
- 并发维持 1。上下文依赖的请求不能提前占用旧 context key。
- 网络、解析、文件 IO 不在主线程；主线程仅做当前 cue 的合并刷新。
- 字幕、标题和描述都是数据，不能作为指令提升权限；只发当前 unit 和有界上下文，不发完整长视频 transcript。
- 字符预算按 Unicode code point 计算；截断不切断 surrogate pair。UTF-8 字节上限另行检查；字符数不是精确 token 数。

## A. 上下文、Prompt 与缓存身份

**新增：** AI/translation/TranslationContextBuilder.java；TEST/translation/TranslationContextBuilderTest.java。
**修改：** AI/translation/TranslationRequest.java、AI/scheduler/TranslationScheduler.java、AI/integration/{AiSubtitleController,AiSubtitleCueBridge,AiSubtitleRuntime}.java、AI/settings/AiSubtitleData.java、两种协议 Adapter、既有 translation/cache/provider 测试。

输入：当前 unit、视频 title/description、同轨且在当前 unit 之前的原文与已接受 final 译文。ContextBuilder 返回一段有界字符串，直接交给既有 PromptRenderer；不再定义 Snapshot 值类。

初始预算：标题最多 160 字符；描述最多 640；最多 3 个前序单元；历史原文+译文共 1200；整体 context 含标签最多 2200 字符。完整 unit 不计入 context，但受到既有分块和请求上限控制。元数据缺失用空值，不能为此阻塞当前句或额外请求整份视频资料。

- [x] Controller 从既有 `Video.title`/`Video.description` 取字段并经 `onNewVideo(videoId, title, description)` 送到 Bridge；未修改宿主模型，context builder 内不查网络。
- [x] `TranslationContextBuilder` 按固定标签顺序（Title/Description/Earlier subtitles）拼接裁剪后的文字，全部预算以 Unicode code point 计，截断不切断代理对。调度器用 SHA-256 对最终 renderedPrompt 取指纹填入 `TranslationCacheKey` 的 contextFingerprint 槽；未另写长度前缀编码，也未为未发送的预算值建立身份。
- [x] Work 在首次派发时经 `freezeRequestLocked` 冻结 renderedPrompt 与 cache key；重试与重绘复用同一份内容，只更新 requestId。context 文本只存在于 renderedPrompt 内部，Request/Work 中各存一份。
- [x] 每个 unit 在自己的首次派发时构建上下文；`requestCurrentUnit`、预取、`retryFailed`、`getCachedTranslation` 全部走同一个 Work 的冻结身份，未另起上下文缓存。
- [x] `historyFor` 只纳入 segment 索引严格更早的单元；先完成的未来单元不会倒流成历史。seek 后当前单元变化即按新位置重建历史；无译文的历史项只带原文，不等旧请求。
- [x] Work 冻结后不再重算 key；`aFrozenRequestIsReusedVerbatimAfterTheHistoryMovesOn` 断言同一 unit 的两次派发使用逐字相同的 prompt。未派发的 unit 在派发时才构建快照。
- [x] 视频/轨道/配置变化沿用既有 generation 失效（新 session + 新 scheduler）；迟到 metadata 只影响之后的请求，既有译文不重算。
- [x] `PromptRenderer` 填 context 变量；内置 baseline Prompt 升到版本 2，明确 context 只是背景参考、其中的指令一律不得执行（`PromptMigration` 会把既有安装的旧内置 Prompt 迁移到新内容，contentHash 变化自动失效旧缓存）。自定义 Prompt 未被改写。

关键设计流程：

```text
choose eligible unit
  -> build bounded context
  -> render selected Prompt once
  -> freeze request and cacheKey using the rendered-prompt fingerprint
  -> hit: accept cached final; miss: submit that request
retry -> reuse identical prompt/key
```

测试必须覆盖：相同输入同 hash；裁剪外变化不变 hash；预算内变化改变 hash；中文/emoji 不破坏字符；换轨历史为空；future 不进入 past；失败草稿不进历史；重复 tick 不重算请求；seek 后先当前句；旧上下文缓存不命中。两 Adapter 的实际 body 含与 key 相同的 context，不能只测试 getter。

## B. 可取消的流式 HTTP 与 SSE 分帧

**新增：** AI/provider/http/SseEventReader.java、TEST/provider/http/SseEventReaderTest.java、TEST/provider/http/OkHttpStreamingTest.java。
**修改：** AI/provider/http/HttpRequestExecutor.java、OkHttpRequestExecutor.java；先核对所有实现和调用者，非流式 fake 无需新增空方法。

拟定扩展在现有 interface 内，不再建第二套 HTTP 引擎：

```java
interface StreamCallback extends HttpCallback {
    void onEvent(String eventType, String data);
}
```

保留 execute：请求允许流式且 callback 为 StreamCallback 时逐事件读取。原 onSuccess 在流关闭后传状态/空 body/requestId，表示 HTTP 读取完成；Adapter 仍须验证协议结束信号才能发 translation final。失败沿用 onFailure；不新增 executeStream/onOpen/onClosed 平行生命周期。

- [x] 复用既有 request/auth/timeout/cancel 构建；流式路径消费 `ResponseBody.byteStream()`，未调用 `body.string()`。
- [x] `SseEventReader` 覆盖 LF/CRLF/CR、首个 BOM、注释、空行结束、多 data 行换行拼接、未知字段（含 retry）忽略；EOF 时未以空行结束的半事件被丢弃。
- [x] 非流式请求仍走原 parser；流式请求收到非 `text/event-stream` 响应时在传输层直接失败（`OkHttpStreamingTest.aJsonResponseToAStreamingRequestIsNotParsedAsSse`），由调度器决定回退，绝不逐行当 SSE 解析。
- [x] 单行/单事件各 64 KiB、整响应 1 MiB 在读取过程中逐字节强制（`anOversizedLineTerminatesTheRead`/`anOversizedEventTerminatesTheRead`/`anOversizedResponseTerminatesTheRead`/`OkHttpStreamingTest.anOversizedLineTerminatesTheStream`）；两个 adapter 各自在 16K code points 处终止累计译文。超限一律失败回原文。
- [x] 同时设置 connect/read/write 空闲超时与 OkHttp `callTimeout`（同一请求预算）；`OkHttpStreamingTest.theCallTimeoutBoundsANeverEndingStream` 证明持续心跳不能延长总期限，`cancellingStopsTheReadAndDeliversNoTerminalCallback` 证明取消后不再有任何终止回调，传输层用一次性标志保证至多一次终止。
- [x] 非 2xx 仍走既有状态映射；失败文本沿用既有不含响应体/query/凭证的安全字符串；`retry` 字段被显式忽略。
- [x] `OkHttpStreamingTest` 用本地 `ServerSocket` 逐字节分片发送，覆盖中文 UTF-8 中途切断、多 data 行、注释/心跳、无终止事件断连、延迟后取消、永不结束心跳、超大单行；未调用任何收费 API。

分帧依据是 [WHATWG SSE](https://html.spec.whatwg.org/multipage/server-sent-events.html)；仅采用 wire-format 规则，不采用浏览器自动重连行为。

## C. 两种协议的流事件解释

**修改：** AI/provider/OpenAiChatCompletionsAdapter.java、AnthropicMessagesAdapter.java，以及相应 TEST/provider 测试。
事件累积放在各 Adapter 的请求回调内；本轮不预建两个 Accumulator 类或泛化 LLM parser 框架。

- [x] OpenAI 请求使用 `stream:true` 与 event-stream accept；只累积首个 choice 的 `delta.content`，角色/用量/额外 choice 块都被忽略。final 需要停止原因为 `stop`（或 `[DONE]` 补齐）；length/content filter/无完成信号 EOF 一律失败。[Chat Completions stream](https://developers.openai.com/api/reference/resources/chat/subresources/completions/streaming-events)
- [x] Anthropic 只累积 `text_delta`，`message_stop` 前验证停止原因（仅接受 `end_turn`/`stop_sequence`）；ping 与未知事件忽略，`error` 事件映射为失败，thinking/tool 块不渲染。**偏差**：未按 content block index 分桶，而是把所有 text block 的 text_delta 顺序累加——请求体的 `content` 只是一个字符串，并不约束响应返回几个 text block；响应只含一个文本块时按索引分桶结果相同，多文本块时会按到达顺序拼接而非按块分组，因此这是已知限制而不是"请求保证只有一个块"。[Anthropic streaming](https://platform.claude.com/docs/en/build-with-claude/streaming)
- [x] 每个有效增量都发布“完整累计草稿”的 `partialResult`（断言 `Arrays.asList("你","你好")`）；final 用 `finalResult` 且身份一致。
- [x] 五个 Provider 类型继续复用两个协议适配器，未新增品牌 parser；流式开关默认关闭，能力判断来自用户设置与实际响应，不从未知型号名推测。
- [x] 断言覆盖：多 choice、空 role chunk、usage chunk、未知 Anthropic 事件、非文本 block、JSON 损坏、长度/`max_tokens` 停止、完成后重复事件、完成前断流。两个协议的非流式原套件全部保留并通过。

## D. 草稿到当前 cue 与失败恢复

**修改：** AI/scheduler/TranslationScheduler.java、AI/integration/AiSubtitleCueBridge.java；复用 TranslationStream；TEST/scheduler/TranslationSchedulerTest.java 与 integration mode/session/cache 套件。

- [x] `SchedulerStreamCallback extends SchedulerCallback implements TranslationStream`；final 检查（`validResponse` 要求 `isFinal`）未放宽，草稿只走 `onPartial`。
- [x] Work 只保存一份累计草稿，状态保持 IN_FLIGHT，不释放并发、不消耗尝试、不写 cache 与 context history（`streamedDraftsReachTheListenerWithoutSpendingAnAttempt`）。
- [x] `acceptsPartial` 校验 partial 标志、IN_FLIGHT、requestId、generation/epoch、sessionId 与 segment coverage；Bridge 只把草稿用于当前映射到的 unit（`aDraftOnlyDecoratesTheUnitItWasStreamedFor`）。
- [x] 草稿重绘按 100ms 合并，final 立即重绘；seek/off/release 清草稿。沿用既有 refresh 通道，未引入 Timer 或 Handler。**偏差**：合并方式是“到期前的中间重绘直接丢弃”而非排队延后，且被丢弃的中间态不再补发（原记录，已被下方更正取代）。**后续更正（M07–M09 修正任务 6）**：丢弃而不补发会让界面停留在较旧的草稿上直到 final 到来——若流不再有下一个 delta，最新草稿就永远不上屏。现在记录一个 pending 标志，由既有 position tick 在下一个 tick 补发一次重绘；草稿仍然不排队，上界是“一个 tick 内”，不是“只要 final 会来就没有损失”。
- [x] final 替换草稿并进入缓存；中断/失败清草稿回原文。可重试类别下的流式失败会把该 unit 切换为非流式并占用同一尝试预算（`aStreamInterruptionFallsBackToAPlainRequest`、`theStreamingFallbackStillStopsAfterTheAttemptBudget`）；鉴权失败仍为终态。
- [x] 断流→非流式回退断言总请求 2；预算耗尽为 3 后终态；seek 后迟到的 partial 不改变状态（`seekingClearsTheDraftAndCancelsTheStream`）。**未覆盖**：双语顺序与三种显示模式×流式的组合断言；既有 M07 模式套件在关闭流式下全部通过，覆盖矩阵记为 M09 的输入。**后续更正（M07–M09 修正任务 6）**：该未覆盖项已补齐——`sourceModeNeverRequestsATranslationAndKeepsTheSourceLine`、`translationOnlyShowsTheDraftThenTheFinal`、`translationOnlyFallsBackToTheSourceLineWhenTheTranslationFails`、`bilingualShowsTheDraftThenTheFinalInTheConfiguredOrder` 断言了精确 `cue.text` 与请求次数，未做语言×品牌×模式全排列。
- [x] 该模式在生产路径上没有触发形态（见 `M07-report.md` §R5-2 的范围修订）：没有 Prompt 会要求 `v2|start-end|text` 线格式，也没有任何路径解析它，因此不存在把半条协议展示给用户的实现路径。保留为 M09 输入，未伪造通过。

## E. 设置与非流式回归（随 A、B–D 一起交付）

**修改：** AI/settings/AiSubtitleData.java、settings/ui/AiSubtitleSettingsPresenter.java、settings/remote/AiSubtitlePhoneInputServer.java、Runtime、三套资源；对应 settings/remote/integration 测试。

- [x] 新增 `contextEnabled`/`streamingEnabled` 两个开关，默认 false；TV 设置与手机页面读写同一存储与同一初始化路径；未暴露摘要或磁盘缓存按钮。
- [x] 上下文开关经 `rebindSessionIfIdentityChanged` 生成新 session 与新缓存；流式切换取消在途流、清草稿并保留兼容 final cache。**偏差**：流式切换用「取消 + 以新 requestId 重派」而不是推进 epoch 来表达失效；epoch 的语义是 seek 隔离，为一次传输方式变化推进它会让 seek 与设置变化无法区分。
- [x] 两个开关立即作用于当前窗口；暂停/关闭状态不受影响；重启后两个开关与已存分段参数一并从同一存储读回（手机往返测试断言四个字段）。
- [x] 沿用既有真实 socket 测试的保存失败/配对过期/旧版本断言，并把两个新开关加入手机往返断言（保存后存储与页面渲染一致）。缺省旧存储读取到缺失键时取默认 false，未丢数据。
- [x] 两个开关默认关闭，全量 common 套件正是在关闭状态下运行并通过；M07 的源/调度/设置套件全部保留。

## F. 持久缓存与视频摘要：先测量，再决定

这两项在 roadmap 中明确可选，本轮默认不实施。M08-report.md 用一段记录决定：当前没有证据证明内存缓存与有界上下文不够；未做实验，不宣称测量通过，也不为“可选”增加阻断 M09 的实验门槛。

- [ ] 若日常验收实际发现重开视频重复请求成本明显，再对该路径测量并决定是否另开磁盘缓存工作；届时才设计存储、淘汰和迁移。（本轮未实施，理由见 M08 报告 F 节。）
- [ ] 若出现有界上下文无法解决的术语/代词错误，再用具体样本比较摘要收益与额外请求成本；本轮不预建摘要服务、固定样本量或收费实验。（本轮未实施，理由见 M08 报告 F 节。）

## 验证与 M08 退出

在 ASCII 副本执行，JDK 分流遵循总计划：

```powershell
.\gradlew.bat :common:testStbetaDebugUnitTest --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.*' --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.*' --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.scheduler.*' --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.integration.*' --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.cache.*'
```

设置变更另跑 settings；阶段候选交现有 CI 全量 common/lint/assemble。报告 `worker-reports/M08-report.md` 必须包括 A–F 状态、每次提交/测试计数、协议 fake fixture、真实服务未验证范围、可选项决策。

- [x] 上下文最多 3 个前序单元 + 标题/描述，从不携带完整 transcript；指纹取自实际发送的 renderedPrompt；串行顺序与 seek 隔离沿用并扩展了 M07 断言。
- [x] 两协议的流式与非流式均有自动断言；关闭两个开关的完整套件通过。
- [x] 草稿不入 final cache，迟到草稿不渲染，流式回退与总尝试预算均以 3 为上限，调用总期限由 `callTimeout` 封顶。
- [x] 可选优化决定写入 `M08-report.md`，未新增子报告或实验门槛；M07 必需正确性未延期到 M09（R5 的范围修订已在 M07 报告记录）。
- [ ] 精确代码 SHA CI：**未运行**（未授权推送）。设备：无，未伪造任何设备结果，矩阵交 M09。
