# Context implementation tasks

## CTX-016 — EncyclopediaTwo matched decoys and competition

- Guide: `docs/ctx-016-encyclopediatwo-matched-decoys.md`, approved r7 (2026-09-30); r6 is historical.
- Google implementation record: https://docs.google.com/document/d/1u04aFpKACUNP8Qtfl463GUPGoW5WW3zGEcpELGkKPH4/edit
- Approval status: Ariana approved draft r6 on 2026-09-29, clarified winner-only engine inputs on 2026-09-30, then paused implementation for a Context-only correction. Ariana explicitly approved guide r7 and resumed implementation on 2026-09-30 in this chat. Git delivery is not authorized.
- Canonical Until Useful plan: `docs/uu-ctx-016-encyclopediatwo-matched-decoys.md`, revision 2, approved 2026-09-30 for Context-only implementation; revision 1 is retained as history. Do not treat the CTX-015 plan as this feature's plan.
- Scope boundary: CTX-016 replaces CTX-015 as the active implementation. Preserve CTX-015 documents for history. Source edits stay in Context, including Context-owned scoring/preparation and the authorized narrow `TargetedBootstrapper` assignment change. Encyclopedia source stays unchanged; Percolator and mProphet classes are validation targets.

### Acceptance criteria

- [ ] Context uses a pinned unchanged Encyclopedia 6.9.23 artifact and its Context-owned feature-only scoring path without invoking upstream protein TDC or changing Encyclopedia source.
- [ ] The working library is regenerated and target/decoy spectrum predictions are requested before search; Encyclopedia's predicted/shuffled strategy and target-only case are preserved, counted, and recorded. Targets lacking usable spectra are excluded with reasons.
- [ ] Stable upstream precursor/pair keys map to existing feature `id` values in a sidecar that survives every relevant writer/rescorer. Empirical assay entrapment assignments from a narrow `TargetedBootstrapper` change remain distinct, including one-to-many assignments.
- [ ] An optional diagnostic captures retained peak-group candidates, while only winner rows reach the engines; the setting is recorded and cache reuse is guarded.
- [ ] Competition occurs once after all target and decoy candidates are scored and before `ContextFeatureScorer` reports features, for every population. Percolator and mProphet receive valid features, retain reference/background roles, and do not apply a second pairwise competition; their classes remain unmodified unless a new finding and plan revision are approved.
- [ ] Legacy inputs, direct feature-file reuse, malformed/missing mapping, duplicates, ties, modifications, charge, and fallback cases have defined behavior and tests.
- [ ] Documentation, Java 17 tests/package checks, representative scientific comparisons, diff review, and final human review are recorded with their actual limits.

### Approved implementation checklist — r6 with 2026-09-30 clarification

- [ ] `CTX016-001` Settle the two relationship schemas, `id` sidecar join, regeneration interface, exact filter output stage, and confidence-stage boundaries. Files: guide, Google record, this checklist. Check: Ariana approves a dated revision and, if applicable, a frozen canonical plan; CTX-016 replaces CTX-015.
- [ ] `CTX016-002` Establish Context/upstream Java 17 and package baselines at the stated revisions. Files: log only. Check: exact versions, commands, and results recorded.
- [ ] `CTX016-003` Superseded by proposed Context-only tasks `CTX016-014` and `CTX016-015`: the upstream preparation/API/scoring edits in this task are no longer authorized.
- [ ] `CTX016-004` Superseded by proposed Context-only tasks `CTX016-015` and `CTX016-016`: the copied Context feature-only class will remain; no new upstream API is planned.
- [ ] `CTX016-005` Superseded by proposed Context-only task `CTX016-016`: the search-pair sidecar remains required, but its producer and integration change.
- [x] `CTX016-006` Persist `TargetedBootstrapper` empirical entrapment assignments through the authorized narrow change. Files: bootstrapper assignment writer and tests. Check: one-to-many mapping, stable keys, and no confusion with search decoys.
- [ ] `CTX016-007` Validate Percolator/mProphet peptide handling and `id` retention without editing engine classes. Files: focused tests/log. Check: background training, reference application, peptide confidence, and no unintended second pairwise filter. Any proven required engine change triggers a guide revision for Ariana's approval.
- [ ] `CTX016-008` Document CLI/configuration, metadata schemas, winner-only default, prediction/fallback/target-only behavior, and migration. Files: README, help, guide, fixture provenance. Check: examples and actual output names match.
- [ ] `CTX016-009` Run focused JUnit 4, upstream compatibility, full Context Java 17 package checks, and representative scientific comparisons. Files: tests/fixtures/log. Check: commands, seeds, data, numeric tolerances, outcomes, skips, and limits recorded.
- [ ] `CTX016-010` Review the complete diff for minimal scope, dependency justification, scientific behavior, and artifacts; perform applicable Until Useful review. Files: guide and log. Check: findings, corrections, and residual risks recorded.
- [ ] `CTX016-011` Create the external CTX-016 Report after implementation/review. Check: full checklist/log copied verbatim to a unique Downloads path and verified.
- [ ] `CTX016-012` Await Ariana's final human review. Do not mark complete on her behalf.
- [x] `CTX016-013` Before implementation, inventory and discard uncommitted work outside `docs/`, `TASKS.md`, and root `AGENTS.md`, as Ariana instructed. Check: exact retained/discarded paths logged; allowed documents and instruction file preserved. This is a prerequisite for `CTX016-002` and source implementation, not a code commit.

### Proposed commit groups — scope planning only

- Group A: `CTX016-003` — upstream feature API and focused tests. Proposed message: `Expose EncyclopediaTwo matched-decoy feature results`.
- Group B: `CTX016-004`, `CTX016-005` — dependency pin, Context bridge, mapping sidecar, and tests. Proposed message: `Integrate matched EncyclopediaTwo features in Context`.
- Group C: `CTX016-006` — empirical entrapment assignment output and tests. Proposed message: `Record empirical entrapment assignments`.
- Group D: `CTX016-007`, `CTX016-008`, `CTX016-009`, `CTX016-010` — engine validation without engine edits, documentation, verification evidence, review corrections. Proposed message: `Document and validate CTX-016 competition semantics`.
- Planning, baseline logs, external Report, and final human review stay outside code commit groups. Each actual commit will require separate explicit authorization and a reviewed scope.

### Work log

#### 2026-09-30 — `CTX016-001` approval reconfirmed

- Ariana explicitly reconfirmed approval of the CTX-016 plan in this chat and confirmed that the winner-only clarification in this log is correct. Implementation remains on Context branch `ariana/scoring` at `138e34f581d9623bf9d47d72ef26300effd6f7d2`; the related Encyclopedia checkout is on `master` at `65d6346d2883cfda37ece0f9f2cc674bc5567899`. This is scope approval, not Git delivery authorization.

#### 2026-09-30 — `CTX016-001` Chat-model implementation resumed

- Attempt/change: Ariana directed this implementation to use Chat models only and to set aside the unavailable Claude Code runtime. Re-read the frozen CTX-016 plan, this checklist, the Google implementation record, Context and Encyclopedia instructions, current Git status, and the upstream preparation/search path. No source edits were made in this step.
- Result/observation: Context remains at `138e34f581d9623bf9d47d72ef26300effd6f7d2`; the upstream working tree is clean. The Google record still contains its earlier planning-only wording, while this checklist records the later approval and winner-only clarification. Upstream `ScribeTwoLibraryPreparation.appendPredictedPairs` currently drops both entries when either prediction is absent; upstream `EncyclopediaTwo.generateFeatureFile` selects mapped versus shuffled search and counts target-only entries, while its scoring task trims to one result before the feature writer. Context `partitionFeatures` then reduces rows by sequence before partitioning.
- Conclusion/next action: Resolve the preparation interface, stable feature-row join, and diagnostic capture contract against source before editing Java. The existing baseline failures remain recorded under `CTX016-002`; no unrelated baseline fixes are included in CTX-016.

#### 2026-09-29 — `CTX016-001` implementation approval and contract clarification

- Attempt/change: Retrieved the corrected Google CTX-016 record and read local draft r6. Ariana explicitly confirmed CTX-016 is the approved feature. Rechecked Context HEAD (`138e34f581d9623bf9d47d72ef26300effd6f7d2`), current changes, upstream instructions, `ScribeTwoLibraryPreparation.prepare`/`appendPredictedPairs`, upstream scoring-task `result.trim(1)`, and `ContextFeatureScorer.partitionFeatures`.
- Result/observation: The approved guide both requests retained candidates for downstream comparison and requires competition before ContextFeatureScorer output for all populations. Source inspection confirms upstream currently trims the shared result to one winner; Context then separately reduces by sequence. Whether experimental-mode losers reach the engines remains scientifically consequential and cannot be resolved from source. Requested Ariana's choice between unfiltered engine inputs and diagnostic-only retained candidates with winner-only engine inputs.
- Conclusion/next action: Scope approval is recorded; `CTX016-001` remains incomplete pending the experimental-mode contract and canonical plan. No source files, dependencies, or existing user work were changed or discarded. No tests were run. Resume the contract and canonical plan when the answer arrives, then perform the approved cleanup and baseline tasks.

#### 2026-09-29 — `CTX016-001` planning started

- Attempt/change: Confirmed Context `138e34f581d9623bf9d47d72ef26300effd6f7d2` and Encyclopedia `65d6346d2883cfda37ece0f9f2cc674bc5567899`; inspected the copied/upstream feature generator, upstream matched-decoy strategy, Context partitioning and peptide selection, README, and the implementation-record template. Created draft r1 and this pending checklist.
- Result/observation: Context pins Encyclopedia 6.6.24 and uses its copied feature generator; upstream feature generation is package-private. Upstream predicted-pair selection is conditional and can fall back to shuffled or target-only behavior. Context's sequence-only partitioning does not carry explicit predicted pairing. No build or scientific validation was run.
- Conclusion/next action: Resolve the five open design questions in draft r1, revise and approve this checklist, then begin implementation. No Java or dependency edits were made.

#### 2026-09-29 — `CTX016-001` revised to draft r3

- Attempt/change: Ariana authorized clearing the completed CTX-014 tasks while retaining documents. Verified the entire CTX-014 section was present in `/Users/arianashannon/Downloads/2026-09-13-CTX-014-Report.md` before clearing it from this file. Ariana confirmed winner-only peak-group output as the default and a clear error if a target or decoy spectrum cannot be predicted. Inspected upstream preparation and search paths to distinguish prediction from fallback.
- Result/observation: Upstream ScribeTwo preparation requests and persists target/decoy spectra; EncyclopediaTwo search itself does not predict a missing pair. Its strategy may choose shuffled decoys below 97.5% mapped coverage or with extra decoy libraries, and predicted mode may search an individual target without a mapped decoy. CTX-016 draft r3 now requires complete-pair preparation and strict preflight before search. No source implementation or tests were run.
- Conclusion/next action: Resolve preparation interface, feature-row key, filter output stage, and peptide confidence semantics; seek approval of the settled guide/checklist before implementation.

#### 2026-09-29 — `CTX016-001` revised to draft r4

- Attempt/change: Ariana clarified that Context should request predictions before search, then use Encyclopedia's shuffled fallback and target-only search when a predicted decoy is absent. Inspected `ScribeTwoLibraryPreparation` and `EncyclopediaTwo` for the distinction between preparation and search.
- Result/observation: Search already supports global all-shuffled fallback and an individual target-only case in predicted mode. The current ScribeTwo preparation omits both target and decoy when either prediction is missing; it would need a narrow change, or a different supported preparation path, to retain a target for the requested target-only search. Draft r4 records this gap and supersedes r3's strict-failure criterion. No implementation or tests were run.
- Conclusion/next action: Settle the preparation API and missing-target policy, then approve the plan/checklist before implementation.

#### 2026-09-29 — `CTX016-001` record formatting correction

- Attempt/change: Ariana noted that answers in the Google implementation record inherited the template's Subtitle style. Changed all 22 answer/metadata paragraphs with that style to Normal Text while retaining heading styles and table labels.
- Result/observation: Google Docs readback shows zero Subtitle paragraphs; sampled answers are Normal Text and 42 heading paragraphs remain. No planning content or Java source changed.
- Conclusion/next action: Continue reviewing draft r4's open design questions.

#### 2026-09-29 — `CTX016-001` template bullet placement correction

