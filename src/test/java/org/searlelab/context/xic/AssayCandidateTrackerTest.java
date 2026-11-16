package org.searlelab.context.xic;

import static org.junit.Assert.*;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.Test;
import org.searlelab.context.datastructures.IsolationWindow;
import org.searlelab.context.encyclopedia.ContextEncyclopediaScoringFactory;
import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.Range;
import edu.washington.gs.maccoss.encyclopedia.algorithms.AbstractScoringResult;
import edu.washington.gs.maccoss.encyclopedia.algorithms.TDCPeptideScoringResult;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryEntry;
import edu.washington.gs.maccoss.encyclopedia.datastructures.PrecursorScanMap;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;
import edu.washington.gs.maccoss.encyclopedia.utils.massspec.PeptideUtils;

public class AssayCandidateTrackerTest {
	@Test public void forcedLoserIsScoredAndRepeatedAcquisitionRangeDoesNotGiveAnotherChance() {
		var p = SearchParameterParser.getDefaultParametersObject();
		var aa = p.getAAConstants();
		double mass = PeptideUtils.getPeptideModel("PEPTIDEK", aa).getPrimaryIonObjects(p.getFragType(), (byte) 2, true)[3].getMass();
		var target = new LibraryEntry("synthetic", new HashSet<>(), 500, (byte) 2, "PEPTIDEK", 1, 101, 1,
				new double[] {mass}, new float[] {1}, Optional.of(1f), aa);
		var decoy = target.getDecoy(p);
		var entries = new ArrayList<>(List.of(target, decoy));
		var scans = new ArrayList<FragmentScan>();
		for (int t = 100; t <= 102; t++) scans.add(new FragmentScan("synthetic", "scan" + t, t, 500, t, 1,
				null, 499, 501, new double[] {}, new float[] {}, new float[] {}, (byte) 2, 100, 2000));
		StripeFileInterface raw = (StripeFileInterface) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] {StripeFileInterface.class}, (proxy, method, args) -> {
					if (method.getName().equals("getStripes")) return new ArrayList<>(scans);
					if (method.getName().equals("getPrecursors")) return new ArrayList<>();
					throw new AssertionError(method.getName());
				});
		var tracker = new AssayCandidateTracker(List.of(new IsolationWindow("PEPTIDEK", 500, (byte) 2, 100, 102, true)), p);
		var factory = new ContextEncyclopediaScoringFactory(p, null, tracker, raw);
		var queue = new LinkedBlockingQueue<AbstractScoringResult>();
		var precursorMap = new PrecursorScanMap(new ArrayList<>());
		factory.getScoringTask(factory.getLibraryScorer(null), entries, scans, new Range(499, 501), 1, precursorMap, queue).call();
		assertEquals(1, queue.size());
		var result = (TDCPeptideScoringResult) queue.peek();
		assertEquals(1, result.getGoodMSMSCandidates().size());
		var row = tracker.rows().get(0);
		assertTrue(tracker.evidence(row, target).scored());
		assertTrue(tracker.evidence(row, decoy).scored());
		assertEquals("DETECTED", tracker.detection(row, null, 3).status());
		assertEquals(!tracker.evidence(row, target).retained(), tracker.detection(row, null, 3).reason().contains("TDC_LOSER"));
		assertTrue(tracker.evidence(row, target).forced());
		assertTrue(tracker.evidence(row, decoy).forced());
		assertNotEquals(tracker.evidence(row, target).retained(), tracker.evidence(row, decoy).retained());
		factory.getScoringTask(factory.getLibraryScorer(null), entries, scans, new Range(499.5f, 501.5f), 1, precursorMap, queue).call();
		assertEquals("Already-scored losers must not get another competition", 1, queue.size());
	}
}
