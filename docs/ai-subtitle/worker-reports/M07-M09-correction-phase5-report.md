# M07–M09 Correction — Phase 5 Report (Tasks 7–8, final phase)

Task IDs: `M07`/`M08`/`M09` correction plan, tasks 7 and 8 (fifth of five commit units) — the
minimal Prompt and fixture corrections, then the report corrections, candidate verification and
handover.

Plan: `review-plans/M07-M09-code-correction-plan.md`, SHA-256
`a13a5687ce01ffe7b06504376e13a097e6b6e5776926fa02dd6813684fb130a7`.

Base for this phase: `0fef49ade` (phase 4: task 6).
Branch: `feature/ai-bilingual-subtitles`.

## Task 7 — Prompt and fixture

Production, exactly the file the plan names: `prompt/BuiltInSubtitlePrompts.java`. The migration
mechanism was already correct and was not touched; no production patch was manufactured for it.

- **The indexed built-in keeps its id and its name** so an existing selection survives, and its
  text becomes: translate subtitle unit `{{unit_index}}` from the source to the target language,
  reply with the translation alone **without** the unit index. `unit_index` is now an input
  locator. Its version moved 1 → 2 so an installed app migrates, and the class javadoc says
  plainly that this is not the boundary protocol — no indexed response is parsed or assembled
  anywhere — so the prompt cannot be read as implementing one.
- **The audit's already-passing migration probe is now a permanent regression**,
  `anExistingBaselineMigratesOnceAndCustomPromptsSurvive`. Its "old" baseline text and version are
  fixed literals rather than values generated from the current `BuiltInSubtitlePrompts`: a
  migration test whose old value came from the code under test would pass whether or not the
  migration ran. It asserts the built-in is replaced, the custom profile and the user's selection
  survive, exactly one write happens, and a reload changes nothing.
- **Old and new cache identity are proven different** by
  `theMigratedBuiltInPromptsChangeTheCacheIdentity`, which builds a key from the indexed prompt's
  version-1 profile and one from its shipped version-2 profile and asserts they differ — the old
  translation cannot be served under the new instruction — and does the same for the baseline,
  because the migration replaces both.
- **The fixture gained the missing Japanese manual sample** and the coverage assertion is now over
  the six explicit `(language, captionKind)` pairs. Checking the language set and the caption-kind
  set separately is satisfied while a whole pair is missing, which is how Japanese manual captions
  were absent without the suite noticing.
- **The "long" event was 65 characters, under the splitter's 80-character threshold**, and the old
  assertion (`texts.size() > 1`, over a fixture with many cues) was true for unrelated reasons. It
  is now a 157-character independently written line, and
  `aLinePastTheThresholdIsSplitAtWordBoundaries` asserts on that line alone: two pieces, the first
  starting where the line starts, the last ending where it ends, no piece over the threshold, and
  the pieces joined back into the exact input.
- **The word-timing category was a label with no content.** The event has cue-level start/end only,
  and nothing in the fixture or the parser carries per-word times. It is renamed `label-001` with
  the category `synthetic-short-cue`, and `VttParserTest` now states what actually happens to
  inline word timings: the parser keeps them as cue text and the normalizer strips them with every
  other tag. Per-word timing is not implemented, and no fixture claims it any more.
- **The fixture resource is now checked.** `theFixtureResourceMatchesTheIndependentCaseBuilder`
  loads `independent-cases.json` and asserts it describes the same events as the builder. It
  catches a category renamed in one place and not the other; it does not catch a wrong time that
  both files agree on.
- Existing gap / noise / dedup / overlap / mapping assertions were kept and their claims narrowed
  to what they prove. The new tests revealed no error in `RuleSentenceBreaker`, so its long-sentence
  and no-space paths were not modified.

One thing the fixture work exposed and is worth recording: the normalizer clips a cue that starts
before the previous one ends, so an event listed out of timeline order is silently removed. The
first version of the new Japanese event was appended out of order and vanished; the fixture now
says events are listed in timeline order.

## Task 8 — Corrections, verification and handover

### The correction table

Every row is corrected in place, with the historical SHA and test numbers kept as the record. No
number was replaced rather than corrected.

