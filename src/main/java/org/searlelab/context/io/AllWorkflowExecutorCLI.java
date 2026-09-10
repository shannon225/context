package org.searlelab.context.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.searlelab.context.encyclopedia.MProphetReiter;
import org.searlelab.context.percolator.ContextPercolator;
import org.searlelab.context.percolator.ContextPercolatorExecutor;
import org.searlelab.context.percolator.ContextPercolatorResult;
import org.searlelab.context.percolator.PyIsoPEPRunner;
import org.searlelab.context.percolator.TrainingSeeds;

import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.MProphetExecutionData;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.MProphetResult;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.PercolatorExecutionData;
import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchParameters;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;
import edu.washington.gs.maccoss.encyclopedia.utils.CommandLineParser;
import edu.washington.gs.maccoss.encyclopedia.utils.Logger;
import edu.washington.gs.maccoss.encyclopedia.utils.math.LinearDiscriminantAnalysis;

/**
 * Runs four confidence-estimation workflows on one matched feature-file set:
 *
 * <ol>
 * <li>Context Percolator on the background and reference feature files</li>
 * <li>standard Percolator on the complete feature file</li>
 * <li>targeted Percolator on the reference feature file</li> 
 * <li>Context mProphet on the background and reference feature files</li>
 * <li>standard mProphet on the complete feature file</li>
 * <li>targeted mProphet on the reference feature file</li>
 * </ol>
 *
 * The complete, background, and reference files must have been produced from
 * the same acquisition. The background and reference files must partition the
 * peptides represented by the complete feature file.
 */

public final class AllWorkflowExecutorCLI {

	private static final String CONTEXT_MPROPHET_ENGINE_NAME = "context-mprophet";
	private static final String STANDARD_MPROPHET_ENGINE_NAME = "standard-mprophet";
	private static final String TARGET_MPROPHET_ENGINE_NAME = "target-mprophet";
	private static final String CONTEXT_PERCOLATOR_ENGINE_NAME = ContextPercolator.ENGINE_NAME;
	private static final String STANDARD_PERCOLATOR_ENGINE_NAME = "standard-percolator";
	private static final String TARGET_PERCOLATOR_ENGINE_NAME = "target-percolator";

	private static final float DEFAULT_FDR = ContextPercolator.DEFAULT_FDR;
	private static final int MPROPHET_ROUND = 1;

	private static final String COMPLETE_SUFFIX = ".features.txt";
	private static final String BACKGROUND_SUFFIX = "_background.features.txt";
	private static final String REFERENCE_SUFFIX = "_reference.features.txt";

	private static final class FeatureFileSet {

		private final String prefix;
		private final File completeFeatures;
		private final File backgroundFeatures;
		private final File referenceFeatures;

		private FeatureFileSet(String prefix, File completeFeatures, File backgroundFeatures, File referenceFeatures) {

			this.prefix = prefix;
			this.completeFeatures = completeFeatures;
			this.backgroundFeatures = backgroundFeatures;
			this.referenceFeatures = referenceFeatures;
		}
	}

