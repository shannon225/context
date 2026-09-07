package org.searlelab.context.percolator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.PercolatorExecutionData;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.PercolatorExecutor;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.PercolatorVersion;
import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchParameters;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;
import edu.washington.gs.maccoss.encyclopedia.utils.Logger;

import org.searlelab.context.datastructures.EncyclopediaFeatures;
import org.searlelab.context.datastructures.ContextFeatures;
import org.searlelab.context.io.DirectoryOptions;

public class ContextPercolator {
	public static final String ENGINE_NAME = "percolator";

	public static final float DEFAULT_FDR = 0.01f;

	private static final int FIRST_ROUND = 1;

	static final String SCORE_COLUMN = "score";
	static final String Q_VALUE_COLUMN = "q-value";
	static final String PEP_COLUMN = "posterior_error_prob";

	private static final String TARGET_LABEL = "1";
	private static final String DECOY_LABEL = "-1";

	private ContextPercolator() {
	}

	public static ContextPercolatorResult trainAndApply(File backgroundFeatures, File referenceFeatures, File fasta,
			HashMap<String, String> encyclopediaArgs, PyIsoPEPRunner pyIsoPEP, float fdr, File outputDirectory,
			String prefix) throws IOException, InterruptedException {
		
		return trainAndApply(backgroundFeatures, referenceFeatures, fasta, encyclopediaArgs, pyIsoPEP, fdr, outputDirectory, prefix, TrainingSeeds.DEFAULT_SEED);
	}

	public static ContextPercolatorResult trainAndApply(File backgroundFeatures, File referenceFeatures, File fasta, HashMap<String, String> encyclopediaArgs, PyIsoPEPRunner pyIsoPEP, float fdr, File outputDirectory, String prefix, int seed) throws IOException, InterruptedException {
		requireReadable(fasta, "FASTA file");

		TrainingSeeds.requireValid(seed);
		requireReadable(fasta, "FASTA file");
		
		File engineDirectory = DirectoryOptions.engineDirectory(outputDirectory, ENGINE_NAME);
		File workingDirectory = DirectoryOptions.subdirectory(engineDirectory, DirectoryOptions.WORK_DIRECTORY);
		File modelDirectory = DirectoryOptions.subdirectory(engineDirectory, DirectoryOptions.MODEL_DIRECTORY);

		ContextFeatures prepared = ContextFeatures.prepare(backgroundFeatures, referenceFeatures, workingDirectory,
				prefix);

		File nativeWeights = new File(modelDirectory, prefix + ".weights.txt");
		PercolatorWeights model = train(prepared.getBackground(), fasta, encyclopediaArgs, workingDirectory, prefix,
				fdr, nativeWeights, seed);

		File averagedWeights = new File(modelDirectory, prefix + ".weights.averaged.txt");
		model.writeAveraged(averagedWeights);

		EncyclopediaFeatures prunedReferenceTable = prepared.getReferenceTable();
		checkModelMatchesTable(model, prunedReferenceTable);

		File rescoredFeatures = new File(engineDirectory, prefix + ".rescored_features.txt");
		int[] counts = writeRescoredFeatures(prunedReferenceTable, model, rescoredFeatures);
		Logger.logLine("Rescored " + prunedReferenceTable.size() + " reference rows (" + counts[0] + " targets, "
				+ counts[1] + " decoys) with the background model");

		File rawPsmReport = new File(workingDirectory, prefix + ".psm.pyisopep.txt");
		PyIsoPEPRunner.Table psmTable = pyIsoPEP.runD2PEP(rescoredFeatures, rawPsmReport, SCORE_COLUMN,
				prunedReferenceTable.getHeader()[EncyclopediaFeatures.LABEL_INDEX], TARGET_LABEL, DECOY_LABEL);

		File psmOutput = new File(engineDirectory, prefix + ".psm.reference.txt");
		writePyIsoPEPReport(psmTable, prunedReferenceTable, psmOutput);

		File peptideInput = new File(workingDirectory, prefix + ".peptide.rescored.txt");
		int peptideRows = writeBestPerPeptide(rescoredFeatures, prunedReferenceTable, peptideInput);

		File rawPeptideReport = new File(workingDirectory, prefix + ".peptide.pyisopep.txt");
		PyIsoPEPRunner.Table peptideTable = pyIsoPEP.runD2PEP(peptideInput, rawPeptideReport, SCORE_COLUMN,
				prunedReferenceTable.getHeader()[EncyclopediaFeatures.LABEL_INDEX], TARGET_LABEL, DECOY_LABEL);

		File peptideOutput = new File(engineDirectory, prefix + ".peptide.reference.txt");
		writePyIsoPEPReport(peptideTable, prunedReferenceTable, peptideOutput);

		int passingPeptides = countPeptidesPassingPEPThreshold(peptideTable, fdr);

		Logger.logLine(
				"Wrote " + rescoredFeatures.getName() + " (" + prunedReferenceTable.size() + " rows including decoys)");
		Logger.logLine("Wrote " + psmOutput.getName() + " (" + psmTable.size() + " reference target PSMs)");
		Logger.logLine(
				"Wrote " + peptideOutput.getName() + " (" + peptideTable.size() + " reference target peptides from "
						+ peptideRows + " deduplicated rows, " + passingPeptides + " at " + (fdr * 100f) + "% FDR)");
		Logger.logLine("Results are under " + engineDirectory.getAbsolutePath());

		return new ContextPercolatorResult(model, nativeWeights, averagedWeights, rescoredFeatures, psmOutput,
				peptideOutput, rawPsmReport, rawPeptideReport, psmTable.size(), peptideTable.size(), passingPeptides,
				fdr);
	}

