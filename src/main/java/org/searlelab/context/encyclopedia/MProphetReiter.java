package org.searlelab.context.encyclopedia;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.MProphetDataset;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.MProphetExecutionData;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.MProphetFeatureReader;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.MProphetResult;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.PercolatorPeptide;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.ScoredMProphetData;
import edu.washington.gs.maccoss.encyclopedia.datastructures.AminoAcidConstants;
import edu.washington.gs.maccoss.encyclopedia.utils.EncyclopediaException;
import edu.washington.gs.maccoss.encyclopedia.utils.Logger;
import edu.washington.gs.maccoss.encyclopedia.utils.Pair;
import edu.washington.gs.maccoss.encyclopedia.utils.math.General;
import edu.washington.gs.maccoss.encyclopedia.utils.math.LinearDiscriminantAnalysis;
import edu.washington.gs.maccoss.encyclopedia.utils.math.RandomGenerator;
import edu.washington.gs.maccoss.encyclopedia.utils.math.ScoredObject;
import gnu.trove.map.hash.TObjectDoubleHashMap;

public class MProphetReiter implements Runnable {
	private final float peptideFDRThreshold;
	private final MProphetExecutionData settings;
	private final AminoAcidConstants aaConstants;
	private final int seed;
	// CTX-018 Context-only mode: on only for the Context mProphet workflow; every other caller keeps the legacy path.
	private final boolean contextMode;
	private final List<MProphetDataset[]> suppliedFolds;
	private final ArrayList<String> iterationRecords = new ArrayList<>();
	
	private Throwable error;
	private MProphetResult result;
	
	public MProphetReiter(MProphetExecutionData settings, float peptideFDRThreshold, int seed, AminoAcidConstants aaConstants) {
		this(settings, peptideFDRThreshold, seed, aaConstants, false, null);
	}

	private MProphetReiter(MProphetExecutionData settings, float peptideFDRThreshold, int seed, AminoAcidConstants aaConstants,
			boolean contextMode, List<MProphetDataset[]> suppliedFolds) {
		this.settings = settings;
		this.peptideFDRThreshold=peptideFDRThreshold;
		this.aaConstants=aaConstants;
		this.seed=seed;
		this.contextMode=contextMode;
		this.suppliedFolds=suppliedFolds;
	}

	/** Training iterations; also the number of iterations a diagnostic fold file must describe. */
	public static final int ITERATIONS = 50;

	/**
	 * CTX-018 Context-only training on a dataset Context built itself. Differences from the legacy path: models are
	 * ranked by passing count alone with ties in iteration order (Context-MS), folds are not capped in size, and
	 * {@code folds} (one {training, testing} pair per iteration, from a diagnostic fold file) replaces the random
	 * split when given. A training failure is thrown rather than logged and returned as null.
	 */
	public static ContextTraining executeContextTraining(MProphetDataset background, MProphetExecutionData settings,
			float threshold, int seed, AminoAcidConstants aaConstants, List<MProphetDataset[]> folds) throws EncyclopediaException {
		if (folds != null && folds.size() != ITERATIONS) {
			throw new EncyclopediaException("A fold plan needs " + ITERATIONS + " iterations, not " + folds.size());
		}
		MProphetReiter prophet = new MProphetReiter(settings, threshold, seed, aaConstants, true, folds);
		MProphetResult result = prophet.calculateProbabilities(background, seed);
		return new ContextTraining(result, List.copyOf(prophet.iterationRecords));
	}

	/** Applies a background model to a reference dataset Context built with the same feature names, without retraining. */
	public static MProphetResult applyContextModel(MProphetDataset reference, MProphetExecutionData settings, float threshold,
			LinearDiscriminantAnalysis lda, AminoAcidConstants aaConstants) throws EncyclopediaException {
		return new MProphetReiter(settings, threshold, 1, aaConstants, true, null).calculateProbabilitiesWithModel(reference, lda);
	}

	/** The trained result plus one tab-separated record per iteration: iteration, trained passing, seed passing, kept, kept passing. */
	public record ContextTraining(MProphetResult result, List<String> iterations) {
	}
	
