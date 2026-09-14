# M09 强化与发布候选验收 Implementation Plan

> **For agentic workers:** 后续执行使用 executing-plans；本轮仅写计划。先读取 [总恢复计划](M07-M09-continuation-plan.md) 和 [M08-plan.md](M08-plan.md)。设备缺失可继续自动检查，但不得宣布 M09 完整完成。

**Goal:** 把 M07/M08 的功能转为可复验的发布候选，证明播放正确、请求和资源有界、设置可用、密钥不泄漏，并留下未来上游同步的回归流程。

**Architecture:** 在既有测试、feature-owned Controller/Bridge/Scheduler/Cache 上补缺口；复用现有 CI 和宿主 hook。发现哪条链路失败就先补自动复现再最小修正，不以大重构完成审计。

**Tech Stack:** 现有 JUnit 4/本地假服务、GitHub Actions/JDK 17、JDK 11 preference 通道、Android/adb 设备工具。

日期：2026-09-14。所有阈值为拟定验收预算，不是测量结果。具体设备型号、ABI、服务能力以执行时可验证信息为准。

Ponytail：只补已有套件缺失的场景，不重写 M07/M08 测试、不做语言×模式×Provider×生命周期的全排列。矩阵和性能结果都放 M09-report.md；只有发现缺陷才新增实现提交。

## 全局约束

- 不新增宿主 hook；既有 hook 的必要修正逐条记 upstream-patches。原版播放/字幕行为为回归基线。
- 公共 Provider 真实调用、设备安装和发布按执行时授权范围执行；本次不触发。
- 候选代码 SHA、文档 SHA、CI run、APK 哈希与安装 APK 必须一一对应；代码改动使受影响验证过期。
- fake 与真实 Provider、自动与设备结果分栏，不能互相替代。不可用组合标“不适用+理由”，未运行标“待验”，不能一律写通过。
- 不为一个设备宣称所有 ABI 已验。无真机不阻塞自动实现，但会阻塞最终设备验收签字。

## A. 建立覆盖矩阵和自动回归

**修改/扩展：** `common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/` 下现有 source/segmentation/scheduler/integration/provider 套件。
**记录：** `docs/ai-subtitle/worker-reports/M09-report.md` 的验收矩阵章节。
**fixture：** 先复用 `docs/ai-subtitle/fixture-provenance.md` 指向的独立 fixture；缺少的放同一既有测试资源目录并补来源声明，不复制 KissTranslator testdata。

- [x] 场景以现有测试方法名和设备步骤标识并关联证据，未另建场景 ID 注册表。
- [x] fixture 补齐中文（人工 + 自动生成），英语/日语/中文 × 人工/自动生成齐备，并断言 word-level、无空格、噪声、重复文本、快/慢语速、长句、重叠、空隙九类 provenance 全部存在（`SubtitleFixturePipelineTest.theFixtureCoversEveryRequiredLanguageAndCaptionKind`）。此前该 fixture 是**没有调用者的数据**，本轮才第一次被断言。可用字幕轨的设备组合待设备确认。
- [x] `TranslationSchedulerCapacityTest` 使用 7500 个 1 秒 cue 的合成时间线，以虚拟时间遍历 256 次前后跳转；报告明确写明这不是两小时真机稳定性测试。
- [x] `SubtitleFixturePipelineTest.aGapInTheSourceHasNoUnitToShow` 断言空隙处没有可显示 unit，前后仍有；`everyUnitCoversContiguousSegmentsInTimelineOrder` 与 `everySegmentResolvesToAUnitAndEveryUnitToASegment` 断言映射；源缺失/歧义沿用 M07-R3 的 source-only 断言，网络失败沿用 `loadDeliversFailureForEmptySubtitles` 与调度器的 source fallback 断言。
- [x] 覆盖：暂停→resume、前后 seek、连续拖动只保留最终位置、换轨/换视频/换配置、关闭字幕/AI、退出重进；迟到 partial/final/error 全部有断言。**未覆盖**：50 次快速拖动的显式计数用例、后台/前台（需要设备或 Activity 生命周期，M09-E 的输入）。
- [x] `repeatedPositionTicksDoNotDuplicateRequests`（100 次）、`dragKeepsLatestPositionAndSeekEndDispatchesOnlyIt`、`aLaterUnitThatFinishedFirstIsNeverHistory`、以及关闭/打开 streaming 的两条链路（全量套件在开关默认关闭下通过，streaming 用例单独覆盖打开态）。
- [x] 归一化与预算：`everyProviderFailureCategoryKeepsSourceOnly`（全部类别）、`terminalCategoriesDoNotRetry`、`rateLimitAndServerUseTheSameRetryBudget`（429/5xx）、`timeoutUsesThreeNetworkAttemptsWithBackoff`；同步回调与抛错：`providerExceptionIsIsolatedToSourceOnlyOutput`。**未覆盖**：重复终止 callback 的显式用例（传输层与各 adapter 用一次性标志保证至多一次，但没有专门断言重复投递）。