- Attempt/change: Ariana pointed out that responses sat above the template's answer bullets. Restored the original prompt text and moved 25 responses into their corresponding native bullet paragraphs, including the implementation-concept bullet. Left the two future work-log entries blank with `YYYY-MM-DD` placeholders.
- Result/observation: Google Docs readback confirms sampled prompts are followed by populated Normal Text bullets, no answer remains in the sampled prompt positions, and only six intended future-work bullets remain empty. Headings and template tables were retained.
- Conclusion/next action: Continue review of draft r4; implementation approval remains pending.

#### 2026-09-29 — `CTX016-001` revised to draft r6

- Attempt/change: Ariana settled library regeneration, the upstream pair/precursor key with a verified `id`-keyed sidecar, competition timing before `ContextFeatureScorer` reports for all populations, CTX-016 replacing CTX-015, and target-spectrum exclusion with a report. She authorized only a narrow `TargetedBootstrapper` exception to the scoring-focused edit scope and confirmed `AGENTS.md` should be preserved with `docs/` and `TASKS.md` during pre-implementation cleanup. Rechecked Context's feature and engine paths before removing `ContextPercolator.java` and mProphet classes from planned edits.
- Result/observation: Current `ContextFeatureScorer.partitionFeatures` reduces by sequence before writing partitions. The upstream writer emits an `id`; Context's Percolator rescored-feature writer preserves that column, and mProphet parses it as metadata. Adding a pair-key TSV column would enter the numerical feature set, so r6 uses an `id`-keyed sidecar and requires join checks at every writer/rescorer. Engine classes are validation targets only. No files were discarded, and no implementation or tests were run.
- Conclusion/next action: Ariana reviews r6; approval remains pending. Before any source implementation, complete `CTX016-013`, then establish baseline and implement the approved scope.


#### 2026-09-30 — `CTX016-001` winner-only contract settled

- Attempt/change: Ariana specified, "Only winners should reach the engines". Appended the clarification to the local guide and froze canonical scope revision 1, preserving r6 text. Retained candidates are diagnostic only; winner selection and engine inputs must be identical with diagnostics enabled or disabled.
- Result/observation: Upstream `EncyclopediaTwoPointOneScoringTask.call` trims once before queuing results; this is the intended diagnostic capture boundary. Current Context and upstream HEADs still match the recorded baselines; upstream working tree is clean. Interface/schema verification remains part of `CTX016-001`.
- Baseline prerequisite issue: `bash -n build.sh` fails at line 124; `bash build.sh maven` fails first at line 44 (`MAVEN_REPO: readonly variable`). These are pre-existing and have not been repaired. Existing Maven 3.9.9 at `/Users/arianashannon/.codex/worktrees/42b7/context/build-cache/apache-maven-3.9.9/bin/mvn` runs on Oracle Java 17, so direct Maven invocation can establish the baseline without editing build tooling.
- Next action: Complete the authorized cleanup inventory and baseline checks, then verify the smallest upstream API/preparation and sidecar contracts. No Java changes yet.


#### 2026-09-30 — `CTX016-013` approved cleanup inventory

- Retained: all `docs/` contents, root `TASKS.md`, and root `AGENTS.md`. Ignored build artifacts are outside this cleanup. No Eclipse process was found in the process check.
- Exact uncommitted paths to discard (a deleted tracked file is restored):

  - ` D CTX-008-seed-implementation.patch`
  - `?? .Rhistory`
  - `?? .metadata/.lock`
  - `?? .metadata/.lock_info`
  - `?? .metadata/.mylyn/.taskListIndex/segments_1`
  - `?? .metadata/.mylyn/.taskListIndex/write.lock`
  - `?? .metadata/.mylyn/repositories.xml.zip`
  - `?? .metadata/.mylyn/tasks.xml.zip`
  - `?? .metadata/.plugins/org.eclipse.buildship.core/gradle/versions.json`
  - `?? .metadata/.plugins/org.eclipse.core.resources/.projects/.org.eclipse.egit.core.cmp/.location`
  - `?? .metadata/.plugins/org.eclipse.core.resources/.root/.indexes/history.version`
  - `?? .metadata/.plugins/org.eclipse.core.resources/.root/.indexes/properties.index`
  - `?? .metadata/.plugins/org.eclipse.core.resources/.root/.indexes/properties.version`
  - `?? .metadata/.plugins/org.eclipse.core.resources/.root/1.tree`
  - `?? .metadata/.plugins/org.eclipse.core.resources/.safetable/org.eclipse.core.resources`
  - `?? .metadata/.plugins/org.eclipse.e4.workbench/last_nl.properties`
  - `?? .metadata/.plugins/org.eclipse.e4.workbench/workbench.xmi`
  - `?? .metadata/.plugins/org.eclipse.egit.ui/dialog_settings.xml`
  - `?? .metadata/.plugins/org.eclipse.jdt.core/assumedExternalFilesCache`
  - `?? .metadata/.plugins/org.eclipse.jdt.core/externalFilesCache`
  - `?? .metadata/.plugins/org.eclipse.jdt.core/javaLikeNames.txt`
  - `?? .metadata/.plugins/org.eclipse.jdt.core/nonChainingJarsCache`
  - `?? .metadata/.plugins/org.eclipse.jdt.core/variablesAndContainers.dat`
  - `?? .metadata/.plugins/org.eclipse.jdt.launching/.install.xml`
  - `?? .metadata/.plugins/org.eclipse.jdt.launching/libraryInfos.xml`
  - `?? .metadata/.plugins/org.eclipse.jdt.ui/OpenTypeHistory.xml`
  - `?? .metadata/.plugins/org.eclipse.jdt.ui/QualifiedTypeNameHistory.xml`
  - `?? .metadata/.plugins/org.eclipse.jdt.ui/dialog_settings.xml`
  - `?? .metadata/.plugins/org.eclipse.m2e.core/workspaceState.ser`
  - `?? .metadata/.plugins/org.eclipse.m2e.logback/logback.2.7.101.20251017-1242.xml`
  - `?? .metadata/.plugins/org.eclipse.mylyn.github.ui/avatars.ser`
  - `?? .metadata/.plugins/org.eclipse.oomph.setup/workspace.setup`
  - `?? .metadata/.plugins/org.eclipse.tips.ide/dialog_settings.xml`
  - `?? .metadata/.plugins/org.eclipse.ui.ide/dialog_settings.xml`
  - `?? .metadata/.plugins/org.eclipse.ui.intro/introstate`
  - `?? .metadata/.plugins/org.eclipse.ui.workbench/dialog_settings.xml`
  - `?? .metadata/.plugins/org.eclipse.ui.workbench/workingsets.xml`
  - `?? .metadata/version.ini`

- Result: Discarded the inventoried untracked files and restored the tracked root patch. Preserved every allowed document and ignored file. Cleanup complete.


#### 2026-09-30 — `CTX016-002` baseline results and dependency blocker

- Goal check: Work remains confined to CTX-016 setup, approved cleanup, and baseline validation. No Java, POM, engine, upstream source, or build-script edits have been made. No staging, commits, pushes, or external document writes occurred.
- Environment: Existing Maven 3.9.9 at `/Users/arianashannon/.codex/worktrees/42b7/context/build-cache/apache-maven-3.9.9/bin/mvn`, Oracle Java 17, macOS 15.7.9 x86_64. Context HEAD `138e34f581d9623bf9d47d72ef26300effd6f7d2`; Encyclopedia HEAD `65d6346d2883cfda37ece0f9f2cc674bc5567899` with clean source working tree.
- Context attempt: `<mvn> -o -B -Dmaven.repo.local=build-cache/m2-repository -Ppackage verify` failed on uncached resources plugin. Online retry initially failed sandbox DNS; approved network retry resolved public dependencies, then failed on JavaPot 1.0.5 unavailable in declared repositories. Copied the already-installed jar/POM from `~/.m2/repository/org/searlelab/javapot/1.0.5` into the project-local cache. Jar SHA-256: `7cdfc154858c2028055166f87d122659c4fbd671e48df47e1fd77c020d91c410`; POM SHA-256: `9d51635c1f7490731198dc009276036a7b765a2889d9e0a75c55f46ecb5260b9`. This is an installed artifact baseline, not verification of the source revision named in build.sh.
- Context result: `<mvn> -B -Dmaven.repo.local=build-cache/m2-repository -Ppackage verify` compiled production/test sources and ran 88 tests: 3 failures, 2 errors, 8 skips. Verification failed in the test phase; packaging/vendor-binary checks did not run. Full final log: `tmp/ctx016/ctx016-context-baseline-cached-javapot.log`; reports: `target/surefire-reports/`.
- Failing baseline cases: `ContextScribeTwoBridgeTest.absentPreAlignmentCannotBecomeUncalibratedSuccess`; `ScribeFeatureTableTest.missingRuntimeHasActionableError`; `ScribeFeatureTableTest.rejectsInvalidLabelsAndNonfiniteScoresWithRowContext`. Errors: `ScribeCalibratedWorkflowTest.realCalibratedTableSupportsPartitionAndBackgroundModelApplication` (missing classpath resource); `ContextPercolatorRunnerTest.commandMatchesPinnedDependencyExceptSeedForBothRounds` (could not preserve executable for debugging). No root-cause fixes attempted. Asked Ariana whether to record these or investigate separately.
- Upstream attempt: In Encyclopedia, `<mvn> -o -B -Dmaven.test.skip=true compile` failed because JChronologer 1.0.10 is uncached. Approved online retry `<mvn> -B -Dmaven.test.skip=true compile` failed resolving its POM with GitHub Packages HTTP 401. Upstream compilation/tests have not run. Full log: `tmp/ctx016/ctx016-upstream-baseline.log`.
- Blocker/next action: Asked Ariana for an accessible JChronologer 1.0.10 artifact or source checkout. Do not substitute cached 1.0.9 or change dependency requirements. `CTX016-002` remains incomplete; source implementation awaits the approved upstream baseline prerequisite. Continue interface/schema verification when the dependency is available. Canonical revision 1 remains unchanged.


#### 2026-09-30 — `CTX016-002` requested JChronologer download retry

- Attempt/change: At Ariana's request, retried from the Encyclopedia checkout with `/Users/arianashannon/.codex/worktrees/42b7/context/build-cache/apache-maven-3.9.9/bin/mvn -U -B -Dmaven.test.skip=true compile`, using approved network access. `-U` forces Maven to retry cached resolution failures.
- Result: Exit 1. JChronologer 1.0.10 POM resolution still returns HTTP 401 Unauthorized from the configured GitHub Packages repository. The configured S3 repository also reports connection refused; the other configured repositories did not resolve the dependency. Compilation was not reached; no tests ran.
- Evidence: `tmp/ctx016/ctx016-upstream-redownload.log`.
- Next action: Obtain working package-repository access or an accessible JChronologer 1.0.10 artifact/source checkout. Dependency versions and source files remain unchanged.


#### 2026-09-30 — `CTX016-002` Maven authentication diagnosis

- At Ariana's request, checked repository IDs, credential configuration structure (without printing credential values), environment-variable presence, and artifact provenance. `~/.m2/settings.xml` has a server ID `github` matching upstream pom.xml. Its username/password reference `GITHUB_PACKAGES_USER` and `GITHUB_PACKAGES_TOKEN`; both are absent in the tool shell, including an explicitly checked unsandboxed shell matching the retry environment. This explains why the GitHub request lacks the configured authentication inputs; valid-token/package access remains untested.
- Context's Encyclopedia 6.6.24 and MSRawJava core-nolice 26.7.31 resolved from repository-local `libs/`, not fresh authenticated remote downloads. Upstream MSRawJava core 26.7.31 and JavaPot 1.0.5 cache metadata records prior downloads through repository ID `github` dated 2026-09-21; current remote access for those artifacts was not tested. The upstream Encyclopedia project is built from source.
- Conclusion: GitHub is reachable and returns HTTP 401; this is an authentication failure, distinct from the fallback S3 connection-refused error. Supply the configured environment variables securely to the Maven process and retry before considering dependency or repository changes. No credentials displayed or modified.


#### 2026-09-30 — `CTX016-002` JChronologer available; upstream compile passes

