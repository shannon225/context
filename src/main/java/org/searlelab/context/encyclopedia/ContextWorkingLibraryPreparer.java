package org.searlelab.context.encyclopedia;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import edu.washington.gs.maccoss.encyclopedia.algorithms.prediction.AminoAcidEncoding;
import edu.washington.gs.maccoss.encyclopedia.algorithms.prediction.NonstandardAminoAcidException;
import edu.washington.gs.maccoss.encyclopedia.datastructures.AminoAcidConstants;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryEntry;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryPrecursorMetadata;
import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchParameters;
import edu.washington.gs.maccoss.encyclopedia.filereaders.LibraryFile;
import edu.washington.gs.maccoss.encyclopedia.filewriters.web.DecoyPeptideGenerator;
import edu.washington.gs.maccoss.encyclopedia.filewriters.web.JChronologerLibraryPredictionClient;
import edu.washington.gs.maccoss.encyclopedia.filewriters.web.JChronologerLibraryPredictionClient.ScribeTwoPrecursorPredictionRequest;
import edu.washington.gs.maccoss.encyclopedia.filewriters.web.JChronologerLibraryPredictionClient.ScribeTwoPredictionSession;
import edu.washington.gs.maccoss.encyclopedia.utils.massspec.PeptideUtils;

/** Builds a run-local predicted library without changing Encyclopedia's preparation code. */
public final class ContextWorkingLibraryPreparer {
	private static final int PAIRS_PER_BATCH = JChronologerLibraryPredictionClient.JCHRONOLOGER_CARTOGRAPHER_BATCH_SIZE / 2;

	private ContextWorkingLibraryPreparer() {}

	public record Settings(int nce, byte defaultCharge, boolean adjustNceForDia) {
		public Settings {
			if (nce <= 0 || defaultCharge <= 0) throw new IllegalArgumentException("Prediction NCE and default charge must be positive");
		}
	}

	public record Assignment(String targetKey, String decoyKey, String status, String reason) {}

	public record Result(LibraryFile library, List<Assignment> assignments, long retainedTargets,
			long retainedDecoys, File libraryFile, File assignmentFile) implements AutoCloseable {
		@Override
		public void close() throws IOException {
			library.close();
		}
	}

	@FunctionalInterface
	interface Predictor {
		ArrayList<LibraryEntry> predict(List<ScribeTwoPrecursorPredictionRequest> requests) throws RuntimeException;
	}

	public static Result prepare(LibraryFile source, SearchParameters parameters, Settings settings,
			File outputLibrary, File assignmentFile) throws IOException, SQLException {
		if (outputLibrary.exists() || assignmentFile.exists()) {
			throw new IOException("Working-library output already exists; choose a fresh run path: " + outputLibrary);
		}
		try (ScribeTwoPredictionSession session = JChronologerLibraryPredictionClient.createScribeTwoPredictionSession()) {
			return prepare(source, parameters, settings, outputLibrary, assignmentFile,
					requests -> session.predictPrecursors(requests, settings.nce(), settings.defaultCharge(),
							settings.adjustNceForDia(), parameters));
		}
	}

