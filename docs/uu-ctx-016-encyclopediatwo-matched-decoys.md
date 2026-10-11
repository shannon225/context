# CTX-016 — Approved canonical scope, revision 2

Approved by Ariana on 2026-09-30 after the Context-only correction in local guide r7. This revision supersedes revision 1 below. Implementation is authorized in Context only; no Encyclopedia source edits are authorized. Use Chat-model review and retain Git delivery for Ariana unless separately authorized.

## Intent and scientific boundaries

Regenerate a run-local working library, request target and decoy spectrum predictions before feature search, follow Encyclopedia's mapped-versus-shuffled decoy strategy, preserve a usable target when its predicted decoy is absent, and report a target whose spectrum is unavailable. Compete after scoring and before Context feature reporting for every population. Only winners reach Percolator and mProphet; optional retained candidates are diagnostic output. Keep empirical assay entrapments distinct from search decoys, preserve reference/background model roles, and validate peptide-level outputs without claiming FDR validity from a build alone.

## Context-only implementation

1. Build a Context-owned working-library preparer using unchanged public Encyclopedia APIs: source `LibraryFile` target metadata, deterministic `DecoyPeptideGenerator`, JChronologer prediction client, and `LibraryFile` persistence. Context may call existing types named `ScribeTwoPrecursorPredictionRequest` and `ScribeTwoPredictionSession`. If public APIs are insufficient, keep any needed adapted implementation in Context; do not edit Encyclopedia's ScribeTwo or other source classes. Record modified-sequence/charge keys, prediction settings, requested/retained pairs, target-only reasons, excluded targets, mapping coverage, source identity, and cache identity. Use bounded batches with failure isolation.
2. Retain Context's copied feature-only `org.searlelab.context.encyclopedia.EncyclopediaTwo`. Pin an unchanged Encyclopedia 6.9.23 artifact after compatibility checks, then narrowly use its existing `EncyclopediaTwoDecoyStrategy`, `MatchedDecoyAssessment`, `MatchedDecoyKey`, and `MatchedDecoyLibrary` APIs inside the Context scoring path. Return typed pair/mode metadata and counts. Do not call upstream peptide or protein FDR stages. The existing upstream feature method is package-private, so a Context-owned feature entry point is necessary under the no-upstream-edits boundary.
3. Capture optional pre-trim target/decoy candidate diagnostics in Context's copied scoring task/factory. The existing `result.trim(1)` remains the winner boundary. Diagnostics must not change scores, tie order, winners, feature TSVs, training inputs, or confidence estimates; record the selected mode in outputs and cache identity.
4. Write a search-pair sidecar keyed by each feature row's existing `id`, with upstream-compatible modified-sequence/charge pair keys, relation type, source/method/run/version provenance, eligibility, mode, and competition outcome. Join before Context partition/reduction; fail clearly on missing or ambiguous paired-mode joins, and keep explicit legacy handling for old feature files. Do not add pair metadata to numerical feature columns. The separate `TargetedBootstrapper` empirical-entrapment assignment sidecar remains in scope, including one-to-many relationships.
5. Validate strategy selection, target-only and shuffled fallback, missing-target exclusions, modified sequences/charge, duplicates/ties, both diagnostic modes, sidecar survival through Context writers and both rescorers, direct feature-file reuse, cache invalidation, and no second pairwise competition. Use JUnit 4 and small documented fixtures; compare representative scientific outputs when data are available. Keep Percolator and mProphet classes unchanged unless a proven need triggers another approved plan revision. Update README/help, review the diff, maintain TASKS.md and the implementation record, and hand off for Ariana's final review.

## Scope and delivery

All production/test/documentation edits belong in the Context repository. Build or package the unchanged Encyclopedia source only as needed to obtain and verify its 6.9.23 artifact; do not modify or publish it. Preserve existing unrelated changes and CTX-015 history. No staging, commit, push, pull request, release, or Report publication is authorized by this approval; follow the repository's separate delivery and final-review rules. TASKS.md contains the approved replacement tasks and validation log.

## Superseded historical revision 1 — retained for reference only

# CTX-016 — Approved canonical scope, revision 1

Canonical plan document: `docs/uu-ctx-016-encyclopediatwo-matched-decoys.md`

Frozen 2026-09-30 from the approved local r6 guide plus Ariana's winner-only clarification. Historical pending-approval statements in the verbatim guide below are superseded by the approval recorded in TASKS.md. Detailed interface choices still require repository verification; material changes return to Ariana.

# CTX-016 — EncyclopediaTwo matched decoys and competition

