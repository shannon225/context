# CTX-019 — Bootleak 2.0 canonical plan, revision 1

- **Status: pending Ariana's approval.** Written on 2026-11-13 at Ariana's direction ("Go ahead and make the plan, then commit and push"). It becomes frozen for the implement/review loop once Ariana approves it together with the `TASKS.md` checklist. Until then it may still be revised.
- Google implementation record: https://docs.google.com/document/d/1VT-sY1rvrERuoMl1OY4to79TicAT01Rtq8mheJy0B04/edit — draft r0 (Docs revisionId `AHj4eMR4dPB_IKLpnKZpqs7pv8olPoNwQivy84_Qu-RhtIfQ9JBOuOuJFYfPMFCVMFi8xuv-INzazH8tI51DMw_Inki-trWQ2ux4ib51Fwg`, 2026-11-13), including Ariana's edits of that day.
- Checklist and log: `TASKS.md`, section "CTX-019 — Bootleak 2.0".
- Branch: `bootleak2`, created 2026-11-13 from `ariana/scoring` at `7d0f5240411e8177db4744bcc6500166f9e5255a` (includes the BOOTLEAK fixes).
- Dependencies, unchanged: EncyclopeDIA 6.9.23 and msrawjava-core-nolice 26.7.31 (from `pom.xml`); Java 17; JUnit 4.

## Intent

1. **Part 1 — independent seeds (confirmed scope).** Every bootstrap seed draws all of its targets, initial and replacement, from its own random stream.
2. **Part 2 — m/z centering (pending evidence).** First measure where targets sit within their MS2 isolation windows. Ariana then decides whether the bootstrapper should require centered targets.

## Current behavior (verified 2026-11-13 on `7d0f524`)

- `TargetedBootstrapper.selectTargets(numberOfPeptides, aaConstants, i, entries, …, excludedTargetSequences)` sets `randomValue = 1 + i` (`TargetedBootstrapper.java:110`). It then advances the value with EncyclopeDIA's `RandomGenerator.randomInt` (an LCG: `(x·1103515245 + 12345) mod 2^32`, then `rem 2^31−1`) and picks `Math.abs(randomValue) % entries.size()`.
- `selectMask` calls `selectTargets(…, i = seed, …)` for the initial draw. For replacement round *r* it calls `selectTargets(…, i = seed + r, …)` (`:406`). So round *r* of seed *s* restarts the stream at `s + r + 1`, which is the initial starting point of seed *s + r*. Seeds that need replacements therefore reuse another seed's draws.
- Replacement happens when `selectDecoys` cannot find `trapsPerTarget` eligible entrapments within 1000 ppm for a target. The target is then logged as "Only found k of n requested entrapment peptides for target …", rejected, and replaced.
- `writeMaskedFile` copies a target's MS2 scans when the scan's isolation window contains the target m/z (`getStripes(mz, rtStart, rtStop)`: `IsolationWindowLower <= mz <= IsolationWindowUpper` and `ScanStartTime` between the bounds, in seconds) and the scan's precursor m/z is within ±`halfWindowWidthMz` of the target (`:484–491`). Where in the window the target sits is not checked.
- `selectTargets` has no caller outside `TargetedBootstrapper`.

## Populations and identities (unchanged by this feature)

- Bootstrap targets are the **reference** peptides of the simulated assay; their MS2 and MS1 scans are copied into the masked `.dia`.
- Entrapments are library precursors near each target's m/z whose data is deliberately not copied. Any detection of one is a known false discovery.
- Target identity is `LibraryEntry.getAccuratePeptideModSeq(aaConstants)` (modified sequence). Charge is kept but does not count toward uniqueness, so one sequence appears at most once per seed. Entrapment rules (same charge, ±10→1000 ppm widening, RT/m/z separation, closest first) are unchanged.
- Nondeterminism: none expected. The LCG is deterministic, and the `HashSet<Integer>` of drawn indices iterates deterministically for `Integer` keys. Seeds are the integers 1…`maxSeed` from the CLI.