- Attempt/change: After Ariana reported successful terminal compilation and updated Maven settings, reran `/Users/arianashannon/.codex/worktrees/42b7/context/build-cache/apache-maven-3.9.9/bin/mvn -U -B -Dmaven.test.skip=true compile` in Encyclopedia from this chat's execution environment.
- Result: BUILD SUCCESS, exit 0; compiled 670 source files. JChronologer 1.0.10 is now present in the local Maven cache (jar SHA-256 `a4d90e69e1721d107d88cc0abc36daf962b0782493767ae53151c693aa909253`). This verifies dependency availability and upstream compilation; the command reused the cached release artifact, so it does not independently verify a fresh authenticated download. No tests ran under `maven.test.skip=true`.
- Evidence: `tmp/ctx016/ctx016-upstream-access-recheck.log`.
- Next action: The missing-dependency blocker is cleared. Continue remaining CTX-016 baseline/interface verification under the approved plan. Previously recorded Context baseline test failures remain unresolved; no source or dependency declarations changed in this check.


#### 2026-09-30 — `CTX016-006` empirical assignment output

- Attempt/change: Added `<acquisition>_masked<seed>_entrapment_assignments.tsv` beside each bootstrapper assay list. Each row has relation type `EMPIRICAL_ASSAY_ENTRAPMENT` plus modified target/entrapment sequences and charges. The writer validates that every selected target has the requested number of ordered entrapments before writing. Added a synthetic one-to-many test and checked that the existing bootstrapper run creates the sidecar.
- Result: `/Users/arianashannon/.codex/worktrees/42b7/context/build-cache/apache-maven-3.9.9/bin/mvn -B -Dmaven.repo.local=build-cache/m2-repository -Dtest=TargetedBootstrapperTest test` passed: 2 tests, 0 failures/errors/skips. `git diff --check` passed. This tests assignment persistence and a small existing fixture; it does not validate the later search-pair sidecar or peptide confidence estimates.
- Next action: Continue `CTX016-001` upstream preparation and feature-identity contract, then `CTX016-003` upstream implementation. Document the new sidecar path and schema under `CTX016-008`.


#### 2026-09-30 — `CTX016-001` material plan correction and implementation pause

- Trigger: Ariana paused CTX-016 implementation and directed that source changes be confined to Context, with no edits to `ScribeTwoLibraryPreparation.java` or other Encyclopedia classes. This replaces the r6 upstream-change architecture while preserving the scientific outcome and winner-only engine boundary.
- Action/evidence: The only Encyclopedia edits made in this implementation were to `ScribeTwoLibraryPreparation.java` and `ScribeTwoLibraryPreparationResult.java`. Ariana explicitly requested their removal. Restored exactly those two files from Encyclopedia `HEAD`; `git status --short --branch` now shows clean `master...origin/master`, and `git diff --stat` is empty. No upstream tests were run on the discarded edits.
- Impact/next action: Frozen canonical revision 1 remains unchanged as history. Proposed local guide r7 and replacement tasks `CTX016-014`–`CTX016-016` are pending approval. No further implementation or upstream source changes while paused. Context's `TargetedBootstrapper` assignment output and passing 2-test result remain in the working tree because Ariana requested only Encyclopedia changes be discarded; it will be reviewed against r7 before resuming. The earlier `CTX016-006` log's next action is superseded by this pause.


### Approved r7 replacement tasks — 2026-09-30

- [ ] `CTX016-014` Add Context-owned working-library regeneration and prediction orchestration using unchanged public Encyclopedia APIs. Files: new Context scoring/preparation class, focused tests, fixture notes. Check: exact modified-sequence/charge requests, target retention after missing decoy, reasoned target exclusion, mapping coverage, bounded failure isolation, deterministic cache/provenance.
- [ ] `CTX016-015` Update only Context's copied feature-only `EncyclopediaTwo` and scoring task/factory to use the unmodified 6.9.23 matched-decoy strategy and to emit typed pair/mode metadata plus optional pre-trim candidate diagnostics. Files: Context scoring classes, `pom.xml`, focused tests. Check: predicted/shuffled/target-only cases, winner parity with diagnostics on/off, no upstream FDR call, no Encyclopedia source diff.
- [ ] `CTX016-016` Produce and validate the search-pair sidecar from the Context search result; join by existing feature `id` through partitioning and reusable inputs. Files: `ContextFeatureScorer` and Context scoring IO/tests. Check: modifications, charge, duplicates, ties, missing/ambiguous joins, cache identity, legacy path, and both rescorers' `id` retention without engine-class edits.

### Approved r7 commit groups — Scope planning only; execution requires separate authorization

- Earlier Groups A and B are superseded because they included upstream edits or removal of Context's copied feature generator. Do not execute them.
- Group E: `CTX016-014` — Context working-library preparation and prediction tests. Proposed message: `Prepare matched search libraries in Context`.
- Group F: `CTX016-015` — Context feature-only scoring, mode/pair results, diagnostics, and unmodified dependency pin. Proposed message: `Score matched decoys through Context feature path`.
- Group G: `CTX016-016` — `id`-keyed pair sidecar, partition/cache handling, and tests. Proposed message: `Track search pair identity through Context features`.
- Existing completed `CTX016-006` remains its own Group C scope. Existing `CTX016-007`–`CTX016-012` remain pending downstream validation, documentation, review, Report, and final human review tasks; applicable review will use Chat models only. Each actual commit still requires separate explicit authorization.

#### 2026-09-30 — `CTX016-001`, `CTX016-014`–`CTX016-016` Context-only approval and resume

- Ariana explicitly approved the Context-only r7 guide and told Codex to continue. Canonical revision 2 records that scope and the winner-only engine boundary; revision 1 and the older task/commit groups remain historical. The Encyclopedia checkout remains clean, and no Encyclopedia source edits are authorized.
- `CTX016-014` starts with verification of the unchanged prediction and library APIs. Next action: implement Context-owned working-library preparation, then the Context feature and sidecar tasks. Chat-model review replaces the unavailable Claude Code scripted review. No Git delivery action is authorized.

#### 2026-09-30 — `CTX016-014`, `CTX016-015` unchanged dependency compatibility

- Inspected the unchanged Encyclopedia 6.9.23 APIs: `LibraryFile` exposes target metadata and batched persistence; `JChronologerLibraryPredictionClient.ScribeTwoPredictionSession` accepts exact modified-sequence/charge requests and general `SearchParameters`; `EncyclopediaTwoDecoyStrategy` selects mapped mode only at at least 97.5% unique coverage with matching metadata and no extra decoy libraries. Context's current copied search still generates shuffled decoys and requires adaptation.
- Built the unchanged Encyclopedia checkout with Java 17. `mvn -Dmaven.test.skip=true package` was interrupted after it began downloading a large Maven S3 transport graph; the offline package attempt compiled 670 source files but stopped at missing Surefire plugin dependencies. A focused `mvn jar:jar` completed successfully and produced `target/encyclopedia-6.9.23.jar`. The upstream Git tree remained clean.
- Added the pinned 6.9.23 jar and Maven descriptor to Context `libs/`, plus JChronologer 1.0.10 required by the new prediction API. Added the previously locally built JavaPot 1.0.5 artifact to `libs/` because the initial Context compile could not resolve that existing direct dependency from Maven Central. Updated `pom.xml` to 6.9.23 and removed its JChronologer exclusion. Context `mvn -B -U -Dmaven.repo.local=/Users/arianashannon/.m2/repository -Dmaven.test.skip=true compile` passed with Java 17 (39 source files). This is compilation only, not package or runtime validation. Next: implement and test Context-owned working-library preparation and feature scoring.

#### 2026-09-30 — `CTX016-014`, `CTX016-015` first Context implementation slice

- Added Context-owned `ContextWorkingLibraryPreparer` using unchanged public prediction and `LibraryFile` APIs. It predicts exact modified-sequence/charge requests in bounded batches, isolates request failures, retains a target if its decoy is unavailable, excludes a target without a spectrum, writes a run-local library and assignment report, and rejects existing output paths. Added a small synthetic JUnit 4 test; `ContextWorkingLibraryPreparerTest` passed 2 tests, 0 failures/errors/skips. This class is not yet connected to `ContextFeatureScorer`; cache identity, source/version provenance, and batch failure detail remain to complete `CTX016-014`.
- Updated only Context's copied `EncyclopediaTwo` to resolve the unchanged upstream strategy and select mapped predicted decoys, a target-only candidate when mapping is missing, or shuffled decoys. Added a typed selection callback and focused strategy tests. `EncyclopediaTwoMatchedDecoyTest` and the preparer test passed together: 4 tests, 0 failures/errors/skips. Diagnostic candidate capture, result metadata persistence, and the search-pair sidecar remain to complete `CTX016-015`/`CTX016-016`. No Encyclopedia source diff was made.
- The first offline focused test command was unrun because several dependencies and Surefire artifacts were absent from that cache. The subsequent online `mvn -B -U -Dmaven.repo.local=/Users/arianashannon/.m2/repository -Dtest=ContextWorkingLibraryPreparerTest test` passed 2 tests; after the Context strategy change, offline `mvn -B -o -Dmaven.repo.local=/Users/arianashannon/.m2/repository -Dtest=ContextWorkingLibraryPreparerTest,EncyclopediaTwoMatchedDecoyTest test` passed 4 tests. These checks do not validate live JChronologer prediction or the end-to-end scoring path.

#### 2026-09-30 — `CTX016-014`, `CTX016-016` prediction settings and feature-id join

- Ariana specified Context prediction defaults: NCE 27, default charge 2, DIA NCE adjustment enabled. These replace the upstream ScribeTwo defaults for CTX-016. The values must be exposed at the Context entry point, included in cache identity, and documented; the preparer currently accepts them explicitly and its test uses older example values, so integration remains open.
- Added a Context search-pair sidecar builder that collects typed target/decoy selections and joins each feature row using existing `id`, `Label`, modified sequence, and charge. It fails on missing relations, duplicate IDs, and inconsistent row identity. Focused `SearchPairSidecarTest` passed 2 tests, 0 failures/errors/skips with `mvn -B -o -Dmaven.repo.local=/Users/arianashannon/.m2/repository -Dtest=SearchPairSidecarTest test`. Production integration, cache validation, and partition/rescorer survival are still pending.

#### 2026-09-30 — `CTX016-014`–`CTX016-016` feature path and population propagation

- Connected the Context-owned working-library preparer to `ContextFeatureScorer.scoreFeaturesForContext` for new feature generation. The Context copied search now emits typed target/decoy selections; its `id` sidecar is written before partitioning. A SHA-256 manifest covers source library, DIA, FASTA, assay schedule, search parameters, Context prediction settings, generated features, sidecar, and optional diagnostic. Reuse requires a matching manifest; explicit `allowLegacyFeatures` permits an old feature file only when neither CTX-016 companion exists. Stale caches fail clearly rather than being silently reused.
- Added Context CLI settings with Ariana's defaults (NCE 27, charge 2, DIA adjustment enabled), optional pre-trim candidate diagnostics, and explicit legacy reuse. The Context copied scoring task sends candidates to a separate diagnostic writer immediately before its unchanged `trim(1)` winner boundary; only trimmed results reach the feature writer. Diagnostic output is optional and included in cache identity.
- Updated Context partitioning to use the sidecar's target modified-sequence/charge key when assigning reference versus background. Independently predicted decoys inherit their search target's population. The original sequence/reversal classification remains for legacy feature files. Reference/background sidecars contain the selected feature IDs. A synthetic paired-partition test with a non-reversed predicted decoy passed (2 reference rows, 0 background rows, both IDs retained). Focused preparer, strategy, sidecar, cache, and CLI tests passed together: 10 tests, 0 failures/errors/skips. `ContextFeatureScorerPairedPartitionTest` separately passed 1 test, 0 failures/errors/skips.
- Remaining: end-to-end live prediction/search validation, stronger partial-output cleanup and resource handling, direct-input rescorer checks, documentation, Java 17 package verification, scientific comparison where data exist, and independent Chat-model review. No claim of FDR validity follows from these small tests.

#### 2026-09-30 — `CTX016-014`–`CTX016-016` output integrity and predicted-mode callback correction

