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
| Chinese, Japanese, English fixtures, manual and ASR | All present and asserted | `theFixtureCoversEveryRequiredLanguageAndCaptionKind` | PASS |
| Word timing, no-space, noise, duplicate, fast, slow, overlap, gap, long | Each behaves as its category promises | `thePipelineKeepsContentAndDropsOnlyWhatItShould` | PASS |
| A source gap | No unit to show; the previous caption is not held over | `aGapInTheSourceHasNoUnitToShow` | PASS |
| Segment/unit time mapping | Contiguous coverage, ordered by time, every segment resolves | `everyUnitCoversContiguousSegmentsInTimelineOrder`, `everySegmentResolvesToAUnitAndEveryUnitToASegment` | PASS |
| 2h+ synthetic timeline (7500 one-second cues), 256 seeks | Work records and cache stay bounded | `aTwoHourTimelineStaysBoundedUnderRepeatedSeeks` | PASS |
| Two units sharing a start time | Both dispatched, neither dropped | `twoUnitsThatShareAStartTimeAreBothDispatched` | PASS |
| Pause → resume, seek, drag, track/video/config change, off/on, reopen | No stale result changes the current state | `pauseStopsNewWorkAndResumeDispatchesImmediately`, `dragKeepsLatestPositionAndSeekEndDispatchesOnlyIt`, `newVideoStartsFreshSessionIdentity`, `trackChangeCreatesNewGenerationWithNewIdentity`, `releaseClosesTheActiveSession`, `seekingClearsTheDraftAndCancelsTheStream` | PASS |
| 100 identical ticks | One logical request | `repeatedPositionTicksDoNotDuplicateRequests` | PASS |
| A later unit finishing first | Never used as current history | `aLaterUnitThatFinishedFirstIsNeverHistory` | PASS |
| AUTH / PROTOCOL / INVALID_OUTPUT / CANCELLED / NETWORK / TIMEOUT, 429, 5xx | Normalized, budgeted, terminal categories never retried | `everyProviderFailureCategoryKeepsSourceOnly`, `terminalCategoriesDoNotRetry`, `rateLimitAndServerUseTheSameRetryBudget`, `timeoutUsesThreeNetworkAttemptsWithBackoff` | PASS |
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
  therefore the complete feature diff (180 files, +28073 / -1).
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

- This report carries the M07→M09 commit list, the modified files per milestone (in the M07 and
  M08 reports), the fixes found by running, the accepted and rejected M08 optional features, the
  test method names and counts, the upstream diff conclusion and the known limitations.
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
| Display mode × streaming combination matrix | `NOT COVERED` | M09 section A follow-up |
