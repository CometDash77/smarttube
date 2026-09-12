# M02 Worker Report

Task ID: `M02`

Milestone: M02 — CI Lane & Deterministic Dual-Subtitle Baseline

Status: second-pass correction (M02-FIX-02) complete; replacement CI run `34660051184` is green for `854460bb4`; awaiting Commander re-review

## Ordered commits

| # | Commit | Message | Content |
|---|---|---|---|
| 1 | `918c487d2` | `ci(ai-subtitle): add milestone validation workflow` | Worker plan + feature-owned CI workflow |
| 2 | `f8f921139` | `feat(ai-subtitle): add fake translation baseline` | Translation contracts, fake provider, settings store, cue bridge, tests |
| 3 | `8e9c030fe` | `feat(player): connect fake AI bilingual subtitles` | Controller, three upstream hooks, feature string resource, controller tests |
| 4 | `3efb96374` | `docs(ai-subtitle): report M02 validation` | First report revision (uploaded) |
| 5 | `26693c340` | `docs(ai-subtitle): record M02 upload and CI status` | Post-upload report amendment |
| 6 | `6e316173f` | `docs(ai-subtitle): require M02 corrections` | Commander Review + M02-FIX-01 package (Commander commit) |
| 7 | `69f644f4a` | `fix(ai-subtitle): satisfy M02 review` | First corrections: code, tests, workflow, fix plan |
| 8 | `be02bc2b3` | `docs(ai-subtitle): finalize M02 validation` | Final report revision of the first correction |
| 9 | `854460bb4` | `fix(ai-subtitle): address M02 second-pass review` | Settings-helper CRLF repair, strengthened re-enable test, review record, M02-FIX-02 package/plan, progress ledger |
| 10 | this revision | `docs(ai-subtitle): finalize M02 second-pass validation` | Amended report with the second-pass disposition and replacement-run evidence |

Base for this milestone: `0b16df3d5` (`docs(ai-subtitle): approve architecture and package M02`) on `feature/ai-bilingual-subtitles`.

## Commander Review disposition

Review: `docs/ai-subtitle/reviews/M02-review.md` — **CHANGES REQUIRED** over `0b16df3d5...26693c340`.

Correction package: `docs/ai-subtitle/tasks/M02-FIX-01.md`; correction plan (created before code): `docs/ai-subtitle/worker-plans/M02-fix-01-plan.md`; correction commit: `69f644f4a`.

### Finding corrections

| Finding (severity) | Root cause | Correction |
|---|---|---|
| First visible translation missed (P1, Spec) | `findOrRequest()` always returned `null` after starting a request, even when a synchronous provider (the production `new FakeTranslationProvider()`) had already delivered through `BridgeCallback` and populated `mCompleted`. A caption delivered once remained source-only. | `findOrRequest()` now re-reads `mCompleted` after `translate()` returns, consuming a synchronous completion inside the same `process()` call. Deferred providers are unchanged (source-only until a later rendering opportunity). New test `immediateFakeDecoratesOnTheFirstAndOnlyProcessCall` proves one-call dual-line output and cache-hit behavior; a second call must not duplicate the request. |
| API 17 violations (P1, Spec) | `java.util.function.BooleanSupplier` is API 24; `java.util.Objects` is API 19; the app's minimum SDK is 17. | `BooleanSupplier` replaced by the feature-owned nested interface `AiSubtitleCueBridge.EnableState` (production constructor still binds `AiSubtitleData`); `Objects` removed from `TranslationRequest`/`TranslationResult`, replaced by explicit Java 6-compatible null-safe `equals`/`hashCode`. No dependency, no suppression, no minSdk change. |
| Disable not immediate (P2, Spec) | The settings callback only wrote the persisted preference; cancellation happened later, if and when another non-empty cue arrived. | New public bridge entry point `onEnabledChanged(boolean)`; the switch callback now notifies the bridge synchronously, so disabling cancels in-flight calls, increments generation, and clears results before the callback returns. New tests: `disablingImmediatelyCancelsInFlightWork` (captured call must be cancelled during the notification) and `stubbornLateCallbackAfterDisableIsRejected` (a provider ignoring cancellation cannot populate cache; re-enable starts clean). |
| Persistence tests skipped (P2, Spec) | Robolectric 4.6.1 cannot complete its runner lifecycle on JDK 17. | Per **ADR-010**: a supplementary GitHub Actions job on Temurin **JDK 11** runs only `AiSubtitleDataTest` via `--tests`; it demonstrably executes all three methods and passes. JDK 17 remains authoritative for unit tests, lint, and assembly. |
| `m`-prefix violations (P3 ×2, Standards) | `PendingRequest` members and the test helper field lacked the required prefix. | Renamed to `mRequestId`/`mGeneration`/`mEpoch`/`mCall` and `RecordingBridge.mCalls`; all usages updated. |
| Lint failure (run `34615801161`) | Exact diagnostic: `AiSubtitleCueBridge.java:93: Error: Call requires API level 24 (current min is 17): java.util.function.BooleanSupplier#getAsBoolean [NewApi]` — 1 error, 385 warnings; `Lint found errors in the project; aborting build.` | Fixed by the API-17 correction above. Local diagnostic lint afterwards: **0 errors**, 385 pre-existing warnings; the replacement CI run's `Lint beta release` step passes. Nothing suppressed. |

