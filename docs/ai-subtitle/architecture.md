# SmartTube AI Bilingual Subtitle Architecture

Status: **Accepted — approved by user on 2026-09-11**

Reconnaissance date: 2026-09-11

Target: SmartTube `6e2e00bb8c989e089735c3f26fcd5511669f1597`

Reference: KissTranslator `226e5780f5ccee1cd42d4167518632318c2f7d7b`

## 1. Architectural outcome

Implement AI bilingual subtitles as an isolated feature package inside SmartTube's existing `common` Android library. SmartTube continues to own playback, source-track selection, caption decoding, settings presentation, and the existing `SubtitleView`. The new feature owns source normalization, segmentation, prompts, provider protocols, scheduling, cancellation, cache identity, and translated state.

The first implementation must prove the full player/renderer lifecycle with a deterministic `FakeTranslationProvider` before any real network provider is connected. This keeps renderer and lifecycle defects distinguishable from LLM defects.

No ExoPlayer/Media3 migration, renderer fork, dependency upgrade, or broad SmartTube refactor is part of this project.

## 2. Evidence baseline

### Repositories and build

- SmartTube is a multi-module Android/Android TV Gradle project. The main feature host is `common`; the app shell is `smarttubetv`; YouTube media extraction is in the pinned `MediaServiceCore` submodule; shared utilities and the HTTP client are in `SharedModules`.
- SmartTube uses Java/Kotlin targeting Java 8, Android Gradle Plugin 7.4.2, compile SDK 34, and retains Android 4.x compatibility in its main variants. It uses the in-repository `exoplayer-amzn-2.10.6`, RxJava 2, Retrofit 2.5, and forced OkHttp 3.12.13.
- The project already has JUnit and Robolectric dependencies in `common`, but no existing `common/src/test` subtitle suite was found.
- KissTranslator is a React 18 browser extension/userscript written in JavaScript, with Jest tests and subtitle fixtures. Browser lifecycle, DOM, CacheStorage, and extension APIs are not portable integration mechanisms.

### Current SmartTube caption path

1. `MediaItemFormatInfoImpl` exposes `MediaSubtitle` objects from `PlayerResult.getMergeCaptionTracks()`.
2. `PlayerResultExtensions.getMergeCaptionTracks()` combines original YouTube caption tracks with YouTube-provided translated tracks (`tlang`) and currently requests TTML.
3. `YouTubeMPDBuilder` emits each `MediaSubtitle` as a DASH subtitle adaptation set whose `BaseURL` is the timed-text URL.
4. ExoPlayer selects, downloads, and decodes that track.
5. `SubtitleManager`, an ExoPlayer `TextOutput`, receives `List<Cue>`, normalizes repeated ASR text, and sends it to the existing single `SubtitleView`.

This path is reusable. The AI feature must not replace YouTube extraction, MPD construction, track selection, or subtitle decoding.

### Current player lifecycle

`ExoPlayerController` already emits source change, video loaded, track selected/changed, play, pause, buffering, seek completion, playback end, and engine release events. `PlaybackPresenter` dispatches those events to an ordered set of `BasePlayerController` instances and supplies a periodic `onTickle` callback. These are the intended lifecycle seam for a new `AiSubtitleController`.

`VideoLoaderController` obtains `MediaItemFormatInfo`, but `Video.sync(formatInfo)` does not retain subtitle URLs. The selected ExoPlayer `Format` also does not expose the source timed-text URL in a stable domain API. Full-track lookahead therefore needs an explicit source-data strategy; the recommended strategy is in section 7.

## 3. Non-negotiable invariants

1. The Source Track and basic playback work without any Provider Profile, API key, network, or valid AI response.
2. Provider errors never block, stop, seek, or re-prepare the player.
3. The source line remains renderable while translation is missing or failed.
4. A result may mutate cache or UI only if its Session Generation still matches the active Translation Session.
5. A Translation Unit is scheduled at most once concurrently within a Translation Session.
6. Cache identity includes every input capable of changing translation output.
7. Prompt and provider protocol details never enter player or renderer classes.
8. Existing SmartTube files contain lifecycle/data hooks only; AI business logic lives in new feature files.
9. Streaming is optional. A complete non-streaming response remains the baseline contract.
10. No KissTranslator source is copied or mechanically translated while the target remains MIT-licensed without an explicit legal/compliance decision.

