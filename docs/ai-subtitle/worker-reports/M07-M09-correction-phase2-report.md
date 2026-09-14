# M07–M09 Correction — Phase 2 Report (Task 3)

Task ID: `M07`/`M08`/`M09` correction plan, task 3 (second of five commit units) —
caching eviction, window and attempt-budget closure (C8, C9, C11).

Plan: `review-plans/M07-M09-code-correction-plan.md`, SHA-256
`a13a5687ce01ffe7b06504376e13a097e6b6e5776926fa02dd6813684fb130a7`.

Base for this phase: `321fd90b7` (phase 1 review fixes).
Branch: `feature/ai-bilingual-subtitles`.

## Scope

One production file, as the plan's task header names it:
`common/src/main/java/.../ai/subtitle/scheduler/TranslationScheduler.java`.

`cache/InMemoryTranslationCache.java` was **not** changed. Task 3 permits touching it only if
needed, and it was not needed: the 1-entry eviction used by the counterexample is the real cache
behaving correctly, and the defect was entirely on the scheduler side of the miss. The plan's
Minor item about the cache's now-unreachable "single oversized entry" branch is explicitly
optional and is not a substitute for this task; it is left for the documentation pass.

## Defects closed

| ID | Closed by |
|---|---|
| C8 | A work record that finished translating but whose result has since been evicted is reset to PENDING at the shared work lookup, with the attempt budget reopened and the terminated call released, so the current unit is translated again instead of staying source-only. The no-timeline display-cue fallback is now pruned at the real request entry, so a sequence of distinct cues no longer accumulates one record per cue. |
| C9 | Due retry and manual retry now share one window predicate — a unit that covers the playhead, or one starting at/after the playhead and no later than the window end. The attempt ceiling is enforced once at the shared submit entry, so no dispatch path (window fill, redraw, resume, due retry, streaming toggle) can issue a fourth request. Fallback accounting counts only the single streamed-to-plain conversion. |
| C11 | `historyFor` binary-searches the ordered timeline units by first-segment index and examines at most the three immediately preceding units, so building a context no longer walks the whole unit list or performs one cache read per earlier cue. |

## Counterexamples turned into formal tests

Each probe from the audit was run first against the unmodified production code and then kept as a
permanent test under a descriptive name. The failure text before the fix is the evidence that the
defect was real.

| Audit probe | Formal test | Red evidence before the fix |
|---|---|---|
| `reviewEvictedCurrentTranslationCanBeRequestedAgain` | `anEvictedCurrentTranslationIsRequestedAgain` | `eviction cannot leave SUCCEEDED permanently source-only` |
| `reviewNoTimelineFallbackWorkIsBounded` | `noTimelineFallbackWorkStaysBounded` | `fallback work also needs a bound; actual=514` |

The first red message is byte-identical to the audit's recorded failure. The second differs only
in the number: the audit asserted once after 1000 cues and saw `actual=1000`, while the permanent
test asserts **at every step**, so it stops at the first step that exceeds the bound (514) rather
than at the end. The defect measured is the same one — the work map grew with the number of cues.

Red run, for the record: JDK 17, `--tests '….ai.subtitle.scheduler.*'` → 2 suites, tests=42,
failures=2, errors=0. Raw XML at `C:\tmp\ev-phase2-red`.

## Additional tests added

Nine more, so the phase adds eleven tests in total.

`TranslationSchedulerCapacityTest` (5 new):

- `noTimelineFallbackStaysBoundedAcrossFailures` — the same per-step bound with alternating
  terminal failures, so it is not only a run of successes that has to stay bounded.
- `continuousSuccessAndFailureWindowsStayBounded` — the audit's already-passing 2000-step
  no-seek case, kept as a regression (lookahead 0, throttle 30 s, all-success and all-AUTH, work
  ≤ 574 and cache ≤ 512 at every step).
- `cancellingAndResumingNeverExceedsTheAttemptBudget` — six pause/resume cycles issue exactly
  three requests and then stop.

`TranslationSchedulerTest` (6 new):

- `buildingTheContextReadsOnlyTheImmediatePredecessors` — a cache decorator counts `get` calls;
  at the end of a 60-cue timeline the context build costs exactly 4 reads (one cache-hit check
  plus three history candidates). The previous implementation walked every unit in the timeline,
  so by construction the same request would have performed 59 history reads plus that one check.
  The rendered body contains cues 57 and 58 and not cue 10. This is an access assertion, not an
  elapsed-time threshold; the 4 is measured, the 59 is read off the old loop rather than run.