The correction stayed inside the authorized file set (feature-owned files plus the already-approved M02 switch helper in `SubtitleSettingsPresenter.java`). During self-review a formatting regression was caught and reduced, but not actually eliminated: an intermediate rewrite of `SubtitleSettingsPresenter.java` had converted its line endings, producing 184 lines of churn; the churn was reduced to a 6-line logical diff, yet the added method still carried 12 LF-only terminators inside a CRLF-stored file. The Commander second pass flagged exactly that, and `M02-FIX-02` repaired those 12 terminators: the file is now byte-verified at **102 CRLF / 0 LF-only** lines with a 12/12 diff, so the added method matches the file's original style.

### Second pass

Review: `docs/ai-subtitle/reviews/M02-review.md` (second pass) — **CHANGES REQUIRED** over `0b16df3d5...be02bc2b3`, recorded after the reviewer verified the green runs `34618112103` and `34618912621` and re-ran the 34 JVM tests locally.

Correction package: `docs/ai-subtitle/tasks/M02-FIX-02.md`; correction plan (created before correction code): `docs/ai-subtitle/worker-plans/M02-fix-02-plan.md`; correction commit: `854460bb4`.

| Finding (severity) | Root cause | Correction |
|---|---|---|
| Mixed line endings in `SubtitleSettingsPresenter.java` (P2, Standards) | The first correction inserted the helper with LF terminators inside a file stored as CRLF (baseline 87 CRLF / 0 LF; reviewed tip 90 CRLF / 12 LF), which also made the report's "original style restored" statement inaccurate. | Rows 70–81 rewritten to CRLF only: byte-verified 102 CRLF / 0 LF-only terminators, 12/12 diff, no semantic change; the paragraph above and the modified-files table now state the repair accurately. |
| Re-enable completion unproven (P2, Spec) | `stubbornLateCallbackAfterDisableIsRejected` asserted only that disable/re-enable issues a second request; a bridge that rejected every post-re-enable callback would still have passed. | The test now delivers the post-re-enable callback, asserts the exact `Hello\n[ZH] Hello` output, and asserts the request count stays 2 (cached, no third request). Mutation check: with the delivery line removed the test fails at `AiSubtitleCueBridgeTest:267`; restored, it passes. |
| Overstated automation coverage (P2, Spec) | The report stated that all lifecycle transitions in the device matrix were covered by automation, while background/foreground and PiP have no automated equivalent. | The statement now lists exactly which lifecycle events the automated suites cover and keeps background/foreground, PiP, styling, and the device rendering path `NOT RUN`. |

The two judgement items the review deferred — the duplicated API-17-safe equality/hash helpers and the `(requestId, generation, epoch)` cluster — were deliberately left untouched: the correction package authorizes no widening of the M02 patch.

## Files created

