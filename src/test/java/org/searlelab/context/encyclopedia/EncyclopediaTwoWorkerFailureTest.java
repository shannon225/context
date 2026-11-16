package org.searlelab.context.encyclopedia;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.Range;

import edu.washington.gs.maccoss.encyclopedia.algorithms.AbstractScoringResult;
import edu.washington.gs.maccoss.encyclopedia.algorithms.AbstractLibraryScoringTask;
import edu.washington.gs.maccoss.encyclopedia.algorithms.PSMScorer;
import edu.washington.gs.maccoss.encyclopedia.algorithms.library.EncyclopediaTwoJobData;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryEntry;
import edu.washington.gs.maccoss.encyclopedia.datastructures.PrecursorScanMap;
import edu.washington.gs.maccoss.encyclopedia.filereaders.LibraryFile;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;
import edu.washington.gs.maccoss.encyclopedia.utils.Nothing;
import edu.washington.gs.maccoss.encyclopedia.utils.threading.EmptyProgressIndicator;

/** The existing small DIA fixture exercises real search workers without loading the predictor. */
public class EncyclopediaTwoWorkerFailureTest {
	@Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void failedWorkerFailsSearchWithOriginalCause() throws Exception {
		AtomicInteger submitted = new AtomicInteger();
		var parameters = SearchParameterParser.getDefaultParametersObject();
		var factory = new ContextEncyclopediaScoringFactory(parameters) {
			@Override public AbstractLibraryScoringTask getScoringTask(PSMScorer scorer,
					ArrayList<LibraryEntry> entries, ArrayList<FragmentScan> stripes, Range range,
					float duty, PrecursorScanMap precursors, BlockingQueue<AbstractScoringResult> queue) {
				if (submitted.getAndIncrement() != 0) return super.getScoringTask(scorer, entries, stripes, range, duty, precursors, queue);
				return new AbstractLibraryScoringTask(scorer, entries, stripes, precursors, queue, parameters) {
					@Override protected Nothing process() {
						throw new IllegalStateException("A4 deliberate scoring failure");
					}
				};
			}
		};
		try {
			search("failed", factory);
			fail("Expected the scoring-worker failure to stop feature generation");
		} catch (IOException expected) {
			assertTrue(expected.getMessage(), expected.getMessage().contains("Scoring worker failed"));
			Throwable cause = expected;
			while (cause.getCause() != null) cause = cause.getCause();
			assertTrue(cause.toString(), cause instanceof IllegalStateException);
			assertEquals("A4 deliberate scoring failure", cause.getMessage());
		}
		assertTrue("Other scoring tasks should have been submitted", submitted.get() > 1);
	}

	@Test
	public void successfulWorkersStillProduceFeatureRows() throws Exception {
		File features = search("successful", new ContextEncyclopediaScoringFactory(SearchParameterParser.getDefaultParametersObject()));
		try (var rows = Files.lines(features.toPath())) {
			assertTrue("Expected a header and scored features", rows.count() > 1);
		}
	}

	private File search(String name, ContextEncyclopediaScoringFactory factory) throws Exception {
		LibraryFile library = new LibraryFile();
		try {
			library.openFile(resource("/org/searlelab/context/io/IL2_and_IL15_Combo.elib"));
			File raw = resource("/org/searlelab/context/io/IL2A_GPFDIA_0combined_masked0_assay.dia");
			File fasta = resource("/org/searlelab/context/mprophet/mus_musculus_reviewed_uniprot.fasta");
			var job = new EncyclopediaTwoJobData(raw, fasta, library, library,
					new File(temporaryFolder.newFolder(name), "search"), factory);
			var reader = job.getDiaFileReader();
			try {
				EncyclopediaTwo.generateFeatureFile(new EmptyProgressIndicator(false), library, job, reader,
						Optional.empty(), OptionalInt.empty());
			} finally {
				reader.close();
			}
			return job.getPercolatorFiles().getInputTSV();
		} finally {
			library.close();
		}
	}

	private File resource(String path) throws Exception {
		return new File(Objects.requireNonNull(getClass().getResource(path)).toURI());
	}
}
