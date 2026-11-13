package org.searlelab.context.io;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;

/**
 * CTX018-019: {@code mprophet -seed} trains with that seed, defaults to 1, and gives the same results as
 * AllWorkflowExecutorCLI's Context mProphet step for the same seed and inputs.
 */
public class ContextSeedOptionTest {
	private static final Path FIXTURE = Path.of("src/test/resources/org/searlelab/context/percolator/standard_percolator_test.features.txt");
	private static final Path FASTA = Path.of("src/test/resources/org/searlelab/context/mprophet/mus_musculus_reviewed_uniprot.fasta");
	private static final String[] OUTPUTS = { "s.peptide.reference.target.txt", "s.peptide.reference.decoy.txt",
			"s.peptide.background.target.txt", "s.peptide.background.decoy.txt" };

	@ClassRule
	public static TemporaryFolder folder = new TemporaryFolder();
	private static File background;
	private static File reference;

	/** Same split as SeedWorkflowIntegrationTest: by peptide sequence, so a peptide stays in one population. */
	@BeforeClass
	public static void splitFixture() throws Exception {
		List<String> lines = Files.readAllLines(FIXTURE);
		int peptide = Arrays.asList(lines.get(0).split("\t")).indexOf("sequence");
		assertTrue(peptide >= 0);
		List<String> bg = new ArrayList<>(List.of(lines.get(0)));
		List<String> ref = new ArrayList<>(List.of(lines.get(0)));
		for (String line : lines.subList(1, lines.size())) {
			if (line.isBlank()) continue;
			(Math.floorMod(line.split("\t", -1)[peptide].hashCode(), 2) == 0 ? bg : ref).add(line);
		}
		background = folder.newFile("s_background.features.txt");
		reference = folder.newFile("s_reference.features.txt");
		Files.write(background.toPath(), bg);
		Files.write(reference.toPath(), ref);
	}

	private static File cli(String name, String... extra) throws Exception {
		File root = folder.newFolder(name);
		List<String> args = new ArrayList<>(List.of("-background", background.getPath(), "-reference", reference.getPath(),
				"-f", FASTA.toString(), "-o", root.getPath(), "-prefix", "s", "--allow-legacy-features", "true"));
		args.addAll(List.of(extra));
		ContextMProphetCLI.run(args.toArray(new String[0]));
		return new File(root, "context-mprophet");
	}

	private static File allWorkflowStep(String name, int seed) throws Exception {
		File root = folder.newFolder(name);
		AllWorkflowExecutorCLI.runContextMProphet(background, reference, FASTA.toFile(),
				AllWorkflowExecutorCLI.parseSearchParameters(SearchParameterParser.getDefaultParameters(), 0.01f), 0.01f,
				root, "s", seed);
		return new File(root, "context-mprophet");
	}

	private static void assertSameOutputs(File expected, File actual) throws Exception {
		for (String output : OUTPUTS) {
			assertArrayEquals(output, Files.readAllBytes(new File(expected, output).toPath()),
					Files.readAllBytes(new File(actual, output).toPath()));
		}
	}

	@Test
	public void seedOptionMatchesTheAllWorkflowStepForTheSameSeed() throws Exception {
		assertSameOutputs(allWorkflowStep("all-workflow-seed-3", 3), cli("cli-seed-3", "-seed", "3"));
	}

	@Test
	public void noSeedOptionMeansSeedOne() throws Exception {
		assertSameOutputs(cli("cli-explicit-seed-1", "-seed", "1"), cli("cli-default-seed"));
		assertSameOutputs(allWorkflowStep("all-workflow-seed-1", 1), cli("cli-default-seed-again"));
	}

	@Test
	public void invalidSeedsAreRejectedBeforeAnyOutput() throws Exception {
		for (String value : new String[] { "0", "-2", "x" }) {
			File root = folder.newFolder("bad-seed-" + value);
			IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> ContextMProphetCLI.run(
					new String[] { "-background", background.getPath(), "-reference", reference.getPath(), "-f",
							FASTA.toString(), "-o", root.getPath(), "--allow-legacy-features", "true", "-seed", value }));
			assertTrue(error.getMessage(), error.getMessage().toLowerCase().contains("seed"));
			assertEquals("no output for a rejected seed", 0, root.list().length);
		}
	}
}
