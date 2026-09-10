package org.searlelab.context.io;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.searlelab.context.percolator.TrainingSeeds;

import edu.washington.gs.maccoss.encyclopedia.utils.CommandLineParser;

public class TrainingSeedsTest {
	
	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	@Test
	public void preservesRequestedOrderAndSupportsOneHundredSeeds() {
		assertEquals(List.of(7, 1, 2147483647), TrainingSeeds.parse("7, 1,2147483647"));
		String hundred = java.util.stream.IntStream.rangeClosed(1, 100).mapToObj(Integer::toString)
				.collect(java.util.stream.Collectors.joining(","));
		assertEquals(100, TrainingSeeds.parse(hundred).size());
	}

	@Test
	public void rejectsMalformedAndDuplicateSeeds() {
		for (String value : new String[] { null, "", " ", "1,", ",1", "1,,2", "1,1", "0", "-1", "x", "1.5",
				"2147483648" }) {

			assertThrows("Accepted " + value, IllegalArgumentException.class, () -> TrainingSeeds.parse(value));

		}

		assertThrows(IllegalArgumentException.class, () -> TrainingSeeds.validate(List.of()));
		assertThrows(IllegalArgumentException.class, () -> TrainingSeeds.validate(List.of(1, 1)));
		assertThrows(IllegalArgumentException.class, () -> TrainingSeeds.requireValid(0));
	}

	@Test
	public void consumesWorkflowFlagWithoutMutatingCaller() {
		HashMap<String, String> args = CommandLineParser
				.parseArguments(new String[] { "--seeds", "1,2", "-percolatorTrainingIterations", "10" });
		HashMap<String, String> passed = AllWorkflowExecutorCLI.encyclopediaArguments(args);

		assertFalse(passed.containsKey("--seeds"));
		assertEquals("10", passed.get("-percolatorTrainingIterations"));
		assertEquals("1,2", args.get("--seeds"));

		HashMap<String, String> missing = CommandLineParser.parseArguments(new String[] { "--seeds" });
		assertThrows(IllegalArgumentException.class, () -> TrainingSeeds.parse(missing.get("--seeds")));
	}


	@Test
	public void isolatesSamplesAndSeedsAndRejectsExistingOutputs() throws Exception {
		File root = temp.newFolder();
		var one = SeedExperiment.directory(root, "sample A", 1);
		
		assertNotEquals(one, SeedExperiment.directory(root, "sample A", 2));
		assertNotEquals(one, SeedExperiment.directory(root, "sample B", 1));
		
		SeedExperiment.requireFresh(root, "sample A", 1);
		Files.createDirectories(one);
		
		assertThrows(java.io.IOException.class, () -> SeedExperiment.requireFresh(root, "sample A", 1));
		
		for (String prefix : new String[] { "", " ", ".", "..", "../escape", "a/b", "a\\b", null }) {
			assertThrows(IllegalArgumentException.class, () -> SeedExperiment.directory(root, prefix, 1));
		}
	}

	@Test
	public void rejectsModelApplicationBeforeCreatingOutputs() throws Exception {
		File input = temp.newFile("input.txt");
		File root = new File(temp.getRoot(), "outputs");
		HashMap<String, String> args = new HashMap<>();
		args.put("-percolatorModelFile", "/missing-model");
		assertThrows(IllegalArgumentException.class, () -> AllWorkflowExecutorCLI.runAllSeeds(input, input, input,
				input, null, .01f, root, "sample", args, List.of(1)));
		assertFalse(root.exists());
	}

	@Test
	public void folderPreflightRejectsLaterCollisionBeforeStartingFirstSample() throws Exception {
		
		File inputs = temp.newFolder("inputs");
		File root = temp.newFolder("outputs");
		File fasta = temp.newFile("proteins.fasta");
		
		for (String sample : new String[] { "a", "b" }) {
			for (String suffix : new String[] { ".features.txt", "_background.features.txt",
					"_reference.features.txt" }) {
				Files.writeString(new File(inputs, sample + suffix).toPath(), "header\n");
			}
		}
		
		Files.createDirectories(SeedExperiment.directory(root, "b", 2));
		
		assertThrows(java.io.IOException.class, () -> AllWorkflowExecutorCLI.runAllOnFolder(inputs, fasta, null, .01f,
				root, new HashMap<>(), List.of(1, 2)));
		assertFalse(new File(root, "a").exists());
	}

	@Test
	public void missingPartnerAndInvalidFdrFailBeforeOutputs() throws Exception {
		File inputs = temp.newFolder("unmatched");
		File root = new File(temp.getRoot(), "not-created");
		File fasta = temp.newFile("db.fasta");
		Files.writeString(new File(inputs, "a.features.txt").toPath(), "header\n");

		assertThrows(java.io.IOException.class, () -> AllWorkflowExecutorCLI.runAllOnFolder(inputs, fasta, null, .01f,
				root, new HashMap<>(), List.of(1)));
		assertThrows(IllegalArgumentException.class, () -> AllWorkflowExecutorCLI.runAllSeeds(fasta, fasta, fasta,
				fasta, null, Float.NaN, root, "sample", new HashMap<>(), List.of(1)));
		assertFalse(root.exists());
	}
}