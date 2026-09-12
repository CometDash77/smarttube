# M04 Worker Report

Task ID: `M04`

Milestone: M04 — Provider, model, persistence, and connection management

Status: **IN PROGRESS — M04-C0 through M04-C2 landed; M04-C3..C7 remain**

## Task/Milestone and pinned SHAs

- Milestone: `M04` — provider, model, persistence, and connection management (M03-M06 consolidated Worker program, plan section 9).
- Pinned base: `8a4bd175b` (M03-C5 checkpoint = M03 final tip).
- Final product SHA: `NOT RUN` (filled at M04-C7).
- Branch: `feature/ai-bilingual-subtitles`.

## Baseline confirmation (M04-C0)

| Item | State | Evidence |
|---|---|---|
| M03 self-acceptance | COMPLETE | `M03-report.md`; M03 product tip `52877ed96`; checkpoint `8a4bd175b` |
| Branch clean at start | CONFIRMED | `git status --short --branch` — clean, in sync with origin |
| Exact base SHA | CONFIRMED | `8a4bd175b` |
| Submodules | CONFIRMED | unchanged since M03 |
| M03 exact-SHA CI | `BLOCKED` | Workstation API access (404, private repo) — see M03 report; user/browser verification required |

## G04-1 / G04-2 disposition

- **G04-1** (Android secret storage, backup/export exclusion, API 17 fallback): **settled** — `research/g04-1-android-secret-storage.md`, recorded as **ADR-012**. Locally verified evidence: `KeyGenParameterSpec`/`KeyProperties` since API 23 (SDK `api-versions.xml`), app floor 17 (`SharedModules/constants.gradle`), `android:allowBackup="true"` with no exclusion rules (`smarttubetv/src/main/AndroidManifest.xml`). Policy: separated `SecretStore`; Keystore AES-256-GCM on API 23+; documented app-private fallback on API 17–22; no host-manifest change (not authorized and not needed); fail-safe reads normalize to configuration/auth failure + Source-Only Fallback.
- **G04-2** (Provider capability failure and model-discovery UX): **settled** — `research/g04-2-provider-capabilities.md`. Policy: runtime capability checks only; manual Model ID first-class; discovery failure never invalidates a saved profile; presets are defaults, not capability claims; normalized connection-test categories reuse the M03 `TranslationFailureCategory` vocabulary.

## Scope

Implement five user-facing Provider Types through two shared normal-response protocol adapters. Persist versioned non-secret profile data, protect secrets under ADR-012, support model discovery plus manual Model ID, expose CRUD/test/select UI, and prove Source-Only Fallback on Provider failure. SSE parsing, prompt CRUD, scheduler retries, and persistent translation cache remain out of scope.

## Planned commit table

| # | Commit subject | Content | State |
|---|---|---|---|
| C0 | `docs(ai-subtitle): settle provider security and capability policy` | research notes, ADR-012, M04 report start, baseline pin | MERGED (this commit) |
| C1 | `feat(settings): version provider profile persistence` | schema/repository/serializer/migration + tests | MERGED `3e1e1ed60` |
| C2 | `feat(settings): protect provider credentials across Android versions` | SecretStore/AndroidSecretStore + tests | MERGED `686768e54` |
| C3 | `feat(provider): add OpenAI-compatible normal responses` | adapter + fake-executor tests | NOT RUN |
| C4 | `feat(provider): add Anthropic-compatible normal responses` | adapter + tests | NOT RUN |
| C5 | `feat(provider): add presets and model discovery` | presets + ModelCatalog + ConnectionTestResult | NOT RUN |
| C6 | `feat(settings): manage and test provider profiles` | feature-owned UI + the single host settings hook | NOT RUN |
| C7 | `docs(ai-subtitle): record M04 provider and persistence checkpoint` | self-acceptance, report completion, CI evidence | NOT RUN |

## Interruption point (2026-09-12)

Per the user's instruction, this section records the exact resumption state:

- **Last completed**: M04-C0 落盘 — 两个研究笔记（`research/g04-1-android-secret-storage.md`、`research/g04-2-provider-capabilities.md`）、ADR-012（`decisions.md`，并关闭 open ruling）、本报告、plan ledger 行、`progress.md` 状态推进，全部在同一个 docs-only commit。
- **Next step**: **M04-C1** — `settings/` 下 Provider Profile 持久化：`AiSubtitleSchema` / `AiSubtitleData` 版本化、`ProviderProfileRepository` / `ProviderProfileSerializer` / `ProviderProfileMigration`。TDD 先 RED：空存储默认值、schema 升级、未知未来版本拒绝、损坏 JSON 默认修复、稳定 ID、create/update/delete、selected/default 修复、app-profile 切换、secret 引用分离（不含凭据）。
- **Resumption environment notes**：
  - 构建工作区：`X:\SmartTube`（`subst X: D:\obsidian\工程\VIBECODING项目\smartube` 映射到工作区根；JDK 17 = `C:/Users/77182/.gradle/jdks/jetbrains_s_r_o_-17-amd64-windows.2`）。
  - 测试命令：`cd /x/SmartTube && export JAVA_HOME=<JDK17> && ./gradlew :common:testStbetaDebugUnitTest --tests "com.liskovsoft.smartyoutubetv2.common.ai.subtitle.*"`。
  - 本 harness 的文件写入要求「先读后写」；bash 不能改有读取证据要求的文件（用 read_file → write_file/edit_file，跨轮次）。
  - 推送：`unset GITHUB_TOKEN && git push origin feature/ai-bilingual-subtitles`（网络偶发 SSL 失败时重试）。
  - `api.github.com` 对本仓库返回 404（private repo + token 无 API 读权限）→ CI 状态只能在 GitHub UI 查看。
