#!/usr/bin/env python3
"""CTX018-006: write the reference values the Java port of Context-MS's fdr.py is tested against.

Output: src/test/resources/org/searlelab/context/mprophet/ctx018_fdr_reference.tsv, one row per score:
  set, kind (target/decoy), score, p, q, pep   (p/q/pep are NA for decoys)

Every set is synthetic and deterministic (numpy default_rng, seeds below), so the file can be regenerated
exactly: `python -I docs/validation/ctx018_fdr_fixture.py <output.tsv>` with Context-MS d207f47 installed.
Sets cover a typical separation, tied scores, extreme scores (p underflow), a small reference set, and targets
that all lie inside the decoy distribution (pi0 near 1).
"""
import sys

import numpy as np
from context import fdr


def sets():
    rng = np.random.default_rng(20261101)
    yield "typical", np.r_[rng.normal(3, 1, 30), rng.normal(0, 1, 30)], rng.normal(0, 1, 40)
    tied = np.round(rng.normal(2, 1.5, 40), 1)
    yield "ties", tied, np.round(rng.normal(0, 1, 40), 1)
    yield "extreme", np.r_[rng.normal(2, 1, 20), [40.0, 60.0, -30.0]], rng.normal(0, 1, 25)
    yield "small", rng.normal(2.5, 1, 5), rng.normal(0, 1, 5)
    yield "null", rng.normal(0, 1, 30), rng.normal(0, 1, 30)


def main():
    out = sys.argv[1]
    with open(out, "w") as handle:
        handle.write("# CTX018-006 reference values from Context-MS d207f47 context/fdr.py "
                     "(gaussian_pvalues, benjamini_hochberg, local_fdr).\n")
        handle.write("# Regenerate: python -I docs/validation/ctx018_fdr_fixture.py <this file>\n")
        handle.write("set\tkind\tscore\tp\tq\tpep\n")
        for name, targets, decoys in sets():
            p = fdr.gaussian_pvalues(targets, decoys)
            q, pep = fdr.q_and_pep(targets, decoys)
            for score, pv, qv, pepv in zip(targets, p, q, pep):
                handle.write(f"{name}\ttarget\t{float(score)!r}\t{float(pv)!r}\t{float(qv)!r}\t{float(pepv)!r}\n")
            for score in decoys:
                handle.write(f"{name}\tdecoy\t{float(score)!r}\tNA\tNA\tNA\n")


if __name__ == "__main__":
    main()