	public static void main(String[] args) {
		HashMap<String, String> arguments = CommandLineParser.parseArguments(args);

		if (args.length == 0 || containsHelpFlag(arguments)) {
			printHelp();
			System.exit(args.length == 0 ? 1 : 0);
		}

		try {

			boolean folderMode = arguments.containsKey("--features-folder");
			boolean singleFileMode = arguments.containsKey("--features") || arguments.containsKey("--background")
					|| arguments.containsKey("--reference");

			if (folderMode && singleFileMode) {
				throw new IllegalArgumentException(
						"Use either --features-folder or the single-file options, not both.");
			}

			List<Integer> seeds = arguments.containsKey("--seeds") ? TrainingSeeds.parse(arguments.get("--seeds")) : null; 
			File fasta = requiredReadableFile(arguments, "-f");
			float fdr = Float.parseFloat(arguments.getOrDefault("-fdr", Float.toString(DEFAULT_FDR)));
			PyIsoPEPRunner pyIsoPEP = new PyIsoPEPRunner(arguments.get("--pyisopep"));
			HashMap<String, String> encyclopediaArguments = encyclopediaArguments(arguments);

			if (folderMode) {
				File featuresFolder = requiredReadableDirectory(arguments, "--features-folder");
				File outputDirectory = DirectoryOptions.outputDirectory(arguments,
						featuresFolder.getAbsoluteFile().getParentFile());
				
				if (seeds == null) {
					runAllOnFolder(featuresFolder, fasta, pyIsoPEP, fdr, outputDirectory, encyclopediaArguments);
				} else {
					runAllOnFolder(featuresFolder, fasta, pyIsoPEP, fdr, outputDirectory, encyclopediaArguments, seeds);
				}
				
			} else {
				File allFeatures = requiredReadableFile(arguments, "--features");
				File backgroundFeatures = requiredReadableFile(arguments, "--background");
				File referenceFeatures = requiredReadableFile(arguments, "--reference");
				File outputDirectory = DirectoryOptions.outputDirectory(arguments,
						allFeatures.getAbsoluteFile().getParentFile());
				String prefix = arguments.getOrDefault("--prefix", stripFeatureSuffix(allFeatures));
				
				if (seeds == null) {
					runAll(allFeatures, backgroundFeatures, referenceFeatures, fasta, pyIsoPEP, fdr, outputDirectory, prefix, encyclopediaArguments);
				} else {
					runAllSeeds(allFeatures, backgroundFeatures, referenceFeatures, fasta, pyIsoPEP, fdr, outputDirectory, prefix, encyclopediaArguments, seeds);
				}
			
			} 
		} catch (Exception e) {
			Logger.errorLine("Workflow execution failed: " + e.getMessage());
			e.printStackTrace();
			System.exit(2);
		}
	}


	 // Programmatic entry point used by the CLI and suitable for integration tests.
	public static AllWorkflowResult runAll(File allFeatures, File backgroundFeatures, File referenceFeatures, File fasta, PyIsoPEPRunner pyIsoPEP, float fdr, File outputDirectory, String prefix, HashMap<String, String> encyclopediaArguments) throws Exception {
		
		return runAll(allFeatures, backgroundFeatures, referenceFeatures, fasta, pyIsoPEP, fdr, outputDirectory, prefix, encyclopediaArguments, TrainingSeeds.DEFAULT_SEED);
	}
	public static AllWorkflowResult runAll(File allFeatures, File backgroundFeatures, File referenceFeatures,
			File fasta, PyIsoPEPRunner pyIsoPEP, float fdr, File outputDirectory, String prefix,
			HashMap<String, String> encyclopediaArguments, int seed) throws Exception {

		TrainingSeeds.requireValid(seed);
		
		requireReadableFile(allFeatures, "Complete feature file");
		requireReadableFile(backgroundFeatures, "Background feature file");
		requireReadableFile(referenceFeatures, "Reference feature file");
		requireReadableFile(fasta, "FASTA file");

		if (!(fdr > 0.0f && fdr <= 1.0f)) {
			throw new IllegalArgumentException("FDR must be greater than 0 and at most 1.");
		}
		if (outputDirectory == null) {
			throw new IllegalArgumentException("Output directory must not be null.");
		}
		if (prefix == null || prefix.trim().isEmpty()) {
			throw new IllegalArgumentException("Output prefix must not be empty.");
		}

		HashMap<String, String> parameters = encyclopediaArguments == null
				? SearchParameterParser.getDefaultParameters()
				: new HashMap<>(encyclopediaArguments);

		Logger.logLine("[1/6] Running Context Percolator");
		ContextPercolatorResult contextPercolator = ContextPercolator.trainAndApply(backgroundFeatures,
				referenceFeatures, fasta, parameters, pyIsoPEP, fdr, outputDirectory, prefix, seed);

		Logger.logLine("[2/6] Running standard discovery Percolator");

		SearchParameters searchParameters = parseSearchParameters(parameters, fdr);

		PercolatorExecutionData standardPercolator = ContextPercolatorExecutor.runStandardPercolator(allFeatures, fasta,
				pyIsoPEP, fdr, outputDirectory, prefix, new HashMap<>(parameters), STANDARD_PERCOLATOR_ENGINE_NAME,
				"standard", seed);

		Logger.logLine("[3/6] Running targeted Percolator");

		PercolatorExecutionData targetPercolator = ContextPercolatorExecutor.runTargetPercolator(referenceFeatures,
				fasta, pyIsoPEP, fdr, outputDirectory, prefix, new HashMap<>(parameters), TARGET_PERCOLATOR_ENGINE_NAME,
				"targeted", seed);

		Logger.logLine("[4/6] Running Context mProphet");
		MProphetResult contextMProphet = runContextMProphet(backgroundFeatures, referenceFeatures, fasta,
				searchParameters, fdr, outputDirectory, prefix, seed);

		Logger.logLine("[5/6] Running standard discovery mProphet");
		MProphetResult standardMProphet = runStandardMProphet(allFeatures, fasta, searchParameters, fdr,
				outputDirectory, prefix, seed);

		Logger.logLine("[6/6] Running targeted mProphet");
		MProphetResult targetMProphet = runTargetMProphet(referenceFeatures, fasta, searchParameters, fdr,
				outputDirectory, prefix, seed);

		Logger.logLine("Finished all workflows. Results are under " + outputDirectory.getAbsolutePath());

		return new AllWorkflowResult(contextPercolator, standardPercolator, targetPercolator, contextMProphet,
				standardMProphet, targetMProphet);
	}

