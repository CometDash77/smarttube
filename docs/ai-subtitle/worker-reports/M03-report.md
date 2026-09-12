# M03 Worker Report

Task ID: `M03`

Milestone: M03 — AI subtitle domain and session core

Status: **COMPLETE (Worker self-acceptance) — M03-C0..C5 landed; product tip `52877ed96`; 168 passed / 0 failed / 3 skipped; static gate passed; exact-SHA CI evidence `BLOCKED` by workstation API access (see below)**

## Task/Milestone and pinned SHAs

- Milestone: `M03` — AI subtitle domain and session core (M03-M06 consolidated Worker program, plan section 8).
- Pinned base (execution start): `422451df87917a3f932c60291f4907d31f1f21ff` (`docs(ai-subtitle): plan uninterrupted M03-M06 worker run`).
- Final product SHA: `52877ed96` (M03-C4); M03-C5 appends this docs-only finalization commit.
- Branch: `feature/ai-bilingual-subtitles`.

## Baseline confirmation (M03-C0)

| Item | State | Evidence |
|---|---|---|
| M02 Commander PASS | CONFIRMED | User relay 2026-09-12: "M02已经过了" (explicit user decision; plan authority order 1). Product validation SHA `854460bb4`, authoritative run `34660051184` green (recorded in `progress.md`). |
| Branch clean at start | CONFIRMED | `git status --short --branch` → only `## feature/ai-bilingual-subtitles...origin/feature/ai-bilingual-subtitles [ahead 1]`; no modified/untracked files. |
| Exact base SHA | CONFIRMED | `git rev-parse HEAD` → `422451df87917a3f932c60291f4907d31f1f21ff`. |
| Submodules | CONFIRMED | `git submodule status --recursive` → `MediaServiceCore 82e9ccde`, `SharedModules 86f0327`; nested `MediaServiceCore/SharedModules` is uninitialized locally (CI checks out recursively). |
| Baseline workflow | `PENDING AUTHORIZED UPLOAD` | Workflow `ai-subtitle-validation.yml` triggers on push to the feature branch. Previous green product baseline: run `34660051184` for `854460bb4`. The M03-C0 push provides the new exact-SHA baseline run; result recorded in the M03 ledger when available. |

### G03-1 disposition

`NO GRILL REQUIRED`. Accepted documents already settle the exact Session/Track/Profile identity and cache-key version fields:

- `architecture.md` §5 defines the internal domain model (`SourceTrackId`, `SourceCue`, `SubtitleSegment`, `TranslationUnit`, `TranslationProfile`, `TranslationSessionId`, `TranslationResult`, `TranslationFailure`).
- `architecture.md` §9 enumerates the cache-key field list (engine schema version, video ID, Source Track ID, normalized source coverage + text hash, Provider Profile ID, provider protocol, base URL identity without credential, model ID, Prompt Profile ID + prompt content hash/version, target language, context fingerprint, segmentation/boundary version) and states credentials are never included or logged.
- `M03-M06-plan.md` §8 pins the stable interface shapes (`TranslationSessionId` constructor, `TranslationCacheKey.from`, `TranslationCache`).
- ADR acceptance: ADR-006 (dedicated versioned store), ADR-011 (delivery model).

No open identity-field question remains for M03-C1..C4.

## Scope

Replace M02's normalized-source-text identity and bridge-owned lifecycle maps with stable, provider-neutral domain/session/cache contracts. No HTTP, persistence migration, Provider brand/preset, prompt CRUD, real subtitle segmentation, or scheduler implementation in M03.

## Planned commit table