| Row | Where it now lives |
|---|---|
| M07 R1 "segmentation reload and pause config were correct" | `M07-report.md` correction §1: the old evidence proved a *new* adapter received the limits, not the existing one, and a config change resumed playback. Both were reproduced red and fixed in phase 1. |
| M07 D4–D6 checkboxes | `M07-plan.md` D4/D5/D6 split into the implemented whole-unit half (checked) and the unimplemented partial-batch half (unchecked, with the reason). |
| M07 R5 "only `BoundaryProtocolTest` references them" | `M07-report.md` correction §2: `SentenceAndChunkerTest` and `AsrTimingEstimatorTest` are real references, `DeterministicSegmentationFallback` has none, and `encodeItem` having no caller is not a test defect. |
| M07 R5-3 indexed prompt limitation | `M07-report.md` correction §3: closed by task 7. |
| M08 E context switch / phone save | `M08-report.md` correction §1, with the bridge-to-request evidence and the phase 1 PARTIAL. |
| M08 B "classifies by the request's own budget" | `M08-report.md` §3 and the annotated bullet: the elapsed-time rule was the defect. |
| M08 C final accepts only stop/DONE | `M08-report.md` correction §2: the code excluded only `length`/`content_filter`; the whitelist is now real. |
| M08 C "the request returns one text block" | Corrected in phase 3 and re-stated in the M08 correction §2. |
| M08 D "dropping an intermediate repaint is lossless" | Corrected in phase 4. |
| M09 B work map bound / 2 MiB | `M09-report.md` correction §B: the full ceiling, the no-timeline branch, re-translation after eviction, and 2 MiB as payload rather than a memory claim. |
| M09 A fixtures | `M09-report.md` correction §A and the two annotated matrix rows. |
| M09 C credential storage | `M09-report.md` correction §C: AES-256-GCM on API 23+, app-private plaintext on API 17–22 — the description was wrong, the approved strategy was not redesigned. |
| M09 D upstream tallies | `upstream-patches.md` and `M09-report.md` correction §D: 184/+29 324/-1 at `10d6a18cf`, 190/+33 012/-1 at `0fef49ade`. |
| .gitignore counted as an upstream patch | `upstream-patches.md` lists it with the other non-product files. |
| M09 F per-file inventory | `M09-report.md` correction §F plus an appendix with the name-status list for all three milestone ranges, generated from the commits. |

### Host-patch surface, re-verified file by file

Eight files: `PlayerUIController +43`, `PlaybackPresenter +2`, `SubtitleSettingsPresenter +6`,
`SubtitleManager +35/-1`, `VideoPlayerGlue +4`, `ids.xml +1`, `common/build.gradle +1` (the zxing
dependency and nothing else), `.gitignore +1`. 93 insertions, 1 deletion. `git diff --summary`
lists only `create mode` entries: no rename, no mode change, no deletion, no whole-file
reformatting. Submodule pointers are unchanged and match upstream; no path under
`exoplayer-amzn-2.10.6/` appears in the diff.

### Verification

Fresh ASCII copy `C:\tmp\smartube-code-fix`. Results directory deleted before each lane, each
lane's XML archived before the next overwrites it.

| Lane | Command | suites | tests | failures | errors | skipped | result |
|---|---|---:|---:|---:|---:|---:|---|
| JDK 17 full | `:common:testStbetaDebugUnitTest` | 51 | 524 | 0 | 0 | 21 | `BUILD SUCCESSFUL in 1m 11s` |
| JDK 11 settings | `:common:testStbetaDebugUnitTest --tests '….ai.subtitle.settings.*'` | 11 | 79 | 0 | 0 | 0 | `BUILD SUCCESSFUL in 1m 25s` |
| common lint | `:common:lintStbetaRelease` | — | 388 findings | 0 errors | — | — | `BUILD SUCCESSFUL in 1m 26s` |

The task-7 packages alone (prompt, source, segmentation, translation, provider) are 24 suites /
189 tests / 0 failures. All 388 lint findings are warnings and **none of them is in
`ai/subtitle`**. Phase 4 was 518/21 and 79/0; the JDK 17 total rises by exactly the six tests this
phase adds, and the skip count is unchanged at 21 for the same three suites. Raw XML and reports
at `C:\tmp\ev-phase5-jdk17`, `C:\tmp\ev-phase5-jdk11`, `C:\tmp\ev-phase5-lint.xml`.

### The completion gate

Tasks 1–7's required behaviours pass on this tree. The local candidate is reproducible from the
commits listed in `progress.md`'s completion note. No P1/P2 runtime defect is known to remain; the
items that are open are listed there too, none of which is a running-code defect. The reports
carry no "fully verified", "all devices supported" or "ready to release" claim: CI is written as
awaiting an external run, the device matrix as the user's responsibility, and neither is presented
as release readiness.

## Review gates

Two subagents reviewed the phase diff on the Standards and Spec axes; neither ran tests or built
anything, and no review verdict is quoted as a test result. No finding was Blocking.

The Spec gate had to be dispatched twice: the first attempt stalled with no progress for ten
minutes and was reported failed by the harness, so it produced no findings. The second attempt
carries the same instructions with a tighter scope. Recorded because a gate that failed is not a
gate that passed.

### Standards axis

Fixed:

- *The final review roll-up claimed gate runs that had not happened.* It said ten gates had run,
  including "the phase-5 tree", while this phase's gates were still open — the exact kind of
  claim this run exists to keep honest. The roll-up now says nine completed and points here for
  the tenth pair.
