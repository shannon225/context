package org.searlelab.context.encyclopedia;

import static org.junit.Assert.*;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.Test;
import org.searlelab.context.datastructures.IsolationWindow;
import org.searlelab.context.xic.AssayCandidateTracker;
import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.WindowData;
import edu.washington.gs.maccoss.encyclopedia.algorithms.AbstractScoringResult;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryEntry;
import edu.washington.gs.maccoss.encyclopedia.filereaders.LibraryFile;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;

public class AssayCompletionTest {

	@Test public void noAcquisitionRangesStillProducesAReportableEmptyFeatureTable() throws Exception {
		var p = SearchParameterParser.getDefaultParametersObject();
		var root = java.nio.file.Files.createTempDirectory("ctx020-empty-ranges-");
		var library = new LibraryFile();
		var delegate = new org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile();
		try {
			library.openFile();
			var rawFile = root.resolve("empty.dia").toFile();
			java.nio.file.Files.copy(new java.io.File(getClass().getResource("/org/searlelab/context/io/IL2A_GPFDIA_0combined_masked0_assay.dia").toURI()).toPath(), rawFile.toPath());
			delegate.openFile(rawFile);
			var raw = (StripeFileInterface) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {StripeFileInterface.class},
					(proxy, method, args) -> switch (method.getName()) {
						case "getRanges" -> new HashMap<Range, WindowData>();
						case "getStripes", "getPrecursors" -> new ArrayList<>();
						default -> method.invoke(delegate, args);
					});
			var tracker = new AssayCandidateTracker(List.of(new IsolationWindow("PEPTIDEK", 500, (byte) 2, 100, 102, false)), p);
			var factory = new ContextEncyclopediaScoringFactory(p, null, tracker, raw);
			var job = new edu.washington.gs.maccoss.encyclopedia.algorithms.library.EncyclopediaTwoJobData(rawFile,
					root.resolve("proteins.fasta").toFile(), library, library, root.resolve("run").toFile(), factory);
			EncyclopediaTwo.generateFeatureFile(new edu.washington.gs.maccoss.encyclopedia.utils.threading.EmptyProgressIndicator(),
					library, job, raw, java.util.Optional.empty(), java.util.OptionalInt.empty(), selection -> {});
			assertEquals("NO_REFERENCE", factory.detections().get(0).status());
			assertEquals(1, java.nio.file.Files.readAllLines(job.getPercolatorFiles().getInputTSV().toPath()).size());
		} finally {
			delegate.close(); library.close();
			try (var files = java.nio.file.Files.walk(root)) {
				for (var file : files.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.delete(file);
			}
		}
	}

	@Test public void finalizesRowsEvenWithoutSearchDispatch() throws Exception {
		for (boolean hasScans : new boolean[] {false, true}) {
			var p = SearchParameterParser.getDefaultParametersObject();
			var library = new LibraryFile();
			library.openFile();
			var supplement = new LibraryFile();
			supplement.openFile();
			try {
				double ion = edu.washington.gs.maccoss.encyclopedia.utils.massspec.PeptideUtils.getPeptideModel("PEPTIDEK", p.getAAConstants())
						.getPrimaryIonObjects(p.getFragType(), (byte) 2, true)[3].getMass();
				var target = new LibraryEntry("synthetic", new java.util.HashSet<>(), 500, (byte) 2, "PEPTIDEK", 1, 101, 1,
						new double[] {ion}, new float[] {16}, java.util.Optional.empty(), p.getAAConstants());
				supplement.addEntries(new ArrayList<>(List.of(target)), false);
				var window = new IsolationWindow(target.getPeptideModSeq(), target.getPrecursorMZ(), target.getPrecursorCharge(), 200, 202, false);
				assertTrue(library.getEntries("WWWWWWWWWWWWK", (byte) 2, false).isEmpty());
				var missing = new IsolationWindow("WWWWWWWWWWWWK", 700, (byte) 2, 100, 102, true);
				var scans = new ArrayList<FragmentScan>();
				var range = new Range((float) target.getPrecursorMZ() - 2, (float) target.getPrecursorMZ() + 1);
				if (hasScans) for (int t = 100; t <= 102; t++) scans.add(new FragmentScan("synthetic", "scan" + t, t,
						target.getPrecursorMZ(), t, 1, null, range.getStart(), range.getStop(), new double[0], new float[0], new float[0], (byte) 2, 100, 2000));
				var ranges = new HashMap<Range, WindowData>();
				if (hasScans) ranges.put(range, new WindowData(1f, 3));
				var requestedCenters = new ArrayList<Double>();
				var precursorReads = new int[1];
				var raw = (StripeFileInterface) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {StripeFileInterface.class},
						(proxy, method, args) -> switch (method.getName()) {
							case "getStripes" -> { requestedCenters.add((Double) args[0]); yield new ArrayList<>(scans); }
							case "getPrecursors" -> { precursorReads[0]++; yield new ArrayList<>(); }
							case "getRanges" -> ranges;
							default -> throw new AssertionError(method.getName());
						});
				var tracker = new AssayCandidateTracker(List.of(window, missing), p);
				var factory = new ContextEncyclopediaScoringFactory(p, null, tracker, raw);
				factory.setAssaySupplement(supplement, java.util.Map.of("WWWWWWWWWWWWK|2", "REFERENCE_PREPARATION_FAILED: unsupported input"));
				var queue = new LinkedBlockingQueue<AbstractScoringResult>();
				var selections = new ArrayList<EncyclopediaTwo.SearchSelection>();
				factory.completeAssays(library, factory.getLibraryScorer(null), queue, selections::add);
				assertEquals(2, factory.detections().size());
				assertEquals(hasScans ? "DETECTED" : "NOT_DETECTED", factory.detections().get(0).status());
				assertEquals("NO_REFERENCE", factory.detections().get(1).status());
				assertEquals("REFERENCE_PREPARATION_FAILED: unsupported input", factory.detections().get(1).reason());
				assertEquals(0, library.countUniqueTargetPrecursors());
				assertEquals(hasScans ? 1 : 0, queue.size());
				assertEquals(hasScans ? 1 : 0, selections.size());
				assertEquals(hasScans ? 1 : 0, precursorReads[0]);
				if (hasScans) {
					assertTrue("Completion must load the selected processing range, not merge at precursor m/z",
							requestedCenters.contains((double) range.getMiddle()));
					assertArrayEquals(supplement.getEntries("PEPTIDEK", (byte) 2, EncyclopediaTwo.useSqrt).get(0).getIntensityArray(),
							selections.get(0).target().getIntensityArray(), 0);
				}
			} finally { library.close(); supplement.close(); }
		}
	}
}