- Revision: draft r6, 2026-09-29. Status: Needs definition; approval pending.
- Source implementation record: https://docs.google.com/document/d/1u04aFpKACUNP8Qtfl463GUPGoW5WW3zGEcpELGkKPH4/edit
- Context baseline: `ariana/scoring` at `138e34f581d9623bf9d47d72ef26300effd6f7d2`.
- Encyclopedia baseline: `master` at `65d6346d2883cfda37ece0f9f2cc674bc5567899` (6.9.23 source). Context currently pins 6.6.24.
- Priority: Ariana to assign. Issue/PR: none identified.

## 1. Problem and desired behavior

Context calls its copied `org.searlelab.context.encyclopedia.EncyclopediaTwo.generateFeatureFile` and therefore misses upstream's mapped predicted-decoy selection. It partitions feature rows using an assay match or sequence reversal, which cannot reliably identify independently predicted search decoys. A separate assay entrapment relationship from `TargetedBootstrapper` is currently not emitted. This makes pair provenance and the place of target-decoy competition difficult to audit.

The desired result is a peptide-level Context workflow that regenerates the working library, requests target/decoy spectrum predictions before search, follows Encyclopedia's predicted-versus-shuffled strategy, and searches an available target alone when its predicted decoy is absent in predicted mode. It carries explicit pair identity and mode through feature partitioning and confidence estimation, and records empirical assay entrapment assignments separately. A reversible option will compare upstream peak-group winner filtering against retaining both scored candidates for Context; winner-only is the default. Competition occurs after target and decoy scoring and before `ContextFeatureScorer` reports features, for every population. Protein-level target-decoy competition and CTX-015 implementation are excluded; CTX-016 replaces CTX-015 as the active feature.

## 2. Requirements and acceptance criteria

1. Context uses the verified Encyclopedia revision through its normal pinned Maven artifact; no filesystem symlink, reflection, or source copy remains on the active EncyclopediaTwo feature-scoring path.
2. Upstream exposes a narrow public feature-generation entry point returning the feature artifact and typed target/decoy pairing metadata without invoking upstream peptide or protein FDR stages. The exact API is subject to upstream review.
3. Regenerate a working library and request target and decoy spectrum predictions before `EncyclopediaTwo` feature search. Record prediction method, requested/retained keys, and failures. Use upstream's strategy: choose all-shuffled search when mapped prediction is unavailable, unmarked, below 97.5% unique coverage, or extra decoy libraries are configured; otherwise use mapped predicted decoys and search any target lacking a mapped decoy alone. Record the selected mode and target-only count. Do not silently omit a target whose decoy prediction failed. If a target spectrum cannot be predicted or read, exclude that target and write a reasoned report.
4. A stable upstream pair/precursor key identifies each search target and decoy using modified peptide sequence and charge. A companion sidecar maps that key to the existing feature-row `id` and carries relation type, source library, prediction/generation method, run/engine version, eligibility status, and competition outcome. A narrow `TargetedBootstrapper` addition records empirical assay target-to-entrapment assignments, including one-to-many assignments, as a separate relation type.
5. Tests prove the sidecar join survives each relevant feature writer and rescorer using the existing `id`; duplicate sequences, modifications, charge, missing rows, and ties are handled explicitly. Pair fields do not enter Percolator or mProphet numerical feature matrices. The required target/decoy `Label` remains available to supervised training.
6. Peak-group winner-only filtering is selectable for an experiment and the selected mode is recorded in outputs/cache identity. The default preserves present winner-only behavior, as Ariana confirmed. The alternative must retain both candidates sufficiently to compare downstream effects.
7. Perform the target/decoy peak-group competition once, after all candidates are scored and before `ContextFeatureScorer` reports features, for reference, background, and every other population. Verify later best-per-sequence/peptide reductions do not repeat pairwise competition or lose the surviving pair identity.
8. Reference/background partitioning and direct `-background`/`-reference` reuse either load matching sidecar metadata or fail clearly when paired-prediction mode requires it. Existing legacy inputs remain readable under an explicit legacy path.
9. Focused JUnit 4 and small synthetic integration checks cover strategy selection, pair mapping, entrapment identity, both filter modes, both engines, cache reuse, and fallback/invalid inputs. Representative scientific validation compares target/decoy feature distributions, peptide-level calibration, and detection counts; a passing build alone cannot establish FDR validity.
10. CLI/help/README and provenance documentation describe inputs, output paths, mapping schema, modes, fallback policy, and migration behavior. Java 17 package verification and actual skipped checks are recorded.

## 3. Proposed approach

**Recommended.** Regenerate the working library through an upstream preparation path and add a small supported API to upstream `EncyclopediaTwo` for feature generation and typed search-pair/mode output, then bump Context's pinned dependency after packaging compatibility is proven. Reuse upstream `MatchedDecoyLibrary`, `MatchedDecoyKey`, `MatchedDecoyAssessment`, `EncyclopediaTwoDecoyStrategy`, and `EncyclopediaTwoScoringCandidate`. Adapt preparation to preserve a target with a missing predicted decoy; current `ScribeTwoLibraryPreparation` drops both spectra when either prediction is absent. Keep Context's reference/background classification in `ContextFeatureScorer`, and add only the authorized narrow `TargetedBootstrapper` assignment output. Use a companion sidecar keyed by feature `id` rather than extra numeric feature columns. Remove Context's copied `EncyclopediaTwo` only after all live callers have moved to the supported API; separately check whether the copied scoring task/factory is still needed by the less-used `scoreFeatures` path.

