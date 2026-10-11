package org.searlelab.context.encyclopedia;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.washington.gs.maccoss.encyclopedia.algorithms.library.EncyclopediaTwoDecoyStrategy;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.PercolatorPeptide;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryEntry;

/** Joins Context's search selections to the existing feature id without adding numeric feature columns. */
public final class SearchPairSidecar {
	private static final String HEADER = "id\tLabel\tmodifiedSequence\tcharge\ttargetKey\tdecoyKey"
			+ "\trelationType\tmode\tmethod\teligibility\tcompetitionOutcome\tsourceLibrary\trun\tengineVersion";

	private record CandidateKey(String sequence, byte charge, boolean decoy) {}
	private record Relation(String targetKey, String decoyKey, String relationType,
			EncyclopediaTwoDecoyStrategy.Mode mode, String method, String eligibility) {}

	private final HashMap<CandidateKey, Relation> relations = new HashMap<>();
	private EncyclopediaTwoDecoyStrategy.Mode mode;

	public void record(EncyclopediaTwo.SearchSelection selection) {
		if (mode != null && mode != selection.mode()) throw new IllegalStateException("Mixed search decoy modes");
		mode = selection.mode();
		List<LibraryEntry> candidates = selection.candidates();
		if (candidates.isEmpty() || candidates.get(0) != selection.target()) {
			throw new IllegalArgumentException("Search selection must start with its target");
		}
		LibraryEntry target = selection.target();
		String targetKey = key(target);
		String decoyKey = candidates.size() > 1 ? key(candidates.get(1)) : "";
		String method = selection.mode() == EncyclopediaTwoDecoyStrategy.Mode.PREDICTED ? "JChronologer" : "shuffle";
		String relationType = selection.targetOnly() ? "SEARCH_TARGET_ONLY" : "SEARCH_TARGET_DECOY";
		String eligibility = selection.targetOnly() ? "TARGET_ONLY" : "PAIRED";
		Relation primary = new Relation(targetKey, decoyKey, relationType, selection.mode(), method, eligibility);
		add(target, primary);
		if (candidates.size() > 1) add(candidates.get(1), primary);
		for (int i = 2; i + 1 < candidates.size(); i += 2) {
			LibraryEntry extraTarget = candidates.get(i);
			LibraryEntry extraDecoy = candidates.get(i + 1);
			Relation extra = new Relation(key(extraTarget), key(extraDecoy), "SEARCH_EXTRA_SHUFFLE",
					selection.mode(), "shuffle", "PAIRED");
			add(extraTarget, extra);
			add(extraDecoy, extra);
		}
		if (candidates.size() > 2 && candidates.size() % 2 != 0) {
			throw new IllegalArgumentException("Unpaired extra search candidate");
		}
	}

	public EncyclopediaTwoDecoyStrategy.Mode mode() {
		return mode;
	}

