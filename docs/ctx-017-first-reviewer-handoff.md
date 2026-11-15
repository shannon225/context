# CTX-017 — first reviewer handoff

Prepared 2026-11-15 for an independent reviewer in a separate chat (`CTX017-013`, Until Useful review of the scripts and the analysis). This is a request for review of finished analysis work. It does not claim completion and is not Ariana's final review.

## Review authority and snapshot

- **Read first:**
  - root `AGENTS.md` and `CLAUDE.md`
  - `TASKS.md`: the CTX-017 checklist and the dated log from 2026-11-12 on
  - the approved canonical plan `docs/uu-ctx-017-feature-necessity.md`, **revision 2**. Revision 1 is kept underneath and still applies except where revision 2 changes it.
  - the Google implementation record, guide **r2.2**: https://docs.google.com/document/d/1m9qrS-0_IErOL_dia7ms43ggwaRDsL8O1f4HTme-lxI

  Treat the plan, logs and guide as scope and claims to check, not as proof.
- **Repository:** `shannon225/context`, branch `ctx017`. CTX-017 made **no source, test or build changes**. It is Percolator-only analysis through the Python package context-ms 0.1.3 and is out of Java scope by plan. The branch carries only `TASKS.md`, `CLAUDE.md` (Report location), this note and the canonical plan.
- **Where the scripts and results are:** the analysis scripts live in `tmp/ctx017/`, which Git ignores, so **they are not in the repository**. Review the copies in Google Drive under `ctx-017/results/`:

| Drive folder | Stage | Scripts | Evidence |
|---|---|---|---|
| `2026-11-13_baselines_perc305` | Starting benchmark | `run_baselines.sh`, `baseline_counts.py`, `baseline_figure_v2.R` | `baseline_counts.tsv(.gz)`, figures |
| `2026-11-13_ablation_driver_check` | Driver self-check (no-exclusion run reproduces the benchmark) | `ablate.py` (first version) | check outputs |
| `2026-11-13_roundA_screening` | Stages 1–2, Round A | `screen.py`, `screen_figures.R` | screening tables, `roundA_recommendations.tsv`, `README_reproduce_roundA.md` |
| `2026-11-13_roundBC_ablation_stage4` | Stages 3, 3b, 4; Rounds B and C | `ablate.py`, `run_roundB.sh`, `run_roundC_single.sh`, `ablation_summary.py`, `ablation_figures_v2.R`, `weight_stability.py`, `weight_figure.R` | summary and paired tables, `weight_stability.tsv`, recommendations, `README_reproduce_roundBC.md` |
| `2026-11-15_stage5_backward_elimination` | Stage 5, Round D | `backward_elim.py`, `be_figure.R` | `be_path.tsv`, `be_candidates.tsv`, `roundD_recommendations.tsv`, `README_reproduce_stage5.md` |

- **Raw run folders** (about 1,100 runs) existed only in the cloud container and are not archived. The paired and candidate tables hold every count used in the decisions.
- **Review is read-only.** Do not stage, commit, push, edit `TASKS.md`, or upload. Send findings to the implementing chat for correction.

## What was done and decided

- **Data:**
  - Mouse `cd8_2mz_gpf_masked2..6_assay`: 152 assay targets, r = 10.
  - HeLa `astral_2mz_hela_masked1..5_assay`: about 62 assay targets, r = 5.
  - Training seeds 1–3; Percolator 3.05.0; pyIsoPEP 0.1.4 q-values.
  - Results are per dataset and never pooled across datasets.
- **Features:** 24 features: the 23 columns plus `charge1`–`charge4` treated as one "charge" feature. These form 21 ablation units; the three Spearman |ρ| ≥ 0.9 pairs are each one unit.
- **Rounds:**
  - Stage 1–2 screening → Round A
  - Grouped leave-one-out (630 runs) → Round B
  - Single pair-member removal (180 runs) and weight stability → Round C
  - Greedy backward elimination (195 candidates × 30 runs) → Round D
