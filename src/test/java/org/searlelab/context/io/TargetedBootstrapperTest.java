package org.searlelab.context.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.searlelab.context.datastructures.IsolationWindow;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

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
