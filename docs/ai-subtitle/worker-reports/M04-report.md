# M04 Worker Report

Task ID: `M04`

Milestone: M04 — Provider, model, persistence, and connection management

Status: **PAUSED — M04-C7 checkpoint review returned `CHANGES_REQUIRED`; M04 is not accepted and M05-C0 has not started. Local tests are green, but the report/evidence gate and research-source gate remain open.**

## Task/Milestone and pinned SHAs

- Milestone: `M04` — provider, model, persistence, and connection management (M03-M06 consolidated Worker program, plan section 9).
- Pinned base: `8a4bd175b` (M03-C5 checkpoint = M03 final tip).
- Final product SHA: `0cc5bd6c6` (M04-C6); M04-C7 appends this docs-only finalization commit.
- Checkpoint commit subject: `docs(ai-subtitle): record M04 provider and persistence checkpoint`.
- Branch: `feature/ai-bilingual-subtitles`.

## Baseline confirmation (M04-C0)

| Item | State | Evidence |
|---|---|---|
| M03 self-acceptance | COMPLETE | `M03-report.md`; M03 product tip `52877ed96`; checkpoint `8a4bd175b` |
| Branch clean at start | CONFIRMED | `git status --short --branch` — clean |
| Exact base SHA | CONFIRMED | `8a4bd175b` |
| Submodules | CONFIRMED | unchanged since M03 |
| M03 exact-SHA CI | `BLOCKED` | Workstation API access — see M03 report; user/browser verification required |

## G04-1 / G04-2 disposition

- **G04-1** (Android secret storage, backup/export exclusion, API 17 fallback): **settled** — `research/g04-1-android-secret-storage.md`, recorded as **ADR-012**. Locally verified evidence: `KeyGenParameterSpec`/`KeyProperties` since API 23 (SDK `api-versions.xml`), app floor 17 (`SharedModules/constants.gradle`), `android:allowBackup="true"` with no exclusion rules (`smarttubetv/src/main/AndroidManifest.xml`). Policy: separated `SecretStore`; Keystore AES-256-GCM on API 23+; documented app-private fallback on API 17–22; no host-manifest change; fail-safe reads normalize to configuration/auth failure + Source-Only Fallback.
- **G04-2** (Provider capability failure and model-discovery UX): **settled** — `research/g04-2-provider-capabilities.md`. Policy: runtime capability checks only; manual Model ID first-class; discovery failure never invalidates a saved profile; presets are defaults, not capability claims; normalized connection-test categories reuse the M03 `TranslationFailureCategory` vocabulary.

## Scope

Implement five user-facing Provider Types through two shared normal-response protocol adapters. Persist versioned non-secret profile data, protect secrets under ADR-012, support model discovery plus manual Model ID, expose CRUD/test/select UI, and prove Source-Only Fallback on Provider failure. SSE parsing, prompt CRUD, scheduler retries, and persistent translation cache remain out of scope.

## Planned commit table

| # | Commit subject | Content | State |
|---|---|---|---|
| C0 | `docs(ai-subtitle): settle provider security and capability policy` | research notes, ADR-012, M04 report start, baseline pin | MERGED `8d0c9a50e` |
| C1 | `feat(settings): version provider profile persistence` | schema/repository/serializer/migration + tests | MERGED `3e1e1ed60` |
| C2 | `feat(settings): protect provider credentials across Android versions` | SecretStore/AndroidSecretStore + tests | MERGED `686768e54` |
| C3 | `feat(provider): add OpenAI-compatible normal responses` | adapter + fake-executor tests | MERGED `47af3c992` |
| C4 | `feat(provider): add Anthropic-compatible normal responses` | adapter + tests | MERGED `54085bdf4` |
| C5 | `feat(provider): add presets and model discovery` | presets + ModelCatalog + ConnectionTestResult | MERGED `46c780405` |
| C6 | `feat(settings): manage and test provider profiles` | feature-owned UI + the single host settings hook | MERGED `c65628db9`, `0cc5bd6c6` |
| C7 | `docs(ai-subtitle): record M04 provider and persistence checkpoint` | self-acceptance, report completion, CI evidence | `CHANGES_REQUIRED` at `6a2af2d2c`; correction paused |

## M04-C6 completion evidence

- C6 range: `130c47e02..0cc5bd6c6`; task review outcome recorded in `.superpowers/sdd/progress.md` as review clean at `0cc5bd6c6`.
- Deferred Minor findings from C6 review: trailing newline at `ProviderProfileRuntimeTest` EOF; best-effort rollback cleanup could preserve the original failure. Both are Minor and do not affect any M04 acceptance criterion; neither is changed in C7.

## Pause checkpoint and resume instructions (2026-09-12)

The C7 checkpoint at `6a2af2d2c` is pushed, and its local verification evidence is retained, but independent task review returned `CHANGES_REQUIRED`. Work was paused before any correction edits. A resumption must complete the following in order:

1. Restore the complete plan section 13 report template evidence: every created/modified/deleted file, implementation summary by commit, witnessed RED evidence and final test inventory, static-check results, GitHub Actions accounting, full device matrix, separate standards/spec self-review dispositions, decisions/research/upstream changes, deviations/risks/deferred findings, acceptance table, and next-milestone confirmation.
2. Close G04-1 and G04-2 source-verification obligations using current primary sources, or explicitly mark each `BLOCKED` with owner and resolution trigger. Do not leave “must re-confirm” language.
3. Update this report, `docs/ai-subtitle/progress.md`, the plan ledger, and the affected research/ADR status text. Commit and push the correction.
4. Re-run the independent M04-C7 task review. M05-C0 remains blocked until that review is clean.

Review findings being carried forward:

- The current file-inventory section is aggregate-only and does not satisfy `worker-plans/M03-M06-plan.md` section 13 lines 903–917.
- `research/g04-1-android-secret-storage.md` still needs primary-source closure for the Keystore/Auto-Backup behavior statement.
- `research/g04-2-provider-capabilities.md` still needs primary-source closure for temporally unstable provider endpoint/header details.
## Authoritative C7 local verification (JDK 17)

Commands run from `X:\SmartTube` (ASCII junction workaround for the non-ASCII workspace path) with `JAVA_HOME` set to `C:\Users\77182\.gradle\jdks\jetbrains_s_r_o_-17-amd64-windows.2`:

```text
git diff --check 8a4bd175b..0cc5bd6c6
cmd /c "set JAVA_HOME=...&& gradlew.bat :common:testStbetaDebugUnitTest --tests "com.liskovsoft.smartyoutubetv2.common.ai.subtitle.*" --no-daemon"
cmd /c "set JAVA_HOME=...&& gradlew.bat :common:lintStbetaDebug --no-daemon"
```

Observed outputs:

- `git diff --check` — clean, exit 0.
- Full ai-subtitle unit tests — `BUILD SUCCESSFUL in 1m 1s`; aggregated JUnit XML: **281 tests / 0 failures / 0 errors / 7 skipped**.
- Lint — `BUILD SUCCESSFUL in 1m 14s`; `590 actionable tasks`; lint report written to `common/build/reports/lint-results-stbetaDebug.html`.
- Supplementary JDK 11 settings/secret lane (`temurin-11`, `--tests "com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.*"`) — `BUILD SUCCESSFUL in 1m 15s`; **62 tests / 0 failures / 0 errors / 0 skipped**.

## Static and secret audit

- Inherited host-file audit: `git diff --name-status 8a4bd175b..0cc5bd6c6` filtered outside feature/docs/workflow leaves only `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/presenters/settings/SubtitleSettingsPresenter.java`. Its range diff is 2 insertions, 11 deletions: one import plus one `AiSubtitleSettingsPresenter.append(...)` call replacing the M02 test switch. No other existing SmartTube file is touched.
- CRLF audit: byte-level read of `SubtitleSettingsPresenter.java` reports `CRLF=93 LF=0 CR=0`; the file is entirely CRLF.
- Secret scan: `git grep` for high-confidence patterns (`sk-`, `AIza`, `ghp_`, `Bearer ...`, credential assignments) finds no real credential; the only credential-shaped literal is `synthetic-provider-credential-1234` in `SecretStoreTest.java`.
- Branding/GPL scan: `git grep` for `GPL|KissTranslator|fishjar|kiss-translator` over `common/src/main/java/.../ai/subtitle/**`, tests, and the feature string resource returns no matches.
- Network-import scan: OkHttp imports are confined to `provider/http/OkHttpRequestExecutor.java`; no network import exists in UI, integration, controller, or settings presenters.
- Forbidden-path scan: no `MediaServiceCore`, `SharedModules`, `exoplayer-amzn-2.10.6`, inherited `.github/workflows/CI.yml`, Gradle dependency/version, or renderer-internal path appears in `8a4bd175b..0cc5bd6c6`.
- Out-of-scope scan: no SSE/event-stream, scheduler retry, prompt CRUD, or persistent translation cache implementation is present; `TranslationFailure.isRetryable` is a domain label only, with no scheduler/retry loop in M04.

## File inventory

56 changed files in `8a4bd175b..0cc5bd6c6` (8103 insertions, 41 deletions). New feature-owned production files are under `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/{provider,provider/http,settings,settings/ui,integration}`; new tests are under `common/src/test/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/{provider,settings,settings/ui,integration}`. One feature-owned workflow, one feature string resource, and the AI subtitle docs are also changed. The only inherited SmartTube source file changed is `SubtitleSettingsPresenter.java`.

## GitHub Actions runs

- M04-C0..C5 tips are pushed; exact-SHA Actions status could not be read from the workstation API for any of them and requires GitHub UI verification.
- M04-C6 tip `0cc5bd6c6` and M04-C7 checkpoint are pushed by this C7 task. Exact-SHA status for the pushed tip is attempted at C7; the result is recorded in `../../.superpowers/sdd/M04-C7-report.md` (`BLOCKED` unless a real exact-SHA run can be read). An older or unrelated run is never used.

## Device matrix

`NOT RUN` for all rows. Physical-device backup/export, decryption-after-restore, and Android TV UI acceptance remain `NOT RUN`; automated tests do not imply a device PASS.

