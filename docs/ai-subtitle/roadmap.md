# AI Subtitle Roadmap

Status: **Accepted — approved by user on 2026-09-11**

Planning unit: one Milestone = one self-contained Stage Package, one Worker execution plan, a small series of internally coherent commits, one consolidated Worker report, and one Commander milestone review.

## Delivery rules

- Production work starts only after the architecture/migration/roadmap approval.
- The user transports at most one package to the Worker per Milestone. Internal workstreams and commits are not separate user handoffs.
- M01 was completed by the Commander. The remaining implementation therefore requires eight Worker handoffs: M02 through M09.
- `origin` is the user's personal project remote; `upstream` is read-only official SmartTube.
- Build, lint, tests, and APK assembly are accepted only from GitHub Actions for the exact reported commit SHA.
- New feature files are preferred over changes to upstream files. Every existing-file change is declared in its Task Package and logged in `upstream-patches.md`.
- A milestone cannot hide failing acceptance scenarios behind a later milestone. Later milestones may deepen quality but must not repair a knowingly broken foundational lifecycle.
- Internal workstream counts are planning aids, not separate Task Packages. They can change through recorded technical rulings without changing final scope or increasing user handoffs.

## M01 — Repository reconnaissance and architecture baseline

State: Complete; overall architecture approved on 2026-09-11.

Deliverables:

- repository/Git/build/license reconnaissance;
- SmartTube caption, renderer, lifecycle, settings, persistence, network, and historical dual-subtitle analysis;
- KissTranslator behavior/test/fixture analysis;
- official protocol review for all five Provider Types;
- architecture, migration map, decisions, upstream strategy, roadmap, progress ledger, glossary, and M01 plan;
- personal project `origin`, official `upstream`, and isolated feature branch.

Exit: Architecture + Migration Map + Roadmap approved as the single planned overall architecture gate.

Worker handoffs: 0. Any factual correction is a documentation amendment, not a production task.

## M02 — CI lane and deterministic dual-subtitle baseline

Goal: prove the SmartTube lifecycle and renderer using `FakeTranslationProvider`, independent of network/API behavior.

One Worker Stage Package containing these internal workstreams:

1. Add a feature-owned GitHub Actions workflow that runs targeted unit tests, lint, and APK assembly on the feature branch/pull requests. Record the first green baseline run.
2. Add minimal domain IDs/models and Fake provider contracts needed by the vertical slice.
3. Implement source-track matching/full-track loader spike and tests; decide whether ADR-005 needs the conditional loader hook.
4. Add `AiSubtitleController` and the single `PlaybackPresenter` registration hook.
5. Add `AiSubtitleCueBridge` and the single `SubtitleManager` hook.
6. Exercise play, pause/resume, seek/rapid seek, track switch, subtitles off/on, video change, close/open, PiP, and background/foreground on a test matrix.

Exit: source plus deterministic translated line displays reliably; AI disable/failure leaves original captions and playback unchanged; GitHub Actions green.

Upstream budget: two expected existing Java files; a third loader hook only if source-spike evidence requires it.

## M03 — AI subtitle domain and session core

Goal: replace vertical-slice shortcuts with stable, provider-neutral feature contracts.

One Worker Stage Package containing these internal workstreams:

1. Subtitle timeline, Translation Unit, Translation Profile, and Translation Session models.
2. Session Generation and scheduling-epoch lifecycle with stale-result rejection.
3. In-memory translation cache and complete output-affecting cache key.
4. Provider-neutral request/result/stream/failure contracts.
5. State-machine and identity-invalidation test suite.

Exit: deterministic tests prove no cross-video, cross-track, or cross-profile contamination.

Upstream budget: zero additional existing files.

## M04 — Provider, model, persistence, and connection management

Goal: implement the five Provider Types using two shared wire protocols.

One Worker Stage Package containing these internal workstreams:

1. Versioned Provider/Profile schema, repository, migrations, and default repair.
2. Secret storage policy spike and Android-version fallback decision.
3. OpenAI Chat Completions normal-response adapter.
4. Anthropic Messages normal-response adapter.
5. OpenRouter, DeepSeek, and MiMo presets/capability policies over shared adapters.
6. Model discovery and manual Model ID path.
7. Normalized connection test and error categories.
8. Provider/model CRUD and default/current selection UI.
9. Serialization, redaction, cancellation, timeout, and malformed-response tests.
10. Provider failure integration test proving Source-Only Fallback.

Exit: each Provider Type can be saved, edited, tested, selected, and used with a model; no secret appears in logs/reports/backups covered by the chosen policy.

Upstream budget: one settings-entry hook; feature-owned resource and presenter files.

## M05 — Prompt Manager and resolved Translation Profile