- `.github/workflows/ai-subtitle-validation.yml` — feature-owned validation lane (primary + JDK 11 preference job).
- `docs/ai-subtitle/worker-plans/M02-plan.md` — execution plan (predates all code).
- `docs/ai-subtitle/worker-plans/M02-fix-01-plan.md` — first correction plan (predates correction code).
- `docs/ai-subtitle/tasks/M02-FIX-02.md` — second-pass correction package.
- `docs/ai-subtitle/worker-plans/M02-fix-02-plan.md` — second-pass correction plan (predates correction code).
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

## Files modified (upstream surface)

| File | Logical change |
|---|---|
| `.../app/presenters/PlaybackPresenter.java` | One import + one registration immediately after `VideoLoaderController` (+2 lines) |
| `.../exoplayer/other/SubtitleManager.java` | One import + one bridge call around the already normalized cue list (+3/−1) |
| `.../app/presenters/settings/SubtitleSettingsPresenter.java` | One import + one default-off switch + one narrow disable notification inside the approved helper (+5/−1 in the first correction); M02-FIX-02 then rewrote 12 terminators to CRLF with no semantic change |

No other existing SmartTube file was touched; `CI.yml`, `PlayerData.java`, Gradle files, layouts, `strings.xml`, submodules, and the architecture documents are unchanged. No new upstream hook was added by either correction.

## Implementation summary by workstream

**A — CI lane.** Feature-owned workflow `AI Subtitle Validation`: runs on pushes to `feature/ai-bilingual-subtitles`, pull requests, and manual dispatch; `permissions: contents: read`; concurrency cancellation by workflow/ref; recursive submodule checkout; pinned action SHAs reused from `CI.yml`. Primary job (JDK 17, 60-minute timeout): `:common:testStbetaDebugUnitTest`, `lintStbetaRelease`, `assembleStbetaRelease` as separate named steps; reports uploaded with `always()`; beta APKs only on success. Supplementary job (JDK 11, 30-minute timeout, ADR-010): only `:common:testStbetaDebugUnitTest --tests ...settings.AiSubtitleDataTest` plus its report upload. No secrets, signing, version mutation, release publishing, or VirusTotal anywhere. The second-pass correction did not touch the workflow.

**B — Fake-provider core.** M02 contracts implemented as specified; `FakeTranslationProvider` (deterministic `[ZH] ` prefix, blank-input failure, cancellation, deferred delivery mode); `AiSubtitleData` (dedicated named store, default off); `AiSubtitleCueBridge` (post-`forceCenterAlignment` decoration, synchronous-completion consumption, source-only fallback, in-flight dedup, generation + epoch + request-id guards, pause admission control, immediate-disable invalidation, no renderer-thread blocking). No network, provider brands, prompts, segmentation, or full-track parsing. The second-pass correction changed no production behavior.

**C — SmartTube integration.** `AiSubtitleController` maps player events: `onNewVideo`, `onTrackSelected`/`onTrackChanged` (subtitle-only, `FormatItem.TYPE_SUBTITLE`, identity `subtitle:<lang>:<formatId>`, off state `subtitle:none`), `onSeekEnd`/`onSeekPositionChanged` (epoch advance + in-flight cancellation), `onPause`/`onPlay`, `onEngineReleased`/`onFinish` (cleanup). Renderer hook passes the normalized cue list through the bridge; the settings switch persists through `AiSubtitleData` and notifies the bridge on disable.

## Automated tests

Executed counts read from the artifacts of the green run `34660051184` (`ai-subtitle-validation-reports-4` and `ai-subtitle-preference-reports-4`, downloaded and parsed per suite):

| Suite | JDK 17 primary job | JDK 11 preference job |
|---|---|---|
| `FakeTranslationProviderTest` | 5 passed | not run (other suite only) |
| `AiSubtitleCueBridgeTest` | **19 passed** (16 + 3 correction tests) | not run |
| `AiSubtitleControllerTest` | 10 passed | not run |
| `AiSubtitleDataTest` | 3 skipped (deliberate, ADR-010) | **3 passed, 0 skipped, 0 failures** |

The JDK 11 artifact executes all three preference methods: `defaultsToDisabled`, `disablingRestoresDefault`, `enabledStateIsPersistedAcrossInstanceRecreation`.

