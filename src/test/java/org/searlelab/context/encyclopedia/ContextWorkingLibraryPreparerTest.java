package org.searlelab.context.encyclopedia;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import edu.washington.gs.maccoss.encyclopedia.datastructures.AminoAcidConstants;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryEntry;
import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchParameters;
import edu.washington.gs.maccoss.encyclopedia.filereaders.LibraryFile;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;

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

	private static LibraryFile source(String sequence, AminoAcidConstants constants) throws Exception {
		LibraryFile library = new LibraryFile();
		library.openFile();
		ArrayList<LibraryEntry> entries = new ArrayList<>();
		entries.add(entry(sequence, (byte)2, constants));
		library.addEntries(entries, false);
		return library;
	}

	private static LibraryEntry entry(String sequence, byte charge, AminoAcidConstants constants) {
		return new LibraryEntry("synthetic CTX-016 test", new HashSet<>(), constants.getChargedMass(sequence, charge),
				charge, sequence, 1, 10.0f, 1.0f, new double[] {250.0, 350.0},
				new float[] {10.0f, 5.0f}, Optional.of(1.0f), constants);
	}
}