	// Run the excuteMProphetTSV function with a fixed seed
	public static MProphetResult executeMProphetTSV(MProphetExecutionData commandData, float threshold, AminoAcidConstants aaConstants, int round) throws IOException, FileNotFoundException, UnsupportedEncodingException, InterruptedException {
		int seed=1;
		return executeMProphetTSV(commandData, threshold, seed, aaConstants, round);
	}
	
	// Run the execute mPRophet with seed as an input parameter
	public static MProphetResult executeMProphetTSV(MProphetExecutionData commandData, float threshold, int seed, AminoAcidConstants aaConstants, int round) throws IOException, FileNotFoundException, UnsupportedEncodingException, InterruptedException {
		MProphetReiter prophet=new MProphetReiter(commandData, threshold, seed, aaConstants);
		prophet.run();
		return prophet.getResult();
	}
	
	public void runWithModel(LinearDiscriminantAnalysis lda) {
		File file = settings.getInputTSV();
		
		try {
			MProphetDataset data = MProphetFeatureReader.parseFeatureFile(file, settings);
			result = calculateProbabilitiesWithModel(data, lda);
		} catch (Throwable t ) {
			Logger.errorLine("Error performing MProphetReiter with the supplied LDA model");
			Logger.errorException(t);
			this.error = t;
		}
	}
	
	public static MProphetResult executeMProphetTSVWithModel(MProphetExecutionData commandData, float threshold, LinearDiscriminantAnalysis lda, AminoAcidConstants aaConstants) throws IOException, FileNotFoundException, UnsupportedEncodingException, InterruptedException {
		MProphetReiter prophet = new MProphetReiter(commandData, threshold, 1, aaConstants);
		prophet.runWithModel(lda);
		return prophet.getResult();
	}

	@Override
	public void run() {
		File file=settings.getInputTSV();
		try {
			MProphetDataset data = MProphetFeatureReader.parseFeatureFile(file, settings);
			result = calculateProbabilities(data, seed);

		} catch (Throwable t) {
			Logger.errorLine("Error performing mProphet!");
			Logger.errorException(t);

			this.error = t;
		}
	}

