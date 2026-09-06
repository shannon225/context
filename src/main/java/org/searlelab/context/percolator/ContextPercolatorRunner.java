package org.searlelab.context.percolator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.PercolatorPeptide;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.PercolatorVersion;
import edu.washington.gs.maccoss.encyclopedia.datastructures.AminoAcidConstants;
import edu.washington.gs.maccoss.encyclopedia.filereaders.PercolatorReader;
import edu.washington.gs.maccoss.encyclopedia.utils.EncyclopediaException;
import edu.washington.gs.maccoss.encyclopedia.utils.Logger;
import edu.washington.gs.maccoss.encyclopedia.utils.Pair;
import edu.washington.gs.maccoss.encyclopedia.utils.io.FileConcatenator;
import edu.washington.gs.maccoss.encyclopedia.utils.io.OutputMessage;
import edu.washington.gs.maccoss.encyclopedia.utils.threading.ExternalExecutor;

/* Adapted from Encyclopedia's PercolatorExecutor (version 6.6.24).
 * Copyright 2018 Brian C. Searle (searleb@uw.edu). 
 * Licensed under the Apache License, Version 2.0: 
 * http://www.apache.org/licenses/LICENSE-2.0
 */
public class ContextPercolatorRunner extends ExternalExecutor {

	public static final String PI_0_TAG = "pi_0=";

	private static final Pattern PERCOLATOR_VERSION_PATTERN = Pattern.compile("Percolator version (.+),");
	private static final String SELECTING_PI_0 = "Selecting pi_0=";
	private static final String ERROR_PREFIX = "Error : ";
	private static final String BAD_ALLOCATION = "bad allocation";
	private static final String EXCEPTION_CAUGHT_PREFIX = "Exception caught: ";

	private ContextPercolatorRunner(String[] command) {
		super(command);
	}

