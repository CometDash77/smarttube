# M03–M06 Consolidated Worker Execution Plan

Status: **READY FOR ONE WORKER HANDOFF after M02 Commander PASS and this plan commit**

> **For agentic workers:** REQUIRED SKILLS: use `matt-skills-curated:implement-spec` to coordinate the complete plan and `matt-skills-curated:tdd` for every production behavior. Use `matt-skills-curated:grill-with-docs`, `research`, `prototype`, and `domain-modeling` only at their explicit triggers. The Worker performs the first review through the self-acceptance gates below; after the one final Worker return, the Commander performs the independent second review per milestone with `superpowers:requesting-code-review` and `superpowers:verification-before-completion`.

**Goal:** Hand M03 through M06 to one Worker once, complete them in one uninterrupted execution, and return once, while preserving four independently testable, reviewable, and reversible milestone ranges for later Commander review.

**Architecture:** M03 replaces the M02 text-keyed vertical-slice shortcuts with stable domain/session/cache contracts. M04 adds versioned Provider/Profile persistence, two shared wire-protocol adapters, model discovery, secret handling, and settings UI. M05 adds versioned Prompt Profiles and resolves Provider + Model + Prompt + Target Language atomically. M06 then clean-room reimplements subtitle normalization, timing, sentence breaking, chunking, Boundary Protocol validation, metrics, and deterministic AI-segmentation recovery. All production logic remains feature-owned under `common/.../ai/subtitle/`; SmartTube host files contain hooks only.

**Tech Stack:** Android TV, Java 8 syntax with Android API 17 runtime compatibility, Gradle 7.5, JUnit 4, Robolectric 4.6.1 on the existing narrow JDK 11 lane, OkHttp 3.12.13, RxJava 2 where lifecycle integration requires it, and GitHub Actions as the authoritative validation environment.

## Global constraints

- This file is the single M03–M06 Stage Program handed to the Worker once. The Worker proceeds through all four milestones without user relay or Commander review between them and returns one consolidated delivery only after M06.
- The single handoff does not collapse the four milestones into one commit or one diff. M03, M04, M05, and M06 each receive Worker self-acceptance and a pinned base/tip. After the Worker returns, the Commander reviews those four ranges separately.
- M02 must receive Commander PASS before the first M03 production edit. Until then, plan refinement and read-only reconnaissance are allowed; implementation is blocked.
- Milestone order on the integration branch is M03 -> M04 -> M05 -> M06. Research agents may explore a later unblocked question early. The Worker may advance after the preceding milestone's self-acceptance passes; it does not wait for Commander review.
- Work stays on `feature/ai-bilingual-subtitles`; `origin` is the project remote and `upstream` is read-only official SmartTube.
- SmartTube and all new target fixtures remain MIT-compatible. KissTranslator GPL source, tests, and fixtures are behavioral evidence only and must not be copied or mechanically translated.
- New feature files are preferred. No existing SmartTube file may be modified unless this plan names it, the reason is recorded before the edit, and `docs/ai-subtitle/upstream-patches.md` is updated in the same commit.
- Do not modify `MediaServiceCore`, `SharedModules`, `exoplayer-amzn-2.10.6`, Gradle dependency versions, the inherited `.github/workflows/CI.yml`, or renderer internals during M03–M06.
- Android API 17 compatibility is binding: do not introduce unguarded APIs added after 17 or Java-library APIs unavailable on the app floor.
- A Provider failure may never block, stop, seek, or re-prepare playback. Source-Only Fallback is the mandatory rendering result for missing, late, invalid, cancelled, or failed translation.
- No stale result may mutate current UI or cache. Every callback that can write state must prove active Session Generation, scheduling epoch where applicable, request ownership, and output identity.
- Cache identity includes every output-affecting input and never contains a credential.
- Streaming, retry scheduling, persistent translation cache, full real-time lookahead scheduling, and renderer-style changes remain outside M03–M06 unless an accepted ADR explicitly changes the roadmap.
- Each functional change follows observed RED -> minimal GREEN -> targeted regression -> full relevant suite. A test that passes before the intended implementation change is investigated and corrected.
- Local results are diagnostic. The Worker records and pushes each milestone tip when already authorized so exact-SHA GitHub Actions can run in parallel with later work. The final return must identify the result for all four milestone tips; only the Commander may issue the post-delivery milestone PASS after fresh verification and second review.
- Never report unavailable evidence as PASS. Use `NOT RUN`, `BLOCKED`, or `PENDING AUTHORIZED UPLOAD` with the exact reason.
- No `TBD`, implicit “handle edge cases,” or unowned follow-up is allowed in a worker plan/report. A newly discovered requirement is either resolved inside the authorized milestone, captured as a named blocked decision, or explicitly ruled out of scope.

---

## 1. Authority, state, and required reading

### Starting state

- Repository: `SmartTube/`.
- Integration branch: `feature/ai-bilingual-subtitles`.
- Plan authoring base: `a6324c9e0` (`docs(ai-subtitle): close M02 audit trail`).
- M02 product validation SHA: `854460bb4`; authoritative workflow run `34660051184` was green, but the current progress ledger still records M02 as awaiting Commander re-review.
- Existing M02 contracts: `TranslationProvider`, `TranslationCall`, `TranslationCallback`, `TranslationRequest`, `TranslationResult`, `FakeTranslationProvider`, `AiSubtitleCueBridge`, `AiSubtitleController`, and `AiSubtitleData`.
- Known compatibility constraint: ordinary unit tests/lint/assembly run on JDK 17; `AiSubtitleDataTest` remains on the narrow JDK 11 Robolectric lane under ADR-010.
- Known line-ending constraint: `SubtitleSettingsPresenter.java` is stored as CRLF. Any authorized future edit must preserve 0 LF-only lines and be staged without conversion churn.

At execution start the coordinator must replace this snapshot in the live execution ledger with the actual `git rev-parse HEAD`, branch, `git status --short`, submodule state, and current workflow baseline. The historical values above remain unchanged as provenance.

### Live execution ledger

Execution snapshot at Worker start (2026-09-12): `HEAD = 422451df87917a3f932c60291f4907d31f1f21ff`, branch `feature/ai-bilingual-subtitles` (ahead 1, clean), submodules `MediaServiceCore 82e9ccde` / `SharedModules 86f0327`, M02 Commander PASS relayed by the user on 2026-09-12.

