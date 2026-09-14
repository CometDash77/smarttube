# AI Subtitle Progress Ledger

Last updated: 2026-09-14

Authoritative branch: `feature/ai-bilingual-subtitles`

Personal project remote: `origin` → `https://github.com/CometDash77/smartube.git` (GitHub reported public visibility during Phase 0)

Official source: `upstream` → `https://github.com/yuliskov/SmartTube.git`

## M07–M09 correction run — execution contract (2026-09-14)

Recorded before execution starts so a fresh session can resume from this file alone.

- Governing plan: `D:\obsidian\工程\VIBECODING项目\smartube\review-plans\M07-M09-code-correction-plan.md`, SHA-256 `a13a5687ce01ffe7b06504376e13a097e6b6e5776926fa02dd6813684fb130a7`, 54 840 bytes. The plan is deliberately kept outside this clone and is **not under version control** (the outer Obsidian repository tracks a single file). This path and hash are its only durable record. If the file and the hash disagree, the hash wins and the run stops until the user re-points it.
- Base for this run: `a22e525df`. The plan is anchored at `10d6a18cf`; the only commit since is `a22e525df`, a one-line in-place edit to `SseEventReader.java:30` (BOM written as an escape) that changes no line count, so the plan's line anchors still hold. Method names remain the locator of record per plan §1.
- Execution mode: **serial, no implementer subagents** — plan §1 `不派生代理` governs implementation.
- Review mode: subagents are dispatched at the five phase gates and once at the end, on the **Standards and Spec axes only** — no test or build execution, no device. A review verdict is not a test result, and the AGENTS.md evidence rules still bind every "passes" claim.
- Phase boundaries are the plan's five commit units: tasks 1–2, task 3, tasks 4–5, task 6, tasks 7–8. Each phase: implement → capture evidence → dispatch the review subagent → append here and to `worker-reports/M07-M09-correction-phase<N>-report.md` → commit code and ledger together.
- Final review report: `reviews/M07-M09-correction-review.md`, carrying both axes plus the Minor-finding roll-up and its triage.
- The local SDD ledger `.superpowers/sdd/` is ignored by its own `.gitignore` (`*`) and is therefore **not durable**. Conclusions must be mirrored into this file; never treat that directory as the record.
- Unchanged prohibitions (AGENTS.md and plan §1): no push, no tag, no release, no artifact upload, no paid API calls; no changes to KissTranslator, SharedModules, MediaServiceCore, ExoPlayer, or Gradle dependency versions; no edits to upstream host files this round. Device acceptance stays with the user, so those items stay unchecked and marked `PENDING DEVICE`.
- Workspace trap: the outer Obsidian `.claude/worktrees/*` checkouts are not to be restored or used; git operations belong to this clone only.

## M07–M09 correction run — phase status (2026-09-14)

| Phase | Plan tasks | Commit | State |
|---|---|---|---|
| 1 | 1–2 | `fccf8d9ca` + review fixes | Implemented, both lanes green, Standards/Spec review gates **complete** and every finding fixed or recorded with its reason |
| 2 | 3 | `fbeda06e8` | Implemented, both lanes green, Standards/Spec review gates **complete** and every finding fixed or recorded with its reason |
| 3 | 4–5 | `fbeda06e8` + tasks 4–5 | Implemented, both lanes green, Standards/Spec review gates **complete** and every finding fixed or recorded with its reason |

| 4 | 6 | `551b10a4c` + task 6 | Implemented, both lanes green, Standards/Spec review gates **complete** and every finding fixed or recorded with its reason |
| 5 | 7–8 | `0fef49ade` + tasks 7–8 | Implemented, both lanes green, common lint run, Standards/Spec review gates **complete** and every finding fixed or recorded with its reason |

- Correction-run commits so far: `28d8311e0` (execution contract), `fccf8d9ca` (phase 1: tasks 1–2), `96a437c59` (phase status + continuation state), `321fd90b7` (phase 1 review fixes), `fbeda06e8` (phase 2: task 3), `551b10a4c` (phase 3: tasks 4–5), `0fef49ade` (phase 4: task 6).

### Completion note (M07–M09 correction run, 2026-09-14)

**Repair commits.** `fccf8d9ca` (tasks 1–2), `fbeda06e8` (task 3), `551b10a4c` (tasks 4–5), `0fef49ade` (task 6), and the phase 5 commit for tasks 7–8. Branch `feature/ai-bilingual-subtitles`; upstream base `6e2e00bb8c`. Not pushed, no tag, no release.

**Changed files.** Thirteen production files modified and one added, all under
`common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/`:
`integration/AiSubtitleController.java`, `integration/AiSubtitleCueBridge.java`,
`integration/AiSubtitleRuntime.java`, `provider/AnthropicMessagesAdapter.java`,
`provider/OpenAiChatCompletionsAdapter.java`, `provider/http/HttpRequestExecutor.java`,
`provider/http/OkHttpRequestExecutor.java`, `scheduler/TranslationScheduler.java`,
`settings/AiSubtitleData.java`, `settings/remote/AiSubtitlePhoneInputServer.java`,
`source/SmartTubeSubtitleSourceAdapter.java`, `source/SourceTimeline.java`,
`prompt/BuiltInSubtitlePrompts.java`, and the new `segmentation/SegmentationLimits.java`. The
cache was not modified. Seventeen test classes and the ledger, phase reports and review roll-up
under `docs/ai-subtitle/` changed with them.

**Lanes** (fresh ASCII copy `C:\tmp\smartube-code-fix`, XML parsed, never the build banner):

| Lane | suites | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|---:|
| JDK 17 full `:common:testStbetaDebugUnitTest` | 51 | 524 | 0 | 0 | 21 |
| JDK 11 `--tests '….ai.subtitle.settings.*'` | 11 | 79 | 0 | 0 | 0 |

The 21 JDK 17 skips are exactly `AiSubtitleDataTest` (6), `AndroidSecretStoreRobolectricTest` (4) and `AiSubtitlePhoneInputServerTest` (11), all of which run 0-skipped in the JDK 11 lane. `:common:lintStbetaRelease` succeeded with 388 findings, all warnings, none in `ai/subtitle`. Baseline for comparison at `10d6a18cf`: 460/20 and 78/0. These two lanes measure two different things and are not additive.

**Closed defects.** C1, C2, C3, C4 (phase 1); C8, C9, C11 (phase 2); C5, C6, C7 (phase 3); C10 (phase 4); and the two M09 automatic PARTIALs — the explicit 50-drag case and the repeated-terminal-callback assertion.

**Not verified, and not claimed.** Exact-SHA CI has not run (`PENDING PUSH AUTHORIZATION`). The whole device matrix — including the 2h device run, performance and memory sampling, QR pairing, real-provider combinations and backup/export behaviour — is `PENDING DEVICE` and is the user's to run. No APK was built, and no performance, latency or memory figure is claimed anywhere. Three automatic items stay open and are recorded in the phase reports: the phase 1 phone test could not observe the live bridge's request over real HTTP in the Robolectric lane; the attempt budget is not absolutely unbreakable across a seek because the plan's own out-of-window prune drops spent records; and per-word timing is not implemented. Partial-batch repair remains `NOT IMPLEMENTED` with its restart conditions. `DeterministicSegmentationFallback` has no test reference at all.

