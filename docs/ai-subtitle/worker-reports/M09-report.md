# M09 Worker Report

Task ID: `M09`

Milestone: M09 — Hardening, upstream-regression gate and release candidate

Status: **IMPLEMENTATION AND AUTOMATIC CHECKS COMPLETE. FINAL ACCEPTANCE PENDING.** The
code, the automatic suites and the upstream audit are done. Exact-SHA CI is
`PENDING PUSH AUTHORIZATION` and every device item is `PENDING DEVICE`, so M09 is **not**
marked complete and the roadmap milestone is not marked complete.

## Pinned SHAs

| Item | SHA |
|---|---|
| Branch | `feature/ai-bilingual-subtitles` |
| M07 implementation and review fixes | `38604559d` (resume point) → `a73ce61cb`, `468d77839` |
| M07 documentation | `21b61a3ae` |
| M08 | `46c24aa0b`, `a6d6e6943` |
| M09 | `2f98945e3` |
| Upstream base | `master` == `upstream/master` == `6e2e00bb8c989e089735c3f26fcd5511669f1597` |
| Submodules | `MediaServiceCore 82e9ccde`, `SharedModules 86f0327` (unchanged) |

## A. Coverage matrix and automatic regression

The matrix below is the M09 acceptance matrix. Every automatic row names the test method that
proves it; every device row is marked `PENDING DEVICE` rather than being claimed.

| Scenario / operation | Expected | Evidence | Result |
|---|---|---|---|
| English manual and auto-generated in one list, order swapped | The selected track is chosen, not the first | `matchSubtitleSelectsTheSelectedSameLanguageTrackRegardlessOfListOrder` | PASS |
| Only a language code is known and two tracks share it | Source-only, never a guess | `matchSubtitleFallsBackToSourceOnlyWhenOnlyTheLanguageCodeIsKnown` | PASS |
| Timed-text URL without a query / with a non-VTT fmt / with escaped signature / with a fragment | `fmt=vtt` set without corrupting anything else | `toVttUrlAddsQueryWhenTheTimedTextUrlHasNone`, `toVttUrlReplacesANonVttFormatWithoutTouchingSignedParameters`, `toVttUrlKeepsFragmentsAfterTheQuery` | PASS |
| Chinese, Japanese, English fixtures, manual and ASR | All present and asserted | `theFixtureCoversEveryRequiredLanguageAndCaptionKind` | PASS — **corrected by the M07–M09 correction run task 7**: the assertion checked the language set and the caption-kind set separately, which passes while a whole pair is missing; Japanese manual captions were missing. It now asserts the six explicit pairs, and `ja-manual-001` supplies the missing one. |
| Word timing, no-space, noise, duplicate, fast, slow, overlap, gap, long | Each behaves as its category promises | `thePipelineKeepsContentAndDropsOnlyWhatItShould` | PASS — **corrected by the M07–M09 correction run task 7**: "word timing" was a provenance label with no per-word times anywhere, and the "long" event was 65 characters, under the splitter's 80-character threshold, so the splitting assertion (`texts.size() > 1` over the whole fixture) proved nothing. The label is now an honest short-cue category, the long line is 157 characters with a targeted assertion, and what the parser really does with inline word timings is stated in `VttParserTest`. |
| A source gap | No unit to show; the previous caption is not held over | `aGapInTheSourceHasNoUnitToShow` | PASS |
| Segment/unit time mapping | Contiguous coverage, ordered by time, every segment resolves | `everyUnitCoversContiguousSegmentsInTimelineOrder`, `everySegmentResolvesToAUnitAndEveryUnitToASegment` | PASS |
| 2h+ synthetic timeline (7500 one-second cues), 256 seeks | Work records and cache stay bounded | `aTwoHourTimelineStaysBoundedUnderRepeatedSeeks` | PASS |
| Two units sharing a start time | Both dispatched, neither dropped | `twoUnitsThatShareAStartTimeAreBothDispatched` | PASS |
| Pause → resume, seek, drag, track/video/config change, off/on, reopen | No stale result changes the current state | `pauseStopsNewWorkAndResumeDispatchesImmediately`, `dragKeepsLatestPositionAndSeekEndDispatchesOnlyIt`, `newVideoStartsFreshSessionIdentity`, `trackChangeCreatesNewGenerationWithNewIdentity`, `releaseClosesTheActiveSession`, `seekingClearsTheDraftAndCancelsTheStream` | PASS |
| 100 identical ticks | One logical request | `repeatedPositionTicksDoNotDuplicateRequests` | PASS |
| A later unit finishing first | Never used as current history | `aLaterUnitThatFinishedFirstIsNeverHistory` | PASS |
| AUTH / PROTOCOL / INVALID_OUTPUT / CANCELLED / NETWORK / TIMEOUT, 429, 5xx | Normalized, budgeted, terminal categories never retried | `everyProviderFailureCategoryKeepsSourceOnly`, `terminalCategoriesDoNotRetry`, `rateLimitAndServerUseTheSameRetryBudget`, `timeoutUsesThreeNetworkAttemptsWithBackoff` | PASS — **corrected by the M07–M09 correction run tasks 4–5**: this row covers the scheduler's handling of a category once it is known, and that part held. Error *normalisation* did not. A call deadline before the response headers was reported as CANCELLED rather than TIMEOUT, and a streamed 429 or 5xx was reported as PROTOCOL rather than a retryable category, so the plain fallback never ran for the failures that most need it. Both were reproduced red and fixed; the evidence is in `M07-M09-correction-phase3-report.md`. |
| Provider throws synchronously | Isolated to source output | `providerExceptionIsIsolatedToSourceOnlyOutput` | PASS |
| Streaming off and on | Core chain passes in both modes | Full suite runs with both switches off; streaming cases run with it on | PASS |
| 50 rapid drags (explicit count) | Only the final position dispatches | Not written as a 50-iteration case; the semantics are covered by the drag test | PARTIAL — closed by the M07–M09 correction run phase 1 (`AiSubtitleControllerTest`) |
| Repeated terminal callback | No state or handle left hanging | Guaranteed by one-shot flags in the transport and adapters, but no dedicated assertion | PARTIAL — closed by the M07–M09 correction run phase 3 (terminal-count assertions in both adapter streaming suites) |
| Background / foreground, PiP | Lifecycle correct, no background requests | Needs a device | PENDING DEVICE |
| 2h+ continuous playback on a device | Resource stability | Needs a device | PENDING DEVICE |