| UTC timestamp | Milestone/Ticket | State | Base..Tip | Evidence | Docs changed | Next unblocked item |
|---|---|---|---|---|---|---|
| 2026-09-12T02:10Z | M03-C0 | MERGED | `422451df8..M03-C0` | M02 Commander PASS (user relay 2026-09-12); clean branch; submodules pinned; G03-1 = NO GRILL REQUIRED (architecture §5/§9, plan §8) | `M03-report.md`, `progress.md`, this ledger | M03-C1 domain identities |
| 2026-09-12T02:18Z | M03-C1 | MERGED | M03-C0..M03-C1 | Witnessed RED 50 tests/32 failed then GREEN 50/50; full ai-subtitle regression 84 passed, 0 failed (JDK 17 local diagnostics); CONTEXT.md unchanged (no semantic change) | `M03-report.md`, `progress.md`, this ledger | M03-C2 session identity |
| 2026-09-12T02:28Z | M03-C2 | MERGED | M03-C1..M03-C2 | Witnessed RED 24 tests/19 failed (session scaffold) then GREEN 24/24; full regression 118 passed, 0 failed; mutation checks: generation 2 named failures, epoch 1, request-identity 1 — all reverted and re-verified green | `M03-report.md`, `progress.md`, this ledger | M03-C3 cache identity |
| 2026-09-12T02:38Z | M03-C3 | MERGED | M03-C2..M03-C3 | Witnessed RED 26 tests/6 failed (cache scaffold) then GREEN 26/26; full regression 150 passed, 0 failed, 3 skipped; mutation: dropping the base-URL comparison fails baseUrlIdentityIsIsolated (reverted, re-verified green); profile gained protocol + credential-free base URL identity | `M03-report.md`, `progress.md`, this ledger | M03-C4 provider-neutral contracts |
| 2026-09-12T02:48Z | M03-C4 | MERGED | M03-C3..M03-C4 | Witnessed RED 171 tests/6 failed (contract scaffold) then GREEN 168 passed / 0 failed / 3 skipped; contracts carry session + unit identity, final/partial state, and normalized failure categories; Fake/bridge/controller tests adapted | `M03-report.md`, `progress.md`, this ledger | M03-C5 checkpoint |
| 2026-09-12T02:51Z | M03-C5 | MERGED | M03-C4..M03-C5 | M03 Worker self-acceptance complete: static gate passed (0 non-feature paths, host diff empty, brand/network scans clean, lint green); product tip `52877ed96`; exact-SHA CI `BLOCKED` at workstation API access (user/browser verification required) | `M03-report.md`, `progress.md`, this ledger | M04-C0 provider security and capability policy |
| 2026-09-12T03:05Z | M04-C0 | MERGED | `8a4bd175b`..M04-C0 | G04-1 settled → ADR-012 (local SDK API-database evidence: KeyGenParameterSpec/KeyProperties = API 23; allowBackup=true with no exclusion rules; documented API 17-22 fallback; no host-manifest change); G04-2 settled (runtime capability checks, manual model id first-class, discovery failure preserves profiles); no production network code precedes these decisions | `research/g04-1-android-secret-storage.md`, `research/g04-2-provider-capabilities.md`, `decisions.md`, `M04-report.md`, `progress.md`, this ledger | M04-C1 provider profile persistence |
| 2026-09-12T03:28Z | M04-C1 | MERGED | M04-C0..`3e1e1ed60` | Witnessed RED compile scaffolds (34 + 8 + 8 + 13 + 7 missing symbols) plus one invalid-collection failure, then GREEN 193 passed / 0 failed / 6 skipped on JDK 17 and 6/6 on JDK 11; mutations for selection repair / credential-reference serialization / future-schema rejection produced 4 / 2 / 1 named failures and were reverted; app-profile switching, corrupt repair, stable IDs, CRUD, enabled-flag preservation, and credential-free schema verified; lint green | `M04-report.md`, `progress.md`, `CONTEXT.md`, this ledger | M04-C2 provider credential protection |
| 2026-09-12T03:40Z | M04-C2 | MERGED | M04-C1..M04-C2 | Witnessed RED 57 missing-symbol compile failures plus a 3-symbol API-policy RED, then GREEN 207 passed / 0 failed / 7 skipped on JDK 17 and 41/41 on JDK 11; API 17 plaintext fallback and API 23 Keystore path selection explicit; deletion/update/reset secret cleanup, masking, safe AUTH failures, redaction, and auth-to-source-only behavior covered; cleanup-threshold mutations produced 3 / 1 named failures and were reverted; secret scan and lint clean | `M04-report.md`, `progress.md`, `.github/workflows/ai-subtitle-validation.yml`, this ledger | M04-C3 OpenAI-compatible responses |


### Read completely before dispatching any worker

1. `AGENTS.md`
2. `CONTEXT.md`
3. `docs/ai-subtitle/architecture.md`
4. `docs/ai-subtitle/migration-map.md`
5. `docs/ai-subtitle/roadmap.md`
6. `docs/ai-subtitle/decisions.md`
7. `docs/ai-subtitle/progress.md`
8. `docs/ai-subtitle/upstream-patches.md`
9. `docs/ai-subtitle/tasks/M02.md`
10. `docs/ai-subtitle/worker-plans/M02-plan.md`
11. `docs/ai-subtitle/worker-reports/M02-report.md`
12. all current files under `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/`
13. matching tests under `common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/`
14. `.github/workflows/ai-subtitle-validation.yml`
15. relevant SmartTube persistence, dialog, and HTTP patterns discovered for the active task

### Plan authority

When sources disagree, resolve in this order:

1. the latest explicit user decision;
2. accepted ADRs in `docs/ai-subtitle/decisions.md`;
3. this plan's Global constraints and milestone acceptance criteria;
4. milestone-specific live decisions recorded through the Grill with Docs gate;
5. implementation convenience.

Do not silently “interpret around” a conflict. Record the exact conflicting clauses in the live ledger and obtain one ruling before affected production work proceeds.

## 2. Execution DAG and milestone gates

```text
M02 Commander PASS
        |
        v
M03 implementation -> Worker M03 self-acceptance -> record M03 tip
        |
        +--------------------+
        |                    |
        v                    v
M04 implementation       M06 research + fixture authorship only
        |
        v
Worker M04 self-acceptance -> record M04 tip -> M05 implementation
        |
        v
Worker M05 self-acceptance -> record M05 tip -> M06 production
        |
        v
Worker M06 self-acceptance -> one consolidated Worker return
        |
        v
Commander second review: M03 range, M04 range, M05 range, M06 range
```

- M04 production and M06 production are not merged concurrently. M06 research/fixture provenance work may run after M03 if it touches only its isolated worktree and produces no integration-branch commit before M05 self-acceptance.
- The Worker pins each milestone's base and tip and evaluates its own checks against that range, not `HEAD~1`.
- Worker advancement requires the milestone's self-acceptance checklist and fresh relevant verification. It does not require a Commander response.
- After the single final return, the Commander reviews each pinned range independently. Commander findings never get blended into one M03–M06 verdict.
- If Commander review finds Critical/Important issues, corrections are packaged by affected milestone/range, executed by a Worker, re-verified, and re-reviewed before that milestone is accepted.

## 3. Skill routing

| Situation | Required skill | Output |
|---|---|---|
| Coordinate this complete, approved multi-milestone plan | `matt-skills-curated:implement-spec` | dependency-aware worktree dispatch, merge order, milestone gates |
| Convert a milestone section into fresh-agent-sized execution units | `matt-skills-curated:to-tickets` | local ticket/DAG entries with blockers and binary acceptance checks |
| Add or change production behavior | `matt-skills-curated:tdd` | witnessed red, minimal green, targeted and regression evidence |
| Implement one approved ticket | `matt-skills-curated:implement` | scoped code, tests, verification, commit |
| Resolve an unsettled domain/architecture choice | `matt-skills-curated:grill-with-docs` | batched questions plus immediate glossary/ADR/plan updates |
| Define or rename a domain concept | `matt-skills-curated:domain-modeling` | immediate `CONTEXT.md` alignment; ADR only at its strict threshold |
| Verify Android/provider/API/security facts | `matt-skills-curated:research` | cited primary-source Markdown note; facts separated from recommendations |
| Test a load-bearing state/UI option that cannot be settled from evidence | `matt-skills-curated:prototype` | throwaway isolated branch; only the resulting decision merges |
| Worker first-pass review before advancing | milestone self-acceptance in this plan + `superpowers:verification-before-completion` | fresh evidence, exact pinned range, honest open risks |
| Commander second review after final Worker return | `superpowers:requesting-code-review` + `superpowers:verification-before-completion` | independent review per M03/M04/M05/M06 range and evidence-backed verdict |
| Execute the entire accepted specification with isolated agents | `matt-skills-curated:implement-spec` | unified branch with dedicated merger verification |

`wayfinder` is not the default execution skill because the M03–M06 destination and major boundaries are already accepted. Invoke it only if execution discovers multiple interdependent architectural unknowns that cannot be settled by one bounded Grill with Docs round. If invoked, it maps decisions only and must not become a parallel implementation track.

## 4. Subagent usage guide

### Roles and concurrency

