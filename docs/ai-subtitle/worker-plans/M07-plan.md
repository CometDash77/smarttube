# 当前 Bug 修复与 M07 前瞻调度 Implementation Plan

> **For agentic workers:** 按 `executing-plans` 逐项执行并更新复选框；用户选择子代理方式时使用 `subagent-driven-development`。`ponytail full` 持续生效：理解完整调用链，复用优先，最小实现，留下能发现真实故障的验证。

**Goal:** 修复已确认的手机配置编辑和电视测试反馈问题，接通完整字幕时间线与 M06 处理链，完成 M07 调度及设置，然后统一真机验收。

**Architecture:** 沿用现有 Provider、Prompt、SecretStore、TranslationSession、缓存与播放器控制器；仅为缺失的字幕源适配和窗口调度补少量功能内代码。seek 使用现有 epoch，身份变化使用现有 generation，不建立第二套状态机。真机验证默认排在 M07 实现与自动检查之后；有证据表明某个前置 bug 使后续功能无法验证时，先修复并验证该依赖，再继续受影响任务。

**Tech Stack:** Android/API 17，现有 Java、Gradle 7.5、JUnit 4、OkHttp、RxJava 和 Android Handler；构建 JDK 17，已有 JDK 11 补充测试通道。

修订日期：2026-09-13。本文替换旧版 M07-plan 的 Gate 0、重复类设计及提前真机门槛。
本轮交付是计划修订，不代表以下修复已实施或 M07 已通过。


## Execution checkpoint — 2026-09-13 (Task A complete)

- Working tree: Task A code/test are ready for review. `AiSubtitlePhoneInputServer`, `AiSubtitleSettingsPresenter`, and `ProviderProfilesPresenter` were updated; the new real-socket remote server test is present. The M07 plan and progress ledgers are updated in the same review unit. No submodule or KissTranslator files were changed.
- Task A implementation: local edit with explicit save/test, no `/update` auto submit, no state-field overwrite, `?k=` pairing on every POST, strict version-equality conflict handling, provider/protocol/base URL/model/target language/prompt fields, keep/replace/clear secret actions, existing repository save path, real `testTranslationConnection`, prompt/provider cross-store rollback, main-thread TV UI, and suppression of late results after cancel/close.
- Task A automatic verification passed on local JDK 11: focused `settings.*` run => 11 XML suites / 74 tests / 0 failures / 0 errors / 0 skipped, `BUILD SUCCESSFUL in 50s`. The remote test exercises the real socket routes and explicit page script route names.
- Task B implemented and automatically verified: `SmartTubeSubtitleSourceAdapter` and `SourceTimeline` now supply the full normalized timeline through VTT parsing, SubtitleNormalizer, RuleSentenceBreaker, and TranslationChunker; `AiSubtitleCueBridge` uses real segment IDs and time-based unit lookup when the timeline is available, falling back to displayed-cue mapping otherwise.
- No device or CI verification has been run for these changes. Task A is code/automatic-test complete, not device-accepted.
- Next action: commit Task B as one reviewable unit, then proceed to Task C.

## 1. 执行优先级与全局约束

1. 用户最新四条要求优先于旧计划：真机验证延后；串行阻塞例外；不重复造轮子；说明详细、实现最小。
2. “可并行”表示依赖允许独立推进，不要求启动多个代理。不因为没有电视而暂停手机页面、保存校验、调度或已有自动测试。
3. 延后的是统一真机验收，不是已确认 bug 的修复。已知故障先做自动复现和根因修复；不要等用户再把已复现的问题复现一次。
4. 对阻塞项写清 `前置故障 → 受影响功能 → 自动验证是否足够 → 最小解锁检查`。只有最后一项必须依赖真机时，才提前做该范围的真机检查；不提前重跑整套验收。
5. 不修改两个 submodule、内嵌 ExoPlayer 源码或 Gradle 依赖版本。已有公共 API 可以调用；KissTranslator 只读，不复制 GPL 实现。
6. M07 默认零新增宿主文件修改。优先 `AiSubtitleController` 既有事件与源适配器；不按旧计划给 `PlaybackFragment` 再接一套生命周期。确有缺口先记录调用链证据和最小 hook 于 `upstream-patches.md`。
7. Java 4 空格、成员 m 前缀、API 17 兼容；不创建单实现接口、抽象工厂、通用任务框架或仅为一项常量增加设置系统。
8. 测试使用仓库现有框架和 fake provider。新增复杂逻辑留下最小可运行回归检查，不为私有方法逐个建测试文件，也不要求人为凑固定提交数。
9. 保存用户数据、密钥隔离、输入边界、失败回退、过期结果隔离不能简化。实现少写代码不等于少做这些保护。
10. M08 的上下文、SSE、持久翻译缓存仍留在 M08。不能用空设置项冒充已支持，也不能以“懒惰”为由删除本计划明列的 M07 功能。

