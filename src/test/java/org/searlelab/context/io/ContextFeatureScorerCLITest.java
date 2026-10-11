package org.searlelab.context.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ContextFeatureScorerCLITest {
	@Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void parsesCustomInputPrefix() {
		Map<String, String> arguments = ContextFeatureScorerCLI.parseArguments(new String[] { "--library", "test.elib",
				"--fasta", "test.fasta", "--dia-folder", "inputs", "--input-prefix", "_bootstrap" });

		assertEquals("_bootstrap", arguments.get("--input-prefix"));
	}

	@Test
	public void rejectsDuplicateInputPrefix() {
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> ContextFeatureScorerCLI.parseArguments(
						new String[] { "--input-prefix", "_masked", "--input-prefix", "_bootstrap" }));

		assertEquals("Argument was supplied more than once: --input-prefix", error.getMessage());
	}

	@Test
	public void predictionStoreOptionsDefaultToTheBesideTheRunStore() throws Exception {
		ContextFeatureScorer.Options defaults = ContextFeatureScorerCLI.scoringOptions(Map.of());
		assertNull(defaults.predictionStore());
		assertFalse(defaults.clearStaleStoreLock());
		File runs = temporaryFolder.newFolder("runs");
		String baseName = new File(runs, "sample").getAbsolutePath();
		assertEquals(new File(runs, "prediction-cache"), ContextFeatureScorer.predictionStoreDirectory(baseName, defaults));

		File shared = new File(temporaryFolder.getRoot(), "shared-store");
		ContextFeatureScorer.Options override = ContextFeatureScorerCLI.scoringOptions(ContextFeatureScorerCLI.parseArguments(
				new String[] { "--prediction-store", shared.getPath(), "--clear-stale-store-lock", "true" }));
		assertEquals(shared, override.predictionStore());
		assertTrue(override.clearStaleStoreLock());
		assertEquals(shared.getAbsoluteFile(), ContextFeatureScorer.predictionStoreDirectory(baseName, override));
	}

	@Test
	public void positionalFeaturesCommandKeepsDefaultsAndAcceptsScoringFlags() throws Exception {
		assertEquals(ContextFeatureScorer.Options.defaults(), ContextFeatureScorer.positionalOptions(new String[0]));
		File shared = temporaryFolder.newFolder("shared-store");
		ContextFeatureScorer.Options options = ContextFeatureScorer.positionalOptions(new String[] { "--prediction-store",
				shared.getPath(), "--clear-stale-store-lock", "true", "--prediction-nce", "30" });
		assertEquals(shared, options.predictionStore());
		assertTrue(options.clearStaleStoreLock());
		assertEquals(30, options.predictionNce());
		IllegalArgumentException folderOnly = assertThrows(IllegalArgumentException.class,
				() -> ContextFeatureScorer.positionalOptions(new String[] { "--dia-folder", "runs" }));
		assertEquals("Unsupported option for features: --dia-folder", folderOnly.getMessage());
	}

	@Test
	public void rejectsInvalidPredictionStoreOptions() throws Exception {
		File notADirectory = temporaryFolder.newFile("store.txt");
		IllegalArgumentException fileStore = assertThrows(IllegalArgumentException.class,
				() -> ContextFeatureScorerCLI.scoringOptions(Map.of("--prediction-store", notADirectory.getPath())));
		assertTrue(fileStore.getMessage().contains("must be a directory"));
		IllegalArgumentException flag = assertThrows(IllegalArgumentException.class,
				() -> ContextFeatureScorerCLI.scoringOptions(Map.of("--clear-stale-store-lock", "yes")));
		assertEquals("--clear-stale-store-lock must be true or false.", flag.getMessage());
	}
}
