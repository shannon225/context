# TASKS: implement `MassListDecoyGenerator.resolveDecoysMessage`

Status: **implemented, committed and pushed 2026-10-11**. Approved by the owner 2026-10-11 (D1: reuse naming and fix the help text; D2: un-ignore and report only)

## Problem
`resolveDecoysMessage` is an auto-generated stub that returns `null` (since edcb3cb).
`ContextPercolatorExecutor.runContextPercolator` dereferences that result, so
`context.jar percolator -i … -l … -f … -massList …` dies with
`Cannot invoke "java.io.File.getAbsolutePath()" because "splitOn" is null`
before feature scoring.

## Intended behaviour (from the ignored tests)
- Mass list already has decoys: return it unchanged, whatever the flag.
- `generateDecoys == false`: return the mass list unchanged (`withoutTheFlagTheListIsNotTouched`).
- `generateDecoys == true` and no decoys: return the decoy-augmented list in `outputDirectory`.

## Planned change (minimal, reuses existing logic)
```java
public static File resolveDecoysMessage(File massList, File outputDirectory, String resolvedPrefix,
        boolean generateDecoys) throws IOException {
    if (!generateDecoys)
        return massList;
    return checkIfDecoysArePresent(massList, outputDirectory, 0);
}
```
- Adds `throws IOException`. The only production caller already throws `Exception`.
- Seed `0` matches `ContextFeatureScorer.prepareMassList`.
- `resolvedPrefix` is unused (see decision D1).

## Checklist
- [x] 1. Implement `resolveDecoysMessage` as above in `MassListDecoyGenerator.java`
- [x] 2. Remove `@Ignore` and the unused import from `MassListDecoyGeneratorTest` (2 tests)
- [ ] 3. Remove `@Ignore` from `ContextPercolatorExecutorTest` (2 tests): **Superseded** by task 9
- [x] 4. `pip install pyIsoPEP` in this session so the executor tests run instead of being skipped by `assumeTrue`
- [ ] 5. **Superseded** by task 9. `mvn test -Dtest=MassListDecoyGeneratorTest,ContextPercolatorExecutorTest`: all 4 pass (none skipped)
- [x] 6. Full `mvn test`: no regressions against the baseline
- [x] 7. Build the jar and run the raw `percolator` command on the IL2 fixture; confirm the log reaches
       "Scoring … against IL2_and_IL15_Combo.elib" and feature scoring runs
- [x] 8. Report the results; commit/push only after explicit authorization

- [x] 9. Restore `@Ignore` on both `ContextPercolatorExecutorTest` tests, with reasons naming the real causes
       (owner request, 2026-10-11). Check: both test classes run, 9 pass, 2 skipped, 0 fail.
- [ ] 10. Owner decision: whether to fix either `ContextPercolatorExecutorTest` test, and under which feature

## Decisions needed from the owner
- **D1. Name of the generated file.** Reusing `checkIfDecoysArePresent` writes
  `<assay>_td_assay.txt` (for example `IL2A_GPFDIA_0combined_masked0_td_assay.txt`). The CLI help text
  (`ContextPercolatorExecutorCLI` line 152) promises `<prefix>.assay.with_decoys.txt`, which would need new
  naming logic. Proposal: reuse the existing naming and fix the help line to match.
- **D2. `standardPercolatorProducesNativeAndPyIsoPEPReports` is unrelated to this stub.** It never calls
  `resolveDecoysMessage`. It passes `null` engine/workflow names into `runPercolator`, and
  `DirectoryOptions.engineDirectory(dir, null)` does `new File(dir, null)`, which throws a
  `NullPointerException`. It also expects a `standard-percolator/*.pyisopep.txt` report, and `runPercolator`
  never calls pyIsoPEP. Un-ignoring it will most likely fail for reasons outside this feature.

## Observed, not in scope
- `ContextFeatureScorer.prepareMassList` always calls `checkIfDecoysArePresent`, so a targets-only list
  still gets decoys during scoring (written next to the DIA base name) even without `-generateDecoys`.
  As a result, `-generateDecoys=false` does not actually stop decoy generation on this path.
- The task refers to the repository's CLAUDE.md, but no CLAUDE.md exists in the repo.

## Baseline (2026-10-11, before any change)
`MassListDecoyGeneratorTest`: 9 run, 0 fail, 2 skipped. `ContextPercolatorExecutorTest`: 2 run, 0 fail, 2 skipped.
pyIsoPEP and percolator are not on PATH. pyIsoPEP 0.1.4 can be installed from PyPI.

## Results (2026-10-11, after the change)
- `MassListDecoyGeneratorTest`: 9/9 pass, including both previously ignored tests.
- `ContextPercolatorExecutorTest`: 0/2 pass. Neither failure involves `resolveDecoysMessage`:
  - `standardPercolatorProducesNativeAndPyIsoPEPReports`: NPE at `DirectoryOptions.engineDirectory:30`,
    as predicted in D2 (`null` engineName). The test would also need `runPercolator` to write a pyIsoPEP report.
  - `contextPercolatorProducesTransferredModelReports`: now gets past the null and through feature scoring,
    then fails in `ContextFeatures.requireCompetable` with "The reference set has 0 targets and no decoys".
    All 10 assay target peptides are in `IL2_and_IL15_Combo.elib`, but scoring the masked DIA processed
    only 24 peptides (all mouse background), so the reference split is empty.
- Raw CLI (`java -jar target/context-0.0.2-SNAPSHOT.jar percolator …`, shaded jar from `mvn -Ppackage package`):
  reaches "Scoring … against IL2_and_IL15_Combo.elib", writes `_background`/`_reference` feature files,
  then exits 2 with the same reference-set error.