矩阵字段：`场景/操作 | 预期 | 自动/设备证据 | 结果/问题`。SHA、设备和公共设置在报告头统一记录，仅例外行单列。矩阵正文见 `worker-reports/M09-report.md`。

## B. 长视频容量、请求预算与性能

**修改：** `AI/cache/InMemoryTranslationCache.java`、`AI/scheduler/TranslationScheduler.java`、必要时 `AI/source/SourceTimeline.java` 与对应现有测试。AI/TEST 前缀与总计划一致。
**记录：** M09-report.md 的资源与性能章节。

现有内存 cache 是 HashMap；mWork 保留访问过的单元。只限制 lookahead 不能保证总内存不会随播放增长。

- [x] `InMemoryTranslationCache` 改为访问序 `LinkedHashMap` LRU，上限 512 条且 UTF-8 文本总量 ≤2 MiB，先到为准；尺寸在 put 时计算、替换扣旧值、clear 清零。未新建缓存框架。测试：`theEntryCountLimitEvictsTheLeastRecentlyUsedEntry`、`theByteLimitEvictsUntilTheStoredTextFits`、`replacingAnEntryCorrectsTheStoredSizeInsteadOfDoubleCounting`、`aSingleValueLargerThanTheWholeBudgetIsNotKept`。
- [x] `pruneWorkLocked` 在每次允许派发的窗口更新时清除窗口外的终态与已取消记录；只保留在途请求、当前窗口（含 30 秒回溯余量）以及缓存仍持有的结果——后者让工作记录的上限直接由缓存上限给出。**实现要点**：清除判定使用不过期的 `contains`，否则清除扫描本身会不断刷新 LRU，什么都淘汰不掉（本轮实测到该反馈回路并修复）。反复重绘不重置尝试预算由 `aRedrawNeverResetsTheAttemptBudget` 断言。
- [x] `getFirstAttemptCount`/`getRetryAttemptCount`/`getFallbackAttemptCount` 分开计数，`attemptAccountingSeparatesFirstRetryAndFallback` 断言回退与普通重试不混算，`theStreamingFallbackStillStopsAfterTheAttemptBudget` 断言总预算为 3，手动重试建立新预算由 `manualRetryRequeuesTerminalFailuresWithAFreshBudget` 断言。**尾部修复无计数**：该模式在生产路径上不存在（见 M07 报告 R5 范围修订）。
- [x] 源下载上限改为**字节**上限 8 MiB（此前按字符计数，多字节字幕可远超预期），适配器另有 100000 cue 上限；超限返回 null 走 source-only，绝不截断后声称完整。Work/缓存/草稿/context 的上限由 B 段其余条目覆盖。
- [x] `mUnitsByStart` 改为 `TreeMap<Long, List<TranslationUnit>>`，同一起点按源顺序保留全部 unit；`twoUnitsThatShareAStartTimeAreBothDispatched` 断言两条都被派发。
- [x] `aTwoHourTimelineStaysBoundedUnderRepeatedSeeks` 断言 256 次前后跳转后工作记录与缓存都在上限内；release 后无新增请求、定时回调与取消句柄沿用既有 `closeCancelsActiveWorkAndStopsDispatch` 等断言。**未覆盖**：256 次开关/退出重入的显式计数用例。
- [ ] **PENDING DEVICE**：需要设备与候选 APK；未运行，未声称任何测量结果。
- [ ] 集合/输入上限已由自动断言覆盖；**退出后无新派发与 ANR/播放中断需要设备**，未运行，未作为发布门槛。
- [x] 本轮没有性能失败证据，因此没有改动线程模型、播放器或依赖版本。

