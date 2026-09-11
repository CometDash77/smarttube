# AI Subtitle Progress Ledger

Last updated: 2026-09-11

Authoritative branch: `feature/ai-bilingual-subtitles`

Personal project remote: `origin` → `https://github.com/CometDash77/smarttube.git` (GitHub reported public visibility during Phase 0)

Official source: `upstream` → `https://github.com/yuliskov/SmartTube.git`

## Current state

- Current milestone: M02 — CI Lane & Deterministic Dual-Subtitle Baseline
- Current task: `M02-FIX-01 — consolidated Commander correction package`
- Task state: CHANGES REQUIRED after Commander Review; M03 blocked
- Production code changes: M02 implementation present but not accepted
- Actual upstream patch count: 3 existing SmartTube files, all inside the approved M02 budget
- Remote sync: implementation/report through `3efb96374` uploaded; local Commander/report documentation is ahead of `origin/feature/ai-bilingual-subtitles`
- Local compile/test status: not used as acceptance evidence by user direction
- GitHub Actions status: run `34615801161` for `3efb96374` failed at `Lint beta release`; unit-test step ran first, assembly did not establish an accepted result

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

## Completed tasks

| Task | Result | Commit | Verification |
|---|---|---|---|
| Phase 0 reconnaissance | Accepted 2026-09-11 | `7e38c9db7` | Read-only evidence and document consistency checks |
| M02 first Worker delivery | Changes required | `918c487d2..26693c340` | Commander code/spec review; Actions run `34615801161` failed at lint |

## Accepted commits

- `7e38c9db7` — Phase 0 architecture, migration map, roadmap, decisions, progress, upstream strategy, M01 plan, and glossary.

## Rejected or superseded commits

None.

## Pending commits

- `918c487d2` through `26693c340` — M02 implementation and report chain; not accepted until `M02-FIX-01` passes Commander re-review and authoritative CI.

## Open problems

1. The selected Exo subtitle format does not expose the full timed-text URL through a stable app-level API. M02 must validate track matching and format-info cache behavior before deciding whether a loader hook is unavoidable.
2. Existing `SubtitleManager` disables embedded styles. M02 must prove whether a decorated two-line cue can retain separate source/translation styles without forking `SubtitlePainter`.
3. The current GitHub Actions workflow does not automatically validate the feature branch or explicitly invoke unit tests. The M02 Stage Package adds an isolated validation workflow before its product changes are accepted.
4. Android secret storage must balance Keystore protection with SmartTube's old-device support and backup/export behavior; decision deferred to a focused M04 spike.
5. KissTranslator tests/fixtures are GPL-covered repository content; target fixtures need independent authorship/provenance.
6. M02 synchronous Fake completion is cached but not returned on the first cue-processing call, so a one-shot caption can remain source-only.
7. M02 production code references `BooleanSupplier` (API 24) and `Objects` (API 19) despite the API 17 floor.
8. Turning the M02 switch off does not cancel immediately; invalidation waits for a later non-empty cue.
9. The three required `AiSubtitleDataTest` cases are ignored on JDK 17; ADR-010 authorizes a supplementary JDK 11 execution lane.

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

## Planned next action

Complete `docs/ai-subtitle/tasks/M02-FIX-01.md` as one correction package, obtain a green replacement GitHub Actions run, and return one amended M02 report. Commander then re-reviews M02 before issuing M03.

## Test status

- Phase 0: no production behavior changed, so no product test was run.
- M02 local diagnostic report: 31 pure-JVM tests passed; 3 Robolectric preference tests were skipped and are not accepted as coverage.
- M02 authoritative run `34615801161`: FAILURE at lint; validation reports artifact exists; no accepted assembly/APK result.
- Replacement authoritative CI baseline: pending `M02-FIX-01`.