- Review found that the Context copied search emitted pair selections only in the shuffled branch. Moved the callback after candidate construction so predicted, shuffled, and target-only selections are all recorded before scoring. This was a required correction within the approved Context-only path; no Encyclopedia source was changed.
- Sidecar creation now writes a temporary file and publishes it atomically after every feature ID is validated. Partition sidecars are likewise published atomically and can be regenerated on verified cache reuse. The cache identity now hashes analysis settings without the legacy-reuse permission switch; its manifest is published atomically. Working-library preparation checks output paths before opening the predictor and removes its own partial library/report on failure.
- Focused Java 17 tests after the callback and output corrections: `mvn -B -o -Dmaven.repo.local=/Users/arianashannon/.m2/repository -Dtest=ContextWorkingLibraryPreparerTest,EncyclopediaTwoMatchedDecoyTest,SearchPairSidecarTest,ContextFeatureCacheIdentityTest,ContextFeatureScorerCLITest,ContextFeatureScorerPairedPartitionTest test` passed 11 tests, 0 failures/errors/skips. A preceding run including `TargetedBootstrapperTest` passed 13 tests. Tests now cover failed joins leaving no sidecar and repeated partition output. Next: run broader package checks, validate live scoring and engine reuse, then document observed limits.

#### 2026-09-30 — `CTX016-009` full verify and live-prediction limitation

- Offline `-Ppackage verify` stopped before compilation because Maven jar plugin 3.4.1 was uncached. An online retry downloaded that project-local plugin and ran 98 tests: 4 failures, 3 errors, 8 skips, then stopped before packaging. Five failures/errors match the recorded preimplementation baseline (`ContextScribeTwoBridgeTest`, two `ScribeFeatureTableTest` cases, `ScribeCalibratedWorkflowTest`, and `ContextPercolatorRunnerTest`). `ContextMProphetExecutorTest.test` did not produce its expected output, and the old `ContextFeatureScorerTest.smokeTest` encountered a preexisting feature file without the new required sidecar/manifest. Those two cases require isolation before attributing them to CTX-016.
- A new one-precursor integration test attempted the actual JChronologer 1.0.10 prediction session and failed before prediction: DJL reported no PyTorch 2.7.1 native library for `cpu-osx-x86_64`. JChronologer's descriptor ships natives for macOS ARM, Linux x86_64, and Windows x86_64, not this host. Docker is unavailable locally. Live prediction/search and representative scientific comparison are therefore unverified here. The integration test is gated to skip on Intel macOS and remains runnable on supported hosts; Ariana was asked whether one is available. No dependency or scientific fallback was substituted.

#### 2026-09-30 — `CTX016-008`, `CTX016-009` package and documentation

- `mvn -B -U -Dmaven.repo.local=/Users/arianashannon/.m2/repository -Ppackage -Dmaven.test.skip=true verify` passed Java 17 compilation, jar/shade creation, and the `verify-no-vendor-binaries` packaging task. This package check skipped tests and cannot establish live prediction. The shaded jar is 311 MB and contains the JChronologer model resources and supported-platform PyTorch libraries. Normal `-Ppackage verify` still fails in its test phase as recorded above.
- Replaced the old smoke test, which depended on an unverified generated feature file under `target/test-classes`, with an isolated cache-guard check using copied input fixtures. Updated README with the approved prediction defaults, working-library/pair sidecars, winner-only diagnostics, cache migration, and platform limit. Focused CTX-016 tests including the replacement cache test passed 15 tests, 0 failures/errors, 1 platform-gated skip. Added a modified-sequence/two-charge sidecar check, which passed 3 sidecar tests. Scientific comparisons and both rescorer paths remain unverified.

## Approved separate follow-up — Context feature and mProphet entry points

- Ariana explicitly approved a separate fix on 2026-09-30 for the positional `features` command's out-of-range `args[7]` access and the `mprophet` command's disabled dispatcher route. This is separate from the frozen CTX-016 canonical plan. No backlog CTX ID was assigned. No Git delivery was authorized.
- [x] `CLI-FOLLOWUP-001` Make the four-argument `features` command reach Context scoring and propagate failures. Check: Java 17 compilation and focused CLI checks.
- [x] `CLI-FOLLOWUP-002` Provide a working `mprophet` command for raw and pre-split feature inputs using existing Context mProphet logic, with clear failures and output handling. Check: CLI tests, readable help, and integration where supported.
- `CLI-FOLLOWUP-001` in progress: the positional command now uses its four declared arguments, passes seed 0 to the scorer, and propagates errors. `CLI-FOLLOWUP-002` needs an entry-point adapter because the existing `ContextMProphetExecutor` has no `main` method; its commented dispatcher line cannot simply be uncommented. Keep these fixes Context-only and record validation limits separately.

#### 2026-09-30 — `CLI-FOLLOWUP-001`, `CLI-FOLLOWUP-002` implementation and direct-feature check

- The positional `features` command no longer reads `args[7]` after requiring exactly four arguments. It passes seed 0 to `ContextFeatureScorer` and propagates failures instead of printing a stack trace and returning apparent success. Java 17 compilation passed; its live raw scoring path remains limited by the unsupported local JChronologer native runtime.
- Added a Context-owned `mprophet` CLI adapter for raw or pre-split feature inputs. It checks paired sidecars by `id` or requires explicit legacy input, rejects an existing output directory, supports `-fdr`, and reuses `AllWorkflowExecutorCLI.runContextMProphet` for background model training and reference application. The latter now checks for missing model/results rather than dereferencing null. The mProphet engine class is unchanged. Focused CLI tests passed 5 tests, 0 failures/errors/skips.
- Created a temporary alternating-row split of the existing `standard_percolator_test.features.txt` fixture (500 background and 500 reference rows; background 254 targets/246 decoys) under `tmp/ctx016/mprophet-direct/`. Packaged the current command with tests skipped, then ran the shaded jar with `mprophet -background ... -reference ... --allow-legacy-features true`. It exited 0, trained a background LDA, applied it to reference, and wrote four peptide target/decoy outputs under a fresh `context-mprophet` directory. All output `PSMId` values in both populations were present in their respective input feature files. This verifies the direct legacy CLI path and ID retention on a synthetic split; the alternating split is not a biological reference/background partition and does not validate calibration or FDR.

#### 2026-09-30 — `CTX016-009`, `CLI-FOLLOWUP-002` broad rerun

- After replacing the old cache-dependent smoke test and adding CLI checks, `mvn -B -o -Dmaven.repo.local=/Users/arianashannon/.m2/repository -Ppackage verify` ran 103 tests: 4 failures, 2 errors, 9 skips. Five failures/errors are the exact cases recorded in the preimplementation baseline. The additional `ContextMProphetExecutorTest.test` failure is the documented Intel-macOS JChronologer native-library limitation (its captured stderr reports `cpu-osx-x86_64:2.7.1`), because that older test starts raw prediction. No new CTX-016 focused test failed. The Maven lifecycle stopped before packaging; the separate package check with tests skipped passed.
- Corrected the Context working-library path to preserve a completed exclusion report when every target prediction is unavailable; the focused missing-target test now checks that report. Added a direct diagnostic writer test to check pre-trim output does not mutate candidate order or the subsequent winner. Results of that latest diagnostic test are still pending at this log point.

#### 2026-09-30 — `CLI-FOLLOWUP-002`, `CTX016-016` direct reuse validation

- The direct mProphet adapter now verifies that every split feature `id`, `Label`, modified sequence, and charge agrees with its pair sidecar before engine execution. It rejects extra or missing sidecar rows, duplicate IDs, invalid labels, and malformed IDs as input errors. A focused sidecar test confirms that a changed feature label fails, while a matching file passes.
- Java 17 focused command: `mvn -B -o -Dmaven.repo.local=/Users/arianashannon/.m2/repository -Dtest=SearchPairSidecarTest,ContextMProphetCLITest,ContextFeatureScorerCLITest,ContextWorkingLibraryPreparerTest,CandidateDiagnosticWriterTest,ContextFeatureScorerPairedPartitionTest test`. Result: 13 tests, 0 failures, 0 errors, 0 skips. `git diff --check` passed. The diagnostic writer test confirms candidate order and winner are unchanged by diagnostic capture.
- `CLI-FOLLOWUP-002` is complete for its approved CLI scope: help/dispatch and error tests pass, and the shaded jar completed a direct pre-split mProphet run as logged above. Raw prediction remains unrun on this Intel Mac because the pinned JChronologer/PyTorch dependency lacks its native library; this is a CTX-016 platform validation limit, not a claim of raw-path success. No commit, push, or PR was made.

#### 2026-09-30 — `CTX016-016`, `CTX016-008`, `CTX016-009` final preflight correction

- Review found that direct `partitionFeatures` checked sidecar ID coverage but did not recheck Label, modified sequence, and charge. It now runs the shared sidecar/feature validator before partitioning. The first focused rerun compiled but failed one test because its expected error text still named the later ID-coverage check. Updated that assertion to the earlier validator's equivalent ID-mismatch error; the rerun passed 7 tests, 0 failures/errors/skips (`SearchPairSidecarTest`, `ContextFeatureScorerPairedPartitionTest`, `ContextMProphetCLITest`). This is a test-message correction; the corrupt sidecar remains rejected.
- Updated README and top-level help to match the approved `mprophet` adapter. The direct legacy fixture run produced four files under `context-mprophet/` with `.peptide.<population>.<target|decoy>.txt` names; the previous README named a different directory and target filenames. Removed its unsupported `-seed` and pass-through flag claims for the new adapter.
- Java 17 `mvn -B -o -Dmaven.repo.local=/Users/arianashannon/.m2/repository -Ppackage -Dmaven.test.skip=true verify` passed after the main CLI and sidecar changes, including shaded jar and vendor-binary verification. This package check skipped tests; the last partition validator line was then compiled and covered by the 7-test focused rerun. Full test-inclusive verify remains limited by the baseline cases and Intel-macOS native issue logged above.
- The mProphet adapter now rejects `-generateDecoys` with direct pre-split inputs because that flag has meaning only during raw scoring. The focused 7-test suite was rerun after this check and passed with 0 failures/errors/skips. `git diff --check` passed, the Context branch remains `ariana/scoring`, and the separate Encyclopedia checkout remains clean.
- CTX-016 is still in progress. Live JChronologer prediction/search needs a supported runtime, and the Percolator runtime check needs its executable and pyIsoPEP. The direct legacy mProphet run and source inspection do not establish representative calibration or FDR validity. The external completion Report and final human-review handoff therefore remain pending.

#### 2026-10-06 — `CTX016-010` first Chat-model reviewer handoff prepared

- Wrote `docs/ctx-016-first-reviewer-handoff.md` against the approved Context-only canonical revision 2 and local guide r7. It identifies the entire tracked and untracked CTX-016 change surface, separate approved CLI follow-up, scientific/compatibility review questions, known baseline failures, actual checks, and unverified live prediction, Percolator, and representative calibration work. This is a review request, not a reviewer finding or completion outcome.
- Verified current Context checkout is `ariana/scoring` at `4cccfc7b5b10e63d33360bdec23d89494be2b13c` with CTX-016 changes still uncommitted. `git diff --check` passed; no tests or package build were rerun on this date. Next action: obtain first independent Chat-model review, record its findings and corrections, then complete remaining validation before the external Report and Ariana's final review. `CTX016-010` remains open.

#### 2026-10-06 — `CTX016-010` first reviewer outcome and implementation-record sync

- First Chat-model reviewer outcome: **REQUEST CHANGES** against approved r7/canonical revision 2. Findings: sequence-only best-feature reduction occurs before charge-aware reference/background partitioning and can discard an assayed charge; a failed feature run can leave a raw TSV without its pair sidecar/manifest and block retry; and direct Percolator split-file reuse lacks CTX-016 pair-sidecar preflight. The reviewer inspected tracked and untracked code, both engine entry paths, and the pinned `SearchParameters` API. `git diff --check` passed; tests were not rerun during this read-only review. These findings await implementation and verification; `CTX016-010` remains open.
- Ariana approved the current local r7 implementation record and requested synchronization of the linked Google record. Updated that record in place to identify approved r7/canonical revision 2, retain r6 as historical, and describe the Context-only scope, work, validation limits, and pending reviewer corrections. Readback confirmed those markers and section headings. The frozen canonical plan was not edited; no Java, dependency, Git delivery, or external Report action occurred. Next action: address the three findings within approved scope, log focused checks, then continue the remaining review and scientific validation.

#### 2026-10-06 — `CTX016-010` first reviewer corrections under approved r7

