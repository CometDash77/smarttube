# AI Subtitle Decision Log

This file is the authoritative decision ledger for the feature. ADR-001 through ADR-006 were accepted with the overall Phase 0 architecture approval on 2026-09-11; later reversals must add a superseding entry rather than erase history.

## ADR-001 — Isolate the feature inside `common`

Status: Accepted by user

Date: 2026-09-11

Decision: Place new AI subtitle implementation files in a dedicated package tree under SmartTube's `common` module. Do not introduce a new Gradle module in the first implementation.

Reason: `common` already owns player controllers, subtitle output, preferences, presenters, MediaServiceCore access, and the existing network utilities. Package isolation gives a small upstream patch surface without new build wiring or dependency cycles.

Alternatives: a separate Gradle module; implementation spread through existing player/settings classes.

Consequences: architecture tests and review must enforce package boundaries because Gradle cannot enforce them. A module extraction remains possible after the seams stabilize.

Upstream impact: new files plus three expected hooks in existing SmartTube files.

## ADR-002 — Treat KissTranslator as a behavioral oracle, not a source donor

Status: Accepted by user

Date: 2026-09-11

Decision: Re-express relevant behavior, invariants, edge cases, and tests in an independent Android implementation. Do not copy or mechanically translate KissTranslator source or fixtures into the SmartTube MIT tree without a separate legal/compliance decision.

Reason: SmartTube is MIT-licensed and KissTranslator is GPL-3.0. Changing JavaScript to Java/Kotlin does not by itself remove copyright/license obligations.

Alternatives: direct source port and GPL compliance for the combined distribution; obtain separate permission from copyright holders.

Consequences: migration tasks must contain behavior specifications and independently authored fixtures. This is an engineering policy, not legal advice.

Upstream impact: none.

## ADR-003 — Five Provider Types share two protocol adapters

Status: Accepted by user

Date: 2026-09-11

Decision: Expose OpenAI-Compatible, Anthropic-Compatible, OpenRouter, DeepSeek, and MiMo as user-facing Provider Types, implemented primarily by `OpenAiChatCompletionsAdapter` and `AnthropicMessagesAdapter`. Provider presets supply default URLs, authentication, discovery, headers, and parameter policies.

Reason: Current official documentation shows OpenRouter, DeepSeek, and MiMo intentionally reuse one or both standard protocols. Five independent clients would duplicate serialization, SSE, error, cancellation, and model-list code.

Alternatives: one client per brand; only a generic custom endpoint type.

Consequences: capability checks remain runtime/profile-specific; compatibility does not imply identical optional parameters. Manual model entry is mandatory.

Upstream impact: none outside new feature/settings files.

## ADR-004 — Reuse the existing subtitle view before considering a renderer fork

Status: Accepted by user

Date: 2026-09-11

Decision: Render source and translation as one decorated ExoPlayer `Cue` through the existing `SubtitleManager` and `SubtitleView`. Do not fork `SubtitlePainter` in the baseline.

Reason: One cue preserves SmartTube timing, placement, PiP, and visibility behavior with a one-call integration hook. Historical PR #5839 proved dual-line cue composition is viable but expanded into a large renderer/player patch.

Alternatives: second subtitle overlay; forked `SubtitlePainter`; parallel subtitle source merged inside ExoPlayer.

Consequences: M02 must validate line styling on real devices and Robolectric. A renderer fork requires a new decision because it materially increases upstream merge cost.

Upstream impact: planned narrow hook in `SubtitleManager.java`.

## ADR-005 — Acquire the full source track through a feature adapter

Status: Accepted by user

Date: 2026-09-11

Decision: The AI controller/source adapter will obtain SmartTube `MediaItemFormatInfo` for the active video and load the selected `MediaSubtitle` timeline independently. It will not build lookahead solely from currently displayed cues.

Reason: displayed cues arrive too late for lookahead. Current `Video` and selected Exo `Format` do not expose the full timed-text source URL through a stable application API. Reusing MediaServiceCore from new files avoids modifying the submodule or widening the global player listener interface.

Alternatives: a new `onFormatInfo` hook in `VideoLoaderController`; change `Video` to retain subtitles; intercept only live cues.

