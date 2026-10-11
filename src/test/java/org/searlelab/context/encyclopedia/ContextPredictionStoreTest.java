package org.searlelab.context.encyclopedia;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import edu.washington.gs.maccoss.encyclopedia.algorithms.scribe.JChronologerWorkingLibrary;
import edu.washington.gs.maccoss.encyclopedia.datastructures.AminoAcidConstants;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryEntry;
import edu.washington.gs.maccoss.encyclopedia.datastructures.SimplePeptidePrecursor;
import edu.washington.gs.maccoss.encyclopedia.filereaders.LibraryFile;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;

public class ContextPredictionStoreTest {
	@Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

	private static final ContextWorkingLibraryPreparer.Settings SETTINGS = new ContextWorkingLibraryPreparer.Settings(27, (byte) 2, true);
	private final AminoAcidConstants constants = SearchParameterParser.getDefaultParametersObject().getAAConstants();

	@Test
	public void returnsExactPrecursorsAndPreservesSpectra() throws Exception {
		File directory = temporaryFolder.newFolder(ContextPredictionStore.DEFAULT_DIRECTORY_NAME);
		LibraryEntry stored = entry("PEPTIDEK", (byte) 2, 612.5f, Optional.of(0.91f));
		try (ContextPredictionStore store = ContextPredictionStore.open(directory, SETTINGS, false)) {
			store.add(new ArrayList<>(List.of(stored, entry("ELVISLIVESK", (byte) 3, 300f, Optional.of(1.02f)))));
		}
		try (ContextPredictionStore store = ContextPredictionStore.open(directory, SETTINGS, false)) {
			Map<String, LibraryEntry> found = store.find(List.of(precursor("PEPTIDEK", (byte) 2), precursor("PEPTIDEK", (byte) 3),
					precursor("MISSINGK", (byte) 2)), constants);
			assertEquals("only the stored charge of the stored peptide is returned", 1, found.size());
			LibraryEntry read = found.get(ContextPredictionStore.key("PEPTIDEK", (byte) 2));
			assertArrayEquals(stored.getMassArray(), read.getMassArray(), 0.0);
			assertArrayEquals(stored.getIntensityArray(), read.getIntensityArray(), 0.0f);
			assertEquals(stored.getRetentionTime(), read.getRetentionTime(), 0.0f);
			assertEquals(stored.getIonMobility(), read.getIonMobility());
		}
	}

	@Test
	public void separatesPredictionSettingsIntoDifferentCaches() throws Exception {
		File directory = temporaryFolder.newFolder(ContextPredictionStore.DEFAULT_DIRECTORY_NAME);
		try (ContextPredictionStore store = ContextPredictionStore.open(directory, SETTINGS, false)) {
			store.add(new ArrayList<>(List.of(entry("PEPTIDEK", (byte) 2, 600f, Optional.empty()))));
		}
		ContextWorkingLibraryPreparer.Settings otherNce = new ContextWorkingLibraryPreparer.Settings(30, (byte) 2, true);
		try (ContextPredictionStore store = ContextPredictionStore.open(directory, otherNce, false)) {
			assertTrue(store.find(List.of(precursor("PEPTIDEK", (byte) 2)), constants).isEmpty());
		}
		ContextWorkingLibraryPreparer.Settings noAdjustment = new ContextWorkingLibraryPreparer.Settings(27, (byte) 2, false);
		try (ContextPredictionStore first = ContextPredictionStore.open(directory, SETTINGS, false);
				ContextPredictionStore second = ContextPredictionStore.open(directory, noAdjustment, false)) {
			assertNotEquals(first.cacheFile(), second.cacheFile());
			assertTrue(second.find(List.of(precursor("PEPTIDEK", (byte) 2)), constants).isEmpty());
		}
	}

	@Test
	public void refusesStoreWrittenUnderAnotherProfileWithoutWriting() throws Exception {
		File directory = temporaryFolder.newFolder(ContextPredictionStore.DEFAULT_DIRECTORY_NAME);
		File cacheFile;
		try (ContextPredictionStore store = ContextPredictionStore.open(directory, SETTINGS, false)) {
			store.add(new ArrayList<>(List.of(entry("PEPTIDEK", (byte) 2, 600f, Optional.empty()))));
			cacheFile = store.cacheFile();
		}
		File profile = ContextPredictionStore.profileFile(cacheFile);
		Files.writeString(profile.toPath(), ContextPredictionStore.profile(SETTINGS).replace("JChronologer-1.0.10", "JChronologer-9.9.9"),
				StandardCharsets.UTF_8);
		byte[] before = Files.readAllBytes(cacheFile.toPath());
		try {
			ContextPredictionStore.open(directory, SETTINGS, false).close();
			fail("A store written under another profile must be refused");
		} catch (IOException expected) {
			assertTrue(expected.getMessage(), expected.getMessage().contains("different profile"));
		}
		assertArrayEquals("refusal must not touch the cache", before, Files.readAllBytes(cacheFile.toPath()));
	}

