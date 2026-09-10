package org.searlelab.context.percolator;

import java.io.File;

import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.PercolatorExecutionData;
import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchParameters;

// Context wrapper for Encyclopedia's PercolatorExecutionData
public final class ContextPercolatorExecutionData extends PercolatorExecutionData{
	
	public ContextPercolatorExecutionData(File input, File fasta, File targets, File decoys, File proteinTargets, File proteinDecoys, SearchParameters parameters) {
		super(input, fasta, targets, decoys, proteinTargets, proteinDecoys, parameters);	
}

	@Override 
	public void setPercolatorExecutableVersion(String version) {
		super.setPercolatorExecutableVersion(version);
	}
}
