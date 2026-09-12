package org.searlelab.context.io;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.zip.DataFormatException;

//import edu.washington.gs.maccoss.encyclopedia.algorithms.library.EncyclopediaScoringFactory;
import edu.washington.gs.maccoss.encyclopedia.algorithms.library.EncyclopediaTwoJobData;
import edu.washington.gs.maccoss.encyclopedia.algorithms.library.LibraryScoringFactory;
import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchParameters;
import edu.washington.gs.maccoss.encyclopedia.filereaders.BlibToLibraryConverter;
import edu.washington.gs.maccoss.encyclopedia.filereaders.LibraryInterface;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;
import edu.washington.gs.maccoss.encyclopedia.utils.Logger;
import edu.washington.gs.maccoss.encyclopedia.utils.massspec.PeptideUtils;
import edu.washington.gs.maccoss.encyclopedia.utils.threading.EmptyProgressIndicator;
import edu.washington.gs.maccoss.encyclopedia.utils.threading.ProgressIndicator;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.PercolatorPeptide;

import org.searlelab.context.datastructures.IsolationWindow;
import org.searlelab.context.datastructures.ScoredFeature;
import org.searlelab.context.encyclopedia.EncyclopediaTwo;
import org.searlelab.context.encyclopedia.ContextEncyclopediaScoringFactory;
import org.searlelab.context.encyclopedia.EncyclopediaTwoPointOneScoringTask;
import org.searlelab.msrawjava.Main.CliArguments;

public class ContextFeatureScorer {

	public static void execute(String[] args) throws IOException, SQLException, InterruptedException, DataFormatException {

		if (args.length != 4) {
			System.err.println("Usage: ");
			System.err.println("java org.searlelab.context.mprophet.ContextFeatureScorer "
					+ "<rawFilePath> <libraryFilePath> <fastaPath> <massListPath>");
			System.err.println("rawFilePath accepts " + RawFiles.supportedExtensions());
			System.exit(1);
		}

		String rawFilePath = args[0];
		String libraryFilePath = args[1];
		String fastaPath = args[2];
		String massListPath = args[3];
		String seedForDecoys = args[7];
		
		String baseName = RawFiles.stripExtension(rawFilePath);
		final File fasta = new File(fastaPath);
		File rawFile = new File(rawFilePath);
		File library = new File(libraryFilePath);
		int shuffledSeed = Integer.parseInt(seedForDecoys);
		

		try {
			ArrayList<ScoredFeature> partitionedFeatures = scoreFeaturesForContext(library, rawFile, fasta, baseName,
					massListPath, shuffledSeed);
			
			Logger.logLine("Scored and partitioned " + partitionedFeatures.size() + " features.");
		} catch (Exception e) {
			Logger.logLine("Something did not work... see the error tace");
			e.printStackTrace();
		} finally {
			Logger.logLine("Program concluded.");
		}
	}

	static IsolationWindow findMatchingMassListWindow(ScoredFeature feature, ArrayList<IsolationWindow> targetWindows,
			SearchParameters parameters) {

		String sequence = cleanPeptideSequence(feature.getSequence());

		// Use decoys that are in the assay as entrapment decoys
		for (IsolationWindow window : targetWindows) {
			String compound = cleanPeptideSequence(window.getCompound());

			if (compound.equals(sequence)) {
				return window;
			}
		}

		// Support entrapment decoys
		for (IsolationWindow window : targetWindows) {
			String compound = cleanPeptideSequence(window.getCompound());
			String encyclopediaDecoy = PeptideUtils.reverse(compound, parameters.getAAConstants());

			if (encyclopediaDecoy.equals(sequence)) {
				return window;
			}
		}
		return null;
	}

	private static File prepareMassList(String massListPath, String baseName, int shuffledSeed) throws IOException {
		File inputMassList = new File(massListPath);

		File outputPrefix = new File(baseName).getAbsoluteFile();
		File outputDirectory = outputPrefix.getParentFile();

		return MassListDecoyGenerator.checkIfDecoysArePresent(inputMassList, outputDirectory, 0);
	}

	private static String cleanPeptideSequence(String sequence) {
		if (sequence == null)
			return "";

		return sequence.trim().replaceFirst("^[A-Za-z-]?\\.", "").replaceFirst("\\.[A-Za-z-]?$", "");
	}

