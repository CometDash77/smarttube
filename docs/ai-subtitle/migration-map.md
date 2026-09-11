# KissTranslator to SmartTube Migration Map

Status: **Proposed — awaiting Phase 0 approval**

Reference revision: KissTranslator `226e5780f5ccee1cd42d4167518632318c2f7d7b`

## Classification rules

- **A — Behavior Port**: reproduce externally meaningful behavior/invariants with an independent Android implementation and new tests.
- **B — Algorithm Port**: reimplement a bounded algorithm from a written behavioral specification; do not translate source line-by-line.
- **C — Concept / Reference Only**: use the design lesson, not the code or browser integration.
- **D — Reject**: outside scope, superseded by SmartTube, or incompatible with the host.

Because KissTranslator is GPL-3.0 and SmartTube is MIT, every A/B item below means clean-room behavioral reimplementation unless a later license decision says otherwise. Existing Kiss tests can identify cases; final target tests and fixtures must be independently expressed.

## Subtitle processing

| KissTranslator component | Class | Behavior retained | SmartTube target |
|---|---:|---|---|
| `src/subtitle/youtubeSubtitleProcessing.js` normalization | A | Decode/clean visible text, remove empty/non-speech noise, preserve timing gaps, remove adjacent exact duplicates without collapsing meaningful repeats | `source/SubtitleNormalizer` |
| `youtubeSubtitleProcessing.js` coarse ASR expansion | B | Recover approximate word timing when one ASR segment contains many words; cap at the next effective event | `segmentation/AsrTimingEstimator` |
| `youtubeSubtitleProcessing.js` rule segmentation | B | Boundary priority from punctuation, pauses, duration, word count, and no-space-language length | `segmentation/RuleSentenceBreaker` |
| `youtubeSubtitleProcessing.js` chunk construction | B | Bounded character/token-size groups, prefer sentence/pause boundaries near the target, allow a single oversize unit rather than lose text | `segmentation/TranslationChunker` |
| `src/subtitle/sentenceBreaker.js` robust gap statistics | B | Detect fill gaps/outliers with median/percentile/MAD-style statistics; score punctuation, capitalization, gaps, event boundaries; split long and merge undersized sentences; clip overlap | `segmentation/StatisticalSentenceBreaker` |
| `src/subtitle/subtitleBoundaryProtocol.js` boundary-v2/v3 | A | Indexed continuous source coverage, end-index mapping, source-text reconstruction, validation, legacy rejection/migration | `segmentation/BoundaryProtocol` |
| `src/subtitle/subtitleSegmentationMetrics.js` | B | Coverage, missing/duplicate source tokens, empty/invalid/overlap/non-monotonic ranges, duration/length warnings, no-space tokenization | `segmentation/SegmentationMetrics` plus test oracle |
| `src/subtitle/youtubeAiSegmentation.js` accepted-prefix/tail recovery | A | Accept only a validated continuous prefix, retry uncovered tail once, then deterministic fallback for the remainder; final full-coverage validation | `segmentation/AiSegmentationCoordinator` |
| `youtubeAiSegmentation.js` lazy chunk scheduler | A | Current/lookahead-window admission, serial pump, seek reprioritization, deduplication, stale video/track checks, fallback on failure | `scheduler/SegmentationScheduler` and later unified `TranslationScheduler` |
| `processRemainingChunksAsync` eager whole-video path | D | None; eager background processing conflicts with cost and lookahead goals | No target |

## Playback, rendering, and source integration

| KissTranslator component | Class | Behavior retained | SmartTube target |
|---|---:|---|---|
| `src/subtitle/BilingualSubtitleManager.js` time lookup | B | Ordered timeline binary search and source/translation lookup | `integration/AiSubtitleCueBridge` |
| `BilingualSubtitleManager.js` lifecycle state | A | Destroy-time cancellation, stale-result rejection, render only current cue, pause/seek-aware admission | `session/TranslationSession`, `AiSubtitleController` |
| `BilingualSubtitleManager.js` current seek implementation | C | Preserve the documented failure lesson: merely cancelling a throttle is insufficient | Target improves it with scheduling epoch, cancellation, and stale guards |
| `BilingualSubtitleManager.js` DOM overlay and drag UI | D | None | Existing SmartTube `SubtitleView` and settings |
| `src/subtitle/YouTubeCaptionProvider.js` page/player observers | D | None | SmartTube player lifecycle events |
| `YouTubeCaptionProvider.js` processing version/abort behavior | A | Generation bump, abort old work, reject stale video/track results | `session/TranslationSession` |
| `YouTubeCaptionProvider.js` bounded metadata context | C | Use title/description and optionally a capped transcript summary; never copy browser acquisition | `translation/ContextBuilder` |
| `src/subtitle/youtubeCaptionTracks.js` page metadata/caption discovery | D | None | SmartTube `MediaItemFormatInfo` and `MediaSubtitle` |
| `src/subtitle/subtitle.js` provider registry and browser injection | D | None | `PlaybackPresenter` controller registration |
| subtitle list/export/download modules | D | None; explicitly out of scope | No target |
| hover lookup, word tooltips, blur-learning modes | D | None; explicitly out of scope | No target |

## Translation scheduling, cache, and recovery