| # | Commit subject | Content | State |
|---|---|---|---|
| C0 | `docs(ai-subtitle): start consolidated M03 execution ledger` | M03 report start, live ledger, baseline pin, G03-1 disposition | MERGED (`2a1e45c65`) |
| C1 | `feat(domain): add stable subtitle timeline identities` | `domain/` value objects + tests + CONTEXT.md if semantics change | MERGED (this commit) |
| C2 | `feat(session): enforce generation and scheduling epoch ownership` | `session/` model, bridge/controller lifecycle ownership move + tests | MERGED (this commit) |
| C3 | `feat(cache): key in-memory translations by complete output identity` | `cache/` key/cache + isolation tests per architecture §9 | MERGED (this commit) |
| C4 | `refactor(translation): stabilize provider-neutral request contracts` | request/result/callback/stream/failure evolution + fake/bridge/controller adaptation + tests | MERGED (this commit) |
| C5 | `docs(ai-subtitle): record M03 domain and session checkpoint` | Self-acceptance, report completion, progress/ledger, CI evidence | MERGED (this commit) |

## Files created

### M03-C1 — `feat(domain): add stable subtitle timeline identities`

Production (6, all under `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/domain/`):

- `SourceTrackId.java` — video + track + language identity; blank fields rejected.
- `SourceCue.java` — pre-normalization cue; non-negative start, end strictly after start, text non-null.
- `SubtitleSegmentId.java` — track + zero-based timeline position; negative index and null track rejected.
- `SubtitleSegment.java` — normalized segment; non-blank text and real interval enforced.
- `TranslationUnit.java` — copied immutable segment-id list; same-track, ordered, contiguous, duplicate-free enforced; unmodifiable accessor.
- `TranslationProfile.java` — provider/model/prompt/version/target-language; blank fields and non-positive version rejected.

Tests (5, under `common/src/test/java/.../ai/subtitle/domain/`, 50 test methods total):

- `SourceTrackIdTest.java` (9) — value equality/hash, per-field inequality, blank-field rejection, literal getter values.
- `SourceCueTest.java` (9) — equality, invalid ranges (negative/reversed/zero-length), null text, empty text allowed before normalization.
- `SubtitleSegmentTest.java` (13) — segment-id identity, same text at different positions stays distinct, invalid ranges, blank text, null id.
- `TranslationUnitTest.java` (11) — ordered/contiguous/duplicate-free coverage, mixed-track rejection, defensive copy, unmodifiable list, equality.
- `TranslationProfileTest.java` (8) — every field participates in equality, blank field rejection, version floor.

`CONTEXT.md` unchanged: the implementation follows the existing `Source Track`, `Source Cue`, `Subtitle Segment`, `Translation Unit`, and `Translation Profile` definitions without changing their meaning.

### M03-C2 — `feat(session): enforce generation and scheduling epoch ownership`

Production (3, under `common/src/main/java/.../ai/subtitle/session/`):

- `TranslationSessionId.java` — video + Source Track + Translation Profile + engine schema version; blank video id, null track/profile, and non-positive engine version rejected; `ENGINE_SCHEMA_VERSION = 1` constant; all four fields participate in equality.
- `TranslationSession.java` — immutable identity/generation plus scheduling epoch and lifecycle state; documented state machine (`LOADING_SOURCE → READY → ACTIVE ⇄ PAUSED`, `SOURCE_ONLY`/`DEGRADED` reachable, terminal `CLOSED`); every transition is idempotent and a closed session ignores further events; `owns(generation, epoch)` is the request/callback ownership check.
- `TranslationSessionSnapshot.java` — immutable snapshot of identity, generation, epoch, and state.

Tests (3):

- `session/TranslationSessionIdTest.java` (7) — per-identity-field inequality (video/track/profile/engine version; provider/model/prompt/version/language), blank/null rejection, exact getter values.
- `session/TranslationSessionTest.java` (17) — state machine and idempotency, pause/resume semantics, seek advances the epoch but never identity or state, terminal/idempotent close, closed-session transition rejection, ownership accept/reject per generation and epoch, snapshot capture.
- `integration/AiSubtitleCueBridgeSessionTest.java` (10) — bridge-level: seek advances only the epoch, identity changes create new generations, repeated identical events are idempotent, subtitles off/on restarts cleanly, profile change advances the generation and rejects stale callbacks, pause/play reflected in state, release closes the session, mismatched result identity never caches.

