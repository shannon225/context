# CTX-016 scientific validation assessment — 2026-10-19

**Outcome: descriptive checks completed; q-value/PEP calibration remains unestablished.** This assessment extends the existing masked-dataset comparison without rerunning engines or changing inputs. It does not certify improved sensitivity, FDR control or portability.

## Data and reproducibility

Three existing masked datasets (1–3), six workflows each, training seed 1. All 18 workflow result sets have COMPLETE manifests. Reports come from the October 11 analyses, before the r8 shared-cache implementation; r8 functional equivalence was checked separately in the historical log. These results cannot establish scientific equivalence across every r8 production input.

Run from repository root:

```bash
python3 docs/validation/ctx016_validation.py --output tmp/ctx016/scientific-audit-new
```

Use a fresh output directory. The script reads original workflow reports, source assays, reference pair metadata and feature tables; verifies complete manifests, exact ID coverage, unique report IDs, finite probabilities in [0,1], and consistent assay classification. It hashes all 33 read source artifacts before/after analysis. The completed run wrote 216 threshold summaries and 3,600 reference report rows under `tmp/ctx016/review-20261019/scientific-diagnostics/`. No inputs were changed.

Outputs: threshold_counts.tsv, reference_peptide_evidence.tsv (including peak RT versus listed assay window), primary_score_distributions.tsv, assay_inventory.tsv and input_sha256.json. The source script is [ctx016_validation.py](validation/ctx016_validation.py).

## Populations and counting rules

Search Label=1 includes genuine assay targets **and empirical entrapments**; Label=-1 is the generated search-decoy population. The assay isDecoy flag identifies empirical entrapments by exact modified-sequence/charge targetKey. Search-decoy rows are never counted as empirical entrapments.

For report-level counts, modified peptide sequences are unique within each dataset/workflow; charges are collapsed by the minimum reported PEP or q-value for that peptide, separately for each metric. That operation reproduces the earlier plotting rule; the resulting minimum is not a newly calibrated peptide-level probability. Full precursor evidence is retained. Thresholds are inclusive at 0.001, 0.005, 0.01, 0.02, 0.05 and 0.1.

Standard workflows calculate confidence on the combined population; this analysis selects reference IDs afterward. Their q-values are not recalibrated for that selected subgroup. Context/targeted results refer to reference reports. Comparing these columns is descriptive, not a comparison of matched FDR guarantees.

| Dataset | Assay targets | Assay entrapments | Surviving target precursors | Surviving entrapment precursors |
| --- | ---: | ---: | ---: | ---: |
| 1 | 100 | 500 | 98 | 102 |
| 2 | 100 | 500 | 98 | 87 |
| 3 | 100 | 500 | 99 | 116 |

The design ratio is 500/100 = 5 entrapments per target, whereas the surviving feature ratio differs substantially. A scaling factor must not be inferred from the post-selection survivor counts. This analysis deliberately does **not** turn those counts into an FDP estimate without establishing the estimator's assumptions.

## Reported PEP ≤ 0.01

Each cell is non-entrapment / entrapment unique peptides. These reproduce the existing non-entrapment and entrapment count files.

| Workflow | Dataset 1 | Dataset 2 | Dataset 3 |
| --- | ---: | ---: | ---: |
| Context Percolator | 75 / 2 | 80 / 0 | 85 / 0 |
| Standard Percolator | 79 / 2 | 81 / 0 | 78 / 0 |
| Targeted Percolator | 34 / 0 | 76 / 1 | 41 / 0 |
| Context mProphet | 90 / 3 | 95 / 3 | 90 / 4 |
| Standard mProphet | 90 / 3 | 92 / 3 | 89 / 2 |
| Targeted mProphet | 91 / 2 | 95 / 3 | 83 / 4 |

The direct observed entrapment share E/(E+T) ranges from 0 to 4.60% across these 18 cells. It is neither a scaled FDP estimator nor a false-positive rate. Even if every entrapment identification were known false, it would describe errors in a single reported list, not by itself the expectation defining FDR. Zero observed entrapments does not prove control at 1%.

## Reported q-value ≤ 0.01

| Workflow | Dataset 1 | Dataset 2 | Dataset 3 |
| --- | ---: | ---: | ---: |
| Context Percolator | 87 / 2 | 96 / 3 | 93 / 6 |
| Standard Percolator | 90 / 2 | 94 / 1 | 91 / 3 |
| Targeted Percolator | 84 / 2 | 95 / 5 | 92 / 5 |
| Context mProphet | 91 / 4 | 96 / 9 | 93 / 7 |
| Standard mProphet | 91 / 6 | 96 / 6 | 92 / 4 |
| Targeted mProphet | 93 / 5 | 97 / 8 | 88 / 8 |

## Primary-score distribution check

These are feature-level medians of the primary search score before either confidence model, after partitioning and best-per-precursor reduction. Label=1 reference rows mix real assay targets with empirical entrapments. Distribution separation alone cannot diagnose correct or incorrect calibration.

| Dataset | Reference Label=1 | Reference Label=-1 | Background Label=1 | Background Label=-1 |
| --- | ---: | ---: | ---: | ---: |
| 1 | 2.1924 | 0.5944 | 0.6036 | 0.4897 |
| 2 | 3.1494 | 0.4492 | 0.5527 | 0.4718 |
| 3 | 1.3097 | 0.4752 | 0.5626 | 0.4572 |

## What prevents a calibration conclusion

1. **Empirical-entrapment null validity is not established here.** The bootstrapper selects library peptides outside target acquisition windows and masks using real-target windows. The listed entrapment RT is its library RT, not an artificial target assay window. Thus finding the two high-confidence Context Percolator entrapment peaks outside their own listed RT window is not itself a code defect or proof of true signal leakage. Verifying absence requires the actual extraction/window provenance and chromatographic evidence, including RT drift and interference. The new RT columns are diagnostics only.
2. **Exchangeability is unverified.** Empirical entrapments selected by RT/m/z rules need not have the same false-match behavior as incorrect target identifications. No supported paired-estimator mapping was reconstructed for these historical inputs. Existing search-pair sidecars link each candidate to its generated search decoy; they are not the empirical target-to-entrapment assignments.
3. **Only three masks and one training seed are represented.** Masks derive from the same acquisition and may overlap; they are not independent biological replicates. A pooled binomial confidence interval or reliability plot with independent observations would misstate the experimental design. No such interval or calibration claim is supplied.
4. **Target correctness is unknown.** Treating every non-entrapment as a correct identification would give an artificial ground truth. A binary reliability curve or Brier score would therefore be inappropriate here.
5. **No matched pre-change scientific baseline is present.** The old/new fixture equivalence check supports cache refactoring, not improved calibration or sensitivity from predicting both target and decoy spectra.

The next validation decision is which endpoint to certify (combined reference target+entrapment list, real-target-only subset, or another population), and which independently justified null/paired estimator applies to the acquired data. The available artifacts support diagnostics; they do not settle those decisions. `CTX016-009` remains open for that reason.

## Methodological reference

“Assessment of false discovery rate control in tandem mass spectrometry analysis using entrapment,” *Nature Methods* 22, 1454–1463 (2025), [doi:10.1038/s41592-025-02719-x](https://doi.org/10.1038/s41592-025-02719-x). The paper distinguishes invalid, lower-bound and valid but underpowered entrapment estimates. This assessment uses that distinction to avoid calling an unscaled observed entrapment share a validated FDP estimate; it does not claim that the paper validates this PRM masking design.
