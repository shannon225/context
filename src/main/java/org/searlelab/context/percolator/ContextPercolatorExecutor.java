package org.searlelab.context.percolator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.nio.file.Files;

import org.searlelab.context.io.ContextFeatureScorer;
import org.searlelab.context.io.DirectoryOptions;
import org.searlelab.context.io.MassListDecoyGenerator;
import org.searlelab.context.io.RawFiles;

import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.PercolatorExecutionData;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.PercolatorExecutor;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.PercolatorPeptide;
import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchParameters;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;
import edu.washington.gs.maccoss.encyclopedia.utils.Logger;
import edu.washington.gs.maccoss.encyclopedia.utils.Pair;


public class ContextPercolatorExecutor {
	private static final String STANDARD_ENGINE_NAME = "standard-percolator";
	private static final String TARGET_ENGINE_NAME = "target-percolator";
	private static final int FINAL_ROUND = 2;

	public static ContextPercolatorResult runContextPercolator(File library, File fasta, File dia, File massList,
			PyIsoPEPRunner pyIsoPEP, float fdr, File outputDirectory, String prefix, boolean generateDecoys,
			HashMap<String, String> encyclopediaArgs, int shuffledSeed) throws Exception {

		if (!dia.exists()) throw new IOException("Input file not found: " + dia);
		if (!library.exists()) throw new IOException("Library file not found: " + library);
		if (!fasta.exists()) throw new IOException("FASTA file not found: " + fasta);
		if (!massList.exists()) throw new IOException("Mass list file not found: " + massList);
		RawFiles.requireSupported(dia);

		String baseName = RawFiles.baseName(dia);
		String resolvedPrefix = prefix != null ? prefix : new File(baseName).getName();

		File splitOn = MassListDecoyGenerator.resolveDecoysMessage(massList, outputDirectory, resolvedPrefix,
				generateDecoys);

		Logger.logLine("Scoring " + dia.getName() + " against " + library.getName());
		ContextFeatureScorer.scoreFeaturesForContext(library, dia, fasta, baseName, splitOn.getAbsolutePath(), shuffledSeed);

		File backgroundFeatures = new File(baseName + "_background.features.txt");
		File referenceFeatures = new File(baseName + "_reference.features.txt");

		return ContextPercolator.trainAndApply(backgroundFeatures, referenceFeatures, fasta, encyclopediaArgs,
				pyIsoPEP, fdr, outputDirectory, resolvedPrefix);
	}

	
	public static PercolatorExecutionData runStandardPercolator(File features, File fasta, PyIsoPEPRunner pyIsoPEP, float fdr, File outputDirectory, String prefix, HashMap<String, String> encyclopediaArgs, String engineName, String workflowName) throws IOException, InterruptedException {
		return runPercolator(features, fasta, pyIsoPEP, fdr, outputDirectory, prefix, encyclopediaArgs, engineName, workflowName, TrainingSeeds.DEFAULT_SEED);
	}
	
	public static PercolatorExecutionData runStandardPercolator(File features, File fasta, PyIsoPEPRunner pyIsoPEP, float fdr, File outputDirectory, String prefix, HashMap<String, String> encyclopediaArgs, String engineName, String workflowName, int seed) throws IOException, InterruptedException {
		return runPercolator(features, fasta, pyIsoPEP, fdr, outputDirectory, prefix, encyclopediaArgs, STANDARD_ENGINE_NAME, "standard", seed);
	}
	