### M03-C3 — `feat(cache): key in-memory translations by complete output identity`

Production (3, under `common/src/main/java/.../ai/subtitle/cache/`):

- `TranslationCache.java` — cache contract (`get`/`put`/`clear`); session-scoped by design, only accepted results are stored.
- `TranslationCacheKey.java` — immutable key over all 14 output-affecting components from architecture §9: engine schema version, video id, Source Track, source coverage + text fingerprint, Provider Profile id, provider protocol, base URL identity, model id, Prompt Profile id, prompt version, target language, context fingerprint, segmentation version, and boundary version. Raw source text is reduced to a length + hash fingerprint so no subtitle content can reach a log; null session/unit are rejected.
- `InMemoryTranslationCache.java` — `HashMap`-backed implementation; a null result can never create or replace an entry.

Tests (3):

- `cache/TranslationCacheKeyTest.java` (21) — one isolation test per architecture §9 field (14 fields, one varied at a time), equal-input equality/hash, equal text across tracks cannot collide, null rejection, `toString` exposes identity values for diagnostics but never raw source text, and a reflection check that no credential-bearing field exists.
- `cache/InMemoryTranslationCacheTest.java` (5) — round trip, miss, overwrite, non-aliasing keys, clear.
- `integration/AiSubtitleCueBridgeCacheTest.java` (3) — a failed translation is never cached and is re-requested; equal text on another track is re-requested; the cache is cleared when session identity changes (bounded to the active session scope).

Contract evolution in the same commit: `TranslationProfile` gained `providerProtocol` and `baseUrlIdentity` (the base URL identity rejects values embedding user information, so credentials can never enter profile, session, or cache identity); its tests, the session tests, and the bridge default profile were updated together.

### M03-C4 — `refactor(translation): stabilize provider-neutral request contracts`

Production (3 new under `common/src/main/java/.../ai/subtitle/translation/`):

- `TranslationFailureCategory.java` — the eight normalized categories (`CANCELLED`, `TIMEOUT`, `RATE_LIMITED`, `AUTH`, `SERVER`, `PROTOCOL`, `INVALID_OUTPUT`, `NETWORK`) with the retryable/terminal policy (`TIMEOUT`, `RATE_LIMITED`, `SERVER`, `NETWORK` are retryable; the rest are terminal).
- `TranslationFailure.java` — immutable category + non-sensitive message; `isRetryable()`/`isTerminal()` derive from the category; a null category is rejected.
- `TranslationStream.java` — the streaming extension of `TranslationCallback` (`onPartial`); a partial may only update its owning current request and can never be stored as final text. The non-streaming callback remains the baseline contract; the real stream implementation arrives with M08.

Tests (3 new):

- `translation/TranslationFailureTest.java` (4) — retryable set, terminal set, retryable/terminal complement for every category, getters.
- `translation/TranslationRequestTest.java` (4) — derived accessors (source text, source/target language) come from session + unit; equality; null/non-positive rejection.
- `translation/TranslationResultTest.java` (7) — final/partial state, coverage copy and unmodifiability, value equality per field, blank final text and null identity rejection.

## Files modified

### M03-C4

