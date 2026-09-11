# AI Subtitle Progress Ledger

Last updated: 2026-09-11

Authoritative branch: `feature/ai-bilingual-subtitles`

Personal project remote: `origin` → `https://github.com/CometDash77/smartube.git` (GitHub reported public visibility during Phase 0)

Official source: `upstream` → `https://github.com/yuliskov/SmartTube.git`

## Current state

- Current milestone: M02 — CI Lane & Deterministic Dual-Subtitle Baseline
- Current task: `M02-FIX-02 — second-pass Commander correction package`
- Task state: corrections landed and locally verified; replacement CI run pending the push
- Production code changes: M02 implementation present; M02-FIX-01 corrections verified green by run `34618112103`; second-pass corrections verified locally
- Actual upstream patch count: 3 existing SmartTube files, all inside the approved M02 budget
- Remote sync: `origin/feature/ai-bilingual-subtitles` at `be02bc2b3`; the second-pass correction commit is local until pushed
- Local compile/test status: diagnostic only (34 JVM tests green on JDK 17; see Test status)
- GitHub Actions status: run `34618112103` green for `69f644f4a` (primary JDK 17 job plus supplementary JDK 11 preference job); run `34618912621` also verified; the second-pass replacement run is pending

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
- [x] Completed `M02-FIX-02` corrections: byte-level CRLF repair of the settings helper (rows 70–81), strengthened re-enable assertions with mutation evidence, and corrected report statements.

## Completed tasks

| Task | Result | Commit | Verification |
|---|---|---|---|
| Phase 0 reconnaissance | Accepted 2026-09-11 | `7e38c9db7` | Read-only evidence and document consistency checks |
| M02 first Worker delivery | Changes required | `918c487d2..26693c340` | Commander code/spec review; Actions run `34615801161` failed at lint |
| M02-FIX-01 correction | Green CI; second review required | `69f644f4a`, `be02bc2b3` | Run `34618112103` green (JDK 17 unit tests/lint/assembly + JDK 11 preference suite); Commander second pass found 3 blocking items |
| M02-FIX-02 correction | Local corrections verified; replacement CI pending | this commit | 34 JVM tests green on JDK 17; mutation check red at `AiSubtitleCueBridgeTest:267` without the post-re-enable delivery; settings helper byte-verified at 102 CRLF / 0 LF-only |

## Accepted commits

- `7e38c9db7` — Phase 0 architecture, migration map, roadmap, decisions, progress, upstream strategy, M01 plan, and glossary.

## Rejected or superseded commits

None.

## Pending commits

- `918c487d2` through `be02bc2b3` — M02 implementation, first correction, and report chain; not accepted until `M02-FIX-02` passes the Commander re-review and the replacement authoritative CI run.

## Open problems

1. The selected Exo subtitle format does not expose the full timed-text URL through a stable app-level API. M02 must validate track matching and format-info cache behavior before deciding whether a loader hook is unavoidable.
2. Existing `SubtitleManager` disables embedded styles. M02 must prove whether a decorated two-line cue can retain separate source/translation styles without forking `SubtitlePainter`.
3. The current GitHub Actions workflow does not automatically validate the feature branch or explicitly invoke unit tests. The M02 Stage Package adds an isolated validation workflow before its product changes are accepted.
4. Android secret storage must balance Keystore protection with SmartTube's old-device support and backup/export behavior; decision deferred to a focused M04 spike.
5. KissTranslator tests/fixtures are GPL-covered repository content; target fixtures need independent authorship/provenance.
6. Resolved by `M02-FIX-01`: M02 synchronous Fake completion is now consumed on the first cue-processing call.
7. Resolved by `M02-FIX-01`: no M02 production reference requires an API newer than 17 (`EnableState` seam; explicit null-safe equality).
8. Resolved by `M02-FIX-01`: disabling the setting cancels in-flight work and clears bridge state inside the settings callback.
9. Resolved by `M02-FIX-01`: the Robolectric preference suite executes in the supplementary JDK 11 job (3/3 passed in run `34618112103`).
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
- Accepted: one Stage Package and one consolidated Worker return per Milestone; internal commits do not create extra user handoffs.
- Accepted: JDK 17 remains authoritative; a narrow JDK 11 job may execute the inherited Robolectric 4.6.1 preference suite until its dependency is upgraded.
- Accepted: M02-FIX-02 stays inside the three-file upstream patch surface and leaves the two deferred refactors (duplicated equality helpers, bridge request-identity cluster) untouched.

## Planned next action

Push the second-pass correction once, obtain a green replacement GitHub Actions run, and return one amended M02 report. Commander then re-reviews M02 before issuing M03; M03 remains blocked.

## Test status

- Phase 0: no production behavior changed, so no product test was run.
- M02 local diagnostic report (first delivery): 31 pure-JVM tests passed; 3 Robolectric preference tests skipped and not accepted as coverage.
- M02 authoritative run `34615801161`: FAILURE at lint; validation-reports artifact exists; no accepted assembly/APK result.
- M02-FIX-01 authoritative run `34618112103`: green — bridge 19/19, controller 10/10, provider 5/5 on JDK 17; preference suite 3/3 on JDK 11; lint and beta assembly pass.
- M02-FIX-02 local diagnostics (JDK 17): 34 JVM tests green (bridge 19, controller 10, provider 5; preference suite deliberately skipped); mutation check red at `AiSubtitleCueBridgeTest:267` with the post-re-enable delivery removed and green again after restoring it; settings helper byte-verified at 102 CRLF / 0 LF-only lines with a 12/12 diff.
- Replacement authoritative CI for M02-FIX-02: pending the correction push.
