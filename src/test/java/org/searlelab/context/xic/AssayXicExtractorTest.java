package org.searlelab.context.xic;

import static org.junit.Assert.*;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import org.junit.Test;
import org.searlelab.context.datastructures.IsolationWindow;
import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.model.FragmentScan;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryEntry;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;
import edu.washington.gs.maccoss.encyclopedia.utils.massspec.PeptideUtils;

public class AssayXicExtractorTest {
	@Test public void extractsFullGradientAndPreservesScheduleAndWeakSignalWithoutMs1() throws Exception {
		var p = SearchParameterParser.getDefaultParametersObject();
		double ion = PeptideUtils.getPeptideModel("PEPTIDEK", p.getAAConstants())
				.getPrimaryIonObjects(p.getFragType(), (byte) 2, true)[3].getMass();
		var entry = new LibraryEntry("synthetic", new HashSet<>(), 500, (byte) 2, "PEPTIDEK", 1,
				101, 1, new double[] {ion}, new float[] {1}, Optional.of(1f), p.getAAConstants());
		var scans = new ArrayList<FragmentScan>();
		for (int time : new int[] {99, 100, 101, 102, 103}) scans.add(new FragmentScan("synthetic", "scan" + time,
				time, 500, time, 1, null, 499, 501, new double[] {ion}, new float[] {time == 101 ? 2 : 0},
				new float[] {0}, (byte) 2, 100, 2000));
		StripeFileInterface reader = (StripeFileInterface) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] {StripeFileInterface.class}, (proxy, method, args) -> {
					if (method.getName().equals("getStripes")) return new ArrayList<>(scans);
					if (method.getName().equals("getPrecursors")) return new ArrayList<>();
					throw new AssertionError("Unexpected reader call: " + method.getName());
				});
		var window = new IsolationWindow("PEPTIDEK", 500, (byte) 2, 100, 102, true);
		var xic = AssayXicExtractor.extract(7, window, entry, reader, p);
		assertEquals(7, xic.assayRow());
		assertTrue(xic.entrapment());
		assertNull(xic.referenceProblem());
		assertEquals(5, xic.ms2ScanCount());
		assertEquals(100, xic.startSeconds(), 0);
		assertEquals(102, xic.stopSeconds(), 0);
		assertTrue(xic.precursors().isEmpty());
		assertEquals(1, xic.fragments().size());
		assertArrayEquals(new float[] {99, 100, 101, 102, 103}, xic.fragments().get(0).seconds(), 0);
		assertArrayEquals(new float[] {0, 0, 2, 0, 0}, xic.fragments().get(0).intensities(), 0);
		float[] leaked = xic.fragments().get(0).intensities(); leaked[2] = 999;
		assertEquals(2, xic.fragments().get(0).intensities()[2], 0);
	}

	@Test public void missingReferenceTakesPrecedenceWithoutPretendingExtractionOccurred() throws Exception {
		var p = SearchParameterParser.getDefaultParametersObject();
		var window = new IsolationWindow("PEPTIDEK", 500, (byte) 2, 100, 102, false);
		var xic = AssayXicExtractor.extract(1, window, null, null, p);
		assertEquals("NO_EXACT_REFERENCE", xic.referenceProblem());
		assertTrue(xic.fragments().isEmpty());
		assertEquals(0, xic.ms2ScanCount());
	}
	@Test public void differentPeptidesWithSimilarMassKeepOwnIonsAcrossFullGradient() throws Exception {
		var p = SearchParameterParser.getDefaultParametersObject();
		String[] sequences = {"PEPTIDEK", "PEDITPEK"};
		var references = new LibraryEntry[2];
		var ionsA = PeptideUtils.getPeptideModel(sequences[0], p.getAAConstants()).getPrimaryIonObjects(p.getFragType(), (byte) 2, true);
		var ionsB = PeptideUtils.getPeptideModel(sequences[1], p.getAAConstants()).getPrimaryIonObjects(p.getFragType(), (byte) 2, true);
		double massA = java.util.Arrays.stream(ionsA).filter(a -> java.util.Arrays.stream(ionsB)
				.noneMatch(b -> Math.abs(a.getMass() - b.getMass()) < 0.1)).findFirst().orElseThrow().getMass();
		double massB = java.util.Arrays.stream(ionsB).filter(b -> Math.abs(b.getMass() - massA) > 1)
				.findFirst().orElseThrow().getMass();
		for (int i = 0; i < 2; i++) references[i] = new LibraryEntry("synthetic", new HashSet<>(), 500 + i * 0.01,
				(byte) 2, sequences[i], 1, 101, 1, new double[] {i == 0 ? massA : massB}, new float[] {1}, Optional.of(1f), p.getAAConstants());
		double[] masses = {Math.min(massA, massB), Math.max(massA, massB)};
		float[] intensities = massA < massB ? new float[] {5, 7} : new float[] {7, 5};
		var scans = new ArrayList<FragmentScan>();
		for (int t = 99; t <= 104; t++) scans.add(new FragmentScan("synthetic", "scan" + t, t, 500, t, 1,
				null, 499, 501, masses, intensities, new float[2], (byte) 2, 100, 2000));
		StripeFileInterface reader = (StripeFileInterface) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] {StripeFileInterface.class}, (proxy, method, args) -> {
					if (method.getName().equals("getStripes")) return new ArrayList<>(scans);
					if (method.getName().equals("getPrecursors")) return new ArrayList<>();
					throw new AssertionError(method.getName());
				});
		var a = AssayXicExtractor.extract(1, new IsolationWindow(sequences[0], 500, (byte) 2, 100, 102, false), references[0], reader, p);
		var b = AssayXicExtractor.extract(2, new IsolationWindow(sequences[1], 500.01, (byte) 2, 101, 103, true), references[1], reader, p);
		assertArrayEquals(new float[] {99, 100, 101, 102, 103, 104}, a.fragments().get(0).seconds(), 0);
		assertArrayEquals(new float[] {99, 100, 101, 102, 103, 104}, b.fragments().get(0).seconds(), 0);
		assertArrayEquals(new float[] {5, 5, 5, 5, 5, 5}, a.fragments().get(0).intensities(), 0);
		assertArrayEquals(new float[] {7, 7, 7, 7, 7, 7}, b.fragments().get(0).intensities(), 0);
		assertFalse(a.entrapment()); assertTrue(b.entrapment());
	}

}