- `translation/TranslationRequest.java` — now carries `TranslationSessionId` + request id + `TranslationUnit`; source text and source/target languages are derived from those identities; null session/unit and non-positive request ids are rejected.
- `translation/TranslationResult.java` — now carries session identity, request id, mapped source coverage, translated text, and the final/partial state; final text must be non-blank; coverage is copied and unmodifiable.
- `translation/TranslationCallback.java` — failures arrive as normalized `TranslationFailure` values instead of raw `Throwable`s.
- `translation/FakeTranslationProvider.java` — emits `TranslationResult.finalResult(...)` and normalized `INVALID_OUTPUT` failures; the blank-input guard became a null-request guard because `TranslationUnit` rejects blank text by construction.
- `integration/AiSubtitleCueBridge.java` — result acceptance now additionally requires the result to be final, to repeat the session identity, and to answer exactly the request's segment coverage.
- Test suites adapted to the evolved contracts: `FakeTranslationProviderTest`, `InMemoryTranslationCacheTest`, `AiSubtitleCueBridgeTest`, `AiSubtitleCueBridgeSessionTest` (plus two new C4 tests: partial results never enter the cache; mismatched coverage is rejected), `AiSubtitleCueBridgeCacheTest`.

### M03-C3

- `integration/AiSubtitleCueBridge.java` (feature-owned M02 file) — the text-keyed result map is replaced by the session-scoped `TranslationCache`; pending requests are keyed by `TranslationCacheKey`; one displayed cue maps to one single-segment unit on the session's Source Track (placeholder context fingerprint and version fields until M06/M08 supply the real pipeline). Public seams and the controller stay unchanged.
- `domain/TranslationProfile.java` (feature-owned M03 file) — contract evolution: protocol and credential-free base URL identity added; tests updated in the same commit.

### M03-C2

- `integration/AiSubtitleCueBridge.java` (feature-owned M02 file) — lifecycle ownership moved from bridge fields into `TranslationSession`: session identity, generation, scheduling epoch, and pause state now live in the session; `onNewVideo`/`onSubtitleTrackChanged`/`onProfileChanged` rebind a session only when the identity actually changes; stale callbacks are rejected via session ownership plus pending/result request-identity comparison. Public seams (`process`, `instance`, `onEnabledChanged`) unchanged; `AiSubtitleController` unchanged.

No non-feature SmartTube file is authorized in M03; none changed.

## Implementation summary by commit ID

`NOT RUN` (filled per commit).

## Witnessed RED evidence and test inventory

### M03-C1 (local diagnostics, JDK 17; CI is authoritative)

- Scaffold phase (constructors without validation or value equality): `./gradlew :common:testStbetaDebugUnitTest --tests "com.liskovsoft.smartyoutubetv2.common.ai.subtitle.domain.*"` → **50 tests completed, 32 failed** (EXIT=1). Assertion-level RED, not a compile failure: validation tests failed by not throwing, equality tests failed by identity comparison.
- Implementation phase (validation + value equality): same task → **BUILD SUCCESSFUL**, 50/50 green in 12s.
- Full AI-subtitle regression in the same change set: **84 passed, 0 failed** — domain 50, bridge 19, controller 10, fake provider 5; `settings.AiSubtitleDataTest` remains 3 skipped on JDK 17 by design (ADR-010; the JDK 11 lane executes it on CI).

Per-suite GREEN counts (XML artifacts): `SourceTrackIdTest` 9, `SourceCueTest` 9, `SubtitleSegmentTest` 13, `TranslationUnitTest` 11, `TranslationProfileTest` 8.

### M03-C2 (local diagnostics, JDK 17; CI is authoritative)

- Session package scaffold phase (no validation, no state machine, `owns()` returning true): `./gradlew :common:testStbetaDebugUnitTest --tests "com.liskovsoft.smartyoutubetv2.common.ai.subtitle.session.*"` → **24 tests completed, 19 failed** (EXIT=1), assertion-level RED.
- Session implementation phase: same task → 24/24 green.
- Full ai-subtitle regression after the bridge integration: **118 passed, 0 failed** — domain 50, session 24 (7 + 17), integration 39 (bridge 19 + bridge-session 10 + controller 10), translation 5; `settings.AiSubtitleDataTest` remains 3 skipped on JDK 17 by design (ADR-010).
- **Mutation checks (required by the M03-C2 self-acceptance):**
  1. `TranslationSession.owns` ignoring the generation comparison → `ownsAcceptsOnlyCurrentGenerationAndEpoch` and `everyIdentityFieldChangeRequiresADistinctSessionWithNewGeneration` fail (63 executed, 2 failed).
  2. `TranslationSession.owns` ignoring the epoch comparison → `ownsAcceptsOnlyCurrentGenerationAndEpoch` fails (63 executed, 1 failed).
  3. Bridge callback dropping the `result.getRequestId()` comparison → `resultWithMismatchedRequestIdentityIsRejected` fails (39 executed, 1 failed).
  All three mutations were reverted and the full suite re-ran green (118 passed, 0 failed).

