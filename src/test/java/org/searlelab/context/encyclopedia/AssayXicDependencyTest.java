package org.searlelab.context.encyclopedia;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;

import edu.washington.gs.maccoss.encyclopedia.algorithms.quantitation.TransitionRefiner;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;
import edu.washington.gs.maccoss.encyclopedia.utils.graphing.GraphType;
import edu.washington.gs.maccoss.encyclopedia.utils.massspec.ChromatogramExtractor;
import edu.washington.gs.maccoss.encyclopedia.utils.massspec.PeptideUtils;
import edu.washington.gs.maccoss.encyclopedia.utils.massspec.MassConstants;

/** CTX020-013: characterize the pinned dependency before changing assay scoring. */
public class AssayXicDependencyTest {
	@Rule public TemporaryFolder temporary = new TemporaryFolder();

	@Test
	public void normalizationUsesSumAndPreservesZeroTrace() {
		var traces = new ArrayList<float[]>();
		traces.add(new float[] {1, 2, 3});
		traces.add(new float[] {0, 0, 0});
		var normalized = TransitionRefiner.normalize(traces);
		assertArrayEquals(new float[] {1f / 6, 2f / 6, 3f / 6}, normalized.get(0), 1e-7f);
		assertArrayEquals(new float[] {0, 0, 0}, normalized.get(1), 0);
	}

	@Test
	public void refinementHandlesShortAndZeroTracesWithoutNonfiniteValues() {
		var parameters = SearchParameterParser.getDefaultParametersObject();
		var model = PeptideUtils.getPeptideModel("PEPTIDEK", parameters.getAAConstants());
		for (int points : new int[] {1, 2, 3, 4, 9, 25}) {
			for (int fragments : new int[] {1, 2, 3}) {
				for (boolean zero : new boolean[] {false, true}) {
					float[] times = new float[points];
					for (int i = 0; i < points; i++) times[i] = 100 + i;
					var ions = Arrays.copyOf(model.getPrimaryIonObjects(parameters.getFragType(), (byte) 2, true), fragments);
					var traces = new ArrayList<float[]>();
					for (int j = 0; j < fragments; j++) {
						float[] trace = new float[points];
						for (int i = 0; i < points; i++) {
							trace[i] = zero ? 0 : (float) Math.exp(-Math.pow(i - points / 2, 2) / 4) * (j + 1);
						}
						traces.add(trace);
					}
					var result = TransitionRefiner.identifyTransitions("PEPTIDEK", (byte) 2,
							times[points / 2], ions, traces, times, false, parameters);
					assertEquals(fragments, result.getCorrelationArray().length);
					assertEquals(points, result.getMedianChromatogram().length);
					assertTrue(result.getRange().getStart() >= times[0]);
					assertTrue(result.getRange().getStop() <= times[points - 1]);
					for (float[] values : new float[][] {result.getCorrelationArray(), result.getIntegrationArray(),
							result.getMedianChromatogram()}) {
						for (float value : values) assertTrue("Nonfinite refinement value", Float.isFinite(value));
					}
					if (zero) assertArrayEquals(new float[fragments], result.getIntegrationArray(), 0);
				}
			}
		}
	}

	@Test
	public void fixtureFragmentExtractionMatchesScanSumsAndUsesMinutesOnTraceAxis() throws Exception {
		var parameters = SearchParameterParser.getDefaultParametersObject();
		File source = new File(Objects.requireNonNull(getClass().getResource(
				"/org/searlelab/context/io/IL2A_GPFDIA_0combined_masked0_assay.dia")).toURI());
		File copy = new File(temporary.getRoot(), "fixture.dia");
		Files.copy(source.toPath(), copy.toPath());
		var reader = new EncyclopeDIAFile();
		try {
			reader.openFile(copy);
			float start = (16.225927f - 0.25f) * 60;
			float stop = (16.225927f + 0.25f) * 60;
			var precursors = reader.getPrecursors(start, stop);
			assertFalse("The IL2A fixture must supply precursor scans", precursors.isEmpty());
			var precursorTraces = ChromatogramExtractor.extractPrecursorChromatograms(
					parameters.getPrecursorTolerance(), 478.26841469471407, (byte) 3, precursors, false, false);
			assertArrayEquals(new byte[] {-1, 0, 1, 2}, ChromatogramExtractor.isotopes);
			for (int isotope = 0; isotope < precursorTraces.length; isotope++) {
				double mz = MassConstants.getChargedIsotopeMass(478.26841469471407, (byte) 3,
						ChromatogramExtractor.isotopes[isotope]);
				var xy = precursorTraces[isotope].toArrays();
				assertEquals(precursors.size(), xy.x.length);
				for (int i = 0; i < precursors.size(); i++) {
					var scan = precursors.get(i);
					float sum = 0;
					for (int index : parameters.getPrecursorTolerance().getIndices(scan.getMassArray(), mz)) {
						sum += scan.getIntensityArray()[index];
					}
					assertEquals(scan.getScanStartTime() / 60.0, xy.x[i], 1e-5);
					assertEquals(sum, xy.y[i], Math.max(1e-6, Math.abs(sum) * 1e-6));
				}
			}
			var scans = reader.getStripes(478.26841469471407, start, stop, false);
			assertFalse("The existing IL2A window must contain scans", scans.isEmpty());
			var ions = PeptideUtils.getPeptideModel("IGQVHHALDTTIK", parameters.getAAConstants())
					.getPrimaryIonObjects(parameters.getFragType(), (byte) 3, true);
			var tolerance = parameters.getFragmentTolerance();
			var traces = ChromatogramExtractor.extractFragmentChromatograms(tolerance, ions, scans,
					null, GraphType.line, false, false);
			assertEquals(ions.length, traces.size());
			for (var ion : ions) {
				var xy = traces.get(ion).toArrays();
				assertEquals(scans.size(), xy.x.length);
				for (int i = 0; i < scans.size(); i++) {
					var scan = scans.get(i);
					assertTrue(scan.getScanStartTime() >= start && scan.getScanStartTime() <= stop);
					assertEquals(scan.getScanStartTime() / 60.0, xy.x[i], 1e-5);
					float sum = 0;
					for (int index : tolerance.getIndices(scan.getMassArray(), ion.getMass())) {
						sum += scan.getIntensityArray()[index];
					}
					assertEquals(sum, xy.y[i], Math.max(1e-6, Math.abs(sum) * 1e-6));
				}
			}
		} finally {
			reader.close();
		}
	}
}
