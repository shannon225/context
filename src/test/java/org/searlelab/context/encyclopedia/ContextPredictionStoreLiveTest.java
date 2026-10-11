package org.searlelab.context.encyclopedia;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.searlelab.msrawjava.model.Range;

import edu.washington.gs.maccoss.encyclopedia.datastructures.AminoAcidConstants;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryEntry;
import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchParameters;
import edu.washington.gs.maccoss.encyclopedia.filereaders.LibraryFile;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;
import edu.washington.gs.maccoss.encyclopedia.filewriters.web.JChronologerLibraryPredictionClient;
import edu.washington.gs.maccoss.encyclopedia.filewriters.web.JChronologerLibraryPredictionClient.ScribeTwoPredictionSession;
import edu.washington.gs.maccoss.encyclopedia.utils.Logger;

/**
 * Real-prediction check of the prediction store on the IL2 fixture: an overlapping library built on a partly filled
 * store sends only its new precursors to JChronologer and matches a fresh full prediction within the batch tolerance.
 */
public class ContextPredictionStoreLiveTest {
	@Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

	/*
	 * Batch composition changes JChronologer output slightly (CTX-016 r8 evidence, 400 peptides): intensity by up to
	 * 1.7e-5, ion mobility by up to 6e-5, and retention time by up to 2.4e-4 s, one float32 step for retention times
	 * between 2,048 and 4,096 s. Fragment and precursor m/z were identical. Each tolerance bounds its observed maximum.
	 */
	private static final double INTENSITY_TOLERANCE = 1e-4;
	private static final double ION_MOBILITY_TOLERANCE = 1e-4;
	private static final double RETENTION_TIME_TOLERANCE_SECONDS = 1e-3;
	private static final double MZ_TOLERANCE = 1e-6;

	@Test
	public void overlappingLibraryReusesStoredSpectraWithinTolerance() throws Exception {
		// JChronologer 1.0.10 ships PyTorch 2.7.1 natives for macOS ARM, Linux x86_64, and Windows x86_64.
		assumeFalse(System.getProperty("os.name").toLowerCase().contains("mac")
				&& System.getProperty("os.arch").equals("x86_64"));
		SearchParameters parameters = SearchParameterParser.getDefaultParametersObject();
		AminoAcidConstants constants = parameters.getAAConstants();
		ContextWorkingLibraryPreparer.Settings settings = new ContextWorkingLibraryPreparer.Settings(27, (byte) 2, true);
		LibraryFile full = fixture();
		LibraryFile half = subset(full, constants);
		try (ContextPredictionRuntime runtime = new ContextPredictionRuntime();
				ScribeTwoPredictionSession session = JChronologerLibraryPredictionClient.createScribeTwoPredictionSession();
				ContextPredictionStore store = ContextPredictionStore.open(temporaryFolder.newFolder("prediction-cache"), settings, false)) {
			Set<String> freshRequests = new LinkedHashSet<>();
			Set<String> halfRequests = new LinkedHashSet<>();
			Set<String> partialRequests = new LinkedHashSet<>();
			Set<String> fullHitRequests = new LinkedHashSet<>();
			Prepared fresh = prepare(full, parameters, settings, "fresh", recording(session, settings, parameters, freshRequests), null);
			prepare(half, parameters, settings, "half", recording(session, settings, parameters, halfRequests), store);
			Prepared partial = prepare(full, parameters, settings, "partial", recording(session, settings, parameters, partialRequests), store);
			Prepared fullHit = prepare(full, parameters, settings, "full-hit", recording(session, settings, parameters, fullHitRequests), store);

			Set<String> expectedNew = new LinkedHashSet<>(freshRequests);
			expectedNew.removeAll(halfRequests);
			assertTrue("the subset must overlap the full library", halfRequests.size() > 0 && expectedNew.size() < freshRequests.size());
			assertEquals("only precursors absent from the store are predicted", expectedNew, partialRequests);
			assertEquals("a full store hit predicts nothing", 0, fullHitRequests.size());

			assertEquals(fresh.assignments, partial.assignments);
			assertEquals(fresh.assignments, fullHit.assignments);
			double[] partialDifference = maximumDifferences(fresh.spectra, partial.spectra);
			Logger.logLine("Partial-store versus fresh prediction, maximum differences: fragment m/z " + partialDifference[0]
					+ ", intensity " + partialDifference[1] + ", retention time " + partialDifference[2] + " s, precursor m/z "
					+ partialDifference[3] + ", ion mobility " + partialDifference[4] + "; " + partialRequests.size() + " of "
					+ freshRequests.size() + " precursors predicted");
			assertTrue("fragment m/z " + partialDifference[0], partialDifference[0] <= MZ_TOLERANCE);
			assertTrue("intensity " + partialDifference[1], partialDifference[1] <= INTENSITY_TOLERANCE);
			assertTrue("retention time " + partialDifference[2], partialDifference[2] <= RETENTION_TIME_TOLERANCE_SECONDS);
			assertTrue("precursor m/z " + partialDifference[3], partialDifference[3] <= MZ_TOLERANCE);
			assertTrue("ion mobility " + partialDifference[4], partialDifference[4] <= ION_MOBILITY_TOLERANCE);
			for (double difference : maximumDifferences(partial.spectra, fullHit.spectra)) {
				assertEquals("a full store hit reads exactly what the partial run stored", 0.0, difference, 0.0);
			}
		} finally {
			half.close();
			full.close();
		}
	}

