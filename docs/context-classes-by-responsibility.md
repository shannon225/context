# Context production classes by responsibility

Snapshot: `0f4b002`, inspected 2026-10-19. Coverage: **47 production Java source files**, including the package-private records/helpers. Nested records and helpers are described with their enclosing class. This is a source reference, not a proposal to move classes.

## Start with the data flow

Raw acquisition + source library + FASTA + assay schedule enter `ContextFeatureScorer`. It resolves a shared predicted library, searches target/decoy candidates, keeps peak-group winners, joins their IDs to search pairs, and separates reference from background. Background features train the discriminant; reference features receive that model. Confidence estimation happens afterward. An empirical assay entrapment is a library peptide marked in the assay; a search decoy is a generated competitor. Each empirical entrapment can itself have a search decoy.

The alternate `ScribeTwoFeatureGenerator` path requires its own prealignment before feature extraction. It does not imply that CTX-016 performs Scribe prealignment. Public jar commands are dispatched by `Context.main`; classes with a main method are not automatically jar subcommands.

## Implementation history

`891b1f4` changes documentation only. The preceding source-bearing commits are:

| Commit | Main responsibility |
| --- | --- |
| `07975eb` | Empirical entrapment assignment output |
| `c4a0cde` | Dependency alignment and predicted working-library preparation |
| `860d728` | Matched-decoy feature scoring and diagnostics |
| `7b06e33` | Pair identity, partitioning and cache integrity |
| `16275b1` | Feature and mProphet entry points |

The **†** marker below means that the class changed in `138e34f..891b1f4`; it does not imply the class was newly created. Prediction store, shared-library cache and platform compatibility were added afterward and are included in this current inventory.

## Command dispatch and workflow coordination

| Class / current location | Responsibility, required inputs and collaborators |
| --- | --- |
| [Context](../src/main/java/org/searlelab/context/Context.java) † | Dispatches jar subcommands to their entry points. Inputs: command plus arguments; collaborators: feature, Scribe, bootstrap, decoy, Percolator and mProphet CLIs. The actual switch is authoritative: help currently advertises a percolator-context command that the switch does not implement. |
| [ContextFeatureScorerCLI](../src/main/java/org/searlelab/context/io/ContextFeatureScorerCLI.java) † | Parses named features-folder options, prediction settings and seed ranges; finds acquisition/assay pairs and calls ContextFeatureScorer. Produces complete/reference/background feature artifacts beside acquisitions. |
| [ContextMProphetCLI](../src/main/java/org/searlelab/context/io/ContextMProphetCLI.java) † | Public jar mprophet adapter. Validates raw or pre-split input, companion sidecars and fresh output; invokes ContextFeatureScorer when needed and the existing Context mProphet workflow. |
| [ContextPercolatorExecutorCLI](../src/main/java/org/searlelab/context/percolator/ContextPercolatorExecutorCLI.java) † | Parses raw, split and standard Percolator modes, pyIsoPEP location and prediction-store options. Validates direct-input sidecars before invoking ContextPercolatorExecutor or ContextPercolator. |
| [AllWorkflowExecutorCLI](../src/main/java/org/searlelab/context/io/AllWorkflowExecutorCLI.java) † | Runs Context, standard and targeted versions of both engines from complete/background/reference feature tables plus FASTA. Supports folders and explicit training seeds; uses SeedExperiment to record each run and AllWorkflowResult to return artifacts. |
| [ContextMProphetExecutorCLI](../src/main/java/org/searlelab/context/mprophet/ContextMProphetExecutorCLI.java) | Separate class-level mProphet CLI for older executor modes. It is not the adapter selected by the jar mprophet switch; do not infer public jar options from it. |
| [ScribeTwoFeatureScorerCLI](../src/main/java/org/searlelab/context/io/ScribeTwoFeatureScorerCLI.java) | Orchestrates one Scribe acquisition or a validated seeded folder. Uses ScribeTwoFeatureGenerator, partitions features, and optionally trains/applies the Context LDA. AssaySchedulePairMatcher validates folder inputs before processing. |
| [TargetedBootstrapperCLI](../src/main/java/org/searlelab/context/io/TargetedBootstrapperCLI.java) | Parses library/acquisition, seed count, target count, window widths and entrapment count, then calls TargetedBootstrapper.execute. |
| [MProphetReiterCLI](../src/main/java/org/searlelab/context/encyclopedia/MProphetReiterCLI.java) | Direct class entry point for the MProphetReiter training/application implementation; separate from the jar mprophet wrapper. |

## Input interpretation, assay construction and output locations

