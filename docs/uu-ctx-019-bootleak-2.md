# CTX-019 — Bootleak 2.0 canonical plan, revision 2 (approved)

- **Status: approved by Ariana on 2026-11-14; frozen for implementation/review.** Approved together with guide r1 and the revised checklist. Current execution scope is CTX019-001 through CTX019-003; Part 2 parameters retain their separate decision checkpoint.
- Google implementation record: https://docs.google.com/document/d/1VT-sY1rvrERuoMl1OY4to79TicAT01Rtq8mheJy0B04/edit — approved guide r1, 2026-11-14 (Docs revision ID `AHj4eMSZJhcv9hz96icJcBkelR7Fv2FIiTKssi-YM4ew1Y3pQFjERwnRf-mcQMWuIKj5BxfwRL1qbjksHt1WI_IXZmc3f1rZiSkIwfs-44Q`).
- Checklist and log: `TASKS.md`, section "CTX-019 — Bootleak 2.0".
- Branch: `bootleak2`, created 2026-11-13 from `ariana/scoring` at `7d0f5240411e8177db4744bcc6500166f9e5255a` (includes the BOOTLEAK fixes).
- Dependencies, unchanged: EncyclopeDIA 6.9.23 and msrawjava-core-nolice 26.7.31 (from `pom.xml`); Java 17; JUnit 4.

## Intent

1. **Part 1 — independent seeds (confirmed scope).** Every bootstrap seed draws all of its targets, initial and replacement, from its own random stream.
2. **Part 2 — acquisition-aware assay selection (proposed).** Estimate a defensible target concurrency limit, fill target slots up to that limit, and reassign entrapments across targets before rejecting a target. Centered m/z placement and RT intervals covering observed peptide peak bounds are feasibility constraints to measure before choosing defaults. Do not assume every proposed constraint will be enabled in production.

## Current behavior (verified 2026-11-13 on `7d0f524`)

- `TargetedBootstrapper.selectTargets(numberOfPeptides, aaConstants, i, entries, …, excludedTargetSequences)` sets `randomValue = 1 + i` (`TargetedBootstrapper.java:110`). It then advances the value with EncyclopeDIA's `RandomGenerator.randomInt` (an LCG: `(x·1103515245 + 12345) mod 2^32`, then `rem 2^31−1`) and picks `Math.abs(randomValue) % entries.size()`.
- `selectMask` calls `selectTargets(…, i = seed, …)` for the initial draw. For replacement round *r* it calls `selectTargets(…, i = seed + r, …)` (`:406`). So round *r* of seed *s* restarts the stream at `s + r + 1`, which is the initial starting point of seed *s + r*. Seeds that need replacements therefore reuse another seed's draws.
- Replacement happens when `selectDecoys` cannot find `trapsPerTarget` eligible entrapments within 1000 ppm for a target. The target is then logged as "Only found k of n requested entrapment peptides for target …", rejected, and replaced.
- `writeMaskedFile` copies a target's MS2 scans when the scan's isolation window contains the target m/z (`getStripes(mz, rtStart, rtStop)`: `IsolationWindowLower <= mz <= IsolationWindowUpper` and `ScanStartTime` between the bounds, in seconds) and the scan's precursor m/z is within ±`halfWindowWidthMz` of the target (`:484–491`). Where in the window the target sits is not checked.
- `selectTargets` has no caller outside `TargetedBootstrapper`.

## Populations and identities

- Bootstrap targets are selected from the library's `entries` (target) table and become the **reference** peptides of the simulated assay; their MS2 and MS1 scans are copied into the masked `.dia`. The pinned EncyclopeDIA 6.9.23 `getAllEntries(false, ...)` SQL selects from `entries`, not `decoyentries`; the boolean controls sqrt intensity. Verify this population boundary in the pilot and a mixed-table test library.
- Entrapments are also drawn from target-library precursors near each reference's m/z, but their data is deliberately not copied. Any detection of one is a known false discovery. Library search decoys in `decoyentries` are a separate population and must not be considered for either role.
- Target identity is `LibraryEntry.getAccuratePeptideModSeq(aaConstants)` (modified sequence). Charge is kept but does not count toward uniqueness, so one sequence appears at most once per seed. Current entrapment eligibility uses same charge, ±10→1000 ppm widening, and RT/m/z separation, then commits the closest candidates first. Part 2 preserves eligibility but proposes reassignment across targets instead of closest-first commitment.
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