### M03-C3 (local diagnostics, JDK 17; CI is authoritative)

- Cache package scaffold phase (`TranslationCacheKey` without validation, equality, or `toString`; cache as no-op): `./gradlew :common:testStbetaDebugUnitTest --tests "com.liskovsoft.smartyoutubetv2.common.ai.subtitle.cache.*"` → **26 tests completed, 6 failed** (EXIT=1) — assertion-level RED on equality, `toString`, null rejection, and all cache behaviors. (The per-field isolation tests assert inequality, so they only become meaningful once equality exists; their effectiveness is proven by the mutation check below.)
- Implementation phase: same task → 26/26 green.
- Full ai-subtitle regression after the bridge integration: **150 passed, 0 failed, 3 skipped (ADR-010)** — cache 26, domain 53, session 24, integration 42 (bridge 19 + bridge-session 10 + bridge-cache 3 + controller 10), translation 5.
- **Mutation check:** dropping the `baseUrlIdentity` comparison from `TranslationCacheKey.equals` → `baseUrlIdentityIsIsolated` fails (26 executed, 1 failed); reverted and the full suite re-ran green.

### M03-C4 (local diagnostics, JDK 17; CI is authoritative)

- Contract scaffold phase (new types without retryable mapping or validation; request/result factories without validation): `./gradlew :common:testStbetaDebugUnitTest --tests "com.liskovsoft.smartyoutubetv2.common.ai.subtitle.*"` → **171 tests completed, 6 failed, 3 skipped** (EXIT=1) — assertion-level RED on the retryable mapping, blank/null result text, and null/non-positive request identity. All pre-existing tests compiled and passed against the evolved contracts.
- Implementation phase: 171 executed → **168 passed, 0 failed, 3 skipped (ADR-010)**.
- Per-suite counts: cache 27 (key 21 + memory 6), domain 53, session 24, integration 44 (bridge 19 + bridge-session 12 + bridge-cache 3 + controller 10), translation 20 (fake 5 + failure 4 + request 4 + result 7).
- Contract checks (static inspection): the evolved types contain no provider brand, HTTP, prompt, retry, or persistence detail; the non-streaming success path remains the baseline and the production Fake still produces the exact `source + "\n" + "[ZH] source"` output on the first process call (`immediateFakeDecoratesOnTheFirstAndOnlyProcessCall`).

## Static checks

Milestone static gate over `422451df8...HEAD` (product tip `52877ed96`), executed 2026-09-12:

- `git status --short --branch` — clean; in sync with `origin` after the push.
- `git diff --check` — clean.
- `git diff --name-status 422451df8...HEAD` — 30 added, 9 modified; **0 non-feature paths** (filtering out `common/.../ai/subtitle/**` and `docs/ai-subtitle/**` yields zero files).
- Host-file diff (`.github/workflows/CI.yml`, `common/app/**`, `common/exoplayer/**`, `MediaServiceCore`, `SharedModules`, `exoplayer-amzn-2.10.6`) — **empty**; `upstream-patches.md` is unchanged and matches the empty host diff.
- Brand scan (`openai|anthropic|openrouter|deepseek|mimo`) over the complete feature tree — no match.
- Network-import scan (`java.net`, `okhttp`, `sharedutils.okhttp`) over the complete feature tree — no match; no network request exists anywhere in M03.
- `./gradlew :common:lintStbetaDebug` — **BUILD SUCCESSFUL** (55s, 277 tasks executed); API 17 compatibility preserved.

