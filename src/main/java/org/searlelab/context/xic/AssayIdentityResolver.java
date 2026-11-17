package org.searlelab.context.xic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.searlelab.context.datastructures.IsolationWindow;
import edu.washington.gs.maccoss.encyclopedia.datastructures.AminoAcidConstants;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryPrecursorMetadata;
import edu.washington.gs.maccoss.encyclopedia.utils.massspec.PeptideUtils;

/** Resolve against the complete target metadata catalog, before filtering spectra or search winners. */
public final class AssayIdentityResolver {
	private record Key(String sequence, byte charge) {}
	private final Map<Key, Set<String>> candidates = new HashMap<>();
	private final AminoAcidConstants constants;

	public AssayIdentityResolver(Iterable<LibraryPrecursorMetadata> precursors, AminoAcidConstants constants) {
		this.constants = constants;
		for (var precursor : precursors) {
			String canonical = PeptideUtils.getCorrectedMasses(precursor.getPeptideModSeq(), constants);
			add(canonical, precursor.getPrecursorCharge(), canonical);
			// These are the same dependency APIs used by LibraryEntry.getAccuratePeptideModSeq().
			String exported = PeptideUtils.getSequence(PeptideUtils.getPeptideModel(canonical, constants).getAas());
			add(PeptideUtils.getCorrectedMasses(exported, constants), precursor.getPrecursorCharge(), canonical);
		}
	}

	private void add(String notation, byte charge, String canonical) {
		candidates.computeIfAbsent(new Key(notation, charge), key -> new HashSet<>()).add(canonical);
	}

	public String resolve(String sequence, byte charge) {
		String corrected = PeptideUtils.getCorrectedMasses(sequence, constants);
		Set<String> matches = candidates.get(new Key(corrected, charge));
		if (matches == null) return corrected; // Preserve unmatched identity for normal missing-reference handling.
		if (matches.size() != 1) throw new IllegalArgumentException(
				"Ambiguous assay notation for " + sequence + " charge " + charge + ": " + new java.util.TreeSet<>(matches));
		return matches.iterator().next();
	}

	/** Returns a canonical identity view; leaves the original report row and schedule untouched. */
	public IsolationWindow resolve(IsolationWindow window) {
		return new IsolationWindow(resolve(window.getCompound(), window.getCharge()), window.getTargetMz(),
				window.getCharge(), window.getRtMin(), window.getRtMax(), window.isDecoy());
	}
}
