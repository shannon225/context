package org.searlelab.context.io;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.searlelab.context.datastructures.EncyclopediaFeatures;
import org.searlelab.context.encyclopedia.SearchPairSidecar;

import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchParameters;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;

/** Runs the existing background-trained Context mProphet workflow from the public command. */
public final class ContextMProphetCLI {
	private static final Set<String> FLAGS = Set.of("-i", "-l", "-f", "-massList", "-background",
			"-reference", "-o", "-prefix", "-fdr", "--allow-legacy-features", "--prediction-store", "--clear-stale-store-lock");

	private ContextMProphetCLI() {}

	public static void run(String[] args) throws Exception {
		if (args.length == 1 && ("-h".equals(args[0]) || "--help".equals(args[0]))) {
			printUsage();
			return;
		}
		Map<String, String> options = parse(args);
		File fasta = readable(options, "-f");
		boolean direct = options.containsKey("-background") || options.containsKey("-reference");
		if (direct == (options.containsKey("-i") || options.containsKey("-l") || options.containsKey("-massList"))) {
			throw new IllegalArgumentException("Use either -background/-reference or -i/-l/-massList with -f");
		}
		for (String rawOnly : new String[] {"-generateDecoys", "--prediction-store", "--clear-stale-store-lock"}) {
			if (direct && options.containsKey(rawOnly)) {
				throw new IllegalArgumentException(rawOnly + " applies only to raw acquisition inputs");
			}
		}
		float fdr = Float.parseFloat(options.getOrDefault("-fdr", "0.01"));
		if (!Float.isFinite(fdr) || fdr <= 0f || fdr > 1f) {
			throw new IllegalArgumentException("-fdr must be greater than zero and at most one");
		}
		boolean allowLegacy = Boolean.parseBoolean(options.getOrDefault("--allow-legacy-features", "false"));
		File background;
		File reference;
		File input;
		if (direct) {
			background = readable(options, "-background");
			reference = readable(options, "-reference");
			input = background;
		} else {
			File dia = readableAcquisition(options);
			File library = readable(options, "-l");
			File massList = readable(options, "-massList");
			String base = RawFiles.stripExtension(dia.getAbsolutePath());
			ContextFeatureScorer.Options defaults = ContextFeatureScorer.Options.defaults();
			ContextFeatureScorer.Options scoringOptions = new ContextFeatureScorer.Options(defaults.predictionNce(),
					defaults.predictionDefaultCharge(), defaults.adjustNceForDia(), defaults.candidateDiagnostics(), allowLegacy,
					ContextFeatureScorerCLI.predictionStore(options.get("--prediction-store")),
					Boolean.parseBoolean(options.getOrDefault("--clear-stale-store-lock", "false")));
			ContextFeatureScorer.scoreFeaturesForContext(library, dia, fasta, base, massList.getAbsolutePath(), 0,
					scoringOptions);
			background = new File(base + "_background.features.txt");
			reference = new File(base + "_reference.features.txt");
			input = dia;
		}
		SearchPairSidecar.requirePairMetadata(background, reference, allowLegacy);
		EncyclopediaFeatures.requireMatchingHeaders(background, reference);
		File root = options.containsKey("-o") ? new File(options.get("-o")) : input.getAbsoluteFile().getParentFile();
		String prefix = options.getOrDefault("-prefix", direct ? DirectoryOptions.stripSplitSuffix(background)
				: RawFiles.stripExtension(input.getName()));
		if (prefix.isBlank() || prefix.contains("/") || prefix.contains("\\")) {
			throw new IllegalArgumentException("-prefix must be a nonempty filename component");
		}
		File engine = new File(root, "context-mprophet");
		if (engine.exists()) throw new IOException("Choose a fresh mProphet output directory: " + engine);
		HashMap<String, String> parameterMap = SearchParameterParser.getDefaultParametersObject().toParameterMap();
		parameterMap.put("-percolatorThreshold", Float.toString(fdr));
		SearchParameters parameters = SearchParameterParser.parseParameters(parameterMap);
		AllWorkflowExecutorCLI.runContextMProphet(background, reference, fasta, parameters, fdr, root, prefix, 1);
		System.out.println("Context mProphet results: " + engine.getAbsolutePath());
	}

	static Map<String, String> parse(String[] args) {
		if (args.length == 0) throw new IllegalArgumentException("Expected flag/value pairs");
		HashMap<String, String> options = new HashMap<>();
		for (int i = 0; i < args.length;) {
			String flag = args[i++];
			if ("-generateDecoys".equals(flag)) {
				if (options.putIfAbsent(flag, "true") != null) throw new IllegalArgumentException("Duplicate option: " + flag);
				continue;
			}
			if (!FLAGS.contains(flag) || i >= args.length || options.putIfAbsent(flag, args[i]) != null) {
				throw new IllegalArgumentException("Unknown, duplicate, or missing value for option: " + flag);
			}
			if (args[i].isBlank()) throw new IllegalArgumentException("Empty value for " + flag);
			i++;
		}
		for (String flag : new String[] {"--allow-legacy-features", "--clear-stale-store-lock"}) {
			if (options.containsKey(flag) && !Set.of("true", "false").contains(options.get(flag).toLowerCase())) {
				throw new IllegalArgumentException(flag + " must be true or false");
			}
		}
		return options;
	}

	private static File readable(Map<String, String> options, String flag) throws IOException {
		String value = options.get(flag);
		if (value == null) throw new IllegalArgumentException("Missing required option: " + flag);
		File file = new File(value);
		if (!file.isFile() || !file.canRead()) throw new IOException(flag + " is not a readable file: " + file);
		return file;
	}

	static File readableAcquisition(Map<String, String> options) throws IOException {
		String value = options.get("-i");
		if (value == null) throw new IllegalArgumentException("Missing required option: -i");
		File file = new File(value);
		if (file.isDirectory() && file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".d")
				&& file.canRead()) return file;
		if (file.isFile() && file.canRead()) return file;
		throw new IOException("-i is not a readable acquisition file or Bruker .d directory: " + file);
	}

	private static void printUsage() {
		System.out.println("Usage: context.jar mprophet -i <run.dia> -l <library.elib> -f <proteins.fasta> -massList <assay.txt> [-o <results>] [-fdr <fraction>]");
		System.out.println("   or: context.jar mprophet -background <background.features.txt> -reference <reference.features.txt> -f <proteins.fasta> [-o <results>] [-fdr <fraction>]");
		System.out.println("Options: -prefix <name>; --allow-legacy-features true for old split files without pair sidecars.");
		System.out.println("-generateDecoys is accepted for raw runs; Context scoring ensures assay decoys when needed.");
		System.out.println("Raw runs only: --prediction-store <folder> shares stored predictions across folders (default:");
		System.out.println("prediction-cache beside the acquisition); --clear-stale-store-lock true clears a lock left by an interrupted run.");
	}
}
