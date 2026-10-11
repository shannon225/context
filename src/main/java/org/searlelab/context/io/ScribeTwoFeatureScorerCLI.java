package org.searlelab.context.io;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.searlelab.context.mprophet.ContextMProphetExecutor;

import edu.washington.gs.maccoss.encyclopedia.algorithms.scribe.ScribeTwoSearchParameters;
import edu.washington.gs.maccoss.encyclopedia.utils.Logger;
import edu.washington.gs.maccoss.encyclopedia.utils.threading.EmptyProgressIndicator;

public class ScribeTwoFeatureScorerCLI {

	private static final int DEFAULT_START_SEED = 1;
	private static final int DEFAULT_END_SEED = 100;
	private static final Set<String> FOLDER_ARGUMENTS = Set.of("--dia-folder", "--library", "--fasta",
			"--output-directory", "--start-seed", "--end-seed", "--input-prefix");

	private ScribeTwoFeatureScorerCLI() {
	}

	public static void main(String[] args) throws Exception {
		run(args, false);
	}

	public static void run(String[] args, boolean trainContext) throws Exception {
		System.setProperty("java.awt.headless", "true");
		if (containsHelpFlag(args)) {
			printUsage();
			return;
		}

		if (args.length > 0 && args[0].startsWith("--")) {
			runFolder(parseFolderArguments(args), trainContext);
		} else {
			runSingle(args, trainContext);
		}
	}

	private static void runSingle(String[] args, boolean trainContext) throws Exception {
		if (args.length < 5 || (args.length - 5) % 2 != 0) {
			throw new IllegalArgumentException(
					"Expected raw, library, FASTA, mass-list, output-directory and property/value pairs; see --help");
		}

		HashMap<String, String> settings = ScribeTwoSearchParameters.getDefaultParameters();
		for (int i = 5; i < args.length; i += 2) {
			if (!settings.containsKey(args[i])) {
				throw new IllegalArgumentException("Unknown Scribe parameter: " + args[i]);
			}
			settings.put(args[i], args[i + 1]);
		}

		File massList = requireReadableFile("Mass list", args[3]);
		IsolationWindowReader.parseMassList(massList.getAbsolutePath());
		scoreInput(new File(args[0]), new File(args[1]), new File(args[2]), massList, Path.of(args[4]),
				ScribeTwoSearchParameters.parseParameters(settings), trainContext);
	}

	static FolderOptions parseFolderArguments(String[] args) {
		if (args.length == 0) {
			throw new IllegalArgumentException("No arguments were provided.");
		}

		Map<String, String> folderArguments = new HashMap<>();
		HashMap<String, String> scribeSettings = ScribeTwoSearchParameters.getDefaultParameters();
		Set<String> suppliedScribeArguments = new HashSet<>();

		for (int index = 0; index < args.length; index += 2) {
			String flag = args[index];
			if (index + 1 >= args.length) {
				throw new IllegalArgumentException("Missing value for " + flag);
			}
			String value = args[index + 1];

			if (flag.startsWith("--")) {
				if (!FOLDER_ARGUMENTS.contains(flag)) {
					throw new IllegalArgumentException("Unknown folder argument: " + flag);
				}
				if (value.startsWith("--")) {
					throw new IllegalArgumentException("Missing value for " + flag);
				}
				if (folderArguments.putIfAbsent(flag, value) != null) {
					throw new IllegalArgumentException("Argument was supplied more than once: " + flag);
				}
			} else if (flag.startsWith("-")) {
				if (!scribeSettings.containsKey(flag)) {
					throw new IllegalArgumentException("Unknown Scribe parameter: " + flag);
				}
				if (!suppliedScribeArguments.add(flag)) {
					throw new IllegalArgumentException("Scribe parameter was supplied more than once: " + flag);
				}
				scribeSettings.put(flag, value);
			} else {
				throw new IllegalArgumentException(
						"Folder mode accepts named arguments only; positional value found: " + flag);
			}
		}

		File diaFolder = requireReadableDirectory("DIA folder",
				requireArgument(folderArguments, "--dia-folder"));
		File library = requireReadableFile("Library", requireArgument(folderArguments, "--library"));
		File fasta = requireReadableFile("FASTA", requireArgument(folderArguments, "--fasta"));
		Path outputDirectory = Path.of(requireArgument(folderArguments, "--output-directory"));
		if (Files.exists(outputDirectory) && !Files.isDirectory(outputDirectory)) {
			throw new IllegalArgumentException("Output directory is an existing file: " + outputDirectory.toAbsolutePath());
		}

		int startSeed = parseNonNegativeInteger(
				folderArguments.getOrDefault("--start-seed", Integer.toString(DEFAULT_START_SEED)), "--start-seed");
		int endSeed = parseNonNegativeInteger(
				folderArguments.getOrDefault("--end-seed", Integer.toString(DEFAULT_END_SEED)), "--end-seed");
		if (endSeed < startSeed) {
			throw new IllegalArgumentException("--end-seed must be greater than or equal to --start-seed.");
		}

		String inputPrefix = folderArguments.getOrDefault("--input-prefix",
				AssaySchedulePairMatcher.DEFAULT_INPUT_PREFIX);
		return new FolderOptions(diaFolder, library, fasta, outputDirectory, startSeed, endSeed, inputPrefix,
				Map.copyOf(scribeSettings));
	}

