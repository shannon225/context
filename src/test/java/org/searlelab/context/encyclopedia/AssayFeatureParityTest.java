package org.searlelab.context.encyclopedia;

import static org.junit.Assert.*;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.Test;
import org.searlelab.context.datastructures.IsolationWindow;
import org.searlelab.context.xic.AssayCandidateTracker;
import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.PrecursorScan;

import edu.washington.gs.maccoss.encyclopedia.algorithms.AbstractScoringResult;
import edu.washington.gs.maccoss.encyclopedia.algorithms.ScoredPSM;
import edu.washington.gs.maccoss.encyclopedia.algorithms.library.EncyclopediaTwoScoringFactory;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryEntry;
import edu.washington.gs.maccoss.encyclopedia.datastructures.PrecursorScanMap;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;
import edu.washington.gs.maccoss.encyclopedia.utils.massspec.FragmentIon;
import edu.washington.gs.maccoss.encyclopedia.utils.massspec.PeptideUtils;

public class AssayFeatureParityTest {
    @Test public void fullRunAssayAndBackgroundProduceIdenticalFeatures() {
        var p = SearchParameterParser.getDefaultParametersObject();
        var aa = p.getAAConstants();
        var ions = Arrays.stream(PeptideUtils.getPeptideModel("PEPTIDEK", aa)
                .getPrimaryIonObjects(p.getFragType(), (byte) 2, true))
                .filter(ion -> ion.getMass() > 250).limit(8).toArray(FragmentIon[]::new);
        var masses = FragmentIon.getMasses(ions); Arrays.sort(masses);
        var predicted = new float[masses.length]; Arrays.fill(predicted, 1);
        var entry = new LibraryEntry("synthetic", new HashSet<>(), 500, (byte) 2, "PEPTIDEK", 1,
                140, 1, masses, predicted, Optional.empty(), aa);
        var scans = new ArrayList<FragmentScan>();
        var ms1 = new ArrayList<PrecursorScan>();
        for (int i = 0; i < 81; i++) {
            var intensity = new float[masses.length];
            Arrays.fill(intensity, (float) Math.exp(-Math.pow(i - 40, 2) / 64.0));
            // One reference fragment has no acquired signal and must be filtered equally.
            intensity[0] = 0;
            scans.add(new FragmentScan("synthetic", "scan" + i, i, 500, 100 + i, 1, null,
                    499, 501, masses, intensity, new float[masses.length], (byte) 2, 100, 2000));
            ms1.add(new PrecursorScan("ms1" + i, i, 100 + i, 1, 100, 2000, null,
                    new double[] {500, 500.5016774}, new float[] {intensity[1], intensity[1] / 2}, null));
        }
        var baseline = new ArrayList<ScoredPSM>();
        var assay = new ArrayList<ScoredPSM>();
        var scorer = new EncyclopediaTwoScoringFactory(p).getLibraryScorer(null);
        for (boolean assayMode : new boolean[] {false, true}) {
            new EncyclopediaTwoPointOneScoringTask(scorer, new ArrayList<>(List.of(entry)), scans,
                    new Range(499, 501), 1, new PrecursorScanMap(ms1),
                    new LinkedBlockingQueue<AbstractScoringResult>(), p,
                    assayMode ? assay::addAll : baseline::addAll,
                    assayMode ? new Range(-Float.MAX_VALUE, Float.MAX_VALUE) : null, false).call();
        }
        assertFalse("Fixture must exercise normally gated candidates, not just fallback", baseline.isEmpty());
        assertEquals(baseline.size(), assay.size());
        for (int i = 0; i < baseline.size(); i++) {
            assertEquals(baseline.get(i).getMSMS().getScanStartTime(), assay.get(i).getMSMS().getScanStartTime(), 0);
            assertEquals(baseline.get(i).getPrimaryScore(), assay.get(i).getPrimaryScore(), 0);
            assertArrayEquals(baseline.get(i).getAuxScores(), assay.get(i).getAuxScores(), 0);
        }
    }

