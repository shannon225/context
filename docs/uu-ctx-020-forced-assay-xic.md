# CTX-020 — Forced assay XIC extraction: Until Useful plan, approved r5

- **Status: Approved by Ariana on 2026-11-16; frozen r5.** Approval covers the r5 plan and implementation checklist. No implementation started at this handoff. Keep this canonical plan unchanged during the implementation/review loop; later material changes require renewed approval.
- **Google implementation record:** https://docs.google.com/document/d/1h2OXg2m2EvRqcr8FEKG4MQvB-xdnbjReCI3TC-yJqa8/edit (r4 decisions plus approved r5 plan; approval recorded 2026-11-16).
- **Checklist/log:** root TASKS.md, CTX-020. This r5 plan supersedes conflicting r1–r4 proposals below; preserve those sections as historical evidence.
- **Branch:** ctx020. Local branch is one existing planning commit behind origin/ctx020 (683a5d6); no pull/rebase performed. Read its status-name/history-only diff; preserve that history alongside newer decisions. No Git delivery authorized.

## Intent

- Account for every effective assay row, target and entrapment, with DETECTED, NOT_DETECTED or NO_REFERENCE. DETECTED means scored before competition, not passing FDR or winning TDC. Execution errors remain errors.
- Search the DIA file for background and some reference candidates, then force XIC extraction/scoring for reference candidates not successfully scored within their original schedule. Existing TDC selects retained features; do not rescue competition losers or introduce another competition method.
- Keep reference rows out of discriminant training. Apply the background-trained static model to retained reference features through the current confidence-engine paths. A DETECTED loser can have no retained output feature or downstream q-value/PEP.
- Use only in-window scans for reference extraction, local feature calculation and peak integration. Full-run score statistics may still supply the existing E-value null; do not use out-of-window peak signal to rescue a reference candidate.

## Current understanding and inputs

- ContextFeatureScorer.scoreFeaturesForContext prepares a shared predicted library, calls EncyclopediaTwo.generateFeatureFile, then partitions retained features. Current raw feature TSV and SearchPairSidecar contain winners only.
- EncyclopediaTwoPointOneScoringTask has a pre-trim candidate callback but skips candidates with too few quantitative fragments; its best-weak-candidate fallback is commented out. EncyclopediaTwo also skips stripes with fewer than ten scans. These shortcuts cannot determine NOT_DETECTED for assay rows.
- Partitioning uses one best feature per sequence/charge. The paired path uses findMatchingPairedTargetWindow; changing only findMatchingMassListWindow would miss that route. Reconcile by assay row identity before global precursor collapse.
- Reuse MSRawJava StripeFileInterface.getPrecursors/getStripes, ChromatogramExtractor, TransitionRefiner, existing feature scorer/result writer, predicted-decoy relationships and SearchPairSidecar. No dependency or confidence-engine algorithm changes.
- Input root: /Users/arianashannon/Documents/GitHub/context/tmp/ctx020-inputs. examples/ contains seeds 23–27; failed_ex/ contains 1/2/22/28. FASTA/library downloaded and checked. Failed runs have empty feature files/in-progress markers, no logs; do not diagnose their cause from those outputs.
- Supplied cache: prediction-cache/scribetwo-jchronologer-nce27-z2-dia.dlib; 179606 spectra; metadata profile matches Context/EncyclopeDIA 6.9.23/JChronologer 1.0.10, NCE27, reference charge2, DIA adjustment true. Working library astral_1mz_hela_b72cf9e112a7 has 89803 PAIRED assignments. SQLite quick_check and companion hashes passed. Actual Context identity/coverage reuse is still unrun.
- Docker is local Linux/x86_64. Java17 image can read the inputs via a read-only /inputs bind mount. No Context image built. About 70 GiB free at this check; build/runtime peak not measured.

## Desired direction and scope

- Automatic for assay runs; no new enable flag. Preserve non-assay behavior. Standard/TDC algorithms, predictor models/settings, CLI input syntax, numeric feature columns and pairing-file schema remain unchanged.
- New headless xic package owns assay-row identity, reference availability, scan coverage, trace extraction, candidate bookkeeping and detection-report generation. Keep visualization separate as already approved; PDF QC is hand-run, never automatic during search.
- Scope includes narrow Context-only changes to ContextFeatureScorer, ContextEncyclopediaScoringFactory, EncyclopediaTwo, EncyclopediaTwoPointOneScoringTask, ContextFeatureCacheIdentity, and reference-preparation integration only where assay-request coverage requires it. These additions to the old scope require checklist approval; no dependency modifications.
- Keep modified sequence, charge, scheduled m/z, RT bounds and assay isDecoy distinct. Carry stable one-based assay-row identity internally; never merge distinct scheduled windows. The proposed report preserves every original effective-assay row, including duplicate rows.

## Implementation approach

