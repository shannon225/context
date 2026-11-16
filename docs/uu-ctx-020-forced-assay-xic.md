# CTX-020 — Forced assay XIC extraction: canonical plan, draft r1

- **Status: Draft, pending Ariana's approval.** This is not frozen yet. It becomes frozen for the Until Useful implementation/review loop once Ariana approves it and answers the open questions below. A material change after that needs a new revision approved by Ariana.
- Phase: propose, completed 2026-11-16. Answers Ariana's questions Q1–Q9 and her T1/T2 questions.
- Google implementation record: https://docs.google.com/document/d/1h2OXg2m2EvRqcr8FEKG4MQvB-xdnbjReCI3TC-yJqa8/edit (r2).
- Checklist and log: `TASKS.md`, section "CTX-020 — Forced assay XIC extraction".
- Branch: `ctx020`, created from `ariana/scoring` at `7d0f524`. Baselines: EncyclopeDIA 6.9.23 and MSRawJava 26.7.31 jars in `libs/`, unchanged.
- Evidence: `javap -c` bytecode of the jars. No source jars are available. Details below the method-call level are inferred and are checked by the test "agreement with EncyclopeDIA" (Q5.4, Q5.5).

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
- **`org.searlelab.context.visualization`**: `XicChartWriter`, which renders `AssayXic` to image files with JFreeChart 1.0.19.
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

## Q5. Tests

JUnit 4, using the existing IL2A fixtures.

1. **Every row is extracted.** The number of results equals the number of rows. Decoys and rows with no signal are included, with empty or zero traces.
2. **Identity is preserved.** Sequence with modifications, charge, m/z, rtMin/rtMax and isDecoy are unchanged.
3. **The RT window is honored.** Every trace point is in [rtMin, rtMax].
4. **Agreement with EncyclopeDIA.** The bridge's traces equal a direct `ChromatogramExtractor` call (1e-6 relative).
5. **Refinement.** The flags match a direct `TransitionRefiner` call, and there are no more fragments than predicted ions.
6. **Failures are loud.** A missing raw or library file, or an unparseable row, throws.
7. **Visualization.** One non-empty image is written per row to a temp directory, headless. The test checks only that the file exists and has a size.
8. **Regression.** The existing suite and `mvn -Ppackage verify` pass. With extraction off, `ContextFeatureScorer` output is unchanged.

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
- Whether CTX-020 feeds features to the discriminant is undecided (open question 3).

## Q8. Definition of done

The feature is done when all of the following hold:
- The acceptance criteria are met, with evidence.
- Tests Q5.1–Q5.8 pass under `mvn -Ppackage verify`.
- Ariana's file-specific check passes (implementation record, section 7; Ariana supplies it).
- The diff stays within the Q9 scope.
- The docs are updated.
- The Until Useful review reaches APPROVE.
- The Report is written and Ariana gives final acceptance.

## Q9. Side-effect safeguards

- **Allowed scope:**
  - new `src/main/java/org/searlelab/context/xic/*` and `…/visualization/*`, plus their tests;
  - a guarded call site in `io/ContextFeatureScorer.java`;
  - `ContextFeatureScorerCLI`, only if a flag is approved.
- **Must not change:** `IsolationWindowReader`, `IsolationWindow`, `TargetedBootstrapper`, the `encyclopedia/*` scoring tasks, the percolator and mprophet packages, `pom.xml`, existing fixtures.
- **Opt-in:** with extraction off, scorer outputs are byte-identical (Q5.8).
- **Package boundary:** `xic` has no AWT or JFreeChart imports. `visualization` depends on `xic`, never the reverse.
- **Inputs are read only.** Plots go only to a user-given or temporary directory.
- **Review check:** `git diff --stat ariana/scoring...ctx020` lists only the allowed files and the planning docs.

## Open questions (needed before freezing)

1. Are the package names `xic` and `visualization` acceptable?
2. Plot format: PNG only, or also SVG or PDF? Is it triggered by a flag on `features` or by a separate command?
3. Does CTX-020 add XIC-derived features to the discriminant, or only extract and plot?
4. Which file and expected output define Ariana's acceptance check?
