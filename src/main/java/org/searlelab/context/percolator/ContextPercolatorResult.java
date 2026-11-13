package org.searlelab.context.percolator;

import java.io.File;

public class ContextPercolatorResult {
	private final PercolatorWeights model;
	private final File nativeWeightsFile;
	private final File averagedWeightsFile;
	private final File rescoredFeaturesFile;
	private final File psmOutputFile;
	private final File peptideOutputFile;
	private final File rawPsmReportFile;
	private final File rawPeptideReportFile;
	private final int referencePSMCount;
	private final int referencePeptideCount;
	private final int passingPeptideCount;
	private final int pepPassingPeptideCount;
	private final float fdrThreshold;

	ContextPercolatorResult(PercolatorWeights model, File nativeWeightsFile, File averagedWeightsFile,
			File rescoredFeaturesFile, File psmOutputFile, File peptideOutputFile, File rawPsmReportFile,
			File rawPeptideReportFile, int referencePSMCount, int referencePeptideCount, int passingPeptideCount,
			int pepPassingPeptideCount, float fdrThreshold) {
		this.model = model;
		this.nativeWeightsFile = nativeWeightsFile;
		this.averagedWeightsFile = averagedWeightsFile;
		this.rescoredFeaturesFile = rescoredFeaturesFile;
		this.psmOutputFile = psmOutputFile;
		this.peptideOutputFile = peptideOutputFile;
		this.rawPsmReportFile = rawPsmReportFile;
		this.rawPeptideReportFile = rawPeptideReportFile;
		this.referencePSMCount = referencePSMCount;
		this.referencePeptideCount = referencePeptideCount;
		this.passingPeptideCount = passingPeptideCount;
		this.pepPassingPeptideCount = pepPassingPeptideCount;
		this.fdrThreshold = fdrThreshold;
	}

	public PercolatorWeights getModel() {
		return model;
	}

	public File getNativeWeightsFile() {
		return nativeWeightsFile;
	}

	public File getAveragedWeightsFile() {
		return averagedWeightsFile;
	}

	public File getRescoredFeaturesFile() {
		return rescoredFeaturesFile;
	}

	public File getPsmOutputFile() {
		return psmOutputFile;
	}

	public File getPeptideOutputFile() {
		return peptideOutputFile;
	}

	public File getRawPsmReportFile() {
		return rawPsmReportFile;
	}

	public File getRawPeptideReportFile() {
		return rawPeptideReportFile;
	}

	public int getReferencePSMCount() {
		return referencePSMCount;
	}

	public int getReferencePeptideCount() {
		return referencePeptideCount;
	}

	/** Reference target peptides with q-value at or below the FDR threshold. */
	public int getPassingPeptideCount() {
		return passingPeptideCount;
	}

	/** Reference target peptides with PEP at or below the same threshold. */
	public int getPEPPassingPeptideCount() {
		return pepPassingPeptideCount;
	}

	public float getFdrThreshold() {
		return fdrThreshold;
	}

	@Override
	public String toString() {
		return "ContextPercolatorResult[" + referencePeptideCount + " reference peptides; " + passingPeptideCount
				+ " at q <= " + fdrThreshold + ", " + pepPassingPeptideCount + " at PEP <= " + fdrThreshold + "]";
	}
}
