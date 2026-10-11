package org.searlelab.context.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;
import org.searlelab.context.encyclopedia.ContextWorkingLibraryPreparer;

public class SharedWorkingLibraryTest {
	@Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

	private static final String IDENTITY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

	@Test
	public void buildsOnceAndReusesVerifiedLibrary() throws Exception {
		File store = temporaryFolder.newFolder("prediction-cache");
		AtomicInteger builds = new AtomicInteger();
		SharedWorkingLibrary.Resolved first = SharedWorkingLibrary.resolve(store, "source", IDENTITY, counting(builds));
		SharedWorkingLibrary.Resolved second = SharedWorkingLibrary.resolve(store, "source", IDENTITY, counting(builds));
		assertEquals(1, builds.get());
		assertEquals(first, second);
		assertEquals(ContextFeatureCacheIdentity.artifact(first.library()), first.libraryArtifact());
		assertFalse(new File(first.library().getParentFile(), "source_0123456789ab.context.inprogress").exists());
	}

	@Test
	public void concurrentRunsWaitForOneBuild() throws Exception {
		File store = temporaryFolder.newFolder("prediction-cache");
		AtomicInteger builds = new AtomicInteger();
		CountDownLatch building = new CountDownLatch(1);
		AtomicReference<SharedWorkingLibrary.Resolved> other = new AtomicReference<>();
		AtomicReference<Exception> failure = new AtomicReference<>();
		Thread second = new Thread(() -> {
			try {
				building.await();
				other.set(SharedWorkingLibrary.resolve(store, "source", IDENTITY, counting(builds)));
			} catch (Exception e) {
				failure.set(e);
			}
		});
		second.start();
		SharedWorkingLibrary.Resolved first = SharedWorkingLibrary.resolve(store, "source", IDENTITY, (library, predictions) -> {
			building.countDown();
			try {
				Thread.sleep(300);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IOException(e);
			}
			counting(builds).build(library, predictions);
		});
		second.join();
		if (failure.get() != null) throw failure.get();
		assertEquals("the waiting run reuses the library instead of building again", 1, builds.get());
		assertEquals(first, other.get());
	}

	@Test
	public void interruptedBuildIsPreservedAndRebuilt() throws Exception {
		File store = temporaryFolder.newFolder("prediction-cache");
		try {
			SharedWorkingLibrary.resolve(store, "source", IDENTITY, (library, predictions) -> {
				Files.writeString(library.toPath(), "partial");
				throw new IOException("simulated prediction failure");
			});
			fail("The builder failure must propagate");
		} catch (IOException expected) {
			assertTrue(expected.getMessage().contains("simulated prediction failure"));
		}
		File directory = new File(store, SharedWorkingLibrary.DIRECTORY_NAME);
		assertTrue(new File(directory, "source_0123456789ab.context.inprogress").isFile());
		AtomicInteger builds = new AtomicInteger();
		SharedWorkingLibrary.Resolved rebuilt = SharedWorkingLibrary.resolve(store, "source", IDENTITY, counting(builds));
		assertEquals(1, builds.get());
		assertEquals("library 1", Files.readString(rebuilt.library().toPath()));
		File[] archives = directory.listFiles((dir, name) -> name.startsWith("source_0123456789ab.failed-"));
		assertNotNull(archives);
		assertEquals(1, archives.length);
		assertEquals("partial", Files.readString(new File(archives[0], "source_0123456789ab.elib").toPath()));
	}

	@Test
	public void attemptThatWroteNothingLeavesNoFailedFolder() throws Exception {
		File store = temporaryFolder.newFolder("prediction-cache");
		try {
			SharedWorkingLibrary.resolve(store, "source", IDENTITY, (library, predictions) -> {
				throw new IOException("simulated store lock");
			});
			fail("The builder failure must propagate");
		} catch (IOException expected) {
			assertTrue(expected.getMessage().contains("simulated store lock"));
		}
		AtomicInteger builds = new AtomicInteger();
		SharedWorkingLibrary.resolve(store, "source", IDENTITY, counting(builds));
		assertEquals(1, builds.get());
		File[] archives = new File(store, SharedWorkingLibrary.DIRECTORY_NAME).listFiles((dir, name) -> name.contains(".failed-"));
		assertNotNull(archives);
		assertEquals("an attempt with no partial output needs no failed folder", 0, archives.length);
	}

