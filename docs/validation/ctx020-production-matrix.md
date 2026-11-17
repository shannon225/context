# CTX-020 production validation matrix — 2026-11-16

Authority: canonical r5.3 in `docs/uu-ctx-020-forced-assay-xic.md`. Original supplied DIA and assay files were preserved. All runs use full-gradient search and fallback scoring with original schedules retained as metadata. No new confidence estimates are implied by these counts.

| Seed | True targets scored | Entrapments scored | Entrapments without MS2 | True target winners | Entrapment target winners | Outside schedule: true / entrapment | Raw / reference / background rows |
| --- | ---: | ---: | ---: | ---: | ---: | --- | --- |
| 1 | 96 | 443 | 37 | 95 | 230 | 0 / 230 | 8259 / 539 / 7720 |
| 2 | 96 | 453 | 27 | 96 | 230 | 0 / 230 | 8392 / 549 / 7843 |
| 22 | 96 | 435 | 45 | 96 | 205 | 0 / 205 | 7994 / 531 / 7463 |
| 23 | 96 | 450 | 30 | 95 | 225 | 0 / 225 | 8122 / 546 / 7576 |
| 24 | 96 | 460 | 20 | 96 | 231 | 0 / 231 | 8807 / 556 / 8251 |
| 25 | 96 | 454 | 26 | 94 | 224 | 0 / 224 | 8617 / 550 / 8067 |
| 26 | 96 | 452 | 28 | 94 | 222 | 0 / 222 | 8236 / 548 / 7688 |
| 27 | 96 | 462 | 18 | 94 | 220 | 0 / 220 | 7885 / 558 / 7327 |
| 28 | 96 | 461 | 19 | 94 | 223 | 0 / 223 | 8267 / 557 / 7710 |

DETECTED denotes successful target scoring before competition. Retained target winners exclude matched-search-decoy winners. Entrapments are search targets, distinct from search decoys. None of these columns represents PEP-filtered discoveries.

All nine audits check 576 detection rows with unchanged original fields/order, source DIA/assay hashes, unique unchanged feature IDs, feature/pair parity, finite numeric reference features including search decoys, and disjoint reference/background ID sets whose union equals the raw set. Canonical assay identities and their search decoys are excluded from background. All final-code cache logs show verified reuse and zero failed runs.

Runtime: existing local Linux/amd64 Java17 image `context-ctx020-round1b` (sha256:d7c436c49c980ca56e78b775bd6e0bcf95ecfd4c187b8c9763ae014ef45688c3). The remaining eight raw runs used an immutable r5.3 guard-inclusive class snapshot; all nine were revalidated with final classes. No supplemental spectra were needed for these supplied assays. Separate cached synthetic integration tests establish the missing-reference supplementation path.

Evidence: `tmp/ctx020-validation/production-full-gradient-remaining/seed*-audit.json`, seed23 audit under `production-full-gradient-seed23/`, `remaining-canonical/`, `r53-guard-classes/validation-class-hashes.json`, `r53-final-class-hashes.json`, and `tmp/ctx020-environment/final-code-remaining-reuse.log`. Audit script: `tmp/ctx020-probes/audit-remaining-seeds.py`. Historical supplied counts: `tmp/ctx020-validation/supplied-feature-baseline-counts.json`; those outputs have incomplete original provenance and are not a controlled comparator.

Seed23 production features are byte-identical to the full-gradient experiment described in `docs/validation/ctx020-rt-policy-comparison.md`; its Context + Percolator, standard Percolator and targeted Percolator comparison therefore applies without rerunning confidence engines. Background-only model training/application is separately covered by integration tests. No new per-seed confidence comparison was run for the other eight acquisitions.

Limitations: this matrix verifies processing and population integrity, not general FDR/FDP validity or improved sensitivity. Off-schedule entrapments may represent genuine peptide signal. Unique bootstrapper-notation matching remains a workaround; the export bug is deferred. Automatic assay-entrapment generation remains enabled, but unsupported generated peptides fail the source-library/FASTA rule. Targeted Percolator’s seed23 fallback warning remains recorded with the earlier confidence comparison. Process RSS peak was not measured separately. Ariana’s final scientific review remains pending.

## Runtime observations

Highest observed kernel container memory high-water mark: 1,879,801,856 bytes (1.75 GiB). This includes container page cache; it is not JVM heap or process RSS. The high-water mark was sampled every30seconds near the end of the matrix. Evidence: `tmp/ctx020-environment/remaining-memory-observations.tsv`.

| Seed | First-to-last logged timestamp (seconds) |
| --- | ---: |
| 1 | 696 |
| 2 | 928 |
| 22 | 521 |
| 24 | 482 |
| 25 | 534 |
| 26 | 710 |
| 27 | 516 |
| 28 | 363 |

Times are log-derived intervals including preparation, search, forced completion and partitioning, not isolated search/forcing benchmarks. Other validation jobs shared the machine, so these are operational observations rather than controlled performance comparisons.
