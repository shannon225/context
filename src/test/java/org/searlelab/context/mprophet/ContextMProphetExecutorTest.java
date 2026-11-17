package org.searlelab.context.mprophet;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.io.Files;

public class ContextMProphetExecutorTest {

	@Rule 
	public TemporaryFolder tempFolder = TemporaryFolder.builder().assureDeletion().build();
	
	
	
	@Test
	public void rejectsLegacyFixtureWithUnsupportedEntrapments() throws Throwable {
		
		System.out.println("Running ContextMProphetExecutorTest.");

		// Locate resources
		URL libraryFileName = Objects.requireNonNull(getClass().getResource("/org/searlelab/context/io/IL2_and_IL15_Combo.elib"));
		URL fastaFileName = Objects.requireNonNull(getClass().getResource("/org/searlelab/context/mprophet/mus_musculus_reviewed_uniprot.fasta"));
		URL diaFileName = Objects.requireNonNull(getClass().getResource("/org/searlelab/context/io/IL2A_GPFDIA_0combined_masked0_assay.dia"));
		URL massListName = Objects.requireNonNull(getClass().getResource("/org/searlelab/context/io/IL2A_GPFDIA_0combined_masked0_assay.txt"));

		// Are all the paths mapped to real files? 
		assertNotNull("Library was not found. ", libraryFileName);
		assertNotNull("Fasta was not found. ", fastaFileName);
		assertNotNull("DIA file was not found. ", diaFileName);
		assertNotNull("Mass list was not found. ", massListName);
		
		// Convert URLs to Paths
		Path libraryPath = Paths.get(libraryFileName.toURI());
		Path fastaPath = Paths.get(fastaFileName.toURI());
		Path diaPath = Paths.get(diaFileName.toURI());
		Path massListPath = Paths.get(massListName.toURI());
		
		// Temporary directory 
		File tempDirectory = tempFolder.newFolder("context-mprophet-test");
		
		// Copy files into temp directory 
		File diaFile = new File(tempDirectory, diaPath.getFileName().toString());
		File library = new File(tempDirectory, libraryPath.getFileName().toString());
		File fasta = new File(tempDirectory, fastaPath.getFileName().toString());
		File massList = new File(tempDirectory, massListPath.getFileName().toString());
		
		Files.copy(diaPath.toFile(), diaFile);
		Files.copy(libraryPath.toFile(), library);
		Files.copy(fastaPath.toFile(), fasta);
		Files.copy(massListPath.toFile(), massList);
		
		// Ensure they were copied
		assertTrue("DIA file was not copied.", diaFile.isFile());
		assertTrue("Library file was not copied.", library.isFile());
		assertTrue("Fasta file was not copied.", fasta.isFile());
		assertTrue("Mass list file was not copied", massList.isFile());
		
	
		System.out.println("Processessing " + diaFile.getName());
		
		File expectedOutput = new File(tempDirectory, "IL2A_GPFDIA_0combined_masked0_assay_reference.features.pep.output.txt");
		
		String base = diaFile.getAbsolutePath().replaceFirst("\\.dia$", "");
		var error = org.junit.Assert.assertThrows(java.io.IOException.class,
				() -> org.searlelab.context.io.ContextFeatureScorer.scoreFeaturesForContext(library, diaFile, fasta,
						base, massList.getAbsolutePath(), 0));
		assertTrue(error.getMessage().contains("IITTDLAHHVQGK"));
		assertTrue(error.getMessage().contains("absent from both the source library and supplied FASTA"));
		ContextMProphetExecutor.executeContextMProphet(library.getAbsolutePath(), fasta.getAbsolutePath(), diaFile.getAbsolutePath(), massList.getAbsolutePath());
		org.junit.Assert.assertFalse("Invalid input must not produce apparent confidence output", expectedOutput.exists());
		// Positive model training/application is exercised by ScribeCalibratedWorkflowTest using valid captured features.
	}	
}
	

