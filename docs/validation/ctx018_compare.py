#!/usr/bin/env python3
"""CTX-018: compare Context (Java) with Context-MS (Python) Percolator and mProphet results.

Two subcommands:

  run-context-ms  Run Context-MS (imported as an installed package; its source is not edited)
                  for one engine over a list of seeds. Each seed gets a fresh directory with
                  Context-MS's outputs and a provenance.json. With --export-folds (mProphet only),
                  the script wraps Context-MS internals to record the training folds it actually
                  used (folds.tsv: iteration, fold, position, id) and the per-iteration passing
                  counts (iterations.tsv). Wrapping is passive: it does not change any result.

  compare         Compute the CTX-018 r2 gates, acceptance criteria and report-only metrics
                  for one engine from Context runs and Context-MS runs, and write metrics.json
                  and summary.md. Context runs are optional (Context-MS noise floor only).

Populations: every metric uses the reference population only (background trains the model and
is never scored here). Peptide-level counts use the peptide-level target outputs; z-scores use all
PSM-level reference rows (targets for comparison, decoys for standardisation).

Requires numpy, pandas, scipy and an installed Context-MS (`pip install --no-deps <checkout>`);
run-context-ms also needs `percolator` on PATH for the Percolator engine and pyIsoPEP importable.
"""
import argparse
import hashlib
import importlib.metadata
import json
import math
import platform
import shutil
import sys
import time
from pathlib import Path

import numpy as np
import pandas as pd
from scipy.stats import spearmanr

ENGINES = ("percolator", "mprophet")
PEP_THRESHOLD = 0.01
Q_THRESHOLD = 0.01
PEPTIDE_TOLERANCE_FRACTION = 0.02   # criterion: |dN| <= max(2% of the Context-MS median, 1 peptide)
PEPTIDE_TOLERANCE_MINIMUM = 1
SCORE_TOLERANCE = 0.20              # criterion: median over runs of the per-run median |dz|/|z| <= 20%
Z_ROW_MINIMUM = 1.0                 # rows: reference targets with consensus |z| >= 1
MONOTONE_TOLERANCE = 1e-12          # PEP may not decrease as score decreases by more than this


# ----------------------------------------------------------------------------- utilities

def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def parse_seeds(text):
    seeds = []
    for token in text.split(","):
        token = token.strip()
        if "-" in token:
            low, high = (int(v) for v in token.split("-", 1))
            seeds.extend(range(low, high + 1))
        elif token:
            seeds.append(int(token))
    if not seeds or len(set(seeds)) != len(seeds):
        raise SystemExit(f"invalid or duplicate seeds: {text!r}")
    return seeds


def package_versions():
    names = ["numpy", "pandas", "scipy", "pyIsoPEP", "context-ms"]
    versions = {}
    for name in names:
        try:
            versions[name] = importlib.metadata.version(name)
        except importlib.metadata.PackageNotFoundError:
            versions[name] = None
    versions["python"] = platform.python_version()
    return versions


def read_tsv(path):
    """Read a tab-separated file as strings, dropping Context's trailing 'pi_0=' lines."""
    frame = pd.read_csv(path, sep="\t", dtype=str, keep_default_na=False)
    first = frame.columns[0]
    return frame[~frame[first].str.startswith("pi_0=")].reset_index(drop=True)


def numeric(series, what):
    values = pd.to_numeric(series, errors="coerce")
    if values.isna().any():
        raise ValueError(f"{what}: {int(values.isna().sum())} non-numeric value(s)")
    return values.to_numpy(dtype=float)


def peptide_key(value):
    """Context's legacy mProphet files wrap peptides as '-.SEQ.-'; compare the bare sequence."""
    if value.startswith("-.") and value.endswith(".-"):
        return value[2:-2]
    return value


# ----------------------------------------------------------------------------- run-context-ms