	private static PercolatorWeights train(File prunedBackground, File fasta, HashMap<String, String> encyclopediaArgs,
			File workingDirectory, String prefix, float fdr, File weightsDestination, int seed)
			throws IOException, InterruptedException {

		SearchParameters parameters = SearchParameterParser.parseParameters(copyOf(encyclopediaArgs));
		PercolatorVersion version = parameters.getPercolatorVersionNumber();

		File peptideOutput = new File(workingDirectory, prefix + ".background.percolator.peptides.txt");
		File peptideDecoy = new File(workingDirectory, prefix + ".background.percolator.decoy.peptides.txt");
		File proteinOutput = new File(workingDirectory, prefix + ".background.percolator.proteins.txt");
		File proteinDecoy = new File(workingDirectory, prefix + ".background.percolator.decoy.proteins.txt");
		ContextPercolatorExecutionData trainingRun = new ContextPercolatorExecutionData(prunedBackground, fasta, peptideOutput,
				peptideDecoy, proteinOutput, proteinDecoy, parameters);
		
		Files.deleteIfExists(peptideOutput.toPath());
		Files.deleteIfExists(peptideDecoy.toPath());
		Files.deleteIfExists(trainingRun.getModelFile().toPath());
		Files.deleteIfExists(trainingRun.getWeightsFile(FIRST_ROUND).toPath());

		Logger.logLine("Training Percolator on the background with seed " + seed + "...");
		ContextPercolatorRunner.executePercolatorTSV(version, trainingRun, fdr, parameters.getAAConstants(), FIRST_ROUND, seed);

		File trainedModel = trainingRun.getModelFile();
		if (!trainedModel.exists() || !trainedModel.canRead()) {
			throw new IOException("Percolator finished but wrote no model to " + trainedModel.getAbsolutePath()
					+ ". Check the Percolator output above.");
		}

		PercolatorWeights model = PercolatorWeights.parse(trainedModel);
		Logger.logLine("Trained model: " + model);

		copyFile(trainedModel, weightsDestination);

		moveFile(trainedModel, new File(workingDirectory, prefix + ".background.percolator.peptides.model"));
		return model;
	}

	private static void checkModelMatchesTable(PercolatorWeights model, EncyclopediaFeatures table) throws IOException {
		List<String> modelFeatures = model.getFeatureNames();
		List<String> tableFeatures = table.getFeatureNames();
		if (!modelFeatures.equals(tableFeatures)) {
			throw new IOException("Percolator's weights name features that the reference table does not have in the "
					+ "same order.\n  model:     " + modelFeatures + "\n  reference: " + tableFeatures);
		}
	}

	private static int[] writeRescoredFeatures(EncyclopediaFeatures table, PercolatorWeights model,
			File rescoredFeatures) throws IOException {

		int[] featureIndices = table.getFeatureIndices();
		int targets = 0;
		int decoys = 0;

		try (BufferedWriter out = new BufferedWriter(new FileWriter(rescoredFeatures))) {
			out.write(String.join("\t", table.getHeader()));
			out.write("\t" + SCORE_COLUMN + "\n");

			for (String[] row : table.getRows()) {
				boolean isDecoy = table.isDecoy(row);
				if (isDecoy) {
					decoys++;
				} else {
					targets++;
				}

				String[] normalised = row.clone();
				normalised[EncyclopediaFeatures.LABEL_INDEX] = isDecoy ? DECOY_LABEL : TARGET_LABEL;

				out.write(String.join("\t", normalised));
				out.write("\t" + model.score(table.getFeatureValues(row, featureIndices)) + "\n");
			}
		}
		return new int[] { targets, decoys };
	}

