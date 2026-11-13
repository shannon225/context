package org.searlelab.context.encyclopedia;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.DataFormatException;

import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.Range;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.searlelab.context.encyclopedia.SearchToBLIB.OutputFormat;

import edu.washington.gs.maccoss.encyclopedia.CLIConverter;
import edu.washington.gs.maccoss.encyclopedia.DIABrowser;
import edu.washington.gs.maccoss.encyclopedia.ProgramType;
import edu.washington.gs.maccoss.encyclopedia.Scribe;
import edu.washington.gs.maccoss.encyclopedia.SearchGUIMain;
import edu.washington.gs.maccoss.encyclopedia.Thesaurus;
import edu.washington.gs.maccoss.encyclopedia.Walnut;
import edu.washington.gs.maccoss.encyclopedia.algorithms.AbstractScoringResult;
import edu.washington.gs.maccoss.encyclopedia.algorithms.PSMInterface;
import edu.washington.gs.maccoss.encyclopedia.algorithms.PSMScorer;
import edu.washington.gs.maccoss.encyclopedia.algorithms.alignment.TargeteDecoyPSMFilter;
import edu.washington.gs.maccoss.encyclopedia.algorithms.library.EncyclopediaScoringFactory;
import edu.washington.gs.maccoss.encyclopedia.algorithms.library.EncyclopediaTwoJobData;
import edu.washington.gs.maccoss.encyclopedia.algorithms.library.EncyclopediaTwoDecoyStrategy;
import edu.washington.gs.maccoss.encyclopedia.algorithms.library.EncyclopediaTwoLDAScorer;
import edu.washington.gs.maccoss.encyclopedia.algorithms.library.EncyclopediaTwoScorer;
import edu.washington.gs.maccoss.encyclopedia.algorithms.library.EncyclopediaTwoScoringFactory;
import edu.washington.gs.maccoss.encyclopedia.algorithms.library.LibraryScoringFactory;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.MProphetExecutionData;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.MProphetReiter;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.MProphetResult;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.PercolatorExecutor;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.PercolatorPeptide;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryEntry;
import edu.washington.gs.maccoss.encyclopedia.datastructures.PrecursorScanMap;
import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchJobData;
import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchParameters;
import edu.washington.gs.maccoss.encyclopedia.datastructures.parameters.InstrumentSpecificSearchParameters;
import edu.washington.gs.maccoss.encyclopedia.filereaders.BlibToLibraryConverter;
import edu.washington.gs.maccoss.encyclopedia.filereaders.LibraryInterface;
import edu.washington.gs.maccoss.encyclopedia.filereaders.MatchedDecoyAssessment;
import edu.washington.gs.maccoss.encyclopedia.filereaders.MatchedDecoyKey;
import edu.washington.gs.maccoss.encyclopedia.filereaders.MatchedDecoyLibrary;
import edu.washington.gs.maccoss.encyclopedia.filereaders.OverlappingDiaPreprocessor;
import edu.washington.gs.maccoss.encyclopedia.filereaders.PercolatorReader;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;
import edu.washington.gs.maccoss.encyclopedia.filewriters.LibraryUtilities;
import edu.washington.gs.maccoss.encyclopedia.filewriters.PSMConsumer;
import edu.washington.gs.maccoss.encyclopedia.filewriters.PeptideScoringResultsConsumer;
import edu.washington.gs.maccoss.encyclopedia.filewriters.TeeResultsConsumer;
import edu.washington.gs.maccoss.encyclopedia.utils.CommandLineParser;
import edu.washington.gs.maccoss.encyclopedia.utils.EncyclopediaException;
import edu.washington.gs.maccoss.encyclopedia.utils.FileLogRecorder;
import edu.washington.gs.maccoss.encyclopedia.utils.Logger;
import edu.washington.gs.maccoss.encyclopedia.utils.Pair;
import edu.washington.gs.maccoss.encyclopedia.utils.VersioningDetector;
import edu.washington.gs.maccoss.encyclopedia.utils.math.General;
import edu.washington.gs.maccoss.encyclopedia.utils.math.RandomGenerator;
import edu.washington.gs.maccoss.encyclopedia.utils.threading.EmptyProgressIndicator;
import edu.washington.gs.maccoss.encyclopedia.utils.threading.ProgressIndicator;
import edu.washington.gs.maccoss.encyclopedia.utils.threading.SubProgressIndicator;

public class EncyclopediaTwo {
	public static final String TARGET_LIBRARY_TAG = "-l";
	public static final String PREALIGNMENT_LIBRARY_TAG = "-p";
	public static final String OUTPUT_RESULT_TAG = "-o";
	public static final String INPUT_DIA_TAG = "-i";
	public static final String BACKGROUND_FASTA_TAG = "-f";
	public static final String QUIET_MODE_ARG = "-quiet";
	public static final boolean useSqrt = false;