	private static void runFolder(FolderOptions options, boolean trainContext) throws Exception {
		List<AssayScheduleInputPair> inputs = findAndPreflightInputs(options);

		Logger.logLine("Found " + inputs.size() + " DIA/assay-schedule pairs covering seeds "
				+ options.startSeed() + " through " + options.endSeed() + ".");
		FolderRunSummary summary = executeFolderInputs(inputs, options, trainContext,
				ScribeTwoFeatureScorerCLI::scoreFolderInput);
		printSummary(summary);
		if (summary.failed() > 0) {
			throw new FolderRunException(summary);
		}
	}

	static List<AssayScheduleInputPair> findAndPreflightInputs(FolderOptions options) {
		List<AssayScheduleInputPair> inputs = AssaySchedulePairMatcher.findInputPairs(options.diaFolder(),
				options.inputPrefix(), options.startSeed(), options.endSeed());
		for (AssayScheduleInputPair input : inputs) {
			preflightAssaySchedule(input.assayScheduleFile());
		}
		return inputs;
	}

	static FolderRunSummary executeFolderInputs(List<AssayScheduleInputPair> inputs, FolderOptions options,
			boolean trainContext, FolderInputRunner runner) throws Exception {
		Files.createDirectories(options.outputDirectory());
		List<FolderRunOutcome> outcomes = new ArrayList<>();

		for (AssayScheduleInputPair input : inputs) {
			String acquisitionName = RawFiles.stripExtension(input.acquisitionFile().getName());
			Path inputOutputDirectory = options.outputDirectory().resolve(acquisitionName);
			try {
				ScribeTwoSearchParameters parameters = ScribeTwoSearchParameters
						.parseParameters(new HashMap<>(options.scribeSettings()));
				runner.run(input, options.library(), options.fasta(), inputOutputDirectory, parameters, trainContext);
				outcomes.add(FolderRunOutcome.success(input.seed(), input.acquisitionFile()));
			} catch (Exception e) {
				Logger.errorLine("ScribeTwo feature scoring failed for seed " + input.seed() + ": " + e.getMessage());
				outcomes.add(FolderRunOutcome.failure(input.seed(), input.acquisitionFile(), e));
			}
		}

		return new FolderRunSummary(List.copyOf(outcomes));
	}

	private static void scoreFolderInput(AssayScheduleInputPair input, File library, File fasta,
			Path outputDirectory, ScribeTwoSearchParameters parameters, boolean trainContext) throws Exception {
		scoreInput(input.acquisitionFile(), library, fasta, input.assayScheduleFile(), outputDirectory, parameters,
				trainContext);
	}

	private static void scoreInput(File raw, File library, File fasta, File massList, Path outputDirectory,
			ScribeTwoSearchParameters parameters, boolean trainContext) throws Exception {
		var result = ScribeTwoFeatureGenerator.generate(raw, library, fasta, outputDirectory, parameters,
				new EmptyProgressIndicator(true));
		var features = ContextFeatureScorer.partitionFeatures(result.features(), result.parameters(),
				result.outputPrefix().getAbsolutePath(), massList);
		Logger.logLine("Calibrated full features: " + result.features());
		Logger.logLine("Partitioned " + features.size() + " best peptide features under "
				+ result.outputPrefix().getParent());
		if (trainContext) {
			String prefix = result.outputPrefix().getAbsolutePath();
			var analysis = ContextMProphetExecutor.trainAndApplyFeature(new File(prefix + "_background.features.txt"),
					new File(prefix + "_reference.features.txt"), fasta, result.parameters());
			Logger.logLine("Context final training scope: BACKGROUND"
					+ "\nReference peptides passing a 0.01 PEP threshold: "
					+ analysis.reference().getPassingPeptides().size());
		}
	}

