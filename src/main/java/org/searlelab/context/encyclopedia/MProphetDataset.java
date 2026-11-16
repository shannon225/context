package org.searlelab.context.encyclopedia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;


import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.MProphetData;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.ScoredMProphetData;
import edu.washington.gs.maccoss.encyclopedia.utils.Pair;
import edu.washington.gs.maccoss.encyclopedia.utils.math.BenjaminiHochberg;
import edu.washington.gs.maccoss.encyclopedia.utils.math.General;
import edu.washington.gs.maccoss.encyclopedia.utils.math.LinearDiscriminantAnalysis;
import edu.washington.gs.maccoss.encyclopedia.utils.math.LocalFDR;
import edu.washington.gs.maccoss.encyclopedia.utils.math.QuickMedian;
import edu.washington.gs.maccoss.encyclopedia.utils.math.RandomGenerator;
import edu.washington.gs.maccoss.encyclopedia.utils.math.ScoredObject;
import edu.washington.gs.maccoss.encyclopedia.utils.math.distributions.Gaussian;

import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TFloatArrayList;


/**
 * Unused as of 2026-10-31 (CTX018-016): MProphetReiter imports EncyclopeDIA's
 * {@code edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.MProphetDataset}, not this copy. Kept, not
 * deleted, at Ariana's request. Do not use it: its q-value code multiplies by pi0 twice (in getPassingTargetsByFDR).
 * Code in this package that names MProphetDataset without importing EncyclopeDIA's class gets this copy.
 */
public class MProphetDataset {
	private final int startingScoreIndex;
	private final ArrayList<String> featureNames;
	private final ArrayList<MProphetData> targetPeptideData;
	private final ArrayList<MProphetData> decoyPeptideData;
	
	public MProphetDataset(ArrayList<String> featureNames, int startingScoreIndex, ArrayList<MProphetData> peptideData) {
		this.featureNames = featureNames;
		this.startingScoreIndex=startingScoreIndex;
		targetPeptideData=new ArrayList<MProphetData>();
		decoyPeptideData=new ArrayList<MProphetData>();
		for (MProphetData mProphetData : peptideData) {
			if (mProphetData.isDecoy()) {
				decoyPeptideData.add(mProphetData);
			} else {
				targetPeptideData.add(mProphetData);
			}
		}
	}
	
	private MProphetDataset(ArrayList<String> featureNames, int startingScoreIndex, ArrayList<MProphetData> targetPeptideData,
			ArrayList<MProphetData> decoyPeptideData) {
		this.featureNames = featureNames;
		this.startingScoreIndex=startingScoreIndex;
		this.targetPeptideData = targetPeptideData;
		this.decoyPeptideData = decoyPeptideData;
	}

	public static MProphetDataset[] splitKFold(MProphetDataset dataset, int k, int randomSeed, int maximumFoldSize) {
		ArrayList<MProphetData>[] targetSplitData=splitKFold(dataset.targetPeptideData, k, randomSeed, maximumFoldSize);
		ArrayList<MProphetData>[] decoySplitData=splitKFold(dataset.decoyPeptideData, k, RandomGenerator.randomIntAlt(randomSeed), maximumFoldSize);
		
		MProphetDataset[] splitDatasets=new MProphetDataset[k];
		for (int i = 0; i < splitDatasets.length; i++) {
			splitDatasets[i]=new MProphetDataset(dataset.featureNames, dataset.startingScoreIndex, targetSplitData[i], decoySplitData[i]);
		}
		return splitDatasets;
	}
	
	private static ArrayList<MProphetData>[] splitKFold(ArrayList<MProphetData> dataset, int k, int randomSeed, int maximumFoldSize) {
		ArrayList<ScoredObject<MProphetData>> targetDataRandomized=new ArrayList<ScoredObject<MProphetData>>();
		for (MProphetData data : dataset) {
			randomSeed=RandomGenerator.randomInt(randomSeed);
			float random=RandomGenerator.floatFromRandomInt(randomSeed);
			targetDataRandomized.add(new ScoredObject<MProphetData>(random, data));
		}
		Collections.sort(targetDataRandomized);

		@SuppressWarnings("unchecked")
		ArrayList<MProphetData>[] splitData=new ArrayList[k];
		for (int i = 0; i < splitData.length; i++) {
			splitData[i]=new ArrayList<MProphetData>();
		}
		int currentK=0;
		for (ScoredObject<MProphetData> scoredObject : targetDataRandomized) {
			if (splitData[currentK].size()<maximumFoldSize) {
				splitData[currentK].add(scoredObject.y);
			}
			currentK = (currentK + 1) % k;
		}
		return splitData;
	}
	
	public static MProphetDataset combineFolds(MProphetDataset[] folds) {
		ArrayList<MProphetData> targets=new ArrayList<MProphetData>();
		ArrayList<MProphetData> decoys=new ArrayList<MProphetData>();
		
		for (int i = 0; i < folds.length; i++) {
			targets.addAll(folds[i].targetPeptideData);
			targets.addAll(folds[i].decoyPeptideData);
		}
		return new MProphetDataset(folds[0].featureNames, folds[0].startingScoreIndex, targets, decoys);
	}

