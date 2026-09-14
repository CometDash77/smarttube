# M07–M09 Correction — Continuation State (breakpoint handoff)

> **For a session resuming this work:** this file is the *state* of the correction run, not a
> second plan. The authoritative task list is `review-plans/M07-M09-code-correction-plan.md`
> (SHA-256 `a13a5687ce01ffe7b06504376e13a097e6b6e5776926fa02dd6813684fb130a7`); read it for task
> detail. Read this file first for where the run stands, what is uncommitted, how to run a cycle,
> and what was already tried and failed. The binding execution rules live in
> `docs/ai-subtitle/progress.md` under *M07–M09 correction run — execution contract*. The
> environment traps are in `AGENTS.md` under *环境与流程坑* — read those before your first build.

## 1. Where the run stands

- Repository root: `D:\obsidian\工程\VIBECODING项目\smartube\SmartTube`, branch
  `feature/ai-bilingual-subtitles`. Run base `a22e525df`.
- Committed: `28d8311e0` (execution contract), `fccf8d9ca` (phase 1 = plan tasks 1–2),
  `96a437c59` (phase status + this file's first version).
- **Uncommitted in the working tree right now** (phase 1 review fixes, verified but not committed —
  see §3 and §4):
  - `common/.../ai/subtitle/integration/AiSubtitleCueBridge.java`
  - `common/.../ai/subtitle/integration/AiSubtitleRuntime.java`
  - `common/.../ai/subtitle/settings/AiSubtitleData.java`
  - `common/.../ai/subtitle/source/SmartTubeSubtitleSourceAdapter.java`
  - `common/.../ai/subtitle/segmentation/SegmentationLimits.java` (new file)
  - `common/.../ai/subtitle/settings/remote/AiSubtitlePhoneInputServerTest.java` (test)
  - `docs/ai-subtitle/worker-reports/M07-M09-correction-phase1-report.md`
  - `AGENTS.md` (workspace root — the environment-traps section)
- Untracked and deliberately preserved: `docs/ai-subtitle/reviews/M09-review-request.md`.
- **Nothing is pushed. No tag, no release. Device items stay `PENDING DEVICE`.**
- Phases remaining, using the plan's five commit units: phase 2 = task 3, phase 3 = tasks 4–5,
  phase 4 = task 6, phase 5 = tasks 7–8.

## 2. What phase 1 delivered

Tasks 1–2 of the plan, closing C1–C4. The counterexample-to-test mapping, red-run evidence and
the lane numbers are in `docs/ai-subtitle/worker-reports/M07-M09-correction-phase1-report.md`.

Both review gates ran on `fccf8d9ca` and their findings are dispositioned in §4. The phase-1 code
review is **complete**; there is no outstanding review for phase 1.

## 3. The exact breakpoint

Phase 1 is code-complete, both lanes were re-verified after the review fixes, and the review
fixes are in the working tree waiting on one thing only: **a commit**. The next session should:

1. Re-run both lanes if the working tree has moved (recipe in §5), confirming JDK 17 full and
   JDK 11 settings are green with the numbers recorded in §4.
2. Commit the phase-1 review fixes together with the updated phase report and `AGENTS.md`.
3. Then start phase 2 (plan task 3) using the anchors in §6.

Do not re-open phase 1 unless a lane is red.

## 4. Phase 1 review: findings and dispositions

Two axes were reviewed by separate subagents on the diff `fccf8d9ca`; neither ran tests.

**Standards axis.** Verified and fixed:

- *Blocking* — `rebindTranslationIdentity`'s javadoc claimed the bounded context flows through it;
  it does not (`onContextEnabledChanged` goes straight to the rebuild). Javadoc corrected, and the
  sibling docs now name the two paths explicitly.
- `applySchedulingToBridge` had **zero callers** once the phone path moved to `applyToBridge`;
  deleted rather than left as dead public surface.
- The segmentation-limits rule existed in three copies. Extracted to
  `segmentation/SegmentationLimits.isValid(long, long, long)` and used by `AiSubtitleData`,
  `AiSubtitleCueBridge` and `SmartTubeSubtitleSourceAdapter`. The parameters stay `long` on
  purpose: the settings path range-checks before narrowing, and an `int` signature would let an
  out-of-range value truncate into something that looks valid.
- The identity guard was copied verbatim into both rebind paths; extracted to
  `isSessionIdentityCurrent()`, and `rebuildTranslationSession` renamed to `rebuildLiveSession` to
  stop it colliding by one letter with `rebindTranslationIdentity`.
- Unused `java.io.InputStream` import removed from the phone test.

Rejected with reason (recorded, not implemented):

- *Data Clumps* — threading `(targetChars, maxChars, longSentenceChars)` through five signatures
  was flagged, and a `SegmentationLimits` value object suggested. **The plan mandates the three
  explicit `int` parameters** (`buildTimeline` accepts them as explicit parameters; the old test
  entry may snapshot and forward). Introducing a value object would contradict the plan and add a
  type the plan did not authorise, so the shape stays. The validation duplication the same finding
  raised *was* fixed, above.

**Spec axis.** Verified and fixed:

- *F2 (the plan's "selected track" precondition was missing)* — `canStartWork()` omitted it. Fixed
  in a way that does not break the existing suite: the bridge now tracks whether a track event has
  ever arrived (`mTrackStateKnown`). Before the first event it cannot tell "subtitles off" from
  "nothing selected yet", so the displayed-cue fallback stays available; after one, "no selected
  track" means subtitles are off and no scheduler is built. `recreateScheduler()` is now gated by
  `canStartWork()`, and `rebuildLiveSession()` always creates the session (so lifecycle state and
  identity stay observable) and creates the scheduler only when work can actually run. This keeps
  `subtitlesOffAndOnRestartFromCleanState` green, which needs a session snapshot after
  subtitles-off.
- *Report wording* — the C3 row claimed the old phone path "applied only the scheduling limits".
  It actually called two entries (`applySchedulingToBridge` **and** `applyToBridge`), so the
  provider/prompt and the scheduling limits did apply; what never reached the running bridge was
  the bilingual order, the context switch and the streaming switch. The report now says that.
- *Test name* — `aPhoneSaveAppliesToTheLiveBridgeThatIsAlreadyPlaying` overstated what it asserts.
  Renamed to `aPhoneSaveLeavesTheLiveBridgeConfiguredAndResolvable`, with the javadoc and the
  assertion message reworded to say "the save goes through the singleton", not "the saved values
  took effect".

Accepted as a recorded partial (not fixed, by decision):

- *F5, apply order* — `applySettingsToBridge` applies segmentation **first**, where the plan lists
  it last. The javadoc explains why: applying it last lets the settings before it dispatch requests
  that the new cut immediately invalidates. This is a deliberate deviation, recorded rather than
  reverted.
- *F6, assertion strength* — the "no work" behaviour-matrix tests assert request counts, not the
  request source text the plan asked for. For a test whose subject is "nothing was dispatched",
  the count is the honest assertion; the two tests where a request *is* expected do assert the
  source text. Recorded as partial.

### The one open PARTIAL: task 2 bullet 7

The plan asks the JDK 11 phone test to observe the live bridge's actual request — the `system`
body and the transport mode handed to the provider. It is **not delivered**. Everything below is
what was established, so the next attempt does not repeat it.

Established:

- Real HTTP from the JDK 11 Robolectric lane **works**: a direct `OkHttpRequestExecutor` POST to a
  local `ServerSocket` arrives and its body is captured. The transport is not the problem.
- The adapter accepts `http://` base URLs (`OpenAiChatCompletionsAdapter` validates the scheme;
  `http` and `https` are both allowed) and `resolveTimeout()` returns the 30 s default, so the
  request is constructible.
- `ProviderProfileResolver.resolve` and `TranslationProfileResolver.resolve` are **offline** — they
  read stored state and construct an adapter; no network is touched during resolution.
- With the bridge instrumented, `findOrRequest` reached its dispatch branch with
  `session=true provider=true prompt=true scheduler=true enabled=true`. The bridge was fully
  configured; the dispatch was attempted.

Not established:

- Where the request then goes. The runtime status stayed `TRANSLATING` with an empty
  `getLastError()` for 20 s, and the local endpoint recorded no connection. No failure was ever
  reported either, which rules out the adapter failing fast.
- One instrumentation run printed `adapter.translate entered` / `streaming=false` six times with
  `OkHttpRequestExecutor.execute` never printing — but that run exercised only the phone page's
  `/test` route, which uses the **injected fake executor**. That observation therefore says
  nothing about the bridge's path and must not be read as evidence either way.

A plausible next step: instrument `OkHttpRequestExecutor.execute` and the bridge together in a
test that actually calls `bridge.process`, and check whether OkHttp under Robolectric picks its
Android platform for this path (cleartext policy) — or simply accept the PARTIAL, since the
bridge-level behaviour of C3 is covered by the JDK 17 integration tests, which do observe the
requests. The phase report already records the gap honestly; nothing is claimed as passing.

## 5. One execution cycle, exactly

Never build inside the repository (the path holds Chinese characters and `protoc` fails on them).
Build in an ASCII copy, sync into it, and run there. Read `AGENTS.md` *环境与流程坑* first: the
copy and the repository are two trees, and a stale XML can make a failed run look green.

```bash
# 1. copy once. MSYS2_ARG_CONV_EXCL is required: without it Git Bash rewrites /E into a path and
#    robocopy prints its usage instead of copying. Exit 1 = files copied, >=8 = error, 0 = nothing.
MSYS2_ARG_CONV_EXCL='*' robocopy \
  "D:\\obsidian\\工程\\VIBECODING项目\\smartube\\SmartTube" "C:\\tmp\\smartube-code-fix" \
  /E /XD build .gradle .git /XF .git /R:1 /W:1 /NFL /NDL /NJH /NJS

# 2. sync only what you changed (main and test paths mirror each other)
SRC="D:/obsidian/工程/VIBECODING项目/smartube/SmartTube"; DST="/c/tmp/smartube-code-fix"
cp "$SRC/<changed file>" "$DST/<changed file>"

# 3. run. Both lanes write the same results directory, so archive the XML between them, and
#    delete it first so a compile failure cannot leave last round's XML in place.
cd "$DST"
rm -rf common/build/test-results/testStbetaDebugUnitTest
JAVA_HOME='C:\Users\77182\.gradle\jdks\jetbrains_s_r_o_-17-amd64-windows.2' \
ANDROID_HOME='C:\Users\77182\AppData\Local\Android\Sdk' \
  ./gradlew.bat --no-daemon :common:testStbetaDebugUnitTest
cp -r common/build/test-results/testStbetaDebugUnitTest /c/tmp/ev-jdk17
rm -rf common/build/test-results/testStbetaDebugUnitTest
JAVA_HOME='C:\Users\77182\.gradle\jdks\temurin-11' \
ANDROID_HOME='C:\Users\77182\AppData\Local\Android\Sdk' \
  ./gradlew.bat --no-daemon :common:testStbetaDebugUnitTest \
  --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.*'
cp -r common/build/test-results/testStbetaDebugUnitTest /c/tmp/ev-jdk11
```

Narrow a cycle with repeated `--tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.<pkg>.*'`.
One cycle costs roughly 55–90 s per lane including compile.

Read the result from the XML, never from `BUILD SUCCESSFUL` alone:

```bash
python -c "
import glob,xml.etree.ElementTree as ET
s=[ET.parse(f).getroot() for f in glob.glob('common/build/test-results/testStbetaDebugUnitTest/TEST-*.xml')]
print('suites=%d tests=%d failures=%d errors=%d skipped=%d'%(len(s),sum(int(x.get('tests')) for x in s),sum(int(x.get('failures')) for x in s),sum(int(x.get('errors')) for x in s),sum(int(x.get('skipped')) for x in s)))
for r in s:
    for tc in r.iter('testcase'):
        for x in list(tc.iter('failure'))+list(tc.iter('error')):
            print('FAIL', tc.get('name'), '|', (x.get('message') or '')[:300])
"
```

### Expected numbers at this breakpoint

| Lane | suites | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|---:|
| JDK 17 full | 51 | 475 | 0 | 0 | 21 |
| JDK 11 settings | 11 | 79 | 0 | 0 | 0 |

Baseline at `10d6a18cf` was 460/20 and 78/0. The 15 added tests are 12 in
`AiSubtitleCueBridgeSessionTest`, one each in `AiSubtitleControllerTest`,
`SmartTubeSubtitleSourceAdapterTest` and `AiSubtitlePhoneInputServerTest`. The JDK 17 skip count
moved 20 → 21 because the new phone test joins the ten `AiSubtitlePhoneInputServerTest` methods
that `JdkAwareRobolectricRunner` skips above JDK 16; it runs for real in the JDK 11 lane.

## 6. Anchors for the remaining work

The audit's probe scripts and their recorded failures define "red" for each remaining task.

- Probe scripts: `C:\tmp\m07m09-evidence\review-m07m09-evidence\add-review-probes.ps1` and
  `add-extra-probes.ps1` (extracted from `review-plans/M07-M09-review-evidence.zip`).
- Recorded failures: `C:\tmp\m07m09-evidence\review-m07m09-evidence\probe-summary.json`.
- Each script appends to the test class it names and **must only be run once per copy**. The
  phase-1 pattern is the one to repeat: paste the probe body as a permanent test under a
  descriptive name, run it red, then fix production code.

| Phase | Task | Probes still expected red | Recorded failure to reproduce |
|---|---|---|---|
| 2 | 3 | `reviewEvictedCurrentTranslationCanBeRequestedAgain` | `eviction cannot leave SUCCEEDED permanently source-only` |
| 2 | 3 | `reviewNoTimelineFallbackWorkIsBounded` | `fallback work also needs a bound; actual=1000` |
| 3 | 4 | `reviewDeadlineBeforeHeadersMustBeTimeoutNotCancelled` | `expected:<TIMEOUT> but was:<CANCELLED>` |
| 3 | 4 | `reviewStreaming429KeepsRetryableCategory` | `expected:<RATE_LIMITED> but was:<PROTOCOL>` |
| 3 | 4 | `reviewStreamingOverloadRemainsRetryable` | `expected:<SERVER> but was:<PROTOCOL>` |
| 3 | 5 | `reviewUnknownFinishReasonIsNotSuccessful` | `only an accepted completion may succeed expected null, but was:<…text=half, final>` |
| 4 | 6 | `reviewStreamingOffRepaintsWithoutManualProcess` | `clearing the visible draft must repaint expected:<2> but was:<1>` |
| 5 | 7 | `reviewExistingBaselineMigratesOnceAndCustomSurvives` | **already passes** — keep as a permanent regression, do not expect red |

Production files each phase owns, from the plan's task headers:

- Phase 2 (task 3): `scheduler/TranslationScheduler.java`; `cache/InMemoryTranslationCache.java`
  only if needed. Tests: `scheduler/TranslationSchedulerCapacityTest.java`,
  `scheduler/TranslationSchedulerTest.java`, `cache/InMemoryTranslationCacheTest.java`.
- Phase 3 (tasks 4–5): `provider/http/OkHttpRequestExecutor.java`,
  `provider/OpenAiChatCompletionsAdapter.java`, `provider/AnthropicMessagesAdapter.java`.
  Tests: `provider/http/OkHttpStreamingTest.java`, both adapter tests, and one scheduler + real
  adapter + fake transport closed loop in `scheduler/TranslationSchedulerTest`.
- Phase 4 (task 6): `integration/AiSubtitleCueBridge.java`. Tests:
  `integration/AiSubtitleCueBridgeSessionTest.java`, `integration/AiSubtitleCueBridgeModeTest.java`.
- Phase 5 (tasks 7–8): `prompt/BuiltInSubtitlePrompts.java`; the fixture
  `common/src/test/resources/ai-subtitle/fixtures/independent-cases.json`;
  `docs/ai-subtitle/fixture-provenance.md`; then the reports and plans listed in plan task 8.

### Notes that will save you time in phase 2

- `TranslationScheduler.dispatch` already returns early on `mSession.isPaused()`, so restoring a
  paused session is enough to stop window dispatch; the scheduler's own `mPaused` need not be set.
- `pruneWorkLocked` currently skips any work whose `startTime` is negative, which is exactly the
  no-timeline fallback case the second probe measures — that is the hole to close.
- `submitLocked` is the one shared entry where the attempt budget must be enforced.
- `freezeRequestLocked` is **not** wrapped in a try/catch inside `submitLocked`; an exception there
  propagates out of `requestCurrentUnit` and is swallowed by `AiSubtitleCueBridge.process`.

## 7. Exit condition for each remaining phase

1. Every plan bullet for the phase's tasks is implemented in the owner the plan names.
2. The phase's probes ran red before the fix and are permanent tests afterwards under descriptive
   names, with the red failure text quoted in the phase report.
3. Both lanes ran on a fresh ASCII copy; the XML-parsed numbers are in the phase report and in
   `progress.md`; new tests are named and any skip-count change is explained.
4. A Standards/Spec review subagent was dispatched on the phase diff; each finding is either fixed
   or recorded with the decision and the reason. Review does not run tests, and a review verdict is
   never quoted as a test result.
5. Code, ledger and phase report are committed together, with no push, tag or release.