	static Result prepare(LibraryFile source, SearchParameters parameters, Settings settings,
			File outputLibrary, File assignmentFile, Predictor predictor) throws IOException, SQLException {
		if (outputLibrary.exists() || assignmentFile.exists()) {
			throw new IOException("Working-library output already exists; choose a fresh run path: " + outputLibrary);
		}
		ArrayList<LibraryPrecursorMetadata> targets = source.getUniqueTargetPrecursorMetadata();
		if (targets.isEmpty()) throw new IOException("Source library has no target precursors");
		AminoAcidConstants constants = parameters.getAAConstants();
		HashSet<String> targetPeptides = new HashSet<>();
		for (LibraryPrecursorMetadata target : targets) targetPeptides.add(normalized(target.getPeptideModSeq(), constants));
		HashMap<Byte, HashSet<String>> usedDecoys = new HashMap<>();
		ArrayList<Assignment> assignments = new ArrayList<>();
		LibraryFile working = new LibraryFile();
		boolean completeExclusionReport = false;
		try {
			working.openFile();
			working.createDecoyEntriesTable();
			long retainedTargets = 0;
			long retainedDecoys = 0;
			for (int start = 0; start < targets.size(); start += PAIRS_PER_BATCH) {
				List<LibraryPrecursorMetadata> batch = targets.subList(start, Math.min(start + PAIRS_PER_BATCH, targets.size()));
				LinkedHashMap<String, LibraryPrecursorMetadata> requestedTargets = new LinkedHashMap<>();
				HashMap<String, String> decoyByTarget = new HashMap<>();
				ArrayList<ScribeTwoPrecursorPredictionRequest> requests = new ArrayList<>();
				for (LibraryPrecursorMetadata target : batch) {
					String key = key(target.getPeptideModSeq(), target.getPrecursorCharge(), constants);
					if (!supported(target)) {
						assignments.add(new Assignment(key, "", "EXCLUDED", "unsupported target length or charge"));
						continue;
					}
					requestedTargets.put(key, target);
					requests.add(request(target.getPeptideModSeq(), target.getPrecursorCharge(), constants));
					HashSet<String> unavailable = new HashSet<>(targetPeptides);
					unavailable.addAll(usedDecoys.computeIfAbsent(target.getPrecursorCharge(), ignored -> new HashSet<>()));
					Optional<String> decoy = makeDecoy(target.getPeptideModSeq(), unavailable, constants);
					if (decoy.isPresent()) {
						String decoyKey = key(decoy.get(), target.getPrecursorCharge(), constants);
						if (supported(decoy.get(), target.getPrecursorCharge())) {
							usedDecoys.get(target.getPrecursorCharge()).add(normalized(decoy.get(), constants));
							decoyByTarget.put(key, decoy.get());
							requests.add(request(decoy.get(), target.getPrecursorCharge(), constants));
						}
					}
				}
				HashMap<String, LibraryEntry> predictions = new HashMap<>();
				HashMap<String, String> failures = new HashMap<>();
				predictWithIsolation(requests, predictor, constants, predictions, failures);
				ArrayList<LibraryEntry> targetEntries = new ArrayList<>();
				ArrayList<LibraryEntry> decoyEntries = new ArrayList<>();
				HashMap<String, String> targetByDecoyKey = new HashMap<>();
				for (Map.Entry<String, LibraryPrecursorMetadata> item : requestedTargets.entrySet()) {
					String targetKey = item.getKey();
					LibraryPrecursorMetadata target = item.getValue();
					LibraryEntry predictedTarget = predictions.get(targetKey);
					String decoySequence = decoyByTarget.get(targetKey);
					String decoyKey = decoySequence == null ? "" : key(decoySequence, target.getPrecursorCharge(), constants);
					if (predictedTarget == null) {
						assignments.add(new Assignment(targetKey, decoyKey, "EXCLUDED",
								failures.getOrDefault(targetKey, "target spectrum unavailable: no prediction returned")));
						continue;
					}
					LibraryEntry targetEntry = copy(predictedTarget, target.getPeptideModSeq(),
							new HashSet<>(target.getAccessions()), constants);
					targetEntries.add(targetEntry);
					retainedTargets++;
					LibraryEntry predictedDecoy = predictions.get(decoyKey);
					if (predictedDecoy == null) {
						assignments.add(new Assignment(targetKey, decoyKey, "TARGET_ONLY",
								decoySequence == null ? "unique supported decoy unavailable"
										: failures.getOrDefault(decoyKey, "decoy spectrum unavailable: no prediction returned")));
						continue;
					}
					HashSet<String> decoyAccessions = new HashSet<>();
					for (String accession : target.getAccessions()) decoyAccessions.add(LibraryEntry.DECOY_STRING + accession);
					LibraryEntry decoyEntry = copy(predictedDecoy, decoySequence, decoyAccessions, constants);
					decoyEntries.add(decoyEntry);
					targetByDecoyKey.put(decoyKey, target.getPeptideModSeq());
					retainedDecoys++;
					assignments.add(new Assignment(targetKey, decoyKey, "PAIRED", "predicted spectra available"));
				}
				working.addEntries(targetEntries, false);
				working.addProteinsFromEntries(targetEntries);
				working.addDecoyEntriesToExistingTable(decoyEntries, targetByDecoyKey, false);
				working.addDecoyProteinsFromEntries(decoyEntries);
			}
			if (retainedTargets == 0) {
				writeAssignments(assignmentFile, assignments, settings, source.getFile());
				completeExclusionReport = true;
				throw new IOException("No target spectra could be predicted; see " + assignmentFile);
			}
			if (retainedDecoys > 0) working.setHasMatchedDecoySpectra(true);
			working.createIndices();
			if (working.countUniqueTargetPrecursors() != retainedTargets || working.countUniqueDecoyPrecursors() != retainedDecoys) {
				throw new IOException("Working-library precursor counts differ from retained prediction counts");
			}
			working.saveAsFile(outputLibrary);
			writeAssignments(assignmentFile, assignments, settings, source.getFile());
			return new Result(working, List.copyOf(assignments), retainedTargets, retainedDecoys, outputLibrary, assignmentFile);
		} catch (IOException | SQLException | RuntimeException e) {
			try {
				working.close();
				Files.deleteIfExists(outputLibrary.toPath());
				if (!completeExclusionReport) Files.deleteIfExists(assignmentFile.toPath());
			} catch (IOException cleanupFailure) {
				e.addSuppressed(cleanupFailure);
			}
			throw e;
		}
	}

