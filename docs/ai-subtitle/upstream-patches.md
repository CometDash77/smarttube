# AI Subtitle Upstream Modification Ledger

Status: Phase 0 planning ledger

Actual existing SmartTube files modified by AI Subtitle: **0**

Planned unconditional existing-file hooks: **3**

Planned conditional existing-file hooks: **1**

Only modifications to files inherited from official SmartTube belong here. New feature files, new tests, new resources, new docs, and a new feature-owned workflow do not count as upstream-file patches, though they remain visible in Git.

## Patch budget

| Existing upstream file | Status | Intended patch surface | Merge risk |
|---|---|---:|---:|
| `common/.../app/presenters/PlaybackPresenter.java` | Planned | Import + one controller registration | Low |
| `common/.../exoplayer/other/SubtitleManager.java` | Planned | One cue-bridge invocation and, only if required, a narrow decorated-style branch | Low–Medium |
| `common/.../app/presenters/settings/SubtitleSettingsPresenter.java` | Planned | One AI subtitle settings entry | Low |
| `common/.../playback/controllers/VideoLoaderController.java` | Conditional | One format-info callback/handoff only if the adapter-only source spike fails | Medium |

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

WHY MODIFIED: Provide the user-visible entry point and enable/disable control in the existing subtitle settings area.

PATCH SURFACE: One category/option entry that opens feature-owned settings presenters. Detailed Provider, Model, Prompt, and language UI remains in new files.

AI MODULE DEPENDENCY: `settings/AiSubtitleSettingsPresenter` and `AiSubtitleData`.

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

## Explicitly rejected historical patch shape

SmartTube PR #5839 modifies roughly 19 files and adds about 1,699 lines, including player UI, engine interfaces, settings serialization, renderer/painter code, embed view/layout, services, and resource files; several files also carry unrelated executable-bit changes. It demonstrates useful dual-cue concepts but is **Reference Only**, not a cherry-pick candidate for the current architecture.

## Upstream sync checklist

1. Fetch `upstream` and review changes to the four files above before merging/rebasing.
2. Compare the actual feature diff against this ledger; reject undeclared host-file changes.
3. Check for whole-file formatting, mode changes, renames, and dependency upgrades.
4. Run the GitHub Actions upstream-regression workflow for the exact integration SHA.
5. Exercise caption discovery, selected-track identity, cue rendering, seek, off/on, video change, settings, persistence, and provider failure.
6. Update each ledger entry only when the actual patch or upstream dependency changed.