- `aFailureBehindThePlayheadIsNotRetried` — a failed cue the playhead has passed, still inside
  the 30 s history margin and therefore still reachable, stays FAILED and costs no request.
- `aLongUnitCrossingThePlayheadCanBeRetriedManually` — the guard against an over-eager lower
  bound: a single cue spanning two minutes is still retriable while the playhead is inside it.
- `aStreamFallbackIsCountedOnceAndLaterRetriesAreOrdinaryRetries` — the plan's worked example:
  stream NETWORK → plain SERVER → plain success gives 3 requests, first=1, fallback=1, retry=1.
- `closeReleasesTheWorkRecords` — after `close()` the scheduler reports zero work records and no
  longer resolves a translation it had cached.
- `lateDeliveriesAfterCloseCannotDispatchOrRepaint` — a late partial, final and error after
  `close()` produce no request, no draft notification, no arrival and no failure notification.

## Documented bound (plan bullet 4)

`pruneWorkLocked`'s javadoc now states the ceiling the plan asks for: the cache's entry limit,
plus the units still inside the window, plus whatever the throttle interval added since the last
prune, plus the one request in flight. It explicitly says this is *not* "512 work records", so a
later reader cannot mistake the map for the cache.

## Documentation corrected (plan bullet 6)

`onStreamingChanged`'s javadoc claimed a cancelled in-flight request is "re-dispatched under the
new mode **without spending a new attempt**". That was false in both halves: the cancelled request
had already reached the provider, and the re-dispatch spends another. The javadoc now says the
cancelled request counts against the budget exactly like any issued request and the re-dispatch
spends one more, because the budget — not the toggle — is what bounds the network. This is the
plan's instruction to make the documentation match the real network count.

## Verification evidence

Fresh ASCII copy `C:\tmp\smartube-code-fix` (excludes `.git`, `.gradle`, every `build`). The
results directory is deleted before each lane so a compile failure cannot leave stale XML behind,
and each lane's XML is archived before the next one overwrites the same directory.

| Lane | Command | suites | tests | failures | errors | skipped | result |
|---|---|---:|---:|---:|---:|---:|---|
| JDK 17 full | `:common:testStbetaDebugUnitTest` | 51 | 486 | 0 | 0 | 21 | `BUILD SUCCESSFUL in 1m 6s` |
| JDK 11 settings | `:common:testStbetaDebugUnitTest --tests '….ai.subtitle.settings.*'` | 11 | 79 | 0 | 0 | 0 | `BUILD SUCCESSFUL in 1m 24s` |

Phase 1 was 475/21 and 79/0; the JDK 17 total moves by exactly the eleven tests added here, and
the JDK 11 settings lane is unchanged because no settings test was added. The JDK 17 skip count
stays at 21: the 21 skips are still only `AiSubtitleDataTest` (6), `AndroidSecretStoreRobolectricTest`
(4) and `AiSubtitlePhoneInputServerTest` (11), all of which really run in the JDK 11 lane, which
reports 0 skipped. Raw XML is kept at `C:\tmp\ev-phase2-jdk17` and `C:\tmp\ev-phase2-jdk11`.

## Review gates

Two subagents reviewed the phase diff on the Standards and Spec axes; neither ran tests or built
anything, and no review verdict is quoted as a test result. No finding was Blocking. Every
finding below is either fixed in the tree that was re-verified, or recorded with the reason it
was not.

### Standards axis

Fixed:

- *Dead branch in the budget guard.* `submitLocked` opens by returning unless `mActive == null`,
  so the `if (mActive == work) mActive = null;` copied from the completion paths was unreachable.
  Deleted; the guard now states only what can happen.
- *The new helper was not used by the code it was written for.* `isResultStillCached` was
  introduced by this phase, and both prunes still spelled the expression out inline. Both sites
  now call it, and its javadoc no longer implies it only applies to a finished record.
- *`pruneUntimedWorkLocked`'s javadoc overclaimed its scope.* It said only the requested cue, the
  in-flight request and cached results survive, but it also skips every record that has a start
  time — and it runs on the normal path too, not only the fallback. The sentence is now scoped to
  untimed records and points at `pruneWorkLocked` for the rest.
- *`setStreamingEnabled`'s javadoc described the mechanism backwards.* It said a request is
  streamed when its callback can consume drafts; the callback is chosen *by* the streaming
  decision. It now states the actual rule (`streaming enabled`, and the unit has not already
  fallen back from a transient streamed failure).
- *`retryFailed`'s javadoc still said "inside the current window".* That stopped being true the
  moment the predicate was widened to include a cue covering the playhead and an untimed cue. The
  summary now matches the predicate and defers the detail to `isInRetryWindow`.
