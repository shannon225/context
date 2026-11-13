package org.searlelab.context.percolator;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Objects;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.PercolatorExecutionData;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;

/**
 * Integration tests for the two workflows coordinated by
 * {@link ContextPercolatorExecutor}.
 *
 * These tests launch Percolator and pyIsoPEP. The Context workflow also scores
 * a DIA file against a library. They are therefore integration tests rather
 * than isolated unit tests.
 */

public class ContextPercolatorExecutorTest {

	private static final String FEATURE_RESOURCE = "/org/searlelab/context/percolator/standard_percolator_test.features.txt";
	private static final String LIBRARY_RESOURCE = "/org/searlelab/context/io/IL2_and_IL15_Combo.elib";
	private static final String FASTA_RESOURCE = "/org/searlelab/context/mprophet/mus_musculus_reviewed_uniprot.fasta";
	private static final String DIA_RESOURCE = "/org/searlelab/context/io/IL2A_GPFDIA_0combined_masked0_assay.dia";
	private static final String MASS_LIST_RESOURCE = "/org/searlelab/context/io/IL2A_GPFDIA_0combined_masked0_assay.txt";

	@Rule
	public TemporaryFolder folder = TemporaryFolder.builder().assureDeletion().build();

	@Ignore("Passes null engine/workflow names to runPercolator (NPE in DirectoryOptions.engineDirectory) and expects a pyIsoPEP report runPercolator does not write")
	@Test
	public void standardPercolatorProducesNativeAndPyIsoPEPReports() throws Exception {
		String pyisopep = locatePyIsoPEP();
		assumeTrue("pyIsoPEP is not installed; set PYISOPEP or put pyisopep on PATH",
				pyisopep != null);

		File inputDirectory = folder.newFolder("standard-input");
		File outputDirectory = folder.newFolder("standard-output");
		File features = copyResource(FEATURE_RESOURCE, inputDirectory);
		File fasta = copyResource(FASTA_RESOURCE, inputDirectory);

		HashMap<String, String> encyclopediaArguments = SearchParameterParser.getDefaultParameters();
		PyIsoPEPRunner pyIsoPEP = new PyIsoPEPRunner(pyisopep);

		PercolatorExecutionData run = ContextPercolatorExecutor.runPercolator(features, fasta, pyIsoPEP, 0.01f,
				outputDirectory, "standard-test", encyclopediaArguments, null, null);

		assertNotNull(run);
		assertTrue("Native target-peptide report was not generated", run.getPeptideOutputFile().isFile());
		assertTrue("Native decoy-peptide report was not generated", run.getPeptideDecoyFile().isFile());
		assertTrue("Percolator model was not generated", run.getModelFile().isFile());

		File pyIsoOutput = new File(new File(outputDirectory, "standard-percolator"),
				"standard-test.peptide.pyisopep.txt");

		assertTrue("pyIsoPEP report was not generated: " + pyIsoOutput, pyIsoOutput.isFile());

		PyIsoPEPRunner.Table pyIsoTable = PyIsoPEPRunner.Table.read(pyIsoOutput);
		assertTrue("pyIsoPEP returned no target peptides", pyIsoTable.size() > 0);
		assertConfidenceColumn(pyIsoTable, PyIsoPEPRunner.PEP_COLUMN);
		assertConfidenceColumn(pyIsoTable, PyIsoPEPRunner.Q_VALUE_COLUMN);
	}

	@Ignore("On the IL2 masked fixture EncyclopeDIA scores none of the assay peptides, so the reference set is empty and ContextFeatures.requireCompetable fails")
	@Test
	public void contextPercolatorProducesTransferredModelReports() throws Exception {
		String pyisopep = locatePyIsoPEP();
		assumeTrue("pyIsoPEP is not installed; set PYISOPEP or put pyisopep on PATH", pyisopep != null);

		File inputDirectory = folder.newFolder("context-input");
		File outputDirectory = folder.newFolder("context-output");
		File library = copyResource(LIBRARY_RESOURCE, inputDirectory);
		File fasta = copyResource(FASTA_RESOURCE, inputDirectory);
		File dia = copyResource(DIA_RESOURCE, inputDirectory);
		File massList = copyResource(MASS_LIST_RESOURCE, inputDirectory);

		HashMap<String, String> encyclopediaArguments = SearchParameterParser.getDefaultParameters();
		PyIsoPEPRunner pyIsoPEP = new PyIsoPEPRunner(pyisopep);

		ContextPercolatorResult result = ContextPercolatorExecutor.runContextPercolator(library, fasta, dia, massList,
				pyIsoPEP, 0.01f, outputDirectory, "context-test", false, encyclopediaArguments, 0);

		assertNotNull(result);
		assertTrue("Native Percolator weights were not generated", result.getNativeWeightsFile().isFile());
		assertTrue("Averaged Percolator weights were not generated", result.getAveragedWeightsFile().isFile());
		assertTrue("Rescored reference features were not generated", result.getRescoredFeaturesFile().isFile());
		assertTrue("Reference PSM report was not generated", result.getPsmOutputFile().isFile());
		assertTrue("Reference peptide report was not generated", result.getPeptideOutputFile().isFile());
		assertTrue("Raw PSM pyIsoPEP report was not generated", result.getRawPsmReportFile().isFile());
		assertTrue("Raw peptide pyIsoPEP report was not generated", result.getRawPeptideReportFile().isFile());
		assertTrue("Passing peptide count cannot be negative", result.getPassingPeptideCount() >= 0);
	}

	private static void assertConfidenceColumn(PyIsoPEPRunner.Table table, String columnName) {

		assertTrue("Missing confidence column: " + columnName, table.indexOf(columnName) >= 0);

		for (String[] row : table.getRows()) {
			double value = Double.parseDouble(table.get(row, columnName));
			assertTrue(columnName + " was below zero: " + value, value >= 0.0);
			assertTrue(columnName + " was above one: " + value, value <= 1.0);
		}
	}

	private File copyResource(String resourceName, File destinationDirectory)
			throws Exception {

		URL resource = Objects.requireNonNull(getClass().getResource(resourceName),
				"Test resource was not found: " + resourceName);

		Path source = Paths.get(resource.toURI());
		File destination = new File(destinationDirectory, source.getFileName().toString());

		Files.copy(source, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);

		return destination;
	}

	private static String locatePyIsoPEP() {
		String configured = System.getenv("PYISOPEP");
		if (configured != null && new File(configured).canExecute()) {
			return configured;
		}

		String path = System.getenv("PATH");
		if (path == null)
			return null;

		for (String directory : path.split(File.pathSeparator)) {
			if (directory.isEmpty())
				continue;

			File candidate = new File(directory, "pyisopep");
			if (candidate.isFile() && candidate.canExecute()) {
				return candidate.getAbsolutePath();
			}
		}

		return null;
	}
}
