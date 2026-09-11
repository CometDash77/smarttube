# AI Subtitle Context

AI Subtitle is a bounded feature inside SmartTube that turns an existing SmartTube caption track into a bilingual playback experience. SmartTube remains the owner of video playback and source captions; the AI Subtitle feature owns segmentation, translation orchestration, and translated-caption state.

## Language

**Source Track**:
The SmartTube caption track selected by the viewer and used as the authoritative text and timeline input.
_Avoid_: Original subtitle, raw subtitle

**Source Cue**:
A time-bounded piece of text decoded from the Source Track before AI-specific normalization or segmentation.
_Avoid_: Translation line, DOM caption

**Subtitle Segment**:
A normalized, time-bounded unit of source text that can be rendered and grouped for translation.
_Avoid_: Sentence when the boundary is not linguistic

**Translation Unit**:
One or more contiguous Subtitle Segments translated in a single logical request and mapped back without losing source coverage.
_Avoid_: Chunk when referring to the persisted/cache identity

**Lookahead Window**:
The playback interval from the current position into the near future whose untranslated Translation Units are eligible for scheduling.
_Avoid_: Preload when it does not mean translation work

**Translation Session**:
The isolated lifetime of AI subtitle work for one video, Source Track, Provider Profile, Model, Prompt, and Target Language combination.
_Avoid_: API session, player session

**Session Generation**:
A monotonically changing identity used to reject results belonging to an obsolete Translation Session.
_Avoid_: Version when referring to persisted schema versions

**Provider Profile**:
A user-saved endpoint configuration containing provider type, protocol, base URL, credentials, model choices, and provider-specific options.
_Avoid_: Provider when referring to a saved account/configuration

**Protocol Adapter**:
A transport boundary that serializes requests, parses normal and streaming responses, discovers models, and normalizes provider errors for one wire protocol.
_Avoid_: Provider-specific client when the protocol is shared

**Prompt Profile**:
A user-managed, versioned translation instruction template independent of Provider Profile and Model.
_Avoid_: System prompt when referring to the whole saved template

**Translation Profile**:
The resolved runtime selection of Provider Profile, Model, Prompt Profile, and Target Language.
_Avoid_: Provider settings

**Boundary Protocol**:
The indexed response contract that maps model-produced segmentation or translation output back to a continuous range of source events.
_Avoid_: Timestamp guessing

**Source-Only Fallback**:
The safe rendering state in which SmartTube continues showing the Source Track while AI translation is unavailable, late, invalid, or disabled.
_Avoid_: Failure screen