## Part 2 — acquisition-aware target and entrapment selection

### 2a. Read-only feasibility study on Ariana's data

- Inputs, read only: `/Users/arianashannon/Documents/Playground_for_Context/ctx019/library_cd8.elib`, `cd8_2mz_gpf.dia`, and `mus_musculus_reviewed_uniprot.fasta`. The FASTA's role in this study is provenance/compatibility; the schedule and matching analysis can use the `.elib` and `.dia` without a FASTA search. Record file hashes, software version and query parameters. Do not copy the 1.7 GB `.dia` or publish these files.
- Ariana confirmed on 2026-11-14 that `.elib` `RTInSecondsStart/Stop` are observed peak boundaries. The supplied `.elib` has 99,147 `entries`, no `decoyentries`, and populated ordered bounds for every entry. The library RT lies within those bounds for 98,336 entries; 358 have RT before start, 453 after stop. Ariana chose to exclude these 811 entries from **both** reference-target and entrapment selection for this implementation. This is an eligibility policy, not evidence that their identifications are wrong; the entry RT may be a corrected or otherwise distinct RT coordinate. Record the excluded count for each input library and fail clearly if too few eligible precursors remain. Restrict the pilot to `entries`; verify with a mixed-table fixture that `decoyentries` are excluded.
- Inspect actual MS2 isolation window lower/upper bounds, width, overlap, scan cadence and retention-time coverage in the `.dia`. Ariana reports 4 m/z overlapping/staggered acquisition windows that deconvolve to effective 2 m/z windows, 2 m/z non-overlapping windows, and possible 1 or 2 m/z GPF windows; verify the supplied file's actual layout rather than assuming one pattern. For each potential target, calculate center offset and nearer-edge distance in each containing window, M+1/M+2 coverage, and available MS2 scan count across its proposed RT interval. Compare with the existing masked-file behavior. GPF or staggered windows may make a target's best window depend on cycle.
- Plot target-only concurrency for the existing symmetric schedule and for a schedule that covers observed RT start/stop. Entrapments are library challenges, **not physical PRM measurements** and do not consume target slots. Report peak and distribution of concurrency, target yield, interval lengths, edge cases and how these vary by m/z and RT.
- Estimate capacity from acquisition timing when possible: require a chosen minimum points per chromatographic peak, giving a maximum cycle time `T_cycle,max = peak_width / points_required`; then derive an approximate target-slot limit `K = floor((T_cycle,max - T_other) / T_MS2)`. State which peak-width statistic, scan time, overhead and safety margin were used. For rough total-target estimates, use the peptide elution span rather than the full gradient; Ariana notes that elution often starts 3–7 min after gradient start and ends 3–7 min before gradient completion. Measure this span in the supplied run rather than hardcoding a margin. The DIA scan cadence is evidence for the simulation, not an automatic PRM instrument-capacity guarantee. If timing data are insufficient, compare a sensitivity range of user-supplied `K` values and leave the default undecided. Never infer `K` solely from library size or the observed 14-target peak in the example assay.
- Compare feasible target count and entrapment fill rate under baseline selection, a capacity rule alone, and capacity plus centering and observed-bound coverage. Report failures and runtime/memory, including sparse RT–m/z regions, narrow/long peaks, overlapping windows, and demanding trap ratios. This pilot is read-only; it does not change bootstrapper outputs.

### 2b. Decision round and implementation boundary

