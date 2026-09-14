# M07–M09 Correction — Continuation State

> **For a session resuming this work:** this file is the *state* of the correction run, not a
> second plan. The authoritative task list is `review-plans/M07-M09-code-correction-plan.md`
> (SHA-256 `a13a5687ce01ffe7b06504376e13a097e6b6e5776926fa02dd6813684fb130a7`); read it for the
> task details. Read this file first for where the run actually stands, how to run a cycle, and
> which method names the remaining work is anchored to. The binding execution rules are in
> `docs/ai-subtitle/progress.md` under *M07–M09 correction run — execution contract*.

## Where the run stands

- Repository root: `D:\obsidian\工程\VIBECODING项目\smartube\SmartTube`, branch
  `feature/ai-bilingual-subtitles`.
- Run base: `a22e525df`.
- Commits so far: `28d8311e0` (execution contract), `fccf8d9ca` (phase 1 = plan tasks 1–2).
- Phase 1 closed C1–C4 and is green on both lanes. Its report is
  `worker-reports/M07-M09-correction-phase1-report.md`; it also records the one open PARTIAL.
- Phases remaining, using the plan's five commit units: phase 2 = task 3, phase 3 = tasks 4–5,
  phase 4 = task 6, phase 5 = tasks 7–8.
- Nothing has been pushed; there is no tag and no release. Device items stay `PENDING DEVICE`.

## One execution cycle, exactly

Do not build inside the repository: the path contains Chinese characters and `protoc` fails on it.
Work in an ASCII copy, sync into it, and run there.

```bash
# 1. copy (once). MSYS2_ARG_CONV_EXCL is required: without it Git Bash rewrites /E into a path
#    and robocopy prints its usage instead of copying.
MSYS2_ARG_CONV_EXCL='*' robocopy \
  "D:\\obsidian\\工程\\VIBECODING项目\\smartube\\SmartTube" "C:\\tmp\\smartube-code-fix" \
  /E /XD build .gradle .git /XF .git /R:1 /W:1 /NFL /NDL /NJH /NJS

# 2. sync only what you changed (src and test paths mirror each other)
SRC="D:/obsidian/工程/VIBECODING项目/smartube/SmartTube"; DST="/c/tmp/smartube-code-fix"
cp "$SRC/<changed file>" "$DST/<changed file>"

# 3. run. Both lanes write to the same test-results directory, so archive the XML between them.
cd "$DST"
JAVA_HOME='C:\Users\77182\.gradle\jdks\jetbrains_s_r_o_-17-amd64-windows.2' \
ANDROID_HOME='C:\Users\77182\AppData\Local\Android\Sdk' \
  ./gradlew.bat --no-daemon :common:testStbetaDebugUnitTest
JAVA_HOME='C:\Users\77182\.gradle\jdks\temurin-11' \
ANDROID_HOME='C:\Users\77182\AppData\Local\Android\Sdk' \
  ./gradlew.bat --no-daemon :common:testStbetaDebugUnitTest \
  --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.*'
```

Narrow a cycle with repeated `--tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.<pkg>.*'`.
A cycle costs roughly 55–90 s per lane including compile.

Read the result from the XML, never from "BUILD SUCCESSFUL" alone:

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

## Anchors for the remaining work

The audit left one-file probe scripts and their recorded failures. They are the definition of
"red" for each remaining task and they are the evidence the fix is real.

- Probe scripts: `C:\tmp\m07m09-evidence\review-m07m09-evidence\add-review-probes.ps1` and
  `add-extra-probes.ps1` (already extracted from `review-plans/M07-M09-review-evidence.zip`).
- Recorded failures: `C:\tmp\m07m09-evidence\review-m07m09-evidence\probe-summary.json`.
- Each script appends to the test class it names and **must only be run once per copy**. The
  phase-1 pattern is the one to repeat: paste the probe body as a permanent test under a
  descriptive name, run it red, then fix production code.

