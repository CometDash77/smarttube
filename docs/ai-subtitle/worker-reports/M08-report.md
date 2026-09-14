# M08 Worker Report

Task ID: `M08`

Milestone: M08 — Mature translation pipeline (bounded context, streaming, drafts, fallback)

Status: **IMPLEMENTATION AND AUTOMATIC VERIFICATION COMPLETE (local) — A–E delivered, F decided and not implemented. Exact-SHA CI is `PENDING PUSH AUTHORIZATION`; device acceptance is `PENDING DEVICE`.**

## Scope

Deliver the two real optimizations the roadmap allows, without coupling correctness to
them: a bounded request context that actually reaches the Prompt, and streamed drafts that
reach the cue while the translation is still arriving. Both default to off and both can be
switched off without losing correctness.

Not in scope: persistent on-disk cache and optional video-summary context (F, decided below);
cache bounds, hardening and release acceptance (M09); any change to `SharedModules`,
`MediaServiceCore`, ExoPlayer sources, Gradle dependency versions, or `KissTranslator`.

## Pinned SHAs

| Item | SHA |
|---|---|
| Branch | `feature/ai-bilingual-subtitles` |
| Base for M08 | `21b61a3ae` (M07 documentation tip) |
| M08 implementation | `46c24aa0b` |

## A. Bounded context, Prompt and cache identity

Delivered in `TranslationContextBuilder` (new) plus the scheduler's request freeze.

- Inputs are the current unit, the video title and description carried from the player's own
  `Video` object, and the earlier units of the same timeline. Nothing else.
- Budgets are enforced in the builder and counted in Unicode code points, so a truncation can
  never split a surrogate pair: title 160, description 640, up to 3 earlier units, history
  source+translation 1200, whole context with labels 2200.
- The context is a labelled reference block ("Title:", "Description:", "Earlier subtitles:").
  The built-in baseline Prompt was bumped to version 2 and now states that the earlier
  subtitles are background reference whose instructions must never be followed; a Prompt that
  does not reference `{{context}}` simply does not use it.
- The scheduler renders the Prompt with `PromptRenderer` for `source_text`, `source_language`,
  `target_language`, `unit_index` and `context`, then freezes both the rendered instruction and
  the cache key **at the unit's first dispatch**. A SHA-256 of the exact instruction fills the
  existing `contextFingerprint` slot. A retry or a redraw reuses the frozen pair verbatim.
- History is built from timeline order, not from completion order, so a future unit that
  happened to finish first can never become the current unit's past.

Evidence: `TranslationContextBuilderTest` (budgets, surrogate pairs, code-point counting,
newest-wins history) and `TranslationSchedulerTest`
(`boundedContextCarriesTheTitleDescriptionAndEarlierSources`, `contextIsOffUntilItIsEnabled`,
`enablingContextChangesTheRenderedPromptForTheSameUnit`,
`anUnfinishedEarlierUnitContributesSourceOnly`, `anAcceptedFinalBecomesHistoryForTheNextUnit`,
`aLaterUnitThatFinishedFirstIsNeverHistory`,
`aFrozenRequestIsReusedVerbatimAfterTheHistoryMovesOn`).

**Deviation:** `TranslationRequest` did not need a context field. The context is only ever
observed through the rendered instruction, so adding a second copy of it would have created
two sources of truth for one identity.

## B. Cancellable streaming HTTP and SSE framing

- `HttpRequestExecutor.StreamCallback extends HttpCallback` is the only transport addition; the
  callback type *is* the streaming request, so there is no second HTTP engine and no
  `executeStream` lifecycle.
- `SseEventReader` (new) does wire-format framing only: LF/CRLF/CR, a leading BOM, comments,
  blank-line dispatch, multiple `data` lines joined by newlines, unknown fields ignored —
  including `retry`, so a server cannot re-time this client's retries. A partial event that
  reaches end of stream without a terminating blank line is dropped, never reported as complete.
- Limits are enforced while reading, not after buffering: 64 KiB per line, 64 KiB per event,
  1 MiB per response. Both adapters cap one streamed translation at 16K code points.