- Present the pilot evidence and a recommendation. Ariana decides the capacity rule/default and whether it is user-configurable; center tolerance or isotope margin and whether centering is default; handling of missing or reversed bounds in other libraries; and whether target spreading in sparse RT/m/z regions warrants a later experiment. Observed bounds and exclusion from both roles when a library RT falls outside them are already decided. Record remaining decisions in the guide and `TASKS.md` before changing scientific behavior. If the data show that the proposed rules cannot be supported, revise the plan/checklist for approval.
- The core selection proposal, once parameters are approved: keep `numberOfPeptides` as a hard requested total; exclude `entries` whose library RT falls outside their observed peak bounds from both assay roles; draw remaining candidates deterministically from each seed's stream and continue trying eligible candidates until the total is reached; admit a target only if its scheduled RT interval fits the concurrency limit at every overlapping time; score/choose a feasible m/z window according to the approved centering rule; then assign `trapsPerTarget` distinct eligible entrapments to all admitted targets with bipartite matching (augmenting paths or max flow). Compute each target–entrapment eligibility against the **entire selected target set** using the existing charge, m/z, RT-separation and identity rules. Reassign already chosen entrapments along an augmenting path before declaring a new target infeasible. If no complete assignment exists, try another target or backtrack according to a bounded, deterministic policy; fail clearly if the requested total cannot be reached; never silently reduce the requested total or trap ratio. Active preference for underfilled RT or m/z regions is a separate optional experiment.
- Keep reference targets, entrapments and library search decoys distinct, with only `entries` eligible for either assay role. Preserve modified-sequence uniqueness, charge matching, seed reproducibility, existing file columns and the no-entrapment-spectra rule. Entrapment matching changes *which* library challenges are paired, so old outputs with and without replacement may change once this part is enabled; the Part 1 no-replacement equivalence promise applies to the isolated seed fix, not the combined algorithm.
- First compare matching to the current nearest-first assignment on small and representative inputs. Use a local, deterministic augmenting-path implementation if it is sufficient; consider general max flow only if the measured conflicts require it. Bound candidate expansion and search so a 99k-entry library does not create an all-pairs graph. No new dependency is planned.
- A preference for underfilled RT/m/z regions is an **optional experiment**, not a default selection rule or acceptance criterion. Compare its yield and selected-population distribution with capacity-only selection; do not claim it is more representative of a real PRM assay or improves detectability without evidence.

### Implementation details to settle after the pilot

- Reuse `rawDia.getRanges()` for the m/z-window list. For overlapping windows, compare all containing windows and select the best usable one; do not assume the first containing window is the right choice. The precise center tolerance or isotope margin is a decision item.
- Read observed peak start/stop from the library through the repository's pinned EncyclopeDIA API if it preserves these fields. Verify that `ChromatogramLibraryEntry.getRtRange()` exposes the values used in the `.elib`; otherwise propose the smallest Context-side read path. Scheduling can shift the RT center and widen the window, provided both accepted bounds are covered and the output format remains compatible.
- Preserve existing 2–7 argument CLI invocations. Decide any new capacity or centering options only after the pilot; update README and usage text for approved options.
- Tests should cover center and RT boundaries, overlapping-window selection, capacity at peak overlap, an augmenting-path reassignment, impossible trap ratios, seeded determinism, and a large-library performance bound. Entrapment scans remain absent from masked files.

## Acceptance criteria