- **CTX020-012 — approve/freeze.** Align this plan, Google record and TASKS.md; obtain approval before implementation. Record revision/date. No commits or pushes.
- **CTX020-013 — dependency/API probe.** Confirm RT units, extraction flags, normalization, minimum-point behavior and finite feature behavior with existing IL2A fixtures. Verify how to build a task result for weak/zero signal and short windows using supported APIs. A computation that cannot be performed safely is a processing error, not NOT_DETECTED. Log any need for material scientific changes and pause.
- **CTX020-014/024 — assay extraction/results.** Create AssayXic and AssayXicExtractor. Read only the original scheduled interval, including exact endpoints, and applicable precursor isolation ranges. Missing MS1 alone is not NOT_DETECTED. With a usable reference and any applicable in-window MS2 scans, attempt feature scoring, including 1–3 good fragments or no positive measured fragments. Preserve all traces and quality flags; do not add a minimum-good-fragment gate.
- **Reference lookup.** Resolve exact modified sequence/charge against prepared/reference spectra and compatible prediction store. For missing references, request through the existing prediction preparation APIs without inventing protein assignments or changing decoy generation. Preserve source identities and matched-decoy associations. Unsupported input, failed/no prediction, no scoring-compatible reference ions, or invalid reference arrays produces NO_REFERENCE with a specific reason. Invalid run-level library/cache/runtime remains an error. No silent empirical/predicted substitution. Validate equal nonempty arrays, finite positive m/z, finite nonnegative intensities, at least one usable positive scoring ion, and consistent precursor identity; low fragment counts are allowed.
- **CTX020-032 — pre-competition tracking.** Extend the existing diagnostic callback path with bounded per-assay-row bookkeeping independent of the optional candidate TSV. Record score success, original window, candidate identity, source (search/forced), and retained-feature association. Observe before trim(1), and count weak fallback candidates only after valid features exist. Do not label a TDC loser unscored or force it again merely because it lost. Concurrency must not change ordering/ties.
- **CTX020-030 — RT boundaries.** Replaces old CTX020-022 apex-only approach. For scheduled candidates and their matched search decoys, constrain all local feature/refinement/integration scans to the row's original window. Keep the existing full-run E-value score population. A searched reference candidate with out-of-window integration is not reusable; recompute inside the window, replacing its assay candidate rather than adding a second competitive chance. Background processing remains unchanged. Test signal just outside either boundary and overlapping isolation windows.
- **CTX020-027 — search then force.** Keep the background search. Inspect pre-competition assay results; force only missing or invalid-for-window candidates, with target and matched search decoy treated symmetrically through the existing candidate-set/TDC path. An explicit assay entrapment is its own reference row, not the matched search decoy. Handle <10-scan reference windows explicitly instead of inheriting the background stripe skip. Stream results into existing serializers, preserving feature/sidecar identity and avoiding duplicate feature IDs. Do not rerun competition for a searched candidate that already lost.
- **CTX020-023/025 — partitioning.** Update paired and legacy matching, not just sequence-only lookup. Resolve reference membership separately from RT validity so out-of-window reference candidates never leak into background training. Keep existing background best-per-precursor semantics; preserve per-window candidate tracking for references. Feature IDs, pair associations and original modification identity must remain consistent. Do not fabricate zero feature values to hide undefined computations.
- **CTX020-028 — detection report.** Proposed filename: <baseName>_assay.detections.tsv beside the split feature files. Copy effective-assay columns/order verbatim; append DetectionStatus and DetectionReason. NO_REFERENCE has precedence; NOT_DETECTED means no applicable in-window MS2 scans; DETECTED means valid feature scoring before TDC. Reasons distinguish SEARCH_SCORED, FORCED_SCORED, TDC_LOSER and detailed reference/coverage failures. Link candidates internally; do not add numeric predictors or change the pairing schema. Write atomically only after processing completes; never publish an apparently complete report after a task failure. Refuse unknown existing outputs and test missing/partial report recovery.
- **CTX020-033 — cache correctness.** Version assay-processing identity; include the detection artifact and relevant provenance in completion/reuse checks. Pre-CTX020 winner-only outputs cannot prove candidate coverage and must not be silently reused as complete CTX020 runs. Reuse compatible prediction spectra/shared libraries; rebuild only run-level assay outputs in separate validation directories. Do not invalidate shared prediction profiles unnecessarily. Document legacy/direct-feature behavior; no forced extraction without raw data.
- **CTX020-026 — optional PDF QC.** Reuse extracted AssayXic in visualization/XicChartWriter and Charter.writeAsPDF, marking schedule/peak boundaries. Calibrate axes/units, keep xic free of AWT, and test headless rendering. No automatic plotting or new CLI dispatcher command.
- **CTX020-017/018/019/020/021 — validation, docs, review, handoff.** Run focused JUnit4 checks, broader Java17 package verification, cache-backed representative seeds and a background-model application check. Record actual failures/skips and limitations. Complete Until Useful review/correction and smallest-change assessment, then prepare external Downloads Report and await Ariana's final review. Do not spawn reviewers/subagents without permission. No final acceptance on Ariana's behalf.

