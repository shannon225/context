package org.searlelab.context.mprophet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.searlelab.context.datastructures.EncyclopediaFeatures;

import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.MProphetData;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.MProphetDataset;

/** CTX018-024: the diagnostic fold file joins by id, keeps within-fold order, and rejects malformed plans. */
public class MProphetFoldFileTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private MProphetDataset background() throws Exception {
		File table = folder.newFile("bg.txt");
		Files.write(table.toPath(), List.of("id\tLabel\tScanNr\txCorrLib\tsequence\tProteins",
				"t1\t1\t1\t1\t-.A.-\tP", "t2\t1\t2\t2\t-.B.-\tP", "t3\t1\t3\t3\t-.C.-\tP",
				"d1\t-1\t4\t0.1\t-.D.-\tD", "d2\t-1\t5\t0.2\t-.E.-\tD"));
		return ContextMProphetFeatures.dataset(EncyclopediaFeatures.read(table), ContextMProphetFeatures.Selection.CONTEXT_MS);
	}

	private File plan(String... rows) throws IOException {
		File file = folder.newFile();
		List<String> lines = new ArrayList<>(List.of("iteration\tfold\tposition\tid"));
		lines.addAll(List.of(rows));
		Files.write(file.toPath(), lines);
		return file;
	}

	/** A valid two-iteration plan; iteration 1 lists rows out of file order on purpose. */
	private static final String[] VALID = { "0\t0\t0\tt1", "0\t0\t1\td1", "0\t1\t0\tt2", "0\t1\t1\tt3", "0\t1\t2\td2",
			"1\t0\t0\tt3", "1\t0\t1\tt1", "1\t0\t2\td2", "1\t0\t3\td1", "1\t1\t0\tt2" };

	@Test
	public void joinsByIdAndKeepsWithinFoldOrder() throws Exception {
		List<MProphetDataset[]> folds = MProphetFoldFile.read(plan(VALID), background(), 2);
		assertEquals(2, folds.size());
		assertEquals(List.of("t1", "d1"), ids(folds.get(0)[0]));
		assertEquals(List.of("t2", "t3", "d2"), ids(folds.get(0)[1]));
		assertEquals("targets then decoys, each in file position order", List.of("t3", "t1", "d2", "d1"), ids(folds.get(1)[0]));
		assertEquals(2, folds.get(1)[0].getDecoyData().size());
		assertEquals(0.2f, folds.get(1)[0].getDecoyData().get(0)[0], 0f);
	}

	@Test
	public void malformedPlansFailClearly() throws Exception {
		MProphetDataset background = background();
		String[][] cases = {
				{ "missing", "0\t0\t0\tt1", "0\t0\t1\td1", "0\t1\t0\tt2", "0\t1\t1\tt3" }, // d2 absent in iteration 0
				{ "twice", "0\t0\t0\tt1", "0\t0\t1\tt1" },
				{ "not in the background", "0\t0\t0\tzz" },
				{ "outside", "2\t0\t0\tt1" }, // a third iteration
				{ "repeats", "0\t0\t0\tt1", "0\t0\t0\tt2" },
				{ "fold must be", "0\t2\t0\tt1" },
				{ "not an integer", "x\t0\t0\tt1" },
		};
		for (String[] c : cases) {
			String[] rows = java.util.Arrays.copyOfRange(c, 1, c.length);
			expectFailure(c[0], plan(rows), background, 2);
		}
		// Only iteration 0 present: iteration 1 is missing.
		expectFailure("iteration 1 is missing", plan(java.util.Arrays.copyOfRange(VALID, 0, 5)), background, 2);
		// Positions with a gap.
		expectFailure("are not 0..", plan("0\t0\t0\tt1", "0\t0\t5\td1", "0\t1\t0\tt2", "0\t1\t1\tt3", "0\t1\t2\td2",
				"1\t0\t0\tt3", "1\t0\t1\tt1", "1\t0\t2\td2", "1\t0\t3\td1", "1\t1\t0\tt2"), background, 2);
		// Wrong header.
		File header = folder.newFile();
		Files.write(header.toPath(), List.of("iter\tfold\tpos\tid"));
		expectFailure("must start with", header, background, 2);
	}

	private static void expectFailure(String message, File file, MProphetDataset background, int iterations) {
		try {
			MProphetFoldFile.read(file, background, iterations);
			fail("accepted a plan that should fail with [" + message + "]");
		} catch (IOException expected) {
			assertTrue(expected.getMessage(), expected.getMessage().contains(message));
		}
	}

	private static List<String> ids(MProphetDataset dataset) {
		List<String> ids = new ArrayList<>();
		for (MProphetData row : dataset.allData()) ids.add(row.getId());
		return ids;
	}
}
