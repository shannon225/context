# CTX-016 — Reviewer 3

Date: 2026-10-10 (America/Chicago). Outcome: **REQUEST CHANGES**.
Reviewed `ariana/scoring` at `891b1f4652cecf1a4f19c2f8e6d663138b8f535d` after an authorized `git pull --ff-only origin ariana/scoring` returned already up to date. Existing user changes were preserved. Scope: approved r7/canonical revision 2, previous reviewer corrections, and Ariana's added Windows/macOS/Linux compatibility requirement in the linked Google implementation record. No implementation corrections or Git delivery were performed.

## Findings

1. **P1 — Packaging loses platform-specific PyTorch discovery metadata.** All three native dependencies use the same resource name, `native/lib/pytorch.properties`. The package profile in `pom.xml:106` has no transformer that preserves distinct platform metadata. Inspection of the existing local shaded jar found all three sets of native binaries but only the `osx-aarch64` properties file. DJL 0.36.0 `Platform.detectPlatform` enumerates properties resources and ignores mismatched classifiers; `LibUtils.findNativeLibrary` downloads PyTorch when the detected platform is a placeholder. A metadata-only probe against this jar returned `placeholder=true` for Windows x86_64 and Linux x86_64, and `false` for macOS ARM. Thus those platforms cannot use their bundled natives through normal discovery, introducing an unexpected network/cache prerequisite. Recommend per-platform distributions or a supported packaging arrangement retaining separate native jars. Validate actual packaged prediction on each platform with a fresh cache and network disabled. This artifact was inspected, not rebuilt with Maven during this review.

2. **P1 — Intel macOS cannot run raw CTX-016 prediction.** The pinned JChronologer POM (`libs/org/searlelab/jchronologer/1.0.10/jchronologer-1.0.10.pom:65`) supplies macOS ARM, Windows x86_64, and Linux x86_64 natives, but no macOS x86_64 native. The live test explicitly skips Intel macOS (`ContextWorkingLibraryLiveTest.java:28`); README acknowledges the limitation. Java portability does not make this native predictor portable. To satisfy unqualified macOS support, approve and validate a compatible prediction dependency or an explicit supported execution route for Intel Macs. Do not silently substitute a predictor. ARM Windows/Linux also have no bundled native classifier; define the supported architecture matrix explicitly.

3. **P2 — Apple Silicon Percolator requires an additional compatibility prerequisite.** The dependency's bundled default `bin/percolator-v3-01.mac` is Mach-O x86_64, whereas the supported macOS prediction native is ARM. `PercolatorVersion` selects the executable by OS, without architecture selection. An ARM Mac therefore needs x86_64 translation or a compatible Percolator executable; this prerequisite and its validation are absent from the setup guidance. Windows and Linux default binaries are x86_64 too. Provide the appropriate executable or document and test the compatibility prerequisite across the complete two-engine workflow.

4. **P2 — Platform acceptance lacks packaged runtime tests.** `.github/workflows/test.yml:16` runs only Ubuntu; current smoke tests exercise help output. Windows and macOS packaging, actual target/decoy prediction, small acquisition search, and both engine paths are not demonstrated. Add a platform matrix and run the packaged artifact through prediction/search and engine integration with IDs/populations checked. Windows setup guidance also needs `Scripts\\pyisopep.exe` and PowerShell-compatible commands; current virtualenv examples use Unix `bin/` paths. Passing synthetic tests cannot close this criterion.

## Supplied Windows console failure

Ariana supplied `/Users/arianashannon/Downloads/Untitled.tsv`. Its last lines show DJL finding the Windows native and initializing PyTorch, followed by:

```text
java.lang.NoSuchMethodError: 'java.util.ArrayList ...LibraryFile.getUniqueTargetPrecursorMetadata()'
at ...ContextWorkingLibraryPreparer.prepare(ContextWorkingLibraryPreparer.java:74)
```