## C. 手机、TV、Provider 与秘密数据审计

**检查：** AI/settings、provider/http、Runtime、Bridge、三套资源、现有备份/导出排除规则；修改限发现缺陷的实际文件。

- [ ] **PENDING DEVICE**：手机页面路由、配对、保存、重开、迟到回调在真实 socket 测试中已覆盖；扫码与真机浏览需要设备。
- [x] `ProviderAuditTest.everyProviderTypeResolvesToAProtocolAndCompletesThroughFakeHttp` 遍历全部 `ProviderType` 并按其协议完成一次假 HTTP 请求；真实服务需要有效密钥，本机未做。
- [x] 身份变化沿用既有 generation/cache-key 断言；`bilingualOrderChangesPresentationWithoutNewRequests` 断言顺序不触发请求；`replacingTheSecretChangesWhatTheNextRequestCarries` 断言替换后的凭证用于下一次请求。
- [x] `theCredentialCanaryNeverAppearsInAnyDiagnostic` 用唯一 canary 跑成功与错误响应、取消路径，并对 adapter、请求、调用句柄、HTTP 请求、HTTP 失败与响应对象逐一断言 canary 不出现。
- [x] 手机状态响应不回传密钥（既有断言）；M08 未加磁盘缓存，因此没有新的落盘路径；密钥仍只存于 M04/G04-1 决定的加密 SecretStore。**备份/导出排除规则**沿用 M04 记录的结论，本轮未重新审计系统备份配置。
- [x] 三种模式与顺序有自动断言；简繁/英文字符串齐备；无 Provider 与失败提示有独立字符串。**遥控器可达性与实际排版需要设备**。用户可见文案中未出现类名或调试参数。

## D. 上游补丁与持续回归流程

**修改：** 在 `docs/ai-subtitle/upstream-patches.md` 补同步检查章节；如存在实际漏跑范围，最小修改 `.github/workflows/ai-subtitle-validation.yml`，不另外拆一份同步文档。

- [x] `origin` = 个人仓库，`upstream` = yuliskov/SmartTube；本地 `master` 与 `upstream/master` 同为 `6e2e00bb8c`，因此 `git diff master...HEAD` 就是相对已验证上游基准的完整 diff（180 文件，+28073/-1）。submodule 指针未变、无 ExoPlayer 源码改动、无依赖版本变化，唯一新增依赖已记入 `upstream-patches.md`。
- [x] `upstream-patches.md` 已按实际 diff 重写：5 个宿主 Java 文件 + 1 个资源文件 + 1 个构建文件，逐项写明用途、补丁面与回归检查；发现两处**未登记**的宿主改动（`PlayerUIController`、`VideoPlayerGlue`）与一处构建改动并补齐；条件性 `VideoLoaderController` 钩子标记为**未使用**并说明原因。无整文件格式化、无 rename、宿主内无 HTTP/缓存/调度逻辑。
- [x] `ai-subtitle-validation.yml` 未改动，仍覆盖 common 全量、JDK 11 设置通道、lint、assemble、APK 签名与报告产物；未新增第二条 workflow。**该 workflow 对候选 SHA 的运行仍待授权推送**。
- [x] `upstream-patches.md` 的同步章节已按该顺序重写为可执行步骤，并明确本文不执行合并。本轮未 merge upstream。
- [x] `upstream-patches.md` 新增回归 checklist，覆盖选轨、源、cue 桥、生命周期、持久化、Prompt 实际请求、SSE 取消与迟到源结果八类。
- [ ] **PENDING**：需要候选 APK 与设备；未运行。