	public static Map<String, AllWorkflowResult> runAllOnFolder(File folder, File fasta, PyIsoPEPRunner pyIsoPEP,
			float fdr, File outputDirectory, HashMap<String, String> encyclopediaArguments) throws Exception {

		requireReadableFile(fasta, "FASTA file");
		File featureFolder = requireReadableDirectory(folder, "Folder containing features");

		if (!(fdr > 0.0f && fdr <= 1.0f)) {
			throw new IllegalArgumentException("FDR must be greater than 0 and at most 1.");
		}

		if (outputDirectory == null) {
			throw new IllegalArgumentException("Output directory must not be null.");
		}

		ArrayList<FeatureFileSet> featureSets = findFeatureFileSets(featureFolder);

		Logger.logLine("Found " + featureSets.size() + " matched feature-file sets.");
		Map<String, AllWorkflowResult> results = new HashMap<>();

		int index = 0;

		for (FeatureFileSet featureSet : featureSets) {
			index++;

			Logger.logLine("[" + index + "/" + featureSets.size() + "] Processing " + featureSet.prefix);
			AllWorkflowResult result = runAll(featureSet.completeFeatures, featureSet.backgroundFeatures,
					featureSet.referenceFeatures, fasta, pyIsoPEP, fdr, outputDirectory, featureSet.prefix,
					encyclopediaArguments);

			results.put(featureSet.prefix, result);

			Logger.logLine("Finished " + results.size() + " matched feature-file sets. Results are under "
					+ outputDirectory.getAbsolutePath());

		}
		return results;

	}

	// Seed experiments require fresh directories and model training
	public static Map<Integer, AllWorkflowResult> runAllSeeds(File allFeatures, File backgroundFeatures, File referenceFeatures, File fasta, PyIsoPEPRunner pyIsoPEP, float fdr, File outputDirectory, String prefix, HashMap<String, String> encyclopediaArguments, List<Integer> seeds) throws Exception {
		
		List<Integer> validated = TrainingSeeds.validate(seeds);
		preflightExperiment(allFeatures, backgroundFeatures, referenceFeatures, fasta, fdr, outputDirectory, prefix, encyclopediaArguments, validated);
		Map<Integer, AllWorkflowResult> results = new java.util.LinkedHashMap<>();
		
		for (int seed : validated) {
			Path destination = SeedExperiment.directory(outputDirectory, prefix, seed);
			Files.createDirectories(destination.getParent());
			Files.createDirectory(destination); // never silently resuse a run, even after preflight
			
			SeedExperiment record = new SeedExperiment(destination, seed, fdr, allFeatures, backgroundFeatures, referenceFeatures, fasta, encyclopediaArguments);
			
			try {
					
					AllWorkflowResult result = runAll(allFeatures, backgroundFeatures, referenceFeatures, fasta, pyIsoPEP, fdr, destination.toFile(), prefix, encyclopediaArguments, seed);
					record.complete(result);
					results.put(seed, result);
					
				} catch (Exception e) {
					
					try { record.fail(e); } catch (IOException metadataError) { e.addSuppressed(metadataError); 
					throw e;
				}
		}
	}
		return results;

	}
	