Correction tests added by M02-FIX-01: `immediateFakeDecoratesOnTheFirstAndOnlyProcessCall`, `disablingImmediatelyCancelsInFlightWork`, `stubbornLateCallbackAfterDisableIsRejected`; `disablingTheSettingClearsStateAndRestoresSourceOnly` revised to drive the explicit notification path. M02-FIX-02 strengthened `stubbornLateCallbackAfterDisableIsRejected` so it additionally proves post-re-enable completion and the absence of a third request, instead of only the re-request.

Local M02-FIX-02 diagnostics (non-authoritative): 34 JVM tests green on JDK 17 (bridge 19, controller 10, provider 5 — the Robolectric class is deliberately skipped there); the strengthened test fails at `AiSubtitleCueBridgeTest:267` with the post-re-enable delivery line removed and passes again after restoring it; `SubtitleSettingsPresenter.java` byte-verified at 102 CRLF / 0 LF-only terminators with a 12/12 diff.

Red → green evidence from the original implementation (kept for the record): stub-bridge baseline `24 tests completed, 11 failed, 3 skipped` with all failures being assertion failures (`8× AssertionError`, `3× ComparisonFailure`); after implementation `BUILD SUCCESSFUL` with per-suite XML counts.

Tests cover: disabled pass-through (same list reference), null/empty/blank inputs, synchronous single-call dual-line output, deferred two-line output, source-only fallback on failure/exception, in-flight deduplication, stale callback rejection after video change / track change / seek (including a provider that ignores cancellation), immediate-disable cancellation and late-callback rejection, re-enable cleanliness and post-re-enable completion, seek keeping completed cache, pause admission control, release cleanup, and the full controller event mapping.

## GitHub Actions runs

**Run 1 — `34615801161` (failed, historical):** validated `3efb96374`. `Run common unit tests` passed; `Lint beta release` failed with the API-24 diagnostic quoted above; assembly was not attempted; validation-reports artifact uploaded. This run is the evidence base for the first correction.

**Run 2 — `34618112103` (green, first correction):**

- URL: https://github.com/CometDash77/smartube/actions/runs/34618112103
- Run ID: `34618112103`; validated commit: `69f644f4a` (final product SHA of that set)
- Primary job `Test, lint, and assemble` (JDK 17), 6m43s: `Run common unit tests` success; `Lint beta release` success; `Assemble beta release` success; validation-reports and beta-APK uploads success.
- Supplementary job `Preference tests (JDK 11)`, 3m30s: `Run AI subtitle preference tests` success (3/3 executed); report upload success.
- Artifacts: `ai-subtitle-validation-reports-2`, `ai-subtitle-beta-apks-2`, `ai-subtitle-preference-reports-2`.

**Run 3 — `34660051184` (green, second-pass replacement):**

- URL: https://github.com/CometDash77/smartube/actions/runs/34660051184
- Run ID: `34660051184`; validated commit: `854460bb4` (`head_sha` `854460bb42c437ea2ec5762b68ac6cc5ea467e97`, push event)
- Primary job `Test, lint, and assemble` (JDK 17), 5m29s: `Set up job`, `Checkout code and submodules`, `Set up JDK 17`, `Grant Gradle wrapper permission`, `Run common unit tests`, `Lint beta release`, `Assemble beta release`, `Upload validation reports`, `Upload beta APKs` — all success.
- Supplementary job `Preference tests (JDK 11)`, 2m10s: `Set up job`, `Checkout code and submodules`, `Set up JDK 11`, `Grant Gradle wrapper permission`, `Run AI subtitle preference tests`, `Upload preference test reports` — all success (3/3 methods executed).
- Artifacts: `ai-subtitle-validation-reports-4`, `ai-subtitle-beta-apks-4`, `ai-subtitle-preference-reports-4`.

**JDK responsibilities (ADR-010):** JDK 17 is authoritative for normal unit tests, lint, and beta assembly. JDK 11 exists solely to execute the Robolectric-backed preference suite that Robolectric 4.6.1 cannot run on JDK 17; it runs no builds, lint, releases, or other tests. The JDK 17 job deliberately reports that class as ignored; the JDK 11 job demonstrably executes and passes it.

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

