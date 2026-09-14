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

- [ ] Controller 从既有 Video.title/description 或已到达的 metadata 获取字段，按 videoId 送到 Bridge；不修改宿主模型，不在 context builder 内查网络。
- [ ] ContextBuilder 按固定标签顺序拼接裁剪后的文字。用 SHA-256 对最终 renderedPrompt 取指纹，填既有 cache key 的 contextFingerprint 槽；不另写长度前缀编码或为未发送的预算值建立身份。
- [ ] Work 首次派发前冻结已有 TranslationRequest（含 renderedPrompt）和 cache key；不在 Request、Snapshot、Work 中各存一份 contextText。重试复用 request 内容与 key，只更新必要的请求尝试身份。
- [ ] 下一 unit 等前一 final 后构建自己的上下文；requestCurrentUnit、预取、retry、getCachedTranslation 共用该 Work 的冻结身份，禁止另起一套上下文缓存。
- [ ] 只纳入时间/索引上更早的 final；未来预取完成的单元不能倒流成为当前句的历史。seek 后按新位置重建历史，缺失译文只带原文，不等待无关旧请求。
- [ ] 同一 unit 已生成的工作保留其冻结 key；重绘不随滚动历史变化重新计算 key 而重复请求。尚未派发的 unit 可以在派发时构建更新快照。
- [ ] 配置、视频、轨道变化清 context 工作状态；已知输出内容变化沿既有 generation 失效。迟到 metadata 只影响之后的新请求，不让整段既有译文反复重算。
- [ ] 用 PromptRenderer 填 context 变量；系统提示明确 context 是参考资料，源字幕中的指令当待翻译文本处理。内置 Prompt 更新版本，自定义 Prompt 不强行重写；不含 context 变量的自定义 Prompt 在设置中说明不会使用该字段。

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

- [ ] 复用 OkHttpRequestExecutor 的 request/auth/timeout/cancel 构建；stream 路径消费 ResponseBody.source/Reader，不调用 body.string()。
- [ ] SseEventReader 只做 UTF-8 行与事件分帧：支持 LF/CRLF/CR、首个 BOM、注释、空行结束事件、多个 data 行用换行拼接；未知字段忽略。EOF 时未被空行结束的半事件丢弃，不能伪造完整事件。
- [ ] 普通 JSON 响应仍走原非流式 parser；请求 SSE 却收到错误 MIME，作为能力/协议失败交 Scheduler 决定回退，不能把 JSON 逐行当 SSE。
- [ ] 初始上限：单行和单事件各 64 KiB；整个响应 1 MiB；累计译文 16K code points；超限终止，原文回退。长行不能先无限 readLine 再检查。
- [ ] 同时设置连接/读取空闲超时和整个调用期限，默认沿用现有 30s 请求预算。心跳不能无限延长总期限；取消关闭 Call/Response，read loop 停止；终止回调最多一次。
- [ ] 429/5xx/鉴权错误沿已有映射；失败文本不包含响应体、URL query、Authorization 或 API key。不得使用 SSE 的 retry 字段绕开 Scheduler 重试预算。
- [ ] 用本地 fake HTTP server 分片发送：中文 UTF-8 中间切断、data 多行、空行/心跳、无终止事件断连、延迟后取消、永不结束心跳、超大单行；不调用收费 API。