def run_context_ms(args):
    from context import pipeline
    import context.mprophet as mprophet

    background = Path(args.background).resolve()
    reference = Path(args.reference).resolve()
    seeds = parse_seeds(args.seeds)
    root = Path(args.outdir).resolve() / args.engine
    percolator_path = shutil.which("percolator")
    if args.engine == "percolator" and percolator_path is None:
        raise SystemExit("percolator is not on PATH; Context-MS would fall back to an unpinned container image")
    if args.export_folds and args.engine != "mprophet":
        raise SystemExit("--export-folds applies only to the mprophet engine")

    for seed in seeds:
        seed_dir = root / f"seed_{seed}"
        if seed_dir.exists():
            raise SystemExit(f"refusing to reuse an existing run directory: {seed_dir}")
        seed_dir.mkdir(parents=True)
        captured = _install_capture(mprophet) if args.export_folds else None
        started = time.time()
        try:
            pipeline.run(background, reference, outdir=seed_dir, prefix=args.prefix, seed=seed,
                         engine=args.engine, container_cmd="unused-percolator-on-path")
        finally:
            if captured is not None:
                _remove_capture(mprophet, captured)
        elapsed = time.time() - started
        if captured is not None:
            _write_folds(captured, seed_dir)
        provenance = {
            "tool": "context-ms", "engine": args.engine, "seed": seed, "prefix": args.prefix,
            "background": str(background), "background_sha256": sha256(background),
            "reference": str(reference), "reference_sha256": sha256(reference),
            "context_ms_commit": args.context_ms_commit,
            "percolator": None if percolator_path is None else {
                "path": percolator_path, "realpath": str(Path(percolator_path).resolve()),
                "sha256": sha256(Path(percolator_path).resolve())},
            "packages": package_versions(), "export_folds": bool(args.export_folds),
            "elapsed_seconds": round(elapsed, 3), "argv": sys.argv,
        }
        (seed_dir / "provenance.json").write_text(json.dumps(provenance, indent=2) + "\n")
        print(f"[ctx018] context-ms {args.engine} seed {seed}: {seed_dir} ({elapsed:.1f}s)", flush=True)


def _install_capture(mprophet):
    captured = {"splits": [], "inner": {}, "originals": {}}
    originals = captured["originals"]
    originals["_split_kfold"] = mprophet._split_kfold
    originals["_inner_loop"] = mprophet._inner_loop
    originals["train"] = mprophet.train

    def split_kfold(n, k, rng, max_fold_size):
        folds = originals["_split_kfold"](n, k, rng, max_fold_size)
        captured["splits"].append([np.array(f, copy=True) for f in folds])
        return folds

    def inner_loop(train_t, train_d, seed_lda, **kwargs):
        lda = originals["_inner_loop"](train_t, train_d, seed_lda, **kwargs)
        iteration = len(captured["splits"]) // 2 - 1
        captured["inner"][iteration] = lda
        return lda

    def train(df, feature_cols, label_col, **kwargs):
        captured["df"] = df
        captured["feature_cols"] = list(feature_cols)
        captured["label_col"] = label_col
        captured["seed_coefficients"] = kwargs.get("seed_coefficients")
        return originals["train"](df, feature_cols, label_col, **kwargs)

    mprophet._split_kfold = split_kfold
    mprophet._inner_loop = inner_loop
    mprophet.train = train
    return captured


def _remove_capture(mprophet, captured):
    for name, original in captured["originals"].items():
        setattr(mprophet, name, original)


