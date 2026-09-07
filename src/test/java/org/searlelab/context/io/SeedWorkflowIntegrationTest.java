package org.searlelab.context.io;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.Test;

import org.searlelab.context.percolator.ContextPercolatorExecutionData;
import org.searlelab.context.percolator.ContextPercolatorRunner;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.PercolatorExecutor;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.PercolatorVersion;
import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchParameters;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;

// Opt in with -Dcontext.seedIntegration=true-Dcontext.pyisopep=/absolute/path/to/pyisopep. 

public class SeedWorkflowIntegrationTest {
	private Path root;
	private Path complete;
	private Path background;
	private Path reference;
	private Path fasta;
	private String pyisopep;
	private boolean resume;

	@Test public void actualEnginesRepeatAcrossProcessesAndMatchLegacySeedOne() throws Exception { 
		
		assumeTrue("Enable the real-engine integration test explicitly", Boolean.getBoolean("context.seedIntegration")); 
		pyisopep = System.getProperty("context.pyisopep", ""); 
		assertTrue("Supply -Dcontext.pyisopep with an executable", new File(pyisopep).canExecute()); 
		
		Files.createDirectories(Path.of("target")); 
		String existing = System.getProperty("context.resumeEvidence"); 
		resume = existing != null; 
		root = resume ? Path.of(existing).toAbsolutePath() : Files.createTempDirectory(Path.of("target"), "seed-validation-").toAbsolutePath(); 
		
		System.out.println("Seed experiment evidence: " + root);
		if (resume) { 
			complete = root.resolve("inputs/sample.features.txt");
			background = root.resolve("inputs/sample_background.features.txt"); 
			reference = root.resolve("inputs/sample_reference.features.txt"); 
			fasta = Path.of("src/test/resources/org/searlelab/context/mprophet/mus_musculus_reviewed_uniprot.fasta").toAbsolutePath(); 

			for (Path input : List.of(complete, background, reference, fasta)) 
				assertTrue(Files.isRegularFile(input)); 
		} else prepareFixture();

			int count = Integer.getInteger("context.seedCount", 2); 
			assertTrue("Use at least two seeds", count >= 2); 
			
			String seeds = java.util.stream.IntStream.rangeClosed(1, count)
					.mapToObj(Integer::toString)
					.collect(Collectors.joining(",")); 
			
			runCLI("first", false, seeds, 0); 
			runCLI("repeat", false, "1,2", 0); 
			
			for (int seed : new int[]{1, 2}) 
				compareRun(root.resolve("first/sample/seed_" + seed), root.resolve("repeat/sample/seed_" + seed)); 
			
			runCLI("legacy", false, null, 0); 
			
			compareRun(root.resolve("first/sample/seed_1"), root.resolve("legacy")); 
			
			runCLI("batch", true, "1,2", 0); for (int seed : new int[]{1, 2}) 
				compareRun(root.resolve("first/sample/seed_" + seed), root.resolve("batch/sample/seed_" + seed));
			
			assertTrue(Files.isDirectory(root.resolve("batch/sampleB/seed_2/target-mprophet"))); 
			
			runCLI("first", false, "1", 2); // Must refuse reuse before executing any engine. 
			
			nativeSeedOneParity(); 
			
			Properties first = properties(root.resolve("first/sample/seed_1/run.properties")); 
			Properties second = properties(root.resolve("first/sample/seed_2/run.properties")); 
			
			assertEquals("COMPLETE", first.getProperty("status")); 
			assertEquals("COMPLETE", second.getProperty("status")); 
			assertEquals("1", first.getProperty("seed")); 
			assertEquals("2", second.getProperty("seed"));
			assertEquals(first.getProperty("sha256.complete"), second.getProperty("sha256.complete"));
			
			boolean mprophetChanged = !first.getProperty("mprophet.standard.coefficients").equals(second.getProperty("mprophet.standard.coefficients")); 

			Files.writeString(root.resolve("RESULT.txt"), "PASS: " + count + " seeds completed; "
					+ "fresh-process repeats 1/2 (probabilities within 1e-12 and identical 0.01 decisions), legacy default, folder mode, output reuse refusal, and native seed-1 parity passed."
					+ "\nStandard mProphet coefficients changed between seeds 1/2: " + mprophetChanged + "\n"); 
		}