	public static void main(String[] args) {
		HashMap<String, String> arguments = CommandLineParser.parseArguments(args);
		arguments = InstrumentSpecificSearchParameters.checkParameters(arguments);

		if (arguments.size() == 0) {
			SearchGUIMain.runGUI(ProgramType.EncyclopeDIA);

		} else if (arguments.size() == 1 && arguments.containsKey(SearchParameters.ENABLE_ADVANCED_OPTIONS)) {
			SearchGUIMain.runGUI(ProgramType.Global, true, false);

		} else if (arguments.containsKey("-browser")) {
			DIABrowser.main(args);

		} else if (arguments.containsKey("-libexport")) {
			SearchToBLIB.main(args);

		} else if (arguments.containsKey("-thesaurus")) {
			Thesaurus.main(args);

		} else if (arguments.containsKey("-scribe")) {
			Scribe.main(args);

		} else if (arguments.containsKey("-walnut") || arguments.containsKey("-pecan")) {
			Walnut.main(args);

		} else if (arguments.containsKey("-convert")) {
			CLIConverter.main(args);

		} else if (arguments.containsKey("-h") || arguments.containsKey("-help") || arguments.containsKey("--help")) {
			Logger.logLine("EncyclopeDIA Help");
			Logger.timelessLogLine("EncyclopeDIA is a library search engine for DIA data.");
			Logger.timelessLogLine(
					"You should prefix your arguments with a high memory setting, e.g. \"-Xmx8g\" for 8gb");
			Logger.timelessLogLine("Required Parameters: ");
			Logger.timelessLogLine("\t-i\tinput .DIA or .MZML file");
			Logger.timelessLogLine("\t-f\tprotein .FASTA database");
			Logger.timelessLogLine("\t-p\tpre-alignment library .DLIB or .ELIB file");
			Logger.timelessLogLine("\t-l\tlibrary .DLIB or .ELIB file");
			Logger.timelessLogLine("Other Programs: ");
			Logger.timelessLogLine("\t-walnut\trun Walnut FASTA search (use -walnut -h for help)");
			Logger.timelessLogLine("\t-thesaurus\trun Thesaurus localization search (use -thesaurus -h for help)");
			Logger.timelessLogLine("\t-scribe\trun Scribe (use -scribe -h for Scribe help)");
			Logger.timelessLogLine("\t-browser\trun ELIB Browser (use -browser -h for ELIB Browser help)");
			Logger.timelessLogLine("\t-libexport\trun Library Export (use -libexport -h for Library Export help)");
			Logger.timelessLogLine("\t-convert\trun files converter (use -convert -h for help)");
			Logger.timelessLogLine("Other Parameters: ");
			Logger.timelessLogLine("\t-o\toutput report file (default: [input file]"
					+ EncyclopediaTwoJobData.OUTPUT_FILE_SUFFIX + ")");

			TreeMap<String, String> defaults = new TreeMap<String, String>(
					SearchParameterParser.getDefaultParameters());
			int maxWidth = 0;
			for (String key : defaults.keySet()) {
				if (key.length() > maxWidth)
					maxWidth = key.length();
			}
			for (Entry<String, String> entry : defaults.entrySet()) {
				Logger.timelessLogLine("\t" + General.formatCellToWidth(entry.getKey(), maxWidth) + " (default: "
						+ entry.getValue() + ")");
			}

			Logger.timelessLogLine("\t" + QUIET_MODE_ARG + "\tsuppress log output to stdout/stderr");

			System.exit(1);

		} else if (arguments.containsKey("-v") || arguments.containsKey("-version")
				|| arguments.containsKey("--version")) {
			Logger.logLine("EncyclopeDIA version " + ProgramType.getGlobalVersion());
			System.exit(1);

		} else {
			VersioningDetector.checkVersionCLI(ProgramType.EncyclopeDIA);

			if (!arguments.containsKey(INPUT_DIA_TAG) || !arguments.containsKey(TARGET_LIBRARY_TAG)
					|| !arguments.containsKey(BACKGROUND_FASTA_TAG)) {
				Logger.errorLine("You are required to specify an input file (" + INPUT_DIA_TAG + "), a library file ("
						+ TARGET_LIBRARY_TAG + "), and a fasta file (" + BACKGROUND_FASTA_TAG + ")");
				System.exit(1);
			}

			File diaFile = new File(arguments.get(INPUT_DIA_TAG));
			File libraryFile = new File(arguments.get(TARGET_LIBRARY_TAG));
			File prealignmentLibraryFile = new File(arguments.get(EncyclopediaTwo.PREALIGNMENT_LIBRARY_TAG));
			File fastaFile = new File(arguments.get(EncyclopediaTwo.BACKGROUND_FASTA_TAG));

			File outputFile;
			if (arguments.containsKey(OUTPUT_RESULT_TAG)) {
				outputFile = new File(arguments.get(OUTPUT_RESULT_TAG));
			} else {
				outputFile = new File(diaFile.getAbsolutePath() + EncyclopediaTwoJobData.OUTPUT_FILE_SUFFIX);
			}

			try {
				if (arguments.containsKey(QUIET_MODE_ARG)) {
					Logger.PRINT_TO_SCREEN = false;
				}
				FileLogRecorder logRecorder = new FileLogRecorder(
						new File(outputFile.getAbsolutePath() + EncyclopediaTwoJobData.LOG_FILE_SUFFIX));
				Logger.addRecorder(logRecorder);

				Logger.logLine("EncyclopeDIA version " + ProgramType.getGlobalVersion());

				SearchParameters parameters = SearchParameterParser.parseParameters(arguments);
				LibraryScoringFactory factory = EncyclopediaScoringFactory.getScoringFactory(arguments, parameters);

				LibraryInterface prealignmentLibrary = BlibToLibraryConverter.getFile(prealignmentLibraryFile,
						fastaFile, parameters);
				LibraryInterface library = BlibToLibraryConverter.getFile(libraryFile, fastaFile, parameters);
				EncyclopediaTwoJobData job = new EncyclopediaTwoJobData(diaFile, fastaFile, prealignmentLibrary,
						library, outputFile, factory);
				runSearch(new EmptyProgressIndicator(), job);
			} catch (Exception e) {
				Logger.errorLine("Encountered Fatal Error!");
				Logger.errorException(e);

				// Forcibly exit with error to avoid hanging the process if there are any
				// leftover user threads
				// that weren't properly cleaned up as a result of the error. First we log any
				// hung / remaining
				// threads though, to aid debugging.

				for (Entry<Thread, StackTraceElement[]> threadEntry : Thread.getAllStackTraces().entrySet()) {
					if (threadEntry.getKey().isDaemon()) {
						continue;
					}
					if (Thread.currentThread().equals(threadEntry.getKey())) {
						continue;
					}

					Logger.errorLine("\nLeftover user thread will be KILLED: " + threadEntry.getKey().getName());
					for (StackTraceElement element : threadEntry.getValue()) {
						Logger.errorLine("  " + element);
					}
				}

				System.exit(1); // nonzero status indicates error
			} finally {
				Logger.close();
			}

			// Do not forcibly exit; this can cause a hang if any user threads aren't
			// cleaned up.
		}
	}

