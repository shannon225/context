package org.searlelab.context.xic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import org.searlelab.context.encyclopedia.EncyclopediaTwoPointOneScoringTask;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.Range;
import edu.washington.gs.maccoss.encyclopedia.algorithms.*;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryEntry;
import edu.washington.gs.maccoss.encyclopedia.datastructures.PrecursorScanMap;
import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchParameters;
import edu.washington.gs.maccoss.encyclopedia.utils.Nothing;

/** Search, score only missing candidates, then emit one existing TDC result for the candidate set. */
public final class AssayScoringTask extends AbstractLibraryScoringTask {
	private final AssayCandidateTracker tracker;
	private final AssayCandidateTracker.Row row;
	private final Range isolationRange;
	private final float dutyCycle;
	private final Consumer<List<ScoredPSM>> diagnostics;

	public AssayScoringTask(PSMScorer scorer, ArrayList<LibraryEntry> entries, ArrayList<FragmentScan> stripes,
			Range isolationRange, float dutyCycle, PrecursorScanMap precursors,
			BlockingQueue<AbstractScoringResult> queue, SearchParameters parameters,
			AssayCandidateTracker tracker, AssayCandidateTracker.Row row, Consumer<List<ScoredPSM>> diagnostics) {
		super(scorer, entries, stripes, precursors, queue, parameters);
		this.tracker = tracker; this.row = row; this.isolationRange = isolationRange;
		this.dutyCycle = dutyCycle; this.diagnostics = diagnostics;
	}

	@Override protected Nothing process() {
		var candidates = new ArrayList<ScoredPSM>();
		score(entries, false, candidates);
		var scored = new HashSet<AssayCandidateTracker.CandidateKey>();
		for (ScoredPSM candidate : candidates) scored.add(AssayCandidateTracker.CandidateKey.of(candidate.getLibraryEntry()));
		var missing = new ArrayList<LibraryEntry>();
		for (LibraryEntry entry : entries) if (!scored.contains(AssayCandidateTracker.CandidateKey.of(entry))) missing.add(entry);
		if (!missing.isEmpty()) score(missing, true, candidates);
		// Intermediate task queues are private. Only the combined candidate set enters the output queue.
		var result = new TDCPeptideScoringResult();
		for (ScoredPSM candidate : candidates) result.addStripe(candidate.getLibraryEntry(), candidate.getPrimaryScore(),
				candidate.getAuxScores(), candidate.getDeltaPrecursorMass(), candidate.getDeltaFragmentMass(), candidate.getMSMS());
		if (diagnostics != null) diagnostics.accept(result.getGoodMSMSCandidates());
		result.trim(1);
		tracker.retained(row, result.getGoodMSMSCandidates());
		resultsQueue.add(result);
		return Nothing.NOTHING;
	}

	private void score(ArrayList<LibraryEntry> selected, boolean forced, ArrayList<ScoredPSM> candidates) {
		new EncyclopediaTwoPointOneScoringTask(scorer, selected, stripes, isolationRange, dutyCycle, precursors,
				new LinkedBlockingQueue<>(), parameters, observed -> {
					tracker.scored(row, observed, forced);
					candidates.addAll(observed);
				}, new Range(-Float.MAX_VALUE, Float.MAX_VALUE), forced).call();
	}
}
