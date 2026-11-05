# CTX-016 Report — Addendum, 2026-11-01

- **Feature:** CTX-016 — EncyclopediaTwo matched decoys and competition.
- **Status:** Accepted. Ariana closed the implementation on 2026-11-01.
- **Adds to:** `/Users/arianashannon/Downloads/2026-10-19-CTX-016-Report.md` on Ariana's machine. That Report was last appended on 2026-10-19 (`CTX016-057`) and is not modified here. Ariana asked that this addendum go in `docs/`, because the Report location is not reachable from the cloud session.
- **Google implementation record:** https://docs.google.com/document/d/1u04aFpKACUNP8Qtfl463GUPGoW5WW3zGEcpELGkKPH4/edit
  - Closing note appended 2026-11-01 under the heading "CTX-016 Closure — implementation closed by Ariana (2026-11-01)".
  - Docs revisionId after the append: `AHj4eMT0JL3lRWYIELrB6Q3SEgan0gIgWnOSjJdu3CIELgcHMwFMVGIr7nk9i2oFL6DHowp1NLkCFHRFkglGumDydtKcV6gvDPbLOgMuC8E`.
- **Canonical plan:** `docs/uu-ctx-016-encyclopediatwo-matched-decoys.md`, revision 2 (unchanged).
- **Full feature record:** `docs/TASKS-archive.md`. The CTX-016 checklist and dated log were moved there verbatim from the root `TASKS.md` on 2026-11-01, at Ariana's request.

## What changed since the 2026-10-19 Report

1. The CTX-016 sections of `TASKS.md` were moved verbatim to `docs/TASKS-archive.md`. `TASKS.md` keeps a short pointer.
2. Ariana's independent global-file entrapment review was recorded, and the remaining items were checked. They are the combined acceptance item, `CTX016-009`, `CTX016-010`, `CTX016-040` and `CTX016-053`, shown here in their final state as they appear in the archive:

- [x] Documentation, Java 17 tests/package checks, representative scientific comparisons, diff review, and final human review are recorded with their actual limits.
- [x] `CTX016-009` Run focused JUnit 4, upstream compatibility, full Context Java 17 package checks, and representative scientific comparisons. Files: tests/fixtures/log. Check: commands, seeds, data, numeric tolerances, outcomes, skips, and limits recorded.
- [x] `CTX016-010` Review the complete diff for minimal scope, dependency justification, scientific behavior, and artifacts; perform applicable Until Useful review. Files: guide and log. Check: findings, corrections, and residual risks recorded.
- [x] `CTX016-040` Ariana's final review and acceptance. Dependency: 039. Check: remain `Awaiting Ariana's final review` until she accepts the feature; Git delivery remains separate.
- [x] `CTX016-053` Ariana's final review and acceptance of A4. Dependency: 052. Check: remain `Awaiting Ariana's final review` until she accepts it; Git delivery is separate.

3. The closing note was appended to the Google implementation record (revision above).

## Closing log entry (verbatim from `docs/TASKS-archive.md`)

#### 2026-11-01 — `CTX016-009`, `CTX016-010`, `CTX016-040`, `CTX016-053` closed; CTX-016 implementation closed by Ariana

- **Decision:** Ariana closed the CTX-016 implementation on 2026-11-01 and asked that the remaining items be checked off. Checked: `CTX016-009`, `CTX016-010`, `CTX016-040`, `CTX016-053` and the combined validation/review acceptance item. The checkbox lines are otherwise unchanged; earlier statements that these items were open are kept as history.
- **Evidence, as reported by Ariana (her independent review; not rerun in this session):**
  - She processed one global DIA file with the new and legacy Context decoy-generation/scoring versions in a DIA-style entrapment experiment, then plotted entrapment FDP against Percolator q-value (0–5%) for both.
  - Estimators on her figure:
    - Single: FDP = N_E / N_T.
    - Combined upper bound: 2·N_E / (N_T + N_E).
    - Lower bound: N_E / (N_T + N_E).
  - Values read from her figure (approximate, at q = 5%):
    - Legacy: single FDP about 5.2%, slightly above the y = x line over most of the range; combined upper bound about 9.9%, well above it; lower bound about 5%, near it.
    - New: single FDP about 2.2%; combined upper bound about 4.2%, at or below the line over most of the range; lower bound about 2.2%.
  - Ariana's conclusion: with either the single or the combined calculation, the new version's entrapment FDP is lower than legacy's. She considers the implementation working and the science validated.
- **Limits recorded:**
  - One global file. The Percolator q-value level (PSM or peptide), the file's identity and the entrapment ratio were not given here, so they are not recorded.
  - The new version's estimates sit below the y = x line, which can mean conservative q-values. That is a possible sensitivity question, not an FDR-control failure.
  - The targeted-assay assessment with `TargetedBootstrapper` is Ariana's separate experiment, outside the CTX-016 implementation.
- **Not done here:**
  - The external Report (`/Users/arianashannon/Downloads/2026-10-19-CTX-016-Report.md`) is on Ariana's machine and was not updated from this cloud session; this entry needs appending there.
  - The Google implementation record's completion summary was not edited.
  - No commit.

## Status of the items the log lists as "Not done here"

- External Report: this addendum takes its place, at Ariana's direction. The original Report on her machine is unchanged.
- Google implementation record: the closing note has since been appended (revision above).
- Commit: not part of this addendum. Git delivery is recorded in the Google record, as `CLAUDE.md` requires.

## Follow-up log entry (verbatim from `docs/TASKS-archive.md`)

#### 2026-11-01 — CTX-016 Google closing note and Report addendum

- **Google implementation record:** Ariana approved adding the closing note. It was appended under the heading "CTX-016 Closure — implementation closed by Ariana (2026-11-01)", after the A4 section, and nothing else in the record changed. Docs revisionId after the append: `AHj4eMT0JL3lRWYIELrB6Q3SEgan0gIgWnOSjJdu3CIELgcHMwFMVGIr7nk9i2oFL6DHowp1NLkCFHRFkglGumDydtKcV6gvDPbLOgMuC8E`. Checked by reading the record again: one HEADING_1 and four NORMAL_TEXT paragraphs, with no added text styles.
- **Report:** at Ariana's direction, the addendum is `docs/2026-11-01-CTX-016-Report-addendum.md`, in the repo instead of beside the external Report, which this cloud session cannot reach. It holds the final state of the checked items, the closing log entry verbatim, and this entry. The external `/Users/arianashannon/Downloads/2026-10-19-CTX-016-Report.md` is unchanged.
- The header and status table at the top of the Google record still say the implementation is in progress. They were not edited, because the request was to add the closing note.
