# AI Subtitle Upstream Modification Ledger

Status: verified against the real feature diff at M09 (2026-09-14); each entry records its
purpose, entry point, and regression check, and every host change in the branch appears below.

Actual existing SmartTube files modified by AI Subtitle: **5**

Existing SmartTube resource files modified: **1** (`common/src/main/res/values/ids.xml`)

Existing build files modified: **1** (`common/build.gradle`, one added dependency)

Planned conditional existing-file hooks: **1**, now **closed as unused**

Only modifications to files inherited from official SmartTube belong here. New feature files, new tests, new resources, new docs, and a new feature-owned workflow do not count as upstream-file patches, though they remain visible in Git.

## Verified against the real diff (M09-D)

Base: `master` is identical to `upstream/master` at `6e2e00bb8c989e089735c3f26fcd5511669f1597`, so
`git diff master...HEAD` **is** the complete feature diff against the verified upstream base — not
just the latest round of changes.

Verified properties of the whole branch diff. The M09 figure below was wrong and is corrected
here, per the M07–M09 correction run task 8:

| Candidate | Files | Insertions | Deletions |
|---|---:|---:|---:|
| `10d6a18cf` (the M09 candidate this section was first written against) | 184 | +29 324 | -1 |
| `0fef49ade` (M07–M09 correction run, phases 1–4 — recomputed, not carried over) | 190 | +33 012 | -1 |

- Submodule pointers are unchanged, and unchanged from upstream: `MediaServiceCore 82e9ccde`,
  `SharedModules 86f0327` (the diff against `6e2e00bb8c` is empty for both paths).
- No file under `exoplayer-amzn-2.10.6/`, `MediaServiceCore/`, or `SharedModules/` is modified.
- No ExoPlayer source file is modified: zero paths under `exoplayer-amzn-2.10.6/` appear in the
  diff.
- No Gradle dependency **version** is changed; exactly one dependency is added (`com.google.zxing:core:3.5.3`, for the QR pairing code the phone editor needs). `common/build.gradle`'s whole
  diff is that one line.
- Every host Java file has a single-digit-to-low-double-digit line delta, verified file by file
  (M07–M09 correction run, task 8): `PlayerUIController +43`, `PlaybackPresenter +2`,
  `SubtitleSettingsPresenter +6`, `SubtitleManager +35/-1`, `VideoPlayerGlue +4`. `ids.xml +1`.
  Eight files in total, 93 insertions and 1 deletion, with no rename, no mode change, no
  deletion and no whole-file reformatting (`git diff --summary` lists only `create mode` lines).
- No provider, HTTP, cache, or scheduling logic lives in a host file: those are all under `common/.../common/ai/subtitle/`.
- `VideoLoaderController.java` is **not** modified. The conditional hook was not needed: the adapter reaches the selected track through the existing public media-item service.

Non-product files that also appear in the branch diff and are **not** upstream patches:
`CONTEXT.md` (new glossary), `.gitignore` (one ignored local path, `/.gh-config/`), `.superpowers/sdd/M04-C7-research-report.md` (a process artifact committed by the M04 session), and `.github/workflows/ai-subtitle-validation.yml` (new feature-owned workflow).

## Patch budget

| Existing upstream file | Status | Actual patch surface | Merge risk |
|---|---|---:|---:|
| `common/.../app/presenters/PlaybackPresenter.java` | Implemented (M02) | Import + one controller registration | Low |
| `common/.../exoplayer/other/SubtitleManager.java` | Implemented (M02) | One bridge call around the cue list, one refresh-listener registration, one main-thread repaint hop, saved source cues | Low–Medium |
| `common/.../app/presenters/settings/SubtitleSettingsPresenter.java` | Implemented (M04-C6) | One import plus one feature-owned `AiSubtitleSettingsPresenter` entry | Low |
| `common/.../playback/controllers/PlayerUIController.java` | Implemented (M07) | Import, one dialog entry calling the feature-owned settings presenter, one status-text helper | Low |
| `smarttubetv/.../tv/ui/playback/other/VideoPlayerGlue.java` | Implemented (M07) | One import, one `putAction`, one `adapter.add` | Low–Medium |
| `common/src/main/res/values/ids.xml` | Implemented (M07) | One `item` entry for the new action id | Low |
| `common/build.gradle` | Implemented (M07) | One added `implementation` dependency (zxing core) for the pairing QR code | Low |
| `common/.../playback/controllers/VideoLoaderController.java` | **Closed as unused** | None. The adapter-only source path succeeded through the existing public media-item service, so no format-info handoff was needed | — |