	protected MProphetResult calculateProbabilities(MProphetDataset dataset, int seed) throws EncyclopediaException {
		int randomSeed=RandomGenerator.randomInt(seed);
		int iterationCount=50;
		int maxKeptModels=iterationCount/2;
		int numIterationsPerCalculation=10;
		
		TObjectDoubleHashMap<String> seedCoefficients=new TObjectDoubleHashMap<String>();
		seedCoefficients.put("HyperScore", 0.6);
		seedCoefficients.put("xCorrLib", 5.0);
		seedCoefficients.put("xCorrModel", 0.2);
		seedCoefficients.put("numberOfMatchingPeaksAboveThreshold", 0.2);
		seedCoefficients.put("isotopeDotProduct", 0.5);
		seedCoefficients.put("correlationToPrecursor", 0.4);
		seedCoefficients.put("isIntegratedSignal", 0.2);
		seedCoefficients.put("numPeaksWithGoodCorrelation", 0.1);
		
		seedCoefficients.put("main_var_Intensity", 1.0); // W0
		seedCoefficients.put("var_Shape_(weighted)", 4.0); // W4
		seedCoefficients.put("var_Co-elution_(weighted)", -0.05); // W5
		seedCoefficients.put("var_Co-elution_count", 1.0); // W1
		
		
		double[] coefficients=new double[dataset.getFeatureNames().size()];
		for (int i = 0; i < coefficients.length; i++) {
			double value=seedCoefficients.get(dataset.getFeatureNames().get(i));
			if (value!=0.0) {
				coefficients[i]=value;
			}
		}
		LinearDiscriminantAnalysis seedModel=new LinearDiscriminantAnalysis(coefficients, 0.0);
		
		boolean seedUsable=!(General.checkNaN(seedModel.getCoefficients())||General.checkIfAllX(seedModel.getCoefficients(), 0.0));
		ArrayList<ScoredObject<LinearDiscriminantAnalysis>> models=new ArrayList<ScoredObject<LinearDiscriminantAnalysis>>();
		for (int n = 0; n < iterationCount; n++) {
			randomSeed=RandomGenerator.randomInt(randomSeed);
			MProphetDataset[] folds;
			if (contextMode && suppliedFolds != null) {
				folds=suppliedFolds.get(n);
			} else {
				int maximumFoldSize=contextMode ? Math.max(dataset.getTargetData().size(), dataset.getDecoyData().size())
						: settings.getParameters().getPercolatorTrainingSetSize();
				folds=MProphetDataset.splitKFold(dataset, 2, randomSeed, maximumFoldSize);
			}
			
			MProphetDataset trainingDataset=folds[0];
			MProphetDataset testingDataset=folds[1];
			if (contextMode && trainingDataset.getStartingScoreIndex() < 0) {
				// No preferred starting score: Context-MS picks the best-separating feature on each training fold.
				trainingDataset=new MProphetDataset(new ArrayList<>(trainingDataset.getFeatureNames()),
						bestSeparatingFeature(trainingDataset), trainingDataset.allData());
			}
			
			if (contextMode && (trainingDataset.getTargetData().isEmpty() || trainingDataset.getDecoyData().isEmpty())) {
				// Context-MS skips an iteration whose training fold lacks targets or decoys.
				Logger.logLine("Iteration "+(n+1)+": training fold has no targets or no decoys; skipped");
				iterationRecords.add(n + "\t\t\t\t");
				continue;
			}

			LinearDiscriminantAnalysis lda;
			if(General.checkNaN(seedModel.getCoefficients())||General.checkIfAllX(seedModel.getCoefficients(), 0.0)) {
				lda=null;
			} else {
				lda=seedModel;
			}
			
			int best=0;
			LinearDiscriminantAnalysis previousLDA=null;
			for (int i = 0; i < numIterationsPerCalculation; i++) {
				float targetFDR=0.01f;
				if (i==0) {
					targetFDR=0.15f; // first time through Skyline starts with 15% of the target data, not a 15% FDR. This ensures that the first round of training can happen!
				} else if (i==1) {
					targetFDR=0.02f;
				}
				
				ArrayList<ScoredMProphetData> data;
				if (i==0) {
					data=trainingDataset.getPassingTargetsByPercentage(Optional.ofNullable(lda), targetFDR);
				} else {
					data=trainingDataset.getPassingTargetsByFDR(Optional.ofNullable(lda), targetFDR).x;
				}
				
				System.out.println(n+"."+i+") "+data.size()+" (targetFDR="+Math.round(targetFDR*100)+"%) starting model: "+lda); //FIXME
				if (data.size()==0||(i>2&&data.size()<=best)) {
					if (previousLDA!=null) {
						lda=previousLDA;
					}
					break;
				}
				best=data.size();
				ArrayList<float[]> decoyData = trainingDataset.getDecoyData();
				ArrayList<float[]> scoredData = MProphetDataset.getScoredData(data);
				if (decoyData.size()>scoredData.size()*10) {
					// make sure the decoy data doesn't get too out of hand in size
					decoyData=new ArrayList<float[]>(decoyData.subList(0, scoredData.size()*10));
				}
				if (contextMode && (scoredData.isEmpty() || decoyData.isEmpty())) {
					// Context-MS's build_lda returns no model without positives or negatives; keep the current model.
					break;
				}
				LinearDiscriminantAnalysis model=LinearDiscriminantAnalysis.buildModel(scoredData, decoyData);
				
				if (!General.checkNaN(model.getCoefficients())&&!General.checkIfAllX(model.getCoefficients(), 0.0)) {
					// if we get NaNs, then fall back on wherever we were previously
					previousLDA=lda;
					lda=model;
				} else {
					break;
				}
			}
			
			if (lda==null) {
				Logger.logLine("Iteration "+(n+1)+": Failed to generate a meaningful model!");
				iterationRecords.add(n + "\t\t\t\t");
			} else {
				Pair<ArrayList<ScoredMProphetData>, Float> data=testingDataset.getPassingTargetsByFDR(Optional.ofNullable(lda), 0.01f);
				// Context-MS compares the seed model on the test fold only when the seed model is usable.
				Pair<ArrayList<ScoredMProphetData>, Float> seedData=contextMode && !seedUsable ? null
						: testingDataset.getPassingTargetsByFDR(Optional.ofNullable(seedModel), 0.01f);
				boolean preferSeed=seedData!=null && seedData.x.size()>data.x.size();
				iterationRecords.add(n + "\t" + data.x.size() + "\t" + (seedData==null ? "" : Integer.toString(seedData.x.size()))
						+ "\t" + (preferSeed ? "seed" : "trained") + "\t" + (preferSeed ? seedData.x.size() : data.x.size()));
				if (preferSeed) {
					models.add(new ScoredObject<LinearDiscriminantAnalysis>(seedData.x.size(), seedModel));
					Logger.logLine("Iteration "+(n+1)+": prefer seed model, "+seedData.x.size()+"/"+testingDataset.getTargetData().size()+" passing, pi0:"+cappedPi0(seedData.y));
				} else {
					models.add(new ScoredObject<LinearDiscriminantAnalysis>(data.x.size(), lda));
					Logger.logLine("Iteration "+(n+1)+": "+data.x.size()+"/"+testingDataset.getTargetData().size()+" passing, pi0:"+cappedPi0(data.y));
				}
			}
		}
		
		LinearDiscriminantAnalysis averageModel;
		if (models.size()==0 && contextMode && !seedUsable) {
			throw new EncyclopediaException("mProphet training failed: no usable model was trained and the seed model is unusable "
					+ "(none of its seed features are present). Check that the background has enough targets and decoys.");
		}
		if (models.size()==0) {
			Logger.logLine("No meaningful models generated, falling back on seed model for separation!");
			averageModel=seedModel;
		} else {	
			rankModels(models, contextMode);
			// keep the N best models
			ArrayList<LinearDiscriminantAnalysis> bestModels=new ArrayList<LinearDiscriminantAnalysis>();
			for (ScoredObject<LinearDiscriminantAnalysis> scoredModel : models) {
				bestModels.add(scoredModel.getY());
				if (bestModels.size()>=maxKeptModels) break;
			}
			averageModel=LinearDiscriminantAnalysis.average(bestModels);
		}

		Pair<ArrayList<ScoredMProphetData>, Float> finalData=dataset.getPassingTargetsByFDR(Optional.ofNullable(averageModel), Float.MAX_VALUE);
		int passingCount=0;
		for (ScoredMProphetData data : finalData.x) {
			if (data.getFDR()<0.01) passingCount++;
		}
		Pair<ArrayList<ScoredMProphetData>, Float> finalDecoyData=dataset.getPassingTargetsByFDR(Optional.ofNullable(averageModel), Float.MAX_VALUE, true);

		float pi0=cappedPi0(finalData.y);

		// report model for logging
		Logger.logLine("Final model: "+passingCount+"/"+dataset.getTargetData().size()+" passing, pi0:"+pi0);
		
		for (int i = 0; i < averageModel.getCoefficients().length; i++) {
			Logger.logLine("   "+dataset.getFeatureNames().get(i)+" --> "+averageModel.getCoefficients()[i]);
		}
		Logger.logLine("   constant --> "+averageModel.getConstant());
		
		ArrayList<ScoredMProphetData> allData=new ArrayList<ScoredMProphetData>();
		allData.addAll(finalData.x);
		allData.addAll(finalDecoyData.x);
		Collections.sort(allData);
			
		HashSet<String> detectedPeptideSequences=new HashSet<String>();
		ArrayList<PercolatorPeptide> detectedPeptides=new ArrayList<>();
		try {
			PrintWriter targetWriter=new PrintWriter(settings.getPeptideOutputFile(), "UTF-8");
			PrintWriter decoyWriter=new PrintWriter(settings.getPeptideDecoyFile(), "UTF-8");
			
			targetWriter.println("PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds");
			decoyWriter.println("PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds");
			
			for (ScoredMProphetData scoredData : allData) {
				if (detectedPeptideSequences.contains(scoredData.getData().getSequence())) {
					// only keep the best (highest scoring) representation of the peptide
					continue;
				}
				detectedPeptideSequences.add(scoredData.getData().getSequence());
				
				float score=scoredData.getScore();
				float qValue=(float)scoredData.getFDR();
				float posteriorErrorProb=(float)scoredData.getLocalFDR();
				if (qValue<=peptideFDRThreshold&&!scoredData.getData().isDecoy()) {
					PercolatorPeptide pep=new PercolatorPeptide(scoredData.getData().getId(), scoredData.getData().getProtein(), qValue, posteriorErrorProb, aaConstants);
					detectedPeptides.add(pep);
				}
				
				if (scoredData.getData().isDecoy()) {
					decoyWriter.println(scoredData.getData().getId()+"\t"+score+"\t"+qValue+"\t"+posteriorErrorProb+"\t"+"-."+scoredData.getData().getSequence()+".-"+"\t"+scoredData.getData().getProtein());
				} else {
					targetWriter.println(scoredData.getData().getId()+"\t"+score+"\t"+qValue+"\t"+posteriorErrorProb+"\t"+"-."+scoredData.getData().getSequence()+".-"+"\t"+scoredData.getData().getProtein());
				}
			}
	
			targetWriter.println("pi_0="+finalData.y);
			decoyWriter.println("pi_0="+finalData.y);
			targetWriter.flush();
			decoyWriter.flush();
			targetWriter.close();
			decoyWriter.close();

		} catch (FileNotFoundException e) {
			throw new EncyclopediaException("Error setting up output file: " + settings.getPeptideOutputFile().getAbsolutePath(), e);
		} catch (UnsupportedEncodingException e) {
			throw new EncyclopediaException("Error setting up output file: " + settings.getPeptideOutputFile().getAbsolutePath(), e);
		}
		
		return new MProphetResult(detectedPeptides, averageModel, dataset.getFeatureNames(), pi0);
	}

