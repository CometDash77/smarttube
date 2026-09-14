# M07–M09 Correction — Phase 4 Report (Task 6)

Task ID: `M07`/`M08`/`M09` correction plan, task 6 (fourth of five commit units) — draft
last-state reachability and immediate repaint on settings changes (C10).

Plan: `review-plans/M07-M09-code-correction-plan.md`, SHA-256
`a13a5687ce01ffe7b06504376e13a097e6b6e5776926fa02dd6813684fb130a7`.

Base for this phase: `551b10a4c` (phase 3: tasks 4–5).
Branch: `feature/ai-bilingual-subtitles`.

## Scope

One production file, as the plan's task header names it:
`integration/AiSubtitleCueBridge.java`. No timer or Handler was added: the position tick
`AiSubtitleController` already runs every `SCHEDULER_TICK_MS = 1_000` is the only clock the
bridge uses, which is why the guarantee is stated as "within the next tick" and not as a
100 ms deadline.

## Defect closed

| ID | Closed by |
|---|---|
| C10 | A repaint coalesced inside the 100 ms window is now remembered and re-sent once by the next position tick, instead of being dropped outright; a stream that goes quiet before its final can no longer leave older draft text on screen. Every settings transition that clears what the cue is showing — stream off in either direction, the feature being switched off, the context toggle, a segmentation change, and a provider or profile change — notifies the existing refresh listener immediately, with the state already cleared. A seek, a drag and a release clear the pending repaint and drop the recorded cue instead (a new cue follows at once, and the player is going away respectively). And the scheduler's "the visible state moved" notification no longer marks a cue TRANSLATED when what moved was a draft cleared for a retry. |

## Counterexample turned into a formal test

The audit probe was run against the pre-fix bridge and then kept as a permanent test under a
descriptive name.

| Audit probe | Formal test | Red evidence before the fix |
|---|---|---|
| `reviewStreamingOffRepaintsWithoutManualProcess` | `clearingTheVisibleDraftRepaintsImmediately` | `clearing the visible draft must repaint expected:<2> but was:<1>` |

Two further probes sharing that run's scenario were run red with it, because they use only
pre-existing API and so could join it:

| Red-run probe | Formal test | Red evidence before the fix |
|---|---|---|
| `reviewSwitchingTheFeatureOffRepaints` | `switchingTheFeatureOffRepaintsBackToTheSourceLine` | `dropping the session removes what the cue was showing expected:<2> but was:<1>` |
| `reviewARetryableFailureIsNotTranslated` | `aRetryableFailureDoesNotClaimTheCueIsTranslated` | `a retry that is still coming is not a completed translation expected:<TRANSLATING> but was:<TRANSLATED>` |

Those three ran as an isolated JUnit invocation and are archived at `C:\tmp\ev-phase4-red`
(1 suite, tests=3, failures=3).

**Every other new test was also run against the pre-fix tree**, in a second run over the two full
bridge suites with the pre-fix `AiSubtitleCueBridge.java` and the final test files, archived at
`C:\tmp\ev-phase4-red-all`. The result is not uniform, and saying so is the point:

- Five are red pre-fix: the anchor, `turningStreamingOnRepaintsImmediatelyToo`,
  `theNewestDraftIsRepaintedOnTheNextTick`, `switchingTheFeatureOffRepaintsBackToTheSourceLine`
  and `aRetryableFailureDoesNotClaimTheCueIsTranslated`.
- Six are green pre-fix: `aFinalRepaintsImmediatelyAndSupersedesThePendingDraft`,
  `seekingAwayDoesNotResurrectThePreviousDraft` and the four mode combinations. They assert the
  *absence* of a re-send or a draft, which code that never re-sends anything satisfies by
  accident. They are regression guards for the new behaviour, not evidence that the defect was
  real; the five above carry that.
- One cannot run pre-fix at all: `thePendingRepaintWaitsUntilTheIntervalHasElapsed` calls
  `flushPendingDraftRefresh` directly to supply its own clock value, and that method is new.

The first version of this report claimed the other eight tests "drive the interval and the
flush, which do not exist before the fix, so the pre-fix tree does not compile against them".
That was wrong on both counts — the interval setter predates this phase and no test calls the
flush except the one above — and the review gate caught it. The paragraph above replaces it.

## How the re-flush works

- `mDraftRefreshPending` is one flag, set when a repaint is coalesced away. Deltas are still
  never queued, so nothing grows with the number of deltas.