Automated coverage exists for the lifecycle logic that the controller and bridge suites exercise — new video, subtitle-track change, seek (including stale-result rejection), pause/play, release/finish, and enable/disable with immediate cancellation. It does not cover every row of this matrix: background/foreground, PiP, and the real device rendering path (styling, embedded styles, ExoPlayer cue flow) have no automated equivalent, and the entire physical matrix remains `NOT RUN` because no Android device was available to the Worker.

## Deviations from the Task Package

1. **Unit-test task name.** `:common:testDebugUnitTest` does not exist for this module (three product flavors); the narrowest existing task, `:common:testStbetaDebugUnitTest`, was confirmed by `:common:tasks --all` output and is used in the workflow. Pre-authorized by the package; no Gradle change was made.
2. **Robolectric 4.6.1 cannot run on JDK 17** (`Unsupported class file major version 61` raised during its sandbox lifecycle). Handled per ADR-010: `AiSubtitleCueBridgeTest`/`AiSubtitleControllerTest` are Android-free JVM tests through the reduced-visibility enable-state seam; `AiSubtitleDataTest` keeps its Robolectric implementation, is reported ignored on JDK > 16, and executes fully in the JDK 11 job (3/3 passed). A later dependency/toolchain task should remove this exception once Robolectric is upgraded.
3. **Repository-local `core.whitespace=cr-at-eol`** set in `.git/config` (not repository content) so `git diff --check` gives accurate results for CRLF-stored upstream files.
4. **Local build environment** required ASCII-path mapping (`subst X: <workspace>`; the original path contains non-ASCII characters that break AGP and `protoc`) plus an explicit JDK 17 `JAVA_HOME`. Machine-local diagnostics only; CI needs neither.
5. **Repository-wide `core.autocrlf=true`.** `SubtitleSettingsPresenter.java` is stored with CRLF, so it must be staged with `git -c core.autocrlf=false add <file>`; the default cleaner would normalize the repaired file to LF and recreate whole-file churn. M02-FIX-02 used that explicit staging command and verified the 12/12 diff afterwards.

## Unexpected discoveries

1. `SubtitleManager.forceCenterAlignment()` deletes `\n` and repeated fragments for multi-line ASR text; the bridge is invoked strictly after it and never re-feeds decorated text, so the two-line output cannot be corrupted by it.
2. `SubtitleView.setCues()` contains a local modification that keeps only the last cue of a list; the feature deliberately decorates in place and never relies on multi-cue behavior.
3. `repo1.maven.org` direct access measured ~419 KB/s from this network versus ~9.5 MB/s through the Aliyun mirror; the 96 MB Robolectric `android-all-instrumented` jar was fetched via the mirror into the local Maven repository.
4. The three upstream files are stored as CRLF in this repository's Git object store while the machine's global Git configuration sets `core.autocrlf=true`; a full-file rewrite — or a plain `git add` of a repaired file — silently normalizes the whole file and creates churn. The first correction reduced that churn to a 6-line logical diff but left 12 LF-only terminators in the added method; M02-FIX-02 removed exactly those terminators and staged the file with autocrlf disabled.
5. A stale `GITHUB_TOKEN` environment variable can shadow the machine's valid stored Git credentials and break push authentication; excluding it restored the authenticated path without any persistent change.

## Remaining risks and concerns

1. **Device behavior unverified** — styling, PiP, and real ExoPlayer cue flow need the device matrix before final Commander PASS; automated coverage exists for the lifecycle logic only, and this limitation is honestly `NOT RUN`.
2. **Robolectric exception is temporary** — the JDK 11 job covers the preference suite until a planned dependency/toolchain upgrade (outside M02 scope) removes the need.
3. **M02 lookup-key limitation (by design)** — normalized source text is the in-memory key; documented in the bridge class and replaced by timeline/segment identity in M03 before real providers.
4. Local Gradle output is diagnostic only; none of its results are presented as acceptance evidence.

## Acceptance criteria disposition (M02)

