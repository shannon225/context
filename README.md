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

results_run01/mprophet/
  run01.peptide.reference.txt          target reference peptides
  run01.peptide.reference.decoy.txt    decoy reference peptides
  run01.peptide.background.txt         target background peptides
  run01.peptide.background.decoy.txt   decoy background peptides
  model/, work/
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
| `-seed` | `1` | random seed (`mprophet` only) |
| `-pyisopep` | look on `PATH` | path to the `pyisopep` executable (`percolator` only) |

Any other flag is passed straight through to EncyclopeDIA.

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
