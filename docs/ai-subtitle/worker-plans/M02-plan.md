# M02 Worker Execution Plan

Status: created before any repository modification

Package: `docs/ai-subtitle/tasks/M02.md` — M02 — CI Lane & Deterministic Dual-Subtitle Baseline

Executing role: external Worker; internal workstreams and commits are not separate user handoffs

## Task understanding

Prove, without any network or real provider, that SmartTube's existing caption renderer and the full player lifecycle can safely carry an opt-in bilingual vertical slice driven by a deterministic `FakeTranslationProvider`.

Stage deliverables:

- one default-off persisted setting labelled `AI bilingual subtitles (test provider)`;
- when enabled, each visible source caption renders as `source` + newline + `[ZH] source` through the existing `SubtitleView` and one Cue;
- when disabled, behavior is byte-for-byte upstream-equivalent;
- video change, subtitle-track change, subtitles off/on, seek, pause/resume, engine release, and player reopen must not leak a prior session's translation state or affect playback;
- the fake provider performs no network access.

Out of scope for M02: Provider brands, prompts, HTTP, full-track parsing, persistent translation cache, segmentation types, renderer forks, new layouts, and any Gradle/dependency change.

Execution model: workstreams A (CI lane), B (fake-provider core, test-first), C (SmartTube integration), D (regression evidence and report); four logical commits; one consolidated return.

## Starting state