## B. Long-video capacity, request budget and performance

- `InMemoryTranslationCache` is now an access-ordered `LinkedHashMap` bounded at 512 entries and
  2 MiB of stored UTF-8 text, whichever comes first. Sizes are measured on put, corrected on
  replace, and reset on clear. A single value larger than the whole budget is not kept.
- `TranslationScheduler.pruneWorkLocked` drops terminal records whose unit has left the window,
  keeping a 30-second trailing margin so the bounded context still finds the last few translated
  units. Records whose result is still cached are retained — the frozen cache key lives on the
  record, and dropping it early would make a cached translation unreachable. That ties the map's
  bound directly to the cache's bound.
- The unit index is now `TreeMap<Long, List<TranslationUnit>>`, so two units sharing a start time
  are both kept.
- The source download limit is now a true **byte** limit (8 MiB); it was a character count, which
  a multi-byte caption stream could exceed several times over. The adapter refuses a timeline
  above 100 000 cues rather than truncating it.
- Attempts are counted separately for first, ordinary retry, and SSE-to-plain fallback. The
  per-unit budget stays three. Manual retry opens a new budget; ordinary redraws never reset it.
- The `tail repair` counter does not exist because that mode has no production path (see
  `M07-report.md` §R5-2).

### Bugs found by these checks

Running the bound tests found three real defects, each fixed with the check that found it:

1. **The LRU never evicted.** `evict()` read each entry through `get()` while iterating an
   access-ordered map, which reorders the map under the iterator; the subsequent
   `Iterator.remove()` threw and the entry added before the failure stayed. The cache grew to
   5741 entries with no upper bound. Now the scan collects keys first and removes them after.