| Phase | Plan task | Probes still expected red | Recorded failure to reproduce |
|---|---|---|---|
| 2 | 3 | `reviewEvictedCurrentTranslationCanBeRequestedAgain` | `eviction cannot leave SUCCEEDED permanently source-only` |
| 2 | 3 | `reviewNoTimelineFallbackWorkIsBounded` | `fallback work also needs a bound; actual=1000` |
| 3 | 4 | `reviewDeadlineBeforeHeadersMustBeTimeoutNotCancelled` | `expected:<TIMEOUT> but was:<CANCELLED>` |
| 3 | 4 | `reviewStreaming429KeepsRetryableCategory` | `expected:<RATE_LIMITED> but was:<PROTOCOL>` |
| 3 | 4 | `reviewStreamingOverloadRemainsRetryable` | `expected:<SERVER> but was:<PROTOCOL>` |
| 3 | 5 | `reviewUnknownFinishReasonIsNotSuccessful` | `only an accepted completion may succeed expected null, but was:<…text=half, final>` |
| 4 | 6 | `reviewStreamingOffRepaintsWithoutManualProcess` | `clearing the visible draft must repaint expected:<2> but was:<1>` |
| 5 | 7 | `reviewExistingBaselineMigratesOnceAndCustomSurvives` | **already passes** — keep it as a permanent regression, do not expect red |

Production files per phase, from the plan's task headers:

- Phase 2 (task 3): `scheduler/TranslationScheduler.java`; `cache/InMemoryTranslationCache.java`
  only if it turns out to be needed. Tests: `scheduler/TranslationSchedulerCapacityTest.java`,
  `scheduler/TranslationSchedulerTest.java`, `cache/InMemoryTranslationCacheTest.java`.
- Phase 3 (tasks 4–5): `provider/http/OkHttpRequestExecutor.java`,
  `provider/OpenAiChatCompletionsAdapter.java`, `provider/AnthropicMessagesAdapter.java`.
  Tests: `provider/http/OkHttpStreamingTest.java`, both adapter tests, and one
  scheduler + real adapter + fake transport closed loop in `scheduler/TranslationSchedulerTest`.
- Phase 4 (task 6): `integration/AiSubtitleCueBridge.java`. Tests:
  `integration/AiSubtitleCueBridgeSessionTest.java`, `integration/AiSubtitleCueBridgeModeTest.java`.
- Phase 5 (tasks 7–8): `prompt/BuiltInSubtitlePrompts.java`; the fixture
  `common/src/test/resources/ai-subtitle/fixtures/independent-cases.json`;
  `docs/ai-subtitle/fixture-provenance.md`; then the reports and plans listed in plan task 8.

## Traps this run already paid for

- `MSYS2_ARG_CONV_EXCL='*'` for robocopy, or `/E` becomes a path and nothing is copied.
- The two lanes overwrite `common/build/test-results/testStbetaDebugUnitTest`. Archive the XML
  after each lane, or one lane's numbers will be quoted as the other's.
- Adding a test to `AiSubtitlePhoneInputServerTest` raises the JDK 17 **skipped** count by one.
  `JdkAwareRobolectricRunner` skips that class above JDK 16 and it runs for real on JDK 11. A
  skip-count change is expected, not a regression.
- `AiSubtitleData.isEnabled()` defaults to **false**; a test that drives the bridge must set it.
- `ProviderProfilesPresenter.create` does **not** select the profile it creates, and
  `AiSubtitleRuntime.resolve` reads the *selected* profile. A fixture that creates a profile and
  then expects resolution to work must call `data.providerProfiles().select(id)`.
- `AiSubtitleCueBridge.process` swallows exceptions by design. When a bridge-level assertion fails
  with an empty `getLastError()`, do not guess: put the missing fact in the assertion message, or
  instrument the bridge temporarily — and remove the instrumentation in the same cycle.
- `TranslationScheduler.dispatch` already returns early on `mSession.isPaused()`, so restoring a
  paused session is enough to stop window dispatch; the scheduler's own `mPaused` need not be set
  for that case.

## Exit condition for each remaining phase

1. Every plan bullet for the phase's tasks is implemented in the owner the plan names.
2. The phase's probes were run red before the fix and are permanent tests afterwards, under
   descriptive names; the red failure text is quoted in the phase report.
3. Both lanes were run on a fresh ASCII copy and the XML-parsed numbers are in the phase report
   and in `progress.md`; new tests are listed by name, and any skip-count change is explained.
4. A Standards/Spec review subagent was dispatched on the phase diff and its findings are either
   fixed or explicitly recorded with the decision and the reason. Review does not run tests, and a
   review verdict is never quoted as a test result.
5. Code, ledger and phase report are committed together, with no push, tag or release.