## Worker first-pass self-review dispositions

Worker first-pass self-review is **incomplete for checkpoint acceptance**: the independent C7 task review found that the required standards/spec dispositions were reduced or omitted. The report must be corrected before this item can be marked complete.

## Decisions / ADRs / glossary / research / upstream ledger

- **ADR-012** added (credential protection policy).
- Research notes created for G04-1 and G04-2.
- `CONTEXT.md`: Provider Profile clarified as non-secret configuration plus a separate credential reference.
- `upstream-patches.md`: exactly one M04-C6 host patch is recorded; its actual diff matches the ledger (one import + one feature-owned entry call).

## Deviations, unexpected discoveries, remaining risks, deferred Minor findings

- Web research was unavailable from this workstation during M04-C0; the C7 pause now assigns the outstanding source checks to the next M04-C7 resumption with explicit owner/trigger instead of leaving unowned “must re-confirm” language.
- Remaining risk: the API 17–22 plaintext fallback band is exercised by M04-C2 tests; decryption failure after device restore remains an expected physical-device acceptance path and is `NOT RUN`.
- Deferred Minor findings from C6 review are preserved in `.superpowers/sdd/progress.md`; neither affects an M04 acceptance criterion.

## Evidence-status vocabulary

- `PASS`: evidence was read from the exact named SHA/run and matches the claim.
- `BLOCKED`: the evidence exists or should exist but cannot be read from this workstation for the exact reason stated (for example GitHub Actions API 401/404, no authenticated read access, or no device).
- `NOT RUN`: the activity has not been performed (for example physical-device matrix).
No unavailable evidence is reported as PASS.

## Acceptance-criteria table (M04 exit/self-acceptance)

| # | Criterion | Disposition | Evidence |
|---|---|---|---|
| 1 | Five Provider Types resolve through exactly two normal-response protocol adapters | MET | `ProviderPreset` maps OpenAI/OpenRouter/DeepSeek/MiMo to `OPENAI_CHAT_COMPLETIONS` and Anthropic to `ANTHROPIC_MESSAGES`; `ProviderProfileResolverTest` proves all five resolve to one of the two shared adapters; only `OpenAiChatCompletionsAdapter` and `AnthropicMessagesAdapter` implement `ProtocolAdapter`. |
| 2 | Profile/schema migration and default repair are deterministic across restart/profile switch | MET | `ProviderProfileMigrationTest`, `ProviderProfileSerializerTest`, `ProviderProfileRepositoryTest`, and `AiSubtitleDataTest` cover corrupt JSON repair, future-schema rejection, stable IDs, CRUD, dangling selected/default repair, enabled-flag preservation, and app-profile switching; 62/62 settings/secret tests green on JDK 11. |
| 3 | Secrets satisfy the accepted API 17/backup/export policy and do not appear in logs/reports/artifacts | MET | `AndroidSecretStore` selects `KEYSTORE_AES_256_GCM` on API 23+ and the documented `APP_PRIVATE_PLAINTEXT` fallback below 23; `SecretStore.Failure`/`toString()` redact secret values; deletion/reset clears stored secrets; secret scan found no real credential in logs/reports/artifacts. Physical-device backup/export acceptance remains `NOT RUN`. |
| 4 | Manual model entry works when discovery is unsupported or fails | MET | `ModelCatalogTest` proves discovery failure/unsupported preserves the saved manual Model ID; `ProviderProfilesPresenterTest` covers save/edit of manual Model IDs; `repairSelectedModel` only substitutes when the profile has no saved Model ID. |
| 5 | Every Provider failure category proves Source-Only Fallback | MET | `AiSubtitleCueBridgeTest.everyProviderFailureCategoryKeepsSourceOnly` iterates every `TranslationFailureCategory` and asserts the cue stays source-only; `nullProviderStaysSourceOnlyWhenEnabled` and `authFailureStillLeavesCueSourceOnly` cover null-provider and auth paths. |
| 6 | No SSE, scheduler retry, prompt CRUD, or persistent translation cache was added | MET | Static scans over the M04 range find no SSE/event-stream, no scheduler/retry loop, no prompt CRUD repository/API, and no persistent translation cache; `TranslationFailure.isRetryable` is a domain label with no scheduler attached. |
| 7 | M04 Worker self-acceptance complete; range/evidence frozen; Worker continues to M05 without user relay | NOT MET | C7 review returned `CHANGES_REQUIRED` for incomplete report evidence and unowned G04-1/G04-2 source verification; correction is paused. M05-C0 must not start. |

## Confirmation

M04-C0 through M04-C6 are landed (product tip `0cc5bd6c6`). The C7 checkpoint at `6a2af2d2c` is pushed, but it is **not accepted**: independent review returned `CHANGES_REQUIRED`, and the correction is paused. Local verification is diagnostic and green: 281 ai-subtitle tests passed on JDK 17 with 0 failures, 62/62 settings/secret tests passed on JDK 11, lint green, CRLF/host-hook/static/secret scans clean. Exact-SHA GitHub Actions status for the pushed C7 tip remains `BLOCKED` at the workstation API and requires GitHub UI verification; it is never reported as PASS from local results.