## Part 1 — seed independence

### Change
- `selectTargets` replaces its `int i` parameter with a one-element `int[] randomState`. It reads the current value from `randomState[0]`, advances it with `RandomGenerator.randomInt` exactly as today, and writes the last value back before returning, including on the exception path.
- `selectMask` creates `int[] randomState = {1 + seed}` once per seed. It passes the array to the initial call and to every replacement call, and stops passing `i + replacementRound`.
- Nothing else changes: entrapment search, masking, file writing, log text, CLI.

### Compatibility
- **Unchanged:** seeds whose initial targets all get entrapments (no replacement round) draw the same indices in the same order, so their `_assay.dia`, `_assay.txt` and `_entrapment_assignments.tsv` stay identical.
- **Changed:** seeds with at least one replacement round get different replacement targets, and therefore different entrapments, from the first replacement onward.
- Older outputs that had a replacement round can be identified by "Only found … entrapment peptides" lines in their logs. Regenerate them if they are used as independent replicates.
- CLI syntax, file names, columns and formats are unchanged.

### Tests (JUnit 4, `TargetedBootstrapperTest`)
1. **Forced-replacement regression test.** A synthetic library built with the existing `entry(...)` helper, in which some drawn targets have no same-charge candidate within 1000 ppm, so seed 1 needs a replacement round.
   - Assert that seed 1's replacement targets are not taken from seed 2's starting stream (seed 2's initial targets from `selectMask` on the same library). Where it is practical, assert the property rather than one exact random sequence.
   - Must fail on the current code and pass on the fix. Check by stashing only `TargetedBootstrapper.java`, then restoring it.
2. **Multi-round test.** A seed that needs two or more replacement rounds keeps advancing one stream: no round restarts at `1 + seed` or at another seed's start, and the requested number of targets is still met.
3. **No-replacement equivalence.** On a library or fixture where no replacement occurs, the targets per seed are unchanged before and after the fix (compared with a baseline captured before the change).
4. **Existing tests.** The 10 existing tests still pass, including the IL2/IL15 fixture integration test and `multipleSeedsLeaveNoTemporaryLibraryCopies`.
5. **Error paths unchanged.** Requesting more targets than library entries still throws `IllegalArgumentException`, and running out of unique peptides still throws `IllegalStateException`.

### Before/after evidence (log only)
- A scratch harness in ignored `tmp/ctx019/` runs `execute` on the IL2/IL15 fixtures for seeds 1–3 before and after the change. It compares `_assay.txt`, `_entrapment_assignments.tsv`, the selection log lines, and the masked `.dia` SQLite table contents.
- It also reports, per seed, whether a replacement round occurred and how many targets each pair of seeds shares.
- Known limitation from BOOTLEAK: this fixture's masked files hold few or no fragment spectra, so masked-file equivalence is weak evidence. The fixture may also need no replacements at all. Record what actually happened.

### Documentation
- `README.md`, "Simulated assays with `bootstrap`": one or two sentences saying each seed draws from its own random stream, replacements continue that stream, and outputs that had replacement rounds before this change differ.

## Part 2 — m/z centering

### 2a. Read-only diagnostic (no repository code)
- A Python script in ignored `tmp/ctx019/`, run with `python3 -I`, and an R script (ggplot2, patchwork, dplyr, tidyr; `theme_bw()`, Arial).
- **Inputs:** a raw acquisition `.dia`, its `_masked<seed>_assay.txt`, and its `_masked<seed>_assay.dia`. All are read only; the script opens copies or uses read-only SQLite URIs.
- **Report:**
  - The MS2 window layout from `spectra.IsolationWindowLower/Upper`: distinct windows, widths, overlap, and whether it looks non-overlapping, staggered, or GPF-combined.
  - For each target (`isDecoy = false`): the nearest containing window, the signed offset from that window's center, the distance to the nearer edge, whether M+1 (`+1.00335/z`) and M+2 fall outside the window, and the MS2 scan count in the masked file.
  - Summary tables by charge and window.
- **Checks:** the script refuses to overwrite outputs and records input paths with SHA-256. It is first run on the IL2/IL15 fixture as a self-check, then on Ariana's acquisitions. Those are not in the cloud container, so Ariana supplies them or runs the script herself.

### 2b. Decision round
- Present the evidence first and the recommendation second.
- Ariana decides:
  - whether to add centering;
  - the rule (maximum center offset in m/z, or an isotope-based margin);
  - whether it is on by default;
  - the approach: reject off-center targets (non-overlapping windows) or keep only the best-centered window per target (overlapping or staggered windows).
- Record the decision in the guide's decision log and in `TASKS.md`.
- If centering is added, that is a **material plan change**: propose a guide revision and plan revision 2, set approval to Pending, and append new tasks before implementing.

### 2c. Implementation sketch (only if approved in 2b; detailed in plan revision 2)
- Reuse `rawDia.getRanges()`, which `execute` already reads for `isolationWindowWarning`, and pass the window list to `selectTargets`.
- A target is eligible only if some window contains it with `|targetMz − center| <= maxCenterOffsetMz`. The boundary is inclusive unless Ariana decides otherwise.
- An optional 8th positional argument in `TargetedBootstrapperCLI`, so existing 2–7 argument invocations keep working. README and usage text are updated.
- Entrapment rules are unchanged; entrapment scans are never copied anyway.
- Tests: inclusive boundary, rejection near the window edge, unchanged behavior when centering is off, and a clear error when no centered target is available.

## Acceptance criteria

- [ ] Each seed's initial and replacement targets come from one stream starting at `1 + seed`; no replacement round restarts at another seed's start.
- [ ] Seeds without replacement rounds give identical outputs before and after the change; CLI syntax and file formats are unchanged.
- [ ] The new regression test fails on the old code and passes on the new; all `TargetedBootstrapperTest` tests pass on JDK 17.
- [ ] The full `-Ppackage verify` result on JDK 17 is recorded, with pre-existing failures named.
- [ ] README updated; affected older outputs explained.
- [ ] Diagnostic results presented; Ariana's centering decision recorded (and, if centering is added, its revision approved and implemented separately).
- [ ] Until Useful review findings, dispositions and residual risks recorded; Ariana's final review; Report written outside the repository.
- These checks do not establish improved sensitivity or general FDR validity. Small-fixture tests show only that the selection logic behaves as specified.

## Until Useful review

- Review the Part 1 diff (and Part 2c, if it is added) against this frozen plan. The review covers:
  - the smallest practical change and reuse of existing code;
  - no dependency changes;
  - unchanged outputs where promised;
  - test strength (fails on the old code);
  - scientific behavior: populations, identities, determinism.
- Record findings, dispositions, checks and residual risks in `TASKS.md`.
- `APPROVE` from the review means ready for Ariana's final review, not accepted. `NEEDS_INPUT`, `FAILED` and `STOPPED` halt automatic continuation.

## Out of scope

- Library decoy rows being drawable as targets (`LibraryFile.getAllEntries(false, …)`'s flag is the sqrt-intensity flag, not a decoy filter). This needs its own ID or decision.
- An RT peak-boundary filter (a wider RT window is recommended instead).
- Changing `RandomGenerator.randomInt` or the index mapping (`Math.abs(x) % size`), whose statistical quality has not been evaluated.
- Changes to EncyclopeDIA, msrawjava, or Context's scoring, Percolator, mProphet or FDR workflows.
- Git delivery planning; it starts only when Ariana asks.

## Open questions

1. Centering: whether, which rule, which default (decided in 2b).
2. MS2 window layout of Ariana's bootstrap acquisitions (answered by 2a).
3. CTX-019 priority, and whether CTX-016 to CTX-019 should be added to the backlog sheet (it lists CTX-001 to CTX-015).
4. Where the Report should be saved (the Downloads path does not exist in cloud sessions).
5. Whether 2a's result tables and figures should also be uploaded to a Drive folder, and which one.
