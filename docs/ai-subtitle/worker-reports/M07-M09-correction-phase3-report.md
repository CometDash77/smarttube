# M07–M09 Correction — Phase 3 Report (Tasks 4–5)

Task IDs: `M07`/`M08`/`M09` correction plan, tasks 4 and 5 (third of five commit units) —
unified real-transport failure classification (C5, C6) and streaming completion plus the real
memory bound (C7).

Plan: `review-plans/M07-M09-code-correction-plan.md`, SHA-256
`a13a5687ce01ffe7b06504376e13a097e6b6e5776926fa02dd6813684fb130a7`.

Base for this phase: `fbeda06e8` (phase 2: task 3).
Branch: `feature/ai-bilingual-subtitles`.

## Scope

Production, exactly the files the plan names:

- `provider/http/HttpRequestExecutor.java` — one added method on the existing `HttpCall`.
- `provider/http/OkHttpRequestExecutor.java` — failure classification.
- `provider/OpenAiChatCompletionsAdapter.java`, `provider/AnthropicMessagesAdapter.java` —
  streaming status judgement, completion rules, termination, and the draft bound.

The plan permits "only the in-feature change necessary to close the existing `HttpCall`
promptly, no new transport interface or SDK". `HttpCall` gained `close()`; nothing else on the
transport boundary changed, and no new type was introduced. Because it is an interface method,
the six test doubles that implement it gained a one-line implementation — four of them in test
classes this phase did not otherwise touch (`ModelCatalogTest`, `ProviderAuditTest`,
`AiSubtitlePhoneInputServerTest`, `ProviderProfilesPresenterTest`). That is a compile
requirement, not scope: no assertion in those files changed.

## Defects closed

| ID | Closed by |
|---|---|
| C5 | User cancel is now judged only by this executor's own flag. A call OkHttp cancelled itself — which is what its call deadline does — is classified as a timeout instead of CANCELLED, and the elapsed-time-minus-250 ms guess is gone. The same judgement is shared by the pre-headers, streamed-read and plain-body-read paths. |
| C6 | Both `streaming.onSuccess` callbacks make the same response/status judgement as the non-streaming path before consulting any stream state, reusing `TranslationFailureMapper`. Anthropic's `error` SSE event reuses `namedErrorCategory`, so `overloaded_error` is SERVER and `rate_limit_error` is RATE_LIMITED rather than a blanket PROTOCOL. |
| C7 | OpenAI's success is a whitelist — a natural `stop`, or a compatible `[DONE]` — so a tool call or an unknown reason can no longer be delivered as a translation. A stream cut before its completion signal is a retryable failure, so the scheduler's one plain fallback actually happens. The draft bound is checked before appending, a clear protocol end delivers immediately and closes the HTTP read, and the terminal state is one one-shot flag on the adapter's existing `Call`. |

## Counterexamples turned into formal tests

Each probe was run against the pre-fix production code and then kept as a permanent test under a
descriptive name. The red run restored the phase-2 sources of the four production files and the
pre-fix test files, appended the four probe bodies verbatim, and ran only those four methods:
3 suites, tests=4, failures=4. Raw XML at `C:\tmp\ev-phase3-red`. Every message is
byte-identical to the audit's recorded failure.

| Audit probe | Formal test | Red evidence before the fix |
|---|---|---|
| `reviewDeadlineBeforeHeadersMustBeTimeoutNotCancelled` | `aDeadlineBeforeTheResponseHeadersIsATimeoutNotACancel` | `expected:<TIMEOUT> but was:<CANCELLED>` |
| `reviewStreaming429KeepsRetryableCategory` | `aStreamingRateLimitKeepsItsRetryableCategory` | `expected:<RATE_LIMITED> but was:<PROTOCOL>` |
| `reviewStreamingOverloadRemainsRetryable` | `anOverloadedStreamingErrorStaysRetryable` | `expected:<SERVER> but was:<PROTOCOL>` |
| `reviewUnknownFinishReasonIsNotSuccessful` | `anUnknownFinishReasonIsNotSuccessful` | `only an accepted completion may succeed expected null, but was:<…text=half, final>` |

## Other tests added

Sixteen more, so the phase adds twenty tests in total.

**OpenAI adapter (7).** Streaming status classification for 401/403/408/429/500/503/504/400; the
draft bound checked by code points including a surrogate pair split across two deltas (16384
completes, 16385 is rejected); a tool-call reason that publishes no further draft; the protocol
end delivering exactly once and closing the HTTP read; and a synchronously delivered outcome
closing the handle when it arrives.

**Anthropic adapter (6).** The same status matrix; the named-error categories for
`rate_limit_error`, `authentication_error`, `api_error` and an unknown type; `message_stop`
delivering exactly once and closing the HTTP read; the same draft-bound cases; and the same
synchronous-termination case.

