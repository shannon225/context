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
import java.util.HashSet;
import java.util.Map;
import java.util.zip.DataFormatException;

import edu.washington.gs.maccoss.encyclopedia.algorithms.library.EncyclopediaTwoJobData;
import edu.washington.gs.maccoss.encyclopedia.algorithms.library.EncyclopediaTwoDecoyStrategy;
import edu.washington.gs.maccoss.encyclopedia.algorithms.library.LibraryScoringFactory;
import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchParameters;
import edu.washington.gs.maccoss.encyclopedia.filereaders.BlibToLibraryConverter;
import edu.washington.gs.maccoss.encyclopedia.filereaders.LibraryInterface;
import edu.washington.gs.maccoss.encyclopedia.filereaders.LibraryFile;
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
import org.searlelab.context.encyclopedia.ContextWorkingLibraryPreparer;
import org.searlelab.context.encyclopedia.CandidateDiagnosticWriter;
import org.searlelab.context.encyclopedia.SearchPairSidecar;

public class ContextFeatureScorer {
	private record FeaturePrecursor(String sequence, byte charge) {}

	public record Options(int predictionNce, byte predictionDefaultCharge, boolean adjustNceForDia,
			boolean candidateDiagnostics, boolean allowLegacyFeatures) {
		public Options {
			if (predictionNce <= 0 || predictionDefaultCharge <= 0) {
				throw new IllegalArgumentException("Prediction NCE and default charge must be positive");
			}
		}

		public static Options defaults() { return new Options(27, (byte)2, true, false, false); }
	}