- Corrected precursor reduction in `ContextFeatureScorer.partitionFeatures` to select the best feature per modified sequence and charge before reference/background partitioning. A synthetic regression gives the unassayed charge the higher score and verifies that the assayed charge remains in the reference output while the other charge goes to background.
- Added a CTX-016 in-progress marker before feature generation. On retry, a marked attempt lacking a completed manifest moves its partial feature TSV, sidecar, working library, prediction report, optional diagnostic, and marker into a nearby unique `.failed-*` directory before regeneration. Existing unmarked outputs still fail preflight, and a completed cache still requires identity verification. A focused test verifies partial artifacts and the prediction report are preserved and the output path can be reused.
- Added the existing pair-sidecar preflight to direct Percolator split-file reuse, sharing the check with mProphet. Both sidecars must match their feature files; explicit `--allow-legacy-features true` applies only when neither sidecar exists. The CLI test checks default rejection, explicit legacy acceptance, and rejection of a partial companion set. Updated README for the flag, precursor reduction, and retry behavior. No Percolator or mProphet engine class was changed.
- Verification: Java 17 Maven focused affected suite (`-Dtest=SearchPairSidecarTest,ContextWorkingLibraryPreparerTest,CandidateDiagnosticWriterTest,ContextFeatureScorerCLITest,TargetedBootstrapperTest,ContextFeatureScorerTest,ContextFeatureCacheIdentityTest,ContextFeatureScorerPairedPartitionTest,ContextPercolatorExecutorCLITest,ContextMProphetCLITest test`) passed 20 tests, 0 failures/errors/skips. The initial four-class regression suite passed 8 tests. Live JChronologer prediction/search, Percolator/pyIsoPEP execution, and representative scientific calibration remain unverified; this correction does not establish them. The first-review findings are corrected locally, while `CTX016-010` remains open pending broader review and remaining validation.
- Offline Java 17 `-Ppackage -Dmaven.test.skip=true verify` passed shaded jar creation and the vendor-binary check after these corrections. Tests were skipped in this package command; the separate 20-test run above supplies test evidence. Maven shade emitted overlapping metadata/resource warnings, as in the prior package check. No full test-inclusive verification was rerun because its recorded baseline and Intel-macOS JChronologer failures remain unresolved.
- Synchronized the linked Google implementation record by appending the correction dispositions and validation limits under a new reviewer-correction heading; readback confirmed its heading style and text. Updated the local r7 guide and README, and `git diff --check` passed. The canonical revision 2 plan was not edited. Next action: independent review of these corrections and the remaining supported-host/live scientific checks before the completion Report and Ariana's final acceptance.

#### 2026-10-06 — `CTX016-010` Reviewer #2 outcome and second correction pass

- Reviewer #2 returned **REQUEST CHANGES** with three P2 findings: direct sidecar preflight accepted inconsistent target/decoy keys or a `LOSER` row; retry recovery could move output from an active invocation; and directory hashing plus the mProphet raw-input check rejected documented Bruker `.d` acquisitions. Reviewer #2 reported 15 focused tests passing from existing compiled classes, `git diff --check` passing, and temporary probes for the sidecar and directory failures. Their review did not rebuild or edit the repository. Ariana requested implementation of these corrections under the approved Context-only plan.
- Accepted all three findings. `SearchPairSidecar` now validates `WINNER`, candidate-to-target/decoy key agreement, relation type, eligibility, mode, method, and key charge in both direct preflight and partition metadata reads. Negative regressions alter the target key, decoy key, and winner outcome of a generated sidecar. A row cannot prove that an absent paired counterpart was biologically generated from its claimed target; source generation and provenance remain the authority for that claim.
- `ContextFeatureScorer` acquires an exclusive adjacent feature-output file lock before cache inspection or recovery and holds it through feature, sidecar, manifest, and split-file publication. A second overlapping invocation fails with an in-use error and leaves the first attempt's marker/output untouched; after release, retry can archive the incomplete attempt. The lock file persists so its identity remains stable across invocations. A two-thread regression covers overlap and subsequent recovery.
- `ContextFeatureCacheIdentity` now hashes directory trees by sorted relative path, entry type, size, and file content, without timestamps; a `.d` fixture verifies stable identity and invalidation after nested content changes. The mProphet `-i` validator accepts readable `.d` directories while keeping other input flags file-only. README describes lock ownership and directory hashing. No Encyclopedia source or confidence engine class changed.
- Java 17 source and test compilation passed. The affected 10-class suite passed 24 tests, 0 failures/errors/skips; the initial five-class correction suite passed 15 tests. Offline `-Ppackage -Dmaven.test.skip=true verify` passed shaded jar and vendor-binary checks with tests skipped. `git diff --check` passed. Full test-inclusive verification was not rerun; recorded baseline failures and the Intel-macOS JChronologer limitation remain. Live prediction/search, Percolator/pyIsoPEP, representative calibration, completion Report, and Ariana's final review remain open. `CTX016-010` stays unchecked.
- Ariana asked for an implementation-record currency check before handoff. Updated the linked Google record's top status and r7 validation/next-action statements, labeled the first review outcome as historical, and added Reviewer #2 outcome and correction sections. Readback confirmed current status, section headings, and no remaining current-section claim that reviewer corrections are pending. The old r6 template still says approval/pending implementation because it is explicitly labeled superseded history, not current status. Added a matching current-status note to the top of the local guide. The Google and local records both leave `CTX016-010` open and distinguish completed checks from live/scientific validation still needed.

#### 2026-10-06 — `CTX016-010` Git delivery draft and 6.9.23 alignment

- Ariana requested a Git delivery draft, then specified that Encyclopedia 6.9.23 should be the active version everywhere. Wrote `docs/ctx-016-git-delivery-draft.md` with proposed r7/CLI commit groups, exact paths, exclusions, and readiness checks. No staging, commit, push, PR, or release occurred.
- Aligned the active `build.sh` jar path, test/image/release workflow jar checks, and `ScribeTwoFeatureGenerator` dependency metadata/error with the already pinned 6.9.23 POM and CTX-016 provenance. The only remaining active-source text mentioning 6.6.24 is the historical attribution comment for `ContextPercolatorRunner`; old r6/task records also retain their original historical wording. The tracked 6.6.24 bundle remains unused and was not deleted.
- Java 17 Maven recompiled 44 production and 30 test source files. A two-method `ScribeFeatureTableTest` run reported 1 pass and 1 known baseline failure: `missingRuntimeHasActionableError` expects lowercase `no uncalibrated fallback`, while the existing message begins uppercase `No`; line 75 failed. `bash -n build.sh` failed at its pre-existing line-124 syntax error. These outcomes do not verify full build-script or CI execution. The new 6.9.23 jar exists locally; a clean-repository dependency check and actual CI remain unrun. Next action: review the proposed commit scopes and validation gates, then seek explicit authorization for each actual commit.

#### 2026-10-06 — `CTX016-006` Commit 1 staged draft; awaiting Ariana's approval

- Ariana clarified that the six proposed commits should be drafted in Git on the command line and approved one at a time. The Google implementation record now contains the detailed six-commit proposal and exclusions; readback confirmed the new section and heading.
- Staged only `src/main/java/org/searlelab/context/io/TargetedBootstrapper.java` and `src/test/java/org/searlelab/context/io/TargetedBootstrapperTest.java` for proposed message `Record empirical entrapment assignments`. `git diff --cached --check` passed; staged scope is exactly two files, 61 insertions and 2 deletions. `git commit --dry-run -m "Record empirical entrapment assignments"` succeeded and showed only those two files in the proposed commit. `-Dtest=TargetedBootstrapperTest test` passed 2 tests, 0 failures/errors/skips. No commit was created. Await Ariana's explicit approval before running `git commit` or staging Commit 2.

#### 2026-10-06 — `CTX016-006` Commit 1 approved and created

- Ariana explicitly approved the drafted Commit 1 message and two-file scope. Verified the staged names and `git diff --cached --check`, then ran `git commit -F tmp/ctx016/commit-1-message.txt`. Commit `07975eb9f1b5dcdf56257ce4e67cd56cd1a41890` contains only `TargetedBootstrapper.java` and `TargetedBootstrapperTest.java` (61 insertions, 2 deletions). The staged index was empty afterward. No push, PR, release, or other commit was made. Next action: draft Group E Commit 2 for Ariana's separate approval.

#### 2026-10-06 — `CTX016-014`–`CTX016-016`, `CLI-FOLLOWUP-001`–`CLI-FOLLOWUP-002` remaining Git commits authorized and created

- Ariana clarified that seeing one drafted commit was sufficient and explicitly authorized the remaining commits and push. The preparer imports a public class absent from the previously committed 6.6.24 jar, so the 6.9.23 dependency and build/CI alignment were committed with the preparer in Group 2. The revised scope is recorded in `docs/ctx-016-git-delivery-draft.md`. Each staged file list matched its group and each staged diff passed `git diff --cached --check`.
- Commit `c4a0cde` (`Pin Encyclopedia 6.9.23 and prepare matched search libraries`) contains the 6.9.23, JavaPot 1.0.5, and JChronologer 1.0.10 bundled artifacts, active version paths, Context working-library preparer, and its tests. Four focused tests passed, with one platform-gated live test skipped on Intel macOS. The JavaPot and JChronologer artifacts are needed by the approved 6.9.23 dependency path.
- Commit `860d728` (`Score matched decoys through Context feature path`) contains Context's copied scoring adaptations and diagnostic writer. `EncyclopediaTwoMatchedDecoyTest,CandidateDiagnosticWriterTest` passed 3 tests. This Maven run recompiled 44 production and 30 test classes using Java 17; missing dependencies were resolved into this checkout's Maven repository.
- Commit `7b06e33` (`Track search pair identity through Context features`) contains sidecar validation, charge-aware partitioning, directory-content cache identity, exclusive output locking/recovery, Percolator direct-input preflight, and focused regressions. `SearchPairSidecarTest,ContextFeatureCacheIdentityTest,ContextFeatureScorerPairedPartitionTest,ContextFeatureScorerTest,ContextPercolatorExecutorCLITest` passed 12 tests. The positional `features` fix shares `ContextFeatureScorer.java` and is in this commit.
- Commit `16275b1` (`Expose Context feature and mProphet CLI paths`) contains CLI dispatch, raw and split mProphet input validation, and CLI tests. `ContextFeatureScorerCLITest,ContextMProphetCLITest` passed 6 tests. No pull request or release is authorized. Next action: commit the documentation and task log, then push `ariana/scoring` to `origin` as requested. `CTX016-010`, supported-host live prediction/search, Percolator/pyIsoPEP, representative calibration, completion Report, and final human review remain open.

#### 2026-10-06 — `CTX016-010` documentation commit and push credential blocker

- Created documentation commit `891b1f4` (`Document CTX-016 implementation and review evidence`) with README, local guide, frozen canonical plan, first-review handoff, Git delivery record, and this task log. Its six-file staged diff passed `git diff --cached --check`. The unrelated `.gitignore` edit and local CTX-015/R-history files remain excluded.
- `git push -u origin ariana/scoring` failed with HTTP 400; an HTTP/1.1 retry disconnected. A buffered HTTP/1.1 retry reached GitHub and returned the definitive rejection: the current Personal Access Token cannot update `.github/workflows/image.yml` without `workflow` scope. The remote `ariana/scoring` head remained `138e34f`; local HEAD is `891b1f4`. A read-only SSH check using GitHub's verified host key failed `Permission denied (publickey)`. No push succeeded. Next action: use a GitHub credential with workflow-file write permission and retry the same non-force push; do not remove the version-alignment workflow changes merely to bypass the rejection. No PR or release was attempted.

#### 2026-10-07 — `CTX016-010` Git delivery verified

- Ariana added the token's `workflow` permission and retried the push; her terminal still reported HTTP 400 and a disconnect. A buffered HTTP/1.1 retry returned `Everything up-to-date` and set the upstream tracking branch. A fresh `git ls-remote --heads origin ariana/scoring` independently confirmed remote HEAD `891b1f4652cecf1a4f19c2f8e6d663138b8f535d`, matching local HEAD. The six CTX-016 commits are therefore present on `origin/ariana/scoring`. The earlier failed-push entry remains as history. No PR or release was attempted. `CTX016-010`, supported-host live prediction/search, Percolator/pyIsoPEP, representative calibration, completion Report, and Ariana's final review remain open.

#### 2026-10-10 — `CTX016-010` Reviewer 3 compatibility review

