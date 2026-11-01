# Context implementation tasks

## CTX-016 — EncyclopediaTwo matched decoys and competition (archived; closed 2026-11-01)

- Moved verbatim to [`docs/TASKS-archive.md`](docs/TASKS-archive.md) on 2026-11-01 at Ariana's request, together with the `CLI-FOLLOWUP-001`/`002` and `CLASS-GUIDE-001` follow-ups recorded in the same block.
- Closed by Ariana on 2026-11-01 after her independent global-file entrapment review. `CTX016-009`, `010`, `040`, `053` and the last acceptance item are checked in the archive, with her evidence and its limits in the archive's last log entry. No CTX-016 work remains open.
- Closing note appended to the Google implementation record on 2026-11-01. Report addendum: [`docs/2026-11-01-CTX-016-Report-addendum.md`](docs/2026-11-01-CTX-016-Report-addendum.md), at Ariana's direction; the external Report on her machine is unchanged.

## CTX-018 — Percolator and mProphet parity with Context-MS

- Google implementation record: https://docs.google.com/document/d/1wbZsg6TDiMx8mZ-__cl8tGexxWRcIw7IESnbO_-c_Pk/edit — revision r1 (2026-10-31; Docs revisionId `AHj4eMTbkMMjX3KFGMxxlDHakDZnFcFFatKh4gPIsg8RwRbx9X4R7ek4cRPFIj0JO_qdBzlUQalCrih-jVWsLky1g59LZhJU5ny44ZlDrmg`). Updated to r2 on 2026-10-31 (Docs revisionId `AHj4eMQTP-eUhkBt9VX-0P7taQSTpmvCewKWqDrIDwxLQc2L3GEZh11_cVMi5W71gxUKlVY1aGWWcMvbJSHEncpJno1exHhK-76pc83u92o`; later edits that only record commits do not change the plan).
- Canonical Until Useful plan: `docs/uu-ctx-018-percolator-mprophet-parity.md`, revision 1 (frozen 2026-10-31).
- Approval status: **Approved** by Ariana on 2026-10-31 in chat ("I approve of the plan"). Ariana will have an independent agent review the plan before implementation; implementation has not started. Git delivery is not authorized.
- Revision 2 approval status: **Approved** by Ariana on 2026-10-31 in chat ("You have my approval"). r2 is now canonical in `docs/uu-ctx-018-percolator-mprophet-parity.md`, with r1 kept below it as history. Earlier pending wording, kept as history: The independent review (2026-10-31) proposed r2: `docs/ctx-018-canonical-r2-proposal.md`, adding tasks `CTX018-017`–`027` below. Ariana approved the H2 and H7 directions and asked for the r2 files; approval of the full r2 text is pending. Until then, r1 (`docs/uu-ctx-018-percolator-mprophet-parity.md`) stays frozen and canonical, and no implementation starts.
- Baselines: Context `ariana/scoring` at `a57ef953b29b091d6ea0284030d8082a422062b6`; Context-MS `main` at `d207f47291b6a1716e4a09e326fc3eda518a197f`; EncyclopeDIA 6.9.23 jar (unchanged).
- Scope boundary: Context-only. Out of scope: `TargetedBootstrapper` and its CLI/tests; decoy generation (CTX-016); EncyclopeDIA source; Context-MS source; the standard and targeted Percolator/mProphet workflows; changing Context's default Percolator version (v3.01); porting numpy's random generator (option A). Language-specific differences are deferred unless the final criteria fail.
- Populations: background trains the discriminant; the model is applied without retraining to reference targets and decoys; q-values and PEPs are estimated on the scored reference, at PSM level and again after best-per-peptide de-duplication.

### Acceptance criteria

- [ ] Context Percolator uses pyIsoPEP `q2pep` (`--calc-q-from-fdr`) and Context-MS's Percolator arguments for background training; the standard and targeted workflows are unchanged.
- [ ] Context mProphet uses Context-MS's q-value/PEP method (Gaussian decoy null, Benjamini–Hochberg q, Storey lfdr with π0 ≥ 0.05 and Silverman KDE on logit p), recomputed at peptide level, and additionally writes `P.psm.reference.txt` and `P.peptide.reference.txt` in the Context-MS format; existing mProphet outputs are kept.
- [ ] Context mProphet training preprocessing matches Context-MS (pairing check, near-constant pruning, feature exclusions, starting score, no fold cap).
- [ ] Option B: an optional diagnostic fold file makes Java mProphet use Context-MS's exact folds; with it, weights and scores agree to within floating-point precision. Default behavior without the file is unchanged. Malformed fold files fail clearly. **Superseded** in r2 (approved 2026-10-31): option B is diagnostic only; see "r2 acceptance criteria".
- [ ] Final acceptance only (CTX018-010; not a gate for earlier tasks), on Ariana's comparison file, both engines, seeds 1–10, Percolator v3.05 for both tools:
  - Peptides: median count of reference target peptides at PEP ≤ 0.01 across seeds within ±2% of the Context-MS median.
  - Scores: per run, z = (score − decoy mean) / decoy SD; over reference targets with Context-MS |z| ≥ 1, the median per-row relative difference in z within ±20%. Spearman rank correlation reported for information.
  - Context-MS `example/` data is interim and report-only. A miss triggers review of language-specific differences or of the thresholds with Ariana.
- [ ] Documentation, Java 17 verify, Until Useful review, and Ariana's final review are recorded with their actual limits.

### Approved checklist — plan r1