	public static Map<String, Map<Integer, AllWorkflowResult>> runAllOnFolder(File folder, File fasta, PyIsoPEPRunner pyIsoPEP, float fdr, File outputDirectory, HashMap<String, String> encyclopediaArguments, List<Integer> seeds) throws Exception {
		List<Integer> validated = TrainingSeeds.validate(seeds);
		ArrayList<FeatureFileSet> sets = findFeatureFileSets(requireReadableDirectory(folder, "Features Folder"));
		
		// Validate every sample prior to the first expensive workflow
		for (FeatureFileSet set : sets) {
			preflightExperiment(set.completeFeatures, set.backgroundFeatures, set.referenceFeatures, fasta, fdr, outputDirectory, set.prefix, encyclopediaArguments, validated);
			
		}
		
		Map<String, Map<Integer, AllWorkflowResult>> results = new java.util.LinkedHashMap<>();
		for (FeatureFileSet set : sets) {
			results.put(set.prefix, runAllSeeds(set.completeFeatures, set.backgroundFeatures, set.referenceFeatures, fasta, pyIsoPEP, fdr, outputDirectory, set.prefix, encyclopediaArguments, validated));
		}
		return results;
	}
	
	private static void preflightExperiment(File all, File background, File reference, File fasta, float fdr, File output, String prefix, HashMap<String, String> arguments, List<Integer> seeds) throws IOException {
		
		requireReadableFile(all, "Complete feature file");
		requireReadableFile(background, "Background feature file");
		requireReadableFile(reference, "Reference feature file");
		requireReadableFile(fasta, "Fasta file");
		
		if (!(fdr > 0 && fdr <= 1)) throw new IllegalArgumentException("FDR must be between 0 and 1.");
		
		HashMap<String, String> copy = arguments == null ? SearchParameterParser.getDefaultParameters() : new HashMap<>(arguments);
		SearchParameters parameters = parseSearchParameters(copy, fdr);
		
		if (copy.get("-percolatorModelFile") != null || parameters.getPercolatorModelFile().isPresent()) {
			throw new IllegalArgumentException("Seed experiments require fresh training; remove -percolatorModelFile");
		}
		
		if (parameters.getPercolatorTrainingIterations() <1) {
			throw new IllegalArgumentException("Seed experiments require positive Percolator training iterations.");
		}
		
		for (int seed: seeds) SeedExperiment.requireFresh(output, prefix, seed);
	}
	
	private static MProphetResult runContextMProphet(File backgroundFeatures, File referenceFeatures, File fasta,
			SearchParameters parameters, float fdr, File outputDirectory, String prefix, int seed) throws Exception {

		File engineDirectory = DirectoryOptions.engineDirectory(outputDirectory, CONTEXT_MPROPHET_ENGINE_NAME);

		MProphetExecutionData backgroundData = new MProphetExecutionData(backgroundFeatures, fasta,
				new File(engineDirectory, prefix + ".peptide.background.target.txt"),
				new File(engineDirectory, prefix + ".peptide.background.decoy.txt"), parameters);

		MProphetExecutionData referenceData = new MProphetExecutionData(referenceFeatures, fasta,
				new File(engineDirectory, prefix + ".peptide.reference.target.txt"),
				new File(engineDirectory, prefix + ".peptide.reference.decoy.txt"), parameters);

		deleteMProphetOutputs(backgroundData);
		deleteMProphetOutputs(referenceData);

		MProphetResult backgroundResult = MProphetReiter.executeMProphetTSV(backgroundData, fdr, seed,
				parameters.getAAConstants(), MPROPHET_ROUND);
		LinearDiscriminantAnalysis backgroundModel = backgroundResult.getLDA();

		MProphetResult referenceResult = MProphetReiter.executeMProphetTSVWithModel(referenceData, fdr, backgroundModel,
				parameters.getAAConstants());

		Logger.logLine("Context mProphet found " + referenceResult.getPassingPeptides().size()
				+ " reference peptides at " + (fdr * 100.0f) + "% FDR");
		return referenceResult;
	}

	private static MProphetResult runStandardMProphet(File allFeatures, File fasta, SearchParameters parameters,
			float fdr, File outputDirectory, String prefix, int seed) throws Exception {

		File engineDirectory = DirectoryOptions.engineDirectory(outputDirectory, STANDARD_MPROPHET_ENGINE_NAME);
		MProphetExecutionData data = new MProphetExecutionData(allFeatures, fasta,
				new File(engineDirectory, prefix + ".peptide.target.txt"),
				new File(engineDirectory, prefix + ".peptide.decoy.txt"), parameters);

		deleteMProphetOutputs(data);
		
		MProphetResult result = MProphetReiter.executeMProphetTSV(data, fdr, seed, parameters.getAAConstants(),
				MPROPHET_ROUND);

		Logger.logLine("Standard mProphet found " + result.getPassingPeptides().size() + " peptides at "
				+ (fdr * 100.0f) + "% FDR");
		
		return result;
	}

