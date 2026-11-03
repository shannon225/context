package org.searlelab.context.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.searlelab.context.datastructures.IsolationWindow;
import org.searlelab.msrawjava.model.Range;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import edu.washington.gs.maccoss.encyclopedia.datastructures.AminoAcidConstants;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryEntry;

public class TargetedBootstrapperTest {

	@Rule
	public TemporaryFolder tempFolder = TemporaryFolder.builder().assureDeletion().build();

	@Test
	public void testIfBootstrapperCreatesExpectedFiles() throws Throwable {
		Path testDirectory = tempFolder.newFolder("targeted-bootstrapper-test").toPath();
		System.out.println("Test class loaded from: " + getClass().getProtectionDomain().getCodeSource().getLocation());

		URL libraryURL = Objects.requireNonNull(getClass().getResource("/org/searlelab/context/io/IL2_and_IL15_Combo.elib"), "Could not find the library resource.");
		String library = Paths.get(libraryURL.toURI()).toString();

		URL diaURL = Objects.requireNonNull(getClass().getResource("/org/searlelab/context/io/IL2A_GPFDIA_0combined_masked0_assay.dia"), "Could not find the DIA file resource.");
		Path sourceDIAPath = Paths.get(diaURL.toURI());

		Path testDIAPath = testDirectory.resolve("IL2A_GPFDIA_0combined.dia");

		Files.copy(sourceDIAPath, testDIAPath, StandardCopyOption.REPLACE_EXISTING);
		TargetedBootstrapper bootstrapper = new TargetedBootstrapper();
		bootstrapper.execute(library, testDIAPath.toString(), 1, 1, 0.5f, 1.0, 1);
		
		Path expectedOutput = testDirectory.resolve("IL2A_GPFDIA_0combined_masked1_assay.txt");
		assertTrue("The bootstrapper did not create the expected output.", Files.exists(expectedOutput));
		assertTrue(Files.exists(testDirectory.resolve("IL2A_GPFDIA_0combined_masked1_entrapment_assignments.tsv")));

	}

	@Test
	public void multipleSeedsLeaveNoTemporaryLibraryCopies() throws Throwable {
		Path testDirectory = tempFolder.newFolder("targeted-bootstrapper-temp-test").toPath();
		String library = Paths.get(Objects.requireNonNull(getClass().getResource("/org/searlelab/context/io/IL2_and_IL15_Combo.elib")).toURI()).toString();
		Path testDIAPath = testDirectory.resolve("IL2A_GPFDIA_0combined.dia");
		Files.copy(Paths.get(Objects.requireNonNull(getClass().getResource("/org/searlelab/context/io/IL2A_GPFDIA_0combined_masked0_assay.dia")).toURI()), testDIAPath);
		Set<String> before = temporaryLibraryCopies();

		new TargetedBootstrapper().execute(library, testDIAPath.toString(), 3, 1, 0.5f, 1.0, 1);

		Set<String> leftover = temporaryLibraryCopies();
		leftover.removeAll(before);
		assertTrue("Temporary library copies were left behind: " + leftover, leftover.isEmpty());
		for (int seed = 1; seed <= 3; seed++) {
			assertTrue(Files.exists(testDirectory.resolve("IL2A_GPFDIA_0combined_masked" + seed + "_assay.txt")));
		}
	}

	private static Set<String> temporaryLibraryCopies() throws IOException {
		try (Stream<Path> files = Files.list(Paths.get(System.getProperty("java.io.tmpdir")))) {
			return files.map(p -> p.getFileName().toString()).filter(n -> n.startsWith("encyclopedia_") && n.endsWith(".elib"))
					.collect(Collectors.toCollection(HashSet::new));
		}
	}

	// Synthetic entrapment tests: RT half-window 0.5 min (30 s), m/z half-window 1.0. A target at 1800 s blocks
	// candidates within 90 s of it; candidates at 3600 s are RT-eligible.
	private static final AminoAcidConstants CONSTANTS = new AminoAcidConstants();
	private static final float HALF_RT = 0.5f;
	private static final double HALF_MZ = 1.0;

	@Test
	public void choosesMzMatchedEntrapmentOverEarlierOffMassEntry() throws Exception {
		IsolationWindow target = target("TARGETK", 500.0, 1800f);
		ArrayList<IsolationWindow> unmatched = new ArrayList<>();

		ArrayList<IsolationWindow> entrapments = selectDecoys(List.of(target), unmatched, 1,
				entry("OFFMASSK", 400.0, (byte) 2, 3600f), entry("NEARBYK", ppm(500.0, 8), (byte) 2, 3600f));

		assertEquals(List.of("NEARBYK"), compounds(entrapments));
		assertTrue(unmatched.isEmpty());
	}