**Alternatives.** (A) A Context-only adapter around current upstream public APIs would avoid an upstream change, but the feature generator is package-private and an equivalent wrapper would duplicate orchestration. (B) A shared module could expose common code, but creates a larger build and release boundary than a narrow public upstream method. (C) Source linking/symlinking or reflection is brittle and does not pin a reproducible dependency.

**Inputs and likely files.** Upstream: `ScribeTwoLibraryPreparation.java` or an existing prediction client, `EncyclopediaTwo.java`, `EncyclopediaTwoPointOneScoringTask.java`, `EncyclopediaTwoDecoyStrategy.java`, matched-decoy library types, feature writer, and tests. Context: `pom.xml`, `ContextFeatureScorer`, the copied Encyclopedia scoring classes, a narrow `TargetedBootstrapper` assignment writer, and focused tests. `ContextPercolator`, `ContextMProphetExecutor`, and their rescorers are validation targets, not planned edits. Documentation and `TASKS.md` will record behavior and evidence.

**Scientific boundaries.** An empirical entrapment is an actual library peptide assigned to a reference assay, not the predicted search decoy for that peptide. Each searched entrapment may have its own predicted search decoy. Track modifications and charge, define duplicate/tie policy, and never infer a predicted pair by reversing a sequence. Preserve separate background training and reference model application. Clarify which population supplies peptide-level calibration for each engine before implementation.

## 4. Implementation plan

1. Freeze a stage-by-stage data contract: source library, regenerated working library, target/decoy prediction and persistence, selected search mode and target-only entries, peak-group competition, feature rows, assay entrapments, partitioning, model training/application, peptide confidence estimation, and reporting. Define both relation schemas, stable upstream precursor key, `id` sidecar join, cardinalities, and target-exclusion report.
2. Implement CTX-016's library regeneration without invoking CTX-015's prepared-library workflow. Propose the smallest change that retains a valid target when decoy prediction fails and reports excluded targets, plus the narrow upstream feature API and filter switch. Confirm the 6.9.23 source compiles under Java 17, package a pinned artifact, and exercise existing upstream tests. This is a material dependency change requiring approval before implementation.
3. Integrate Context with that artifact. Migrate live feature-scoring call sites, remove the active copied class, and retain or migrate the copied factory/task based on caller evidence. Keep upstream's full peptide/protein analysis out of Context's call path.
4. Emit and validate the search-pair sidecar keyed by the existing feature `id`, and separately emit empirical `TargetedBootstrapper` assignments through a narrow change. Join metadata during partitioning; validate the join against both engines' outputs and reusable feature-file entry points without planning engine-class edits.
5. Add the peak-group filter option with a legacy-preserving default, explicit provenance/cache invalidation, and deterministic tie handling. Verify that later Percolator and mProphet stages do not add pairwise competition or lose the retained identity; propose any engine edit separately if evidence shows it is required.
6. Add focused tests, small end-to-end fixtures, documentation, full package verification, and representative scientific comparisons. Review the diff and record any scientific or compatibility limitation before Ariana's final review.

## 5. Work log and experiments

- 2026-09-29: Planning only. Confirmed Context revision and upstream commit; inspected current feature generator, mapping types, strategy, partitioning, and peptide selection. Ariana confirmed winner-only default, then clarified that prediction should be requested before search, with shuffled fallback and target-only search if the predicted decoy is absent. Upstream search already has those modes. Current ScribeTwo preparation drops a pair when either prediction is absent, so preserving a target with failed decoy prediction needs a preparation-path change or a different supported preparation path. Ariana confined planned edits to scoring with a narrow `TargetedBootstrapper` exception, chose a verified `id`-keyed sidecar, selected library regeneration and target-exclusion reporting, and replaced CTX-015 with CTX-016. No Java implementation or tests run.

## 6. Decision log