## 4. Target data flow

```text
SmartTube selected Source Track
        |
        v
SmartTubeSubtitleSourceAdapter
        |
        v
SourceCue -> normalization -> SubtitleSegment timeline
        |                         |
        |                         v
        |                 segmentation/chunking
        |                         |
player position ----------> TranslationScheduler
                                  |
                         Lookahead + priority
                                  |
                         TranslationProvider
                                  |
                    normalized result / stream
                                  |
                  validation + TranslationCache
                                  |
        Source Cue + translated segment lookup
                                  |
                    SmartTubeSubtitleOutputAdapter
                                  |
                    existing SubtitleView/Cue path
```

## 5. Placement and module boundaries

The recommended physical placement is new files below:

```text
common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/
    domain/
    integration/
    source/
    segmentation/
    translation/
    provider/
    prompt/
    scheduler/
    cache/
    session/
    settings/
```

This is package-level isolation inside `common`, not a new Gradle module. A new Gradle module would add settings/build wiring and make access to current player, preferences, resources, and MediaServiceCore APIs more complex without yet creating a useful deployable boundary. Revisit only if dependency cycles appear in M02/M03.

### Public seams

Keep the externally visible surface small:

- `AiSubtitleController`: the only playback controller registered with `PlaybackPresenter`.
- `AiSubtitleCueBridge`: a tiny renderer-facing lookup/decorator called by `SubtitleManager`.
- `TranslationProvider`: provider-neutral async translation contract.
- `SubtitleSource`: loads the selected full caption timeline for lookahead.
- `AiSubtitleData`: owns persisted non-secret AI settings and profiles.

Other types should be package-private/internal until a demonstrated variant requires a seam.

### Internal domain model

- `SourceTrackId`: video ID plus stable caption-track identity and language.
- `SourceCue`: decoded start/end/text before AI normalization.
- `SubtitleSegment`: normalized start/end/source text and source-event coverage.
- `TranslationUnit`: contiguous segment IDs, source text, context envelope, and boundary contract.
- `TranslationProfile`: resolved Provider Profile ID, model ID, Prompt Profile ID/version, and target language.
- `TranslationSessionId`: video + Source Track + Translation Profile + engine schema version.
- `TranslationResult`: final or partial translated text mapped to segment IDs.
- `TranslationFailure`: normalized category (`cancelled`, `timeout`, `rate_limited`, `auth`, `server`, `protocol`, `invalid_output`, `network`).

## 6. Provider architecture

The UI exposes five Provider Types, while transport code is shared by protocol:

```text
TranslationProvider
  -> ProviderProfileResolver
       -> OpenAiChatCompletionsAdapter
       |    OpenAI-Compatible
       |    OpenRouter preset
       |    DeepSeek preset
       |    MiMo OpenAI preset
       |
       -> AnthropicMessagesAdapter
            Anthropic-Compatible
            MiMo Anthropic preset (optional profile protocol choice)
```

### Protocol responsibilities

Each `ProtocolAdapter` owns authentication headers, request serialization, normal response parsing, SSE parsing, model discovery, timeout/cancellation propagation, request ID capture, and error normalization. It does not own subtitle scheduling, retry policy, prompt selection, or UI.

### Profile responsibilities

Each `ProviderProfile` owns name, Provider Type, protocol, base URL, API key reference, available model IDs, current/default model, optional headers, and narrowly typed provider-specific options. Presets supply defaults but users may override base URL and enter model IDs manually.

### Current official protocol findings

