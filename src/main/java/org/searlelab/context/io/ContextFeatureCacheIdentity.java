package org.searlelab.context.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Stream;

import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchParameters;

/** Content identity for Context feature reuse and its companion pair sidecar. */
final class ContextFeatureCacheIdentity {
	private ContextFeatureCacheIdentity() {}

	static final class OutputLock implements AutoCloseable {
		private final FileChannel channel;
		private final FileLock lock;

		private OutputLock(FileChannel channel, FileLock lock) {
			this.channel = channel;
			this.lock = lock;
		}

		@Override public void close() throws IOException {
			try {
				lock.release();
			} finally {
				channel.close();
			}
		}
	}

	static OutputLock acquireOutputLock(File features) throws IOException {
		File lockFile = new File(features.getAbsolutePath() + ".context.lock");
		FileChannel channel = FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
		try {
			FileLock lock = channel.tryLock();
			if (lock == null) throw new IOException("Context feature output is in use: " + features);
			return new OutputLock(channel, lock);
		} catch (OverlappingFileLockException e) {
			channel.close();
			throw new IOException("Context feature output is in use: " + features, e);
		} catch (IOException | RuntimeException e) {
			channel.close();
			throw e;
		}
	}

	static String inputs(File library, File run, File fasta, File massList, SearchParameters parameters,
			ContextFeatureScorer.Options options) throws IOException {
		MessageDigest digest = sha256();
		update(digest, "Context-matched-decoys-v1|Encyclopedia-6.9.23|JChronologer-1.0.10");
		update(digest, parameters.toString());
		update(digest, options.predictionNce() + "|" + options.predictionDefaultCharge() + "|"
				+ options.adjustNceForDia() + "|" + options.candidateDiagnostics());
		for (File file : new File[] {library, run, fasta, massList}) {
			update(digest, file.getCanonicalPath());
			content(digest, file);
		}
		return hex(digest.digest());
	}

	static String artifact(File file) throws IOException {
		MessageDigest digest = sha256();
		stream(digest, file);
		return hex(digest.digest());
	}

	static void markInProgress(File marker, String inputIdentity, boolean diagnostics) throws IOException {
		Files.writeString(marker.toPath(), inputIdentity + "\n" + diagnostics + "\n", StandardCharsets.UTF_8,
				StandardOpenOption.CREATE_NEW);
	}

	static File archiveIncomplete(File marker, File features, File sidecar, File manifest, String baseName) throws IOException {
		if (!marker.isFile() || manifest.isFile()) return null;
		List<String> lines = Files.readAllLines(marker.toPath(), StandardCharsets.UTF_8);
		if (lines.size() != 2 || !lines.get(0).matches("[0-9a-f]{64}")
				|| !("true".equals(lines.get(1)) || "false".equals(lines.get(1)))) {
			throw new IOException("Invalid Context in-progress marker: " + marker);
		}
		String prefix = baseName + "_context_" + lines.get(0).substring(0, 12);
		File archive = Files.createTempDirectory(features.getAbsoluteFile().getParentFile().toPath(),
				features.getName() + ".failed-").toFile();
		for (File file : new File[] {features, sidecar, new File(prefix + ".elib"),
				new File(prefix + "_predictions.tsv"),
				"true".equals(lines.get(1)) ? new File(prefix + "_candidates.tsv") : null, marker}) {
			if (file != null && file.exists()) {
				Files.move(file.toPath(), new File(archive, file.getName()).toPath());
			}
		}
		return archive;
	}

	static void save(File manifest, String inputIdentity, File features, File sidecar, File diagnostic) throws IOException {
		if (manifest.exists()) throw new IOException("Feature cache manifest already exists: " + manifest);
		File temporary = File.createTempFile(manifest.getName(), ".tmp", manifest.getAbsoluteFile().getParentFile());
		try {
			Files.writeString(temporary.toPath(), expected(inputIdentity, features, sidecar, diagnostic),
					StandardCharsets.UTF_8);
			Files.move(temporary.toPath(), manifest.toPath(), StandardCopyOption.ATOMIC_MOVE);
		} finally {
			Files.deleteIfExists(temporary.toPath());
		}
	}

	static void requireMatch(File manifest, String inputIdentity, File features, File sidecar, File diagnostic) throws IOException {
		if (!manifest.isFile() || !sidecar.isFile()) {
			throw new IOException("Context feature cache lacks its pair sidecar or identity manifest: " + features);
		}
		if (diagnostic != null && !diagnostic.isFile()) {
			throw new IOException("Context feature cache lacks its candidate diagnostic: " + diagnostic);
		}
		if (!expected(inputIdentity, features, sidecar, diagnostic).equals(Files.readString(manifest.toPath(), StandardCharsets.UTF_8))) {
			throw new IOException("Context feature cache input or artifact identity differs: " + features);
		}
	}

	private static String expected(String inputIdentity, File features, File sidecar, File diagnostic) throws IOException {
		return inputIdentity + "\n" + artifact(features) + "\n" + artifact(sidecar) + "\n"
				+ (diagnostic == null ? "-" : artifact(diagnostic)) + "\n";
	}

	private static void stream(MessageDigest digest, File file) throws IOException {
		try (InputStream input = Files.newInputStream(file.toPath())) {
			byte[] bytes = new byte[64 * 1024];
			int count;
			while ((count = input.read(bytes)) != -1) digest.update(bytes, 0, count);
		}
	}

	private static void content(MessageDigest digest, File file) throws IOException {
		Path root = file.toPath();
		if (Files.isRegularFile(root, LinkOption.NOFOLLOW_LINKS)) {
			update(digest, "file");
			update(digest, Long.toString(Files.size(root)));
			stream(digest, file);
		} else if (Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
			update(digest, "directory");
			try (Stream<Path> paths = Files.walk(root)) {
				for (Path path : paths.sorted((left, right) -> root.relativize(left).toString()
						.compareTo(root.relativize(right).toString())).toList()) {
					if (path.equals(root)) continue;
					update(digest, root.relativize(path).toString().replace(File.separatorChar, '/'));
					if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) update(digest, "directory");
					else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
						update(digest, "file");
						update(digest, Long.toString(Files.size(path)));
						stream(digest, path.toFile());
					} else throw new IOException("Unsupported acquisition entry: " + path);
				}
			}
		} else throw new IOException("Input is not a readable file or directory: " + file);
	}

	private static void update(MessageDigest digest, String value) {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		digest.update((byte) (bytes.length >>> 24));
		digest.update((byte) (bytes.length >>> 16));
		digest.update((byte) (bytes.length >>> 8));
		digest.update((byte) bytes.length);
		digest.update(bytes);
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is unavailable", e);
		}
	}

	private static String hex(byte[] bytes) {
		StringBuilder text = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) text.append(Character.forDigit((b >>> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
		return text.toString();
	}
}