## Validation approach

- Deterministic synthetic/IL2A tests: 7/8-column assays; modification/charge/mz/window/isDecoy identity; duplicate schedules; applicable MS2 versus MS1-only/no scans; all three statuses/reasons; all-zero/weak fragments; malformed reference data; errors propagated; deterministic tie handling; TDC loser DETECTED with no forced second chance; exact RT boundaries and overlap; no reference/background contamination; unchanged non-assay outputs; report counts and ordering; report/cache atomicity and invalidation; pairing identity and target/decoy symmetry.
- Compare direct dependency extraction/refinement against the bridge; use justified absolute plus relative tolerances (including zero signals). Verify no new numeric feature columns and no TDC algorithm change. Ensure callback observes actual scored candidates before trimming, not just search selections.
- Baseline seeds23–27: retained scored true targets 1/1/1/1/3 out of96 each. These counts are an observational baseline, not an expected sensitivity/FDR improvement. After processing, require 576 report rows per supplied assay (96 targets,480 entrapments), each with valid status/reason or explicit run failure. Investigate any target NOT_DETECTED/NO_REFERENCE against actual scan/reference coverage; do not force DETECTED to satisfy a count.
- Seeds1/2/22/28: separate output dirs, complete report if computation succeeds, otherwise preserve explicit error and logs. No assertion that their old failure must be fixed within this feature; ask before pursuing an unrelated defect.
- Verify cache-backed library identity, target/search-decoy coverage, and predictor initialization only if new predictions are needed. Run model application on retained reference features and show assay rows absent from training. Report completion/status separately from passing FDR. Full human scientific acceptance remains Ariana's decision.

## Local Docker layout and reproducible setup

- Host repository: /Users/arianashannon/Documents/GitHub/context. Host inputs/cache: tmp/ctx020-inputs; no persistent Docker environment exists yet. Bind mounts supply paths per container invocation, not through a Docker Desktop directory setting.
- Completed smoke check: existing eclipse-temurin:17-jdk container mounted host tmp/ctx020-inputs read-only at /inputs and listed /inputs/prediction-cache. Container removed after exit; source inputs/cache unchanged.
- After checklist approval, build from this checkout using the command below; the final dot selects this repository as the build context.

```bash
docker build --platform linux/amd64 -t context-ctx020 .
```

- Validation runs bind host tmp/ctx020-inputs read-only at /inputs and a fresh host tmp/ctx020-validation directory read-write at /work. Use /inputs/astral_1mz_hela.elib, /inputs/Human_UP000005640_9606.fasta and /inputs/examples/<run>.dia/assay. Copy supplied prediction-cache once into /work/prediction-cache because Context may update metadata, locks or missing spectra; point --prediction-store there. Never write into supplied inputs or reuse their old output locations. Build dependencies/model assets may download if absent; record versions and actual transfers. No paid/cloud jobs.
- Before the first baseline run, create a fresh writable run directory with only copied DIA/assay inputs (no old feature files), plus a working copy of the supplied cache. Source CLI writes beside the acquisition, so do not point --dia-folder at the read-only /inputs folder. These commands are proposed, not yet executed:

```bash
mkdir -p tmp/ctx020-validation/baseline-seed23
cp -n tmp/ctx020-inputs/examples/astral_1mz_hela_96peptides_masked23_assay.dia tmp/ctx020-validation/baseline-seed23/
cp -n tmp/ctx020-inputs/examples/astral_1mz_hela_96peptides_masked23_assay.txt tmp/ctx020-validation/baseline-seed23/
cp -R -n tmp/ctx020-inputs/prediction-cache tmp/ctx020-validation/
docker run --rm --platform linux/amd64 \
  --mount type=bind,source="$PWD/tmp/ctx020-inputs",target=/inputs,readonly \
  --mount type=bind,source="$PWD/tmp/ctx020-validation",target=/work \
  context-ctx020 features-folder \
  --library /inputs/astral_1mz_hela.elib \
  --fasta /inputs/Human_UP000005640_9606.fasta \
  --dia-folder /work/baseline-seed23 --start-seed 23 --end-seed 23 \
  --prediction-store /work/prediction-cache --candidate-diagnostics true
```

- Verify --help from the built image and capture logs/exit status before the data run. Use distinct new directories for post-change validation and other seeds; never overwrite an old baseline. Check run completion rather than interpreting empty output as success. Benchmark elapsed search/forced phases and peak memory; optimize later without changing this feature's meaning.

## Risks, decisions and reviewer focus

- Weak/short/zero-signal scoring must produce scientifically valid finite features through the existing method or expose an error; do not conceal unsupported cases as NO_REFERENCE/NOT_DETECTED. API probe may reveal a material design change requiring renewed approval.
- Distinguish pre-TDC score availability from retained rows/model/FDR outputs. Preserve loser status without creating an extra route into confidence estimation. Do not conflate entrapments with matched search decoys.
- Verify full-run E-value statistics versus window-only local features, candidate replacement without extra competitive chances, cache completeness and duplicate schedules. These are review priorities, not assumed validated behavior.
- The branch's one-commit lag is planning history only; resolve integration with Ariana before delivery. No automatic pull, commit, push or unrelated cleanup.