2. **Work records grew without bound** (5741 after 256 seeks). Fixed by pruning outside the
   window, as above.
3. **The prune scan kept the cache full** by touching every entry through `get()`, refreshing
   the LRU order on every dispatch so nothing was ever evictable. `TranslationCache` gained a
   non-touching `contains`, which is what the scan uses.

### Performance

No performance failure was observed, so no thread model, player, or dependency version was
changed. No device sampling was run and no request-count, latency, or memory figure is claimed.

## C. Phone, TV, Provider and secret-data audit

- All six `ProviderType` values were driven through their protocol against a fake transport and
  completed a translation (`everyProviderTypeResolvesToAProtocolAndCompletesThroughFakeHttp`).
  The only difference between types at the wire level is the preset; no brand-specific parser
  exists.
- A unique credential canary was pushed through a success response, a provider error response
  containing the canary, and cancellation, then searched for in the adapter, the request, the
  call handle, the HTTP request, the HTTP failure and the HTTP response strings
  (`theCredentialCanaryNeverAppearsInAnyDiagnostic`).
- Replacing a credential changes what the next request carries
  (`replacingTheSecretChangesWhatTheNextRequestCarries`).
- Bilingual order changes presentation without issuing a request; the three display modes and
  both orders are asserted. zh, zh-rTW and en strings are present for every new user-visible
  string.
- The credential still lives only in the encrypted store decided by ADR-012; M08 added no disk
  path, so there is no new file to audit for secrets. The phone state response does not echo the
  secret (existing assertion).
- Device-only items — pairing by QR, real-provider combinations, remote-control reachability of
  long options, offline and no-provider prompts on screen, backup/export behaviour on a real
  device — are `PENDING DEVICE`.

## D. Upstream patch audit and continuous regression

`docs/ai-subtitle/upstream-patches.md` was rewritten against the actual diff, not the plan:

- Local `master` is identical to `upstream/master` at `6e2e00bb8c`; `git diff master...HEAD` is
  therefore the complete feature diff. **Corrected by the M07–M09 correction run task 8: at
  `10d6a18cf` the diff is 184 files, +29 324 / -1, not 180 and +28 073 / -1.** Recomputing it is
  the point — the number was carried over rather than measured. The correction candidate
  `0fef49ade` is 190 files, +33 012 / -1.
- The real host surface is **5 Java files** (`PlaybackPresenter`, `SubtitleManager`,
  `SubtitleSettingsPresenter`, `PlayerUIController`, `VideoPlayerGlue`), **1 resource file**
  (`ids.xml`) and **1 build file** (`common/build.gradle`, one added dependency for the pairing
  QR code).
- Two host modifications and the build change were **missing from the ledger** and are now
  recorded with their purpose, patch surface and regression check.
- The conditional `VideoLoaderController` hook is **closed as unused**: the adapter reaches the
  selected track through the existing public media-item service.
- No whole-file formatting, no renames, no executable-bit changes, no ExoPlayer, submodule or
  dependency-version changes. No HTTP, cache or scheduling logic lives in a host file.
- The workflow `ai-subtitle-validation.yml` is unchanged and still covers full `common`, the
  JDK 11 settings lane, lint, assemble, APK signing and report artifacts. No second workflow was
  added. Its run against the candidate SHA is `PENDING PUSH AUTHORIZATION`.
- A concrete upstream-sync procedure and a regression checklist were added to the same document.
  Explicitly: no upstream merge was performed.

## E. Unified device acceptance

`PENDING DEVICE`. No device is connected, so no item in this section was executed and no
screenshot, count or score is reported. The full list stays in `M09-plan.md` §E and is
reproduced in the matrix above; the operating steps are unchanged from M07 Task F and
`tv-usability-repair-plan.md`.

## F. Release candidate and completion gate

- This report carries the M07→M09 commit list, the per-file inventory for all three milestone
  ranges (appendix at the end of this report), the fixes found by running, the accepted and
  rejected M08 optional features, the test method names and counts, the upstream diff
  conclusion and the known limitations. **Corrected by the M07–M09 correction run task 8:**
  this bullet previously said the modified files per milestone were "in the M07 and M08
  reports". They were not — those reports describe types and behaviour, not file lists — and
  the appendix now carries what the milestone ranges actually contain.
