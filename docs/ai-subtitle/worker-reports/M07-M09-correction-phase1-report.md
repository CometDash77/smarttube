# M07–M09 Correction — Phase 1 Report (Tasks 1–2)

Task IDs: `M07`/`M08`/`M09` correction plan, tasks 1 and 2 (first of five commit units).

Plan: `review-plans/M07-M09-code-correction-plan.md`, SHA-256
`a13a5687ce01ffe7b06504376e13a097e6b6e5776926fa02dd6813684fb130a7`.

Base for this phase: `a22e525df` (plus `28d8311e0`, the execution-contract commit).
Branch: `feature/ai-bilingual-subtitles`.

## Scope

Task 1 — lifecycle and source-timeline isolation (C1, C2).
Task 2 — phone/TV settings actually reaching the live playback (C3, C4).

## Defects closed

| ID | Closed by |
|---|---|
| C1 | The bridge discards the previous source's timeline on a real video/track change and on subtitles-off, and invalidates the in-flight load sequence, so a pending new source can never answer from the old track's units. |
| C2 | Playback pause is now a fact on the bridge (`mPlaybackPaused`) that outlives a session and survives the feature being switched off. Every session is created paused when playback is paused, and `onPlay`/`onPause`/`onSeek`/`onSeekDrag` no longer build a runnable scheduler for a disabled feature or a missing track. |
| C3 | `onContextEnabledChanged` rebuilds the live session instead of short-circuiting on an unchanged session identity. `AiSubtitleRuntime.applyToBridge` is now the single entry that applies the resolved provider/prompt plus every stored playback setting, and the phone `saveDraft` calls it once instead of writing storage and applying only the scheduling limits. |
| C4 | A segmentation change now reconfigures the adapter already serving the current track before reloading, and `SmartTubeSubtitleSourceAdapter.load` reads the three limits once as a snapshot, so a load is cut by the limits it started under. |

## Counterexamples turned into formal tests

Each probe from the audit's `add-review-probes.ps1` was run first against the unmodified
production code and then kept as a permanent test under a descriptive name. The failure text
before the fix is the evidence that the defect was real.

| Audit probe | Formal test | Red evidence before the fix |
|---|---|---|
| `reviewSourceReplacementDoesNotUseOldTimeline` | `aPendingSourceReplacementNeverDispatchesTheOldTrack` | `pending new source must not dispatch old track expected:<1> but was:<2>` |
| `reviewDisabledFeatureDoesNotRestartOnPlay` | `playbackResumeWhileDisabledStartsNoWork` | `AI off must not translate on play expected:<1> but was:<2>` |
| `reviewProfileChangeKeepsPlaybackPaused` | `aProfileChangeKeepsPausedPlaybackPaused` | `a configuration change must not resume playback work expected:<PAUSED> but was:<ACTIVE>` |
| `reviewContextToggleRecreatesTheLiveSession` | `contextToggleRecreatesTheLiveSessionAndChangesWhatIsSent` (strengthened to assert the rendered prompt, not the generation) | `context toggle must replace the active scheduler` |
| `reviewSegmentationUpdatesTheExistingAdapter` | `segmentationChangeUpdatesTheExistingAdapter` | `arrays first differed at element [0]; expected:<100> but was:<60>` |

The strengthened context test asserts the actual request: with the context off the rendered
prompt carries no video title, after the toggle the next request does, and toggling it off again
removes it.

## Additional tests added

Behaviour matrix on the bridge: a late source load for the previous video is discarded; a late
load for an abandoned track in an A→B→A sequence is discarded while the newest load wins; a tick
after subtitles are switched off starts no work; playback events while the feature is off start
no work; a tick after release starts no work; a provider change while paused keeps playback
paused and dispatches nothing; re-segmentation while paused dispatches nothing.

Controller: 50 drags dispatch exactly one seek, carrying the final position (closes the M09
`50-drag case` PARTIAL).

Adapter: two loads started under different segmentation limits answer out of order and each
keeps the limits it started with.

## Verification evidence

Fresh ASCII copy `C:\tmp\smartube-code-fix` (excludes `.git`, `.gradle`, every `build`).

| Lane | Command | suites | tests | failures | errors | skipped | result |
|---|---|---:|---:|---:|---:|---:|---|
| JDK 17 full | `:common:testStbetaDebugUnitTest` | 51 | 475 | 0 | 0 | 21 | `BUILD SUCCESSFUL in 1m` |
| JDK 11 settings | `:common:testStbetaDebugUnitTest --tests '….ai.subtitle.settings.*'` | 11 | 79 | 0 | 0 | 0 | `BUILD SUCCESSFUL in 1m 9s` |

Baseline for comparison (audit, at `10d6a18cf`): JDK 17 51/460/0/0/20 and JDK 11 11/78/0/0/0.
The 15 added tests are 12 in `AiSubtitleCueBridgeSessionTest`, one in `AiSubtitleControllerTest`,
one in `SmartTubeSubtitleSourceAdapterTest` and one in `AiSubtitlePhoneInputServerTest`.

The JDK 17 skip count moved from 20 to 21 for a single reason: the new phone test joins the ten
`AiSubtitlePhoneInputServerTest` methods that `JdkAwareRobolectricRunner` skips above JDK 16, and
it really runs in the JDK 11 lane, which is the lane that asserts 0 skipped. Raw XML is kept at
`C:\tmp\ev-jdk17` and `C:\tmp\ev-jdk11`.

## Known gap (PARTIAL)

The plan's task 2 asks the JDK 11 phone test to observe the live bridge's actual request: the
rendered/`system` body and the transport mode the provider was driven in. That was attempted and
**is not proven**; the delivered test asserts the wiring up to the point the lane can currently
reach, and the gap is recorded rather than papered over.

What was established, with the evidence:

- Real HTTP from this lane works: a direct `OkHttpRequestExecutor` POST to a local `ServerSocket`
  arrives and its body is captured.
- After the phone save the live singleton is the same object the save applied to, the saved
  profile is the selected one resolution reads, the credential survives a `keep` save, and the
  saved configuration still resolves to a runnable provider.
- With the bridge instrumented, `findOrRequest` reaches its dispatch branch with
  `session=true provider=true prompt=true scheduler=true enabled=true`, i.e. the bridge is fully
  configured — but neither a request nor a failure is ever observed: the runtime status stays
  `TRANSLATING` with an empty error for 20 s, and the local endpoint records no connection.

So the request is neither delivered nor reported as failed inside this Robolectric lane. Nothing
suggests a product defect: the bridge-level behaviour is covered by the integration tests above,
which do observe the requests. The open question is why the resolved provider never reaches the
socket under Robolectric, and it is recorded here for a follow-up rather than guessed at.

## Unchanged / not attempted

No push, tag, release, artifact upload or paid API call. No changes to KissTranslator,
SharedModules, MediaServiceCore, ExoPlayer or dependency versions. No upstream host files were
touched. Device acceptance remains with the user (`PENDING DEVICE`).
