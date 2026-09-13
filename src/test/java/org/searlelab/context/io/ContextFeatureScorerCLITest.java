package org.searlelab.context.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.Map;

import org.junit.Test;

public class ContextFeatureScorerCLITest {

	@Test
	public void parsesCustomInputPrefix() {
		Map<String, String> arguments = ContextFeatureScorerCLI.parseArguments(new String[] { "--library", "test.elib",
				"--fasta", "test.fasta", "--dia-folder", "inputs", "--input-prefix", "_bootstrap" });

		assertEquals("_bootstrap", arguments.get("--input-prefix"));
	}

	@Test
	public void rejectsDuplicateInputPrefix() {
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> ContextFeatureScorerCLI.parseArguments(
						new String[] { "--input-prefix", "_masked", "--input-prefix", "_bootstrap" }));

		assertEquals("Argument was supplied more than once: --input-prefix", error.getMessage());
	}
}
