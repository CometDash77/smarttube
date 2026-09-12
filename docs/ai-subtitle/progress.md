# AI Subtitle Progress Ledger

Last updated: 2026-09-12

Authoritative branch: `feature/ai-bilingual-subtitles`

Personal project remote: `origin` → `https://github.com/CometDash77/smartube.git` (GitHub reported public visibility during Phase 0)

Official source: `upstream` → `https://github.com/yuliskov/SmartTube.git`

## Current state

- Current milestone: M04 — Provider, model, persistence, and connection management (M03 self-acceptance complete; product tip `52877ed96`; exact-SHA CI `BLOCKED` at workstation API access)
- Current task: `M04-C4 — add Anthropic-compatible normal responses` (M04-C0..C3 landed: policy, versioned non-secret profiles, separated credentials, and the OpenAI-compatible normal-response adapter; next is the Anthropic-compatible adapter)
- Task state: M03 complete (C0–C5; product tip `52877ed96`; exact-SHA CI still needs GitHub UI verification); M04 in progress at C4; M02 remains accepted
- Production code changes: M02 renderer/lifecycle slice present; M03 domain/session/cache/contracts added; M04-C1 adds feature-owned Provider Profile persistence; M04-C2 adds separated Android secret storage; M04-C3 adds the OpenAI-compatible normal-response adapter and HTTP executor boundary
- Actual upstream patch count: 3 existing SmartTube files, all inside the approved M02 budget
- Remote sync: `feature/ai-bilingual-subtitles` tracks `origin`; M04-C0 tip `8d0c9a50e`, M04-C1 tip `3e1e1ed60`, and M04-C2 tip `686768e54` are pushed
- Local compile/test status: diagnostic only (M04-C3 full ai-subtitle suite 221 passed / 0 failed / 7 skipped on JDK 17; settings/secret suite 41/41 on JDK 11; see Test status)
- GitHub Actions status: M02 run `34660051184` green for `854460bb4`; M03 and M04 exact-SHA runs remain `BLOCKED` at the workstation Actions API (404) and require GitHub UI verification
- Forward plan: `worker-plans/M03-M06-plan.md` is the single M03–M06 Worker program. Per ADR-011 it is handed off once and returned once; the Worker self-validates by milestone during the run, then the Commander performs a separate Superpowers second review for each milestone. Execution is unblocked: M03 self-acceptance is complete; M04-C4 is next.

## Completed work

- [x] Inspected workspace and located/cloned both repositories.
- [x] Recorded clean Git state, revisions, branches, remotes, and pinned submodules.
- [x] Read SmartTube README, Gradle topology, build constraints, and licenses.
- [x] Traced SmartTube caption discovery through MediaServiceCore, dynamic MPD, ExoPlayer, and `SubtitleManager`.
- [x] Traced player lifecycle and seek events through `ExoPlayerController`, `PlaybackPresenter`, and `BasePlayerController`.
- [x] Inspected subtitle settings, `PlayerData`, `AppPrefs`, shared persistence, OkHttp, Retrofit, RxJava, and current test facilities.
- [x] Investigated SmartTube PR #5839 and rejected it as a direct cherry-pick candidate.
- [x] Inspected KissTranslator normalization, sentence breaking, AI segmentation, boundary protocol, metrics, scheduler, rendering manager, cache identity, context, streaming, retry, prompts, provider code, tests, and fixtures.
- [x] Confirmed SmartTube MIT and KissTranslator GPL-3.0 license mismatch.
- [x] Read current official OpenAI, Anthropic, OpenRouter, DeepSeek, and MiMo API/model/streaming documentation.
- [x] Created the required Phase 0 architecture, migration, roadmap, decisions, progress, upstream ledger, milestone plan, and domain glossary.
- [x] Configured personal project `origin`, official `upstream`, and `feature/ai-bilingual-subtitles` branch.
- [x] Recorded GitHub Actions as the authoritative build/test environment.
- [x] Received and reviewed the first M02 Worker delivery (`918c487d2` through `3efb96374`, plus local report amendment `26693c340`).
- [x] Persisted `M02-review.md`, ADR-010, and one consolidated `M02-FIX-01` package.
- [x] Completed `M02-FIX-01`: first-cue immediate rendering, API 17 compatibility, immediate disable, JDK 11 preference lane; replacement run `34618112103` green.
- [x] Received and recorded the Commander second pass: 3 blocking items (settings-helper line endings, unproven re-enable completion, overstated automation coverage) plus 2 explicitly deferred judgement items.
- [x] Completed `M02-FIX-02` corrections: byte-level CRLF repair of the settings helper (rows 70–81), strengthened re-enable assertions with mutation evidence, and corrected report statements; run `34660051184` green for `854460bb4`.
- [x] Consolidated M03–M06 into one uninterrupted Worker execution plan with milestone-scoped commits, self-acceptance, exact-SHA CI evidence, subagent rules, Grill with Docs gates, live-document requirements, and post-return Commander Superpowers reviews; ADR-011 records the one-handoff/one-return policy.
- [x] Completed M03-C0..C5 (domain/session/cache/contracts), M04-C0..C2 (profile persistence and credential protection), and M04-C3 (OpenAI-compatible non-streaming request/response adapter with fake-executor coverage, cancellation, timeout propagation, request IDs, and secret redaction).

