package org.searlelab.context.mprophet;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import edu.washington.gs.maccoss.encyclopedia.utils.math.LinearDiscriminantAnalysis;

/**
 * CTX018-007/023: the Context-MS-format mProphet outputs. Synthetic reference rows include repeated peptides
 * (one charge state each) and a tied score, which Context-MS's example/ data does not have.
 */
public class ContextMProphetReportsTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private static final LinearDiscriminantAnalysis MODEL = new LinearDiscriminantAnalysis(new double[] { 2.0, -1.0 }, 0.5);
	private static final List<String> FEATURES = List.of("f1", "f2");

	/** score = 2*f1 - f2 + 0.5. */
	private File reference() throws IOException {
		File file = folder.newFile("ref_reference.features.txt");
		Files.write(file.toPath(), List.of(
				"id\tLabel\tScanNr\tf1\tunused\tf2\tsequence\tProteins",
				"t1a\t1\t1\t3.0\t9\t0.0\tPEPA\tP1",   // 6.5
				"t1b\t1\t2\t1.0\t9\t0.0\tPEPA\tP1",   // 2.5, loses to t1a
				"t2a\t1\t3\t2.0\t9\t1.0\tPEPB\tP2",   // 3.5, tied with t2b; first in the file wins
				"t2b\t1\t4\t2.0\t9\t1.0\tPEPB\tP2",   // 3.5
				"t3\t1\t5\t1.5\t9\t0.0\tPEPC\tP3",    // 3.5
				"t4\t1\t6\t0.0\t9\t0.0\tPEPD\tP4",    // 0.5
				"d1\t-1\t7\t0.5\t9\t1.0\tDECA\tD1",   // 0.5
				"d1b\t-1\t8\t1.0\t9\t0.0\tDECA\tD1",  // 2.5, wins over d1
				"d2\t-1\t9\t0.0\t9\t0.5\tDECB\tD2",   // 0.0
				"d3\t-1\t10\t0.25\t9\t0.0\tDECC\tD3")); // 1.0
		return file;
	}

	private static List<String[]> rows(File file) throws IOException {
		List<String[]> rows = new ArrayList<>();
		for (String line : Files.readAllLines(file.toPath())) rows.add(line.split("\t", -1));
		return rows;
	}

	@Test
	public void writesAllFourOutputsWithIdentityAndRecomputedPeptideConfidence() throws Exception {
		File engine = folder.newFolder("context-mprophet");
		ContextMProphetReports.Summary summary = ContextMProphetReports.write(reference(), MODEL, FEATURES, engine, "P", 0.01f);
		assertEquals(6, summary.psmTargets());
		assertEquals(4, summary.peptideTargets());

		// Rescored: every row in order, labels as 1/-1, score from the named feature columns only.
		List<String[]> rescored = rows(new File(engine, "P.rescored_features.txt"));
		assertEquals("score", rescored.get(0)[rescored.get(0).length - 1]);
		assertEquals(11, rescored.size());
		double[] expected = { 6.5, 2.5, 3.5, 3.5, 3.5, 0.5, 0.5, 2.5, 0.0, 1.0 };
		for (int i = 0; i < expected.length; i++) {
			assertEquals(rescored.get(i + 1)[0], expected[i], Double.parseDouble(rescored.get(i + 1)[8]), 1e-12);
		}

		// PSM level: all six targets against all four decoys.
		MProphetConfidence.Confidence psm = MProphetConfidence.qAndPep(new double[] { 6.5, 2.5, 3.5, 3.5, 3.5, 0.5 },
				new double[] { 0.5, 2.5, 0.0, 1.0 });
		Map<String, double[]> psmRows = byId(rows(new File(engine, "P.psm.reference.txt")));
		String[] targetIds = { "t1a", "t1b", "t2a", "t2b", "t3", "t4" };
		for (int i = 0; i < targetIds.length; i++) {
			assertArrayEquals(targetIds[i], new double[] { psm.q()[i], psm.pep()[i] }, psmRows.get(targetIds[i]), 0.0);
		}

		// Peptide level: best row per peptide (t1a; t2a wins the tie; d1b), then q/PEP recomputed on those rows.
		List<String[]> peptides = rows(new File(engine, "P.peptide.reference.txt"));
		assertArrayEquals(new String[] { "id", "ScanNr", "score", "q-value", "posterior_error_prob", "sequence", "Proteins" },
				peptides.get(0));
		Map<String, double[]> peptideRows = byId(peptides);
		assertEquals(List.of("t1a", "t2a", "t3", "t4"), new ArrayList<>(new java.util.TreeSet<>(peptideRows.keySet())));
		MProphetConfidence.Confidence peptide = MProphetConfidence.qAndPep(new double[] { 6.5, 3.5, 3.5, 0.5 },
				new double[] { 2.5, 1.0, 0.0 });
		String[] peptideIds = { "t1a", "t2a", "t3", "t4" };
		for (int i = 0; i < peptideIds.length; i++) {
			assertArrayEquals(peptideIds[i], new double[] { peptide.q()[i], peptide.pep()[i] }, peptideRows.get(peptideIds[i]), 0.0);
		}
		assertNotEquals("peptide-level confidence is recomputed, not copied from the PSM level",
				psmRows.get("t2a")[0], peptideRows.get("t2a")[0], 0.0);
		assertEquals("P2", peptides.stream().filter(r -> r[0].equals("t2a")).findFirst().get()[6]);

		// Weights: feature/weight rows and the bias.
		assertEquals(List.of("feature\tweight", "f1\t2.0", "f2\t-1.0", "__bias__\t0.5"),
				Files.readAllLines(new File(engine, "model/P.weights.txt").toPath()));
	}

	@Test
	public void reportsAreSortedByQValue() throws Exception {
		File engine = folder.newFolder("sorted");
		ContextMProphetReports.write(reference(), MODEL, FEATURES, engine, "P", 0.01f);
		List<String[]> psm = rows(new File(engine, "P.psm.reference.txt"));
		for (int i = 2; i < psm.size(); i++) {
			assertTrue(Double.parseDouble(psm.get(i)[3]) >= Double.parseDouble(psm.get(i - 1)[3]));
		}
	}

	@Test
	public void aModelFeatureMissingFromTheReferenceIsAnError() throws Exception {
		File engine = folder.newFolder("missing");
		try {
			ContextMProphetReports.write(reference(), new LinearDiscriminantAnalysis(new double[] { 1, 1 }, 0),
					List.of("f1", "absent"), engine, "P", 0.01f);
			fail("a model feature without a reference column must not be scored as zero");
		} catch (IOException expected) {
			assertTrue(expected.getMessage(), expected.getMessage().contains("absent"));
		}
	}

	private static Map<String, double[]> byId(List<String[]> report) {
		Map<String, double[]> values = new HashMap<>();
		for (String[] row : report.subList(1, report.size())) {
			values.put(row[0], new double[] { Double.parseDouble(row[3]), Double.parseDouble(row[4]) });
		}
		return values;
	}
}