## 2. 事实基线与依赖分流

依据：链接任务 `01a09a05-afd7-7f40-909b-a8f76578336f` 的用户反馈和审计结论，以及本轮读取的当前源码。r6 台账中的“代码完成”不能覆盖审计中已确认的故障。

| 项目 | 已有证据 / 实际缺口 | 先做什么 | 真机时机 |
|---|---|---|---|
| 手机编辑内容消失 | 轮询覆盖输入字段；旧任务已做页面脚本复现 | 改为手机本地编辑、显式保存，自动验证轮询不覆盖 | M07 后统一验证 |
| 手机更新/保存 401 | 页面 POST 漏带服务端要求的配对凭证 | 修复共享请求路径，保持未配对请求拒绝 | M07 后；服务端/页面测试先行 |
| Profile 不完整 | 页面只编辑电视当前 Profile 的几个字段 | 复用仓库选择/新建/保存 Profile，补 Provider、协议、语言、Prompt | M07 后 |
| “保存并测试”不测试 | 当前只保存；Prompt 失败结果也必须传播 | 接真实测试，分别记录保存和测试终态 | M07 后 |
| 电视测试没有反馈 | 单取消按钮触发菜单自动执行；空列表结果页；异步 UI 线程风险 | 用已有通知和真实内容页修复，测试取消/迟到回调 | M07 后；若它是后续唯一可用验证入口则提前验证该入口 |
| 提前翻译 | 当前 Bridge 只在显示 cue 后请求，并用 segment 0 临时标识 | 先接完整字幕源与稳定 segment，再接调度 | 源/映射为串行依赖；其余纯调度测试可独立推进 |
| 安装与 ABI | 历史有失败反馈，不能仅凭签名得出根因 | 保存原问题，最终核对匹配 ABI 包 | M07 后；若唯一验证设备安装/启动失败，先解决安装再测下游 |
| 台账互相矛盾 | M06 待验收与 r6“代码完成”并存 | 改为已确认故障、已修复自动通过、待真机三个事实状态 | 不需要设备即可修正 |

默认顺序：依赖盘点 → 手机/测试根因修复 → 字幕源及 M06 接线 → M07 调度与设置 → 自动检查/CI → 统一真机。
阻塞只阻塞依赖它的检查。例如：手机保存失败不阻塞 fake provider 调度单测；没有真实时间线不能宣称前瞻播放集成通过；只有 universal 能装时可以用它验功能，另留匹配 ABI 包验收记录。

## 3. 复用清单：先用已有能力，再补缺口

以下路径相对 SmartTube 仓库。为了让清单可读，`AI/` 精确定义为 `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/`，`TEST/` 为 `common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/`；它们不是要新建的目录。

