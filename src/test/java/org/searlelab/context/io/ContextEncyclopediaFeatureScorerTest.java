package org.searlelab.context.io;

import static org.junit.Assert.*;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Objects;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.searlelab.context.datastructures.ScoredFeature;

public class ContextFeatureScorerTest {

	@Rule
	public TemporaryFolder tempFolder = TemporaryFolder.builder().assureDeletion().build();
	
	@Test
	public void smokeTest() throws Exception {
		// This tests if the ContextFeatureScorer can find the correct files and return scored features 
		
		// Locate the files for the test	
		URL rawFileName = Objects.requireNonNull(getClass().getResource("/org/searlelab/context/io/IL2A_GPFDIA_0combined_masked0_assay.dia"));
		URL libraryFileName = Objects.requireNonNull(getClass().getClassLoader().getResource("org/searlelab/context/io/IL2_and_IL15_Combo.elib"));
		URL fastaFileName = Objects.requireNonNull(getClass().getClassLoader().getResource("org/searlelab/context/mprophet/mus_musculus_reviewed_uniprot.fasta"));
		URL massListFileName = Objects.requireNonNull(getClass().getClassLoader().getResource("org/searlelab/context/io/IL2A_GPFDIA_0combined_masked0_assay.txt"));

		assertNotNull("DIA file was not found.", rawFileName);
		assertNotNull("Library was not found.", libraryFileName);
		assertNotNull("Fasta was not found.", fastaFileName);
		assertNotNull("Mass list was not found", massListFileName);

		
		// Copy the files to the temporary directory
		Path rawFilePath = Paths.get(rawFileName.toURI());
		Path libraryFilePath = Paths.get(libraryFileName.toURI());
		Path fastaFilePath = Paths.get(fastaFileName.toURI());
		
		String massListPath = Paths.get(massListFileName.toURI()).toString();

		File rawFile = rawFilePath.toFile(); 
		File library = libraryFilePath.toFile();
		File fasta = fastaFilePath.toFile();
		
		String baseName = rawFile.toString().replaceFirst("\\.dia$", "");

			ArrayList<ScoredFeature> partitionedFeatures = ContextFeatureScorer.scoreFeaturesForContext(library, rawFile, fasta, baseName, massListPath, 0);
			assertNotNull(partitionedFeatures);
			
			System.out.println(partitionedFeatures.size() + " features scored and partitioned.");
			System.out.println("Last feature in the list: " + partitionedFeatures.get(partitionedFeatures.size() - 1));
			
		Path referenceOutput = Paths.get(baseName + "_reference.features.txt");
		Path backgroundOutput = Paths.get(baseName + "_background.features.txt");
		
		assertTrue("Reference feature file was not created.", Files.exists(referenceOutput));
		assertTrue("Background feature file was not created.", Files.exists(backgroundOutput));
		
		System.out.println(partitionedFeatures.size() + " paritioned features were returned.");
		int featuresSize = 17;
		assertTrue(partitionedFeatures.size()==featuresSize);
		

		}
	}


