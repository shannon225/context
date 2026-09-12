package org.searlelab.context.io;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;

import org.searlelab.context.mprophet.ContextMProphetExecutor;

import edu.washington.gs.maccoss.encyclopedia.algorithms.scribe.ScribeTwoSearchParameters;
import edu.washington.gs.maccoss.encyclopedia.utils.Logger;
import edu.washington.gs.maccoss.encyclopedia.utils.threading.EmptyProgressIndicator;

public class ScribeTwoFeatureScorerCLI {
	private ScribeTwoFeatureScorerCLI() {}

	public static void main(String[] args) throws Exception {
		run(args, false);
	}

	public static void run(String[] args, boolean trainContext) throws Exception {
		// Pre-alignment writes fit plots; this command never opens up a desktop UI. 
		System.setProperty("java.awt.headless", "true");
		if (args.length == 1 && (args[0].equals("--help") || args[0].equals("-h"))) {
			Logger.logLine("scribe-features <raw> <library> <fasta> <mass-list> <output-directory> [-parameter value...]");
			Logger.logLine("Uses Scribes prealignemnt trained on all populations, then exports and partitions calibrated features.");
			Logger.logLine("Scribe parameter examples: -numberOfThreadsUsed 2 -adjustTolerances true");
			Logger.logLine("Use scribe-Context with the same arguments to train final background LDA and apply model to reference features.");
			return;
		}
		if (args.length < 5 || (args.length - 5) % 2 != 0) {
			throw new IllegalArgumentException("Expected raw, likbrary, FASTA, mass-list, output-directory and property/value pairs; see --help");
		}

		HashMap<String, String> settings = ScribeTwoSearchParameters.getDefaultParameters();
		for (int i = 5; i < args.length; i += 2) {
			if (!settings.containsKey(args[i])) 
				throw new IllegalArgumentException("Unknown Scribe parameter: " + args[i]);
			settings.put(args[i], args[i+1]);
		}

		File massList = new File(args[3]);
		if (!massList.isFile() || !massList.canRead()) throw new 
		IllegalArgumentException("Unreadable mass list: " + massList);

		// Validate the assay before starting an expensive search. 
		IsolationWindowReader.parseMassList(massList.getAbsolutePath());
		var result = ScribeTwoFeatureGenerator.generate(new File(args[0]), new File(args[1]), new File(args[2]), Path.of(args[4]), ScribeTwoSearchParameters.parseParameters(settings), new EmptyProgressIndicator(true));

		var features = ContextFeatureScorer.partitionFeatures(result.features(), result.parameters(), result.outputPrefix().getAbsolutePath(), massList);
		Logger.logLine("Calibrated full features: " + result.features());
		Logger.logLine("Partitioned " + features.size() + "best peptide features under " + result.outputPrefix().getParent());
		if(trainContext) {
			String prefix = result.outputPrefix().getAbsolutePath();
			var analysis = ContextMProphetExecutor.trainAndApplyFeature(new File(prefix + "_background.features.txt"),
					new File(prefix + "_reference.features.txt"), 
					new File(args[2]), result.parameters());
			Logger.logLine("Context final training scope: BACKGROUND"
					+ "\nReference peptides passing a 0.01 PEP threshold: " + analysis.reference().getPassingPeptides().size());
		}

	}

}