## Completed tasks

| Task | Result | Commit | Verification |
|---|---|---|---|
| Phase 0 reconnaissance | Accepted 2026-09-11 | `7e38c9db7` | Read-only evidence and document consistency checks |
| M02 first Worker delivery | Changes required | `918c487d2..26693c340` | Commander code/spec review; Actions run `34615801161` failed at lint |
| M02-FIX-01 correction | Green CI; second review required | `69f644f4a`, `be02bc2b3` | Run `34618112103` green (JDK 17 unit tests/lint/assembly + JDK 11 preference suite); Commander second pass found 3 blocking items |
| M02-FIX-02 correction | Accepted by user 2026-09-12 | `854460bb4`, `0b1f4ab53`, `2716e5380` | Run `34660051184` green for `854460bb4`: 34 JVM tests on JDK 17 and 3/3 preference methods on JDK 11; docs-only run `34660774973` succeeded; mutation and CRLF-fit evidence recorded |
| M03 domain/session/cache/contracts | Self-acceptance complete; Commander review pending | `422451df8..8a4bd175b`; product tip `52877ed96` | 168 passed / 0 failed / 3 skipped on JDK 17; lint green; static gate found 0 non-feature paths; exact-SHA CI blocked at workstation API |
| M04-C0 policy settlement | Accepted as Worker evidence | `8d0c9a50e` | G04-1/ADR-012 and G04-2 research recorded; docs-only |
| M04-C1 provider profile persistence | Local TDD green; pushed; exact-SHA CI pending UI verification | `3e1e1ed60` | Incremental RED plus full GREEN; JDK 17 193 passed / 0 failed / 6 skipped; JDK 11 settings suite 6/6; three mutation checks each produced named failures and were reverted |
| M04-C2 credential protection | Local TDD green; pushed; exact-SHA CI pending UI verification | `686768e54` | RED missing-symbol compile rounds plus API-policy RED, then full GREEN 214 total / 207 passed / 0 failed / 7 skipped on JDK 17 and 41/41 on JDK 11; cleanup and API-threshold mutations produced 3/1 named failures and were reverted; secret scan clean |
| M04-C3 OpenAI-compatible adapter | Local TDD green; push/CI pending | `M04-C3` (tip recorded in plan ledger) | RED 44 missing-symbol compile failures, then 14/14 adapter tests and full GREEN 221 passed / 0 failed / 7 skipped on JDK 17; authorization and JSON-escaping mutations produced 2/1 named failures and were reverted; lint green |

## Accepted commits

- `7e38c9db7` — Phase 0 architecture, migration map, roadmap, decisions, progress, upstream strategy, M01 plan, and glossary.
- `918c487d2` through `2716e5380` — M02 implementation, corrections, and audit trail; user relayed Commander PASS after run `34660051184`.

## Rejected or superseded commits

None.

## Pending commits

- None. M03/M04 are inside the active Worker program; M04-C4 is the next implementation item, not a pending review.
## Open problems

