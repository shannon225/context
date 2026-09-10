package org.searlelab.context.encyclopedia;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;

import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.Spectrum;

import edu.washington.gs.maccoss.encyclopedia.EncyclopediaTwo;
import edu.washington.gs.maccoss.encyclopedia.algorithms.AbstractLibraryScoringTask;
import edu.washington.gs.maccoss.encyclopedia.algorithms.AbstractScoringResult;
import edu.washington.gs.maccoss.encyclopedia.algorithms.AuxillaryPSMScorer;
import edu.washington.gs.maccoss.encyclopedia.algorithms.EValueCalculator;
import edu.washington.gs.maccoss.encyclopedia.algorithms.IsotopicDistributionCalculator;
import edu.washington.gs.maccoss.encyclopedia.algorithms.PSMScorer;
import edu.washington.gs.maccoss.encyclopedia.algorithms.TDCPeptideScoringResult;
import edu.washington.gs.maccoss.encyclopedia.algorithms.alignment.TargeteDecoyPSMFilter;
import edu.washington.gs.maccoss.encyclopedia.algorithms.library.EncyclopediaScorer;
import edu.washington.gs.maccoss.encyclopedia.algorithms.library.EncyclopediaTwoLDAScorer;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.MProphetResult;
import edu.washington.gs.maccoss.encyclopedia.algorithms.quantitation.TransitionRefinementData;
import edu.washington.gs.maccoss.encyclopedia.algorithms.quantitation.TransitionRefiner;
import edu.washington.gs.maccoss.encyclopedia.datastructures.FragmentationModel;
import edu.washington.gs.maccoss.encyclopedia.datastructures.IntRange;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryEntry;
import edu.washington.gs.maccoss.encyclopedia.datastructures.PrecursorScanMap;
import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchParameters;
import edu.washington.gs.maccoss.encyclopedia.utils.Nothing;
import edu.washington.gs.maccoss.encyclopedia.utils.Pair;
import edu.washington.gs.maccoss.encyclopedia.utils.massspec.FragmentIon;
import edu.washington.gs.maccoss.encyclopedia.utils.massspec.MassTolerance;
import edu.washington.gs.maccoss.encyclopedia.utils.massspec.Peak;
import edu.washington.gs.maccoss.encyclopedia.utils.massspec.PeptideUtils;
import edu.washington.gs.maccoss.encyclopedia.utils.math.BackgroundSubtractionFilter;
import edu.washington.gs.maccoss.encyclopedia.utils.math.Correlation;
import edu.washington.gs.maccoss.encyclopedia.utils.math.FloatPair;
import edu.washington.gs.maccoss.encyclopedia.utils.math.General;
import edu.washington.gs.maccoss.encyclopedia.utils.math.Log;
import edu.washington.gs.maccoss.encyclopedia.utils.math.QuickMedian;
import edu.washington.gs.maccoss.encyclopedia.utils.math.ScoredIndex;
import edu.washington.gs.maccoss.encyclopedia.utils.math.SkylineSGFilter;
import edu.washington.gs.maccoss.encyclopedia.utils.math.distributions.CosineGaussian;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.map.hash.TFloatFloatHashMap;
import gnu.trove.set.hash.TIntHashSet;

public class EncyclopediaTwoPointOneScoringTask extends AbstractLibraryScoringTask {
	private final float dutyCycle;
	private final Range precursorIsolationRange;
	
	/**
	 * NOTE: Entries now compete (target/decoy competition). Make sure all entries are appropriate to compete against each other!
	 * @param scorer
	 * @param entries
	 * @param stripes
	 * @param precursorIsolationRange
	 * @param dutyCycle
	 * @param precursors
	 * @param resultsQueue
	 * @param parameters
	 */
	public EncyclopediaTwoPointOneScoringTask(PSMScorer scorer, ArrayList<LibraryEntry> entries, ArrayList<FragmentScan> stripes, Range precursorIsolationRange, float dutyCycle, PrecursorScanMap precursors, BlockingQueue<AbstractScoringResult> resultsQueue,
			SearchParameters parameters) {
		super(scorer, entries, stripes, precursors, resultsQueue, parameters);
		this.dutyCycle=dutyCycle;
		this.precursorIsolationRange=new Range(precursorIsolationRange.getStart(), precursorIsolationRange.getStop());
	}
	
	private static final int peaksKept=12;
	private static final int peaksConsidered=15;

