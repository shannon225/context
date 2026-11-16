package org.searlelab.context.encyclopedia;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
			assertFalse("a new store must not create a profile sidecar", ContextPredictionStore.profileFile(store.cacheFile()).exists());
			assertEquals(ContextPredictionStore.profile(SETTINGS), storedProfile(store.cacheFile()));
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
		String changed = ContextPredictionStore.profile(SETTINGS).replace("JChronologer-1.0.10", "JChronologer-9.9.9");
		setProfile(cacheFile, changed);
		try {
			ContextPredictionStore.open(directory, SETTINGS, false).close();
			fail("A store written under another profile must be refused");
		} catch (IOException expected) {
			assertTrue(expected.getMessage(), expected.getMessage().contains("different profile"));
		}
		assertEquals("refusal must not replace the conflicting profile", changed, storedProfile(cacheFile));
		assertEquals("refusal must not replace stored spectra", 1, storedEntryCount(cacheFile));
	}

	@Test
	public void migratesVerifiedLegacyProfileWithoutDeletingSidecar() throws Exception {
		File directory = temporaryFolder.newFolder(ContextPredictionStore.DEFAULT_DIRECTORY_NAME);
		File cacheFile;
		try (ContextPredictionStore store = ContextPredictionStore.open(directory, SETTINGS, false)) {
			store.add(new ArrayList<>(List.of(entry("PEPTIDEK", (byte) 2, 600f, Optional.empty()))));
			cacheFile = store.cacheFile();
		}
		deleteProfile(cacheFile);
		File sidecar = ContextPredictionStore.profileFile(cacheFile);
		Files.writeString(sidecar.toPath(), ContextPredictionStore.profile(SETTINGS), StandardCharsets.UTF_8);
		try (ContextPredictionStore store = ContextPredictionStore.open(directory, SETTINGS, false)) {
			assertEquals(1, store.find(List.of(precursor("PEPTIDEK", (byte) 2)), constants).size());
		}
		assertEquals(ContextPredictionStore.profile(SETTINGS), storedProfile(cacheFile));
		assertTrue("legacy sidecar is preserved", sidecar.isFile());
	}

	@Test
	public void rejectsConflictingLegacyProfileBeforeMigration() throws Exception {
		File directory = temporaryFolder.newFolder(ContextPredictionStore.DEFAULT_DIRECTORY_NAME);
		File cacheFile;
		try (ContextPredictionStore store = ContextPredictionStore.open(directory, SETTINGS, false)) {
			cacheFile = store.cacheFile();
		}
		deleteProfile(cacheFile);
		Files.writeString(ContextPredictionStore.profileFile(cacheFile).toPath(), "version=unknown\n", StandardCharsets.UTF_8);
		try {
			ContextPredictionStore.open(directory, SETTINGS, false).close();
			fail("A conflicting legacy profile must be refused");
		} catch (IOException expected) {
			assertTrue(expected.getMessage(), expected.getMessage().contains("different profile"));
		}
		assertEquals(0, profileRowCount(cacheFile));
	}

	@Test
	public void rejectsDuplicateEmbeddedProfilesWithoutChangingStoredSpectra() throws Exception {
		File directory = temporaryFolder.newFolder(ContextPredictionStore.DEFAULT_DIRECTORY_NAME);
		File cacheFile;
		try (ContextPredictionStore store = ContextPredictionStore.open(directory, SETTINGS, false)) {
			store.add(new ArrayList<>(List.of(entry("PEPTIDEK", (byte) 2, 600f, Optional.empty()))));
			cacheFile = store.cacheFile();
		}
		insertProfile(cacheFile, ContextPredictionStore.profile(SETTINGS));
		try {
			ContextPredictionStore.open(directory, SETTINGS, false).close();
			fail("Duplicate profiles must be refused even when their text agrees");
		} catch (IOException expected) {
			assertTrue(expected.getMessage(), expected.getMessage().contains("duplicate Context profiles"));
		}
		assertEquals(2, profileRowCount(cacheFile));
		assertEquals(1, storedEntryCount(cacheFile));
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
	public void refusesExistingStoreWhoseEmbeddedProfileIsMissing() throws Exception {
		File directory = temporaryFolder.newFolder(ContextPredictionStore.DEFAULT_DIRECTORY_NAME);
		File cacheFile;
		try (ContextPredictionStore store = ContextPredictionStore.open(directory, SETTINGS, false)) {
			store.add(new ArrayList<>(List.of(entry("PEPTIDEK", (byte) 2, 600f, Optional.empty()))));
			cacheFile = store.cacheFile();
		}
		deleteProfile(cacheFile);
		try {
			ContextPredictionStore.open(directory, SETTINGS, false).close();
			fail("An existing store without a Context profile must be refused");
		} catch (IOException expected) {
			assertTrue(expected.getMessage(), expected.getMessage().contains("no Context profile"));
		}
		assertEquals(0, profileRowCount(cacheFile));
		assertEquals(1, storedEntryCount(cacheFile));
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

	private static void setProfile(File cacheFile, String value) throws Exception {
		deleteProfile(cacheFile);
		insertProfile(cacheFile, value);
	}

	private static void deleteProfile(File cacheFile) throws Exception {
		withCacheConnection(cacheFile, "DELETE FROM metadata WHERE Key=?", ContextPredictionStore.PROFILE_METADATA_KEY);
	}

	private static void insertProfile(File cacheFile, String value) throws Exception {
		LibraryFile library = new LibraryFile(true);
		library.openFile(cacheFile);
		try (Connection c = library.getImmediateConnection();
				PreparedStatement statement = c.prepareStatement("INSERT INTO metadata (Key, Value) VALUES (?, ?)")) {
			statement.setString(1, ContextPredictionStore.PROFILE_METADATA_KEY);
			statement.setString(2, value);
			statement.executeUpdate();
			c.commit();
		} finally {
			library.close();
		}
	}

	private static void withCacheConnection(File cacheFile, String sql, String key) throws Exception {
		LibraryFile library = new LibraryFile(true);
		library.openFile(cacheFile);
		try (Connection c = library.getImmediateConnection(); PreparedStatement statement = c.prepareStatement(sql)) {
			statement.setString(1, key);
			statement.executeUpdate();
			c.commit();
		} finally {
			library.close();
		}
	}

	private static String storedProfile(File cacheFile) throws Exception {
		LibraryFile library = new LibraryFile(true);
		library.openFile(cacheFile);
		try (Connection c = library.getConnection();
				PreparedStatement statement = c.prepareStatement("SELECT Value FROM metadata WHERE Key=?")) {
			statement.setString(1, ContextPredictionStore.PROFILE_METADATA_KEY);
			try (ResultSet rows = statement.executeQuery()) {
				assertTrue(rows.next());
				String profile = rows.getString(1);
				assertFalse("profile metadata must be unique", rows.next());
				return profile;
			}
		} finally {
			library.close();
		}
	}

	private static int profileRowCount(File cacheFile) throws Exception {
		return queryCount(cacheFile, "SELECT COUNT(*) FROM metadata WHERE Key='" + ContextPredictionStore.PROFILE_METADATA_KEY + "'");
	}

	private static int storedEntryCount(File cacheFile) throws Exception {
		return queryCount(cacheFile, "SELECT COUNT(*) FROM entries");
	}

	private static int queryCount(File cacheFile, String sql) throws Exception {
		LibraryFile library = new LibraryFile(true);
		library.openFile(cacheFile);
		try (Connection c = library.getConnection(); PreparedStatement statement = c.prepareStatement(sql);
				ResultSet rows = statement.executeQuery()) {
			assertTrue(rows.next());
			return rows.getInt(1);
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