1. The selected Exo subtitle format does not expose the full timed-text URL through a stable app-level API. M02 must validate track matching and format-info cache behavior before deciding whether a loader hook is unavoidable.
2. Existing `SubtitleManager` disables embedded styles. M02 must prove whether a decorated two-line cue can retain separate source/translation styles without forking `SubtitlePainter`.
3. The current GitHub Actions workflow does not automatically validate the feature branch or explicitly invoke unit tests. The M02 Stage Package adds an isolated validation workflow before its product changes are accepted.
4. Android secret storage must balance Keystore protection with SmartTube's old-device support and backup/export behavior; decision deferred to a focused M04 spike.
5. KissTranslator tests/fixtures are GPL-covered repository content; target fixtures need independent authorship/provenance.
6. Resolved by `M02-FIX-01`: M02 synchronous Fake completion is now consumed on the first cue-processing call.
7. Resolved by `M02-FIX-01`: no M02 production reference requires an API newer than 17 (`EnableState` seam; explicit null-safe equality).
8. Resolved by `M02-FIX-01`: disabling the setting cancels in-flight work and clears bridge state inside the settings callback.
9. Resolved by `M02-FIX-01`: the Robolectric preference suite executes in the supplementary JDK 11 job (3/3 passed in runs `34618112103` and `34660051184`).
10. Open: `SubtitleSettingsPresenter.java` is CRLF in the Git object store while repository-wide `core.autocrlf=true`; any whole-file rewrite creates churn, so edits to that file must stay byte-verified and be staged with the autocrlf conversion disabled.

## Current architecture decisions

- Accepted: feature-owned package tree inside `common`, not a new Gradle module.
- Accepted: clean-room behavioral implementation from KissTranslator evidence.
- Accepted: five Provider Types over two shared protocol adapters.
- Accepted: one decorated cue through existing `SubtitleView`; no renderer fork.
- Accepted: full-track source adapter using existing MediaServiceCore access, with one conditional loader hook only if evidence requires it.
- Accepted: dedicated versioned `AiSubtitleData`, separate from `PlayerData`.
- Accepted: `origin` is the user's personal project remote; `upstream` is official SmartTube.
- Accepted: GitHub Actions is authoritative for build/test acceptance.
- Accepted: one Stage Package and one consolidated Worker return per Milestone is the default; ADR-011 defines the M03–M06 exception without removing their milestone-scoped evidence.
- Accepted: JDK 17 remains authoritative; a narrow JDK 11 job may execute the inherited Robolectric 4.6.1 preference suite until its dependency is upgraded.
- Accepted: M02-FIX-02 stays inside the three-file upstream patch surface and leaves the two deferred refactors (duplicated equality helpers, bridge request-identity cluster) untouched.
- Accepted: ADR-011 supersedes ADR-009 only for the M03–M06 handoff boundary: one uninterrupted Worker handoff/return, Worker self-acceptance per milestone, then Commander Superpowers second review per milestone.

## Planned next action

M03 is self-accepted and M04 is in progress under `worker-plans/M03-M06-plan.md`; M04-C3 OpenAI-compatible normal responses are green, and M04-C4 Anthropic-compatible responses are next. After the single M03–M06 Worker return, the Commander runs four milestone-scoped Superpowers second reviews.

## Test status