## E. 统一真机验收（记录设备证据，不单靠自动测试）

复用 M07 Task F 和 `tv-usability-repair-plan.md` 的全部用户路径，合并到本阶段矩阵。已经在精确候选通过的设备项不重复劳动；代码影响其路径时才标过期重测。

| 顺序 | 操作 | 明确预期 |
|---|---|---|
| 1 | 核对候选 SHA、APK 哈希/签名/ABI后覆盖安装 | 可启动，原配置仍在 |
| 2 | 手机/TV 新建与编辑 Profile、测试、重开 | 字段保留，令牌/冲突/密钥动作正确 |
| 3 | AI off 播放原字幕 | 原播放和字幕行为保持 |
| 4 | 开启 AI，短片 manual/ASR，切三模式/顺序 | 原译时间匹配，设置即时生效 |
| 5 | 提前量 0/30/90/120，暂停后改设置 | 请求范围随值变化，暂停不启动新请求 |
| 6 | 快速 seek、换轨/换视频/换 Prompt/provider | 当前新身份优先，旧结果不污染 |
| 7 | context on/off；stream on/off；断网/重连 | 可退普通翻译/原文，草稿不残留 |
| 8 | 后台前台、退出重开，宿主支持时 PiP | 生命周期正确，无后台泄漏请求 |
| 9 | 2h+ 视频长时连续播放并执行阶段采样 | 资源稳定；与模拟虚拟时间结果分开记录 |
| 10 | 配对过期、非法值、失败/取消、密钥清除 | 准确反馈，原文仍可用，无密钥外泄 |

- [ ] **PENDING DEVICE**：无设备，未记录任何设备行，也未伪造设备结果。
- [x] 本轮自动检查发现的阻断（缓存淘汰在迭代中重排导致永不淘汰、工作记录无界增长、清除扫描自刷新 LRU）均已先复现、再修复、再跑全量套件复验。设备项待设备。
- [x] M09-E 全部条目在报告中标 PENDING 并给出操作步骤与触发条件；未伪造任何截图、计数或跑分。

## F. 发布候选报告与完成门槛

**交付：** `docs/ai-subtitle/worker-reports/M09-report.md`、更新 progress.md/roadmap.md 中实际状态；本次只规划，执行时生成真实报告。

- [x] `worker-reports/M09-report.md` 包含上述全部字段；性能原始数据位置标为无（未运行设备采样），CI run 标为待授权推送。
- [ ] **PENDING PUSH AUTHORIZATION**：未运行精确 SHA 的 CI，本地只跑 common 全量与 JDK 11 设置通道。
- [ ] **PENDING DEVICE + PUSH**：无候选产物（未构建/未签名/未上传），未运行。
- [ ] **未满足**：设备维度与精确 SHA CI 仍 PENDING，因此 M09 未标记完成。无已知未解决的高影响缺陷。
- [x] 报告与进度台账按该措辞记录，roadmap milestone 未标 complete。
- [x] 未创建 release/tag、未上传 APK、未覆盖任何已有 tag。

## 命令和测试证据

在 ASCII 副本、正确 JDK 环境运行新增/受影响 suite；候选全量由 CI：

```powershell
.\gradlew.bat :common:testStbetaDebugUnitTest --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.source.*' --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation.*' --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.scheduler.*' --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.integration.*' --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.cache.*'
git diff --check
```

Provider/settings 审计修改后定向加跑相应包，不重复执行无关全量。测试报告统计 tests/failures/errors/skipped，留命令、JDK、源 SHA/脏 diff 标识与时间。拒绝使用旧 XML、空匹配或 skipped 作为通过。