| 能力 | 已核对实现 | 本轮策略 |
|---|---|---|
| 会话身份/代际/seek | `AI/session/TranslationSession.java` 的 `advanceEpoch()`、`owns(long,long)`、pause/resume；`TranslationSessionId`、`TranslationSessionSnapshot` | 直接使用；删除旧计划的 SchedulerSession、SessionIdentity、SeekKind 等第二套模型 |
| 缓存/去重 | `AI/cache/TranslationCacheKey.java`、`InMemoryTranslationCache.java`；Bridge 的 mInFlight 与 PendingRequest | 沿用 key 和缓存；调度接管请求时迁移同一份 in-flight 状态，不维持两套并发账本 |
| 请求/取消/失败分类 | `AI/translation/TranslationRequest.java`、`TranslationCall.java`、`TranslationFailure.isRetryable()` | 使用现有 adapter 和取消句柄；调度不解析 HTTP 文本、不再写 failure enum |
| 生命周期 | `AI/integration/AiSubtitleController.java` 已转发 play/pause/seek/track/video/release | 在此接位置和驱动；onSeekEnd 当前传 -1，改用播放器真实位置，未知值不当作 0 |
| 原始数据/断句/分块 | `AI/domain/SourceCue.java`、`SubtitleSegment.java`、`TranslationUnit.java`；`AI/source/SubtitleNormalizer.java`；`AI/segmentation/` | 接通已有 M06 算法；没有现成 SubtitleTimeline 类型，使用已有类型的 List |
| 保存/密钥/Prompt | `AI/settings/AiSubtitleData.java`、`ProviderProfileRepository.java`、`AI/settings/ui/ProviderProfilesPresenter.java`、`PromptProfilesPresenter.java` | 手机调用同一保存路径，不创建另一种配置文件格式、密钥仓库或云服务 |
| 优先级与计时 | Java 集合，Android Handler，现有播放器 onTickle 事件 | 有序列表够用就不加队列包装类；确需重排用 PriorityQueue；nowMs 显式传入，免造 Clock 框架 |
| 外部行为参考 | `../KissTranslator/src/subtitle/BilingualSubtitleManager.js`、`youtubeAiSegmentation.js`；既有 `architecture.md` §7–8 / ADR-005 | 参考前瞻行为与边界，不移植 JS 队列或引入新的调度依赖 |

本修订不重选已有架构，也不声称已完成新的外部候选库调查。执行字幕源适配时，先查现有依赖提供的格式加载/解析能力；若确实需要新组件或替换 HTTP 服务，再按项目 `prior-art-before-build` 检索该精确缺口，记录候选、许可证、拒绝原因和最小接入边界，之后定实现。不能以“看起来不难”为理由直接手写协议解析器。

预期新增生产文件仅两个职责：`AI/source/SmartTubeSubtitleSourceAdapter.java`（取选中轨及时间线）和 `AI/scheduler/TranslationScheduler.java`（窗口与请求安排）。已有等价实现则直接用；额外文件须说明具体独立职责。小 work item 可为内部类，窗口比较和重试延迟可为私有方法。

## 4. Task A：手机远程 Profile 编辑与测试反馈

**Files:** 修改 `AI/settings/remote/AiSubtitlePhoneInputServer.java`、`AI/settings/ui/AiSubtitleSettingsPresenter.java`、`ProviderProfilesPresenter.java`、必要的 `PromptProfilesPresenter.java`；沿用默认、简中、繁中 `common/src/main/res/values*/ai_subtitle_strings.xml`。测试优先扩展 `TEST/settings/ui/ProviderProfilesPresenterTest.java`；手机路径新增 `TEST/settings/remote/AiSubtitlePhoneInputServerTest.java`，页面脚本验证优先复用已有测试入口。

**可观察契约：** 手机本地编辑不会因轮询或测试回调丢失；保存成功能在电视的同一 Provider Profile 中读取；测试使用该配置真正发送最小翻译请求，手机和电视都能看见结果。

