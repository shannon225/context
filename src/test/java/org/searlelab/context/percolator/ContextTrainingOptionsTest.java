package org.searlelab.context.percolator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.HashMap;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;

/** CTX018-028 (Percolator part): conflicting EncyclopeDIA options are reported, defaults and equivalents are not. */
public class ContextTrainingOptionsTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private static HashMap<String, String> defaultsWith(String... pairs) {
		HashMap<String, String> arguments = SearchParameterParser.getDefaultParameters();
		for (int i = 0; i < pairs.length; i += 2) arguments.put(pairs[i], pairs[i + 1]);
		return arguments;
	}

	@Test
	public void defaultsAndBinaryChoicesProduceNoWarning() {
		assertEquals(List.of(), ContextTrainingOptions.percolatorConflicts(defaultsWith(), 0.01f));
		assertEquals(List.of(), ContextTrainingOptions.percolatorConflicts(null, 0.01f));
		assertEquals(List.of(), ContextTrainingOptions.percolatorConflicts(defaultsWith("-percolatorVersion", "v3-05"), 0.01f));
		// -fdr alone must not make the untouched -percolatorThreshold default look like a conflict.
		assertEquals(List.of(), ContextTrainingOptions.percolatorConflicts(defaultsWith(), 0.05f));
		// Equivalent spellings of the defaults, and -percolatorThreshold equal to -fdr.
		assertEquals(List.of(), ContextTrainingOptions.percolatorConflicts(defaultsWith("-percolatorTrainingFDR", "0.00",
				"-percolatorTrainingSetSize", "500000", "-percolatorProteinThreshold", "0.010"), 0.01f));
		assertEquals(List.of(), ContextTrainingOptions.percolatorConflicts(defaultsWith("-percolatorThreshold", "0.05"), 0.05f));
	}

	@Test
	public void eachConflictingOptionIsNamedOnce() throws Exception {
		File model = folder.newFile("old.model");
		String[][] cases = { { "-percolatorTrainingIterations", "5" }, { "-percolatorTrainingFDR", "0.05" },
				{ "-percolatorTrainingSetSize", "1000" }, { "-percolatorModelFile", model.getAbsolutePath() },
				{ "-percolatorProteinThreshold", "0.05" }, { "-percolatorThreshold", "0.05" } };
		for (String[] option : cases) {
			List<String> conflicts = ContextTrainingOptions.percolatorConflicts(defaultsWith(option[0], option[1]), 0.01f);
			assertEquals(option[0] + " -> " + conflicts, 1, conflicts.size());
			assertTrue(conflicts.get(0), conflicts.get(0).startsWith(option[0] + " "));
		}
		assertEquals(2, ContextTrainingOptions.percolatorConflicts(defaultsWith("-percolatorTrainingIterations", "5",
				"-percolatorTrainingFDR", "0.05"), 0.01f).size());
	}
}
