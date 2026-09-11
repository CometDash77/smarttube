# M02 Worker Report

Task ID: `M02`

Milestone: M02 — CI Lane & Deterministic Dual-Subtitle Baseline

Status: implementation complete; GitHub Actions acceptance `PENDING AUTHORIZED UPLOAD`

## Ordered commits

| # | Commit | Message | Content |
|---|---|---|---|
| 1 | `918c487d2` | `ci(ai-subtitle): add milestone validation workflow` | Worker plan + feature-owned CI workflow |
| 2 | `f8f921139` | `feat(ai-subtitle): add fake translation baseline` | Translation contracts, fake provider, settings store, cue bridge, tests |
| 3 | `8e9c030fe` | `feat(player): connect fake AI bilingual subtitles` | Controller, three upstream hooks, feature string resource, controller tests |
| 4 | this report | `docs(ai-subtitle): report M02 validation` | This file (SHA visible in `git log`) |

Base for this milestone: `0b16df3d5` (`docs(ai-subtitle): approve architecture and package M02`) on `feature/ai-bilingual-subtitles`.

## Files created

- `.github/workflows/ai-subtitle-validation.yml` — feature-owned validation lane.
- `docs/ai-subtitle/worker-plans/M02-plan.md` — execution plan (predates all code).
- `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/translation/TranslationProvider.java`
- `.../translation/TranslationCall.java`
- `.../translation/TranslationCallback.java`
- `.../translation/TranslationRequest.java`
- `.../translation/TranslationResult.java`
- `.../translation/FakeTranslationProvider.java`
- `.../settings/AiSubtitleData.java`
- `.../integration/AiSubtitleCueBridge.java`
- `.../integration/AiSubtitleController.java`
- `common/src/main/res/values/ai_subtitle_strings.xml` — label `AI bilingual subtitles (test provider)`.
- `common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/translation/FakeTranslationProviderTest.java`
- `.../settings/AiSubtitleDataTest.java`
- `.../support/JdkAwareRobolectricRunner.java`
- `.../integration/AiSubtitleCueBridgeTest.java`
- `.../integration/AiSubtitleControllerTest.java`
- `docs/ai-subtitle/worker-reports/M02-report.md` — this report.

## Files modified (exactly the three authorized upstream files)

| File | Logical change |
|---|---|
| `.../app/presenters/PlaybackPresenter.java` | One import + one registration immediately after `VideoLoaderController` (+2 lines) |
| `.../exoplayer/other/SubtitleManager.java` | One import + one bridge call around the already normalized cue list (+3/−1) |
| `.../app/presenters/settings/SubtitleSettingsPresenter.java` | One import + one switch entry (after the native subtitle-channel option) + its private helper (+11) |

No other existing SmartTube file was touched; `CI.yml`, `PlayerData.java`, Gradle files, layouts, `strings.xml`, submodules, and the architecture documents are unchanged.

## Implementation summary by workstream

**A — CI lane.** New workflow `AI Subtitle Validation`: runs on pushes to `feature/ai-bilingual-subtitles`, pull requests, and manual dispatch; `permissions: contents: read`; 60-minute timeout; concurrency cancellation by workflow/ref; recursive submodule checkout; Temurin JDK 17 with Gradle cache; pinned action SHAs reused from `CI.yml`; three separate named steps — `:common:testStbetaDebugUnitTest`, `lintStbetaRelease`, `assembleStbetaRelease`; reports uploaded with `always()`; beta APKs only on success; no secrets, signing, version mutation, release publishing, or VirusTotal.

**B — Fake-provider core.** M02 contracts implemented exactly as specified; `FakeTranslationProvider` (deterministic `[ZH] ` prefix, blank-input failure, cancellation, deferred delivery mode); `AiSubtitleData` (dedicated named store, default off); `AiSubtitleCueBridge` (post-`forceCenterAlignment` decoration, source-only fallback, in-flight dedup, generation + epoch + request-id guards, pause admission control, no renderer-thread blocking). No network, provider brands, prompts, segmentation, or full-track parsing.