		private void prepareFixture() throws Exception {
			Path source = Path.of("src/test/resources/org/searlelab/context/percolator/standard_percolator_test.features.txt");
			String inputPrefix = System.getProperty("context.inputPrefix");
			
			if (inputPrefix != null) source = Path.of(inputPrefix + ".features.txt");
		
			List<String> lines = Files.readAllLines(source);
			Path inputs = Files.createDirectory(root.resolve("inputs"));
			
			complete = inputs.resolve("sample.features.txt");
			background = inputs.resolve("sample_background.features.txt");
			reference = inputs.resolve("sample_reference.features.txt");
			
			Files.copy(source, complete);
			
			List<String> bg = new ArrayList<>(List.of(lines.get(0)));
			List<String> ref = new ArrayList<>(List.of(lines.get(0))); // Partition by exact peptide sequence so repeated
			
			// PSMs stay in one set; preserve input order.
			int peptide = Arrays.asList(lines.get(0).split("\t")).indexOf("sequence");
			
			assertTrue(peptide >= 0);
			for (String line : lines.subList(1, lines.size())) {
				if (line.isBlank())
					continue;
				String sequence = line.split("\t", -1)[peptide];
				(Math.floorMod(sequence.hashCode(), 2) == 0 ? bg : ref).add(line);
			}
			
			if (inputPrefix == null) {
				Files.write(background, bg);
				Files.write(reference, ref);
			} else {
				Files.copy(Path.of(inputPrefix + "_background.features.txt"), background);
				Files.copy(Path.of(inputPrefix + "_reference.features.txt"), reference);
				bg = Files.readAllLines(background);
				ref = Files.readAllLines(reference);
			}
			
			for (String suffix : new String[] { ".features.txt", "_background.features.txt", "_reference.features.txt" }) {
				Files.copy(inputs.resolve("sample" + suffix), inputs.resolve("sampleB" + suffix));
			}
			
			fasta = Path.of("src/test/resources/org/searlelab/context/mprophet/mus_musculus_reviewed_uniprot.fasta")
					.toAbsolutePath();
			Files.writeString(root.resolve("fixture.txt"),
					"Source: " + source.toAbsolutePath() + "\nComplete rows: " + (lines.size() - 1) + "\nBackground rows: "
							+ (bg.size() - 1) + "\nReference rows: " + (ref.size() - 1) + "\n");
		}

	

	private void runCLI(String label, boolean folder, String seeds, int expectedExit) throws Exception {
		if (resume && !folder && seeds != null && expectedExit == 0) {
			boolean completed = true;
			for (String seed : seeds.split(",")) {
				Path manifest = root.resolve(label + "/sample/seed_" + seed + "/run.properties");
				completed &= Files.isRegularFile(manifest)
						&& properties(manifest).getProperty("status").equals("COMPLETE");
			}

			if (completed)
				return;
		}

		List<String> command = new ArrayList<>(
				List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp",
						System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
						AllWorkflowExecutorCLI.class.getName(), "-f", fasta.toString(), "-o",
						root.resolve(label).toString(), "--pyisopep", pyisopep));
		if (folder)
			command.addAll(List.of("--features-folder", complete.getParent().toString()));
		else
			command.addAll(List.of("--features", complete.toString(), "--background", background.toString(),
					"--reference", reference.toString(), "--prefix", "sample"));

		if (seeds != null)
			command.addAll(List.of("--seeds", seeds));

		Path log = root.resolve(label + (expectedExit == 0 ? "" : "-refused") + ".log");
		Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
		long defaultMinutes = Math.max(30L, 5L * (seeds == null ? 1 : seeds.split(",").length));
		long timeoutMinutes = Long.getLong("context.seedTimeoutMinutes", defaultMinutes);