- Ariana requested Reviewer 3 review of latest `ariana/scoring`, prioritizing Windows/macOS/Linux compatibility and required libraries. Authorized fast-forward pull returned already up to date at `891b1f4652cecf1a4f19c2f8e6d663138b8f535d`; preserved existing changes. Read current Google record, local guide, canonical revision 2, and source/dependency APIs. Review outcome: **REQUEST CHANGES**, detailed in `docs/ctx-016-reviewer-3.md`.
- Findings: shaded PyTorch platform metadata collision triggers download fallback on Windows/Linux despite bundled binaries; Intel macOS prediction unsupported; Apple Silicon Percolator needs x86_64 translation or a compatible executable; packaged platform runtime validation/setup guidance incomplete. Metadata-only JVM probe confirmed Windows/Linux placeholder detection against the existing shaded artifact. No Windows/Linux native execution or Maven rebuild was performed.
- Java 17 manual compilation of current production/test sources passed. Focused JUnitCore run reported `OK (19 tests)` with the live prediction test assumption-skipped on Intel macOS. Logs are under `/private/tmp/ctx016-reviewer3/`. Maven verification unrun because the documented Maven executable and system Maven are absent. Initial manual harness classpath errors were corrected; they are not repository defects.
- Ariana supplied Windows console log `Downloads/Untitled.tsv`: PyTorch initializes, then `LibraryFile.getUniqueTargetPrecursorMetadata()` raises `NoSuchMethodError`. Verified that bundled/local cached 6.9.23 jars contain the API and have identical SHA-256; the Windows loaded jar remains unidentified. Requested launch command/IDE configuration to identify its runtime dependency origin. No fixes were implemented. Next: identify Windows class origin and prepare approved compatibility corrections; live engines/scientific validation and final human review remain open. Canonical plan unchanged; `CTX016-010` remains unchecked.


### CTX-016 — uu-revise #3 corrective checklist (2026-10-10)

Scope authorization: Ariana requested implementation of Reviewer 3 corrections through `uu-revise #3`, and approved the Linux container route for Intel Macs. Guide: r7, correction addendum R3; canonical revision 2 remains unchanged. No predictor version change or Git delivery is authorized. Acceptance: preserve the single-jar CLI and scientific behavior, expose each bundled native descriptor to DJL, identify incompatible Encyclopedia APIs, and provide platform setup and packaged validation.

- [x] `CTX016-R3-01` Preserve native metadata during packaging and narrowly adapt discovery in Context. Files: POM, runtime helper, prediction entry point/tests. Check: packaged Windows x86_64, Linux x86_64, and macOS ARM metadata selection needs no download.
- [x] `CTX016-R3-02` Check required Encyclopedia APIs before prediction and report the loaded artifact on mismatch. Files: runtime helper/tests and README. Check: incompatible API regression preserves cause/location; pinned API passes.
- [ ] `CTX016-R3-03` Document Windows/macOS prerequisites and the approved Intel Mac container route; add packaged runtime platform checks. Files: README, CI, test helpers. Check: local checks pass; unavailable platform execution stays unrun.
- [ ] `CTX016-R3-04` Review corrections, record dispositions/validation, and hand off for independent re-review. Files: guide/review/log. Check: canonical plan and unrelated work preserved; no claim of final acceptance or scientific validation.

Proposed commit groups (scope only; no staging/commits): R3-A (`CTX016-R3-01`, `CTX016-R3-02`), `Preserve native discovery and diagnose incompatible prediction runtimes`; R3-B (`CTX016-R3-03`, `CTX016-R3-04`), `Validate and document Context platform prerequisites`.

#### 2026-10-10 — `CTX016-R3-01`–`CTX016-R3-04` started

- Read the conversation, invoked `uu-revise`, and inspected instructions, canonical plan, guide, review, source, and tests. Accepted the metadata collision and API mismatch. Intel native support is replaced by Ariana's approved Linux-container route. Reuse the Ant packaging plugin to retain classifier-specific descriptors and a narrow Context classloader adapter for DJL discovery, retaining a self-contained jar and pinned dependencies.
- Downloaded repository-documented Maven 3.9.9 locally for validation. Existing build.sh syntax/setup defects remain outside this correction. The Windows log's exact loaded jar remains unidentified; diagnostics can improve but its environment cannot be claimed repaired.


#### 2026-10-10 — `CTX016-R3-01`, `CTX016-R3-02` checks and approved engine-launch correction

- Maven package verification passed 25 tests (0 failures/errors, 1 Intel-Mac live-prediction skip) and verified the rebuilt jar's three native descriptors and all listed native libraries. Four runtime-helper regressions passed after adding partial-metadata and Intel-route checks. Logs: `tmp/ctx016/revise3/package.log`, `runtime-tests.log`.
- Reproduced a pre-existing clean-directory Percolator launch failure: `NoSuchFileException: target/debug-percolator.exe`. Ariana explicitly approved removing the unconditional debug copy and adding a clean-directory regression. This narrowly expands R3-A / `CTX016-R3-03` to ContextPercolatorRunner and its tests without changing engine parameters or scoring. The Docker JavaPot setup-step correction remains separately pending. Preserve all prior plan/history.


### CTX-016 — runtime naming correction (2026-10-10)

Approval: Ariana explicitly requested removal of tracking codes from all Context code, including CLI messages and internal strings. Tracking IDs remain in planning/history documents only. This extends the current corrective scope; canonical revision 2 remains unchanged.

- [x] `CTX016-R3-05` Replace tracking-code messages, temporary names, cache names, and identity namespace with functional Context names in source, tests, and active CI. Update README migration guidance; verify sidecar/cache and CLI regressions plus a source scan. Proposed separate commit scope: `Use functional names in Context runtime code` (execution not authorized).

#### 2026-10-10 — `CTX016-R3-05` started

- Replaced tracking-code strings in runtime source and tests, including cache filenames and identity salt. Existing outputs are preserved; older raw caches require fresh generation, while valid split sidecars retain their unchanged schema. README documents naming and lock compatibility. No historical planning record or frozen canonical plan was renamed.

#### 2026-10-10 — `CTX016-R3-05` regression results

- Java 17 rebuilt production/test sources and ran 31 focused tests: 0 failures, 0 errors, 1 expected Intel-macOS live-prediction skip. This includes cache recovery/locking, sidecar validation, CLI parsing, runtime API/platform checks, and clean-directory Percolator command construction. Package verification is still running. `git diff --check` and the tracking-code scan of source/tests/build configuration pass. No classes were relocated.

#### 2026-10-10 — `CTX016-R3-01`, `CTX016-R3-05` package verification

- Java 17 targeted `-Ppackage verify` passed (31 tests, 0 failures/errors, 1 platform skip); shaded-artifact-only checks selected Windows x86_64, Linux x86_64, and macOS ARM descriptors without placeholders and verified all named native resources. Log: `tmp/ctx016/revise3/final-package.log`. These metadata checks do not execute the three native platforms. Naming scan and diff whitespace checks pass; canonical plan is unchanged.
- Initial packaged mProphet execution completed but the new test helper rejected the established `pi_0` footer and FASTA I/L expansion. Corrected the helper to ignore the summary footer and compare peptide IDs within I/L equivalence; no analysis code changed. Engine rerun evidence is recorded separately below. Docker build-step approval remains pending and Docker is unavailable locally.

#### 2026-10-10 — `CTX016-R3-03`, `CTX016-R3-04` packaged engine evidence and handoff

- Both packaged engines completed on this Intel Mac from a fresh directory outside the repository root using the existing 1,000-row fixture split into 500 background/500 reference rows. mProphet's four peptide reports passed population-ID checks accounting for established I/L expansion and the summary footer. Percolator trained, rescored 500 reference rows, and completed both pyIsoPEP calls; reference-ID checks passed. Java 17, project-local pyIsoPEP 0.1.4; logs: `tmp/ctx016/revise3/final-engine-check-2.log` and `tmp/compatibility/platform-engines/run-_4vx35bj/`. This proves local launch/runtime integration, not representative calibration or cross-platform execution.
- Reviewed the correction diff and added evidence to the Reviewer 3 handoff. No dependency version change, canonical-plan edit, class relocation, staging, commit, push, or remote CI execution occurred. Native Windows/Linux/Apple-Silicon prediction checks, full raw search and scientific validation remain unrun. Docker setup correction remains pending Ariana's earlier decision and cannot be tested locally without Docker. R3-03/R3-04 and final independent/human review remain open; no completed-feature Report is claimed.

### Follow-up — functional class guide

Requested by Ariana on 2026-10-10; documentation task only, with no class relocation authorized.

- [ ] `CLASS-GUIDE-001` Create a concise document explaining every Context production class, grouped by function (CLI/input, library preparation/prediction, search/features, identity/cache, scoring/confidence, outputs), rather than by directory. Describe each class's responsibility and major collaborators, emphasizing classes added or changed in the implementation delivered through `891b1f4`. That exact commit changes documentation only; identify the preceding implementation commits explicitly. Include a function-to-current-package map and evidence-based restructuring observations for Ariana's review. Check completeness against production source inventory and Git history. Proposed documentation-only commit: `Document Context classes by responsibility`; no implementation or Git delivery is authorized by this follow-up task.


### CTX-016 — supplied ten-file Linux validation (2026-10-10)

Ariana supplied ten matched DIA/assay pairs, the library and FASTA, then authorized installing Docker and proceeding after freeing storage. Scope: local installation and validation only; no Git delivery or unrelated build-script repair.

- [ ] `CTX016-R3-06` Install Intel Docker Desktop and validate the rebuilt Java 17 artifact in a Linux container using copied supplied inputs, seeds 1–10. Record runtime/image identity, input checksums, commands, outputs, and failures. Keep source inputs intact. This validates Linux-container execution, not native Windows/macOS prediction or general FDR calibration.

#### 2026-10-10 — `CTX016-R3-06` started

- Confirmed 70 GiB available on macOS 15.7.9 Intel. Downloading the official Intel Docker installer. Prepared isolated copies under `tmp/compatibility/ten-file-20261010` with SHA-256 input manifest. Test the current jar in a Java 17 Linux runtime directly; repository Dockerfile JavaPot correction remains separately pending.

#### 2026-10-10 — `CTX016-R3-06` Docker installed; Linux check started

- Installed verified official Intel Docker Desktop 4.94.0 (CLI/engine 29.8.2). Silent command-line installer was terminated before installation; documented app-copy method succeeded and Desktop initialized Linux x86_64 with approximately 4 GB RAM. Downloaded official `eclipse-temurin:17-jdk`, digest `sha256:5d6042fb8cdc14d614e4e421f52e3211fc5aeadddce44cbf9de8ed37c791f824`.
- Started packaged live prediction/native executable smoke check with Java heap capped at 2 GB, fresh temporary native caches, networking disabled and read-only artifact/source mounts. Log: `tmp/compatibility/ten-file-20261010/linux-smoke.log`. Task-focus check: work remains the authorized supplied-data validation; no repository Dockerfile or scientific code changes.

#### 2026-10-10 — `CTX016-R3-06` Linux smoke passed; supplied-data run launched

- Offline Linux smoke exited 0: all native resources loaded from the jar, one target/decoy prediction persisted, and bundled Percolator launched. First dataset command mistakenly selected positional `features`; it exited 1 with usage before analysis (`features.log`). Corrected the harness to the source-verified `features-folder` dispatch and retained the first log. Current full run uses seeds 1–10, Java `-Xmx2g`, networking disabled, the pinned image digest and copied inputs. Log: `features-folder.log`; command: `test-command.txt`.

#### 2026-10-10 — `CTX016-R3-06` long-running dataset status

- Docker installation and offline Linux smoke validation are complete. Full supplied-data run remains active in local container `context-ten-file-validation` (exec session 52329), still on seed 1 with 8 completed 1,024-request prediction batches at the latest check and no logged errors. The source library has 99,147 distinct modified-sequence/charge entries. Observed container memory was approximately 872 MiB of 3.82 GiB. No seed is complete yet; full analysis remains unverified.
- The container has networking disabled; only the current jar (read-only) and isolated validation workspace are mounted. Output log: `tmp/compatibility/ten-file-20261010/features-folder.log`. Keep the machine awake and Docker running for execution to continue. No background notification automation or completion claim was created. Resume by checking this container/log and validating final artifacts against `input-manifest.json`; do not start a duplicate run.


### CTX-016 — supplied seed-1 all-workflow analysis (2026-10-11)

- [x] `CTX016-R3-07` Run AllWorkflowExecutorCLI on the three supplied seed-1 feature tables with the matching FASTA, default 1% FDR and training seed 1. Validate pair sidecars before all six workflows, preserve source data through snapshots/checksums, record completion/failures and outputs. Ariana explicitly requested this analysis; no engine edits or Git delivery authorized.