- Phase 0: no production behavior changed, so no product test was run.
- M02 local diagnostic report (first delivery): 31 pure-JVM tests passed; 3 Robolectric preference tests skipped and not accepted as coverage.
- M02 authoritative run `34615801161`: FAILURE at lint; validation-reports artifact exists; no accepted assembly/APK result.
- M02-FIX-01 authoritative run `34618112103`: green — bridge 19/19, controller 10/10, provider 5/5 on JDK 17; preference suite 3/3 on JDK 11; lint and beta assembly pass.
- M02-FIX-02 local diagnostics (JDK 17): 34 JVM tests green (bridge 19, controller 10, provider 5; preference suite deliberately skipped); mutation check red at the dual-line assertion with the post-re-enable delivery removed (JUnit line 267 in the mutated file, 268 restored) and green again after restoring it; settings helper byte-verified at 102 CRLF / 0 LF-only lines with a 12/12 diff.
- M02-FIX-02 authoritative run `34660051184`: green — same counts as run `34618112103`; primary job 5m29s and preference job 2m10s, all steps successful; three artifacts present.
- M02-FIX-02 docs-only follow-up: run `34660494711` was cancelled by concurrency after the JDK 11 job completed; superseding run `34660774973` for `2716e5380` completed successfully with all steps and three artifacts.
- M03-C1 local diagnostics (JDK 17): witnessed RED 50 tests / 32 failed, then GREEN 50/50; full ai-subtitle regression 84 passed / 0 failed (domain 50 + bridge 19 + controller 10 + provider 5); `AiSubtitleDataTest` stays 3-skipped on JDK 17 per ADR-010.
- M03-C2 local diagnostics (JDK 17): witnessed RED 24 tests / 19 failed (session scaffold), then GREEN 24/24; full ai-subtitle regression 118 passed / 0 failed (domain 50 + session 24 + integration 39 + provider 5). Mutation checks: owns-ignoring-generation 2 named failures, owns-ignoring-epoch 1, dropped result-identity check 1 — all reverted, suite re-ran green.
- M03-C3 local diagnostics (JDK 17): witnessed RED 26 tests / 6 failed (cache scaffold), then GREEN 26/26; full ai-subtitle regression 150 passed / 0 failed / 3 skipped (cache 26 + domain 53 + session 24 + integration 42 + provider 5). Mutation check: dropping the base-URL comparison from the cache key fails `baseUrlIdentityIsIsolated` — reverted, suite re-ran green.
- M03-C4 local diagnostics (JDK 17): witnessed RED 171 tests / 6 failed (contract scaffold, all pre-existing tests compiled and passed), then GREEN 168 passed / 0 failed / 3 skipped (cache 27 + domain 53 + session 24 + integration 44 + translation 20). Contracts now carry session/unit identity, final/partial state, and normalized failure categories; the production Fake baseline output was re-verified.
- M03-C5 static gate (local, JDK 17): `git diff --check` clean; 30 added / 9 modified files with 0 non-feature paths; host-file diff empty; brand and network-import scans clean; `:common:lintStbetaDebug` BUILD SUCCESSFUL (API 17 preserved). Exact-SHA GitHub Actions status: `BLOCKED` — the Actions API is unreadable from this workstation (404 for the private repository), so runs for the five M03 tips must be verified in the GitHub UI.
- M04-C0 (documentation-only): no product test applicable; evidence is the G04-1 local SDK verification (`KeyGenParameterSpec`/`KeyProperties` since API 23 queried from the SDK api-versions.xml) and the G04-2 Phase 0 protocol baseline. Interruption point recorded in `worker-reports/M04-report.md`; M04 base pinned at `8a4bd175b`.
- M04-C1 local diagnostics: witnessed RED while production types/API were absent (34 + 8 + 8 + 13 + 7 missing-symbol compile failures across the value, serializer, migration, repository, and Android-store rounds) and a 1-failure RED for invalid optional collection repair; then full ai-subtitle GREEN 199 total / 193 passed / 0 failed / 6 skipped on JDK 17, and `AiSubtitleDataTest` 6/6 with 0 skipped on JDK 11. Mutation checks: disabled default/selection repair produced 4 named failures; dropped credential-reference serialization produced 2; disabled future-schema rejection produced 1; each mutation was reverted; `:common:lintStbetaDebug` is green.
- M04-C2 local diagnostics: initial RED was 57 missing-symbol compile failures across secret-store and repository cleanup tests, with a later 3-symbol RED for the API policy selector; full ai-subtitle GREEN is 214 total / 207 passed / 0 failed / 7 skipped on JDK 17, and the complete settings/secret suite is 41/41 with 0 skipped on JDK 11. Mutation checks skipped credential cleanup (3 named failures) and lowered the policy threshold (1 named failure), then restored the code. High-confidence repository secret scan found no real credential. `:common:lintStbetaDebug` is green.
- M04-C3 local diagnostics: after the test surface compiled, the adapter round witnessed RED as 44 missing production symbols, then GREEN 14/14 protocol tests and the full ai-subtitle suite at 228 total / 221 passed / 0 failed / 7 skipped on JDK 17. Mutation checks omitting Authorization produced 2 named failures and disabling quote escaping produced 1; both were reverted. `:common:lintStbetaDebug` is green.