**Nothing in this note is a release statement.** The code package is reproducible locally from the commits above; whether it ships is a separate, explicitly authorized decision.
- Phase 1 closes C1, C2, C3 and C4. The counterexample-to-test mapping, the red-run evidence and the full tables are in `worker-reports/M07-M09-correction-phase1-report.md`.
- Phase 1 review gates ran on `fccf8d9ca` as two subagents on the Standards and Spec axes, neither running tests. Fixed: the `rebindTranslationIdentity` javadoc claimed the bounded context flows through it (it does not); the zero-caller `applySchedulingToBridge` deleted; the segmentation-limits rule collapsed into `segmentation/SegmentationLimits.isValid(long, long, long)`; the duplicated identity guard extracted; the phone test renamed to what it actually asserts; the C3 row in the phase report corrected (the old phone path called two entries, so the provider and scheduling did apply — the order, context and streaming switches did not). Fixed from the Spec axis: `canStartWork()` gained the plan's "selected track" precondition, implemented with a new `mTrackStateKnown` flag so "subtitles off" is distinguishable from "no track event yet"; `recreateScheduler()` is now gated by it while the session is always created. Recorded rather than changed: the apply order (segmentation first, with the reason in its javadoc) and the count-only assertions in the "no work" matrix tests.
- Phase 1 evidence (fresh ASCII copy `C:\tmp\smartube-code-fix`; raw XML kept at `C:\tmp\ev-jdk17` and `C:\tmp\ev-jdk11`):
  - JDK 17 full `:common:testStbetaDebugUnitTest` → 51 suites, **tests=475, failures=0, errors=0, skipped=21**.
  - JDK 11 `:common:testStbetaDebugUnitTest --tests '….ai.subtitle.settings.*'` → 11 suites, **tests=79, failures=0, errors=0, skipped=0**.
  - The skip count rose 20 → 21 for one reason only: the new phone test joins the ten `AiSubtitlePhoneInputServerTest` methods that `JdkAwareRobolectricRunner` skips above JDK 16, and it runs for real in the JDK 11 lane (0 skipped there).
- Open PARTIAL from phase 1: the plan's task 2 asks the JDK 11 phone test to observe the live bridge's request over real HTTP. That observation could not be reproduced in the Robolectric lane; the evidence gathered, what it does and does not prove, and the next step to try are recorded in `worker-plans/M07-M09-correction-continuation.md` §4. The delivered test asserts the wiring up to that point and the phase report states the gap rather than papering over it. C3's bridge-level behaviour is covered by the integration suite, which does observe the requests.
- Phase 2 (plan task 3) closes C8, C9 and C11 in `scheduler/TranslationScheduler.java`; the cache is unchanged because the defect was on the scheduler side of the miss. Detail, red-run evidence and tables: `worker-reports/M07-M09-correction-phase2-report.md`.
  - Red first, then permanent: `anEvictedCurrentTranslationIsRequestedAgain` (`eviction cannot leave SUCCEEDED permanently source-only`) and `noTimelineFallbackWorkStaysBounded` (`fallback work also needs a bound; actual=514`; the audit asserted once after 1000 cues and saw 1000, this test asserts every step).
  - C8: a SUCCEEDED record whose result was evicted is reset to PENDING at the shared work lookup with its budget reopened and its terminated call released; the no-timeline display-cue fallback is pruned at the real request entry, keeping only the requested cue, the in-flight one and cache-resident records.
  - C9: due retry and manual retry share one predicate (covers the playhead, or starts at/after it inside the window); the 3-attempt ceiling is enforced once at the shared submit entry so no dispatch path bypasses it; fallback counts only the single streamed-to-plain conversion, per the plan's 1/1/1 worked example.
  - C11: `historyFor` binary-searches the ordered units by first-segment index and reads at most the three immediately preceding ones; a test cache decorator asserts it by access count (4 reads at the end of a 60-cue timeline) rather than by elapsed time.
  - Documentation corrected: `onStreamingChanged`'s javadoc had claimed a cancelled in-flight request is re-dispatched "without spending a new attempt" — the cancelled request already reached the provider, so it spends one, and the re-dispatch spends another. The budget, not the toggle, is what bounds the network.
  - Phase 2 evidence (fresh ASCII copy `C:\tmp\smartube-code-fix`; raw XML kept at `C:\tmp\ev-phase2-jdk17`, `C:\tmp\ev-phase2-jdk11`, red run at `C:\tmp\ev-phase2-red`):
    - JDK 17 full `:common:testStbetaDebugUnitTest` → 51 suites, **tests=486, failures=0, errors=0, skipped=21**.
    - JDK 11 `:common:testStbetaDebugUnitTest --tests '….ai.subtitle.settings.*'` → 11 suites, **tests=79, failures=0, errors=0, skipped=0**.
    - The JDK 17 total rises 475 → 486 by exactly the eleven tests this phase adds; the skip count is unchanged at 21 for the same three suites as phase 1, all of which run 0-skipped in the JDK 11 lane. The JDK 11 lane is unchanged because no settings test was added.
- Phase 2 review gates ran on the task-3 diff as two subagents on the Standards and Spec axes, neither running tests; no finding was Blocking. Fixed: the unreachable `mActive` branch in the budget guard deleted; both prunes now call the `isResultStillCached` helper this phase introduced instead of re-inlining it; the `pruneUntimedWorkLocked` javadoc scoped to untimed records (it runs on the normal path too); `setStreamingEnabled`'s javadoc now states the real streaming rule instead of restating the call site; `retryFailed`'s javadoc no longer says "inside the current window"; `drainEvents` now copies and clears the pending queue under the monitor and drops a drain that starts after `close()`, so the new `close()` comment is true as written rather than overclaiming; the access test records and checks the actual keys read, not only how many; the context test moved to a mid-timeline cue so it can assert the absence of a future cue as well as a far-past one. Recorded rather than changed: the two prunes stay separate (disjoint populations, and sharing the loop would cost anonymous-class plumbing under API 17); the attempt budget is not absolutely unbreakable across a seek, because the plan's own bullet 4 requires dropping out-of-window records — resolved in favour of the bound, with the restart condition in the phase report. One deviation recorded: a javadoc paragraph on `SourceTimeline.getUnits()` stating the ordering contract the plan's binary search depends on — documentation only, no behaviour change, no upstream host file.
- Phase 3 (plan tasks 4–5) closes C5, C6 and C7 across `provider/http/OkHttpRequestExecutor.java` and the two adapters; `HttpCall` gained one method (`close()`) and no new transport type was introduced. Detail, red-run evidence and tables: `worker-reports/M07-M09-correction-phase3-report.md`.
  - Red first, then permanent, each message byte-identical to the audit's recorded failure: `aDeadlineBeforeTheResponseHeadersIsATimeoutNotACancel` (`expected:<TIMEOUT> but was:<CANCELLED>`), `aStreamingRateLimitKeepsItsRetryableCategory` (`expected:<RATE_LIMITED> but was:<PROTOCOL>`), `anOverloadedStreamingErrorStaysRetryable` (`expected:<SERVER> but was:<PROTOCOL>`), `anUnknownFinishReasonIsNotSuccessful` (`only an accepted completion may succeed`). Red run 3 suites / 4 tests / 4 failures, XML at `C:\tmp\ev-phase3-red`.
  - Three existing tests asserted the defect and were changed with the reason recorded: both `aStreamWithoutACompletionSignalIsNotASilentSuccess` (PROTOCOL → NETWORK, because a non-retryable category is exactly why no plain fallback ever happened) and the Anthropic `aStreamingErrorEventBecomesAFailure`, which expected PROTOCOL for `overloaded_error`.
  - Task 5 closes the M09 repeated-terminal PARTIAL: both streaming callbacks count their terminal deliveries and the two "delivers once and closes the read" tests assert the count does not move for a late delta, a late end of stream and a late transport failure.
  - Documentation corrected per task 5: the M08 plan, the M08 report and this ledger all claimed the request forces one response block. The request body sends the source as a plain string and constrains nothing; the three statements now describe the real known limitation (more than one text block would be concatenated in arrival order).
  - Recorded consequence: a stream that completes early no longer records the provider request id, because the HTTP read is closed before the end-of-body callback. Diagnostic only; both adapters' javadoc says so.
  - Phase 3 evidence (fresh ASCII copy `C:\tmp\smartube-code-fix`; raw XML at `C:\tmp\ev-phase3-jdk17`, `C:\tmp\ev-phase3-jdk11`, `C:\tmp\ev-phase3-red`):
    - JDK 17 full `:common:testStbetaDebugUnitTest` → 51 suites, **tests=506, failures=0, errors=0, skipped=21**.
    - JDK 11 `:common:testStbetaDebugUnitTest --tests '….ai.subtitle.settings.*'` → 11 suites, **tests=79, failures=0, errors=0, skipped=0**.
    - The JDK 17 total rises 486 → 506 by exactly the twenty tests this phase adds; the skip count is unchanged at 21 for the same three suites, and the JDK 11 lane is unchanged because no settings test was added.