	public void write(File featureFile, File sidecarFile, File sourceLibrary, File run) throws IOException {
		if (sidecarFile.exists()) throw new IOException("Search-pair sidecar already exists: " + sidecarFile);
		File temporary = File.createTempFile("context-pairs-", ".tmp", sidecarFile.getAbsoluteFile().getParentFile());
		try {
		try (BufferedReader reader = Files.newBufferedReader(featureFile.toPath(), StandardCharsets.UTF_8);
				BufferedWriter writer = Files.newBufferedWriter(temporary.toPath(), StandardCharsets.UTF_8)) {
			String header = reader.readLine();
			if (header == null) throw new IOException("Empty feature file: " + featureFile);
			String[] names = header.split("\t", -1);
			int idIndex = column(names, "id");
			int labelIndex = column(names, "Label");
			int sequenceIndex = column(names, "sequence");
			writer.write(HEADER);
			writer.newLine();
			String line;
			int rowNumber = 1;
			HashSet<String> seenIds = new HashSet<>();
			while ((line = reader.readLine()) != null) {
				rowNumber++;
				String[] cells = line.split("\t", -1);
				if (cells.length < names.length) throw new IOException("Truncated feature row " + rowNumber);
				String id = cells[idIndex];
				if (!seenIds.add(id)) throw new IOException("Duplicate feature id: " + id);
				int label;
				try {
					label = Integer.parseInt(cells[labelIndex]);
					if (label != 1 && label != -1) throw new IllegalArgumentException("Label must be 1 or -1");
				} catch (RuntimeException e) {
					throw new IOException("Invalid Label at feature row " + rowNumber, e);
				}
				String sequence;
				byte charge;
				boolean decoy;
				try {
					sequence = PercolatorPeptide.getPeptideSequence(id);
					charge = PercolatorPeptide.getCharge(id);
					decoy = PercolatorPeptide.isPSMIDDecoy(id);
				} catch (RuntimeException e) {
					throw new IOException("Invalid feature id at row " + rowNumber + ": " + id, e);
				}
				if (decoy != (label == -1) || !sequence.equals(unflank(cells[sequenceIndex]))) {
					throw new IOException("Feature id, sequence, and Label disagree at row " + rowNumber + ": " + id);
				}
				Relation relation = relations.get(new CandidateKey(sequence, charge, decoy));
				if (relation == null) throw new IOException("No search-pair relation for feature id: " + id);
				writer.write(id + "\t" + label + "\t" + sequence + "\t" + charge + "\t" + relation.targetKey()
						+ "\t" + relation.decoyKey() + "\t" + relation.relationType() + "\t" + relation.mode()
						+ "\t" + relation.method() + "\t" + relation.eligibility() + "\tWINNER\t"
						+ sourceLibrary.getAbsolutePath() + "\t" + run.getName() + "\t6.9.23");
				writer.newLine();
			}
			if (rowNumber == 1) throw new IOException("No scored feature rows in " + featureFile);
		}
			Files.move(temporary.toPath(), sidecarFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
		} finally {
			Files.deleteIfExists(temporary.toPath());
		}
	}

	public static void partition(File sourceSidecar, File outputSidecar, Set<String> selectedIds) throws IOException {
		HashSet<String> found = new HashSet<>();
		File temporary = File.createTempFile("context-partition-", ".tmp", outputSidecar.getAbsoluteFile().getParentFile());
		try {
		try (BufferedReader reader = Files.newBufferedReader(sourceSidecar.toPath(), StandardCharsets.UTF_8);
				BufferedWriter writer = Files.newBufferedWriter(temporary.toPath(), StandardCharsets.UTF_8)) {
			String header = reader.readLine();
			if (!HEADER.equals(header)) throw new IOException("Unexpected Context pair sidecar schema: " + sourceSidecar);
			writer.write(header);
			writer.newLine();
			String line;
			while ((line = reader.readLine()) != null) {
				int tab = line.indexOf('\t');
				if (tab < 0) throw new IOException("Malformed pair sidecar row: " + sourceSidecar);
				String id = line.substring(0, tab);
				if (selectedIds.contains(id)) {
					if (!found.add(id)) throw new IOException("Duplicate pair sidecar id: " + id);
					writer.write(line);
					writer.newLine();
				}
			}
		}
		if (!found.equals(selectedIds)) {
			HashSet<String> missing = new HashSet<>(selectedIds);
			missing.removeAll(found);
			throw new IOException("Missing partition pair metadata for " + missing.size() + " feature id(s)");
		}
			Files.move(temporary.toPath(), outputSidecar.toPath(), StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(temporary.toPath());
		}
	}

	public static Map<String, String> targetKeysByFeatureId(File sidecar) throws IOException {
		HashMap<String, String> targetKeys = new HashMap<>();
		try (BufferedReader reader = Files.newBufferedReader(sidecar.toPath(), StandardCharsets.UTF_8)) {
			String header = reader.readLine();
			if (!HEADER.equals(header)) throw new IOException("Unexpected Context pair sidecar schema: " + sidecar);
			String line;
			while ((line = reader.readLine()) != null) {
				String[] cells = line.split("\t", -1);
				validateRelation(cells, sidecar);
				if (targetKeys.putIfAbsent(cells[0], cells[4]) != null) {
					throw new IOException("Duplicate Context pair sidecar id: " + cells[0]);
				}
			}
		}
		return targetKeys;
	}

	/** Requires matching pair metadata for both direct confidence-engine inputs. */
	public static void requirePairMetadata(File background, File reference, boolean allowLegacy) throws IOException {
		File backgroundPairs = new File(background + ".pairs.tsv");
		File referencePairs = new File(reference + ".pairs.tsv");
		if (!backgroundPairs.isFile() || !referencePairs.isFile()) {
			if (!allowLegacy || backgroundPairs.exists() || referencePairs.exists()) {
				throw new IOException("Both split feature pair sidecars are required; use --allow-legacy-features true only for older inputs without either sidecar");
			}
			return;
		}
		requireMatches(background, backgroundPairs);
		requireMatches(reference, referencePairs);
	}

	/** Validates a split feature file before direct reuse by a confidence engine. */
	public static void requireMatches(File features, File sidecar) throws IOException {
		HashMap<String, String[]> metadata = new HashMap<>();
		try (BufferedReader reader = Files.newBufferedReader(sidecar.toPath(), StandardCharsets.UTF_8)) {
			if (!HEADER.equals(reader.readLine())) throw new IOException("Unexpected Context pair sidecar schema: " + sidecar);
			String line;
			while ((line = reader.readLine()) != null) {
				String[] cells = line.split("\t", -1);
				if (cells.length != HEADER.split("\t", -1).length
						|| !("1".equals(cells[1]) || "-1".equals(cells[1]))
						|| metadata.putIfAbsent(cells[0], cells) != null) {
					throw new IOException("Malformed or duplicate pair sidecar row: " + sidecar);
				}
				validateRelation(cells, sidecar);
			}
		}
		HashSet<String> seen = new HashSet<>();
		try (BufferedReader reader = Files.newBufferedReader(features.toPath(), StandardCharsets.UTF_8)) {
			String header = reader.readLine();
			if (header == null) throw new IOException("Empty feature file: " + features);
			String[] names = header.split("\t", -1);
			int idIndex = column(names, "id");
			int labelIndex = column(names, "Label");
			int sequenceIndex = column(names, "sequence");
			String line;
			while ((line = reader.readLine()) != null) {
				String[] cells = line.split("\t", -1);
				if (cells.length != names.length) throw new IOException("Malformed feature row in " + features);
				String id = cells[idIndex];
				String[] pair = metadata.get(id);
				try {
					if (!seen.add(id) || pair == null || !pair[1].equals(cells[labelIndex])
							|| !pair[2].equals(unflank(cells[sequenceIndex]))
							|| !pair[2].equals(PercolatorPeptide.getPeptideSequence(id))
							|| !pair[3].equals(Byte.toString(PercolatorPeptide.getCharge(id)))
							|| PercolatorPeptide.isPSMIDDecoy(id) != "-1".equals(pair[1])) {
						throw new IOException("Feature row and pair sidecar disagree for id: " + id);
					}
				} catch (RuntimeException malformedId) {
					throw new IOException("Malformed feature id: " + id, malformedId);
				}
			}
		}
		if (!seen.equals(metadata.keySet())) throw new IOException("Pair sidecar ids differ from feature file: " + features);
	}

	private static void validateRelation(String[] cells, File sidecar) throws IOException {
		if (cells.length != HEADER.split("\t", -1).length || !"WINNER".equals(cells[10])
				|| !("1".equals(cells[1]) || "-1".equals(cells[1]))) {
			throw new IOException("Invalid Context pair relation or winner outcome: " + sidecar);
		}
		String candidate = cells[2] + "|" + cells[3];
		String target = cells[4];
		String decoy = cells[5];
		if (!validKey(target, cells[3]) || ("1".equals(cells[1]) ? !candidate.equals(target)
				: !candidate.equals(decoy) || !validKey(decoy, cells[3]))) {
			throw new IOException("Feature candidate and Context pair relation disagree: " + sidecar);
		}
		boolean targetOnly = "SEARCH_TARGET_ONLY".equals(cells[6]);
		boolean paired = "SEARCH_TARGET_DECOY".equals(cells[6]) || "SEARCH_EXTRA_SHUFFLE".equals(cells[6]);
		boolean predicted = "PREDICTED".equals(cells[7]);
		boolean shuffled = "SHUFFLED".equals(cells[7]);
		if ((!targetOnly && !paired) || (targetOnly && (!decoy.isEmpty() || !"1".equals(cells[1])
				|| !"TARGET_ONLY".equals(cells[9])))
				|| (paired && (!validKey(decoy, cells[3]) || target.equals(decoy)
				|| !"PAIRED".equals(cells[9])))
				|| (!predicted && !shuffled)
				|| ("SEARCH_EXTRA_SHUFFLE".equals(cells[6]) && !"shuffle".equals(cells[8]))
				|| (!"SEARCH_EXTRA_SHUFFLE".equals(cells[6])
						&& !(predicted ? "JChronologer".equals(cells[8]) : "shuffle".equals(cells[8])))) {
			throw new IOException("Invalid Context pair relation: " + sidecar);
		}
	}

	private static boolean validKey(String key, String charge) {
		int separator = key.lastIndexOf('|');
		try {
			return separator > 0 && Byte.parseByte(charge) > 0 && key.substring(separator + 1).equals(charge);
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private void add(LibraryEntry entry, Relation relation) {
		CandidateKey candidate = new CandidateKey(entry.getPeptideModSeq(), entry.getPrecursorCharge(), entry.isDecoy());
		Relation existing = relations.putIfAbsent(candidate, relation);
		if (existing != null && !existing.equals(relation)) {
			throw new IllegalStateException("Ambiguous search-pair relation for " + key(entry));
		}
	}

	private static String key(LibraryEntry entry) {
		return entry.getPeptideModSeq() + "|" + entry.getPrecursorCharge();
	}

	private static int column(String[] names, String name) throws IOException {
		for (int i = 0; i < names.length; i++) if (name.equals(names[i])) return i;
		throw new IOException("Missing feature column: " + name);
	}

	private static String unflank(String sequence) {
		return sequence.trim().replaceFirst("^[A-Za-z-]?\\.", "").replaceFirst("\\.[A-Za-z-]?$", "");
	}
}