- The coordinator owns this plan, the dependency graph, exact base SHAs, live Markdown ledger, merge order, and final truthfulness. The coordinator does not hand-wave verification or let an implementer approve its own milestone.
- An implementer owns one context-window-sized ticket and one isolated worktree/branch. It writes tests, witnesses RED, implements, verifies, self-reviews, commits, and returns one structured status.
- A research agent answers one bounded factual question from primary sources and writes a cited note. It does not edit production code.
- A Grill with Docs agent prepares evidence-backed questions, but the coordinator owns user interaction and immediately persists settled answers.
- A merger integrates only reviewed commits into `feature/ai-bilingual-subtitles`, resolves no semantic conflict by guesswork, and reruns the declared gate.
- The Worker may use a fresh reviewer to strengthen its first pass, but remains responsible for completing the milestone self-acceptance checklist. This is not the Commander review.
- After the final return, the Commander dispatches a fresh reviewer separately for each milestone range using Superpowers; the review prompt receives that milestone's plan section, report, base/tip, and diff package, not the Worker's conversation history.
- With four total agent slots, use at most three subagents concurrently so the coordinator remains active. Prefer two implementers plus one research/review agent only when their file ownership and blockers are disjoint.

### Isolation and branch naming

- Create every implementer worktree from a recorded SHA, never from an uncommitted integration worktree.
- Branch form: `codex/m03-<ticket>`, `codex/m04-<ticket>`, `codex/m05-<ticket>`, or `codex/m06-<ticket>`.
- One worktree may not be shared by two agents. Two concurrent tickets may not own the same production or test file.
- Never ask a subagent to commit directly on the integration branch. Only the merger/coordinator updates it.
- Keep prototypes on `codex/prototype-<decision>` and do not merge prototype code. Merge only its decision note/ADR after review.

### Dispatch packet

Every implementer receives pointers, not a transcript dump:

1. this plan path and the exact ticket/commit ID;
2. pinned base SHA and assigned worktree/branch;
3. exact owned files and forbidden files;
4. consumed/produced interface signatures;
5. copied Global constraints that matter to the ticket;
6. required RED test names and expected failure reason;
7. exact local verification commands and expected outcome;
8. commit subject/body requirements;
9. required final status format.

Every implementer returns exactly one of:

- `DONE`: commit SHA, files, witnessed RED evidence, GREEN/full-suite evidence, self-review result, docs updated, risks.
- `DONE_WITH_CONCERNS`: same fields plus precise concern and affected acceptance criterion.
- `NEEDS_CONTEXT`: missing fact/interface and why repository evidence did not answer it.
- `BLOCKED`: repeated failure evidence, attempted remedies, smallest decision or authority needed.

Do not retry a blocked agent unchanged. Supply missing context, strengthen the model/role, split the ticket, or escalate a real plan conflict.

### Review and merge protocol

1. Record `BASE=$(git rev-parse HEAD)` before dispatch.
2. After `DONE`, build a review package for `BASE...worker-tip` containing commit list, stat, and full contextual diff.
3. Run task-level spec and quality review before merge. Critical and Important findings return to a fix worker and are re-reviewed inside the same uninterrupted Worker execution.
4. Merge reviewed commits in DAG order. Preserve logical commit boundaries; do not squash unrelated capabilities into one opaque commit.
5. Run targeted tests after each merge and the milestone self-acceptance gate after the final merge in that milestone.
6. Record Minor findings in the live ledger; the Worker report must carry them to the Commander, who explicitly disposes of them during the affected milestone's second review.
7. Delete an isolated worktree only after its commit is merged, verified, and recoverable from the branch/repository.

## 5. Real-time Markdown update contract

Documentation is part of the implementation state, not a report written from memory at the end.

### Always-live files

- `docs/ai-subtitle/worker-plans/M03-M06-plan.md`: tick a checkbox only after its evidence exists; append a dated execution-ledger row after every merged commit, correction, gate decision, or blocker.
- `docs/ai-subtitle/progress.md`: update Current state, Completed tasks, Pending/Accepted commits, Open problems, Planned next action, and Test status in the same commit that changes those facts.
- `docs/ai-subtitle/worker-reports/M03-report.md` through `M06-report.md`: create at milestone start and append evidence as it is produced. Before the final documentation commit, replace provisional language with exact SHAs/counts/URLs; retain honest historical failures.

### Update-on-trigger files

- `CONTEXT.md`: update in the same decision commit when a domain term is introduced, split, renamed, or semantically changed. Keep it implementation-free.
- `docs/ai-subtitle/decisions.md`: append a new ADR immediately when a choice is hard to reverse, surprising without context, and the result of a real trade-off. Supersede prior decisions; never rewrite history.
- `docs/ai-subtitle/upstream-patches.md`: update in the same commit as any existing SmartTube file change, with exact reason, logical line budget, alternatives, and verification.
- `docs/ai-subtitle/research/<topic>.md`: create before relying on a temporally unstable API/security/platform fact. Use only primary sources and distinguish fact from recommendation.
- `docs/ai-subtitle/fixture-provenance.md`: create with the first M06 fixture and update in the same commit as every fixture addition or revision.

### Ledger row format

```text
| UTC timestamp | Milestone/Ticket | State | Base..Tip | Evidence | Docs changed | Next unblocked item |
```

Allowed states are `PLANNED`, `RED`, `GREEN_LOCAL`, `MERGED`, `CI_PENDING`, `PASS`, `CHANGES_REQUIRED`, and `BLOCKED`. Never rewrite a failed row into PASS; append the later correction/result.

### Documentation commit rules

- A functional commit includes its tests and the documentation whose truth changed because of that function.
- Do not defer glossary, ADR, upstream-ledger, or progress changes to a milestone-end “docs sweep.”
- The milestone report may have one final docs-only commit for exact CI URLs, immutable SHAs, review disposition, and final counts that could not exist before upload.
- Documentation must identify evidence scope: local diagnostic, GitHub Actions authoritative, device, static inspection, mutation check, or reviewer judgment.
- No secret, authorization header, raw API key, full prompt payload, or subtitle content from a user's real video may appear in Markdown, logs, screenshots, or artifacts.

## 6. Commit standard

### Required granularity

- One commit represents one reviewable capability or one narrowly scoped correction.
- Keep production code, its behavioral tests, and directly affected live docs together.
- Do not create one commit per file. Do not combine provider persistence, protocol adapters, UI, prompt management, and segmentation in one commit.
- A commit must leave the integration branch buildable and must not knowingly break an earlier accepted milestone.
- Refactors needed to expose a seam use an Expand -> Migrate -> Contract sequence and have separate commits when each step is independently safe.

### Required message format

Subject:

```text
<type>(ai-subtitle): <imperative user-visible capability>
```

Allowed types are `feat`, `fix`, `refactor`, `test`, `docs`, and `ci`. Use a more specific scope such as `provider`, `prompt`, `segmentation`, `session`, or `settings` when it improves retrieval.

Every non-trivial commit body must contain:

```text
Milestone: M0X
Plan task: M0X-CY
Why: <the invariant or user behavior enabled>
What: <2-5 concrete changes>
Verification: <exact test task and result; CI pending if not yet available>
Docs: <updated Markdown files or "none — no documented fact changed">
Risk: <remaining bounded risk or "none known">
```

The subject describes value, not mechanics. `update files`, `misc fixes`, `WIP`, `address review`, and giant file lists are invalid subjects. Correction commits name the violated invariant, for example `fix(session): reject stale results after profile change`.

### Pre-commit self-check

- [ ] Ticket-owned files only; no unreviewed concurrent edits.
- [ ] RED was witnessed for the intended missing behavior.
- [ ] Targeted test is green and earlier milestone regression tests remain green.
- [ ] `git diff --check` is clean and line endings/modes are unchanged unless explicitly intended.
- [ ] Android API 17 compatibility and secret redaction were inspected where relevant.
- [ ] Live plan/progress/report and triggered glossary/ADR/upstream/provenance docs are current.
- [ ] Commit body contains exact verification and no claim broader than the evidence.

## 7. Grill with Docs protocol

### Trigger threshold

Start a Grill with Docs round only when all of the following hold:

1. the repository and existing documents do not already settle the question;
2. at least two materially different choices remain;
3. the choice affects a public/domain contract, security/privacy policy, compatibility floor, persistent data, user-visible behavior, or multiple later tasks;
4. choosing a default without the user could cause rework outside the current ticket.