- Exact-SHA CI: **not run**. No candidate APK exists, so no package name, ABI, SHA-256 or signing
  fingerprint is reported.
- Completion gate: M07 source/scheduler/settings correctness is covered automatically; M08 is
  switchable off and its cache is bounded; M09 resource and security dimensions are covered
  automatically; the upstream audit is done. The **device dimension and the exact-SHA CI are
  both PENDING**, so M09 is not complete and the roadmap milestone is not marked complete.
- No release, tag or APK upload was created, and no existing tag was touched. Release remains a
  separate, later, explicitly authorized step.

## Verification

Local, from a fresh ASCII copy of the working tree at `C:\tmp\smartube-m07-m09`.

| Lane | Command | Result |
|---|---|---|
| JDK 17, full `common` | `gradlew.bat :common:testStbetaDebugUnitTest` | `BUILD SUCCESSFUL` — 51 suites, tests=460, failures=0, errors=0, skipped=20 |
| JDK 11, settings/secret (ADR-010 lane) | `gradlew.bat :common:testStbetaDebugUnitTest --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.*'` | `BUILD SUCCESSFUL` — 11 suites, tests=78, failures=0, errors=0, skipped=0 |

The JDK 17 skips are exactly the inherited Robolectric preference/secret suites, which run
0-skipped in the JDK 11 lane.

One process defect was found and fixed here as well: five newly added cache tests were missing
their `@Test` annotation, so the suite reported success while silently running only six of the
eleven methods in that class. The suite was re-run after the fix.

## Known limitations and remaining work

| Item | State | Trigger |
|---|---|---|
| Exact-SHA CI (common, lint, assemble, JDK 11 lane, artifacts) | `PENDING PUSH AUTHORIZATION` | An authorized push to the feature branch |
| Device matrix (sections E and the device rows above) | `PENDING DEVICE` | A TV/Android device and a candidate APK |
| Performance and memory sampling on a long video | `NOT RUN` | The same device |
| 50-drag explicit case, repeated-terminal-callback assertion | `CLOSED` | Both closed by the M07–M09 correction run (phase 1 and phase 3) |
| Partial-batch repair | `NOT IMPLEMENTED` | The three restart conditions in `M07-report.md` §R5-2 |
| Persistent cache and video summary | `NOT IMPLEMENTED` | The measurement conditions in `M08-report.md` §F |
| Display mode × streaming combination matrix | `COVERED` | Closed by the M07–M09 correction run task 6 (`AiSubtitleCueBridgeModeTest`) |

## Correction — M07–M09 correction run, task 8 (2026-09-14)

Appended, not a rewrite. The local numbers above (460/20 and 78/0) are the M09 record and stay
as they are; the correction run's own numbers are in `progress.md`, and the two are not the same
measurement of the same tree.

### A. The fixture matrix proved less than it claimed

Two rows are annotated above. In short: the language and caption-kind assertions were over two
independent sets, which is satisfied while an entire pair is missing — Japanese manual captions
were — and the "word timing" category was a provenance label with no per-word times behind it
anywhere in the fixture or the parser, while the "long" event was 65 characters and so never
crossed the splitter's 80-character threshold. Task 7 added the missing sample, asserted the six
explicit pairs, replaced the long event with a genuinely long line and asserted splitting on that
line alone, renamed the word-timing category to what it is, and added `VttParserTest` coverage
that states what happens to inline word timings. Per-word timing is **not implemented**; that is
now recorded rather than implied.

### A2. Error normalisation was not correct

