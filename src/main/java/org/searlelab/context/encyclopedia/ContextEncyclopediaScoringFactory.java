package org.searlelab.context.encyclopedia;

import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;

import org.searlelab.msrawjava.model.FragmentScan;

import edu.washington.gs.maccoss.encyclopedia.algorithms.AbstractLibraryScoringTask;
import edu.washington.gs.maccoss.encyclopedia.algorithms.AbstractScoringResult;
import edu.washington.gs.maccoss.encyclopedia.algorithms.PSMScorer;
import edu.washington.gs.maccoss.encyclopedia.algorithms.library.EncyclopediaTwoScoringFactory;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryEntry;
import edu.washington.gs.maccoss.encyclopedia.datastructures.PrecursorScanMap;
import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchParameters;

public class ContextEncyclopediaScoringFactory extends EncyclopediaTwoScoringFactory {
	
	private final SearchParameters parameters;

	public ContextEncyclopediaScoringFactory(SearchParameters parameters) {
		super(parameters);
		this.parameters = parameters;
	}
	
	@Override
	public AbstractLibraryScoringTask getScoringTask(PSMScorer scorer, ArrayList<LibraryEntry> entries, ArrayList<FragmentScan> stripes, org.searlelab.msrawjava.model.Range precursorIsolationRange, float dutyCycle, PrecursorScanMap precursors, BlockingQueue<AbstractScoringResult> resultsQueue) {
		
		return new org.searlelab.context.encyclopedia.EncyclopediaTwoPointOneScoringTask(scorer, entries, stripes, precursorIsolationRange, dutyCycle, precursors, resultsQueue, parameters);
	}

}