	/**
	 * Best model first. Legacy: EncyclopeDIA's order (ties broken by the sum of the coefficients, then reversed).
	 * Context mode (CTX018-022): passing count alone, as Context-MS; a stable sort keeps tied models in iteration order.
	 */
	static void rankModels(ArrayList<ScoredObject<LinearDiscriminantAnalysis>> models, boolean contextMode) {
		if (contextMode) {
			models.sort(Comparator.comparingDouble((ScoredObject<LinearDiscriminantAnalysis> m) -> m.getScore()).reversed());
		} else {
			Collections.sort(models);
			Collections.reverse(models);
		}
	}

	/**
	 * Context-MS mprophet._auto_main_score: among features whose range exceeds 1e-6 in both targets and decoys, the one
	 * with the largest |mean difference| / mean of the two population SDs; feature 0 when none qualifies.
	 */
	static int bestSeparatingFeature(MProphetDataset dataset) {
		ArrayList<float[]> targets = dataset.getTargetData();
		ArrayList<float[]> decoys = dataset.getDecoyData();
		int features = dataset.getFeatureNames().size();
		int best = 0;
		double bestScore = Double.NEGATIVE_INFINITY;
		boolean anyValid = false;
		for (int f = 0; f < features; f++) {
			double[] t = column(targets, f);
			double[] d = column(decoys, f);
			boolean valid = range(t) > 1e-6 && range(d) > 1e-6;
			double score = valid ? Math.abs(mean(t) - mean(d)) / Math.max((sd(t) + sd(d)) / 2.0, 1e-12) : -1.0;
			anyValid |= valid;
			if (score > bestScore) {
				bestScore = score;
				best = f;
			}
		}
		return anyValid ? best : 0;
	}

