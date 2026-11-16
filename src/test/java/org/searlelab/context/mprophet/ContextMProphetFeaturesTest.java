package org.searlelab.context.mprophet;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.searlelab.context.datastructures.EncyclopediaFeatures;

import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.MProphetData;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.MProphetDataset;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.MProphetExecutionData;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.MProphetFeatureReader;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;

/** CTX018-025: the Context-owned mProphet reader. */
public class ContextMProphetFeaturesTest {
	private static final File FIXTURE = new File("src/test/resources/org/searlelab/context/percolator/standard_percolator_test.features.txt");
	private static final File FASTA = new File("src/test/resources/org/searlelab/context/mprophet/mus_musculus_reviewed_uniprot.fasta");

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	/** Step (b): with EncyclopeDIA's column rules, the Context reader yields exactly EncyclopeDIA's dataset. */
	@Test
	public void legacySelectionReproducesEncyclopediaReaderExactly() throws Exception {
		MProphetExecutionData settings = new MProphetExecutionData(FIXTURE, FASTA, folder.newFile("t.txt"),
				folder.newFile("d.txt"), SearchParameterParser.getDefaultParametersObject());
		MProphetDataset expected = MProphetFeatureReader.parseFeatureFile(FIXTURE, settings);
		MProphetDataset actual = ContextMProphetFeatures.dataset(EncyclopediaFeatures.read(FIXTURE),
				ContextMProphetFeatures.Selection.LEGACY);

		assertEquals(expected.getFeatureNames(), actual.getFeatureNames());
		assertEquals(expected.getStartingScoreIndex(), actual.getStartingScoreIndex());
		List<MProphetData> e = expected.allData();
		List<MProphetData> a = actual.allData();
		assertEquals(1000, e.size());
		assertEquals(e.size(), a.size());
		for (int i = 0; i < e.size(); i++) {
			assertEquals(e.get(i).getId(), a.get(i).getId());
			assertEquals(e.get(i).getSequence(), a.get(i).getSequence());
			assertEquals(e.get(i).getProtein(), a.get(i).getProtein());
			assertEquals(e.get(i).isDecoy(), a.get(i).isDecoy());
			assertArrayEquals(e.get(i).getId(), e.get(i).getData(), a.get(i).getData(), 0f);
		}
	}

	private File table(String name, String... lines) throws Exception {
		File file = folder.newFile(name);
		Files.write(file.toPath(), List.of(lines));
		return file;
	}

	/** Step (c): Context-MS's columns, which differ from EncyclopeDIA's for ExpMass, a mid-table protein column and scan. */
	@Test
	public void contextMSSelectionKeepsExpMassAndDropsMetadataAndPositionalColumns() throws Exception {
		File file = table("cols.txt",
				"id\tLabel\tscan\tExpMass\tprimary\tpepLength\tprotein\txCorrLib\tfeat\tsequence\tProteins",
				"a\t1\t1\t500.1\t3\t9\tP1\t2.0\t1.5\t-.PEPA.-\tP1",
				"b\t-1\t2\t501.2\t2\t9\tP2\t1.0\t0.5\t-.DECA.-\tD1",
				"c\t0\t3\t502.3\t1\t9\tP3\t0.5\t0.1\t-.PEPC.-\tP3");
		MProphetDataset dataset = ContextMProphetFeatures.dataset(EncyclopediaFeatures.read(file),
				ContextMProphetFeatures.Selection.CONTEXT_MS);
		assertEquals(List.of("ExpMass", "protein", "xCorrLib", "feat"), dataset.getFeatureNames());
		assertEquals("xCorrLib is the last preferred name present", 2, dataset.getStartingScoreIndex());
		assertEquals("label 0 is skipped", 2, dataset.allData().size());
		MProphetData target = dataset.allData().get(0);
		assertEquals("PEPA", target.getSequence());
		assertArrayEquals("non-numeric protein reads as zero", new float[] { 500.1f, 0f, 2.0f, 1.5f }, target.getData(), 0f);
	}

	/** Step (d): Context-MS's preference includes main_var_Intensity; with no preferred name, training picks per fold. */
	@Test
	public void startingScoreFollowsContextMSPreference() throws Exception {
		String[] header = {};
		assertEquals(1, ContextMProphetFeatures.startingScore(header, List.of("xCorrModel", "main_var_Intensity"),
				ContextMProphetFeatures.Selection.CONTEXT_MS));
		assertEquals(0, ContextMProphetFeatures.startingScore(header, List.of("xCorrModel", "scribe"),
				ContextMProphetFeatures.Selection.CONTEXT_MS));
		assertEquals(ContextMProphetFeatures.AUTO_STARTING_SCORE, ContextMProphetFeatures.startingScore(header,
				List.of("var_a", "var_b"), ContextMProphetFeatures.Selection.CONTEXT_MS));
	}

	/** Steps (a) and (f): pruning on the background applies to the reference, and both share one feature list. */
	@Test
	public void prepareprunesOnTheBackgroundAndSharesTheFeatureList() throws Exception {
		File background = table("bg.txt",
				"id\tLabel\tScanNr\tconst\txCorrLib\tfeat\tsequence\tProteins",
				"t1\t1\t1\t7\t2.0\t1.0\t-.A.-\tP", "t2\t1\t2\t7\t1.5\t0.8\t-.B.-\tP",
				"d1\t-1\t3\t7\t0.5\t0.2\t-.C.-\tD", "d2\t-1\t4\t7\t0.4\t0.1\t-.E.-\tD");
		File reference = table("ref.txt",
				"id\tLabel\tScanNr\tconst\txCorrLib\tfeat\tsequence\tProteins",
				"r1\t1\t1\t3\t2.0\t1.0\t-.F.-\tP", "r2\t-1\t2\t9\t0.1\t0.2\t-.G.-\tD");
		ContextMProphetFeatures.Prepared prepared = ContextMProphetFeatures.prepare(background, reference,
				folder.newFolder("work"), "x");
		assertEquals(List.of("const"), prepared.droppedFeatures());
		assertEquals(List.of("xCorrLib", "feat"), prepared.background().getFeatureNames());
		assertEquals(prepared.background().getFeatureNames(), prepared.reference().getFeatureNames());
		assertFalse("const is dropped from the reference even though it varies there",
				new ArrayList<>(List.of(Files.readAllLines(prepared.prunedReference().toPath()).get(0).split("\t"))).contains("const"));
		assertTrue(prepared.prunedBackground().getName().endsWith(".background.pin"));
	}
}