| Class / current location | Responsibility, required inputs and collaborators |
| --- | --- |
| [RawFiles](../src/main/java/org/searlelab/context/io/RawFiles.java) | Recognizes supported acquisition paths and strips extensions; detects whether Thermo support is available. Shared by raw-input adapters and generators. |
| [IsolationWindowReader](../src/main/java/org/searlelab/context/io/IsolationWindowReader.java) | Parses the tabular assay mass list into IsolationWindow objects, including sequence, charge, precursor m/z, RT window and assay isDecoy flag. |
| [IsolationWindow](../src/main/java/org/searlelab/context/datastructures/IsolationWindow.java) | Value holder for an assay sequence/charge, precursor m/z, RT interval and empirical-entrapment flag. Used by assay readers, bootstrap selection and partition matching. |
| [AssayScheduleInputPair](../src/main/java/org/searlelab/context/io/AssayScheduleInputPair.java) | Package-private record of seed, acquisition file and companion assay schedule; returned by the folder matcher. |
| [AssaySchedulePairMatcher](../src/main/java/org/searlelab/context/io/AssaySchedulePairMatcher.java) | Matches immediate .dia files to assay schedules using a literal filename prefix and inclusive seed range; rejects missing or duplicate inputs. Used by Scribe folder mode. |
| [MassListDecoyGenerator](../src/main/java/org/searlelab/context/io/MassListDecoyGenerator.java) | Reads assay decoy status, optionally creates shuffled assay entries and resolves the effective mass-list file for executors. This sequence-shuffling path is distinct from TargetedBootstrapper empirical library-peptide selection and predicted search decoys. |
| [TargetedBootstrapper](../src/main/java/org/searlelab/context/io/TargetedBootstrapper.java) † | Selects targets and empirical library-peptide entrapments, masks DIA data to selected acquisition windows, and writes assays plus assignment metadata. Inputs include library, acquisition, random seed, window widths and traps per target. Its empirical relationships must not be substituted for SearchPairSidecar search pairs. |
| [DirectoryOptions](../src/main/java/org/searlelab/context/io/DirectoryOptions.java) | Shared output-directory creation, engine subdirectory naming and split-file prefix handling. It organizes files; it does not calculate confidence. |

## Library preparation, prediction and runtime compatibility

| Class / current location | Responsibility, required inputs and collaborators |
| --- | --- |
| [ContextWorkingLibraryPreparer](../src/main/java/org/searlelab/context/encyclopedia/ContextWorkingLibraryPreparer.java) † | prepare reads source target precursor metadata and predicts target/decoy spectra through public Encyclopedia APIs. Settings holds NCE/reference-charge/DIA adjustment; Assignment records pair/status/reason; Result owns the opened working library. Missing decoys retain usable targets; missing target spectra are reported. ContextPredictionStore optionally supplies cached spectra. |
| [ContextPredictionStore](../src/main/java/org/searlelab/context/encyclopedia/ContextPredictionStore.java) | Persistent raw predicted spectra keyed by modified sequence and charge. open validates the profile and stale-lock policy; find retrieves exact precursors; add writes under the upstream database lock. Stores prediction settings/version provenance separately from source-library accession and pair assignments. |
| [ContextPredictionRuntime](../src/main/java/org/searlelab/context/encyclopedia/ContextPredictionRuntime.java) | Scoped context-classloader adapter for three packaged PyTorch resource descriptors; restores the previous loader on close. Checks required LibraryFile APIs and rejects unsupported Intel-macOS prediction with setup guidance. Used before opening the predictor. |

## Search, peak-group scoring and feature generation

