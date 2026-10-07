package org.searlelab.context.percolator;

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
}
