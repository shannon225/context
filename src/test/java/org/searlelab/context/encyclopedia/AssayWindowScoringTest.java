package org.searlelab.context.encyclopedia;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.Test;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.Range;
import edu.washington.gs.maccoss.encyclopedia.algorithms.AbstractScoringResult;
import edu.washington.gs.maccoss.encyclopedia.algorithms.ScoredPSM;
import edu.washington.gs.maccoss.encyclopedia.algorithms.library.EncyclopediaTwoScoringFactory;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryEntry;
import edu.washington.gs.maccoss.encyclopedia.datastructures.PrecursorScanMap;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;
import edu.washington.gs.maccoss.encyclopedia.utils.massspec.FragmentIon;
import edu.washington.gs.maccoss.encyclopedia.utils.massspec.PeptideUtils;

public class AssayWindowScoringTest {
	@Test public void scoresShortWeakAndZeroSignalWithoutMs1() {
		for (int points : new int[] {1, 2, 3, 9, 25}) {
			for (int fragments : new int[] {1, 2, 3}) {
				for (boolean zero : new boolean[] {false, true}) {
					var candidates = score(points, fragments, zero, 0);
					assertFalse("No valid candidate for " + points + " scans / " + fragments + " fragments", candidates.isEmpty());
					for (var candidate : candidates) {
						assertTrue(candidate.getMSMS().getScanStartTime() >= 100);
						assertTrue(candidate.getMSMS().getScanStartTime() <= 100 + points - 1);
						for (float value : candidate.getAuxScores()) assertTrue(Float.isFinite(value));
					}
				}
			}
		}
	}

	@Test public void outsideSignalDoesNotChangeLocalFeatures() {
		var quiet = score(9, 3, false, 0).get(0);
		var interference = score(9, 3, false, 100000).get(0);
		assertEquals(quiet.getMSMS().getScanStartTime(), interference.getMSMS().getScanStartTime(), 0);
		float[] expected = quiet.getAuxScores(), actual = interference.getAuxScores();
		assertEquals(expected.length, actual.length);
		for (int i = 0; i < expected.length; i++) {
			// E-value deliberately retains full-run statistics; all local features must be invariant.
			if (i != 1) assertEquals("Local feature " + i, expected[i], actual[i], 1e-6f);
		}
	}

	private List<ScoredPSM> score(int points, int fragments, boolean zero, float outside) {
		return score(points, fragments, zero, outside, 100);
	}

	private List<ScoredPSM> score(int points, int fragments, boolean zero, float outside, int start) {
		var p = SearchParameterParser.getDefaultParametersObject();
		var aa = p.getAAConstants();
		var allIons = PeptideUtils.getPeptideModel("PEPTIDEK", aa).getPrimaryIonObjects(p.getFragType(), (byte) 2, true);
		var ions = Arrays.stream(allIons).filter(ion -> ion.getMass() > 250).limit(fragments).toArray(FragmentIon[]::new);
		double[] masses = FragmentIon.getMasses(ions);
		Arrays.sort(masses);
		float[] intensities = new float[fragments]; Arrays.fill(intensities, 10);
		var entry = new LibraryEntry("synthetic", new HashSet<>(), aa.getChargedMass("PEPTIDEK", (byte) 2),
				(byte) 2, "PEPTIDEK", 1, 100, 1, masses, intensities, Optional.of(1f), aa);
		var scans = new ArrayList<FragmentScan>();
		for (int i = -1; i <= points; i++) {
			float[] signal = new float[fragments];
			for (int j = 0; j < fragments; j++) signal[j] = i < 0 || i == points ? outside
					: zero ? 0 : (float) Math.exp(-Math.pow(i - points / 2, 2) / 4) * (j + 1) * 10;
			scans.add(new FragmentScan("synthetic", "scan" + i, i + 1, entry.getPrecursorMZ(), 100 + i, 1,
					null, entry.getPrecursorMZ() - 1, entry.getPrecursorMZ() + 1, masses, signal,
					new float[fragments], (byte) 2, 100, 2000));
		}
		var observed = new ArrayList<ScoredPSM>();
		new EncyclopediaTwoPointOneScoringTask(new EncyclopediaTwoScoringFactory(p).getLibraryScorer(null),
				new ArrayList<>(List.of(entry)), scans, new Range((float) entry.getPrecursorMZ() - 1,
				(float) entry.getPrecursorMZ() + 1), 1, new PrecursorScanMap(new ArrayList<>()),
				new LinkedBlockingQueue<AbstractScoringResult>(), p, observed::addAll,
				new Range(start, 100 + points - 1), true).call();
		return observed;
	}
}