| # | Criterion | Disposition |
|---|---|---|
| 1 | All four internal workstreams complete in one Worker execution | MET (commits 1–4; corrections in 7 and 9) |
| 2 | Only the three authorized existing SmartTube files modified | MET (patch budgets verified; the settings helper carries a 6-line logical change plus a 12-line terminator-only repair, net 0 lines) |
| 3 | Setting defaults off; disabled behavior upstream-equivalent | MET (disabled path returns the same list reference; bridge tests) |
| 4 | Enabled output shows source + `[ZH] source` through the existing SubtitleView, one Cue | MET (single-call and deferred two-line cue tests) |
| 5 | No network, Provider brand, prompt, segmentation, or full-track feature | MET (fake provider only; no permissions, endpoints, or keys) |
| 6 | Automated tests cover behavior and stale/cancellation invariants | MET (37 executing tests across two lanes; JDK 17 skips only the Robolectric class covered by the JDK 11 job) |
| 7 | GitHub Actions green for the exact final SHA | MET (run `34660051184` green for `854460bb4`; both jobs; three artifacts) |
| 8 | Unavailable device checks explicitly NOT RUN | MET (full matrix recorded NOT RUN) |
| 9 | Worker Plan predates implementation; Worker Report truthful | MET (M02 plan in commit 1; both correction plans predate their code; report records all evidence, and the second pass corrected two inaccurate statements rather than leaving them) |
| 10 | Worker stops after M02; M03 not started | MET |

## Acceptance criteria disposition (M02-FIX-01)

| # | Criterion | Disposition |
|---|---|---|
| 1 | All four Commander findings corrected and covered by tests | MET (findings table above; 3 new tests) |
| 2 | Exact lint failure documented; no M02-origin lint error remains | MET (diagnostic quoted; local 0 errors; CI lint green in runs 2 and 3) |
| 3 | No API newer than 17 referenced without an existing compatible mechanism | MET (EnableState seam; explicit hashing/equality) |
| 4 | One-call production-Fake test proves exact dual-line output | MET |
| 5 | No-next-cue test proves immediate disable cancellation and stale rejection | MET |
| 6 | All three `AiSubtitleDataTest` methods execute and pass in the supplementary job | MET (JDK 11 job: 3 passed, 0 skipped, method names listed above) |
| 7 | Primary JDK 17 job passes unit tests, lint, and beta assembly | MET (runs 2 and 3) |
| 8 | Validation reports and beta APK artifacts present for the replacement run | MET (three artifacts listed for run 3) |
| 9 | Only authorized files changed; M03 untouched | MET |
| 10 | Amended report states final SHA, run URL/ID, step outcomes, test counts/skips, artifacts, deviations, risks truthfully | MET (this revision) |

## Acceptance criteria disposition (M02-FIX-02)

| # | Criterion | Disposition |
|---|---|---|
| 1 | Settings helper contains 0 LF-only lines and its diff is limited to the 12 corrected terminators | MET (byte-verified 102 CRLF / 0 LF-only; 12/12 diff; no semantic change) |
| 2 | Strengthened re-enable test proves completion and provably fails without the post-re-enable delivery | MET (dual-line output and request-count assertions; mutation check red at `AiSubtitleCueBridgeTest:267`, green after restore) |
| 3 | Report line-ending and automation-coverage statements accurate; matrix stays `NOT RUN` | MET (both statements corrected in this revision; full physical matrix `NOT RUN`) |
| 4 | 34 JVM tests pass on JDK 17 with the preference suite still executing on JDK 11 | MET (run `34660051184` artifact XML: 34 passed, 3 deliberately skipped on JDK 17; 3/3 on JDK 11) |
| 5 | Replacement CI run green for the final product SHA with steps and artifacts recorded | MET (run `34660051184`; both jobs; every step listed above) |
| 6 | Only authorized files changed; M03 untouched | MET (commit `854460bb4` touches the settings helper, the bridge test, and four docs; the two deferred refactors stay untouched) |

## Confirmation

M03 was not started. The Worker stopped after M02, completed the second correction package (`M02-FIX-02`), and returns this amended report and the commit range `0b16df3d5..854460bb4` (plus this report-only finalization commit) once. The product-validation evidence for this revision is run `34660051184` for `854460bb4`; the report-only finalization push may trigger a docs-only workflow run.