Do not grill naming, formatting, helper placement, ordinary test cases, or reversible implementation detail. The worker resolves those from conventions.

### Before asking

- Read the relevant code, schemas, accepted ADRs, and earlier reports.
- For external facts, run `research` against current primary sources and save the cited note.
- State the observed facts separately from the recommendation.
- Batch independent frontier questions in one numbered round. Each question gives a recommended default, alternatives, concrete trade-offs, and which tasks are blocked.
- Limit a round to the smallest set that unlocks the next DAG frontier; do not interview distant speculative work.

### Real-time persistence

After each settled answer, before affected production code:

1. update `CONTEXT.md` if domain language changed;
2. append an ADR only if the three-part ADR threshold is met;
3. update the exact plan task, interface, acceptance check, and commit boundary;
4. update `progress.md` and the active milestone report;
5. commit the decision documents with subject `docs(ai-subtitle): settle <decision>` unless they naturally belong in the immediately following functional commit.

### Mandatory decision gates

| Gate | Must settle | Recommended default already supported by architecture | Blocks |
|---|---|---|---|
| G03-1 | exact Session/Track/Profile identity and cache-key version fields | immutable value objects; explicit engine/segmentation versions; credentials excluded | M03-C1..C4 |
| G04-1 | Android secret storage, backup/export exclusion, and API 17 fallback | Keystore-backed encryption where supported; separately stored non-exportable secret reference; documented compatibility fallback | M04-C1..C2 |
| G04-2 | Provider capability failure and model-discovery UX | runtime capability check; manual Model ID remains first-class; discovery failure does not invalidate a saved profile | M04-C4..C6 |
| G05-1 | prompt variable syntax, escaping, missing/unknown handling, and built-in immutability | strict allow-list; unknown/missing variable is a validation failure; built-ins copied before editing | M05-C1..C3 |
| G06-1 | fixture provenance boundary and independent authorship evidence | synthetic/minimal factual fixtures authored from behavioral categories, with no copied strings/arrays/timings | M06-C0..C5 |
| G06-2 | rule/statistical thresholds and accepted-prefix fallback observables | deterministic versioned defaults; validated continuous prefix; one tail retry; deterministic smaller-unit/source-only fallback | M06-C2..C5 |

If existing accepted documentation plus code evidence already settles a gate, record `NO GRILL REQUIRED` with citations in the ledger. Do not ask the user to reconfirm an accepted decision.

### Exit criterion for a round

A Grill with Docs round is complete when every next-frontier worker has exact input/output contracts and binary acceptance checks. “We discussed it” is not an exit criterion. Unsettled distant questions stay outside the current frontier and do not accumulate speculative prose.

## 8. M03 — AI subtitle domain and session core

### Objective and boundary

Replace M02's normalized-source-text identity and bridge-owned lifecycle maps with stable, provider-neutral domain/session/cache contracts. M03 performs no HTTP, persistence migration, Provider brand/preset, prompt CRUD, real subtitle segmentation, or scheduler implementation.

### Planned files

Create under `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/`:

- `domain/SourceTrackId.java`
- `domain/SourceCue.java`
- `domain/SubtitleSegment.java`
- `domain/SubtitleSegmentId.java`
- `domain/TranslationUnit.java`
- `domain/TranslationProfile.java`
- `session/TranslationSessionId.java`
- `session/TranslationSession.java`
- `session/TranslationSessionSnapshot.java`
- `cache/TranslationCache.java`
- `cache/InMemoryTranslationCache.java`
- `cache/TranslationCacheKey.java`
- `translation/TranslationFailure.java`
- `translation/TranslationFailureCategory.java`
- `translation/TranslationStream.java`

Modify feature-owned M02 files as needed:

- `translation/TranslationRequest.java`
- `translation/TranslationResult.java`
- `translation/TranslationProvider.java`
- `translation/TranslationCallback.java`
- `translation/FakeTranslationProvider.java`
- `integration/AiSubtitleCueBridge.java`
- `integration/AiSubtitleController.java`

Create matching pure-JVM tests under `common/src/test/java/.../ai/subtitle/{domain,session,cache,translation,integration}/`.

No existing non-feature SmartTube Java/XML file is authorized in M03.

### Stable interfaces produced by M03

```java
public final class TranslationSessionId {
    public TranslationSessionId(String videoId, SourceTrackId sourceTrackId,
                                TranslationProfile profile, int engineSchemaVersion);
}

public final class TranslationCacheKey {
    public static TranslationCacheKey from(TranslationSessionId sessionId,
                                           TranslationUnit unit,
                                           String contextFingerprint,
                                           int segmentationVersion,
                                           int boundaryVersion);
}

public interface TranslationCache {
    TranslationResult get(TranslationCacheKey key);
    void put(TranslationCacheKey key, TranslationResult result);
    void clear();
}
```

Exact constructors may be narrowed by G03-1 before implementation, but consumers must receive immutable values and equality/hash behavior compatible with API 17.

### Commit sequence

#### M03-C0 — `docs(ai-subtitle): start consolidated M03 execution ledger`

- [ ] Confirm M02 Commander PASS, clean branch, exact base SHA, submodules, and green baseline workflow.
- [ ] Create `M03-report.md` with scope, baseline, planned commit table, and all evidence fields marked `NOT RUN` rather than omitted.
- [ ] Resolve G03-1 or record evidence that accepted docs already settle it.

Worker self-acceptance:

- [ ] No production file changed.
- [ ] The M03 base SHA and baseline workflow are immutable and reproducible.
- [ ] The next worker can name every identity field without guessing.

#### M03-C1 — `feat(domain): add stable subtitle timeline identities`

- [ ] Add immutable `SourceTrackId`, `SourceCue`, `SubtitleSegmentId`, `SubtitleSegment`, `TranslationUnit`, and `TranslationProfile` value objects.
- [ ] RED tests prove equality/hash stability, ordered continuous segment coverage, invalid time ranges, empty identity fields, and same text at different timeline positions remaining distinct.
- [ ] GREEN implementation performs validation without Android/runtime dependencies.
- [ ] Update `CONTEXT.md` immediately for any changed semantic definition.

Worker self-acceptance:

- [ ] Same text at different segment IDs is not equal and cannot alias in a lookup.
- [ ] A Translation Unit cannot contain unordered/non-contiguous segment IDs or silently drop source coverage.
- [ ] Tests assert independent literal expectations, not mirrored helper logic.
- [ ] M02 regression tests still pass.

#### M03-C2 — `feat(session): enforce generation and scheduling epoch ownership`

- [ ] Add `TranslationSessionId`, `TranslationSession`, and immutable snapshots/state transitions.
- [ ] RED tests cover new video, track off/on/change, Provider/Profile/model/prompt/language identity changes, seek epoch changes, pause/resume, close/reopen, duplicate invalidation, and late callbacks.
- [ ] Move lifecycle ownership out of text-keyed bridge maps while keeping `AiSubtitleController` and `AiSubtitleCueBridge` public seams stable.
- [ ] A seek advances the scheduling epoch but not Session identity; every identity change advances generation and clears/cancels owned state.

Worker self-acceptance:

- [ ] Mutation-style check demonstrates that removing generation, epoch, or request-owner comparison makes a named test fail.
- [ ] No obsolete callback can alter current session/cache/render lookup.
- [ ] Repeated lifecycle events are idempotent and never escape into playback.
- [ ] Source-Only Fallback remains the result of all invalid/missing state.

#### M03-C3 — `feat(cache): key in-memory translations by complete output identity`

- [ ] Add `TranslationCache`, `InMemoryTranslationCache`, and immutable `TranslationCacheKey`.
- [ ] RED tests vary one output-affecting field at a time: engine schema version, video, Source Track, source coverage/text hash, Provider Profile ID, protocol/base URL identity, model, Prompt Profile ID/content version, target language, context fingerprint, segmentation version, and Boundary Protocol version.
- [ ] Tests prove credentials are absent from equality, `toString`, debug output, and persisted/loggable representations.
- [ ] Replace M02 source-text lookup with segment/unit identity.