- **Progress record inventory**（首次工作伦理要求）：本报告 + `progress.md`（Test status 区）+ plan ledger 三处已同步。

## Resumption progress (2026-09-12)

- **M04-C1 completed**: versioned non-secret Provider Profile model, serializer, migration, repository, and Android app-profile storage bridge landed. Deterministic repair covers blank/duplicate IDs, invalid profile entries, dangling default/selected IDs, corrupt JSON, and older schemas; newer schemas fail closed without overwrite.
- **M04-C2 completed**: `SecretStore` / `AndroidSecretStore` now keep credentials outside profile JSON, clear them on profile deletion/reset, mask display values, redact failures, and select AES-256-GCM on API 23+ or the documented app-private plaintext fallback on API 17–22.
- **Next step**: M04-C3 — OpenAI-compatible normal-response adapter with a fake HTTP executor, path/header/body/response/error/cancellation coverage.
- **Environment addition**: JDK 11 Temurin `11.0.32.1` is available at `C:\Users\77182\.gradle\jdks\temurin-11` for the narrow Robolectric lane; JDK 17 remains the authoritative primary lane.

## Files created

### M04-C0

- `docs/ai-subtitle/research/g04-1-android-secret-storage.md` — G04-1 research note (verified evidence table, accepted policy, limitations).
- `docs/ai-subtitle/research/g04-2-provider-capabilities.md` — G04-2 research note (Phase 0 protocol baseline, accepted policy, limitations).
- `docs/ai-subtitle/worker-reports/M04-report.md` — this report.


### M04-C1

- `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/provider/ProviderType.java` — five user-facing Provider Types.
- `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/provider/ProviderProtocol.java` — two normal-response wire protocols.
- `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/provider/ProviderProfile.java` — immutable non-secret profile value object.
- `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/AiSubtitleSchema.java` — schema/version/JSON/store-key constants.
- `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/ProviderProfileState.java` — immutable list/selection state.
- `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/ProviderProfileSerializer.java` — version-one JSON encode/decode with credential-free schema.
- `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/ProviderProfileMigration.java` — legacy repair and future-schema rejection.
- `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/ProviderProfileRepository.java` — stable IDs, CRUD, selection repair, and app-profile-aware read-through.
- Matching pure-JVM tests plus expanded `AiSubtitleDataTest` coverage.

### M04-C2

- `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/SecretStore.java` — separated credential contract, safe failure vocabulary, protection levels, and masking.
- `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/AndroidSecretStore.java` — AndroidKeyStore AES-256-GCM implementation with API 17–22 compatibility fallback.
- `common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/SecretStoreTest.java` — masking and safe-failure tests.
- `common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/AndroidSecretStoreTest.java` — storage/codec, CRUD, restart, failure normalization, redaction, and API-policy tests.
- `common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/AndroidSecretStoreRobolectricTest.java` — explicit API 17 plaintext fallback test.

## Files modified

### M04-C0

- `docs/ai-subtitle/decisions.md` — ADR-012 appended; the open M04 Keystore ruling marked resolved.
- `docs/ai-subtitle/worker-plans/M03-M06-plan.md` — live ledger row for M04-C0.
- `docs/ai-subtitle/progress.md` — current state advanced to M04.

No production file changed; no existing SmartTube file touched.


### M04-C1

- `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/AiSubtitleData.java` — now exposes the Provider Profile repository and implements its app-profile storage bridge while preserving the M02 enabled flag.
- `common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/AiSubtitleDataTest.java` — restart, enabled-flag, corrupt-payload, and multi-profile tests.
- `docs/ai-subtitle/progress.md`, `docs/ai-subtitle/worker-plans/M03-M06-plan.md`, `docs/ai-subtitle/worker-reports/M04-report.md`, `CONTEXT.md` — live evidence/domain terminology.

### M04-C2