- 2026-09-29, proposed: use upstream's matched-decoy mapping framework through a supported API. Reason: one source of scientific search behavior. Alternatives: duplicated Context class, symlink/reflection, larger shared module.
- 2026-09-29, proposed: separate search-pair and empirical-entrapment relation types in one metadata contract or two linked tables. Reason: their scientific meanings and cardinalities differ. Physical table layout remains open.
- 2026-09-29, confirmed by Ariana: default peak-group filter mode stays winner-only pending comparison. Reason: preserve current behavior while testing the alternate mode.
- 2026-09-29, confirmed by Ariana (supersedes earlier strict-failure choice): request predictions before Context search; allow Encyclopedia's all-shuffled fallback; in predicted mode search an available target alone when its mapped decoy is absent. Record the mode and mapping status. Exact preparation interface and missing-target policy remain open.
- 2026-09-29, confirmed by Ariana: competition occurs after target and decoy scoring and before `ContextFeatureScorer` reports features, for reference, background, and other populations. Percolator and mProphet classes are not planned edits; validate their existing processing and request a plan revision only if a specific required change is proven.
- 2026-09-29, confirmed by Ariana: regenerate the working library in CTX-016 instead of implementing CTX-015; preserve a stable upstream precursor/pair key through an `id`-keyed sidecar verified at each writer/rescorer. A narrow `TargetedBootstrapper` change is allowed for empirical entrapment assignments. Exclude a target lacking a usable spectrum with a report.
- 2026-09-29, cleanup instruction: before implementation, discard uncommitted work outside `docs/`, `TASKS.md`, and root `AGENTS.md`. Ariana explicitly preserved the modified root instructions. Inventory the affected files first and retain the allowed documents and task history. Cleanup has not been performed during planning.

## 7. Validation and test notes

Expected: matched target/decoy pairs remain reconstructable for each feature and assay entrapment assignment; toggling filtering changes only the intended peak-group output; both engines retain correct populations and peptide-level estimates; no upstream protein TDC is called. Observed: not tested; this is a planning draft. Use synthetic duplicate/modification/charge/tie and missing-mapping fixtures, then representative acquisitions for calibration and feature-distribution analysis. Record software versions, seeds, numeric tolerances, commands, failures, and skips when run.

## 8. Files, code, and references

- Current Context: `src/main/java/org/searlelab/context/io/ContextFeatureScorer.java`, `src/main/java/org/searlelab/context/encyclopedia/`, `src/main/java/org/searlelab/context/io/TargetedBootstrapper.java` (narrow assignment output only), and `pom.xml` are likely integration edits. `ContextPercolator.java`, `ContextMProphetExecutor.java`, and `ContextFeatures.java` are read-only validation references unless a separate necessity is demonstrated and approved.
- Upstream commit: https://bitbucket.org/searleb/encyclopedia/commits/65d6346d2883cfda37ece0f9f2cc674bc5567899
- Scientific context: Lin et al. 2022, https://pubmed.ncbi.nlm.nih.gov/36166314/; Debrie et al. 2023, https://pubmed.ncbi.nlm.nih.gov/36648107/. These motivate evaluation; they do not validate the proposed pipeline.

## 9. Open questions and follow-up

1. Settled direction: CTX-016 regenerates the working library rather than implementing CTX-015. The chosen upstream preparation API must retain a usable target when its decoy prediction fails and report excluded targets without usable spectra. Confirm the exact regeneration/cache inputs and output paths in the implementation design.
2. The peak-group filter switch should compare winner-only against both scored candidates, with winner-only as default. Confirm the exact output stage and CLI name.
3. Settled direction: retain the stable upstream pair/precursor key in a sidecar keyed by each feature row's existing `id`. Verify the join after every relevant feature writer and rescorer; do not add a pair-key numeric feature column.
4. Settled stage boundary: competition occurs after all target and decoy peak groups are scored, before `ContextFeatureScorer` reports features, for every population. Verify that later best-per-sequence/peptide reductions are ordinary duplicate selection and do not apply another pairwise target-decoy competition. Do not edit `ContextPercolator.java` or mProphet classes without a specific finding and Ariana's approval of a revised plan.
5. CTX-016 replaces CTX-015 as the active implementation; retain existing CTX-015 documents for history. Before implementation, remove uncommitted work outside `docs/`, `TASKS.md`, and root `AGENTS.md`, after reviewing the exact inventory.
6. Settled: if a target spectrum itself cannot be predicted or read, exclude that target with a reasoned report. A target-only search still requires a valid target spectrum.

## 10. Completion summary

Pending implementation. Record actual files, checks, scientific validation, limitations, and Ariana's final review here when complete.


## Approved clarification — 2026-09-30

Ariana approved draft r6 on 2026-09-29 and clarified on 2026-09-30: "Only winners should reach the engines". Retained target/decoy candidates are diagnostic output only. Both modes perform the same single upstream peak-group competition before Context feature reporting; only winners enter reference/background partitions and Percolator/mProphet. Diagnostic retention must not alter scores, tie ordering, winners, training inputs, or confidence estimates.

Implementation follows the r6 scope and checklist. Existing upstream preparation/search helpers remain the reuse points. Resolve routine API names and physical metadata layout from code; return any new scientific or architectural decision to Ariana. Preserve the original r6 text above as history.