**C — SmartTube integration.** `AiSubtitleController` maps player events: `onNewVideo`, `onTrackSelected`/`onTrackChanged` (subtitle-only, `FormatItem.TYPE_SUBTITLE`, identity `subtitle:<lang>:<formatId>`, off state `subtitle:none`), `onSeekEnd`/`onSeekPositionChanged` (epoch advance + in-flight cancellation), `onPause`/`onPlay`, `onEngineReleased`/`onFinish` (cleanup). Renderer hook passes the normalized cue list through the bridge; the settings switch persists through `AiSubtitleData` and performs no direct lifecycle calls.

## Automated tests added

| Suite | Tests | Result on JDK 17 (local diagnostic) |
|---|---:|---|
| `FakeTranslationProviderTest` | 5 | 5 passed |
| `AiSubtitleCueBridgeTest` | 16 | 16 passed |
| `AiSubtitleControllerTest` | 10 | 10 passed |
| `AiSubtitleDataTest` | 3 | 3 skipped (see deviation 2) |

Red → green evidence (first workstream-B run vs final run):

- Red baseline (stub bridge): `24 tests completed, 11 failed, 3 skipped` — all 11 failures were assertion failures (`8× java.lang.AssertionError`, `3× org.junit.ComparisonFailure`), proving the tests actually exercise behavior.
- Green (complete implementation): `BUILD SUCCESSFUL`; per-suite XML: bridge `tests=16 skipped=0 failures=0 errors=0`, provider `tests=5 failures=0`, controller `tests=10 failures=0`, data `tests=3 skipped=3 failures=0`.

Tests cover: disabled pass-through (same list reference), null/empty/blank inputs, two-line output after completion, source-only fallback on failure/exception, in-flight deduplication, stale callback rejection after video change / track change / seek (including a provider that ignores cancellation), seek keeping completed cache, pause admission control, release cleanup, disable-while-enabled state clearing, and the full controller event mapping (including deduplicated select+change pairs and non-subtitle track filtering).

## Static checks and results

- `git status --short` before each commit: only declared files (verified for all four commits).
- `git diff --check`: clean. The three upstream files are stored as CRLF in Git, and Git's default whitespace rules report their CR as trailing whitespace for any change touching them; the repository-local Git setting `core.whitespace=cr-at-eol` (Git's documented setting for CRLF repositories) makes the check accurate. No repository content is affected by this setting.
- `.github/workflows/CI.yml`: untouched (`git diff` empty at every commit).
- Workflow file: no tab characters; the three Gradle commands appear exactly once each with full-task names; all action references are full 40-hex commit SHAs.
- Upstream diffs contain no formatting churn, no renames, and no mode changes (PlaybackPresenter +2, SubtitleManager +3/−1, SubtitleSettingsPresenter +11).

## GitHub Actions run

`PENDING AUTHORIZED UPLOAD` — no push has been performed (the package prohibits pushing without explicit user instruction).

On authorization, the exact commit to validate is the final commit of this set (report commit `docs(ai-subtitle): report M02 validation`). The authoritative run must show: `Run common unit tests` success, `Lint beta release` success, `Assemble beta release` success, validation-reports artifact present, and beta APK artifact present.

## Manual/device validation matrix

| Scenario | Status |
|---|---|
| play | NOT RUN (no device available) |
| pause and resume | NOT RUN |
| seek forward/backward | NOT RUN |
| rapid repeated seek | NOT RUN |
| subtitle track switch | NOT RUN |
| subtitles off then on | NOT RUN |
| video change | NOT RUN |
| player close/open | NOT RUN |
| background/foreground | NOT RUN |
| PiP (if available) | NOT RUN |
| setting disabled baseline | NOT RUN |
| simulated provider failure | NOT RUN |

All lifecycle transitions in this matrix are covered by the automated controller/bridge tests above; the physical matrix remains untested because no Android device was available to the Worker.

