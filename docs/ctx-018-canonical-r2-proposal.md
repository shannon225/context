# CTX-018 — Canonical scope revision 2 proposal (adopted; review record)

- Status: **Adopted.** Ariana approved this revision on 2026-10-31, and the text is now revision 2 of `docs/uu-ctx-018-percolator-mprophet-parity.md`, which is canonical. This file is kept unchanged below as the review record. Do not edit it; make changes only through a new canonical revision. (Original status line: pending Ariana's approval; until approved, revision 1 stayed canonical.)
- Drafted 2026-10-31 from the independent plan review in chat (items H1–H7). Ariana approved the H2 and H7 directions and asked for the r2 files. She chose the H5 (extra outputs) and H6 (seed flag) directions earlier in the same chat.
- Google implementation record: https://docs.google.com/document/d/1wbZsg6TDiMx8mZ-__cl8tGexxWRcIw7IESnbO_-c_Pk/edit. The record is still at r1; a matching r2 update has been proposed but not made.
- Checklist and log: `TASKS.md`, section "CTX-018 — Percolator and mProphet parity with Context-MS". r1 tasks `CTX018-001`–`016` are kept; r2 adds `CTX018-017`–`027`. r1 tasks that r2 replaces are marked "proposed superseded" and become `Superseded` on approval.
- Baselines (unchanged): Context `ariana/scoring` at `a57ef953b29b091d6ea0284030d8082a422062b6`; Context-MS `main` at `d207f47291b6a1716e4a09e326fc3eda518a197f`; EncyclopeDIA 6.9.23 jar, unchanged. No Git delivery is authorized.

## Changes from revision 1

| Item | Change | Reason |
|---|---|---|
| H1 | All aligned mProphet behavior sits behind a **Context-only mode** that only `runContextMProphet` turns on. The default `MProphetReiter` path stays unchanged for standard and targeted mProphet and for `scribe-context` / `ContextMProphetExecutor`. | `MProphetReiter` is shared by five call sites; changing it would alter out-of-scope workflows. ScribeTwo is no longer the production path. |
| H2 | In Context-only mode, models are ranked by passing count alone with a stable sort; ties keep iteration order, as in Context-MS. | Verified in the bytecode: today ties are broken by `LinearDiscriminantAnalysis.compareTo` (sum of the coefficients) and then reversed. |
| H3 | Option B is **diagnostic only**. Its check is agreement of passing counts per iteration, plus the first iteration that diverges and why. The weight tolerance comes from observed differences. The r1 "agree within floating-point precision" bar is dropped. | Java parses features and computes scores in float32, and discrete thresholds amplify small differences. The r1 tasks contradicted the canonical plan, which already called option B diagnostic. |
| H4 | A **Context-owned mProphet reader** replaces `MProphetFeatureReader.parseFeatureFile` in Context-only mode. It also checks that the model and the reference have the same features. | EncyclopeDIA's reader hard-codes feature exclusions, throws when no named starting score exists, and parses the reference separately with no feature-alignment check. |
| H5 | Context mProphet also writes `P.rescored_features.txt` and `model/P.weights.txt`. | The z-score metric needs every reference target and decoy score, and the weight comparison needs a weights file. Context-MS writes both. |
| H6 | `-seed <int>` on the `percolator` and `mprophet` commands. The default is 1, so current behavior is unchanged. The same seed must give the same results as `AllWorkflowExecutorCLI --seeds`. | Option C needs seeds 1–10 for each engine. Today only AllWorkflow accepts seeds, and `ContextMProphetCLI` hard-codes seed 1. |
| H7 | Acceptance metrics fully specified: identity and validity gates, the peptide count, a Context-MS z consensus, absolute relative differences, and noise floors (see below). | r1 left open signed vs absolute differences, how seeds are paired, the decoy SD convention, and the join key. |
| Order | Fold injection (option B) comes **before** preprocessing alignment, so each preprocessing change is measured with folds fixed. The seed flag comes before the phase measurements. | Separates algorithmic effects from seed noise and gives every phase measurement multiple seeds. |
| Baseline | The baseline adds Context-MS's own seed-to-seed spread, a repeat run at a fixed seed, the current `*.command.txt`, a count of rows per peptide in the comparison file, and pinned binaries. | Checks whether ±2% is achievable before implementation, settles `-Y`/`-y` without code, and pins reproducibility. |

## Intent

Bring Context's (Java) Context-Percolator and Context-mProphet workflows back in line with Context-MS. Fix algorithmic differences first; address language-specific differences (float32 vs float64, parsing edge cases) only if the final criteria fail. Results need not match 1-to-1.

## Populations (unchanged from r1)

- **Background:** trains the discriminant (Percolator or mProphet).
- **Reference:** the trained model is applied without retraining to reference targets and decoys.
- **Confidence:** q-values and PEPs are estimated on the scored reference targets and decoys, at PSM level and again after best-per-peptide de-duplication.
  - Percolator-Context uses pyIsoPEP `q2pep`.
  - mProphet-Context uses Context-MS's q-value/PEP method.

## Scope

- **All edits are in Context.**
- **Out of scope:**
  - `TargetedBootstrapper` and its CLI/tests.
  - Decoy generation (CTX-016).
  - EncyclopeDIA and Context-MS source.
  - The standard and targeted Percolator/mProphet workflows.
  - `scribe-context` / `ContextMProphetExecutor`, which stay on the default path.
  - Changing Context's default Percolator version (v3.01).
  - Porting numpy's random generator, or matching Context-MS folds in production.

## Approach

1. **Baseline and reproducibility.**
   - Report peptides at PEP ≤ 0.01 alongside the existing q ≤ 0.01 count.
   - Add `docs/validation/ctx018_compare.py`. It imports Context-MS as an installed package and makes no Context-MS edits. Context-MS internals are instrumented only by wrapping them inside the script:
     - `context.mprophet._split_kfold`, to export the folds actually used.
     - The fold-level passing step, to get per-iteration passing counts.
   - Pin the tools:
     - Both tools use the identical Percolator v3.05 binary: the jar's `bin/percolator-v3-05.lin`, recorded by sha256, and exposed to Context-MS through a symlink named `percolator` on `PATH`.
     - pyIsoPEP is pinned to 0.1.4, which was verified for this plan.
   - Before any code change, record:
     - The starting gap.
     - Context-MS's seed 1–10 spread.
     - A repeat run at a fixed seed for each tool.
     - The current Percolator `*.command.txt`.
     - The number of rows per peptide in the comparison file.
2. **Seed flag.** Add `-seed <int>` (default 1, checked by `TrainingSeeds.requireValid`) to the `percolator` and `mprophet` commands. Both commands already call `ContextPercolator.trainAndApply(..., seed)` and `AllWorkflowExecutorCLI.runContextMProphet(..., seed)`. Make their parameter construction match AllWorkflow's, so the same seed and inputs produce the same outputs from either entry point.
3. **Percolator.**
   - Use pyIsoPEP `q2pep --calc-q-from-fdr` in the Context workflow; keep `d2pep` for the other workflows.
   - For Context background training only, match Context-MS's training-affecting arguments:
     - Add `--post-processing-tdc --override --protein-report-duplicates`.
     - Use Percolator's own defaults for `-N` (0), `--maxiter` (10) and `--trainFDR`/`--testFDR` (0.01). These defaults were confirmed from the v3.05 `--help`.
     - Drop `--no-terminate`.
   - Keep the output-only arguments that Context needs: `--seed`, `--weights`, `--results-peptides`, `--decoy-results-peptides`.
   - EncyclopeDIA Percolator options are not used by Context training. How they are handled is open question 2.
   - The same binary, seed and input should give the same per-fold weights.
4. **mProphet confidence.**
   - Port Context-MS `fdr.py` to a small Context class:
     - Gaussian decoy null, using the population SD (`ddof=0`).
     - p-values from the complementary CDF.
     - Benjamini–Hochberg q-values without π0.
     - π0 = the median over λ = 0.1…0.9, with a floor of 0.05.
     - scipy-style Silverman KDE on logit p: bandwidth = sample SD (`ddof=1`) × (3n/4)^(−1/5).
     - PEP non-decreasing in p.
   - Reuse EncyclopeDIA math only where it is verified identical. Its `LocalFDR.estimateLocalFDR` is **not** identical: it builds `KDE(x, 1.0, 100)` and applies no π0 floor.
   - Recompute q and PEP after best-per-peptide de-duplication. The peptide key is the peptide column (second-to-last) verbatim. Ties keep reference-file order. Reuse the logic of `ContextPercolator.writeBestPerPeptide`.
   - Additionally write `P.psm.reference.txt`, `P.peptide.reference.txt`, `P.rescored_features.txt` (every reference row plus `score`) and `model/P.weights.txt` (`feature`/`weight`/`__bias__`, the `PercolatorWeights.writeAveraged` format). Keep the existing outputs.
5. **Context-only mProphet mode.** Add a mode in `MProphetReiter` that only `runContextMProphet` turns on. With the mode off, behavior is byte-for-byte unchanged for every other caller. Default-path regression tests cover standard and targeted mProphet and `ContextMProphetExecutor.trainAndApplyFeature`.
6. **Model ranking.** In Context-only mode, rank by passing count with a stable descending sort; ties keep iteration order.
7. **Option B (diagnostic).**
   - An optional fold file with columns `iteration`, `fold`, `position`, `id`. Within-fold order is preserved, because the 10× decoy cap takes the first decoys in a fold.
   - Folds are built with the public `MProphetDataset(featureNames, startingScoreIndex, data)` constructor from `MProphetData` rows joined by `id`.
   - Missing, duplicate or extra ids and a wrong iteration count fail clearly.
   - Without the file, behavior is unchanged.
   - Report, for each of the 50 iterations, whether the passing counts agree, the first iteration that diverges and its cause, and the maximum relative difference in averaged weights.
8. **Context-owned reader and preprocessing**, used in Context-only mode only, and measured one change at a time with injected folds:
   - (a) Reuse `ContextFeatures.prepare`: header check, σ < 1e-6 pruning (population SD) on the background, and the same drop list applied to the reference.
   - (b) Build `MProphetData(id, peptide, proteins, float[] features, label == −1)` and `MProphetDataset` for the background and the reference. First prove that, with legacy column selection, this matches `parseFeatureFile`.
   - (c) Context-MS feature selection: positional non-feature columns plus case-insensitive `ScanNr`/`scan`, then the metadata exclusions. Resolve `ExpMass` and `protein`.
   - (d) Context-MS starting-score preference: the last matching name wins; then `main_var_Intensity`; then the best-separating feature.
   - (e) No 500,000-row fold cap.
   - (f) Check that the model and the reference have the same feature names in the same order.
9. **Dead code.** Comment `encyclopedia/MProphetDataset.java` as unused as of 2026-10-31; its q-value code multiplies by π0 twice. Do not delete it. Do not place new classes in `org.searlelab.context.encyclopedia` that refer to `MProphetDataset` without an import, or they would pick up this dead class.

## Final acceptance (option C)

Judged only in the final-acceptance task, not as gates for earlier tasks. The same metrics are measured and recorded after every phase.

- **Data:** Ariana's comparison file. Context-MS `example/` is interim and report-only; it has exactly one row per peptide, so it cannot test de-duplication.
- **Runs:** both engines, seeds 1–10 per tool, the identical Percolator v3.05 binary (sha256 recorded), pyIsoPEP 0.1.4.
- **Gates** (pass/fail, no tolerance):
  1. **Identity:** the same reference target and decoy `id`s and counts in both tools' rescored outputs, and the same de-duplicated peptide keys.
  2. **Validity:** scores are finite; q and PEP are in [0, 1]; PEP never decreases as score decreases.
- **Criteria:**
  3. **Peptides:** N = reference target peptides with PEP ≤ 0.01 in the peptide-level output. |median₁₋₁₀ N(Context) − median₁₋₁₀ N(Context-MS)| / median N(Context-MS) ≤ 2%. Also report Context-MS's own min–max over seeds. If ±2% is narrower than that spread, the result goes to Ariana's threshold review rather than auto-failing.
  4. **Scores:**
     - Per run, z = (score − mean of reference decoy scores) / population SD of reference decoy scores.
     - Context-MS consensus: each row's median z over the 10 Context-MS seeds.
     - Rows: reference targets with consensus |z| ≥ 1, joined by `id`.
     - For each Context run: m = median over rows of |z_Context − z_consensus| / |z_consensus|.
     - Pass if the median of m over the 10 runs is ≤ 0.20.
     - Noise floor: the same statistic for each Context-MS seed against the consensus of the other nine.
- **Report-only:** Jaccard index of the PEP ≤ 0.01 peptide sets; 90th percentile of the relative z difference; Spearman correlation; number of sign flips; q ≤ 0.01 counts; Percolator per-fold weight differences; option B per-iteration agreement.
- A miss triggers a review, with Ariana, of language-specific differences or of the thresholds. It is never silently accepted.

## Validation

- **JUnit 4 tests:**
  - Passing counts.
  - pyIsoPEP and Percolator commands, for both the Context and the standard workflow.
  - The `-seed` flag, and equivalence with AllWorkflow on a fixture.
  - The q/PEP port against Python-computed values.
  - Peptide de-duplication, identity and ties.
  - Stable model ranking.
  - Regression tests for the default path.
  - Reader feature selection, starting score and alignment failures.
  - Fold-file joins and malformed inputs.
  - The new output files.
- **Comparison script:** runs on `example/` and on the comparison file.
- **Build:** Java 17 Maven `verify`, with skips and pre-existing failures recorded separately.

## Open questions for Ariana

1. **Peptide-count floor:** for small reference sets, should the criterion be strict ±2% (current) or "±2% or ±1 peptide, whichever is larger"?
2. **EncyclopeDIA Percolator options** (`-percolatorTrainingIterations`, `-percolatorTrainingFDR`, `-percolatorThreshold`-style test FDR, `-percolatorTrainingSetSize`, `-percolatorModelFile`) are not used by Context training in r2. Recommendation:
   - Reject `-percolatorModelFile` with a clear error in the Context workflow, since it would otherwise add `--init-weights` with `--maxiter 0`.
   - Log the ignored training options, and document them in the README.
   - This reinterprets config keys, so it needs Ariana's decision.
3. **Is "q2pep switch" a user-facing flag or a hard change?** The Context PEP column changes meaning either way, so the README needs a migration note.

## Follow-up (not part of CTX-018; needs a backlog ID from Ariana)

- **ScribeTwo vs EncyclopediaTwo comparison for the paper.** Run both feature sets through the aligned `mprophet` and `percolator` commands with identical settings and seeds, so the comparison isolates feature generation. Check ScribeTwo feature names against the step 8 reader, and whether `.pairs.tsv` sidecars exist (otherwise `--allow-legacy-features true` is needed).