	public static void runSearch(ProgressIndicator progress, EncyclopediaTwoJobData job)
			throws IOException, SQLException, DataFormatException, ExecutionException, InterruptedException {
		if (job.getPercolatorFiles().hasDataAvailable()) {
			try {
				ArrayList<PercolatorPeptide> passingPeptidesFromTSV = PercolatorReader.getPassingPeptidesFromTSV(
						job.getPercolatorFiles().getPeptideOutputFile(), job.getParameters(), false).x;

				File elibFile = job.getResultLibrary();
				if (!elibFile.exists()) {
					progress.update("Writing elib result library...");
					Logger.logLine("Writing elib result library...");
					ArrayList<SearchJobData> jobs = new ArrayList<SearchJobData>();
					jobs.add(job);
					SearchToBLIB.convert(progress, jobs, elibFile, OutputFormat.ELIB, false, job.getParameters());
				}
				Logger.logLine("Previously found " + passingPeptidesFromTSV.size() + " peptides identified at "
						+ (job.getParameters().getPercolatorThreshold() * 100.0f) + "% FDR");
				progress.update("Previously found " + passingPeptidesFromTSV.size() + " peptides identified at "
						+ (job.getParameters().getPercolatorThreshold() * 100.0f) + "% FDR", 1.0f);
				// progress.update("Previously found "+passingPeptidesFromTSV.size()+" peptides
				// ("+ParsimonyProteinGrouper.groupProteins(passingPeptidesFromTSV).size()+"
				// proteins) identified at
				// "+(job.getParameters().getPercolatorThreshold()*100.0f)+"% FDR", 1.0f);

				return;
			} catch (Exception e) {
				// problem! so just continue on and overwrite old result
				Logger.logLine("Found unexpected exception trying to read old results: ");
				Logger.logException(e);
				Logger.logLine("Just going to go ahead and reprocess this file!");
			}
		}

		job = OverlappingDiaPreprocessor.preprocess(progress, job);
		Logger.logLine("Using " + job.getTaskFactory().getName());
		Logger.logLine("Input File: " + job.getOriginalDiaFileName());
		Logger.logLine("Prealignment Library File: " + job.getPrealignmentLibrary().getName());
		Logger.logLine("Library File: " + job.getLibrary().getName());
		Logger.logLine("Result File: " + job.getResultLibrary().getName());
		Logger.logLine("Parameters:");
		Logger.logLine(job.getParameters().toString());

		Logger.logLine("Converting files...");
		progress.update("Converting files...", Float.MIN_VALUE);

		final StripeFileInterface stripefile = job.getDiaFileReader();
		runIterativeSearch(progress, job, stripefile);
		stripefile.close();
	}

