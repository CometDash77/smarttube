# M07 Worker Report

Task ID: `M07`

Milestone: M07 — Real-time translation scheduler

Status: **IMPLEMENTATION AND AUTOMATIC VERIFICATION COMPLETE (local) — M07 A–E closed with review fixes R1–R4; R5 records one evidenced scope revision (partial-batch repair not implemented). Exact-SHA CI is `PENDING PUSH AUTHORIZATION`; unified device acceptance (Task F) is `PENDING DEVICE`.**

## Scope

Finish the interrupted M07 Task E, then work the review items R1–R5 from
`worker-plans/M07-M09-continuation-plan.md`: restore a compiling baseline (R0),
make the persisted settings actually drive behaviour (R1), complete the settings
paths and bilingual order (R2), resolve the subtitle track the user selected
(R3), route the selected Prompt into the real request (R4), and re-verify the
original Task D coverage claims (R5).

Not in scope: bounded context, SSE/streaming (M08); cache bounds, hardening and
release acceptance (M09); any change to `SharedModules`, `MediaServiceCore`,
ExoPlayer sources, Gradle dependency versions, or `KissTranslator`.

## Pinned SHAs

| Item | SHA |
|---|---|
| Branch | `feature/ai-bilingual-subtitles` |
| Resume point (start of this session) | `38604559d6f343ca09913e4767dff2131b8267e5` |
| R3 — subtitle track resolution | `a73ce61cb` |
| R0/R1/R2/R4 — Task E completion, settings, prompt wiring | `468d77839` |
| R5 documentation (this report, plan, progress) | recorded in the follow-up docs commit |

Submodule pointers at execution: `MediaServiceCore 82e9ccde`, `SharedModules 86f0327` (unchanged).

## A. Task A — phone remote Profile editing and test feedback

Committed before this session as `717618f86`. Its evidence stands: focused
`settings.*` run recorded 74 tests at the time. Re-verified in this session as
part of the JDK 11 lane below (settings suites are now 78 tests including the
four added here).

## B. Task B — full subtitle source and M06 wiring

Committed as `738325822`. This session corrected the track-selection defect it
left open (see R3 below) and added the missing fixtures. The pipeline itself
(`VttParser` → `SubtitleNormalizer` → `RuleSentenceBreaker` → `TranslationChunker`
→ `SourceTimeline`) was not rewritten.

## C. Task C — window, dedup, pause, seek

Committed as `1ce3d8b2e`. Re-verified in this session; the window tests were
strengthened with the specified 10/25/40/70/100/101-second fixture and with a
window-shrink case (see R2 below).

## D. Task D — bounded retry, partial results, source fallback

Committed as `38604559d`. Bounded retry, terminal category handling, timeout
budgets and source fallback were re-checked and are backed by real assertions
(`timeoutUsesThreeNetworkAttemptsWithBackoff`,
`rateLimitAndServerUseTheSameRetryBudget`, `terminalCategoriesDoNotRetry`,
`partialDraftsAndWrongCoverageAreTerminalWithoutCache`,
`retryOnlyTheFailedUnitAfterNeighborsSucceed`).

The D3 claim "manual retry has a clear entry point" was **not true at the time**:
see R5-1.

## E. Task E — settings that actually take effect

Completed in `468d77839`. Delivered: persisted lookahead (0/30/60/90/120 s,
default 90), background schedule interval (5/15/30 s, default 30), segmentation
limits (target/max/long-sentence), and bilingual order — all readable and
writable from TV settings and the phone page, all shared through the same store.

## R1–R4 review fixes