	public Pair<ArrayList<ScoredMProphetData>, Float> getPassingTargetsByFDR(Optional<LinearDiscriminantAnalysis> optionalLDA, float targetFDR) {
		return getPassingTargetsByFDR(optionalLDA, targetFDR, false);
	}
	public Pair<ArrayList<ScoredMProphetData>, Float> getPassingTargetsByFDR(Optional<LinearDiscriminantAnalysis> optionalLDA, float targetFDR, boolean getDecoysInstead) {
		// choose the right dataset to work with
		ArrayList<MProphetData> dataset=targetPeptideData;
		if (getDecoysInstead) {
			dataset=decoyPeptideData;
		}
		
		TFloatArrayList targetScores = getScoresForDataset(optionalLDA, dataset);

		// calculate p-values for the scores
		Gaussian nullDistribution = getDecoyDistribution(optionalLDA);
		TDoubleArrayList targetPValues=new TDoubleArrayList();
		for (int i = 0; i < targetScores.size(); i++) {
			float score=targetScores.get(i);
			double pvalue=nullDistribution.getComplementaryCDF(score);
			if (Double.isNaN(pvalue)) {
				targetPValues.add(1.0);
			} else {
				targetPValues.add(pvalue);
			}
		}
		
		// calculate FDRs using B-H approach
		double[] pValueArray = targetPValues.toArray();
		double minimumPi0 = 0.05;
		double pi0=Math.max(minimumPi0, (LocalFDR.estimatePi0(pValueArray)));
		double[] targetFDRValues=BenjaminiHochberg.calculateAdjustedPValues(pValueArray);
		double[] targetLFDRValues=LocalFDR.estimateLocalFDR(pValueArray);
		
		// Use pi0 to update FDR for a Storey-style q-value calculation
		for (int i = 0; i < targetFDRValues.length; i++) {
			targetFDRValues[i] = Math.min(1.0, pi0*targetFDRValues[i]);
		}
		
		ArrayList<ScoredMProphetData> returnedData=new ArrayList<ScoredMProphetData>();
		for (int i = 0; i < targetFDRValues.length; i++) {
			targetFDRValues[i] = Math.min(1.0, pi0*targetFDRValues[i]);
				ScoredMProphetData data=new ScoredMProphetData(dataset.get(i), 
						targetScores.get(i), pValueArray[i], targetLFDRValues[i], targetFDRValues[i]);
				returnedData.add(data);
				//System.out.println(data.getData().getSequence()+" --> "+data.getScore()+", "+data.getPvalue()+", "+data.getFDR()+", "+data.getLocalFDR());
			}			
		
		return new Pair<ArrayList<ScoredMProphetData>, Float>(returnedData, (float)pi0);
	}
	
	public ArrayList<ScoredMProphetData> getPassingTargetsByPercentage(Optional<LinearDiscriminantAnalysis> optionalLDA, float topNPercentage) {
		ArrayList<MProphetData> dataset=targetPeptideData;
		TFloatArrayList targetScores = getScoresForDataset(optionalLDA, dataset);
		
		float[] scoreArray = targetScores.toArray();
		float minimumScore=QuickMedian.select(scoreArray.clone(), 1.0f-topNPercentage);

		ArrayList<ScoredMProphetData> returnedData=new ArrayList<ScoredMProphetData>();
		for (int i = 0; i < scoreArray.length; i++) {
			if (scoreArray[i]>=minimumScore) {
				ScoredMProphetData data=new ScoredMProphetData(dataset.get(i), targetScores.get(i), 0.0f, 0.0f, 0.0f);
				returnedData.add(data);
			}			
		}
		return returnedData;
	}

	private TFloatArrayList getScoresForDataset(Optional<LinearDiscriminantAnalysis> optionalLDA, ArrayList<MProphetData> dataset) {
		// calculate the scores for each entry in the dataset
		TFloatArrayList targetScores=new TFloatArrayList();
		for (MProphetData mProphetData : dataset) {
			float score;
			if (optionalLDA.isPresent()) {
				score=optionalLDA.get().getScore(mProphetData.getData());
			} else {
				score=mProphetData.getData()[startingScoreIndex];
			}
			targetScores.add(score);
		}
		return targetScores;
	}

	private Gaussian getDecoyDistribution(Optional<LinearDiscriminantAnalysis> optionalLDA) {
		TFloatArrayList decoyScores=new TFloatArrayList();
		for (MProphetData mProphetData : decoyPeptideData) {
			float score;
			if (optionalLDA.isPresent()) {
				score=optionalLDA.get().getScore(mProphetData.getData());
			} else {
				score=mProphetData.getData()[startingScoreIndex];
			}
			decoyScores.add(score);
		}
		
		float[] nullScoreArray=decoyScores.toArray();
		
		Gaussian nullDistribution=new Gaussian(General.mean(nullScoreArray), General.stdev(nullScoreArray), 1.0f);
		return nullDistribution;
	}
	
	public ArrayList<MProphetData> allData() {
		ArrayList<MProphetData> dataset=new ArrayList<>();
		dataset.addAll(targetPeptideData);
		dataset.addAll(decoyPeptideData);
		return dataset;
	}
	
	public ArrayList<float[]> getTargetData() {
		return getData(targetPeptideData);
	}
	
	public ArrayList<float[]> getDecoyData() {
		return getData(decoyPeptideData);
	}
	public ArrayList<String> getFeatureNames() {
		return featureNames;
	}
	public int getStartingScoreIndex() {
		return startingScoreIndex;
	}
	
	public static ArrayList<float[]> getData(ArrayList<MProphetData> dataset) {
		ArrayList<float[]> data=new ArrayList<float[]>();
		for (MProphetData mProphetData : dataset) {
			data.add(mProphetData.getData());
		}
		return data;
	}
	
	public static ArrayList<float[]> getScoredData(ArrayList<ScoredMProphetData> dataset) {
		ArrayList<float[]> data=new ArrayList<float[]>();
		for (ScoredMProphetData mProphetData : dataset) {
			data.add(mProphetData.getData().getData());
		}
		return data;
	}
	
}
