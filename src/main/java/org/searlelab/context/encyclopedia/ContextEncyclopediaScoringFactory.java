package org.searlelab.context.encyclopedia;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Collections;
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
	private java.util.List<AssayCandidateTracker.Detection> detections;
	private edu.washington.gs.maccoss.encyclopedia.filereaders.LibraryInterface assaySupplement;
	private java.util.Map<String, String> referenceFailures = java.util.Map.of();

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
				if (stripes.isEmpty()) continue;
				if (!dispatchedRows.add(row.number())) continue;
				var window = row.window();
				var canonicalWindow = new org.searlelab.context.datastructures.IsolationWindow(row.correctedSequence(),
						window.getTargetMz(), window.getCharge(), window.getRtMin(), window.getRtMax(), window.isDecoy());
				if (org.searlelab.context.xic.AssayXicExtractor.referenceProblem(canonicalWindow, entries.get(0), parameters) != null) continue;
				// Use the same processing-range scans and precursor map as background scoring.
				tasks.add(new AssayScoringTask(scorer, entries, stripes, precursorIsolationRange, dutyCycle,
						precursors, resultsQueue, parameters, assays, row,
						diagnosticWriter == null ? null : diagnosticWriter::accept));
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

	/** Caller owns the supplement; it is consulted only for missing assay references. */
	public void setAssaySupplement(edu.washington.gs.maccoss.encyclopedia.filereaders.LibraryInterface supplement,
			java.util.Map<String, String> failures) {
		this.assaySupplement = supplement;
		this.referenceFailures = java.util.Map.copyOf(failures);
	}

	/** Finalize every original row, including precursors outside all searched library ranges. */
	public void completeAssays(edu.washington.gs.maccoss.encyclopedia.filereaders.LibraryInterface library,
			PSMScorer scorer, BlockingQueue<AbstractScoringResult> queue,
			java.util.function.Consumer<EncyclopediaTwo.SearchSelection> selections)
			throws java.io.IOException, java.sql.SQLException, java.util.zip.DataFormatException {
		if (assays == null) return;
		var completed = new ArrayList<AssayCandidateTracker.Detection>();
		PrecursorScanMap forcedPrecursors = null;
		var strategy = edu.washington.gs.maccoss.encyclopedia.algorithms.library.EncyclopediaTwoDecoyStrategy.resolve(library, parameters);

		for (var row : assays.rows()) {
			var window = row.window();
			var rowLibrary = library;
			var references = rowLibrary.getEntries(row.correctedSequence(), window.getCharge(), EncyclopediaTwo.useSqrt);
			if (references.isEmpty() && assaySupplement != null) {
				rowLibrary = assaySupplement;
				references = rowLibrary.getEntries(row.correctedSequence(), window.getCharge(), EncyclopediaTwo.useSqrt);
			}
			references.removeIf(entry -> entry.isDecoy() || !row.correctedSequence().equals(entry.getPeptideModSeq()));
			if (references.size() > 1) throw new java.io.IOException("Multiple prepared references for assay row " + row.number());
			LibraryEntry reference = references.isEmpty() ? null : references.get(0);
			var canonicalWindow = new org.searlelab.context.datastructures.IsolationWindow(row.correctedSequence(),
					window.getTargetMz(), window.getCharge(), window.getRtMin(), window.getRtMax(), window.isDecoy());
			String problem = org.searlelab.context.xic.AssayXicExtractor.referenceProblem(canonicalWindow, reference, parameters);
			if (reference == null) problem = referenceFailures.getOrDefault(row.correctedSequence() + "|" + window.getCharge(), problem);
			int scans = problem == null ? org.searlelab.context.xic.AssayXicExtractor.readFragments(raw, window).size() : 0;
			if (problem == null && scans > 0 && !dispatchedRows.contains(row.number())) {
				var range = raw.getRanges().keySet().stream()
						.filter(r -> r.contains((float) window.getTargetMz()) && Float.isFinite(raw.getRanges().get(r).getAverageDutyCycle())
								&& raw.getRanges().get(r).getAverageDutyCycle() > 0)
						.sorted().findFirst()
						.orElseThrow(() -> new java.io.IOException("No valid acquisition duty cycle for assay row " + row.number()));
				var decoys = new java.util.HashMap<edu.washington.gs.maccoss.encyclopedia.filereaders.MatchedDecoyKey, LibraryEntry>();
				if (strategy.usesPredictedDecoys()) {
					decoys = ((edu.washington.gs.maccoss.encyclopedia.filereaders.MatchedDecoyLibrary) rowLibrary).getMappedDecoyEntries(
							new org.searlelab.msrawjava.model.Range((float) reference.getPrecursorMZ() - 0.01f,
									(float) reference.getPrecursorMZ() + 0.01f), EncyclopediaTwo.useSqrt, parameters.getAAConstants());
				}
				var entries = EncyclopediaTwo.getTargetAndPrimaryDecoy(reference, strategy, decoys, parameters);
				selections.accept(new EncyclopediaTwo.SearchSelection(reference, java.util.List.copyOf(entries), strategy.getMode()));
				var stripes = raw.getStripes(range.getMiddle(), -Float.MAX_VALUE, Float.MAX_VALUE, EncyclopediaTwo.useSqrt);
				Collections.sort(stripes);
				if (forcedPrecursors == null) forcedPrecursors = new PrecursorScanMap(raw.getPrecursors(-Float.MAX_VALUE, Float.MAX_VALUE));
				getScoringTask(scorer, entries, stripes, range, raw.getRanges().get(range).getAverageDutyCycle(),
						forcedPrecursors, queue).call();
			}
			completed.add(assays.detection(row, problem, scans));
		}
		detections = java.util.List.copyOf(completed);
	}

	public java.util.List<AssayCandidateTracker.Detection> detections() {
		if (detections == null) throw new IllegalStateException("Assay processing has not completed");
		return detections;
	}

	public boolean hasAssays() { return assays != null; }
	public boolean isAssayTarget(LibraryEntry entry) { return assays != null && !assays.matching(entry).isEmpty(); }
}
