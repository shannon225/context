package org.searlelab.context.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.searlelab.context.Context;

public class ContextMProphetCLITest {
	@Rule public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void commandRoutesToHelpAndParsesOptionalFlag() throws Throwable {
		Context.main(new String[] {"mprophet", "-h"});
		assertEquals("true", ContextMProphetCLI.parse(new String[] {"-generateDecoys", "-fdr", "0.01"})
				.get("-generateDecoys"));
	}

	@Test
	public void directInputsRequireMatchingPairMetadataOrExplicitLegacyMode() throws Exception {
		File background = file("background.features.txt", "id\tLabel\tScanNr\tfeat\tsequence\tProteins\n");
		File reference = file("reference.features.txt", "id\tLabel\tScanNr\tfeat\tsequence\tProteins\n");
		File fasta = file("proteins.fasta", ">P\nPEPTIDEK\n");
		IOException error = assertThrows(IOException.class, () -> ContextMProphetCLI.run(new String[] {
				"-background", background.toString(), "-reference", reference.toString(), "-f", fasta.toString()}));
		assertTrue(error.getMessage().contains("Both split feature pair sidecars are required"));
		assertTrue(!new File(folder.getRoot(), "context-mprophet").exists());
		IllegalArgumentException directDecoys = assertThrows(IllegalArgumentException.class,
				() -> ContextMProphetCLI.run(new String[] {"-background", background.toString(),
						"-reference", reference.toString(), "-f", fasta.toString(), "-generateDecoys"}));
		assertTrue(directDecoys.getMessage().contains("only to raw"));
		for (String[] storeOption : new String[][] {{"--prediction-store", folder.getRoot().getPath()},
				{"--clear-stale-store-lock", "true"}}) {
			IllegalArgumentException directStore = assertThrows(IllegalArgumentException.class,
					() -> ContextMProphetCLI.run(new String[] {"-background", background.toString(),
							"-reference", reference.toString(), "-f", fasta.toString(), storeOption[0], storeOption[1]}));
			assertTrue(directStore.getMessage(), directStore.getMessage().contains(storeOption[0] + " applies only to raw"));
		}
		assertThrows(IllegalArgumentException.class,
				() -> ContextMProphetCLI.parse(new String[] {"--clear-stale-store-lock", "yes"}));
	}

	@Test
	public void existingEngineOutputIsPreserved() throws Exception {
		File background = file("background.features.txt", "id\tLabel\tScanNr\tfeat\tsequence\tProteins\n");
		File reference = file("reference.features.txt", "id\tLabel\tScanNr\tfeat\tsequence\tProteins\n");
		File fasta = file("proteins.fasta", ">P\nPEPTIDEK\n");
		File engine = folder.newFolder("context-mprophet");
		File marker = new File(engine, "existing.txt");
		Files.writeString(marker.toPath(), "keep");
		IOException error = assertThrows(IOException.class, () -> ContextMProphetCLI.run(new String[] {
				"-background", background.toString(), "-reference", reference.toString(), "-f", fasta.toString(),
				"--allow-legacy-features", "true"}));
		assertTrue(error.getMessage().contains("fresh mProphet output directory"));
		assertEquals("keep", Files.readString(marker.toPath()));
	}

	@Test
	public void acceptsBrukerAcquisitionDirectoryOnlyForRawInput() throws Exception {
		File bruker = folder.newFolder("sample.d");
		Files.writeString(new File(bruker, "analysis.tdf").toPath(), "fixture");
		assertEquals(bruker, ContextMProphetCLI.readableAcquisition(Map.of("-i", bruker.getAbsolutePath())));
		File other = folder.newFolder("ordinary");
		assertThrows(IOException.class,
				() -> ContextMProphetCLI.readableAcquisition(Map.of("-i", other.getAbsolutePath())));
	}

	private File file(String name, String content) throws IOException {
		File file = new File(folder.getRoot(), name);
		Files.writeString(file.toPath(), content);
		return file;
	}
}
