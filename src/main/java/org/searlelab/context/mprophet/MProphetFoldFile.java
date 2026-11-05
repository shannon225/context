package org.searlelab.context.mprophet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.MProphetData;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.MProphetDataset;

/**
 * Diagnostic fold file for Context mProphet training (CTX018-024, option B): the exact training/testing split
 * Context-MS used in each iteration, as exported by {@code docs/validation/ctx018_compare.py run-context-ms
 * --export-folds}. Columns: {@code iteration} (0-based), {@code fold} (0 = training, 1 = testing), {@code position}
 * (order within the fold), {@code id} (background feature id). Order within a fold is kept, because the 10x decoy cap
 * takes the first decoys of the training fold. Every background row must appear exactly once per iteration.
 */
public final class MProphetFoldFile {
	private static final String HEADER = "iteration\tfold\tposition\tid";

	private MProphetFoldFile() {
	}

	/** One {training, testing} pair per iteration, built from {@code background}'s rows. */
	public static List<MProphetDataset[]> read(File file, MProphetDataset background, int iterations) throws IOException {
		Map<String, MProphetData> byId = new HashMap<>();
		for (MProphetData row : background.allData()) {
			if (byId.put(row.getId(), row) != null) {
				throw new IOException("Fold file: background id [" + row.getId() + "] is not unique, so folds cannot be joined by id");
			}
		}

		List<List<TreeMap<Integer, MProphetData>>> plan = new ArrayList<>();
		List<Set<String>> seen = new ArrayList<>();
		for (int i = 0; i < iterations; i++) {
			plan.add(List.of(new TreeMap<>(), new TreeMap<>()));
			seen.add(new HashSet<>());
		}

		try (BufferedReader in = new BufferedReader(new FileReader(file))) {
			String header = in.readLine();
			if (!HEADER.equals(header)) {
				throw new IOException("Fold file " + file.getName() + " must start with [" + HEADER.replace("\t", " ") + "], not [" + header + "]");
			}
			String line;
			int lineNumber = 1;
			while ((line = in.readLine()) != null) {
				lineNumber++;
				if (line.isBlank()) continue;
				String[] fields = line.split("\t", -1);
				if (fields.length != 4) throw new IOException("Fold file line " + lineNumber + ": expected 4 columns");
				int iteration = integer(fields[0], "iteration", lineNumber);
				int fold = integer(fields[1], "fold", lineNumber);
				int position = integer(fields[2], "position", lineNumber);
				if (iteration < 0 || iteration >= iterations) {
					throw new IOException("Fold file line " + lineNumber + ": iteration " + iteration + " is outside 0.."
							+ (iterations - 1) + "; training uses " + iterations + " iterations");
				}
				if (fold != 0 && fold != 1) throw new IOException("Fold file line " + lineNumber + ": fold must be 0 or 1");
				MProphetData row = byId.get(fields[3]);
				if (row == null) throw new IOException("Fold file line " + lineNumber + ": id [" + fields[3] + "] is not in the background");
				if (!seen.get(iteration).add(fields[3])) {
					throw new IOException("Fold file line " + lineNumber + ": id [" + fields[3] + "] appears twice in iteration " + iteration);
				}
				if (plan.get(iteration).get(fold).put(position, row) != null) {
					throw new IOException("Fold file line " + lineNumber + ": position " + position + " repeats in iteration "
							+ iteration + ", fold " + fold);
				}
			}
		}

		List<MProphetDataset[]> folds = new ArrayList<>();
		for (int i = 0; i < iterations; i++) {
			final int iteration = i;
			if (seen.get(iteration).isEmpty()) {
				throw new IOException("Fold file: iteration " + iteration + " is missing; training uses " + iterations + " iterations");
			}
			if (seen.get(iteration).size() != byId.size()) {
				String example = byId.keySet().stream().filter(id -> !seen.get(iteration).contains(id)).findFirst().orElse("");
				throw new IOException("Fold file: iteration " + iteration + " covers " + seen.get(iteration).size() + " of "
						+ byId.size() + " background rows; missing ids include [" + example + "]");
			}
			MProphetDataset[] pair = new MProphetDataset[2];
			for (int fold = 0; fold < 2; fold++) {
				TreeMap<Integer, MProphetData> members = plan.get(iteration).get(fold);
				if (!members.isEmpty() && (members.firstKey() != 0 || members.lastKey() != members.size() - 1)) {
					throw new IOException("Fold file: positions in iteration " + iteration + ", fold " + fold + " are not 0.."
							+ (members.size() - 1));
				}
				pair[fold] = new MProphetDataset(new ArrayList<>(background.getFeatureNames()),
						background.getStartingScoreIndex(), new ArrayList<>(members.values()));
			}
			folds.add(pair);
		}
		return folds;
	}

	private static int integer(String value, String column, int lineNumber) throws IOException {
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException nfe) {
			throw new IOException("Fold file line " + lineNumber + ": " + column + " [" + value + "] is not an integer", nfe);
		}
	}
}