	public static void execute(String[] args) throws IOException, SQLException, InterruptedException, DataFormatException {

		if (args.length != 4) {
			System.err.println("Usage: ");
			System.err.println("java -jar context.jar features "
					+ "<rawFilePath> <libraryFilePath> <fastaPath> <massListPath>");
			System.err.println("rawFilePath accepts " + RawFiles.supportedExtensions());
			System.exit(1);
		}

		String rawFilePath = args[0];
		String libraryFilePath = args[1];
		String fastaPath = args[2];
		String massListPath = args[3];

		String baseName = RawFiles.stripExtension(rawFilePath);
		final File fasta = new File(fastaPath);
		File rawFile = new File(rawFilePath);
		File library = new File(libraryFilePath);
		ArrayList<ScoredFeature> partitionedFeatures = scoreFeaturesForContext(library, rawFile, fasta, baseName,
				massListPath, 0);
		Logger.logLine("Scored and partitioned " + partitionedFeatures.size() + " features.");
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

	// Changed isFeatureOnMassList to only check for peptide sequence equivalence

	@SuppressWarnings("unused")
	public static ArrayList<ScoredFeature> scoreFeaturesForContext(File library, File rawFile, File fasta,
			String baseName, String massListPath, int shuffledSeed)
					throws IOException, SQLException, DataFormatException, InterruptedException {
		return scoreFeaturesForContext(library, rawFile, fasta, baseName, massListPath, shuffledSeed, Options.defaults());
	}

	public static ArrayList<ScoredFeature> scoreFeaturesForContext(File library, File rawFile, File fasta,
			String baseName, String massListPath, int shuffledSeed, Options options)
					throws IOException, SQLException, DataFormatException, InterruptedException {

		File effectiveMassList = prepareMassList(massListPath, baseName, shuffledSeed);

		// Set up the variables to run an Encyclopedia job
		SearchParameters params = SearchParameterParser.getDefaultParametersObject();
		LibraryScoringFactory scoringForLibrary = new ContextEncyclopediaScoringFactory(params);
		LibraryInterface interfaceForLibrary = BlibToLibraryConverter.getFile(library, fasta, params);
		try {

		File outputPrefix = new File(baseName);

		EncyclopediaTwoJobData job = new EncyclopediaTwoJobData(rawFile, fasta, interfaceForLibrary,
				interfaceForLibrary, outputPrefix, scoringForLibrary);

		// Run Encyclopedia job to get the feature file
		File featuresToSplit = job.getPercolatorFiles().getInputTSV();
		try (ContextFeatureCacheIdentity.OutputLock outputLock = ContextFeatureCacheIdentity.acquireOutputLock(featuresToSplit)) {
			File pairSidecar = new File(featuresToSplit.getAbsolutePath() + ".pairs.tsv");
			File identityManifest = new File(featuresToSplit.getAbsolutePath() + ".context.sha256");
			File inProgress = new File(featuresToSplit.getAbsolutePath() + ".context.inprogress");
			String identity = ContextFeatureCacheIdentity.inputs(library, rawFile, fasta, effectiveMassList, params, options);
			File diagnosticFile = options.candidateDiagnostics()
					? new File(baseName + "_context_" + identity.substring(0, 12) + "_candidates.tsv") : null;
			File incompleteArchive = ContextFeatureCacheIdentity.archiveIncomplete(inProgress, featuresToSplit,
					pairSidecar, identityManifest, baseName);
			if (incompleteArchive != null) Logger.logLine("Preserved incomplete Context output in " + incompleteArchive);

			if (featuresToSplit.exists() && featuresToSplit.canRead()) {
				if (options.allowLegacyFeatures() && !pairSidecar.exists() && !identityManifest.exists()) {
					Logger.logLine("Using an explicitly accepted legacy feature file without Context pairing: " + featuresToSplit);
				} else {
					ContextFeatureCacheIdentity.requireMatch(identityManifest, identity, featuresToSplit, pairSidecar, diagnosticFile);
					java.nio.file.Files.deleteIfExists(inProgress.toPath());
					Logger.logLine("Reusing verified Context feature and pair sidecar: " + featuresToSplit);
				}
			} else {
				if (!(interfaceForLibrary instanceof LibraryFile)) {
					throw new IOException("Context requires an Encyclopedia LibraryFile source for exact target metadata");
				}
				File workingLibrary = new File(baseName + "_context_" + identity.substring(0, 12) + ".elib");
				File predictions = new File(baseName + "_context_" + identity.substring(0, 12) + "_predictions.tsv");
				if (pairSidecar.exists() || identityManifest.exists() || workingLibrary.exists()
						|| predictions.exists() || (diagnosticFile != null && diagnosticFile.exists())) {
					throw new IOException("Context output already exists without a reusable feature cache: " + featuresToSplit);
				}
				ContextFeatureCacheIdentity.markInProgress(inProgress, identity, options.candidateDiagnostics());
				Logger.logLine("Calculating features...");
				ContextWorkingLibraryPreparer.Settings predictionSettings = new ContextWorkingLibraryPreparer.Settings(
						options.predictionNce(), options.predictionDefaultCharge(), options.adjustNceForDia());
				try (ContextWorkingLibraryPreparer.Result prepared = ContextWorkingLibraryPreparer.prepare(
						(LibraryFile) interfaceForLibrary, params, predictionSettings, workingLibrary, predictions);
					CandidateDiagnosticWriter diagnostics = diagnosticFile == null ? null : new CandidateDiagnosticWriter(
							diagnosticFile, EncyclopediaTwoDecoyStrategy.resolve(prepared.library(), params).getMode())) {
					LibraryScoringFactory preparedFactory = new ContextEncyclopediaScoringFactory(params, diagnostics);
					EncyclopediaTwoJobData preparedJob = new EncyclopediaTwoJobData(rawFile, fasta, prepared.library(),
							prepared.library(), outputPrefix, preparedFactory);
					ProgressIndicator progress = new EmptyProgressIndicator(true);
					org.searlelab.msrawjava.io.StripeFileInterface interfaceForStripeFile = preparedJob.getDiaFileReader();
					SearchPairSidecar pairs = new SearchPairSidecar();
					try {
						EncyclopediaTwo.generateFeatureFile(progress, prepared.library(), preparedJob, interfaceForStripeFile,
								java.util.Optional.empty(), java.util.OptionalInt.empty(), pairs::record);
					} finally {
						interfaceForStripeFile.close();
					}
					if (diagnostics != null) diagnostics.requireSuccess();
					pairs.write(featuresToSplit, pairSidecar, library, rawFile);
					ContextFeatureCacheIdentity.save(identityManifest, identity, featuresToSplit, pairSidecar, diagnosticFile);
				}
				java.nio.file.Files.deleteIfExists(inProgress.toPath());
			}
			return partitionFeatures(featuresToSplit, params, baseName, effectiveMassList);
		}
		} finally {
			if (interfaceForLibrary instanceof LibraryFile) ((LibraryFile) interfaceForLibrary).close();
		}
	}

	// Partition on an already generated feature file
	public static ArrayList<ScoredFeature> partitionFeatures(File featuresToSplit, SearchParameters params, String baseName, File effectiveMassList) throws IOException { 
		FeatureTable table = readFeatures(featuresToSplit);
		String header = table.header(); 
		File sourceSidecar = new File(featuresToSplit.getAbsolutePath() + ".pairs.tsv");
		File referenceSidecar = new File(baseName + "_reference.features.txt.pairs.tsv");
		File backgroundSidecar = new File(baseName + "_background.features.txt.pairs.tsv");
		if (!sourceSidecar.isFile() && (referenceSidecar.exists() || backgroundSidecar.exists())) {
			throw new IOException("Legacy feature source has stale Context partition pair sidecars");
		}
		if (sourceSidecar.isFile()) SearchPairSidecar.requireMatches(featuresToSplit, sourceSidecar);
		Map<String, String> targetKeys = sourceSidecar.isFile()
			? SearchPairSidecar.targetKeysByFeatureId(sourceSidecar) : null;
		if (targetKeys != null) {
			HashSet<String> seenIds = new HashSet<>();
			for (ScoredFeature feature : table.rows()) {
				String id = featureId(feature);
				if (!seenIds.add(id)) throw new IOException("Duplicate feature id: " + id);
				if (!targetKeys.containsKey(id)) throw new IOException("Missing pair metadata for feature id: " + id);
			}
			if (!seenIds.equals(targetKeys.keySet())) throw new IOException("Pair sidecar has feature ids absent from source TSV");
		}
		HashMap<FeaturePrecursor, ScoredFeature> bestFeatureByPrecursor = new HashMap<>();
		for (ScoredFeature feature : table.rows()) {
			FeaturePrecursor key = new FeaturePrecursor(feature.getSequence(), feature.getCharge());
			ScoredFeature previous = bestFeatureByPrecursor.get(key);
			if (previous == null || feature.getPrimary() > previous.getPrimary() || (feature.getPrimary() == previous.getPrimary() && feature.getOriginalLine().compareTo(previous.getOriginalLine()) < 0)) {
				bestFeatureByPrecursor.put(key, feature);
			}
		}

		ArrayList<ScoredFeature> bestFeatures = new ArrayList<>(bestFeatureByPrecursor.values());
		bestFeatures.sort(Comparator.comparing(ScoredFeature::getPrimary).reversed().thenComparing(ScoredFeature::getSequence));

		Logger.logLine(" ");

		//		bestFeatures.sort(Comparator.comparing(ScoredFeature::getPrimary).reversed());

		Logger.logLine("Selecting the best feature per precursor..." + bestFeatures.size() + " precursors remain.");

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
			IsolationWindow matchingWindow = targetKeys == null
					? findMatchingMassListWindow(feature, targetWindows, params)
					: findMatchingPairedTargetWindow(targetKeys.get(featureId(feature)), targetWindows, params);

			boolean isOnMassList = matchingWindow != null;
			boolean isBackground = !isOnMassList;

			ScoredFeature annotatedFeature = new ScoredFeature(feature.getMz(), feature.getCharge(), feature.isDecoy(), feature.getPrimary(), feature.getRetentionTime(), cleanPeptideSequence(feature.getSequence()), feature.getProtein(), feature.getOriginalLine(), isBackground);
			partitionedFeatures.add(annotatedFeature);

			if (isOnMassList) {
				referenceFeatures.add(feature);
			} else {
				backgroundFeatures.add(feature);
			}
		}
		writeScoredFeatures(referenceOutput, referenceFeatures, header);
		writeScoredFeatures(backgroundOutput, backgroundFeatures, header);
		if (sourceSidecar.isFile()) {
			SearchPairSidecar.partition(sourceSidecar, referenceSidecar, featureIds(referenceFeatures));
			SearchPairSidecar.partition(sourceSidecar, backgroundSidecar, featureIds(backgroundFeatures));
		}

		Logger.logLine("Reference target features: " + referenceFeatures.size());
		return partitionedFeatures;
	}

