package org.searlelab.context.encyclopedia;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.DataFormatException;

import edu.washington.gs.maccoss.encyclopedia.algorithms.scribe.JChronologerWorkingLibrary;
import edu.washington.gs.maccoss.encyclopedia.datastructures.AminoAcidConstants;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryEntry;
import edu.washington.gs.maccoss.encyclopedia.datastructures.PeptidePrecursor;
import edu.washington.gs.maccoss.encyclopedia.datastructures.SimplePeptidePrecursor;
import edu.washington.gs.maccoss.encyclopedia.filereaders.LibraryFile;
import edu.washington.gs.maccoss.encyclopedia.filereaders.LibraryFile.PredictionCacheWriteLock;
import edu.washington.gs.maccoss.encyclopedia.utils.Logger;

/**
 * Persistent per-precursor prediction store shared across source libraries. It holds raw predicted spectra keyed by
 * modified sequence and precursor charge, with no accessions or decoy links, in Encyclopedia's prediction-cache file.
 * A Context profile in the cache metadata guards reuse: Encyclopedia replaces rows by key without checking how they
 * were predicted, so a store written under another profile is refused rather than mixed.
 */
public final class ContextPredictionStore implements AutoCloseable {
	public static final String DEFAULT_DIRECTORY_NAME = "prediction-cache";
	static final String PROFILE_SUFFIX = ".context-profile";
	static final String PROFILE_METADATA_KEY = "ContextPredictionProfile";
	private static final String PROFILE_VERSION = "Context-prediction-store-v1|Encyclopedia-6.9.23|JChronologer-1.0.10";

	private final LibraryFile cache;
	private final File cacheFile;

	private ContextPredictionStore(LibraryFile cache, File cacheFile) {
		this.cache = cache;
		this.cacheFile = cacheFile;
	}

	/**
	 * Opens or creates the store for these prediction settings. A write lock left by an interrupted run fails with its
	 * details unless {@code clearStaleLock} is set; Context never clears a lock on its own.
	 */
	public static ContextPredictionStore open(File directory, ContextWorkingLibraryPreparer.Settings settings,
			boolean clearStaleLock) throws IOException, SQLException {
		File cacheFile = JChronologerWorkingLibrary.getPredictionCacheFile(directory, settings.nce(), settings.defaultCharge(),
				!settings.adjustNceForDia());
		File profileFile = profileFile(cacheFile);
		String expected = profile(settings);
		boolean existed = cacheFile.exists();
		boolean hasLegacyProfile = profileFile.isFile();
		if (hasLegacyProfile) {
			String found = Files.readString(profileFile.toPath(), StandardCharsets.UTF_8);
			if (!expected.equals(found)) {
				throw new IOException("Prediction store " + cacheFile + " was written under a different profile; use another"
						+ " prediction store directory. Expected:\n" + expected + "Found:\n" + found);
			}
			if (!existed) throw new IOException("Legacy prediction profile exists without its cache: " + profileFile);
		}
		if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create prediction store directory " + directory);
		LibraryFile cache = JChronologerWorkingLibrary.openOrCreatePredictionCache(Optional.of(directory), settings.nce(),
				settings.defaultCharge(), settings.adjustNceForDia());
		try {
			verifyProfile(cache, cacheFile, expected, existed, hasLegacyProfile, clearStaleLock);
			return new ContextPredictionStore(cache, cacheFile);
		} catch (IOException | SQLException | RuntimeException e) {
			try {
				cache.close();
			} catch (RuntimeException cleanupFailure) {
				e.addSuppressed(cleanupFailure);
			}
			throw e;
		}
	}

	public File cacheFile() {
		return cacheFile;
	}

	/** Returns stored spectra for the exact precursors requested, keyed by {@link #key}; absent precursors are omitted. */
	public Map<String, LibraryEntry> find(Collection<? extends PeptidePrecursor> precursors, AminoAcidConstants constants)
			throws IOException, SQLException, DataFormatException {
		ArrayList<PeptidePrecursor> requested = new ArrayList<>();
		for (PeptidePrecursor precursor : precursors) {
			requested.add(new SimplePeptidePrecursor(precursor.getPeptideModSeq(), precursor.getPrecursorCharge(), constants));
		}
		HashMap<String, LibraryEntry> found = new HashMap<>();
		for (LibraryEntry entry : cache.getPredictionCacheEntries(requested, false)) {
			LibraryEntry previous = found.putIfAbsent(key(entry.getPeptideModSeq(), entry.getPrecursorCharge()), entry);
			if (previous != null) throw new IOException("Duplicate stored prediction for " + key(entry.getPeptideModSeq(), entry.getPrecursorCharge()));
		}
		return found;
	}