- *The new `close()` comment asserted more than the code guaranteed.* `drainEvents` copied and
  cleared `mPendingEvents` outside the monitor, so a concurrent drain could race `close()` — and
  could race a callback's queued event — meaning "no queued notification survives" was not
  actually guaranteed. `drainEvents` now copies and clears under the monitor and drops the drain
  entirely once closed, and the comment says exactly that: a drain that starts after `close()`
  delivers nothing, while one already past its copy can still deliver its snapshot. That residual
  window is stated rather than claimed away.

Accepted as recommended (no change):

- *Do not merge the two prunes.* They share only the collect-then-remove skeleton and the two keep
  conditions; the decisions differ, they run on disjoint populations (`start < 0` and
  `start >= 0` are each other's `continue`), and sharing the loop under API 17 would cost a
  filtering interface and an anonymous class. The one thing genuinely worth sharing — the
  cache-resident test — was shared, above.

The axis also confirmed, by reading the code rather than running it: no new public or
package-private surface, no naming collision between `mFallbackPending` (one-shot) and
`mStreamingDisabled` (sticky), no API-17-incompatible construct, every new cache read and work-map
iteration inside the monitor, and the rewritten `onStreamingChanged`, `workFor` and
`pruneWorkLocked` comments accurate against the code.

### Spec axis

Every task-3 bullet was found implemented in the owner the plan names; two were partial and are
now closed, and one is recorded rather than fixed.

Fixed:

- *Bullet 10, the access assertion recorded counts but not keys.* The plan asks the test cache to
  record the keys read. `RecordingGetCache` now records them and the test asserts four reads across
  four **distinct** keys, so an implementation that read one key four times is no longer invisible
  to it.
- *Bullet 10, the "no future" half was only asserted elsewhere.* The test drove to the last cue,
  where no future cue exists to assert about. It now stops at cue 30 of 60 and asserts that the
  body quotes neither a far-past cue (`cue 10`) nor a future one (`cue 40`), alongside the three
  predecessors it must quote.
- *The binary-search ordering invariant was load-bearing and undocumented.* Both axes raised it;
  it is the strongest of the findings because an out-of-order unit list would silently resolve the
  wrong neighbours. The contract is now stated on `SourceTimeline.getUnits()`, where the input type
  lives. This is a documentation-only edit to a file the plan did not name for task 3 — see the
  deviation note below.

Recorded rather than changed:

- *The attempt budget is not absolutely unbreakable across a seek.* `mAttempts` lives on the work
  record, and the window prune the plan mandates in bullet 4 drops out-of-window records that are
  not cache-resident. A unit left PENDING with three spent attempts by cancels can therefore be
  dropped by a forward seek beyond the margin and re-created with a fresh budget when playback
  returns. Bullet 6's enumerated paths (ordinary failure, pause-cancel, streaming toggle) are all
  correctly capped, and the two bullets genuinely pull in opposite directions: keeping the record
  to preserve the budget is exactly the unbounded growth bullet 4 exists to prevent. Resolved in
  favour of the bound and recorded here. Restart condition, if it ever matters: a tombstone for
  exhausted records would have to expire, so it buys bounded extra work rather than none.

Also recorded, not acted on:

- Two *individual* assertions hold before the fix as well as after it, while the tests around them
  discriminate: the closing `assertEquals(before, provider.getCallCount())` in
  `aFailureBehindThePlayheadIsNotRetried` (the discriminator is the `hasFailed` assertion, which
  flips) and the intermediate `assertNull` in `anEvictedCurrentTranslationIsRequestedAgain` (the
  discriminator is the closing `assertNotNull`). Both are load-bearing as regression guards even
  where they do not discriminate, so they stay.

### Deviation recorded for this phase

`SourceTimeline.getUnits()` gained a javadoc paragraph stating the ordering contract that the
plan's own binary-search approach depends on. The plan's task 3 names `TranslationScheduler.java`
(and the cache, only if needed) as the files to modify; this is a documentation-only addition to
`source/SourceTimeline.java`, it changes no behaviour, and it touches no upstream host file. It is
recorded rather than silently taken, because the ordering requirement is what the plan's chosen
algorithm introduced and it belongs next to the data that must satisfy it.

## Unchanged / not attempted

No push, tag, release, artifact upload or paid API call. No changes to KissTranslator,
SharedModules, MediaServiceCore, ExoPlayer or dependency versions. No upstream host files were
touched. Device acceptance remains with the user (`PENDING DEVICE`).