	private static void writeScoredFeatures(File outputFile, ArrayList<ScoredFeature> features, String header)
			throws IOException {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
			writer.write(header);
			writer.newLine();

			for (ScoredFeature feature : features) {
				writer.write(feature.getOriginalLine());
				writer.newLine();
			}
		}
	}

	private static int findColumnIndex(String[] headerColumns, String columnName) {
		for (int i = 0; i < headerColumns.length; i++) {
			if (columnName.equals(headerColumns[i])) {
				return i;
			}
		}
		throw new IllegalArgumentException("Required column was not found in the feature file: " + columnName);
	}

	// Changed isFeatureOnMassList to only check for peptide sequence equivalence,
	// not for mass, charge and RT equivalence. I don't think is needed, but keeping
	// for now.

	@SuppressWarnings("unused")
	public static ArrayList<ScoredFeature> scoreFeaturesForContext(File library, File rawFile, File fasta,
			String baseName, String massListPath, int shuffledSeed)
					throws IOException, SQLException, DataFormatException, InterruptedException {

		File effectiveMassList = prepareMassList(massListPath, baseName, shuffledSeed);

		// Run an Encyclopedia job
		SearchParameters params = SearchParameterParser.getDefaultParametersObject();
		LibraryScoringFactory scoringForLibrary = new ContextEncyclopediaScoringFactory(params);
		LibraryInterface interfaceForLibrary = BlibToLibraryConverter.getFile(library, fasta, params);

		File outputPrefix = new File(baseName);

		EncyclopediaTwoJobData job = new EncyclopediaTwoJobData(rawFile, fasta, interfaceForLibrary,
				interfaceForLibrary, outputPrefix, scoringForLibrary);

		ProgressIndicator progress = new EmptyProgressIndicator(true);
		org.searlelab.msrawjava.io.StripeFileInterface interfaceForStripeFile = job.getDiaFileReader();

		// Run Encyclopedia job to get the feature file
		File featuresToSplit = job.getPercolatorFiles().getInputTSV();

		if (featuresToSplit.exists() && featuresToSplit.canRead()) {
			Logger.logLine("Feature file already exists, skipping feature calculation!");
			Logger.logLine(featuresToSplit.getAbsolutePath());
		} else {
			Logger.logLine("Calculating features...");
			EncyclopediaTwo.generateFeatureFile(progress, interfaceForLibrary, job, interfaceForStripeFile,
					java.util.Optional.empty());
		}

		ArrayList<ScoredFeature> uniqueFeatures = new ArrayList<>();
		ArrayList<ScoredFeature> uniqueFeaturesList = uniqueFeatures;
		HashMap<String, ScoredFeature> bestFeatureByPeptide = new HashMap<>();
		String header;

		// Read all rows the feature file
		try (BufferedReader br = new BufferedReader(new FileReader(featuresToSplit))) {
			header = br.readLine();
			if (header == null) {
				throw new IOException("Feature file is empty, so no file could be read.");
			}

			String[] headerColumns = header.split("\t", -1);

			int precursorMzIndex = findColumnIndex(headerColumns, "precursorMz");
			int isDecoyIndex = findColumnIndex(headerColumns, "Label");
			int retentionTimeIndex = findColumnIndex(headerColumns, "RTinMin");
			int primaryScoreIndex = findColumnIndex(headerColumns, "primary");
			int xCorrLibIndex = findColumnIndex(headerColumns, "xCorrLib");
			int sequenceIndex = findColumnIndex(headerColumns, "sequence");
			int proteinIndex = findColumnIndex(headerColumns, "Proteins");

			String line;
			while ((line = br.readLine()) != null) {
				String columns[] = line.split("\t", -1);

				double mz = Double.parseDouble(columns[precursorMzIndex]);
				byte featureCharge = PercolatorPeptide.getCharge(columns[0]);
				boolean isDecoy = Integer.parseInt(columns[isDecoyIndex]) == -1;
				float primary = Float.parseFloat(columns[primaryScoreIndex]);
				float retentionTime = Float.parseFloat(columns[retentionTimeIndex]);
				String sequence = columns[sequenceIndex];
				String protein = columns[proteinIndex];

				ScoredFeature feature = new ScoredFeature(mz, featureCharge, isDecoy, primary, retentionTime, sequence,
						protein, line);

				uniqueFeaturesList.add(feature);

				ScoredFeature currentBest = bestFeatureByPeptide.get(sequence);

				// Take the peptide with a higher primary score and place it on a new list
				if (currentBest == null || feature.getPrimary() > currentBest.getPrimary()) {
					bestFeatureByPeptide.put(sequence, feature);
				}
			}
		}
		
		ArrayList<ScoredFeature> bestFeatures = new ArrayList<>(bestFeatureByPeptide.values());
		bestFeatures.sort(Comparator.comparing(ScoredFeature::getPrimary).reversed());

		Logger.logLine("Selecting the betst feature per peptide..." + bestFeatures.size() + " peptides remain.");

		// Output Paths
		String referenceOutputPath = baseName + "_reference.features.txt";
		String backgroundOutputPath = baseName + "_background.features.txt";

		// Output Files
		File referenceOutput = new File(referenceOutputPath);
		File backgroundOutput = new File(backgroundOutputPath);

		// Target mass list
		ArrayList<IsolationWindow> targetWindows = IsolationWindowReader
				.parseMassList(effectiveMassList.getAbsolutePath());

		System.out.println(targetWindows.size() + " windows found in the mass list");

		ArrayList<ScoredFeature> referenceFeatures = new ArrayList<>();
		ArrayList<ScoredFeature> backgroundFeatures = new ArrayList<>();

		ArrayList<ScoredFeature> partitionedFeatures = new ArrayList<>(); // so that the return is all of the features

		for (ScoredFeature feature : bestFeatures) {
			IsolationWindow matchingWindow = findMatchingMassListWindow(feature, targetWindows, params);

			boolean isOnMassList = matchingWindow != null;
			boolean isBackground = !isOnMassList;

			ScoredFeature annotatedFeature = new ScoredFeature(feature.getMz(), feature.isDecoy(), feature.getPrimary(),
					feature.getRetentionTime(), cleanPeptideSequence(feature.getSequence()), feature.getProtein(),
					feature.getOriginalLine(), isBackground);
			partitionedFeatures.add(annotatedFeature);

			if (isOnMassList) {
				referenceFeatures.add(feature);
			} else {
				backgroundFeatures.add(feature);
			}
		}
		writeScoredFeatures(referenceOutput, referenceFeatures, header);
		writeScoredFeatures(backgroundOutput, backgroundFeatures, header);

		Logger.logLine("Reference target features: " + referenceFeatures.size());
		return partitionedFeatures;
	}


	// Scores features from a global experiment
	public static ArrayList<ScoredFeature> scoreFeatures(int shuffledSeed, File library, File rawFile, File fasta, String baseName,
			String massListPath) throws IOException, SQLException, DataFormatException, InterruptedException {

		// Run an Encyclopedia job
		SearchParameters params = SearchParameterParser.getDefaultParametersObject();
		LibraryScoringFactory scoringForLibrary = new ContextEncyclopediaScoringFactory(params);
		LibraryInterface interfaceForLibrary = BlibToLibraryConverter.getFile(library, fasta, params);

		File outputPrefix = new File(baseName);

		EncyclopediaTwoJobData job = new EncyclopediaTwoJobData(rawFile, fasta, interfaceForLibrary,
				interfaceForLibrary, outputPrefix, scoringForLibrary);

		ProgressIndicator progress = new EmptyProgressIndicator(true);
		org.searlelab.msrawjava.io.StripeFileInterface interfaceForStripeFile = job.getDiaFileReader();

		// Run Encyclopedia job to get the feature file
		File featuresToSplit = job.getPercolatorFiles().getInputTSV();

		if (featuresToSplit.exists() && featuresToSplit.canRead()) {
			Logger.logLine("Feature file already exists, skipping feature calculation!");
			Logger.logLine(featuresToSplit.getAbsolutePath());
		} else {
			Logger.logLine("Calculating features...");
			EncyclopediaTwo.generateFeatureFile(progress, interfaceForLibrary, job, interfaceForStripeFile,
					java.util.Optional.empty());
		}

		String header;
		ArrayList<ScoredFeature> allFeatures = new ArrayList<>();

		// Read all rows the feature file
		try (BufferedReader br = new BufferedReader(new FileReader(featuresToSplit))) {
			header = br.readLine();
			if (header == null) {
				throw new IOException("Feature file is empty, so no file could be read.");
			}

			String[] headerColumns = header.split("\t", -1);

			int precursorMzIndex = findColumnIndex(headerColumns, "precursorMz");
			int isDecoyIndex = findColumnIndex(headerColumns, "Label");
			int retentionTimeIndex = findColumnIndex(headerColumns, "RTinMin");
			int primaryScoreIndex = findColumnIndex(headerColumns, "primary");
			@SuppressWarnings("unused")
			int xCorrLibIndex = findColumnIndex(headerColumns, "xCorrLib");
			int sequenceIndex = findColumnIndex(headerColumns, "sequence");
			int proteinIndex = findColumnIndex(headerColumns, "Proteins");

			String line;
			while ((line = br.readLine()) != null) {
				String columns[] = line.split("\t", -1);

				double mz = Double.parseDouble(columns[precursorMzIndex]);
				byte featureCharge = PercolatorPeptide.getCharge(columns[0]);
				boolean isDecoy = Integer.parseInt(columns[isDecoyIndex]) == -1;
				float primary = Float.parseFloat(columns[primaryScoreIndex]);
				float retentionTime = Float.parseFloat(columns[retentionTimeIndex]);
				String sequence = columns[sequenceIndex];
				String protein = columns[proteinIndex];

				ScoredFeature feature = new ScoredFeature(mz, featureCharge, isDecoy, primary, retentionTime, sequence,
						protein, line);
				allFeatures.add(feature);

			}
		}

		// Output Paths
		String featureOutputPath = baseName + ".features.txt";

		// Output Files
		File featureOutput = new File(featureOutputPath);

		ArrayList<ScoredFeature> featuresList = new ArrayList<>();

		ArrayList<ScoredFeature> partitionedFeatures = new ArrayList<>(); // so that the return is all of the features

		for (ScoredFeature feature : allFeatures) {

			ScoredFeature annotatedFeature = new ScoredFeature(feature.getMz(), feature.isDecoy(), feature.getPrimary(),
					feature.getRetentionTime(), cleanPeptideSequence(feature.getSequence()), feature.getProtein(),
					feature.getOriginalLine());
			partitionedFeatures.add(annotatedFeature);

			if (annotatedFeature != null) {
				featuresList.add(feature);
			}
		}

		writeScoredFeatures(featureOutput, featuresList, header);

		Logger.logLine("Target features: " + featuresList.size());
		return partitionedFeatures;
	}
}
