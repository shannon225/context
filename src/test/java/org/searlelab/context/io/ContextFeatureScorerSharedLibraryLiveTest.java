package org.searlelab.context.io;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.Objects;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Two acquisitions in one folder share one predicted working library; the second run reuses it unchanged. */
public class ContextFeatureScorerSharedLibraryLiveTest {
	@Rule public TemporaryFolder tempFolder = new TemporaryFolder();

	@Test
	public void runsOfOneLibraryShareItsWorkingLibrary() throws Exception {
		// JChronologer 1.0.10 ships PyTorch 2.7.1 natives for macOS ARM, Linux x86_64, and Windows x86_64.
		assumeFalse(System.getProperty("os.name").toLowerCase().contains("mac")
				&& System.getProperty("os.arch").equals("x86_64"));
		File first = copy("/org/searlelab/context/io/IL2A_GPFDIA_0combined_masked0_assay.dia", "first.dia");
		File second = copy("/org/searlelab/context/io/IL2A_GPFDIA_0combined_masked0_assay.dia", "second.dia");
		File library = copy("/org/searlelab/context/io/IL2_and_IL15_Combo.elib", "library.elib");
		File fasta = copy("/org/searlelab/context/mprophet/mus_musculus_reviewed_uniprot.fasta", "proteins.fasta");
		File massList = copy("/org/searlelab/context/io/IL2A_GPFDIA_0combined_masked0_assay.txt", "assay.txt");
		String firstBase = new File(tempFolder.getRoot(), "first").getAbsolutePath();
		String secondBase = new File(tempFolder.getRoot(), "second").getAbsolutePath();

		int firstCount = ContextFeatureScorer.scoreFeaturesForContext(library, first, fasta, firstBase, massList.getAbsolutePath(), 0).size();
		File[] libraries = workingLibraries();
		assertEquals(1, libraries.length);
		byte[] built = Files.readAllBytes(libraries[0].toPath());
		long builtModified = libraries[0].lastModified();

		int secondCount = ContextFeatureScorer.scoreFeaturesForContext(library, second, fasta, secondBase, massList.getAbsolutePath(), 0).size();
		assertEquals(firstCount, secondCount);
		assertEquals("no second working library", 1, workingLibraries().length);
		assertArrayEquals("the shared working library is reused unchanged", built, Files.readAllBytes(libraries[0].toPath()));
		assertEquals(builtModified, libraries[0].lastModified());
		assertEquals("identical acquisitions give identical features", Files.readString(new File(firstBase + ".features.txt").toPath()),
				Files.readString(new File(secondBase + ".features.txt").toPath()));

		String firstReference = Files.readString(new File(firstBase + "_reference.features.txt").toPath());
		assertEquals(firstCount, ContextFeatureScorer.scoreFeaturesForContext(library, first, fasta, firstBase,
				massList.getAbsolutePath(), 0).size());
		assertEquals("a rerun reuses the verified feature cache", firstReference,
				Files.readString(new File(firstBase + "_reference.features.txt").toPath()));
	}

	private File[] workingLibraries() {
		File directory = new File(new File(tempFolder.getRoot(), "prediction-cache"), SharedWorkingLibrary.DIRECTORY_NAME);
		File[] libraries = directory.listFiles((dir, name) -> name.endsWith(".elib"));
		return libraries == null ? new File[0] : libraries;
	}

	private File copy(String resource, String name) throws Exception {
		URL url = Objects.requireNonNull(getClass().getResource(resource));
		File destination = new File(tempFolder.getRoot(), name);
		Files.copy(new File(url.toURI()).toPath(), destination.toPath());
		return destination;
	}
}
