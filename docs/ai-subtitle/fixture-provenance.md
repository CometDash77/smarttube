# AI subtitle fixture provenance

All M06 fixtures are synthetic, minimal, and authored for this SmartTube feature. They are
identified by behavior categories rather than copied subtitle strings, arrays, or timings from
KissTranslator or any other repository. The target suite covers manual and ASR-shaped events,
short labelled cues, no-space text, noise, fast/slow intervals, long text, overlaps, gaps, and
duplicate events. `SubtitleFixture` is the source of the current JVM fixtures; future additions
must retain the same independent-authorship rule and add a short category note here.

`common/src/test/resources/ai-subtitle/fixtures/independent-cases.json` mirrors
`SubtitleFixture.independentCases()`. `SubtitleFixturePipelineTest` loads the resource and
asserts it describes the same events, so the two cannot drift apart.

## Corrections from the M07–M09 correction run (task 7)

- **Every language must appear in both caption kinds.** The suite previously checked the
  language set and the caption-kind set separately, which passes while a whole combination is
  missing. Japanese manual captions were missing; `ja-manual-001` supplies one, and the
  assertion is now over the six explicit pairs.
- **The "long" event was not long.** `long-001` was 65 characters, under the splitter's
  80-character threshold, so it could not exercise long-line splitting at all, and the old
  assertion (`texts.size() > 1` over the whole fixture) was true because the fixture has many
  cues. The event is now 157 characters, and splitting is asserted on that line alone — piece
  count, coverage times, and the pieces joined back into the exact input.
- **The word-timing category was a label without content.** `word-001` carried the provenance
  id `synthetic-word-timing` but the event has cue-level start/end only, and no per-word times
  exist anywhere in the fixture or the parser. It is renamed `label-001` with the category
  `synthetic-short-cue`, and `VttParserTest` now states what the pipeline actually does with
  inline word timestamps: the parser keeps them as cue text and the normalizer strips them with
  every other tag, so words arrive without per-word timing. Per-word timing is not implemented,
  and no fixture claims it.