	@Test
	public void refusesEncyclopediaCacheWithoutContextProfile() throws Exception {
		File directory = temporaryFolder.newFolder(ContextPredictionStore.DEFAULT_DIRECTORY_NAME);
		JChronologerWorkingLibrary.openOrCreatePredictionCache(Optional.of(directory), SETTINGS.nce(), SETTINGS.defaultCharge(),
				SETTINGS.adjustNceForDia()).close();
		try {
			ContextPredictionStore.open(directory, SETTINGS, false).close();
			fail("A cache not created by Context must be refused");
		} catch (IOException expected) {
			assertTrue(expected.getMessage(), expected.getMessage().contains("no Context profile"));
		}
	}

	@Test
	public void staleLockFailsWithDetailsUntilExplicitlyCleared() throws Exception {
		File directory = temporaryFolder.newFolder(ContextPredictionStore.DEFAULT_DIRECTORY_NAME);
		File cacheFile;
		try (ContextPredictionStore store = ContextPredictionStore.open(directory, SETTINGS, false)) {
			cacheFile = store.cacheFile();
		}
		leaveLock(cacheFile, "crashed-host", "4242");
		try {
			ContextPredictionStore.open(directory, SETTINGS, false).close();
			fail("A stale lock must fail without the explicit flag");
		} catch (IOException expected) {
			assertTrue(expected.getMessage(), expected.getMessage().contains("host=crashed-host"));
			assertTrue(expected.getMessage(), expected.getMessage().contains("pid=4242"));
			assertTrue(expected.getMessage(), expected.getMessage().contains("--clear-stale-store-lock"));
		}
		try (ContextPredictionStore store = ContextPredictionStore.open(directory, SETTINGS, true)) {
			store.add(new ArrayList<>(List.of(entry("PEPTIDEK", (byte) 2, 600f, Optional.empty()))));
			assertEquals(1, store.find(List.of(precursor("PEPTIDEK", (byte) 2)), constants).size());
		}
		try (ContextPredictionStore store = ContextPredictionStore.open(directory, SETTINGS, false)) {
			assertEquals("a completed write releases its lock", 1, store.find(List.of(precursor("PEPTIDEK", (byte) 2)), constants).size());
		}
	}

	@Test
	public void writeFailsWithoutAddingWhenAnotherRunHoldsTheLock() throws Exception {
		File directory = temporaryFolder.newFolder(ContextPredictionStore.DEFAULT_DIRECTORY_NAME);
		try (ContextPredictionStore store = ContextPredictionStore.open(directory, SETTINGS, false)) {
			leaveLock(store.cacheFile(), "other-host", "77");
			try {
				store.add(new ArrayList<>(List.of(entry("PEPTIDEK", (byte) 2, 600f, Optional.empty()))));
				fail("A held lock must stop the write");
			} catch (IOException expected) {
				assertTrue(expected.getMessage(), expected.getMessage().contains("pid=77"));
			}
			assertTrue(store.find(List.of(precursor("PEPTIDEK", (byte) 2)), constants).isEmpty());
		}
	}

	@Test
	public void emptyWriteLeavesNoLock() throws Exception {
		File directory = temporaryFolder.newFolder(ContextPredictionStore.DEFAULT_DIRECTORY_NAME);
		try (ContextPredictionStore store = ContextPredictionStore.open(directory, SETTINGS, false)) {
			store.add(new ArrayList<>());
		}
		try (ContextPredictionStore store = ContextPredictionStore.open(directory, SETTINGS, false)) {
			assertTrue(store.find(List.of(precursor("PEPTIDEK", (byte) 2)), constants).isEmpty());
		}
	}

	private static void leaveLock(File cacheFile, String host, String pid) throws Exception {
		LibraryFile library = new LibraryFile(true);
		library.openFile(cacheFile);
		try (Connection c = library.getImmediateConnection()) {
			assertTrue(library.acquirePredictionCacheWriteLock(c, host, pid, System.currentTimeMillis() - 3_600_000L, "test lock"));
			c.commit();
		} finally {
			library.close();
		}
	}

	private SimplePeptidePrecursor precursor(String sequence, byte charge) {
		return new SimplePeptidePrecursor(sequence, charge, constants);
	}

	private LibraryEntry entry(String sequence, byte charge, float retentionTimeInSeconds, Optional<Float> ionMobility) {
		return new LibraryEntry("synthetic Context prediction store test", new HashSet<>(), constants.getChargedMass(sequence, charge),
				charge, sequence, 1, retentionTimeInSeconds, 1f, new double[] {250.1, 350.2, 450.3}, new float[] {10f, 5.5f, 1.25f},
				ionMobility, constants);
	}
}
