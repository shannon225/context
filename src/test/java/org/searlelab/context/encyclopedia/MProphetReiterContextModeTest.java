package org.searlelab.context.encyclopedia;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.MProphetExecutionData;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;
import edu.washington.gs.maccoss.encyclopedia.utils.EncyclopediaException;

import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.MProphetData;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.MProphetDataset;
import edu.washington.gs.maccoss.encyclopedia.utils.math.LinearDiscriminantAnalysis;
import edu.washington.gs.maccoss.encyclopedia.utils.math.ScoredObject;

/** CTX018-021/022/025(d): the Context-only training mode and the legacy path it leaves unchanged. */
public class MProphetReiterContextModeTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	/** EncyclopeDIA-style ids (PercolatorPeptide parses them). Features absent from the seed coefficients, so the seed model is unusable, and no preferred starting score. */
	private static MProphetDataset unseededDataset(int n, double separation, long seed) {
		Random random = new Random(seed);
		ArrayList<MProphetData> rows = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			rows.add(new MProphetData("run:" + i + ".0:PEPTIDE" + i + "K+2", "PEPTIDE" + i + "K", "P", new float[] { (float) (separation + random.nextGaussian()),
					(float) random.nextGaussian(), (float) (0.5 * separation + random.nextGaussian()) }, false));
		}
		for (int i = 0; i < n; i++) {
			rows.add(new MProphetData("run:" + i + ".5:DECOYPEP" + i + "R+2", "DECOYPEP" + i + "R", "D", new float[] { (float) random.nextGaussian(),
					(float) random.nextGaussian(), (float) random.nextGaussian() }, true));
		}
		return new MProphetDataset(new ArrayList<>(List.of("f1", "f2", "f3")), -1, rows);
	}

	private MProphetExecutionData settings() throws Exception {
		File input = folder.newFile();
		return new MProphetExecutionData(input, input, folder.newFile(), folder.newFile(),
				SearchParameterParser.getDefaultParametersObject());
	}

	@Test
	public void anUnusableSeedModelIsNotComparedOnTheTestFold() throws Exception {
		MProphetReiter.ContextTraining training = MProphetReiter.executeContextTraining(unseededDataset(400, 4, 3),
				settings(), 0.01f, 1, SearchParameterParser.getDefaultParametersObject().getAAConstants(), null);
		assertNotNull(training.result().getLDA());
		assertEquals(MProphetReiter.ITERATIONS, training.iterations().size());
		for (String record : training.iterations()) {
			String[] fields = record.split("\t", -1);
			assertEquals("no seed comparison: " + record, "", fields[2]);
			assertEquals(record, "trained", fields[3]);
		}
	}

	@Test
	public void noUsableModelAndNoUsableSeedIsAnErrorNotAZeroModel() throws Exception {
		ArrayList<MProphetData> rows = new ArrayList<>();
		for (int i = 0; i < 40; i++) {
			rows.add(new MProphetData("t" + i, "T" + i, "P", new float[] { 1f, 1f }, false));
			rows.add(new MProphetData("d" + i, "D" + i, "P", new float[] { 1f, 1f }, true));
		}
		MProphetDataset constant = new MProphetDataset(new ArrayList<>(List.of("f1", "f2")), -1, rows);
		try {
			MProphetReiter.executeContextTraining(constant, settings(), 0.01f, 1,
					SearchParameterParser.getDefaultParametersObject().getAAConstants(), null);
			fail("training without any usable model or seed must not fall back to an all-zero model");
		} catch (EncyclopediaException expected) {
			assertTrue(expected.getMessage(), expected.getMessage().contains("seed model is unusable"));
		}
	}

	@Test
	public void anIterationWhoseTrainingFoldLacksDecoysIsSkippedNotFatal() throws Exception {
		MProphetDataset dataset = unseededDataset(200, 4, 5);
		ArrayList<MProphetData> targets = new ArrayList<>();
		ArrayList<MProphetData> decoys = new ArrayList<>();
		for (MProphetData row : dataset.allData()) (row.isDecoy() ? decoys : targets).add(row);
		List<MProphetDataset[]> folds = new ArrayList<>();
		for (int n = 0; n < MProphetReiter.ITERATIONS; n++) {
			ArrayList<MProphetData> train = new ArrayList<>(targets.subList(0, 100));
			ArrayList<MProphetData> test = new ArrayList<>(targets.subList(100, 200));
			if (n == 7) {
				test.addAll(decoys); // iteration 7: every decoy in the test fold, none to train on
			} else {
				train.addAll(decoys.subList(0, 100));
				test.addAll(decoys.subList(100, 200));
			}
			folds.add(new MProphetDataset[] { new MProphetDataset(new ArrayList<>(dataset.getFeatureNames()), -1, train),
					new MProphetDataset(new ArrayList<>(dataset.getFeatureNames()), -1, test) });
		}
		MProphetReiter.ContextTraining training = MProphetReiter.executeContextTraining(dataset, settings(), 0.01f, 1,
				SearchParameterParser.getDefaultParametersObject().getAAConstants(), folds);
		assertEquals("7\t\t\t\t", training.iterations().get(7));
		assertEquals("trained", training.iterations().get(8).split("\t", -1)[3]);
		assertNotNull(training.result().getLDA());
	}

	private static ScoredObject<LinearDiscriminantAnalysis> model(int passing, double coefficient) {
		return new ScoredObject<>(passing, new LinearDiscriminantAnalysis(new double[] { coefficient }, 0));
	}

	/** Models as iterations produce them: the two tied at 128 straddle the cut, as in Context-MS seed 1. */
	private static ArrayList<ScoredObject<LinearDiscriminantAnalysis>> iterations() {
		ArrayList<ScoredObject<LinearDiscriminantAnalysis>> models = new ArrayList<>();
		models.add(model(129, 1));
		models.add(model(128, 1)); // earlier tie
		models.add(model(140, 1));
		models.add(model(128, 9)); // later tie, larger coefficient sum
		models.add(model(100, 1));
		return models;
	}

	@Test
	public void contextModeKeepsTiedModelsInIterationOrder() {
		ArrayList<ScoredObject<LinearDiscriminantAnalysis>> models = iterations();
		ScoredObject<LinearDiscriminantAnalysis> earlierTie = models.get(1);
		MProphetReiter.rankModels(models, true);
		assertEquals(List.of(140f, 129f, 128f, 128f, 100f), scores(models));
		assertSame("Context-MS keeps the earlier of two tied models first", earlierTie, models.get(2));
	}

	@Test
	public void legacyModeIsUnchanged() {
		ArrayList<ScoredObject<LinearDiscriminantAnalysis>> models = iterations();
		ScoredObject<LinearDiscriminantAnalysis> laterTie = models.get(3);
		ArrayList<ScoredObject<LinearDiscriminantAnalysis>> expected = new ArrayList<>(models);
		java.util.Collections.sort(expected);
		java.util.Collections.reverse(expected);
		MProphetReiter.rankModels(models, false);
		assertEquals(expected, models);
		assertSame("legacy ranks the tie with the larger coefficient sum first", laterTie, models.get(2));
	}

	@Test
	public void bestSeparatingFeatureFollowsContextMS() {
		// Feature 0 separates poorly, feature 1 well, feature 2 is constant in the decoys (not eligible).
		ArrayList<MProphetData> rows = new ArrayList<>();
		float[][] targets = { { 1, 5, 3 }, { 2, 6, 4 }, { 1.5f, 5.5f, 5 } };
		float[][] decoys = { { 1.2f, 1, 0 }, { 1.8f, 2, 0 }, { 1.4f, 1.5f, 0 } };
		for (int i = 0; i < targets.length; i++) rows.add(new MProphetData("t" + i, "T" + i, "P", targets[i], false));
		for (int i = 0; i < decoys.length; i++) rows.add(new MProphetData("d" + i, "D" + i, "P", decoys[i], true));
		MProphetDataset dataset = new MProphetDataset(new ArrayList<>(List.of("a", "b", "c")), -1, rows);
		assertEquals(1, MProphetReiter.bestSeparatingFeature(dataset));

		ArrayList<MProphetData> constant = new ArrayList<>();
		constant.add(new MProphetData("t", "T", "P", new float[] { 1, 1 }, false));
		constant.add(new MProphetData("d", "D", "P", new float[] { 1, 1 }, true));
		assertEquals("no eligible feature means feature 0",
				0, MProphetReiter.bestSeparatingFeature(new MProphetDataset(new ArrayList<>(List.of("a", "b")), -1, constant)));
	}

	private static List<Float> scores(List<ScoredObject<LinearDiscriminantAnalysis>> models) {
		List<Float> scores = new ArrayList<>();
		for (ScoredObject<LinearDiscriminantAnalysis> m : models) scores.add(m.getScore());
		return scores;
	}
}