	@Test
	public void refusesUnmarkedOutputWithoutManifest() throws Exception {
		File store = temporaryFolder.newFolder("prediction-cache");
		File directory = new File(store, SharedWorkingLibrary.DIRECTORY_NAME);
		assertTrue(directory.mkdirs());
		Files.writeString(new File(directory, "source_0123456789ab.elib").toPath(), "unknown origin");
		AtomicInteger builds = new AtomicInteger();
		try {
			SharedWorkingLibrary.resolve(store, "source", IDENTITY, counting(builds));
			fail("Unmarked output must not be overwritten or reused");
		} catch (IOException expected) {
			assertTrue(expected.getMessage(), expected.getMessage().contains("without a verified manifest"));
		}
		assertEquals(0, builds.get());
		assertEquals("unknown origin", Files.readString(new File(directory, "source_0123456789ab.elib").toPath()));
	}

	@Test
	public void changedLibraryFailsClearly() throws Exception {
		File store = temporaryFolder.newFolder("prediction-cache");
		AtomicInteger builds = new AtomicInteger();
		SharedWorkingLibrary.Resolved built = SharedWorkingLibrary.resolve(store, "source", IDENTITY, counting(builds));
		Files.writeString(built.library().toPath(), "edited");
		try {
			SharedWorkingLibrary.resolve(store, "source", IDENTITY, counting(builds));
			fail("A changed working library must not be reused");
		} catch (IOException expected) {
			assertTrue(expected.getMessage(), expected.getMessage().contains("does not match its manifest"));
		}
		assertEquals(1, builds.get());
	}

	@Test
	public void libraryIdentityIgnoresRunInputsButTracksPredictionInputs() throws Exception {
		File library = file("source.elib", "library");
		File fasta = file("proteins.fasta", "fasta");
		var parameters = SearchParameterParser.getDefaultParametersObject();
		ContextWorkingLibraryPreparer.Settings settings = new ContextWorkingLibraryPreparer.Settings(27, (byte) 2, true);
		String identity = ContextFeatureCacheIdentity.workingLibraryInputs(library, fasta, parameters, settings);
		File copied = file("copy-of-source.elib", "library");
		assertEquals("content, not path, identifies the library", identity,
				ContextFeatureCacheIdentity.workingLibraryInputs(copied, fasta, parameters, settings));
		assertFalse(identity.equals(ContextFeatureCacheIdentity.workingLibraryInputs(library, fasta, parameters,
				new ContextWorkingLibraryPreparer.Settings(30, (byte) 2, true))));
		Files.writeString(library.toPath(), "changed library");
		assertFalse(identity.equals(ContextFeatureCacheIdentity.workingLibraryInputs(library, fasta, parameters, settings)));
		String runIdentity = IDENTITY;
		assertFalse("a different working library changes the per-run identity",
				ContextFeatureCacheIdentity.withWorkingLibrary(runIdentity, "a".repeat(64))
						.equals(ContextFeatureCacheIdentity.withWorkingLibrary(runIdentity, "b".repeat(64))));
	}

	private static SharedWorkingLibrary.Builder counting(AtomicInteger builds) {
		return (library, predictions) -> {
			int build = builds.incrementAndGet();
			Files.writeString(library.toPath(), "library " + build);
			Files.writeString(predictions.toPath(), "predictions " + build);
		};
	}

	private File file(String name, String contents) throws Exception {
		File file = new File(temporaryFolder.getRoot(), name);
		Files.writeString(file.toPath(), contents);
		return file;
	}
}
