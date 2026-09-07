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
	