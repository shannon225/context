package org.searlelab.context.encyclopedia;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import edu.washington.gs.maccoss.encyclopedia.datastructures.AminoAcidConstants;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryEntry;
import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchParameters;
import edu.washington.gs.maccoss.encyclopedia.filereaders.LibraryFile;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;
import edu.washington.gs.maccoss.encyclopedia.utils.massspec.PeptideUtils;
import org.searlelab.msrawjava.model.Range;

public class ContextWorkingLibraryPreparerTest {
	@Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void keepsTargetWhenPredictedDecoyIsMissing() throws Exception {
		SearchParameters parameters = SearchParameterParser.getDefaultParametersObject();
		AminoAcidConstants constants = parameters.getAAConstants();
		LibraryFile source = source("PEPTIDEK", constants);
		try {
			File libraryFile = new File(temporaryFolder.getRoot(), "working.elib");
			File assignmentsFile = new File(temporaryFolder.getRoot(), "assignments.tsv");
			try (ContextWorkingLibraryPreparer.Result result = ContextWorkingLibraryPreparer.prepare(source, parameters,
					new ContextWorkingLibraryPreparer.Settings(27, (byte)2, true), libraryFile, assignmentsFile,
					requests -> {
						ArrayList<LibraryEntry> predictions = new ArrayList<>();
						for (var request : requests) {
							if (request.getPeptideModSeq().equals("PEPTIDEK")) {
								predictions.add(entry("PEPTIDEK", request.getPrecursorCharge(), constants));
							}
						}
						return predictions;
					})) {
				assertEquals(1, result.retainedTargets());
				assertEquals(0, result.retainedDecoys());
				assertEquals("TARGET_ONLY", result.assignments().get(0).status());
				assertEquals(1, result.library().countUniqueTargetPrecursors());
				assertEquals(0, result.library().countUniqueDecoyPrecursors());
				assertTrue(Files.readString(assignmentsFile.toPath()).contains("decoy spectrum unavailable"));
				assertTrue(libraryFile.isFile());
			}
		} finally {
			source.close();
		}
	}

	@Test
	public void excludesTargetWithUnavailableSpectrum() throws Exception {
		SearchParameters parameters = SearchParameterParser.getDefaultParametersObject();
		AminoAcidConstants constants = parameters.getAAConstants();
		LibraryFile source = source("PEPTIDEK", constants);
		try {
			File libraryFile = new File(temporaryFolder.getRoot(), "working.elib");
			File assignmentsFile = new File(temporaryFolder.getRoot(), "assignments.tsv");
			try {
				ContextWorkingLibraryPreparer.prepare(source, parameters,
						new ContextWorkingLibraryPreparer.Settings(27, (byte)2, true), libraryFile, assignmentsFile,
						requests -> new ArrayList<>());
			} catch (java.io.IOException expected) {
				assertTrue(expected.getMessage().contains("No target spectra"));
				assertTrue(!libraryFile.exists());
				assertTrue(Files.readString(assignmentsFile.toPath()).contains("EXCLUDED"));
				return;
			}
			throw new AssertionError("Expected missing-target failure");
		} finally {
			source.close();
		}
	}

	@Test
	public void persistsOneToOneMappedPredictions() throws Exception {
		SearchParameters parameters = SearchParameterParser.getDefaultParametersObject();
		AminoAcidConstants constants = parameters.getAAConstants();
		LibraryFile source = source("PEPTIDEK", constants);
		try {
			File libraryFile = new File(temporaryFolder.getRoot(), "paired.elib");
			File assignmentsFile = new File(temporaryFolder.getRoot(), "paired.tsv");
			try (ContextWorkingLibraryPreparer.Result result = ContextWorkingLibraryPreparer.prepare(source, parameters,
					new ContextWorkingLibraryPreparer.Settings(27, (byte)2, true), libraryFile, assignmentsFile,
					requests -> {
						ArrayList<LibraryEntry> entries = new ArrayList<>();
						for (var request : requests) entries.add(entry(request.getPeptideModSeq(), request.getPrecursorCharge(), constants));
						return entries;
					})) {
				assertEquals(1, result.retainedTargets());
				assertEquals(1, result.retainedDecoys());
				assertEquals("PAIRED", result.assignments().get(0).status());
				assertEquals(1, result.library().getMatchedDecoyAssessment().getUniquelyMappedTargetCount());
				assertEquals(1.0d, result.library().getMatchedDecoyAssessment().getCoverage(), 0.0d);
			}
		} finally {
			source.close();
		}
	}