	private static void predictWithIsolation(List<ScribeTwoPrecursorPredictionRequest> requests, Predictor predictor,
			AminoAcidConstants constants, HashMap<String, LibraryEntry> predictions, HashMap<String, String> failures) {
		if (requests.isEmpty()) return;
		ArrayList<LibraryEntry> returned;
		try {
			returned = predictor.predict(requests);
		} catch (RuntimeException failure) {
			if (requests.size() == 1) {
				ScribeTwoPrecursorPredictionRequest request = requests.get(0);
				failures.put(key(request.getPeptideModSeq(), request.getPrecursorCharge(), constants),
						"prediction failed: " + failure.getClass().getSimpleName() + " " + safe(failure.getMessage()));
				return;
			}
			int middle = requests.size() / 2;
			predictWithIsolation(requests.subList(0, middle), predictor, constants, predictions, failures);
			predictWithIsolation(requests.subList(middle, requests.size()), predictor, constants, predictions, failures);
			return;
		}
		HashSet<String> requestedKeys = new HashSet<>();
		for (ScribeTwoPrecursorPredictionRequest request : requests) {
			requestedKeys.add(key(request.getPeptideModSeq(), request.getPrecursorCharge(), constants));
		}
		for (LibraryEntry entry : returned) {
			String key = key(entry.getPeptideModSeq(), entry.getPrecursorCharge(), constants);
			if (!requestedKeys.contains(key)) throw new IllegalStateException("Unexpected prediction for " + key);
			LibraryEntry previous = predictions.putIfAbsent(key, entry);
			if (previous != null) throw new IllegalStateException("Duplicate prediction for " + key);
		}
	}

	private static void writeAssignments(File file, List<Assignment> assignments, Settings settings, File sourceFile) throws IOException {
		try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
			writer.write("targetKey\tdecoyKey\tstatus\treason\tpredictionNCE\tdefaultCharge\tadjustNCEForDIA\tsourceLibrary\tengineVersion");
			writer.newLine();
			for (Assignment assignment : assignments) {
				writer.write(assignment.targetKey() + "\t" + assignment.decoyKey() + "\t" + assignment.status() + "\t"
						+ assignment.reason() + "\t" + settings.nce() + "\t" + settings.defaultCharge() + "\t"
						+ settings.adjustNceForDia() + "\t" + (sourceFile == null ? "in-memory" : sourceFile.getAbsolutePath())
						+ "\t6.9.23");
				writer.newLine();
			}
		}
	}

	private static String safe(String text) {
		return text == null ? "" : text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
	}

	private static LibraryEntry copy(LibraryEntry predicted, String sequence, HashSet<String> accessions,
			AminoAcidConstants constants) {
		return new LibraryEntry(predicted.getSource(), accessions, predicted.getPrecursorMZ(), predicted.getPrecursorCharge(),
				sequence, 1, predicted.getRetentionTime(), predicted.getScore(), predicted.getMassArray(),
				predicted.getIntensityArray(), predicted.getIonMobility(), constants);
	}

	private static Optional<String> makeDecoy(String sequence, HashSet<String> unavailable, AminoAcidConstants constants) {
		try {
			return DecoyPeptideGenerator.getUniqueDecoyPeptideModSeq(sequence,
					AminoAcidEncoding.reverse(AminoAcidEncoding.getAAs(sequence, constants).clone()), unavailable, constants);
		} catch (NonstandardAminoAcidException e) {
			return Optional.empty();
		}
	}

	private static boolean supported(LibraryPrecursorMetadata target) {
		return supported(target.getPeptideModSeq(), target.getPrecursorCharge());
	}

	private static boolean supported(String sequence, byte charge) {
		return charge >= JChronologerLibraryPredictionClient.MIN_SUPPORTED_CHARGE
				&& charge <= JChronologerLibraryPredictionClient.MAX_SUPPORTED_CHARGE
				&& JChronologerLibraryPredictionClient.isSupportedPeptideLength(PeptideUtils.getPeptideSeq(sequence).length());
	}

	private static ScribeTwoPrecursorPredictionRequest request(String sequence, byte charge, AminoAcidConstants constants) {
		return new ScribeTwoPrecursorPredictionRequest(PeptideUtils.getCorrectedMasses(sequence, constants), charge);
	}

	private static String normalized(String sequence, AminoAcidConstants constants) {
		return DecoyPeptideGenerator.getILNormalizedPeptideKey(PeptideUtils.getCorrectedMasses(sequence, constants));
	}

	private static String key(String sequence, byte charge, AminoAcidConstants constants) {
		return PeptideUtils.getCorrectedMasses(sequence, constants) + "|" + charge;
	}
}