	public static PercolatorExecutionData runPercolator(File features, File fasta, PyIsoPEPRunner pyIsoPEP, float fdr, File outputDirectory, String prefix, HashMap<String, String> encyclopediaArgs, String engineName, String workflowName) throws IOException, InterruptedException {
		return runPercolator(features, fasta, pyIsoPEP, fdr, outputDirectory, prefix, encyclopediaArgs, engineName, workflowName, TrainingSeeds.DEFAULT_SEED);
		
	}
	public static PercolatorExecutionData runPercolator(File features, File fasta, PyIsoPEPRunner pyIsoPEP, float fdr, File outputDirectory, String prefix, HashMap<String, String> encyclopediaArgs, String engineName, String workflowName, int seed) throws IOException, InterruptedException {
		
		TrainingSeeds.requireValid(seed);

		if (!features.exists() || !features.canRead()) {
			throw new IOException("Feature file not found or is unreadable: " + features.getAbsolutePath());
		}

		if (!fasta.exists() || !fasta.canRead()) {
			throw new IOException("FASTA file not found or is unreadable: " + fasta);
		}

		if (!(fdr > 0.0f && fdr <= 1.0f)) {
			throw new IllegalArgumentException("FDR must a value between 0 and 1.");
		}

		HashMap<String, String> parameterMap = SearchParameterParser.getDefaultParametersObject().toParameterMap();

		if (encyclopediaArgs !=null) {
			parameterMap.putAll(encyclopediaArgs);
		}

		parameterMap.put("-percolatorThreshold",  Float.toString(fdr));
		SearchParameters parameters = SearchParameterParser.parseParameters(parameterMap);

		File engineDirectory = DirectoryOptions.engineDirectory(outputDirectory, engineName);
		File peptideTargets = new File(engineDirectory, prefix + ".peptide.target.txt");
		File peptideDecoys = new File(engineDirectory, prefix + ".peptide.decoy.txt");
		File proteinTargets = new File(engineDirectory, prefix + ".protein.target.txt");
		File proteinDecoys = new File(engineDirectory, prefix + ".protein.decoy.txt");

		ContextPercolatorExecutionData run = new ContextPercolatorExecutionData(features, fasta, peptideTargets, peptideDecoys, proteinTargets, proteinDecoys, parameters);

		deletePercolatorOutputs(run, peptideTargets, peptideDecoys, proteinTargets, proteinDecoys);

		Logger.logLine("Running " + workflowName + " standard Percolator cross-validation on " + features.getName());

		Pair<ArrayList<PercolatorPeptide>, Float> result = ContextPercolatorRunner.executePercolatorTSV(parameters.getPercolatorVersionNumber(), run, fdr, parameters.getAAConstants(), FINAL_ROUND, seed);

		File workingDirectory = DirectoryOptions.subdirectory(engineDirectory, DirectoryOptions.WORK_DIRECTORY);	
		Logger.logLine("Standard Percolator found " + result.x.size() + " peptides at " + (fdr * 100.0f)
				+ "% FDR (pi0 = " + result.y + ")");
		Logger.logLine("Standard Percolator results are under " + engineDirectory.getAbsolutePath());

		return run;

	}
	
	public static PercolatorExecutionData runTargetPercolator(File features, File fasta, PyIsoPEPRunner pyIsoPEP, float fdr, 
			File outputDirectory, String prefix, HashMap<String, String> encyclopediaArgs, String engineName, String workflowName) throws IOException, InterruptedException {
		return runPercolator(features, fasta, pyIsoPEP, fdr, outputDirectory, prefix, encyclopediaArgs, engineName, workflowName, TrainingSeeds.DEFAULT_SEED);
	}

	public static PercolatorExecutionData runTargetPercolator(File features, File fasta, PyIsoPEPRunner pyIsoPEP, float fdr, 
			File outputDirectory, String prefix, HashMap<String, String> encyclopediaArgs, String engineName, String workflowName, int seed) throws IOException, InterruptedException {
		return runPercolator(features, fasta, pyIsoPEP, fdr, outputDirectory, prefix, encyclopediaArgs, TARGET_ENGINE_NAME, "targeted", seed);
	}

	private static void deletePercolatorOutputs(PercolatorExecutionData commandData, File peptideTargets, File peptideDecoys, File proteinTargets, File proteinDecoys) throws IOException {
		Files.deleteIfExists(peptideTargets.toPath());
		Files.deleteIfExists(peptideDecoys.toPath());
		Files.deleteIfExists(proteinTargets.toPath());
		Files.deleteIfExists(proteinDecoys.toPath());
		Files.deleteIfExists(commandData.getModelFile().toPath());
		Files.deleteIfExists(commandData.getWeightsFile(FINAL_ROUND).toPath());
	}
	
}