# AI Subtitle Progress Ledger

Last updated: 2026-09-12

Authoritative branch: `feature/ai-bilingual-subtitles`

Personal project remote: `origin` → `https://github.com/CometDash77/smartube.git` (GitHub reported public visibility during Phase 0)

Official source: `upstream` → `https://github.com/yuliskov/SmartTube.git`

## Current state

- Current milestone: M03 — AI subtitle domain and session core (M03–M06 consolidated Worker program)
- Current task: `M03-C3 — key in-memory translations by complete output identity` (M03-C2 landed: session core + bridge lifecycle ownership, 118 tests green, three mutation checks recorded)
- Task state: M02 accepted — user relayed the Commander PASS on 2026-09-12 (product evidence run `34660051184` green for `854460bb4`); M03 execution started from base `422451df8`
- Production code changes: M02 implementation present; both correction packages verified green by authoritative runs (`34618112103`, `34660051184`)
- Actual upstream patch count: 3 existing SmartTube files, all inside the approved M02 budget
- Remote sync: `feature/ai-bilingual-subtitles` is pushed and matches the local tip; product validation is `854460bb4`, followed by report-only commits `0b1f4ab53` and `2716e5380` plus this final audit-trail revision
- Local compile/test status: diagnostic only (34 JVM tests green on JDK 17; see Test status)
- GitHub Actions status: run `34660051184` green for `854460bb4` (both jobs); earlier runs `34618112103` and `34618912621` green; `34615801161` failed at lint (historical, superseded)
- Forward plan: `worker-plans/M03-M06-plan.md` is the single M03–M06 Worker program. Per ADR-011 it is handed off once and returned once; the Worker self-validates by milestone during the run, then the Commander performs a separate Superpowers second review for each milestone. Execution is unblocked: M02 PASS relayed 2026-09-12; M03 execution in progress.

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

## Completed tasks

| Task | Result | Commit | Verification |
|---|---|---|---|
| Phase 0 reconnaissance | Accepted 2026-09-11 | `7e38c9db7` | Read-only evidence and document consistency checks |
| M02 first Worker delivery | Changes required | `918c487d2..26693c340` | Commander code/spec review; Actions run `34615801161` failed at lint |
| M02-FIX-01 correction | Green CI; second review required | `69f644f4a`, `be02bc2b3` | Run `34618112103` green (JDK 17 unit tests/lint/assembly + JDK 11 preference suite); Commander second pass found 3 blocking items |
| M02-FIX-02 correction | Green CI; awaiting Commander re-review | `854460bb4`, `0b1f4ab53`, `2716e5380` (+ this audit-trail revision) | Run `34660051184` green for `854460bb4`: artifact XML shows 34 JVM tests passed on JDK 17 and 3/3 preference methods on JDK 11; docs-only run `34660494711` was superseded/cancelled and `34660774973` succeeded; mutation check red at the dual-line assertion (JUnit line 267 in the mutated file, 268 restored); settings helper byte-verified at 102 CRLF / 0 LF-only |

## Accepted commits

- `7e38c9db7` — Phase 0 architecture, migration map, roadmap, decisions, progress, upstream strategy, M01 plan, and glossary.

## Rejected or superseded commits

None.

## Pending commits

- `918c487d2` through `2716e5380` — M02 implementation, two correction packages, and the report chain; this final audit-trail revision is docs-only. Not accepted until the Commander re-review accepts M02.

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

M03–M06 execution in progress by the Worker under `worker-plans/M03-M06-plan.md`; M03-C0 landed (ledger + report start), next is M03-C1 stable domain identities. After the single M03–M06 Worker return, the Commander runs four milestone-scoped Superpowers second reviews.

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