The matrix row above claims the failure categories were "normalized, budgeted, terminal
categories never retried" and marks it PASS. The scheduler's half of that held — once a category
is known, the budget and the terminal rules work. Normalisation did not: a call deadline before
the response headers surfaced as CANCELLED, and a streamed 429 or 5xx surfaced as PROTOCOL. The
probes `reviewDeadlineBeforeHeadersMustBeTimeoutNotCancelled`, `reviewStreaming429KeepsRetryableCategory`
and `reviewStreamingOverloadRemainsRetryable` reproduced all three against this tree, and tasks 4
and 5 fixed them. Recorded in `M07-M09-correction-phase3-report.md`; the M08 report's correction
section carries the same defect from the transport's side.

### B. Section B: the work map's bound was not the cache's bound

The section says keeping cache-resident records "ties the map's bound directly to the cache's
bound". Two holes made that false, and both were reproduced red in task 3:

- A `SUCCEEDED` record whose result had been evicted kept saying `SUCCEEDED`, so returning to that
  cue never translated it again and the viewer kept the source line.
- The displayed-cue path has no timeline: `dispatch` returns before pruning and `start < 0` was
  skipped by the prune, so a thousand distinct cues left a thousand records.

The full ceiling, now stated in the scheduler's own javadoc, is the cache's entry limit plus the
units still inside the window plus whatever the throttle interval added since the last prune plus
the one request in flight — deliberately not "512 records", and deliberately not "the whole work
map". The no-timeline branch is bounded by its own rule, and an evicted result is re-requested.

**2 MiB is the translation payload bound, not a memory statement about the app.** It caps stored
UTF-8 translation text; nothing in this report measured JVM, Android or device memory, and the
performance and memory sampling rows above remain `NOT RUN` / `PENDING DEVICE`.

### C. The credential is not only in the encrypted store

The claim is corrected: `AndroidSecretStore` uses AES-256-GCM with a non-exportable
AndroidKeyStore key on **API 23+**, and an explicitly documented **app-private plaintext**
compatibility path on **API 17–22**. The plaintext fallback is the policy ADR-012 already
approved for those levels, so the description was wrong, not the storage strategy: the strategy
was not redesigned, and no file moved.

### D. The upstream numbers were carried over, not measured

`upstream-patches.md` now carries the recomputed table: `10d6a18cf` is 184 files, +29 324 / -1
(the report said 180 and +28 073 / -1), and the correction candidate `0fef49ade` is 190 files,
+33 012 / -1. The host-patch surface was re-verified file by file for this run — five Java files
(+43/+2/+6/+35-1/+4), `ids.xml +1`, `common/build.gradle +1` — with no rename, no mode change,
no whole-file reformatting, unchanged submodule pointers and no ExoPlayer file touched.

### F. The file inventory

The section claimed the per-milestone file lists were in the M07 and M08 reports. They were not.
The appendix below carries the name-status list for each of the three milestone ranges, generated
from the commits rather than described.

## Appendix — per-milestone file inventory

Generated with `git diff --name-status <from> <to>` for each milestone range, using the commit that ends each milestone as the next range's start. `A` = added, `M` = modified, `D` = deleted, `R` = renamed. The M07 range starts at the last commit before the M07 work, so it does not reach back into M05/M06.

### M07 range, `76eb2511f..468d77839` — 35 files changed, 4394 insertions(+), 494 deletions(-)

```
M	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/integration/AiSubtitleController.java
M	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/integration/AiSubtitleCueBridge.java
M	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/integration/AiSubtitleRuntime.java
M	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/prompt/PromptRenderer.java
M	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/provider/AnthropicMessagesAdapter.java
M	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/provider/OpenAiChatCompletionsAdapter.java
A	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/scheduler/TranslationScheduler.java
M	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/segmentation/RuleSentenceBreaker.java
M	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/AiSubtitleData.java
M	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/remote/AiSubtitlePhoneInputServer.java
M	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/ui/AiSubtitleSettingsPresenter.java
M	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/ui/ProviderProfilesPresenter.java
A	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/source/SmartTubeSubtitleSourceAdapter.java
A	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/source/SourceTimeline.java
A	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/source/VttParser.java
M	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/translation/TranslationRequest.java
M	common/src/main/res/values-zh-rTW/ai_subtitle_strings.xml
M	common/src/main/res/values-zh/ai_subtitle_strings.xml
M	common/src/main/res/values/ai_subtitle_strings.xml
M	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/integration/AiSubtitleControllerTest.java
M	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/integration/AiSubtitleCueBridgeCacheTest.java
M	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/integration/AiSubtitleCueBridgeModeTest.java
M	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/integration/AiSubtitleCueBridgeSessionTest.java
M	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/integration/AiSubtitleCueBridgeTest.java
M	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/provider/AnthropicMessagesAdapterTest.java
M	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/provider/OpenAiChatCompletionsAdapterTest.java
A	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/scheduler/TranslationSchedulerTest.java
A	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/remote/AiSubtitlePhoneInputServerTest.java
A	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/source/SmartTubeSubtitleSourceAdapterTest.java
A	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/source/VttParserTest.java
M	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/translation/FakeTranslationProviderTest.java
M	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/translation/TranslationRequestTest.java
M	docs/ai-subtitle/progress.md
M	docs/ai-subtitle/tv-usability-repair-progress.md
A	docs/ai-subtitle/worker-plans/M07-plan.md
```

