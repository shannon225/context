# Context implementation tasks

## CTX-014 — ScribeTwo folder feature scoring

- Guide: `docs/scribetwo-folder-feature-scoring.md`
- Google implementation record: https://docs.google.com/document/d/1IeHK6oC8eU32yRv7RgUM_-43RTafEkmplcp5D2vVUY4/edit
- Approved record revision: `ANLCKQnYKN02Zof-f5UUIrAmS0UiVhWmEKvSE7385q_oDEoENNji93rCd_kIoTkzKT6eVY_X-Jwi3C2IMXLtcMmBMH1AhVuwhN-_XkEP5Qg`
- Implementation record revision: `ANLCKQkCqT_65ma8Gwz35th5laPKaF8nHh9heiuo0UM3UhKporCednCU872K8VPPGNjnRwBF7zMskOPpuhqrIorwHVJlZu5x1DnZsMZTgR4`
- Approval status: Approved by Ariana Shannon on 2026-09-13; implementation authorized in the message beginning “I think this looks great.” Local commits authorized by “Please make those commits.” Push explicitly retained by Ariana.
- Final status: Accepted by Ariana; local commits created, awaiting Ariana's push

### Acceptance criteria

- [x] Default `_maskedN` Context folder behavior remains compatible.
- [x] Both folder CLIs accept the same optional literal `--input-prefix`; custom prefixes and regex metacharacters behave predictably.
- [x] All requested seeds and same-basename readable assay schedules are preflighted before scoring.
- [x] Existing Scribe single-run syntax and scientific generation/partition/training behavior remain compatible.
- [x] Folder runs use ascending seeds, fresh requested parameters, run-specific effective parameters, isolated basename-derived outputs, and aggregate failure reporting.
- [x] Help and README document required inputs, naming, defaults, examples, outputs, and failures.
- [x] Focused tests and full Java 17 Maven verification are recorded; native integration is run only when data are available.
- [x] Final diff review finds no unapproved public API, dependency, scientific, or unrelated changes.
- [x] Ariana completes final human review.

### Implementation checklist

- [x] `SFS-001` Record the approved guide revision and class-name decisions in `docs/scribetwo-folder-feature-scoring.md` and this checklist. Check: links, revision, approval, scope, and names are present.
- [x] `SFS-002` Establish the `origin/ariana/scoring` Java 17/Maven baseline. Files: work log only. Check: exact commit, versions, focused/full command results, and pre-existing failures are recorded.
- [x] `SFS-003` Add package-private `AssaySchedulePairMatcher` and `AssayScheduleInputPair`. Files: two new `io` classes. Check: literal prefix validation, range validation, immediate `.dia` scan, duplicate/missing detection, same-basename `.txt` pairing, immutable ascending result.
- [x] `SFS-004` Refactor `ContextFeatureScorerCLI` to delegate pairing and parse optional `--input-prefix`. Files: Context CLI and regression tests. Check: omitted flag preserves `_maskedN`; custom flag selects expected inputs.
- [x] `SFS-005` Add explicit Scribe folder-mode parsing while preserving positional single-run parsing. File: `ScribeTwoFeatureScorerCLI.java`. Check: required/optional flags, mixed syntax, duplicate/missing/unknown values, and Scribe property passthrough.
- [x] `SFS-006` Add preflighted per-pair Scribe orchestration, isolated output parents, fresh parameters, optional Context training, and ordered aggregate outcomes. Files: Scribe CLI plus narrow test seam. Check: unit tests can exercise orchestration without native Scribe.
- [x] `SFS-007` Add JUnit 4 matcher and Scribe CLI tests and extend Context regression coverage. Files: `AssaySchedulePairMatcherTest.java`, `ScribeTwoFeatureScorerCLITest.java`, and existing relevant tests if needed. Check: edge cases and compatibility cases pass.
- [x] `SFS-008` Update CLI help and README; conditionally expose packaged Scribe commands only if existing dispatcher scope supports it without a material plan change. Files: both CLIs, `README.md`, conditional `Context.java`. Check: syntax, prefix contract, examples, output layout, and failure behavior are documented.
- [x] `SFS-009` Run focused JUnit 4 validation. Check: exact commands, pass/fail counts, failures, and native skips are logged.
- [x] `SFS-010` Run full Java 17 Maven package verification. Check: command and result are logged and compared with baseline.
- [x] `SFS-011` Review the complete diff for minimal scope, compatibility, scientific behavior, error handling, and accidental generated files; update the guide, Google record, and this log with evidence.
- [x] `SFS-012` Prepare the external CTX Report after the actual CTX ID is assigned and implementation/review are complete. Check: full checklist/log copied verbatim and report path verified.
- [x] `SFS-013` Await Ariana’s final human review. Do not mark complete on Ariana’s behalf.