	private static MProphetResult runTargetMProphet(File allFeatures, File fasta, SearchParameters parameters,
			float fdr, File outputDirectory, String prefix, int seed) throws Exception {

		File engineDirectory = DirectoryOptions.engineDirectory(outputDirectory, TARGET_MPROPHET_ENGINE_NAME);
		MProphetExecutionData data = new MProphetExecutionData(allFeatures, fasta,
				new File(engineDirectory, prefix + ".peptide.target.txt"),
				new File(engineDirectory, prefix + ".peptide.decoy.txt"), parameters);

		deleteMProphetOutputs(data);
		
		MProphetResult result = MProphetReiter.executeMProphetTSV(data, fdr, seed, parameters.getAAConstants(),
				MPROPHET_ROUND);

		Logger.logLine("Standard mProphet found " + result.getPassingPeptides().size() + " peptides at "
				+ (fdr * 100.0f) + "% FDR");
		
		return result;
	}

	private static SearchParameters parseSearchParameters(HashMap<String, String> encyclopediaArguments, float fdr) {
		HashMap<String, String> parameterMap = SearchParameterParser.getDefaultParametersObject().toParameterMap();
		parameterMap.putAll(encyclopediaArguments);
		parameterMap.put("-percolatorThreshold", Float.toString(fdr));
		return SearchParameterParser.parseParameters(parameterMap);
	}

	private static void deleteMProphetOutputs(MProphetExecutionData data) throws IOException {
		Files.deleteIfExists(data.getPeptideOutputFile().toPath());
		Files.deleteIfExists(data.getPeptideDecoyFile().toPath());
	}

	private static File requiredReadableFile(HashMap<String, String> arguments, String flag) throws IOException {
		String value = arguments.get(flag);
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalArgumentException("Missing required argument: " + flag);
		}