This is a runtime Java dependency/API mismatch, distinct from the native packaging problem. The log uses separate Maven-cache native jars, which avoids the shaded metadata collision. The `LibraryFile` actually loaded on that Windows run lacks the method required by CTX-016. The bundled 6.9.23 jar and this Mac's cached 6.9.23 jar both contain that method and have SHA-256 `06534c08bfc37f0c954272c09279e8d6f1426399bf74100899f7954ea059087d`. The Windows runtime's loaded jar cannot be identified from this log alone: an older dependency, differing artifact under the same coordinates, or classpath precedence are possible. Requested the launch command/IDE configuration. Next diagnostic: obtain `LibraryFile.class.getProtectionDomain().getCodeSource().getLocation()` from that runtime and compare its jar/API/hash with the reviewed artifact. Correct the runtime dependency selection once identified; do not add a fallback for the missing API.

## Checks and limitations

- Java 17 freshly compiled all production and test source into `/private/tmp/ctx016-reviewer3/`, with bundled Encyclopedia 6.9.23 first on the compilation classpath.
- Focused JUnitCore run against freshly compiled Context classes and the existing shaded jar's dependency classes reported **OK (19 tests)**: preparation, sidecar validation, cache ownership/directory hashing, charge-aware partitioning, and both CLI preflights. The live prediction test used its Intel-Mac assumption skip; this is not a live prediction pass.
- The documented Maven executable is absent and no system Maven was found, so Maven package verification was **unrun**. The initial manual compile accidentally included the old Encyclopedia jar first; corrected classpath compilation passed. The initial broad runtime classpath included conflicting cached SLF4J bindings; rerunning with packaged dependency classes passed. These were review harness errors, not repository findings.
- PyTorch metadata probe changed JVM platform properties only; it did not execute Windows/Linux native binaries. The probe confirms discovery behavior, not cross-platform execution.
- Verified bundled Percolator architecture with `file`; `otool -L` shows the macOS executable links system C++/system libraries. No Apple Silicon, Windows, or Linux runtime was available for this review.
- Logs/probes: `/private/tmp/ctx016-reviewer3/focused-tests.log`, `platform-probe.log`, `libutils.txt`, and `platform.txt`. Temporary files are not durable archive Reports.
- Representative FDR/calibration, full live search, Percolator/pyIsoPEP integration, and Ariana's final acceptance remain open. Earlier reviewer corrections have focused regression coverage; this review does not establish end-to-end scientific validity.

These are review recommendations. Dependency/packaging/architecture changes require a revised guide/checklist approved by Ariana before implementation. The canonical plan was not changed. CTX016-010 remains open.

## Correction handoff — 2026-10-10

Ariana invoked uu-revise #3 and approved the Intel-Mac Linux container route and removal of the clean-directory debug-copy blocker. Metadata collision is corrected with classifier-specific packaged descriptors and the scoped Context runtime adapter; required LibraryFile APIs are checked with artifact-origin diagnostics. Setup guidance and packaged CI checks were added. No dependency versions changed. The Windows runtime origin remains unknown. Docker setup correction awaits its separate decision; native multi-platform execution and scientific validation remain open.

Java 17 targeted package verification passed: 31 tests, 0 failures/errors, 1 expected Intel-Mac live skip. Shaded-jar checks verified all three platform descriptors and native resources. The clean-directory command regression passed. Logs: `tmp/ctx016/revise3/final-package.log`. Tracking codes were removed from source/tests/build configuration at Ariana's request; cache migration is documented in README. Canonical revision 2 remains unchanged. This is correction evidence for re-review, not an independent approval or final feature acceptance.

Packaged engine validation completed on Intel macOS: mProphet and Percolator with pyIsoPEP 0.1.4 ran from a clean directory and passed population-ID checks on the documented existing fixture split (I/L equivalence for peptide reports). Logs: `tmp/ctx016/revise3/final-engine-check-2.log`, `tmp/compatibility/platform-engines/run-_4vx35bj/`. Percolator rescored 500 reference rows and completed both confidence-estimation calls. This does not establish native prediction on other platforms or scientific calibration. Independent re-review and Docker setup approval remain pending.