- [x] A1 阅读共享保存、HTTP 请求和测试回调的全部调用者，复现旧任务报告的五个页面断言：名称不被轮询改写、update/save 的鉴权、Prompt 名回填、401 不被误报为局域网断线。移除 update 路径时改测“编辑不提交，只保存提交”，不为旧接口保留无用实现。
- [x] A2 移除逐字自动提交与服务器轮询覆盖。只在初始化、明确切换 Profile、手动重新加载时填表；存在未保存内容时，切换/重载提示放弃。轮询仅更新连接及保存/测试状态。普通 HTML 表单加少量 JS 足够，不引入前端框架。
- [x] A3 下拉选择已有 Profile 或新建，字段包括名称、Provider、Custom 协议/地址、模型、目标语言、Prompt 选择/内容与密钥替换/明确清除。官方地址复用 ProviderPreset；旧 Custom 地址不得被初始化覆盖。保留、替换、清除密钥是三种不同动作。
- [x] A4 共享请求函数携带配对凭证；服务端保留会话、Host/Origin、大小、字段白名单校验。禁止明文密钥回传、日志记录或浏览器持久化。状态读取不返回旧密钥。
- [x] A5 保存携带加载时的版本，服务端在同一保存临界区校验版本与字段；旧版本明确返回冲突并保留手机草稿，不用 incoming >= version 接受过期覆盖。先验证 Provider、Prompt、语言和密钥动作再写入；使用已有仓库保存入口。跨存储异常若发生部分写入，恢复旧快照并报告失败；恢复失败明确记录受影响项，不能报告“保存成功”。不新建通用事务框架。
- [x] A6 “保存并测试”调用真实翻译探测。保存成功但网络测试失败时保留已保存配置，并分别显示“已保存 / 测试失败”；不把后者误报保存失败或自动删配置。对有效配置、鉴权失败、超时、取消、无效输出和迟到回调分别落终态。
- [x] A7 电视复用 MessageHelpers 发“正在测试”和最终结果提示，并提供含实际文本的持久结果页；检查现有对话框自动执行规则，必要时局部关闭 expandable。UI 回到主线程；关闭/取消后旧回调不能重开页面。成功显示 Profile、模型、耗时、测试译文；不输出凭证或原始响应。
- [x] A8 运行 focused settings 测试和页面可运行检查，记录实际断言结果。修复上述问题后继续 Task B；电视提示真实外观统一在 Task F 检查。

**验收例：** 手机编辑名称后等待至少两个轮询周期，内容不变；旧版本保存得到明确冲突，电视旧值不变；未配对保存被拒绝；合法保存后重开同一 Profile 值一致；取消测试后延迟成功不弹窗。页面测试必须执行生产页面脚本/实际路由，不能仅搜索 token 字符串。

## 5. Task B：完整字幕源与 M06 处理接线（M07 前置依赖）

**Files:** 新增/复用 `AI/source/SmartTubeSubtitleSourceAdapter.java`；修改 `AI/integration/AiSubtitleController.java`、`AiSubtitleCueBridge.java`；调用 `AI/source/SubtitleNormalizer.java`、`AI/segmentation/` 和 `AI/domain/` 现有类型。新增 `TEST/source/SmartTubeSubtitleSourceAdapterTest.java` 并扩展 integration 测试。

**输入/输出：** 活动视频及实际字幕轨 → `List<SourceCue>` → 有稳定 `SubtitleSegmentId` 与真实起止时间的 `List<SubtitleSegment>` → `TranslationChunker.chunk(List<SubtitleSegment>, int, int)` 的 `List<TranslationUnit>`。时间通过 segment ID 查回，不另外复制成一套 timeline 模型。

- [x] B1 按 ADR-005 检查 `MediaItemService.getFormatInfoObserve(videoId)`、`MediaItemFormatInfo`、`MediaSubtitle` 及项目已安装字幕解析器。记录可复用方法、支持格式、格式信息是否重复请求；同语言人工/ASR 必须匹配用户实际选择的轨，不能只按 language 猜。
- [x] B2 编写真实格式响应的独立小 fixture：同语言两条轨、空字幕、重复文本但不同时间、轨切换时源请求迟到。调用已有解析器；确有未支持格式才补有边界的格式适配，先执行本计划复用检索门槛。
- [x] B3 用独立 adapter 获取并缓存当前选中轨的源结果，取消/身份校验复用现有模式。未知或歧义轨回退原文并记录原因；不静默翻译另一条轨，不拉取整视频的翻译结果。
- [x] B4 串接 M06 normalizer、已实现断句/时序/分块。segment 索引必须稳定且覆盖关系可回查；删掉生产路径“一律 segment 0”的临时代码。重复文字在不同时段不得借错译文，句子跨多个 cue 的覆盖也须验证。
- [x] B5 给当前 cue 匹配时间与 segment 覆盖，再查相应 unit 的译文。多 segment 译文只能在它对应的覆盖时间内使用，保留宿主原始 cues 与渲染时序；原文回退不改原播放行为。不得靠重复进入 process() 重跑归一化/分块。
- [x] B6 运行 source、segmentation、integration tests：即使当前没有新的 onCues，未来 30 秒的原文也已可供调度；当前句映射不会取到另一个 segment 的缓存。若这一步失败，先修源/映射，再验 Task C 的真实集成；其纯调度测试继续。