Goal: make prompts versioned, user-manageable, independent of provider/model, and safe for cache identity.

One Worker Stage Package containing these internal workstreams:

1. Built-in and custom Prompt Profile schema/repository/migrations.
2. Strict variable catalog and renderer with missing/unknown-variable diagnostics.
3. Prompt CRUD, copy, default/current selection UI.
4. Resolve Provider + Model + Prompt + Target Language atomically.
5. Cache invalidation tests on every output-affecting change.
6. Independently authored baseline translation and indexed-output prompts.

Exit: a valid Translation Profile is always unambiguous and persists across restart/profile switch.

Upstream budget: no additional existing files expected.

## M06 — Subtitle processing behavioral port

Goal: independently reimplement the KissTranslator-derived subtitle behavior on Android.

One Worker Stage Package containing these internal workstreams:

1. Minimal fixture format and provenance rules.
2. Normalization and non-speech/duplicate handling.
3. Coarse ASR word-timing estimation.
4. Rule sentence breaker.
5. Statistical sentence breaker.
6. Translation chunk construction.
7. Boundary Protocol and malformed-output handling.
8. Segmentation Metrics and coverage oracle.
9. AI segmentation accepted-prefix, tail retry, and deterministic fallback.

Exit: independently authored fixture suites cover manual captions, ASR, word timing, no-space languages, noise, fast/slow speech, long segments, overlaps, gaps, and malformed model output.

Upstream budget: zero.

## M07 — Real-time translation scheduler

Goal: make lookahead translation correct under real playback changes and bounded in cost.

One Worker Stage Package containing these internal workstreams:

1. Window calculation and chronological priority queue.
2. Request deduplication and concurrency control.
3. Pause/resume admission policy.
4. Forward/backward/rapid-seek cancellation and reprioritization.
5. Session identity changes for video, track, provider, model, prompt, and language.
6. Bounded transient retry with jitter and terminal failure handling.
7. Partial-batch repair and Source-Only Fallback.
8. End-to-end state-machine stress tests.

Exit: no stale response can alter current UI/cache; duplicate calls remain bounded; seek resumes near-current translations promptly.

Upstream budget: zero additional existing files.

## M08 — Mature translation pipeline

Goal: improve daily-use latency and quality without coupling correctness to optional optimizations.

One Worker Stage Package containing these internal workstreams:

1. Bounded video-title/description and rolling-context builder.
2. Context fingerprinting and serialized context-dependent scheduling.
3. OpenAI-style SSE parser.
4. Anthropic named-event SSE parser and unknown-event tolerance.
5. Draft/final stream updates with current-cue ownership.
6. Persistent bounded cache with eviction/migration, if measurements justify it.
7. Optional video-summary context experiment with cost/quality decision.
8. Stream interruption, timeout, 429, 5xx, malformed output, and recovery tests.

Exit: streaming can be disabled without losing correctness; context is bounded and cache-safe; failures recover without player impact.

Upstream budget: zero.

## M09 — Hardening and upstream-regression gate

Goal: establish a repeatable release and future-upstream-sync safety net.

One Worker Stage Package containing these internal workstreams:

1. Full language/caption matrix: English/Japanese/Chinese, manual/ASR, word-level, fast/slow, short/2h+.
2. Full player lifecycle matrix including background/foreground and reopen.
3. Provider/profile/prompt switching and all normalized failure categories.
4. Performance, memory, request-count, cache-bound, and secret-redaction audits.
5. Upstream patch audit: no formatting churn, undeclared changes, or AI logic in host files.
6. Documented upstream-sync checklist and GitHub Actions regression workflow.
7. Release candidate report with accepted commit/workflow evidence.

Exit: all completion dimensions in the project charter are evidenced; upstream ledger matches the actual diff; CI is green for the release SHA.

Upstream budget: no new hooks; only corrections to already approved hooks.

## Dependency chain

```text
M01 approval
  -> M02 lifecycle/renderer proof + CI
  -> M03 stable domain/session contracts
  -> M04 provider persistence/protocols
  -> M05 prompts and resolved profile
  -> M06 processing behavior
  -> M07 scheduler correctness
  -> M08 optional maturity optimizations
  -> M09 release/upstream gate
```

M04 and M06 are conceptually independent after M03, but each remains a separate single-transfer Stage Package. No subtask inside a Milestone requires an additional user handoff.

## First Worker Stage Package after approval

`M02 — CI Lane & Deterministic Dual-Subtitle Baseline`

The single M02 package includes the feature-owned GitHub Actions lane, minimal persisted enable switch, FakeTranslationProvider vertical slice, controller/renderer hooks, automated tests, and the full player-lifecycle validation matrix. The Worker may create several named logical commits, but returns one plan and one consolidated milestone report.