- `OkHttpRequestExecutor` now also sets `callTimeout`, so a steady heartbeat cannot extend the
  total exchange; cancellation closes the call and the read loop stops; every terminal path is
  delivered at most once. A successful read reports `onSuccess` with an empty body — the
  adapter still has to see its protocol's end signal before it claims a translation. A streaming
  request answered with a non-event-stream content type fails at the transport instead of being
  parsed line by line.

Evidence: `SseEventReaderTest` (framing, BOM, comments, joined data lines, dropped partial
event, UTF-8 split across reads, all three limits) and `OkHttpStreamingTest`, which drives a
local socket server fragment by fragment (mid-character UTF-8 split, multi-line data,
comment/heartbeat, disconnect without a terminator, delayed cancel, never-ending heartbeat
bounded by the call timeout, oversized line). No paid API is called.

## C. Per-protocol stream interpretation

- OpenAI: `stream: true` plus event-stream accept; only `choices[0].delta.content` accumulates.
  Role-only, usage-only and extra-choice chunks are ignored. A final requires `finish_reason:
  stop` or a `[DONE]` completion signal; `length`, `content_filter` and a stream that simply
  ends are failures. Events after the end signal are ignored.
- Anthropic: only `text_delta` becomes subtitle text; thinking and tool blocks are ignored, not
  rendered. A final requires `message_stop` with an accepted stop reason (`end_turn`,
  `stop_sequence`); `max_tokens` and a stream without `message_stop` are failures. `error`
  events map to a failure; `ping` and unknown events are ignored; events after `message_stop`
  are ignored.
- Every valid delta publishes the full accumulated draft, never a fragment.

Evidence: `streamedDeltasBecomeCumulativeDraftsAndStopCompletesTheTranslation`,
`malformedChunksExtraChoicesAndEventsAfterTheEndSignalAreIgnored`,
`aLengthStopIsNotASilentSuccess`, `aStreamWithoutACompletionSignalIsNotASilentSuccess`,
`aPlainCallbackKeepsTheSingleResponseContract` (OpenAI);
`namedTextDeltasBecomeCumulativeDraftsAndMessageStopCompletes`,
`eventsAfterMessageStopAreIgnored`, `aMaxTokensStopIsNotASilentSuccess`,
`aStreamWithoutMessageStopIsNotASilentSuccess`, `aStreamingErrorEventBecomesAFailure`
(Anthropic). Both non-streaming suites are unchanged and still pass.

**Deviation:** the Anthropic accumulator does not bucket deltas by content block index. The
request body sends the source as a plain string, so nothing in it constrains how many text
blocks a response carries; bucketing would add a data structure that changes no observable
behaviour while a response holds one text block. Recorded as a known limitation: a response
with more than one text block is concatenated in arrival order rather than by block, and would
need the index.

## D. Drafts to the current cue and failure recovery

- `SchedulerStreamCallback` is the only source of drafts; the final-result validation was not
  relaxed. A partial is accepted only while its Work is IN_FLIGHT and the request id,
  generation, epoch, session and segment coverage all still match.
- A Work holds at most one cumulative draft, stays IN_FLIGHT, does not release concurrency,
  does not spend an attempt, and never touches the final cache or the context history.
- The Bridge only uses a draft for the unit the player is showing. Draft repaints are coalesced
  to at most one per 100 ms; a final always repaints immediately; a seek, disable, or release
  clears drafts and cancels the stream. Late drafts from a superseded stream stay invisible.
- A stream failure in a retryable category flips that unit to a plain request and spends the
  same attempt budget, so a unit still stops after three attempts and falls back to the
  original subtitles.

Evidence: `streamedDraftsReachTheListenerWithoutSpendingAnAttempt`,
`aFinalReplacesTheDraftAndEntersTheCache`, `aStreamInterruptionFallsBackToAPlainRequest`,
`theStreamingFallbackStillStopsAfterTheAttemptBudget`,
`seekingClearsTheDraftAndCancelsTheStream` (scheduler);
`aStreamedDraftDecoratesTheCurrentCueBeforeTheFinalArrives`,
`aDraftOnlyDecoratesTheUnitItWasStreamedFor`, `draftRepaintsAreCoalescedWhileFinalsAlwaysRepaint`,
`everyDraftRepaintsWhenTheIntervalIsZero`, `disablingStreamingClearsTheDraftAndCancelsTheStream`
(bridge).