- *"The dispositions are in full in each phase report" was not true for phase 1.* That report had
  no review section at all; its dispositions lived only in the ledger. The section has been added
  to `M07-M09-correction-phase1-report.md`, including the three findings that were recorded rather
  than fixed and the reason for each.
- *The roll-up's per-phase finding counts could not be re-derived* from the reports, because the
  counting convention was never stated. The table is replaced by a pointer table: which phase's
  dispositions live in which file. A number a reader cannot check is worse than no number.
- *The M07 R5-2 correction re-committed the error it was correcting, in miniature.* It said "only
  `BoundaryProtocolParser`, `BoundaryValidator` and `BoundaryProtocol` are referenced by
  `BoundaryProtocolTest` alone"; `AiSegmentationCoordinator` and `SegmentationMetrics` are also
  referenced only there. All five are now listed, and `DeterministicSegmentationFallback` is still
  called out as having no test reference at all.
- *`theCorrectedBuiltInPromptChangesTheCacheIdentity` named a prompt its body never used.* Both
  keys were built from the baseline profile, so the prompt task 7 actually corrected — the indexed
  one — was never the subject, and the plan's "the old indexed entry cannot be hit" property was
  untested. Renamed to `theMigratedBuiltInPromptsChangeTheCacheIdentity` and it now builds the
  before/after keys from the indexed prompt's old and shipped versions, and from the baseline's,
  because the migration replaces both.

Recorded, not changed:

- *The reviewed tree moved while the gate was reading it.* Three revisions were observed while
  the ledger, this report and the review roll-up were still being written. The gate verified both
  versions of the M09 appendix byte-for-byte against git, so nothing was wrong, but an uncommitted
  tree cannot be pinned to a SHA and anything added after a gate opens is not covered by it. The
  process fix is the one the run has used everywhere else: write the tree, then dispatch the gate,
  and record the reviewed fingerprint — which this run should have done here too. For the record,
  the tree whose findings are dispositioned above, after every fix in this section, has
  `git diff 0fef49ade | git hash-object --stdin` =
  `8c9041978a5a0a3ac2064b8a88a188cadf1b3a92`.

The axis also confirmed, by measurement rather than by reading: every number in the corrected
documents reproduces (both branch-diff tallies, the host-file deltas, the submodule and ExoPlayer
claims, the test arithmetic from 460 → 475 → 486 → 506 → 518 → 524 and the skip count of 21, the
157-character line against the 65-character one and the 80-character threshold); no historical
record was rewritten rather than annotated, and every old SHA and test number survives; no
correction over-corrects; the fixture resource cannot drift from the builder on any of the seven
fields, order or count; and the new tests have no assertion that cannot fail and no dead helper.

### Spec axis

Every task-7 bullet was found implemented, and it verified each one against the code rather than
the prose: the fixed old literals and the cache-identity inequality; the indexed prompt's id, text
and version with its "not the boundary protocol" javadoc; the Japanese manual sample sitting
adjacent to the previous event without overlapping and the six explicit pairs; the long-line
assertion over that line alone with the old `texts.size() > 1` conclusion gone; the demoted
label with `VttParserTest`'s explicit inline-word-timing input; and `RuleSentenceBreaker` absent
from the changed-file set.

Fixed:

- *One correction-table row was left uncorrected.* Task 8's table names "M09 error normalisation
  PASS", and the M09 matrix row still read `PASS` with no note, while the evidence for the defect
  lived only in the M08 report. The row is annotated and the M09 correction section has an A2
  entry: the scheduler's handling of a known category held, but a deadline before the response
  headers was reported as CANCELLED and a streamed 429 or 5xx as PROTOCOL.
- *This report named the cache-identity test by its pre-review name.* Corrected to
  `theMigratedBuiltInPromptsChangeTheCacheIdentity`.
- *The completion note lists this phase's commit without a SHA*, because it is written before the
  commit exists. The plan asks the note to carry the repair SHAs, so the phase-5 commit `31aa5a4d6`
  is recorded in the ledger by the follow-up commit that closes the run.

Recorded:

- Task 8's remaining bullets were confirmed rather than changed: old numbers preserved in all
  three reports, each with a correction section; the host-patch facts stated with the numbers the
  plan requires; both lanes and lint recorded with separate XML paths and the skips spelled out;
  and a completion note that carries the changed files, the per-lane figures, the closed defect
  IDs and the open items, with no release-readiness claim anywhere.

## Unchanged / not attempted

No push, tag, release, artifact upload or paid API call. No changes to KissTranslator,
SharedModules, MediaServiceCore, ExoPlayer or Gradle dependency versions. No upstream host files
were touched. Device acceptance remains with the user (`PENDING DEVICE`).