		File file = new File(value);
		requireReadableFile(file, flag);
		return file;
	}

	private static void requireReadableFile(File file, String description) throws IOException {
		if (file == null || !file.isFile() || !file.canRead()) {
			throw new IOException(
					description + " is not a readable file: " + (file == null ? "null" : file.getAbsolutePath()));
		}
	}

	private static File requireReadableDirectory(File directory, String description) throws IOException {

		if (directory == null || !directory.isDirectory() || !directory.canRead()) {
			throw new IOException(description + " is not a readable directory: "
					+ (directory == null ? "null" : directory.getAbsolutePath()));
		}

		return directory;
	}

	private static File requiredReadableDirectory(HashMap<String, String> arguments, String flag) throws IOException {

		String value = arguments.get(flag);

		if (value == null || value.trim().isEmpty()) {
			throw new IllegalArgumentException("Missing required argument: " + flag);
		}

		File directory = new File(value);

		return requireReadableDirectory(directory, flag);
	}

	private static boolean containsHelpFlag(HashMap<String, String> arguments) {
		return arguments.containsKey("-h") || arguments.containsKey("-help") || arguments.containsKey("--help");
	}

	private static String stripFeatureSuffix(File featureFile) {
		return featureFile.getName().replaceFirst("\\.features\\.txt$", "");
	}

	private static boolean isCompleteFeatureFile(Path path) {
		String name = path.getFileName().toString();

		return name.endsWith(COMPLETE_SUFFIX) && !name.endsWith(BACKGROUND_SUFFIX) && !name.endsWith(REFERENCE_SUFFIX);
	}

	private static ArrayList<FeatureFileSet> findFeatureFileSets(File folder) throws IOException {

		List<Path> completeFiles = new ArrayList<>();

		try (Stream<java.nio.file.Path> paths = Files.list(folder.toPath())) {
			paths.filter(Files::isRegularFile).filter(path -> isCompleteFeatureFile(path))
					.sorted(Comparator.comparing(path -> path.getFileName().toString())).forEach(completeFiles::add);
		}

		if (completeFiles.isEmpty()) {
			throw new IOException("No complete *.features.txt files were found in " + folder.getAbsolutePath());
		}

		ArrayList<FeatureFileSet> featureSets = new ArrayList<>();
		ArrayList<String> missingFiles = new ArrayList<>();

		for (Path completePath : completeFiles) {
			File completeFile = completePath.toFile();
			String completeName = completeFile.getName();

			String prefix = completeName.substring(0, completeName.length() - COMPLETE_SUFFIX.length());

			File backgroundFile = new File(folder, prefix + BACKGROUND_SUFFIX);

			File referenceFile = new File(folder, prefix + REFERENCE_SUFFIX);

			if (!backgroundFile.isFile() || !backgroundFile.canRead()) {
				missingFiles.add(backgroundFile.getName());
			}

			if (!referenceFile.isFile() || !referenceFile.canRead()) {
				missingFiles.add(referenceFile.getName());
			}

			if (backgroundFile.isFile() && backgroundFile.canRead() && referenceFile.isFile()
					&& referenceFile.canRead()) {

				featureSets.add(new FeatureFileSet(prefix, completeFile, backgroundFile, referenceFile));
			}

		}

		if (!missingFiles.isEmpty()) {
			throw new IOException("The following required feature files are missing or unreadable: "
					+ String.join(", ", missingFiles));
		}

		return featureSets;
	}

	public static HashMap<String, String> encyclopediaArguments(HashMap<String, String> arguments) {

		HashMap<String, String> parameters = SearchParameterParser.getDefaultParameters();
		HashMap<String, String> remaining = new HashMap<>(arguments);

		for (String workflowFlag : new String[] { "--features-folder", "--features", "--background", "--reference", "--seeds",
				"-f", "-fdr", "-o", "-outdir", "--outdir", "--prefix", "--pyisopep", "-h", "-help", "--help" }) {
			remaining.remove(workflowFlag);
		}

		parameters.putAll(remaining);
		return parameters;
	}

	private static void printHelp() {
		Logger.timelessLogLine("AllWorkflowExecutorCLI");
		Logger.timelessLogLine("Run Context Percolator, standard Percolator, Context mProphet,");
		Logger.timelessLogLine("and standard mProphet on one matched feature-file set.");
		Logger.timelessLogLine("");
		Logger.timelessLogLine("Required:");
		Logger.timelessLogLine("  --features   <file> complete, unsplit feature TSV");
		Logger.timelessLogLine("  --background <file> background feature TSV from the same experiment");
		Logger.timelessLogLine("  --reference  <file> reference feature TSV from the same experiment");
		Logger.timelessLogLine("  -f          <file> FASTA protein database");
		Logger.timelessLogLine("");
		Logger.timelessLogLine("Optional:");
		Logger.timelessLogLine("--seeds <list> comma-separated positive integers, e.g. 1,2,3 (default: 1)"); 
		Logger.timelessLogLine(" Explicit seeds write to <o>/<prefix>/seed_<N>/<engine>; seed directories must not exist."); 
		Logger.timelessLogLine(" Omit--seeds to preserve the existing <o>/<engine> output layout.");
		Logger.timelessLogLine("  -fdr      <float> peptide FDR threshold (default: " + DEFAULT_FDR + ")");
		Logger.timelessLogLine("  -o        <dir>   output root (default: next to -features)");
		Logger.timelessLogLine("  --prefix   <name>  output filename prefix");
		Logger.timelessLogLine("  --pyisopep <path>  pyIsoPEP executable (default: PATH)");
		Logger.timelessLogLine("");
		Logger.timelessLogLine("Other flags are passed through to EncyclopeDIA parameters.");
		Logger.timelessLogLine("");
		Logger.timelessLogLine("Output directories:");
		Logger.timelessLogLine("  <o>/" + CONTEXT_PERCOLATOR_ENGINE_NAME);
		Logger.timelessLogLine("  <o>/" + STANDARD_PERCOLATOR_ENGINE_NAME);
		Logger.timelessLogLine("  <o>/" + TARGET_PERCOLATOR_ENGINE_NAME);
		Logger.timelessLogLine("  <o>/" + CONTEXT_MPROPHET_ENGINE_NAME);
		Logger.timelessLogLine("  <o>/" + STANDARD_MPROPHET_ENGINE_NAME);
		Logger.timelessLogLine("  <o>/" + TARGET_MPROPHET_ENGINE_NAME);

	}
}