**停止条件：** 没有完整时间线时只能标记源接线未完成，不能用“多缓存当前 cue”声称实现 lookahead。源格式或轨匹配在测试输入不能判定时，先补证据；只有该证据必须来自设备才触发提前局部真机检查。

## 6. Task C：一个调度类完成窗口、去重、暂停和 Seek

**Files:** 新增 `AI/scheduler/TranslationScheduler.java`，修改 `AI/integration/AiSubtitleCueBridge.java`、`AiSubtitleController.java`；新增 `TEST/scheduler/TranslationSchedulerTest.java`，复用现有 integration/session 测试。

**所有权：** 沿用 TranslationSession，Bridge 负责渲染/配置桥接；调度负责请求安排。现有 mInFlight/PendingRequest 请求 bookkeeping 只保留一个权威所有者。provider 回调、生命周期事件、队列修改必须串行化或置于同一现有同步边界；不在持锁时调用 UI listener。

- [x] C1 先写窗口测试：位置 10 秒，前瞻 30 秒，应包含覆盖当前位置的单元及 10–40 秒内开始的未来单元，不含已结束单元和 40 秒后单元；边界采用当前覆盖 end > position、未来 start <= windowEnd。零前瞻仍允许翻译当前句。
- [x] C2 用已排序 unit 列表或 stdlib PriorityQueue 实现“当前句优先、未来按时间顺序”。长视频不全量排翻译任务；窗口有界且队列去重。时间以 long 毫秒计算并做溢出/未知位置处理。
- [x] C3 默认并发 1，先沿用 architecture.md 的保守策略。单个在途句柄加按 TranslationCacheKey 去重已足够，不增加 AdmissionController。缓存命中跳过请求；pending、retry 等待和 terminal 单元不会因重绘反复提交。
- [x] C4 用现有播放器事件驱动进度；检查 onTickle 真实频率，若不足以兑现设置，才在功能 Controller 中增加一个可取消 Handler 回调。nowMs 从 Android 单调时钟取，传给纯逻辑；测试直接传数值，无需新 SchedulerClock 接口。
- [x] C5 暂停停止新请求，包括重试；允许未过期在途请求完成入缓存。resume 立刻按实际播放位置补窗口。没有当前文字但时间线有未来内容时仍可预取；状态不能因仅开启开关就变“翻译中”。
- [x] C6 Seek 用 TranslationSession.advanceEpoch()，取消旧队列/在途工作并根据最新位置重排；onSeekEnd 读取真实位置。拖动中只保存最新位置并取消旧工作，完成后优先当前句，避免每个拖动事件都发请求。seek 前已验证缓存可以保留，seek 后旧回调不能新写缓存/UI。
- [x] C7 视频、轨道、Provider、模型、Prompt 内容/版本、目标语言变化沿用现有 generation/identity 失效。字幕关闭或 release 同时清理队列、回调和句柄；不能由后续轮询复活会话。
- [x] C8 成功/失败/取消/同步抛错/无效回调都必须有释放或终止路径。保留现有“先登记再调用 provider”的顺序，以容纳同步 fake 回调；迟到失败也验证 ownership，不能污染新视频状态。
- [x] C9 用 fake provider 一次可控推进验证：100 次相同 tick 只发一次；暂停无新请求；resume 立即调度；连续 seek 只为最终位置派发；所有身份字段分别变化后，旧成功、旧失败和重复回调均不修改当前状态。

## 7. Task D：有限重试、部分结果与原文回退

**Files:** 继续修改同一个 `AI/scheduler/TranslationScheduler.java`、`TEST/scheduler/TranslationSchedulerTest.java`；复用 `AI/translation/TranslationFailure.java`、`AI/segmentation/BoundaryProtocol.java`、`AiSegmentationCoordinator.java` 的现有校验与恢复能力。协议层仅在缺少必要结果元数据时做最小兼容扩展。

