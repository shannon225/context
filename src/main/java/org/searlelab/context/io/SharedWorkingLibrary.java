package org.searlelab.context.io;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.List;

import edu.washington.gs.maccoss.encyclopedia.utils.Logger;

/**
 * One working library per source library and prediction profile, kept in the prediction store directory and shared
 * by every run that uses it. Creation is serialized by a blocking lock; a run that died while building leaves an
 * in-progress marker, and the next run moves that partial output to a unique failed directory before rebuilding.
 */
final class SharedWorkingLibrary {
	static final String DIRECTORY_NAME = "working-libraries";

	record Resolved(File library, File predictions, String libraryArtifact) {}

	@FunctionalInterface
	interface Builder {
		void build(File library, File predictions) throws IOException, SQLException;
	}

	private SharedWorkingLibrary() {}

	static Resolved resolve(File storeDirectory, String sourceName, String identity, Builder builder)
			throws IOException, SQLException {
		File directory = new File(storeDirectory, DIRECTORY_NAME);
		if (!directory.isDirectory() && !directory.mkdirs()) {
			throw new IOException("Cannot create shared working-library directory " + directory);
		}
		String name = sourceName + "_" + identity.substring(0, 12);
		File library = new File(directory, name + ".elib");
		File predictions = new File(directory, name + "_predictions.tsv");
		File manifest = new File(directory, name + ".context.sha256");
		File marker = new File(directory, name + ".context.inprogress");
		try (ContextFeatureCacheIdentity.OutputLock lock = ContextFeatureCacheIdentity.acquireSharedLock(
				new File(directory, name + ".context.lock"))) {
			if (marker.isFile() && !manifest.isFile()) {
				if (!library.exists() && !predictions.exists()) {
					// The interrupted attempt wrote nothing (for example, it stopped on a store lock): nothing to preserve.
					Files.delete(marker.toPath());
				} else {
					File archive = Files.createTempDirectory(directory.toPath(), name + ".failed-").toFile();
					for (File file : new File[] {library, predictions, marker}) {
						if (file.exists()) Files.move(file.toPath(), new File(archive, file.getName()).toPath());
					}
					Logger.logLine("Preserved incomplete shared working library in " + archive);
				}
			}
			if (manifest.isFile()) {
				try {
					ContextFeatureCacheIdentity.requireMatch(manifest, identity, library, predictions, null);
				} catch (IOException e) {
					throw new IOException("Shared working library does not match its manifest; move " + library.getName()
							+ " and its companions aside to rebuild it: " + library, e);
				}
				Files.deleteIfExists(marker.toPath());
				Logger.logLine("Reusing verified shared working library: " + library);
				return new Resolved(library, predictions, libraryArtifact(manifest));
			}
			if (library.exists() || predictions.exists()) {
				throw new IOException("Shared working library exists without a verified manifest or in-progress marker: " + library);
			}
			ContextFeatureCacheIdentity.markInProgress(marker, identity, false);
			builder.build(library, predictions);
			ContextFeatureCacheIdentity.save(manifest, identity, library, predictions, null);
			Files.delete(marker.toPath());
			Logger.logLine("Saved shared working library: " + library);
			return new Resolved(library, predictions, libraryArtifact(manifest));
		}
	}

	/** The manifest's second line is the library file's SHA-256 (see {@link ContextFeatureCacheIdentity#save}). */
	private static String libraryArtifact(File manifest) throws IOException {
		List<String> lines = Files.readAllLines(manifest.toPath(), StandardCharsets.UTF_8);
		if (lines.size() < 2 || !lines.get(1).matches("[0-9a-f]{64}")) throw new IOException("Invalid working-library manifest: " + manifest);
		return lines.get(1);
	}
}