#### 2026-10-11 — `CTX016-R3-07` started

- Inspected CLI: six workflows (Context/discovery/targeted Percolator and mProphet). Prepared immutable-source copies and SHA-256 manifest under `tmp/compatibility/all-workflows-20261011/inputs`. Use native Java 17 and existing project-local pyIsoPEP 0.1.4; this feature-only stage does not require predictor natives. Explicit `--seeds 1` supplies fresh-directory protection and experiment provenance.

#### 2026-10-11 — `CTX016-R3-07` six workflows completed

- Packaged AllWorkflowExecutorCLI exited 0; run.properties records COMPLETE. Java 17, native Intel macOS, pyIsoPEP 0.1.4, training seed 1, FDR 0.01. Sidecar preflights passed for all three input tables. Copied input checksums remained unchanged, and all six target peptide reports contain finite q-values/PEPs in [0,1].
- Target peptide report rows with q <= 0.01: Context Percolator 89/200 reference; targeted Percolator 86/200 reference; Context mProphet 95/200 reference; targeted mProphet 98/200 reference; standard Percolator 1180/6210 combined; standard mProphet 1350/6210 combined. Combined-population counts must not be compared directly with reference-only counts as sensitivity evidence. These are reported q-value thresholds, not independently measured error rates.
- Outputs: `tmp/compatibility/all-workflows-20261011/results/cd8_2mz_gpf_masked1_assay/seed_1/`. Root also contains exact command, input/artifact SHA-256 manifest, workflow log, exit code, summary.json, and summary.tsv. Processed only the seed-1 file set requested in the preceding message; no prediction rerun, source-code edit, Git delivery, or final feature-acceptance claim.


### CTX-016 — ten-dataset non-entrapment PEP comparison (2026-10-11)

- [x] `CTX016-R3-08` Ariana requested and clarified: process all ten datasets through all six workflows, then draw a violin of dataset-level counts of non-entrapment reference peptides at PEP <= 0.01. Fix training seed 1, reuse completed dataset 1, snapshot/preflight remaining datasets, and preserve input data. Define non-entrapments using assay isDecoy=false joined to reference target keys; count unique modified peptide sequences, collapsing charge states. Deliver PNG/PDF/SVG, source and counts; do not claim independent biological replication or calibrated FDR from the plot.

#### 2026-10-11 — `CTX016-R3-08` started

- All ten feature trios are present. Exact modified-sequence/charge matching accounts for every reference target; 98–100 non-entrapment reference feature rows per dataset. Sequential batch for datasets 2–10 started under `tmp/compatibility/reference-pep-20261011/`, with explicit training seed 1 and fresh output directories. Dataset 1 reuses the completed six-workflow analysis. Plotting script will require all ten COMPLETE manifests before rendering.

#### 2026-10-11 — `CTX016-R3-08` scope reduced by Ariana

- Ariana requested only three datasets instead of ten. Supersedes the ten-dataset scope above: use datasets 1–3 only, with identical PEP/non-entrapment/counting definitions. Stopped only the Python batch supervisor while dataset-2 Java continued; this prevents datasets 4–10 from starting. Dataset 2 completion will be verified through its COMPLETE experiment manifest and final log because its supervisor can no longer record process exit. Dataset 3 uses a separate single-dataset script. Plot will show all three observations and explicitly note the limited sample size.

#### 2026-10-11 — `CTX016-R3-08` three-dataset plot completed

- Dataset 2 completed with exit 0 before supervisor termination; dataset 3 was prepared/preflighted but not launched. An initial dataset-3 restart safely refused its existing directory; resumed using its checksum-verified copies, then all six workflows completed with exit 0. No dataset 4–10 directory was created. Dataset 1 reused existing complete results. All copied input checksums passed.
- Exact reference ID and assay modified-sequence/charge joins passed across all 18 comparisons; eligible non-entrapment unique modified peptides = 98, 98, 99. Counts at PEP <= 0.01 by dataset 1/2/3: Context Percolator 75/80/85; standard Percolator 79/81/78; targeted Percolator 34/76/41; Context mProphet 90/95/90; standard mProphet 90/92/89; targeted mProphet 91/95/83. Counts collapse charge states and use PEP, not q-values.
- Created and visually inspected PNG violin with all three observations shown, plus PDF/SVG, counts.tsv, peptide-level PEP evidence, reproducible Python and methodology README under `tmp/compatibility/reference-pep-20261011`. Installed matplotlib/seaborn only in the existing project-local Python environment; initial bundled Python lacked matplotlib. Font-cache warnings did not prevent successful rendering. These are descriptive masked-dataset results, not independent biological replicates or scientific calibration proof. No source-code changes or Git delivery.


### CTX-016 — r8 proposal: per-peptide prediction store and shared working library (2026-10-11)

- **Approval status: `Approved` — accepted by Ariana 2026-10-11** in chat ("update this record to r8 and note my acceptance"), after she answered the design decisions and the two open sub-questions (below). Guide revision r8 is recorded in the Google record https://docs.google.com/document/d/1u04aFpKACUNP8Qtfl463GUPGoW5WW3zGEcpELGkKPH4/edit (sections 15–19, added 2026-10-11) and in the local guide. The acceptance covers the r8 scope in `CTX016-017` to `CTX016-024`. It does not authorize Git delivery or constitute final feature acceptance. Implementation of r8 has not started. Canonical plan `docs/uu-ctx-016-encyclopediatwo-matched-decoys.md` stays frozen until the approved handoff. No code, dependency, or Git action has been performed.
- **Trigger:** Ariana reports that feature generation takes very long and that she searches overlapping libraries. `ContextFeatureScorer.scoreFeaturesForContext` re-runs `ContextWorkingLibraryPreparer.prepare` for every raw file, although `prepare` reads neither the raw file nor the assay schedule. The working library name and cache identity include the raw file, so each seed re-predicts the same library and saves its own copy. `ContextFeatureScorerCLI` also processes seeds sequentially.
- **Impact:** changes cache identity, file layout, and CLI options. It is a material plan change under CLAUDE.md. No existing task is superseded: `CTX016-014` to `CTX016-016` keep their approved behavior, and r8 changes where predictions come from and how the working library is cached.
- **Out of scope for r8:** parallel runs (separate follow-up after r8), GPU/CUDA prediction (abandoned by Ariana), a change to Encyclopedia source, and any change to Percolator or mProphet classes.

#### Proposed design (r8)

1. **Per-peptide prediction store (shared across libraries).** A Context-owned `prediction-cache` folder created in the output directory (decision of 2026-10-11; see below for what "output directory" means today and the optional override). It is persistent, not a temporary location. It uses Encyclopedia 6.9.23's public prediction-cache API on `LibraryFile` (`openOrCreatePredictionCache`, `addPredictionCacheEntries`, `getPredictionCacheEntries(precursors, ...)`, `acquirePredictionCacheWriteLock`, `clearPredictionCacheWriteLock`). It does not use Encyclopedia's default per-user cache directory, so it never mixes with GUI predictions. It holds predicted spectra only, keyed by modified sequence and precursor charge, with no accessions and no decoy links.
2. **Profile.** A Context manifest beside the store records the profile that produced it: NCE, default charge, DIA-adjustment flag, JChronologer version, Encyclopedia version, and Context cache-schema version. Encyclopedia's file name already encodes NCE, default charge, and DIA/DDA, and Context adds the rest. A store whose profile differs is a hard error, never a silent overwrite. `addPredictionCacheEntries` replaces rows with the same (sequence, charge) key without checking the profile, so Context must provide that guard.
3. **Assembly per source library.** `ContextWorkingLibraryPreparer` keeps its current decoy selection (unchanged public `DecoyPeptideGenerator`), target retention, exclusion reasons, and assignment report. It looks up each exact (sequence, charge) target and decoy in the store, predicts only missing keys with the existing bounded batches and failure isolation, writes new spectra back under the store write lock, then builds the working library with this library's accessions and decoy links. Exact-precursor lookup is used. `getMissingPredictionCachePeptides` is not used: it matches by sequence only and treats a peptide as missing if any row lacks ion mobility.
4. **Shared working library.** The assembled working library and assignment report are cached once per source library under a manifest hashing the source library content, the profile, and the store entries used. All raw files that use that library reuse it. The in-progress marker, archive-on-failure recovery, and exclusive lock apply at this level.
5. **Per-run features.** Per-run feature manifests record the working-library hash. The raw file, assay schedule, FASTA, and diagnostics settings still define per-run identity.
6. **Store lock policy.** Encyclopedia's write lock is a database row with host, PID, and start time. It is released in `finally` blocks and never expires. After a crash it remains until cleared. Interactive Encyclopedia prompts a person. Context runs headless, so it fails with the lock's host, PID, and age and clears a lock only with an explicit flag (`--clear-stale-store-lock`).

#### Evidence from checks run 2026-10-11 (feeds r8; detail in log)

- Prediction output varies slightly with batch composition. Same batch repeated: bit-identical. Different batch composition: 27 of 400 spectra differ; single-peptide vs batched: 60 of 60 differ. Maximum differences: intensity 1.7e-5, retention time 2.4e-4 s, ion mobility 6e-5; fragment m/z identical. A cached spectrum can therefore differ from a freshly predicted one in about the fifth decimal place. This is a recorded source of nondeterminism. Comparisons of old and new outputs use a tolerance of 1e-4 on intensity and retention time, not exact equality. **Correction 2026-10-11 (`CTX016-022`):** the 1e-4 tolerance is wrong for retention time; this same check measured retention-time differences of 2.4e-4 s. Per-quantity tolerances replace it: intensity 1e-4, ion mobility 1e-4, retention time 1e-3 s, fragment and precursor m/z 1e-6.
- NCE, default charge, and the DIA adjustment flag change spectra substantially. Fragmentation type, fragment tolerance, fixed modifications, and thread count did not change any of the 400 predicted spectra. The acquisition parameter was not tested.
- Encyclopedia cache round trip: 2,000 spectra stored and read back bit-identical including retention time; an unstored charge was correctly absent. About 700 bytes per entry (1.4 MB for 2,000). A 1,112-target library with decoys saved as a 1.27 MB working library (about 570 bytes per entry). Estimated store size is on the order of 0.7 GB per million spectra.
- Search never writes the working library. `LibraryFile.openFile()` copies a user file to a temporary file unless opened in place; hash unchanged after open/read/close of an `.elib` fixture. Consequence: each run copies the whole working library to a temporary file, which costs time and disk.
- Decoy collisions: `DecoyPeptideGenerator.getUniqueDecoyPeptideModSeq` (Bitbucket master, matches 6.9.23 bytecode) tries the reversed peptide, then up to 3 terminus-preserving scrambles seeded by the target's `hashCode()`, then returns empty (target-only). Tested with oxidized methionine, carbamidomethyl cysteine, phosphoserine, I/L swaps, and a palindrome: none missed a forced collision. Target order from `getUniqueTargetPrecursorMetadata` was sorted and stable across reopening on the fixture. Not investigated: some scrambled fallback decoys ended in a different residue than the reversed sequence.
- Speed on this host (Linux x86_64, 4 cores, CPU, JDK 21): about 1,250 spectra/s; a full feature run on the repo's IL2 fixture (1,112 targets) took 8.4 s, of which about 3 s was prediction. Real-library profiling was skipped at Ariana's direction, so the speed-up from r8 on production-size runs is not measured.

#### Design decisions — answered by Ariana 2026-10-11

- [x] **Store location:** a `prediction-cache` folder in the output directory. Ariana asked whether Encyclopedia keeps this in a temporary directory and said to use the output-directory folder if unclear. Finding: Encyclopedia's default prediction cache is a persistent per-user directory (`~/Library/Application Support/EncyclopeDIA` on macOS, `%LOCALAPPDATA%\SearleLab\EncyclopeDIA` on Windows, `$XDG_DATA_HOME/EncyclopeDIA` or `~/.encyclopedia` on Linux), opened in place. The only temporary files are `LibraryFile`'s per-open working copies, deleted on close. A temporary store would lose its predictions after each run, so the decision stands as `prediction-cache` in the output directory. Note: Context writes outputs beside each acquisition file (`baseName` is the acquisition path minus its extension), so the "output directory" is that file's folder. Runs share a store only when their acquisition files are in the same folder. **Override accepted 2026-10-11:** an optional `--prediction-store <dir>` lets runs in different folders share one store; the default remains the `prediction-cache` folder in the output directory.
- [x] **Migration:** accept regeneration. Existing per-run feature caches and per-run working libraries are not reused under r8, and no new legacy-read path is added. The existing r7 `--allow-legacy-features` flag (pre-CTX-016 feature files without sidecar or manifest) is not changed by r8; Ariana confirmed this reading on 2026-10-11.
- [x] **Working-library retention:** keep the working library after the runs.
- [x] **Stale-lock policy:** fail with details (lock host, PID, age) and clear the lock only on an explicit flag (`--clear-stale-store-lock`).