## GitHub Actions runs

- Push confirmed at the git layer for every M03 tip: `2a1e45c65` (C0), `047022ae2` (C1), `872c07205` (C2), `66ee4e265` (C3), `52877ed96` (C4); `git ls-remote origin refs/heads/feature/ai-bilingual-subtitles` matched the local tip after every push.
- **Exact-SHA workflow status: `BLOCKED` (workstation API access).** The Actions REST API is not readable from this workstation: `gh api repos/CometDash77/smartube` and its `actions/*` sub-resources return 404 even though `gh api user` and `gh api rate_limit` succeed (the API itself is reachable), and an unauthenticated `curl https://github.com/CometDash77/smartube` also returns 404 — the repository is private and the current workstation credential has no API read access to it. Every push therefore launched the workflow (the lane triggers on branch pushes), but the run results cannot be read from here.
- **Required user action to unblock:** open https://github.com/CometDash77/smartube/actions while authenticated, or refresh the workstation gh authorization, and record the run URL/ID/step conclusions for the five tips above. This is the only open M03 evidence item; it is reported as `BLOCKED`, never as PASS.

## Device matrix

`NOT RUN` for all rows at M03 (pure-domain milestone; physical device matrix remains `NOT RUN` — no device available to the Worker; automated tests do not imply a device PASS).

## Worker first-pass self-review dispositions

`NOT RUN` (performed against M03 base...tip at C5, before the Commander second review; no Commander PASS is claimed here).

## Decisions / ADRs / glossary / research / upstream ledger

- G03-1 recorded as `NO GRILL REQUIRED` with citations (see above); no new ADR needed for it.
- `CONTEXT.md` update: only if a semantic definition changes in C1–C4.
- `upstream-patches.md`: expected unchanged (zero existing-file patches in M03).

## Deviations, unexpected discoveries, remaining risks, deferred Minor findings

`NOT RUN` (recorded as they occur; honest historical failures retained).

## Acceptance-criteria table (M03 exit/self-acceptance)

| # | Criterion | Disposition | Evidence |
|---|---|---|---|
| 1 | Deterministic tests prove no cross-video, cross-track, cross-profile, or same-text/different-segment contamination | MET | `TranslationCacheKeyTest` (21 field-isolation tests), `AiSubtitleCueBridgeCacheTest` (equal text on another track re-requests; session change clears the cache), `SubtitleSegmentTest` (same text at different segment ids stays distinct) |
| 2 | All M02 regression tests pass | MET | bridge 19, controller 10, fake provider 5 — green inside the 168-passed full run |
| 3 | No network request, real Provider profile, prompt manager, or segmentation pipeline exists | MET | brand scan: 0 matches; network-import scan: 0 matches; only `FakeTranslationProvider` is wired |
| 4 | No non-feature SmartTube file changed | MET | 0 non-feature paths in `422451df8...HEAD`; host-file diff empty |
| 5 | Exact-SHA GitHub Actions launched and recorded; green mandatory before final M03–M06 return | `BLOCKED` | Pushes confirmed at git layer for all five tips; Actions API unreadable from this workstation (404; repository private, workstation credential lacks API read access). User/browser verification required; never reported as PASS. |
| 6 | M03 Worker self-acceptance complete; base/tip frozen; Worker continues to M04 without user relay | MET | base `422451df8`; product tip `52877ed96`; this report; C5 is the docs-only finalization commit |

## Confirmation

M03-C0 through M03-C5 are landed (product tip `52877ed96`; C5 is the docs-only finalization commit). No existing SmartTube file has been changed in M03; `upstream-patches.md` is unchanged and matches the empty host diff. M03 Worker self-acceptance is complete; the only open item is the exact-SHA CI evidence, which is `BLOCKED` by workstation API access and needs browser/user verification. The Worker continues to M04 without user relay.