- `flushPendingDraftRefresh(long)` is package-private because a same-package test calls it with
  its own clock value; production reaches it through `onPositionUpdate`, which the controller's
  tick drives. It stays pending while the interval has not elapsed.
- `mLastDraftRefreshMs`, `mDraftRefreshPending` and `mRenderedUnit` are all cleared by
  `cancelSessionState()`, so a session rebuild, the feature being switched off, a release and a
  new video leave nothing stale behind. A seek and a drag clear the pending flag explicitly, and
  a seek also drops `mRenderedUnit` — the renderer has moved on, and until it asks about a cue
  again no unit is on screen for a status of TRANSLATED to be claimed about.
- `mRenderedUnit` is the unit the renderer last asked about. `onSchedulerArrived` claims
  TRANSLATED only when that unit actually has an accepted final; otherwise the status stays as it
  was, which for a retry is TRANSLATING.

## Tests added

Twelve in total.

`AiSubtitleCueBridgeSessionTest` (8): the anchor; the next tick re-sending a coalesced draft
exactly once (`theNewestDraftIsRepaintedOnTheNextTick`, which also asserts a second tick does not
re-send it again); a final superseding the pending draft so the next tick sends nothing;
turning streaming on repainting too; switching the feature off repainting back; a retryable
failure not claiming TRANSLATED and leaving no draft behind; a seek dropping the pending
repaint; and `thePendingRepaintWaitsUntilTheIntervalHasElapsed`, which calls the flush with its
own clock value and thereby exercises the "not due yet, and not forgotten" branch.

`AiSubtitleCueBridgeModeTest` (4): SOURCE with streaming on never requesting a translation and
keeping the source line; TRANSLATION_ONLY showing the draft and then the final; TRANSLATION_ONLY
falling back to the source line when the translation fails; and BILINGUAL in both orders. Each
asserts the exact `cue.text`, and the request count where a request is expected.

Coalescing is verified without sleeping. Most tests switch the interval between a long value (so
the second delta is genuinely coalesced) and zero (so the tick genuinely flushes); the interval
is the knob the plan's "fixed, controllable now values" requirement maps onto when the bridge
reads its own clock. The one place a test supplies a clock value directly is
`thePendingRepaintWaitsUntilTheIntervalHasElapsed`, which passes `System.nanoTime() / 1_000_000`
— the same source the bridge reads, made explicit — so the interval gate is exercised
deterministically rather than raced.

## Documentation corrected

The plan requires the coalescing description to become the real bounded re-flush, and to stop
claiming that dropping an intermediate repaint costs nothing because a final always arrives.
That claim appeared in four places and all four now say what is true:

- `worker-plans/M08-plan.md` — the deviation note.
- `worker-reports/M08-report.md` — both the deviation paragraph and the behaviour summary, with
  the new tests appended to the evidence list.
- `progress.md` — the M08 deviations line.

Why the old claim was wrong, in one sentence, since it is the whole point of the task: a
cumulative draft is only lossless if something paints it again, and the thing that was supposed
to paint it again was a final that a stalled stream may never produce.

## Verification evidence

Fresh ASCII copy `C:\tmp\smartube-code-fix` (excludes `.git`, `.gradle`, every `build`). The
results directory is deleted before each lane, each lane's XML is archived before the next
overwrites it, and the red run is kept separately.

| Lane | Command | suites | tests | failures | errors | skipped | result |
|---|---|---:|---:|---:|---:|---:|---|
| JDK 17 full | `:common:testStbetaDebugUnitTest` | 51 | 518 | 0 | 0 | 21 | `BUILD SUCCESSFUL in 1m 16s` |
| JDK 11 settings | `:common:testStbetaDebugUnitTest --tests '….ai.subtitle.settings.*'` | 11 | 79 | 0 | 0 | 0 | `BUILD SUCCESSFUL in 1m 25s` |

The integration package was also run on its own in both directions
(`--tests '….ai.subtitle.integration.*'`): 91 tests, 0 failures after the fix; 1 failure before
it, which is the anchor.

Phase 3 was 506/21 and 79/0; the JDK 17 total rises by exactly the twelve tests added here and
the JDK 11 lane is unchanged because no settings test was added. The skip count stays at 21,
again only `AiSubtitleDataTest` (6), `AndroidSecretStoreRobolectricTest` (4) and
`AiSubtitlePhoneInputServerTest` (11). Raw XML at `C:\tmp\ev-phase4-jdk17`,
`C:\tmp\ev-phase4-jdk11`, `C:\tmp\ev-phase4-red` and `C:\tmp\ev-phase4-red-all`.

