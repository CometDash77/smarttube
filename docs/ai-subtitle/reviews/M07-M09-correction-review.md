# M07–M09 Correction — Final Review Roll-Up

Plan: `review-plans/M07-M09-code-correction-plan.md`, SHA-256
`a13a5687ce01ffe7b06504376e13a097e6b6e5776926fa02dd6813684fb130a7`.

This is the run's review record, as the execution contract requires: both axes, per phase, with
every finding's disposition, plus the Minor roll-up and its triage. It is a review document, not
a test result. No review verdict in it is quoted as evidence that anything passes, and none of
the reviewers ran a build.

## How the gates were run

Two subagents per phase, one on the **Standards** axis (does the code read like it belongs here,
and is it honest) and one on the **Spec** axis (does it do what the plan's task requires, in the
files the plan names). Ten gate runs in total, on the phase diffs of `fccf8d9ca`, `fbeda06e8`,
`551b10a4c`, `0fef49ade` and the phase-5 tree. Neither axis ran tests, Gradle or any build; the
instructions said so and each report records it. One run — the first phase-5 Spec attempt —
stalled and was reported failed by the harness, so it produced no findings and was dispatched
again; a gate that failed is not a gate that passed, and both the phase-5 report and the ledger
say so.

Every finding is either **fixed** in the tree that was re-verified, or **recorded** with the
reason it was not. Nothing was closed by re-describing it. The dispositions are in full in the
phase reports — except for phase 1, whose dispositions were carried in the ledger at the time and
were added to its own report afterwards, which the phase-5 Standards gate flagged.

## Where each phase's dispositions live

| Phase | Tasks | Dispositions |
|---|---|---|
| 1 | 1–2 | `worker-reports/M07-M09-correction-phase1-report.md` §Review gates (added after the phase-5 gate flagged that it had none) |
| 2 | 3 | `worker-reports/M07-M09-correction-phase2-report.md` §Review gates |
| 3 | 4–5 | `worker-reports/M07-M09-correction-phase3-report.md` §Review gates |
| 4 | 6 | `worker-reports/M07-M09-correction-phase4-report.md` §Review gates |
| 5 | 7–8 | `worker-reports/M07-M09-correction-phase5-report.md` §Review gates |

No review gate produced a Blocking finding.

A note on pinning: the phase-5 Standards gate observed that its tree changed while it was reading
it — the ledger, this roll-up and the phase-5 report were being written as the gate ran. It
verified both versions of the M09 appendix against git, so nothing was wrong, but a review of an
uncommitted tree cannot be pinned to a SHA. The process fix is recorded in the phase-5 report:
freeze the tree or commit before dispatching a gate, and record the reviewed fingerprint.

## The findings that changed behaviour rather than wording

Most findings were documentation precision — this run has a long history of javadoc drifting from
behaviour, and each phase fixed several. These four changed what the code does:

1. **Phase 2 — the scheduler's bound was wrong in two places.** A `SUCCEEDED` record whose result
   had been evicted kept answering "already translated" from an empty cache, and the displayed-cue
   path (no timeline) was pruned by nothing. Both were the phase's own subject, so this is the
   gate confirming rather than extending the fix — but the reviewers were the ones who traced the
   interaction between `mLastDraftRefreshMs`-style state and the prune.
2. **Phase 3 — the non-SSE transport branch did not honour `close()`'s promise.** The interface
   javadoc said a closed call delivers no further callback; that branch called `onFailure`
   without checking. The guard was added so the method is true as written rather than relying on
   both adapters to drop the extra callback.
3. **Phase 3 — the draft-bound test could not fail if the fix were absent.** The test asserted the
   same outcome under "check then append" and "append then check", so its name claimed a property
   it did not guard. Renamed to what it can observe, with the reason it cannot separate the two
   orders recorded rather than a test-only production field added to make the claim true.
4. **Phase 4 — three more settings transitions cleared the cue without repainting.** Both axes
   found this independently, and it is the run's clearest example of a review catching an
   incomplete fix: the plan's bullet says "stream off, AI off **and the other transitions that
   clear the on-screen draft**", and the context toggle, a segmentation change and a
   provider/profile change all clear the cached translation while leaving the old text on screen.
   All three now notify.

5. **Phase 5 — a test named a prompt its body never used.** `theCorrectedBuiltInPromptChangesTheCacheIdentity`
   built both of its cache keys from the *baseline* profile, so the prompt the phase actually
   corrected — the indexed one — was never the subject, and the plan's "the old indexed entry
   cannot be hit" property was untested. Renamed and rebuilt from the indexed prompt's old and
   shipped versions. The same gate also caught that phase 5 had left one row of the plan's
   correction table untouched, and that the roll-up you are reading claimed gate runs that had
   not happened when it was written.

One finding corrected a **false statement in a phase report**: phase 4's first version claimed
the other tests could not compile against the pre-fix tree. They could, and a second red run
replaced the guess with a measurement (five red, six green, one not runnable pre-fix).

## Minor roll-up and triage

Collected across all gates. "Open" means the run deliberately left it; each names its reason.

| # | Minor | Triage |
|---|---|---|
| 1 | The attempt budget is not absolutely unbreakable across a seek: the plan's own out-of-window prune drops a record whose budget was spent, so seeking back re-creates it with a fresh budget | **Open, by design.** The two bullets genuinely conflict; resolved in favour of the bound the same plan demands. Restart condition in the phase 2 report. |
| 2 | `mRenderedUnit` can name a pre-seek unit in the narrow window where SOURCE mode returns before `findOrRequest` | **Open.** Transient, closes on the next `process`; clearing it on a mode change would be more code than the status is worth. |
| 3 | The coalescing tests control the repaint *interval* rather than a literal clock value | **Open, stated.** One test supplies its own clock value directly, so the plan's requirement is met two ways; the substitution is recorded in the phase 4 report. |
| 4 | Two streaming provider doubles share a package | **Open.** The codebase keeps one such double per suite already; extracting one would be churn against the surrounding convention. |
| 5 | `FailureReason.IO` from the non-SSE transport branch maps to NETWORK with a generic message, discarding the transport's more accurate text | **Open.** Retryable, which is what enables the plain fallback; outside the phase's diff. Noted for whoever owns the transport vocabulary next. |
| 6 | The M08 plan's retained "偏差" sentence reads as current state even though a correction follows it | **Fixed** in phase 4 by prefixing it as a historical record. |
| 7 | `DeterministicSegmentationFallback` has no test reference at all | **Open.** It has no production caller either. Recorded in the M07 report correction; it is a coverage gap, not a defect the correction run introduced. |

## What the reviews did not cover

- No reviewer ran a test, a build, lint or a device. Every "passes" claim in this run comes from
  the lanes recorded in the phase reports, parsed from XML.
- No reviewer checked the tree against a device or against the CI workflow.
- The phase 5 gate's findings and dispositions are in the phase 5 report, not repeated here.
