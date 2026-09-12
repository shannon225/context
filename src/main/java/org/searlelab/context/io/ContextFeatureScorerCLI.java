package org.searlelab.context.io;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ContextFeatureScorerCLI {

	private static final int DEFAULT_START_SEED = 1;
	private static final int DEFAULT_END_SEED = 100;
	private static final int DEFAULT_SHUFFLED_SEED = 2;

	private static final Pattern MASKED_SEED_PATTERN = Pattern.compile("_masked([0-9]+)(?=_|\\.)",
			Pattern.CASE_INSENSITIVE);

	private static final Set<String> ALLOWED_ARGUMENTS = Set.of("--library", "--fasta", "--dia-folder", "--start-seed",
			"--end-seed");

	public static void main(String[] args) {

		if (containsHelpFlag(args)) {
			printUsage();
			return;
		}

		try {
			Map<String, String> arguments = parseArguments(args);

			File library = requireReadableFile("Library", requireArgument(arguments, "--library"));

			File fasta = requireReadableFile("FASTA", requireArgument(arguments, "--fasta"));

			File diaFolder = requireReadableDirectory("DIA folder", requireArgument(arguments, "--dia-folder"));

			int startSeed = parseNonNegativeInteger(arguments.getOrDefault("--start-seed", Integer.toString(DEFAULT_START_SEED)), "--start-seed");

			int endSeed = parseNonNegativeInteger(arguments.getOrDefault("--end-seed", Integer.toString(DEFAULT_END_SEED)), "--end-seed");

			int shuffledSeed = parseNonNegativeInteger(arguments.getOrDefault("--shuffledSeed", Integer.toString(DEFAULT_SHUFFLED_SEED)), "--shuffled-seed");
			
			if (endSeed < startSeed) {
				throw new IllegalArgumentException("--end-seed must be greater than or equal to --start-seed.");
			}

			List<InputPair> inputs = findInputPairs(diaFolder, startSeed, endSeed);

			System.out.println("Found " + inputs.size() + " DIA/mass-list pairs covering seeds " + startSeed + " through " + endSeed + ".");

			int completed = 0;
			List<String> failures = new ArrayList<>();

			for (InputPair input : inputs) {
				System.out.println();
				System.out.println("Processing seed " + input.seed + ": " + input.diaFile.getName());

				String baseName = RawFiles.baseName(input.diaFile);

				try {
					int featureCount = ContextFeatureScorer.scoreFeaturesForContext(library, input.diaFile, fasta, baseName, input.massListFile.getAbsolutePath(), shuffledSeed).size();

					completed++;

					System.out.println("Finished seed " + input.seed + ": " + featureCount + " features were scored and partitioned.");

				} catch (Exception e) {
					failures.add("Seed " + input.seed + " (" + input.diaFile.getName() + "): " + e.getMessage());

					System.err.println("Feature scoring failed for seed " + input.seed + ".");

					e.printStackTrace();
				}
			}

			System.out.println();
			System.out.println("Batch scoring concluded.");
			System.out.println("Successfully processed: " + completed);
			System.out.println("Failed: " + failures.size());

			if (!failures.isEmpty()) {
				System.err.println();
				System.err.println("Failed inputs:");

				for (String failure : failures) {
					System.err.println("  " + failure);
				}

				System.exit(2);
			}

		} catch (IllegalArgumentException e) {
			System.err.println("Error: " + e.getMessage());
			System.err.println();
			printUsage();
			System.exit(1);
		}
	}

	private static List<InputPair> findInputPairs(File diaFolder, int startSeed, int endSeed) {

		File[] folderContents = diaFolder.listFiles();

		if (folderContents == null) {
			throw new IllegalArgumentException("Could not list files in: " + diaFolder.getAbsolutePath());
		}

		Map<Integer, File> diaFileBySeed = new TreeMap<>();

		for (File file : folderContents) {

			if (!file.isFile()) {
				continue;
			}

			if (!file.getName().toLowerCase().endsWith(".dia")) {
				continue;
			}

			Matcher matcher = MASKED_SEED_PATTERN.matcher(file.getName());

			if (!matcher.find()) {
				continue;
			}

			int seed = Integer.parseInt(matcher.group(1));

			if (seed < startSeed || seed > endSeed) {
				continue;
			}

			File previousFile = diaFileBySeed.putIfAbsent(seed, file);

			if (previousFile != null) {
				throw new IllegalArgumentException("More than one DIA file was found for seed " + seed + ": "
						+ previousFile.getName() + " and " + file.getName());
			}
		}

		List<Integer> missingSeeds = new ArrayList<>();

		for (int seed = startSeed; seed <= endSeed; seed++) {
			if (!diaFileBySeed.containsKey(seed)) {
				missingSeeds.add(seed);
			}
		}

		if (!missingSeeds.isEmpty()) {
			throw new IllegalArgumentException("No DIA file was found for seed(s): " + missingSeeds);
		}

		List<InputPair> inputs = new ArrayList<>();
		List<String> missingMassLists = new ArrayList<>();

		for (Map.Entry<Integer, File> entry : diaFileBySeed.entrySet()) {
			int seed = entry.getKey();
			File diaFile = entry.getValue();

			String baseName = RawFiles.baseName(diaFile);
			File massListFile = new File(baseName + ".txt");

			if (!massListFile.isFile() || !massListFile.canRead()) {
				missingMassLists.add("seed " + seed + ": " + massListFile.getAbsolutePath());
				continue;
			}

			inputs.add(new InputPair(seed, diaFile, massListFile));
		}

		if (!missingMassLists.isEmpty()) {
			throw new IllegalArgumentException(
					"Mass-list files were not found for:\n  " + String.join("\n  ", missingMassLists));
		}

		return inputs;
	}

	private static Map<String, String> parseArguments(String[] args) {

		if (args.length == 0) {
			throw new IllegalArgumentException("No arguments were provided.");
		}

		Map<String, String> arguments = new HashMap<>();

		for (int index = 0; index < args.length; index++) {
			String flag = args[index];

			if (!flag.startsWith("--")) {
				throw new IllegalArgumentException("Expected a named argument, but found: " + flag);
			}

			if (!ALLOWED_ARGUMENTS.contains(flag)) {
				throw new IllegalArgumentException("Unknown argument: " + flag);
			}

			if (index + 1 >= args.length || args[index + 1].startsWith("-")) {
				throw new IllegalArgumentException("Missing value for " + flag);
			}

			if (arguments.containsKey(flag)) {
				throw new IllegalArgumentException("Argument was supplied more than once: " + flag);
			}

			arguments.put(flag, args[++index]);
		}

		return arguments;
	}

	private static String requireArgument(Map<String, String> arguments, String flag) {

		String value = arguments.get(flag);

		if (value == null || value.trim().isEmpty()) {
			throw new IllegalArgumentException("Required argument is missing: " + flag);
		}

		return value;
	}

	private static File requireReadableFile(String description, String path) {

		File file = new File(path);

		if (!file.isFile() || !file.canRead()) {
			throw new IllegalArgumentException(description + " is not a readable file: " + file.getAbsolutePath());
		}

		return file;
	}

	private static File requireReadableDirectory(String description, String path) {

		File directory = new File(path);

		if (!directory.isDirectory() || !directory.canRead()) {
			throw new IllegalArgumentException(
					description + " is not a readable directory: " + directory.getAbsolutePath());
		}

		return directory;
	}

	private static int parseNonNegativeInteger(String value, String flag) {

		try {
			int parsedValue = Integer.parseInt(value);

			if (parsedValue < 0) {
				throw new IllegalArgumentException(flag + " cannot be negative.");
			}

			return parsedValue;

		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(flag + " must be an integer, but found: " + value);
		}
	}

	private static boolean containsHelpFlag(String[] args) {
		for (String argument : args) {
			if ("-h".equals(argument) || "--help".equals(argument)) {
				return true;
			}
		}

		return false;
	}

	private static void printUsage() {
		System.out.println("Usage:");
		System.out.println("  ContextFeatureScorerCLI " + "--library <library.elib> " + "--fasta <proteins.fasta> "
				+ "--dia-folder <folder> " + "[--start-seed <integer>] " + "[--end-seed <integer>]");
		System.out.println();
		System.out.println("Required:");
		System.out.println("  --library      Spectral library used for scoring.");
		System.out.println("  --fasta        FASTA protein database.");
		System.out.println("  --dia-folder   Folder containing paired .dia and .txt files.");
		System.out.println();
		System.out.println("Optional:");
		System.out.println("  --start-seed   First masked seed to process; default: " + DEFAULT_START_SEED);
		System.out.println("  --end-seed     Last masked seed to process; default: " + DEFAULT_END_SEED);
		System.out.println();
		System.out.println("Expected filename pairs:");
		System.out.println("  example_masked1_assay.dia");
		System.out.println("  example_masked1_assay.txt");
	}

	private static final class InputPair {

		private final int seed;
		private final File diaFile;
		private final File massListFile;

		private InputPair(int seed, File diaFile, File massListFile) {

			this.seed = seed;
			this.diaFile = diaFile;
			this.massListFile = massListFile;
		}
	}
}