- [ ] Each seed's initial and replacement targets come from one stream starting at `1 + seed`; no replacement round restarts at another seed's start.
- [ ] For the isolated seed fix, seeds without replacement rounds give identical outputs before and after the change; the later selection algorithm may intentionally change assay contents. Existing CLI invocations and file formats remain accepted.
- [ ] The new regression test fails on the old code and passes on the new; all `TargetedBootstrapperTest` tests pass on JDK 17.
- [ ] The full `-Ppackage verify` result on JDK 17 is recorded, with pre-existing failures named.
- [ ] README updated; affected older outputs explained.
- [ ] Pilot results presented; Ariana's decisions on centering, RT-bound use, capacity and matching limits recorded, with a revised approval checkpoint if material parameters differ from this plan.
- [ ] The pilot reports a justified capacity range, target concurrency, target yield and complete-entrapment assignment rate on the supplied data, with timing and failure cases; Ariana approves final parameters before implementation of Part 2.
- [ ] Entries whose library RT falls outside observed peak bounds are excluded from both reference and entrapment selection; the excluded count and any consequent infeasibility are reported clearly.
- [ ] For any approved Part 2 implementation, no cycle exceeds the approved target cap; every scheduled target RT interval covers its accepted observed lower and upper peak bounds; every target has the requested number of distinct, eligible entrapments, including a case that needs reassignment; seeded runs are reproducible and impossible `numberOfPeptides` or trap requests fail clearly, without silently reducing them.
- [ ] Until Useful review findings, dispositions and residual risks recorded; Ariana's final review; Report written under `docs/reports/` in the repository.
- These checks do not establish improved sensitivity or general FDR validity. Small-fixture tests show only that the selection logic behaves as specified.

## Until Useful review

- Review the approved implementation diff against the frozen plan. The review covers:
  - the smallest practical change and reuse of existing code;
  - no dependency changes;
  - unchanged outputs where promised;
  - test strength (fails on the old code);
  - scientific behavior: populations, identities, determinism.
- Record findings, dispositions, checks and residual risks in `TASKS.md`.
- `APPROVE` from the review means ready for Ariana's final review, not accepted. `NEEDS_INPUT`, `FAILED` and `STOPPED` halt automatic continuation.

## Out of scope

- Library search-decoy generation or filtering inside the search engine. CTX-019's assay selection uses only the `.elib` `entries` population; `decoyentries` must stay excluded.
- A default centering threshold before the feasibility study and Ariana's decision. Handling of missing or reversed bounds in other libraries and any symmetric-window fallback require an explicit decision; the supplied library's 811 out-of-bound entries are excluded from both roles.
- Changing `RandomGenerator.randomInt` or the index mapping (`Math.abs(x) % size`), whose statistical quality has not been evaluated.
- Changes to EncyclopeDIA, msrawjava, or Context's scoring, Percolator, mProphet or FDR workflows.
- Git delivery planning; it starts only when Ariana asks.

## Open questions

1. Centering: whether, which rule and default (decided in 2b).
2. RT bounds: observed peak start/stop and exclusion of the 811 out-of-bound entries from both roles are decided by Ariana. After the pilot, decide padding, if any, and policy for missing or reversed bounds in other libraries.
3. Capacity: points-per-peak criterion, timing/overhead data, safety margin, default or required user parameter, and interval endpoint convention.
4. Matching: maximum candidate expansion and bounded backtracking policy; measure yield and resource use before freezing these details.
5. Verify the supplied `.dia` window layout against Ariana's 4 m/z staggered/deconvoluted and 1–2 m/z GPF description in 2a.
6. CTX-019 priority remains unset. Ariana reports CTX-016 is complete in another account/computer; reconcile backlog status separately, without changing CTX-019's technical approval scope.
7. Save the required Report under `docs/reports/` with the unique date-stamped filename specified in `AGENTS.md`. Ariana also provided a Google Drive `ctx-019` folder for a Report copy: https://drive.google.com/drive/folders/1mIQTNaHLO-9ph5fxVuF4OUeMtqFYo7ix?usp=sharing. Her permission covers the Report, not the input datasets or pilot outputs.

## Documentation handoff

- Ariana requested a final targeted-bootstrapper documentation pass. After implementation and before final validation/review, check README and CLI usage against actual inputs, defaults, file outputs, scheduling rules, seed behavior, exclusions and failure messages; fill gaps with concise examples. Do not reinterpret existing public syntax or formats without a separate approved change.
- Ariana says the existing random generator was externally validated. CTX-019 preserves it and does not repeat that validation; record this as Ariana's statement rather than a new validation claim.