	static void runIterativeSearch(ProgressIndicator progress, EncyclopediaTwoJobData job,
			StripeFileInterface stripefile)
					throws IOException, SQLException, DataFormatException, ExecutionException, InterruptedException {
		long startTime = System.currentTimeMillis();
		LibraryScoringFactory taskFactory = job.getTaskFactory();
		SearchParameters parameters = taskFactory.getParameters();

		Pair<MProphetResult, TargeteDecoyPSMFilter> mprophetResults = null;
		float percentage = 0.0f;

		if (!parameters.isSkipLibraryRetentionTime()) {
			float fraction = 0.0f;
			LibraryInterface prealignmentLibrary;
			if (job.getLibrary() == job.getPrealignmentLibrary()) {
				Logger.logLine("Pre-alignment library and search library are the same so no alignment necessary.");
				prealignmentLibrary = job.getPrealignmentLibrary();
				fraction = 0.5f;
			} else {
				Logger.logLine("Aligning pre-alignment library to " + job.getLibrary().getName());
				prealignmentLibrary = LibraryUtilities.getReferenceCorrectedLibrary(job.getPrealignmentLibrary(),
						job.getLibrary());
				int preAlignSize = prealignmentLibrary.size();
				fraction = preAlignSize / (float) (preAlignSize + job.getLibrary().size());
			}

			percentage = Math.min(0.5f, fraction * 2f) - 0.05f;
			ProgressIndicator subProgress1 = new SubProgressIndicator(progress, percentage);

			Logger.logLine("Calculating pre-alignment features for " + job.getLibrary().getName());
			PSMConsumer saveResultsConsumer1 = generateFeatureFile(subProgress1, prealignmentLibrary, job, stripefile,
					Optional.empty());

			Logger.logLine("Assessing FDR...");
			mprophetResults = firstPassFDR(subProgress1, job, stripefile, saveResultsConsumer1);

			if (mprophetResults.getX().getPassingPeptides().size() == 0) {
				Logger.errorLine(
						"Found zero peptides after pre-alignment, consider searching a different pre-alignment library!");
				Logger.errorLine("Exiting early from failed analysis.");
				return;
			}
		}

		SubProgressIndicator subProgress2 = new SubProgressIndicator(progress, 1.0f - percentage - 0.05f);
		Logger.logLine("Calculating post-alignment features for " + job.getLibrary().getName());
		PSMConsumer saveResultsConsumer2 = generateFeatureFile(subProgress2, job.getLibrary(), job, stripefile,
				Optional.ofNullable(mprophetResults));

		SubProgressIndicator subProgress3 = new SubProgressIndicator(progress, 0.05f);
		Logger.logLine("Assessing FDR...");
		// percolatorResults=percolatePeptides(subProgress, job, stripefile,
		// saveResultsConsumer);
		Pair<ArrayList<PercolatorPeptide>, TargeteDecoyPSMFilter> percolatorResults = repercolatePeptides(subProgress3,
				job, stripefile, saveResultsConsumer2);

		ArrayList<PercolatorPeptide> passingPeptides = percolatorResults.getX();

		Logger.logLine("Writing elib result library...");
		File elibFile = job.getResultLibrary();
		ArrayList<SearchJobData> jobs = new ArrayList<SearchJobData>();
		jobs.add(job);

		SearchToBLIB.convertElib(subProgress3, job, elibFile, parameters, parameters.isIntegratePrecursors());

		progress.update("Found " + passingPeptides.size() + " peptides identified at "
				+ (job.getParameters().getPercolatorThreshold() * 100.0f) + "% FDR", 1.0f);
		Logger.logLine("Finished analysis! " + passingPeptides.size() + " peptides identified at "
				+ (parameters.getPercolatorThreshold() * 100f) + "% FDR ("
				+ (Math.round((System.currentTimeMillis() - startTime) / 1000f / 6f) / 10f) + " minutes)");
		Logger.logLine("");
	}

	public static PSMConsumer generateFeatureFile(ProgressIndicator progress, LibraryInterface library,
			EncyclopediaTwoJobData job, StripeFileInterface stripefile,
			Optional<Pair<MProphetResult, TargeteDecoyPSMFilter>> prelimAnalysis)
					throws IOException, SQLException, DataFormatException, InterruptedException {
		return generateFeatureFile(progress, library, job, stripefile, prelimAnalysis, OptionalInt.empty());
	}

	public static PSMConsumer generateFeatureFile(ProgressIndicator progress, LibraryInterface library, EncyclopediaTwoJobData job, StripeFileInterface stripefile, Optional<Pair<MProphetResult, TargeteDecoyPSMFilter>> prelimAnalysis, OptionalInt shuffledSeed) throws IOException, SQLException, DataFormatException, InterruptedException {
		return generateFeatureFile(progress, library, job, stripefile, prelimAnalysis, shuffledSeed, ignored -> {});
	}