Worker self-acceptance:

- [ ] Equal source text across video/track/profile/timeline cannot collide.
- [ ] Every field listed in architecture section 9 has an isolation test.
- [ ] Cache stores only accepted final results; failures/status sentinels are not cached as translation text.
- [ ] Cache is in-memory and bounded to the active session scope in M03.

#### M03-C4 — `refactor(translation): stabilize provider-neutral request contracts`

- [ ] Evolve request/result/callback/stream/failure types to carry session, cache key/unit identity, request ownership, final/partial state, and normalized failure category without Provider brands.
- [ ] Preserve a complete non-streaming success path as the baseline contract.
- [ ] Adapt `FakeTranslationProvider` and bridge/controller tests to the stable contracts.
- [ ] RED tests cover synchronous completion, asynchronous completion, cancellation, exception isolation, invalid result identity, partial result ownership, and terminal/retryable failure representation.

Worker self-acceptance:

- [ ] Player/renderer classes contain no protocol, HTTP, prompt, retry, or persistence details.
- [ ] Translation contracts contain no OpenAI/Anthropic/brand-specific field.
- [ ] Fake path still produces exact source + newline + `[ZH] source` on its valid baseline.
- [ ] API 17 lint compatibility is preserved.

#### M03-C5 — `docs(ai-subtitle): record M03 domain and session checkpoint`

- [ ] Complete every M03 Worker self-acceptance item against M03 base...tip and independently inspect both standards and spec coverage.
- [ ] Fix and re-check all Worker-discovered Critical/Important findings before this commit.
- [ ] Complete `M03-report.md`, `progress.md`, plan ledger, and any triggered decisions/glossary entries.
- [ ] Push only when authorized; launch the exact-SHA workflow and record URL/run ID/SHA plus current `CI_PENDING`, PASS, or FAIL state. The Worker may continue while pending but must return to any failure immediately.

M03 exit/self-acceptance:

- [ ] Deterministic tests prove no cross-video, cross-track, cross-profile, or same-text/different-segment contamination.
- [ ] All M02 regression tests pass.
- [ ] No network request, real Provider profile, prompt manager, or segmentation pipeline exists.
- [ ] No non-feature SmartTube file changed.
- [ ] Exact-SHA GitHub Actions is launched and recorded. Green is mandatory before the final M03–M06 return; a pending run does not authorize a PASS claim.
- [ ] M03 Worker self-acceptance is complete; its base/tip and evidence are frozen for later Commander second review, and the same Worker continues to M04 without user relay.

## 9. M04 — Provider, model, persistence, and connection management

### Objective and boundary

Implement five user-facing Provider Types through two shared normal-response protocol adapters. Persist versioned non-secret profile data, protect secrets under the accepted G04-1 policy, support model discovery plus manual Model ID, expose CRUD/test/select UI, and prove Source-Only Fallback on Provider failure. SSE parsing, prompt CRUD, scheduler retries, and persistent translation cache remain out of scope.

### Planned feature-owned files

Create or refine:

- `settings/AiSubtitleSchema.java`
- `settings/AiSubtitleData.java`
- `settings/ProviderProfileRepository.java`
- `settings/ProviderProfileSerializer.java`
- `settings/ProviderProfileMigration.java`
- `settings/SecretStore.java`
- `settings/AndroidSecretStore.java`
- `provider/ProviderType.java`
- `provider/ProviderProtocol.java`
- `provider/ProviderCapabilities.java`
- `provider/ProviderPreset.java`
- `provider/ProviderProfile.java`
- `provider/ProviderProfileResolver.java`
- `provider/ProtocolAdapter.java`
- `provider/OpenAiChatCompletionsAdapter.java`
- `provider/AnthropicMessagesAdapter.java`
- `provider/ModelCatalog.java`
- `provider/ConnectionTestResult.java`
- `provider/TranslationFailureMapper.java`
- `provider/http/HttpRequestExecutor.java`
- `provider/http/OkHttpRequestExecutor.java`
- `settings/ui/AiSubtitleSettingsPresenter.java`
- `settings/ui/ProviderProfilesPresenter.java`
- `settings/ui/ProviderProfileEditorPresenter.java`
- feature resource files under `common/src/main/res/values/ai_subtitle_*.xml`
- `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/presenters/settings/SubtitleSettingsPresenter.java` (replace the M02 test-provider switch with one narrow entry into the feature-owned AI settings flow; preserve CRLF and update the upstream ledger in the same commit)

The existing M02 `SubtitleSettingsPresenter.java` hook is the sole host entry point and may be revised once in M04-C6 so it opens the feature-owned settings flow instead of exposing only the test-provider switch. The host file may contain only the import/call or option-construction needed for that handoff; Provider/Profile/secret/model logic remains in feature-owned presenters. Preserve the file's CRLF bytes, stage without automatic line-ending conversion, and record the exact logical-line diff in `upstream-patches.md`. Any wider host edit requires a new Grill/ADR/upstream-budget decision.

### Commit sequence

#### M04-C0 — `docs(ai-subtitle): settle provider security and capability policy`

- [ ] Run G04-1 using current Android/SmartTube backup and minimum-API evidence; write a primary-source research note and ADR if the accepted choice meets the threshold.
- [ ] Run G04-2 using current official Provider documentation; record exact protocol/capability facts and editable defaults in a cited research note.
- [ ] Create `M04-report.md` and pin the M04 base.

Worker self-acceptance:

- [ ] Secret behavior is defined for API 17 and every supported API band, including unavailable/invalidated key material, backup/export, reset, delete, and migration.
- [ ] Provider presets are defaults, not hard-coded capability claims.
- [ ] No production network code precedes these decisions.

#### M04-C1 — `feat(settings): version provider profile persistence`

- [ ] RED Robolectric/pure-JVM tests cover empty-store defaults, schema upgrade, unknown future schema rejection, corrupt JSON/default repair, stable IDs, create/update/delete, selected/default repair, app-profile switch, and secret-reference separation.
- [ ] Implement non-secret Provider Profile serialization/repository/migrations in `AiSubtitleData` without extending `PlayerData`.
- [ ] Preserve the current enabled flag through migrations.

Worker self-acceptance:

- [ ] Restart and singleton recreation preserve valid profiles and selections.
- [ ] Deleting selected/default profiles repairs state deterministically.
- [ ] Corrupt data recovers without crashing playback or erasing unrelated settings.
- [ ] Serialized/exportable profile payload contains no API key.

#### M04-C2 — `feat(settings): protect provider credentials across Android versions`

- [ ] RED tests/fakes cover save/read/update/delete, masked display, missing key material, invalidation, API-floor fallback, profile deletion, and redaction of exceptions/log objects.
- [ ] Implement `SecretStore` behind the accepted policy and keep secret references separate from profile JSON.
- [ ] Add explicit secret clearing when a profile is deleted or reset.

Worker self-acceptance:

- [ ] Repository-wide secret scan finds no test key outside approved synthetic fixtures and no committed real secret.
- [ ] `toString`, reports, thrown errors, request diagnostics, backups/exports covered by policy, and UI never reveal full keys.
- [ ] Failure to read a secret yields a normalized auth/configuration failure and Source-Only Fallback, not playback failure.

#### M04-C3 — `feat(provider): add OpenAI-compatible normal responses`

- [ ] RED tests use a fake `HttpRequestExecutor`, never the public network, to assert base-URL normalization, path construction, headers, JSON escaping/serialization, normal response parsing, empty/malformed response handling, cancellation, timeout propagation, request ID capture, and error mapping.
- [ ] Implement `OpenAiChatCompletionsAdapter` and the generic OpenAI-Compatible preset path.
- [ ] Keep retry and SSE outside the adapter contract for this milestone.

Worker self-acceptance:

- [ ] Authorization values are never returned from diagnostics.
- [ ] Adapter owns wire concerns only; it cannot access player/session scheduling/UI.
- [ ] Non-streaming translation reaches the M03 provider-neutral success/failure seam.

#### M04-C4 — `feat(provider): add Anthropic-compatible normal responses`

- [ ] RED tests cover system/body placement, required version/max-token policy from the accepted research note, both authorized authentication shapes, JSON parsing, named error bodies, cancellation, timeout, request ID, and normalized failures.
- [ ] Implement `AnthropicMessagesAdapter` without duplicating scheduling/profile/UI logic.

Worker self-acceptance:

- [ ] Shared normalized result/failure behavior matches the OpenAI adapter.
- [ ] Provider-specific optional fields are capability-gated, not inferred solely from display name.
- [ ] SSE remains absent.

#### M04-C5 — `feat(provider): add presets and model discovery`

- [ ] Add OpenRouter, DeepSeek, and MiMo editable presets over the two adapters.
- [ ] RED tests cover per-profile capabilities, base override, optional headers, discovery success/failure/unsupported, duplicate/blank model IDs, selected-model repair, manual Model ID, and cancellation.
- [ ] Implement `ModelCatalog` and normalized `ConnectionTestResult`.

Worker self-acceptance:

- [ ] All five Provider Types can resolve to one of two adapters without five client implementations.
- [ ] Model discovery failure leaves manual entry usable.
- [ ] Connection test distinguishes auth, rate limit, timeout, network, server, protocol, and invalid response without exposing secrets.

#### M04-C6 — `feat(settings): manage and test provider profiles`

- [ ] RED presenter/repository tests cover list/create/edit/copy/delete, default/current selection, unsaved changes, invalid base/model/secret fields, masked key replacement, connection-test progress/cancel/result, and restoration after recreation.
- [ ] Build feature-owned dialogs/presenters/resources through existing SmartTube dialog patterns.
- [ ] Replace the M02 test-provider-only settings switch with the single narrow host entry into `AiSubtitleSettingsPresenter`; move enable/disable handling into the feature-owned flow without weakening immediate cancellation or default-off behavior.
- [ ] Connect the selected valid profile to the provider resolver and M03 Translation Session identity.
- [ ] Add an integration test in which every normalized Provider failure category preserves Source-Only Fallback.

Worker self-acceptance:

- [ ] Each Provider Type can be saved, edited, tested, selected, and used with a model.
- [ ] No invalid/incomplete profile becomes the active resolved provider.
- [ ] UI/controller/renderer contain no raw wire-protocol logic.
- [ ] Existing subtitle setting and M02/M03 lifecycle behavior remain intact.
- [ ] `SubtitleSettingsPresenter.java` remains CRLF-only, its diff is within the declared hook, and `upstream-patches.md` matches it exactly.

#### M04-C7 — `docs(ai-subtitle): record M04 provider and persistence checkpoint`

- [ ] Complete every M04 Worker self-acceptance item against M04 base...tip; correct and re-check every Worker-discovered Critical/Important finding.
- [ ] Complete M04 report, live plan/progress, research notes, ADRs, secret/redaction audit, file inventory, and exact-SHA CI launch/current-state evidence.

M04 exit/self-acceptance:

- [ ] Five Provider Types resolve through exactly two normal-response protocol adapters.
- [ ] Profile/schema migration and default repair are deterministic across restart/profile switch.
- [ ] Secrets satisfy the accepted API 17/backup/export policy and do not appear in logs/reports/artifacts.
- [ ] Manual model entry works when discovery is unsupported or fails.
- [ ] Every Provider failure category proves Source-Only Fallback.
- [ ] No SSE, scheduler retry, prompt CRUD, or persistent translation cache was added.
- [ ] M04 Worker self-acceptance is complete and its range/evidence are frozen for later Commander second review; the same Worker continues to M05 without user relay.

## 10. M05 — Prompt Manager and resolved Translation Profile

### Objective and boundary

Make built-in/custom prompts versioned, user-manageable, Provider-independent, and cache-safe. Resolve Provider Profile + Model + Prompt Profile + Target Language as one immutable runtime `TranslationProfile`. This milestone does not add subtitle segmentation, SSE, scheduler retry, or generic non-subtitle prompt categories.

### Planned files

- `prompt/PromptProfile.java`
- `prompt/PromptRepository.java`
- `prompt/PromptSerializer.java`
- `prompt/PromptMigration.java`
- `prompt/PromptVariable.java`
- `prompt/PromptRenderer.java`
- `prompt/BuiltInSubtitlePrompts.java`
- `translation/TranslationProfileResolver.java`
- `settings/ui/PromptProfilesPresenter.java`
- `settings/ui/PromptProfileEditorPresenter.java`
- `settings/ui/TranslationProfilePresenter.java`
- prompt resources under feature-owned `common/src/main/res/values/ai_subtitle_*.xml`
- matching tests under `common/src/test/java/.../ai/subtitle/{prompt,translation,settings}/`

No existing non-feature SmartTube file is authorized.

### Commit sequence

#### M05-C0 — `docs(ai-subtitle): settle prompt contract and resolution policy`

- [ ] Run G05-1 and record the exact allow-listed variables, syntax/escaping, validation errors, built-in immutability, copy semantics, version behavior, and invalid selection repair.
- [ ] Define independently authored baseline translation and indexed-output prompt behavior without copying KissTranslator prose.
- [ ] Create `M05-report.md` and pin the M05 base.

Worker self-acceptance:

- [ ] A worker can render or reject every prompt deterministically without guessing.
- [ ] Prompt content/version effects on cache/session identity are explicit.
- [ ] No production prompt repository code predates the settled contract.

#### M05-C1 — `feat(prompt): persist versioned built-in and custom profiles`

- [ ] RED tests cover first-run built-ins, stable IDs, schema migration, corrupt data/default repair, custom CRUD/copy, built-in edit/delete rejection, selected/default repair, and restart/profile switch.
- [ ] Implement repository/serializer/migration integrated with versioned `AiSubtitleData`.

Worker self-acceptance:

- [ ] Built-ins are immutable and can be copied to editable custom prompts.
- [ ] Built-in content upgrades do not overwrite custom content.
- [ ] Deleting a selected custom prompt repairs selection deterministically.

#### M05-C2 — `feat(prompt): render a strict subtitle variable catalog`

- [ ] RED tests cover every allowed variable, repeated variables, literal escaping, missing required value, unknown variable, malformed delimiter, null/empty input, Unicode/no-space text, and bounded context values.
- [ ] Implement renderer as pure JVM logic with explicit diagnostics; do not silently erase unknown/missing variables.

Worker self-acceptance:

- [ ] Expected strings are independent literals and include escaping edge cases.
- [ ] Rendering is deterministic and Provider-independent.
- [ ] Diagnostics do not leak secret or unrelated subtitle/prompt content.

#### M05-C3 — `feat(settings): manage prompt profiles and target language`

- [ ] RED presenter/repository tests cover list/create/copy/edit/delete, built-in restrictions, validation diagnostics, default/current selection, target-language selection, cancel/save, and recreation.
- [ ] Implement feature-owned SmartTube dialogs/resources.

Worker self-acceptance:

- [ ] Invalid prompt or missing required variable cannot become active.
- [ ] User can recover from a deleted/invalid current prompt without losing other profiles.
- [ ] No Provider credential/model UI logic is duplicated in the Prompt Manager.

#### M05-C4 — `feat(translation): resolve profiles atomically and invalidate output identity`

- [ ] RED tests vary Provider Profile, protocol/base identity, Model ID, Prompt Profile ID/content/version, and Target Language one at a time.
- [ ] Implement `TranslationProfileResolver` that returns either one fully valid immutable profile or one normalized validation failure; never a partially resolved object.
- [ ] Wire profile changes to new Session Generation and complete cache-key identity.
- [ ] Add independently authored baseline translation and indexed-output built-ins.

Worker self-acceptance:

- [ ] A valid Translation Profile is unambiguous across restart/app-profile switch.
- [ ] Every output-affecting change invalidates session/cache identity; cosmetic profile-name changes do not.
- [ ] Missing secret/model/prompt/language yields Source-Only Fallback without issuing a request.
- [ ] Built-in prompt snapshots and hashes are deterministic.

#### M05-C5 — `docs(ai-subtitle): record M05 prompt and profile checkpoint`

- [ ] Complete every M05 Worker self-acceptance item against M05 base...tip; correct/re-check all Worker-discovered Critical/Important findings.
- [ ] Complete M05 report, plan/progress/decision/glossary updates, prompt originality statement, file inventory, and exact-SHA CI launch/current-state evidence.

M05 exit/self-acceptance:

- [ ] Prompt Profile schema, migrations, strict renderer, CRUD/copy/default/current UI, and target language persist correctly.
- [ ] Provider/Model/Prompt/Language resolve atomically.
- [ ] Cache/session invalidation tests cover every output-affecting field.
- [ ] Built-in prompts are independently authored and no unrelated KissTranslator prompt category exists.
- [ ] M05 Worker self-acceptance is complete and its range/evidence are frozen for later Commander second review; the same Worker continues to M06 without user relay.

## 11. M06 — Subtitle processing behavioral port

### Objective and boundary

Independently implement the Android subtitle-processing behavior described by the migration map: fixture/provenance, normalization, coarse ASR timing, rule/statistical sentence breaking, translation chunking, indexed Boundary Protocol, Segmentation Metrics, accepted-prefix/tail retry, and deterministic fallback. M06 does not implement the M07 playback scheduler or M08 streaming/context/persistent cache.

### Planned files

- `source/SubtitleNormalizer.java`
- `segmentation/AsrTimingEstimator.java`
- `segmentation/RuleSentenceBreaker.java`
- `segmentation/StatisticalSentenceBreaker.java`
- `segmentation/TranslationChunker.java`
- `segmentation/BoundaryProtocol.java`
- `segmentation/BoundaryProtocolParser.java`
- `segmentation/BoundaryValidationResult.java`
- `segmentation/SegmentationMetrics.java`
- `segmentation/AiSegmentationCoordinator.java`
- `segmentation/DeterministicSegmentationFallback.java`
- independently authored fixtures below `common/src/test/resources/ai-subtitle/fixtures/`
- `common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/source/SubtitleFixture.java`
- matching pure-JVM tests below `common/src/test/java/.../ai/subtitle/{source,segmentation}/`
- `docs/ai-subtitle/fixture-provenance.md`

No existing non-feature SmartTube file is authorized.

### Commit sequence

#### M06-C0 — `test(ai-subtitle): establish independent fixture provenance`

- [ ] Run G06-1 and G06-2; pin M06 base and create `M06-report.md`.
- [ ] Define a minimal fixture schema containing stable event ID, start/end time, text, caption kind/language metadata, and provenance ID.
- [ ] Author small synthetic/factual fixtures for manual English/Japanese/Chinese, ASR aggregate events, word timing, no-space language, noise/non-speech, fast/slow speech, long segment, overlap, gap, duplicate, and malformed model output.
- [ ] Record author, date, generation method, behavioral category, and a statement that no KissTranslator strings/timings/arrays were copied.

Worker self-acceptance:

- [ ] Every roadmap sample class maps to at least one independently authored fixture/test case.
- [ ] Fixtures contain only minimal synthetic/factual content and no user/private subtitle data.
- [ ] Schema parser rejects missing IDs, negative/reversed times, and unsupported versions.

#### M06-C1 — `feat(source): normalize cues and estimate coarse ASR timing`

- [ ] RED tests cover entity/markup cleanup appropriate to the accepted input format, whitespace, empty/non-speech noise, adjacent exact duplicates, meaningful later repeats, gaps, overlaps, line breaks, Unicode/no-space languages, aggregate ASR events, and capping at the next effective event.
- [ ] Implement `SubtitleNormalizer` and `AsrTimingEstimator` as deterministic pure JVM components.

Worker self-acceptance:

- [ ] Normalization preserves timing gaps and visible semantic text.
- [ ] Adjacent duplicates are removed without collapsing meaningful repeats separated by time/content.
- [ ] Estimated word timing is monotonic, non-negative, capped, and retains full text coverage.
- [ ] Fixture expectations are independent literals.

#### M06-C2 — `feat(segmentation): add rule and statistical sentence breaking`

- [ ] RED rule tests cover punctuation priority, pause, maximum duration, word-count/no-space length, abbreviations/decimals, missing punctuation, fast/slow speech, and single oversize input.
- [ ] RED statistical tests cover median/percentile/MAD-style robust gaps, outliers, capitalization/event boundaries, long split, undersized merge, overlap clipping, monotonicity, and deterministic ties.
- [ ] Implement both breakers behind a small common contract; version their behavior for cache identity.

Worker self-acceptance:

- [ ] Each strategy produces ordered, non-overlapping, full-coverage segment IDs.
- [ ] No-space languages do not depend on ASCII whitespace token counts.
- [ ] One pathological gap/outlier cannot dominate all boundaries.
- [ ] Repeated runs produce byte-identical output.

#### M06-C3 — `feat(segmentation): build translation chunks without losing coverage`

- [ ] RED tests cover target/max size, preferred sentence/pause boundary, exact limit, single oversize unit, gaps/overlaps, no-space text, empty input, and full reconstruction.
- [ ] Implement `TranslationChunker` producing contiguous `TranslationUnit` values accepted by M03 contracts.

Worker self-acceptance:

- [ ] Concatenated source coverage exactly reconstructs the accepted normalized timeline.
- [ ] No segment is duplicated, reordered, or dropped.
- [ ] A single oversize segment remains one explicit oversize unit instead of being silently truncated.

#### M06-C4 — `feat(segmentation): validate indexed boundary output and metrics`

- [ ] RED parser/validator tests cover valid indexed ranges, source reconstruction, duplicate/missing indices, empty/invalid ranges, overlaps, non-monotonic order, out-of-range indices, malformed JSON/text, legacy shape rejection, no-space tokenization, and duration/length warnings.
- [ ] Implement `BoundaryProtocol`, parser/result types, and `SegmentationMetrics` as a test oracle and production validator.

Worker self-acceptance:

- [ ] Only a continuous prefix beginning at the requested start index is accepted.
- [ ] Accepted ranges reconstruct source text/coverage exactly under the documented normalization rule.
- [ ] Invalid output never enters cache or render lookup as successful translation.
- [ ] Metrics distinguish hard coverage errors from warnings.

#### M06-C5 — `feat(segmentation): recover accepted prefix and deterministic tail`

- [ ] RED coordinator tests cover full valid result, partial continuous prefix, invalid first element, gap after prefix, one tail retry, retry success, retry partial/invalid, cancellation, stale generation/epoch/request, Provider failure, and deterministic fallback.
- [ ] Implement `AiSegmentationCoordinator` that accepts only validated prefix, retries uncovered tail once, then uses smaller deterministic units or Source-Only Fallback for the remainder.
- [ ] Keep this coordinator independent of playback lookahead; M07 owns admission/priority.

Worker self-acceptance:

- [ ] Final successful output has complete source coverage or explicitly identifies Source-Only fallback units; no silent hole exists.
- [ ] Tail retry executes at most once for the same failed range.
- [ ] Stale/cancelled output cannot mutate cache/session state.
- [ ] Deterministic fallback needs no Provider/network call.

#### M06-C6 — `docs(ai-subtitle): record M06 processing behavior checkpoint`

- [ ] Run all M06 fixture suites plus complete M02–M05 regressions.
- [ ] Complete every M06 Worker self-acceptance item against M06 base...tip; correct/re-check all Worker-discovered Critical/Important findings.
- [ ] Complete M06 report, live plan/progress, provenance, decisions/glossary, file inventory, exact test counts, mutation evidence for coverage/stale/tail guards, and exact-SHA CI evidence.
- [ ] Before the one final return, confirm that M03, M04, M05, and M06 milestone-tip workflows are all green or report a blocking failure instead of returning a completion claim.