	@Override
	protected Nothing process() {
		if (super.stripes.size()==0) return Nothing.NOTHING;
		TDCPeptideScoringResult result=new TDCPeptideScoringResult();
		
		MassTolerance libraryTolerance=parameters.getLibraryFragmentTolerance();
		EncyclopediaScorer eScorer=(EncyclopediaScorer)scorer;
		int movingAverageLength=Math.round(parameters.getExpectedPeakWidth()/dutyCycle);
		for (LibraryEntry entry : super.entries) {
			
			if (parameters.getTopNTargetsUsed()>0) {
				entry=entry.trimToNPeaks(parameters.getTopNTargetsUsed(), parameters.getAAConstants());
			}
			
			AuxillaryPSMScorer auxScorer=eScorer.getAuxScorer().getEntryOptimizedScorer(entry);
			FragmentationModel model=PeptideUtils.getPeptideModel(entry.getPeptideModSeq(), parameters.getAAConstants());
			FragmentIon[] ions=model.getPrimaryIonObjects(parameters.getFragType(), entry.getPrecursorCharge(), true);
			Optional<FragmentIon[]> modificationSpecificIons;
			if (parameters.isVerifyModificationIons()) {
				modificationSpecificIons=model.getModificationSpecificIonObjects(precursorIsolationRange, parameters.getFragType(), entry.getPrecursorCharge(), true);
				if (modificationSpecificIons.isPresent()) {
					FragmentIon[] modIons=modificationSpecificIons.get();
					Arrays.sort(modIons);
					modificationSpecificIons=Optional.of(modIons);
				}
			} else {
				modificationSpecificIons=Optional.empty();
			}
			
			ions=FragmentIon.getUniqueFragments(ions, parameters.getFragmentTolerance()); // ensure that all ions are unique within tolerance

			double[] predictedMasses=entry.getMassArray();
			float[] predictedIntensities=entry.getIntensityArray();
			float[] correlation=entry.getCorrelationArray();
			ArrayList<FragmentIon> predictedTargets=new ArrayList<FragmentIon>();
			TFloatArrayList predictedTargetIntensities=new TFloatArrayList();
			TFloatArrayList maxCorrelations=new TFloatArrayList();

			ArrayList<FragmentIon> predictedModTargets=new ArrayList<FragmentIon>();
			TFloatArrayList predictedModTargetIntensities=new TFloatArrayList();
			TFloatArrayList maxModCorrelations=new TFloatArrayList();
			
			for (FragmentIon target : ions) {
				int[] predictedIndicies=libraryTolerance.getIndices(predictedMasses, target.getMass());
				float predictedIntensity=0.0f;
				float maxCorrelation=0.01f;
				for (int i=0; i<predictedIndicies.length; i++) {
					if (predictedIntensity<predictedIntensities[predictedIndicies[i]]) {
						predictedIntensity=predictedIntensities[predictedIndicies[i]];
					}
					if (maxCorrelation<correlation[predictedIndicies[i]]) {
						maxCorrelation=correlation[predictedIndicies[i]];
					}
				}
				
				if (predictedIntensity>0) {
					maxCorrelations.add(maxCorrelation);
					predictedTargetIntensities.add(predictedIntensity);
					predictedTargets.add(target);
					
					if (modificationSpecificIons.isPresent()) {
						if (Arrays.binarySearch(modificationSpecificIons.get(), target)>=0) {
							// mod ion
							maxModCorrelations.add(maxCorrelation);
							predictedModTargetIntensities.add(predictedIntensity);
							predictedModTargets.add(target);
						}
					}
				}
			}
			if (predictedTargets.size()==0) continue;
			
			// limit to just measured in library
			ions=predictedTargets.toArray(new FragmentIon[0]);
			predictedIntensities=predictedTargetIntensities.toArray();
			correlation=maxCorrelations.toArray();
			FragmentIon[] modIons=predictedModTargets.toArray(new FragmentIon[0]);
			float[] modPredictedIntensities=predictedModTargetIntensities.toArray();
			float[] modCorrelation=maxModCorrelations.toArray();
			double[] targetMasses=new double[ions.length];
			
			for (int i = 0; i < targetMasses.length; i++) {
				targetMasses[i]=ions[i].getMass();
			}
			
			float[] predictedIsotopeDistribution=IsotopicDistributionCalculator.getIsotopeDistribution(entry.getPeptideModSeq(), parameters.getAAConstants());
			predictedIsotopeDistribution=IsotopicDistributionCalculator.normalizeToMax(predictedIsotopeDistribution);
			
			// extract chromatograms
			double[][] allMasses=new double[super.stripes.size()][];
			float[][] allSqrtIntensities=new float[super.stripes.size()][];
			float[] retentionTimes=new float[super.stripes.size()];
			for (int i=0; i<super.stripes.size(); i++) {
				FragmentScan stripe=super.stripes.get(i);
				
				Pair<double[], float[]> results=extract(stripe, ions);
				double[] masses=results.x;
				float[] intensities=results.y;
				
				allMasses[i]=masses;
				allSqrtIntensities[i]=intensities;
				retentionTimes[i]=super.stripes.get(i).getScanStartTime();
			}
			
			float[][] transposeChromatograms=General.transposeMatrix(allSqrtIntensities);
			float[][] transposeNormalizedChromatograms=new float[transposeChromatograms.length][];
			
//			ArrayList<XYTrace> traces=new ArrayList<XYTrace>();
			for (int i = 0; i < transposeChromatograms.length; i++) {
				if (parameters.isSmoothIntegrations()) {
					transposeChromatograms[i]=SkylineSGFilter.paddedSavitzkyGolaySmooth(transposeChromatograms[i]);
				}
				if (parameters.isSubtractBackground()) {
					transposeChromatograms[i]=BackgroundSubtractionFilter.backgroundSubtractMovingMedian(transposeChromatograms[i], movingAverageLength*10);
				}
				
				// pad moving normalizer by 50%
				float[] movingAverageSum=BackgroundSubtractionFilter.movingCenteredSum(transposeChromatograms[i], Math.round(movingAverageLength*1.5f));

				transposeNormalizedChromatograms[i]=new float[transposeChromatograms[i].length];
				for (int j = 0; j < transposeNormalizedChromatograms[i].length; j++) {
					if (movingAverageSum[j]>0) {
						transposeNormalizedChromatograms[i][j]=transposeChromatograms[i][j]/movingAverageSum[j];
					}
				}
//				Color c = RandomGenerator.randomColor(i);
//				traces.add(new XYTrace(General.divide(retentionTimes, 60f), transposeNormalizedChromatograms[i], GraphType.line, getTaskName(), new Color(c.getRed(), c.getGreen(), c.getBlue(), 122), 2f));
			}
			allSqrtIntensities=General.transposeMatrix(transposeChromatograms);
			float[][] allNormalizedIntensities=General.transposeMatrix(transposeNormalizedChromatograms);
			
			int targetTransitions=Math.max(parameters.getMinNumOfQuantitativePeaks(), (int)Math.ceil(parameters.getNumberOfQuantitativePeaks()/2.0));
			float[] medianChromatogram=new float[super.stripes.size()];
			for (int i = 0; i < medianChromatogram.length; i++) {
				TFloatArrayList nonZeros=new TFloatArrayList();
				for (int j = 0; j < allNormalizedIntensities[i].length; j++) {
					if (allNormalizedIntensities[i][j]>0) {
						nonZeros.add(allNormalizedIntensities[i][j]);
					}
				}
				
				if (targetTransitions>nonZeros.size()) {
					medianChromatogram[i]=0;
				} else {
					float targetFraction=1.0f-targetTransitions/(float)nonZeros.size(); // get closest to max
					medianChromatogram[i]=QuickMedian.select(nonZeros.toArray(), targetFraction);
				}
			}

			float[] correlationSumAcrossTime=new float[medianChromatogram.length];
			float[] numberOfHighlyCorrelatedFragments=new float[medianChromatogram.length];
			for (int i = 0; i < transposeChromatograms.length; i++) {
				float[] correlationAcrossTime=new float[transposeChromatograms[i].length];
				for (int j = 0; j < transposeChromatograms[i].length-movingAverageLength; j++) {
					IntRange range=new IntRange(j, j+movingAverageLength);
					
					int center=j+movingAverageLength/2;
					
					if (medianChromatogram[center]>medianChromatogram[range.getStart()]&&medianChromatogram[center]>medianChromatogram[range.getStop()]) {
						correlationAcrossTime[center]=TransitionRefiner.calculateCorrelation(range, medianChromatogram, transposeChromatograms[i]);
						if (Float.isNaN(correlationAcrossTime[center])) {
							correlationAcrossTime[center]=0.0f;
						}
					}
					if (correlationAcrossTime[center]>TransitionRefiner.quantitativeCorrelationThreshold) {
						numberOfHighlyCorrelatedFragments[center]+=1.0f;
					} else if (correlationAcrossTime[center]>TransitionRefiner.identificationCorrelationThreshold) {
						numberOfHighlyCorrelatedFragments[center]+=0.1f;
					} else if (correlationAcrossTime[center]>TransitionRefiner.weakIdentificationCorrelationThreshold) {
						numberOfHighlyCorrelatedFragments[center]+=0.01f;
					} else if (correlationAcrossTime[center]>0.0f) {
						numberOfHighlyCorrelatedFragments[center]+=0.001f;
					}
				}
				correlationSumAcrossTime=General.add(correlationSumAcrossTime, correlationAcrossTime);
			}
			correlationSumAcrossTime=BackgroundSubtractionFilter.fastMovingCenteredAverage(correlationSumAcrossTime, movingAverageLength/2);
			correlationSumAcrossTime=General.divide(correlationSumAcrossTime, (float)targetTransitions);
			
			// score time points
			float[] primary=new float[super.stripes.size()];
			for (int i=0; i<super.stripes.size(); i++) {
				FragmentScan stripe=super.stripes.get(i);
				FloatPair scores=score(allMasses[i], allSqrtIntensities[i], predictedIntensities, correlation);
				
				primary[i]=scores.getTwo();
				
				if (primary[i] > 0.0f && modificationSpecificIons.isPresent()) {
					// if modified signal represents less than 25% of the score then don't trust it
					Pair<double[], float[]> modSpecificResults=extract(stripe, modIons);
					FloatPair modSpecificScores=score(modSpecificResults.x, modSpecificResults.y, modPredictedIntensities, modCorrelation);
					
					float scoreFromModIons=modSpecificScores.getTwo();
					if (scoreFromModIons/primary[i]<0.25f) {
						primary[i]=0.0f;
					}
				}
			}
			
			if (parameters.getInstrument().hasLowresFragments()) {
				// additional tuning for higher-noise instruments
				primary=General.multiply(General.multiply(primary, correlationSumAcrossTime), numberOfHighlyCorrelatedFragments);
			} else {
				primary=General.multiply(primary, correlationSumAcrossTime);
			}

//			traces.add(new XYTrace(General.divide(retentionTimes, 60f), correlationSumAcrossTime, GraphType.boldline, getTaskName(), Color.black, 4f));
//			traces.add(new XYTrace(General.divide(retentionTimes, 60f), primary, GraphType.boldline, getTaskName(), Color.red, 4f));
//			Charter.launchChart("time", "score", false, traces.toArray(new XYTrace[0]));

			TFloatFloatHashMap map=new TFloatFloatHashMap();
			ArrayList<ScoredIndex> goodStripes=new ArrayList<ScoredIndex>();
			for (int i=0; i<primary.length; i++) {
				goodStripes.add(new ScoredIndex(primary[i], i));
				map.put(i, primary[i]);
			}
			Collections.sort(goodStripes);

			EValueCalculator calculator=new EValueCalculator(map, 0f, 0.5f);

			float bestScore=-Float.MAX_VALUE;
			float[] bestAuxScores=null;
			FragmentScan bestStripe=null;
			
			TIntHashSet takenScans=new TIntHashSet();
			
			if (parameters.isSkipLibraryRetentionTime()) {
				//remove the expected retention time
				int index=Arrays.binarySearch(retentionTimes,entry.getScanStartTime());
				if (index<0) index=-(index+1);
				
				int lowerWindow=Math.max(0, index-2*movingAverageLength);
				int upperWindow=Math.min(super.stripes.size()-1, index+2*movingAverageLength);
				// block out a 40 scan window
				for (int j=lowerWindow; j<=upperWindow; j++) {
					takenScans.add(j);
				}
			}
			
			int identifiedPeaks=0;
			int consideredPeaks=0;
			for (int i=goodStripes.size()-1; i>=0; i--) {
				float score=goodStripes.get(i).x;
				int index=goodStripes.get(i).y;
				if (takenScans.contains(index)) {
					continue;
					
				} else {	
					FragmentScan stripe=super.stripes.get(index);
					int lowerWindow=Math.max(0, index-2*movingAverageLength);
					int upperWindow=Math.min(super.stripes.size()-1, index+2*movingAverageLength);
					int range=Math.max(1, upperWindow-lowerWindow);
					float[] localRetentionTimes=new float[range];
					float[][] localRawIntensities=new float[range][]; // raw intensities, not sqrt
					System.arraycopy(retentionTimes, lowerWindow, localRetentionTimes, 0, range);
					System.arraycopy(allSqrtIntensities, lowerWindow, localRawIntensities, 0, range);
					for (int j = 0; j < localRawIntensities.length; j++) {
						if (EncyclopediaTwo.useSqrt) {
							localRawIntensities[j]=General.multiply(localRawIntensities[j], localRawIntensities[j]); // undo the sqrt
						}
					}
					
					float[][] chromatograms=General.transposeMatrix(localRawIntensities);
					ArrayList<float[]> chromatogramList=new ArrayList<float[]>();
					for (int j = 0; j < chromatograms.length; j++) {
						if (General.sum(chromatograms[j])>0.0f) {
							if (parameters.isSmoothIntegrations()) {
								chromatograms[j]=SkylineSGFilter.paddedSavitzkyGolaySmooth(chromatograms[j]);
							}
							if (parameters.isSubtractBackground()) {
								chromatograms[j]=BackgroundSubtractionFilter.backgroundSubtractMovingMedian(chromatograms[j], movingAverageLength*10);
							}
							chromatogramList.add(chromatograms[j]);
						}
					}
					
					TransitionRefinementData data=TransitionRefiner.identifyTransitions(entry.getPeptideModSeq(), entry.getPrecursorCharge(), stripe.getScanStartTime(), 
							ions, chromatogramList, localRetentionTimes, false, parameters);
					float[] correlations=data.getCorrelationArray();
					float[] integrations=data.getIntegrationArray();
					float[] median=data.getMedianChromatogram();
					Range rtRange=data.getRange();

//					System.out.println(stripe.getScanStartTime()/60+" --> "+rtRange.getStart()/60f+"\t"+rtRange.getStop()/60f);
//					HashMap<String, ChartPanel> panels=TransitionRefiner.getChartPanels(data);
//					Charter.launchCharts("TITLE", panels);
//					Charter.launchChart("RT (Min)", "Score", true, new XYTrace(General.divide(retentionTimes, 60f), primary, GraphType.line, "Primary"));					
//					Charter.launchChart("RT (Min)", "Intensity", true, new XYTrace(General.divide(localRetentionTimes, 60f), median, GraphType.line, "Median"));
//					try {
//						Thread.sleep(1000000);
//					} catch (Exception e) {}
					
					// 4 stdevs cover the range of 95% of the peak, assume peaks can't be smaller than 67% of the expected peak width
					float stdev = Math.max(parameters.getExpectedPeakWidth()/6.0f, rtRange.getRange()/4.0f);
					CosineGaussian gaussian=new CosineGaussian(stripe.getScanStartTime(), stdev, 1.0f);
					float[] idealGaussian=new float[median.length];
					float[] precursor=new float[median.length];
					float[] precursorPlusOne=new float[median.length];
					float sumMedianInRange=0;
					float maximumRT=-1.0f;
					float maximumMedian=0.0f;
					for (int j = 0; j < idealGaussian.length; j++) {
						idealGaussian[j]=(float)gaussian.getPDF(localRetentionTimes[j]);
						if (rtRange.contains(localRetentionTimes[j])) {
							sumMedianInRange+=median[j];
							if (median[j]>maximumMedian) {
								maximumMedian=median[j];
								maximumRT=localRetentionTimes[j];
							}
							
							// zero outside peak
							Peak[] peaks=precursors.getIsotopePacket(entry.getPrecursorMZ(), localRetentionTimes[j], entry.getPrecursorCharge(), parameters.getPrecursorTolerance());
							precursor[j]=peaks[PrecursorScanMap.monoisotopicIndex].intensity;
							precursorPlusOne[j]=peaks[PrecursorScanMap.plusOneIndex].intensity;
						}
					}
					
					//Spectrum acquiredPeak=getSpectrum(allMasses[index], integrations, stripe.getScanStartTime(), stripe.getPrecursorMZ());
					
					float[] auxScoreArray=scorer.auxScore(entry, stripe, predictedIsotopeDistribution, precursors);
					
					// ADDITIONAL AUX SCORES (make sure to add titles)
					
					// main score evalue
					float evalue=calculator.getNegLnEValue(score);
					if (Float.isNaN(evalue)) {
						evalue=-1.0f;
					}
					
					// correlation scores
					int numPeaksWithGoodCorrelation=0;
					int numPeaksWithGreatCorrelation=0;
					for (float corr : correlations) {
						if (corr>=TransitionRefiner.identificationCorrelationThreshold) {
							numPeaksWithGoodCorrelation++;
						}

						if (corr>=TransitionRefiner.quantitativeCorrelationThreshold) {
							numPeaksWithGreatCorrelation++;
						}
					}
					
					int isIntegratedSignal=0;
					int isIntegratedPrecursor=0;
					float correlationToGaussian=0.0f;
					float correlationToPrecursor=0.0f;
					//float correlationToPlusOne=0.0f;
					if (sumMedianInRange>0) {
						if (numPeaksWithGreatCorrelation>0) {
							isIntegratedSignal=1;
						}
						if (General.sum(precursor)>0) {
							isIntegratedPrecursor=1;
						}
						correlationToGaussian=Correlation.getPearsons(idealGaussian, median);
						correlationToPrecursor=Correlation.getPearsons(precursor, median);
						//correlationToPlusOne=Correlation.getPearsons(precursorPlusOne, median);
					}
					
					float deltaPrecursorMass=auxScorer.getParentDeltaMassIndex()>=0?auxScoreArray[auxScorer.getParentDeltaMassIndex()]:0.0f;
					float deltaFragmentMass=auxScorer.getFragmentDeltaMassIndex()>=0?auxScoreArray[auxScorer.getFragmentDeltaMassIndex()]:0.0f;
					
					auxScoreArray=General.concatenate(new float[] {score}, new float[] {evalue, correlationToGaussian, 
							correlationToPrecursor, isIntegratedSignal, isIntegratedPrecursor,
							numPeaksWithGoodCorrelation}, auxScoreArray); // auxScoreArray must be last
					
					// block out a 40 scan window
					for (int j=lowerWindow; j<=upperWindow; j++) {
						takenScans.add(j);
					}

					if (parameters.isSkipLibraryRetentionTime()&&maximumRT>=0.0f) {
						// too close to the expected retention time
						if (Math.abs(entry.getScanStartTime()-maximumRT)<parameters.getExpectedPeakWidth()) {
							continue;
						}
					}
					
					if (bestStripe==null || score > bestScore) {
						bestStripe=stripe;
						bestAuxScores=auxScoreArray;
						bestScore=score;
					}

					if (consideredPeaks>peaksConsidered) {
						break;
					}
					consideredPeaks++;
					
					// don't add if we don't have enough quantitative ions (add below if it's still the best and there are no matches)
					if (numPeaksWithGoodCorrelation<parameters.getMinNumOfQuantitativePeaks()) continue;
					
					
					if (scorer instanceof EncyclopediaTwoLDAScorer) {
						Optional<Pair<MProphetResult, TargeteDecoyPSMFilter>> optScorer=((EncyclopediaTwoLDAScorer) scorer).getLDAScorer();
						if (optScorer.isPresent()) {
							int[] scoreIndexConvertingArray=((EncyclopediaTwoLDAScorer) scorer).getScoreIndexConvertingArray();
							float[] ldaFeatures=new float[scoreIndexConvertingArray.length];
							for (int j = 0; j < ldaFeatures.length; j++) {
								if (scoreIndexConvertingArray[j]>=0) {
									// zero out features we can't carry forward
									ldaFeatures[j]=auxScoreArray[scoreIndexConvertingArray[j]];
								}
							}
							score=optScorer.get().getX().getLDA().getScore(ldaFeatures);
						}
						auxScoreArray[0]=score; // update score with LDA
					}
					result.addStripe(entry, score, auxScoreArray, deltaPrecursorMass, deltaFragmentMass, stripe);
					if (identifiedPeaks>peaksKept) {
						// keep N+1 peaks
						break;
					}
					identifiedPeaks++;
				}
			}
			
//			if (identifiedPeaks==0) {
				// add the best data if we can't find any valid peaks
//				float deltaPrecursorMass=auxScorer.getParentDeltaMassIndex()>=0?bestAuxScores[auxScorer.getParentDeltaMassIndex()]:0.0f;
//				float deltaFragmentMass=auxScorer.getFragmentDeltaMassIndex()>=0?bestAuxScores[auxScorer.getFragmentDeltaMassIndex()]:0.0f;
//				if (entry.getPeptideModSeq().equals("VVDESDETENQEEKAK")||entry.getPeptideModSeq().equals("VAKEEQNETEDSEDVK")) {
//					System.out.println(">"+entry.getPeptideModSeq()+" --> "+(bestStripe.getScanStartTime()/60f)+", "+bestScore);	
//				}
//				result.addStripe(entry, bestScore, bestAuxScores, deltaPrecursorMass, deltaFragmentMass, bestStripe);
//			}
			
		}
		result.trim(1);
		resultsQueue.add(result);
		return Nothing.NOTHING;
	}