- [ ] D1 调度仅看 failure.isRetryable()，不再判断品牌或 HTTP 文本。默认每单元最多 3 次网络尝试（初次 + 2 次重试），退避基数 1 秒、2 秒，随机抖动 0–250ms。数字为本次实现默认值，无需先做配置 UI；测试固定 Random seed 或传入 jitter 数值。
- [ ] D2 用现有 tick/唯一 Handler 定时器检查到期，不 Thread.sleep，不为每个请求建线程。暂停、seek、关闭和换身份取消对应重试；重试到期时仍重新检查窗口、缓存、attempt 和 session ownership。立即重试不是本轮必需，统一走有限退避即可。
- [ ] D3 AUTH、PROTOCOL、INVALID_OUTPUT、CANCELLED 沿用现有 terminal 分类；当前 epoch 内不自动循环。手动重试有明确入口；失败总有原文，不因为重新渲染重置计数。
- [ ] D4 区分两种“部分”：TranslationResult.partialResult 是流式草稿，绝不能作为完成或部分批次成功。批次缺项必须由 segment 覆盖和已验证边界证明；有连续有效前缀则保留，只为未覆盖尾部安排一次修复，修复仍算入总预算。边界不可证明时保持原文，不伪造完整成功。
- [ ] D5 尽量直接调用 M06 的 accepted-prefix/tail recovery；不要另写 response parser 或第二套批次协议。单 unit 单请求失败则只重试该 unit，已经成功的邻近 unit 不重发。
- [ ] D6 同一测试文件加入超时、429、5xx、鉴权、取消、空/错误覆盖、草稿、前缀+尾部失败、预算耗尽场景。断言总请求次数、已接受覆盖、缓存内容和 source fallback，而不只是断言方法被调用。

## 8. Task E：接通实际生效的设置与回归

**Files:** `AI/settings/AiSubtitleData.java`、`AI/settings/ui/AiSubtitleSettingsPresenter.java`、`AI/settings/remote/AiSubtitlePhoneInputServer.java`、`AI/integration/AiSubtitleRuntime.java`、`AiSubtitleCueBridge.java` 与三套 AI 字符串；扩展现有 settings/integration 测试。

| 设置 | 本轮兑现的行为 | 最小做法 |
|---|---|---|
| 提前量 | 默认 90 秒，允许 0/30/60/90/120 秒；手机非法值拒绝，旧缺失值用 90 | AiSubtitleData 现有读写模式，无新配置仓库 |
| 调度节流 | 默认 30 秒，仅限制后台窗口补充；允许 5/15/30 秒 | 当前句、首次启动、seek 完成、resume 和用户配置变化立即检查；不是把每个网络请求硬等 30 秒 |
| 自动翻译 | 复用已有启用/显示模式，明确新视频是否继续启用 | 不增加语义相同的第二个布尔值；若单独“新视频自动开启”语义确有需要才独立存储 |
| 双语顺序 | 原文优先/译文优先；当前句即时刷新 | 组合文字时一处分支，保留三种显示模式 |
| 断句/分块/长句阈值 | 暴露已存在且接通的 M06 策略及参数 | 默认沿用 M06 当前调用/fixture 的有效值，保存校验 targetChars >= 1、maxChars >= targetChars、长句阈值 > 0；更改时重新处理并失效旧缓存 |
| Prompt 策略 | 复用 M05 Prompt 选择和 identity | 没有 AI 断句调用的模式不显示假开关；AI 断句启用时才绑定现有 coordinator 与对应 Prompt |
| 上下文/SSE/持久缓存 | 属于 M08 | 记录在原 roadmap 中，本轮不加无效 UI |

- [ ] E1 对每个设置补“保存→重开→Runtime 读取→实际行为变化”的检查；电视与手机写入同一存储。改前瞻/节流只重排窗口；改变分段输出的参数必须纳入既有缓存身份或清理受影响缓存与请求。
- [ ] E2 用测试输入提前量 30 秒与 90 秒对比实际请求单元集合，不以 UI 存储成功代替功能验证。
- [ ] E3 跑完整 common 测试、lint、assemble；修复本轮引起的回归。每次定向验证通过后不要重复跑全量，最终候选 SHA 再统一检查一次。

## 9. 验证命令、提交与证据

命令从 SmartTube 根目录执行；Windows 使用 gradlew.bat，JDK 17 路径从本机实际安装确认。中文路径问题使用已验证 ASCII 映射，或仅本地加 `-Pandroid.overridePathCheck=true`，不写入仓库 gradle.properties。历史测试数字不作为本轮结果。