- **Ariana's decisions:** keep all 24 features at every round. Final set recorded 2026-11-15. FDP is reported as the lower bound N_E/(N_T+N_E) and the tight upper bound (1+1/r)·N_E/(N_T+N_E). The loose 2× bound appears in earlier outputs only.
- **Closing check (`CTX017-017`):** a fresh all-features run (2026-11-15) matched the starting benchmark on all 1,500 rows (dataset × assay × seed × 50 q thresholds).

## Priority review questions

1. **`ablate.py` correctness and safety.**
   - Do the pruned copies remove only the requested feature columns and keep `id`, `Label`, `ScanNr`, `sequence`, `Proteins` and the label values?
   - Are unknown, non-feature and all-feature exclusions rejected?
   - Are existing output folders refused and failed runs recorded as failed?
   - Are inputs SHA-256 checked before and after?
   - Is each training seed actually passed to Percolator?
2. **Population and identity rules.**
   - Is Percolator trained on background only and applied to the reference?
   - Are assay targets and entrapments classified by bare sequence (modifications and flanking residues stripped) against `*_entrapment_assignments.tsv`, with entrapments excluded from N_T and decoys excluded from both?
   - Is r measured per assay?
   - Is there any duplicate-peptide or tie handling that could double-count?
3. **Measures.**
   - Check the paired Δ N_T, the pooled FDP bounds and the q-threshold matching (float rounding at 0.01/0.05) in `ablation_summary.py` and `backward_elim.py`.
   - Check the AUC tie handling, KS/SMD, bootstrap seed and correlation clustering in `screen.py`.
   - Check the weight normalization and sign-flip rate in `weight_stability.py`.
4. **Stage 5 design.**
   - Was the rule truly fixed before the runs? (See the `TASKS.md` 2026-11-14 entry and the `backward_elim.py` docstring.)
   - Is comparison against the fixed all-features reference appropriate?
   - Is excluding HeLa masked2 at q = 0.01 justified? Its benchmark passes 0 targets at q ≤ 0.010 and about 45 at q ≤ 0.011.
   - Is restart-by-reuse after the container restart safe (`TASKS.md` 2026-11-15)?
5. **Claims and interpretation.**
   - Are the round summaries, the guide and the recommendation files consistent with the tables?
   - Are evidence and recommendations kept separate (no recommendation columns in result tables)?
   - Do any claims go beyond the data (two datasets, five assays each, selection and evaluation on the same assays)?
   - Do the figures follow the agreed conventions: both datasets, 1% and 5%, `theme_bw`, Arial, the agreed colours?
6. **Reproducibility.**
   - Can the stages be reproduced from the three `README_reproduce_*.md` files?
   - Are versions, seeds and nondeterminism recorded? The Round A rerun was byte-identical, and Percolator reruns reproduced the benchmark.

## Known limits at handoff

- **No Java or unit tests apply.** The checks are the scripts' self-checks, the baseline reproduction and the closing check. These show mechanics and reproducibility, not general feature necessity or FDR validity.
- **Scope of the evidence.** Two datasets with five assays each. HeLa has only about 60 assay targets per assay, so its 1% results are coarse.
- **Stage 5 is optimistic.** The reduced 9-unit set was selected and evaluated on the same data. It was not adopted.
- **Thresholds.** The −1 target and +1 pp stage 5 tolerances were search settings only. Ariana has not set formal FDP or seed-spread thresholds.
- **Fonts.** Arial fell back to Liberation Sans in the cloud figures.
- **Still to come:** Ariana's final review (`CTX017-014`) and the Report in `docs/reports/` (`CTX017-018`).

## Requested response

Lead with `APPROVE`, `APPROVE WITH MINOR ISSUES`, `REQUEST CHANGES`, or `BLOCKED`. Distinguish:

- script defects
- analysis or interpretation errors
- validation gaps
- documentation gaps

For each actionable finding, give the file and line (or table and row), the failure scenario and its scientific impact, and a concrete correction. State what you inspected or reran yourself and what remains unverified. Say whether the work meets canonical plan revision 2. Send findings to the implementing chat; this handoff is not the review outcome.