	public static Pair<ArrayList<PercolatorPeptide>, Float> executePercolatorTSV(PercolatorVersion percolatorVersion, ContextPercolatorExecutionData commandData, float threshold, AminoAcidConstants aaConstants, int round, int seed) throws IOException, FileNotFoundException, UnsupportedEncodingException, InterruptedException {

		String[] command = generateCommand(percolatorVersion, commandData, round, seed);
		Files.write(new File(commandData.getPeptideOutputFile().getAbsolutePath() + ".command.txt").toPath(), java.util.Arrays.asList(command));

		ContextPercolatorRunner e = new ContextPercolatorRunner(command);
		BlockingQueue<OutputMessage> result = e.start();

		Float pi0 = null;
		String errorMessage = null;
		Optional<String> percolatorExecutableVersion = Optional.empty();
		while (!e.isFinished() || !result.isEmpty()) {
			if (!result.isEmpty()) {
				OutputMessage data = result.take();
				if(!data.isStdOutput()) {
					if (!percolatorExecutableVersion.isPresent()) {
						String message = data.getMessage();
						percolatorExecutableVersion = getPercolatorVersionFromOutput(message);
					}

					Logger.logLine(data.getMessage());String currentError = getErrorMessage(data);
					if(currentError != null) errorMessage = currentError;

					if (null == errorMessage) {
						final String trim = data.getMessage().trim();
						if (trim.startsWith(SELECTING_PI_0)) {
							try {
								pi0 = Float.parseFloat(trim.substring(SELECTING_PI_0.length()));
							} catch (NumberFormatException nfe) {
								Logger.errorLine("Error parsing pi0 from [" + trim + "]");
							}
						}
					}
				}
			} else {
				Thread.sleep(10);
			}
		}

		if (errorMessage != null) {
			throw new EncyclopediaException(errorMessage); 
		}

		checkResult(e);

		try {
			Files.write(commandData.getPeptideOutputFile().toPath(), (PI_0_TAG + pi0 + System.lineSeparator()).getBytes(), StandardOpenOption.APPEND);


			// if round 1, then start the model file over, otherwise append weights to model file
			if (round == 1) {
				Files.move(commandData.getWeightsFile(round).toPath(), commandData.getModelFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
			} else {
				FileConcatenator.saveConcatenatedFile(commandData.getModelFile(), commandData.getWeightsFile(round));
				Files.delete(commandData.getWeightsFile(round).toPath());
			}
		} catch (IOException ioe) {
			throw new EncyclopediaException("Error appending to Percolator text file.", ioe);
		}

		commandData.setPercolatorExecutableVersion(percolatorExecutableVersion.orElse(null));

		Pair<ArrayList<PercolatorPeptide>, Float> passingPeptides = PercolatorReader.getPassingPeptidesFromTSV(commandData.getPeptideOutputFile(), threshold, aaConstants, false);

		if (commandData.getParameters().getNumberOfExtraDecoyLibrariesSearched()>0.0f) {
			// entrapment search 
			int targets = 0;
			int traps = 0;
			for (PercolatorPeptide pep : passingPeptides.x) {
				if (!pep.isPSMIDDecoy()) {
					if (pep.isEntrapment()) {
						traps++;
					} else {
						targets++;
					}
				}
			}

			Logger.logLine("Entrapment Analysis found " + traps + " entrapment peptides and " + targets + " target peptides, (" + new DecimalFormat("#,#").format(100.0f*traps/(float) targets) + "%)");
		}

		return passingPeptides;
	}

	static String getErrorMessage(OutputMessage data) {
		final String trim = data.getMessage().trim();

		final String errorMessage;
		if(trim.startsWith(ERROR_PREFIX)) {
			errorMessage = trim.substring(ERROR_PREFIX.length());
		} else if (trim.startsWith(EXCEPTION_CAUGHT_PREFIX)) {
			errorMessage = trim.substring(EXCEPTION_CAUGHT_PREFIX.length());
		} else if (trim.contains(BAD_ALLOCATION)) {
			errorMessage = trim;
		} else {
			errorMessage = null;
		}
		return errorMessage;
	} 

	static Optional<String> getPercolatorVersionFromOutput(String standardOutputLine) {
		Matcher matcher = PERCOLATOR_VERSION_PATTERN.matcher(standardOutputLine);
		if(matcher.find()) {
			return Optional.ofNullable(matcher.group(1));
		} else {
			return Optional.empty();
		}
	}

	private static void checkResult(ExternalExecutor e) throws EncyclopediaException {
		if (0 != e.getResultCode()) {
			throw new EncyclopediaException("Percolator exited with non-zero status: " + e.getResultCode());
		}
	}

	static String parsePeptideSequence(String peptideString) {
		return peptideString.substring(peptideString.indexOf('.') + 1, peptideString.lastIndexOf('.'));
	}

	static String[] generateCommand(PercolatorVersion percolatorVersion, ContextPercolatorExecutionData commandData, int round, int seed) {
		TrainingSeeds.requireValid(seed);
		File percolator = percolatorVersion.getPercolator();

		ArrayList<String> params = new ArrayList<>();

		params.add(percolator.getAbsolutePath());
		params.add("--seed"); params.add(Integer.toString(seed));
		params.add("--results-peptides"); params.add(commandData.getPeptideOutputFile().getAbsolutePath());
		params.add("--weights");  params.add(commandData.getWeightsFile(round).getAbsolutePath());
		params.add("--decoy-results-peptides"); params.add(commandData.getPeptideDecoyFile().getAbsolutePath());
		if (commandData.isUseMinMax()) {
			params.add("-y");
		} else {
			params.add("-Y");
		}

		if (commandData.getPercolatorModelFile().isPresent() && commandData.getPercolatorModelFile().get().canRead()) {
			File modelFile = commandData.getPercolatorModelFile().get();

			try {
				int actualRound = Math.min(round,  FileConcatenator.getNumberOfSubFiles(modelFile));
				File model = FileConcatenator.extractFile(modelFile, actualRound);
				Logger.logLine("Extracting weights from " + modelFile.getName() + " (" + round + "," + actualRound + ")");

				if (round != actualRound) {
					Logger.errorLine("Couldn't extract specific model for round " + round + ", using last model available (round " + actualRound + ")");
				}

				params.add("--init-weights"); params.add(model.getAbsolutePath());
				params.add("--maxiter"); params.add("0");

			} catch (IOException ioe) {
				Logger.errorLine("Problem extracting Percolator weights from " + modelFile.getName() + ". Continuing without using weights...");;
				Logger.errorException(ioe);;
				if (commandData.getParameters().getScoringBreadthType().runRecalibration() && round == 1) {
					params.add("--maxiter"); params.add(Integer.toString(Math.min(1, commandData.getParameters().getPercolatorTrainingIterations())));
				} else {
					params.add("--maxiter"); params.add(Integer.toString(commandData.getParameters().getPercolatorTrainingIterations()));
				}
			}
		} else {
			if (commandData.getParameters().getScoringBreadthType().runRecalibration() && round == 1) {
				params.add("--maxiter"); params.add(Integer.toString(Math.min(3,  commandData.getParameters().getPercolatorTrainingIterations())));
			} else {
				params.add("--maxiter"); params.add(Integer.toString(commandData.getParameters().getPercolatorTrainingIterations()));
			}
		}

		if (percolatorVersion.getMajorVersion() >2) {
			params.add("--no-terminate");
			params.add("-N"); params.add(Integer.toString(commandData.getParameters().getPercolatorTrainingSetSize()));

			final float percolatorTestThreshold = commandData.getParameters().getPercolatorTestThreshold();
			params.add("--testFDR"); params.add(Float.toString(percolatorTestThreshold));

			final float percolatorTrainingSetThreshold = commandData.getParameters().getPercolatorTrainingSetThreshold();
			if (percolatorTrainingSetThreshold > 0.0) {
				params.add("--trainFDR"); params.add(Float.toString(percolatorTrainingSetThreshold));
			} else {
				params.add("--trainFDR"); params.add(Float.toString(percolatorTestThreshold));
			}
		}
		params.add(commandData.getInputTSV().getAbsolutePath());

		return params.toArray(new String[params.size()]);
	}
}