- Full `mvn test`: 65 run, 3 errors, 1 skipped. The third error, `ContextPercolatorRunnerTest.commandMatchesPinnedDependencyExceptSeedForBothRounds`
  ("Could not preserve Percolator executable for debugging"), fails identically on the unmodified baseline.
- Checklist item 5 is therefore only partly met (2 of 4).

## Log
- 2026-10-11, tasks 1-7: implemented and validated; results above. Validation ran on JDK 21 with system Maven 3.9.11,
  not the JDK 17 / `build-cache` Maven that CLAUDE.md specifies. pyIsoPEP was installed with system `pip` (as root),
  not into the project-local `./build.sh pyisopep` virtualenv.
- 2026-10-11, task 9: plan change requested by the owner (supersedes D2 and tasks 3 and 5). Both executor tests
  are `@Ignore`d again, with reasons naming the real causes. Additional evidence for the context test: every
  assay target is in the library with matching m/z and RT, and the masked DIA has scans in each assay window
  over the expected RT range, but EncyclopeDIA still emits no features for any assay peptide. Command:
  `mvn -q test -Dtest=MassListDecoyGeneratorTest,ContextPercolatorExecutorTest` gives
  MassListDecoyGeneratorTest 9 run, 0 fail, 0 skipped; ContextPercolatorExecutorTest 2 run, 0 fail, 2 skipped.
  Next: owner decides task 10. No commit has been made.
- 2026-10-11, task 10 (investigation, read-only): the owner's hypothesis is that the assay and DIA fixture are
  mismatched because `TargetedBootstrapper` changed after the fixtures were generated. Evidence: both files arrived
  in the same commit (681fe70, 2026-08-01). The DIA's 8 isolation windows (2 m/z wide) and their RT ranges
  (about ±0.24 min) are centred exactly on this assay's 10 targets, so the DIA was masked with this assay or one
  with the same targets. A fragment-presence check (library top 6 fragments vs DIA scans, 10 ppm) found 0-2 of 6
  for assay targets, and also 1-2 of 6 for background peptides EncyclopeDIA did score. The check is therefore
  inconclusive. No code changed. Next: owner decides whether to regenerate the fixture pair with the current
  `TargetedBootstrapper` (needs the unmasked source DIA, which is not in the repo).

## Fixture regeneration test (task 10 follow-up): Approval **Approved 2026-10-11** (owner supplied CD8 files and parameters)
Feature ID: none assigned (no CTX ID supplied; not inventing one). Google guide: none (owner waived for this fix).
Hypothesis (owner): the masked DIA/assay fixture pair predates 14 `TargetedBootstrapper` commits (681fe70 → 2e1b5fe,
including faefa66 "only write targets to new masked PRM files" and several decoy-selection fixes). Regenerating
the pair with the current code should give a reference set that contains assay targets.
Acceptance: with a regenerated pair, `ContextFeatureScorer` puts at least one assay target in the reference set,
and `contextPercolatorProducesTransferredModelReports` either passes or fails for a different, documented reason.
- [ ] 11. Get the unmasked source DIA (`IL2A_GPFDIA_0combined.dia`) into the session's scratchpad, outside the repo.
- [ ] 12. Run the current `TargetedBootstrapperCLI` in the scratchpad: library `IL2_and_IL15_Combo.elib`,
       seed 1, 10 peptides, RT/m/z widths as confirmed by the owner. Record the command, parameters and outputs.
- [ ] 13. Run `ContextFeatureScorer` and the raw `percolator` command on the regenerated pair (scratch copies).
       Record the reference target/decoy counts against the old fixture (0/0).
- [ ] 14. Report the result. Do not modify `src/test/resources` fixtures; replacing them is a separate decision
       because `ContextFeatureScorerTest` asserts 17 features and three other test classes use the same fixture.
- 2026-10-11, task 11: **blocked**. The owner supplied `cd8_2mz_gpf.dia` (1.82 GB), `library_cd8.elib` (157 MB) and
  `mus_musculus_reviewed_uniprot.fasta` (11.5 MB) in Drive folder 1tfUrj8fwagp8UMeox1Nkq5jOx8-bslE7. The Drive connector
  returns file contents base64-encoded inside the conversation, so it cannot carry files this size. A direct HTTPS
  download from drive.google.com is refused by the environment network policy (proxy CONNECT 403).
  Parameters for task 12, as given by the owner: 2 m/z isolation window (`halfWindowWidthMz` = 1.0), 5 min RT window
  (`halfWindowWidthRT` = 2.5, because the code applies it as rtCenter ± 60 × value seconds), 10 target peptides,
  1 entrapment decoy per target, 1 seed. Command: `TargetedBootstrapperCLI library_cd8.elib cd8_2mz_gpf.dia 1 10 2.5 1.0 1`.
  The old fixture used about ±0.25 min, so this RT window is about 10× wider. Next: owner chooses a transfer route.
- 2026-10-11, task 12: the owner confirmed the 5 min RT window is the total width, so the command is
  `TargetedBootstrapperCLI library_cd8.elib cd8_2mz_gpf.dia 1 10 2.5 1.0 1`. The owner plans to continue on a local
  machine, using `claude --teleport` from a clean checkout. This requires the branch to be pushed first.
- 2026-10-11, task 8: the owner authorized one combined commit (code, `TASKS.md` and the new root `CLAUDE.md`) and the
  push to `claude/amazing-pasteur-1ac813`. The owner had the `CLAUDE.md` rule changed so that tracking files go in the
  code commits they record. That file was added at the repo root, from the copy the owner supplied in this session,
  with only that rule edited. Purpose: let `claude --teleport` carry this work to the owner's machine.