```powershell
# A：服务端/设置；页面脚本还须独立执行真实页面检查
.\gradlew.bat :common:testStbetaDebugUnitTest --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.*'
# B：源、处理和播放器集成
.\gradlew.bat :common:testStbetaDebugUnitTest --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.source.*' --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation.*' --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.integration.*'
# C/D：窗口和失败状态机
.\gradlew.bat :common:testStbetaDebugUnitTest --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.scheduler.*'
# E：最终候选全量验证
.\gradlew.bat :common:testStbetaDebugUnitTest lintStbetaRelease assembleStbetaRelease

git diff --check
git diff --stat
```

预期是测试实际执行、无 failures/errors、构建成功，skip 按原因记录。无测试匹配、运行器跳过或只编译不算通过。正式构建验收使用候选代码精确 SHA 的现有 GitHub Actions，不新建一条近似 workflow。

按能独立审阅的修复/功能提交：手机+测试、源接线、调度+设置可分别提交，必要时合并；不为接口脚手架单独提交。只暂存逐个核对的文件，不使用整个 common/smarttubetv 目录兜底添加。CI 完成后允许单独补证据文档，不为把 CI 链接塞回原提交而 amend 已验证 SHA；最终交付清楚区分代码 SHA 和文档 SHA。

## 10. Task F：M07 实现完成后统一真机验收

**Files:** 更新 `docs/ai-subtitle/progress.md`、`tv-usability-repair-progress.md`、`upstream-patches.md`，将一次性结果写入 `docs/ai-subtitle/worker-reports/M07-report.md`。引用旧修补计划的全部八项验收，编辑方式以本计划“手机本地编辑、显式保存”取代旧实时同步要求。

- [ ] F1 使用 M07 候选 CI 产物，核对 APK 哈希、签名、manifest 中真实 applicationId 和设备 ABI 后覆盖安装，不硬编码旧计划包名、不默认卸载用户旧配置。无设备时记录“代码/自动检查完成，统一真机验收待执行”，不宣称 M07 全部验收通过。
- [ ] F2 依次验证安装/启动→手机选择或新建 Profile→输入不丢失→保存重开→真实测试通知/持久结果→播放。某一步失败导致下一步无法测，先修该步、自动回归、更新包并复验该路径；独立项继续。
- [ ] F3 验证三模式、简繁界面、官方/Custom、密钥保持/替换/清除、版本冲突、配对过期、断网和取消；记录明确的通过/失败/待验证，不将“代码存在”写成通过。
- [ ] F4 播放验证英文/中文/日文中可用的人工与 ASR 轨、短片与长片；开播尚未到达的字幕已触发翻译，切换提前量改变请求范围，原文/译文时间匹配。限制总请求量，长视频不能整片预翻译。
- [ ] F5 验证暂停/resume、前后 seek/快速拖动、换轨/换视频/换配置、字幕关闭、退出重进、迟到响应、失败及原文回退；确认退出后无持续定时请求。
- [ ] F6 若有修复，只重跑受影响自动检查及设备路径；形成最终候选后完整发布检查一次。复用签名/资产验证工具；不覆盖旧 tag。未测 ABI 包明确标记未验证，不用一个设备冒充所有架构验证。
- [ ] F7 报告记录代码 SHA、CI run、设备/Android/ABI、包名和哈希、路径结果、请求/重试上限、未验证项。只有统一真机通过才标完整验收；M07 实现完成与真机待验可分别记录，后者不倒置成实现前门槛。

## 11. 执行复选总表

- [ ] 已确认的手机/测试 bug 完成自动复现和根因修复。
- [ ] 完整时间线、轨匹配、稳定 segment 与 M06 处理链接通。
- [ ] M07 八个工作流全部覆盖：窗口/优先级、去重/并发、暂停、seek、身份、有限重试、部分批次恢复、端到端状态机。
- [ ] 提前量及相关设置实际影响处理/调度，M08 功能无假入口。
- [ ] 复用现有 session/cache/provider/控制器及标准集合；无第二套会话、缓存、通用队列框架或无依据的新依赖。
- [ ] 自动检查和精确代码 SHA 的 CI 通过；未做的设备检查如实记录。
- [ ] 统一真机在 M07 实现完成后执行；每个提前验证例外均有明确串行依赖证据。
- [ ] 已保留全部旧八项用户路径，修复过程中没有遗失设置/密钥，原播放器及原文字幕不受影响。
