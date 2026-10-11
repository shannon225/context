package org.searlelab.context.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AssaySchedulePairMatcherTest {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void findsDefaultPairsInSeedOrder() throws Exception {
		createPair("sample_masked2_assay");
		createPair("sample_masked1_assay");
		folder.newFile("unrelated.dia");

		List<AssayScheduleInputPair> pairs = AssaySchedulePairMatcher.findInputPairs(folder.getRoot(),
				AssaySchedulePairMatcher.DEFAULT_INPUT_PREFIX, 1, 2);

		assertEquals(2, pairs.size());
		assertEquals(1, pairs.get(0).seed());
		assertEquals("sample_masked1_assay.dia", pairs.get(0).acquisitionFile().getName());
		assertEquals("sample_masked1_assay.txt", pairs.get(0).assayScheduleFile().getName());
		assertEquals(2, pairs.get(1).seed());
	}

	@Test
	public void treatsCustomPrefixAsCaseInsensitiveLiteralText() throws Exception {
		folder.newFile("sample_RUN+7_assay.DIA");
		folder.newFile("sample_RUN+7_assay.txt");

		List<AssayScheduleInputPair> pairs = AssaySchedulePairMatcher.findInputPairs(folder.getRoot(), "_run+", 7,
				7);

		assertEquals(1, pairs.size());
		assertEquals(7, pairs.get(0).seed());
	}

	@Test
	public void requiresSeedBoundaryAfterDigits() throws Exception {
		createPair("sample_masked1x_assay");

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> AssaySchedulePairMatcher.findInputPairs(folder.getRoot(), "_masked", 1, 1));

		assertTrue(error.getMessage().contains("No DIA file was found for seed(s): [1]"));
	}

	@Test
	public void rejectsDuplicateSeedBeforeReturningWork() throws Exception {
		createPair("sample_masked1_a");
		createPair("other_masked1_assay");

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> AssaySchedulePairMatcher.findInputPairs(folder.getRoot(), "_masked", 1, 1));

		assertTrue(error.getMessage().contains("More than one DIA file was found for seed 1"));
	}

	@Test
	public void reportsMissingSeedsAndAssaySchedulesTogether() throws Exception {
		folder.newFile("sample_masked1_assay.dia");

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> AssaySchedulePairMatcher.findInputPairs(folder.getRoot(), "_masked", 1, 2));

		assertTrue(error.getMessage().contains("prefix '_masked', seeds 1 through 2"));
		assertTrue(error.getMessage().contains("No DIA file was found for seed(s): [2]"));
		assertTrue(error.getMessage().contains("sample_masked1_assay.txt"));
	}

	@Test
	public void validatesFolderRangeAndPrefix() throws Exception {
		assertThrows(IllegalArgumentException.class,
				() -> AssaySchedulePairMatcher.findInputPairs(null, "_masked", 1, 1));
		assertThrows(IllegalArgumentException.class,
				() -> AssaySchedulePairMatcher.findInputPairs(folder.getRoot(), "_masked", 2, 1));
		assertThrows(IllegalArgumentException.class,
				() -> AssaySchedulePairMatcher.findInputPairs(folder.getRoot(), " ", 1, 1));
		assertThrows(IllegalArgumentException.class,
				() -> AssaySchedulePairMatcher.findInputPairs(folder.getRoot(), " _masked", 1, 1));
		assertThrows(IllegalArgumentException.class,
				() -> AssaySchedulePairMatcher.findInputPairs(folder.getRoot(), "path/prefix", 1, 1));
		assertThrows(IllegalArgumentException.class,
				() -> AssaySchedulePairMatcher.findInputPairs(folder.getRoot(), "masked1", 1, 1));
	}

	@Test
	public void returnedPairsAreImmutable() throws Exception {
		createPair("sample_masked1_assay");
		List<AssayScheduleInputPair> pairs = AssaySchedulePairMatcher.findInputPairs(folder.getRoot(), "_masked", 1,
				1);

		assertThrows(UnsupportedOperationException.class, () -> pairs.add(pairs.get(0)));
	}

	private void createPair(String baseName) throws Exception {
		folder.newFile(baseName + ".dia");
		folder.newFile(baseName + ".txt");
	}
}