| KissTranslator component | Class | Behavior retained | SmartTube target |
|---|---:|---|---|
| `BilingualSubtitleManager.#triggerTranslations` | A | Lookahead scanning from current position, untranslated/in-flight guards | `scheduler/TranslationScheduler` |
| `src/apis/index.js` batch queue | C | Pool small tasks into bounded batches; key queues by output-affecting configuration; serialize context-sensitive work | Purpose-built subtitle `TranslationScheduler`, not the generic page queue |
| `src/apis/index.js` cache signature | B | Include source, languages, prompt signature, engine/schema version, text format, and bounded context fingerprint | `cache/TranslationCacheKey` |
| browser CacheStorage/http-cache polyfill | D | None | Android memory cache first; bounded persistent cache later |
| message history keyed by API slug | C | Rolling context must be isolated and cleared when identity changes | `translation/RollingContext`, keyed by Translation Session |
| per-line streaming callbacks | A | Partial text can update only its owning current segment; final result replaces draft; abort/stale results are ignored | `provider/TranslationStream`, `integration/AiSubtitleCueBridge` |
| retry scattered across generic APIs | C | Normalize transient vs terminal errors; bounded retry; no provider-specific retry in scheduler | `translation/RetryPolicy`, `provider/TranslationFailureMapper` |
| `[Translation failed]` stored as translated text | D | Do not poison content/cache with a UI sentinel | Explicit failure state + Source-Only Fallback |

## Provider and prompt systems

| KissTranslator component | Class | Behavior retained | SmartTube target |
|---|---:|---|---|
| `src/apis/trans.js` multi-provider request generator | C | Separate request generation, streaming parse, and normalized result; study edge cases only | Two Android `ProtocolAdapter`s backed by SmartTube OkHttp |
| Kiss provider-specific browser fetch/CORS bridge | D | None | Native Android OkHttp 3.12.13 |
| `src/config/api.js` API settings and capability sets | A | Saved profile identity, capabilities, model/manual model, stream/context flags, defaults | `settings/ProviderProfile`, `ProviderCapabilities` |
| `src/config/prompt.js` preset + custom prompt lifecycle | A | Stable IDs, built-ins that cannot be deleted, copy-to-custom, normalization, versioned migration, default repair | `prompt/PromptProfile`, `PromptRepository` |
| Kiss prompt categories unrelated to subtitles | D | Dictionary, page translation, OCR/PDF and other unrelated templates | No target |
| batch translation and subtitle segmentation prompts | C | Use indexed/structured output and explicit coverage rules; author target prompts independently | Target built-in Prompt Profiles |
| video context summarization request | C | Optional mature-stage enhancement only if measured value exceeds cost/latency | M08 optional `VideoContextSummarizer` |

## Tests and fixtures

| KissTranslator component | Class | Use | SmartTube target |
|---|---:|---|---|
| `youtubeSubtitleProcessing.test.js` | A | Case inventory for normalization, ASR timing, sentence and chunk boundaries | Independent JUnit parameterized tests |
| `sentenceBreaker.test.js` | A | Statistical boundary and pathological timing cases | Independent JUnit tests |
| `youtubeAiSegmentation.test.js` | A | Tail retry, partial stream, lazy windows, seek priority, dedup, fallback, stale work | Scheduler/segmentation state-machine tests |
| `subtitleBoundaryProtocol.test.js` and metrics tests | A | Coverage and malformed-output contract | Boundary/metrics tests |
| `BilingualSubtitleManager.test.js` | C | Lifecycle scenario inventory; DOM assertions are not portable | Controller/cue bridge tests |
| `YouTubeCaptionProvider.test.js` | C | Video/track generation and cancellation scenarios | Source adapter/session tests |
| `testdata/subtitle-samples/*.json` | C | Identify required sample classes and edge cases | Independently generated minimal JSON/TTML fixtures with provenance notes |
| browser/page/API-wide tests | D | Outside scope | No target |

## SmartTube-native leverage map

| Need | Existing host capability | Decision |
|---|---|---|
| Caption discovery | `MediaItemFormatInfo.getSubtitles()` / `MediaSubtitle` | Reuse through source adapter |
| YouTube translated tracks | `getMergeCaptionTracks()` with `tlang` | Preserve; AI is additional opt-in behavior |
| Subtitle selection | `ExoPlayerController.getSubtitleFormats/getSubtitleFormat/selectFormat` | Observe current selection; do not replace |
| Cue decode/timing | ExoPlayer + `SubtitleManager.onCues` | Reuse and decorate output |
| Lifecycle | `PlayerEngineEventListener`, `PlaybackPresenter`, `onTickle` | Add one controller registration |
| Seek completion | `onSeekProcessed -> onSeekEnd` | Reprioritize and cancel obsolete work |
| Metadata | `Video` synchronized from format info | Use bounded title/description context |
| Settings UI | `SubtitleSettingsPresenter` + AppDialog | Add one AI settings entry; new feature presenters own details |
| Persistence | `SharedPreferencesBase`, `AppPrefs` patterns | New versioned `AiSubtitleData` |
| HTTP | shared `OkHttpManager` / OkHttp 3.12.13 | Reuse; no new HTTP stack |
| Async | RxJava 2 and controller disposal patterns | Reuse where it fits lifecycle; domain stays callback/future-neutral |
| Tests | JUnit + Robolectric already configured | Add target suites; CI is authoritative |

## Explicit non-migrations

DOM injection, userscripts, browser storage, page/PDF/EPUB translation, dictionary/hover lookup, word lists, exports/downloads, WebDAV/Gist sync, generic translation history, and unrelated KissTranslator provider breadth are permanently outside this feature's scope unless the product charter is explicitly changed.
