package org.searlelab.context.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import edu.washington.gs.maccoss.encyclopedia.algorithms.scribe.ScribeTwoSearchParameters;

public class ScribeTwoFeatureScorerCLITest {

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void parsesFolderArgumentsAndScribeOverrides() throws Exception {
		File inputs = temporaryFolder.newFolder("inputs");
		File library = temporaryFolder.newFile("library.elib");
		File fasta = temporaryFolder.newFile("proteins.fasta");
		File output = new File(temporaryFolder.getRoot(), "output");

		ScribeTwoFeatureScorerCLI.FolderOptions options = ScribeTwoFeatureScorerCLI.parseFolderArguments(new String[] {
				"--dia-folder", inputs.getAbsolutePath(), "--library", library.getAbsolutePath(), "--fasta",
				fasta.getAbsolutePath(), "--output-directory", output.getAbsolutePath(), "--start-seed", "3",
				"--end-seed", "5", "--input-prefix", "_bootstrap", "-numberOfThreadsUsed", "2" });

		assertEquals(inputs, options.diaFolder());
		assertEquals(library, options.library());
		assertEquals(fasta, options.fasta());
		assertEquals(output.toPath(), options.outputDirectory());
		assertEquals(3, options.startSeed());
		assertEquals(5, options.endSeed());
		assertEquals("_bootstrap", options.inputPrefix());
		assertEquals("2", options.scribeSettings().get("-numberOfThreadsUsed"));
		assertThrows(UnsupportedOperationException.class,
				() -> options.scribeSettings().put("-numberOfThreadsUsed", "9"));
	}

	@Test
	public void defaultsFolderSeedRangeAndPrefix() throws Exception {
		ScribeTwoFeatureScorerCLI.FolderOptions options = parseRequiredFolderArguments();

		assertEquals(1, options.startSeed());
		assertEquals(100, options.endSeed());
		assertEquals(AssaySchedulePairMatcher.DEFAULT_INPUT_PREFIX, options.inputPrefix());
	}

	@Test
	public void rejectsInvalidFolderArguments() throws Exception {
		String[] required = requiredFolderArguments();
		assertTrue(assertThrows(IllegalArgumentException.class,
				() -> ScribeTwoFeatureScorerCLI.parseFolderArguments(append(required, "--unknown", "value")))
				.getMessage().contains("Unknown folder argument"));
		assertTrue(assertThrows(IllegalArgumentException.class,
				() -> ScribeTwoFeatureScorerCLI.parseFolderArguments(
						append(required, "--input-prefix", "_a", "--input-prefix", "_b")))
				.getMessage().contains("more than once"));
		assertTrue(assertThrows(IllegalArgumentException.class,
				() -> ScribeTwoFeatureScorerCLI.parseFolderArguments(new String[] { "positional", "value" }))
				.getMessage().contains("named arguments only"));
		assertTrue(assertThrows(IllegalArgumentException.class,
				() -> ScribeTwoFeatureScorerCLI.parseFolderArguments(append(required, "--start-seed", "6",
						"--end-seed", "5"))).getMessage().contains("greater than or equal"));
	}

	@Test
	public void executesEveryInputWithIsolatedParametersAndBasenameOutputs() throws Exception {
		ScribeTwoFeatureScorerCLI.FolderOptions options = parseRequiredFolderArguments();
		File first = temporaryFolder.newFile("sample_masked1_assay.dia");
		File second = temporaryFolder.newFile("sample_masked2_assay.dia");
		File firstSchedule = temporaryFolder.newFile("sample_masked1_assay.txt");
		File secondSchedule = temporaryFolder.newFile("sample_masked2_assay.txt");
		List<AssayScheduleInputPair> inputs = List.of(new AssayScheduleInputPair(1, first, firstSchedule),
				new AssayScheduleInputPair(2, second, secondSchedule));
		List<Integer> visitedSeeds = new ArrayList<>();
		List<Path> outputDirectories = new ArrayList<>();
		List<ScribeTwoSearchParameters> parameterObjects = new ArrayList<>();

		ScribeTwoFeatureScorerCLI.FolderRunSummary summary = ScribeTwoFeatureScorerCLI.executeFolderInputs(inputs,
				options, true, (input, library, fasta, output, parameters, trainContext) -> {
					visitedSeeds.add(input.seed());
					outputDirectories.add(output);
					parameterObjects.add(parameters);
					assertTrue(trainContext);
					if (input.seed() == 1) {
						throw new IllegalStateException("expected failure");
					}
				});

		assertEquals(List.of(1, 2), visitedSeeds);
		assertEquals(List.of(options.outputDirectory().resolve("sample_masked1_assay"),
				options.outputDirectory().resolve("sample_masked2_assay")), outputDirectories);
		assertNotSame(parameterObjects.get(0), parameterObjects.get(1));
		assertEquals(1, summary.completed());
		assertEquals(1, summary.failed());
		assertFalse(summary.outcomes().get(0).succeeded());
		assertTrue(summary.outcomes().get(1).succeeded());
		assertThrows(UnsupportedOperationException.class,
				() -> summary.outcomes().add(ScribeTwoFeatureScorerCLI.FolderRunOutcome.success(3, first)));
	}

	@Test
	public void preflightsEveryAssayScheduleBeforeExecution() throws Exception {
		File inputs = temporaryFolder.newFolder("preflight-inputs");
		File library = temporaryFolder.newFile("preflight.elib");
		File fasta = temporaryFolder.newFile("preflight.fasta");
		temporaryFolder.newFile("preflight-inputs/sample_masked1_assay.dia");
		temporaryFolder.newFile("preflight-inputs/sample_masked1_assay.txt");
		ScribeTwoFeatureScorerCLI.FolderOptions options = ScribeTwoFeatureScorerCLI.parseFolderArguments(new String[] {
				"--dia-folder", inputs.getAbsolutePath(), "--library", library.getAbsolutePath(), "--fasta",
				fasta.getAbsolutePath(), "--output-directory", new File(temporaryFolder.getRoot(), "preflight-output").getAbsolutePath(),
				"--start-seed", "1", "--end-seed", "1" });

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> ScribeTwoFeatureScorerCLI.findAndPreflightInputs(options));

		assertTrue(error.getMessage().contains("has no isolation windows"));
		assertFalse(options.outputDirectory().toFile().exists());
	}

	private ScribeTwoFeatureScorerCLI.FolderOptions parseRequiredFolderArguments() throws Exception {
		return ScribeTwoFeatureScorerCLI.parseFolderArguments(requiredFolderArguments());
	}

	private String[] requiredFolderArguments() throws Exception {
		File inputs = temporaryFolder.newFolder();
		File library = temporaryFolder.newFile();
		File fasta = temporaryFolder.newFile();
		File output = new File(temporaryFolder.getRoot(), "output-" + inputs.getName());
		return new String[] { "--dia-folder", inputs.getAbsolutePath(), "--library", library.getAbsolutePath(),
				"--fasta", fasta.getAbsolutePath(), "--output-directory", output.getAbsolutePath() };
	}

	private static String[] append(String[] source, String... additional) {
		List<String> values = new ArrayList<>(List.of(source));
		values.addAll(List.of(additional));
		return values.toArray(String[]::new);
	}
}