**Transport (5).** A deadline before the response headers; a plain body that never arrives; a
connection truncated before the deadline; a user cancel before the headers; and `close()`
stopping the exchange without reporting a failure.

**Scheduler closed loop (3).** A real OpenAI adapter over a scripted transport: HTTP 429 on the
streamed attempt costs exactly one plain retry and succeeds; 401 costs exactly one request; a
stream cut after a draft recovers with one plain request that still carries the original
subtitle and leaves no stale draft.

## Tests whose expectation this phase changed

Three existing tests asserted the defect. They were changed, and the reason is recorded here
rather than buried in the diff:

- `aStreamWithoutACompletionSignalIsNotASilentSuccess` (both adapters) asserted PROTOCOL for a
  cut stream. That category is not retryable, which is exactly why no plain fallback ever
  happened. Renamed to `anInterruptedStreamFailsRetryablySoAPlainRequestCanFollow` and now
  asserts NETWORK.
- `aStreamingErrorEventBecomesAFailure` (Anthropic) asserted PROTOCOL for an `overloaded_error`
  event — the C6 defect itself, written down as the expectation. Replaced by
  `anOverloadedStreamingErrorStaysRetryable` (SERVER) plus `streamingErrorsReuseTheNamedCategories`
  for the other named types and the unknown case.

## The M09 repeated-terminal PARTIAL

The M09 report disclosed a PARTIAL for a missing repeated-terminal-callback assertion. It is
closed here rather than in a new test project: both adapter streaming callbacks now count their
terminal deliveries, and `theProtocolEndDeliversOnceAndClosesTheHttpRead` /
`messageStopDeliversOnceAndClosesTheHttpRead` assert that the count does not move when a late
delta, the end of the stream and a late transport failure all arrive after the outcome.

## Documented bound

`MAX_STREAM_CODE_POINTS` is now enforced before the text enters the buffer, with the count kept
incrementally so the check is not quadratic on a long stream. The 1 MiB `SseEventReader` response
cap is unchanged and is not passed off as the draft bound anywhere.

## Documentation corrected

Task 5 forbids the claim that the request forces a single response block: the request body sends
the source as a plain string and constrains nothing about how many text blocks the response
carries. That claim appeared in the M08 plan's deviation note, in `M08-report.md`, and in
`progress.md`'s summary of M08's deviations. All three now say what is true — the deltas are
concatenated in arrival order instead of being grouped by block, a response with more than one
text block is the case that would need the index — and the known-limitation row in the M08
report is unchanged. This is a correction of a current-code claim, not of a historical SHA or a
test number; the task-8 correction section still gets written.

## Verification evidence

Fresh ASCII copy `C:\tmp\smartube-code-fix` (excludes `.git`, `.gradle`, every `build`). The
results directory is deleted before each lane, each lane's XML is archived before the next
overwrites it, and the red run is kept separately.

| Lane | Command | suites | tests | failures | errors | skipped | result |
|---|---|---:|---:|---:|---:|---:|---|
| JDK 17 full | `:common:testStbetaDebugUnitTest` | 51 | 506 | 0 | 0 | 21 | `BUILD SUCCESSFUL in 1m 12s` |
| JDK 11 settings | `:common:testStbetaDebugUnitTest --tests '….ai.subtitle.settings.*'` | 11 | 79 | 0 | 0 | 0 | `BUILD SUCCESSFUL in 1m 24s` |

Phase 2 was 486/21 and 79/0; the JDK 17 total rises by exactly the twenty tests added here and
the JDK 11 lane is unchanged because no settings test was added. The skip count stays at 21, again
only `AiSubtitleDataTest` (6), `AndroidSecretStoreRobolectricTest` (4) and
`AiSubtitlePhoneInputServerTest` (11). Raw XML at `C:\tmp\ev-phase3-jdk17`,
`C:\tmp\ev-phase3-jdk11` and `C:\tmp\ev-phase3-red`.

## Review gates

Two subagents reviewed the phase diff on the Standards and Spec axes; neither ran tests or built
anything, and no review verdict is quoted as a test result. No finding was Blocking. Every
finding below is either fixed in the tree that was re-verified, or recorded with the reason it
was not.

### Standards axis

Fixed:

- *Two cancellation javadocs stopped describing the code.* The class javadoc said only
  `HttpCall.cancel()` suppresses a terminal notification, and `transportFailureReason` said this
  executor "has no other internal canceller" — both written before this phase added `close()`,
  which also suppresses one. Both sentences now name `close()` and say that nothing inside the
  executor ever cancels of its own accord.