	@Test
	public void storeReusesPredictionsAndMatchesUnstoredPreparation() throws Exception {
		SearchParameters parameters = SearchParameterParser.getDefaultParametersObject();
		AminoAcidConstants constants = parameters.getAAConstants();
		ContextWorkingLibraryPreparer.Settings settings = new ContextWorkingLibraryPreparer.Settings(27, (byte)2, true);
		LibraryFile source = source(constants, "PEPTIDEK", "ELVISLIVESK");
		try (ContextPredictionStore store = ContextPredictionStore.open(temporaryFolder.newFolder("store"), settings, false)) {
			ArrayList<String> unstoredRequests = new ArrayList<>();
			Prepared unstored = prepare(source, parameters, settings, "unstored", recording(unstoredRequests, constants), null);
			ArrayList<String> firstRequests = new ArrayList<>();
			Prepared first = prepare(source, parameters, settings, "first", recording(firstRequests, constants), store);
			Prepared reused = prepare(source, parameters, settings, "reused", requests -> {
				throw new AssertionError("A full store hit must not predict: " + requests.size() + " requests");
			}, store);
			assertEquals(4, unstoredRequests.size());
			assertEquals("two targets and two decoys are compared", 4, unstored.entries.size());
			assertEquals(unstoredRequests, firstRequests);
			assertEquals(unstored.assignments, first.assignments);
			assertEquals(unstored.assignments, reused.assignments);
			assertEquals(unstored.entries, first.entries);
			assertEquals(unstored.entries, reused.entries);
			assertEquals(2, unstored.assignments.stream().filter(a -> a.status().equals("PAIRED")).count());
		} finally {
			source.close();
		}
	}

	@Test
	public void overlappingLibraryPredictsOnlyNewPrecursors() throws Exception {
		SearchParameters parameters = SearchParameterParser.getDefaultParametersObject();
		AminoAcidConstants constants = parameters.getAAConstants();
		ContextWorkingLibraryPreparer.Settings settings = new ContextWorkingLibraryPreparer.Settings(27, (byte)2, true);
		LibraryFile first = source(constants, "PEPTIDEK");
		LibraryFile overlapping = source(constants, "PEPTIDEK", "ELVISLIVESK");
		try (ContextPredictionStore store = ContextPredictionStore.open(temporaryFolder.newFolder("store"), settings, false)) {
			ArrayList<String> firstRequests = new ArrayList<>();
			prepare(first, parameters, settings, "first", recording(firstRequests, constants), store);
			ArrayList<String> overlapRequests = new ArrayList<>();
			Prepared prepared = prepare(overlapping, parameters, settings, "overlap", recording(overlapRequests, constants), store);
			assertEquals(2, firstRequests.size());
			assertEquals("only the new target and its decoy are predicted", 2, overlapRequests.size());
			for (String request : overlapRequests) assertFalse(request, firstRequests.contains(request));
			assertTrue(overlapRequests.contains(PeptideUtils.getCorrectedMasses("ELVISLIVESK", constants) + "|2"));
			assertEquals(2, prepared.assignments.stream().filter(a -> a.status().equals("PAIRED")).count());
		} finally {
			first.close();
			overlapping.close();
		}
	}

	@Test
	public void failedPredictionIsReportedNotStoredAndRetried() throws Exception {
		SearchParameters parameters = SearchParameterParser.getDefaultParametersObject();
		AminoAcidConstants constants = parameters.getAAConstants();
		ContextWorkingLibraryPreparer.Settings settings = new ContextWorkingLibraryPreparer.Settings(27, (byte)2, true);
		LibraryFile source = source(constants, "PEPTIDEK");
		try (ContextPredictionStore store = ContextPredictionStore.open(temporaryFolder.newFolder("store"), settings, false)) {
			Prepared failed = prepare(source, parameters, settings, "failed", requests -> {
				ArrayList<LibraryEntry> entries = new ArrayList<>();
				for (var request : requests) {
					if (!request.getPeptideModSeq().equals("PEPTIDEK")) throw new IllegalStateException("simulated decoy failure");
					entries.add(entry(request.getPeptideModSeq(), request.getPrecursorCharge(), constants));
				}
				return entries;
			}, store);
			assertEquals("TARGET_ONLY", failed.assignments.get(0).status());
			assertTrue(failed.assignments.get(0).reason(), failed.assignments.get(0).reason().contains("simulated decoy failure"));
			ArrayList<String> retried = new ArrayList<>();
			Prepared repaired = prepare(source, parameters, settings, "repaired", recording(retried, constants), store);
			assertEquals("only the failed decoy is predicted again", 1, retried.size());
			assertFalse(retried.get(0).startsWith("PEPTIDEK"));
			assertEquals("PAIRED", repaired.assignments.get(0).status());
		} finally {
			source.close();
		}
	}

	@Test
	public void publicStoreEntryPointUsesStoredPredictions() throws Exception {
		SearchParameters parameters = SearchParameterParser.getDefaultParametersObject();
		AminoAcidConstants constants = parameters.getAAConstants();
		ContextWorkingLibraryPreparer.Settings settings = new ContextWorkingLibraryPreparer.Settings(27, (byte)2, true);
		LibraryFile source = source(constants, "PEPTIDEK");
		try (ContextPredictionStore store = ContextPredictionStore.open(temporaryFolder.newFolder("store"), settings, false)) {
			prepare(source, parameters, settings, "prefill", recording(new ArrayList<>(), constants), store);
			File libraryFile = new File(temporaryFolder.getRoot(), "public.elib");
			File assignmentsFile = new File(temporaryFolder.getRoot(), "public.tsv");
			try (ContextWorkingLibraryPreparer.Result result = ContextWorkingLibraryPreparer.prepare(source, parameters, settings,
					libraryFile, assignmentsFile, store)) {
				assertEquals("PAIRED", result.assignments().get(0).status());
				assertTrue(libraryFile.isFile());
			}
		} finally {
			source.close();
		}
	}

