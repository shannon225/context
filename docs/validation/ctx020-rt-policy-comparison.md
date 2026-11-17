# CTX-020: seed23 RT-policy comparison

Approved by Ariana on 2026-11-16 as an experiment. This supporting record does not replace the frozen production plan in `docs/uu-ctx-020-forced-assay-xic.md` (r5.2). Historical experiment status: production adoption was pending at execution. Ariana subsequently adopted full-gradient scope on 2026-11-16; current authority is canonical r5.3. Production parity validation is recorded below. Task: CTX020-017.

Compare the current scheduled-window scoring with full-gradient scoring for both true targets and entrapments. Forced scoring remains enabled in both. Use the same source DIA, complete source-library identity resolver, prepared working library, target/decoy strategy, scorer, diagnostic settings and confidence workflows. The full-gradient condition changes only RT center and width in a derived assay copy; it does not change Java scoring or public CLI behavior.

The copied full-gradient assay has576rows, all spanning5–37minutes, covering actual MS2 times344.869476–2159.574463seconds and MS1 times345.297821–2160.163086seconds. Outward whole-minute rounding prevents float truncation from excluding boundary scans. The original assay is retained for per-result original-window membership and provenance. Original source files and prior results are preserved. All output locations are fresh; predictions/shared working library are reused with existing checks.

- Scheduled features: `tmp/ctx020-validation/integrated-seed23`.
- Scheduled confidence results: `tmp/ctx020-validation/percolator-comparison/after`.
- Full-gradient features: `tmp/ctx020-validation/full-gradient-seed23`.
- Full-gradient confidence results: `tmp/ctx020-validation/full-gradient-seed23-confidence`.
- Comparison outputs: `tmp/ctx020-validation/rt-policy-comparison-final`.
- Existing Docker image: `context-ctx020-round1b`, sha256:d7c436c49c980ca56e78b775bd6e0bcf95ecfd4c187b8c9763ae014ef45688c3.
- Confidence defaults: acquisition seed23; training seed1; Percolator3.01,0.01training/test FDR; Context q2pep. Run existing public APIs for Context + Percolator, standard and targeted Percolator.

Validation checks: identical copied DIA SHA-256; exactly two modified assay fields; all576detection rows accounted for or explicit failure; no assay identities in background; feature/sidecar ID parity; retained apex lies inside effective bounds; full-gradient hits labeled against ORIGINAL schedule. Compare native peptide output, PEP-only (<.01), joint PEP<.01 and q<.01 (supplied R Figure3), and q≤.01 counts. PR ranks native peptide scores, enters score ties together, and uses T/96recall and T/(T+E)precision. Calibration uses supplied R ten squared-edge bins and lower/upper FDP formulas with per-bin counts and invalid-value exclusions. No confidence threshold changes or NaN imputation.

Interpretation: this single-seed experiment compares window policies, not just forcing. Broadening search opportunity can change winners/scores and confidence models. Entrapment sequences may have genuine signal outside intended trap windows; a full-gradient entrapment match is not automatically an incorrect sequence identification. Apply the requested entrapment formulas descriptively; no assumption that they validate this altered experimental design. Preserve results even if they do not support the proposed policy change.

Results and decisions are logged under CTX020-017 in root TASKS.md and the Google implementation record.

## Subsequent approved decision — 2026-11-16

Ariana selected full-gradient search/scoring for both targets and entrapments. Retain forced fallback and preserve original assay schedules as metadata/QC. This does not change the recorded experiment or prove FDP calibration. See canonical r5.3 and TASKS.md for implementation work. Scheduled results remain archived in `2026-11-16-CTX020-seed23-RT-policy-comparison.zip` in the ctx-020 Drive folder: `native-peptide-outputs/before/` and `provenance/scheduled/`. Archive: https://drive.google.com/file/d/10ajpKSSdtcW3cEQiLH1k-4-38Hoq7l05/view .

## Production parity check — 2026-11-16

The r5.3 production scorer reran seed23 with the original unchanged assay and DIA. All576 detection statuses/reasons match this experiment; original assay metadata is preserved. Raw8122, reference546 and background7576 feature files are byte-identical to the experimental full-gradient files. Audit and runtime class hashes: `tmp/ctx020-validation/production-full-gradient-seed23/`. Confidence engines were not rerun on these identical feature inputs. This verifies the production transition, not general FDP validity.
