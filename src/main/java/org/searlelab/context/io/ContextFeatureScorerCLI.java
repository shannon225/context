package org.searlelab.context.io;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ContextFeatureScorerCLI {

	private static final int DEFAULT_START_SEED = 1;
	private static final int DEFAULT_END_SEED = 1;
	private static final int DEFAULT_SHUFFLED_SEED = 2;

	private static final Set<String> ALLOWED_ARGUMENTS = Set.of("--library", "--fasta", "--dia-folder", "--start-seed",
			"--end-seed", "--input-prefix", "--prediction-nce", "--prediction-default-charge",
			"--adjust-nce-for-dia", "--candidate-diagnostics", "--allow-legacy-features", "--prediction-store",
			"--clear-stale-store-lock");

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

			String inputPrefix = arguments.getOrDefault("--input-prefix",
					AssaySchedulePairMatcher.DEFAULT_INPUT_PREFIX);
			ContextFeatureScorer.Options options = scoringOptions(arguments);

			int shuffledSeed = parseNonNegativeInteger(arguments.getOrDefault("--shuffledSeed", Integer.toString(DEFAULT_SHUFFLED_SEED)), "--shuffled-seed");
			
			if (endSeed < startSeed) {
				throw new IllegalArgumentException("--end-seed must be greater than or equal to --start-seed.");
			}

			List<AssayScheduleInputPair> inputs = AssaySchedulePairMatcher.findInputPairs(diaFolder, inputPrefix,
					startSeed, endSeed);

			System.out.println("Found " + inputs.size() + " DIA/mass-list pairs covering seeds " + startSeed + " through " + endSeed + ".");

			int completed = 0;
			List<String> failures = new ArrayList<>();

			for (AssayScheduleInputPair input : inputs) {
				System.out.println();
				System.out.println("Processing seed " + input.seed() + ": " + input.acquisitionFile().getName());

				String baseName = RawFiles.baseName(input.acquisitionFile());

				try {
					int featureCount = ContextFeatureScorer.scoreFeaturesForContext(library, input.acquisitionFile(),
						fasta, baseName, input.assayScheduleFile().getAbsolutePath(), shuffledSeed, options).size();

					completed++;

					System.out.println("Finished seed " + input.seed() + ": " + featureCount
							+ " features were scored and partitioned.");

				} catch (Exception e) {
					failures.add("Seed " + input.seed() + " (" + input.acquisitionFile().getName() + "): "
							+ e.getMessage());

					System.err.println("Feature scoring failed for seed " + input.seed() + ".");

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

	static Map<String, String> parseArguments(String[] args) {

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

	private static int parsePositiveInteger(String value, String flag) {
		int parsed = parseNonNegativeInteger(value, flag);
		if (parsed == 0) throw new IllegalArgumentException(flag + " must be positive.");
		return parsed;
	}

	static ContextFeatureScorer.Options scoringOptions(Map<String, String> arguments) {
		int predictionNce = parsePositiveInteger(arguments.getOrDefault("--prediction-nce", "27"), "--prediction-nce");
		int defaultCharge = parsePositiveInteger(arguments.getOrDefault("--prediction-default-charge", "2"),
				"--prediction-default-charge");
		if (defaultCharge > Byte.MAX_VALUE) throw new IllegalArgumentException("--prediction-default-charge is too large.");
		return new ContextFeatureScorer.Options(predictionNce, (byte) defaultCharge,
				parseBoolean(arguments.getOrDefault("--adjust-nce-for-dia", "true"), "--adjust-nce-for-dia"),
				parseBoolean(arguments.getOrDefault("--candidate-diagnostics", "false"), "--candidate-diagnostics"),
				parseBoolean(arguments.getOrDefault("--allow-legacy-features", "false"), "--allow-legacy-features"),
				predictionStore(arguments.get("--prediction-store")),
				parseBoolean(arguments.getOrDefault("--clear-stale-store-lock", "false"), "--clear-stale-store-lock"));
	}

	/** Null keeps the default prediction-cache folder beside the run outputs. */
	public static File predictionStore(String value) {
		if (value == null) return null;
		File directory = new File(value);
		if (directory.exists() && !directory.isDirectory()) {
			throw new IllegalArgumentException("--prediction-store must be a directory: " + directory);
		}
		return directory;
	}

	private static boolean parseBoolean(String value, String flag) {
		if ("true".equalsIgnoreCase(value)) return true;
		if ("false".equalsIgnoreCase(value)) return false;
		throw new IllegalArgumentException(flag + " must be true or false.");
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
				+ "--dia-folder <folder> " + "[--start-seed <integer>] " + "[--end-seed <integer>] "
				+ "[--input-prefix <literal>] [--prediction-nce <integer>] "
				+ "[--prediction-default-charge <integer>] [--adjust-nce-for-dia <true|false>] "
				+ "[--candidate-diagnostics <true|false>] [--allow-legacy-features <true|false>] "
				+ "[--prediction-store <folder>] [--clear-stale-store-lock <true|false>]");
		System.out.println();
		System.out.println("Required:");
		System.out.println("  --library      Spectral library used for scoring.");
		System.out.println("  --fasta        FASTA protein database.");
		System.out.println("  --dia-folder   Folder containing paired .dia and .txt files.");
		System.out.println();
		System.out.println("Optional:");
		System.out.println("  --start-seed   First seed to process; default: " + DEFAULT_START_SEED);
		System.out.println("  --end-seed     Last seed to process; default: " + DEFAULT_END_SEED);
		System.out.println("  --input-prefix Literal text immediately before the seed; default: "
				+ AssaySchedulePairMatcher.DEFAULT_INPUT_PREFIX);
		System.out.println("  --prediction-nce NCE for exact target/decoy predictions; default: 27.");
		System.out.println("  --prediction-default-charge NCE reference charge; default: 2.");
		System.out.println("  --adjust-nce-for-dia Apply DIA NCE conversion; default: true.");
		System.out.println("  --candidate-diagnostics Write optional scored candidates before winner trimming; default: false.");
		System.out.println("  --allow-legacy-features Reuse an old feature file without pair metadata; default: false.");
		System.out.println("  --prediction-store Folder for stored predictions and shared working libraries, so runs in");
		System.out.println("                   different folders can share them; default: prediction-cache beside each DIA file.");
		System.out.println("  --clear-stale-store-lock Clear a prediction store lock left by an interrupted run; default: false.");
		System.out.println("                   Use only when no other Context run is using that store.");
		System.out.println();
		System.out.println("Expected filename pairs:");
		System.out.println("  example_masked1_assay.dia");
		System.out.println("  example_masked1_assay.txt");
		System.out.println("  Custom example: --input-prefix _bootstrap matches example_bootstrap1_assay.dia/.txt");
	}
}
