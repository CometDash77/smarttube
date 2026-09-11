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
