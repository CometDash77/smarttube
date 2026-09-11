# AI Subtitle Progress Ledger

Last updated: 2026-09-11

Authoritative branch: `feature/ai-bilingual-subtitles`

Personal project remote: `origin` → `https://github.com/CometDash77/smarttube.git` (GitHub reported public visibility during Phase 0)

Official source: `upstream` → `https://github.com/yuliskov/SmartTube.git`

## Current state

- Current milestone: M02 — CI Lane & Deterministic Dual-Subtitle Baseline
- Current task: `M02 — CI Lane & Deterministic Dual-Subtitle Baseline`
- Task state: READY FOR WORKER; consolidated Stage Package persisted for one user handoff
- Production code changes: none
- Actual upstream patch count: 0
- Remote sync: deferred by user direction; Phase 0 plan currently exists as a local Git commit
- Local compile/test status: not used as acceptance evidence by user direction
- GitHub Actions status: no feature-branch validation run yet; existing upstream `CI.yml` triggers pushes to `master` or manual dispatch and runs lint plus beta release assembly, but does not explicitly run unit tests

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

## Completed tasks

| Task | Result | Commit | Verification |
|---|---|---|---|
| Phase 0 reconnaissance | Accepted 2026-09-11 | `7e38c9db7` | Read-only evidence and document consistency checks |

## Accepted commits

- `7e38c9db7` — Phase 0 architecture, migration map, roadmap, decisions, progress, upstream strategy, M01 plan, and glossary.

## Rejected or superseded commits

None.

## Open problems

1. The selected Exo subtitle format does not expose the full timed-text URL through a stable app-level API. M02 must validate track matching and format-info cache behavior before deciding whether a loader hook is unavoidable.
2. Existing `SubtitleManager` disables embedded styles. M02 must prove whether a decorated two-line cue can retain separate source/translation styles without forking `SubtitlePainter`.
3. The current GitHub Actions workflow does not automatically validate the feature branch or explicitly invoke unit tests. The M02 Stage Package adds an isolated validation workflow before its product changes are accepted.
4. Android secret storage must balance Keystore protection with SmartTube's old-device support and backup/export behavior; decision deferred to a focused M04 spike.
5. KissTranslator tests/fixtures are GPL-covered repository content; target fixtures need independent authorship/provenance.

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

## Planned next action

When complete, the user may transport `docs/ai-subtitle/tasks/M02.md` once to the external Worker. The Worker completes all internal M02 workstreams and returns one consolidated report; the Commander reviews the whole milestone before issuing M03.

## Test status

- Phase 0: no production behavior changed, so no product test was run.
- Repository cleanliness and references: to be verified immediately before committing this document set.
- First authoritative CI baseline: pending the M02 Stage Package and a GitHub Actions run on its final commit.
