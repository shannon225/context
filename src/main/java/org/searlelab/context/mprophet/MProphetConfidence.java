package org.searlelab.context.mprophet;

import java.util.Arrays;

import org.apache.commons.math3.special.Erf;

import edu.washington.gs.maccoss.encyclopedia.utils.Logger;

/**
 * Context-MS's mProphet q-values and PEPs (context/fdr.py at d207f47), ported for the Context mProphet workflow
 * (CTX018-006). Populations: the scored reference targets and decoys of one level (PSM, or best-per-peptide).
 * <ul>
 * <li>p-value: complementary normal CDF of the target score under a Gaussian fitted to the decoy scores (mean and
 * population SD).</li>
 * <li>q-value: Benjamini–Hochberg on the target p-values, without pi0.</li>
 * <li>PEP: Storey local FDR, pi0 (median over lambda = 0.1–0.9, floor 0.05) times the logit-p density of a uniform
 * p over a Gaussian KDE of logit p (scipy's Silverman factor), clipped to [0, 1] and made non-decreasing in p.</li>
 * </ul>
 * This differs from EncyclopeDIA's {@code LocalFDR.estimateLocalFDR}, which uses a fixed kernel and no pi0 floor.
 */
public final class MProphetConfidence {
	static final double PI0_FLOOR = 0.05;
	static final double SMALLEST_MEANINGFUL_P = 1e-8;

	/** q-values and PEPs, aligned with the target scores they were computed for. */
	public record Confidence(double[] q, double[] pep) {
	}

	private MProphetConfidence() {
	}

	public static Confidence qAndPep(double[] targetScores, double[] decoyScores) {
		double[] p = gaussianPValues(targetScores, decoyScores);
		return new Confidence(benjaminiHochberg(p), localFdr(p));
	}

	static double[] gaussianPValues(double[] targetScores, double[] decoyScores) {
		double[] p = new double[targetScores.length];
		if (decoyScores.length == 0) {
			Arrays.fill(p, 1.0);
			return p;
		}
		double mean = 0.0;
		for (double score : decoyScores) mean += score;
		mean /= decoyScores.length;
		double squares = 0.0;
		for (double score : decoyScores) squares += (score - mean) * (score - mean);
		double sigma = Math.sqrt(squares / decoyScores.length);

		for (int i = 0; i < p.length; i++) {
			if (!Double.isFinite(sigma) || sigma <= 0) {
				p[i] = targetScores[i] > mean ? 0.0 : 1.0;
			} else {
				double z = (targetScores[i] - mean) / sigma;
				p[i] = clip(0.5 * Erf.erfc(z / Math.sqrt(2.0)));
			}
		}
		return p;
	}

	static double[] benjaminiHochberg(double[] p) {
		int m = p.length;
		Integer[] order = ascending(p);
		double[] adjusted = new double[m];
		for (int rank = 0; rank < m; rank++) {
			adjusted[rank] = p[order[rank]] * m / (rank + 1.0);
		}
		for (int rank = m - 2; rank >= 0; rank--) {
			adjusted[rank] = Math.min(adjusted[rank], adjusted[rank + 1]);
		}
		double[] q = new double[m];
		for (int rank = 0; rank < m; rank++) q[order[rank]] = clip(adjusted[rank]);
		return q;
	}

	static double estimatePi0(double[] p) {
		if (p.length == 0) return 1.0;
		double[] pi0 = new double[9];
		for (int i = 1; i <= 9; i++) {
			double lambda = i / 10.0;
			int above = 0;
			for (double value : p) if (value > lambda) above++;
			pi0[i - 1] = ((double) above / p.length) / (1.0 - lambda);
		}
		Arrays.sort(pi0);
		return pi0[4];
	}

	static double[] localFdr(double[] p) {
		int n = p.length;
		double[] lfdr = new double[n];
		if (n == 0) return lfdr;
		double pi0 = Math.max(PI0_FLOOR, estimatePi0(p));

		double eps = SMALLEST_MEANINGFUL_P;
		double[] x = new double[n];
		double[] dx = new double[n];
		for (int i = 0; i < n; i++) {
			x[i] = Math.log((p[i] + eps) / (1.0 - p[i] + eps));
			double ex = Math.exp(x[i]);
			dx[i] = ex / ((1.0 + ex) * (1.0 + ex));
		}

		double[] density = silvermanDensity(x);
		for (int i = 0; i < n; i++) {
			double f = density == null ? Double.NaN : density[i];
			lfdr[i] = clip(f > 0 ? pi0 * dx[i] / f : 1.0);
		}

		Integer[] order = ascending(p);
		double running = Double.NEGATIVE_INFINITY;
		for (Integer index : order) {
			running = Math.max(running, lfdr[index]);
			lfdr[index] = running;
		}
		return lfdr;
	}

	/**
	 * scipy.stats.gaussian_kde(x, bw_method="silverman") evaluated at x: bandwidth = sample SD (ddof 1) times
	 * (3n/4)^(-1/5). Returns null where scipy fails: a single value (Context-MS then stops with an error; here
	 * every PEP becomes 1 with a warning) or zero spread (scipy's singular covariance, which Context-MS maps to 1).
	 */
	private static double[] silvermanDensity(double[] x) {
		int n = x.length;
		if (n < 2) {
			Logger.errorLine("Warning: a PEP needs at least two targets; with " + n + ", every PEP is set to 1.");
			return null;
		}
		double mean = 0.0;
		for (double value : x) mean += value;
		mean /= n;
		double squares = 0.0;
		for (double value : x) squares += (value - mean) * (value - mean);
		double variance = squares / (n - 1);
		if (!(variance > 0) || !Double.isFinite(variance)) return null;

		double factor = Math.pow(n * 3.0 / 4.0, -1.0 / 5.0);
		double bandwidth = Math.sqrt(variance) * factor;
		double norm = 1.0 / (n * bandwidth * Math.sqrt(2.0 * Math.PI));
		double[] density = new double[n];
		for (int i = 0; i < n; i++) {
			double sum = 0.0;
			for (int j = 0; j < n; j++) {
				double u = (x[i] - x[j]) / bandwidth;
				sum += Math.exp(-0.5 * u * u);
			}
			density[i] = sum * norm;
		}
		return density;
	}

	/** Indices that sort {@code values} ascending; ties keep input order (numpy's mergesort). */
	private static Integer[] ascending(double[] values) {
		Integer[] order = new Integer[values.length];
		for (int i = 0; i < order.length; i++) order[i] = i;
		Arrays.sort(order, (a, b) -> Double.compare(values[a], values[b]));
		return order;
	}

	private static double clip(double value) {
		return Math.min(1.0, Math.max(0.0, value));
	}
}
