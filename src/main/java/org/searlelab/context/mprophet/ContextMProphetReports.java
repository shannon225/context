package org.searlelab.context.mprophet;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.searlelab.context.datastructures.EncyclopediaFeatures;
import org.searlelab.context.io.DirectoryOptions;

import edu.washington.gs.maccoss.encyclopedia.utils.math.LinearDiscriminantAnalysis;

/**
 * Context-MS-format outputs of the Context mProphet workflow (CTX018-007, CTX018-023), written next to the existing
 * outputs, which are unchanged. The background-trained LDA scores every reference row without retraining; q-values
 * and PEPs come from {@link MProphetConfidence}, once over all reference rows (PSM level) and again over the
 * best-scoring row per peptide (peptide level), so peptide-level confidence is recomputed rather than carried over.
 * <ul>
 * <li>{@code P.rescored_features.txt}: every reference row, target and decoy, plus {@code score}.</li>
 * <li>{@code P.psm.reference.txt}, {@code P.peptide.reference.txt}: targets with score, q-value and PEP.</li>
 * <li>{@code model/P.weights.txt}: the model as feature/weight rows plus {@code __bias__}.</li>
 * </ul>
 * Peptide identity is the peptide column (second to last) verbatim; ties in score keep reference-file order.
 */
public final class ContextMProphetReports {
	static final String SCORE_COLUMN = "score";
	static final String Q_VALUE_COLUMN = "q-value";
	static final String PEP_COLUMN = "posterior_error_prob";
	private static final String TARGET_LABEL = "1";
	private static final String DECOY_LABEL = "-1";

	/** Row counts and passing counts for logging. */
	public record Summary(int psmTargets, int peptideTargets, int peptidesAtQ, int peptidesAtPEP) {
	}

	private ContextMProphetReports() {
	}

	public static List<File> outputs(File engineDirectory, String prefix) {
		return List.of(new File(engineDirectory, prefix + ".rescored_features.txt"),
				new File(engineDirectory, prefix + ".psm.reference.txt"),
				new File(engineDirectory, prefix + ".peptide.reference.txt"),
				new File(new File(engineDirectory, DirectoryOptions.MODEL_DIRECTORY), prefix + ".weights.txt"));
	}

	public static Summary write(File referenceFeatures, LinearDiscriminantAnalysis model, List<String> featureNames,
			File engineDirectory, String prefix, float threshold) throws IOException {
		EncyclopediaFeatures table = EncyclopediaFeatures.read(referenceFeatures);
		String[] header = table.getHeader();
		int[] featureIndices = featureIndices(header, featureNames, model.getCoefficients().length);

		List<String[]> rows = table.getRows();
		double[] scores = new double[rows.size()];
		boolean[] decoy = new boolean[rows.size()];
		for (int i = 0; i < rows.size(); i++) {
			scores[i] = score(model, table.getFeatureValues(rows.get(i), featureIndices));
			decoy[i] = table.isDecoy(rows.get(i));
		}

		List<File> files = outputs(engineDirectory, prefix);
		writeRescored(files.get(0), header, rows, scores, decoy);

		Integer[] all = new Integer[rows.size()];
		for (int i = 0; i < all.length; i++) all[i] = i;
		int psmTargets = writeReport(files.get(1), table, Arrays.asList(all), scores, decoy, threshold)[0];
		int[] peptide = writeReport(files.get(2), table, bestPerPeptide(table, scores), scores, decoy, threshold);

		writeWeights(files.get(3), featureNames, model);
		return new Summary(psmTargets, peptide[0], peptide[1], peptide[2]);
	}

	private static int[] featureIndices(String[] header, List<String> featureNames, int coefficients) throws IOException {
		if (featureNames.size() != coefficients) {
			throw new IOException("The mProphet model has " + coefficients + " coefficients but names "
					+ featureNames.size() + " features");
		}
		List<String> columns = Arrays.asList(header);
		int[] indices = new int[featureNames.size()];
		List<String> missing = new ArrayList<>();
		for (int i = 0; i < indices.length; i++) {
			indices[i] = columns.indexOf(featureNames.get(i));
			if (indices[i] < 0) missing.add(featureNames.get(i));
		}
		if (!missing.isEmpty()) {
			throw new IOException("The reference features lack model features " + missing);
		}
		return indices;
	}

