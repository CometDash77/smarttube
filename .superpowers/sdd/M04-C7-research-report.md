# M04-C7 G04 Research Correction Report

## Status

**DONE_WITH_CONCERNS (research scope)**

- G04-1 primary-source retrieval: complete.
- G04-2 primary-source retrieval: complete for all five Provider Types.
- M04 acceptance: **BLOCKED** by the newly confirmed backup/export-exclusion gap in ADR-012. No production fix was authorized or made.

## Scope and repository state

- Repository: `D:\obsidian\工程\VIBECODING项目\smartube\SmartTube`
- Branch: `feature/ai-bilingual-subtitles`
- Starting HEAD: `da65fa915c12626118ad9f050899a76bff58a135`
- Research date: 2026-09-13
- Scope honored: documentation/research only; no production code, tests, build files, submodules, or unrelated feature documents changed.

## Method

Current official documentation was retrieved with the available web research surface. Only Android/AndroidX/AOSP and each provider's own documentation domain were used for technical facts. Local repository claims were checked against the current branch files. Memory and secondary sources were not used as evidence.

## G04-1 URLs and access results

| URL | Result | Evidence obtained |
|---|---|---|
| <https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec> | PASS | Official API reference states `KeyGenParameterSpec` was added in API level 23. |
| <https://developer.android.com/privacy-and-security/keystore> | PASS | Official Android security page states the Android Keystore provider was introduced in API 18 and key material remains non-exportable/outside the app process. |
| <https://source.android.com/docs/security/features/keystore#android_60> | PASS | Official AOSP history states Android 6.0 added symmetric AES/HMAC primitives. |
| <https://developer.android.com/identity/data/autobackup> | PASS | Official Auto Backup page states API 23 applicability, default `SharedPreferences` inclusion, no-backup-directory exclusion, and configurable include/exclude rules. |
| <https://developer.android.com/privacy-and-security/risks/backup-best-practices> | PASS | Official guidance recommends excluding particularly sensitive data or requiring backup encryption. |
| <https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences> | PASS | Official AndroidX reference warns not to back up encrypted preferences because the restore key is likely absent, and directs exclusion by backup rules. |

Disposition:

- API 23 and non-exportability statements are verified.
- The former absolute claim that Keystore key material “does not travel with a backup” was narrowed to the wording Android actually supports.
- The former policy conclusion that encrypted preferences may be backed up without exclusion was rejected as inconsistent with official AndroidX guidance.
- ADR-012 was reopened. The research source gate is complete; design/implementation is blocked with an explicit owner, trigger, and exact reason in the G04-1 note.

## G04-2 URLs and access results

| Provider | URL | Result | Evidence obtained |
|---|---|---|---|
| OpenAI | <https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create> | PASS | `POST /v1/chat/completions` example at the OpenAI API host. |
| OpenAI | <https://developers.openai.com/api/reference/resources/models/methods/list> | PASS | `GET /v1/models` example. |
| OpenAI | <https://developers.openai.com/api/reference/overview#authentication> | PASS | Bearer authentication requirement. |
| Anthropic | <https://platform.claude.com/docs/en/api/overview> | PASS | API base, `/v1/messages`, `/v1/models`, Bearer or legacy `x-api-key`, required `anthropic-version`, and JSON content type. |
| Anthropic | <https://platform.claude.com/docs/en/api/http/messages/create> | PASS | Message endpoint and direct HTTP headers. |
| Anthropic | <https://platform.claude.com/docs/en/api/models/list> | PASS | Model-list endpoint. |
| OpenRouter | <https://openrouter.ai/docs/api/api-reference/chat/send-chat-completion-request> | PASS | Full chat-completion URL and Bearer authentication. |
| OpenRouter | <https://openrouter.ai/docs/api/api-reference/models/get-models> | PASS | Full models URL and Bearer authentication. |
| OpenRouter | <https://openrouter.ai/docs/app-attribution> | PASS | Attribution scope of `HTTP-Referer` and `X-OpenRouter-Title`. |
| DeepSeek | <https://api-docs.deepseek.com/guides/function_calling> | PASS | Default base, full chat URL, and Bearer example. |
| DeepSeek | <https://api-docs.deepseek.com/api/create-chat-completion/> | PASS | `/chat/completions` path. |
| DeepSeek | <https://api-docs.deepseek.com/api/list-models> | PASS | `/models` path. |
| DeepSeek | <https://api-docs.deepseek.com/api/deepseek-api/> | PASS | HTTP Bearer scheme. |
| MiMo | <https://mimo.mi.com/docs/en-US/quick-start/summary/first-api-call> | PASS | OpenAI/Anthropic compatibility and pay-as-you-go bases. |
| MiMo | <https://mimo.mi.com/docs/en-US/api/chat/openai-api> | PASS | Full OpenAI-format chat URL and direct request headers. |
| MiMo | <https://mimo.mi.com/docs/en-US/api/chat/anthropic-api> | PASS | Full Anthropic-format message URL and direct request headers. |
| MiMo | <https://mimo.mi.com/docs/en-US/api/model/list-models> | PASS | Full model-list URL and `api-key` authentication example. |
| MiMo | <https://mimo.mi.com/docs/en-US/quick-start/faq/api-integration> | PASS | `api-key` or Bearer authentication. |

Disposition:

- Every endpoint/authentication/version-header fact in the G04-2 table now has a direct official link.
- No provider source is BLOCKED.
- Facts and project recommendations are separate; no new provider design was introduced.

## Files changed

- `docs/ai-subtitle/research/g04-1-android-secret-storage.md`
- `docs/ai-subtitle/research/g04-2-provider-capabilities.md`
- `docs/ai-subtitle/decisions.md`
- `docs/ai-subtitle/worker-plans/M03-M06-plan.md`
- `.superpowers/sdd/M04-C7-research-report.md` (force-added because `.superpowers/sdd/.gitignore` ignores task reports by default)

## Verification

- `git diff --check` — PASS (exit 0; final run before commit).
- Documentation secret scan — PASS. `rg --pcre2` found no credential-shaped OpenAI/Anthropic-style key, GitHub token, AWS access-key ID, Google API key, or private-key marker in the five changed documentation/report files (`rg` exit 1 = no matches).
- Production-path diff audit — PASS. The changed tracked paths before staging were four files under `docs/`; the fifth deliverable is the ignored `.superpowers/sdd/` report. No production, test, resource, manifest, build, workflow, or submodule path changed.

## Unresolved items

1. **ADR-012 backup/export exclusion — BLOCKED**
   - Owner: M04 Commander/design owner, then M04 correction Worker.
   - Trigger: before ADR-012 returns to Accepted, before M04-C7 re-review completion, and before M05-C0.
   - Exact reason: the current secret file is a named `SharedPreferences` store covered by Auto Backup's default include set; official AndroidX guidance directs exclusion for Keystore-encrypted preferences, and no exclusion is demonstrated for the API 17–22 plaintext fallback. This task cannot change production code or the host manifest.
2. The separate M04-C7 report-completeness Important finding is outside this G04-only assignment and remains untouched.
