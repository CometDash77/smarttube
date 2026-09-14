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

- [ ] 用现有测试方法名和设备步骤标识场景，关联实际证据；不另外维护一套场景 ID 注册表。
- [ ] 每种语言 English/Japanese/Chinese 至少有 manual 与 ASR 自动 fixture；补 word-level、无空格、噪声、重复文本、快/慢语速、超长句、重叠/空隙。无实际可用字幕轨的设备组合注明。
- [ ] 创建独立 2h+ 合成时间线，例如 7500 秒、每秒一小 cue，用虚拟时间遍历；不需等待两小时、不把它描述成两小时真机稳定性测试。
- [ ] 断言正确 unit/segment 时间映射；没有 cue 的空隙不显示上一句；字幕源缺失/歧义/网络失败保持原字幕。
- [ ] 用 fake provider 可控延迟组合播放事件：暂停→resume、前后 seek、50 次快速拖动、换轨/视频/配置、关闭字幕/AI、退出重进、后台前台。迟到 partial/final/error 全覆盖。
- [ ] 100 次相同 tick/cue 不增加同一逻辑工作请求；seek 只优先最终位置；串行上下文不把未来译文写入当前历史；stream off 与 on 都跑核心链路。
- [ ] AUTH/PROTOCOL/INVALID_OUTPUT/CANCELLED/NETWORK/TIMEOUT 及 HTTP 429/5xx 覆盖归一化与预算；包含 synchronous callback、throw、重复终止 callback，防止状态/句柄悬挂。

矩阵字段：`场景/操作 | 预期 | 自动/设备证据 | 结果/问题`。SHA、设备和公共设置在报告头统一记录，仅例外行单列。

## B. 长视频容量、请求预算与性能

**修改：** `AI/cache/InMemoryTranslationCache.java`、`AI/scheduler/TranslationScheduler.java`、必要时 `AI/source/SourceTimeline.java` 与对应现有测试。AI/TEST 前缀与总计划一致。
**记录：** M09-report.md 的资源与性能章节。

现有内存 cache 是 HashMap；mWork 保留访问过的单元。只限制 lookahead 不能保证总内存不会随播放增长。

- [ ] 内存 cache 改标准 LinkedHashMap LRU，初始上限 512 条且估算文本 UTF-8 总量 <=2 MiB，两者先到为准；条目尺寸在 put 时计算，替换扣旧值，clear 清计数。不另建缓存框架。
- [ ] mWork 清除窗口外且非 active/retry 的历史终态；保留当前窗口和必要的短期失败抑制记录，后者同样有硬上限。cache 淘汰后可以按需重新翻译，但反复重绘不能绕过同一工作预算。
- [ ] 总网络计数区分首次、transient retry、尾部修复、SSE→非流式回退。每一逻辑工作总预算 <=3；用户明确手动重试建立新预算，普通 redraw 不重置。
- [ ] 完整 SourceTimeline 可以随字幕长度线性增长，但 Work/译文缓存/草稿/context 不应无限增长。源下载加初始 8 MiB 字节上限、100000 cue 上限，超限取消 AI 源处理并回原文；不能截断后声称“完整时间线”。
- [ ] 检查 TreeMap<Long,TranslationUnit> 相同起点覆盖风险：fixture 两个 unit 同时开始，均不能丢失。若输入允许重叠，用既有 list/稳定次序作为次键，不静默覆盖。
- [ ] 测试虚拟长片、反复前后 seek、256 次开关/退出重入：cache/Work/草稿/context 上限稳定、release 后无新增请求、定时回调被移除、取消句柄可释放。
- [ ] 复用长时设备验收，在相同视频/清晰度下比较 AI off、非流式、流式：记录请求数、字幕到达延迟、内存起点/中点/终点和可见卡顿。网络等待与本地耗时分开；不新建持续遥测或全链路 percentile 框架。
- [ ] 硬检查保留集合/输入上限、退出 2 个 tick 周期后无新派发、无新增 ANR/播放中断。若内存持续上涨或出现卡顿，用现有 Android profiler 定位并补针对性测量；不把没有设备基线支撑的统一 16ms 阈值作为发布门槛。
- [ ] 性能失败只优化证据指出的热点，不凭猜测改线程模型或升级播放器。修正后只重跑受影响 suite/采样。

## C. 手机、TV、Provider 与秘密数据审计

**检查：** AI/settings、provider/http、Runtime、Bridge、三套资源、现有备份/导出排除规则；修改限发现缺陷的实际文件。