- `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/AiSubtitleData.java` — exposes secret storage and wires it into the profile repository.
- `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/ProviderProfileRepository.java` — clears replaced/deleted/reset credential references.
- `common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/ProviderProfileRepositoryTest.java` — deletion/update/reset cleanup tests.
- `common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/integration/AiSubtitleCueBridgeCacheTest.java` — auth-failure source-only rendering test.
- `.github/workflows/ai-subtitle-validation.yml` — JDK 11 job now runs the full settings/secret package.
- `common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/ProviderProfileSerializerTest.java` — replaced a realistic-looking sentinel with an explicitly synthetic literal.

## Witnessed RED evidence and test inventory

M04-C0 had no production code. M04-C1 followed test-first rounds: initial RED was observed as missing production types/API (34 + 8 + 8 + 13 + 7 missing-symbol compile failures), plus a 1-failure RED for invalid optional-collection repair. M04-C2 then observed 57 missing-symbol compile failures across secret-store and deletion-cleanup tests, followed by a 3-symbol RED for API policy selection. GREEN evidence for the cumulative M04 suite: 214 total / 207 passed / 0 failed / 7 skipped on JDK 17; the JDK 11 settings/secret suite is 41/41 with 0 skipped, including the explicit API 17 plaintext fallback. Mutation checks: M04-C1 selection repair / credential-reference serialization / future-schema rejection produced 4 / 2 / 1 named failures; M04-C2 skipped credential cleanup produced 3 failures and lowered the API threshold produced 1; every mutation was reverted.

## Static checks

M04-C2 commit-level checks: `git diff --check` clean; `:common:lintStbetaDebug` BUILD SUCCESSFUL; repository-wide high-confidence secret scan found no real credential and only explicit synthetic test literals; no existing SmartTube host file modified. The full static milestone gate remains deferred to M04-C7.

## GitHub Actions runs

- M04-C0 tip: pushed as `8d0c9a50e`; exact-SHA Actions status cannot be read through the workstation API (404) and requires GitHub UI verification.
- M04-C1 tip: pushed as `3e1e1ed60`; M04-C2 tip: pushed as `686768e54`. Exact-SHA Actions status cannot be read through the workstation API (404) and requires GitHub UI verification.
- Milestone tip (M04-C7): `NOT RUN`.

## Device matrix

`NOT RUN` for all rows at M04-C0/C1/C2; device acceptance remains deferred to M04-C7.

## Worker first-pass self-review dispositions

`NOT RUN` (performed against the M04 base...tip at C7, before the Commander second review; no Commander PASS is claimed here).

## Decisions / ADRs / glossary / research / upstream ledger

- **ADR-012** added (credential protection policy).
- Research notes created for G04-1 and G04-2.
- `CONTEXT.md`: unchanged in M04-C0; M04-C1 clarified Provider Profile as non-secret configuration plus a separate credential reference.
- `upstream-patches.md`: expected to gain exactly one entry at M04-C6 (the single settings-entry hook); unchanged so far.

## Deviations, unexpected discoveries, remaining risks, deferred Minor findings

- Web research was unavailable from this workstation during M04-C0 (search-provider authentication failure); both notes record this explicitly and mark the re-verification requirement for the unverified platform-behavior statement.
- Remaining risk: the API 17–22 plaintext fallback band (documented in ADR-012) is exercised by M04-C2 tests; decryption failure after device restore remains an expected physical-device acceptance path for M04-C7.

## Acceptance-criteria table (M04 exit/self-acceptance)

| # | Criterion | Disposition | Evidence |
|---|---|---|---|
| 1 | Five Provider Types resolve through exactly two normal-response protocol adapters | NOT RUN | — |
| 2 | Profile/schema migration and default repair are deterministic across restart/profile switch | PARTIAL — M04-C1 | 199-test M04-C1 suite with 193 executed on JDK 17 plus 6/6 Robolectric settings tests on JDK 11; restart, corrupt repair, stable IDs, CRUD, dangling selections, future-schema rejection, enabled-flag preservation, and app-profile switching covered |
| 3 | Secrets satisfy the accepted API 17/backup/export policy and do not appear in logs/reports/artifacts | PARTIAL — M04-C2 | Separated store, Keystore AES path selection, API 17 plaintext fallback, masking/redaction, deletion/reset cleanup, safe failure vocabulary, and repository secret scan covered; physical-device backup/export acceptance remains for M04-C7 |
| 4 | Manual model entry works when discovery is unsupported or fails | NOT RUN | — |
| 5 | Every Provider failure category proves Source-Only Fallback | NOT RUN | — |
| 6 | No SSE, scheduler retry, prompt CRUD, or persistent translation cache was added | NOT RUN | — |
| 7 | M04 Worker self-acceptance complete; range/evidence frozen; Worker continues to M05 without user relay | NOT RUN | — |

## Confirmation

M04-C0 through M04-C2 are complete. G04-1/G04-2 remain settled; no production network code exists yet. Versioned non-secret profiles and separated credential storage are implemented and locally verified; M04-C3 (OpenAI-compatible normal responses) starts next.
