package org.searlelab.context.encyclopedia;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Comparator;
import org.searlelab.context.xic.AssayCandidateTracker;
import org.searlelab.context.xic.AssayScoringTask;
import org.searlelab.msrawjava.io.StripeFileInterface;
import edu.washington.gs.maccoss.encyclopedia.utils.Nothing;
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
	private final CandidateDiagnosticWriter diagnosticWriter;
	private final AssayCandidateTracker assays;
	private final StripeFileInterface raw;
	private final HashSet<Integer> dispatchedRows = new HashSet<>();

	public ContextEncyclopediaScoringFactory(SearchParameters parameters) {
		this(parameters, null);
	}

	public ContextEncyclopediaScoringFactory(SearchParameters parameters, CandidateDiagnosticWriter diagnosticWriter) {
		this(parameters, diagnosticWriter, null, null);
	}

	public ContextEncyclopediaScoringFactory(SearchParameters parameters, CandidateDiagnosticWriter diagnosticWriter,
			AssayCandidateTracker assays, StripeFileInterface raw) {
		super(parameters);
		this.parameters = parameters;
		this.diagnosticWriter = diagnosticWriter;
		this.assays = assays;
		this.raw = raw;
		if (assays != null && raw == null) throw new IllegalArgumentException("Assay scoring requires raw scan access");
	}
	
	@Override
	public AbstractLibraryScoringTask getScoringTask(PSMScorer scorer, ArrayList<LibraryEntry> entries, ArrayList<FragmentScan> stripes, org.searlelab.msrawjava.model.Range precursorIsolationRange, float dutyCycle, PrecursorScanMap precursors, BlockingQueue<AbstractScoringResult> resultsQueue) {
		
		if (assays != null && !entries.isEmpty() && isAssayTarget(entries.get(0))) {
			var tasks = new ArrayList<AbstractLibraryScoringTask>();
			for (var row : assays.matching(entries.get(0))) {
				if (!dispatchedRows.add(row.number())) continue;
				try {
					var window = row.window();
					var allScans = raw.getStripes(window.getTargetMz(), -Float.MAX_VALUE, Float.MAX_VALUE, EncyclopediaTwo.useSqrt);
					allScans.removeIf(scan -> !scan.getPrecursorRange().contains((float) window.getTargetMz()));
					allScans.sort(Comparator.comparingDouble(FragmentScan::getScanStartTime).thenComparingInt(FragmentScan::getSpectrumIndex));
					var localPrecursors = raw.getPrecursors(window.getRtMin(), window.getRtMax());
					localPrecursors.removeIf(scan -> scan.getScanStartTime() < window.getRtMin() || scan.getScanStartTime() > window.getRtMax());
					tasks.add(new AssayScoringTask(scorer, entries, allScans, precursorIsolationRange, dutyCycle,
							new PrecursorScanMap(localPrecursors), resultsQueue, parameters, assays, row,
							diagnosticWriter == null ? null : diagnosticWriter::accept));
				} catch (java.io.IOException | java.sql.SQLException | java.util.zip.DataFormatException e) {
					throw new IllegalStateException("Cannot read assay scans at row " + row.number(), e);
				}
			}
			return new AbstractLibraryScoringTask(scorer, entries, stripes, precursors, resultsQueue, parameters) {
				@Override protected Nothing process() {
					for (var task : tasks) task.call();
					return Nothing.NOTHING;
				}
			};
		}
		return new org.searlelab.context.encyclopedia.EncyclopediaTwoPointOneScoringTask(scorer, entries, stripes,
				precursorIsolationRange, dutyCycle, precursors, resultsQueue, parameters,
				diagnosticWriter == null ? null : diagnosticWriter::accept);
	}

	public boolean hasAssays() { return assays != null; }
	public boolean isAssayTarget(LibraryEntry entry) { return assays != null && !assays.matching(entry).isEmpty(); }
}
