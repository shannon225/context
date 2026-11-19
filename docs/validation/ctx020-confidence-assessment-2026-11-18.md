# CTX-020 confidence assessment: PEP calibration and sensitivity across 9 seeds — 2026-11-18

- **Task:** CTX020-040/041/042, requested by Ariana on 2026-11-18.
- **Authority:** canonical r5.3. This is descriptive evidence for Ariana's final review, not an acceptance decision.

## Design

**Builds**
- **CTX-020** (`1cf1985`, r5.3 full-gradient search-then-force).
- **Baseline:** pre-CTX-020 (`7d0f524`) with only the approved CTX020-035 search-range fix (`5532fd3`) applied, uncommitted, in a temporary worktree.
- **Why the fix is applied:** the unmodified `7d0f524` scored about 72 features per seed (3 reference targets, 0 reference decoys), and Context-Percolator refuses that input. Ariana chose this comparator on 2026-11-18.

**Data**
- Seeds 1, 2, 22–28 from Ariana's ctx-020 Drive folder (`examples.zip`, `failed_ex.zip`), `astral_1mz_hela.elib`, `Human_UP000005640_9606.fasta`, and the supplied `prediction-cache`.
- Each assay has 96 true targets and 480 entrapments.
- Each run got a fresh directory, copied DIA/assay files and its own cache copy. Supplied inputs were never written to.

**Pipeline**
1. `features-folder` per seed.
2. Context + Percolator from the split files: background training seed 1, Percolator 3.01, pyIsoPEP 0.1.4 `q2pep --calc-q-from-fdr`.
3. All 18 runs exited 0.

**Environment**
- Linux x86-64, 4 CPUs, Java 17.0.21, `-Xmx6g`.
- About 4.5 min per seed for CTX-020 and about 2 min for the baseline.

**Analysis**
- Run with `docs/validation/ctx020_confidence_assessment.R` (R/ggplot2, theme_bw): `Rscript docs/validation/ctx020_confidence_assessment.R <runs_dir> <inputs_dir> <out_dir>`.
- Input is the native peptide output (`*.peptide.reference.txt`), matched to assay rows by sequence and charge. The bootstrapper writes N-terminal acetylation after residue 1, so it is moved to the N terminus first. All peptides matched.
- **Calibration** follows Ariana's `figure_09_only_two_flavors.R`:
  - ten bins with edges (k/10)²;
  - lower FDP = E/(T+E);
  - upper FDP = min(lower × 1.2, 1);
  - binom.test intervals.
- **Precision–recall:**
  - precision = T/(T+E), recall = T/96;
  - ranked by score, ties entering together.
- **Cross-check:** all 20 pooled calibration bins agree with `docs/validation/ctx020_peptide_calibration.py` within 1e-10 relative.

## Results

### Sensitivity (paired by seed, n = 9)

| Metric | CTX-020 median (range) | Baseline median (range) | CTX-020 − baseline, median (range) | CTX-020 lower in |
| --- | --- | --- | --- | --- |
| True targets reported (any score) | 95 (94–96) | 92 (90–93) | +3 (+1 to +6) | 0/9 |
| True targets at PEP < 0.01 and q < 0.01 | 63 (61–76) | 71 (62–77) | **−6 (−10 to +1)** | **8/9** |
| True targets at q ≤ 0.01 | 84 (82–90) | 86 (79–90) | 0 (−5 to +4) | 4/9 |
| AUPRC | 0.959 | 0.947 | +0.015 (−0.011 to +0.049) | 2/9 |

- **Coverage.** CTX-020 does what it is designed to do: nearly every scheduled target now gets a score and appears in the output (maximum recall 0.98–1.00 against 0.94–0.97). This is the source of the higher AUPRC.
- **Ranking.** At q ≤ 0.01, ranking-based sensitivity is unchanged.
- **PEP.** CTX-020 yields **fewer confident identifications by PEP**, a median of 6 fewer targets per seed below PEP 0.01, lower in 8 of 9 seeds. The PEP < 0.01 filter alone gives the same counts.

### FDR validity (entrapment-estimated FDP)

Pooled over all nine seeds:

| Build | Targets at q ≤ 0.01 | Entrapments at q ≤ 0.01 | FDP lower / upper | Targets at PEP < 0.01 | Entrapments at PEP < 0.01 | FDP lower / upper |
| --- | --- | --- | --- | --- | --- | --- |
| CTX-020 | 761 | 20 | 2.6% / 3.1% | 580 | 5 | 0.85% / 1.0% |
| Baseline | 764 | 27 | 3.4% / 4.1% | 622 | 6 | 0.96% / 1.2% |

