# M03 Worker Report

Task ID: `M03`

Milestone: M03 — AI subtitle domain and session core

Status: **IN PROGRESS — M03-C1 and M03-C2 landed (domain + session core, 118 tests green, mutation checks recorded); M03-C3..C5 remain**

## Task/Milestone and pinned SHAs

- Milestone: `M03` — AI subtitle domain and session core (M03-M06 consolidated Worker program, plan section 8).
- Pinned base (execution start): `422451df87917a3f932c60291f4907d31f1f21ff` (`docs(ai-subtitle): plan uninterrupted M03-M06 worker run`).
- Final product SHA: `NOT RUN` (filled at M03-C5).
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
| C3 | `feat(cache): key in-memory translations by complete output identity` | `cache/` key/cache + isolation tests per architecture §9 | NOT RUN |
| C4 | `refactor(translation): stabilize provider-neutral request contracts` | request/result/callback/stream/failure evolution + fake/bridge/controller adaptation + tests | NOT RUN |
| C5 | `docs(ai-subtitle): record M03 domain and session checkpoint` | Self-acceptance, report completion, progress/ledger, CI evidence | NOT RUN |

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

## Files modified

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

## Static checks

`NOT RUN` as a full milestone gate (performed at M03-C5: `git diff --check`, declared file inventory, no undeclared host-file change, no line-ending/mode churn, `upstream-patches.md` unchanged because no host file is touched). Commit-level `git diff --check` stays clean; no existing SmartTube file has been modified so far.

## GitHub Actions runs

- M03-C0 push: `PENDING AUTHORIZED UPLOAD` — exact-SHA run for the M03-C0 tip; URL/run ID/step conclusions to be recorded here when available.
- Milestone tip (M03-C5): `NOT RUN`.

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
| 1 | Deterministic tests prove no cross-video, cross-track, cross-profile, or same-text/different-segment contamination | NOT RUN | — |
| 2 | All M02 regression tests pass | NOT RUN | — |
| 3 | No network request, real Provider profile, prompt manager, or segmentation pipeline exists | NOT RUN | — |
| 4 | No non-feature SmartTube file changed | NOT RUN | — |
| 5 | Exact-SHA GitHub Actions launched and recorded; green mandatory before final M03–M06 return | NOT RUN | — |
| 6 | M03 Worker self-acceptance complete; base/tip frozen; Worker continues to M04 without user relay | NOT RUN | — |

## Confirmation

M03-C0, M03-C1, and M03-C2 are landed. No existing SmartTube file has been changed in M03; `upstream-patches.md` stays unchanged. M03-C3 (cache identity) starts after this commit.