| Class / current location | Responsibility, required inputs and collaborators |
| --- | --- |
| [ContextFeatureScorer](../src/main/java/org/searlelab/context/io/ContextFeatureScorer.java) † | Main Context raw feature pipeline: prepares/reuses a predicted working library, runs feature-only search, writes raw sidecar/manifest, reduces to best modified-sequence/charge features and partitions by assay search-target keys. execute supports positional CLI; readFeatures parses feature TSVs. Its older scoreFeatures path also remains public. |
| [EncyclopediaTwo](../src/main/java/org/searlelab/context/encyclopedia/EncyclopediaTwo.java) † | Context-owned search implementation. generateFeatureFile selects mapped-predicted, shuffled or target-only candidate sets and emits SearchSelection metadata through a callback. It uses ContextEncyclopediaScoringFactory and the feature writer; separate runSearch/FDR methods exist but are not the CTX-016 feature-only entry path. |
| [ContextEncyclopediaScoringFactory](../src/main/java/org/searlelab/context/encyclopedia/ContextEncyclopediaScoringFactory.java) † | Creates Context EncyclopediaTwoPointOneScoringTask objects from the upstream scoring factory interface and supplies the optional diagnostic sink. |
| [EncyclopediaTwoPointOneScoringTask](../src/main/java/org/searlelab/context/encyclopedia/EncyclopediaTwoPointOneScoringTask.java) † | Scores candidate spectra against chromatograms/precursors within an isolation range. Sends retained candidates to diagnostics, then trim(1) selects the winner queued for feature output. extract and score supply fragment-level calculations. |
| [CandidateDiagnosticWriter](../src/main/java/org/searlelab/context/encyclopedia/CandidateDiagnosticWriter.java) † | Writes optional ranked pre-trim candidate metadata to a separate TSV. accept preserves candidate order; requireSuccess surfaces deferred write errors. Diagnostics do not feed the confidence engines. |
| [ScribeTwoFeatureGenerator](../src/main/java/org/searlelab/context/io/ScribeTwoFeatureGenerator.java) † | Alternative calibrated Scribe feature pipeline from raw/library/FASTA and Scribe parameters. Checks JavaPot and raw support, creates an isolated output directory, calls ContextScribeTwoBridge and writes generation.properties. It is distinct from CTX-016 matched-decoy preparation. |
| [ContextScribeTwoBridge](../src/main/java/edu/washington/gs/maccoss/encyclopedia/ContextScribeTwoBridge.java) | Context-owned class in the upstream Encyclopedia package to access package-scoped Scribe operations. Requires prealignment, refines tolerances when requested, exports features from the calibrated search, and manages the pin store/reader. Package placement is an API-access constraint, not evidence of an upstream source modification. |

## Feature identity, caching and experiment provenance

| Class / current location | Responsibility, required inputs and collaborators |
| --- | --- |
| [SearchPairSidecar](../src/main/java/org/searlelab/context/encyclopedia/SearchPairSidecar.java) † | Collects typed search selections and joins them to existing feature IDs without adding numeric columns. write emits pair metadata; requireMatches/requirePairMetadata validate companions; partition emits selected-ID companions; targetKeysByFeatureId supports population assignment. |
| [ContextFeatureCacheIdentity](../src/main/java/org/searlelab/context/io/ContextFeatureCacheIdentity.java) † | Package-private helper for content/settings hashes, manifests, output ownership locks and interrupted-output archival. Distinguishes per-run feature identity from shared working-library identity; handles Bruker directory content. |
| [SharedWorkingLibrary](../src/main/java/org/searlelab/context/io/SharedWorkingLibrary.java) | Package-private resolver for one verified working library per source/settings identity. Serializes creation, archives marked partial builds, checks artifact hashes and returns library/report paths plus library hash. Uses ContextFeatureCacheIdentity and a Builder callback. |
| [SeedExperiment](../src/main/java/org/searlelab/context/io/SeedExperiment.java) | Package-private provenance recorder for explicit training-seed experiments. Enforces fresh directories, hashes inputs and records parameters/environment and RUNNING/COMPLETE/failed status in run.properties. |
| [TrainingSeeds](../src/main/java/org/searlelab/context/percolator/TrainingSeeds.java) | Validates and parses training seed specifications and supplies the default seed; keeps engine randomization inputs explicit. |

## Feature tables and discriminant training/application

