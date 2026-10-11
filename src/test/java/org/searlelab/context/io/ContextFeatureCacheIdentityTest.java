package org.searlelab.context.io;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;

public class ContextFeatureCacheIdentityTest {
	@Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void detectsChangedInputAndCompanionArtifact() throws Exception {
		File library = file("source.elib", "library");
		File run = file("run.dia", "run");
		File fasta = file("proteins.fasta", "fasta");
		File massList = file("mass.txt", "mass");
		File features = file("features.tsv", "id\n");
		File sidecar = file("pairs.tsv", "id\n");
		File manifest = new File(temporaryFolder.getRoot(), "manifest.sha256");
		ContextFeatureScorer.Options options = ContextFeatureScorer.Options.defaults();
		String identity = ContextFeatureCacheIdentity.inputs(library, run, fasta, massList,
				SearchParameterParser.getDefaultParametersObject(), options);
		assertEquals(identity, ContextFeatureCacheIdentity.inputs(library, run, fasta, massList,
				SearchParameterParser.getDefaultParametersObject(), new ContextFeatureScorer.Options(27,
						(byte) 2, true, false, true)));
		ContextFeatureCacheIdentity.save(manifest, identity, features, sidecar, null);
		ContextFeatureCacheIdentity.requireMatch(manifest, identity, features, sidecar, null);
		Files.writeString(sidecar.toPath(), "changed\n");
		assertInvalid(manifest, identity, features, sidecar);
		Files.writeString(sidecar.toPath(), "id\n");
		Files.writeString(massList.toPath(), "changed mass\n");
		String changed = ContextFeatureCacheIdentity.inputs(library, run, fasta, massList,
				SearchParameterParser.getDefaultParametersObject(), options);
		assertInvalid(manifest, changed, features, sidecar);
	}

	@Test
	public void archivesOnlyMarkedIncompleteOutputBeforeRetry() throws Exception {
		String base = new File(temporaryFolder.getRoot(), "run").getAbsolutePath();
		File features = file("run.features.txt", "partial feature\n");
		File sidecar = file("run.features.txt.pairs.tsv", "partial pair\n");
		File report = file("run_context_0123456789ab_predictions.tsv", "excluded target\n");
		File working = file("run_context_0123456789ab.elib", "partial library\n");
		File marker = new File(features + ".context.inprogress");
		File manifest = new File(features + ".context.sha256");
		String identity = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
		ContextFeatureCacheIdentity.markInProgress(marker, identity, false);
		File archive = ContextFeatureCacheIdentity.archiveIncomplete(marker, features, sidecar, manifest, base);
		assertTrue(archive.isDirectory());
		assertTrue(new File(archive, features.getName()).isFile());
		assertTrue(new File(archive, sidecar.getName()).isFile());
		assertTrue(new File(archive, working.getName()).isFile());
		assertEquals("excluded target\n", Files.readString(new File(archive, report.getName()).toPath()));
		assertTrue(!features.exists() && !marker.exists() && !report.exists());
		ContextFeatureCacheIdentity.markInProgress(marker, identity, false);
	}

	@Test
	public void overlappingInvocationCannotArchiveActiveOutput() throws Exception {
		File features = file("active.features.tsv", "being written\n");
		File marker = new File(features + ".context.inprogress");
		File sidecar = new File(features + ".pairs.tsv");
		File manifest = new File(features + ".context.sha256");
		ContextFeatureCacheIdentity.markInProgress(marker,
				"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", false);
		AtomicReference<Exception> failure = new AtomicReference<>();
		try (ContextFeatureCacheIdentity.OutputLock owner = ContextFeatureCacheIdentity.acquireOutputLock(features)) {
			Thread second = new Thread(() -> {
				try (ContextFeatureCacheIdentity.OutputLock ignored = ContextFeatureCacheIdentity.acquireOutputLock(features)) {
					ContextFeatureCacheIdentity.archiveIncomplete(marker, features, sidecar, manifest,
							new File(temporaryFolder.getRoot(), "active").getAbsolutePath());
				} catch (Exception e) {
					failure.set(e);
				}
			});
			second.start();
			second.join();
			assertNotNull(failure.get());
			assertTrue(failure.get().getMessage().contains("in use"));
			assertEquals("being written\n", Files.readString(features.toPath()));
			assertTrue(marker.isFile());
		}
		try (ContextFeatureCacheIdentity.OutputLock retry = ContextFeatureCacheIdentity.acquireOutputLock(features)) {
			assertNotNull(ContextFeatureCacheIdentity.archiveIncomplete(marker, features, sidecar, manifest,
					new File(temporaryFolder.getRoot(), "active").getAbsolutePath()));
		}
	}

	@Test
	public void hashesBrukerDirectoryContentsInStablePathOrder() throws Exception {
		File library = file("source.elib", "library");
		File run = temporaryFolder.newFolder("run.d");
		File nested = new File(run, "analysis");
		assertTrue(nested.mkdir());
		Files.writeString(new File(nested, "b.bin").toPath(), "second");
		Files.writeString(new File(run, "a.bin").toPath(), "first");
		File fasta = file("proteins.fasta", "fasta");
		File massList = file("mass.txt", "mass");
		String initial = identity(library, run, fasta, massList);
		Files.writeString(new File(run, "a.bin").toPath(), "first");
		assertEquals(initial, identity(library, run, fasta, massList));
		Files.writeString(new File(nested, "b.bin").toPath(), "changed");
		assertTrue(!initial.equals(identity(library, run, fasta, massList)));
	}

	private static String identity(File library, File run, File fasta, File massList) throws Exception {
		return ContextFeatureCacheIdentity.inputs(library, run, fasta, massList,
				SearchParameterParser.getDefaultParametersObject(), ContextFeatureScorer.Options.defaults());
	}

	private void assertInvalid(File manifest, String identity, File features, File sidecar) throws Exception {
		try {
			ContextFeatureCacheIdentity.requireMatch(manifest, identity, features, sidecar, null);
		} catch (IOException expected) {
			assertTrue(expected.getMessage().contains("identity differs"));
			return;
		}
		throw new AssertionError("Expected stale cache rejection");
	}

	private File file(String name, String contents) throws Exception {
		File file = new File(temporaryFolder.getRoot(), name);
		Files.writeString(file.toPath(), contents);
		return file;
	}
}