def _write_folds(captured, seed_dir):
    import context.mprophet as mprophet
    from context.io import numeric_features

    df = captured["df"]
    labels = pd.to_numeric(df[captured["label_col"]], errors="coerce").fillna(0).astype(int).to_numpy()
    ids = df[df.columns[0]].to_numpy()
    if len(set(ids)) != len(ids):
        raise SystemExit("background ids are not unique; folds cannot be joined by id")
    target_ids, decoy_ids = ids[labels == 1], ids[labels == -1]
    features = numeric_features(df, captured["feature_cols"]).to_numpy(dtype=float)
    x_t, x_d = features[labels == 1], features[labels == -1]
    seed_lda = mprophet._seed_lda(captured["feature_cols"], captured["seed_coefficients"] or {})
    splits = captured["splits"]
    if len(splits) % 2:
        raise SystemExit("unexpected Context-MS fold sequence (odd number of splits)")

    fold_rows, iteration_rows = [], []
    for iteration in range(len(splits) // 2):
        t_folds, d_folds = splits[2 * iteration], splits[2 * iteration + 1]
        for fold in range(len(t_folds)):
            members = list(target_ids[t_folds[fold]]) + list(decoy_ids[d_folds[fold]])
            fold_rows.extend((iteration, fold, position, identifier) for position, identifier in enumerate(members))
        lda = captured["inner"].get(iteration)
        test_t, test_d = x_t[t_folds[1]], x_d[d_folds[1]]
        lda_pass = seed_pass = None
        if lda is not None:
            lda_pass = len(mprophet._passing_by_fdr(mprophet._score(test_t, lda), mprophet._score(test_d, lda), 0.01))
            if mprophet._is_usable(seed_lda):
                seed_pass = len(mprophet._passing_by_fdr(mprophet._score(test_t, seed_lda),
                                                         mprophet._score(test_d, seed_lda), 0.01))
        chosen = None if lda is None else ("seed" if seed_pass is not None and seed_pass > lda_pass else "trained")
        iteration_rows.append({
            "iteration": iteration, "train_targets": len(t_folds[0]), "train_decoys": len(d_folds[0]),
            "test_targets": len(t_folds[1]), "test_decoys": len(d_folds[1]),
            "trained_model": lda is not None, "trained_passing": lda_pass, "seed_passing": seed_pass,
            "kept": chosen, "kept_passing": None if chosen is None else (seed_pass if chosen == "seed" else lda_pass),
        })
    pd.DataFrame(fold_rows, columns=["iteration", "fold", "position", "id"]).to_csv(
        seed_dir / "folds.tsv", sep="\t", index=False)
    pd.DataFrame(iteration_rows).to_csv(seed_dir / "iterations.tsv", sep="\t", index=False)


# ----------------------------------------------------------------------------- loading runs

class Run:
    """One engine run of one tool: reference rows (PSM level) and peptide-level targets."""

    def __init__(self, tool, seed, rows, peptides, psms, note, weights=None):
        self.tool, self.seed, self.rows, self.peptides, self.psms = tool, seed, rows, peptides, psms
        self.note, self.weights = note, weights


def _reference_rows(frame, id_col, label_col):
    return pd.DataFrame({
        "id": frame[id_col].to_numpy(),
        "label": pd.to_numeric(frame[label_col], errors="raise").astype(int).to_numpy(),
        "score": numeric(frame["score"], "score"),
    })


def _report(frame, id_col, peptide_col):
    return pd.DataFrame({
        "id": frame[id_col].to_numpy(),
        "peptide": [peptide_key(v) for v in frame[peptide_col]],
        "score": numeric(frame["score"], "score"),
        "q": numeric(frame["q-value"], "q-value"),
        "pep": numeric(frame["posterior_error_prob"], "posterior_error_prob"),
    })


def load_context_ms(seed_dir, engine):
    provenance = json.loads((seed_dir / "provenance.json").read_text())
    if provenance["engine"] != engine:
        raise SystemExit(f"{seed_dir}: engine {provenance['engine']} != {engine}")
    prefix = provenance["prefix"]
    rescored = read_tsv(seed_dir / f"{prefix}.rescored_features.tsv")
    peptides = read_tsv(seed_dir / f"{prefix}.peptide.reference.txt")
    psms = read_tsv(seed_dir / f"{prefix}.psm.reference.txt")
    header = list(rescored.columns)
    weights = seed_dir / "weights" / f"{prefix}.weights.txt"
    return Run("context-ms", provenance["seed"], _reference_rows(rescored, header[0], header[1]),
               _report(peptides, peptides.columns[0], peptides.columns[-2]),
               _report(psms, psms.columns[0], psms.columns[-2]), "rescored_features.tsv", weights)


def load_context(seed, engine_dir, prefix, engine):
    if engine == "percolator":
        rescored = read_tsv(engine_dir / f"{prefix}.rescored_features.txt")
        peptides = read_tsv(engine_dir / f"{prefix}.peptide.reference.txt")
        psms = read_tsv(engine_dir / f"{prefix}.psm.reference.txt")
        header = list(rescored.columns)
        return Run("context", seed, _reference_rows(rescored, header[0], header[1]),
                   _report(peptides, peptides.columns[0], peptides.columns[-2]),
                   _report(psms, psms.columns[0], psms.columns[-2]), "rescored_features.txt",
                   engine_dir / "model" / f"{prefix}.weights.txt")

    # mProphet. r2 adds rescored features and Context-MS-format reports (CTX018-007/023); until then
    # only the legacy de-duplicated target/decoy files exist, with PSM-level q/PEP carried over.
    modern = engine_dir / f"{prefix}.rescored_features.txt"
    if modern.exists():
        rescored = read_tsv(modern)
        peptides = read_tsv(engine_dir / f"{prefix}.peptide.reference.txt")
        psms = read_tsv(engine_dir / f"{prefix}.psm.reference.txt")
        header = list(rescored.columns)
        return Run("context", seed, _reference_rows(rescored, header[0], header[1]),
                   _report(peptides, peptides.columns[0], peptides.columns[-2]),
                   _report(psms, psms.columns[0], psms.columns[-2]), "rescored_features.txt",
                   engine_dir / "model" / f"{prefix}.weights.txt")
    targets = read_tsv(engine_dir / f"{prefix}.peptide.reference.target.txt")
    decoys = read_tsv(engine_dir / f"{prefix}.peptide.reference.decoy.txt")
    legacy = pd.concat([targets.assign(Label="1"), decoys.assign(Label="-1")], ignore_index=True)
    rows = _reference_rows(legacy, "PSMId", "Label")
    report = _report(targets, "PSMId", "peptide")
    return Run("context", seed, rows, report, report,
               "legacy de-duplicated peptide files: z uses de-duplicated rows and PSM-level q/PEP; "
               "exact only when each reference peptide has one row")


# ----------------------------------------------------------------------------- metrics

def z_scores(run):
    decoys = run.rows.loc[run.rows["label"] == -1, "score"].to_numpy()
    sd = decoys.std(ddof=0)
    if decoys.size == 0 or not math.isfinite(sd) or sd <= 0:
        raise ValueError(f"{run.tool} seed {run.seed}: reference decoy SD is {sd}; z is undefined")
    targets = run.rows[run.rows["label"] == 1]
    return pd.Series((targets["score"].to_numpy() - decoys.mean()) / sd, index=targets["id"].to_numpy())


def validity(run):
    problems = []
    if not np.isfinite(run.rows["score"]).all():
        problems.append("non-finite reference score")
    for name, report in (("peptide", run.peptides), ("psm", run.psms)):
        for column in ("score", "q", "pep"):
            if not np.isfinite(report[column]).all():
                problems.append(f"{name}: non-finite {column}")
        for column in ("q", "pep"):
            if ((report[column] < 0) | (report[column] > 1)).any():
                problems.append(f"{name}: {column} outside [0, 1]")
        ordered = report.sort_values("score", ascending=False, kind="mergesort")
        score, pep = ordered["score"].to_numpy(), ordered["pep"].to_numpy()
        strictly_lower = np.diff(score) < 0
        if (np.diff(pep)[strictly_lower] < -MONOTONE_TOLERANCE).any():
            problems.append(f"{name}: PEP decreases as score decreases")
    return problems


def count_passing(report, column, threshold):
    return int((report[column] <= threshold).sum())


def relative_z_differences(z_run, consensus):
    rows = consensus.index[consensus.abs() >= Z_ROW_MINIMUM]
    common = rows.intersection(z_run.index)
    if len(common) != len(rows):
        raise ValueError(f"{len(rows) - len(common)} consensus row(s) missing from a run")
    reference = consensus.loc[common]
    return (z_run.loc[common] - reference).abs() / reference.abs(), reference, z_run.loc[common]


def summarise_against(z_run, consensus):
    differences, reference, values = relative_z_differences(z_run, consensus)
    rho = spearmanr(values.to_numpy(), reference.to_numpy()).statistic if len(values) > 2 else float("nan")
    return {
        "rows": int(len(differences)),
        "median_relative_z_difference": float(np.median(differences)),
        "p90_relative_z_difference": float(np.quantile(differences, 0.9)),
        "spearman": float(rho),
        "sign_flips": int((np.sign(values.to_numpy()) != np.sign(reference.to_numpy())).sum()),
    }


def percolator_fold_weights(path):
    from context.percolator import parse_weights
    names, raw, bias = parse_weights(path)
    return names, np.column_stack([raw, bias])


def compare_weights(context_path, context_ms_path):
    names_c, w_c = percolator_fold_weights(context_path)
    names_m, w_m = percolator_fold_weights(context_ms_path)
    if names_c != names_m or w_c.shape != w_m.shape:
        return {"comparable": False, "context_features": names_c, "context_ms_features": names_m}
    difference = np.abs(w_c - w_m)
    scale = np.maximum(np.abs(w_m), 1e-12)
    return {"comparable": True, "bins": int(w_c.shape[0]), "max_abs_difference": float(difference.max()),
            "max_relative_difference": float((difference / scale).max())}


def compare(args):
    engine = args.engine
    out = Path(args.out)
    if out.exists():
        raise SystemExit(f"refusing to overwrite {out}")
    ms_root = Path(args.context_ms_root) / engine
    ms_runs = [load_context_ms(d, engine) for d in sorted(ms_root.glob("seed_*"), key=lambda p: int(p.name[5:]))]
    if not ms_runs:
        raise SystemExit(f"no Context-MS runs under {ms_root}")
    context_runs = []
    for spec in args.context or []:
        seed, engine_dir, prefix = spec.split(":", 2)
        context_runs.append(load_context(int(seed), Path(engine_dir), prefix, engine))

    result = {"engine": engine, "context_ms_seeds": [r.seed for r in ms_runs],
              "context_seeds": [r.seed for r in context_runs],
              "definitions": {
                  "N": f"reference target peptides with PEP <= {PEP_THRESHOLD} (peptide-level output)",
                  "peptide_criterion": "|median N(Context) - median N(Context-MS)| <= max(0.02 x median N(Context-MS), 1)",
                  "z": "(score - mean of reference decoy scores) / population SD of reference decoy scores, per run",
                  "consensus": "per-row median z over the Context-MS seeds",
                  "score_criterion": "median over Context runs of the per-run median |z - consensus| / |consensus|, "
                                     "over reference targets with |consensus| >= 1, joined by id, <= 0.20"}}

    # Context-MS self-consistency and noise floors.
    ms_n = [count_passing(r.peptides, "pep", PEP_THRESHOLD) for r in ms_runs]
    ms_z = [z_scores(r) for r in ms_runs]
    consensus = pd.concat(ms_z, axis=1).median(axis=1)
    loo = []
    if len(ms_runs) > 1:
        for i, z in enumerate(ms_z):
            others = pd.concat([v for j, v in enumerate(ms_z) if j != i], axis=1).median(axis=1)
            loo.append(summarise_against(z, others)["median_relative_z_difference"])
    result["context_ms"] = {
        "N_by_seed": dict(zip([r.seed for r in ms_runs], ms_n)),
        "N_median": float(np.median(ms_n)), "N_min": int(min(ms_n)), "N_max": int(max(ms_n)),
        "q_count_by_seed": {r.seed: count_passing(r.peptides, "q", Q_THRESHOLD) for r in ms_runs},
        # Report-only: when no q-value reaches 0.01, any PEP <= 0.01 comes from pyIsoPEP's fit below the lowest
        # observed q, and the count can jump between 0 and ~25 by seed (seen in both tools on example/).
        "min_q_by_seed": {r.seed: float(r.peptides["q"].min()) for r in ms_runs},
        "min_pep_by_seed": {r.seed: float(r.peptides["pep"].min()) for r in ms_runs},
        "loo_median_relative_z_difference": float(np.median(loo)) if loo else None,
        "consensus_rows_abs_z_ge_1": int((consensus.abs() >= Z_ROW_MINIMUM).sum()),
        "validity_problems": {r.seed: validity(r) for r in ms_runs},
    }

    if context_runs:
        ms_targets = set(ms_runs[0].rows.loc[ms_runs[0].rows["label"] == 1, "id"])
        ms_decoys = set(ms_runs[0].rows.loc[ms_runs[0].rows["label"] == -1, "id"])
        ms_peptides = set(ms_runs[0].peptides["peptide"])
        passing_votes = pd.concat([r.peptides.set_index("peptide")["pep"] <= PEP_THRESHOLD for r in ms_runs], axis=1)
        majority = set(passing_votes.index[passing_votes.sum(axis=1) * 2 > len(ms_runs)])
        per_run = []
        for run in context_runs:
            targets = set(run.rows.loc[run.rows["label"] == 1, "id"])
            decoys = set(run.rows.loc[run.rows["label"] == -1, "id"])
            passing = set(run.peptides.loc[run.peptides["pep"] <= PEP_THRESHOLD, "peptide"])
            union = passing | majority
            entry = {
                "seed": run.seed, "source": run.note,
                "gate_identity": {"target_ids_equal": targets == ms_targets, "decoy_ids_equal": decoys == ms_decoys,
                                  "peptide_keys_equal": set(run.peptides["peptide"]) == ms_peptides,
                                  "targets": len(targets), "decoys": len(decoys)},
                "gate_validity_problems": validity(run),
                "N": count_passing(run.peptides, "pep", PEP_THRESHOLD),
                "q_count": count_passing(run.peptides, "q", Q_THRESHOLD),
                "min_q": float(run.peptides["q"].min()), "min_pep": float(run.peptides["pep"].min()),
                "jaccard_vs_context_ms_majority": (len(passing & majority) / len(union)) if union else 1.0,
            }
            try:
                entry["scores"] = summarise_against(z_scores(run), consensus)
            except ValueError as error:
                entry["scores"] = {"error": str(error)}
            if engine == "percolator" and run.weights is not None:
                twin = next((r for r in ms_runs if r.seed == run.seed), None)
                if twin is not None and run.weights.exists() and twin.weights.exists():
                    entry["percolator_fold_weights_vs_same_seed"] = compare_weights(run.weights, twin.weights)
            per_run.append(entry)
        n_median = float(np.median([e["N"] for e in per_run]))
        tolerance = max(PEPTIDE_TOLERANCE_FRACTION * result["context_ms"]["N_median"], PEPTIDE_TOLERANCE_MINIMUM)
        score_medians = [e["scores"]["median_relative_z_difference"] for e in per_run if "error" not in e["scores"]]
        gates_pass = all(all(v for k, v in e["gate_identity"].items() if k.endswith("_equal"))
                         and not e["gate_validity_problems"] for e in per_run)
        result["context"] = {
            "runs": per_run,
            "N_median": n_median,
            "peptide_difference": n_median - result["context_ms"]["N_median"],
            "peptide_tolerance": tolerance,
            "peptide_criterion_met": abs(n_median - result["context_ms"]["N_median"]) <= tolerance,
            "peptide_tolerance_narrower_than_context_ms_spread":
                2 * tolerance < (result["context_ms"]["N_max"] - result["context_ms"]["N_min"]),
            "score_median_of_run_medians": float(np.median(score_medians)) if score_medians else None,
            "score_criterion_met": (bool(np.median(score_medians) <= SCORE_TOLERANCE) if score_medians else None),
            "gates_pass": gates_pass,
        }
        if len(per_run) < len(ms_runs):
            result["context"]["note"] = (f"{len(per_run)} Context run(s) vs {len(ms_runs)} Context-MS runs; "
                                         "the r2 acceptance criteria require seeds 1-10 for both tools")

    out.mkdir(parents=True)
    (out / "metrics.json").write_text(json.dumps(result, indent=2, default=_json_default) + "\n")
    (out / "summary.md").write_text(_summary(result))
    print((out / "summary.md").read_text())


def _json_default(value):
    if isinstance(value, (np.integer,)):
        return int(value)
    if isinstance(value, (np.floating,)):
        return float(value)
    if isinstance(value, (np.bool_,)):
        return bool(value)
    raise TypeError(type(value))


def _summary(result):
    ms = result["context_ms"]
    lines = [f"# CTX-018 comparison: {result['engine']}", "",
             f"Context-MS seeds {result['context_ms_seeds']}: N (PEP <= 0.01) median {ms['N_median']:g}, "
             f"min {ms['N_min']}, max {ms['N_max']}; leave-one-out median relative z difference "
             f"{_fmt(ms['loo_median_relative_z_difference'])}; consensus rows with |z| >= 1: "
             f"{ms['consensus_rows_abs_z_ge_1']}.", ""]
    context = result.get("context")
    if context:
        lines += ["| seed | N | q count | gates | median rel. z diff | p90 | Spearman | sign flips | Jaccard |",
                  "|---|---|---|---|---|---|---|---|---|"]
        for e in context["runs"]:
            gates = all(v for k, v in e["gate_identity"].items() if k.endswith("_equal")) and not e["gate_validity_problems"]
            s = e["scores"]
            lines.append(f"| {e['seed']} | {e['N']} | {e['q_count']} | {'pass' if gates else 'FAIL'} | "
                         f"{_fmt(s.get('median_relative_z_difference'))} | {_fmt(s.get('p90_relative_z_difference'))} | "
                         f"{_fmt(s.get('spearman'))} | {s.get('sign_flips', 'n/a')} | "
                         f"{_fmt(e['jaccard_vs_context_ms_majority'])} |")
        lines += ["",
                  f"Peptides: Context median {context['N_median']:g} vs Context-MS {ms['N_median']:g} "
                  f"(difference {context['peptide_difference']:+g}, tolerance {context['peptide_tolerance']:g}): "
                  f"{'met' if context['peptide_criterion_met'] else 'NOT met'}"
                  + (" (the tolerance is narrower than Context-MS's own seed spread: refer to Ariana's threshold review)"
                     if context["peptide_tolerance_narrower_than_context_ms_spread"] else "") + ".",
                  f"Scores: median of run medians {_fmt(context['score_median_of_run_medians'])} (limit 0.20): "
                  f"{_met(context['score_criterion_met'])}.",
                  f"Gates: {'pass' if context['gates_pass'] else 'FAIL'}."]
        if context.get("note"):
            lines += ["", f"Note: {context['note']}"]
    return "\n".join(lines) + "\n"


def _fmt(value):
    return "n/a" if value is None or (isinstance(value, float) and math.isnan(value)) else f"{value:.4g}"


def _met(value):
    return "n/a" if value is None else ("met" if value else "NOT met")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    run = sub.add_parser("run-context-ms", help="run Context-MS over seeds")
    run.add_argument("--background", required=True)
    run.add_argument("--reference", required=True)
    run.add_argument("--engine", choices=ENGINES, required=True)
    run.add_argument("--seeds", default="1-10", help="e.g. 1-10 or 1,3,5")
    run.add_argument("--prefix", required=True)
    run.add_argument("--outdir", required=True, help="runs go to <outdir>/<engine>/seed_<n>/")
    run.add_argument("--context-ms-commit", default=None, help="Context-MS commit recorded in provenance")
    run.add_argument("--export-folds", action="store_true", help="mProphet only: write folds.tsv and iterations.tsv")
    cmp = sub.add_parser("compare", help="compute CTX-018 metrics")
    cmp.add_argument("--engine", choices=ENGINES, required=True)
    cmp.add_argument("--context-ms-root", required=True, help="the --outdir given to run-context-ms")
    cmp.add_argument("--context", action="append", metavar="SEED:ENGINE_DIR:PREFIX",
                     help="a Context run: seed, its engine output directory, and its prefix (repeatable)")
    cmp.add_argument("--out", required=True, help="new directory for metrics.json and summary.md")
    args = parser.parse_args()
    run_context_ms(args) if args.command == "run-context-ms" else compare(args)


if __name__ == "__main__":
    main()