- OpenAI-compatible baseline: bearer authentication, `POST /v1/chat/completions` (or base URL plus `/chat/completions`), optional SSE, and `GET /v1/models`. OpenAI recommends Responses for new direct integrations, but Chat Completions is the interoperable common denominator required by this project.
- Anthropic-compatible baseline: `POST /v1/messages`, top-level `system`, explicit `max_tokens`, `anthropic-version`, bearer or `x-api-key`, named SSE events, and `GET /v1/models`.
- OpenRouter: OpenAI-compatible `POST https://openrouter.ai/api/v1/chat/completions`, bearer auth, `GET /api/v1/models`, optional attribution headers, and SSE.
- DeepSeek: officially supports OpenAI and Anthropic formats. The first implementation should use its OpenAI-compatible base `https://api.deepseek.com`; model discovery is `GET /models`; SSE uses data-only events ending in `[DONE]`.
- MiMo: officially supports both formats. Pay-as-you-go bases are `https://api.xiaomimimo.com/v1` and `https://api.xiaomimimo.com/anthropic`; `GET /v1/models` accepts `api-key` or bearer auth. Profile defaults must remain editable because token plans use a different base URL.

Implementation must verify server capabilities rather than infer them from Provider Type. Model lists can fail or be unsupported; manual Model ID remains a first-class path.

## 7. Source acquisition and renderer integration

### Recommended full-track source strategy

Use SmartTube's `MediaItemService.getFormatInfoObserve(videoId)` from the new controller/source adapter and select the matching `MediaSubtitle` based on the active SmartTube subtitle selection. Fetch and parse the selected timed-text URL in the AI feature. Prefer an already cached format-info result if the service provides one; measure duplicate-request behavior in M02.

Why this is preferred:

- It avoids modifying the `MediaServiceCore` submodule.
- It avoids expanding `PlayerEngineEventListener` and changing every implementation.
- It supplies the future timeline needed for lookahead; intercepting only displayed `Cue`s cannot.
- It keeps caption parsing in a replaceable adapter and leaves ExoPlayer's source path untouched.

Fallback if M02 proves track matching unreliable or format-info duplication material: add one narrow callback from `VideoLoaderController.processFormatInfo()` to `AiSubtitleController`. This is not approved as the default because it adds an upstream hook.

### Renderer strategy

Do not create a second overlay and do not fork `SubtitlePainter` initially. `SubtitleManager` should make one small call to `AiSubtitleCueBridge`, which can replace a source cue's text with a `SpannableStringBuilder` containing source and translation lines. The existing `SubtitleView` continues to own timing, placement, PiP behavior, and lifecycle.

M02 must test whether per-line styling survives the current `setApplyEmbeddedStyles(false)` behavior. If it does not, the preferred correction is a narrowly scoped AI-cue style path in `SubtitleManager`. Forking `SubtitlePainter` is a last resort requiring a new decision and an expanded upstream ledger entry.

## 8. Translation session and scheduling state machine

### Session states

```text
DISABLED -> LOADING_SOURCE -> READY -> ACTIVE <-> PAUSED
     ^              |          |          |
     |              v          v          v
     +---------- SOURCE_ONLY <- DEGRADED <-+

Any state -> CLOSED on video/engine release
Any identity change -> new generation -> LOADING_SOURCE
```

`DEGRADED` means translation is partly unavailable; it is not a player failure. `SOURCE_ONLY` remains renderable and recoverable.

### Identity changes that always create a new generation

- video change or player release/reopen;
- Source Track change or subtitles off/on;
- Provider Profile change;
- model change;
- Prompt Profile or prompt content/version change;
- target-language change;
- engine schema/cache-key version change.

Seek does not change the underlying session identity, but it creates a new scheduling epoch. Requests outside the new priority window should be cancelled when possible; every callback checks both Session Generation and request ownership before applying UI state.

### Scheduling policy

- On active playback and after seek completion, compute `[position, position + lookahead]`.
- Give highest priority to an untranslated unit containing the current position, then near-future units in chronological order.
- Keep the queue lazy. Do not translate the whole video in the background.
- Start with maximum concurrency 1 for context-preserving translation. Permit a configurable internal limit of 2 only after ordered-context tests establish correctness.
- Pause stops admission of new work; an in-flight request may finish and populate cache but must not force a render of a non-current cue.
- Seek cancels obsolete queued work, attempts to cancel obsolete in-flight work, and immediately schedules the new current/near-future window.
- Deduplicate by Translation Unit cache key and track `pending`, `in_flight`, `complete`, `failed_retryable`, and `failed_terminal` states.
- Retry only normalized transient failures with bounded exponential backoff and jitter. Current-position work may receive one immediate bounded recovery attempt; auth/protocol/invalid-output failures do not spin.
- Partial/malformed batch output accepts only a validated continuous prefix, retries the uncovered tail once, then falls back to smaller units or Source-Only Fallback.

