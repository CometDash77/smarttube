# M02-FIX-02 Worker Execution Plan

Status: created before the correction commit

Package: `docs/ai-subtitle/tasks/M02-FIX-02.md` — correct settings-helper line endings, prove re-enable completion, correct the report's coverage claim

## Starting state

- Branch: `feature/ai-bilingual-subtitles`; HEAD `be02bc2b3` (`docs(ai-subtitle): finalize M02 validation`).
- Working tree: clean; local tip equals `origin/feature/ai-bilingual-subtitles`.
- Authoritative evidence in hand: green run `34618112103` for `69f644f4a` (primary JDK 17 job plus supplementary JDK 11 preference job) and run `34618912621`.
- Commander second pass: `CHANGES REQUIRED` with three blocking items and two explicitly deferred judgement items.

## Exact byte-level evidence for the mixed line endings

`SubtitleSettingsPresenter.java` is stored with CRLF in the Git object store. Measured with a byte-wise split on `\n`:

```text
rev 0b16df3d5 (pre-M02 baseline): CRLF 87, LF-only 0
rev 8e9c030fe (M02 integration):  CRLF 89, LF-only 7
rev be02bc2b3 (reviewed tip):     CRLF 90, LF-only 12
worktree (== be02bc2b3):          CRLF 90, LF-only 12   (LF-only rows: 70-81)
```

The 12 LF rows are exactly the M02 method `appendAiSubtitleSwitch` and the blank line that follows it (rows 70–81, two of which are blank). `grep -c` misreports this file, so the check is byte-wise (`l.endswith(b"\r")`), never `grep`.

## Correction design

1. **Line endings.** Rewrite only rows 70–81 of `SubtitleSettingsPresenter.java` so every row in the file terminates with CRLF. No Java token, whitespace inside a line, comment, or ordering changes. Post-condition: 102 CRLF rows, 0 LF-only rows, and `git diff --numstat` limited to 12 insertions / 12 deletions for that file.
2. **Re-enable completion test.** Extend `stubbornLateCallbackAfterDisableIsRejected`: after the post-re-enable `process()` call that issues the second request, deliver the queued callback (`provider.deliverAll()`), then call `process(cues("Hello"))` again and assert the exact dual-line output `Hello\n[ZH] Hello` plus a request count of 2. Rationale: with the existing test alone, a bridge that rejected every post-re-enable callback would still pass; the new assertions make that failure mode observable. Verified by mutation: removing the `deliverAll()` line must turn the test red.
3. **Report accuracy.** Replace the claim that all lifecycle transitions are covered by automation with an explicit list of what the automated suites do cover (controller event mapping: new video, subtitle-track change, seek, pause/play, release/finish; bridge: disable/re-enable, immediate-Fake single-call rendering, deferred completion, dedup, stale rejection, cancellation, provider failure/exception). Background/foreground, PiP, styling, and the real ExoPlayer cue flow stay `NOT RUN`. Also correct the earlier statement that the settings file had been fully restored to its original style, so it matches the byte-level verification actually performed.
4. **Deferred items stay deferred.** No refactor of the duplicated `equals`/`hashCode` helpers and no restructuring of the `(requestId, generation, epoch)` cluster; the review explicitly asks not to widen the M02 patch.

## Exact files and tests

Modify:

- `common/src/main/java/.../app/presenters/settings/SubtitleSettingsPresenter.java` — line terminators of rows 70–81 only.
- `common/src/test/java/.../ai/subtitle/integration/AiSubtitleCueBridgeTest.java` — post-re-enable completion assertions in `stubbornLateCallbackAfterDisableIsRejected`.
- `docs/ai-subtitle/worker-reports/M02-report.md` — corrected statements plus the second-pass disposition and final CI evidence (final docs commit).

Commands:

```text
git diff --numstat -- common/src/main/java/.../SubtitleSettingsPresenter.java
python byte-wise line-ending check (CRLF count = 102, LF-only count = 0)
JAVA_HOME=<JDK 17> ./gradlew :common:testStbetaDebugUnitTest --tests "com.liskovsoft.smartyoutubetv2.common.ai.subtitle.*"
mutation check: remove provider.deliverAll() -> expect red; restore -> expect green
```

Local runs are diagnostic. Acceptance evidence comes from GitHub Actions only.

## Commit / push / CI sequence

1. Commit A: `fix(ai-subtitle): address M02 second-pass review` — line-ending repair, strengthened test, review record, this package, this plan.
2. Local diagnostics from the ASCII path mapping with JDK 17 (Gradle 7.5 rejects the machine's default JDK 21).
3. Push once → replacement run for the fix SHA; both jobs must pass (JDK 17 primary: unit tests, lint, assembly; JDK 11 supplementary: preference suite).
4. After the run completes, Commit B: `docs(ai-subtitle): finalize M02 second-pass validation` — the amended report with real CI evidence; push (docs-only run may follow; explained in the report).

## Risks and stop conditions

- The line-ending repair is the highest-risk edit in this package: a whole-file rewrite would create hundreds of churn lines on an upstream file. Verify byte-wise before committing, and abort rather than commit any diff that is not limited to rows 70–81.
- The mutation check must be performed by removing the delivery line only temporarily; if the file cannot be restored exactly, stop and report instead of committing.
- If the replacement run fails on anything M02-origin, correct it inside this package's file set; if the failure is outside the authorized set, stop and report.
- Do not rebase, amend, or force-push; do not touch `CI.yml`, Gradle files, or any file outside the authorized list.
- Local results remain diagnostic; acceptance comes from the replacement run only.