- Repository: `SmartTube/` inside workspace `D:\obsidian\工程\VIBECODING项目\smartube`; `origin` = user's personal project, `upstream` = official SmartTube (read-only for this package).
- Branch: `feature/ai-bilingual-subtitles`.
- Starting HEAD: `0b16df3d5` (`docs(ai-subtitle): approve architecture and package M02`).
- Working tree: clean at plan time; submodules `MediaServiceCore` and `SharedModules` checked out at their pinned commits.
- Existing feature files: none yet; `common/src/test` does not exist (M02 adds the module's first unit tests).
- Local build environment (repaired and verified during this execution, for diagnostics only — CI remains authoritative per ADR-008 and the package):
  - JDK 17 present at `C:\Users\77182\.gradle\jdks\jetbrains_s_r_o_-17-amd64-windows.2` (JBR 17.0.14, includes `javac`); default `JAVA_HOME` points at JDK 21, so builds must set `JAVA_HOME` explicitly.
  - Gradle 7.5 distribution downloaded by the wrapper.
  - ASCII drive mapping created: `subst X: D:\obsidian\工程\VIBECODING项目\smartube`. Building from `X:\SmartTube` avoids AGP's non-ASCII path refusal and a `protoc` path corruption that occurs from the original Unicode path. No repository file needed changing for this.
  - Verification evidence: `X:\SmartTube` → `./gradlew :common:testStbetaDebugUnitTest` → `BUILD SUCCESSFUL in 1m 46s` (347 tasks), including `:common:compileStbetaDebugJavaWithJavac`.

## Exact files expected to change

Create:

- `.github/workflows/ai-subtitle-validation.yml`
- `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/integration/AiSubtitleController.java`
- `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/integration/AiSubtitleCueBridge.java`
- `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/AiSubtitleData.java`
- `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/translation/TranslationProvider.java`
- `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/translation/TranslationCall.java`
- `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/translation/TranslationCallback.java`
- `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/translation/TranslationRequest.java`
- `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/translation/TranslationResult.java`
- `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/translation/FakeTranslationProvider.java`
- `common/src/main/res/values/ai_subtitle_strings.xml`
- focused tests below `common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/`
- `docs/ai-subtitle/worker-plans/M02-plan.md` (this file)
- `docs/ai-subtitle/worker-reports/M02-report.md`

Modify (exactly three upstream files):

- `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/presenters/PlaybackPresenter.java` (one import + one registration immediately after `VideoLoaderController`)
- `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/exoplayer/other/SubtitleManager.java` (one bridge call around the already normalized cue list before `setCues`)
- `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/presenters/settings/SubtitleSettingsPresenter.java` (one default-off switch entry)

No other existing SmartTube file will change. `CI.yml`, `PlayerData.java`, Gradle files, layouts, `strings.xml`, `MediaServiceCore`, `SharedModules`, `exoplayer-amzn-2.10.6`, and the architecture documents are untouched.

## Internal workstream and commit sequence

A. CI lane and worker plan — commit `ci(ai-subtitle): add milestone validation workflow`

1. This plan, created before implementation.
2. New workflow `.github/workflows/ai-subtitle-validation.yml`; inherited `CI.yml` unchanged.
3. Workflow: runs on pushes to `feature/ai-bilingual-subtitles`, pull requests, and manual dispatch; `permissions: contents: read`; 60-minute timeout; concurrency cancellation by workflow/ref; recursive submodules; Temurin JDK 17 with Gradle cache; pinned action SHAs reused from `CI.yml` (`checkout@df4cb1c069e1874edd31b4311f1884172cec0e10`, `setup-java@be666c2fcd27ec809703dec50e508c2fdc7f6654`, `upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a`); separate named steps for `:common:testStbetaDebugUnitTest`, `lintStbetaRelease`, `assembleStbetaRelease`; reports uploaded with `always()`; beta APKs uploaded only on success; no secrets, signing, version mutation, release publishing, or VirusTotal.

B. Test-first fake-provider core — commit `feat(ai-subtitle): add fake translation baseline`

1. Failing tests first for fake output, blank-input failure, cancellation, request/result identity, disabled bridge pass-through, source-only fallback, deduplication, stale-callback rejection.
2. Minimal translation value objects/interfaces, `FakeTranslationProvider`, `AiSubtitleData`, `AiSubtitleCueBridge` to pass them.
3. No scheduling, provider profiles, network, or disk cache.

C. Minimal SmartTube integration — commit `feat(player): connect fake AI bilingual subtitles`

1. `AiSubtitleController` plus lifecycle tests.
2. `PlaybackPresenter`: one import and one registration immediately after `VideoLoaderController`.
3. `SubtitleManager.onCues`: pass the already `forceCenterAlignment`-processed list through `AiSubtitleCueBridge.process(...)` before `setCues`.
4. `SubtitleSettingsPresenter`: one default-off switch after the native subtitle-channel option; label string in the new `ai_subtitle_strings.xml`.
5. Verify the three upstream diffs contain no formatting churn.

D. Regression evidence and consolidated report — commit `docs(ai-subtitle): report M02 validation`

1. Complete automated lifecycle/state tests.
2. `docs/ai-subtitle/worker-reports/M02-report.md` with all required evidence, all commit hashes, static checks, CI state (or explicit `PENDING AUTHORIZED UPLOAD`), device matrix status, deviations, discoveries, and concerns.
3. The user does not relay or request approval between these commits; one consolidated return at the end.

## Tests added per workstream

- B: `FakeTranslationProviderTest` (deterministic output, blank failure, cancellation), `AiSubtitleDataTest` under Robolectric (default false + persisted toggle), `AiSubtitleCueBridgeTest` (disabled pass-through returning the same processed list when safe, enabled two-line output, source-only failure, null/empty/blank cues, duplicate in-flight request, exception isolation, stale generation/request rejection).
- C: `AiSubtitleControllerTest` (or feature-owned lifecycle-state collaborator test): every invalidating event, pause/resume, seek, repeated cleanup, subtitle vs non-subtitle track changes.
- No getter-only tests; every asynchronous/stale test controls callback order and proves an obsolete callback cannot change rendered state.

Test placement and annotations (verified during reconnaissance): `common/src/test/java/...` is shared by all three flavors; use `@RunWith(RobolectricTestRunner.class)` and no `@Config` (targetSdk 27 is inside Robolectric 4.6.1's supported range; `@Config(sdk=34)` is forbidden). Context comes from `RuntimeEnvironment.getApplication()`. No mockito (not a declared dependency); use hand-written fakes. Keep new files lint-clean (common uses `abortOnError true`).

## Implementation design decisions carried from reconnaissance

- Renderer: one `new Cue(text)` whose text is `source + "\n" + "[ZH] " + source` (plain text; M02 accepts the single global subtitle style). `\n` renders as a hard line break; `applyEmbeddedStyles(false)` flattens spans, which is irrelevant for plain text.
- Ordering: the bridge is invoked strictly after `forceCenterAlignment`, and the bridge's output is never re-fed through it. This sidesteps the existing `subsBuffer`/`replace("\n","")` behavior in `forceCenterAlignment` for generated two-line text.
- The bridge owns no Activity/View/Video/PlaybackPresenter reference; `SubtitleView` is `final` and is not touched.
- Track identity: subtitle events are recognized by `FormatItem.getType() == FormatItem.TYPE_SUBTITLE`; "subtitles off" is `SUBTITLE_NONE` reached through the `onTrackSelected` path (it does not appear in `onTrackChanged`); `onTrackChanged` items carry no `MediaTrack` (`getTrack() == null`), so both paths must be handled. Identity string shape: `subtitle:<groupIndex>:<trackIndex>:<language>:<formatId>`, with a fixed `subtitle:none` for the off state. `formatId` is session-scoped only (upstream marks subtitle format ids as non-constant).
- Settings: switch via `AppDialogPresenter.appendSingleSwitch(UiOptionItem.from(title, option -> ..., isEnabled()))`, inserted directly after `AppDialogUtil.createSubtitleChannelOption(...)`; state persisted in `AiSubtitleData` (dedicated named preference store, `HiddenPrefs` pattern, default off).

## Static checks

Before commit A: `git diff --check`; workflow YAML contains no tab characters; the three Gradle commands appear exactly once each; action references are full commit SHAs; `git status --short` lists only the declared files; `git diff -- .github/workflows/CI.yml` is empty.

Before the final commit: full set from the package (`git status --short --branch`, `git diff --check`, `git diff --stat upstream/master...HEAD`, diffs of the three upstream files, `CI.yml` diff empty), plus the declaration check that no undeclared file was touched.

## Local diagnostic checks (non-authoritative)

Executed from `X:\SmartTube` with `JAVA_HOME` set to the JBR 17 path:

- `./gradlew :common:testStbetaDebugUnitTest` (also serves as the test-first red/green driver while implementing);
- optionally `./gradlew lintStbetaRelease` and `./gradlew assembleStbetaRelease` as fast diagnostics before upload.

Local results are diagnostics only and will not be presented as acceptance evidence.

## GitHub Actions checks after authorized upload

Not run until the user authorizes a push. The report will state `PENDING AUTHORIZED UPLOAD` and record: the exact final SHA, and once available, run URL/ID with the required step outcomes (common unit tests, beta lint, beta assembly, reports artifact, beta APK artifact). No push will be performed unless the user explicitly instructs it.

## Known risks

1. Task name deviation: `:common:testDebugUnitTest` does not exist for this module (flavors stbeta/ststable/stfdroid); `:common:testStbetaDebugUnitTest` was observed in the module's task list. Fallback behavior is pre-authorized by the package; no Gradle change is made.
2. Robolectric 4.6.1 on JDK 17 is not yet verified. The first Robolectric test will be run locally immediately after it is written; if the runtime is incompatible, the impacted assertions will be reduced to dependency-free logic with the deviation documented (Gradle configuration cannot be changed).
3. Robolectric's first run downloads an `android-all` jar (nothing cached in `~/.m2` yet); CI runs face the same first-run download.
4. `forceCenterAlignment` text munging and the `SubtitleView.setCues` local modification (keeps only the last cue) are existing upstream behaviors; the design does not re-feed generated text through the former and uses the single-cue path the app already relies on. Tests will cover consecutive identical source text.
5. Device/manual matrix will most likely be `NOT RUN` (no device available); recorded honestly. M02 final PASS then depends on credible automated lifecycle coverage.
6. Local environment needs the `subst X:` mapping and explicit JBR 17 `JAVA_HOME` for diagnostics; the mapping does not survive a reboot.
7. `~/.gradle/gradle.properties` contains a plaintext GitHub credential used for dependency resolution. It is unrelated to this package and will never be copied into any artifact, report, or commit.

## Package vs repository evidence (conflicts and deviations)

1. Package names `./gradlew :common:testDebugUnitTest`; repository evidence shows the task is `:common:testStbetaDebugUnitTest`. Package pre-authorizes using the narrowest existing task and reporting the deviation — applied.
2. Package assumes `common` has JUnit/Robolectric dependencies and Android resources for unit tests (confirmed: junit 4.12, robolectric 4.6.1, `includeAndroidResources = true`), but the module has no existing tests; M02 adds the first ones.
3. No other conflicts found so far; any discovered during implementation will be reported, and scope will not be widened autonomously.