	@Test
	public void avoidsTargetCollisionAndReusesDecoySequenceAcrossCharges() throws Exception {
		SearchParameters parameters = SearchParameterParser.getDefaultParametersObject();
		AminoAcidConstants constants = parameters.getAAConstants();
		LibraryFile source = new LibraryFile();
		source.openFile();
		try {
			source.addEntries(new ArrayList<>(List.of(
					entry("PEPTIDEK", (byte)2, constants),
					entry("KEDITPEP", (byte)2, constants),
					entry("PEPTIDEK", (byte)3, constants),
					entry("ELVISLIVESK", (byte)3, constants))), false);
			ArrayList<String> requests = new ArrayList<>();
			Prepared prepared = prepare(source, parameters,
					new ContextWorkingLibraryPreparer.Settings(27, (byte)2, true), "collision",
					recording(requests, constants), null);
			assertEquals(4, prepared.assignments.size());
			assertEquals("PEDITPEK|2", prepared.assignments.stream()
					.filter(a -> a.targetKey().equals("PEPTIDEK|2")).findFirst().orElseThrow().decoyKey());
			assertEquals("PEDITPEK|3", prepared.assignments.stream()
					.filter(a -> a.targetKey().equals("PEPTIDEK|3")).findFirst().orElseThrow().decoyKey());
			assertEquals(4, prepared.assignments.stream().filter(a -> a.status().equals("PAIRED")).count());
			assertEquals(8, requests.size());
		} finally {
			source.close();
		}
	}

	private record Prepared(List<ContextWorkingLibraryPreparer.Assignment> assignments, List<String> entries) {}

	private Prepared prepare(LibraryFile source, SearchParameters parameters, ContextWorkingLibraryPreparer.Settings settings,
			String name, ContextWorkingLibraryPreparer.Predictor predictor, ContextPredictionStore store) throws Exception {
		File libraryFile = new File(temporaryFolder.getRoot(), name + ".elib");
		File assignmentsFile = new File(temporaryFolder.getRoot(), name + ".tsv");
		try (ContextWorkingLibraryPreparer.Result result = ContextWorkingLibraryPreparer.prepare(source, parameters, settings,
				libraryFile, assignmentsFile, predictor, store)) {
			AminoAcidConstants constants = parameters.getAAConstants();
			ArrayList<String> entries = new ArrayList<>();
			for (LibraryEntry entry : result.library().getAllEntries(false, constants)) entries.add("target " + describe(entry));
			for (LibraryEntry entry : result.library().getDecoyEntries(new Range(-Float.MAX_VALUE, Float.MAX_VALUE), false, constants)) {
				entries.add("decoy " + describe(entry));
			}
			Collections.sort(entries);
			return new Prepared(result.assignments(), entries);
		}
	}

	private static String describe(LibraryEntry entry) {
		return entry.getPeptideModSeq() + "|" + entry.getPrecursorCharge() + "|" + entry.getPrecursorMZ() + "|"
				+ entry.getRetentionTime() + "|" + entry.getIonMobility() + "|" + Arrays.toString(entry.getMassArray()) + "|"
				+ Arrays.toString(entry.getIntensityArray()) + "|" + new TreeSet<>(entry.getAccessions());
	}

	/** Predicts every request with a deterministic synthetic spectrum and records the requested precursor keys. */
	private static ContextWorkingLibraryPreparer.Predictor recording(List<String> requested, AminoAcidConstants constants) {
		return requests -> {
			ArrayList<LibraryEntry> entries = new ArrayList<>();
			for (var request : requests) {
				requested.add(request.getPeptideModSeq() + "|" + request.getPrecursorCharge());
				entries.add(entry(request.getPeptideModSeq(), request.getPrecursorCharge(), constants));
			}
			return entries;
		};
	}

	private static LibraryFile source(String sequence, AminoAcidConstants constants) throws Exception {
		return source(constants, sequence);
	}

	private static LibraryFile source(AminoAcidConstants constants, String... sequences) throws Exception {
		LibraryFile library = new LibraryFile();
		library.openFile();
		ArrayList<LibraryEntry> entries = new ArrayList<>();
		for (String sequence : sequences) entries.add(entry(sequence, (byte)2, constants));
		library.addEntries(entries, false);
		return library;
	}

	private static LibraryEntry entry(String sequence, byte charge, AminoAcidConstants constants) {
		return new LibraryEntry("synthetic Context test", new HashSet<>(), constants.getChargedMass(sequence, charge),
				charge, sequence, 1, 10.0f, 1.0f, new double[] {250.0, 350.0},
				new float[] {10.0f, 5.0f}, Optional.of(1.0f), constants);
	}
}