分帧依据是 [WHATWG SSE](https://html.spec.whatwg.org/multipage/server-sent-events.html)；仅采用 wire-format 规则，不采用浏览器自动重连行为。

## C. 两种协议的流事件解释

**修改：** AI/provider/OpenAiChatCompletionsAdapter.java、AnthropicMessagesAdapter.java，以及相应 TEST/provider 测试。
事件累积放在各 Adapter 的请求回调内；本轮不预建两个 Accumulator 类或泛化 LLM parser 框架。

- [ ] OpenAI request 使用 stream=true 和 event-stream accept；只累积目标 choice 的 delta.content，角色/用量空块不能成为字幕。常规 stop 与完成信号组合成一次 final；length/content filter/无完成信号 EOF 不能静默当完整成功。[Chat Completions stream](https://developers.openai.com/api/reference/resources/chat/subresources/completions/streaming-events)
- [ ] Anthropic 按 block index 管理 text_delta，message_stop 前验证停止原因；忽略 ping/未知事件，error 映射到已有 failure。thinking/tool JSON 不渲染成字幕；未支持的输出类型不能伪装文本成功。[Anthropic streaming](https://platform.claude.com/docs/en/build-with-claude/streaming)
- [ ] 每个有效文本增量输出“完整累计草稿”的 TranslationResult.partialResult；最后成功用 finalResult，同一 request/segment 身份。禁止把 delta 片段当完整字幕替换造成只剩最后几字。
- [ ] OpenRouter/DeepSeek/MiMo 复用其既有协议，不额外建品牌 parser。未知模型能力默认非流式；明确开启失败时可以回到普通请求，不从型号名字推测能力。
- [ ] 按假流验证多个 choice、空 role chunk、usage chunk、未知 Anthropic event、非文本 block、JSON 损坏、长度停止、完成后重复事件、完成前网络异常。非流式两协议原套件仍通过。

## D. 草稿到当前 cue 与失败恢复

**修改：** AI/scheduler/TranslationScheduler.java、AI/integration/AiSubtitleCueBridge.java；复用 TranslationStream；TEST/scheduler/TranslationSchedulerTest.java 与 integration mode/session/cache 套件。

- [ ] SchedulerCallback 实现 TranslationStream.onPartial；M07 对“onSuccess 收到 partial”的错误仍终止。合法草稿只来自 onPartial，不能放宽 final 检查以混淆两者。
- [ ] Work 最多保存一份累计草稿，继续 IN_FLIGHT，不释放并发、不增加 retry、不写 final cache/context history。
- [ ] partial 校验 sessionId/generation/epoch/requestId/segment coverage 和活动 Work；Bridge 仅为当前时间覆盖的 unit 渲染。未来 unit 草稿不抢当前画面。
- [ ] UI 最多每 100ms 合并刷新一次；final 立即刷新；seek/off/release 清草稿并移除待执行刷新。使用 Controller/Bridge 既有 main-thread refresh，不引入永久 Timer。
- [ ] final 替换草稿并缓存；流中断清草稿回原文。只有在计划确认的 transient/不支持流式类别下才回退非流式，回退占用原逻辑请求剩余尝试数；总计最多 3 次，鉴权失败不重试。
- [ ] 初次 SSE 断流→一次非流式完成的 fixture 断言总请求 2；预算耗尽=3 后原文；seek 后迟到 partial/final/error 都不改变 UI/cache；双语顺序/三模式/关闭 streaming 同样覆盖。
- [ ] indexed 边界模式不把半条 `v2|...` 协议展示给用户：完整边界可验证后才展示对应译文，否则保持原文直到 final。

## E. 设置与非流式回归（随 A、B–D 一起交付）

**修改：** AI/settings/AiSubtitleData.java、settings/ui/AiSubtitleSettingsPresenter.java、settings/remote/AiSubtitlePhoneInputServer.java、Runtime、三套资源；对应 settings/remote/integration 测试。

- [ ] 添加两个真实开关 contextEnabled/streamingEnabled，默认 false；TV/手机共享读写与初始化路径。不要同时暴露尚未实现的摘要/磁盘缓存按钮。
- [ ] 上下文开关改变输出身份，重新生成 session；流式切换取消活动工作/推进既有 epoch，清草稿，保留兼容 final cache。不要为 transport-only 选项制造不必要缓存失效。
- [ ] 当前句立即按新模式处理，但暂停仍暂停、关闭仍关闭。重启后两开关与已存分段参数一致。
- [ ] 保存失败、配对过期、页面旧版本、手机改值后 TV 显示、TV 改值后手机重开全部测试；缺省旧存储不迁移丢数据。
- [ ] 关闭 context+streaming 跑 M07 核心回归，证明优化可关闭。

## F. 持久缓存与视频摘要：先测量，再决定

这两项在 roadmap 中明确可选，本轮默认不实施。M08-report.md 用一段记录决定：当前没有证据证明内存缓存与有界上下文不够；未做实验，不宣称测量通过，也不为“可选”增加阻断 M09 的实验门槛。

- [ ] 若日常验收实际发现重开视频重复请求成本明显，再对该路径测量并决定是否另开磁盘缓存工作；届时才设计存储、淘汰和迁移。
- [ ] 若出现有界上下文无法解决的术语/代词错误，再用具体样本比较摘要收益与额外请求成本；本轮不预建摘要服务、固定样本量或收费实验。

## 验证与 M08 退出

在 ASCII 副本执行，JDK 分流遵循总计划：

```powershell
.\gradlew.bat :common:testStbetaDebugUnitTest --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.translation.*' --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.*' --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.scheduler.*' --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.integration.*' --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.cache.*'
```

设置变更另跑 settings；阶段候选交现有 CI 全量 common/lint/assemble。报告 `worker-reports/M08-report.md` 必须包括 A–F 状态、每次提交/测试计数、协议 fake fixture、真实服务未验证范围、可选项决策。

- [ ] 不携带完整 transcript；context 与 key 一致；串行顺序和 seek 隔离有证据。
- [ ] 两协议流式/非流式自动验证完成；关闭优化仍正确。
- [ ] 草稿不入 final cache、迟到不渲染、重试和总超时有界。
- [ ] 可选优化决定写入本阶段报告，无额外子报告/实验门槛；M07 必需正确性没有被延期到 M09。
- [ ] 精确代码 SHA CI 通过；设备不足不伪造验收，待执行矩阵交 M09。
