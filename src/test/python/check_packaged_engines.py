"""Packaged engine integration using an alternating split of the existing fixture.

This is an executable/ID-preservation check, not a scientific calibration dataset.
Run from the repository root with Python 3 and pyIsoPEP installed.
"""
import csv
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile


def main():
    jar = Path(sys.argv[1]).resolve()
    java = shutil.which("java")
    pyisopep = shutil.which("pyisopep")
    if not java or not pyisopep:
        raise RuntimeError("Java 17 and pyIsoPEP must be on PATH for this check")
    resources = Path("src/test/resources/org/searlelab/context").resolve()
    work = Path("tmp/compatibility/platform-engines").resolve()
    work.mkdir(parents=True, exist_ok=True)
    work = Path(tempfile.mkdtemp(prefix="run-", dir=work))
    with (resources / "percolator/standard_percolator_test.features.txt").open() as stream:
        table = list(csv.reader(stream, delimiter="\t"))
    header, rows = table[0], table[1:1001]
    populations = {"background": rows[::2], "reference": rows[1::2]}
    for name, values in populations.items():
        with (work / (name + ".features.txt")).open("w", newline="") as stream:
            csv.writer(stream, delimiter="\t", lineterminator="\n").writerows([header] + values)
    ids = {name: {row[header.index("id")] for row in values} for name, values in populations.items()}
    for engine in ("mprophet", "percolator"):
        command = [java, "-Djava.awt.headless=true", "-jar", str(jar), engine,
                   "-background", str(work / "background.features.txt"),
                   "-reference", str(work / "reference.features.txt"),
                   "-f", str(resources / "mprophet/mus_musculus_reviewed_uniprot.fasta"),
                   "-o", str(work / engine), "-prefix", "platform", "-fdr", "1.0",
                   "--allow-legacy-features", "true"]
        if engine == "percolator":
            command += ["-pyisopep", pyisopep]
        with (work / (engine + ".log")).open("w") as log:
            subprocess.run(command, cwd=work, stdout=log, stderr=subprocess.STDOUT,
                           check=True, timeout=300)
        if engine == "mprophet":
            reports = [(work / engine / "context-mprophet" /
                        ("platform.peptide." + name + "." + label + ".txt"), name)
                       for name in populations for label in ("target", "decoy")]
        else:
            reports = [(work / engine / "percolator/platform.rescored_features.txt", "reference")]
        for report, population in reports:
            with report.open() as stream:
                reader = csv.DictReader(stream, delimiter="\t")
                output = [row for row in reader if not row[reader.fieldnames[0]].startswith("pi_0=")]
                key = "PSMId" if "PSMId" in reader.fieldnames else "id"
            # Peptide reports expand indistinguishable I/L variants against the FASTA.
            def equivalent_id(value):
                prefix, peptide = value.rsplit(":", 1)
                return prefix + ":" + peptide.replace("I", "L")
            if not output or not {equivalent_id(row[key]) for row in output}.issubset(
                    {equivalent_id(value) for value in ids[population]}):
                raise AssertionError("Missing rows or incorrect population IDs in " + str(report))
        print(engine + ": packaged execution and population IDs passed")


if __name__ == "__main__":
    main()
