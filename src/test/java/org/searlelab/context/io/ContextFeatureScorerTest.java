package org.searlelab.context.io;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Objects;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** The feature cache guard is exercised without starting a platform-specific prediction runtime. */
public class ContextFeatureScorerTest {
	@Rule public TemporaryFolder tempFolder = new TemporaryFolder();

	@Test
	public void rejectsUnverifiedExistingFeatureCache() throws Exception {
		File raw = copy("/org/searlelab/context/io/IL2A_GPFDIA_0combined_masked0_assay.dia", "run.dia");
		File library = copy("/org/searlelab/context/io/IL2_and_IL15_Combo.elib", "library.elib");
		File fasta = copy("/org/searlelab/context/mprophet/mus_musculus_reviewed_uniprot.fasta", "proteins.fasta");
		File massList = copy("/org/searlelab/context/io/IL2A_GPFDIA_0combined_masked0_assay.txt", "assay.txt");
		String baseName = new File(tempFolder.getRoot(), "run").getAbsolutePath();
		Files.writeString(new File(baseName + ".features.txt").toPath(),
				"id\tLabel\tScanNr\tprimary\tsequence\tProteins\n");
		try {
			ContextFeatureScorer.scoreFeaturesForContext(library, raw, fasta, baseName, massList.getAbsolutePath(), 0);
			fail("Expected an unverified cache to be rejected");
		} catch (IOException expected) {
			assertTrue(expected.getMessage().contains("pair sidecar or identity manifest"));
		}
	}

	private File copy(String resource, String name) throws Exception {
		URL url = Objects.requireNonNull(getClass().getResource(resource));
		File destination = new File(tempFolder.getRoot(), name);
		Files.copy(new File(url.toURI()).toPath(), destination.toPath());
		return destination;
	}
}