	@Test
	public void rejectsIneligibleCandidatesInsideTheWindow() throws Exception {
		IsolationWindow first = target("TARGETAK", 500.0, 1800f);
		IsolationWindow second = target("TARGETBK", ppm(500.0, 2), 600f);
		ArrayList<IsolationWindow> unmatched = new ArrayList<>();

		ArrayList<IsolationWindow> entrapments = selectDecoys(List.of(first, second), unmatched, 1,
				entry("WRONGCHARGEK", ppm(500.0, 1), (byte) 3, 3600f),
				entry("RTOVERLAPK", ppm(500.0, 1), (byte) 2, 1800f),
				entry("TARGETBK", ppm(500.0, 1), (byte) 2, 3600f),
				entry("SHAREDK", ppm(500.0, 3), (byte) 2, 3600f),
				entry("SECONDK", ppm(500.0, 30), (byte) 2, 3600f));

		assertEquals("A used entrapment is not reused for the next target", List.of("SHAREDK", "SECONDK"),
				compounds(entrapments));
		assertTrue(unmatched.isEmpty());
	}

	@Test
	public void choosesClosestCandidatesFirstWithLowerMzWinningTies() throws Exception {
		IsolationWindow target = target("TARGETK", 500.0, 1800f);

		ArrayList<IsolationWindow> entrapments = selectDecoys(List.of(target), new ArrayList<>(), 2,
				entry("PLUSFIVEK", ppm(500.0, 5), (byte) 2, 3600f), entry("PLUSTWOK", ppm(500.0, 2), (byte) 2, 3600f),
				entry("MINUSTWOK", ppm(500.0, -2), (byte) 2, 3600f));

		assertEquals(List.of("MINUSTWOK", "PLUSTWOK"), compounds(entrapments));
	}

