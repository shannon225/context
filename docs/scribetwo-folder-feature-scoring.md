# ScribeTwo folder feature scoring

Status: Implemented and accepted; local commits authorized, push retained by Ariana
Feature ID: CTX-014
Approved: 2026-09-13 by Ariana Shannon
Source record: https://docs.google.com/document/d/1IeHK6oC8eU32yRv7RgUM_-43RTafEkmplcp5D2vVUY4/edit
Approved record revision: `ANLCKQnYKN02Zof-f5UUIrAmS0UiVhWmEKvSE7385q_oDEoENNji93rCd_kIoTkzKT6eVY_X-Jwi3C2IMXLtcMmBMH1AhVuwhN-_XkEP5Qg`
Implementation record revision: `ANLCKQkCqT_65ma8Gwz35th5laPKaF8nHh9heiuo0UM3UhKporCednCU872K8VPPGNjnRwBF7zMskOPpuhqrIorwHVJlZu5x1DnZsMZTgR4`

## Goal

Allow `ScribeTwoFeatureScorerCLI` to process a folder of seeded assay-schedule pairs, analogous to `ContextFeatureScorerCLI`, while preserving the existing single-run Scribe syntax and scientific workflow.

Both folder CLIs will share one package-private matcher instead of exposing Context CLI internals or duplicating discovery logic.

## Approved names and reuse boundary

- `AssaySchedulePairMatcher`: package-private final utility responsible for discovery, validation, pairing, and ordering.
- `AssayScheduleInputPair`: package-private immutable record containing the seed, acquisition file, and assay-schedule/mass-list file.
- `AssaySchedulePairMatcherTest`: focused JUnit 4 unit test.
- `AssaySchedulePairMatcher.DEFAULT_INPUT_PREFIX`: `_masked`.
- `AssaySchedulePairMatcher.findInputPairs(File folder, String inputPrefix, int startSeed, int endSeed)`: shared entry point used by both CLIs.

The latest implementation instruction explicitly selects `AssayScheduleInputPair`; this supersedes the shorter `AssaySchedulePair` wording in one Google Doc comment.

## Input naming contract

`--input-prefix` is optional in folder mode and defaults to `_masked`. It is literal text immediately preceding the decimal seed, not a regular expression. Matching remains case-insensitive and unanchored for compatibility with existing names.

The matcher is built from:

```java
Pattern.quote(inputPrefix) + "([0-9]+)(?=_|\\.)"
```

Accepted pair shape:

```text
<anything><input-prefix><seed>[_<suffix>].dia
<same acquisition basename>.txt
```

Examples:

```text
sample_masked1_assay.dia
sample_masked1_assay.txt

sample_bootstrap7_assay.dia
sample_bootstrap7_assay.txt
```

The second example uses `--input-prefix _bootstrap` and represents seed 7.

## Implementation design

1. Extract the existing immediate-child `.dia` discovery, inclusive seed-range validation, duplicate detection, same-basename `.txt` pairing, and ascending ordering into `AssaySchedulePairMatcher`.
2. Refactor `ContextFeatureScorerCLI` to use the matcher and accept optional `--input-prefix`, preserving `_masked` behavior when omitted.
3. Preserve `ScribeTwoFeatureScorerCLI` single-run parsing exactly. Add a separate named folder mode requiring `--dia-folder`, `--library`, `--fasta`, and `--output-directory`, with optional seed range and input prefix.
4. Preflight all pairs and parse every assay schedule before the first Scribe run.
5. Process pairs in seed order. Give each acquisition an output parent derived from its actual basename. Rebuild requested Scribe parameters for every run and use the effective parameters returned by that run for partitioning and optional Context training.
6. Continue after individual run failures, retain successful outputs, print an ordered summary, and fail the overall command if any input failed.
7. Add a narrow package-private execution seam in the Scribe CLI so orchestration can be tested without requiring native Scribe data.

## Acceptance criteria

- Default `_maskedN` Context folder behavior remains compatible.
- A custom literal prefix works in both folder CLIs.
- Regex metacharacters in the prefix are treated literally.
- Invalid prefixes, ranges, duplicates, missing seeds, and missing or malformed assay schedules fail before the first expensive run.
- Scribe single-run positional syntax and parameter passthrough remain compatible.
- Folder runs execute in ascending seed order with isolated requested/effective parameters and basename-derived output parents.
- A per-seed failure does not stop later seeds, but the final command status is failing.
- CLI help and README document the naming contract, defaults, examples, outputs, and failure behavior.
- Focused JUnit 4 tests and the Java 17 Maven verification pass, or failures/skips are recorded honestly.

## Planned files

- New: `src/main/java/org/searlelab/context/io/AssaySchedulePairMatcher.java`
- New: `src/main/java/org/searlelab/context/io/AssayScheduleInputPair.java`
- New: `src/test/java/org/searlelab/context/io/AssaySchedulePairMatcherTest.java`
- New: `src/test/java/org/searlelab/context/io/ScribeTwoFeatureScorerCLITest.java`
- Modify: `src/main/java/org/searlelab/context/io/ContextFeatureScorerCLI.java`
- Modify: `src/main/java/org/searlelab/context/io/ScribeTwoFeatureScorerCLI.java`
- Conditional: `src/main/java/org/searlelab/context/Context.java`
- Modify: `README.md`
- Track: `TASKS.md` and this guide

## Validation plan

1. Record the Java 17 and Maven baseline before source edits.
2. Run `AssaySchedulePairMatcherTest`, Context CLI regression tests, and `ScribeTwoFeatureScorerCLITest`.
3. Run the full Maven package verification under Java 17.
4. Run opt-in native Scribe integration only when suitable data are available; unrun native validation remains explicitly skipped.
5. Review the final diff for minimal scope, no public API expansion, no dependency changes, and no duplicated pairing policy.

## Exclusions

No scoring-model, calibration, target/decoy/entrapment, feature-selection, partitioning, dependency, recursive-scan, or acquisition-format expansion is included. The existing `--shuffledSeed`/`--shuffled-seed` defect remains deferred. Git commit, push, PR, and merge actions require separate authorization.