### M08 range, `468d77839..a6d6e6943` — 35 files changed, 3507 insertions(+), 117 deletions(-)

```
M	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/integration/AiSubtitleController.java
M	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/integration/AiSubtitleCueBridge.java
M	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/prompt/BuiltInSubtitlePrompts.java
M	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/provider/AnthropicMessagesAdapter.java
M	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/provider/OpenAiChatCompletionsAdapter.java
M	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/provider/http/HttpRequestExecutor.java
M	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/provider/http/OkHttpRequestExecutor.java
A	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/provider/http/SseEventReader.java
M	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/scheduler/TranslationScheduler.java
M	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/AiSubtitleData.java
M	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/remote/AiSubtitlePhoneInputServer.java
M	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/ui/AiSubtitleSettingsPresenter.java
A	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/translation/TranslationContextBuilder.java
M	common/src/main/res/values-zh-rTW/ai_subtitle_strings.xml
M	common/src/main/res/values-zh/ai_subtitle_strings.xml
M	common/src/main/res/values/ai_subtitle_strings.xml
M	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/integration/AiSubtitleControllerTest.java
M	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/integration/AiSubtitleCueBridgeCacheTest.java
M	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/integration/AiSubtitleCueBridgeSessionTest.java
M	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/integration/AiSubtitleCueBridgeTest.java
M	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/provider/AnthropicMessagesAdapterTest.java
M	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/provider/OpenAiChatCompletionsAdapterTest.java
A	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/provider/http/OkHttpStreamingTest.java
A	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/provider/http/SseEventReaderTest.java
M	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/scheduler/TranslationSchedulerTest.java
M	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/remote/AiSubtitlePhoneInputServerTest.java
A	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/translation/TranslationContextBuilderTest.java
M	docs/ai-subtitle/progress.md
A	docs/ai-subtitle/research/2026-09-14-m08-reuse.md
A	docs/ai-subtitle/worker-plans/M07-M09-continuation-plan.md
M	docs/ai-subtitle/worker-plans/M07-plan.md
A	docs/ai-subtitle/worker-plans/M08-plan.md
A	docs/ai-subtitle/worker-plans/M09-plan.md
A	docs/ai-subtitle/worker-reports/M07-report.md
A	docs/ai-subtitle/worker-reports/M08-report.md
```

### M09 range, `a6d6e6943..2f98945e3` — 11 files changed, 980 insertions(+), 24 deletions(-)

```
M	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/cache/InMemoryTranslationCache.java
M	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/cache/TranslationCache.java
M	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/integration/AiSubtitleController.java
M	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/scheduler/TranslationScheduler.java
M	common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/source/SmartTubeSubtitleSourceAdapter.java
M	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/cache/InMemoryTranslationCacheTest.java
A	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/provider/ProviderAuditTest.java
A	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/scheduler/TranslationSchedulerCapacityTest.java
M	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/source/SubtitleFixture.java
A	common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/source/SubtitleFixturePipelineTest.java
M	common/src/test/resources/ai-subtitle/fixtures/independent-cases.json
```