	private static HashSet<String> featureIds(ArrayList<ScoredFeature> features) throws IOException {
		HashSet<String> ids = new HashSet<>();
		for (ScoredFeature feature : features) {
			String id = featureId(feature);
			if (!ids.add(id)) throw new IOException("Duplicate partition feature id: " + id);
		}
		return ids;
	}

	private static String featureId(ScoredFeature feature) throws IOException {
		String line = feature.getOriginalLine();
		int tab = line.indexOf('\t');
		if (tab < 0) throw new IOException("Feature row has no id column");
		return line.substring(0, tab);
	}

	private static IsolationWindow findMatchingPairedTargetWindow(String targetKey, ArrayList<IsolationWindow> windows,
			SearchParameters parameters) throws IOException {
		if (targetKey == null) throw new IOException("Missing search-pair target key");
		int separator = targetKey.lastIndexOf('|');
		if (separator < 1) throw new IOException("Malformed search-pair target key: " + targetKey);
		String sequence = targetKey.substring(0, separator);
		byte charge;
		try {
			charge = Byte.parseByte(targetKey.substring(separator + 1));
		} catch (NumberFormatException e) {
			throw new IOException("Malformed search-pair target charge: " + targetKey, e);
		}
		for (IsolationWindow window : windows) {
			if (window.getCharge() == charge && sequence.equals(PeptideUtils.getCorrectedMasses(
					cleanPeptideSequence(window.getCompound()), parameters.getAAConstants()))) return window;
		}
		return null;
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

		FeatureTable table = readFeatures(featuresToSplit);
		String header = table.header();
		ArrayList<ScoredFeature> allFeatures = table.rows();

		// Output Paths
		String featureOutputPath = baseName + ".features.txt";
		File featureOutput = new File(featureOutputPath);
		ArrayList<ScoredFeature> featuresList = new ArrayList<>();
		ArrayList<ScoredFeature> partitionedFeatures = new ArrayList<>(); // so that the return is all of the features

		// Loop through features 
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

	public record FeatureTable(String header, ArrayList<ScoredFeature> rows) {}

	public static FeatureTable readFeatures(File file) throws IOException {
		try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
			String header = reader.readLine();
			if(header == null) throw new IOException("Empty feature table: " + file);
			String[] names = header.split("\t", -1);
			int mz, label, rt, primary, sequence, proteins; 
			try {
				mz = findColumnIndex(names, "precursorMz");
				label = findColumnIndex(names, "Label");
				rt = findColumnIndex(names, "RTinMin");
				primary = findColumnIndex(names, "primary");
				sequence = findColumnIndex(names, "sequence");
				proteins = findColumnIndex(names, "Proteins");
			} catch (IllegalArgumentException e) {
				throw new IOException(file + ": " + e.getMessage(), e);
			}

			ArrayList<ScoredFeature> rows = new ArrayList<>();
			String line;
			int row = 1;
			while ((line = reader.readLine()) !=null) {
				row++;
				try {
					String[] columns = line.split("\t", -1);
					if (columns.length < names.length) throw new IllegalArgumentException("Truncated row");
					int targetLabel = Integer.parseInt(columns[label]);
					if (targetLabel != 1 && targetLabel != -1) throw new IllegalArgumentException("Label msut be 1 or -1");

					double precursorMz = Double.parseDouble(columns[mz]);
					float retentionTime = Float.parseFloat(columns[rt]);
					float score = Float.parseFloat(columns[primary]);
					if( !Double.isFinite(precursorMz) || !Float.isFinite(retentionTime) || !Float.isFinite(score))
						throw new IllegalArgumentException("Non-finite required numeric value");
					byte charge = PercolatorPeptide.getCharge(columns[0]);
					
					if (charge <=0) throw new IllegalArgumentException("Invalid precursor charge");
					rows.add(new ScoredFeature(precursorMz, charge, targetLabel == -1, score, retentionTime, columns[sequence], columns[proteins], line));
					
				} catch (RuntimeException e) {
						throw new IOException(file + ": row" + row + ": " + e.getMessage(), e);
				}
			}
			return new FeatureTable(header, rows);
		}
	}
}