| Item | Defect confirmed at `38604559d` | Fix and evidence |
|---|---|---|
| R0 | The tree did not compile: an unclosed string literal in `AiSubtitleCueBridge`, and `RuleSentenceBreaker` used a new instance field from two remaining static methods | Both restored; `:common:compileStbetaDebugJavaWithJavac` green, then both test lanes green |
| R1 | `applySchedulingToBridge` also applied segmentation, and `onSegmentationChanged` unconditionally dropped the session; the bridge ignored persisted segmentation limits when a new adapter was created; a segmentation change could start work while paused; a superseded source load could overwrite a newer timeline; an unresolvable provider threw from the scheduler constructor | `onSchedulingChanged` short-circuits on identical values and updates only the window; `onSegmentationChanged` short-circuits, restores the paused state, reloads only with a valid provider and an enabled feature; `setSourceAdapter` configures the stored limits first; source loads carry a monotonic sequence that `dropSession`/identity changes invalidate; `recreateScheduler` tolerates a missing provider. Tests: `aNewSourceAdapterReceivesTheStoredSegmentationLimits`, `aStaleTimelineLoadCannotOverwriteANewerOne`, `schedulingChangeWhilePausedStartsNoWork`, `segmentationChangeStartsANewGenerationAndKeepsThePausedState`, `anUnconfiguredProviderToleratesLifecycleAndSettingsChanges` |
| R2 | Phone saves were skipped when the target language was empty; out-of-range integers truncated into valid-looking presets; invalid input was reported as a page-version conflict; persistence had no batch write; bilingual order did not exist | All five fields are parsed and validated before any write; values are range-checked as long before narrowing; invalid input returns its own state; the five values are written through one `SharedPreferences.Editor` batch; bilingual order is a real setting. Tests: `outOfRangeSchedulingValuesAreRejectedWithoutChangingTheDraft`, `invalidSegmentationIsRejectedWithoutChangingTheDraft`, `anEmptyTargetLanguageStillSavesTheOtherSettings`, `savedSchedulingValuesAreSharedWithTheTvSide`, `bilingualOrderChangesPresentationWithoutNewRequests` |
| R3 | Same-language manual/auto-generated tracks were matched by the first entry after a lossy normalization | See `a73ce61cb`: exact display name → exact language code → lossy name, with ambiguity resolving to source-only. Tests: `matchSubtitleSelectsTheSelectedSameLanguageTrackRegardlessOfListOrder`, `matchSubtitleFallsBackToSourceOnlyWhenOnlyTheLanguageCodeIsKnown`, `matchSubtitleUsesCodeWhenItIdentifiesExactlyOneTrack`, `irregularLanguageNamesStillResolveTheSelectedTrack`, `toVttUrlAddsQueryWhenTheTimedTextUrlHasNone`, `toVttUrlReplacesANonVttFormatWithoutTouchingSignedParameters`, `toVttUrlKeepsFragmentsAfterTheQuery` |
| R4 | Both adapters hard-coded the system instruction; the resolved Prompt Profile never reached the request | See `468d77839`: the Prompt is frozen into `TranslationRequest.renderedPrompt` at submit time and used as the system message by both protocols. Tests: `renderedPromptBecomesTheSystemMessageAndSourceTextStaysTheUserMessage`, `differentPromptsProduceDifferentRequestBodies`, `renderedPromptIsJsonEscaped`, `renderedPromptBecomesTheTopLevelSystemField`, `anUnrenderablePromptIsTerminalWithoutTouchingTheNetwork`, `aPromptThatReferencesTheEmptyContextStillRenders` |

## R5 — review of the original Task D coverage claims

### R5-1 Manual retry entry: was missing, now implemented

At `38604559d` the scheduler had no production retry entry. `hasFailed`/`getFailureMessage`
had no production caller, and `requestCurrentUnitLocked` returns immediately for
any state other than `PENDING`, so a terminal failure was permanent for the
lifetime of the session even after the network recovered.

Implemented: `TranslationScheduler.retryFailed(positionMs, nowMs)` requeues only
terminal failures whose unit starts inside the current window and gives them a
fresh attempt budget; ordinary ticks never reset it. `AiSubtitleCueBridge.retryFailed()`
forwards it, and the player's AI subtitle settings dialog gained a "Retry failed
translations" entry. Assertions: `manualRetryRequeuesTerminalFailuresWithAFreshBudget`,
`manualRetryLeavesUnitsOutsideTheWindowFailed`.

### R5-2 Partial-batch repair: no executable production mode — scope revision

The plan assumed the D4–D6 prefix/tail recovery had a production path to reuse.
It does not. Evidence, all local:

1. `BoundaryProtocolParser`, `BoundaryValidator` and `DeterministicSegmentationFallback`
   have exactly one other main-source reference each: `AiSegmentationCoordinator`.
   `AiSegmentationCoordinator`, `StatisticalSentenceBreaker`, `SegmentationMetrics`
   and `AsrTimingEstimator` have **no** production caller; only
   `segmentation/BoundaryProtocolTest.java` references them.
2. `BoundaryProtocol.encodeItem` has **no caller anywhere**, tests included.
3. The production transport is all-or-nothing: both adapters read one free-text
   translation for the whole unit, and the scheduler dispatches one
   `TranslationUnit` at a time, so there is no "batch partially succeeded"
   response shape to repair.
4. The built-in indexed Prompt asks for "the translation with its unit index",
   not for the `v2|start-end|text` wire format, so selecting it cannot produce a
   response that `BoundaryProtocolParser` accepts.