### Proposed commit groups

- Group A — `SFS-003`, `SFS-004`: matcher, pair type, Context integration, regression tests. Committed as `d55d1fb` with message `Extract shared assay schedule pair matching`.
- Group B — `SFS-005`, `SFS-006`, `SFS-007`: Scribe folder parsing/orchestration and tests. Committed as `5e005a4` with message `Add ScribeTwo folder feature scoring`.
- Group C — `SFS-008`, `SFS-009`, `SFS-010`, `SFS-011`: usage documentation, supported routing, validation, and evidence. Commit authorized with message `Document and expose ScribeTwo folder scoring`; this checklist is included in that commit.

### Work log

#### 2026-09-13 — `SFS-001`

- Attempt/change: Read the implementation-record comments, applied `AssaySchedulePairMatcher`, `AssaySchedulePairMatcherTest`, and the latest-message name `AssayScheduleInputPair`, then resolved all three comment threads with explanatory replies.
- Result/observation: The Google record contains no remaining `SeededInputPairFinder` or `SeededInputPair` references. The repository guide and checklist now record the approved revision and implementation authorization.
- Evidence: Google record revision `ANLCKQnYKN02Zof-f5UUIrAmS0UiVhWmEKvSE7385q_oDEoENNji93rCd_kIoTkzKT6eVY_X-Jwi3C2IMXLtcMmBMH1AhVuwhN-_XkEP5Qg`; remote `origin/ariana/scoring` tip verified as `5601f581cb16ce10688e2a0d07bd7eb4f2690934`.
- Conclusion/next action: Start `SFS-002` baseline validation before source edits.

#### 2026-09-13 — `SFS-002`

- Attempt/change: Moved this isolated worktree to verified `origin/ariana/scoring` commit `5601f581cb16ce10688e2a0d07bd7eb4f2690934`, recorded Java/Maven versions, ran the documented Maven setup entry point, attempted the focused scorer/Scribe test set, and attempted offline package verification.
- Result/observation: Java is Oracle JDK 17; Maven is 3.9.9 and also runs on Java 17. The pre-existing `./build.sh maven` path exits at line 44 because `MAVEN_REPO` is declared `readonly` twice. The focused test command reached dependency resolution but did not compile because the pinned `org.searlelab:javapot:1.0.5` artifact is absent. Offline package verification also stopped before compilation because the package-profile jar plugin had not yet been cached.
- Evidence: `java -version`; Maven 3.9.9 from the existing isolated Context tool cache; `./build.sh maven` exit 1; focused command `/Users/arianashannon/.codex/worktrees/42b7/context/build-cache/apache-maven-3.9.9/bin/mvn -B -Dmaven.repo.local=build-cache/m2-repository -Dtest=ContextFeatureScorerTest,ScribeCalibratedWorkflowTest,ScribeFeatureTableTest,ContextScribeTwoBridgeTest test` exit 1 at missing JavaPot; offline `-Ppackage verify` exit 1 before compile.
- Conclusion/next action: Treat both failures as baseline environment/repository setup defects, leave `build.sh` unchanged, install the pinned JavaPot artifact through a project-local workaround for post-change validation, and start `SFS-003`.

#### 2026-09-13 — `SFS-003` started