	@Test
	public void widensToOneThousandPpmInclusiveThenRejectsTheTarget() throws Exception {
		IsolationWindow widened = target("WIDENEDK", 500.0, 1800f);
		IsolationWindow beyondDoubling = target("FINALSTEPK", 600.0, 1800f);
		IsolationWindow edge = target("EDGEK", 700.0, 1800f);
		IsolationWindow unmatchedTarget = target("UNMATCHEDK", 900.0, 1800f);
		ArrayList<IsolationWindow> unmatched = new ArrayList<>();

		ArrayList<IsolationWindow> entrapments = selectDecoys(List.of(widened, beyondDoubling, edge, unmatchedTarget), unmatched, 1,
				entry("ONEFIFTYK", ppm(500.0, 150), (byte) 2, 3600f),
				entry("EIGHTHUNDREDK", ppm(600.0, -800), (byte) 2, 3600f),
				entry("EXACTEDGEK", 700.0 + 700.0 * 1000 / 1_000_000, (byte) 2, 3600f),
				entry("BEYONDK", ppm(900.0, 1001), (byte) 2, 3600f));

		assertEquals(List.of("ONEFIFTYK", "EIGHTHUNDREDK", "EXACTEDGEK"), compounds(entrapments));
		assertEquals(List.of("UNMATCHEDK"), compounds(unmatched));
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsCandidatesNotSortedByMz() throws Exception {
		ArrayList<LibraryEntry> unsorted = new ArrayList<>(List.of(entry("HIGHK", 600.0, (byte) 2, 3600f),
				entry("LOWK", 500.0, (byte) 2, 3600f)));
		new TargetedBootstrapper().selectDecoys(new ArrayList<>(List.of(target("TARGETK", 500.0, 1800f))), CONSTANTS, 1,
				unsorted, HALF_RT, HALF_MZ, new ArrayList<>(), 1);
	}

	@Test
	public void fixtureEntrapmentsAreMzMatchedAndSeparatedFromTargets() throws Throwable {
		Path testDirectory = tempFolder.newFolder("targeted-bootstrapper-mz-test").toPath();
		String library = Paths.get(Objects.requireNonNull(getClass().getResource("/org/searlelab/context/io/IL2_and_IL15_Combo.elib")).toURI()).toString();
		Path testDIAPath = testDirectory.resolve("IL2A_GPFDIA_0combined.dia");
		Files.copy(Paths.get(Objects.requireNonNull(getClass().getResource("/org/searlelab/context/io/IL2A_GPFDIA_0combined_masked0_assay.dia")).toURI()), testDIAPath);

		new TargetedBootstrapper().execute(library, testDIAPath.toString(), 3, 5, HALF_RT, HALF_MZ, 2);

		for (int seed = 1; seed <= 3; seed++) {
			Map<String, String[]> assay = new HashMap<>();
			List<String> targets = new ArrayList<>();
			for (String line : Files.readAllLines(testDirectory.resolve("IL2A_GPFDIA_0combined_masked" + seed + "_assay.txt")).subList(1, 16)) {
				String[] fields = line.split("\t", -1);
				assay.put(fields[0], fields);
				if (fields[7].equals("false")) targets.add(fields[0]);
			}
			assertEquals(5, targets.size());
			List<String> assignments = Files.readAllLines(testDirectory.resolve("IL2A_GPFDIA_0combined_masked" + seed + "_entrapment_assignments.tsv"));
			assertEquals(11, assignments.size());
			Set<String> entrapmentsInSeed = new HashSet<>();
			for (String line : assignments.subList(1, assignments.size())) {
				String[] fields = line.split("\t", -1);
				String[] target = assay.get(fields[1]);
				String[] entrapment = assay.get(fields[3]);
				double targetMz = Double.parseDouble(target[3]);
				double entrapmentMz = Double.parseDouble(entrapment[3]);
				assertTrue("seed " + seed + " " + line, Math.abs(entrapmentMz - targetMz) <= targetMz * 1000 / 1_000_000);
				assertEquals(target[4], entrapment[4]);
				assertTrue(entrapmentsInSeed.add(fields[3]));
				assertFalse(targets.contains(fields[3]));
				for (String other : targets) {
					double otherMz = Double.parseDouble(assay.get(other)[3]);
					double rtGapMin = Math.abs(Double.parseDouble(entrapment[5]) - Double.parseDouble(assay.get(other)[5]));
					// The selector skips candidates within 3 RT half-windows of a target closer than 11 m/z.
					if (Math.abs(entrapmentMz - otherMz) <= 11 * HALF_MZ) {
						assertTrue("seed " + seed + " " + fields[3] + " vs " + other, rtGapMin > 3 * HALF_RT - 1e-3);
					}
				}
			}
		}
	}

	private static ArrayList<IsolationWindow> selectDecoys(List<IsolationWindow> targets, ArrayList<IsolationWindow> unmatched,
			int trapsPerTarget, LibraryEntry... libraryOrder) throws Exception {
		ArrayList<LibraryEntry> byMz = new ArrayList<>(List.of(libraryOrder));
		byMz.sort(Comparator.comparingDouble(LibraryEntry::getPrecursorMZ));
		return new TargetedBootstrapper().selectDecoys(new ArrayList<>(targets), CONSTANTS, 1, byMz, HALF_RT, HALF_MZ,
				unmatched, trapsPerTarget);
	}

	private static IsolationWindow target(String sequence, double mz, float rtSeconds) {
		return new IsolationWindow(sequence, mz, (byte) 2, rtSeconds - HALF_RT * 60f, rtSeconds + HALF_RT * 60f, false);
	}

	private static LibraryEntry entry(String sequence, double mz, byte charge, float rtSeconds) {
		return new LibraryEntry("synthetic Context test", new HashSet<>(), mz, charge, sequence, 1, rtSeconds, 1.0f,
				new double[] {250.0, 350.0}, new float[] {10.0f, 5.0f}, Optional.of(1.0f), CONSTANTS);
	}

	private static double ppm(double mz, double ppm) {
		return mz + mz * ppm / 1_000_000;
	}

	private static List<String> compounds(List<IsolationWindow> windows) {
		return windows.stream().map(IsolationWindow::getCompound).collect(Collectors.toList());
	}

	@Test
	public void warnsOnlyForIsolationWindowsWiderThanTwoMz() {
		// 2.0009 m/z matches the stored window width in the IL2 GPF fixture.
		assertFalse(TargetedBootstrapper.isolationWindowWarning(List.of(new Range(430.4455, 432.4464), new Range(432.4465, 434.4474)), "narrow.dia").isPresent());
		Optional<String> warning = TargetedBootstrapper.isolationWindowWarning(List.of(new Range(432.4465, 434.4474), new Range(457.4609, 473.4618)), "wide.dia");
		assertTrue(warning.isPresent());
		assertTrue(warning.get(), warning.get().contains("narrow (1-2 m/z) isolation windows") && warning.get().contains("wide.dia is 16.0 m/z"));
	}

	@Test
	public void writesOneToManyEmpiricalAssignmentsWithModifiedSequenceAndCharge() throws IOException {
		ArrayList<IsolationWindow> windows = new ArrayList<>();
		windows.add(new IsolationWindow("PEP[+16]TIDE", 500.0, (byte) 2, 0f, 60f, false));
		windows.add(new IsolationWindow("ENTRAPMENT_A", 501.0, (byte) 2, 0f, 60f, true));
		windows.add(new IsolationWindow("ENTRAPMENT_B", 502.0, (byte) 2, 0f, 60f, true));
		Path output = tempFolder.getRoot().toPath().resolve("assignments.tsv");

		new TargetedBootstrapper().writeEntrapmentAssignments(windows, 2, output);

		assertEquals(List.of(
				"relationType\ttargetPeptideModSeq\ttargetCharge\tentrapmentPeptideModSeq\tentrapmentCharge",
				"EMPIRICAL_ASSAY_ENTRAPMENT\tPEP[+16]TIDE\t2\tENTRAPMENT_A\t2",
				"EMPIRICAL_ASSAY_ENTRAPMENT\tPEP[+16]TIDE\t2\tENTRAPMENT_B\t2"), Files.readAllLines(output));
	}
}
