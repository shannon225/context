package org.searlelab.context.io;

import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

import org.junit.Test;
import org.junit.Rule;
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

	}
}