## Review gates

Two subagents reviewed the phase diff on the Standards and Spec axes; neither ran tests or built
anything, and no review verdict is quoted as a test result. No finding was Blocking.

### Fixed

- *Only two of the transitions that clear the visible cue repainted.* Both axes found it
  independently, and it is the substantive one: the plan's bullet says "stream off, AI off **and
  the other transitions that clear the on-screen draft**", and the task is titled "settings
  repaint immediately". `onContextEnabledChanged`, `onSegmentationChanged` and
  `rebindTranslationIdentity` (provider and profile changes) each clear the cache and the
  in-flight request without telling the renderer, so the superseded translation stayed on screen
  until an unrelated cue arrived. All three now notify, after the state is cleared. A seek, a
  drag and a release are deliberately not in that set: a new cue follows a seek at once, the
  player is going away at release, and the plan itself groups them under "clears pending" rather
  than "notifies". The C10 row above previously claimed they notified; it does not now.
- *The stated reason for the three-test red run was false.* The first version claimed the other
  tests could not compile against the pre-fix tree. They can, and the review gate said so. A
  second red run over all of them replaced the guess with the measurement recorded above.
- *`thePendingRepaintWaitsUntilTheIntervalHasElapsed` did not exist*, so the flush's "not due
  yet" branch was untested and no test supplied a clock value of its own, which is what the
  plan's bullet asks for. Added; it also gives the package-private flush a real caller.
- *Two test names promised more than their bodies exercised.*
  `sourceModeShowsTheSourceLineThroughDraftAndFinal` could not produce a draft or a final,
  because SOURCE mode never issues a request; renamed to what it asserts. And
  `aRetryableFailureDoesNotClaimTheCueIsTranslated` never created a draft, so its "no residue"
  half was vacuous; it now enables streaming, emits a partial, and asserts the draft is gone as
  well as the status.
- *A dead accessor the diff added.* `StreamingProvider.getCallCount()` had no caller; it now
  backs a "one request per unit" assertion in the final-supersedes-draft test.
- *Three javadoc inaccuracies.* "One flag per unit is what makes that bounded" — there is one
  flag for the whole bridge, which is the thing that bounds it. The flush's summary said it
  "sends" a repaint when it only decides and updates state. And its closing sentence defended
  against a reading that had no support at the time; it now states what the method is for.
- *`mRenderedUnit` was not cleared on a seek*, so a final for the pre-seek unit could still be
  reported as the current cue's translation until the renderer asked again. Cleared in `onSeek`.
- *The M08 ledger still contradicted the diff in three places.* `M08-plan.md` recorded the
  display-mode × streaming matrix as "not covered", which this phase covers; the M08 report's
  evidence list omitted the four new mode tests; and the retained original sentences in both
  files read as current state even though the correction sentence that follows supersedes them.
  All three are annotated now.

### Recorded rather than changed

- *The coalescing tests control the interval, not a literal clock value.* The plan asks for
  "fixed, controllable `now` values". The interval switch is deterministic and sleepless, and one
  test now supplies its own clock value directly, so the requirement's intent is met two ways;
  the substitution in the other tests is stated above rather than left implicit.
- *Two streaming provider doubles now exist in the same package*
  (`AiSubtitleCueBridgeSessionTest` and `AiSubtitleCueBridgeModeTest`). The codebase already keeps
  one such double per suite — four exist — and the shared `FakeTranslationProvider` deliberately
  covers only the non-streaming contract. Left as is, with the note that a third caller would
  justify extracting one.
- *`mRenderedUnit` can still name a pre-seek unit in one narrower window* the review identified:
  SOURCE mode returns before `findOrRequest`, so a lookahead final can flip the status to
  TRANSLATED while only the source line is shown. The window closes on the next `process`.
  Recorded; clearing the field on a mode change would be more code than the transient status
  implies.

## Unchanged / not attempted

No push, tag, release, artifact upload or paid API call. No changes to KissTranslator,
SharedModules, MediaServiceCore, ExoPlayer or dependency versions. No upstream host files were
touched. Device acceptance remains with the user (`PENDING DEVICE`).