Consequences: M02 must measure whether format-info access is cached and prove reliable matching between the active track and `MediaSubtitle`. If not, this decision is revisited before adding a narrow loader hook.

Upstream impact: none in the default path; conditional low-risk hook if validation fails.

## ADR-006 — Use a dedicated versioned settings repository

Status: Accepted by user

Date: 2026-09-11

Decision: Persist Provider Profiles, Prompt Profiles, runtime selection, and AI subtitle preferences in a dedicated versioned `AiSubtitleData` store that reuses SmartTube persistence primitives. Do not append the whole feature schema to `PlayerData` serialization.

Reason: AI settings have independent schema evolution, CRUD, secrets, and export/backup concerns. Keeping them separate prevents fragile changes to a high-churn upstream playback preference class.

Alternatives: extend `PlayerData`; introduce a database; use a third-party preferences framework.

Consequences: the repository needs explicit migrations, default repair, stable IDs, and separate secret handling. No new persistence dependency is planned.

Upstream impact: settings entry only; new storage files/classes are feature-owned.

## ADR-007 — Personal project remote and upstream relationship

Status: Accepted by user

Date: 2026-09-11

Decision: `origin` is the user's personal project repository `https://github.com/CometDash77/smarttube.git`. `upstream` is the official SmartTube repository `https://github.com/yuliskov/SmartTube.git`. Project work occurs on `feature/ai-bilingual-subtitles`; upstream is a source for synchronization, not a delivery remote. Repository ownership/role is distinct from GitHub visibility; the GitHub API reported `isPrivate: false` during Phase 0.

Reason: The user identified this personal repository as the project's durable remote storage, correcting the earlier description of it as merely a fork.

Consequences: reports must distinguish personal project history from official upstream history. Never push project commits to `upstream`.

Upstream impact: Git configuration only.

## ADR-008 — GitHub Actions is the authoritative build and test environment

Status: Accepted by user

Date: 2026-09-11

Decision: Compilation, lint, unit tests, and APK assembly are executed and accepted through GitHub Actions in `origin`. Local checks may provide fast diagnostics but are not the authoritative pass signal.

Reason: The user explicitly assigned build and test work to GitHub Actions. This also gives reproducible evidence attached to pushed commits.

Alternatives: local workstation builds as the release gate.

Consequences: before production implementation, add an AI-subtitle-specific workflow as a new file rather than broadening the existing upstream `CI.yml`. It must run on the feature branch and pull requests, initialize recursive submodules, execute relevant unit tests, lint, and assemble the beta release/debug artifact as appropriate. Worker reports cite workflow URL/run ID and commit SHA.

Upstream impact: none if a new feature-owned workflow file is used.

## ADR-009 — One Worker handoff per Milestone

Status: Accepted by user

Date: 2026-09-11

Decision: Each implementation Milestone is delivered as one Stage Package to the external Worker and returned once as one consolidated report. A Stage Package may define several internal workstreams and a small set of logical commits, but those do not require user relay or intermediate approval.

Reason: The user will transport work at milestone granularity and explicitly rejected overly atomic relay. This preserves reviewable commits without turning the user into a message queue.

Alternatives: one user handoff per atomic commit/task; one unstructured package for the entire project.

Consequences: Commander review occurs at the Milestone boundary. Packages must pre-authorize bounded fallback behavior and precise patch limits because the Commander will not review each internal commit before the Worker proceeds. M01 was Commander-only, leaving eight Worker handoffs for M02 through M09.

Upstream impact: none.

## ADR-010 — Keep JDK 17 authoritative and isolate legacy Robolectric execution

Status: Accepted by Commander technical ruling

Date: 2026-09-11

Decision: GitHub Actions continues to use Temurin JDK 17 for normal unit tests, lint, and APK assembly. Until the inherited Robolectric 4.6.1 dependency is upgraded in an explicitly authorized later task, a supplementary JDK 11 job may execute only the Robolectric-backed `AiSubtitleDataTest` suite required by M02.

Reason: Robolectric 4.6.1 cannot complete its runner lifecycle on JDK 17 because its bundled bytecode tooling rejects Java 17 class files. Silently ignoring the suite does not satisfy the M02 persistence-test requirement, while changing Gradle dependencies is outside the approved M02 patch boundary. AGP 7.4 and Gradle 7.5 support the narrow JDK 11 test lane.