	private static double score(LinearDiscriminantAnalysis model, double[] values) {
		double[] coefficients = model.getCoefficients();
		double score = 0.0;
		for (int i = 0; i < coefficients.length; i++) score += values[i] * coefficients[i];
		return score + model.getConstant();
	}

	/** The best-scoring row per peptide, targets and decoys together, highest score first. */
	static List<Integer> bestPerPeptide(EncyclopediaFeatures table, double[] scores) {
		Integer[] order = new Integer[scores.length];
		for (int i = 0; i < order.length; i++) order[i] = i;
		Arrays.sort(order, Comparator.comparingDouble((Integer i) -> scores[i]).reversed());
		Set<String> seen = new HashSet<>();
		List<Integer> best = new ArrayList<>();
		for (Integer index : order) {
			if (seen.add(table.getRows().get(index)[table.getPeptideIndex()])) best.add(index);
		}
		return best;
	}

	/** Writes the targets among {@code members}; returns {targets, at q <= threshold, at PEP <= threshold}. */
	private static int[] writeReport(File file, EncyclopediaFeatures table, List<Integer> members, double[] scores,
			boolean[] decoy, float threshold) throws IOException {
		List<Integer> targets = new ArrayList<>();
		List<Double> decoyScores = new ArrayList<>();
		for (Integer index : members) {
			if (decoy[index]) decoyScores.add(scores[index]);
			else targets.add(index);
		}
		double[] targetScores = targets.stream().mapToDouble(i -> scores[i]).toArray();
		MProphetConfidence.Confidence confidence = MProphetConfidence.qAndPep(targetScores,
				decoyScores.stream().mapToDouble(Double::doubleValue).toArray());

		Integer[] byQ = new Integer[targets.size()];
		for (int i = 0; i < byQ.length; i++) byQ[i] = i;
		Arrays.sort(byQ, Comparator.comparingDouble((Integer i) -> confidence.q()[i]));

		String[] header = table.getHeader();
		int scan = table.getScanIndex();
		double limit = Double.parseDouble(Float.toString(threshold));
		int atQ = 0;
		int atPEP = 0;
		try (BufferedWriter out = new BufferedWriter(new FileWriter(file))) {
			out.write(String.join("\t", header[EncyclopediaFeatures.ID_INDEX], scan < 0 ? "ScanNr" : header[scan],
					SCORE_COLUMN, Q_VALUE_COLUMN, PEP_COLUMN, header[table.getPeptideIndex()],
					header[table.getProteinIndex()]));
			out.write("\n");
			for (Integer i : byQ) {
				String[] row = table.getRows().get(targets.get(i));
				double q = confidence.q()[i];
				double pep = confidence.pep()[i];
				if (q <= limit) atQ++;
				if (pep <= limit) atPEP++;
				out.write(String.join("\t", row[EncyclopediaFeatures.ID_INDEX], scan < 0 ? "" : row[scan],
						Double.toString(targetScores[i]), Double.toString(q), Double.toString(pep),
						row[table.getPeptideIndex()], row[table.getProteinIndex()]));
				out.write("\n");
			}
		}
		return new int[] { targets.size(), atQ, atPEP };
	}

	private static void writeRescored(File file, String[] header, List<String[]> rows, double[] scores, boolean[] decoy)
			throws IOException {
		try (BufferedWriter out = new BufferedWriter(new FileWriter(file))) {
			out.write(String.join("\t", header) + "\t" + SCORE_COLUMN + "\n");
			for (int i = 0; i < rows.size(); i++) {
				String[] row = rows.get(i).clone();
				row[EncyclopediaFeatures.LABEL_INDEX] = decoy[i] ? DECOY_LABEL : TARGET_LABEL;
				out.write(String.join("\t", row) + "\t" + scores[i] + "\n");
			}
		}
	}

	/** Same layout as PercolatorWeights.writeAveraged: feature/weight rows, then __bias__. */
	private static void writeWeights(File file, List<String> featureNames, LinearDiscriminantAnalysis model)
			throws IOException {
		Files.createDirectories(file.getParentFile().toPath());
		try (BufferedWriter out = new BufferedWriter(new FileWriter(file))) {
			out.write("feature\tweight\n");
			for (int i = 0; i < featureNames.size(); i++) {
				out.write(featureNames.get(i) + "\t" + model.getCoefficients()[i] + "\n");
			}
			out.write("__bias__\t" + model.getConstant() + "\n");
		}
	}
}