	private static void preflightAssaySchedule(File massList) {
		if (IsolationWindowReader.parseMassList(massList.getAbsolutePath()).isEmpty()) {
			throw new IllegalArgumentException("Assay schedule has no isolation windows: " + massList.getAbsolutePath());
		}
	}

	private static String requireArgument(Map<String, String> arguments, String flag) {
		String value = arguments.get(flag);
		if (value == null || value.isBlank()) {
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
			int parsed = Integer.parseInt(value);
			if (parsed < 0) {
				throw new IllegalArgumentException(flag + " cannot be negative.");
			}
			return parsed;
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

	private static void printSummary(FolderRunSummary summary) {
		Logger.logLine("ScribeTwo folder scoring concluded. Successfully processed: " + summary.completed()
				+ "; failed: " + summary.failed() + ".");
		for (FolderRunOutcome outcome : summary.outcomes()) {
			String status = outcome.succeeded() ? "completed" : "failed: " + outcome.failure().getMessage();
			Logger.logLine("  Seed " + outcome.seed() + " (" + outcome.acquisitionFile().getName() + "): " + status);
		}
	}

	private static void printUsage() {
		Logger.logLine("ScribeTwoFeatureScorerCLI supports one acquisition or a seeded folder.");
		Logger.logLine("Single: <raw> <library> <fasta> <mass-list> <output-directory> [-parameter value...]");
		Logger.logLine("Folder: --dia-folder <folder> --library <library> --fasta <fasta>"
				+ " --output-directory <folder> [--start-seed <n>] [--end-seed <n>]"
				+ " [--input-prefix <literal>] [-parameter value...]");
		Logger.logLine("Folder defaults: seeds " + DEFAULT_START_SEED + " through " + DEFAULT_END_SEED
				+ "; input prefix " + AssaySchedulePairMatcher.DEFAULT_INPUT_PREFIX + ".");
		Logger.logLine("Example pair: sample_masked1_assay.dia and sample_masked1_assay.txt");
		Logger.logLine("Custom example: --input-prefix _bootstrap matches sample_bootstrap1_assay.dia/.txt");
		Logger.logLine("Each acquisition writes below <output-directory>/<acquisition-basename>/.");
		Logger.logLine("Folder mode continues after individual failures and returns a final failure when any seed fails.");
		Logger.logLine("Scribe parameter examples: -numberOfThreadsUsed 2 -adjustTolerances true");
		Logger.logLine("Use the scribe-context command with the same arguments to train background LDA and apply it to reference features.");
	}

	record FolderOptions(File diaFolder, File library, File fasta, Path outputDirectory, int startSeed, int endSeed,
			String inputPrefix, Map<String, String> scribeSettings) {
	}

	@FunctionalInterface
	interface FolderInputRunner {
		void run(AssayScheduleInputPair input, File library, File fasta, Path outputDirectory,
				ScribeTwoSearchParameters parameters, boolean trainContext) throws Exception;
	}

	record FolderRunOutcome(int seed, File acquisitionFile, Exception failure) {
		static FolderRunOutcome success(int seed, File acquisitionFile) {
			return new FolderRunOutcome(seed, acquisitionFile, null);
		}

		static FolderRunOutcome failure(int seed, File acquisitionFile, Exception failure) {
			return new FolderRunOutcome(seed, acquisitionFile, failure);
		}

		boolean succeeded() {
			return failure == null;
		}
	}

	record FolderRunSummary(List<FolderRunOutcome> outcomes) {
		int completed() {
			return (int) outcomes.stream().filter(FolderRunOutcome::succeeded).count();
		}

		int failed() {
			return outcomes.size() - completed();
		}
	}

	static final class FolderRunException extends Exception {
		private static final long serialVersionUID = 1L;
		private final FolderRunSummary summary;

		FolderRunException(FolderRunSummary summary) {
			super(summary.failed() + " ScribeTwo folder input(s) failed", firstFailure(summary));
			this.summary = summary;
			for (FolderRunOutcome outcome : summary.outcomes()) {
				if (outcome.failure() != null && outcome.failure() != getCause()) {
					addSuppressed(outcome.failure());
				}
			}
		}

		FolderRunSummary summary() {
			return summary;
		}

		private static Exception firstFailure(FolderRunSummary summary) {
			return summary.outcomes().stream().map(FolderRunOutcome::failure).filter(Objects::nonNull).findFirst()
					.orElse(null);
		}
	}
}