    @Test public void searchedAndForcedCandidatesCompeteOnBothSidesWithoutSecondChances() {
        var p = SearchParameterParser.getDefaultParametersObject();
        var aa = p.getAAConstants();
        var ions = Arrays.stream(PeptideUtils.getPeptideModel("PEPTIDEK", aa)
                .getPrimaryIonObjects(p.getFragType(), (byte) 2, true))
                .filter(ion -> ion.getMass() > 250).limit(8).toArray(FragmentIon[]::new);
        var masses = FragmentIon.getMasses(ions); Arrays.sort(masses);
        var intensity = new float[masses.length]; Arrays.fill(intensity, 1);
        var target = new LibraryEntry("synthetic", new HashSet<>(), 500, (byte) 2, "PEPTIDEK", 1,
                140, 1, masses, intensity, Optional.empty(), aa);
        // Distinct synthetic fragment masses isolate which side passes the normal gate.
        var decoyIons = Arrays.stream(PeptideUtils.getPeptideModel("WWWWWWWWWWK", aa)
                .getPrimaryIonObjects(p.getFragType(), (byte) 2, true))
                .filter(ion -> ion.getMass() > 250).limit(8).toArray(FragmentIon[]::new);
        var decoyMasses = FragmentIon.getMasses(decoyIons); Arrays.sort(decoyMasses);
        var decoy = new LibraryEntry("synthetic", new HashSet<>(), 500, (byte) 2, "WWWWWWWWWWK", 1,
                140, 1, decoyMasses, intensity, Optional.empty(), aa) {
            @Override public boolean isDecoy() { return true; }
        };
        for (boolean targetSearched : new boolean[] {true, false}) {
            var searched = targetSearched ? target : decoy;
            var forced = targetSearched ? decoy : target;
            var scans = new ArrayList<FragmentScan>();
            for (int i = 0; i < 81; i++) {
                float[] values = new float[searched.getMassArray().length];
                Arrays.fill(values, (float) Math.exp(-Math.pow(i - 40, 2) / 64.0));
                scans.add(new FragmentScan("synthetic", "scan" + i, i, 500, 100 + i, 1, null,
                        499, 501, searched.getMassArray(), values, new float[values.length], (byte) 2, 100, 2000));
            }
            var tracker = new AssayCandidateTracker(List.of(new IsolationWindow("PEPTIDEK", 500,
                    (byte) 2, 200, 202, false)), p);
            var row = tracker.rows().get(0);
            var queue = new LinkedBlockingQueue<AbstractScoringResult>();
            new org.searlelab.context.xic.AssayScoringTask(new EncyclopediaTwoScoringFactory(p).getLibraryScorer(null),
                    new ArrayList<>(List.of(target, decoy)), scans, new Range(499, 501), 1,
                    new PrecursorScanMap(new ArrayList<>()), queue, p, tracker, row, null).call();
            assertTrue("Expected normally gated candidate: " + targetSearched, tracker.evidence(row, searched).searched());
            assertFalse("Searched candidates must not be forced again", tracker.evidence(row, searched).forced());
            assertTrue("Expected missing counterpart to be forced: " + targetSearched, tracker.evidence(row, forced).forced());
            assertEquals(1, queue.size());
            assertEquals("DETECTED", tracker.detection(row, null, scans.size()).status());
            assertNotEquals(tracker.evidence(row, target).retained(), tracker.evidence(row, decoy).retained());
        }
    }

    @Test public void factoryUsesProcessingRangeWithoutReloadingOverlappingWindows() {
        var p = SearchParameterParser.getDefaultParametersObject();
        var aa = p.getAAConstants();
        double mass = PeptideUtils.getPeptideModel("PEPTIDEK", aa)
                .getPrimaryIonObjects(p.getFragType(), (byte) 2, true)[3].getMass();
        var target = new LibraryEntry("synthetic", new HashSet<>(), 500, (byte) 2, "PEPTIDEK", 1,
                101, 1, new double[] {mass}, new float[] {1}, Optional.empty(), aa);
        var scans = new ArrayList<FragmentScan>();
        for (int i = 0; i < 3; i++) scans.add(new FragmentScan("synthetic", "scan" + i, i, 500,
                100 + i, 1, null, 499, 501, new double[0], new float[0], new float[0], (byte) 2, 100, 2000));
        var raw = (StripeFileInterface) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {StripeFileInterface.class}, (proxy, method, args) -> {
                    throw new AssertionError("Search task must reuse supplied scans/precursors: " + method.getName());
                });
        var tracker = new AssayCandidateTracker(List.of(new IsolationWindow("PEPTIDEK", 500,
                (byte) 2, 200, 202, false)), p);
        var factory = new ContextEncyclopediaScoringFactory(p, null, tracker, raw);
        var queue = new LinkedBlockingQueue<AbstractScoringResult>();
        factory.getScoringTask(factory.getLibraryScorer(null), new ArrayList<>(List.of(target, target.getDecoy(p))),
                scans, new Range(499, 501), 1, new PrecursorScanMap(new ArrayList<>()), queue).call();
        assertEquals(1, queue.size());
        assertEquals("DETECTED", tracker.detection(tracker.rows().get(0), null, 3).status());
    }
}