#### Proposed r8 tasks (appended; IDs are new)

- [x] `CTX016-017` Approve guide r8 and this checklist. Done 2026-10-11: Ariana answered the design decisions, added the `--prediction-store` override, confirmed the `--allow-legacy-features` reading, and accepted r8 in chat. r8 is recorded in the Google record (sections 15–19) and the local guide. Canonical plan adoption remains for the next approved handoff.
- [x] `CTX016-018` Add a Context-owned prediction store, created as a `prediction-cache` folder in the output directory, around Encyclopedia's prediction-cache API: profile manifest and hash, profile-mismatch error, exact-precursor lookup, write-back, write lock with the headless stale-lock policy. Files: new Context class, focused tests. Check: bit-identical round trip, profile mismatch refused, missing charge absent, lock held/stale/cleared cases, no write when profile differs.
- [x] `CTX016-019` Change `ContextWorkingLibraryPreparer` to assemble from the store and predict only missing keys; keep decoy selection, target-only, exclusion reasons, assignment file, and failure isolation unchanged. Files: preparer, tests. Check: full hit predicts nothing; partial hit (overlapping libraries) predicts only new keys; empty store matches current behavior within 1e-4; failure in one batch isolates and is recorded; same assignments as current code on the fixture.
- [x] `CTX016-020` Add the shared working-library cache and manifest, in-progress marker, archive-on-failure, and lock at library level; open the cached library for each run; include the working-library hash in per-run feature identity; old per-run caches are regenerated and no new legacy-read path is added; the working library is kept after runs. Files: `ContextFeatureScorer`, `ContextFeatureCacheIdentity`, tests. Check: second raw file with the same library performs no prediction and no library rebuild; stale or partial library cache fails clearly and recovers; per-run feature outputs and pair sidecar match the current implementation within tolerance; concurrent library creation is serialized.
- [x] `CTX016-021` Add and document the `prediction-cache` default location, the explicit stale-lock flag (`--clear-stale-store-lock`), and the approved optional `--prediction-store <dir>` override in the feature and workflow CLIs. No retention option is needed: the working library is kept. Files: CLI adapters, README, help. Check: required vs optional inputs documented; examples run; help matches behavior.
- [x] `CTX016-022` Validate equivalence and overlap behavior: old vs new on the repo fixture within the recorded tolerance; two libraries sharing peptides; two seeds sharing one library; cache hit counts logged. Files: tests and log. Check: commands, tolerances, counts, skips, and failures recorded. This is functional validation, not scientific calibration.
- [x] `CTX016-023` Run focused JUnit 4 tests and package checks on Java 17; review the diff for minimal scope and reuse of Encyclopedia APIs. Files: log. Check: actual Java version, commands, results, and residual risks recorded; passing tests are not claimed as FDR validation.
- [ ] `CTX016-024` Record r8 evidence and outcome in the Google record, the local guide history, and the external Report at the existing handoff (`CTX016-011`, `CTX016-012`). Check: Report contains the full checklist and log; final acceptance remains Ariana's.

Parallel raw-file processing (`--parallel-runs`) is not in this checklist. After r8, propose it separately: it would split the thread budget across runs, keep per-run outputs and locks, and share the library cache and store.

#### Work log

##### 2026-10-11 — `CTX016-017` r8 drafted, approval pending

- Attempt/change: Investigated slow feature generation. Read the Google record, `ContextFeatureScorer`, `ContextFeatureScorerCLI`, `ContextWorkingLibraryPreparer`, `ContextFeatureCacheIdentity`, `ContextPredictionRuntime`, the bundled `jchronologer-1.0.10.jar` bytecode, and Encyclopedia source on Bitbucket master (`DecoyPeptideGenerator`, `JChronologerLibraryPredictionClient`, `JChronologerWorkingLibrary`, `LibraryFile`, `AnnotatedLibraryEntry`). Ran read-only experiments with scratch code outside the repo, using the repo's compiled classes and the bundled jars.
- Environment: Linux x86_64, 4 cores, 15 GB RAM, JDK 21 (not the Java 17 baseline). `./build.sh maven` failed here with `MAVEN_REPO: readonly variable`; it was not edited. Maven 3.9.9 was fetched by hand into the ignored `build-cache/`. `mvn -DskipTests test-compile` succeeded; `ContextWorkingLibraryLiveTest` passed (1 test, live CPU prediction on Linux).
- Result: see "Evidence from checks" above. No repo files other than this `TASKS.md` section were changed; `target/` and `build-cache/` are ignored. The experiment sources are in the session scratchpad and are not preserved in the repo; they can be turned into JUnit tests under `CTX016-018`/`CTX016-022` if approved.
- Not run: Java 17 builds; real-library profiling; GPU/CUDA (abandoned); Percolator with pyIsoPEP; production-size prediction or search; the `-acquisition` parameter.
- Next action: Ariana reviews r8 and answers the four decisions. No implementation until approval is recorded.

##### 2026-10-11 — `CTX016-017` design decisions recorded; Google record update blocked

- Attempt/change: Recorded Ariana's answers to the four r8 design decisions in this section, and updated tasks `CTX016-017`, `CTX016-018`, `CTX016-020`, `CTX016-021`. Checked her question about Encyclopedia's temporary directory against `JChronologerWorkingLibrary.getDefaultPredictionCacheDirectory` and `LibraryFile.openFile`: the prediction cache is persistent and per-user, and only `LibraryFile` working copies are temporary. Tried to add the r8 annotation to the Google implementation record.
- Result: The Google Docs editor connector is not connected in this session (connectors listed: BioRender, Google Drive, PubMed; Scite not connected). Google Drive here can read, create, copy, rename and trash files but cannot edit a document's contents. The Google record was therefore not changed and no new file was created. The r8 text for it is in this section; a paste-ready copy is in the session scratchpad (`r8-google-record-block.md`).
- Next action: Ariana pastes the r8 block into the Google record (or connects the Google Docs connector and asks again), confirms the two sub-questions, and approves or revises r8. No code changes, commits, or pushes.

##### 2026-10-11 — `CTX016-017` r8 accepted; Google record updated

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

### CTX-016 r8 — proposed commit groups (Git delivery stage, 2026-10-11)

Ariana asked on 2026-10-11 to draft the commits for a push. **Draft only: no staging, commit, or push has been done.** Each commit and the push need Ariana's explicit approval. The remote branch `origin/ariana/scoring` equals local `HEAD` `4188f57` (fetched 2026-10-11), so a push would carry only these commits, and none of them touches `.github/workflows`. External Reports and `build-cache/` stay out of Git.

Split check: in a temporary worktree from `HEAD`, applying the commit 1 files alone and then the commit 2 files, focused suites passed under Temurin 17.0.13. After commit 1: 12 classes, 0 failures/errors. After commit 2: 16 classes, 54 tests, 0 failures/errors. Every changed file is in exactly one commit.

1. **Commit 1 — `CTX016-018`, `CTX016-019`** (plus the reuse-count logging and live test from `CTX016-022`).
   - Files: `src/main/java/org/searlelab/context/encyclopedia/ContextPredictionStore.java`, `src/main/java/org/searlelab/context/encyclopedia/ContextWorkingLibraryPreparer.java`, `src/test/java/org/searlelab/context/encyclopedia/ContextPredictionStoreTest.java`, `src/test/java/org/searlelab/context/encyclopedia/ContextWorkingLibraryPreparerTest.java`, `src/test/java/org/searlelab/context/encyclopedia/ContextPredictionStoreLiveTest.java`.
   - Proposed message:
     ```
     Reuse stored predictions when preparing working libraries

     Add ContextPredictionStore, a persistent per-precursor store of
     predicted spectra built on Encyclopedia 6.9.23's prediction-cache API.
     A profile file records the prediction settings and Encyclopedia and
     JChronologer versions; a store written under another profile is
     refused. Writes take the store's write lock. A lock left by an
     interrupted run fails with its host, process ID, and age, and is
     cleared only on explicit request.

     ContextWorkingLibraryPreparer gains a store-aware prepare that looks up
     exact modified-sequence and charge keys, predicts only missing
     precursors, stores them, and loads the prediction model only when
     something is missing. Decoy selection, target-only and exclusion
     handling, the assignment report, and the existing entry point are
     unchanged. Each store-backed run logs reused and predicted counts.

     Tasks: CTX016-018, CTX016-019, CTX016-022 (logging, live test).
     ```
2. **Commit 2 — `CTX016-020`, `CTX016-021`, `CTX016-025`** (plus the shared-library live test from `CTX016-022`).
   - Files: `src/main/java/org/searlelab/context/io/SharedWorkingLibrary.java`, `src/main/java/org/searlelab/context/io/ContextFeatureCacheIdentity.java`, `src/main/java/org/searlelab/context/io/ContextFeatureScorer.java`, `src/main/java/org/searlelab/context/io/ContextFeatureScorerCLI.java`, `src/main/java/org/searlelab/context/io/ContextMProphetCLI.java`, `src/main/java/org/searlelab/context/percolator/ContextPercolatorExecutor.java`, `src/main/java/org/searlelab/context/percolator/ContextPercolatorExecutorCLI.java`, `src/test/java/org/searlelab/context/io/SharedWorkingLibraryTest.java`, `src/test/java/org/searlelab/context/io/ContextFeatureScorerCLITest.java`, `src/test/java/org/searlelab/context/io/ContextMProphetCLITest.java`, `src/test/java/org/searlelab/context/percolator/ContextPercolatorExecutorCLITest.java`, `src/test/java/org/searlelab/context/io/ContextFeatureScorerSharedLibraryLiveTest.java`.
   - These are one commit because `ContextFeatureScorer.java` carries both the shared-library change and the option changes, and the option code depends on the CLI parser changes. Splitting them would need partial-file staging and leave a commit that does not compile.
   - Proposed message:
     ```
     Share one predicted working library across runs of a library

     SharedWorkingLibrary keeps one working library per source library,
     FASTA, search parameters, and prediction settings under
     prediction-cache/working-libraries, with a manifest, an in-progress
     marker, preservation of partial output, and a blocking lock so
     concurrent runs wait for a single build. ContextFeatureScorer resolves
     it instead of predicting a run-local library, and each run's feature
     identity now includes the working library's SHA-256. Raw feature
     caches made before this change fail with "identity differs" and are
     not moved or deleted.

     Add --prediction-store and --clear-stale-store-lock to features,
     features-folder, and raw mprophet and percolator runs; split-input
     routes reject them. ContextPercolatorExecutor gains only an options
     pass-through.

     Tasks: CTX016-020, CTX016-021, CTX016-025, CTX016-022 (live test).
     ```
3. **Commit 3 — `CTX016-017`, `CTX016-022`, `CTX016-023`, `CTX016-024`** (documentation and records only).
   - Files: `README.md`, `TASKS.md`, `docs/ctx-016-encyclopediatwo-matched-decoys.md`.
   - Proposed message:
     ```
     Document the CTX-016 r8 prediction store and its validation

     README describes the prediction store, shared working libraries, the
     new options, the lock policy, the measured batch nondeterminism, and
     how to migrate older raw feature caches. TASKS.md and the local guide
     record r8 approval and decisions, the per-task log, the corrected
     per-quantity tolerances, the Java 17 verification, and this commit
     plan.

     Tasks: CTX016-017, CTX016-022, CTX016-023, CTX016-024.
     ```

Each commit message ends with the session's attribution lines (`Co-Authored-By` and `Claude-Session`) when committed.

##### 2026-10-11 — r8 commits and push authorized

- Ariana approved the three proposed commits and the push on 2026-10-11: "Leave the task numbers (CTX-016) out of the subjects and messages, but you have my approval. Please make the commits and push!" The commit subjects and bodies therefore omit the task IDs and the "Tasks:" lines shown in the draft above; the third subject is "Document the prediction store and its validation". File groups are unchanged. Commit hashes and the push result are recorded in the entry after this one.