	public Pair<double[], float[]> extract(Spectrum spectrum, FragmentIon[] ions) {
		MassTolerance acquiredTolerance=parameters.getFragmentTolerance();
		
		double[] acquiredMasses=spectrum.getMassArray();
		float[] acquiredIntensities=spectrum.getIntensityArray();

		TDoubleArrayList actualTargetMasses=new TDoubleArrayList();
		TFloatArrayList actualTargetIntensities=new TFloatArrayList();
		for (int i = 0; i < ions.length; i++) {
			FragmentIon target=ions[i];
		
			int[] indicies=acquiredTolerance.getIndices(acquiredMasses, target.getMass());
			float intensity=0.0f;
			float bestPeakIntensity=0.0f;
			double bestPeakMass=0.0;
			
			for (int j=0; j<indicies.length; j++) {
				intensity+=acquiredIntensities[indicies[j]];
				
				if (acquiredIntensities[indicies[j]]>bestPeakIntensity) {
					bestPeakIntensity=acquiredIntensities[indicies[j]];
					bestPeakMass=acquiredMasses[indicies[j]];
				}
			}
			actualTargetIntensities.add(intensity);
			actualTargetMasses.add(bestPeakMass);
		}

		double[] actualTargetMassesRaw=actualTargetMasses.toArray();
		float[] actualTargetIntensitiesRaw=actualTargetIntensities.toArray();
		
		return new Pair<double[], float[]>(actualTargetMassesRaw, actualTargetIntensitiesRaw);
	}
	