| Class / current location | Responsibility, required inputs and collaborators |
| --- | --- |
| [EncyclopediaFeatures](../src/main/java/org/searlelab/context/datastructures/EncyclopediaFeatures.java) | Parses feature-table headers and rows, identifies numeric model columns and labels, detects near-constant features, and writes tables with selected columns removed. Used by ContextFeatures and Percolator scoring. |
| [ContextFeatures](../src/main/java/org/searlelab/context/datastructures/ContextFeatures.java) | prepare reads background/reference tables, verifies compatible headers and required class populations, identifies near-constant columns on background and removes the same columns from both tables. Returns prepared paths/table plus counts; it does not train a model. |
| [ScoredFeature](../src/main/java/org/searlelab/context/datastructures/ScoredFeature.java) | Carries parsed feature sequence, charge, m/z, primary score, RT, label, original TSV line and population annotation. Original lines preserve output IDs and numeric fields through partitioning. |
| [ContextPercolator](../src/main/java/org/searlelab/context/percolator/ContextPercolator.java) | trainAndApply prepares the two tables, trains Percolator on background, parses weights, applies scores to reference rows, and calls pyIsoPEP for PSM/peptide confidence. Best-per-peptide reduction is downstream of peak-group search competition. |
| [ContextPercolatorExecutor](../src/main/java/org/searlelab/context/percolator/ContextPercolatorExecutor.java) | Programmatic raw/split and standard/targeted workflow orchestration. Raw Context runs resolve the mass list and invoke ContextFeatureScorer, then ContextPercolator; standard/targeted routes use the native engine on the selected population. |
| [ContextPercolatorRunner](../src/main/java/org/searlelab/context/percolator/ContextPercolatorRunner.java) | Builds and launches the pinned native Percolator command for training, including seed handling and model outputs. Called by ContextPercolator; separate from the high-level executor. |
| [PercolatorWeights](../src/main/java/org/searlelab/context/percolator/PercolatorWeights.java) | Parses native model weights, retains/averages model coefficients, writes weight artifacts and computes scores for feature vectors supplied in matching feature order. |
| [PyIsoPEPRunner](../src/main/java/org/searlelab/context/percolator/PyIsoPEPRunner.java) | Locates and launches pyIsoPEP, checks process/output results and reads tabular confidence values. Its nested Table supports column lookup and row access; used after Context rescoring and in comparative workflows. |
| [ContextMProphetExecutor](../src/main/java/org/searlelab/context/mprophet/ContextMProphetExecutor.java) | Programmatic feature-only or raw/folder Context mProphet execution. Trains background LDA with MProphetReiter and applies that model to reference features. Raw paths call ContextFeatureScorer. |
| [MProphetDataset](../src/main/java/org/searlelab/context/encyclopedia/MProphetDataset.java) | Holds feature names, initial-score index and peptide data; builds seeded folds, combines them and selects candidate positives by FDR or percentage for iterative training. |
| [MProphetReiter](../src/main/java/org/searlelab/context/encyclopedia/MProphetReiter.java) | Implements iterative mProphet training and probability reporting, plus application of a supplied LDA without retraining. Uses MProphetDataset and upstream LDA/FDR helpers; exposes result and error state to callers. |

## Execution results and library exports

| Class / current location | Responsibility, required inputs and collaborators |
| --- | --- |
| [ContextPercolatorExecutionData](../src/main/java/org/searlelab/context/percolator/ContextPercolatorExecutionData.java) | Extends the upstream Percolator execution-data contract with Context-specific model/weights handling; consumed by ContextPercolatorRunner and training orchestration. |
| [ContextPercolatorResult](../src/main/java/org/searlelab/context/percolator/ContextPercolatorResult.java) | Return object bundling trained weights/model, rescored reference features, peptide/PSM confidence reports and related output paths. Accessors describe artifacts, not a new confidence algorithm. |
| [AllWorkflowResult](../src/main/java/org/searlelab/context/io/AllWorkflowResult.java) | Bundles artifacts/results from all six workflows: Context, standard and targeted Percolator and mProphet. Returned by AllWorkflowExecutorCLI. |
| [SearchToBLIB](../src/main/java/org/searlelab/context/encyclopedia/SearchToBLIB.java) | Converts search results into BLIB/ELIB libraries with optional alignment/quantification, plus entrapment summary logging. Uses upstream search jobs and integration/alignment helpers; it is not part of the normal CTX-016 feature-only scoring call. |

## Function-to-package map and restructuring observations

All short packages below are under `org.searlelab.context` unless stated otherwise.

| Function | Current packages | Consequence |
| --- | --- | --- |
| Dispatch and orchestration | root, io, percolator, mprophet, encyclopedia | Follow the dispatcher and callers; CLI classes are not centralized. |
| Assay/input models | io, datastructures | Window parsing and selection are separated from value holders. |
| Prediction/search | encyclopedia; Scribe adapter in io | The package contains Context-owned adapters and copied algorithms, not only dependency classes. |
| Identity/cache/provenance | io, encyclopedia, percolator | Search relationships and training seeds have different owners from artifact caching. |
| Confidence | percolator, mprophet, encyclopedia, datastructures | mProphet's high-level executor is separate from its training implementation. |
| Library export | encyclopedia | SearchToBLIB is a separate output workflow. |
| Package-scoped Scribe access | edu.washington.gs.maccoss.encyclopedia | Moving ContextScribeTwoBridge can break access to dependency methods. |

The smallest useful organizational improvement is this functional map rather than a package migration. `AllWorkflowExecutorCLI` combines orchestration and argument parsing, while mProphet has multiple entry points; future extraction could clarify those responsibilities if an actual change requires it. `EncyclopediaTwo` includes legacy full-search/FDR methods alongside the active feature-only path, so callers must be checked before removing methods. None of these observations authorizes a refactor. Keep public class names and CLI behavior stable when assessing future changes.

Verification: the inventory generator compared the documented names with every `src/main/java/**/*.java` basename and required exact set equality and no duplicates (47/47). Responsibilities were checked against declarations, public methods, dispatch and key call paths; this is not a line-by-line audit of all legacy code.