No modifications are planned in `MediaServiceCore`, `SharedModules`, `exoplayer-amzn-2.10.6`, `PlayerData.java`, player layouts, `EmbedPlayerView`, or `SubtitlePainter`.

## Planned entry: `PlaybackPresenter.java`

WHY MODIFIED: Register `AiSubtitleController` in SmartTube's established ordered controller lifecycle.

PATCH SURFACE: One import and one `mEventListeners.add(...)` statement near the other `BasePlayerController` registrations.

AI MODULE DEPENDENCY: `integration/AiSubtitleController`.

ALTERNATIVE CONSIDERED: Global singleton observers or modifying multiple existing controllers. Rejected because lifecycle ownership and cleanup become implicit.

MERGE RISK: Low. Conflict possible when upstream reorders/adds controllers.

WHEN UPSTREAM CHANGES: Verify registration remains after video loading facilities are available and before lifecycle events that the AI controller must receive; rerun the lifecycle matrix.

## Planned entry: `SubtitleManager.java`

WHY MODIFIED: Combine the current source cue with an already available translated segment while retaining SmartTube's existing caption timing and view.

PATCH SURFACE: One bridge call around the final cue list before `SubtitleView.setCues`. A conditional tiny style branch is allowed only if M02 proves `Spannable` styling is stripped by current settings.

AI MODULE DEPENDENCY: `integration/AiSubtitleCueBridge`.

ALTERNATIVE CONSIDERED: Second overlay, parallel subtitle renderer, forked `SubtitlePainter`. Rejected for lifecycle/PiP complexity and high upstream conflict surface.

MERGE RISK: Low to medium because this method is central to caption rendering.

WHEN UPSTREAM CHANGES: Verify ASR normalization still precedes the bridge, null/empty cues remain valid, non-AI captions are byte-for-byte behavior-equivalent, styles/position work, and Source-Only Fallback remains intact.

## Planned entry: `SubtitleSettingsPresenter.java`

WHY MODIFIED: Provide the single user-visible entry point that opens the feature-owned enable switch and Provider Profile dialogs inside the existing subtitle settings area.

PATCH SURFACE: One import of `settings/ui/AiSubtitleSettingsPresenter` and one call `AiSubtitleSettingsPresenter.instance(getContext()).append(settingsPresenter)` inside `appendAiSubtitleSwitch`. The previous `AiSubtitleCueBridge` and `AiSubtitleData` imports and the M02 test-provider switch body are removed by the same patch.

AI MODULE DEPENDENCY: `settings/ui/AiSubtitleSettingsPresenter`.

ALTERNATIVE CONSIDERED: New top-level SmartTube settings category. Rejected as wider navigation surface for one subtitle feature.

MERGE RISK: Low.

WHEN UPSTREAM CHANGES: Verify entry placement, remote-control navigation, dialog disposal, localization, and that removing/disabling the feature leaves existing subtitle settings unchanged.

## Conditional entry: `VideoLoaderController.java`

WHY MODIFIED: Only if a new-file source adapter cannot reliably and cheaply match the current selected track to `MediaSubtitle` through existing APIs.

PATCH SURFACE: One immutable format-info handoff/callback. No provider, parsing, scheduling, or cache logic.

AI MODULE DEPENDENCY: `source/SmartTubeSubtitleSourceAdapter`.

ALTERNATIVE CONSIDERED: independent access to the existing/cached `MediaItemService.getFormatInfoObserve(videoId)` result. This alternative is the approved default and must be tested first.

MERGE RISK: Medium because `processFormatInfo` is in the video-open critical path.

WHEN UPSTREAM CHANGES: Verify callback remains non-blocking, cannot throw into playback, and never changes the format-info/player-open order.