- Attempt/change: Reviewed the approved matcher API against the current private `ContextFeatureScorerCLI.findInputPairs` implementation.
- Result/observation: The existing logic can be extracted without changing its immediate-child `.dia`, inclusive-range, duplicate-seed, same-basename `.txt`, or ascending-order behavior. Prefix validation and literal quoting are the only intentional discovery extensions.
- Evidence: `ContextFeatureScorerCLI.java` at `5601f581cb16ce10688e2a0d07bd7eb4f2690934`.
- Conclusion/next action: Add `AssayScheduleInputPair`, `AssaySchedulePairMatcher`, and focused matcher tests before refactoring either CLI.

#### 2026-09-13 — `SFS-003`, `SFS-004`

- Attempt/change: Added package-private `AssayScheduleInputPair` and `AssaySchedulePairMatcher`; moved immediate-child DIA discovery, literal prefix matching, inclusive range checks, duplicate/missing diagnostics, same-basename assay pairing, and ordered immutable results into the matcher. Refactored `ContextFeatureScorerCLI` to use it and accept `--input-prefix` with `_masked` as the shared default.
- Result/observation: Both CLIs can share one discovery policy without making either CLI’s internals public. Prefixes are validated and escaped with `Pattern.quote`, including regex metacharacters, while omitted-prefix behavior retains `_maskedN` matching.
- Evidence: `AssaySchedulePairMatcherTest` covers default/custom prefixes, literal escaping, case-insensitive matching, boundaries, duplicates, combined missing inputs, invalid arguments, ordering, and immutability; `ContextFeatureScorerCLITest` covers custom-prefix parsing and duplicate rejection.
- Conclusion/next action: Implement the separate named Scribe folder path while preserving the existing positional path.

#### 2026-09-13 — `SFS-005`, `SFS-006`

- Attempt/change: Added Scribe folder argument parsing, full pair/assay preflight, per-acquisition basename output parents, fresh requested Scribe parameters per seed, reuse of run-specific effective parameters for partition/training, continue-on-error outcomes, ordered summary, and an aggregate failing exception. Retained the positional single-run flow and added only package-private test seams.
- Result/observation: Folder mode is selected only when the first argument is a double-dash folder flag. All pairs and schedules are validated before the output root is created. Each seed runs independently; a failed seed does not stop later inputs, but the overall command fails after reporting all outcomes.
- Evidence: `ScribeTwoFeatureScorerCLITest` verifies required/default/custom arguments, Scribe parameter passthrough and immutable settings, invalid/mixed/duplicate syntax, malformed assay preflight before output creation, seed order, unique basename-derived paths, fresh parameter identities, Context-mode propagation, and continued execution after an injected failure.
- Conclusion/next action: Expose the already-implied packaged commands and document both syntaxes.

#### 2026-09-13 — `SFS-007`, `SFS-008`

- Attempt/change: Added 14 focused JUnit 4 tests across the matcher and both CLIs. Added `scribe-features` and `scribe-context` dispatcher routes and updated CLI/dispatcher help plus README naming, inputs, examples, output layout, and failure behavior.
- Result/observation: `scribe-features` supports both the original positional invocation and the named folder invocation; `scribe-context` uses the same inputs and enables existing final Context LDA training. No dependency version or scientific scoring implementation changed.
- Evidence: Java 17 compilation succeeded; final shaded-jar smoke command `java -jar target/context-0.0.2-SNAPSHOT.jar scribe-features -h` displayed both syntaxes, `_masked` defaults, custom-prefix example, output isolation, and aggregate-failure behavior.
- Conclusion/next action: Run focused and full branch validation.

#### 2026-09-13 — `SFS-009`

- Attempt/change: Installed JavaPot 1.0.5 from the repository-pinned source revision `84a59af82c9258cdf8f9774ed0dbc2b648421631` into this worktree’s ignored Maven cache, then ran `/Users/arianashannon/.codex/worktrees/42b7/context/build-cache/apache-maven-3.9.9/bin/mvn -B -Dmaven.repo.local=build-cache/m2-repository -Dtest=AssaySchedulePairMatcherTest,ContextFeatureScorerCLITest,ScribeTwoFeatureScorerCLITest test` under Java 17.
- Result/observation: Build success; 14 tests ran with 0 failures, 0 errors, and 0 skips. The expected injected seed-1 failure was logged while seed 2 still executed successfully.
- Evidence: Maven final result at 2026-09-13 14:21:51 CDT: `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS`.
- Conclusion/next action: Run the full package verification and distinguish repository baseline failures.