	public record SearchSelection(LibraryEntry target, java.util.List<LibraryEntry> candidates,
			EncyclopediaTwoDecoyStrategy.Mode mode) {
		public boolean targetOnly() { return candidates.size() == 1; }
	}

	public static PSMConsumer generateFeatureFile(ProgressIndicator progress, LibraryInterface library, EncyclopediaTwoJobData job,
			StripeFileInterface stripefile, Optional<Pair<MProphetResult, TargeteDecoyPSMFilter>> prelimAnalysis,
			OptionalInt shuffledSeed, Consumer<SearchSelection> selectionConsumer)
			throws IOException, SQLException, DataFormatException, InterruptedException {

		LibraryScoringFactory taskFactory=job.getTaskFactory();
		SearchParameters parameters=taskFactory.getParameters();
		EncyclopediaTwoDecoyStrategy decoyStrategy=EncyclopediaTwoDecoyStrategy.resolve(library, parameters);
		MatchedDecoyAssessment assessment=decoyStrategy.getAssessment();
		Logger.logLine("EncyclopeDIATwo decoy strategy: "+decoyStrategy.getMode()+" ("+decoyStrategy.getReason()
				+"; mapped="+assessment.getUniquelyMappedTargetCount()+"/"+assessment.getTargetCount()
				+", missing="+assessment.getMissingTargetCount()+", ambiguous="+assessment.getAmbiguousTargetCount()+")");
		File featureFile=job.getPercolatorFiles().getInputTSV();

		int cores=parameters.getNumberOfThreadsUsed();

		Logger.logLine("Processing precursors scans...");
		PrecursorScanMap precursors=new PrecursorScanMap(stripefile.getPrecursors(-Float.MAX_VALUE, Float.MAX_VALUE));

		// get targeted ranges
		ArrayList<Range> ranges=new ArrayList<Range>();
		for (Range range : stripefile.getRanges().keySet()) {
			if (!parameters.useTargetWindowCenter()||range.contains(parameters.getTargetWindowCenter())) {
				if (range.getMiddle()>0.0f) { // TODO repair if we ever work in negative mode
					ranges.add(range);
				}
			}
		}

		Collections.sort(ranges);

		assert(taskFactory instanceof EncyclopediaTwoScoringFactory);
		PSMScorer scorer = taskFactory.getLibraryScorer(null);
		assert(scorer instanceof EncyclopediaTwoScorer);
		scorer=new EncyclopediaTwoLDAScorer((EncyclopediaTwoScorer)scorer, prelimAnalysis);

		PeptideScoringResultsConsumer writeResultsConsumer;
		if (taskFactory instanceof EncyclopediaTwoScoringFactory) {
			writeResultsConsumer=((EncyclopediaTwoScoringFactory)taskFactory).getResultsConsumer(featureFile, scorer.getAuxScoreNames(null), new LinkedBlockingQueue<AbstractScoringResult>(), stripefile, library);
		} else {
			writeResultsConsumer=taskFactory.getResultsConsumer(featureFile, new LinkedBlockingQueue<AbstractScoringResult>(), stripefile, library);
		}
		PSMConsumer saveResultsConsumer=new PSMConsumer(new LinkedBlockingQueue<AbstractScoringResult>(), parameters.getAAConstants());

		BlockingQueue<AbstractScoringResult> resultsQueue=new LinkedBlockingQueue<AbstractScoringResult>();
		TeeResultsConsumer teeConsumer=new TeeResultsConsumer(resultsQueue, writeResultsConsumer, saveResultsConsumer);
		Thread consumer1Thread=new Thread(teeConsumer);
		Thread consumer2Thread=new Thread(writeResultsConsumer);
		Thread consumer3Thread=new Thread(saveResultsConsumer);
		consumer1Thread.start();
		consumer2Thread.start();
		consumer3Thread.start();

		// Try-finally to ensure all threads are cleaned up.
		try {
			// get stripes
			int rangesFinished = 0;
			float numberOfTasks = 2.0f + ranges.size();

			ArrayList<LibraryEntry> nextEntries=library.getEntries(ranges.get(0), useSqrt, parameters.getAAConstants());
			ArrayList<FragmentScan> nextStripes=stripefile.getStripes(ranges.get(0).getMiddle(), -Float.MAX_VALUE, Float.MAX_VALUE, useSqrt);

			for (int rangeIndex = 0; rangeIndex < ranges.size(); rangeIndex++) {
				Range range=ranges.get(rangeIndex);
			ArrayList<LibraryEntry> entries=nextEntries;
			ArrayList<FragmentScan> stripes=nextStripes;
			HashMap<MatchedDecoyKey, LibraryEntry> mappedDecoys=new HashMap<>();
			if (decoyStrategy.usesPredictedDecoys()) {
				mappedDecoys=((MatchedDecoyLibrary)library).getMappedDecoyEntries(range, useSqrt, parameters.getAAConstants());
			}

				String baseMessage = "Working on " + range + " m/z";
				float baseIncrement = 1.0f / numberOfTasks;
				float baseProgress = (1.0f + rangesFinished) / numberOfTasks;
				progress.update(baseMessage, baseProgress);

				float dutyCycle = stripefile.getRanges().get(range).getAverageDutyCycle();
				if (dutyCycle <= 0f) {
					// A stripe with only one scan will get duty cycle
					// of zero. This will only happen in the case of a
					// bad file, or DDA data (where precursor ranges are
					// typically unique). Note that this doesn't guard
					// against (positive) infinity or NaN, but if these
					// values occur it's unclear how to interpret them.
					continue;
				}
				Logger.logLine("Processing " + range + " m/z, (" + dutyCycle + " second duty cycle), "+stripes.size()+" MS/MS");

				Collections.sort(stripes);

				if (stripes.size() < 10) {
					// A stripe with very few scans indicates that either
					// the file is bad, or this is DDA data. Similar to
					// above, we simply skip this stripe.
					continue;
				}
				float[] rts=new float[stripes.size()];
				for (int i = 0; i < rts.length; i++) {
					rts[i]=stripes.get(i).getScanStartTime();
				}

				// prepare executor for background
				ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("STRIPE_" + range.getStart() + "to" + range.getStop() + "-%d").setDaemon(true).build();
				LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>();
				ExecutorService executor = new ThreadPoolExecutor(cores, cores, Long.MAX_VALUE, TimeUnit.NANOSECONDS, workQueue, threadFactory);
				ArrayList<Future<?>> scoringTasks = new ArrayList<>();
				try {
					int count = 0;

					for (LibraryEntry entry : entries) {
						count++;
						ArrayList<LibraryEntry> tasks = getTargetAndPrimaryDecoy(entry, decoyStrategy, mappedDecoys, parameters);

						if (shuffledSeed.isPresent()) {
							int seed = shuffledSeed.getAsInt();
							LibraryEntry shuffle = entry.getShuffle(parameters, seed, false);

							tasks.add(shuffle);
							tasks.add(shuffle.getDecoy(parameters)); 
						} else if (!decoyStrategy.usesPredictedDecoys()) {
							float extraDecoys = parameters.getNumberOfExtraDecoyLibrariesSearched();
							while (extraDecoys > 0.0f) {
								if (extraDecoys < 1.0f) { 
									float test = RandomGenerator.random(count);

									if (test > extraDecoys) {
										break; 
									}
								}
								extraDecoys = extraDecoys - 1.0f;
								LibraryEntry shuffle = entry.getShuffle(parameters, Float.hashCode(extraDecoys), false);

								tasks.add(shuffle);
								tasks.add(shuffle.getDecoy(parameters));

						}
					}
					selectionConsumer.accept(new SearchSelection(entry, java.util.List.copyOf(tasks), decoyStrategy.getMode()));
					ArrayList<FragmentScan> localStripes;
					if (prelimAnalysis.isPresent()) {
						localStripes = getScanSubsetFromStripes(entry, prelimAnalysis.get().getY(), stripes, rts);
					} else {
						localStripes = stripes;
					}
					scoringTasks.add(executor.submit(taskFactory.getScoringTask(scorer, tasks, localStripes, range, dutyCycle, precursors, resultsQueue)));
				}

				if (rangeIndex+1<ranges.size()) {
					nextEntries=library.getEntries(ranges.get(rangeIndex+1), useSqrt, parameters.getAAConstants());
					nextStripes=stripefile.getStripes(ranges.get(rangeIndex+1).getMiddle(), -Float.MAX_VALUE, Float.MAX_VALUE, useSqrt);
				}

				executor.shutdown();
				while (!executor.isTerminated()) {
					Logger.logLine(workQueue.size() + " peptides remaining for " + range + "...");
					float finishedFraction = (count - workQueue.size()) / (float) count;
					progress.update(baseMessage, baseProgress + baseIncrement * (0.2f + finishedFraction * 0.8f));
					Thread.sleep(500);
				}
				executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
				for (Future<?> task : scoringTasks) {
					try {
						task.get();
					} catch (ExecutionException e) {
						throw new IOException("Scoring worker failed for " + range, e.getCause());
					}
				}
			} finally {
				// ExecutorService is only AutoCloseable from Java 19, and this project targets 17.
				executor.shutdownNow();
			}

			rangesFinished++;
		}

		progress.update("Organizing results", (1.0f+rangesFinished)/numberOfTasks);
	}finally

	{
		// The queue MUST be poisoned to avoid a hang due to leftover threads!
		resultsQueue.put(AbstractScoringResult.POISON_RESULT);

		// Wait until the threads we started finish, to ensure they've cleaned up
		// before we move on e.g. with handling a thrown exception.
		consumer1Thread.join();
		consumer2Thread.join();
		consumer3Thread.join();
		teeConsumer.close();
	}

	Logger.logLine(writeResultsConsumer.getNumberProcessed()+" total peptides processed.");

	return saveResultsConsumer;
}

static ArrayList<LibraryEntry> getTargetAndPrimaryDecoy(LibraryEntry target, EncyclopediaTwoDecoyStrategy strategy,
		HashMap<MatchedDecoyKey, LibraryEntry> mappedDecoys, SearchParameters parameters) {
	ArrayList<LibraryEntry> candidates=new ArrayList<>();
	candidates.add(target);
	if (strategy.usesPredictedDecoys()) {
		LibraryEntry decoy=mappedDecoys.get(new MatchedDecoyKey(target));
		if (decoy!=null) candidates.add(decoy);
	} else {
		candidates.add(target.getDecoy(parameters));
	}
	return candidates;
}

static ArrayList<FragmentScan> getScanSubsetFromStripes(LibraryEntry entry, TargeteDecoyPSMFilter filter, ArrayList<FragmentScan> allScansInStripe, float[] rts) {
	float modelRT=entry.getScanStartTime()/60f;
	float realRT=filter.getYRT(modelRT)*60f;
	ArrayList<FragmentScan> subset=new ArrayList<FragmentScan>();

	// find center
	int index=Arrays.binarySearch(rts, realRT);
	if (index<0) {
		index=-(index+1);
	}
	if (index>=allScansInStripe.size()) index=allScansInStripe.size()-1;
	if (index<0) {
		// no scans
		return subset;
	}
	// for before the center, add at end, then reverse
	for (int i = index; i >= 0; i--) {
		float actualRT=allScansInStripe.get(i).getScanStartTime()/60f;
		boolean passes=filter.getRtFilter().getProbabilityFitsModel(actualRT, modelRT)>=0.5f;
		if (passes) {
			subset.add(allScansInStripe.get(i));
		} else {
			break;
		}
	}
	Collections.reverse(subset);

	// then for after the center (+1) add to the end
	for (int i = index+1; i < rts.length; i++) {
		float actualRT=allScansInStripe.get(i).getScanStartTime()/60f;
		boolean passes=filter.getRtFilter().getProbabilityFitsModel(actualRT, modelRT)>=0.5f;
		if (passes) {
			subset.add(allScansInStripe.get(i));
		} else {
			break;
		}
	}

	return subset;
}

public static Pair<MProphetResult, TargeteDecoyPSMFilter> firstPassFDR(ProgressIndicator progress, EncyclopediaTwoJobData job, StripeFileInterface stripefile, PSMConsumer saveResultsConsumer) throws IOException, FileNotFoundException, UnsupportedEncodingException, InterruptedException {
	SearchParameters parameters=job.getParameters();

	try {
		progress.update("Running mProphet ("+(parameters.getPercolatorThreshold()*100f)+"%)");
		Logger.logLine("Running mProphet ("+(parameters.getPercolatorThreshold()*100f)+"%)");

		MProphetExecutionData mprophetData=new MProphetExecutionData(job.getPercolatorFiles());
		MProphetResult result=MProphetReiter.executeMProphetTSV(mprophetData, job.getParameters().getPercolatorThreshold(), job.getParameters().getAAConstants(), 1);
		ArrayList<PercolatorPeptide> passingPeptides=result.getPassingPeptides();
		Logger.logLine("First pass: "+passingPeptides.size()+" peptides identified at "+(parameters.getPercolatorThreshold()*100f)+"% FDR");

		ArrayList<PSMInterface> data=saveResultsConsumer.getSavedResults();
		ArrayList<PercolatorPeptide> decoyPeptides = getDecoyPeptides(job, parameters, passingPeptides.size());
		TargeteDecoyPSMFilter filter=getRescoringModel(passingPeptides, decoyPeptides, data, job, false);

		return new Pair<MProphetResult, TargeteDecoyPSMFilter>(result, filter);
	} catch (EncyclopediaException e) {
		Logger.errorLine("Fatal Error: "+e.getMessage());
		Logger.errorLine("Sorry, not feeling well today! Try again tomorrow!");
		progress.update("Fatal Error: "+e.getMessage(), -1.0f);
		throw e;
	}
}

public static Pair<ArrayList<PercolatorPeptide>, TargeteDecoyPSMFilter> repercolatePeptides(
		ProgressIndicator progress, EncyclopediaTwoJobData job, StripeFileInterface stripefile,
		PSMConsumer saveResultsConsumer)
				throws IOException, FileNotFoundException, UnsupportedEncodingException, InterruptedException {
	SearchParameters parameters = job.getParameters();

	try {
		ArrayList<PSMInterface> data = saveResultsConsumer.getSavedResults();

		ArrayList<PercolatorPeptide> passingPeptides;
		if (parameters.isUsePercolator()) {
			progress.update("Running Percolator (" + (parameters.getPercolatorThreshold() * 100f) + "%)");
			Logger.logLine("Running Percolator (" + (parameters.getPercolatorThreshold() * 100f) + "%)");
			Pair<ArrayList<PercolatorPeptide>, Float> pair = PercolatorExecutor.executePercolatorTSV(
					parameters.getPercolatorVersionNumber(), job.getPercolatorFiles(),
					parameters.getEffectivePercolatorThreshold(), parameters.getAAConstants(), 2);
			passingPeptides = pair.x;
		} else {
			progress.update("Running mProphet (" + (parameters.getPercolatorThreshold() * 100f) + "%)");
			Logger.logLine("Running mProphet (" + (parameters.getPercolatorThreshold() * 100f) + "%)");

			MProphetExecutionData mprophetData = new MProphetExecutionData(job.getPercolatorFiles());
			MProphetResult result = MProphetReiter.executeMProphetTSV(mprophetData,
					job.getParameters().getPercolatorThreshold(), job.getParameters().getAAConstants(), 1);
			passingPeptides = result.getPassingPeptides();
		}
		// FIXME THIS NEVER REALIGNS THE RETENTION TIMES
		ArrayList<PercolatorPeptide> decoyPeptides = getDecoyPeptides(job, parameters, passingPeptides.size());
		TargeteDecoyPSMFilter filter = getRescoringModel(passingPeptides, decoyPeptides, data, job, true);

		progress.update(passingPeptides.size() + " peptides identified at "
				+ (parameters.getPercolatorThreshold() * 100.0f) + "% FDR", 1.0f);
		return new Pair<ArrayList<PercolatorPeptide>, TargeteDecoyPSMFilter>(passingPeptides, filter);

	} catch (EncyclopediaException e) {
		Logger.errorLine("Fatal Error: " + e.getMessage());
		Logger.errorLine("Sorry, not feeling well today! Try again tomorrow!");
		progress.update("Fatal Error: " + e.getMessage(), -1.0f);
		throw e;
	}
}

private static ArrayList<PercolatorPeptide> getDecoyPeptides(EncyclopediaTwoJobData job,
		SearchParameters parameters, int size) {
	ArrayList<PercolatorPeptide> decoyPeptides = new ArrayList<PercolatorPeptide>();
	Pair<ArrayList<PercolatorPeptide>, Float> allDecoyPeptides = PercolatorReader.getPassingPeptidesFromTSV(
			job.getPercolatorFiles().getPeptideDecoyFile(), 1.0f, parameters.getAAConstants(), true);
	for (PercolatorPeptide peptide : allDecoyPeptides.x) {
		if (decoyPeptides.size() < size) {
			decoyPeptides.add(peptide);
		}
	}
	return decoyPeptides;
}

public static TargeteDecoyPSMFilter getRescoringModel(ArrayList<PercolatorPeptide> passingPeptides,
		ArrayList<PercolatorPeptide> decoyPeptides, ArrayList<PSMInterface> data, EncyclopediaTwoJobData job,
		boolean finalPass) {
	ArrayList<PSMInterface> passingPSMs = passingPeptides(passingPeptides, data);
	ArrayList<PSMInterface> decoyPSMs = passingPeptides(decoyPeptides, data);

	Logger.logLine("Generating retention time mapping using " + passingPSMs.size() + "/" + passingPeptides.size()
	+ " target and " + decoyPSMs.size() + "/" + decoyPeptides.size() + " decoy points...");
	TargeteDecoyPSMFilter filter = new TargeteDecoyPSMFilter(job.getParameters(), passingPSMs, decoyPSMs);

	final String passTag = finalPass ? ".final" : ".first";
	filter.makePlots(job.getParameters(), passingPSMs, Optional
			.ofNullable(new File(job.getPercolatorFiles().getPeptideOutputFile().getAbsolutePath() + passTag)));
	return filter;
}

private static ArrayList<PSMInterface> passingPeptides(ArrayList<PercolatorPeptide> passingPeptides,
		ArrayList<PSMInterface> data) {
	HashSet<String> passingSeqs = new HashSet<String>();
	for (PercolatorPeptide pass : passingPeptides) {
		passingSeqs.add(PercolatorPeptide.getPeptideSequence(pass.getPsmID()) + "+"
				+ PercolatorPeptide.getCharge(pass.getPsmID()));
	}

	ArrayList<PSMInterface> passingPSMs = new ArrayList<>();

	for (PSMInterface result : data) {
		String peptideModSeq = result.getLibraryEntry().getPeptideModSeq();
		if (passingSeqs.contains(peptideModSeq + "+" + result.getLibraryEntry().getPrecursorCharge())) {
			passingPSMs.add(result);
		}
	}
	return passingPSMs;
}
}