M06 exit/self-acceptance:

- [ ] Independent fixtures cover manual, ASR, word timing, no-space languages, noise, fast/slow speech, long segments, overlaps, gaps, duplicates, and malformed model output.
- [ ] Normalization through fallback preserves ordered complete source coverage.
- [ ] Boundary/metrics failures cannot poison cache or rendering.
- [ ] No KissTranslator source/fixture content was copied and provenance is complete.
- [ ] No M07 scheduler or M08 streaming/persistent-cache behavior was introduced.
- [ ] M06 Worker self-acceptance is complete; one consolidated Worker return contains all four pinned ranges, reports, CI results, and open risks for Commander second review.

## 12. Verification commands and evidence

### Before every milestone

```text
git status --short --branch
git rev-parse HEAD
git submodule status --recursive
git diff --check
```

The worktree must be clean. If the baseline workflow is not green, record and resolve the pre-existing failure before implementation so new regressions cannot be hidden inside it.

### Targeted local diagnostics

Run the narrow test class/package for each RED/GREEN cycle, then:

```text
./gradlew :common:testStbetaDebugUnitTest
```

For Robolectric preference/presenter coverage, use the existing JDK 11 workflow lane unless a separately authorized dependency/toolchain decision supersedes ADR-010. Local results remain diagnostic.

### Milestone static gate

```text
git status --short --branch
git diff --check
git diff --stat <milestone-base>...HEAD
git diff --name-status <milestone-base>...HEAD
git diff -- .github/workflows/CI.yml
git diff --submodule=log <milestone-base>...HEAD
```

Also verify:

- no undeclared existing SmartTube file changed;
- no submodule pointer, file mode, mass line-ending, generated build output, credential, or local environment file changed;
- no Provider brand/protocol import crossed into integration/player/renderer classes;
- every created production class has behavior coverage or a documented data-only reason;
- `upstream-patches.md` exactly matches the actual host-file diff.

### Authoritative GitHub Actions gate

For the exact milestone product SHA, the existing AI subtitle workflow must show:

- `:common:testStbetaDebugUnitTest` successful on JDK 17;
- `lintStbetaRelease` successful;
- `assembleStbetaRelease` successful;
- the narrow JDK 11 preference tests successful where still applicable;
- validation reports and beta APK artifacts present;
- no unaccounted skipped/ignored required test.

Record workflow URL, run ID, SHA, all job/step conclusions, test-suite counts, skipped tests with justification, artifact names, and superseded/cancelled runs in the milestone report.

### Device matrix

M03 and M06 are primarily pure-domain milestones; M04/M05 change user settings and real Provider selection. Record `PASS`, `FAIL`, or `NOT RUN` for all applicable scenarios: settings create/edit/delete/restart, masked key, connection cancellation, manual model fallback, prompt CRUD/copy/restart, provider/profile/prompt/language switch during playback, provider failure, subtitle off/on, seek, close/reopen, background/foreground, and PiP. Automated tests do not imply a physical device PASS.

## 13. Milestone report template and handoff

Each report must include:

1. Task/Milestone and pinned base/final product SHA.
2. Ordered commit table with subjects and purpose.
3. Every created/modified/deleted file.
4. Implementation summary by commit ID.
5. Witnessed RED evidence and final automated test inventory/counts.
6. Static checks and exact results.
7. GitHub Actions run URL/ID/SHA, step conclusions, artifacts, skips, and historical failed/superseded runs.
8. Device matrix with honest states.
9. Worker first-pass standards/spec self-review dispositions separately, without claiming Commander PASS.
10. Decisions/ADRs/glossary/research/provenance/upstream-ledger changes.
11. Deviations, unexpected discoveries, remaining risks, and deferred Minor findings.
12. Acceptance-criteria table with `MET`, `NOT MET`, or `NOT RUN` plus evidence.
13. Confirmation that the next milestone started only after the current milestone's Worker self-acceptance, plus any deliberately overlapped later research.

The Worker executes all four milestones from this one plan without asking “continue?” and without returning intermediate milestone packages. It returns once after M06. It must stop only for a genuine Grill with Docs decision requiring the user, new authority, an unresolved plan contradiction, unavailable required secret/device/user action, or a failed self-acceptance/CI condition that cannot be corrected inside scope.

After that return, the Commander uses `superpowers:requesting-code-review` four times, once for each pinned milestone range, and applies `superpowers:verification-before-completion` before every PASS/CHANGES REQUIRED claim. The Commander records review results in separate `docs/ai-subtitle/reviews/M03-review.md` through `M06-review.md`; it does not review one giant combined diff as a substitute for those four reviews.

### Commander second-review procedure

1. Verify the four reported base/tip SHAs exist, are ordered and non-overlapping, and together cover the Worker's complete M03–M06 product range.
2. Create one review package per milestone containing that milestone's commit list, stat, full contextual diff, plan section, report, test evidence, CI result, and deferred findings.
3. Invoke `superpowers:requesting-code-review` once per milestone. Reviews may run concurrently after the Worker return because their ranges are immutable, but every reviewer receives only one milestone package.
4. Save the returned evidence/findings to `M03-review.md`, `M04-review.md`, `M05-review.md`, and `M06-review.md`. Preserve separate severity and acceptance disposition for each milestone.
5. Before claiming PASS or CHANGES REQUIRED, invoke `superpowers:verification-before-completion`: identify the command/evidence that proves the claim, run/read it freshly, and state the result no more broadly than the evidence.
6. If any milestone has Critical/Important findings, prepare one consolidated correction handoff grouped by milestone so the user still relays once. The correction Worker fixes in dependency order, returns one amended set of reports/ranges, and the Commander re-reviews only affected milestone ranges plus any downstream range whose contract changed.
7. Minor findings are explicitly accepted, deferred to a named future milestone, or included in the correction package; they are never silently dropped.

## 14. Final M03–M06 completion gate

- [ ] M03, M04, M05, and M06 each have a pinned base/tip, Worker self-acceptance report, and exact-SHA CI result, all produced in one uninterrupted Worker execution.
- [ ] The integration history preserves the declared logical commit order and every commit message/body meets section 6.
- [ ] `CONTEXT.md`, decisions, progress, upstream ledger, research notes, fixture provenance, this plan ledger, and four reports agree with the actual tree/history.
- [ ] `git diff` from the accepted M02 base contains only declared feature files/resources/docs plus explicitly approved host hooks.
- [ ] No secret, copied GPL content, private subtitle content, build output, worktree metadata, or local environment artifact is tracked.
- [ ] M02 behavior remains green and Source-Only Fallback is preserved through Provider/Profile/Prompt/processing failures.
- [ ] No M07/M08 production feature is present.
- [ ] The Worker has returned one consolidated package; no intermediate user relay occurred.
- [ ] The Commander has completed and recorded four separate Superpowers second reviews, one per milestone range, and freshly verified every acceptance claim.
- [ ] Any Commander correction package has been re-run and re-reviewed in the affected milestone range.
- [ ] `docs/ai-subtitle/progress.md` advances the frontier to M07 only after all checks above are evidenced.

## 15. Plan self-review

- Spec coverage: every M03–M06 roadmap workstream maps to a named commit and binary self-acceptance checks.
- Dependency coverage: M03 contracts precede M04/M05/M06; M05 depends on M04; M06 production merges last.
- Scope coverage: M07 scheduler and M08 maturity features are explicitly excluded.
- Documentation coverage: glossary, ADR, research, progress, reports, provenance, upstream ledger, and live-plan triggers are explicit.
- Review coverage: Worker self-review is per milestone during the uninterrupted run; Commander Superpowers second review is per milestone after the one final return, always against pinned bases.
- Placeholder scan: the plan contains no unnamed implementation task or unowned acceptance behavior.
- Interface consistency: M03 produces immutable Session/Profile/Unit/Cache contracts consumed by M04/M05/M06; Provider wire details remain behind `ProtocolAdapter`.
