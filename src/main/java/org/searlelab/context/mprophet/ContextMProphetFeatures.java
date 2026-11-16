package org.searlelab.context.mprophet;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.searlelab.context.datastructures.ContextFeatures;
import org.searlelab.context.datastructures.EncyclopediaFeatures;

import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.MProphetData;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.MProphetDataset;
import edu.washington.gs.maccoss.encyclopedia.utils.Logger;

/**
 * Context-owned mProphet input for the Context workflow (CTX018-025), replacing EncyclopeDIA's
 * {@code MProphetFeatureReader} there. It follows Context-MS:
 * <ol>
 * <li>{@link ContextFeatures#prepare}: background and reference headers must match; features that are constant on
 * the background (population SD below 1e-6) are dropped from both.</li>
 * <li>Features are every column except id (first), label (second), peptide and proteins (last two) and
 * {@code ScanNr}/{@code scan} (any case), minus Context-MS's EncyclopeDIA metadata columns. So {@code ExpMass} is a
 * feature, and a non-numeric column (such as a {@code protein} column in the middle) reads as zero and is pruned.</li>
 * <li>Starting score, used only when the seed model is unusable: the last of Context-MS's preferred names present,
 * else {@link #AUTO_STARTING_SCORE}, which tells the Context-only training to pick the best-separating feature on
 * each training fold.</li>
 * <li>Rows labelled 1 are targets, -1 decoys; other labels are skipped with a warning. A non-numeric or NaN value
 * reads as zero.</li>
 * </ol>
 * The background and reference datasets share one feature list, so a model trained on one applies to the other.
 */
public final class ContextMProphetFeatures {
	/** Context-MS io.ENCYCLOPEDIA_METADATA_COLS (d207f47). */
	static final Set<String> METADATA = Set.of("TD", "pepLength", "charge1", "charge2", "charge3", "charge4",
			"precursorMass", "RTinMin", "midTime", "numberOfMatchingPeaksAboveThreshold", "primary");
	/** Context-MS mprophet.STARTING_SCORE_PREFERENCE; the last one present wins. */
	static final List<String> STARTING_SCORES = List.of("peakZScore", "peakBGScore", "peakBGSScore", "xTandem", "scribe",
			"xCorrLib", "xCorrModel", "main_var_Intensity");
	public static final int AUTO_STARTING_SCORE = -1;

	/** EncyclopeDIA 6.9.23 MProphetFeatureReader rules, kept only to show the Context reader reproduces it. */
	static final Set<String> LEGACY_EXCLUDED = Set.of("id", "Label", "TD", "ScanNr", "sequence", "Proteins", "protein",
			"pepLength", "charge1", "charge2", "charge3", "charge4", "precursorMass", "ExpMass", "RTinMin", "midTime",
			"numberOfMatchingPeaksAboveThreshold", "primary");
	static final List<String> LEGACY_STARTING_SCORES = List.of("peakZScore", "peakBGScore", "peakBGSScore", "xTandem",
			"scribe", "xCorrLib", "xCorrModel");

	enum Selection {
		CONTEXT_MS, LEGACY
	}

	public record Prepared(MProphetDataset background, MProphetDataset reference, File prunedBackground,
			File prunedReference, List<String> droppedFeatures) {
	}

	private ContextMProphetFeatures() {
	}

	public static Prepared prepare(File backgroundFeatures, File referenceFeatures, File workingDirectory, String prefix)
			throws IOException {
		ContextFeatures prepared = ContextFeatures.prepare(backgroundFeatures, referenceFeatures, workingDirectory, prefix);
		EncyclopediaFeatures background = EncyclopediaFeatures.read(prepared.getBackground());
		EncyclopediaFeatures reference = prepared.getReferenceTable();
		MProphetDataset backgroundSet = dataset(background, Selection.CONTEXT_MS);
		MProphetDataset referenceSet = dataset(reference, backgroundSet.getFeatureNames(), backgroundSet.getStartingScoreIndex());
		return new Prepared(backgroundSet, referenceSet, prepared.getBackground(), prepared.getReference(),
				prepared.getDroppedFeatures());
	}

	static MProphetDataset dataset(EncyclopediaFeatures table, Selection selection) throws IOException {
		String[] header = table.getHeader();
		List<String> features = new ArrayList<>();
		if (selection == Selection.CONTEXT_MS) {
			for (int index : table.getFeatureIndices()) {
				if (!METADATA.contains(header[index])) features.add(header[index]);
			}
		} else {
			for (String column : header) {
				if (!LEGACY_EXCLUDED.contains(column)) features.add(column);
			}
		}
		return dataset(table, features, startingScore(header, features, selection));
	}

	static int startingScore(String[] header, List<String> features, Selection selection) throws IOException {
		if (selection == Selection.LEGACY) {
			// EncyclopeDIA keeps the header position of the last preferred name and later reads it as a feature
			// position; reproduced here as-is so the two readers can be compared.
			int chosen = -1;
			List<String> columns = Arrays.asList(header);
			for (String name : LEGACY_STARTING_SCORES) {
				if (columns.contains(name)) chosen = columns.indexOf(name);
			}
			if (chosen < 0) throw new IOException("No starting score feature (" + LEGACY_STARTING_SCORES + ")");
			return chosen;
		}
		int chosen = AUTO_STARTING_SCORE;
		for (String name : STARTING_SCORES) {
			if (features.contains(name)) chosen = features.indexOf(name);
		}
		return chosen;
	}

	static MProphetDataset dataset(EncyclopediaFeatures table, List<String> features, int startingScore) throws IOException {
		String[] header = table.getHeader();
		List<String> columns = Arrays.asList(header);
		int[] indices = new int[features.size()];
		for (int i = 0; i < indices.length; i++) {
			indices[i] = columns.indexOf(features.get(i));
			if (indices[i] < 0) throw new IOException("The feature table lacks model feature [" + features.get(i) + "]");
		}
		int peptide = table.getPeptideIndex();
		int protein = table.getProteinIndex();

		ArrayList<MProphetData> rows = new ArrayList<>();
		int skipped = 0;
		for (String[] row : table.getRows()) {
			int label = parseLabel(row[EncyclopediaFeatures.LABEL_INDEX]);
			if (label != 1 && label != -1) {
				skipped++;
				continue;
			}
			float[] values = new float[indices.length];
			for (int i = 0; i < indices.length; i++) values[i] = parseFloat(row[indices[i]]);
			rows.add(new MProphetData(row[EncyclopediaFeatures.ID_INDEX], legacySequence(row[peptide]), row[protein],
					values, label == -1));
		}
		if (skipped > 0) {
			Logger.errorLine("Warning: skipped " + skipped + " row(s) whose label is neither 1 nor -1.");
		}
		return new MProphetDataset(new ArrayList<>(features), startingScore, rows);
	}

	/** EncyclopeDIA's MProphetData sequence: the peptide column without Percolator's flanking "-." and ".-". */
	static String legacySequence(String peptide) {
		int first = peptide.indexOf('.');
		int last = peptide.lastIndexOf('.');
		return first >= 0 && last > first ? peptide.substring(first + 1, last) : peptide;
	}

	private static int parseLabel(String value) {
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException nfe) {
			return 0;
		}
	}

	/** As Context-MS's numeric_features: non-numeric and NaN read as zero. */
	private static float parseFloat(String value) {
		try {
			float parsed = Float.parseFloat(value.trim());
			return Float.isNaN(parsed) ? 0f : parsed;
		} catch (NumberFormatException nfe) {
			return 0f;
		}
	}
}