	/** Adds predicted spectra under the store write lock. A lock held by anyone else fails with its details. */
	public void add(ArrayList<LibraryEntry> predictions) throws IOException, SQLException {
		if (predictions.isEmpty()) return;
		Connection c = cache.getImmediateConnection();
		boolean locked = false;
		try {
			if (!cache.acquirePredictionCacheWriteLock(c, hostName(), Long.toString(ProcessHandle.current().pid()),
					System.currentTimeMillis(), "Context prediction store write")) {
				Optional<PredictionCacheWriteLock> lock = cache.getPredictionCacheWriteLock(c);
				c.rollback();
				throw lock.isPresent() ? lockedException(cacheFile, lock.get())
						: new IOException("Prediction store write lock could not be acquired: " + cacheFile);
			}
			c.commit();
			locked = true;
			cache.addPredictionCacheEntries(c, predictions);
			cache.clearPredictionCacheWriteLock(c);
			c.commit();
			locked = false;
		} catch (IOException | SQLException | RuntimeException e) {
			try {
				c.rollback();
				if (locked) {
					cache.clearPredictionCacheWriteLock(c);
					c.commit();
				}
			} catch (SQLException | RuntimeException cleanupFailure) {
				e.addSuppressed(cleanupFailure);
			}
			throw e;
		} finally {
			c.close();
		}
	}

	@Override
	public void close() {
		cache.close();
	}

	public static String key(String peptideModSeq, byte charge) {
		return peptideModSeq + "|" + charge;
	}

	static File profileFile(File cacheFile) {
		return new File(cacheFile.getAbsolutePath() + PROFILE_SUFFIX);
	}

	static String profile(ContextWorkingLibraryPreparer.Settings settings) {
		return "version=" + PROFILE_VERSION + "\nnce=" + settings.nce() + "\ndefaultCharge=" + settings.defaultCharge()
				+ "\nadjustNceForDia=" + settings.adjustNceForDia() + "\n";
	}

	private static void verifyProfile(LibraryFile cache, File cacheFile, String expected, boolean existed,
			boolean hasLegacyProfile, boolean clearStaleLock) throws IOException, SQLException {
		Connection c = cache.getImmediateConnection();
		try {
			String found = null;
			int count = 0;
			try (PreparedStatement statement = c.prepareStatement("SELECT Value FROM metadata WHERE Key=?")) {
				statement.setString(1, PROFILE_METADATA_KEY);
				try (ResultSet rows = statement.executeQuery()) {
					while (rows.next()) {
						found = rows.getString(1);
						count++;
					}
				}
			}
			if (count > 1) throw new IOException("Prediction cache has duplicate Context profiles: " + cacheFile);
			if (count == 1 && !expected.equals(found)) {
				throw new IOException("Prediction store " + cacheFile + " was written under a different profile; use another"
						+ " prediction store directory. Expected:\n" + expected + "Found:\n" + found);
			}
			if (count == 0 && existed && !hasLegacyProfile) {
				throw new IOException("Prediction cache " + cacheFile + " has no Context profile in its metadata or legacy sidecar;"
						+ " it was not created by Context. Use another prediction store directory.");
			}

			Optional<PredictionCacheWriteLock> lock = cache.getPredictionCacheWriteLock(c);
			if (lock.isPresent()) {
				if (!clearStaleLock) throw lockedException(cacheFile, lock.get());
				cache.clearPredictionCacheWriteLock(c);
			}
			if (!cache.acquirePredictionCacheWriteLock(c, hostName(), Long.toString(ProcessHandle.current().pid()),
					System.currentTimeMillis(), "Context prediction profile")) {
				throw new IOException("Prediction store write lock could not be acquired: " + cacheFile);
			}
			if (count == 0) {
				try (PreparedStatement statement = c.prepareStatement("INSERT INTO metadata (Key, Value) VALUES (?, ?)")) {
					statement.setString(1, PROFILE_METADATA_KEY);
					statement.setString(2, expected);
					statement.executeUpdate();
				}
			}
			cache.clearPredictionCacheWriteLock(c);
			c.commit();
			if (lock.isPresent()) {
				Logger.logLine("Cleared prediction store write lock on request (" + describe(lock.get()) + "): " + cacheFile);
			}
		} catch (IOException | SQLException | RuntimeException e) {
			try {
				c.rollback();
			} catch (SQLException cleanupFailure) {
				e.addSuppressed(cleanupFailure);
			}
			throw e;
		} finally {
			c.close();
		}
	}

	private static IOException lockedException(File cacheFile, PredictionCacheWriteLock lock) {
		return new IOException("Prediction store is locked (" + describe(lock) + "): " + cacheFile
				+ ". If no other run is using it, the lock was left by an interrupted run; rerun with"
				+ " --clear-stale-store-lock true to clear it.");
	}

	private static String describe(PredictionCacheWriteLock lock) {
		Duration age = Duration.ofMillis(Math.max(0, System.currentTimeMillis() - lock.getStartedMillis()));
		return "host=" + lock.getHost() + ", pid=" + lock.getPid() + ", age=" + age.toMinutes() + " min, operation="
				+ lock.getOperation();
	}

	private static String hostName() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			// The host name only labels the lock for a person reading the error; the lock itself does not depend on it.
			return "unknown";
		}
	}

}
