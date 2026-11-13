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

- Attempt/change: With the Google Docs connector now connected, updated the Google implementation record in place (same document and link) under a revision guard: appended "CURRENT APPROVED REVISION: r8" with sections 15–19 (problem, approach, decisions, evidence, tasks and next action); changed the intro line and the Status table cell to name r8; relabeled the r7 heading "APPROVED REVISION: r7 (extended by r8 below)". Earlier sections, including the r6 history, were not otherwise changed. Updated this file and the local guide to record acceptance, the `--prediction-store` override, and the confirmed `--allow-legacy-features` reading.
- Result: The batch applied with no errors; each of the three text replacements matched exactly once. Read back with Drive `read_file_content`: the new section, intro line, status cell, and r7 heading read as intended. Not checked: the rendered layout (no export or visual check was run), so heading and bullet styling is unverified visually. The record's existing title typo ("Featurm ,lme") and the raw asterisks in its table cells were left as they were.
- Ariana's recorded acceptance: "update this record to r8 and note my acceptance" (2026-10-11). Open items she did not address: none from the r8 design decisions. Git delivery remains unauthorized; nothing was committed or pushed.
- Next action: Begin `CTX016-018` (prediction store) when Ariana directs; run Java 17 for the final checks, since the experiments here used JDK 21.

##### 2026-10-11 — `CTX016-018` prediction store implemented

- Authorization: Ariana asked to start `CTX016-018` under accepted r8.
- Change: added `src/main/java/org/searlelab/context/encyclopedia/ContextPredictionStore.java` and `src/test/java/org/searlelab/context/encyclopedia/ContextPredictionStoreTest.java`. No existing source file was changed.
  - `open(directory, settings, clearStaleLock)` uses Encyclopedia's public `JChronologerWorkingLibrary.getPredictionCacheFile` / `openOrCreatePredictionCache` (the cache file name already separates NCE, default charge, and DIA/DDA). It reuses `ContextWorkingLibraryPreparer.Settings` as the profile.
  - A `<cache>.context-profile` file (written atomically, as in `ContextFeatureCacheIdentity`) records the store version string `Context-prediction-store-v1|Encyclopedia-6.9.23|JChronologer-1.0.10`, NCE, default charge, and the DIA flag. A different profile, or an Encyclopedia cache without a Context profile, is refused before the cache is opened.
  - `find(precursors, constants)` performs exact (modified sequence, charge) lookup through `LibraryFile.getPredictionCacheEntries(precursors, false)` and returns a map keyed by `sequence|charge`. `getMissingPredictionCachePeptides` is not used.
  - `add(predictions)` acquires Encyclopedia's write-lock row on an immediate connection, commits it, writes with `addPredictionCacheEntries`, clears the lock, and commits; an in-process failure rolls back the write and clears its own lock. A lock held by anyone else fails with host, PID, age, and operation. `open` with a lock present fails the same way unless `clearStaleLock` is set, which clears it and logs the cleared lock. Context never clears a lock on its own.
  - `DEFAULT_DIRECTORY_NAME = "prediction-cache"`; resolving the output directory and the `--prediction-store` / `--clear-stale-store-lock` options belongs to `CTX016-019` to `CTX016-021`.
- Validation (JDK 21.0.12 runtime, compiled with `-Dmaven.compiler.release=17`; Java 17 runtime not available here):
  - `mvn -B -q -Dmaven.repo.local=build-cache/m2-repository -Dmaven.compiler.release=17 -Dtest=ContextPredictionStoreTest test` — 7 tests, 0 failures, 0 errors, 0 skips. Covered: exact-precursor lookup with unstored charge and unstored peptide absent; spectra, retention time, and ion mobility read back unchanged after reopening; different NCE and DIA settings use separate caches; a changed profile is refused and the cache bytes are unchanged; an Encyclopedia cache without a Context profile is refused; a stale lock fails with host/PID/flag name and is cleared only with `clearStaleLock`; a lock held by another run stops the write and nothing is added; an empty write leaves no lock.
  - Same command with `-Dtest=ContextPredictionStoreTest,ContextWorkingLibraryPreparerTest,ContextPredictionRuntimeTest,ContextWorkingLibraryLiveTest` — 15 tests, 0 failures, 0 errors, 0 skips (live test ran real CPU prediction on Linux).
  - Trailing-whitespace check on both new files: clean.
- Residual risks / follow-ups: the store is not yet called by any workflow (`CTX016-019`). The version string is a literal and must be updated with the Encyclopedia or JChronologer pins, like the existing cache-identity literal. `ContextPredictionRuntime.requireLibraryApi` does not yet check the prediction-cache methods; consider adding them when the preparer starts using the store. Concurrent writers fail rather than wait, by the approved lock policy; parallel runs would need to revisit this. The tests use synthetic spectra; equivalence with real predictions is `CTX016-022`. Java 17 runtime execution remains for `CTX016-023`.
- Not done: no commit or push.
- Next action: `CTX016-019` (assemble the working library from the store and predict only missing keys), when Ariana directs.

##### 2026-10-11 — `CTX016-019` working library assembled from the prediction store

- Authorization: Ariana asked to start `CTX016-019` under accepted r8.
- Change (`ContextWorkingLibraryPreparer.java`, `ContextWorkingLibraryPreparerTest.java`; no other source file changed):
  - New public `prepare(source, parameters, settings, outputLibrary, assignmentFile, ContextPredictionStore store)`. Per batch it looks up exact (corrected modified sequence, charge) keys in the store, predicts only the missing requests with the unchanged bounded batches and `predictWithIsolation`, and writes the newly predicted spectra back with `ContextPredictionStore.add`. Failed predictions are reported as before and are not stored, so a later run retries them.
  - The prediction session is created lazily through a `Predictor.start()` default method, called once before the first prediction. A full store hit therefore does not load the JChronologer model or require a prediction-capable platform. A failure to start is a failed prerequisite and propagates; it is never recorded as per-peptide exclusions.
  - Decoy selection, target retention, exclusion reasons, assignment report, and library persistence are unchanged. The existing five-argument public `prepare` (no store) is unchanged and is still what `ContextFeatureScorer` calls; switching the scorer to the store is `CTX016-020`.
- Validation (JDK 21.0.12 runtime, `-Dmaven.compiler.release=17`):
  - `mvn -B -q -Dmaven.repo.local=build-cache/m2-repository -Dmaven.compiler.release=17 -Dtest=ContextWorkingLibraryPreparerTest test` — 7 tests, 0 failures, 0 errors, 0 skips. New tests: store path with an empty store matches the no-store path exactly (assignments and all target/decoy spectra, 2 targets + 2 decoys) and a full store hit predicts nothing; an overlapping library predicts only its new target and decoy; a failed decoy prediction is reported in the assignment reason, not stored, and is the only request on the retry, which then pairs; the public store entry point builds a paired library from stored predictions.
  - Broader run, `-Dtest='org.searlelab.context.encyclopedia.*Test,ContextFeatureScorer*Test,ContextFeatureCacheIdentity*Test,SearchPairSidecar*Test'` — 35 tests in 11 classes, 0 failures, 0 errors, 0 skips (includes the live CPU prediction test).
  - Real-prediction fixture check (scratch code outside the repo, IL2 fixture `src/test/resources/org/searlelab/context/io/IL2_and_IL15_Combo.elib`, settings NCE 27 / charge 2 / DIA adjustment on): current no-store `prepare`, store `prepare` with an empty store, and store `prepare` with a full store each retained 1,112 targets and 1,112 decoys. Assignment lists and assignment files were identical across all three; all 2,224 spectra were bit-identical (maximum mass, intensity, retention-time and precursor m/z differences 0.0). Working-library preparation took 3.03 s (no store, includes model warm-up), 1.90 s (empty store), and 0.31 s (full store hit, no prediction). Store file size 1.04 MB.
  - `git diff --check` clean.
- Recorded nondeterminism: with a partially filled store, only missing precursors are sent to the predictor, so they are predicted in a different batch composition than a no-store run. From the 2026-10-11 batch check this can change intensities and retention times by about 1e-5 (tolerance 1e-4). Assignments are unaffected. The fixture check above was a full-library run, so it did not exercise this case.
- Residual risks / follow-ups: the store entry point is not yet reachable from any CLI or workflow (`CTX016-020`, `CTX016-021`). `ContextPredictionRuntime.requireLibraryApi` still does not check the prediction-cache methods. A held store lock fails the whole preparation, by the approved policy. Java 17 runtime execution remains for `CTX016-023`.
- Not done: no commit or push.
- Next action: `CTX016-020` (shared working-library cache and per-run identity in `ContextFeatureScorer`), when Ariana directs.

##### 2026-10-11 — `CTX016-020` shared working library and per-run identity

- Authorization: Ariana asked to move on to `CTX016-020` under accepted r8.
- Change:
  - New `src/main/java/org/searlelab/context/io/SharedWorkingLibrary.java` (package-private). `resolve(storeDirectory, sourceName, identity, builder)` keeps one working library per source library and prediction profile in `<store>/working-libraries/<source>_<id12>.elib`, with `_predictions.tsv`, a `.context.sha256` manifest (written and verified with the existing `ContextFeatureCacheIdentity.save` / `requireMatch`), and a `.context.inprogress` marker. A blocking lock (`ContextFeatureCacheIdentity.acquireSharedLock`: an in-JVM `ReentrantLock` per path plus a `FileChannel.lock`) serializes creation, so a concurrent run waits and then reuses. A marker without a manifest is moved with any partial output to a unique `<name>.failed-*` directory and rebuilt; unmarked output without a manifest is refused; a library that no longer matches its manifest fails with instructions to move it aside. The working library is kept after runs.
  - `ContextFeatureCacheIdentity`: `workingLibraryInputs(library, fasta, parameters, settings)` hashes what preparation reads (source library and FASTA content, search parameters, prediction settings, version string `Context-shared-working-library-v1|Encyclopedia-6.9.23|JChronologer-1.0.10`) and not the raw file, assay schedule, or file paths. `withWorkingLibrary(inputIdentity, workingLibraryArtifact)` gives the per-run identity: the existing per-run input identity combined with the working library file's SHA-256. `requireCompanions` was extracted from `requireMatch` unchanged. `OutputLock` gained an optional thread lock; `acquireOutputLock` behavior is unchanged.
  - `ContextFeatureScorer.scoreFeaturesForContext`: the store directory is `prediction-cache` beside the run outputs (the parent of `baseName`; the `--prediction-store` override and `--clear-stale-store-lock` are `CTX016-021`). It resolves the shared working library (building it through `ContextPredictionStore` and the store-aware `ContextWorkingLibraryPreparer.prepare` when needed), opens it for the search, and uses the combined per-run identity for the feature manifest, in-progress marker, and diagnostic file name. The legacy-feature path, output lock, per-run archive-on-failure, pair sidecar, and partitioning are unchanged. Other callers of `scoreFeaturesForContext` (mProphet and Percolator executors, CLIs) get the same behavior.