## 9. Cache and context policy

### Cache key

At minimum:

```text
engine schema version
video ID
Source Track ID
normalized source coverage + text hash
Provider Profile ID
provider protocol
base URL identity (no credential)
model ID
Prompt Profile ID + prompt content hash/version
target language
context fingerprint
segmentation/boundary version
```

Never include or log the API key. M03/M07 begin with an in-memory session cache; M08 may add a bounded persistent cache after invalidation semantics are proven.

### Context envelope

Use bounded context in this order:

1. source/target language and current Translation Unit;
2. video title;
3. a capped video-description excerpt when available;
4. a bounded number/character budget of immediately preceding source and accepted translations;
5. optional cached video summary only in M08 if quality gains justify an extra request.

Context concurrency is serial because preceding accepted translations form an ordered dependency. Context changes are fingerprinted into cache identity. Never send an unlimited transcript.

## 10. Persistence and secrets

- Use a dedicated versioned `AiSubtitleData` store rather than expanding `PlayerData`'s serialized playback profile with many unrelated fields.
- Reuse SmartTube's `SharedPreferencesBase`/file-backed data behavior for profile and prompt JSON, migrations, defaults, and app-profile switching.
- Store API keys separately from exportable non-secret profile data. M04 must inspect Android backup/export behavior and prefer Android Keystore-backed encryption where supported, with a documented compatibility fallback for old Android versions.
- UI and logs display masked keys only. Exceptions and request diagnostics must scrub authorization headers, query secrets, prompt content when not needed, and subtitle content by default.

## 11. Testing architecture

- Pure JVM tests: normalization, sentence breaking, boundary protocol, metrics, chunking, cache keys, state transitions, prioritization, cancellation, retries, stale rejection, and protocol serialization/parsing.
- Fixture tests: independently authored/converted factual subtitle samples covering manual captions, ASR, no-space languages, coarse word timing, noise, fast/slow speech, long videos, malformed output, and stream interruption.
- Robolectric tests: preferences, settings presenters where practical, and cue decoration/styling.
- Device/integration checklist: play, pause/resume, rapid seek, subtitle switch, subtitles off/on, video close/open, background/foreground, PiP, and provider failures.
- Upstream regression suite: caption discovery, track selection identity, cue bridge, seek lifecycle, and persistence smoke tests after every upstream sync.

Tests derived from KissTranslator behavior must be independently expressed. KissTranslator GPL fixtures are not copied into the MIT tree unless a later legal decision explicitly permits it.

## 12. Official references consulted

- SmartTube repository: <https://github.com/yuliskov/SmartTube>
- SmartTube historical dual-subtitle PR: <https://github.com/yuliskov/SmartTube/pull/5839>
- KissTranslator repository: <https://github.com/fishjar/kiss-translator>
- OpenAI Chat Completions: <https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create>
- OpenAI Models: <https://developers.openai.com/api/reference/resources/models/methods/list>
- OpenAI API overview: <https://developers.openai.com/api/reference/overview>
- Anthropic Messages: <https://platform.claude.com/docs/en/api/messages/create>
- Anthropic Models: <https://platform.claude.com/docs/en/api/models/list>
- Anthropic API overview: <https://platform.claude.com/docs/en/api/overview>
- Anthropic streaming: <https://platform.claude.com/docs/en/build-with-claude/streaming>
- OpenRouter quickstart: <https://openrouter.ai/docs/quickstart>
- OpenRouter models: <https://openrouter.ai/docs/guides/overview/models>
- DeepSeek quickstart: <https://api-docs.deepseek.com/>
- DeepSeek Chat Completions: <https://api-docs.deepseek.com/api/create-chat-completion/>
- DeepSeek Models: <https://api-docs.deepseek.com/api/list-models/>
- MiMo first API call: <https://mimo.mi.com/docs/en-US/quick-start/summary/first-api-call>
- MiMo Models: <https://mimo.mi.com/docs/en-US/api/model/list-models>