	private record Prepared(List<ContextWorkingLibraryPreparer.Assignment> assignments, Map<String, LibraryEntry> spectra) {}

	private Prepared prepare(LibraryFile source, SearchParameters parameters, ContextWorkingLibraryPreparer.Settings settings,
			String name, ContextWorkingLibraryPreparer.Predictor predictor, ContextPredictionStore store) throws Exception {
		try (ContextWorkingLibraryPreparer.Result result = ContextWorkingLibraryPreparer.prepare(source, parameters, settings,
				new File(temporaryFolder.getRoot(), name + ".elib"), new File(temporaryFolder.getRoot(), name + ".tsv"), predictor, store)) {
			AminoAcidConstants constants = parameters.getAAConstants();
			HashMap<String, LibraryEntry> spectra = new HashMap<>();
			for (LibraryEntry entry : result.library().getAllEntries(false, constants)) {
				spectra.put("target " + entry.getPeptideModSeq() + "|" + entry.getPrecursorCharge(), entry);
			}
			for (LibraryEntry entry : result.library().getDecoyEntries(new Range(-Float.MAX_VALUE, Float.MAX_VALUE), false, constants)) {
				spectra.put("decoy " + entry.getPeptideModSeq() + "|" + entry.getPrecursorCharge(), entry);
			}
			return new Prepared(result.assignments(), spectra);
		}
	}

	private static ContextWorkingLibraryPreparer.Predictor recording(ScribeTwoPredictionSession session,
			ContextWorkingLibraryPreparer.Settings settings, SearchParameters parameters, Set<String> requested) {
		return requests -> {
			for (var request : requests) requested.add(request.getPeptideModSeq() + "|" + request.getPrecursorCharge());
			return session.predictPrecursors(requests, settings.nce(), settings.defaultCharge(), settings.adjustNceForDia(), parameters);
		};
	}

	/** Largest absolute differences in fragment m/z, intensity, retention time (s), precursor m/z, and ion mobility. */
	private static double[] maximumDifferences(Map<String, LibraryEntry> expected, Map<String, LibraryEntry> actual) {
		assertEquals(expected.keySet(), actual.keySet());
		double[] maximum = new double[5];
		for (Map.Entry<String, LibraryEntry> item : expected.entrySet()) {
			LibraryEntry a = item.getValue();
			LibraryEntry b = actual.get(item.getKey());
			assertEquals(item.getKey(), a.getMassArray().length, b.getMassArray().length);
			for (int i = 0; i < a.getMassArray().length; i++) {
				maximum[0] = Math.max(maximum[0], Math.abs(a.getMassArray()[i] - b.getMassArray()[i]));
				maximum[1] = Math.max(maximum[1], Math.abs(a.getIntensityArray()[i] - b.getIntensityArray()[i]));
			}
			maximum[2] = Math.max(maximum[2], Math.abs(a.getRetentionTimeInSec() - b.getRetentionTimeInSec()));
			maximum[3] = Math.max(maximum[3], Math.abs(a.getPrecursorMZ() - b.getPrecursorMZ()));
			assertEquals(item.getKey(), a.getIonMobility().isPresent(), b.getIonMobility().isPresent());
			if (a.getIonMobility().isPresent()) {
				maximum[4] = Math.max(maximum[4], Math.abs(a.getIonMobility().get() - b.getIonMobility().get()));
			}
		}
		return maximum;
	}

	private LibraryFile fixture() throws Exception {
		URL url = Objects.requireNonNull(getClass().getResource("/org/searlelab/context/io/IL2_and_IL15_Combo.elib"));
		File copy = new File(temporaryFolder.getRoot(), "source.elib");
		Files.copy(new File(url.toURI()).toPath(), copy.toPath());
		LibraryFile library = new LibraryFile();
		library.openFile(copy);
		return library;
	}

	/** Every other target entry of the fixture, so the two libraries overlap by about half. */
	private static LibraryFile subset(LibraryFile full, AminoAcidConstants constants) throws Exception {
		ArrayList<LibraryEntry> entries = full.getAllEntries(false, constants);
		ArrayList<LibraryEntry> kept = new ArrayList<>();
		for (int i = 0; i < entries.size(); i += 2) kept.add(entries.get(i));
		LibraryFile half = new LibraryFile();
		half.openFile();
		half.addEntries(kept, false);
		half.addProteinsFromEntries(kept);
		return half;
	}
}
