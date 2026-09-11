# M02-FIX-01 Worker Execution Plan

Status: created before any correction code

Package: `docs/ai-subtitle/tasks/M02-FIX-01.md` — correct M02 first-cue rendering, API 17 compatibility, immediate disable, and CI coverage

## Starting state

- Branch: `feature/ai-bilingual-subtitles`; HEAD `6e316173f` (`docs(ai-subtitle): require M02 corrections`).
- Working tree: clean; local is ahead of `origin` by 2 commits (`26693c340`, `6e316173f`, both unpushed by design).
- Authoritative evidence in hand: failed run `34615801161` (4m50s) for `3efb96374`; `Run common unit tests` passed; `Lint beta release` failed; assembly not attempted; validation-reports artifact uploaded.

## Exact failure diagnostics (run 34615801161)

Lint step terminal output:

```
Lint found 1 errors, 385 warnings. First failure:
/home/runner/work/smarttube/smarttube/common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/integration/AiSubtitleCueBridge.java:93:
Error: Call requires API level 24 (current min is 17): java.util.function.BooleanSupplier#getAsBoolean [NewApi]
> Lint found errors in the project; aborting build.
```

Only one lint error exists, and it is M02-origin. Commander additionally identified `java.util.Objects` (API 19) in `TranslationRequest`/`TranslationResult` as an API 17 violation that lint does not flag as an error but that can fail at runtime on API 17–18. Both are corrected; nothing is suppressed.

## Correction design

1. **Synchronous Fake first-cue rendering.** `AiSubtitleCueBridge.findOrRequest()` currently returns `null` unconditionally after starting the request. Correction: after `translate()` returns, re-read `mCompleted` for the source and return it when the provider completed synchronously (the callback has already passed every identity guard and populated the cache). Deferred providers are unaffected: the same call still returns source-only until a later rendering opportunity. No callback bypasses generation/request/epoch checks.

2. **API 17 compatibility.**
   - Replace `java.util.function.BooleanSupplier` with a feature-owned package-private nested interface `AiSubtitleCueBridge.EnableState { boolean isEnabled(); }`. Constructor and field keep `m`-prefixed naming; the production constructor continues to bind `AiSubtitleData`.
   - Remove `java.util.Objects` from `TranslationRequest` and `TranslationResult`; implement explicit null-safe `equals`/`hashCode` (Java 6-compatible) to preserve value-object semantics used by the M02 contract.

3. **Immediate disable.** Add one explicit bridge entry point `public void onEnabledChanged(boolean enabled)`; disabling behaves as an invalidating event (cancel in-flight, increment generation, clear completed results) synchronously inside the settings callback. `SubtitleSettingsPresenter`'s M02 switch helper gains one narrow notification line plus the bridge import — no new upstream hook, no exposure of SubtitleView/UI/provider internals. The existing `process()`-side detection remains as an idempotent safety net.

4. **Robolectric execution (ADR-010).** Keep `JdkAwareRobolectricRunner` (JDK 17 stays green with the class reported ignored) and add a supplementary **JDK 11 job** to the feature workflow that runs only `AiSubtitleDataTest` via a Gradle `--tests` filter and uploads its reports. JDK 17 remains the build/lint/normal-test authority; JDK 11 runs nothing else.

5. **Standards.** Rename `PendingRequest` members to `mRequestId`/`mGeneration`/`mEpoch`/`mCall` (and their uses) and `RecordingBridge.mCalls` in `AiSubtitleControllerTest`.

## Exact files and tests

Modify:

- `common/src/main/java/.../ai/subtitle/integration/AiSubtitleCueBridge.java` — EnableState seam, immediate-consumption fix, `onEnabledChanged`, `m` prefixes.
- `common/src/main/java/.../ai/subtitle/translation/TranslationRequest.java` — remove `Objects`.
- `common/src/main/java/.../ai/subtitle/translation/TranslationResult.java` — remove `Objects`.
- `common/src/main/java/.../app/presenters/settings/SubtitleSettingsPresenter.java` — one notification line + import inside the existing M02 helper.
- `.github/workflows/ai-subtitle-validation.yml` — add the JDK 11 `preference-tests` job; primary job unchanged.
- `common/src/test/java/.../ai/subtitle/integration/AiSubtitleCueBridgeTest.java` — new/revised tests (below).
- `common/src/test/java/.../ai/subtitle/integration/AiSubtitleControllerTest.java` — `mCalls` rename.
- `docs/ai-subtitle/worker-reports/M02-report.md` — amended report (final docs commit).

New tests:

1. `immediateFakeDecoratesOnTheFirstAndOnlyProcessCall` — production-identical `new FakeTranslationProvider()`: one `process(cues("Hello"))` returns `Hello\n[ZH] Hello`; a second call is a cache hit (still one translate call).
2. `disablingImmediatelyCancelsInFlightWork` — cancel-tracking provider: `onEnabledChanged(false)` flips the captured call's cancelled flag before any further cue is processed.
3. `stubbornLateCallbackAfterDisableIsRejected` — provider that ignores cancellation: late result after disable cannot populate cache or affect rendering; re-enable starts clean and re-requests.
4. Revised `disablingTheSettingClearsStateAndRestoresSourceOnly` to drive the explicit notification path.

Existing behavior/stale/release/seek tests remain unchanged and green.

## Commit / push / CI sequence

1. Commit A: `fix(ai-subtitle): satisfy M02 review` — all code, tests, workflow.
2. Local diagnostics (non-authoritative): `:common:testStbetaDebugUnitTest`, `lintStbetaRelease` from the ASCII path mapping.
3. Push once → replacement run for the fix SHA.
4. After the run completes (both jobs), commit B: `docs(ai-subtitle): finalize M02 validation` amending the report with real results; push (docs-only run follows; explained in the report).

## Risks and stop conditions

- JDK 11 job may hit an unrelated toolchain incompatibility; if so, capture the exact failure and return it rather than widening scope (no Gradle changes are authorized).
- If the replacement run fails on anything M02-origin, fix within this package's file set; if the failure is outside the authorized file set, stop and report.
- Do not rebase/amend/force-push; do not touch CI.yml, Gradle, or any file outside the authorized list.
- Local results remain diagnostic; acceptance comes from the replacement run only.