Alternatives: treat skipped tests as accepted; upgrade Robolectric in M02; lower the entire authoritative workflow to JDK 11; replace the Android persistence test with a mock that never exercises SharedPreferences. All are rejected for M02.

Consequences: reports must distinguish the supplementary compatibility-test job from the JDK 17 acceptance job and record real executed test counts. The supplementary job does not authorize builds, lint, releases, or general test execution on JDK 11. A later planned dependency/toolchain task should remove this exception once Robolectric is upgraded.

Upstream impact: none; feature-owned workflow only.

## ADR-011 — Deliver M03 through M06 as one uninterrupted Worker program

Status: Accepted by user

Date: 2026-09-12

Decision: M03, M04, M05, and M06 are handed to one Worker once through `docs/ai-subtitle/worker-plans/M03-M06-plan.md`. The Worker completes all four milestones continuously and returns one consolidated delivery after M06. During that run, the Worker performs and records first-pass self-acceptance separately for each milestone and freezes each milestone's base/tip, report, commits, tests, and CI evidence. After the one final return, the Commander uses Superpowers to perform an independent second review separately for M03, M04, M05, and M06.

Reason: The architecture and dependency chain are already accepted, so four separate Worker handoffs would repeat context ingestion and coordination overhead. One continuous execution preserves context and permits safe research/CI overlap, while milestone-scoped self-acceptance and Commander review keep defects attributable and avoid one opaque M03–M06 mega-diff.

Alternatives: retain one user handoff and return per milestone; insert Commander review between every milestone; collapse M03–M06 into one implementation/review range; review only the final aggregate diff. The first two increase elapsed development time, while the latter two weaken defect isolation and rollback safety.

Consequences: ADR-009 remains the default for other milestones but is superseded for the M03–M06 handoff boundary. Milestone product commits still land in order. The Worker does not wait for intermediate Commander review, but may advance only after its milestone self-acceptance passes. Commander findings are packaged and corrected by affected milestone/range, then freshly verified and re-reviewed before acceptance. The final Worker return contains all four milestone reports and the complete ordered commit/evidence range.

Upstream impact: none; delivery and review process only.

## ADR-012 — Protect provider credentials with a separated, Keystore-backed secret store

Status: Accepted by the Worker under the G04-1 plan gate; supersedes the open M04 ruling below

Date: 2026-09-12

Decision: Provider credentials never live inside serializable profile data. A dedicated `SecretStore` owns them. On API 23+ the secret is encrypted with a non-exportable AES-256-GCM key generated in AndroidKeyStore (fixed, versioned alias) and only ciphertext is persisted in a feature-owned private store; on API 17–22, where `KeyGenParameterSpec` does not exist (verified against the local SDK API database), the store falls back to the app-private preferences area as a documented compatibility exception. No host-manifest change is made: the M04 upstream budget authorizes only the settings-entry hook, and none is needed because Keystore key material does not travel with a backup. Any read or decryption failure normalizes to a configuration/auth failure and Source-Only Fallback.

Reason: The app floor is API 17 (`SharedModules/constants.gradle`) while Keystore-backed AES-GCM requires API 23 (`KeyGenParameterSpec`, `KeyProperties` — verified locally in the SDK API database); `android:allowBackup="true"` with no exclusion rules means plaintext secrets would otherwise be captured by Auto Backup on API 23+.

Alternatives: manifest `fullBackupContent` exclusion (outside the authorized upstream budget); memory-only secrets (unusable UX); plaintext storage on every API band (unacceptable); a third-party crypto dependency (rejected — no new dependencies).

Consequences: Legacy-band devices get weaker protection by explicit, documented policy; decryption failure on a restored device is a first-class, expected path (re-enter the key) rather than an error state; the store is exercised with fakes on the JVM lane and with Robolectric where practical. Full evidence: `docs/ai-subtitle/research/g04-1-android-secret-storage.md`.

Upstream impact: none (feature-owned files only).

## Open rulings

- M02 evidence will decide whether ADR-005 can remain hook-free.
- Resolved by ADR-012 (M04-C0): the exact Android Keystore/fallback policy is settled; see the G04-1 research note for the verified evidence.
- A `SubtitlePainter` fork is prohibited unless ADR-004 is explicitly superseded.