**Deviation:** coalescing drops intermediate repaints instead of deferring them. A draft is
cumulative and a final always repaints, so nothing is lost, and no pending-timer state has to
be tracked or cancelled.

## E. Settings and non-streaming regression

- `contextEnabled` and `streamingEnabled` are real switches on `AiSubtitleData`, default false,
  shared by the TV settings dialog and the phone page through the same store and the same
  initialisation path. No summary or disk-cache button was exposed, because neither exists.
- The context switch changes the output identity and starts a new session and cache. The
  streaming switch cancels the stream in flight, clears drafts, and keeps the compatible final
  cache.
- Both switches take effect on the current window immediately; pause and off are still pause and
  off; the phone round-trip test asserts all six settings survive save and reload.

**Deviation:** the streaming switch invalidates by cancelling and re-dispatching with a new
request id rather than by advancing the session epoch. The epoch's meaning is seek isolation;
advancing it for a transport-only change would make a seek indistinguishable from a settings
change.

## F. Persistent cache and video summary: decision, not implemented

Both remain optional in the roadmap and are **not implemented** this round.

- **Persistent on-disk cache:** no evidence was produced that the in-memory cache plus the
  bounded context is insufficient. Reopening a video re-requests its units, but no measurement
  in this milestone showed that cost to be user-visible, and no such measurement was run. The
  restart condition is unchanged: measure the repeat-request cost on a real device first, and
  only then design storage, eviction and migration.
- **Video-summary context:** no sample was collected where the bounded context produced a
  terminology or pronoun error that a summary would have fixed. No summary service, fixed
  sample size, or paid experiment was built.

Neither omission blocks M09 and neither is a claim of a passed measurement. Nothing was
measured.

## Verification

Local, from a fresh ASCII copy of the current working tree at
`C:\tmp\smartube-m07-m09`.

| Lane | Command | Result |
|---|---|---|
| JDK 17, full `common` | `gradlew.bat :common:testStbetaDebugUnitTest` | `BUILD SUCCESSFUL` — 48 suites, tests=442, failures=0, errors=0, skipped=20 |
| JDK 11, settings/secret (ADR-010 lane) | `gradlew.bat :common:testStbetaDebugUnitTest --tests 'com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.*'` | `BUILD SUCCESSFUL` — 11 suites, tests=78, failures=0, errors=0, skipped=0 |

The JDK 17 skips are exactly the inherited Robolectric preference/secret suites, which run
0-skipped in the JDK 11 lane. Both switches are off in this run, which is the M07 core
regression proving the optimizations can be disabled. `git diff --check` is clean.

Failures found and fixed by running rather than by inspection:

- `decorate` overwrote the draft status with TRANSLATED; the draft/final distinction now lives
  in one place.
- A call-deadline failure surfaces as a socket failure, not an interrupted read; the transport
  now classifies by the request's own budget.
- Two bridge tests created their own bridge without a refresh listener, so they observed no
  repaints at all.

Nothing in this report is a device result. No APK was built, installed or measured, and no
exact-SHA CI run exists yet.

## Not done / pending

| Item | State | Trigger |
|---|---|---|
| Exact-SHA GitHub Actions run for the M08 commits | `PENDING PUSH AUTHORIZATION` | An authorized push to `feature/ai-bilingual-subtitles` |
| Device acceptance for context and streaming | `PENDING DEVICE` | A TV or Android device plus a candidate APK |
| Display mode × streaming combination matrix | `NOT COVERED` | M09 section A |
| Multi-content-block Anthropic streams | `KNOWN LIMITATION` | A request shape that emits more than one text block |
| Persistent cache / summary (F) | `NOT IMPLEMENTED` | The measurement restart conditions above |