- Phase 3 review gates ran on the tasks 4–5 diff as two subagents on the Standards and Spec axes, neither running tests; no finding was Blocking. Fixed: the two cancellation javadocs and the `transportFailureReason` javadoc now name `close()` and no longer claim an internal canceller; the request-id cost note no longer claims in-stream failures carry an id; the Anthropic `message_stop` javadoc no longer reads as if the stop reason arrived after it; `stopped()` renamed `isStopped()`; `refusedFailure` renamed `unacceptedFinishFailure`; the non-SSE transport branch now honours `close()`'s "no further callback" promise; the draft-bound test renamed to what it can assert, with the reason it cannot separate check-then-append from append-then-check recorded; both "delivers once" tests now assert the draft list is unchanged after the outcome; the scheduler's 429 loop now also proves a late transport failure costs no request and does not clear the cached final; and the M09 report's two PARTIAL rows plus this ledger's outstanding-items line now agree that both are closed. Recorded rather than changed: `FailureReason.IO` from the non-SSE branch maps to NETWORK with a generic message, discarding the transport's more accurate text — retryable, which is what enables the fallback, and outside this phase's diff. Also recorded: the red run executed the four probe bodies under their probe names, so the permanent tests (same scenario, same assertions, plus more) were not themselves observed red.
- Phase 4 (plan task 6) closes C10 in `integration/AiSubtitleCueBridge.java`. Detail, red-run evidence and tables: `worker-reports/M07-M09-correction-phase4-report.md`.
  - Red first, then permanent: `clearingTheVisibleDraftRepaintsImmediately` (`clearing the visible draft must repaint expected:<2> but was:<1>`, byte-identical to the audit), plus two more that exercise only pre-existing API so they could join the same red run — `switchingTheFeatureOffRepaintsBackToTheSourceLine` (`expected:<2> but was:<1>`) and `aRetryableFailureDoesNotClaimTheCueIsTranslated` (`expected:<TRANSLATING> but was:<TRANSLATED>`). Red run 1 suite / 3 tests / 3 failures, XML at `C:\tmp\ev-phase4-red`. The other eight new tests drive the interval and the flush, which do not exist before the fix, so they are new assertions rather than reproductions; the phase report says so.
  - The re-flush is one pending flag re-sent once by the existing position tick (`AiSubtitleController`, 1 s) — no timer or Handler added, and the guarantee is stated as "within the next tick", not as a precise 100 ms deadline. Deltas are still never queued. `flushPendingDraftRefresh` is package-private but a real production entry point.
  - Stream off in either direction, the feature being switched off, a seek and a release all notify the existing refresh listener immediately with the state already cleared. `onSchedulerArrived` no longer marks a cue TRANSLATED when what moved was a draft cleared for a retry: it claims TRANSLATED only when the unit the renderer last asked about really has an accepted final.
  - Documentation corrected in four places (`M08-plan.md`, `M08-report.md` twice, and this ledger's M08 deviations line): the old claim that a coalesced repaint is harmless because "a final always repaints" is false for a stream that stalls, so all four now describe the bounded re-flush.
  - Phase 4 evidence (fresh ASCII copy `C:\tmp\smartube-code-fix`; raw XML at `C:\tmp\ev-phase4-jdk17`, `C:\tmp\ev-phase4-jdk11`, `C:\tmp\ev-phase4-red`):
    - JDK 17 full `:common:testStbetaDebugUnitTest` → 51 suites, **tests=518, failures=0, errors=0, skipped=21**.
    - JDK 11 `:common:testStbetaDebugUnitTest --tests '….ai.subtitle.settings.*'` → 11 suites, **tests=79, failures=0, errors=0, skipped=0**.
    - The integration package alone: 91 tests, 0 failures after the fix. The JDK 17 total rises 506 → 518 by exactly the twelve tests this phase adds; the skip count is unchanged at 21 for the same three suites.
- Phase 4 review gates ran on the task-6 diff as two subagents on the Standards and Spec axes, neither running tests; no finding was Blocking. The substantive finding, raised independently by both axes: only stream on/off and the feature switch repainted, while the context toggle, a segmentation change and a provider/profile change also clear the cached translation and notified nobody — all three now notify after the state is cleared, and the C10 row no longer claims a seek or a release notifies (they clear the pending repaint and drop the recorded cue). Also fixed: the first version of the phase report claimed the other new tests could not compile against the pre-fix tree, which was false — a second red run over all of them replaced the guess with a measurement (5 red, 6 green because they assert the absence of a re-send the old code never produced, 1 not runnable pre-fix); the flush's "not due yet" branch now has a test that supplies its own clock value, which also gives the package-private flush a real caller; two test names that promised more than their bodies exercise were renamed or strengthened; a dead accessor the diff added now backs an assertion; three javadoc inaccuracies corrected; `mRenderedUnit` is now cleared on a seek; and the M08 plan/report contradictions about the mode matrix and the superseded coalescing note are annotated. Recorded rather than changed: most coalescing tests control the interval rather than a literal clock value, two streaming doubles now share a package, and `mRenderedUnit` can still name a pre-seek unit in the narrow SOURCE-mode window.
- Phase 5 (plan tasks 7–8) is the final phase. `worker-reports/M07-M09-correction-phase5-report.md`; the run's review roll-up is `reviews/M07-M09-correction-review.md`.
  - Task 7: the built-in indexed Prompt keeps its id and name but its text now treats `unit_index` as an input locator and asks for the translation alone, with its version moved 1 → 2 so installations migrate; the audit's migration probe is a permanent regression with the old text and version as fixed literals, plus a cache-identity assertion proving the old entry cannot be hit; the fixture gained the missing Japanese manual sample and now asserts the six explicit (language, captionKind) pairs; the 65-character "long" event became a 157-character line with splitting asserted on that line alone (piece count, coverage, exact text); the word-timing category became an honest short-cue category, with `VttParserTest` stating what the parser really does with inline word timings; and a new test loads the fixture resource and asserts it matches the builder, so the two cannot drift.
  - Task 8: every row of the plan's correction table is corrected in place with its history intact — the M07 report's R1 evidence gap, its R5-2 test-reference error, and the closure of R5-3; the M08 report's context/phone claim, its transport-classification claim and its final-only-stop claim; the M09 report's fixture matrix, its work-map bound and 2 MiB framing, its credential-storage description, its upstream tallies, and the per-file inventory it claimed but did not carry (now an appendix generated from the three milestone ranges). `upstream-patches.md` carries the recomputed table. No historical SHA or test number was replaced.
  - Host-patch surface re-verified file by file: five Java files (+43 / +2 / +6 / +35-1 / +4), `ids.xml +1`, `common/build.gradle +1`; eight files, +93/-1; no rename, no mode change, no deletion, no whole-file reformat; submodule pointers unchanged and no ExoPlayer path in the diff.
  - Phase 5 evidence (fresh ASCII copy `C:\tmp\smartube-code-fix`; raw XML at `C:\tmp\ev-phase5-jdk17`, `C:\tmp\ev-phase5-jdk11`):
    - JDK 17 full `:common:testStbetaDebugUnitTest` → 51 suites, **tests=524, failures=0, errors=0, skipped=21**.
    - JDK 11 `:common:testStbetaDebugUnitTest --tests '….ai.subtitle.settings.*'` → 11 suites, **tests=79, failures=0, errors=0, skipped=0**.
    - `:common:lintStbetaRelease` → `BUILD SUCCESSFUL`; the report holds 388 issues, **all Warning, 0 errors, and 0 of them in `ai/subtitle`**. XML kept at `C:\tmp\ev-phase5-lint.xml`.
    - The task-7 packages alone (prompt, source, segmentation, translation, provider): 24 suites / 189 tests / 0 failures. The JDK 17 total rises 518 → 524 by exactly the six tests this phase adds; the skip count is unchanged at 21 for the same three suites.
- Phase 5 review gates ran on the tasks 7–8 diff as two subagents on the Standards and Spec axes, neither running tests; no finding was Blocking. The first Spec attempt stalled with no progress and was reported failed, so it produced no findings and was dispatched again — recorded rather than treated as a pass. Fixed from both axes: the final review roll-up claimed this phase's gate runs before they had happened and listed per-phase finding counts a reader could not re-derive (both corrected; the counts table became a pointer table); the phase-1 report had no review section at all, and now carries one; the M07 R5-2 correction repeated the error it was correcting by naming three classes as the only ones `BoundaryProtocolTest` covers when it covers five; the cache-identity test named the corrected prompt but built both keys from the baseline, so the plan's "the old indexed entry cannot be hit" property was untested — it now builds them from the indexed prompt's old and shipped versions as well; and one row of the plan's correction table (M09 error normalisation PASS) had been left UNCORRECTED, now annotated with an A2 entry. Recorded rather than changed: the phase-5 tree changed three times while its Standards gate was reading it, so the review cannot be pinned to a SHA — the process fix is to freeze or commit before dispatching a gate and record the reviewed fingerprint.
- Continuation state for the remaining phases: `worker-plans/M07-M09-correction-continuation.md`, which also carries the iteration recipe, the probes each phase is anchored to, and the environment traps' home in `AGENTS.md`.

## Current state

- Current milestone: **M09 — implementation and automatic checks complete; final acceptance pending.** M09 commit `2f98945e3`; report `worker-reports/M09-report.md`. M09 is **not** marked complete and the roadmap milestone is not marked complete: the exact-SHA CI and the whole device matrix are still outstanding.
- M09 delivered: an access-ordered LRU translation cache bounded at 512 entries / 2 MiB; window-based pruning of the scheduler work map (bounded by the cache, with a 30 s history margin); a byte-based source download limit (8 MiB) and a 100 000 cue cap; a list-per-start unit index so two units sharing a start time are both kept; separate first/retry/fallback attempt counters; a credential-canary audit; all six provider types exercised through a fake transport; and a fixture pipeline suite that finally asserts the independently authored caption categories.
- M09 found and fixed three real defects that had made "bounded" false: the LRU never evicted (its scan reordered the map under the iterator, so the removal threw and the entry stayed; the cache reached 5741 entries), work records grew without bound (5741 after 256 seeks), and the prune scan itself kept the cache full by refreshing the LRU on every dispatch. A process defect was found too: five new cache tests were written without `@Test`, so the suite passed while running six of eleven methods.
- M09 local verification (2026-09-14, fresh ASCII copy at `C:\tmp\smartube-m07-m09`):
  - JDK 17 full `:common:testStbetaDebugUnitTest` → `BUILD SUCCESSFUL`, 51 suites, **tests=460, failures=0, errors=0, skipped=20**.
  - JDK 11 `:common:testStbetaDebugUnitTest --tests '....ai.subtitle.settings.*'` → `BUILD SUCCESSFUL`, 11 suites, **tests=78, failures=0, errors=0, skipped=0**.
- Upstream audit (M09-D): local `master` equals `upstream/master` at `6e2e00bb8c`, so the complete feature diff is 180 files, +28073/-1. `upstream-patches.md` was rewritten against the real diff: 5 host Java files, `ids.xml` and `common/build.gradle` (one added dependency); the previously undeclared host changes and the build change are now recorded, and the conditional `VideoLoaderController` hook is closed as unused. No upstream merge was performed.
- M09 remaining: exact-SHA CI (`PENDING PUSH AUTHORIZATION`) and the entire device matrix including the 2h device run and performance sampling (`PENDING DEVICE`). The two PARTIAL automatic items are both closed by the M07–M09 correction run — the explicit 50-drag case in phase 1, the repeated-terminal-callback assertion in phase 3 — and the M09 report's rows are annotated accordingly.
- M08 commit: `46c24aa0b` — bounded context, streamed drafts, both settings, and the two-protocol stream interpretations. Full detail: `worker-reports/M08-report.md`.
- M08 local verification (2026-09-14, fresh ASCII copy at `C:\tmp\smartube-m07-m09`):
  - JDK 17 full `:common:testStbetaDebugUnitTest` → `BUILD SUCCESSFUL`, 48 suites, **tests=442, failures=0, errors=0, skipped=20**.
  - JDK 11 `:common:testStbetaDebugUnitTest --tests '....ai.subtitle.settings.*'` → `BUILD SUCCESSFUL`, 11 suites, **tests=78, failures=0, errors=0, skipped=0**.
  - Both new switches default off, so this run is also the M07 core regression proving the optimizations can be disabled.
- M08 deviations recorded in the report: no context field on `TranslationRequest` (the rendered instruction is the single source of truth); the Anthropic accumulator does not bucket by content block index (the request body constrains nothing about the response's block count; a response with more than one text block is concatenated in arrival order); draft repaints are coalesced with one bounded re-send on the next tick rather than dropped outright (dropping without a re-send stranded the newest draft until a delta that may never come; corrected by correction-run task 6); the streaming switch invalidates by cancel plus a new request id rather than by advancing the session epoch.
- M08-F decision: persistent on-disk cache and video-summary context are **not implemented**. No measurement was run and none is claimed; the restart conditions are unchanged and neither blocks M09.
- M07 status unchanged: M07 implementation and local automatic verification complete; its exact-SHA CI and unified device acceptance remain pending.
- Current task: `M07 Task E — complete` (committed, tested); review items R0–R4 closed; R5 closed with one evidenced scope revision.
- M07 review-fix commits: `a73ce61cb` (R3, subtitle track resolution), `468d77839` (R0/R1/R2/R4, Task E completion, settings lifecycle, prompt wiring). Full detail: `worker-reports/M07-report.md`.
- M07 local verification (2026-09-14, fresh ASCII copy of the working tree at `C:\tmp\smartube-m07-m09`):
  - JDK 17 full `:common:testStbetaDebugUnitTest` → `BUILD SUCCESSFUL`, 45 suites, **tests=388, failures=0, errors=0, skipped=20**.
  - JDK 11 `:common:testStbetaDebugUnitTest --tests '....ai.subtitle.settings.*'` (ADR-010 lane) → `BUILD SUCCESSFUL`, 11 suites, **tests=78, failures=0, errors=0, skipped=0**.
  - The 20 JDK 17 skips are exactly the inherited Robolectric preference/secret suites, which run 0-skipped in the JDK 11 lane. No result is a device result.
- R5 outcome (evidence in `M07-report.md` §R5): the D3 manual-retry entry was genuinely missing and is now implemented (`TranslationScheduler.retryFailed` + a player settings entry + assertions). Partial-batch repair (M07 exit condition 7, second half) has **no executable production mode**: the boundary classes have no production caller, `BoundaryProtocol.encodeItem` has no caller at all, both adapters return whole-unit free text, the built-in indexed Prompt does not emit the wire format, the index base differs between request and validator, and no assembly path exists for a tail sub-unit. Recorded as a scope revision with restart conditions; the checkboxes stay unchecked and no roadmap exit condition was deleted.
- M07 not done: exact-SHA CI (`PENDING PUSH AUTHORIZATION`), unified device acceptance Task F (`PENDING DEVICE`), lint/assemble at candidate SHA (CI only).
- Task A status: **A1-A8 implemented and automatically verified (settings suites now 78/78 on the JDK 11 lane); device/CI acceptance pending.**
- M07 production changes in working tree: `AiSubtitlePhoneInputServer` now uses explicit save/test routes, pairing token on requests, strict version equality, no state-field overwrite, provider/protocol/base URL/model/target language/prompt fields, keep/replace/clear secret actions, prompt/provider rollback, and the existing `ProviderProfilesPresenter` save/test path; TV test callbacks are main-thread guarded and suppress late results after cancel/close. `ProviderProfilesPresenter.clearSecret` supports the explicit clear action.
- M07 automatic verification: local JDK 11 command `:common:testStbetaDebugUnitTest --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.*'` => `BUILD SUCCESSFUL in 1m 9s`; parsed 11 XML suites, tests=74, failures=0, errors=0, skipped=0.
- M07 committed status: Task A `717618f86`, Task B `738325822`, Task C `1ce3d8b2e`, Task D `38604559d`. Task C focused scheduler/integration tests passed 62/62; Task D focused scheduler/integration tests passed 67/67. CI and device acceptance are still pending.
- Baseline preserved: M02-M06 code remains present. M06 and earlier device checks remain pending; r6 CI/release evidence does not validate these uncommitted M07 changes.
- Device status: no TV/device connected; no M07 device validation attempted.

## Pause checkpoint (2026-09-13, M07 Task E interrupted) — SUPERSEDED 2026-09-14

> Superseded by the "Current state" section above and by `worker-reports/M07-report.md`. The record below is kept for history only: the uncommitted Task E work it describes was compiled, tested, corrected and committed as `468d77839`.

- Resume point: Task C and D are committed. HEAD is `38604559d`. Task E has partial, uncommitted implementation in the working tree and has not compiled or been tested since the last edits.
- Task C/D evidence: Task C `1ce3d8b2e` — focused scheduler/integration run parsed 6 XML suites, tests=62, failures=0, errors=0, skipped=0. Task D `38604559d` — focused scheduler/integration run parsed 6 XML suites, tests=67, failures=0, errors=0, skipped=0. Both local-only; no CI/device claim.
- Uncommitted Task E code: `AiSubtitleData` stores lookahead/throttle/segmentation limits; `AiSubtitleCueBridge` accepts scheduling and segmentation changes; `AiSubtitleRuntime.applySchedulingToBridge` reads the store and applies both; TV settings add lookahead, throttle, and segmentation options; phone server draft, page, validation, persistence, and Runtime application were extended; `RuleSentenceBreaker` and `SmartTubeSubtitleSourceAdapter` accept configured thresholds; English/simplified/traditional strings were added.
- Not yet done for Task E: compile the partial tree; add/verify persistence and rejection tests; verify lookahead 30s vs 90s request-window behavior; verify TV and phone write/read the same values; run focused settings/scheduler/integration tests; update E checkboxes and progress; make one Task E commit. Full common/lint/assemble and Task F device acceptance remain deferred as planned.
- Resume order: (1) `git status`/`git diff` and review the 10 dirty files; (2) copy the full repository to an ASCII path such as `C:\tmp\smartube-test` before Gradle; (3) compile/run targeted tests; (4) finish only missing tests/documentation; (5) commit Task E as one unit; (6) then prepare final Task F checklist, without starting M08.
- Owner: next resumption agent for this goal.

## Pause checkpoint (2026-09-12)

- Authoritative local state: `feature/ai-bilingual-subtitles` has a local pause-persistence commit on top of `6a2af2d2c1481f0977482adcf62ded81b5bf98f0`; `origin` remains at `6a2af2d2c` because the pause push was rejected for missing/invalid HTTPS credentials.
- Working-tree state at the original stop inspection: clean; the pause-persistence edits are the only subsequent docs changes and contain no production changes.
- Stop point: independent M04-C7 task review returned `CHANGES_REQUIRED`.
- Open Important issue 1: `M04-report.md` no longer contains the plan-required every-file inventory, witnessed RED evidence/test inventory, complete device matrix, and separate standards/spec self-review dispositions (required by plan section 13, lines 903–917).
- Open Important issue 2: the G04-1/G04-2 source-verification obligations remain open. The pause update adds an explicit owner and resume trigger to both research notes, but they are not yet source-closed or formally BLOCKED; complete that closure on resume.
- Resume order: (1) restore the M04 report template evidence; (2) close or explicitly block G04-1/G04-2; (3) update `progress.md` and the plan ledger; (4) commit and push the correction; (5) re-run the independent C7 review; (6) only after a clean review, begin M05-C0.
- Owner: the next resumption agent for this goal.
- Explicit trigger: user says resume/continue; no implementation may cross the M04-C7 gate before that.

## Pause checkpoint (2026-09-13)

- Authoritative local state: branch `feature/ai-bilingual-subtitles`, `HEAD=1cbeb95bb` (`docs(ai-subtitle): record interrupted M04 correction`); `origin` remains at `6a2af2d2c` and no push was attempted after the pause.
- Research state: G04-2 is source-verified. G04-1 source research is complete, but ADR-012 is reopened because the current SharedPreferences location is included by Android Auto Backup guidance; the backup/export policy correction is not accepted yet.
- Retained uncommitted correction files: `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/AndroidSecretStore.java`, `common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/AndroidSecretStoreRobolectricTest.java`, `common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/AndroidSecretStoreTest.java`, `docs/ai-subtitle/decisions.md`, and `docs/ai-subtitle/research/g04-1-android-secret-storage.md`.
- The correction Worker was stopped before committing. Its focused JDK 17 command (`:common:testStbetaDebugUnitTest` restricted to the two `AndroidSecretStore` test classes, from the ASCII junction with JDK 17) was interrupted; no pass/fail result exists.
- Resume order: review the five-file diff -> run the focused JDK 17 tests -> finish/verify the backup/export correction -> repair the complete M04 section 13 evidence -> update status/ADR text -> make a correction commit -> independent M04-C7 re-review. M05-C0 remains blocked.

## Completed work

- [x] Inspected workspace and located/cloned both repositories.
- [x] Recorded clean Git state, revisions, branches, remotes, and pinned submodules.
- [x] Read SmartTube README, Gradle topology, build constraints, and licenses.
- [x] Traced SmartTube caption discovery through MediaServiceCore, dynamic MPD, ExoPlayer, and `SubtitleManager`.
- [x] Traced player lifecycle and seek events through `ExoPlayerController`, `PlaybackPresenter`, and `BasePlayerController`.
- [x] Inspected subtitle settings, `PlayerData`, `AppPrefs`, shared persistence, OkHttp, Retrofit, RxJava, and current test facilities.
- [x] Investigated SmartTube PR #5839 and rejected it as a direct cherry-pick candidate.
- [x] Inspected KissTranslator normalization, sentence breaking, AI segmentation, boundary protocol, metrics, scheduler, rendering manager, cache identity, context, streaming, retry, prompts, provider code, tests, and fixtures.
- [x] Confirmed SmartTube MIT and KissTranslator GPL-3.0 license mismatch.
- [x] Read current official OpenAI, Anthropic, OpenRouter, DeepSeek, and MiMo API/model/streaming documentation.
- [x] Created the required Phase 0 architecture, migration, roadmap, decisions, progress, upstream ledger, milestone plan, and domain glossary.
- [x] Configured personal project `origin`, official `upstream`, and `feature/ai-bilingual-subtitles` branch.
- [x] Recorded GitHub Actions as the authoritative build/test environment.
- [x] Received and reviewed the first M02 Worker delivery (`918c487d2` through `3efb96374`, plus local report amendment `26693c340`).
- [x] Persisted `M02-review.md`, ADR-010, and one consolidated `M02-FIX-01` package.
- [x] Completed `M02-FIX-01`: first-cue immediate rendering, API 17 compatibility, immediate disable, JDK 11 preference lane; replacement run `34618112103` green.
- [x] Received and recorded the Commander second pass: 3 blocking items (settings-helper line endings, unproven re-enable completion, overstated automation coverage) plus 2 explicitly deferred judgement items.
- [x] Completed `M02-FIX-02` corrections: byte-level CRLF repair of the settings helper (rows 70–81), strengthened re-enable assertions with mutation evidence, and corrected report statements; run `34660051184` green for `854460bb4`.
- [x] Consolidated M03–M06 into one uninterrupted Worker execution plan with milestone-scoped commits, self-acceptance, exact-SHA CI evidence, subagent rules, Grill with Docs gates, live-document requirements, and post-return Commander Superpowers reviews; ADR-011 records the one-handoff/one-return policy.
- [x] Completed M03-C0..C5 (domain/session/cache/contracts) and M04-C0..C6 (provider persistence, credential protection, OpenAI/Anthropic adapters, presets/model discovery, provider settings UI, and host hook). M04-C7 checkpoint evidence is not accepted.

## Completed tasks

| Task | Result | Commit | Verification |
|---|---|---|---|
| Phase 0 reconnaissance | Accepted 2026-09-11 | `7e38c9db7` | Read-only evidence and document consistency checks |
| M02 first Worker delivery | Changes required | `918c487d2..26693c340` | Commander code/spec review; Actions run `34615801161` failed at lint |
| M02-FIX-01 correction | Green CI; second review required | `69f644f4a`, `be02bc2b3` | Run `34618112103` green (JDK 17 unit tests/lint/assembly + JDK 11 preference suite); Commander second pass found 3 blocking items |
| M02-FIX-02 correction | Accepted by user 2026-09-12 | `854460bb4`, `0b1f4ab53`, `2716e5380` | Run `34660051184` green for `854460bb4`: 34 JVM tests on JDK 17 and 3/3 preference methods on JDK 11; docs-only run `34660774973` succeeded; mutation and CRLF-fit evidence recorded |
| M03 domain/session/cache/contracts | Self-acceptance complete; Commander review pending | `422451df8..8a4bd175b`; product tip `52877ed96` | 168 passed / 0 failed / 3 skipped on JDK 17; lint green; static gate found 0 non-feature paths; exact-SHA CI blocked at workstation API |
| M04-C0 policy settlement | Accepted as Worker evidence | `8d0c9a50e` | G04-1/ADR-012 and G04-2 research recorded; docs-only |
| M04-C1 provider profile persistence | Local TDD green; pushed; exact-SHA CI pending UI verification | `3e1e1ed60` | Incremental RED plus full GREEN; JDK 17 193 passed / 0 failed / 6 skipped; JDK 11 settings suite 6/6; three mutation checks each produced named failures and were reverted |
| M04-C2 credential protection | Local TDD green; pushed; exact-SHA CI pending UI verification | `686768e54` | RED missing-symbol compile rounds plus API-policy RED, then full GREEN 214 total / 207 passed / 0 failed / 7 skipped on JDK 17 and 41/41 on JDK 11; cleanup and API-threshold mutations produced 3/1 named failures and were reverted; secret scan clean |
| M04-C3 OpenAI-compatible adapter | Local TDD green; pushed; exact-SHA CI pending UI verification | `47af3c992` | RED 44 missing-symbol compile failures, then 14/14 adapter tests and full GREEN 221 passed / 0 failed / 7 skipped on JDK 17; authorization and JSON-escaping mutations produced 2/1 named failures and were reverted; lint green |
| M04-C4 Anthropic-compatible adapter | Local TDD green; pushed; exact-SHA CI pending UI verification | `54085bdf4` | RED 15 missing-symbol compile failures, then 14/14 adapter tests and full GREEN 235 passed / 0 failed / 7 skipped on JDK 17; x-api-key and top-level-system mutations each produced 1 named failure and were reverted; lint green |
| M04-C5 presets/model discovery | Local TDD green; pushed; exact-SHA CI pending UI verification | `46c780405` | RED 17 missing preset/resolver symbols plus missing model-catalog surface, then 16/16 C5 tests and full GREEN 251 passed / 0 failed / 7 skipped on JDK 17; preset-protocol and manual-model mutations produced 2/3 named failures and were reverted; lint green |
| M04-C6 settings management and host hook | Local TDD green; task review clean; exact-SHA CI pending UI verification | `130c47e02..0cc5bd6c6` | Full milestone C7 verification: 281 total / 0 failed / 7 skipped on JDK 17 and 62/62 settings/secret on JDK 11; lint green; host hook is one import + one feature-owned entry call; CRLF preserved; task review clean at `0cc5bd6c6` |
| M04-C7 checkpoint | `CHANGES_REQUIRED`; paused for correction | `0cc5bd6c6..6a2af2d2c` | Worker verification is green, but independent task review found the report-template evidence incomplete and G04-1/G04-2 re-verification obligations unowned; M04 is not accepted |

## Accepted commits

- `7e38c9db7` — Phase 0 architecture, migration map, roadmap, decisions, progress, upstream strategy, M01 plan, and glossary.
- `918c487d2` through `2716e5380` — M02 implementation, corrections, and audit trail; user relayed Commander PASS after run `34660051184`.

## Rejected or superseded commits

None.

## Pending commits

- M04-C7 correction commit: not created. Research closure is present in `a0aab0d77`; the five-file backup/export correction remains uncommitted, and its focused JDK 17 test was interrupted. Resume edits must restore the required report evidence and then commit before any re-review; push remains unauthorized/not attempted.
## Open problems

1. The selected Exo subtitle format does not expose the full timed-text URL through a stable app-level API. M02 must validate track matching and format-info cache behavior before deciding whether a loader hook is unavoidable.
2. Existing `SubtitleManager` disables embedded styles. M02 must prove whether a decorated two-line cue can retain separate source/translation styles without forking `SubtitlePainter`.
3. The current GitHub Actions workflow does not automatically validate the feature branch or explicitly invoke unit tests. The M02 Stage Package adds an isolated validation workflow before its product changes are accepted.
4. Open at M04-C7 pause: G04-1 source research is complete, but ADR-012 is reopened because the current SharedPreferences location is included by Android Auto Backup guidance; the retained backup/export correction is uncommitted and unverified.
5. KissTranslator tests/fixtures are GPL-covered repository content; target fixtures need independent authorship/provenance.
6. Resolved by `M02-FIX-01`: M02 synchronous Fake completion is now consumed on the first cue-processing call.
7. Resolved by `M02-FIX-01`: no M02 production reference requires an API newer than 17 (`EnableState` seam; explicit null-safe equality).
8. Resolved by `M02-FIX-01`: disabling the setting cancels in-flight work and clears bridge state inside the settings callback.
9. Resolved by `M02-FIX-01`: the Robolectric preference suite executes in the supplementary JDK 11 job (3/3 passed in runs `34618112103` and `34660051184`).
10. Open: `SubtitleSettingsPresenter.java` is CRLF in the Git object store while repository-wide `core.autocrlf=true`; any whole-file rewrite creates churn, so edits to that file must stay byte-verified and be staged with the autocrlf conversion disabled.

## Current architecture decisions

- Accepted: feature-owned package tree inside `common`, not a new Gradle module.
- Accepted: clean-room behavioral implementation from KissTranslator evidence.
- Accepted: five Provider Types over two shared protocol adapters.
- Accepted: one decorated cue through existing `SubtitleView`; no renderer fork.
- Accepted: full-track source adapter using existing MediaServiceCore access, with one conditional loader hook only if evidence requires it.
- Accepted: dedicated versioned `AiSubtitleData`, separate from `PlayerData`.
- Accepted: `origin` is the user's personal project remote; `upstream` is official SmartTube.
- Accepted: GitHub Actions is authoritative for build/test acceptance.
- Accepted: one Stage Package and one consolidated Worker return per Milestone is the default; ADR-011 defines the M03–M06 exception without removing their milestone-scoped evidence.
- Accepted: JDK 17 remains authoritative; a narrow JDK 11 job may execute the inherited Robolectric 4.6.1 preference suite until its dependency is upgraded.
- Accepted: M02-FIX-02 stays inside the three-file upstream patch surface and leaves the two deferred refactors (duplicated equality helpers, bridge request-identity cluster) untouched.
- Accepted: ADR-011 supersedes ADR-009 only for the M03–M06 handoff boundary: one uninterrupted Worker handoff/return, Worker self-acceptance per milestone, then Commander Superpowers second review per milestone.

## Planned next action

M07, M08 and M09 are implemented and automatically verified. The next action is the final
acceptance pass, which needs two things this session did not have:

1. **Push authorization** for `feature/ai-bilingual-subtitles`, so `ai-subtitle-validation.yml`
   runs against the exact candidate SHA (full `common`, JDK 11 settings lane, lint, assemble,
   signed APK and report artifacts). Record the run id and inspect the artifacts, not just the
   green icon.
2. **A device**, for `M09-plan.md` §E and the device rows of the M09 matrix: install a candidate
   APK, verify the phone pairing flow, the three display modes and both bilingual orders in
   en/zh/zh-rTW, remote-control reachability, seek/pause/off/release, background/foreground,
   the 2h-long-video run with performance sampling, and provider failure and cancel behaviour.

Until both exist, M09 stays "implementation and automatic checks complete, final acceptance
pending" and the roadmap milestone stays incomplete. Do not create a release or tag.

The older M04-C7/M05-C0 pause text below is historical and was resolved during M05/M06.

## Test status

- Phase 0: no production behavior changed, so no product test was run.
- M02 local diagnostic report (first delivery): 31 pure-JVM tests passed; 3 Robolectric preference tests skipped and not accepted as coverage.
- M02 authoritative run `34615801161`: FAILURE at lint; validation-reports artifact exists; no accepted assembly/APK result.
- M02-FIX-01 authoritative run `34618112103`: green — bridge 19/19, controller 10/10, provider 5/5 on JDK 17; preference suite 3/3 on JDK 11; lint and beta assembly pass.
- M02-FIX-02 local diagnostics (JDK 17): 34 JVM tests green (bridge 19, controller 10, provider 5; preference suite deliberately skipped); mutation check red at the dual-line assertion with the post-re-enable delivery removed (JUnit line 267 in the mutated file, 268 restored) and green again after restoring it; settings helper byte-verified at 102 CRLF / 0 LF-only lines with a 12/12 diff.
- M02-FIX-02 authoritative run `34660051184`: green — same counts as run `34618112103`; primary job 5m29s and preference job 2m10s, all steps successful; three artifacts present.
- M02-FIX-02 docs-only follow-up: run `34660494711` was cancelled by concurrency after the JDK 11 job completed; superseding run `34660774973` for `2716e5380` completed successfully with all steps and three artifacts.
- M03-C1 local diagnostics (JDK 17): witnessed RED 50 tests / 32 failed, then GREEN 50/50; full ai-subtitle regression 84 passed / 0 failed (domain 50 + bridge 19 + controller 10 + provider 5); `AiSubtitleDataTest` stays 3-skipped on JDK 17 per ADR-010.
- M03-C2 local diagnostics (JDK 17): witnessed RED 24 tests / 19 failed (session scaffold), then GREEN 24/24; full ai-subtitle regression 118 passed / 0 failed (domain 50 + session 24 + integration 39 + provider 5). Mutation checks: owns-ignoring-generation 2 named failures, owns-ignoring-epoch 1, dropped result-identity check 1 — all reverted, suite re-ran green.
- M03-C3 local diagnostics (JDK 17): witnessed RED 26 tests / 6 failed (cache scaffold), then GREEN 26/26; full ai-subtitle regression 150 passed / 0 failed / 3 skipped (cache 26 + domain 53 + session 24 + integration 42 + provider 5). Mutation check: dropping the base-URL comparison from the cache key fails `baseUrlIdentityIsIsolated` — reverted, suite re-ran green.
- M03-C4 local diagnostics (JDK 17): witnessed RED 171 tests / 6 failed (contract scaffold, all pre-existing tests compiled and passed), then GREEN 168 passed / 0 failed / 3 skipped (cache 27 + domain 53 + session 24 + integration 44 + translation 20). Contracts now carry session/unit identity, final/partial state, and normalized failure categories; the production Fake baseline output was re-verified.
- M03-C5 static gate (local, JDK 17): `git diff --check` clean; 30 added / 9 modified files with 0 non-feature paths; host-file diff empty; brand and network-import scans clean; `:common:lintStbetaDebug` BUILD SUCCESSFUL (API 17 preserved). Exact-SHA GitHub Actions status: `BLOCKED` — the Actions API is unreadable from this workstation (404 for the private repository), so runs for the five M03 tips must be verified in the GitHub UI.
- M04-C0 (documentation-only): no product test applicable; evidence is the G04-1 local SDK verification (`KeyGenParameterSpec`/`KeyProperties` since API 23 queried from the SDK api-versions.xml) and the G04-2 Phase 0 protocol baseline. Interruption point recorded in `worker-reports/M04-report.md`; M04 base pinned at `8a4bd175b`.
- M04-C1 local diagnostics: witnessed RED while production types/API were absent (34 + 8 + 8 + 13 + 7 missing-symbol compile failures across the value, serializer, migration, repository, and Android-store rounds) and a 1-failure RED for invalid optional collection repair; then full ai-subtitle GREEN 199 total / 193 passed / 0 failed / 6 skipped on JDK 17, and `AiSubtitleDataTest` 6/6 with 0 skipped on JDK 11. Mutation checks: disabled default/selection repair produced 4 named failures; dropped credential-reference serialization produced 2; disabled future-schema rejection produced 1; each mutation was reverted; `:common:lintStbetaDebug` is green.
- M04-C2 local diagnostics: initial RED was 57 missing-symbol compile failures across secret-store and repository cleanup tests, with a later 3-symbol RED for the API policy selector; full ai-subtitle GREEN is 214 total / 207 passed / 0 failed / 7 skipped on JDK 17, and the complete settings/secret suite is 41/41 with 0 skipped on JDK 11. Mutation checks skipped credential cleanup (3 named failures) and lowered the policy threshold (1 named failure), then restored the code. High-confidence repository secret scan found no real credential. `:common:lintStbetaDebug` is green.
- M04-C3 local diagnostics: after the test surface compiled, the adapter round witnessed RED as 44 missing production symbols, then GREEN 14/14 protocol tests and the full ai-subtitle suite at 228 total / 221 passed / 0 failed / 7 skipped on JDK 17. Mutation checks omitting Authorization produced 2 named failures and disabling quote escaping produced 1; both were reverted. `:common:lintStbetaDebug` is green.
- M04-C4 local diagnostics: witnessed RED as 15 missing production symbols, then GREEN 14/14 Anthropic protocol tests and the full ai-subtitle suite at 242 total / 235 passed / 0 failed / 7 skipped on JDK 17. Mutation checks replacing the x-api-key value and renaming top-level system each produced 1 named failure; both were reverted. `:common:lintStbetaDebug` is green.

- M04-C5 local diagnostics: witnessed RED as 17 missing preset/resolver symbols plus a missing model-discovery surface, then GREEN 16/16 C5 tests and the full ai-subtitle suite at 258 total / 251 passed / 0 failed / 7 skipped on JDK 17. Mutation checks forcing MiMo to the Anthropic preset produced 2 named failures and dropping manual Model ID preservation produced 3; both mutations were reverted. `:common:lintStbetaDebug` is green.

- M04-C6 local diagnostics (JDK 17): feature-owned provider settings UI and the single host hook landed; task review clean at `0cc5bd6c6`. Full milestone verification is recorded at M04-C7.
- M04-C7 authoritative local verification (JDK 17): `git diff --check` clean; full ai-subtitle suite 281 total / 0 failed / 7 skipped (BUILD SUCCESSFUL, 1m 1s); `:common:lintStbetaDebug` BUILD SUCCESSFUL (1m 14s); JDK 11 settings/secret lane 62/62 with 0 skipped (BUILD SUCCESSFUL, 1m 15s). CRLF byte audit for `SubtitleSettingsPresenter.java` reports 93 CRLF / 0 LF / 0 CR; host diff is one import + one feature-owned entry call; secret/GPL/network/forbidden-path/SSE scans clean. Exact-SHA GitHub Actions status: `BLOCKED` — workstation Actions API is unreadable; see `.superpowers/sdd/M04-C7-report.md`.
11. Resolved by `a0aab0d77`: G04-2 provider endpoint/header details were closed against current official sources for all five provider types.
12. Open at M04-C7 pause: the M04 report must be restored to the complete plan section 13 template before acceptance; owner is the next M04-C7 resumption, trigger is explicit resume.

- 2026-09-13 M07 Task C implementation：新增 `AI/scheduler/TranslationScheduler.java` 作为请求窗口/去重/暂停/seek/取消的单一 owner；Bridge 现在在完整 timeline 可用时只按真实时间选择 unit，不再用当前 cue 造 segment 0；Controller 接入播放位置、拖动 seek 保存最新位置、seek end 读取实际位置，并通过可取消 Handler 补足 onTickle 频率。生产路径安装 SmartTubeSubtitleSourceAdapter，从 MediaItemService 获取字幕列表并下载 timed-text。
- 2026-09-13 M07 Task C focused scheduler/integration：`:common:testStbetaDebugUnitTest --tests '...scheduler.*' --tests '...integration.*'` => BUILD SUCCESSFUL in 3s；解析 6 个 XML：tests=62, failures=0, errors=0, skipped=0。此结果只代表自动验证，不含 CI/真机验收。
- 2026-09-13 M07 Task B focused source/segmentation/integration：`gradlew :common:testStbetaDebugUnitTest --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.source.*' --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.segmentation.*' --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.integration.*'` => BUILD SUCCESSFUL in 26s；解析 11 个 XML：tests=79, failures=0, errors=0。新增 VttParserTest 和 SmartTubeSubtitleSourceAdapterTest；integration 套件全部通过，确认 timeline 回退路径不破坏 M03 行为。该结果针对未提交工作区，不含 CI/真机验收。

- 2026-09-13 M07 Task D implementation：调度器改为 transient 失败最多 3 次尝试（首次 + 2 次退避），1s/2s 加 0–250ms jitter；AUTH/PROTOCOL/INVALID_OUTPUT/CANCELLED 保持 terminal；部分草稿、空/错覆盖响应终止并保留原文；seek/暂停/身份变化取消 retry。当前生产协议返回整 unit 文本且没有 per-segment boundary 元数据，因此不引入推测性 prefix/tail parser；整 unit 的 segment ID 覆盖仍严格校验。
- 2026-09-13 M07 Task D focused scheduler/integration：`:common:testStbetaDebugUnitTest --tests '...scheduler.*' --tests '...integration.*'` => BUILD SUCCESSFUL in 4s；解析 6 个 XML：tests=67, failures=0, errors=0, skipped=0。此结果只代表自动验证，不含 CI/真机验收。