- [x] `CTX018-001` Report peptides at PEP ≤ 0.01 alongside the existing q ≤ 0.01 count in `ContextPercolator` (`countPeptidesPassingPEPThreshold` currently counts q-values); keep the existing count and label both. Files: `percolator/ContextPercolator.java`, `ContextPercolatorResult.java`, tests. Check: unit test with known q/PEP values.
- [ ] `CTX018-002` Add `docs/validation/ctx018_compare.py`, importing Context-MS as an installed package (no Context-MS edits). Compares row counts, features kept, weights, reference scores, and peptide counts at PEP ≤ 0.01 and q ≤ 0.01; computes the final metrics; records versions, seeds, and parameters. Also exports Context-MS mProphet folds for `CTX018-009`. Depends on: none. Check: runs on Context-MS `example/` (kept outside the repo; provenance recorded). **Superseded** by `CTX018-017` (r2, approved 2026-10-31).
- [ ] `CTX018-003` Record the starting gap: run both tools, both engines, on `example/` and (when available) Ariana's comparison file before any code change, with Percolator v3.05. Files: log only. Check: commands, versions, outputs, and metrics recorded. **Superseded** by `CTX018-018` (r2, approved 2026-10-31).
- [x] `CTX018-004` Add a `q2pep` method to `PyIsoPEPRunner` with Context-MS's arguments, and use it in the Context Percolator workflow; keep `runD2PEP` for other workflows. Files: `percolator/PyIsoPEPRunner.java`, `ContextPercolator.java`, tests. Check: command unit test; comparison on `example/`.
- [ ] `CTX018-005` Add a Context-training-only command option in `ContextPercolatorRunner.generateCommand` matching Context-MS (`--post-processing-tdc --override`, Percolator defaults for `-N`, `--maxiter`, `--trainFDR`, `--testFDR`, no `--no-terminate`). Files: `percolator/ContextPercolatorRunner.java`, `ContextPercolator.java`, tests. Check: Context and standard command tests; with v3.05, the same seed and pruned input, per-fold weights match Context-MS. **Superseded** by `CTX018-020` (r2, approved 2026-10-31).
- [x] `CTX018-006` Port Context-MS `fdr.py` (Gaussian p-values, BH, π0 over λ = 0.1–0.9 with floor 0.05, Silverman KDE on logit p, monotone PEP) to a small Context class; reuse EncyclopeDIA `BenjaminiHochberg`/`LocalFDR.estimatePi0` only where verified identical. Files: new class under `org.searlelab.context`, tests. Check: JUnit 4 against Python-computed values (q ~1e-6 relative; PEP tolerance set from observed differences and recorded).
- [x] `CTX018-007` Context mProphet: best-scoring row per peptide, then recompute q/PEP; additionally write `P.psm.reference.txt` and `P.peptide.reference.txt` (Context-MS columns), deleted and rewritten on rerun like existing outputs. Files: `io/AllWorkflowExecutorCLI.java` (`runContextMProphet`), `encyclopedia/MProphetReiter.java`, tests. Check: identity preservation, tie-breaking, target/decoy counts, existing outputs unchanged.
- [ ] `CTX018-008` Align mProphet training preprocessing with Context-MS: header pairing check, near-constant pruning, feature exclusions (resolve `ExpMass`/`protein`), starting-score preference including `main_var_Intensity` and best-feature fallback, no 500,000-row fold cap. Each change measured separately. Files: `encyclopedia/MProphetReiter.java`, `io/AllWorkflowExecutorCLI.java`, tests. Check: focused tests; comparison after each change. **Superseded** by `CTX018-025` (r2, approved 2026-10-31).
- [ ] `CTX018-009` Optional diagnostic fold file for `MProphetReiter` (TSV: `iteration`, `fold`, `position`, `id`; within-fold order preserved). Build folds with EncyclopeDIA's public `MProphetDataset(featureNames, startingScoreIndex, data)` instead of `splitKFold`. Fail on missing, duplicate, or extra ids or a wrong iteration count. Depends on: `CTX018-002`, `CTX018-008`. Check: join and malformed-input tests; on `example/`, per-iteration passing counts and averaged weights match Context-MS. **Superseded** by `CTX018-024` (r2, approved 2026-10-31).
- [ ] `CTX018-010` Option C final acceptance: seeds 1–10 per tool, both engines, Ariana's comparison file, Percolator v3.05. Check: criteria above computed by `ctx018_compare.py` and recorded with seeds and versions. **Superseded** by `CTX018-026` (r2, approved 2026-10-31).
- [ ] `CTX018-011` Documentation: README, CLI help (new mProphet outputs, q2pep switch, fold-file option, comparison Percolator version), and the record's validation sections. Check: examples match actual output names.
- [ ] `CTX018-012` Java 17 Maven `verify` and focused tests. Check: commands, results, failures, and skips recorded; pre-existing baseline failures kept distinct.
- [ ] `CTX018-013` Until Useful review: minimal diff, reuse, scientific behavior, and artifacts. Check: findings, dispositions, residual risks recorded.
- [ ] `CTX018-014` Ariana's final review. Status: not started.
- [ ] `CTX018-015` External Report `YYYY-MM-DD-CTX-018-Report.md` at a location Ariana names (cloud session). Check: full checklist and log copied verbatim and verified.
- [x] `CTX018-016` Add a comment to `src/main/java/org/searlelab/context/encyclopedia/MProphetDataset.java` stating it is dead code as of 2026-10-31 (`MProphetReiter` imports EncyclopeDIA's class) and that its q-value code multiplies by π0 twice; do not delete it. Check: comment-only diff; compiles.

### Log

#### 2026-10-31 — planning (no implementation)

- Compared Context `a57ef95` with Context-MS `d207f47` and inspected the EncyclopeDIA 6.9.23 jar with `javap`. No code run or changed. Findings: Context Percolator uses pyIsoPEP `d2pep` (Context-MS: `q2pep`) and EncyclopeDIA-derived Percolator arguments (`-N 500000`, `--maxiter`, `--trainFDR/--testFDR`, `--no-terminate`, no `--override`); mProphet q-values match (BH, no π0), PEP settings differ, and peptide-level q/PEP are not recomputed; small training-preprocessing differences. EncyclopeDIA's default Percolator is v3.01 (v3.05 bundled); Context-MS uses Percolator on PATH or the unpinned `ghcr.io/percolator/percolator:master`.
- Ariana chose option B (injected folds) as the diagnostic and option C (seeds 1–10) as final acceptance; set ±2% peptides at PEP ≤ 0.01 and ±20% standardized scores (median across seeds) as final criteria only; approved extra Context-MS-format mProphet outputs; chose Percolator v3.05; asked that the dead local `MProphetDataset.java` be commented, not deleted; accepted Context-MS `example/` as interim data. The |z| ≥ 1 row filter and report-only status of `example/` were proposed in chat and adopted with plan approval.
- Ariana approved plan r1 on 2026-10-31. Guide updated to r1; canonical plan frozen as `docs/uu-ctx-018-percolator-mprophet-parity.md` revision 1. Next: Ariana's independent agent review of the plan, then `CTX018-001`. A material change from that review returns to Ariana for a revised approval.

### r2 acceptance criteria (approved 2026-10-31; heading originally "Proposed … (pending Ariana's approval)")

These replace the r1 option B and final-acceptance criteria if r2 is approved. All other r1 criteria still apply.

- [ ] Aligned mProphet behavior runs only in a Context-only mode used by `runContextMProphet`. With the mode off, standard and targeted mProphet and `scribe-context` / `ContextMProphetExecutor` are unchanged, as default-path regression tests show.
- [ ] Option B is diagnostic only. With the fold file, record for each of the 50 iterations whether passing counts agree with Context-MS, the first iteration that diverges and its cause, and the maximum relative difference in averaged weights. The tolerance comes from observed differences and is recorded. Malformed fold files fail clearly; without the file, behavior is unchanged.
- [ ] `percolator` and `mprophet` accept `-seed` (default 1). The same seed and inputs give the same outputs as `AllWorkflowExecutorCLI --seeds`.
- [ ] Context mProphet also writes `P.rescored_features.txt` and `model/P.weights.txt`.
- [ ] Final acceptance (`CTX018-026` only; measured after every phase). Data: Ariana's comparison file, both engines, seeds 1–10 per tool, identical Percolator v3.05 binary (sha256 recorded), pyIsoPEP 0.1.4.
  - Gate 1, identity: the same reference target and decoy `id`s and counts in both tools' rescored outputs, and the same de-duplicated peptide keys.
  - Gate 2, validity: scores are finite; q and PEP are in [0, 1]; PEP never decreases as score decreases.
  - Peptides: |median N(Context) − median N(Context-MS)| / median N(Context-MS) ≤ 2%, where N = reference target peptides with PEP ≤ 0.01 at peptide level. Report Context-MS's seed min–max; if ±2% is narrower than that spread, the result goes to Ariana's threshold review. Whether to allow a minimum of ±1 peptide is open question 1 in the r2 proposal.
  - Scores: per run z = (score − reference decoy mean) / reference decoy population SD. Context-MS consensus = each row's median z over its 10 seeds. Rows = reference targets with consensus |z| ≥ 1, joined by `id`. For each Context run m = median over rows of |z_Context − z_consensus| / |z_consensus|. Pass if the median of m over the 10 runs is ≤ 0.20. Also report the Context-MS leave-one-out noise floor.
  - Report-only: Jaccard index of the PEP ≤ 0.01 peptide sets, 90th-percentile relative z difference, Spearman correlation, sign flips, q ≤ 0.01 counts, Percolator per-fold weight differences, option B per-iteration agreement.

### r2 checklist additions (approved 2026-10-31; heading originally "Proposed … (pending Ariana's approval)")

r2 phase order:
- Phase 0: `001`, `017`, `018`, `019`.
- Phase 1, Percolator: `004`, `020`.
- Phase 2, mProphet confidence: `021`, `006`, `007`, `023`.
- Phase 3, mProphet training: `022`, `024`, `025`, `016`.
- Phase 4: `026`, `011`, `027`, `012`, `013`, `014`, `015`.

r1 tasks marked "proposed superseded" above become `Superseded` only when r2 is approved. (Done 2026-10-31: `002`, `003`, `005`, `008`, `009`, `010` and the r1 option B criterion are now marked **Superseded**.)

- [x] `CTX018-017` (replaces `002`) Add `docs/validation/ctx018_compare.py`, importing Context-MS `d207f47` as an installed package (no Context-MS edits).
  - It computes the r2 gates, criteria and report-only metrics, plus noise floors; records versions, binary sha256, seeds and parameters; and writes a per-phase metrics table.
  - It exports Context-MS mProphet folds and per-iteration passing counts by wrapping `context.mprophet._split_kfold` and the fold-level passing step inside the script, not by re-implementing numpy's random generator.
  - Depends on: none.
  - Check: runs on `example/` (kept outside the repo; provenance recorded); a wrapped run gives the same weights as an unwrapped run at the same seed.
- [ ] `CTX018-018` (replaces `003`) Baseline before any code change, on `example/` and, when available, Ariana's comparison file:
  - Pin the identical Percolator v3.05 binary for both tools: extract the jar's `bin/percolator-v3-05.lin` (sha256 `3bd83c21c4065b082eb168012fd1bd1357b3ab9df8aa48108cb1e33d35c972bd` for the 6.9.23 jar) and put it on Context-MS's `PATH` as `percolator`. Pin pyIsoPEP 0.1.4.
  - Run Context-MS seeds 1–10 for both engines to get its noise floor.
  - Run Context at seed 1, or at seeds 1–10 through `AllWorkflowExecutorCLI --seeds` if an all-features file is available.
  - Repeat each tool once at a fixed seed to confirm the runs are deterministic.
  - Record the current Percolator `*.command.txt`, which settles `-Y`/`-y`.
  - Count rows per peptide in the comparison file (`example/` has exactly one row per peptide).
  - Files: log only.
  - Check: commands, versions, outputs and metrics recorded.
- [x] `CTX018-019` Add `-seed <int>` to the `percolator` (`ContextPercolatorExecutorCLI`) and `mprophet` (`ContextMProphetCLI`) commands.
  - Default 1 (`TrainingSeeds.DEFAULT_SEED`), checked with `TrainingSeeds.requireValid`, passed to `ContextPercolator.trainAndApply(..., seed)` and `runContextMProphet(..., seed)`.
  - Make parameter construction match `AllWorkflowExecutorCLI` so the same seed gives the same results from either entry point.
  - Update help.
  - Files: those CLIs, tests, README.
  - Check: invalid seeds rejected; a run without `-seed` matches the current output; on a fixture, `mprophet -seed 3` and `AllWorkflowExecutorCLI --seeds 3` give identical outputs, ignoring paths and logs. The Percolator equivalence test is skipped and recorded if the Percolator binary or pyIsoPEP is missing.
- [x] `CTX018-020` (replaces `005`) Add a Context-training-only command in `ContextPercolatorRunner.generateCommand`.
  - Training-affecting flags match Context-MS: `--post-processing-tdc --override --protein-report-duplicates`; Percolator defaults for `-N`, `--maxiter`, `--trainFDR`, `--testFDR`; no `--no-terminate`.
  - Keep the output-only flags Context needs: `--seed`, `--weights`, `--results-peptides`, `--decoy-results-peptides`.
  - Handle EncyclopeDIA Percolator options and `-percolatorModelFile` as Ariana decides (open question 2 in the r2 proposal).
  - Standard and targeted commands unchanged.
  - Files: `percolator/ContextPercolatorRunner.java`, `ContextPercolator.java`, tests.
  - Check: Context and standard command tests; with v3.05, the same seed and pruned input give per-fold weights matching Context-MS (difference recorded).
- [x] `CTX018-021` Add the Context-only mProphet mode in `MProphetReiter`, turned on only by `runContextMProphet`. Every other caller (standard and targeted mProphet, `ContextMProphetExecutor`, `scribe-context`) keeps the default path byte-for-byte. `007`, `022`, `023`, `024` and `025` act only in this mode.
  - Files: `encyclopedia/MProphetReiter.java`, `io/AllWorkflowExecutorCLI.java`, tests.
  - Check: default-path regression tests on a fixture for standard and targeted mProphet and `ContextMProphetExecutor.trainAndApplyFeature`.
- [x] `CTX018-022` In Context-only mode, rank trained models by passing count with a stable descending sort, so ties keep iteration order as in Context-MS. Today ties are broken by `LinearDiscriminantAnalysis.compareTo` (sum of coefficients) and then reversed by `Collections.reverse`.
  - Files: `encyclopedia/MProphetReiter.java`, tests.
  - Depends on: `021`.
  - Check: JUnit 4 with a tie straddling position 25 keeps the earliest iterations; default-path ordering unchanged.
- [x] `CTX018-023` Context mProphet also writes:
  - `P.rescored_features.txt`: every reference target and decoy row plus `score`.
  - `model/P.weights.txt`: `feature`/`weight`/`__bias__`, reusing the `PercolatorWeights.writeAveraged` format.
  - Both are deleted and rewritten on rerun like the existing outputs.
  - Files: `io/AllWorkflowExecutorCLI.java` (`runContextMProphet`), tests, README.
  - Depends on: `021`.
  - Check: row counts and target/decoy labels match the reference input; weights match the logged model; existing outputs unchanged.
- [x] `CTX018-024` (replaces `009`) Diagnostic fold file for Context-only `MProphetReiter`.
  - TSV columns: `iteration`, `fold`, `position`, `id`. Within-fold order preserved.
  - Folds built from `MProphetData` joined by `id` through the public `MProphetDataset(featureNames, startingScoreIndex, data)` constructor.
  - Fail on missing, duplicate or extra ids and on a wrong iteration count.
  - Without the file, behavior is unchanged.
  - This task comes before `025`, so preprocessing changes are measured with folds fixed.
  - Files: `encyclopedia/MProphetReiter.java`, tests.
  - Depends on: `017`, `021`, `022`.
  - Check: join and malformed-input tests; on `example/`, record per-iteration count agreement, the first iteration that diverges and its cause, and the maximum relative difference in weights. This is diagnostic, not a gate.
- [x] `CTX018-025` (replaces `008`) Context-owned mProphet reader and preprocessing (Context-only mode). Each step is measured separately with injected folds:
  - (a) Reuse `ContextFeatures.prepare`: header check, σ < 1e-6 pruning on the background, same drop list applied to the reference.
  - (b) Build `MProphetData`/`MProphetDataset` for background and reference. With legacy column selection, prove the result is identical to `MProphetFeatureReader.parseFeatureFile`.
  - (c) Context-MS feature selection: positional non-feature columns, case-insensitive `ScanNr`/`scan`, metadata exclusions; resolve `ExpMass`/`protein`.
  - (d) Context-MS starting-score precedence (last match wins), `main_var_Intensity`, then the best-separating feature.
  - (e) No 500,000-row fold cap.
  - (f) Fail clearly if the model and the reference differ in feature names or order.
  - Files: a new Context class (not in `org.searlelab.context.encyclopedia` without an explicit EncyclopeDIA import), `encyclopedia/MProphetReiter.java`, `io/AllWorkflowExecutorCLI.java`, tests.
  - Depends on: `021`, `024`.
  - Check: focused tests for each step, including a feature file with no named starting score; comparison after each step.
- [ ] `CTX018-026` (replaces `010`) Option C final acceptance, with the r2 gates and criteria above, run through `-seed` (`019`).
  - Check: computed by `ctx018_compare.py` and recorded with seeds, versions and binary sha256; any miss goes to Ariana's review.
- [ ] `CTX018-027` Documentation additions for r2, alongside `011`:
  - The `-seed` flag and its equivalence with AllWorkflow.
  - The Context-only mProphet mode and the workflows it does not change.
  - `P.rescored_features.txt` and `model/P.weights.txt`.
  - The handling of EncyclopeDIA Percolator options in the Context workflow.
  - The r2 metric definitions in the record's validation sections.
  - Check: examples match actual output names and help text.

### Log (continued)

#### 2026-10-31 — independent plan review and r2 proposal (no implementation)

- **Review.** Reviewed guide r1, canonical r1 and this checklist against Context `a57ef95`, Context-MS `d207f47`, pyIsoPEP 0.1.4 (PyPI wheel) and EncyclopeDIA 6.9.23 (`javap` on the jar). Ran the bundled `percolator-v3-05.lin --help`. No Context code changed or ran.
- **Verified the same in both tools:** seed coefficients; the 1e-4 range filter in LDA; strict `q < FDR`; Benjamini–Hochberg without π0; population SD for the decoy null; the 10× decoy cap; Percolator raw-weight averaging; pyIsoPEP 0.1.4 `q2pep` CLI defaults equal the Python API, with outputs aligned to the target input order; v3.05 defaults `-N` 0, `--maxiter` 10, train/test FDR 0.01.
- **Verified differences:**
  - Ties in mProphet model ranking are broken by the sum of the coefficients, then reversed.
  - `LocalFDR.estimateLocalFDR` builds `KDE(x, 1.0, 100)` with no π0 floor.
  - `MProphetFeatureReader` hard-codes name-based exclusions (adds `ExpMass`, `protein`), throws when no named starting score exists, and the reference is parsed separately with no feature-alignment check.
  - Java parses features and computes LDA scores in float32.
  - `MProphetReiter` is shared by standard and targeted mProphet and by `ContextMProphetExecutor` (`scribe-context`).
  - `ContextMProphetCLI` hard-codes seed 1, and the per-engine CLIs have no seed option.
  - `example/` has exactly one row per peptide.
- **Decisions in chat:**
  - `scribe-context` is out of scope; ScribeTwo is no longer the production path. A paper comparison of ScribeTwo vs EncyclopediaTwo should go through the aligned commands as a follow-up.
  - Approved: the H2 stable-sort fix, the H7 metric definitions, the H5 extra outputs and the H6 `-seed` flag.
- **Files.** Created `docs/uu-ctx-018-percolator-mprophet-parity.md` (r1, verbatim as Ariana supplied it) and `docs/ctx-018-canonical-r2-proposal.md`. Added this section from Ariana's `TASKS.md` copy and appended the r2 proposal above.
- **Open questions:** peptide-count floor; handling of EncyclopeDIA Percolator options; whether `q2pep` is a user-facing flag or a hard change.
- **Next:**
  - Ariana approves, revises or rejects r2.
  - The Google implementation record needs a matching r2 update (still at r1, and §1 still shows ±5%/±10%); this needs Ariana's authorization.
  - Implementation does not start until r2 is approved.

#### 2026-10-31 — r2 approved; documents updated (no implementation)

- **Approval.** Ariana approved r2 in chat ("You have my approval") after approving H2 and H7. She also authorized committing and pushing the CTX-018 planning documents to `origin/ariana/scoring`; that does not cover implementation commits.
- **Canonical plan.** `docs/uu-ctx-018-percolator-mprophet-parity.md` now holds revision 2 at the top and revision 1 below it as history. `docs/ctx-018-canonical-r2-proposal.md` is marked adopted and kept as the review record.
- **Checklist.** `CTX018-002`, `003`, `005`, `008`, `009`, `010` and the r1 option B criterion are marked **Superseded**. The r2 acceptance criteria and tasks `CTX018-017`–`027` are approved.
- **Guide.** The Google implementation record is updated to r2:
  - Status.
  - The stale ±5%/±10% text in §1, kept and annotated with the revision.
  - The r2 out-of-scope item, criteria and metric definitions.
  - Approach, components, dependencies and resolved risks.
  - Step 6 phase order.
  - A dated work-log entry and a decision-log row.
  - Validation additions.
  - Files, commands and references.
  - Open questions 1–3 and the follow-up for the paper comparison.
  - I read the guide back afterwards and checked the text. Ariana's own requirement section (§7) was left unchanged, because it is filled from her messages.
- **Commit and push.** The commit and push are recorded in the Google implementation record, not here.
- **Next:** `CTX018-001` (Phase 0). Open question 3 must be decided before `CTX018-004`, question 2 before `CTX018-020`, and question 1 before `CTX018-026`.

- [x] `CTX018-028` Context-workflow guard for EncyclopeDIA options, from Ariana's 2026-10-31 decision on open question 2. **The specifics below await Ariana's confirmation.**
  - Context still accepts and parses every EncyclopeDIA workflow option.
  - The Context Percolator and Context mProphet workflows fail with a clear error, before any training, when an option Context fixes or does not honor is set to a value that differs from the EncyclopeDIA 6.9.23 default. Values are compared after parsing, so `0.010` equals `0.01`.
  - Options that cause the error:
    - `-percolatorTrainingIterations`, `-percolatorTrainingFDR` and `-percolatorTrainingSetSize`. Context-only training uses Percolator's own defaults, and Context mProphet uses no fold cap.
    - `-percolatorModelFile`.
    - `-percolatorProteinThreshold`.
    - `-percolatorThreshold`, if it differs from `-fdr`.
  - Options that are allowed and recorded: `-percolatorVersion` and `-percolatorLocation`, which choose the binary.
  - The error names each option, its value and the default Context requires.
  - `AllWorkflowExecutorCLI` applies the check in preflight, so a run with a conflicting option fails before any workflow starts, including the standard and targeted ones that would otherwise honor it.
  - Standard and targeted workflows run on their own are unchanged.
  - Files: `percolator/ContextPercolator.java` or a small shared Context helper, `io/AllWorkflowExecutorCLI.java`, `io/ContextMProphetCLI.java`, tests, README.
  - Depends on: `020`, `021`.
  - Check: JUnit 4 for each option at its default (passes), at a non-default value (fails with the named option), and at an equivalent spelling (passes); AllWorkflow preflight fails before any output is written; standard and targeted runs on their own still accept the option.
  - **Approved by Ariana on 2026-10-31, with one change: warnings, not errors.** This supersedes the "fail with a clear error" and "fails … in preflight" wording above, which is kept as history.
    - A preflight check runs before any training or workflow starts and reports every conflicting option in one block of warnings: the option, the value given, and the default Context uses instead.
    - The Context Percolator and Context mProphet workflows then run with Context's required settings and do not apply the conflicting values.
    - In `AllWorkflowExecutorCLI`, the check runs once, before the first of the six workflows. The warning states that the standard and targeted workflows will apply the user's values while the Context workflows will not, so their settings differ in that run.
    - The existing seed-experiment rejection of `-percolatorModelFile` in `preflightExperiment` is unchanged.
    - Revised check: each option at its default (no warning), at a non-default value (one warning naming it, and Context uses its required setting), and at an equivalent spelling (no warning). The AllWorkflow warnings appear before any workflow output, and standard and targeted runs still apply the user's value.

#### 2026-10-31 — r2 open questions decided (no implementation)

- **Question 3, q2pep.** Ariana: a **hard change**. The Context Percolator workflow always uses pyIsoPEP `q2pep --calc-q-from-fdr`; there is no user-facing switch. `d2pep` stays for the standard and targeted workflows. The README needs a migration note: the Context Percolator PEP now comes from `q2pep` (`CTX018-004`, `011`, `027`).
  - Follow-up (future direction, not CTX-018): add a user option in Context (Java) to choose `d2pep` or `q2pep` for the Context Percolator workflow.
- **Question 1, peptide count.** Ariana: use **whichever is larger**. The `CTX018-026` criterion becomes |median N(Context) − median N(Context-MS)| ≤ max(0.02 × median N(Context-MS), 1 peptide).
- **Question 2, EncyclopeDIA options.** Ariana: Context accepts all EncyclopeDIA workflow options, but the Context workflows report an error when any option deviates from the defaults Context needs. Added task `CTX018-028` with a proposed specification. Two points await her confirmation:
  - "Error" means the run fails before training, not a warning.
  - `AllWorkflowExecutorCLI` fails the whole run in preflight.
- These answers settle open questions 1–3 in canonical plan r2. They are recorded here and in the implementation record; the frozen canonical plan is not edited.

#### 2026-10-31 — `CTX018-028` specifics approved; documents updated (no implementation)

- **Decision.** Ariana confirmed open question 2 with a change: conflicting EncyclopeDIA options produce **warnings, not errors**. She approved a preflight check that runs before anything else, so all conflicts are reported up front rather than appearing partway through a multi-workflow run.
- **Changes approved.** Ariana approved recording these decisions, together with the earlier q2pep (hard change, with the user option as a future direction) and peptide-count (whichever is larger) decisions, and authorized committing and pushing them to `origin/ariana/scoring`.
- **Plan.** Canonical plan r2 gains an "Approved clarification — 2026-10-31" section that records the answers to open questions 1–3. Following the CTX-016 precedent, this is a clarification within r2, not a new revision.
- **Guide.** The Google implementation record is updated: open question 2 is checked and annotated, and a decision-log row is added. The commit is recorded there after the push.
- **Next:** `CTX018-001` (Phase 0). No implementation has started.

#### 2026-10-31 — Phase 0 implementation: `CTX018-001`, `017`, `018`, `019`

Ariana asked to start the implementation with all Phase 0 tasks. The order followed the plan's dependencies: `017` (script), then `018` (baseline, before any Java change), then `001` and `019`. No commit has been made for this work.

- **Environment, with deviations from the documented setup:**
  - Only JDK 21 (`openjdk 21.0.12.1`) is installed, so builds and tests ran on JDK 21 with `-Dmaven.compiler.release=17`, which checks Java 17 language and API compatibility. A JDK 17 *runtime* was not exercised.
  - `./build.sh maven` fails on an existing bug: `readonly MAVEN_REPO` is declared twice (lines 43–44). That is outside CTX-018 and was not fixed. Maven 3.9.9 was installed by hand using the script's own URL and location (`build-cache/apache-maven-3.9.9`).
  - Python: `tools/ctx018-py` (ignored) with numpy 2.5.4, pandas 3.1.0, scipy 1.18.1, pyIsoPEP 0.1.4, and Context-MS `d207f47` installed with `--no-deps` from a clean clone. The freeze is in `tmp/ctx018/pip-freeze.txt`.
  - Percolator: the 6.9.23 jar's `bin/percolator-v3-05.lin` was extracted to `tools/ctx018-bin/` (sha256 `3bd83c21…72bd`) and put on `PATH` as `percolator` for Context-MS. Context used `-percolatorVersion v3-05`, which extracts the same bundled binary. Both logs report `Percolator version 3.05.0, Build Date Feb 18 2021`.
  - Data: Context-MS `example/` was copied to `tmp/ctx018/data/` (sha256 identical to the originals; background 53,998 rows, reference 264 rows = 132 targets + 132 decoys, one row per peptide).
  - Context requires `-f`, but its Context Percolator and mProphet paths do not pass the FASTA to the engines, so a one-entry placeholder FASTA was used. Split inputs without sidecars needed `--allow-legacy-features true`.
- **`CTX018-017` — done.** Added `docs/validation/ctx018_compare.py` with two subcommands:
  - `run-context-ms`: fresh per-seed directories plus `provenance.json`. With `--export-folds`, it writes `folds.tsv` and `iterations.tsv` by wrapping `context.mprophet._split_kfold`, `_inner_loop` and `train`.
  - `compare`: the r2 gates, the peptide criterion (max of 2% or 1 peptide), consensus-z scores, noise floors, and the report-only metrics, including Percolator per-fold weights at the same seed.
  - Check: the wrapped mProphet seed-1 run and an unwrapped run produced byte-identical weights and peptide report (passive).
- **`CTX018-018` — `example/` done; the comparison-file baseline is pending until Ariana supplies the file.**
  - Context-MS: seeds 1–10, both engines, about 4–9 s per run.
  - Context: seed 1 on the unmodified jar (`821ec25`, kept as `tmp/ctx018/context-baseline-821ec25.jar`).
  - Determinism: a repeat at seed 1 was byte-identical for both tools and both engines.
  - Current Context training command (`*.command.txt`): `-y --maxiter 3 --no-terminate -N 500000 --testFDR 0.01 --trainFDR 0.01`. This settles the `-Y`/`-y` question: Context uses `-y`. It also shows that Context trains with only 3 iterations (the round-1 recalibration cap).
  - Starting gap at seed 1:
    - Percolator: N (PEP ≤ 0.01) 21 vs Context-MS seed 1 27. Per-fold weights at the same seed differ (maximum absolute difference 53.1), as expected from the different arguments.
    - mProphet: 35 vs 35.
  - Context-MS's own seed spread:
    - Percolator: N is **0** for seeds 3, 4 and 8 and 22–28 otherwise (median 23.5). The leave-one-out z noise floor is 0.14.
    - mProphet: N = 35 for every seed; z noise floor 0.031.
  - The Percolator zeros are real. pyIsoPEP's smallest q is 0.5/(T + 0.5), where T is the number of targets above the best decoy (36–43 of 132), so the smallest `q2pep` PEP lands just above or just below 0.01. On small reference sets, the PEP ≤ 0.01 count is therefore a cliff. This needs Ariana's threshold review.
  - Neither tool reaches q ≤ 0.01 on `example/`.
  - Context-MS seed 1 shows the H2 problem on real data: kept-model passing counts 128 and 128 sit at ranks 25 and 26.
- **`CTX018-001` — done.** `ContextPercolator` reports both counts, and `ContextPercolatorResult` gains `getPEPPassingPeptideCount()`; the existing `getPassingPeptideCount()` still means q ≤ threshold. Log line example: `0 at q <= 0.01, 21 at PEP <= 0.01`.
  - The old private method was named `countPeptidesPassingPEPThreshold` but counted q-values. It is replaced by `countAtOrBelow(table, column, threshold)`.
  - Two behavior corrections, for Ariana's review:
    - A value exactly at the threshold now counts. Before, `(double) 0.01f` = 0.0099999998 excluded values of exactly 0.01, so the old q count could be one lower at the boundary.
    - A non-numeric pyIsoPEP value is now an `IOException`, not a silently skipped row (CLAUDE.md error rule).
  - Test: `ContextPercolatorCountTest` (3 tests).
- **`CTX018-019` — done.** `-seed <int>` on `percolator` (split and raw routes) and `mprophet`, parsed by the new `TrainingSeeds.parseSingle` (default 1; rejects 0, negatives, non-integers, lists and overflow).
  - `percolator -standardPercolator -seed` is rejected, because the standard workflow is out of scope.
  - The raw Percolator route gained a `runContextPercolator(..., trainingSeed)` overload. The existing signatures delegate with seed 1.
  - `ContextMProphetCLI` now builds its parameters through `AllWorkflowExecutorCLI.parseSearchParameters(SearchParameterParser.getDefaultParameters(), fdr)` (made package-private), which is how AllWorkflow builds them.
  - A bug in my first edit was caught and fixed before testing: `encyclopediaArguments()` removes `-seed` from the map, so the seed is now read first.
  - Help text and README updated.
  - Checks:
    - `ContextSeedOptionTest` (3 tests): the CLI with `-seed 3` matches the AllWorkflow step with seed 3 byte for byte; no `-seed` equals seed 1; invalid seeds are rejected before any output.
    - `TrainingSeedsTest` (+1).
    - End to end on `example/` with the new jar: without `-seed`, all Percolator and mProphet outputs are byte-identical to the `821ec25` baseline; with `-seed 3`, every output differs and `*.command.txt` holds `--seed 3`.
    - `AllWorkflowExecutorCLI --seeds 3` (status COMPLETE) is byte-identical to `percolator -seed 3` and `mprophet -seed 3` for weights, reports and rescored features. That covers the Percolator equivalence that the JUnit test does not.
- **Phase 0 end measurement** (Context seeds 1–10 via `-seed`, algorithms unchanged; `tmp/ctx018/metrics/phase0-*`):
  - Percolator: Context median N 18.5 vs Context-MS 23.5 (difference −5, tolerance 1): **not met**, and flagged because the tolerance is narrower than Context-MS's own spread (0–28). Scores: median of run medians 0.169 (limit 0.20): met, against a Context-MS noise floor of 0.14.
  - mProphet: Context median N 35 vs 35: met. Scores 0.025: met. These are interim `example/` results only.
  - Gates pass for every run.
- **Test commands:**
  - Baseline, unmodified (`821ec25`): `build-cache/apache-maven-3.9.9/bin/mvn -B -Dmaven.repo.local=build-cache/m2-repository -Dmaven.compiler.release=17 test` gave 148 tests, 3 failures, 0 errors, 6 skipped. The 3 failures already existed and are Scribe-related: `ScribeFeatureTableTest` ×2 and `ContextScribeTwoBridgeTest` ×1.
  - After the change, the same command with `tools/ctx018-py/bin` on `PATH`: 155 tests, the same 3 failures, 0 errors, 4 skipped. The 2 pyIsoPEP tests now run, and no new failures appeared.
  - Package: `-Ppackage -Dmaven.test.skip=true verify` succeeded.
- **Outputs:** everything is under `tmp/ctx018/` (ignored): runs, logs, metrics and jars. Nothing is published.
- **Next:**
  - Ariana reviews the two `001` boundary corrections and the PEP-cliff observation.
  - Run `018` on the comparison file when it is available.
  - Phase 1 (`004`, `020`) only on Ariana's instruction.
  - Nothing is committed; committing needs Ariana's authorization.

#### 2026-10-31 — Phase 0 follow-ups from Ariana's review (uncommitted)

- **PEP ≤ 0.01 jump.** Running pyIsoPEP `q2pep` exactly as Context-MS calls it (`pep_regression(method="q2pep", calc_q_from_fdr=True)`) on Context's own Phase 0 scores gives N = 0 for seeds 2, 7 and 10 and 17–26 for the others. Context-MS shows the same pattern (0 for seeds 3, 4, 8).
  - Context does not show the jump today only because it still uses `d2pep`; after `CTX018-004` both tools will.
  - Mechanism (refined): on `example/` no q-value falls below about 0.0115 (smallest q 0.0115–0.0154). Any PEP ≤ 0.01 therefore comes from the pyIsoPEP fit extending below the lowest observed q, and whether it does varies by seed in both tools.
  - Ariana: the behavior is shared and data-specific, and she is exploring it separately. Implementation continues.
  - Risk to `CTX018-026`: on a reference set like this, the number of zero seeds per tool can move the median by about 20 peptides, far more than the ±1-peptide tolerance. Check the comparison file's smallest q before relying on the criterion.
  - `ctx018_compare.py` now also reports `min_q` and `min_pep` for each run (report-only).
- **`CTX018-001` corrections.** Ariana approved counting values exactly at the threshold, which supports accepting PEP = 0.01. For a non-numeric pyIsoPEP value she asked to keep the legacy skip, so a multi-file experiment such as targeted bootstrapping keeps going when a few of many files fail, but with a warning.
  - `countAtOrBelow` now skips the row and logs `Warning: skipping a pyIsoPEP row with a non-numeric [...] value [...] (row <id>); it is not counted.`
  - Test updated: `aNonNumericValueIsSkippedWithAWarningAndTheRunContinues`. The warnings appear in the test output.
- **Environment.** The JDK limitation is the cloud container's, not the Python virtual environment's: the container has only OpenJDK 21. Tests compile with `--release 17`, which checks Java 17 compatibility; a JDK 17 runtime was not used.
- **`build.sh` (outside CTX-018, authorized by Ariana).** Fixed:
  - the duplicate `readonly MAVEN_REPO`, keeping the line that honors `CONTEXT_MAVEN_REPO`;
  - in `setup_javapot`: `local x = ...` → `local x=...` (three lines), the mismatched `[ ... } && { ... ]` test, and `-f-` → `-f`.
  - Not changed: the duplicated first `build_sif()` definition (the second definition wins, so it is harmless), and `-Dmaven.javac.skip=true`, which is not a real Maven property, so compilation runs anyway.
  - Checks:
    - `bash -n build.sh` passes.
    - `./build.sh maven` runs, using the `mvn` 3.9.11 already on `PATH`.
    - An unknown target prints the usage line.
    - `CONTEXT_MAVEN_REPO=/tmp/ctx018/m2-javapot-test ./build.sh javapot` cloned JavaPot `84a59af`, built and installed `javapot-1.0.5.jar` (89 entries, same count as `libs/`) and wrote the revision file. A second run took the cached path.
    - `./build.sh jar` and `sif` were not run.
- **Tests:** same Maven command as before, with pyIsoPEP on `PATH`: 155 tests, 3 failures (the same existing Scribe failures), 0 errors, 4 skipped.
- **Next:** Ariana authorizes the Phase 0 commit; Phase 1 on her instruction.

#### 2026-10-31 — Phase 1 implementation: `CTX018-004`, `CTX018-020`, and the Percolator part of `CTX018-028` (uncommitted)

Ariana asked to start Phase 1.

- **`CTX018-004` — done.** `PyIsoPEPRunner.runQ2PEP` runs `q2pep --cat-file … --score-col … --label-col … --target-label 1 --decoy-label -1 --calc-q-from-fdr --output …`, the arguments Context-MS passes. The arguments come from a shared `concatenatedArguments(method, …)`, so `runD2PEP`'s arguments are unchanged; a test pins both lists.
  - `ContextPercolator` now calls `runQ2PEP` for the PSM-level and peptide-level reports (a hard change, as decided).
  - The standard and targeted workflows never used pyIsoPEP, so `runD2PEP` now has no production caller. It is kept for the future `d2pep`/`q2pep` option.
  - Tests: `q2pepUsesContextMSArgumentsAndD2pepIsUnchanged`, and `runsQ2PEPAndReportsTargetsWithQValuesAndPEPs` (runs when pyIsoPEP is on `PATH`; it ran here).
- **`CTX018-020` — implemented as approved (no `--no-terminate`); one finding below needs Ariana's decision before this is marked done.**
  - `ContextPercolatorRunner.generateContextTrainingCommand` writes `--seed S --results-peptides … --weights … --decoy-results-peptides … --post-processing-tdc --override --protein-report-duplicates <pin>`. It omits `-N`, `--maxiter`, `--trainFDR`, `--testFDR`, `--no-terminate`, `-y`/`-Y` and `--init-weights`.
  - Only `ContextPercolator.train` uses it, through a new `executePercolatorTSV(…, contextTraining)` overload. The existing signature and `generateCommand` are unchanged (the existing upstream-parity test still passes).
  - Percolator 2 is rejected for Context training with a clear error.
  - Tests: `contextTrainingUsesContextMSArgumentsOnly`, `contextTrainingRejectsPercolatorTwoAndInvalidSeeds`.
- **`CTX018-028`, Percolator part — done; the mProphet part waits for `CTX018-021`/`025`.**
  - New `ContextTrainingOptions.percolatorConflicts` and `warn`. An option is a conflict when its parsed value is off the EncyclopeDIA default; `-percolatorThreshold` must also differ from `-fdr`, so `-fdr 0.05` alone does not warn.
  - Called in `ContextPercolatorExecutorCLI` (both Context routes) and once in `AllWorkflowExecutorCLI.main`, before any workflow, with the note that the standard and targeted workflows apply the values.
  - Test: `ContextTrainingOptionsTest` (2 tests: defaults, `v3-05`, equivalent spellings and `-percolatorThreshold` = `-fdr` give no warning; each conflicting option is named once).
  - End to end: `-percolatorTrainingIterations 5` gives one warning block and unchanged Context weights. In AllWorkflow the block appears once, before `[1/6]`; the standard workflow used `--maxiter 5`; the Context Percolator output is byte-identical to `percolator -seed 3`; status COMPLETE.
- **Phase 1 measurement** (`example/`, seeds 1–10, Percolator v3.05; `tmp/ctx018/metrics/phase1-percolator`):
  - Per-fold Percolator weights are **identical** to Context-MS at the same seed for all 10 seeds (maximum difference 0.0).
  - PSM-level and peptide-level reports match Context-MS for every seed: maximum score difference 1.8e-14 (floating-point); q-values and PEPs identical.
  - N per seed equals Context-MS's (27, 22, 0, 0, 25, 22, 28, 0, 26, 25): median 23.5 vs 23.5, **met**. The tolerance is still narrower than the seed spread; see the PEP-jump entry.
  - Scores: median of run medians 0.103 vs the consensus (limit 0.20), **met**. This equals Context-MS's own spread, as expected when each Context seed reproduces the same Context-MS seed.
  - Gate 1 (identity) passes. **Gate 2 (validity) fails for both tools**, as described next.
- **Gate 2 finding (needs Ariana's decision).** At PSM level, "PEP never decreases as score decreases" fails for every Context-MS run and every Context run, at the same 26 places at seed 1.
  - Every violation is inside a run of tied q-values. pyIsoPEP `q2pep` assigns PEP by rank among tied q-values in **input-row order**, and the PSM input is the unsorted rescored table, so within a tie a higher-scoring row can get a higher PEP. The largest drop is 0.249.
  - Peptide level passes, because that input is sorted by score first.
  - Context reproduces Context-MS exactly here, so this is canonical behavior, not a Context bug. The gate as approved cannot pass at PSM level with `q2pep`.
  - Options:
    - (a) Apply the monotonicity gate at peptide level only, and report PSM-level violations as report-only. **Recommended**: keeps exact parity.
    - (b) Sort the PSM table by score before `q2pep` in Context. This breaks parity with Context-MS unless Context-MS does the same.
    - (c) Raise the PSM-level behavior with the pyIsoPEP or Context-MS authors.
- **Percolator v3.01 finding (needs Ariana's decision).** With Context's default Percolator v3.01 and Context-MS's arguments, Percolator stops on `example/` ("cannot find an initial direction with positive training examples … Terminating"; `percolator` exits 2). v3.05 succeeds.
  - Run directly on the same pin:
    - v3.01 without `--no-terminate`: exit 1.
    - v3.01 with `--no-terminate`: completes, but only by "setting initial direction to the first feature" in all three splits.
    - v3.05 with or without `--no-terminate`: byte-identical weights, equal to Context-MS seed 1.
  - Options:
    - (A) Keep Context-MS's arguments as approved: data like this fails clearly on v3.01, and the user needs `-percolatorVersion v3-05`. Optionally add that hint to the error.
    - (B) Add `--no-terminate` back for Context training: v3.01 completes with the first-feature fallback, results with v3.05 are unchanged here, but it deviates from the approved r2 argument list.
    - Changing the default version is out of scope.
- **Side effect noted.** With `--post-processing-tdc`, Percolator prints no `Selecting pi_0`, so the background working file ends with `pi_0=null`. EncyclopeDIA's reader accepted it, and the run completed.
- **Tests:** `mvn … test` with pyIsoPEP on `PATH`: 161 tests, 3 failures (the same existing Scribe failures), 0 errors, 4 skipped (environment-gated integration tests). `git diff --check` is clean. The phase jar is `tmp/ctx018/context-phase1.jar`.
- **Next:** Ariana decides gate 2 (a/b/c) and v3.01 (A/B); then mark `CTX018-020` done and commit Phase 1 on her authorization. Phase 2 (`021`, `006`, `007`, `023`) starts on her instruction.

#### 2026-10-31 — Phase 1 decisions recorded (uncommitted)

- **Gate 2, decided by Ariana:** PEP monotonicity is checked at **peptide level only**; it is not necessary at PSM level. `ctx018_compare.py` now gates monotonicity on the peptide-level output and reports the PSM-level count as `psm_pep_order_violations` (report-only). The finiteness and [0, 1] checks still apply at both levels.
  - Rerun on the Phase 1 outputs: gates pass for all 10 Percolator runs, and for the legacy mProphet runs too.
  - PSM-level violations per seed are identical in both tools (26, 31, 29, 39, 25, 43, 32, 30, 45, 29), which confirms this is pyIsoPEP's tie ordering.
- **Percolator v3.01, decided by Ariana: option A.** Keep Context-MS's arguments exactly, with no `--no-terminate`. On data like `example/`, v3.01 fails clearly and `-percolatorVersion v3-05` is the remedy. Adding that hint to the error message is offered, not done. `CTX018-020` is marked done.
- **π0 side effect, explained to Ariana.** Percolator estimates and prints π0 only for its default mix-max post-processing ("Separate target and decoy search inputs detected, using mix-max method. Selecting pi_0=0.884971"). With `--post-processing-tdc` it competes targets with decoys instead ("Selected best-scoring PSM per scan+expMass (target-decoy competition): 7783 target PSMs and 6918 decoy PSMs") and never computes π0.
  - The background working file therefore ends in `pi_0=null`.
  - Neither the background file nor its π0 feeds Context's reference results: `ContextPercolator.train` discards the returned passing peptides, and reference q-values and PEPs come from pyIsoPEP. The value is not used.
- **Proposed addendum (not started, awaiting Ariana's approval).** A user option for the Context Percolator PEP method (`q2pep` default, `d2pep` optional) is small, because `PyIsoPEPRunner` already has both. It would replace the "future direction" follow-up only if Ariana adds it to CTX-018.

- [x] `CTX018-029` (addendum, approved by Ariana on 2026-10-31) `--pepMethod q2pep|d2pep` for the Context Percolator workflow, default `q2pep` (Context-MS's method). This replaces the "future direction" follow-up recorded under open question 3.
  - The option name follows the GNU convention Ariana asked for: words with `--`, single letters with `-`. Existing single-dash word options are not renamed, which would be a compatibility change.
  - `PyIsoPEPRunner` carries the method (`PepMethod`, `runPEP`); `ContextPercolator` calls `runPEP`. The CLIs build the runner with the parsed option, so no public workflow signatures change.
  - Accepted by `percolator` (split and raw routes) and `AllWorkflowExecutorCLI`. Rejected with `-standardPercolator`, invalid values are rejected before any output, and the option is not passed to EncyclopeDIA.
  - Only `q2pep` has a Context-MS counterpart; `d2pep` is tested for wiring, not parity.
  - Files: `percolator/PyIsoPEPRunner.java`, `ContextPercolator.java`, `ContextPercolatorExecutorCLI.java`, `io/AllWorkflowExecutorCLI.java`, `PyIsoPEPRunnerTest.java`, README, CLI help.
  - Depends on: `004`.
  - Checks: parse, default and invalid values; a stand-in `pyisopep` records that each method reaches pyIsoPEP.

#### 2026-10-31 — `CTX018-029` implemented; Phase 1 committed with Ariana's authorization

- **Tests:** `pepMethodDefaultsToQ2pepAndRejectsUnknownMethods` and `runPEPCallsTheConfiguredMethod` (the stand-in executable records its arguments).
- **End to end on `example/`:**
  - Without the option, outputs are byte-identical to Phase 1.
  - `--pepMethod d2pep` runs `pyisopep d2pep` for both reports (28 at PEP ≤ 0.01).
  - `--pepMethod tdc2pep` exits 2 with `--pepMethod must be q2pep or d2pep`, and no output directory is created.
  - `-standardPercolator --pepMethod d2pep` exits 2.
  - `AllWorkflowExecutorCLI --pepMethod d2pep --seeds 4` uses `d2pep` and completes (COMPLETE), with no EncyclopeDIA option warning.
- **Full suite** (pyIsoPEP on `PATH`): 163 tests, 3 failures (the same existing Scribe failures), 0 errors, 4 skipped. `git diff --check` is clean.
- **Delivery:** Ariana asked for a commit and push once the option was done ("go ahead and make a commit/push unless you need additional input"). The commit hash is recorded in the implementation record.
- **Next:** Phase 2 (`021`, `006`, `007`, `023`) on Ariana's instruction. `018` (comparison file) and the mProphet part of `028` remain open.

#### 2026-11-01 — Phase 2 implementation: `CTX018-006`, `CTX018-007`, `CTX018-023`; `CTX018-021` moved to Phase 3 (uncommitted)

Ariana asked to move to Phase 2.

- **Order change within the approved plan.**
  - The new outputs are written in `AllWorkflowExecutorCLI.runContextMProphet`, which only the Context workflow calls, so `MProphetReiter` is not changed in this phase.
  - The Context-only mode (`CTX018-021`) would therefore have no effect yet. It moves to Phase 3, where `CTX018-022`, the stable model ranking, first uses it.
  - The out-of-scope workflows were still compared against Phase 0; see "Regression" below.
- **`CTX018-006` — done.** `org.searlelab.context.mprophet.MProphetConfidence` ports Context-MS `fdr.py`:
  - Gaussian decoy null with mean and population SD; p = ½·erfc(z/√2) using commons-math3 3.6 `Erf.erfc`, already a compile dependency of EncyclopeDIA 6.9.23, so no new dependency.
  - Benjamini–Hochberg q-values without π0.
  - π0 as the median over λ = 0.1…0.9, with a floor of 0.05.
  - KDE on logit p with eps = 1e-8, using scipy's Silverman factor: bandwidth = sample SD × (3n/4)^(−1/5).
  - PEPs clipped to [0, 1] and made non-decreasing in p; ties keep input order (mergesort).
  - Edge cases: no decoys gives p = 1; decoy SD 0 gives p = 0/1 by side; zero KDE spread gives PEP 1, as Context-MS does.
  - One deliberate difference: with a single target, Context-MS stops (scipy `gaussian_kde` raises `ValueError`), while Context sets PEP = 1 and logs a warning, so a multi-file run keeps going.
  - Reference values: `src/test/resources/org/searlelab/context/mprophet/ctx018_fdr_reference.tsv`, generated by the new `docs/validation/ctx018_fdr_fixture.py` from Context-MS's own `fdr.py`. It holds 5 deterministic synthetic sets: typical, tied, extreme/underflow, small (5 + 5) and null. Seeds and the command to regenerate it are in the script.
  - Observed differences: p 7.7e-15 relative, q 7.7e-15 relative, PEP 1.6e-15 absolute. Test tolerances are p 1e-10 relative, q 1e-9 relative, PEP 1e-9 absolute.
  - `MProphetConfidenceTest` (4 tests). Two of my first hand-worked expectations were wrong (a BH tie and a π0 median); I corrected them after re-deriving.
- **`CTX018-007` and `CTX018-023` — done.** `org.searlelab.context.mprophet.ContextMProphetReports` writes four new files next to the unchanged outputs:
  - `P.rescored_features.txt`: every reference row, labels normalized to 1/−1, plus `score`.
  - `P.psm.reference.txt`: all reference rows.
  - `P.peptide.reference.txt`: the best row per peptide over targets and decoys together, with q/PEP recomputed on those rows. Peptide key: the peptide column (second to last) verbatim; ties keep file order.
  - `model/P.weights.txt`: `feature`/`weight` rows plus `__bias__`.
  - Scoring uses the background LDA in double precision on the reference feature columns named by the model. A missing column is an `IOException`, not a zero.
  - The files are deleted before a rerun, like the existing outputs.
  - The returned `MProphetResult` and the existing log line are unchanged. A new log line gives the new counts, e.g. `132 reference target PSMs, 132 peptides; 39 at q <= 0.01, 35 at PEP <= 0.01`.
  - `ContextMProphetReportsTest` (3 tests) uses a synthetic table with repeated peptides and a tie. It checks identity, the tie winner, target/decoy counts, recomputed peptide-level values that differ from PSM level, sorting by q, the weights file, and the missing-feature error.
  - README updated.
- **Phase 2 measurement** (`example/`, seeds 1–10; `tmp/ctx018/metrics/phase2-mprophet`):
  - Context mProphet median N 35 vs Context-MS 35: **met**.
  - Scores: median of run medians 0.025: **met**. Gates pass.
  - On Context's own scores, the Java q/PEP match Context-MS `fdr.py` to 3.8e-15 (q) and 6.4e-15 (PEP) at every seed.
  - The training models still differ (Phase 3), so per-seed N is 34–36.
- **Regression:**
  - For seeds 1–10, all four legacy Context mProphet files are byte-identical to Phase 0.
  - `AllWorkflowExecutorCLI --seeds 3` against the Phase 0 run of the same command: `standard-mprophet`, `target-mprophet` and the legacy `context-mprophet` files are byte-identical. In `standard-percolator` and `target-percolator` only `*.command.txt` differs, by the random temporary Percolator path and the output directory. `percolator` (Context) differs, as expected after Phase 1. Status COMPLETE.
  - Full suite (pyIsoPEP on `PATH`): 170 tests, 3 failures (the same existing Scribe failures), 0 errors, 4 skipped. `git diff --check` is clean.
- **Performance note:** the KDE is O(n²) in the number of reference targets at each level. That is fine for targeted reference sets, but would be slow for reference sets of about 10⁵ rows.
- **Next:** commit Phase 2 on Ariana's authorization; Phase 3 (`021`, `022`, `024`, `025`, `016`) on her instruction.

#### 2026-11-01 — Phase 2 delivery

- Ariana kept the single-target PEP behavior: PEP = 1 with a warning, where Context-MS stops. She authorized committing and pushing Phase 2. The commit hash is recorded in the implementation record.
- The move of `CTX018-021` to Phase 3 is recorded above. Ariana has not yet said whether she accepts it.

#### 2026-11-01 — Phase 3 implementation: `CTX018-021`, `022`, `024`, `025`, `016`, and the mProphet part of `028` (uncommitted)

Ariana asked to move to Phase 3 once Phase 2 was pushed (`abcd1e3`). The Phase 2 commit first failed `git diff --cached --check`: the decoy rows of the fixture had empty trailing fields. The generator now writes `NA` there; the test re-ran and passed before the commit.

- **`CTX018-021` — done.** Context-only mode in `MProphetReiter`:
  - `executeContextTraining(dataset, settings, threshold, seed, aa, folds)` and `applyContextModel(reference, …)` are the entry points. Only `runContextMProphet` uses them.
  - The public constructor and `executeMProphetTSV` / `executeMProphetTSVWithModel` keep the legacy path, which `ContextMProphetExecutor` (`scribe-context`) and the standard and targeted workflows use.
  - In this mode: folds are not capped (maximum fold size = the larger of targets and decoys, as Context-MS), a supplied fold plan replaces the random split, a training failure is thrown instead of logged and returned as null, and per-iteration records are returned.
- **`CTX018-022` — done.** `rankModels(models, contextMode)`: in Context mode a stable descending sort by passing count; in legacy mode `Collections.sort` + `reverse`, unchanged. `MProphetReiterContextModeTest` covers both orders with a tie at the cut.
- **`CTX018-025` — done.** `org.searlelab.context.mprophet.ContextMProphetFeatures`:
  - (a) Reuses `ContextFeatures.prepare`: header check, σ < 1e-6 pruning on the background, and the same drop list on the reference.
  - (b) Builds `MProphetData`/`MProphetDataset` through the public constructors. With EncyclopeDIA's column rules, it is **bit-for-bit identical** to `MProphetFeatureReader.parseFeatureFile`: features, starting index, ids, sequences (flanking `-.`/`.-` stripped), proteins, labels and float values. Checked on the 1,000-row fixture (`ContextMProphetFeaturesTest`) and on the full 53,998-row `example/` background (probe: 0 differing rows).
  - (c) Context-MS's columns: positional id/label/peptide/proteins, case-insensitive `ScanNr`/`scan`, and `io.ENCYCLOPEDIA_METADATA_COLS`. `ExpMass` is a feature; a non-numeric mid-table `protein` column reads as 0 and is then pruned. Labels other than 1/−1 are skipped with a warning.
  - (d) Starting score: the last of Context-MS's preferred names present, including `main_var_Intensity`; otherwise the per-training-fold best-separating feature (`MProphetReiter.bestSeparatingFeature`, a port of `_auto_main_score`).
    - **Finding:** EncyclopeDIA's reader stores the *header* position of its starting score and reads it as a *feature* position. On `example/` that is 5 (`weightedSumOfSquaredErrors`) instead of `xCorrModel` (feature 1); on the fixture, 12 (`isotopeDotProduct`) instead of `xCorrModel`. The legacy starting score is therefore wrong whenever it is used, which is only when the seed model is unusable. The Context reader uses the correct index.
  - (e) No fold cap.
  - (f) The reference dataset is built with the background's feature list. The model is applied to it through `applyContextModel`, so coefficients line up by name. The new reports also fail on a missing feature (Phase 2).
  - `runContextMProphet` now uses the reader. The rescored output is written from the pruned reference, as Context-MS writes its own.
  - **Effect of each step on `example/`:** (a), (c), (d) and (e) change nothing there. Pruning drops `charge1`/`charge4`, which both selections exclude anyway; the 15 features are identical; the seed model is usable, so no starting score is needed; and 54k rows are under the old 500,000-row cap. Their effect is covered by synthetic tests. The only change that affects `example/` is `022`: with random folds, seeds 5 and 10 changed materially, because a tie at the top-25 cut now keeps a different model; the other seeds changed only in the last digits, from averaging order.
- **`CTX018-024` — done.** `org.searlelab.context.mprophet.MProphetFoldFile` and `mprophet --foldFile <folds.tsv>` (GNU style; README and help updated).
  - Joins by id; keeps within-fold order (targets then decoys, in file position order).
  - Fails on duplicate background ids, a wrong header, non-integer fields, a fold other than 0/1, an iteration outside 0..49, an unknown id, an id twice in one iteration, a repeated position, gaps in positions, a missing iteration, and incomplete coverage (naming a missing id).
  - When given, `model/P.iterations.tsv` records each iteration.
  - `ctx018_compare.py compare-folds` compares it with Context-MS's `iterations.tsv` and weights.
  - Tests: `MProphetFoldFileTest` (2 tests: join/order, and 10 malformed cases).
  - **Option B result** (`example/`, Context-MS's folds from seeds 1–3; `tmp/ctx018/metrics/phase3-folds-seed*`):
    - Seeds 1 and 3: **50/50 iterations agree**; averaged weights within 2.2e-7 and 1.6e-6 relative.
    - Seed 2: 49/50 agree. Iteration 31 has 207 vs 208 passing (one PSM at the q < 0.01 line, float32 vs float64). Weights are still within 2.1e-7 relative.
    - Seed 1 includes the 128/128 tie at ranks 25/26, so this also confirms `022`.
    - Diagnostic only, as approved.
- **`CTX018-016` — done.** Class comment on `encyclopedia/MProphetDataset.java`: unused as of 2026-10-31, kept at Ariana's request, π0 applied twice in its q-values, and the same-package shadowing caution. Comment-only; compiles.
- **`CTX018-028`, mProphet part — done.** The `-percolatorTrainingSetSize` warning now also says there is no Context mProphet fold cap. The `mprophet` command accepts no EncyclopeDIA options, so the warning can only come through `AllWorkflowExecutorCLI`.
- **Phase 3 measurement** (`example/`, random seeds 1–10; `tmp/ctx018/metrics/phase3-mprophet`): Context mProphet median N 35 vs Context-MS 35, **met**; scores 0.025, **met**; gates pass.
- **Regression:** `AllWorkflowExecutorCLI --seeds 3` against the Phase 2 run: `standard-percolator`, `target-percolator`, `standard-mprophet`, `target-mprophet` and the Context Percolator output are byte-identical (excluding `*.command.txt` temporary paths). Status COMPLETE.
- **Not aligned, found during Phase 3 (outside the approved items; neither changes `example/`):**
  - Java compares the seed model on the test fold even when the seed is unusable; Context-MS does only when usable.
  - When LDA inversion fails, EncyclopeDIA throws, while Context-MS ends that inner loop.
- **Tests:** full suite with pyIsoPEP on `PATH`: 179 tests, 3 failures (the same existing Scribe failures), 0 errors, 4 skipped. `git diff --check` is clean.
- **Next:** commit Phase 3 on Ariana's authorization. Phase 4: `026` needs Ariana's comparison file (and `018`); `011`/`027` docs, `012` verify, `013` Until Useful review, `014` final review, `015` Report.

#### 2026-11-01 — Phase 3 follow-ups from Ariana's review (uncommitted)

- **EncyclopeDIA starting-score bug.** Explained to Ariana: the fix is Context-only. The Context mProphet workflow uses `ContextMProphetFeatures`, which gives the starting score's position in the feature list. EncyclopeDIA 6.9.23 is unchanged, so its reader still has the bug for the standard and targeted mProphet workflows and `scribe-context`, which are out of scope. An upstream fix would be a one-line EncyclopeDIA change and is not planned here.
- **Preprocessing.** Ariana: the steps are needed for other data, even though `example/` is an ideal case where they change nothing.
- **Training alignment, approved by Ariana ("persist training similarly to Context-MS").** All in Context-only mode in `MProphetReiter`; the legacy path is unchanged.
  - The seed model is compared on the test fold only when it is usable (finite, not all zero), as Context-MS's `_is_usable` check does. Iteration records then leave the seed column empty.
  - An iteration whose training fold has no targets or no decoys is skipped, as Context-MS's `continue` does. Before, EncyclopeDIA's LDA threw an `ArrayIndexOutOfBoundsException` there and the run stopped.
  - The inner loop ends without a new model when there are no positives or no decoys to build from, as Context-MS's `build_lda` returning `None` does, so training persists instead of stopping. A singular covariance was probed separately: EncyclopeDIA returns NaN coefficients, which the existing NaN check already handles the same way as Context-MS.
  - With no kept model and an unusable seed model, training stops with a clear error, as Context-MS raises, instead of averaging an all-zero seed model.
  - Iterations without a model now record empty fields, which matches Context-MS's export for `compare-folds`.
  - Tests in `MProphetReiterContextModeTest` (3 added; ids are EncyclopeDIA-style because `PercolatorPeptide` parses them): an unusable seed is never compared; no usable model and no seed raise an error; a training fold without decoys is skipped while the other iterations train.
  - On `example/`, nothing changes, since the seed model is usable and every fold has decoys: random seeds 1–10 and fold-injected seeds 1–3 give weights and iteration records byte-identical to the earlier Phase 3 runs.
- **Tests:** full suite with pyIsoPEP on `PATH`: 182 tests, 3 failures (the same existing Scribe failures), 0 errors, 4 skipped. `git diff --check` is clean.
- **Next:** commit Phase 3 on Ariana's authorization.

#### 2026-11-01 — `CTX018-018` comparison data: library downloaded, feature-scoring probe (log only)

- **Attempt:** fetch Ariana's HeLa comparison inputs into the cloud container (outside the repo, `/home/user/ctx018-data/`).
- **Result:**
  - After Ariana allowed `drive.usercontent.google.com` in the environment's network settings, the library downloaded: `Human_UP000005640_9606_entrapment.fasta.trypsin.z1-6_nce33_DIA.dlib`, 5,163,610,112 bytes (matches the Drive size).
  - It opens read-only as SQLite: 3,875,793 entries; built with JChronologer at NCE 33, default charge 3, charges 1–6, decoys added.
  - The uploaded `.dia`, assay `.txt` (155 targets + 155 decoys), entrapment assignments and FASTA were copied to `/home/user/ctx018-data/run/`. The copies' sha256 values match the uploads.
- **Probe:** a 300 s `features` run with Context's defaults (`--prediction-nce 27`, default charge 2, DIA adjustment on). It was stopped by `timeout`, exit 124.
  - JChronologer runs on this CPU (4 cores, 15 GB RAM).
  - `ContextWorkingLibraryPreparer` predicts every library precursor and a matched decoy: up to 7.75 M requests.
  - 336 k requests took 203 s (about 1,650/s), so prediction alone would take about 80 min.
  - The store holds about 0.6 kB per spectrum, so about 4.7 GB in all, plus the working library.
  - Free disk is 16 GB after the download, so the margin is a few GB.
  - The interrupted run left `.context.lock`/`.context.inprogress` files in the scratch directory; the documented next-run recovery applies.
- **Open for Ariana:**
  - Use her own feature files (via Drive, now reachable), or generate them here (about 1.5–2 h, tight disk).
  - If generating here, which prediction settings: Context's defaults (NCE 27, charge 2) or the library's (NCE 33, charge 3)?
  - Either gives valid input for the parity comparison, since both tools read the same feature files. Her setting is what matches her pipeline.
- **Next:** on Ariana's choice, produce or download the background/reference feature files and pair sidecars, then run the `CTX018-018` comparison-file steps.
