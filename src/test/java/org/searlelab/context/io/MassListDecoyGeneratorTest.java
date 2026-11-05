package org.searlelab.context.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.searlelab.context.datastructures.IsolationWindow;

public class MassListDecoyGeneratorTest {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private File targetsOnly() throws IOException {
		return write("targets_assay.txt",
				"Compound\tFormula\tAdduct\tm/z\tz\tRT Time (min)\tWindow (min)\tisDecoy",
				"AHSQDENK\t\t(no adduct)\t464.7095878086647\t2\t13.984958\t5.0\tfalse",
				"LPFPIIDDK\t\t(no adduct)\t521.7899\t2\t25.0\t5.0\tfalse");
	}

	@Test
	public void recognisesAnAssayWithoutDecoys() throws IOException {
		assertFalse(MassListDecoyGenerator.hasDecoys(targetsOnly().getAbsolutePath()));
	}

	@Test
	public void addsOneDecoyPerTarget() throws IOException {
		ArrayList<IsolationWindow> windows = MassListDecoyGenerator.addShuffledDecoysToAssay(targetsOnly().getAbsolutePath(), 0);

		int targets = 0;
		int decoys = 0;
		for (IsolationWindow window : windows) {
			if (window.isDecoy()) decoys++; else targets++;
		}
		assertEquals(2, targets);
		assertEquals(2, decoys);
	}

	@Test
	public void decoysKeepTheirTargetsWindow() throws IOException {
		ArrayList<IsolationWindow> windows = MassListDecoyGenerator.addShuffledDecoysToAssay(targetsOnly().getAbsolutePath(), 0);

		IsolationWindow target = windows.get(0);
		IsolationWindow decoy = windows.get(1);
		assertTrue(decoy.isDecoy());
		assertEquals(target.getRtMin(), decoy.getRtMin(), 1e-6);
		assertEquals(target.getRtMax(), decoy.getRtMax(), 1e-6);
		assertEquals(target.getCharge(), decoy.getCharge());
		assertEquals(target.getTargetMz(), decoy.getTargetMz(), 0.02);
	}

	@Test
	public void generatingIsIdempotent() throws IOException {
		File first = new File(folder.getRoot(), "first.txt");
		File second = new File(folder.getRoot(), "second.txt");

		File once = MassListDecoyGenerator.checkIfDecoysArePresent(targetsOnly(), folder.getRoot(), 1);
		File twice = MassListDecoyGenerator.checkIfDecoysArePresent(once, folder.getRoot(), 1);

		assertTrue(MassListDecoyGenerator.hasDecoys(once.getAbsolutePath()));
		assertEquals("an assay that already has decoys is passed through unchanged", once, twice);
		assertFalse(first.exists() && second.exists() && first.equals(second));
	}

	@Test
	public void addDecoysLeavesAPreparedAssayAlone() throws IOException {
		File prepared = write("ready_assay.txt",
				"Compound\tFormula\tAdduct\tm/z\tz\tRT Time (min)\tWindow (min)\tisDecoy",
				"AHSQDENK\t\t(no adduct)\t464.7095878086647\t2\t13.984958\t5.0\tfalse",
				"ANEDQSHK\t\t(no adduct)\t464.7095878086647\t2\t13.984958\t5.0\ttrue");

		ArrayList<IsolationWindow> windows = MassListDecoyGenerator.addShuffledDecoysToAssay(prepared.getAbsolutePath(), 0);

		int targets = 0;
		int decoys = 0;
		for (IsolationWindow window : windows) {
			if (window.isDecoy()) decoys++; else targets++;
		}
		assertEquals(1, targets);
		assertEquals(1, decoys);
	}

	@Test
	public void anAssayThatAlreadyHasDecoysIsUsedUnchanged() throws IOException {
		File assay = write("ready_assay.txt",
				"Compound\tFormula\tAdduct\tm/z\tz\tRT Time (min)\tWindow (min)\tisDecoy",
				"AHSQDENK\t\t(no adduct)\t464.7095878086647\t2\t13.984958\t5.0\tfalse",
				"ANEDQSHK\t\t(no adduct)\t464.7095878086647\t2\t13.984958\t5.0\ttrue");

		assertTrue(MassListDecoyGenerator.hasDecoys(assay.getAbsolutePath()));
		assertEquals(assay, MassListDecoyGenerator.resolveDecoysMessage(assay, folder.getRoot(), "run", true));
	}

	@Test
	public void withoutTheFlagTheListIsNotTouched() throws IOException {
		File assay = targetsOnly();
		assertEquals(assay, MassListDecoyGenerator.resolveDecoysMessage(assay, folder.getRoot(), "run", false));
	}

	@Test
	public void bareFlagMeansTrueAndExplicitFalseMeansFalse() {
		HashMap<String, String> bare = new HashMap<>();
		bare.put("-generateDecoys", null);
		assertTrue(DirectoryOptions.isEnabled(bare, "-generateDecoys"));

		HashMap<String, String> explicit = new HashMap<>();
		explicit.put("-generateDecoys", "false");
		assertFalse(DirectoryOptions.isEnabled(explicit, "-generateDecoys"));

		assertFalse(DirectoryOptions.isEnabled(new HashMap<String, String>(), "-generateDecoys"));
	}

	@Test
	public void enginesGetTheirOwnDirectoriesUnderOneOutputDirectory() throws IOException {
		File mprophet = DirectoryOptions.engineDirectory(folder.getRoot(), "mprophet");
		File percolator = DirectoryOptions.engineDirectory(folder.getRoot(), "percolator");

		assertTrue(mprophet.isDirectory());
		assertTrue(percolator.isDirectory());
		assertFalse(mprophet.equals(percolator));
		assertEquals(folder.getRoot(), mprophet.getParentFile());
		assertEquals(folder.getRoot(), percolator.getParentFile());
	}

	private File write(String name, String... lines) throws IOException {
		File file = new File(folder.getRoot(), name);
		try (PrintWriter out = new PrintWriter(file, "UTF-8")) {
			for (String line : lines) {
				out.println(line);
			}
		}
		return file;
	}
}