## Implemented entry: `PlayerUIController.java`

WHY MODIFIED: Give the player a direct AI subtitle entry and a status line, so a failure or a
missing configuration is visible without leaving playback.

PATCH SURFACE: One import block, one entry button that opens `AiSubtitleSettingsPresenter`, and
one status helper that reads the bridge's runtime status. No translation, HTTP, cache, or
scheduling logic.

AI MODULE DEPENDENCY: `integration/AiSubtitleCueBridge`, `integration/AiSubtitleRuntime`,
`settings/AiSubtitleData`, `settings/ui/AiSubtitleSettingsPresenter`.

REGRESSION CHECK: The entry opens and closes, shows "not configured" without a provider, and
never throws while the player is being torn down.

## Implemented entry: `VideoPlayerGlue.java`

WHY MODIFIED: The AI subtitle action must be reachable from the player's own action row.

PATCH SURFACE: One import, one `putAction`, and one unconditional `adapter.add`. It is added
outside the legacy per-button toggles because the feature is a primary entry, not a tweak.

AI MODULE DEPENDENCY: `tv/ui/playback/actions/AiSubtitleAction`.

REGRESSION CHECK: The action is present in the player row, other actions keep their existing
order and visibility toggles, and remote-control focus still reaches the caption action.

## Implemented entry: `common/src/main/res/values/ids.xml` and `common/build.gradle`

WHY MODIFIED: The action needs an id, and the phone pairing page needs to render a QR code.

PATCH SURFACE: One `item` entry in `ids.xml`; one added `implementation` line in
`common/build.gradle`. No existing dependency version is touched.

REGRESSION CHECK: The id resource resolves, the build assembles, and no dependency resolution
or packaging behaviour changes for the existing modules.

## Explicitly rejected historical patch shape

SmartTube PR #5839 modifies roughly 19 files and adds about 1,699 lines, including player UI, engine interfaces, settings serialization, renderer/painter code, embed view/layout, services, and resource files; several files also carry unrelated executable-bit changes. It demonstrates useful dual-cue concepts but is **Reference Only**, not a cherry-pick candidate for the current architecture.

## Upstream sync checklist

Run this before any future rebase or merge onto a newer official SmartTube. Do **not** merge
upstream as part of reading this document.

1. Working tree clean: commit or set aside local work first; never start a merge with
   uncommitted changes.
2. Record the old upstream base: `git rev-parse upstream/master` and
   `git merge-base HEAD upstream/master`, so the diff that follows can be reproduced.
3. Try the merge on an independent branch or a separate worktree, never on the feature branch
   directly.
4. Inspect the incoming changes to every file in the patch-budget table above, one entry at a
   time, and re-check the hook signature and the caption identity it depends on.
5. Verify the conditional `VideoLoaderController` entry stays unused: if upstream removes or
   changes the public format-info access the adapter relies on, the hook becomes necessary and
   must be re-evaluated before the merge is accepted.
6. Run the targeted suites for the touched areas, then the full GitHub Actions validation on the
   merged SHA.
7. Run the device core regression: caption discovery, selected-track identity, cue rendering,
   seek, off/on, video change, settings persistence, provider failure.
8. Compare the ledger against the actual diff again; every host-file change must appear above,
   and nothing above may be stale.

## Regression checklist for the integration

Keep these scenarios in the M09 matrix; they are the paths a rebase is most likely to break.

| Area | Scenario |
|---|---|
| Track selection | Manual and auto-generated captions in the same language, track switch during playback |
| Source | Timed-text download, missing or ambiguous track, empty captions, over-limit timeline |
| Cue bridge | Rendered cue matches the source timing, original captions unchanged with the feature off |
| Lifecycle | Seek, pause, resume, captions off, release, video change, background/foreground |
| Persistence | Settings survive restart; provider, prompt, and language changes take effect |
| Request | The selected prompt is what is actually sent; retry budget and cancellation hold |
| Streaming | SSE cancellation, mid-stream disconnect, fallback to a plain request, late drafts |
| Source results | A superseded source load never overwrites the current one; late results never render |