- Migration (Ariana's "regenerate" decision): per-run working libraries are no longer created. An existing per-run feature cache now has a different per-run identity and fails clearly with the existing "identity differs" error; it is not reused and is not deleted or moved automatically. To regenerate, the old feature outputs must be moved aside. **Ariana to confirm** whether this explicit failure is what she wants, or whether Context should move mismatched old outputs aside automatically (a behavior change not made here).
- Validation (JDK 21.0.12 runtime, `-Dmaven.compiler.release=17`):
  - New `SharedWorkingLibraryTest`, 6 tests: builds once and reuses; a second thread arriving mid-build waits and reuses (1 build); an interrupted build leaves its marker, and the next run preserves the partial library in a `failed-` directory and rebuilds; unmarked output is refused and left untouched; an edited library fails clearly without a rebuild; the library identity ignores file paths, changes with prediction settings and library content, and a different working library changes the per-run identity.
  - `mvn -B -q -Dmaven.repo.local=build-cache/m2-repository -Dmaven.compiler.release=17 '-Dtest=SharedWorkingLibraryTest,ContextFeatureCacheIdentityTest,ContextFeatureScorer*Test,SearchPairSidecar*Test,org.searlelab.context.encyclopedia.*Test' -Dsurefire.failIfNoSpecifiedTests=false test` — 41 tests in 12 classes, 0 failures, 0 errors, 0 skips. The existing `ContextFeatureScorerTest` (unverified feature cache rejected before any prediction) still passes because companions are checked before the library is resolved.
  - End-to-end on the IL2 fixture (scratch code outside the repo, `scoreFeaturesForContext` defaults, copies under the session scratchpad): a baseline run with the previous per-run implementation was captured before the change. The new implementation produced byte-identical `IL2A.features.txt`, `IL2A_reference.features.txt`, and `IL2A_background.features.txt`; the three pair sidecars were identical except for the absolute `sourceLibrary` path, which differed only because the two runs used different folders. A second raw file (a copy of the same acquisition) in the same folder logged "Reusing verified shared working library", made no prediction, left the library file's timestamp and hash unchanged (one `.elib` in `working-libraries/`), and produced identical features. Re-running the first raw file reused both the shared library and its feature cache (0.57 s).
  - `git diff --check` clean.
- Residual risks / follow-ups: README still describes per-run `run_context_<identity>.elib` files (README and CLI help are `CTX016-021`). `parameters.toString()` is part of the library identity, as it already is of the per-run identity, so a parameter that differs between machines (such as thread count) gives a separate shared library per machine. The shared library is opened with `LibraryFile.openFile`, which copies it to a temporary file per run (unchanged cost from the evidence check). Java 17 runtime execution remains for `CTX016-023`.
- Not done: no commit or push.
- Next action: `CTX016-021` (CLI options and README/help), when Ariana directs; Ariana to confirm the migration behavior above.

##### 2026-10-11 — `CTX016-020` migration decision recorded

- Ariana decided she will move old outputs aside herself rather than have Context move data. The `CTX016-020` behavior stands: a raw feature cache made before the shared working library fails with the existing "identity differs" error and is neither moved nor deleted. Documented in README under the cache naming migration note.

##### 2026-10-11 — `CTX016-021` prediction store options, help, and README

- Authorization: Ariana asked to begin `CTX016-021` under accepted r8.
- Change:
  - `ContextFeatureScorer.Options` gained `predictionStore` (null = `prediction-cache` beside the run outputs) and `clearStaleStoreLock`. The existing five-argument constructor is kept and delegates with `null, false`, so existing callers and `Options.defaults()` are unchanged. New package-private `predictionStoreDirectory(baseName, options)` resolves the folder; the scorer passes `clearStaleStoreLock` to `ContextPredictionStore.open`.
  - `features-folder` (`ContextFeatureScorerCLI`): new `--prediction-store <folder>` and `--clear-stale-store-lock <true|false>` (default false), matching the CLI's existing `<true|false>` style. The existing Options construction moved unchanged into a testable `scoringOptions(arguments)`; `--prediction-store` must be a directory if it exists. Usage and help text updated.
  - `mprophet` (`ContextMProphetCLI`): the same two flags for raw `-i/-l/-f/-massList` runs; both are rejected with direct `-background/-reference` input, following the existing `-generateDecoys` rule; `--clear-stale-store-lock` must be true or false. Usage text updated.
  - `percolator` (`ContextPercolatorExecutorCLI`): the raw route goes through `ContextPercolatorExecutor`, which r7 lists among engine-side validation targets, so it keeps the default store. Its CLI forwards unknown flags to Encyclopedia as search parameters, where these would have been silently ignored; it now rejects both flags with a message pointing to the `features` command plus `-background/-reference`. The executor and engine classes were not changed.
  - The store's lock error now names the flag as `--clear-stale-store-lock true`.
  - README: options table rows for both flags; the matched-decoy section now describes the shared `prediction-cache` store, its profile guard, overlap behavior, size estimate (~0.7 GB per million spectra), shared working libraries, the lock policy, and the recorded fifth-decimal batch nondeterminism; the per-run `run_context_<identity>.elib` description was replaced; the migration note explains that old raw feature caches fail with "identity differs" and must be moved aside.
- Validation (JDK 21.0.12 runtime, `-Dmaven.compiler.release=17`):
  - `mvn -B -q -Dmaven.repo.local=build-cache/m2-repository -Dmaven.compiler.release=17 '-Dtest=ContextFeatureScorer*Test,ContextMProphetCLITest,ContextPercolatorExecutorCLITest,SharedWorkingLibraryTest,ContextFeatureCacheIdentityTest,SearchPairSidecar*Test,org.searlelab.context.encyclopedia.*Test' -Dsurefire.failIfNoSpecifiedTests=false test` — 49 tests in 14 classes, 0 failures, 0 errors, 0 skips. New checks: default and override store folders; flags mapped into Options; a file given as `--prediction-store` and a non-boolean `--clear-stale-store-lock` are rejected; mProphet rejects both flags for direct input and validates the boolean; Percolator rejects both flags and accepts ordinary arguments.
  - Real CLI on IL2 fixture copies (scratch folders in the session scratchpad; `java -cp <classes> org.searlelab.context.Context features-folder ...`): folder A predicted 2,224 spectra and saved the shared library in `--prediction-store cli/shared-store`; folder B with the same store reused the library with no prediction; both exited 0 and wrote identical features; no `prediction-cache` folder was created beside either folder. `features-folder --help` shows both flags. `percolator ... --prediction-store` fails with the rejection message.
  - Stale-lock path through the real CLI: with the shared library moved aside and a lock row left in the store (host `crashed-host`, pid 999), `features-folder` failed for that seed with the host, PID, age, and `--clear-stale-store-lock true` hint and exited 2, writing no feature outputs. Rerunning with `--clear-stale-store-lock true` logged the cleared lock, rebuilt the library from stored spectra without any prediction, exited 0, and produced features identical to folder A. The failed attempt had left the shared library's in-progress marker; the retry moved it to a `failed-` folder that contained only the marker (no partial data).
  - `git diff --check` clean.
- Residual risks / follow-ups: the `percolator` raw route always uses the default store and cannot clear a stale store lock; use `features` then `-background/-reference` for those cases. The positional `features` command and other `scoreFeaturesForContext` callers use the default store. A store-lock failure leaves an empty-marker `failed-` folder on the next run (cosmetic). Java 17 runtime execution remains for `CTX016-023`.
- Not done: no commit or push.
- Next action: `CTX016-022` (equivalence and overlap validation) when Ariana directs. Much of its functional evidence is already in the `CTX016-019` to `CTX016-021` entries; `CTX016-022` would consolidate it as repeatable tests or a recorded script and add a partial-store (overlapping-library) comparison with real predictions.

#### Follow-up task appended 2026-10-11

- [x] `CTX016-025` Remove the empty `failed-` folder left after a store-lock failure, and give the remaining raw-input feature-scoring entry points the prediction store options. Approval: Ariana agreed to the proposal on 2026-10-11 ("Please fix item 1. For item 2, if we need to modify the mProphet or Percolator classes at this point, I'm okay with the changes you've proposed."). This authorizes the narrow `ContextPercolatorExecutor` option pass-through below, which r7/r8 otherwise listed as a validation target. Files: `SharedWorkingLibrary`, `ContextFeatureScorer`, `ContextFeatureScorerCLI`, `ContextPercolatorExecutor`, `ContextPercolatorExecutorCLI`, tests, README. Check: an attempt that wrote nothing leaves no `failed-` folder; positional `features` and raw `percolator` accept the options; split/standard routes reject them; existing calls unchanged.

##### 2026-10-11 — `CTX016-025` done

- Change:
  - `SharedWorkingLibrary.resolve`: an in-progress marker with no partial library or report is deleted instead of archived, so a store-lock failure or a crash before any write leaves no empty `failed-` folder. Partial output is still preserved as before.
  - Positional `features` command (`ContextFeatureScorer.execute`): the four positional paths are unchanged and still sufficient; optional scoring flags may follow them (`--prediction-nce`, `--prediction-default-charge`, `--adjust-nce-for-dia`, `--candidate-diagnostics`, `--allow-legacy-features`, `--prediction-store`, `--clear-stale-store-lock`), parsed with `ContextFeatureScorerCLI.parseArguments` and `scoringOptions`; other flags are rejected with the usage text.
  - Raw `percolator` route: `ContextPercolatorExecutor.runContextPercolator` gained an overload taking `ContextFeatureScorer.Options`; the existing signature delegates with `Options.defaults()` (identical to the previous six-argument scoring call). `ContextPercolatorExecutorCLI` parses `--prediction-store` and `--clear-stale-store-lock` for the raw route, removes them before building Encyclopedia search parameters, and rejects them on the split and standard routes. This replaces the `CTX016-021` rejection. `ContextFeatureScorerCLI.predictionStore` is now public for this. No change to `ContextPercolator` or any engine/rescoring class.
  - `ContextMProphetExecutorCLI` was not changed: it is not routed from `context.jar` (the public `mprophet` command is `ContextMProphetCLI`, which already has the options).
  - README options table: both flags now listed for `features`, `features-folder`, and raw `percolator` and `mprophet` runs.
- Validation (JDK 21.0.12 runtime, `-Dmaven.compiler.release=17`): same focused command as `CTX016-021` — 52 tests in 14 classes, 0 failures, 0 errors, 0 skips. New tests: an attempt that wrote nothing leaves no `failed-` folder and rebuilds; positional options default when absent, map flags, and reject `--dia-folder`; Percolator raw options parse and validate; split/standard routes reject both flags. Real CLI: `features <dia> <elib> <fasta> <txt> --prediction-store cli/shared-store` reused the shared library and produced features identical to folder A; `features a b c d --dia-folder x` prints the unsupported-option error and usage.
- Pre-existing issue found, not fixed: the raw `percolator` route fails before scoring with `"splitOn" is null` with or without the new flags, because `MassListDecoyGenerator.resolveDecoysMessage` is an unimplemented stub returning null (unchanged since commit `edcb3cb`, 2026-08-26; its tests are `@Ignore`d). Out of CTX-016 scope; queued for Ariana as a separate suggested task. The new Percolator option handling is therefore covered by unit tests only, not an end-to-end raw run.
- Not done: no commit or push.

##### 2026-10-11 — `CTX016-022` equivalence and overlap validation

- Change:
  - `ContextWorkingLibraryPreparer` logs one summary line per store-backed preparation: `Prediction store <cache file>: <n> precursors reused, <m> sent for prediction` (the task's "cache hit counts logged").
  - New live test `src/test/java/org/searlelab/context/encyclopedia/ContextPredictionStoreLiveTest.java` (real JChronologer session through the package-private `Predictor` seam, recording each request; skipped on Intel macOS like the existing live test). On the IL2 fixture it prepares: a fresh full library with no store; a half library (every other fixture entry) into an empty store; the full library from that partly filled store; and the full library again from the full store. It asserts that the partial run predicts exactly the full library's requests minus the half library's, that the full-hit run predicts nothing, identical assignments in all runs, per-quantity tolerances between fresh and partial-store spectra, and exact equality between the partial and full-hit spectra.
  - New live test `src/test/java/org/searlelab/context/io/ContextFeatureScorerSharedLibraryLiveTest.java`: two copies of the fixture acquisition in one folder through `scoreFeaturesForContext`; asserts one shared working library, unchanged bytes and timestamp after the second run, identical feature tables, and feature-cache reuse on a rerun of the first.
- **Tolerance correction:** the r8 evidence said old-versus-new comparisons use a single 1e-4 tolerance on intensity and retention time. The new live test failed on that: retention time differed by 2.44140625e-4 s, which is one float32 step for retention times between 2,048 and 4,096 s, and is the same retention-time difference recorded in the original batch check (the r8 text misstated it). The `CTX016-019` log line "intensities and retention times by about 1e-5 (tolerance 1e-4)" is likewise wrong for retention time. Tolerances are now per quantity, each bounding the maximum observed in the 400-peptide batch check: intensity 1e-4 (observed 1.7e-5), ion mobility 1e-4 (6e-5), retention time 1e-3 s (2.4e-4 s), fragment and precursor m/z 1e-6 (0). Corrected in the r8 evidence bullet above (annotated, original text kept), README, and the local guide; the Google record is corrected under `CTX016-024`. This changes no code behavior and no design decision.
- Results (JDK 21.0.12 runtime, `-Dmaven.compiler.release=17`, Linux x86_64 CPU):
  - `-Dtest=ContextPredictionStoreLiveTest` — 1 test passed. Logged counts: half library 0 reused / 1,642 predicted; full library from the partly filled store 1,642 reused / 582 predicted (exactly the requests the half library had not made); full library again 2,224 reused / 0 predicted. Partial-store versus fresh maximum differences: fragment m/z 0.0, intensity 0.0, retention time 2.44140625e-4 s, precursor m/z 0.0, ion mobility 3.0517578125e-5. Assignments identical in all runs.
  - `-Dtest=ContextFeatureScorerSharedLibraryLiveTest` — 1 test passed (12.8 s): first run 0 reused / 2,224 predicted and saved the shared library; second acquisition and the rerun both logged "Reusing verified shared working library"; the rerun also reused its feature cache.
  - Earlier evidence that remains one-off (not repeatable now that the old code path is gone): the `CTX016-020` before/after comparison, in which the new implementation produced byte-identical feature, reference, and background tables to the previous per-run implementation on the fixture, and pair sidecars identical apart from the absolute source-library path. The `CTX016-021` and `CTX016-025` real-CLI runs (shared store across folders, stale lock, positional `features`) are also recorded in their entries.
- Scope note: this is functional validation on one small fixture (1,112 targets), not scientific calibration or FDR validation. Overlapping-library behavior was checked with a synthetic half-library subset of the fixture, not with two independently built libraries.
- Not done: no commit or push.

##### 2026-10-11 — `CTX016-023` Java 17 verification and diff review

- Java 17 runtime: the proxy denied `api.adoptium.net`; Eclipse Temurin 17.0.13+11 (linux x64) was downloaded from the Adoptium GitHub release `jdk-17.0.13+11` into the ignored `build-cache/jdk17` and checked against the published SHA-256 `8682892fc02965930b9022c066fa164dd6f458ef4a5dc262016aa28333b30f49` (match). Nothing was installed system-wide. New network dependency for this verification only: `github.com/adoptium/temurin17-binaries` release download.
- Full test-inclusive build, `JAVA_HOME=build-cache/jdk17 build-cache/apache-maven-3.9.9/bin/mvn -B -Dmaven.repo.local=build-cache/m2-repository -Ppackage verify`:
  - Current working tree: 141 tests, 4 failures, 1 error, 8 skipped; BUILD FAILURE (32 s).
  - Clean `HEAD` (`4188f57`) in a temporary git worktree, same JDK, Maven, and repository: 116 tests, 4 failures, 1 error, 8 skipped. The worktree was removed afterwards.
  - The failing tests are identical in both: `ContextScribeTwoBridgeTest.absentPreAlignmentCannotBecomeUncalibratedSuccess`, `ScribeFeatureTableTest.missingRuntimeHasActionableError`, `ScribeFeatureTableTest.partitionsTargetsAndDecoysAfterBestRowSelectionAndPreservesCharge` (expected 4, was 5), `ScribeFeatureTableTest.rejectsInvalidLabelsAndNonfiniteScoresWithRowContext`, and the error `ScribeCalibratedWorkflowTest.realCalibratedTableSupportsPartitionAndBackgroundModelApplication` (missing test resource). These are pre-existing Scribe failures unrelated to CTX-016 r8 (compare the earlier recorded baseline, which listed the same classes on Intel macOS plus platform-specific cases). The 25 additional tests from this work all pass, including both live prediction tests. No regression.
  - Because test failures stop the lifecycle before packaging, packaging was run separately: `... -Ppackage -DskipTests verify` — BUILD SUCCESS; shaded jar `target/context-0.0.2-SNAPSHOT.jar` (326 MB) created and the `verify-no-vendor-binaries` check passed.
- Packaged jar on Java 17 (`build-cache/jdk17/bin/java -jar target/context-0.0.2-SNAPSHOT.jar features-folder ...`, IL2 fixture copy, default store): first run predicted 2,224 spectra through the shaded jar's bundled PyTorch natives and saved the shared library; second run reused the shared library and the feature cache; features byte-identical to the JDK 21 class-path run.
- Diff review (14 modified and 6 new files; no POM or dependency change):
  - Scope: changes are confined to Context prediction preparation, feature caching, CLI adapters, README, and tests. Encyclopedia source, `ContextPercolator`, mProphet engine classes, and rescorers are unchanged; `ContextPercolatorExecutor` received only the option overload approved under `CTX016-025`.
  - Reuse: Encyclopedia's public prediction-cache API (`LibraryFile`, `JChronologerWorkingLibrary`), `ContextFeatureCacheIdentity` hashing/save/verify/archive patterns, `ContextFeatureScorerCLI` argument parsing, and `ContextWorkingLibraryPreparer.Settings` are reused rather than duplicated.
  - Compatibility: existing public signatures are kept (`Options` five-argument constructor, five-argument `prepare`, existing `runContextPercolator`, four-argument positional `features`). Changed behavior is documented: per-run working libraries are no longer written, and old raw feature caches fail with "identity differs".
  - Residual risks: (1) an old-format feature cache triggers a shared-library build (a one-time prediction, reused afterwards) before failing the identity check; (2) `ContextPredictionRuntime.requireLibraryApi` does not check the prediction-cache methods, so a conflicting Encyclopedia jar would fail with `NoSuchMethodError` rather than the actionable message; (3) the in-JVM lock map keeps one entry per shared-library path for the life of the JVM (negligible); (4) the version strings in the store profile and library identity are literals that must follow the Encyclopedia and JChronologer pins; (5) concurrent writers to one store fail rather than wait (approved lock policy); (6) the raw `percolator` route remains blocked by the pre-existing `resolveDecoysMessage` stub.
  - Validation limits: functional checks on one small fixture (1,112 targets) on Linux x86_64 CPU; not run on macOS ARM or Windows; no Percolator/pyIsoPEP run; no scientific calibration or FDR-validity claim.
- Not done: no commit or push.

##### 2026-10-11 — `CTX016-024` record updates (Report pending)

- Google implementation record (same document and link) updated under a revision guard: new section "20. r8 implementation and validation, 2026-10-11" (implementation summary by task, validation, tolerance correction, limits, next action); section 18's tolerance sentence now points to the corrected per-quantity tolerances; the intro line, Status cell, and r8 opening paragraph now say r8 was implemented on 2026-10-11 and awaits final review. All four text replacements matched once; read back with Drive `read_file_content`. Rendered layout not checked visually.
- Local guide `docs/ctx-016-encyclopediatwo-matched-decoys.md`: appended "r8 implementation and validation — 2026-10-11". The frozen canonical plan was not edited.
- Remaining for `CTX016-024`, `CTX016-011`, and `CTX016-012`: the external completion Report (CLAUDE.md: outside the repo; the usual `/Users/arianashannon/Downloads/` path does not exist in this cloud session, so the save location must come from Ariana), and Ariana's final review.


##### 2026-10-11 — r8 commits and push authorized

- Ariana approved the three proposed commits and the push on 2026-10-11: "Leave the task numbers (CTX-016) out of the subjects and messages, but you have my approval. Please make the commits and push!" The commit subjects and bodies therefore omit the task IDs and the "Tasks:" lines shown in the draft above; the third subject is "Document the prediction store and its validation". File groups are unchanged. Commit hashes and the push result are recorded in the entry after this one.

### Bug fix — TargetedBootstrapper temporary library leak (BOOTLEAK; no CTX ID or Google guide by Ariana's decision; started 2026-10-31)

- Problem: on Windows, `TargetedBootstrapperCLI` failed with `FileSystemException: There is not enough space on the disk` after seed 38–39 with a 3,875,793-entry DLIB. `selectMask` opened a new `LibraryFile` for every seed and never closed it. In Encyclopedia 6.9.23 (checked by `javap`), the `LibraryFile` constructor creates `%TEMP%\encyclopedia_*.elib` (`deleteOnExit` only), `openFile` copies the whole library into it, and only `close()` deletes it. Each seed therefore left a full library copy on disk and also reparsed the full library.
- Guide: none. Ariana approved the fix directly in chat on 2026-10-31: load the library once in `execute`, close it, and pass the in-memory entries to `selectMask` for every seed.
- Acceptance: no temporary library copies remain after a multi-seed run; seed outputs (assay list, entrapment assignments, masked file contents) are unchanged; Java 17 tests pass.
- Approval: Approved (Ariana, 2026-10-31).

- [x] `BOOTLEAK-01` Load and close the library once in `TargetedBootstrapper.execute`; `selectMask` takes the loaded `ArrayList<LibraryEntry>` instead of the library path. Files: `src/main/java/org/searlelab/context/io/TargetedBootstrapper.java`. Check: diff review; seed outputs unchanged.
- [x] `BOOTLEAK-02` Regression test that a three-seed run leaves no new `encyclopedia_*.elib` in `java.io.tmpdir`. Files: `src/test/java/org/searlelab/context/io/TargetedBootstrapperTest.java`. Check: fails on the original code, passes on the patch.
- [x] `BOOTLEAK-03` Java 17 validation and before/after output comparison. Files: log only.
- [x] `BOOTLEAK-04` Ariana reruns the large DLIB on Windows past seed 39. Check: run completes; `%TEMP%` holds at most one library copy, only during loading.
- [x] `BOOTLEAK-05` Ariana's final review; then git delivery when requested.

#### Work log

##### 2026-10-31 — `BOOTLEAK-01`/`02`/`03` implemented and validated

- Environment: Temurin 17.0.13 and Maven 3.9.9 downloaded into ignored `build-cache/` (system JDK is 21). `./build.sh maven` still fails at the pre-existing line-44 `MAVEN_REPO: readonly variable` error, not repaired here; Maven was downloaded from the same URL `build.sh` uses.
- Baseline (original code): `mvn -B -Dmaven.repo.local=build-cache/m2-repository -Dtest=TargetedBootstrapperTest test` passed 2 tests. A scratch harness (`tmp/leakcheck/`, ignored) running `execute` with seeds 1–3, 1 peptide, 0.5 min RT, 1.0 m/z, 1 trap on the `IL2_and_IL15_Combo.elib` / `IL2A_GPFDIA_0combined_masked0_assay.dia` fixtures left 3 `encyclopedia_*.elib` copies before JVM exit.
- Patched: same harness left 0. The library is closed right after `getAllEntries`, not at the end of the run, because the entries are fully in memory; this also frees the copy's disk space during masking. Load time is no longer inside `selectMask`'s timer.
- Equivalence: seeds 1–3 assay lists and entrapment-assignment TSVs are byte-identical before vs after; selection and masking log lines (targets, entrapments, scan counts) are identical; masked `.dia` SQLite tables (`metadata`, `ranges`, `spectra`, `precursor`, `fractions`) have identical contents. Limitation: this fixture's masked files hold 0 fragment spectra and at most 3 precursor scans, so masking content is only weakly exercised. Selection is unchanged by construction: the same entries in the same order are passed for every seed, and selection code does not mutate them.
- Tests: `TargetedBootstrapperTest` 3 tests, 0 failures/errors/skips on Java 17. With only `TargetedBootstrapper.java` reverted (stash), the new test failed listing 3 leftover `.elib` files, then the change was restored. `git diff --check` passed. Full `-Ppackage verify` not run (recorded baseline failures and native-runtime limits unchanged); no other caller of `selectMask` exists.
- Not validated: the 3.9M-entry DLIB on Windows (`BOOTLEAK-04`). Still open, out of scope: `writeMaskedFile` closes `rawLibraryFile` outside a `finally`, so an exception during masking can leave one temp `.dia` copy.
- Next: Ariana's Windows rerun and review; commit only on her instruction.

##### 2026-11-01 — dates corrected

- Ariana asked to keep the BOOTLEAK label with the start date 2026-10-31. The earlier entries above were first written as 2026-11-01 (UTC); the work ran about 22:30 America/Chicago on 2026-10-31, so their dates were corrected to 2026-10-31. No other content changed.

#### BOOTLEAK follow-up — entrapments not matched by precursor m/z (2026-11-01)

- Problem: commit `2e1b5fe` (2026-09-05) replaced `library.getEntries(libraryMzRange, ...)` in `selectDecoys` with a loop over every library entry and dropped the m/z test, so `libraryMzRange` is built but unused. Entrapments became the first eligible entries in library order (charge and target-overlap checks only), not entries near the target m/z. In the 2026-10-31 three-seed fixture run, entrapments were 22–42 m/z from their targets and `VFTTGFSR` served seeds 1 and 2. Bootstrap outputs made since 2026-09-05 are affected.
- Decisions (Ariana, 2026-11-01): among eligible candidates, choose the closest precursor m/z first; keep the 10 ppm start, doubling to 320 ppm, inclusive bounds (Ariana allowed a 20 ppm start if faster); fix the "Only found" log line to report this target's entrapment count; no CTX ID or Google guide, keep the BOOTLEAK label.
- Approach (approved 2026-11-01): `execute` makes one stable m/z-sorted copy of the loaded entries; `selectTargets` keeps library order so seeded target draws are unchanged; `selectDecoys` binary-searches the sorted copy for each tolerance window, orders candidates by |Δm/z|, and applies the existing eligibility checks unchanged.
- Acceptance: every entrapment is within 320 ppm of its target, same charge, RT-separated from every target window by the existing buffer, not a target, not repeated within a seed, and the closest eligible candidate(s); unmatched targets are still replaced; Java 17 tests pass; README describes the rule. A passing build does not establish FDR validity.
- Approval: Approved (Ariana, 2026-11-01).

- [x] `BOOTLEAK-06` Build the m/z-sorted entry list once in `execute` and pass it through `selectMask` to `selectDecoys`. Files: `TargetedBootstrapper.java`. Check: diff review; target selection unchanged.
- [x] `BOOTLEAK-07` In `selectDecoys`, restrict candidates to the ppm window via binary search, order by |Δm/z|, add Δppm to the "Decoy k of n" log line, and make "Only found" report this target's count. Files: `TargetedBootstrapper.java`. Check: tests in `BOOTLEAK-08`/`09`.
- [x] `BOOTLEAK-08` Synthetic JUnit 4 tests: off-mass entry listed first is not chosen; in-window ineligible candidates rejected (charge, RT overlap, used sequence); closest-first order with 2 traps; widening finds ~150 ppm, ~400 ppm leaves the target unmatched; inclusive boundary. Files: `TargetedBootstrapperTest.java`. Check: regression test fails on the current code, all pass on the fix.
- [x] `BOOTLEAK-09` Fixture integration test (IL2/IL15, seeds 1–3, 5 peptides, 2 traps): every assignment within 320 ppm, same charge, RT-separated, not a target, unique within seed. Files: `TargetedBootstrapperTest.java`.
- [x] `BOOTLEAK-10` Java 17 focused tests, before/after comparison of targets and entrapments for seeds 1–3, diff review. Files: log only.
- [x] `BOOTLEAK-11` README paragraph on how `bootstrap` chooses entrapments. Files: `README.md`.
- [x] `BOOTLEAK-12` Ariana runs the large DLIB for a few seeds: Δppm distribution, replaced targets per seed, entrapment reuse across seeds, time per seed; then regenerates bootstrap outputs made since 2026-09-05.

##### 2026-11-01 — `BOOTLEAK-06` to `BOOTLEAK-11` implemented and validated

- Code: `execute` builds `entriesByMz` (stable sort by precursor m/z) once after loading; `selectMask` takes it as a new parameter after `entries` and passes it to `selectDecoys`. `selectDecoys` rejects an unsorted list with `IllegalArgumentException`, and for each 10→320 ppm window takes candidates from new package-private `candidatesInWindow` (binary search for the inclusive window, then stable sort by |Δm/z|; ties: lower m/z, then library order). Eligibility checks are unchanged. "Decoy k of n" now logs Δppm; "Only found" reports this target's count. The unused `libraryMzRange` was removed. README gains a "Simulated assays with `bootstrap`" section.
- Note: with closest-first ordering, the doubling rounds give the same result as one 320 ppm window, so the start value (10 vs 20 ppm) affects only speed; 10 ppm was kept as approved.
- Tests (Java 17, Temurin 17.0.13, Maven 3.9.9): `-Dtest=TargetedBootstrapperTest test` 9 tests, 0 failures/errors/skips. With only `TargetedBootstrapper.java` reverted (stash, then restored), 4 new tests failed as expected: off-mass entry chosen (`OFFMASSK`), fixture assignment far outside 320 ppm, unsorted input accepted, 321 ppm candidate accepted. The closest-first and in-window-rejection tests also pass on the old code because their inputs were already in m/z order; they fix the rule going forward.
- Full suite: `mvn -B -Dmaven.repo.local=build-cache/m2-repository test` ran 148 tests, 4 failures, 1 error, 8 skips. All 5 are in `ScribeFeatureTableTest` (3), `ScribeCalibratedWorkflowTest` (missing resource), and `ContextScribeTwoBridgeTest`; the same 5 fail on unchanged commit `558ac27` in a temporary worktree, so they are pre-existing and unrelated.
- Fixture comparison (seeds 1–3, 5 peptides, 2 traps, 0.5 min, 1.0 m/z; harness in ignored `tmp/mzfix/`): maximum |Δppm| per seed fell from 178,351 / 123,045 / 150,597 to 107.8 / 78.9 / 109.2; two entrapments are exact-mass (0.00 ppm). Six targets lacked 2 entrapments by 320 ppm and were replaced, so each seed's target list changed.
- Timing (synthetic, not the real DLIB): 3.9M generated entries (m/z uniform 400–1200, z 2–3, RT 0–120 min, seed 42); sorting once took 3.5 s; `selectDecoys` for 100 targets × 2 traps took 125–411 ms per seed, including the sortedness check.
- Findings for Ariana, not changed: (1) pre-existing: replacement targets are drawn with seed `i + replacementRound`, which repeats the next seed's draws; in the fixture, seed 1's replacements were seed 2's first targets and seed 2's were seed 3's, so seeds share targets and, under the deterministic rule, entrapments. (2) Closest-first prefers exact-mass candidates, including sequence isomers or isobars of the target, whenever they are RT-separated.
- Next: `BOOTLEAK-12` on the real DLIB; Ariana's review; commit only on instruction.

#### BOOTLEAK follow-up — 1000 ppm limit and recorded limitation (2026-11-01)

- Decisions (Ariana, 2026-11-01): raise the final entrapment window from 320 to 1000 ppm with steps 10, 20, 40, 80, 160, 320, 640, 1000 ppm; keep closest-eligible-first. Accept that a target drawn in more than one seed gets the same entrapments; record this as a limitation in the commit message without describing its implications there. Accept that seeds share targets (finding 1: replacements use the next seed's draws), since assays from the same matrix often share targets. Keep exact-mass candidates, including sequence isomers (finding 2), because they must be RT-separated from the target.
- Acceptance amendment: the 320 ppm bound in the earlier acceptance and in `BOOTLEAK-08`/`09` is now 1000 ppm.
- Approval: Approved (Ariana, 2026-11-01).

- [x] `BOOTLEAK-13` Final window 1000 ppm (doubling capped at 1000) with matching tests and README. Files: `TargetedBootstrapper.java`, `TargetedBootstrapperTest.java`, `README.md`. Check: focused tests; the widening test fails with the cap left at 320.
- [x] `BOOTLEAK-14` Commit messages include a Limitations section stating that a target drawn in more than one seed receives the same entrapments. Check: at commit time, on Ariana's authorization.

##### 2026-11-01 — `BOOTLEAK-13` implemented and validated

- Code: `MAX_ENTRAPMENT_PPM = 1000`; after an insufficient round the tolerance becomes `min(2 × ppm, 1000)`, and the loop stops after the 1000 ppm round.
- Tests (Java 17): `-Dtest=TargetedBootstrapperTest test` 9 tests, 0 failures/errors/skips. The widening test now also needs a candidate at −800 ppm (found only in the final round), one exactly at +1000 ppm (inclusive), and rejects +1001 ppm; with `MAX_ENTRAPMENT_PPM` temporarily set to 320 it failed as expected (`[ONEFIFTYK]` only), then the value was restored. Fixture integration bound changed to 1000 ppm.
- Fixture comparison (seeds 1–3, 5 peptides, 2 traps): replaced targets fell from 6 (320 ppm) to 2 (1000 ppm); seed 2 kept all five initially drawn targets; maximum |Δppm| per seed was 771.1 / 736.3 / 109.2, the larger values only for targets that 320 ppm would have replaced.
- Full suite not rerun after this step; only `TargetedBootstrapper` changed since the 148-test run, which had only the 5 pre-existing Scribe failures.
- Next: Ariana's commit and push authorization (`BOOTLEAK-14`), then `BOOTLEAK-12` on the real DLIB.

##### 2026-11-01 — `BOOTLEAK-14` commits and push authorized

- Ariana approved both commits, with the revised first message naming `TargetedBootstrapper.execute` and `TargetedBootstrapper.selectDecoys`, and the push to `claude/targeted-bootstrapper-patch-20261031`. Both messages carry the Limitations section. No Google record exists for BOOTLEAK, so hashes are reported in chat only.

#### BOOTLEAK follow-up — library loading and per-seed progress messages (2026-11-01)

- Problem: opening the large DLIB takes about 2–5 minutes with no output, which looks like a hang; `selectTargets` logged "There are N entries from the library." on every call (each seed and each replacement round).
- Approval: Approved (Ariana, 2026-11-01): add the opening and loaded messages; replace the repeated line, wording at Claude's discretion.

- [x] `BOOTLEAK-15` In `TargetedBootstrapper.execute`, log "Opening the library <file name> (this may take a few minutes)..." before opening and "Loaded N library entries in X s." after loading; log "Seed s of N: selecting K targets." at the start of each seed; remove the repeated "There are N entries from the library." line from `selectTargets`. Files: `TargetedBootstrapper.java`. Check: focused tests; each message appears once (per run or per seed) on the fixture; outputs unchanged.

##### 2026-11-01 — `BOOTLEAK-15` implemented and validated

- Nothing in the repository matched the removed text, so no test or parser depended on it. The load time covers `openFile` (temp copy) and `getAllEntries`, not the m/z sort.
- Tests (Java 17): `-Dtest=TargetedBootstrapperTest test` 9 tests, 0 failures/errors/skips.
- Fixture run (seeds 1–3, 5 peptides, 2 traps): "Opening the library IL2_and_IL15_Combo.elib…" and "Loaded 2224 library entries in 0 s." appeared once each, "Seed s of 3" three times, "There are …" zero times. Assay lists and entrapment assignments for all three seeds are byte-identical to the previous 1000 ppm run.
- Next: Ariana's commit and push authorization; `BOOTLEAK-12` continues on the real DLIB.

##### 2026-11-01 — random entrapment selection declined; `BOOTLEAK-15` commit and push authorized

- Ariana briefly considered adding random entrapment selection, then decided not to add it: closest-eligible-first stays, and the repeated-entrapment behavior remains recorded as a limitation. No tasks were added.
- Ariana authorized committing and pushing the `BOOTLEAK-15` change with the proposed message.

##### 2026-11-01 — `BOOTLEAK-04` Windows run on the full DLIB (reported by Ariana)

- Environment: Ariana's Windows PC, launched from Eclipse 4.37 (Eclipse itself runs on Java 21.0.9), branch `claude/targeted-bootstrapper-patch-20261031` rebuilt after `55a503a`; library `Human_UP000005640_9606_entrapment.fasta.trypsin.z1-6_nce33_DIA.dlib` (3,875,793 entries).
- Before the run, 62 leftover `encyclopedia_*` files from earlier killed runs were deleted from `%TEMP%` (count 0).
- A 399-target, 1-entrapment run took about 85 s per seed and was stopped by Ariana to save time (not a failure). The rerun with 10 targets and 1 entrapment per assay completed all 50 seeds (last seed written 11:23): every seed has 10 target–entrapment pairs, matching the settings. This passes seed 39, where the original run failed with "not enough space on the disk". The "Opening the library…" message appeared.
- Eclipse later failed at 11:50:54 with repeated `SWTError: No more handles` ("Unhandled event loop exception", `org.eclipse.ui`, raised while the Error Log view was redrawing). That is Windows graphics-handle exhaustion in Eclipse's UI after a ~19.5 h session, 27 minutes after the run finished; no frame came from Context. A fixture stress run here (80 seeds, 100 targets, 2 GB heap) completed with flat post-GC heap (~9 MB) and no leftover temp files. Advice given: run long jobs outside Eclipse with output to a file, or restart Eclipse, limit console output, and set an Output File.
- Not yet confirmed: `%TEMP%` count after this run (needed to tick `BOOTLEAK-04`). Console output was lost, so `BOOTLEAK-12`'s "Only found" counts were not captured; Δppm can still be computed from the output files.
- Output check (2026-11-01, from files Ariana uploaded: seed 1 assay list and assignments, seed 4 assignments): RT window 5.0 min (half-window 2.5 min). Seed 1: 10 targets and 10 entrapments; every pair has matching charge; |Δppm| 0.00–43.70 (7 of 10 within ±9 ppm; one exact-mass pair, `SYSVPLR` → `SLADLFR`), so all fell inside 80 ppm and none needed the 640–1000 ppm steps; every entrapment's RT is more than 3 half-windows (7.5 min) from every target within 11 m/z (smallest gap 7.52 min); entrapments are unique and never targets. Seed 4: 10 pairs, charges match, entrapments unique and never targets; no targets or entrapments shared with seed 1. Seed 4 Δppm was not computed (its assay list was not supplied).
- `%TEMP%` check after the run (Ariana, WSL `ls /mnt/c/Users/m334793/AppData/Local/Temp/encyclopedia_* | wc -l`): 0 files. `BOOTLEAK-04` is complete. `BOOTLEAK-12` stays open: "Only found" counts were not captured, and outputs made since 2026-09-05 still need regenerating.
- `BOOTLEAK-12` closed by Ariana's decision (2026-11-01): the full-DLIB runs and output checks above are sufficient. Not done within it: "Only found" (replaced-target) counts were not captured, the Δppm distribution covers seed 1 only, and regenerating bootstrap outputs made since 2026-09-05 is left to Ariana outside this checklist.

##### 2026-11-01 — `BOOTLEAK-05` accepted

- Ariana accepted the BOOTLEAK fix (commits on `claude/targeted-bootstrapper-patch-20261031`: temp-library leak fix, m/z-matched entrapments up to 1000 ppm, loading and seed progress messages) and authorized committing and pushing these `TASKS.md` updates. The Report location has not been chosen yet; it is not written. No pull request has been requested.

#### BOOTLEAK follow-up — warn when isolation windows are wider than 2 m/z (2026-11-03)

- Finding: a 169-target, 10-entrapment, 2.5 min, 1.0 m/z run on a 16 m/z staggered HeLa DIA file produced reference features for only 7 of 169 targets. `writeMaskedFile` keeps MS2 scans whose isolation-window centre is within ±`halfWindowWidthMz` of the target. A copy of the IL2 fixture with windows widened to 16 m/z kept 25 MS2 scans for a target at a window centre and 0 for targets 3 and 7 m/z off centre. Ariana confirmed `TargetedBootstrapper` is only meant for 1-2 m/z (PRM-like) acquisitions, so the masking rule is correct for supported input; the defect was that unsupported input ran without notice.
- Approval: Approved (Ariana, 2026-11-03): check isolation windows before running and print a message that the tool is meant for narrow (1-2 m/z) windows and that larger windows may leave some scheduled targets without MS2 spectra. Implemented as a warning; the run continues.

- [x] `BOOTLEAK-16` Before loading the library, `TargetedBootstrapper.execute` reads the acquisition's isolation windows (`EncyclopeDIAFile.getRanges()`) and logs a warning through `Logger.errorLine` when the widest is above 2.01 m/z (allowance for stored widths such as 2.0009 m/z); README notes the requirement. Files: `TargetedBootstrapper.java`, `TargetedBootstrapperTest.java`, `README.md`. Check: unit test for 2.0009 vs 16 m/z windows; end-to-end runs on the fixture and a 16 m/z copy.

##### 2026-11-03 — `BOOTLEAK-16` implemented and validated

- Tests (Java 17): `-Dtest=TargetedBootstrapperTest test` 10 tests, 0 failures/errors/skips, including new `warnsOnlyForIsolationWindowsWiderThanTwoMz`.
- End-to-end (1 seed, 5 targets, 1 entrapment): with a copy of the IL2 fixture whose windows were widened to 16 m/z (scratch only, not committed), the warning reported "16.0 m/z", appeared before "Opening the library…", and the run still wrote all three outputs. With the unmodified fixture (2.0009 m/z), no warning appeared.
- Not changed: the masking rule itself, and the minor edge case where 2.0009 m/z windows leave about 0.05% of the m/z range more than 1.0 m/z from every window centre.
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

## CTX-017 — Feature necessity analysis (Percolator, context-ms)

- Google implementation record: https://docs.google.com/document/d/1m9qrS-0_IErOL_dia7ms43ggwaRDsL8O1f4HTme-lxI/edit — guide revision r2.2, approved by Ariana 2026-11-13 (r2.1 approved 2026-11-12). The record holds the r2.2 text, decision-log rows, and work log.
- Canonical Until Useful plan: `docs/uu-ctx-017-feature-necessity.md`, revision 2, frozen 2026-11-13 from approved r2.2; revision 1 is retained in the same file.
- Checklist approval status: **Approved by Ariana on 2026-11-13** (this chat). Approval covers scope; each commit, push, PR, or release still needs its own instruction.
- Priority: High.
- Scope boundary: analysis only, with the Python `context-ms` 0.1.3 Percolator engine and Percolator 3.05. No Context Java, context-ms, or EncyclopeDIA source edits. mProphet is set aside for now and the r1/r2 Java steps are superseded; either may be revisited. Ariana makes every keep/drop decision. CTX-017 work is committed on its own branch `ctx017` (created from `ariana/scoring` at `7d0f524` on 2026-11-13 at Ariana's request). Each push, PR, or merge still needs her instruction.

### Acceptance criteria

- [ ] Screening, the decoy-artifact check, and grouped leave-one-out ablations run for the context-ms Percolator engine on mouse masked2–10 (and the second dataset when supplied), producing tidy results tables and R figures.
- [ ] Every analyzed feature or group has Ariana's recorded decision (keep / drop / defer), with date and round, in the guide's Feature decisions table. No feature leaves a later stage or the final set without it.
- [ ] Each round's tables, figures, and R scripts are in Drive `ctx-017/results` before Ariana is asked to decide. Recommendations live only in the per-round recommendations file (`claude_recommendation`, `recommendation_basis`, `confidence`, `if_applied`).
- [ ] Every result row records experiment, workflow, ablation set, training seed, masked seed, Percolator version (3.05), and context-ms version (0.1.3). Results report the mean and spread over masked seeds and at least 3 training seeds.
- [ ] Sensitivity is passing assay targets (N_T) at q ≤ 1%. q-values: pyIsoPEP for Context-Percolator; Percolator's own for standard and reference-only Percolator. Entrapment FDP: lower bound plus both upper bounds until Ariana chooses one.
- [ ] Inputs stay byte-identical (SHA-256). Existing run folders are refused. Failed runs are recorded as failed. A no-exclusion driver run reproduces the regenerated baseline.
- [ ] Until Useful review, Ariana's final review, and the external Report are recorded with actual checks and limits.

### Checklist — approved by Ariana 2026-11-13

- [x] `CTX017-001` Record approved r2.1 in the Google record and freeze the canonical plan. Files: Google record, `docs/uu-ctx-017-feature-necessity.md`, this section. Check: the guide shows r2.1 text, decision-log rows, and the 2026-11-12 work-log entry; the plan is revision 1. Completed and verified 2026-11-13.
- [x] `CTX017-002` Ariana reconfirms the mouse inputs for masked2–10 are current: `*_assay.features.txt`, `*_assay_background.features.txt`, `*_assay_reference.features.txt`, their `.pairs.tsv` files, and `*_entrapment_assignments.tsv`. Depends on: Ariana. Check: her confirmation logged; inventory with SHA-256 recorded.
- [x] `CTX017-003` Environment: context-ms 0.1.3 in a project-local virtual environment, Percolator 3.05 (`percolator-v3-05.centos` from `encyclopedia-6.9.23.jar`) first on `PATH`, pyIsoPEP 0.1.4. Depends on: 002. Check: versions logged; a single-seed smoke run of all three workflows completes.
- [x] `CTX017-004` Regenerate the three baseline workflows (Context-Percolator, standard Percolator, reference-only Percolator) with Percolator 3.05 and at least 3 training seeds on masked2–10. Depends on: 003. Check: sensitivity and FDP tables uploaded to Drive; commands, seeds, and versions logged.
- [x] `CTX017-005` Python ablation driver in `tmp/ctx017/`: read-only inputs, pruned copies keeping `id`/`Label`/`ScanNr`/`sequence`/`Proteins`, one folder per ablation/training seed/masked seed, refusal of existing folders, SHA-256 checks, failure recording, one tidy TSV. Depends on: 003. Check: invalid exclusions rejected; a no-exclusion run reproduces task 004's baseline.
- [x] `CTX017-006` Screening and decoy-artifact scripts (stages 1–2): missing/constant fraction, target-vs-decoy AUC, Spearman clusters at |ρ| ≥ 0.9, background-vs-reference shift, decoy-vs-entrapment AUC with CI. Depends on: 002. Check: outputs on masked2–10; R figures render.
- [ ] `CTX017-007` Round A: upload evidence and `roundA_recommendations.tsv` to Drive, summarize in chat (evidence first), record Ariana's decisions. Depends on: 006. Check: decisions in the guide table and this log.
- [ ] `CTX017-008` Stage 3 grouped leave-one-out on the clusters Ariana selected (≥ 3 training seeds), then Round B. Depends on: 004, 005, 007. Check: Δ sensitivity and Δ FDP tables; decisions recorded.
- [ ] `CTX017-009` Within-cluster single-feature leave-one-out and stage 4 weight stability, then Round C. Depends on: 008. Check: tables and figures; decisions recorded, including whether stage 5 runs.
- [ ] `CTX017-010` Optional stage 5 backward elimination, only if Ariana chose it in Round C, then Round D. Depends on: 009. Check: decisions recorded, or task marked not run by Ariana's choice.
- [ ] `CTX017-011` Record Ariana's final feature set in the guide's Feature decisions table. Depends on: 009/010. Check: every analyzed feature or group has a decision.
- [ ] `CTX017-012` Generalize the FDP-comparison and analysis scripts for several experiments (seed discovery, skip list, file prefix, experiment label, per-seed r). Run the second dataset when supplied; Ariana chooses the upper-bound definition. Depends on: the second dataset. Check: mouse numbers reproduce exactly; combined figure and table in Drive; Ariana's choice logged.
- [ ] `CTX017-013` Until Useful review of the scripts and the analysis. Depends on: 011, 012. Check: findings, dispositions, and residual risks logged.
- [ ] `CTX017-014` Ariana's final review. Check: her acceptance logged with date.
- [ ] `CTX017-015` External Report `YYYY-MM-DD-CTX-017-Report.md` outside the repository, with this checklist and log copied verbatim. Ask Ariana where to save it in cloud sessions. Check: the Report exists and is verified.

### Work log

#### 2026-11-05 — `CTX017-001` guide revision r2 drafted

- Attempt/change: Drafted guide r2 in the Google record at Ariana's request. Ariana makes every keep/drop decision, and Claude presents evidence with a separate recommendation in decision rounds A–D. Installed R 4.3.3 with ggplot2/patchwork/dplyr/tidyr/readr and pyIsoPEP 0.1.4 in the cloud session. Confirmed that Percolator 3.01 and 3.05 binaries are bundled in `encyclopedia-6.9.23.jar` and run.
- Result/observation: The mProphet constant guard was already corrected in `fba83e0`. Installing Microsoft Arial was blocked (licence acceptance); Liberation Sans is the fallback. No repository changes.
- Next action: Ariana reviews r2 and supplies data.

#### 2026-11-06 — data received; FDP estimator comparison

- Attempt/change: Downloaded `mouse.zip` from Ariana's Drive `ctx-017` folder into a read-only folder outside the repository. It has 10 masked seeds: features, partitions, `.pairs.tsv` files, entrapment assignments, and Ariana's `results_3workflows` baselines from Python context-ms. An earlier wrong upload (`patch_5min_2mz.zip`) was stopped and deleted at Ariana's request. Compared the lower, tight, and loose entrapment FDP estimators on the baselines for masked2–10 (`fdp_compare.py`, `fdp_estimator_comparison.R`).
- Result/observation: Each seed has 152 assay targets with 10 entrapments each (r = 10) and 27 scored features. Pooled at q ≤ 1%, lower / tight / loose FDP were: Context 2.33 / 2.56 / 4.66%; full-file Percolator 2.11 / 2.32 / 4.21%; reference-only Percolator 4.77 / 5.25 / 9.54%. Ariana judged these consistent with her other results. The baselines used Percolator 3.09 with training seed 1. The masked1 Context run failed with a `PermissionError` writing its peptide output. Results are in Drive `ctx-017/results/2026-11-06_fdp_estimator_comparison`. The first upload of `fdp_counts.tsv.gz` was truncated; it was re-uploaded and checked by size, and the corrupt copy was renamed for Ariana to delete.
- Decisions (Ariana): use her context-ms results; skip masked1; FDP lower bound and upper bound (she intentionally uses the loose bound; she will compare across experiments); q-values per workflow as above; results in `ctx-017/results`.
- Next action: second dataset from Ariana; plan approval.

#### 2026-11-12 — `CTX017-001` r2.1 approved

- Decisions (Ariana): she makes every keep/drop decision, with no exclusion from a final set without her explicit approval. Recommendations go in up to three columns or a separate mapped file; Claude proposed the separate per-round file with four columns. Java steps dropped for now. Python context-ms with the Percolator engine only; mProphet set aside for now. Percolator 3.05 (context-ms uses whichever `percolator` is on `PATH`), with baselines regenerated as needed. Priority High. Ariana approved the plan. Ariana will confirm whether the earlier files are still useful (`CTX017-002`).
- Attempt/change: Applied the r2.1 text changes to the Google record (48 exact-match replacements, each matched once).
- Next action: add the guide's decision-log rows and work-log entry; freeze the canonical plan; add this checklist.

#### 2026-11-13 — `CTX017-001` canonical plan and checklist written; guide update incomplete

- Attempt/change: Wrote `docs/uu-ctx-017-feature-necessity.md` revision 1 and this section. The remaining guide edits (nine decision-log rows and the 2026-11-12 work-log entry) were built but not sent: after a session restart the Google Docs and Drive connectors failed to connect (502).
- Result/observation: The repository changes are only `TASKS.md` and the new canonical plan. No code, no Git actions.
- Next action: when the Google connectors are back, re-read the guide, add the pending rows and entry, and verify. Ariana approves this checklist and reconfirms the data (`CTX017-002`).

#### 2026-11-13 — `CTX017-001` complete; checklist approved; `CTX017-002` counts shared

- Attempt/change: After the Google connectors reconnected, added the nine r2.1 decision-log rows, the 2026-11-12 work-log entry (with the local commit hash), and the canonical-plan reference to the Google record. The first revision-guarded fill was rejected because Ariana had capitalized one word ("the" → "The"); a re-read confirmed no position shift, and the resent batch applied. Verified the whole record with a Drive read. Ariana authorized committing the canonical plan and this section (local commit `15a9c70`), then approved this checklist and asked for commit and push.
- Result/observation (`CTX017-002`, for Ariana's confirmation): every seed's assay list (`*_assay.txt`) has 1,672 entries: 152 distinct assay target peptides and 1,520 entrapments (10 per target). The 152 targets differ between seeds (no target is shared by all of masked2–10; 1,022 distinct targets across them), as expected for masked bootstrap assays. Reference feature rows (targets / decoys): masked2 601/457, masked3 606/458, masked4 619/465, masked5 623/477, masked6 577/473, masked7 546/446, masked8 627/469, masked9 591/487, masked10 587/471 (masked1, skipped: 638/450). Background rows range from 23,174 to 25,732 per seed.
- Next action: Ariana confirms the mouse inputs (`CTX017-002`), then `CTX017-003` (environment) begins.

#### 2026-11-13 — CTX-017 moved to branch `ctx017`

- Attempt/change: Pushing the two local CTX-017 commits (`15a9c70`, `5de6997`) to `ariana/scoring` was rejected because the remote had 20 newer commits (CTX-018, bootstrapper fixes, the CTX-016 archive, and a reorganized `TASKS.md`). A read-only trial merge showed a conflict only at the end of `TASKS.md`. With Ariana's approval, saved both documents and the two commits as patches, reset local `ariana/scoring` to `origin/ariana/scoring` (`7d0f524`), created branch `ctx017` from it, and re-added the canonical plan and this section after the remote `TASKS.md` content, which is unchanged. The earlier hashes `15a9c70` and `5de6997` no longer exist on any branch.
- Result/observation: CTX-018 changes the Java Context code, which CTX-017 does not use. The remote bootstrapper m/z fix (`eb27bc0`) does not invalidate the mouse data: all entrapments are within 1000 ppm of their targets (median about 32–46 ppm).
- Next action: push `ctx017` (authorized by Ariana); update the commit hash in the Google record; Ariana confirms the mouse data (`CTX017-002`).

#### 2026-11-13 — material plan change proposed: two datasets (r2.2, approval **Pending**)

- Trigger: Ariana asked for the analysis to use 5 HeLa assays and 5 mouse assays, with every figure showing both datasets. This replaces the r2.1 data scope (mouse masked2–10, second dataset later). Implementation stays paused until she approves guide r2.2.
- Ariana's choices: mouse masked2–6; HeLa, the first 5 complete assays. No library is needed, because both uploads contain feature files.
- HeLa data: `test.zip` from Drive `ctx-017` (1,318,860,731 bytes, matching Drive), extracted read-only outside the repository. It has 7 seeds (`astral_2mz_hela_masked1–7`), all complete, with the same file set, the same 27 features as mouse, and EncyclopeDIA 6.9.23 in the pairs files. Each seed has 62 assay targets with 5 entrapments each (r = 5; 310 entrapments). Reference rows (targets/decoys): masked1 132/77, masked2 152/92, masked3 141/79, masked4 129/69, masked5 145/75 (masked6 135/82, masked7 134/73). Background rows 148,840–161,103 per seed. Entrapments are within 1000 ppm of their targets (median 20–34 ppm). There are no HeLa baseline results; all baselines are regenerated with Percolator 3.05 anyway.
- Proposed r2.2: data become mouse masked2–6 plus HeLa masked1–5. Every figure is faceted by dataset, every results row carries `dataset`, and summaries stay per dataset (not pooled). r is taken per seed (mouse 10, HeLa 5). `CTX017-002` becomes inventory and confirmation of both datasets; `CTX017-012` covers both datasets from the start. Guide r2.2, canonical plan revision 2 (revision 1 kept), and appended tasks follow after approval.
- Next action: Ariana approves or revises r2.2.

#### 2026-11-13 — `CTX017-003`, `CTX017-004` environment and baselines (5 HeLa + 5 mouse)

- Authorization: Ariana asked for the baselines to be regenerated for the 10 assays in use, and approved executing `CTX017-001` through `CTX017-005`. Guide r2.2 (two datasets) is otherwise still pending her review of the documents.
- `CTX017-003` attempt/result: installed context-ms 0.1.3 (pyIsoPEP 0.1.4) in `/home/user/context-env`; extracted `percolator-v3-05.centos` from `encyclopedia-6.9.23.jar` as `percolator` (SHA-256 `92a6700d…dbfab6`), first on `PATH`; it reports "Percolator version 3.05.0, Build Date Feb 18 2021". Smoke run (training seed 1) of all three workflows on mouse masked2 and HeLa masked1: all ok.
- `CTX017-004` attempt: `tmp/ctx017/run_baselines.sh` on mouse masked2–6 and HeLa masked1–5, training seeds 1–3, three workflows each (90 runs, 7 min 22 s, 3 in parallel). Counts by `tmp/ctx017/baseline_counts.py`; figure by `tmp/ctx017/baseline_figure.R`.
- Result: 90 of 90 runs ok; every run recorded Percolator 3.05.0, context-ms 0.1.3, pyIsoPEP 0.1.4. SHA-256 of the 80 input files identical before and after. At q ≤ 1%, pooled over 5 assays × 3 training seeds (sensitivity = mean % of assay targets passing; FDP lower / tight / loose):
  - Mouse: Context 91.3%, 1.42 / 1.56 / 2.84%; full-file Percolator 84.0%, 1.69 / 1.86 / 3.39%; reference-only Percolator 80.3% (range 0–96.1%), 3.68 / 4.05 / 7.37%.
  - HeLa: Context 71.7% (range 0–93.5%), 1.19 / 1.42 / 2.37%; full-file Percolator 73.2%, 2.16 / 2.59 / 4.31%; reference-only Percolator 0% (nothing passes at 1% in any run).
  - At q ≤ 5%: mouse Context 95.6%, 5.99 / 6.59 / 11.99%; HeLa Context 93.4%, 2.69 / 3.23 / 5.38%.
- Observation: HeLa masked2 passes 0 Context targets at 1% in all three training seeds: its smallest pyIsoPEP q-value is 0.0110 (45 targets rank above the first decoy and pass at 2%). In the other four HeLa assays the smallest q-value is 0.0083–0.0092, so with about 62 targets per assay the 1% threshold sits at the resolution limit. Training-seed spread is small (N_T range ≤ 2 within an assay for Context).
- Evidence: Drive `ctx-017/results/2026-11-13_baselines_perc305` (summary, counts, figure PDF/PNG, the three scripts, input checksums; upload sizes match). Raw runs in `/home/user/ctx017-runs/baseline` (session only).
- Limits: two datasets, five assays each; not evidence of general FDR validity. Ariana updated the Google record on 2026-11-13; it must be re-read before any further edit.
- Next action: Ariana reviews the baselines and the HeLa 1% observation; `CTX017-005` (ablation driver) next.

#### 2026-11-13 — reporting decisions after the baselines

- Decisions (Ariana): report passing targets and entrapment FDP at both 1% and 5% q-value for both datasets (2% stays in the counts files). This changes reporting only; Percolator and context-ms training settings are unchanged. Figure colours: dark red = Context, light red = targeted Percolator (reference only), gray = Percolator (full file).
- Attempt/result: `tmp/ctx017/baseline_figure.R` recoloured and the figure regenerated; `baseline_figure_v2.png`, `.pdf`, and `.R` uploaded to Drive `ctx-017/results/2026-11-13_baselines_perc305` (sizes match). The v1 files remain there.
- Noise floor for later ablations: for Context at 1%, passing targets differ by at most 2 peptides between training seeds 1–3 on the same assay.
- Next action: `CTX017-005` (ablation driver).

### r2.2 checklist additions (approved by Ariana 2026-11-13)

- `CTX017-002` scope under r2.2: both datasets. Ariana judged the mouse data usable and supplied the HeLa data; the SHA-256 inventory of the 80 input files for the 10 assays is in Drive (`inputs_sha256.txt`). Marked complete.
- `CTX017-012` scope under r2.2: both datasets from the start, not "when supplied".
- [x] `CTX017-016` Download, check, and inventory the HeLa dataset; choose the first five complete assays (masked1–5). Check: file set, features, r, and entrapment m/z distances logged (2026-11-13).
- [ ] `CTX017-017` Closing benchmark check: after the analysis, rerun all features on the 10 assays and confirm the results match the starting benchmark (`2026-11-13_baselines_perc305`). Depends on: 011. Check: outputs identical or differences explained.
- Reporting rule for all later tasks: 1% and 5% q-value, both datasets in every figure and table, and each step returns to Ariana with data, commands, and outputs.

#### 2026-11-13 — r2.2 approved; guide and canonical plan updated

- Decision (Ariana): formal approval of r2.2, with the instruction to keep a backup before modifying files and to continue to `CTX017-005`.
- Attempt/change: saved a backup copy of the Google record in Drive ("BACKUP 2026-11-13 (before r2.2 edits) …"). Re-read the record and kept Ariana's own additions (per-round feature removal, saving the starting benchmark, the closing all-features check, her thresholds, stop-and-start reporting). Applied the r2.2 text and three decision-log rows. Wrote canonical plan revision 2 above the retained revision 1. The repository files were already committed (`a7eaa4a`) before these edits, which is their backup. Input data files are never modified; ablations work on copies.
- Next action: `CTX017-005`.

#### 2026-11-13 — `CTX017-005` ablation driver built and checked

- Attempt/change: wrote `tmp/ctx017/ablate.py`. For each assay and training seed it makes pruned copies of the background and reference feature files inside a new run folder, runs `python -m context run --engine percolator` on the copies, and counts N_T and N_E at each q-value threshold. It never writes to the input files, refuses an existing run folder, checks input SHA-256 before and after, and records each run as ok or FAILED with the Percolator and context-ms versions.
- Commands (with `percolator` 3.05 first on `PATH` and `CONTEXT_PY=/home/user/context-env/bin/python`): `python3 ablate.py --label all_features --exclude none --dataset mouse=<mouse dir> --dataset hela=<hela dir> --assays mouse=<masked2–6 assays> --assays hela=<masked1–5 assays> --seeds 1,2,3 --out /home/user/ctx017-runs/ablation --jobs 3`.
- Results:
  - No exclusions, 10 assays × 3 training seeds: 30 of 30 ok in 4 min 10 s. Peptide outputs and weights files are byte-identical to the starting benchmark (30 of 30 each); all 1,500 count rows equal the baseline; all 30 input files unchanged.
  - Rejected with a clear error and exit 1, creating nothing: an unknown column, a non-feature column (`Label`, `Proteins`), every feature at once, and an existing run folder.
  - One real exclusion as a mechanics test only (`scribe`, mouse masked2 and HeLa masked1, seed 1): runs ok; `scribe` is absent from the weights; pruned copies keep `id`, `Label`, `ScanNr`, `sequence`, `Proteins` and the same row count. This single run is not evidence about `scribe`.
  - Failure handling: with a stand-in `percolator` that exits 3, the run is recorded as FAILED, no counts are written, and the driver exits 1.
- Error found and fixed: the feature files use Windows (CRLF) line endings, so the last header column read as `Proteins\r`. The "every feature excluded" check did not fire and `Proteins` was not recognized as protected. Fixed by stripping `\r\n` when reading column names and keeping each file's line endings in the copies. Re-tested: both rejections work, and a no-exclusion copy is byte-identical to its input. The 30-run check above ran before the fix; it is still valid because its copies and outputs were byte-identical to the inputs and the benchmark.
- Evidence: Drive `ctx-017/results/2026-11-13_ablation_driver_check` (driver, run status, counts, input checksums). Raw runs in `/home/user/ctx017-runs/ablation` (session only).
- Next action: `CTX017-006` (screening and decoy-artifact scripts) needs Ariana's go-ahead; her approval so far covers `CTX017-001` to `005`.

#### 2026-11-13 — Ariana's standing instruction on prediction settings (applies to CTX-018's open question above)

- Ariana (comment on the CTX-018 log line asking "Context's defaults (NCE 27, charge 2) or the library's (NCE 33, charge 3)?"): always use NCE 27 and charge +3 for all DIA or PRM data unless she says otherwise; these are the instrument-method defaults and the fallback when in doubt.
- Note for whoever picks up CTX-018: that log line describes Context's current default as NCE 27 with charge 2, which differs from the charge +3 she specified. Confirm with Ariana whether the Context default should change. Not acted on here; CTX-017 does no prediction.

#### 2026-11-13 — guide backup removed; feature list sent for Ariana's review

- Ariana clarified that her backup instruction meant the data, not the guide, and said the guide copy could be deleted. Moved "BACKUP 2026-11-13 (before r2.2 edits) …" to Drive trash after confirming its ID and title; the guide itself is untouched. The data are protected as before: the downloaded zips are kept, the extracted copies are read-only, and the driver works on pruned copies and checksums the inputs.
- Feature columns (27), sent to Ariana for confirmation: `primary`, `evalue`, `correlationToGaussian`, `correlationToPrecursor`, `isIntegratedSignal`, `isIntegratedPrecursor`, `numPeaksWithGoodCorrelation`, `HyperScore`, `xCorrLib`, `xCorrModel`, `scribe`, `numberOfMatchingPeaks`, `numberOfMatchingPeaksAboveThreshold`, `averageFragmentDeltaMasses`, `isotopeDotProduct`, `averageParentDeltaMass`, `lnSp`, `maxLadderLength`, `numMissedCleavage`, `pepLength`, `charge1`, `charge2`, `charge3`, `charge4`, `precursorMz`, `precursorMass`, `RTinMin`. Not features: `id`, `Label`, `ScanNr`, `sequence`, `Proteins`.
- Observation from the context-ms 0.1.3 source (`context/io.py`): the Percolator engine trains on every column between `ScanNr` and `sequence` (`detect_feature_cols`), minus near-constant ones (σ < 1e-6 on the background). context-ms's `ENCYCLOPEDIA_METADATA_COLS` list (`TD`, `pepLength`, `charge1`–`charge4`, `precursorMass`, `RTinMin`, `midTime`, `numberOfMatchingPeaksAboveThreshold`, `primary`) is applied only by its mProphet engine. In the baselines, all five mouse assays dropped `charge1` and `charge4` as near-constant; the HeLa assays dropped none.
- Next action: Ariana confirms the feature list and gives the go-ahead for `CTX017-006`.

#### 2026-11-13 — feature list confirmed; `CTX017-006` and `CTX017-007` authorized

- Decisions (Ariana): the 27 columns are the correct feature columns, but `charge1`–`charge4` together encode the peptide charge and count as one feature, "charge". So there are 24 features: 23 single columns plus the charge group, which is always screened and removed as a unit. She authorized committing and pushing, and moving on to `CTX017-006` and `CTX017-007`.
- Next action: screening and decoy-artifact scripts on both datasets, then Round A evidence to Ariana.

#### 2026-11-13 — `CTX017-006` screening done; `CTX017-007` Round A evidence sent to Ariana

- Attempt/change: wrote `tmp/ctx017/screen.py` (stages 1–2, no training) and `tmp/ctx017/screen_figures.R`. `charge1`–`charge4` are treated as one feature, "charge" (the charge number). Command: `python screen.py --out <out> --dataset mouse=<dir> --dataset hela=<dir> --assays mouse=<masked2–6> --assays hela=<masked1–5>` (41 s), then `Rscript screen_figures.R <out>`.
- Populations per assay: background targets/decoys about 14,600/10,600 (mouse) and 79,600/74,700 (HeLa). Reference: mouse 149–152 assay targets, 426–474 entrapment rows, 457–477 decoys; HeLa 59–62 assay targets, 67–91 entrapment rows, 69–92 decoys.
- Results (24 features, both datasets):
  - Data quality: no non-finite values. On the background, `charge1` and `charge4` are near-constant in mouse (context-ms drops them); the combined charge feature varies in both datasets.
  - Correlation clusters at |ρ| ≥ 0.9, the same in both datasets: `HyperScore`+`numberOfMatchingPeaks` (0.93 mouse, 0.95 HeLa); `isotopeDotProduct`+`averageParentDeltaMass` (0.92, 0.94; negative); `pepLength`+`precursorMass` (0.97, 0.98). That gives 21 ablation units.
  - Assay target vs entrapment AUC on the reference (mouse, HeLa): highest `primary` 0.99, 0.99; `numPeaksWithGoodCorrelation` 0.98, 0.94; `HyperScore` 0.93, 0.97; `maxLadderLength` 0.91, 0.96; `numberOfMatchingPeaks` 0.92, 0.96; `evalue` 0.94, 0.87; `xCorrLib` 0.88, 0.94. Within 0.06 of 0.5 in both: `numMissedCleavage` 0.48, 0.47; `pepLength` 0.55, 0.52; `precursorMz` 0.54, 0.51; `precursorMass` 0.56, 0.51; `RTinMin` 0.50, 0.48; `charge` 0.53, 0.50. `averageParentDeltaMass` separates in the other direction (0.26, 0.31), as does `averageFragmentDeltaMasses` in mouse (0.31; HeLa 0.51).
  - Background target vs decoy AUC is modest everywhere (mouse up to 0.63, HeLa up to 0.53), as expected when many background targets are false.
  - Decoy-artifact check (entrapment vs decoy AUC, pooled, 95% bootstrap interval): all within 0.445–0.522. Intervals excluding 0.5: mouse `primary` 0.519, `correlationToGaussian` 0.520, `numMissedCleavage` 0.486, `pepLength` 0.481, `precursorMass` 0.480; HeLa `correlationToGaussian` 0.445, `correlationToPrecursor` 0.460, `numMissedCleavage` 0.479. Only `numMissedCleavage` points the same way in both datasets; `correlationToGaussian` points in opposite directions. With 48 intervals, about 2 would exclude 0.5 by chance.
  - Background-vs-reference shift (KS): mouse at most 0.11. HeLa is much larger: `numMissedCleavage` 0.62 (targets) and 0.59 (decoys), `charge` 0.49 and 0.53, `precursorMass` 0.33 and 0.34, and 0.25–0.39 for most score features on targets.
- Recommendation file (`roundA_recommendations.tsv`, kept apart from the evidence): all 21 units enter ablation; no feature dropped up front. Six features are labelled "removal candidate" at low confidence (`numMissedCleavage`, `pepLength`, `precursorMz`, `precursorMass`, `RTinMin`, `charge`); the other 18 are "insufficient evidence" until ablation. `if_applied` is "not yet measured" for all.
- Evidence: Drive `ctx-017/results/2026-11-13_roundA_screening` (7 tables, 4 figure PDFs, both scripts; upload sizes checked). Figures also sent in chat.
- Limits: screening looks at each feature on its own and cannot show whether the model needs it. Two datasets, five assays each.
- Next action: Ariana's Round A decisions (which units enter ablation; any up-front drops). `CTX017-007` stays open until they are recorded.

#### 2026-11-13 — Round A write-up saved; hand-off note for the next session

- Attempt/change: at Ariana's request, saved the Round A evidence summary and Claude's recommendation as `roundA_summary_and_recommendation.md` in Drive `ctx-017/results/2026-11-13_roundA_screening`. Ariana authorized committing and pushing this log, and gave standing permission to create files in her `ctx-017` Drive folder.
- State for whoever continues (Ariana is switching agents):
  - Waiting on Ariana's Round A decisions: which of the 21 units enter the removal runs, any up-front drops, and whether to add the peptide-property family run (`numMissedCleavage`, `pepLength`, `precursorMz`, `precursorMass`, `RTinMin`, `charge`). Record them in the guide's Feature decisions table and here, then close `CTX017-007` and start `CTX017-008`.
  - Scripts live in the ignored folder `tmp/ctx017/` and are not in Git. Copies are in Drive: `run_baselines.sh`, `baseline_counts.py`, `baseline_figure_v2.R` (`2026-11-13_baselines_perc305`); `ablate.py` (`2026-11-13_ablation_driver_check`); `screen.py`, `screen_figures.R` (`2026-11-13_roundA_screening`).
  - A new cloud session starts without the data, tools, or run outputs. Re-download `mouse.zip` and `test.zip` (HeLa) from Drive `ctx-017`; reinstall R with ggplot2/patchwork/dplyr/tidyr/readr, context-ms 0.1.3 and pyIsoPEP 0.1.4; extract `bin/percolator-v3-05.centos` from `libs/maccoss/encyclopedia/6.9.23/encyclopedia-6.9.23.jar` and put it first on `PATH` as `percolator`. Then rerun `ablate.py --exclude none` and confirm it matches `baseline_counts.tsv.gz` before any removal run.
  - Reporting rules: both datasets in every figure and table; 1% and 5% q-value; dark red = Context, light red = targeted Percolator, gray = Percolator; evidence first and recommendation separate; each step returns to Ariana with data, commands, and outputs.
  - Git: work on branch `ctx017`; each commit and push needs Ariana's instruction, and a hook message is not authorization.