**PEP calibration** (`calibration-pooled.pdf`, `calibration-by-seed.pdf`):
- The two builds have the same calibration shape.
- **PEP 0.04–0.36:** entrapment FDP sits near or slightly below the diagonal, so PEPs are calibrated to slightly conservative.
- **Lowest bin, PEP < 0.01 (mean PEP ≈ 0.0002):** FDP is 0.85% (95% CI 0.28–2.4%) for CTX-020 and 0.96% for the baseline. The many PEPs at the 1e-10 floor understate the error rate there. At the 1% decision threshold, however, the accepted set stays at or below about 1% FDP.
- **PEP 0.49–0.81:** FDP is above the diagonal in both builds (CTX-020: 75% and 93% against mean PEP 57% and 73%). PEPs are optimistic in this upper-middle range.
- **q-values at 1%:** not controlled by the entrapment estimate in either build (2.6–4.1% pooled).
  - Seed 2 dominates: 10 (CTX-020) and 12 (baseline) entrapments at q ≤ 0.01.
  - Excluding seed 2: CTX-020 is 1.5% (10/681), baseline 2.2% (15/691).
  - On seed 2 both builds give q = 0.005 down to PEP ≈ 0.46. That is a decoy-based q-value estimation problem that already exists before CTX-020.

**Entrapment caveat**
- Every entrapment reported, in both builds and all seeds, lies outside its own scheduled window.
- The same entrapment peptides recur in both builds at the same RT. An example is `NSNLVGAAHEELQQSR` (seed 2), which has score 8.5, PEP 1e-10, observed at 34.2 min against its 19.7 min schedule.
- Some entrapment matches are therefore likely genuine peptide signal elsewhere in the gradient. The entrapment FDP is then an over-estimate in both builds equally.
- This assumes the entrapments are real proteome peptides that were masked only inside their schedule. That assumption needs Ariana's confirmation.

## Verdict

**FDR validity**
- CTX-020 does **not** worsen calibration relative to the baseline.
- The PEP calibration shape is the same.
- Entrapment FDP at PEP < 0.01 is ≤ about 1% in both builds and slightly lower with CTX-020.
- The q ≤ 0.01 threshold over-reports in both builds, driven mostly by seed 2 and by an issue that predates CTX-020.
- Neither build demonstrates well-calibrated PEPs above 0.49.

**Sensitivity**
- CTX-020 scores almost all scheduled targets and slightly improves AUPRC.
- It **reduces PEP-based detections by a median of 6 targets per seed (8/9 seeds)**.

**Likely causes (unproven)**
- The larger reference population: about 320 against 190 peptides per seed, mostly forced low-quality candidates, which changes the reference decoy distribution that pyIsoPEP fits.
- Review finding 4 (assay feature definitions differ from background) and finding 2 (pre-LDA score for the weak fallback).

**Recommended follow-up, if Ariana wants to recover sensitivity**
- Fix findings 2 and 4.
- Rerun this matrix.
- Optionally compare against computing PEPs over searched candidates only. That would be a material plan change and needs approval.

## Limitations

- This is one dataset family (astral HeLa, 96 targets per seed) with n = 9. No significance testing.
- The baseline includes a patch that was never on `7d0f524` by itself.
- Only Context + Percolator was run; mProphet and standard Percolator were not rerun.
- Fonts: "Arial" resolves to the metric-compatible Liberation Sans in this container.

## Outputs (git-ignored, cloud session)

- `tmp/ctx020-r2/analysis/`:
  - `detection-counts.tsv`, `pr-summary.tsv`, `paired-build-comparison.tsv`;
  - `calibration-pooled.tsv`, `calibration-by-seed.tsv`;
  - `annotated-peptides.tsv`, `assay-match-summary.tsv`;
  - `calibration-pooled.{pdf,png}`, `calibration-by-seed.{pdf,png}`, `sensitivity.{pdf,png}`.
- Runs and logs: `tmp/ctx020-r2/runs/`, `tmp/ctx020-r2/logs/`.
- Drive bundle: `2026-11-18-CTX020-round2-confidence-assessment.zip` in the ctx-020 folder.
- Jar SHA-256:
  - baseline + fix: `9b7bdd6f…a001fe3`;
  - CTX-020: `f0eb3070…5db10`;
  - unmodified `7d0f524`: `04052da3…678a0`.