	private static int writeBestPerPeptide(File rescoredFeatures, EncyclopediaFeatures referenceTable,
			File peptideInput) throws IOException {

		PyIsoPEPRunner.Table rescored = PyIsoPEPRunner.Table.read(rescoredFeatures);
		String peptideColumn = referenceTable.getHeader()[referenceTable.getPeptideIndex()];
		int peptideIndex = rescored.indexOf(peptideColumn);
		int scoreIndex = rescored.indexOf(SCORE_COLUMN);
		if (peptideIndex < 0 || scoreIndex < 0) {
			throw new IOException(
					"Rescored features are missing the [" + peptideColumn + "] or [" + SCORE_COLUMN + "] column.");
		}

		ArrayList<String[]> sorted = new ArrayList<>(rescored.getRows());
		sorted.sort(Comparator.comparingDouble((String[] row) -> parseScore(row, scoreIndex)).reversed());

		Set<String> seen = new LinkedHashSet<>();
		ArrayList<String[]> best = new ArrayList<>();
		for (String[] row : sorted) {
			if (seen.add(row[peptideIndex]))
				best.add(row);
		}

		try (BufferedWriter out = new BufferedWriter(new FileWriter(peptideInput))) {
			out.write(String.join("\t", rescored.getHeader()));
			out.write("\n");
			for (String[] row : best) {
				out.write(String.join("\t", row));
				out.write("\n");
			}
		}
		return best.size();
	}

	private static double parseScore(String[] row, int index) {
		try {
			return Double.parseDouble(row[index].trim());
		} catch (NumberFormatException nfe) {
			return Double.NEGATIVE_INFINITY;
		}
	}

	private static void writePyIsoPEPReport(PyIsoPEPRunner.Table table, EncyclopediaFeatures referenceTable,
			File outputFile) throws IOException {

		String[] header = referenceTable.getHeader();
		String idColumn = header[EncyclopediaFeatures.ID_INDEX];
		int scanIndex = referenceTable.getScanIndex();
		String scanColumn = scanIndex < 0 ? "ScanNr" : header[scanIndex];
		String peptideColumn = header[referenceTable.getPeptideIndex()];
		String proteinColumn = header[referenceTable.getProteinIndex()];

		try (BufferedWriter out = new BufferedWriter(new FileWriter(outputFile))) {
			out.write(String.join("\t", idColumn, scanColumn, SCORE_COLUMN, Q_VALUE_COLUMN, PEP_COLUMN, peptideColumn,
					proteinColumn));
			out.write("\n");

			for (String[] row : table.getRows()) {
				out.write(String.join("\t", table.get(row, idColumn), table.get(row, scanColumn),
						table.get(row, SCORE_COLUMN), table.get(row, PyIsoPEPRunner.Q_VALUE_COLUMN),
						table.get(row, PyIsoPEPRunner.PEP_COLUMN), table.get(row, peptideColumn),
						table.get(row, proteinColumn)));
				out.write("\n");
			}
		}
	}

	private static int countPeptidesPassingPEPThreshold(PyIsoPEPRunner.Table table, float fdr) {
		int passing = 0;
		for (String[] row : table.getRows()) {
			try {
				if (Double.parseDouble(table.get(row, PyIsoPEPRunner.Q_VALUE_COLUMN).trim()) <= fdr)
					passing++;
			} catch (NumberFormatException nfe) {
			}
		}
		return passing;
	}

	private static HashMap<String, String> copyOf(HashMap<String, String> arguments) {
		return arguments == null ? new HashMap<String, String>() : new HashMap<>(arguments);
	}

	private static void copyFile(File from, File to) throws IOException {
		Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
	}

	private static void moveFile(File from, File to) throws IOException {
		Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
	}

	private static void requireReadable(File file, String description) throws IOException {
		if (file == null || !file.exists() || !file.canRead()) {
			throw new IOException(
					"Cannot read the " + description + ": " + (file == null ? "null" : file.getAbsolutePath()));
		}
	}
}
