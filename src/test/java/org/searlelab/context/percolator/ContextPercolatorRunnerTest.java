package org.searlelab.context.percolator;

import static org.junit.Assert.*;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.PercolatorExecutionData;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.PercolatorExecutor;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.PercolatorVersion;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;

public class ContextPercolatorRunnerTest {
	@Test
	public void buildsCommandOutsideRepositoryWithoutCreatingDebugOutput() throws Exception {
		File directory = temp.newFolder("clean launch with spaces");
		File log = new File(directory, "launch.log");
		String javaExecutable = new File(System.getProperty("java.home"), "bin/java").getAbsolutePath();
		String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
		Process process = new ProcessBuilder(javaExecutable, "-cp", classpath, CleanDirectoryCommand.class.getName())
				.directory(directory).redirectErrorStream(true).redirectOutput(log).start();
		assertTrue("Command probe timed out", process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS));
		assertEquals(Files.readString(log.toPath()), 0, process.exitValue());
		assertFalse(new File(directory, "target").exists());
	}

	public static class CleanDirectoryCommand {
		public static void main(String[] args) {
			File executable = new File("executable with spaces").getAbsoluteFile();
			PercolatorVersion version = new PercolatorVersion() {
				public int getMajorVersion() { return 3; }
				public File getPercolator() { return executable; }
			};
			ContextPercolatorExecutionData data = new ContextPercolatorRunnerTest().data(new File("."));
			String[] command = ContextPercolatorRunner.generateCommand(version, data, 1, 1);
			if (!command[0].equals(executable.getAbsolutePath())) throw new AssertionError("Executable path changed");
		}
	}

	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	private ContextPercolatorExecutionData data(File root) { 
		return new ContextPercolatorExecutionData(new File(root, "input.pin"), new File(root, "input.fasta"), new File(root, "targets.txt"), new File(root, "decoys.txt"), new File(root, "proteins.txt"), new File(root, "protein-decoys.txt"), SearchParameterParser.getDefaultParametersObject()); 
		}
	

	@Test public void commandMatchesPinnedDependencyExceptSeedForBothRounds() throws Exception { 
		PercolatorVersion version = new PercolatorVersion() { 
			
			public int getMajorVersion() { 
				return 3; 
				}
			
			public File getPercolator() { 
				return new File(temp.getRoot(), "executable with spaces"); 
				} 
	}; 
	
	Method upstream = PercolatorExecutor.class.getDeclaredMethod("generateCommand", PercolatorVersion.class, PercolatorExecutionData.class, int.class); 
	upstream.setAccessible(true); // Test-only comparison; production does not use reflection. 
	
	for (int round : new int[]{1, 2}) { 
		ContextPercolatorExecutionData data = data(temp.getRoot()); 
		String[] expected = (String[]) upstream.invoke(null, version, data, round); 
		for (int seed : new int[]{
				1, 17, Integer.MAX_VALUE}) 
		{ ArrayList<String> actual = new ArrayList<>(Arrays.asList(ContextPercolatorRunner.generateCommand(version, data, round, seed))); 
		assertEquals("--seed", actual.remove(1)); 
		assertEquals(Integer.toString(seed), actual.remove(1)); 
		assertArrayEquals(expected, actual.toArray(new String[0])); 
		} 
		} 
	} 
	
	@Test public void contextTrainingUsesContextMSArgumentsOnly() {
		File executable = new File(temp.getRoot(), "executable with spaces");
		PercolatorVersion version = new PercolatorVersion() {
			public int getMajorVersion() { return 3; }
			public File getPercolator() { return executable; }
		};
		ContextPercolatorExecutionData data = data(temp.getRoot());
		String[] expected = { executable.getAbsolutePath(), "--seed", "17",
				"--results-peptides", data.getPeptideOutputFile().getAbsolutePath(),
				"--weights", data.getWeightsFile(1).getAbsolutePath(),
				"--decoy-results-peptides", data.getPeptideDecoyFile().getAbsolutePath(),
				"--post-processing-tdc", "--override", "--protein-report-duplicates",
				data.getInputTSV().getAbsolutePath() };
		assertArrayEquals(expected, ContextPercolatorRunner.generateContextTrainingCommand(version, data, 1, 17));

		// EncyclopeDIA's training options must not leak in, whatever the parameters say.
		java.util.List<String> command = Arrays.asList(ContextPercolatorRunner.generateContextTrainingCommand(version, data, 1, 1));
		for (String absent : new String[] { "-N", "--maxiter", "--trainFDR", "--testFDR", "--no-terminate", "-y", "-Y", "--init-weights" }) {
			assertFalse(absent + " in " + command, command.contains(absent));
		}
		// The standard command is still EncyclopeDIA's (checked against upstream above).
		assertTrue(Arrays.asList(ContextPercolatorRunner.generateCommand(version, data, 1, 1)).contains("--no-terminate"));
	}

	@Test public void contextTrainingRejectsPercolatorTwoAndInvalidSeeds() {
		ContextPercolatorExecutionData data = data(temp.getRoot());
		PercolatorVersion two = new PercolatorVersion() {
			public int getMajorVersion() { return 2; }
			public File getPercolator() { return new File(temp.getRoot(), "percolator-2"); }
		};
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> ContextPercolatorRunner.generateContextTrainingCommand(two, data, 1, 1));
		assertTrue(error.getMessage(), error.getMessage().contains("Percolator 3"));
		PercolatorVersion three = new PercolatorVersion() {
			public int getMajorVersion() { return 3; }
			public File getPercolator() { return new File(temp.getRoot(), "percolator-3"); }
		};
		assertThrows(IllegalArgumentException.class, () -> ContextPercolatorRunner.generateContextTrainingCommand(three, data, 1, 0));
	}

	@Test public void preservesVersionThroughDependencyType() { 
		ContextPercolatorExecutionData data = data(temp.getRoot()); data.setPercolatorExecutableVersion("test-version"); 
		PercolatorExecutionData base = data; 
		assertEquals("test-version", base.getPercolatorExecutableVersion().orElseThrow()); 
		} 
	
	@Test public void nonzeroProcessExitIsReported() throws Exception { 
		File executable = temp.newFile("fail.sh"); 
		Files.writeString(executable.toPath(), "#!/bin/sh\nexit 7\n"); 
		assertTrue(executable.setExecutable(true)); 
		PercolatorVersion version = new PercolatorVersion() { 
			public int getMajorVersion() { return 3; } 
			public File getPercolator() { return executable; } 
			}; 
			ContextPercolatorExecutionData data = data(temp.getRoot()); 
			Exception e = assertThrows(Exception.class, ()-> ContextPercolatorRunner.executePercolatorTSV(version, data, .01f, data.getParameters().getAAConstants(), 1, 1)); 
			assertTrue(e.toString(), e.getMessage().contains("non-zero status: 7")); 
			assertFalse(data.getModelFile().exists()); 
			} 
	}
