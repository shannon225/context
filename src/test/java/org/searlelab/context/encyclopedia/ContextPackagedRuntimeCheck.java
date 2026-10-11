package org.searlelab.context.encyclopedia;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import ai.djl.util.Platform;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.PercolatorVersion;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryEntry;
import edu.washington.gs.maccoss.encyclopedia.filereaders.LibraryFile;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;

/** Java 17 source launcher check: its only classpath entry must be the packaged Context jar. */
public class ContextPackagedRuntimeCheck {
	public static void main(String[] args) throws Exception {
		ContextPredictionRuntime.requireLibraryApi();
		if (args.length != 1) throw new IllegalArgumentException("Expected metadata or live");
		if ("metadata".equals(args[0])) {
			String os = System.getProperty("os.name");
			String arch = System.getProperty("os.arch");
			try (ContextPredictionRuntime runtime = new ContextPredictionRuntime()) {
				for (String[] platform : new String[][] {{"Windows 11", "amd64", "win-x86_64"},
						{"Linux", "amd64", "linux-x86_64"}, {"Mac OS X", "aarch64", "osx-aarch64"}}) {
					System.setProperty("os.name", platform[0]);
					System.setProperty("os.arch", platform[1]);
					Platform detected = Platform.detectPlatform("pytorch");
					if (detected.isPlaceholder() || !detected.toString().contains(platform[2])) {
						throw new AssertionError("Bundled native discovery failed: " + detected);
					}
					for (String library : detected.getLibraries()) {
						String resource = "pytorch/cpu/" + platform[2] + "/" + library;
						if (Thread.currentThread().getContextClassLoader().getResource(resource) == null) {
							throw new AssertionError("Missing packaged native: " + resource);
						}
					}
					System.out.println("Bundled native metadata and libraries: " + detected);
				}
			} finally {
				System.setProperty("os.name", os);
				System.setProperty("os.arch", arch);
			}
		} else if ("live".equals(args[0])) {
			predict();
			File executable = PercolatorVersion.DEFAULT_VERSION.getPercolator();
			Process process = new ProcessBuilder(executable.getAbsolutePath(), "--help")
					.redirectErrorStream(true).inheritIO().start();
			if (process.waitFor() != 0) throw new AssertionError("Bundled Percolator could not run");
		} else {
			throw new IllegalArgumentException("Unknown check: " + args[0]);
		}
	}

	private static void predict() throws Exception {
		var parameters = SearchParameterParser.getDefaultParametersObject();
		var constants = parameters.getAAConstants();
		byte charge = 2;
		String sequence = "PEPTIDEK";
		LibraryEntry target = new LibraryEntry("synthetic packaged Context runtime check", new HashSet<>(),
				constants.getChargedMass(sequence, charge), charge, sequence, 1, 10f, 1f,
				new double[] {250, 350}, new float[] {10, 5}, Optional.empty(), constants);
		Path directory = Files.createTempDirectory("context-packaged-prediction-");
		LibraryFile source = new LibraryFile();
		try {
			source.openFile();
			source.addEntries(new ArrayList<>(List.of(target)), false);
			try (ContextWorkingLibraryPreparer.Result result = ContextWorkingLibraryPreparer.prepare(source,
					parameters, new ContextWorkingLibraryPreparer.Settings(27, charge, true),
					directory.resolve("working.elib").toFile(), directory.resolve("assignments.tsv").toFile())) {
				if (result.retainedTargets() != 1 || result.retainedDecoys() != 1
						|| !"PAIRED".equals(result.assignments().get(0).status())) {
					throw new AssertionError("Expected one predicted target/decoy pair");
				}
			}
			System.out.println("Packaged target/decoy prediction and persistence passed");
		} finally {
			source.close();
			try (var files = Files.walk(directory)) {
				for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(file);
			}
		}
	}
}