	private static double[] column(ArrayList<float[]> rows, int feature) {
		double[] values = new double[rows.size()];
		for (int i = 0; i < values.length; i++) values[i] = rows.get(i)[feature];
		return values;
	}

	private static double range(double[] values) {
		double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
		for (double v : values) { min = Math.min(min, v); max = Math.max(max, v); }
		return values.length == 0 ? 0 : max - min;
	}

	private static double mean(double[] values) {
		double sum = 0;
		for (double v : values) sum += v;
		return sum / values.length;
	}

	private static double sd(double[] values) {
		double m = mean(values), squares = 0;
		for (double v : values) squares += (v - m) * (v - m);
		return Math.sqrt(squares / values.length);
	}

	private static float cappedPi0(float pi0) {
		return Math.min(1.0f, pi0);
	}
	
	protected MProphetResult calculateProbabilitiesWithModel(
			MProphetDataset dataset,
			LinearDiscriminantAnalysis lda)
			throws EncyclopediaException {

		Pair<ArrayList<ScoredMProphetData>, Float> finalData =
				dataset.getPassingTargetsByFDR(Optional.ofNullable(lda), Float.MAX_VALUE);

		int passingCount = 0;
		for (ScoredMProphetData data : finalData.x) {
			if (data.getFDR() < 0.01) passingCount++;
		}

		Pair<ArrayList<ScoredMProphetData>, Float> finalDecoyData =
				dataset.getPassingTargetsByFDR(Optional.ofNullable(lda), Float.MAX_VALUE, true);

		float pi0 = cappedPi0(finalData.y);

		Logger.logLine("Final supplied model: " + passingCount + "/"
				+ dataset.getTargetData().size() + " passing, pi0:" + pi0);

		for (int i = 0; i < lda.getCoefficients().length; i++) {
			Logger.logLine("   " + dataset.getFeatureNames().get(i) + " --> " + lda.getCoefficients()[i]);
		}
		Logger.logLine("   constant --> " + lda.getConstant());

		ArrayList<ScoredMProphetData> allData = new ArrayList<ScoredMProphetData>();
		allData.addAll(finalData.x);
		allData.addAll(finalDecoyData.x);
		Collections.sort(allData);

		HashSet<String> detectedPeptideSequences = new HashSet<String>();
		ArrayList<PercolatorPeptide> detectedPeptides = new ArrayList<>();

		try {
			PrintWriter targetWriter = new PrintWriter(settings.getPeptideOutputFile(), "UTF-8");
			PrintWriter decoyWriter = new PrintWriter(settings.getPeptideDecoyFile(), "UTF-8");

			targetWriter.println("PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds");
			decoyWriter.println("PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds");

			for (ScoredMProphetData scoredData : allData) {
				if (detectedPeptideSequences.contains(scoredData.getData().getSequence())) {
					continue;
				}

				detectedPeptideSequences.add(scoredData.getData().getSequence());

				float score = scoredData.getScore();
				float qValue = (float) scoredData.getFDR();
				float posteriorErrorProb = (float) scoredData.getLocalFDR();

				if (qValue <= peptideFDRThreshold && !scoredData.getData().isDecoy()) {
					PercolatorPeptide pep = new PercolatorPeptide(
							scoredData.getData().getId(),
							scoredData.getData().getProtein(),
							qValue,
							posteriorErrorProb,
							aaConstants
					);
					detectedPeptides.add(pep);
				}

				if (scoredData.getData().isDecoy()) {
					decoyWriter.println(scoredData.getData().getId() + "\t"
							+ score + "\t"
							+ qValue + "\t"
							+ posteriorErrorProb + "\t"
							+ "-." + scoredData.getData().getSequence() + ".-" + "\t"
							+ scoredData.getData().getProtein());
				} else {
					targetWriter.println(scoredData.getData().getId() + "\t"
							+ score + "\t"
							+ qValue + "\t"
							+ posteriorErrorProb + "\t"
							+ "-." + scoredData.getData().getSequence() + ".-" + "\t"
							+ scoredData.getData().getProtein());
				}
			}

			targetWriter.println("pi_0=" + pi0);
			decoyWriter.println("pi_0=" + pi0);

			targetWriter.flush();
			decoyWriter.flush();
			targetWriter.close();
			decoyWriter.close();

		} catch (FileNotFoundException e) {
			throw new EncyclopediaException("Error setting up output file: "
					+ settings.getPeptideOutputFile().getAbsolutePath(), e);
		} catch (UnsupportedEncodingException e) {
			throw new EncyclopediaException("Error setting up output file: "
					+ settings.getPeptideOutputFile().getAbsolutePath(), e);
		}

		return new MProphetResult(detectedPeptides, lda, dataset.getFeatureNames(), pi0);
	}

	public boolean hadError() {
		return null != error;
	}

	public Throwable getError() {
		return error;
	}
	
	public MProphetResult getResult() {
		return result;
	}
}