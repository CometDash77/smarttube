# M03-M06 Remaining Execution Plan (Ponytail Revision)

Status: **ACTIVE - 2026-09-13 revision.** This file replaces the original 958-line consolidated program (preserved in git history at `e479c8bf6:docs/ai-subtitle/worker-plans/M03-M06-plan.md`) as the single authority for remaining work. The revision exists because the execution audit found process overhead dominating delivery: since M03, 12 of 23 commits were documentation-only, and the old plan still required 14 remaining task checkpoints, four milestone Commander reviews, repeated exact-SHA CI runs, and a 13-section report per milestone.

**Goal:** finish M04 (secret-storage correction), implement M05 and M06, and validate once. Seven gates, at most six implementation commits plus one review-fix commit, one final code review, one final CI run, one device smoke test.

## 1. Authoritative state

- Branch: `feature/ai-bilingual-subtitles`; `origin` is the personal fork remote, `upstream` is read-only official SmartTube.
- Latest pushed tip before this run: `6a2af2d2c`. Final implementation tip: `20d54878442ae8d9c3f9742a945c1f4c8de6846d`.
- M03: complete and self-accepted.
- M04-C0..C6: implementation complete; product tip `0cc5bd6c6`. Historical verification (do not re-claim as new evidence): full ai-subtitle suite 281 total / 0 failed / 7 skipped on JDK 17; settings/secret lane 62/62 on JDK 11; lint green.
- M04-C7: the correction is **uncommitted and must be preserved**. Retained five-file working-tree diff:
  - `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/AndroidSecretStore.java`
  - `common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/AndroidSecretStoreRobolectricTest.java`
  - `common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/AndroidSecretStoreTest.java`
  - `docs/ai-subtitle/decisions.md` (ADR-012 backup/export policy)
  - `docs/ai-subtitle/research/g04-1-android-secret-storage.md`
- M05, M06: not started.
- Environment facts: the workspace path contains non-ASCII characters and blocked local Gradle execution before test execution; the final push succeeded, but exact-SHA CI run `34735924554` failed and its job details are currently unavailable through the Actions API. No `adb` executable is installed for device smoke. Local results are diagnostic; CI is authoritative. If a local run is path-blocked, do not build tooling workarounds.

## 2. Read before starting

1. `AGENTS.md` (workspace root)
2. `docs/ai-subtitle/progress.md`
3. this plan
4. for M05/M06 design context only: `docs/ai-subtitle/architecture.md`, `docs/ai-subtitle/migration-map.md`, accepted ADRs in `docs/ai-subtitle/decisions.md`

Authority order for conflicts: latest explicit user decision > accepted ADRs > this plan. Record a real conflict in `progress.md` and ask once; do not open a research process for questions the code or an ADR already answers.

## 3. Hard constraints (non-negotiable)

1. **Security:** finish the retained backup/export exclusion for provider secrets. Secrets must never appear in logs, reports, cache, or Android backup/export. This is the one M04 item that may not be waived or deferred.
2. **Android API 17:** no unguarded APIs above API 17; no Java-library APIs missing from the app floor.
3. **GPL isolation:** KissTranslator source, tests, and fixtures are behavioral references only. Never copy or mechanically translate them. Fixtures and prompts are independently authored.
4. **Source-Only Fallback:** missing, late, invalid, cancelled, stale, or failed translation renders source text only. Provider failures must never block, stop, seek, or re-prepare playback.
5. **Patch surface:** production logic stays under `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/`. Existing SmartTube host files keep only the already-declared M02 renderer/lifecycle hooks and the M04 `SubtitleSettingsPresenter` entry. No new host-file edits are planned; if one becomes unavoidable, record the reason in the same commit and update `upstream-patches.md`.
6. **Out of scope:** `MediaServiceCore`, `SharedModules`, `exoplayer-amzn-2.10.6`, Gradle dependency versions, CI workflow files, SSE, scheduler retry, persistent translation cache, M07 lookahead scheduling, M08 features.

## 4. Execution model

