# Context

Confidence estimation for targeted proteomics, built on EncyclopeDIA.

A set of targeted reference peptides is normally far too small to train a discriminant on. But the
same acquisition also contains a large background of non-targeted peptides that is big enough.
Context trains the discriminant on that background and applies it to the reference peptides.

Two engines can realize this:

* `percolator`: trains Percolator on the background peptides, applies the model to the reference
  peptides. q-values and PEPs from [pyIsoPEP](https://github.com/statisticalbiotechnology/smooth_q_to_pep).
* `mprophet`: trains an mProphet LDA on the background peptides, applies it to the reference
  peptides. q-values from Benjamini-Hochberg, PEPs from Storey's method.

## 1. Get Context

### The container image, which needs nothing else installed

```bash
apptainer pull context.sif docker://ghcr.io/shannon225/context:latest
apptainer run context.sif --help
```

or with Docker:

```bash
docker run --rm ghcr.io/shannon225/context:latest --help
```

The image carries the Java runtime, Percolator and pyIsoPEP. This is the recommended way to run Context.

### Or the jar, if Java 17+ is available

Download `context.jar` from the [latest release](https://github.com/shannon225/context/releases/latest):

```bash
java -jar context.jar --help
```

The `mprophet` engine needs nothing else. The `percolator` engine also needs pyIsoPEP to derive
q-values and PEPs. We recommend to create an environment for it.

#### A Python virtual environment

Nothing is required beyond the `python3` (3.9+):

```bash
python3 -m venv ~/context-env
~/context-env/bin/pip install --no-cache-dir pyIsoPEP
```

Any suitable directory can be used. `~/context-env` is just a suggestion.

Then, tell Context where it is:

```bash
java -jar context.jar percolator ... -pyisopep ~/context-env/bin/pyisopep
```

#### Or a conda / mamba environment

```bash
conda create -n context python=3.12    # or: mamba create -n context python=3.12
conda activate context
pip install --no-cache-dir pyIsoPEP
```

While the environment is active, Context can find it without `-pyisopep`:

```bash
java -jar context.jar percolator ...
```

#### Removing the environment

```bash
rm -rf ~/context-env
```
Or

```bash
conda deactivate
conda env remove -n context
conda clean --all        # optional: also drops conda's own cache of downloaded packages
```

## 2. Input files

Four files:

| flag | input | notes |
|------|-------|-------|
| `-i` | the acquisition | `.dia`, `.mzML`, or `.d` (Bruker). Thermo `.raw` must be converted first, [see below](#thermo-raw-files) |
| `-l` | the spectral library | `.elib` preferred, `.dlib` accepted |
| `-f` | the FASTA | the protein database the library was built against |
| `-massList` | assay / mass list | `.txt` (tab-separated) or `.csv`, at least 7 columns |

### The mass list

This is the list of the reference peptides. It is what tells Context which part of the run is the
reference peptides and which part is background. The format is a Skyline-style isolation list:

```text
Compound         Formula  Adduct       m/z       z  RT Time (min)  Window (min)  isDecoy
IGQVHHALDTTIK             (no adduct)  478.2684  3  16.226         0.5           false
IITTDLAHHVQGK             (no adduct)  478.2684  3  16.226         0.5           true
```

Context reads the peptide sequence, `m/z`, `z`, the RT centre and RT window width (both in minutes),
and `isDecoy`. `Formula` and `Adduct` are ignored and may be empty. Everything Context finds in the
acquisition that is **not** on this list becomes the background it trains on.

### Decoys

That last column is what makes the confidence estimate possible. q-values and PEPs come from
target-decoy competition. A plain Skyline export stops at `Window (min)` and so contains no
decoys at all. If the mass list is target-only, let Context build entrapment decoys: one decoy peptide
per target, same charge, same RT window, matched mass. Just add the flag `-generateDecoys`:

```bash
java -jar context.jar percolator -i run01.dia -l library.elib -f database.fasta \
  -massList run01_assay.txt -generateDecoys -o results_run01
```

It writes the expanded list to `results_run01/run01.assay.with_decoys.txt` and uses that. A list that
*already* has decoys is used unchanged, so the flag is harmless to leave on.

## 3. Run Context

With the image:

```bash
apptainer run --bind "$PWD:/data" --pwd /data context.sif percolator \
  -i        run01.dia \
  -l        library.elib \
  -f        database.fasta \
  -massList run01_assay.txt \
  -generateDecoys \
  -o        results_run01
```

With the jar, the same run is:

```bash
java -jar context.jar percolator \
  -i run01.dia -l library.elib -f database.fasta \
  -massList run01_assay.txt -generateDecoys -o results_run01
```

Swap `percolator` for `mprophet` to run the other engine.

### Output layout

```text
results_run01/percolator/
  run01.peptide.reference.txt    reference target peptides, with q-value and posterior_error_prob
  run01.psm.reference.txt        the same at PSM level
  run01.rescored_features.txt    every reference feature, target and decoy, with its score
  model/                         the model learned on the background
  work/                          intermediate tables and files from pyIsoPEP

results_run01/context-mprophet/
  run01.peptide.reference.target.txt     target reference peptides
  run01.peptide.reference.decoy.txt      decoy reference peptides
  run01.peptide.background.target.txt    target background peptides
  run01.peptide.background.decoy.txt     decoy background peptides
```

### Re-running without re-scoring

Scoring the acquisition leaves `run01_background.features.txt` and `run01_reference.features.txt` next to the acquisition, and both engines can start from those directly:

```bash
java -jar context.jar mprophet \
  -background run01_background.features.txt \
  -reference  run01_reference.features.txt \
  -f          database.fasta \
  -o          results_run01 \
  -prefix     run01
```

## Options

| flag | default | description |
|------|---------|-------------|
| `-i`, `-l`, `-f`, `-massList` | *required* | the four inputs above |
| `-background`, `-reference` | — | use these plus `-f` instead, to start from feature files already split |
| `-generateDecoys` | off | add entrapment decoys when the mass list has none |
| `-o` | next to the input | output directory |
| `-prefix` | the acquisition's name | base name for the output files |
| `-fdr` | `0.01` | peptide FDR threshold used for reporting |
| `-pyisopep` | look on `PATH` | path to the `pyisopep` executable (`percolator` only) |
| `--allow-legacy-features` | `false` | accept older direct split files without pair sidecars (`percolator` and `mprophet`) |

The `mprophet` command accepts only the flags shown in its `-h` output; `-generateDecoys`
is accepted on raw runs because CTX-016 scoring supplies assay decoys when needed.

## Thermo .raw files

Context reads `.dia`, `.mzML` and Bruker `.d`, but not Thermo `.raw`: the published jar and image
leave out Thermo's RawFileReader, whose licence forbids redistributing it. Convert first, with
ProteoWizard:

```bash
apptainer pull pwiz.sif docker://proteowizard/pwiz-skyline-i-agree-to-the-vendor-licenses
apptainer exec -B "$(mktemp -d /dev/shm/wineXXXX)":/mywineprefix \
  pwiz.sif wine msconvert run01.raw -o .
```

Then run Context on `run01.mzML`.

## CTX-016 matched-decoy feature scoring

`features-folder` scores paired `.dia` and assay-schedule `.txt` files. It builds a run-local
predicted library from the source `.elib`, chooses EncyclopeDIA 6.9.23's predicted or shuffled
decoy mode, and writes only the best scored peak group per search candidate set to the feature
TSV. A target with no predicted spectrum is excluded; a target with no predicted decoy is
reported as target-only and can be searched alone when predicted mode is selected. The
prediction report records both cases and the selected search mode appears in the pair sidecar.

```bash
java -jar context.jar features-folder \
  --library library.elib --fasta database.fasta --dia-folder input_runs \
  --start-seed 1 --end-seed 10
```

The defaults Ariana selected are `--prediction-nce 27`, `--prediction-default-charge 2`, and
`--adjust-nce-for-dia true`. The charge setting is the NCE reference charge; each prediction
request still uses the source precursor's actual charge. Use `--candidate-diagnostics true` to
write a separate ranked pre-trim candidate TSV. It does not send losing candidates to the
feature TSV or confidence engines.

For a run named `run`, Context writes `run_ctx016_<identity>.elib` and
`run_ctx016_<identity>_predictions.tsv`, the raw `run.features.txt` with
`run.features.txt.pairs.tsv` and `run.features.txt.ctx016.sha256`, and
`run_reference.features.txt` / `run_background.features.txt` with matching `.pairs.tsv` files.
The pair sidecar is keyed by the existing feature `id`; it records target and decoy modified
sequence/charge keys, relation, prediction or shuffle method, eligibility, mode, and winner
status. Empirical assay entrapment assignments from `bootstrap` are a separate relationship.
Pair fields do not become numeric model features.

The `mprophet` command accepts the raw `-i/-l/-f/-massList` form above or direct
`-background/-reference/-f` split feature files. It trains on background rows and applies
that model to reference rows through the existing Context mProphet runner. Both engines
require the `.pairs.tsv` companions for direct CTX-016 split files; for older split files
without either companion, pass `--allow-legacy-features true`. mProphet output goes to a fresh
`<output-root>/context-mprophet/` directory (`-o` selects the root), so an earlier result
is never overwritten by the CLI.

Existing raw feature caches require a matching pair sidecar and SHA-256 manifest. To reuse an
older raw feature file without CTX-016 metadata, pass `--allow-legacy-features true` explicitly;
legacy reference/background classification then uses the prior sequence rules. An interrupted
CTX-016 scoring attempt is marked while it runs; on retry, Context preserves its incomplete
outputs, including the prediction report, in a nearby `.failed-*` directory before generating
new outputs. A lock beside the feature file prevents another invocation from archiving an active
run. Bruker `.d` acquisitions are accepted as directories, and the cache identity hashes their
relative paths and file contents in a stable order.
An unmarked partial or stale cache is rejected. Partitioning retains the best
feature per modified sequence and charge before assigning reference/background populations.
Keep each `.pairs.tsv` beside its feature file when handing
split features to a downstream workflow.

JChronologer 1.0.10 bundles PyTorch 2.7.1 native libraries for macOS Apple Silicon, Linux
x86_64, and Windows x86_64. Live prediction is unavailable on Intel macOS with this pinned
dependency; Context reports the model-load error rather than substituting another predictor.

## ScribeTwo feature scoring over a folder

`scribe-features` keeps its positional single-acquisition syntax and also accepts a named folder mode:

```bash
java -jar context.jar scribe-features \
  --dia-folder input_runs \
  --library library.elib \
  --fasta database.fasta \
  --output-directory scribe_results \
  --start-seed 1 \
  --end-seed 10
```

Folder mode looks only at immediate `.dia` files. By default it matches `_masked` followed by the seed and
pairs each acquisition with a readable `.txt` file having the same basename. For example,
`sample_masked1_assay.dia` pairs with `sample_masked1_assay.txt`. The seed range is inclusive and defaults
to 1 through 100.

Use `--input-prefix` to replace `_masked` with literal text. Regular-expression characters are not special:

```bash
java -jar context.jar scribe-features \
  --dia-folder input_runs \
  --library library.elib \
  --fasta database.fasta \
  --output-directory scribe_results \
  --start-seed 1 --end-seed 10 \
  --input-prefix _bootstrap
```

This matches names such as `sample_bootstrap1_assay.dia`. Context validates the complete seed range and
all assay schedules before starting Scribe. Each acquisition writes under
`scribe_results/<acquisition-basename>/`. Processing continues after an individual scoring failure, retains
successful outputs, prints an ordered summary, and makes the overall command fail if any seed failed.

Scribe settings can be appended as normal single-dash property/value pairs, for example
`-numberOfThreadsUsed 2 -adjustTolerances true`. Use `scribe-context` with the same arguments to additionally
train the final background LDA and apply it to the reference features.

## All commands

```text
java -jar context.jar <command> -h
```

| command | what it does |
|---------|--------------|
| `percolator` | train Percolator on the background, apply it to the reference |
| `mprophet` | train an mProphet LDA on the background, apply it to the reference |
| `decoys` | add entrapment decoys to a mass list |
| `features` | score an acquisition and split the features, without running an engine |
| `features-folder` | score paired seeded DIA and assay-schedule files in a folder |
| `scribe-features` | run ScribeTwo feature scoring for one acquisition or a seeded folder |
| `scribe-context` | run ScribeTwo feature scoring plus final Context LDA training |
| `bootstrap` | build a simulated targeted assay from a library |