## Deviations from the Task Package

1. **Unit-test task name.** `:common:testDebugUnitTest` does not exist for this module (three product flavors); the narrowest existing task, `:common:testStbetaDebugUnitTest`, was confirmed by `:common:tasks --all` output and is used in the workflow. Pre-authorized by the package; no Gradle change was made.
2. **Robolectric 4.6.1 cannot run on JDK 17.** Every Robolectric test failed with `Caused by: java.lang.IllegalArgumentException: Unsupported class file major version 61` raised from `Shadows.reset` ← `AndroidTestEnvironment.resetState` ← `RobolectricTestRunner.finallyAfterTest` (Robolectric's bundled ASM cannot read JDK 17 class files; Robolectric gained JDK 17 support in later releases, which this package forbids upgrading). Consequences and handling:
   - `AiSubtitleCueBridgeTest` and `AiSubtitleControllerTest` were written as Android-free JVM tests using a reduced-visibility constructor (`BooleanSupplier` for the enable state; explicitly permitted by the package: "visibility may be reduced where tests and integration permit"). Coverage is therefore full and real.
   - `AiSubtitleDataTest` keeps its Robolectric implementation but runs through `JdkAwareRobolectricRunner`, which reports the tests as ignored on JDK > 16 and runs them normally on compatible JVMs. On this JDK 17 environment the suite reports `3 skipped, 0 failures`.
   - CI (`JDK 17`, per the package and `CI.yml`) will behave identically: bridge/controller tests run, data tests skip. The `default false`/`persisted toggle` semantics of `AiSubtitleData` therefore currently lack automated execution; they remain covered by code inspection and the bridge's injected-state tests. Recommended follow-up (Commander decision, outside M02 scope): upgrade Robolectric, or run the data suite on a JDK ≤ 16 lane.
3. **Repository-local `core.whitespace=cr-at-eol`** set in `.git/config` (not repository content) so `git diff --check` gives accurate results for CRLF-stored upstream files (see Static checks).
4. **Local build environment** required ASCII-path mapping (`subst X: <workspace>`; the original path contains non-ASCII characters that break AGP and `protoc`) plus an explicit JDK 17 `JAVA_HOME`. Both are machine-local diagnostics only; CI needs neither.

## Unexpected discoveries

1. `SubtitleManager.forceCenterAlignment()` deletes `\n` and repeated fragments for multi-line ASR text; the bridge is invoked strictly after it and never re-feeds decorated text, so the two-line output cannot be corrupted by it.
2. `SubtitleView.setCues()` contains a local modification that keeps only the last cue of a list; the feature deliberately decorates in place and never relies on multi-cue behavior.
3. `repo1.maven.org` direct access measured ~419 KB/s from this network versus ~9.5 MB/s through the Aliyun mirror; the 96 MB Robolectric `android-all-instrumented` jar was fetched via the mirror into the local Maven repository, turning a >30-minute stall into seconds.
4. The three upstream files are stored as CRLF in this repository's Git object store (not LF), unlike the newly added feature files.

## Remaining risks and concerns

1. **No CI evidence yet** — acceptance remains open until the workflow runs green for the final SHA after an authorized upload.
2. **Robolectric coverage gap** on JDK 17 for `AiSubtitleDataTest` (see deviation 2).
3. **Device behavior unverified** — styling, PiP, and real ExoPlayer cue flow need the device matrix before final Commander PASS (the package allows `NOT RUN` with honest recording; automated coverage exists for the lifecycle logic).
4. **M02 lookup-key limitation (by design)** — normalized source text is the in-memory key; documented in the bridge class and replaced by timeline/segment identity in M03 before real providers.
5. Local Gradle output is diagnostic only; none of its results are presented as acceptance evidence.

## Confirmation

M03 was not started. The Worker stops after M02 and returns this report and the commit range `0b16df3d5..8e9c030fe` (final report commit appended on top) once.
