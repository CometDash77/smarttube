# M02 Commander Review

Review status: **CHANGES REQUIRED**

Date: 2026-09-11

Reviewed range: `0b16df3d5...26693c340`

Product implementation tip: `8e9c030fe`; uploaded report tip: `3efb96374`; local report amendment: `26693c340`.

Authoritative CI run: [34615801161](https://github.com/CometDash77/smarttube/actions/runs/34615801161) for `3efb9637448a95cb1c3fe1d83736d0a667b46189` — **FAILURE** after 4m54s. `Run common unit tests` completed before the failing `Lint beta release` step; assembly did not establish an accepted result.

## Outcome

M02 is not accepted and M03 must not start. The implementation stays within the declared patch boundary, but three production defects and one required-test gap prevent the deterministic vertical slice from satisfying its contract. CI criterion 7 is also unmet.

The complete correction is packaged once in `docs/ai-subtitle/tasks/M02-FIX-01.md`. The Worker must return one amended M02 report after the correction and replacement CI run; no intermediate user relay is required.

## Standards

### Hard violations

- **P3 — SmartTube member naming:** `AiSubtitleCueBridge.java:237-240` names `PendingRequest` members `requestId`, `generation`, `epoch`, and `call` without the required `m` prefix.
- **P3 — SmartTube member naming:** `AiSubtitleControllerTest.java:221` names a member `calls` without the required `m` prefix.

### Judgement calls

- **P2 — Refused Bequest / ineffective test runner:** `JdkAwareRobolectricRunner.java:25-35` inherits `RobolectricTestRunner`, but on the authoritative JDK 17 environment it never runs the inherited test lifecycle and marks every child ignored. The test class exists but its required behavior is not exercised on that lane.
- No actionable Mysterious Name, Duplicated Code, Feature Envy, Data Clumps, Primitive Obsession, Repeated Switches, Shotgun Surgery, Divergent Change, Speculative Generality, Message Chains, or Middle Man was found in the reviewed diff.

## Spec

### P1 — The production Fake path misses the first visible translation

M02 requires the enabled test provider to render one cue as `source + "\n" + translation` (`M02.md`, Stage Deliverable and Behavior Requirements 3–5). `FakeTranslationProvider` completes synchronously, but `AiSubtitleCueBridge.findOrRequest()` always returns `null` after starting the call, even if the callback has already populated `mCompleted`. There is no callback-driven renderer invalidation. A caption delivered only once therefore remains source-only.

`AiSubtitleCueBridgeTest.enabledAfterCompletionRendersTwoLineCue` uses a deferred provider, manually flushes it, and calls `process()` a second time. That test does not cover the actual production constructor's immediate Fake behavior and masks this defect.

### P1 — New production APIs violate the Android 4.x / API 17 compatibility contract

`AiSubtitleCueBridge` imports `java.util.function.BooleanSupplier` (API 24), while `TranslationRequest` and `TranslationResult` use `java.util.Objects` (API 19). The app's minimum SDK is 17 and the project does not enable core-library desugaring. The controller instantiates the bridge during video lifecycle events even when the feature is off, so this is not safely isolated behind the opt-in switch.

The CI run failed in `Lint beta release`; the Worker must record the exact lint diagnostics from the run artifact/log and remove all M02-origin lint errors without suppressing them.

### P2 — Required persistence tests do not execute

M02 requires automated verification that `AiSubtitleData` defaults false and persists a toggle across singleton recreation. All three `AiSubtitleDataTest` methods are deliberately ignored on the JDK 17 CI lane. Code inspection is not a substitute for the required behavior test.

### P2 — Disable does not cancel immediately

M02 Settings requirement 4 says switching off immediately cancels and clears AI state. The settings callback only writes the preference. Cancellation occurs later, if and when `AiSubtitleCueBridge.process()` receives another non-empty cue and notices the state change. The correction must make disable invalidation explicit and test a late callback that ignores cancellation.

### Scope and upstream surface

- **PASS:** exactly the three authorized existing SmartTube files were modified.
- **PASS:** no `CI.yml`, Gradle, submodule, ExoPlayer, layout, `PlayerData`, provider-brand, prompt, network, segmentation, or full-track changes were introduced.
- **PASS:** upstream hooks remain narrow: controller registration, cue bridge call, and settings switch.
- **PASS:** M03 was not started.

## Tests and regression assessment

- The pure JVM bridge/controller/provider tests contain useful state, deduplication, cancellation, and stale-result coverage.
- The production immediate-Fake path lacks a one-call rendering regression test.
- The immediate-disable path lacks a test that proves cancellation before another cue arrives.
- The persistence suite is present but not executed on the authoritative lane.
- Manual/device scenarios are honestly `NOT RUN`; this is acceptable for the Worker report but cannot compensate for the defects above.
- Playback/source-only fallback risk remains bounded because the bridge catches feature exceptions, but API linkage on API 17–23 can fail before that boundary.

## Required correction

1. Complete `M02-FIX-01` as one correction package.
2. Push only after all corrections and report amendments are ready, so one replacement CI run validates the final product SHA.
3. Obtain a green run covering unit tests, the legacy Robolectric preference suite, lint, assembly, reports, and APK artifact.
4. Return the amended `M02-report.md` once. Commander will re-review M02; M03 remains blocked.

---

# M02 Commander Review — Second Pass

Review status: **CHANGES REQUIRED**

Date: 2026-09-11

Reviewed range: `0b16df3d5...be02bc2b3`; correction commit `69f644f4a`; reviewed tip `be02bc2b3`.

Verified CI evidence: [Run 34618112103](https://github.com/CometDash77/smartube/actions/runs/34618112103) and [Run 34618912621](https://github.com/CometDash77/smartube/actions/runs/34618912621).

Second-pass verdict, recorded verbatim as returned by the Commander:

> 复核结论：**CHANGES REQUIRED**。原 Commander 的 6 项发现均已实质修正，CI 证据也成立；但复核发现 3 个阻塞项，M02 暂不能 PASS，M03 继续保持未开始。

## Standards

- **P2 — `SubtitleSettingsPresenter.java` 混用换行符。** 基线为 87 行 CRLF / 0 行 LF；当前为 90 行 CRLF / 12 行 LF。新增方法 [SubtitleSettingsPresenter.java](<D:/obsidian/工程/VIBECODING项目/smartube/SmartTube/common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/presenters/settings/SubtitleSettingsPresenter.java:70>) 使用 LF，与文件原有 CRLF 风格不一致，也使报告中"已恢复原始样式"的表述不准确。应只修正该新增段的换行符，不改变语义。

::code-comment{title="[P2] 新增段混用 LF/CRLF" body="该 CRLF 文件中的新增方法使用了 12 个 LF-only 换行；请仅将新增段恢复为 CRLF，并同步修正报告中"已完全恢复原始样式"的表述。" file="D:/obsidian/工程/VIBECODING项目/smartube/SmartTube/common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/presenters/settings/SubtitleSettingsPresenter.java" start=70 end=81 priority=2}

非阻塞判断项：

- `TranslationRequest` 与 `TranslationResult` 重复了相同的 API-17-safe equality/hash helper。
- `(requestId, generation, epoch)` 在 bridge 内形成数据簇。两项均可留待后续重构，不建议为 M02 扩大补丁面。

## Spec

- **P2 — re-enable 测试只证明重新发起请求，没有证明能够再次完成翻译。** [AiSubtitleCueBridgeTest.java](<D:/obsidian/工程/VIBECODING项目/smartube/SmartTube/common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/integration/AiSubtitleCueBridgeTest.java:248>) 应在第二次请求后投递回调，再次 `process()`，断言精确输出 `Hello\n[ZH] Hello` 且不存在第三次请求。

::code-comment{title="[P2] 未验证重新启用后翻译成功" body="当前测试只断言 disable/re-enable 后发出了第二个请求；若所有 re-enable 后回调都被错误拒绝，测试仍会通过。请投递第二次回调并断言双行输出以及请求数仍为 2。" file="D:/obsidian/工程/VIBECODING项目/smartube/SmartTube/common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/integration/AiSubtitleCueBridgeTest.java" start=248 end=264 priority=2}

- **P2 — 报告夸大自动化覆盖。** [M02-report.md](<D:/obsidian/工程/VIBECODING项目/smartube/SmartTube/docs/ai-subtitle/worker-reports/M02-report.md:131>) 称设备矩阵"全部"由自动化覆盖，但 background/foreground、PiP 等没有对应测试，且第 150 行承认 PiP 未验证。应改为只列举实际覆盖的生命周期事件。

::code-comment{title="[P2] 自动化覆盖表述过度" body="自动化测试没有覆盖矩阵中的 background/foreground 与 PiP。请把"全部覆盖"改成对实际已覆盖事件的准确描述，并保留设备矩阵 NOT RUN。" file="D:/obsidian/工程/VIBECODING项目/smartube/SmartTube/docs/ai-subtitle/worker-reports/M02-report.md" start=131 priority=2}

其余规格检查通过：首次调用即时译文、取消与顽固晚到回调、API 17 修正、JDK 11 持久化测试、文件范围、提交数量及 M03 边界均符合要求。

验证证据：

- [Run 34618112103](https://github.com/CometDash77/smartube/actions/runs/34618112103) 与 [Run 34618912621](https://github.com/CometDash77/smartube/actions/runs/34618912621) 的 SHA 和所有关键步骤均核实为成功。
- Artifact XML 实测：bridge 19/19、controller 10/10、provider 5/5；JDK 17 data 3 skipped，JDK 11 data 3/3 passed。
- 本地重跑 34 个 JVM 测试通过；工作区干净且本地 tip 与 origin 一致。
- 本次依照 [using-superpowers](C:/Users/77182/.agents/skills/using-superpowers/SKILL.md) 与 [code-review](C:/Users/77182/.agents/skills/code-review/SKILL.md) 的双轴复核流程完成。

总结：Standards 3 项（1 个硬问题、2 个判断项，最严重为混合换行）；Spec 2 项（均为 P2）；因此 M02 暂不接受。

## Required correction

1. Complete `M02-FIX-02` as one correction package.
2. Push only after all corrections and report amendments are ready, so one replacement CI run validates the final product SHA.
3. Obtain a green run covering unit tests, the supplementary preference job, lint, assembly, reports, and APK artifact.
4. Return the amended `M02-report.md` once. Commander will re-review M02; M03 remains blocked.
