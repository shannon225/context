# CTX-017 — Approved canonical scope, revision 2

Approved by Ariana on 2026-11-13 as guide revision r2.2 (Google implementation record:
https://docs.google.com/document/d/1m9qrS-0_IErOL_dia7ms43ggwaRDsL8O1f4HTme-lxI/edit). Revision 2
changes only the points below. Everything else in revision 1, kept in full underneath, still
applies. This plan is frozen for the implement/review loop; material changes return to Ariana.

## What revision 2 changes

- **Data: two datasets.** Mouse cd8 2mz GPF masked2–6 (152 assay targets, 10 entrapments per target)
  and HeLa astral 2mz masked1–5 (62 assay targets, 5 entrapments per target). Both have the same 27
  features. This replaces "mouse masked2–10, second dataset later".
- **Every figure and table shows both datasets.** Results are summarized per dataset and never
  pooled across datasets. Entrapments per target (r) are measured per assay.
- **Reporting thresholds:** passing targets and entrapment FDP are reported at 1% and 5% q-value for
  both datasets. This is reporting only; Percolator and context-ms training settings are unchanged.
  Reason: with about 62 targets per HeLa assay, the smallest reachable q-value is about 1%.
- **Starting benchmark:** the three baseline workflows were regenerated on the 10 assays with
  Percolator 3.05 and training seeds 1–3 (2026-11-13). They are saved as the starting benchmark, in
  Drive `ctx-017/results/2026-11-13_baselines_perc305`.
- **Features are removed per training round, not from the program** (Ariana's note in the guide).
  All features stay available. At the end, a run with all features must reproduce the starting
  benchmark.
- **Thresholds for recommendations:** Ariana sets the FDP tolerance and seed-spread thresholds after
  seeing the data. The work is stop-and-start: each step returns to her with the data, the commands,
  and the outputs.
- **Figure colours:** dark red = Context, light red = targeted Percolator (reference only),
  gray = Percolator (full file).
- **Branch:** CTX-017 work is committed on `ctx017`.
- Later, mProphet should be runnable the same way with all features (Ariana's note); it stays out
  of scope for now.

## Superseded historical revision 1 — retained in full

# CTX-017 — Approved canonical scope, revision 1

Approved by Ariana on 2026-11-12 as guide revision r2.1 (Google implementation record:
https://docs.google.com/document/d/1m9qrS-0_IErOL_dia7ms43ggwaRDsL8O1f4HTme-lxI/edit). This plan is
frozen for the implement/review loop; material changes return to Ariana as a new guide revision and
a new plan revision. The `TASKS.md` checklist needs its own approval before implementation starts.
No Git delivery is authorized.

## Intent

Find out whether every scored feature is needed by Context's Percolator engine. Ariana makes every
keep/drop decision. Claude processes the data and presents evidence, with a separate, clearly
labelled recommendation. No feature is excluded from a later analysis stage, or from a final feature
set, without Ariana's recorded decision. This feature does not permanently remove features from any
default pipeline.

## Engine, versions, and data

- **Engine:** the Python `context-ms` package, version 0.1.3 from PyPI, unmodified, run with
  `--engine percolator`. The mProphet engine is set aside for now. No Java changes in this repository
  for now; the r1/r2 Java steps are superseded and may be revisited as a separate decision.
- **Percolator 3.05** for every workflow: `percolator-v3-05.centos` from `encyclopedia-6.9.23.jar`,
  placed first on `PATH`. context-ms does not pin a version; it uses the `percolator` on `PATH`, else a
  podman container of Percolator master. Every run records its Percolator version, and results from
  different versions are never compared.
- **pyIsoPEP** 0.1.4; R 4.3.3 with ggplot2, patchwork, dplyr, tidyr (`theme_bw`, Arial). In the
  cloud session Arial falls back to Liberation Sans.
- **Data:** mouse cd8 2mz GPF masked bootstrap assays, seeds masked2–10. masked1 is skipped because
  its earlier Context run failed. Ariana is to reconfirm these files are current before analysis.
  A second dataset will follow and is analysed the same way.
- **Baselines:** Ariana's earlier baselines used Percolator 3.09 with training seed 1. All three
  workflows are regenerated with Percolator 3.05 and at least 3 training seeds:
  - Context-Percolator: context-ms, trained on background and applied to reference;
  - standard Percolator on the full feature file;
  - reference-only Percolator.

## Populations and measurements

- **Background:** trains Percolator inside context-ms.
- **Reference:** the targeted panel, scored by the transferred model. Its target-labelled rows are
  assay targets and entrapment peptides.
- **Entrapments:** empirical entrapment peptides from the `*_entrapment_assignments.tsv` files. They
  are known false targets, r = 10 per assay target in the mouse data.
- **Decoys:** search decoys. Target, decoy, and entrapment identities are preserved end to end.
- **q-values:**
  - Context-Percolator: pyIsoPEP (context-ms `peptide.reference.txt`);
  - standard and reference-only Percolator: Percolator's own q-values.
- **Sensitivity:** passing assay target peptides N_T at q ≤ 1%. Entrapments are excluded.
- **Entrapment FDP:** at each q threshold, with peptides classified by bare sequence:
  - lower bound: N_E / (N_T + N_E);
  - upper bound, loose: 2·N_E / (N_T + N_E);
  - upper bound, tight: (1 + 1/r)·N_E / (N_T + N_E).

  Both upper bounds are reported until Ariana chooses one, after the second dataset.
- **Seeds:** results are the mean and spread over masked seeds and at least 3 training seeds. A single
  run is not evidence. Claims stay limited to the tested datasets.

## Analysis stages and decision rounds

1. **Screening** (no training): missing or constant fraction; univariate target-vs-decoy AUC;
   Spearman correlation clusters at |ρ| ≥ 0.9; background-vs-reference shift.
2. **Decoy-artifact check:** decoy-vs-entrapment AUC with a confidence interval. → **Round A:** which
   clusters and families go into ablation, and any features dropped up front.
3. **Grouped leave-one-out:** remove each correlation cluster or named family as a unit
   (approved). → **Round B:** which clusters get single-feature follow-up.
4. **Within-cluster single-feature leave-one-out and stability:** variation and sign flips of
   Percolator's normalized weights. → **Round C:** proposed final set, and whether stage 5 runs.
5. **Optional backward elimination**, only if Ariana chooses it. → **Round D:** final set.

Each round:
1. Upload the round's tables, figures, and R scripts to the Drive folder `ctx-017/results`.
2. Summarize in chat, evidence first and recommendation second.
3. Ariana decides.
4. Record the decisions in the guide's Feature decisions table and in `TASKS.md`.

## Evidence definitions

- **Δ sensitivity:** paired per masked seed. Passing assay targets at q ≤ 1% without the feature,
  minus with it. Clearly negative, beyond the seed spread, means the feature helps.
- **Δ entrapment FDP:** the same pairing, for the lower and upper bounds. A rise without the feature
  means the feature protects FDR control. A sensitivity gain that comes with an FDP rise is not a gain.
- **Target-vs-decoy AUC:** the feature alone on background targets vs. decoys. 0.5 means no signal.
  A high AUC does not make a feature necessary, because a correlated partner can carry the same signal.
- **Decoy-vs-entrapment AUC:** reference decoys vs. empirical entrapments, both false. Near 0.5 is
  good; far from 0.5 suggests the feature detects decoy construction.
- **Correlation cluster:** Spearman |ρ| ≥ 0.9 on the background. Clusters are the stage 3 removal
  groups.
- **Background-vs-reference shift:** KS statistic and standardized mean difference, separately for
  targets and decoys. A large shift is a transfer risk.
- **Missing/constant fraction:** data quality. context-ms already drops near-constant features
  (`charge1` and `charge4` in the mouse data).
- **Weight stability:** coefficient of variation and sign-flip rate of Percolator's normalized
  weights. This is a stability signal, not importance.

## Recommendations

Recommendations go in a separate file per round, `round<A–D>_recommendations.tsv`, keyed by the same
feature or group IDs as the results tables. Results tables stay evidence-only. Columns:

- `claude_recommendation`: keep / removal candidate / possibly harmful / insufficient evidence.
- `recommendation_basis`: the one or two evidence items behind it, with their values.
- `confidence`: low / medium / high.
- `if_applied`: the measured Δ sensitivity and Δ FDP of that removal, or "not yet measured" before
  ablation.

Thresholds shape recommendations only; they never decide.

## Data handling and checks

- Input files are read-only. They are SHA-256 checked before and after runs.
- Ablations remove feature columns from copies of the inputs before context-ms runs. Pruned copies
  keep `id`, `Label`, `ScanNr`, `sequence`, `Proteins`, and labels.
- One run folder per ablation, training seed, and masked seed. Existing folders are refused, never
  overwritten.
- Unknown, non-feature, and all-feature exclusions are rejected with a clear error. A failed run is
  recorded as failed, never as a result.
- Every result row records experiment, workflow, ablation set, training seed, masked seed, Percolator
  version, and context-ms version.
- Self-check: a driver run with no exclusions reproduces the regenerated baseline.
- Scripts live in the ignored repository folder `tmp/ctx017/`, with copies in Drive. Raw per-run
  outputs stay local to the session or Ariana's machine.

## Scope and delivery

- No edits to Context Java source, context-ms source, or EncyclopeDIA.
- No staging, commit, push, pull request, or release without Ariana's separate explicit
  authorization.
- The Report is written outside the repository. Cloud sessions ask Ariana where to save it.
- Ariana's final review is required. An Until Useful `APPROVE` means ready for her review, not
  accepted.