#### 2026-09-13 — `SFS-010`

- Attempt/change: Ran `/Users/arianashannon/.codex/worktrees/42b7/context/build-cache/apache-maven-3.9.9/bin/mvn -B -Dmaven.repo.local=build-cache/m2-repository -Ppackage verify` under Java 17, followed by the same package profile with `-DskipTests package` to isolate packaging.
- Result/observation: Full verification compiled all production and test sources, then stopped with 88 tests run, 3 failures, 2 errors, and 8 skips. The five failures are outside this feature diff: stale exact-message expectations in `ContextScribeTwoBridgeTest` and two `ScribeFeatureTableTest` cases, the pre-existing `ScribeCalibratedWorkflowTest` request for missing `assay.tsv` while the repository contains `assay.txt`, and a `ContextPercolatorRunnerTest` temporary executable error. The opt-in native Scribe test was skipped because no native dataset was supplied. The independent shaded-jar package command succeeded.
- Evidence: Full command `BUILD FAILURE` after test execution only; feature-specific tests in that run passed. Package-only command at 2026-09-13 14:22:08 CDT: `BUILD SUCCESS`, including JavaPot 1.0.5 in the shaded jar.
- Conclusion/next action: Do not broaden CTX-014 to repair unrelated baseline tests; perform final diff and record review.

#### 2026-09-13 — `SFS-011`

- Attempt/change: Reviewed the complete diff, public/package-private boundaries, positional compatibility, preflight ordering, parameter/effective-parameter flow, output ownership, error causes, documentation, generated files, branch identity, and Google implementation record. Renamed the record from CTX-TBD to its assigned CTX-014 and updated its implementation/validation/completion sections.
- Result/observation: The source tree remains based exactly on `origin/ariana/scoring` commit `5601f581cb16ce10688e2a0d07bd7eb4f2690934`. The matcher, pair record, and test seams are package-private; no new dependency or scoring/calibration behavior was introduced. `git diff --check` passes. Only approved source/tests/docs/task files are changed; pre-existing untracked `.metadata/` is untouched and ignored build outputs remain outside the diff.
- Evidence: Google record revision `ANLCKQkCqT_65ma8Gwz35th5laPKaF8nHh9heiuo0UM3UhKporCednCU872K8VPPGNjnRwBF7zMskOPpuhqrIorwHVJlZu5x1DnZsMZTgR4`; text readback shows zero `CTX-TBD` occurrences, one intentionally unchecked native-data item, and status `Implemented — awaiting Ariana’s final review`; PDF export succeeded at 253,995 bytes.
- Conclusion/next action: Create the required external CTX-014 Report snapshot, then hand the uncommitted changes to Ariana for final review.

#### 2026-09-13 — `SFS-012`

- Attempt/change: Created the external implementation Report at `/Users/arianashannon/Downloads/2026-09-13-CTX-014-Report.md` using the complete CTX-014 checklist and dated log from this file.
- Result/observation: The report path was previously unused. No report was placed in the repository and no previous report was overwritten.
- Evidence: File path and byte-for-byte comparison against the refreshed `TASKS.md` snapshot verified after this log entry.
- Conclusion/next action: Await Ariana’s final human review; keep `SFS-013` unchecked until she accepts the feature.

#### 2026-09-13 — `SFS-013`

- Attempt/change: Ariana explicitly approved the completed changes, approved the proposed three-commit division, and then authorized creation of those commits. She separately instructed, “Do not push, I will do so after verifying the commits.”
- Result/observation: Final human review is accepted. Commit A was created as `d55d1fb`; commit B was created as `5e005a4`; the documentation/routing group is staged next. No push, PR, or other remote mutation is authorized or performed.
- Evidence: Ariana’s messages “I approve the changes you’ve made” and “Please make those commits,” followed by the explicit no-push instruction; local Git commit outputs for `d55d1fb` and `5e005a4`.
- Conclusion/next action: Create the final local documentation/routing commit, refresh the external Report, and hand the commit series to Ariana for verification and push.