		if (!process.waitFor(timeoutMinutes, TimeUnit.MINUTES)) {
			process.descendants().forEach(ProcessHandle::destroyForcibly);
			process.destroyForcibly();
			fail("Timed out; see " + log);
		}
		assertEquals("See " + log, expectedExit, process.exitValue());
	}

	private void compareRun(Path left, Path right) throws Exception {
		try (var paths = Files.walk(left)) {
			for (Path path : paths.filter(Files::isRegularFile).toList()) {
				String name = path.getFileName().toString();
				if (!(name.endsWith(".txt") || name.endsWith(".model")) || name.endsWith(".command.txt"))
					continue;
				Path other = right.resolve(left.relativize(path));
				assertTrue("Missing " + other, Files.isRegularFile(other));
				compareScientificFile(path, other);
			}
		}
		Path l = left.resolve("run.properties"), r = right.resolve("run.properties");
		if (Files.exists(l) && Files.exists(r)) {
			Properties a = properties(l), b = properties(r);
			for (String key : a.stringPropertyNames())
				if (key.startsWith("mprophet."))
					assertEquals(key, a.getProperty(key), b.getProperty(key));
		}
	}

	private void compareScientificFile(Path left, Path right) throws Exception {
		List<String> a = scientificLines(left), b = scientificLines(right);
		assertEquals("Row count: " + left, a.size(), b.size());
		boolean model = left.getFileName().toString().contains(".weights") || left.toString().endsWith(".model");
		if (model) {
			assertTrue("Model mismatch: " + left + " versus " + right, a.equals(b));
			return;
		}
		String[] header = Files.readAllLines(left).get(0).split("\t", -1);
		for (int row = 0; row < a.size(); row++) {
			String[] x = a.get(row).split("\t", -1), y = b.get(row).split("\t", -1);
			assertEquals("Column count: " + left, x.length, y.length);
			for (int col = 0; col < x.length; col++) {
				if (x[col].equals(y[col]))
					continue;
				String name = col < header.length ? header[col] : "";

				String where = left + " row " + row + " column " + name;
				if (name.equals("q-value") || name.equals("posterior_error_prob") || name.equals("pyIsoPEP PEP")
						|| name.equals("pyIsoPEP FDR") || name.equals("pyIsoPEP q-value from FDR")) {
					double first = Double.parseDouble(x[col]), second = Double.parseDouble(y[col]);
					assertTrue(where + " must be finite", Double.isFinite(first) && Double.isFinite(second));
					assertEquals(where, first, second, 1e-12);
					assertEquals(where + " threshold decision", first <= .01, second <= .01);
				} else
					assertEquals(where, x[col], y[col]);
			}
		}
	}

	private List<String> scientificLines(Path path) throws Exception {
		var lines = Files.readAllLines(path).stream().filter(s -> !s.startsWith("#") && !s.isBlank());
		if (path.getFileName().toString().contains(".weights") || path.toString().endsWith(".model"))
			return lines.toList();
		return lines.sorted().toList();
	}

	private Properties properties(Path file) throws Exception {
		Properties properties = new Properties();
		try (var input = Files.newBufferedReader(file)) {
			properties.load(input);
		}
		return properties;
	}

	private void nativeSeedOneParity() throws Exception {

		var parameters = SearchParameterParser.getDefaultParametersObject();
		PercolatorVersion version = parameters.getPercolatorVersionNumber();

		for (int round : new int[] { 1, 2 }) {
			Path old = Files.createDirectory(root.resolve("native-old-" + round));
			Path fresh = Files.createDirectory(root.resolve("native-new-" + round));
			ContextPercolatorExecutionData a = execution(old, parameters), b = execution(fresh, parameters);
			PercolatorExecutor.executePercolatorTSV(version, a, .01f, parameters.getAAConstants(), round);
			ContextPercolatorRunner.executePercolatorTSV(version, b, .01f, parameters.getAAConstants(), round, 1);

			assertEquals(scientificLines(a.getPeptideOutputFile().toPath()),
					scientificLines(b.getPeptideOutputFile().toPath()));
			assertEquals(scientificLines(a.getPeptideDecoyFile().toPath()),
					scientificLines(b.getPeptideDecoyFile().toPath()));
			assertEquals(scientificLines(a.getModelFile().toPath()), scientificLines(b.getModelFile().toPath()));
			assertEquals(a.getPercolatorExecutableVersion(), b.getPercolatorExecutableVersion());
		}
	}

	private ContextPercolatorExecutionData execution(Path directory, SearchParameters parameters) {
		return new ContextPercolatorExecutionData(complete.toFile(), fasta.toFile(),
				directory.resolve("targets.txt").toFile(), directory.resolve("decoys.txt").toFile(),
				directory.resolve("proteins.txt").toFile(), directory.resolve("protein-decoys.txt").toFile(),
				parameters);

	}
}