5. The index base is unspecified across the boundary: the request sends the
   unit's first **absolute** segment index as `unit_index`, while
   `BoundaryValidator` validates **positional** indices and requires the first
   item to start at `requestedStart`.
6. The bridge and cache expose exactly one text per unit
   (`AiSubtitleCueBridge.findOrRequest` → `getCachedTranslation(unit)`), so a
   tail sub-unit's translation has no assembly path to the screen.

Building the repair path now would create machinery for a caller that cannot
exist, which this plan and the Ponytail constraint both forbid. Recorded instead:

- M07 exit condition 7 is **partially** met. Source-Only Fallback is fully
  implemented and asserted. Partial-batch repair is **not implemented** and its
  plan checkboxes stay unchecked.
- Restart conditions: (a) a Prompt whose contracted output is the boundary wire
  format, (b) one agreed index base between request and validator, (c) a decided
  presentation path for a tail sub-unit (either a `TranslationResult` coverage
  range or a per-segment assembly step in the bridge).
- `AiSegmentationCoordinator`'s source-text fallback cannot be mistaken for a
  successful translation because nothing calls it.

### R5-3 Known limitation: the built-in indexed Prompt is selectable but unconsumed

`prompt/BuiltInSubtitlePrompts.INDEXED_ID` is required by the roadmap (M05
workstream 6) and is therefore kept. Because nothing consumes its structured
output shape, selecting it renders the model's index-prefixed reply verbatim.
This is recorded as a production risk, not fixed here: changing its content
changes its identity (`promptVersion`/`contentHash`) and belongs with the same
decision as R5-2. Device step: if a user sees index markers in the rendered
subtitle, the selected Prompt is the indexed one.

### R5-4 Known limitation: residual track ambiguity

When the track identity carries only a language code and two same-language
tracks share that code, translation stays source-only rather than guessing. The
selected track's Exo `formatId` cannot break the tie — `ExoFormatItem`
documents it as non-constant for subtitles. Device step: if a video keeps
showing the original subtitles with AI enabled, record the track identity and
the caption list (display name plus language code for each track) as evidence
for whether a further host field is genuinely required.

## Verification

Local, from a fresh ASCII copy of the current working tree at
`C:\tmp\smartube-m07-m09` (no junction, no `overridePathCheck`, no stale build
output reused).

| Lane | Command | Result |
|---|---|---|
| JDK 17, full `common` | `gradlew.bat :common:testStbetaDebugUnitTest` | `BUILD SUCCESSFUL` — 45 suites, tests=388, failures=0, errors=0, skipped=20 |
| JDK 11, settings/secret (ADR-010 lane) | `gradlew.bat :common:testStbetaDebugUnitTest --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.*'` | `BUILD SUCCESSFUL` — 11 suites, tests=78, failures=0, errors=0, skipped=0 |

The 20 JDK 17 skips are the inherited Robolectric preference/secret suites that
ADR-010 runs in the JDK 11 lane; they are 0-skipped there. `git diff --check` is
clean. Two failures were found and fixed by running rather than by inspection:

- `RuleSentenceBreaker` static/instance mix-up (compile blocker).
- `AiSubtitlePhoneInputServerTest.explicitSaveCarriesVersionAndConflictKeepsDraft`
  and `failedProviderSaveRollsBackPrompt` failed on JDK 11 because their forms
  predated the new required settings fields; both were updated to submit the
  complete form.

Nothing in this report is a device result. No APK was built, installed or
measured, and no exact-SHA CI run exists yet.

## Not done / pending

| Item | State | Trigger |
|---|---|---|
| Exact-SHA GitHub Actions run for `a73ce61cb` + `468d77839` | `PENDING PUSH AUTHORIZATION` | An authorized push to `feature/ai-bilingual-subtitles` runs `ai-subtitle-validation.yml` (full `common`, lint, assemble, JDK 11 lane) |
| Unified device acceptance (M07 Task F, all seven bullets) | `PENDING DEVICE` | A TV or Android device, plus an APK built from the exact candidate SHA |
| Partial-batch repair (M07 exit condition 7, second half) | `NOT IMPLEMENTED` | The three restart conditions in R5-2 |
| `lintStbetaRelease` / `assembleStbetaRelease` locally | `NOT RUN` | Belongs to the candidate-SHA CI run; not repeated locally to avoid re-verifying inside M07 |
| M06 and earlier device checks | `STILL PENDING` | Unchanged from the previous ledger |
