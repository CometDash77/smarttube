# M04 Worker Report

Task ID: `M04`

Milestone: M04 — Provider, model, persistence, and connection management

Status: **IN PROGRESS — M04-C0 (policy settlement) landed; M04-C1..C7 remain**

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
| C1 | `feat(settings): version provider profile persistence` | schema/repository/serializer/migration + tests | NOT RUN |
| C2 | `feat(settings): protect provider credentials across Android versions` | SecretStore/AndroidSecretStore + tests | NOT RUN |
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

## Files created

### M04-C0

- `docs/ai-subtitle/research/g04-1-android-secret-storage.md` — G04-1 research note (verified evidence table, accepted policy, limitations).
- `docs/ai-subtitle/research/g04-2-provider-capabilities.md` — G04-2 research note (Phase 0 protocol baseline, accepted policy, limitations).
- `docs/ai-subtitle/worker-reports/M04-report.md` — this report.

## Files modified

### M04-C0

- `docs/ai-subtitle/decisions.md` — ADR-012 appended; the open M04 Keystore ruling marked resolved.
- `docs/ai-subtitle/worker-plans/M03-M06-plan.md` — live ledger row for M04-C0.
- `docs/ai-subtitle/progress.md` — current state advanced to M04.

No production file changed; no existing SmartTube file touched.

## Witnessed RED evidence and test inventory

`NOT RUN` (no production code in M04-C0; each functional commit records observed RED → minimal GREEN → targeted regression → full relevant suite).

## Static checks

`NOT RUN` as a milestone gate (performed at M04-C7). Commit-level `git diff --check` stays clean; no existing SmartTube file modified so far.

## GitHub Actions runs

- M04-C0 tip: `PENDING AUTHORIZED UPLOAD` (push triggers the workflow; workstation API cannot read run status — see the M03 report limitation).
- Milestone tip (M04-C7): `NOT RUN`.

## Device matrix

`NOT RUN` for all rows at M04-C0.

## Worker first-pass self-review dispositions

`NOT RUN` (performed against the M04 base...tip at C7, before the Commander second review; no Commander PASS is claimed here).

## Decisions / ADRs / glossary / research / upstream ledger

- **ADR-012** added (credential protection policy).
- Research notes created for G04-1 and G04-2.
- `CONTEXT.md`: no semantic change in M04-C0.
- `upstream-patches.md`: expected to gain exactly one entry at M04-C6 (the single settings-entry hook); unchanged so far.

## Deviations, unexpected discoveries, remaining risks, deferred Minor findings

- Web research was unavailable from this workstation during M04-C0 (search-provider authentication failure); both notes record this explicitly and mark the re-verification requirement for the unverified platform-behavior statement.
- Remaining risk: the API 17–22 plaintext fallback band (documented in ADR-012); decryption failure after device restore is an expected path, to be exercised by M04-C2 tests.

## Acceptance-criteria table (M04 exit/self-acceptance)

| # | Criterion | Disposition | Evidence |
|---|---|---|---|
| 1 | Five Provider Types resolve through exactly two normal-response protocol adapters | NOT RUN | — |
| 2 | Profile/schema migration and default repair are deterministic across restart/profile switch | NOT RUN | — |
| 3 | Secrets satisfy the accepted API 17/backup/export policy and do not appear in logs/reports/artifacts | NOT RUN | — |
| 4 | Manual model entry works when discovery is unsupported or fails | NOT RUN | — |
| 5 | Every Provider failure category proves Source-Only Fallback | NOT RUN | — |
| 6 | No SSE, scheduler retry, prompt CRUD, or persistent translation cache was added | NOT RUN | — |
| 7 | M04 Worker self-acceptance complete; range/evidence frozen; Worker continues to M05 without user relay | NOT RUN | — |

## Confirmation

M04-C0 only. G04-1 and G04-2 are settled and recorded; no production network code precedes these decisions. The interruption point above is authoritative for resumption; M04-C1 (provider profile persistence) starts next.
