package org.searlelab.context.encyclopedia;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

import java.io.File;
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

/** One-precursor integration check for the bundled JChronologer prediction runtime. */
public class ContextWorkingLibraryLiveTest {
	@Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void predictsAndPersistsOneTargetAndDecoy() throws Exception {
		// JChronologer 1.0.10 ships PyTorch 2.7.1 natives for macOS ARM, Linux x86_64, and Windows x86_64.
		assumeFalse(System.getProperty("os.name").toLowerCase().contains("mac")
				&& System.getProperty("os.arch").equals("x86_64"));
		SearchParameters parameters = SearchParameterParser.getDefaultParametersObject();
		AminoAcidConstants constants = parameters.getAAConstants();
		byte charge = 2;
		String sequence = "PEPTIDEK";
		LibraryEntry target = new LibraryEntry("synthetic CTX-016 integration test", new HashSet<>(),
				constants.getChargedMass(sequence, charge), charge, sequence, 1, 10f, 1f,
				new double[] {250, 350}, new float[] {10, 5}, Optional.empty(), constants);
		LibraryFile source = new LibraryFile();
		try {
			source.openFile();
			source.addEntries(new ArrayList<>(java.util.List.of(target)), false);
			File working = new File(temporaryFolder.getRoot(), "working.elib");
			File assignments = new File(temporaryFolder.getRoot(), "assignments.tsv");
			try (ContextWorkingLibraryPreparer.Result result = ContextWorkingLibraryPreparer.prepare(source,
					parameters, new ContextWorkingLibraryPreparer.Settings(27, charge, true), working, assignments)) {
				assertEquals(1, result.retainedTargets());
				assertEquals(1, result.retainedDecoys());
				assertEquals("PAIRED", result.assignments().get(0).status());
			}
		} finally {
			source.close();
		}
	}
}