- One worker, sequential gates. No subagent reviews, no Commander relays, no "continue?" prompts between gates.
- **Commit budget:** exactly one commit per Gate 1-6 (six implementation commits). Gate 7 may add at most one `fix(ai-subtitle): address final review findings` commit. Documentation-only commits are not allowed; fold progress/decision updates into the implementation commit they describe.
- **Testing:** every gate ships behavior tests in the same commit. Run the narrow test classes first, then the full ai-subtitle suite. No witnessed-RED ceremony, no per-task mutation reports, no test-count inventories. Keep one focused negative test for each invariant named in the gate.
- **Stop conditions:** only a security regression, API 17 violation, GPL contamination, broken Source-Only Fallback, a gate test that cannot be made green within scope, or missing user-only input (credentials/device). Everything else is recorded in `progress.md` and work continues.
- **Progress ledger:** each gate commit also updates the current-state block of `progress.md` (frontier + one-line verification result). Nothing else.

## Gate 1 - Close M04: secret backup/export correction

Preserve and review the retained five-file diff; do not reset, stash-drop, or re-implement it.

- [x] Confirm `AndroidSecretStore` excludes secrets from Android backup/export per the recorded ADR-012 decision, and the new tests assert the exclusion semantics.
- [x] Attempt `AndroidSecretStoreTest` and `AndroidSecretStoreRobolectricTest` (narrow); the local Gradle invocation is path-blocked before test execution, so final execution remains in Gate 7 CI.
- [x] Commit all five files: `fix(settings): exclude subtitle secrets from Android backup`
- [x] M04 is closed. No C7 re-review, no report restoration, no file inventory, no milestone CI run.

## Gate 2 - M05 core: prompt persistence, renderer, atomic resolution

Planned files: `prompt/PromptProfile.java`, `prompt/PromptRepository.java`, `prompt/PromptSerializer.java`, `prompt/PromptMigration.java`, `prompt/PromptVariable.java`, `prompt/PromptRenderer.java`, `prompt/BuiltInSubtitlePrompts.java`, `translation/TranslationProfileResolver.java`, plus matching tests.

- [x] Versioned built-in/custom prompt persistence: stable IDs, schema migration, corrupt-data repair, built-in immutability plus copy-to-custom, deterministic selection repair, restart/profile-switch stability.
- [x] Strict renderer: fixed allow-listed variable catalog; repeated variables, literal escaping, unknown/missing variables, and malformed input produce explicit diagnostics - never silent erasure.
- [x] `TranslationProfileResolver`: Provider Profile + Model + Prompt Profile + Target Language resolve to exactly one immutable valid profile or one normalized failure; every output-affecting field (protocol/base identity, model ID, prompt ID/content/version, target language) invalidates session/cache identity; cosmetic name changes do not.
- [x] Independently authored baseline translation and indexed-output built-in prompts.
- [x] Missing secret/model/prompt/language yields Source-Only Fallback without issuing a request.
- [x] Commit: `feat(prompt): add versioned profiles and atomic translation resolution`

## Gate 3 - M05 UI: prompt manager and target language

Planned files: `settings/ui/PromptProfilesPresenter.java`, `settings/ui/PromptProfileEditorPresenter.java`, `settings/ui/TranslationProfilePresenter.java`, feature-owned resources, matching presenter/repository tests.

- [x] List/create/copy/edit/delete prompts; built-ins restricted to copy; validation diagnostics; default/current selection; target-language selection; cancel/save; recovery after recreation.
- [x] Invalid prompt or missing required variable cannot become active.
- [x] No provider credential/model UI logic duplicated here.
- [x] Full ai-subtitle suite attempted; local Gradle is path-blocked before test execution, so final CI remains the acceptance signal.
- [x] Commit: `feat(settings): manage prompt profiles and target language`

## Gate 4 - M06: fixtures, normalization, ASR timing

Planned files: `source/SubtitleNormalizer.java`, `segmentation/AsrTimingEstimator.java`, fixtures under `common/src/test/resources/ai-subtitle/fixtures/`, `common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/source/SubtitleFixture.java`, `docs/ai-subtitle/fixture-provenance.md`, matching tests.

- [x] Minimal independently authored fixtures (schema: stable event ID, start/end time, text, caption kind/language metadata, provenance ID) covering manual, ASR, word timing, no-space languages, noise, fast/slow speech, long segments, overlaps, gaps, duplicates; the provenance document stays one page.
- [x] Normalizer: dedupe repeats separated by time/content, clip overlaps, enforce monotonic ordering, preserve complete text coverage.
- [x] ASR timing: monotonic, non-negative, capped word estimates; no-space languages do not depend on ASCII whitespace token counts.
- [x] Commit: `feat(source): normalize cues and estimate ASR timing`