- [ ] 设备路径：扫码/配对→编辑不丢失→保存→重开一致→真实连接测试→选择使用；手机/TV 同一存储；取消测试后迟到结果不弹错误通知。
- [ ] 五 Provider 类型都跑假 HTTP 的保存/解析/请求路径；真实服务仅测试具备有效密钥和授权的组合，记录型号、协议、模型、流式支持，不记录 credential。
- [ ] 切 Provider/model/Prompt/目标语言/分段策略/context 设置会产生应有身份变化；改双语顺序不重新请求；仅密钥替换时旧 adapter/请求不能继续使用旧凭证。
- [ ] 用虚构唯一 secret canary 跑错误、取消、toString、报告和页面回显；捕获日志，断言 canary 不出现。扫描请求 query/headers/body 的错误传播，不能只搜索变量名 apiKey。
- [ ] 检查备份、导出、手机响应、APK 测试资源不含真实密钥；若 M08 加磁盘缓存，单独检查该文件路径与 schema 不含 secret/原始鉴权 URL。
- [ ] 检查三种显示模式、原文/译文顺序、简繁/英文界面、长选项遥控器可达、无 Provider/离线提示。不把源码类名和调试参数直接展示给用户。

## D. 上游补丁与持续回归流程

**修改：** 在 `docs/ai-subtitle/upstream-patches.md` 补同步检查章节；如存在实际漏跑范围，最小修改 `.github/workflows/ai-subtitle-validation.yml`，不另外拆一份同步文档。

- [ ] 核实当前仓库 remote 与上游基准 commit；用已验证 base SHA 比较完整 feature diff，而不是只看本轮 diff。submodule 指针、Gradle 依赖、ExoPlayer 路径必须列入审计。
- [ ] 所有宿主文件改动逐项对应 upstream-patches 的用途/入口/回归测试；确认没有整文件格式化、rename、业务 HTTP/缓存/调度塞进宿主。
- [ ] 保留现有 exact-SHA CI，确认 common 全量、JDK 11 偏好设置、lint、APK、报告产物真实执行；不增加第二条重复 workflow。无额外 CI 缺口则只记录现状。
- [ ] 编写上游同步步骤：工作区清洁/保存用户修改→记录旧 upstream SHA→独立分支或工作树试合并→检查 hook 签名/字幕 identity→定向测试→全量 CI→设备核心回归→核对账本。本文不实际 merge upstream。
- [ ] 回归 checklist 必须包含选轨、格式源、SubtitleManager cue 桥、seek/pause/off/release、保存/重启、Prompt 实际请求、SSE 取消和源结果迟到。
- [ ] APK 对比基于实际 applicationId/签名/ABI，不按旧文档硬编码；不为了测试卸载用户配置。

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

- [ ] 记录每次设备型号/Android/ABI、视频/字幕类型、配置、时间和结果；无法公开的内容用可复验内部编号，不写密钥。
- [ ] 阻断问题自动复现→修复提交→受影响 CI/设备路径复验；某个设备故障不阻止其他独立项，但最后不能漏掉。
- [ ] 缺设备/服务凭据/真实字幕样本的单元明确 PENDING，给出操作步骤与补证触发条件；不得伪造截图、计数或跑分。

## F. 发布候选报告与完成门槛

**交付：** `docs/ai-subtitle/worker-reports/M09-report.md`、更新 progress.md/roadmap.md 中实际状态；本次只规划，执行时生成真实报告。

- [ ] 报告包含 M07→M09 提交清单、修改文件、修复问题、采纳/不采纳的 M08 优化、实际测试方法/计数、CI run 与代码 SHA、设备矩阵、性能原始数据位置、upstream diff 结论、已知限制。
- [ ] 从最终候选 SHA 跑现有 CI：common 全量/JDK 11 通道/lint/assemble；检查产物而非只看绿色图标。final SHA 之后代码变化必须使相关证据重新建立。
- [ ] 用候选 CI 产物完成设备验收；报告中保存包名、版本、ABI、SHA-256、签名指纹及对应安装设备。
- [ ] M07 源/调度/设置正确、M08 可关闭且缓存安全、M09 资源/安全/上游/设备各维度均通过；无必需 PENDING、无未解决高影响错误才标 M09 完成。
- [ ] 代码/自动检查已完但设备未完时，写“代码与自动检查完成，最终验收待设备”，不标 roadmap milestone complete。
- [ ] 发布候选报告与正式发布分开。创建新 release/tag 或上传 APK 只在后续有授权时执行；不覆盖已有 tag，不自行扩大到正式发布。

## 命令和测试证据

在 ASCII 副本、正确 JDK 环境运行新增/受影响 suite；候选全量由 CI：

```powershell
.\gradlew.bat :common:testStbetaDebugUnitTest --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.source.*' --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation.*' --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.scheduler.*' --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.integration.*' --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.cache.*'
git diff --check
```

Provider/settings 审计修改后定向加跑相应包，不重复执行无关全量。测试报告统计 tests/failures/errors/skipped，留命令、JDK、源 SHA/脏 diff 标识与时间。拒绝使用旧 XML、空匹配或 skipped 作为通过。
