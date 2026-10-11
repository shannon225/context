package org.searlelab.context.percolator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import edu.washington.gs.maccoss.encyclopedia.utils.CommandLineParser;
import org.searlelab.context.io.ContextFeatureScorer;

public class ContextPercolatorExecutorCLITest {
	@Rule public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void splitInputsRequirePairsOrExplicitLegacyMode() throws Exception {
		File background = folder.newFile("background.features.txt");
		File reference = folder.newFile("reference.features.txt");
		HashMap<String, String> arguments = new HashMap<>();
		IOException missing = assertThrows(IOException.class,
				() -> ContextPercolatorExecutorCLI.requirePairMetadata(background, reference, arguments));
		assertTrue(missing.getMessage().contains("Both split feature pair sidecars are required"));
		HashMap<String, String> legacyArguments = CommandLineParser.parseArguments(
				new String[] {"--allow-legacy-features", "true"});
		ContextPercolatorExecutorCLI.requirePairMetadata(background, reference, legacyArguments);
		Files.writeString(new File(background + ".pairs.tsv").toPath(), "partial");
		assertThrows(IOException.class,
				() -> ContextPercolatorExecutorCLI.requirePairMetadata(background, reference, legacyArguments));
	}

	@Test
	public void rawRouteAcceptsPredictionStoreFlags() throws Exception {
		File store = folder.newFolder("shared-store");
		HashMap<String, String> arguments = CommandLineParser.parseArguments(new String[] {"--prediction-store",
				store.getPath(), "--clear-stale-store-lock", "true"});
		ContextFeatureScorer.Options options = ContextPercolatorExecutorCLI.rawScoringOptions(arguments);
		assertEquals(store, options.predictionStore());
		assertTrue(options.clearStaleStoreLock());
		assertEquals(ContextFeatureScorer.Options.defaults().predictionNce(), options.predictionNce());
		ContextFeatureScorer.Options defaults = ContextPercolatorExecutorCLI.rawScoringOptions(new HashMap<>());
		assertNull(defaults.predictionStore());
		assertFalse(defaults.clearStaleStoreLock());
		assertThrows(IllegalArgumentException.class, () -> ContextPercolatorExecutorCLI.rawScoringOptions(
				CommandLineParser.parseArguments(new String[] {"--clear-stale-store-lock", "yes"})));
	}

	@Test
	public void splitAndStandardRoutesRejectPredictionStoreFlags() {
		for (String flag : new String[] {"--prediction-store", "--clear-stale-store-lock"}) {
			HashMap<String, String> arguments = CommandLineParser.parseArguments(new String[] {flag, "value"});
			IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
					() -> ContextPercolatorExecutorCLI.rejectStoreOptionsWithoutRawInput(arguments));
			assertTrue(error.getMessage(), error.getMessage().contains(flag + " applies only to raw"));
		}
		ContextPercolatorExecutorCLI.rejectStoreOptionsWithoutRawInput(
				CommandLineParser.parseArguments(new String[] {"-fdr", "0.01"}));
	}
}