---

# Historical proposals and evidence (r1–r4; superseded where inconsistent above)

# CTX-020 — Forced assay XIC extraction: canonical plan, draft r4

- **Status: Draft r4, pending Ariana's checklist approval.** This is not frozen. Final human acceptance criteria remain open at Ariana's request. The r4 amendment below supersedes conflicting r3 requirements; earlier text is retained for revision history.
- Revision history: r1 (propose-phase answers), r3 (Ariana's decisions of 2026-11-16, recorded below). The r2 label belongs to the Google record only, so this document goes from r1 to r3 to keep the two revision numbers aligned. A material change after that needs a new revision approved by Ariana.
- Phase: propose, completed 2026-11-16. Answers Ariana's questions Q1–Q9 and her T1/T2 questions.
- Google implementation record: https://docs.google.com/document/d/1h2OXg2m2EvRqcr8FEKG4MQvB-xdnbjReCI3TC-yJqa8/edit (r3).
- Checklist and log: `TASKS.md`, section "CTX-020 — Forced assay XIC extraction".
- Branch: `ctx020`, created from `ariana/scoring` at `7d0f524`. Baselines: EncyclopeDIA 6.9.23 and MSRawJava 26.7.31 jars in `libs/`, unchanged.
- Evidence: `javap -c` bytecode of the jars. No source jars are available. Details below the method-call level are inferred and are checked by the test "agreement with EncyclopeDIA" (Q5.4, Q5.5).

## Decisions (Ariana, 2026-11-16)

| # | Decision |
|---|---|
| D1 | The package names `org.searlelab.context.xic` and `org.searlelab.context.visualization` are approved. |
| D2 | Plots are vector PDF, written with EncyclopeDIA `gui.general.Charter.writeAsPDF(JFreeChart, File, Dimension)` or `writeChartGridAsPDF`. JFreeChart and OpenPDF are already transitive dependencies, so there is no dependency change. |
| D3 | Plots are for occasional QC only. They are produced by a hand-run entry point, `XicChartWriter.main(raw, assay, library, outDir, [peptides...])`, run with `java -cp context.jar org.searlelab.context.visualization.XicChartWriter ...`. It is not added to Context's CLI dispatcher and is never called from scoring. |
| D4 | Scope: EncyclopeDIA is forced to extract **and score** every assay row at its own m/z, charge and RT window. Those scores enter the discriminant as reference features. No new XIC feature columns are invented. |
| D5 | Scheduled peptides may only be detected within their assay RT window (see "RT-window restriction"). |
| D6 | Detection status is a reported label, not a filter. Two statuses for now: `NO_SCANS` and `DETECTED`. `NOT_DETECTED` is a limitation and a future expansion. |
| D7 | Assay rows never train the discriminant. The background-trained model scores them. |

## Intent

In PRM, the instrument acquires every assay entry (m/z, z, RT window) whether or not the peptide is present. Context should do the same:

- Force-extract the MS1 precursor XIC and the MS2 XICs of library-predicted fragments for **every** assay row, inside its RT window.
- Refine which transitions to use.
- Make the results available to `ContextFeatureScorer`.
- Render the chromatograms **in Java**.

## T1 and T2 (Ariana's questions about the draft tasks)

- **T1 (fixtures).** Context already has usable test inputs under `src/test/resources/org/searlelab/context/io/`: `IL2A_GPFDIA_0combined_masked0_assay.txt` (8 columns with isDecoy, 0.5-minute windows), the matching `.dia`, and `IL2_and_IL15_Combo.elib`.
  - T1 is dropped as a standalone task. Those files are reused.
  - A tiny 7-column copy is added only if a test needs the user form.
- **T2 (parser).** `IsolationWindowReaderTest` covers only the 8-column form. The 7-column path (`columns.length > 7 ? … : false`) is untested.
  - T2 is not a separate task. One small test is added only if CTX-020 depends on the 7-column form.

## Q1. How EncyclopeDIA performs XIC

`utils.massspec.ChromatogramExtractor` provides static, stateless methods.

**`extractPrecursorChromatograms(MassTolerance, double mz, byte z, List<Spectrum> ms1, boolean smooth, boolean subtractBackground)`**
- Isotopes are {−1, 0, +1, +2}. Their m/z comes from `MassConstants.getChargedIsotopeMass`.
- For each MS1 spectrum, it sums the peaks within tolerance (`MassTolerance.getIntegratedIntensity`) and records a point at RT/60.
- It returns one `XYTrace` per isotope.
- Optional `SkylineSGFilter.paddedSavitzkyGolaySmooth` and `BackgroundSubtractionFilter.backgroundSubtractMovingMedian`.

**`extractFragmentChromatograms(MassTolerance, Ion[], List<Spectrum> ms2, Float targetRT, GraphType, boolean smooth, boolean subtractBackground)`**
- If `targetRT` is non-null, it keeps only ions with intensity > 0 in the spectrum nearest that RT. A null `targetRT` keeps all ions.
- It then applies the same per-ion integrated intensity as for precursors.
- It returns a `HashMap<Ion, XYTrace>`.

**Where the spectra come from**
- The caller supplies the spectra. In the GUI peptide tool, `gui.dia.PeptideExtractingBrowserPanel.resetPeptide(seq, charge)`:
  - predicted ions come from `PecanOneFragmentationModel.getUnitSpectrum`;
  - MS1 comes from `StripeFileInterface.getPrecursors(rtMin, rtMax)`;
  - MS2 comes from `getStripes(precursorMz, rtMin, rtMax, sqrt)`;
  - tolerances come from `SearchParameters`.
- `ChromatogrindrPanel` and `DIABrowserPanel` use the same extractor.
- Context already makes the same reads: `TargetedBootstrapper.java:484,508` and `EncyclopediaTwo.java:397,440`.

## Q2. How MSRawJava performs XIC

- MSRawJava does not perform XIC. It has no XIC or chromatogram class.
- It provides the data access: `io.StripeFileInterface` (`getPrecursors`, `getStripes`, `getRanges`, `getTICTrace`) and `model.Spectrum/PrecursorScan/FragmentScan/Range`. RT is in seconds.
- Plan: MSRawJava for reads, EncyclopeDIA for extraction and refinement. Neither is reimplemented.

## Q3. Visualization package

Yes. Two new packages:

- **`org.searlelab.context.xic`**: `AssayXicExtractor` (a bridge to EncyclopeDIA and MSRawJava) and the `AssayXic` record. Headless, with no AWT or JFreeChart imports.
- **`org.searlelab.context.visualization`**: `XicChartWriter`, which renders `AssayXic` to **PDF** with JFreeChart 1.0.19, through `Charter.writeAsPDF` (D2). It is run by hand for QC (D3).
  - JFreeChart is already on the classpath transitively through EncyclopeDIA's pom, along with jsvg and openpdf, so there is **no dependency change**.
  - Each peptide gets a precursor panel and a fragment panel, with the RT window and the refined peak boundaries marked.
  - It works headless. Surefire already sets `java.awt.headless=true`.
  - EncyclopeDIA's `TransitionRefiner.getChartPanels` and `gui.general.Charter` are reference implementations. They build Swing panels.

## Q4. Transition refinement

Reuse `algorithms.quantitation.TransitionRefiner.identifyTransitions(...)`. Context already calls it in `encyclopedia/EncyclopediaTwoPointOneScoringTask.java:364`.

**`extractMedianChromatogram(apexRT, chromatograms, rts, Optional<float[]> median, boolean adjustInferredRTBoundaries, int minPoints, float expectedPeakWidth)`**
1. No fragments: it returns an empty result.
2. If a median trace is supplied, it uses it and skips building one.
3. Otherwise it normalizes each fragment trace (`General.normalize`), then takes the per-scan median across fragments (`QuickMedian`). Missing values count as 0.
4. Peak range (`getIndexRange`): start at the median's maximum and walk outward. Stop when:
   - the signal is below 1% of the apex (`MINIMUM_THRESHOLD_PERCENTAGE`); or
   - the trace turns uphill once it is below half the apex and farther from the apex than max(0.2 × expected peak width, 2.1 × the median scan spacing). The minimum-points setting is honored.
5. Depending on the `adjustInferredRTBoundaries` flag, a range that misses the expected apex is re-centred on the scan nearest that apex.
6. It normalizes and background-subtracts the fragments within the range, then recomputes the median and the range.

**`identifyTransitions`**
- It correlates each fragment with the median inside the range.
- A fragment with r ≥ 0.9 is quantitative.
- If fewer fragments pass than `getMinNumOfQuantitativePeaks`, it uses fragments with r > 0.5, up to `getNumberOfQuantitativePeaks`. 0.75 is the identification threshold.
- It integrates each fragment with a background estimate.
- It returns `TransitionRefinementData`.

**Proposed rule**
- Report every extracted fragment with its correlation and its quantitative flag. "Selected" means quantitative.
- Ariana's target of 3–5 fragments maps to the `SearchParameters` minimum and number of quantitative peaks. The defaults are still to be confirmed.
- The apex RT passed in is the assay RT centre (or the library RT).
- No new thresholds are invented.

**Not confirmed from the bytecode**
- Whether `General.normalize` scales to a maximum of 1 or a sum of 1.
- Which value of the flag turns on step 5.
- How the minimum-points rule widens the range.

## RT-window restriction (D5)

- **Where:** Context's copy of the scoring task, `encyclopedia/EncyclopediaTwoPointOneScoringTask.java`, around lines 297–335. Every scan gets a score, and the scans are then walked from best to worst to pick the apex. The set `takenScans` holds scans that may not be the apex. EncyclopeDIA already uses it to block the library RT when `skipLibraryRetentionTime` is set.
- **Change:**
  - For an entry on the assay list, add every scan outside [rtMin, rtMax] to `takenScans` before the apex walk.
  - Guard it so that nothing changes when no assay windows are supplied.
- **Statistics:** `EValueCalculator` is still built from the scores across the whole run. Only where the apex can be is restricted.
- **Decoys:**
  - EncyclopeDIA's matched decoy of an assay target uses that target's window.
  - Targeted Bootstrapper decoy rows use their own rows' windows.
  - This keeps target–decoy competition fair.
- **Background:** entries that are not on the assay list are scored across the whole run, as today.
- **Lookup:** modified sequence plus charge, to the window.
- **Post-check:** `ContextFeatureScorer.findMatchingMassListWindow` also requires the charge to match and the feature RT to lie inside the window. Today it matches on sequence alone. A mismatch fails loudly instead of being dropped silently.
- **Edges:** the apex must be inside the window. A peak whose integration boundaries cross the window edge is flagged, not clipped.
- **Units:** `IsolationWindowReader` stores the window in seconds (×60). `ScoredFeature` RT units must be confirmed before comparing.
- **Behavior change:** a scheduled peptide is no longer reported at an RT outside its window. The old rule (sequence-only matching, any RT) and the new rule go in the Google record.

## Detection status and populations (D6, D7)

**Statuses**
- `NO_SCANS`: no MS1 or MS2 scans in the window for that precursor.
- `DETECTED`: scans exist and a feature was scored. This does not mean a confident identification; confidence comes from the q-value. (`SCORED` is an optional alternative name, if Ariana prefers it.)

**Populations**
- The status is a label. No row is removed because of it.
- Assay (reference) rows never enter the discriminant training set. Training uses background only, as today. A test guards this.
- `DETECTED` rows are scored by the model and take part in q-value and PEP estimation.
- `NO_SCANS` rows are written to the output with their status and an empty score, q-value and PEP. They are excluded from q-value estimation, because a fabricated score would distort it.
- The counts are reported: scheduled, scored, and `NO_SCANS`.

## Q5. Tests

JUnit 4, using the existing IL2A fixtures.

1. **Every row is extracted.** The number of results equals the number of rows. Decoys and rows with no signal are included, with empty or zero traces.
2. **Identity is preserved.** Sequence with modifications, charge, m/z, rtMin/rtMax and isDecoy are unchanged.
3. **The RT window is honored.** Every trace point is in [rtMin, rtMax].
4. **Agreement with EncyclopeDIA.** The bridge's traces equal a direct `ChromatogramExtractor` call (1e-6 relative).
5. **Refinement.** The flags match a direct `TransitionRefiner` call, and there are no more fragments than predicted ions.
6. **Failures are loud.** A missing raw or library file, or an unparseable row, throws.
7. **Visualization.** `XicChartWriter` writes one non-empty PDF per requested peptide to a temp directory, headless. The test checks only that the file exists and has a size.
8. **Regression.** The existing suite and `mvn -Ppackage verify` pass. With extraction off, `ContextFeatureScorer` output is unchanged.
9. **Apex inside the window.** Every assay feature's RT is in [rtMin, rtMax].
10. **Decoy windows.** A matched decoy uses its target's window, and a bootstrapper decoy row uses its own.
11. **Background unchanged.** With no assay windows, the scoring task output is byte-identical to before.
12. **Training guard.** The discriminant training input contains no assay rows.
13. **NO_SCANS.** The row is output with empty score, q-value and PEP, is excluded from q-value estimation, and is counted.
14. **Post-check.** A charge or RT mismatch throws.

## Q6. Requirements

- **Inputs:**
  - the assay list in both forms (`IsolationWindowReader.parseMassList`, unchanged);
  - a raw or `.dia` file (`RawFiles`);
  - the predicted library (the existing prediction path).
- **Output:** one `AssayXic` per row, holding the precursor isotope traces, all fragment traces, the refinement data, and the identity. Chart files are optional.
- **Constraints:**
  - Java 17 and JUnit 4. No new dependencies.
  - Existing CLI syntax and file formats are unchanged. Any new flag is opt-in.
  - Tolerances come from `SearchParameters`.
  - Extraction is deterministic, so no seeds are needed.
  - Smoothing and background-subtraction flags are documented.
  - The raw file is read per window, not all at once.

## Q7. Limitations

- This understanding comes from bytecode. Q5.4 and Q5.5 confirm it.
- `targetRT` must be null for forced extraction. Otherwise ions absent at that RT are dropped.
- There is no correction for interference from overlapping isolation windows or co-isolated targets.
- Correlation-based selection means nothing for absent peptides. Those rows are extracted and flagged, not treated as refined.
- Fragments are predicted, not empirical.
- The small fixtures show that the code works, not sensitivity or FDR validity.
- `NOT_DETECTED` is not implemented (D6). A `DETECTED` row may still be absent. That is left to the q-value for now and is the first planned expansion. A candidate rule is fewer than N fragments at r ≥ 0.75, with N from `SearchParameters`.
- Narrow windows with few scans may not meet `TransitionRefiner`'s minimum-points rule.
- Peaks at the window edge are flagged, not corrected.

## Q8. Definition of done

The feature is done when all of the following hold:
- The acceptance criteria are met, with evidence.
- Tests Q5.1–Q5.14 pass under `mvn -Ppackage verify`.
- Ariana's file-specific check passes (implementation record, section 7; Ariana supplies it).
- The diff stays within the Q9 scope.
- The docs are updated.
- The Until Useful review reaches APPROVE.
- The Report is written and Ariana gives final acceptance.

## Q9. Side-effect safeguards

- **Allowed scope:**
  - new `src/main/java/org/searlelab/context/xic/*` and `…/visualization/*`, plus their tests;
  - a guarded call site in `io/ContextFeatureScorer.java`, plus the charge/RT post-check in `findMatchingMassListWindow`;
  - the guarded `takenScans` restriction in `encyclopedia/EncyclopediaTwoPointOneScoringTask.java` (assay entries only), plus the window lookup passed to it;
  - no CLI change. The QC entry point is a separate `main` (D3).
- **Must not change:** `IsolationWindowReader`, `IsolationWindow`, `TargetedBootstrapper`, the other `encyclopedia/*` classes and behavior for non-assay entries, the percolator and mprophet packages, `pom.xml`, existing fixtures.
- **Opt-in:** with extraction off, scorer outputs are byte-identical (Q5.8).
- **Package boundary:** `xic` has no AWT or JFreeChart imports. `visualization` depends on `xic`, never the reverse.
- **Inputs are read only.** Plots go only to a user-given or temporary directory.
- **Review check:** `git diff --stat ariana/scoring...ctx020` lists only the allowed files and the planning docs.

## Open questions (needed before freezing)

1. Answered (D1).
2. Answered (D2, D3).
3. Answered (D4, D7).
4. **Open:** which file and expected output define Ariana's acceptance check?
5. Optional: keep the status name `DETECTED`, or use `SCORED`?


## Draft r4 amendment — 2026-11-16 plan discussion

These decisions supersede conflicting r3 text above. Implementation has not started; checklist approval remains pending.

- Activation is automatic whenever an assay list is supplied. There is no extraction-off option for assay runs. Preserve behavior for runs without assay windows. Search followed by forced extraction/scoring for missing assay candidates is a candidate implementation approach, not yet a settled architecture. If selected, record runtime optimization as future work.
- With a usable reference and applicable scans, produce a scored candidate even if measured signal or fragment correlation is weak. Do not introduce an additional fragment-quality exclusion or new target-decoy competition procedure. No new numeric feature columns are proposed. Define handling of computationally invalid features explicitly during planning rather than fabricating values.
- Statuses are DETECTED (scored; does not imply passing an FDR threshold), NOT_DETECTED (no applicable scans), and NO_REFERENCE (no usable reference spectrum). NO_REFERENCE supersedes NOT_DETECTED when both conditions apply. The previous NO_SCANS name is replaced by NOT_DETECTED; the previous deferred biological non-detection classifier is not introduced.
- NO_REFERENCE includes no exact modified-sequence/charge reference obtainable from library or prediction; unsupported prediction inputs; individual prediction failure; no prediction returned; no usable positive-intensity reference fragments matching scoring ions; and structurally invalid/nonfinite reference data. Validation rules for the last category still need specification. Weak acquired signal is not NO_REFERENCE. Run-level unreadable libraries/caches and predictor initialization failures remain explicit errors, not successful per-row annotations.
- Write a separate detection report copied from the effective assay list, preserving original row order, identity, scheduling columns and isDecoy. Append DetectionStatus and DetectionReason. Include every scheduled target and entrapment row, even without a scored feature. Do not extend the scored-winner pairing sidecar to serve as the detection report. The output filename and scan-availability rule remain to be specified.
- Only scans inside the peptide's original scheduled RT boundaries may contribute to forced assay extraction/scoring, including where acquisition isolation windows overlap. This is stronger than r3's apex-only restriction and requires revisiting the proposed scoring integration before approval. Preserve full-run E-value behavior unless Ariana approves a change.
- A repeated identical modified-sequence/charge in distinct assay rows/windows remains an open identity question. Overlapping acquisition windows alone do not change the original assay schedule.
- Keep assay rows out of discriminant training. Apply the background-trained model to scorable assay reference features. Preserve existing TDC procedures.
- Seeds 23–27 supplied by Ariana are regression candidates, not final human acceptance criteria. The archive includes masked DIA files, assay lists, raw/split feature tables, pairing sidecars and entrapment assignments. Seed 22 is absent; Ariana confirms it failed. Request its search log and outputs to distinguish zero retained features from execution failure. Library and FASTA are needed to reproduce the search and validate forced processing.

### Observational baseline — seeds 23–27

Source: https://drive.google.com/file/d/1lqu3yAOxz2fVD643BvW1v3hTWL-NWRdo/view (examples.zip). Downloaded to /private/tmp/ctx020-examples.zip; inputs unchanged. Analysis: tmp/ctx020-seeds23-27/analyze.py; summary.tsv and assay-feature-matches.tsv beside it. Exact modified sequence and charge matched against search-target rows in raw features, independently checked against pairing metadata. RTinMin compared inclusively with the assay centre ± half-width. Search-target Label and assay isDecoy have different meanings: empirical entrapments are search targets.

| Seed | Scheduled targets | Retained scored target features | No retained target feature | Scored inside scheduled window |
|---|---:|---:|---:|---:|
| 23 | 96 | 1 | 95 | 1 |
| 24 | 96 | 1 | 95 | 1 |
| 25 | 96 | 1 | 95 | 1 |
| 26 | 96 | 1 | 95 | 1 |
| 27 | 96 | 3 | 93 | 3 |

Each assay has 480 entrapment rows. Respectively 2, 0, 1, 2 and 1 have retained search-target features at any RT; none are inside their scheduled windows. No scheduled true target has a retained matched-search-decoy winner. These outputs do not expose every internally scored candidate and cannot establish absence of scans or peptide signal. No extraction/search was rerun. Raw feature files for seeds 23 and 25 are byte-identical (SHA-256 55548a91a095309586231cdf22e5ec6a3d3ef7d06f0c20f2b756460e892ee109); cause is undetermined.


### Input and Google-record review — 2026-11-16

Ariana clarified that the next planning stage is the Until Useful plan phase. Her Google-record edits in sections 1, 2 and 7 were read before the attempted update: background remains searched; targets are extracted; peptides with 1–3 good fragment ions count as detected; account for each scheduled non-entrapment target (isDecoy=FALSE), while preserving entrapment rows (isDecoy=TRUE). These additions are compatible with retaining weak candidates; they do not establish a new minimum-fragment filter. The earlier prohibition on asking for final acceptance details remains respected.

The supplied Drive folder https://drive.google.com/drive/folders/1SRLViRWJNAV-zcQ5wt6Q7YaGKmSd7kBu visibly contains examples.zip, failed_ex.zip (568797986 bytes), astral_1mz_hela.elib (115466240 bytes), Human_UP000005640_9606.fasta (13778371 bytes), and the Google implementation record. Library/FASTA are now supplied; their visibility is verified, not their contents or successful use in a rerun.

Downloaded failed_ex.zip to /private/tmp/ctx020-failed-ex.zip. It contains masked acquisitions and assay lists for seeds 1, 2, 22 and 28. All four feature files are zero bytes and accompanied by .context.inprogress markers. No search logs, pairing files or completed feature manifests are included. Treat these as incomplete-output cases pending logs, not proven completed zero-hit searches. No failure diagnosis or separate fix undertaken.

Remaining planning clarification: whether NOT_DETECTED means no applicable MS2 scans in the scheduled window (recommended, since MS1-only data cannot provide the intended fragment features). Preserve row identity for repeated schedules in the design rather than merging by sequence/charge. Decide output filename and precise validity rules in the Until Useful plan.

Google synchronization remains blocked by automatic approval review; the read Google record has not been modified. Local draft r4 records the decisions and evidence pending explicit authorization accepted by the review system.


### Completion wording and runtime readiness — 2026-11-16

Ariana confirmed the completion requirement: a DETECTED, NOT_DETECTED or NO_REFERENCE indication for every reference-list peptide, rather than confident identification of every peptide in the DIA file. NOT_DETECTED means no applicable MS2 scans within the original scheduled RT window. Search first, then forced XIC extraction/scoring for missing assay reference candidates is the selected direction. Keep each effective assay row, including entrapments; do not relabel execution failures as non-detection. This resolves the earlier completion-wording question.

Readiness checked: local Intel macOS x86_64, Java 17 and Maven 3.9.9 using Java 17; bundled jars and existing build artifacts present. ContextPredictionRuntime.requirePredictionPlatform explicitly refuses fresh prediction in Intel macOS; use linux/amd64 container as README specifies. Docker CLI and Docker.app are installed, but even the authorized unsandboxed read-only docker info/image inventory cannot connect to its daemon. No daemon started, container built, or search run. The library/FASTA are supplied in Drive but not yet downloaded. Before representative validation: obtain local copies, start Docker, build the approved ctx020 checkout in a Linux image and validate predictor startup. Plan required downloads from the actual build/cache state; no system installation undertaken.

Google r4 appendix and baseline update succeeded after Ariana's explicit edit authorization; readback confirmed appendix and her section-2 edit preserved. Earlier approval-rejection history remains in TASKS.md. Completion clarification appended separately after her confirmation.
