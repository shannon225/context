package org.searlelab.context.xic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import edu.washington.gs.maccoss.encyclopedia.algorithms.ScoredPSM;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryEntry;
import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchParameters;
import edu.washington.gs.maccoss.encyclopedia.utils.massspec.PeptideUtils;
import org.searlelab.context.datastructures.IsolationWindow;

/** Bounded, per-assay evidence independent of optional candidate TSV diagnostics. */
public final class AssayCandidateTracker {
	public record CandidateKey(String sequence, byte charge, boolean searchDecoy) {
		public static CandidateKey of(LibraryEntry entry) {
			return new CandidateKey(entry.getPeptideModSeq(), entry.getPrecursorCharge(), entry.isDecoy());
		}
	}
	public record Row(int number, IsolationWindow window, String correctedSequence) {}
	public record Evidence(boolean searched, boolean forced, boolean retained) {
		public boolean scored() { return searched || forced; }
	}
	public record Detection(String status, String reason) {}
	private final List<Row> rows;
	private final Map<CandidateKey, List<Row>> byTarget = new HashMap<>();
	private final Map<Integer, Map<CandidateKey, Evidence>> evidence = new HashMap<>();

	public AssayCandidateTracker(List<IsolationWindow> windows, SearchParameters parameters) {
		this(windows, parameters, null);
	}

	public AssayCandidateTracker(List<IsolationWindow> windows, SearchParameters parameters, AssayIdentityResolver identities) {
		var ordered = new ArrayList<Row>();
		for (IsolationWindow window : windows) {
			AssayXicExtractor.validateSchedule(ordered.size() + 1, window);
			String sequence = identities == null ? PeptideUtils.getCorrectedMasses(window.getCompound(), parameters.getAAConstants())
					: identities.resolve(window.getCompound(), window.getCharge());
			Row row = new Row(ordered.size() + 1, window, sequence);
			ordered.add(row);
			byTarget.computeIfAbsent(new CandidateKey(sequence, window.getCharge(), false), key -> new ArrayList<>()).add(row);
			evidence.put(row.number(), new HashMap<>());
		}
		rows = List.copyOf(ordered);
	}
	public List<Row> rows() { return rows; }
	public List<Row> matching(LibraryEntry target) {
		return List.copyOf(byTarget.getOrDefault(CandidateKey.of(target), List.of()));
	}
	public synchronized Evidence evidence(Row row, LibraryEntry candidate) {
		return evidence.get(row.number()).getOrDefault(CandidateKey.of(candidate), new Evidence(false, false, false));
	}
	/** Only the assay target's evidence determines detection, never its matched search decoy. */
	public synchronized Detection detection(Row row, String referenceProblem, int applicableScans) {
		if (referenceProblem != null) return new Detection("NO_REFERENCE", referenceProblem);
		if (applicableScans < 0) throw new IllegalArgumentException("Negative assay scan count");
		if (applicableScans == 0) return new Detection("NOT_DETECTED", "NO_APPLICABLE_MS2_SCANS");
		Evidence observed = evidence.get(row.number()).get(new CandidateKey(row.correctedSequence(), row.window().getCharge(), false));
		if (observed == null || !observed.scored()) {
			throw new IllegalStateException("Assay row " + row.number() + " has a reference and MS2 scans but no valid scored candidate");
		}
		String reason = observed.searched() ? "SEARCH_SCORED" : "FORCED_SCORED";
		if (!observed.retained()) reason += ";TDC_LOSER";
		return new Detection("DETECTED", reason);
	}
	public synchronized void scored(Row row, List<ScoredPSM> candidates, boolean forced) {
		for (ScoredPSM candidate : candidates) {
			float rt = candidate.getMSMS().getScanStartTime();
			if (!Float.isFinite(rt)) {
				throw new IllegalStateException("Nonfinite assay candidate RT at row " + row.number());
			}
			for (float value : candidate.getAuxScores()) if (!Float.isFinite(value)) {
				throw new IllegalStateException("Nonfinite assay candidate at row " + row.number());
			}
			CandidateKey key = CandidateKey.of(candidate.getLibraryEntry());
			Evidence previous = evidence.get(row.number()).getOrDefault(key, new Evidence(false, false, false));
			evidence.get(row.number()).put(key, new Evidence(previous.searched() || !forced,
					previous.forced() || forced, previous.retained()));
		}
	}
	public synchronized void retained(Row row, List<ScoredPSM> candidates) {
		for (ScoredPSM candidate : candidates) {
			CandidateKey key = CandidateKey.of(candidate.getLibraryEntry());
			Evidence previous = evidence.get(row.number()).get(key);
			if (previous == null || !previous.scored()) throw new IllegalStateException("Unobserved retained assay candidate");
			evidence.get(row.number()).put(key, new Evidence(previous.searched(), previous.forced(), true));
		}
	}
}