	public FloatPair score(double[] actualTargetMassesRaw, float[] actualTargetIntensitiesRaw, float[] predictedTargetIntensities, float[] correlations) {

		float[] predictedTargetIntensitiesArray=General.normalizeToL2(predictedTargetIntensities);
		float[] actualTargetIntensitiesArray=General.normalizeToL2(actualTargetIntensitiesRaw);
		
		float dotProduct=General.sum(General.multiply(General.multiply(predictedTargetIntensitiesArray, actualTargetIntensitiesArray), correlations));

		if (Float.isNaN(dotProduct)||dotProduct<0.0f) dotProduct=0.0f;
		
		float sumOfSquaredErrors=0.0f; // normalized to sum of targeted intensities

		int numberOfMatchingPeaks=0;
		for (int i=0; i<predictedTargetIntensitiesArray.length; i++) {
			if (predictedTargetIntensitiesArray[i]>0.0) {
				float delta=predictedTargetIntensitiesArray[i]-actualTargetIntensitiesArray[i];
				float deltaSquared=delta*delta;
				sumOfSquaredErrors+=deltaSquared;
				if (actualTargetIntensitiesArray[i]>0.0) {
					numberOfMatchingPeaks++;
				}
			}
		}
		
		float xTandem;
		if (numberOfMatchingPeaks==0||dotProduct<=0) {
			xTandem=0.0f;
		} else {
			// really log10(X!Tandem score)
			// shifted +2 to capture part of the negative range while ensuring a non-negative score
			xTandem=((float)Log.protectedLog10(dotProduct))+Log.logFactorial(numberOfMatchingPeaks)+2f;
		}

		if (xTandem < 0.0f) {
			xTandem = 0.0f;
		}

		float scribe;
		if (sumOfSquaredErrors<=0.0f) {
			scribe=0.0f;
		} else {
			// shifted +1 to capture part of the negative range while ensuring a non-negative score
			scribe=Log.protectedLn(1.0f/sumOfSquaredErrors)+1f;
		}
		if (scribe < 0.0f) {
			scribe = 0.0f;
		}
		return new FloatPair(xTandem, scribe);
	}
}
