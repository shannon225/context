package org.searlelab.context.encyclopedia;

import static org.junit.Assert.*;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.WindowData;

import edu.washington.gs.maccoss.encyclopedia.algorithms.library.EncyclopediaTwoJobData;
import edu.washington.gs.maccoss.encyclopedia.filereaders.LibraryFile;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;
import edu.washington.gs.maccoss.encyclopedia.utils.threading.EmptyProgressIndicator;

/** Reproduction requested by Ariana: a skipped range must not suppress later ranges. */
public class EncyclopediaTwoRangeAdvanceTest {
	@Rule public TemporaryFolder temporary = new TemporaryFolder();

	@Test
	public void emptyRangeDoesNotPreventReadingLaterPopulatedRange() throws Exception {
		assertLaterRangeRead(0, false);
	}

	@Test
	public void sparseRangeDoesNotPreventReadingLaterPopulatedRange() throws Exception {
		assertLaterRangeRead(9, false);
	}

	@Test
	public void zeroDutyCycleDoesNotReuseScansForLaterRange() throws Exception {
		assertLaterRangeRead(10, true);
	}

	private void assertLaterRangeRead(int firstScanCount, boolean zeroDutyCycle) throws Exception {
		var parameters = SearchParameterParser.getDefaultParametersObject();
		var library = new LibraryFile();
		var reader = new EncyclopeDIAFile();
		try {
			File raw = copyResource("/org/searlelab/context/io/IL2A_GPFDIA_0combined_masked0_assay.dia", "fixture.dia");
			library.openFile(copyResource("/org/searlelab/context/io/IL2_and_IL15_Combo.elib", "fixture.elib"));
			reader.openFile(raw);
			Range populated = null;
			for (Range range : reader.getRanges().keySet()) {
				if (range.getStart() > 2 && reader.getRanges().get(range).getAverageDutyCycle() > 0
						&& reader.getStripes(range.getMiddle(), -Float.MAX_VALUE, Float.MAX_VALUE, false).size() >= 10
						&& !library.getEntries(range, false, parameters.getAAConstants()).isEmpty()) {
					populated = range;
					break;
				}
			}
			assertNotNull("Fixture must supply a populated range with library entries", populated);
			var firstScans = new ArrayList<>(reader.getStripes(populated.getMiddle(),
					-Float.MAX_VALUE, Float.MAX_VALUE, false).subList(0, firstScanCount));
			Range empty = new Range(1f, 2f);
			var ranges = new HashMap<Range, WindowData>();
			ranges.put(empty, zeroDutyCycle ? new WindowData(0f, firstScanCount) : reader.getRanges().get(populated));
			ranges.put(populated, reader.getRanges().get(populated));
			var requestedCenters = new ArrayList<Double>();
			StripeFileInterface sparseFirst = (StripeFileInterface) Proxy.newProxyInstance(
					StripeFileInterface.class.getClassLoader(), new Class<?>[] {StripeFileInterface.class},
					(proxy, method, args) -> {
						if (method.getName().equals("getRanges")) return ranges;
						if (method.getName().equals("getStripes") && args[0] instanceof Double) {
							double center = (Double) args[0];
							requestedCenters.add(center);
							if (center == empty.getMiddle()) return firstScans;
						}
						try {
							return method.invoke(reader, args);
						} catch (InvocationTargetException e) {
							throw e.getCause();
						}
					});
			var factory = new ContextEncyclopediaScoringFactory(parameters);
			File fasta = resource("/org/searlelab/context/mprophet/mus_musculus_reviewed_uniprot.fasta");
			var job = new EncyclopediaTwoJobData(raw, fasta, library, library,
					new File(temporary.getRoot(), "search"), factory);
			EncyclopediaTwo.generateFeatureFile(new EmptyProgressIndicator(false), library, job, sparseFirst,
					Optional.empty(), OptionalInt.empty());
			assertTrue("Later populated range was never read; requested centers=" + requestedCenters,
					requestedCenters.contains((double) populated.getMiddle()));
		} finally {
			reader.close();
			library.close();
		}
	}

	private File copyResource(String path, String name) throws Exception {
		File output = new File(temporary.getRoot(), name);
		Files.copy(resource(path).toPath(), output.toPath());
		return output;
	}

	private File resource(String path) throws Exception {
		return new File(Objects.requireNonNull(getClass().getResource(path)).toURI());
	}
}
