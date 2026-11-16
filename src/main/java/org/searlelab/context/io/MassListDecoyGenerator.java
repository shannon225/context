package org.searlelab.context.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.searlelab.context.datastructures.IsolationWindow;

import edu.washington.gs.maccoss.encyclopedia.datastructures.AminoAcidConstants;
import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchParameters;
import edu.washington.gs.maccoss.encyclopedia.filereaders.PecanParameterParser;
import edu.washington.gs.maccoss.encyclopedia.utils.CommandLineParser;
import edu.washington.gs.maccoss.encyclopedia.utils.Logger;
import edu.washington.gs.maccoss.encyclopedia.utils.massspec.PeptideUtils;

public class MassListDecoyGenerator {

	public static final String GENERATED_SUFFIX = "_td_assay.txt";
	private static final String ASSAY_TXT_SUFFIX = "_assay.txt";

	public static void main(String[] args) throws Exception {
		HashMap<String, String> arguments = CommandLineParser.parseArguments(args);

		if (args.length == 0 || arguments.containsKey("-h") || arguments.containsKey("-help")
				|| arguments.containsKey("--help")) {
			printHelp();
			System.exit(args.length == 0 ? 1 : 0);
		}

		String massListPath = arguments.get("--massList");
		if (massListPath == null) {
			Logger.errorLine("Missing required argument: --massList");
			printHelp();
			System.exit(1);
		}
		File massList = new File(massListPath);
		if (!massList.exists()) {
			Logger.errorLine("Mass list not found: " + massList.getAbsolutePath());
			System.exit(1);
		}

		String outputValue = arguments.get("-o");
		if (outputValue == null)
			outputValue = arguments.get("--outdir");

		File output = outputValue == null ? defaultOutput(massList.getAbsoluteFile().getParentFile(), massList)
				: new File(outputValue);

		if (output.isDirectory())
			output = defaultOutput(output, massList);
		
		String seed = arguments.get("--shuffledSeed");
		int shuffledSeed = seed == null ? 0 : Integer.parseInt(seed);

		writeDecoysToAssay(addShuffledDecoysToAssay(massList.getAbsolutePath(), shuffledSeed), output);
	}

	public static File checkIfDecoysArePresent(File massList, File outputDirectory, int shuffledSeed) throws IOException {

		if (hasDecoys(massList.getAbsolutePath())) {

			Logger.logLine("Mass list " + massList.getName() + " already contains decoys; using mass list as is");
			
			return massList;
		}

		DirectoryOptions.makeDirectory(outputDirectory);
		
		File output = defaultOutput(outputDirectory, massList);
		Logger.logLine("Mass list " + massList.getName() + " has no decoys; generating entrapment decoys");
		
		writeDecoysToAssay(addShuffledDecoysToAssay(massList.getAbsolutePath(), shuffledSeed), output);
		
		return output;
	}

	public static boolean hasDecoys(String massListPath) {
		for (IsolationWindow window : IsolationWindowReader.parseMassList(massListPath)) {
			if (window.isDecoy())
				return true;
		}
		return false;
	}

	public static ArrayList<IsolationWindow> addShuffledDecoysToAssay(String massListPath, int shuffledSeed) {
		ArrayList<IsolationWindow> input = IsolationWindowReader.parseMassList(massListPath);

		for (IsolationWindow window : input) {
			if (window.isDecoy()) {
				
				Logger.logLine("Mass list already contains decoys; leaving it unchanged");
				return input;
				
			}
		}

		SearchParameters params = PecanParameterParser.getDefaultParametersObject();
		AminoAcidConstants constants = new AminoAcidConstants();

		HashSet<String> takenSequences = new HashSet<>();
		ArrayList<IsolationWindow> output = new ArrayList<>();

		for (IsolationWindow target : input) {
			
			output.add(target);

			String sequence = target.getCompound();
			byte charge = target.getCharge();
			takenSequences.add(sequence);
			String decoy = PeptideUtils.shuffle(sequence, shuffledSeed, params); // Use shuffled decoys for entrapment

			String correctedDecoyMass = PeptideUtils.getCorrectedMasses(decoy, constants);
			double decoyMz = constants.getChargedMass(correctedDecoyMass, charge);
			takenSequences.add(decoy);

			output.add(new IsolationWindow(decoy, decoyMz, charge, target.getRtMin(), target.getRtMax(), true));
			
		}

		return output;
	}

	private static void writeDecoysToAssay(ArrayList<IsolationWindow> windows, File output) throws IOException {
		Path parent = output.getAbsoluteFile().toPath().getParent();
		if (parent != null)
			DirectoryOptions.makeDirectory(parent.toFile());

		new TargetedBootstrapper().writeAssayList(windows, output.toPath());

		int decoys = 0;
		for (IsolationWindow window : windows) {
			if (window.isDecoy())
				decoys++;
		}
		Logger.logLine("Wrote " + output.getAbsolutePath() + ": " + (windows.size() - decoys) + " targets, " + decoys
				+ " decoys");
	}

	private static File defaultOutput(File directory, File massList) {

		String prefix = getAssayPrefix(massList);

		return new File(directory, prefix + GENERATED_SUFFIX);
	}

	private static String getAssayPrefix(File massList) {

		String fileName = massList.getName();

		if (!fileName.endsWith(ASSAY_TXT_SUFFIX)) {
			throw new IllegalArgumentException(
					"Error with assay. Check to ensure assay file is real, and ends in .txt");
		}
		return fileName.substring(0, fileName.length() - ASSAY_TXT_SUFFIX.length());
	}

	private static void printHelp() {
		Logger.timelessLogLine("MassListDecoyGenerator");
		Logger.timelessLogLine("Add entrapment decoys to an assay that only lists targets, so that the");
		Logger.timelessLogLine("reference peptides have something to compete against.");
		Logger.timelessLogLine("");
		Logger.timelessLogLine("  -massList <file>   assay / mass list, .csv or .txt, 7 or 8 columns");
		Logger.timelessLogLine("  -o        <path>   file to write, or a directory to write into");
		Logger.timelessLogLine("                     (default: next to the input)");
		Logger.timelessLogLine("");
	}

	public static File resolveDecoysMessage(File massList, File outputDirectory, String resolvedPrefix,
			boolean generateDecoys) throws IOException {
		if (!generateDecoys)
			return massList;

		return checkIfDecoysArePresent(massList, outputDirectory, 0);
	}
}