- *The request-id javadoc over-claimed what the failure paths keep.* It said the id survives on
  "the failure paths". It does not: a failure reported from inside the stream (an SSE `error`
  event, an unaccepted finish reason) is delivered before any `onSuccess`, and the event hook has
  no request id to give. The wording now says non-streaming responses and transport-level
  failures carry it, and that an in-stream failure carries none, instead of widening the event
  hook to make the old sentence true.
- *An Anthropic javadoc read backwards.* "`message_stop` with an accepted stop reason **after**
  it" — the stop reason arrives in a `message_delta` **before** `message_stop`, which is what the
  code reads. Reworded.
- *`stopped()` broke the predicate naming convention* of every sibling (`isCancelled`,
  `isDelivered`, `isClosed`, `isEventStream`, `isAcceptedFinishReason`). Renamed `isStopped()`.
- *`refusedFailure(String)` was named for one of its three cases* — it also handles a token-limit
  truncation, which is not a refusal. Renamed `unacceptedFinishFailure`, pairing with
  `isAcceptedFinishReason`.
- *`close()`'s "delivers no further callback" promise was not held by one transport branch.* The
  "asked to stream, got a non-SSE body" branch called `onFailure` with no `isStopped()` check.
  The guard is now there, so the whole method honours the interface javadoc rather than relying
  on the adapters to drop the extra callback.

Recorded rather than changed:

- The non-SSE branch reports `FailureReason.IO`, and `TranslationFailureMapper` maps `IO` to
  NETWORK with the generic "network request failed" message, discarding the transport's more
  accurate "Provider did not return an event stream." The mapping is retryable, which is what
  lets the plain fallback happen, and neither line is in this phase's diff. Left for whoever owns
  the transport vocabulary next; noted here so it is not rediscovered.

The axis also confirmed, by reading the code: no orphaned constant, helper, parameter or import
survived the deletion of the elapsed-time heuristic; every new message is a fixed string that
never embeds provider text, a credential or an exception; the two adapters' new duplication
matches the deliberate two-provider symmetry rather than adding to it; at most one terminal
outcome can be delivered and no callback runs while a lock is held; and `String.codePointCount`
and `Character.isHighSurrogate` are Android API 1, well under the minimum SDK.

### Spec axis

Every task-4 bullet was found implemented; task 5 was implemented except for one assertion gap.
The axis also confirmed the red evidence is real rather than prose — the archived XML holds
exactly the four probe methods, and the three messages it could compare against
`probe-summary.json` are byte-identical.

Fixed:

- *The draft-bound test could not fail if the fix were absent.* It asserted the same outcome for
  the same inputs under both "check then append" and "append then check", so its name claimed a
  property it did not guard. The plan also requires the bound to be computed before the text
  enters the buffer, and that ordering **is** implemented — but it is not separable by assertion
  from the adapter's public surface, because both orders reject the same input with the same
  failure and the buffer is never read again once the call is terminal. The test is renamed to
  `aStreamIsAcceptedUpToTheDraftBoundAndRejectedPastIt` and its javadoc states exactly that,
  rather than adding a production field readable only by tests to make the claim true.
- *"The partial count does not change after a terminal" was asserted only on the refusal path.*
  Both `…DeliversOnceAndClosesTheHttpRead` tests now snapshot the drafts before the late delta
  and assert the list is unchanged afterwards.
- *The M09 report and this ledger contradicted each other about the repeated-terminal PARTIAL.*
  The M09 report's two rows and the ledger's outstanding-items line now say both PARTIALs are
  closed. The report keeps its historical numbers and SHAs; the task-8 correction section is
  still the place for the formal write-up.
- *"A late EOF or IO after a cached final does not trigger a fallback" was asserted at the
  adapter, not through the cache.* The scheduler's 429 closed loop now also delivers a late
  transport failure after the accepted final and asserts that the request count stays at two and
  the cached translation is unchanged — which is the cache-level statement the plan asked for.

### A note on the red run

The red run executed the four **probe bodies** under their probe names, exactly as the audit
wrote them. The permanent tests are the same scenarios with the same assertions plus the extra
ones this phase added, so the permanent method bodies themselves were not observed red — the
archived failure proves the defect and the scenario, not the final text of each test. Recorded
because it is a real difference from phase 1, where the probe body was pasted verbatim.

## Known consequence of task 5

Delivering the final as soon as the protocol says the message is complete means the provider's
request id — which arrives with the response headers — is not recorded for a stream that
completes early, because the exchange is closed before the stream's end-of-body callback. It is a
diagnostic only; the non-streaming path and every failure path still carry it. It is stated in
both adapters' javadoc rather than left for a reader to discover.

## Unchanged / not attempted

No push, tag, release, artifact upload or paid API call. No changes to KissTranslator,
SharedModules, MediaServiceCore, ExoPlayer or dependency versions. No upstream host files were
touched. Device acceptance remains with the user (`PENDING DEVICE`).