## Gate 5 - M06: sentence breaking and translation chunking

Planned files: `segmentation/RuleSentenceBreaker.java`, `segmentation/StatisticalSentenceBreaker.java`, `segmentation/TranslationChunker.java`, matching tests.

- [x] Rule breaker: punctuation priority, pause, maximum duration, length/word-count limits, abbreviations/decimals, missing punctuation, single oversize input.
- [x] Statistical breaker: robust gap statistics (median/percentile/MAD-style), outlier resistance, event/capitalization boundaries, deterministic ties.
- [x] Chunker: target/max size, preferred sentence/pause boundaries, single oversize unit kept explicit, contiguous full reconstruction - no drop, duplicate, or reorder.
- [x] Version segmentation behavior for cache identity.
- [x] Commit: `feat(segmentation): break sentences and build translation chunks`

## Gate 6 - M06: boundary validation, recovery, fallback

Planned files: `segmentation/BoundaryProtocol.java`, `segmentation/BoundaryProtocolParser.java`, `segmentation/BoundaryValidationResult.java`, `segmentation/SegmentationMetrics.java`, `segmentation/AiSegmentationCoordinator.java`, `segmentation/DeterministicSegmentationFallback.java`, matching tests.

- [x] Parser/validator: valid indexed ranges, source reconstruction, duplicate/missing/out-of-range/non-monotonic indices, overlaps, malformed output, legacy-shape rejection; hard coverage errors distinguished from warnings.
- [x] Coordinator: accept only a continuous prefix beginning at the requested start index; at most one tail retry; stale generation/epoch/request and cancellation cannot mutate cache/session state.
- [x] Deterministic fallback needs no network; final output is complete or explicitly Source-Only per unit - no silent holes.
- [x] Full M02-M06 regression attempted as part of the Gate 6 focused run; local Gradle is path-blocked before test execution, so final CI remains the acceptance signal.
- [x] Commit: `feat(segmentation): validate boundaries and recover deterministically`

## Gate 7 - Final verification (single pass, in order)

1. **One full code review** of the complete feature diff (accepted M02 base...final tip), not per milestone. **Done:** review fixes are in `20d54878442ae8d9c3f9742a945c1f4c8de6846d`.
2. **One final CI run** at the exact final SHA on `feature/ai-bilingual-subtitles`: **Attempted:** run `34735924554` failed; inspect its GitHub UI job log before claiming completion.
3. **One device smoke test** (single TV device, one session): **Blocked:** no `adb` executable or connected TV device is available on this workstation.
4. Update `progress.md`: **Recorded:** final SHA, failed CI URL, unavailable device state, and next action; M07-ready status remains deferred until CI and smoke pass.

## Verification commands

Per gate (narrow tests first, then the suite). Local runs require `JAVA_HOME` on JDK 17 for Gradle 7.5; the Gate 1 settings lane also uses the existing JDK 11 lane where available.

```text
./gradlew :common:testStbetaDebugUnitTest --tests "com.liskovsoft.smartyoutubetv2.common.ai.subtitle.<area>.*"
./gradlew :common:testStbetaDebugUnitTest
```

Static check for every commit:

```text
git status --short
git diff --check
git diff --name-only
```

Confirm no file outside the declared feature/docs scope changed. The final lint/assemble evidence comes from the Gate 7 CI run, not repeated local runs.

## Removed process - do not re-introduce

- Per-task independent reviews, re-reviews, and review-fix review loops.
- Four per-milestone Commander reviews and separate `docs/ai-subtitle/reviews/M03..M06-review.md` packages.
- Documentation-only checkpoint commits (M05-C5 / M06-C6 style) and 13-section milestone reports.
- Intermediate exact-SHA CI per milestone; test-count inventories; every-file inventories; device matrices.
- Witnessed-RED evidence, per-task mutation reports, and reopening completed milestones for Minor or documentation-only findings.

## Historical record

- The original full plan, execution ledger, M03/M04 reports, research notes, and pause checkpoints remain in git history and existing files. They are evidence of what was done, not the forward process.
- The single review in Gate 7 covers the entire M03-M06 feature range; completed milestones are reopened only for the hard-constraint violations in section 3.
