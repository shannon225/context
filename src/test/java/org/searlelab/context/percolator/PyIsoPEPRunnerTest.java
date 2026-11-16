package org.searlelab.context.percolator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PyIsoPEPRunnerTest {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void readsAColumnByName() throws IOException {
		File file = write("out.tsv",
				"id\tscore\tpyIsoPEP PEP",
				"psm1\t2.5\t0.01",
				"psm2\t1.5\t0.20");

		PyIsoPEPRunner.Table table = PyIsoPEPRunner.Table.read(file);

		assertEquals(2, table.size());
		assertEquals(0, table.indexOf("id"));
		assertEquals(2, table.indexOf("pyIsoPEP PEP"));
		assertEquals("0.01", table.get(table.getRows().get(0), "pyIsoPEP PEP"));
	}

	@Test
	public void missingColumnsReadAsEmptyRatherThanThrowing() throws IOException {
		File file = write("out.tsv", "id\tscore", "psm1\t2.5");

		PyIsoPEPRunner.Table table = PyIsoPEPRunner.Table.read(file);

		assertEquals(-1, table.indexOf("pyIsoPEP PEP"));
		assertEquals("", table.get(table.getRows().get(0), "pyIsoPEP PEP"));
	}

	@Test
	public void rejectsAnEmptyOutputFile() throws IOException {
		try {
			PyIsoPEPRunner.Table.read(write("out.tsv"));
			fail("expected an empty pyIsoPEP output to be rejected");
		} catch (IOException expected) {
			assertTrue(expected.getMessage().contains("empty"));
		}
	}

	@Test
	public void theConsoleLogReplacesTheReportsExtension() {
		File report = new File(folder.getRoot(), "run.psm.pyisopep.txt");

		assertEquals(new File(folder.getRoot(), "run.psm.pyisopep.log"), PyIsoPEPRunner.logFileFor(report));
	}

	@Test
	public void aReportWithNoExtensionStillGetsALogBesideIt() {
		File report = new File(folder.getRoot(), "report");

		assertEquals(new File(folder.getRoot(), "report.log"), PyIsoPEPRunner.logFileFor(report));
	}

	@Test
	public void explainsHowToInstallPyIsoPEPWhenItCannotBeStarted() throws Exception {
		File input = write("in.tsv", "id\tLabel\tscore", "psm1\t1\t2.5");

		try {
			new PyIsoPEPRunner(new File(folder.getRoot(), "not-a-real-pyisopep").getAbsolutePath())
					.runD2PEP(input, new File(folder.getRoot(), "out.tsv"), "score", "Label", "1", "-1");
			fail("expected a missing pyIsoPEP executable to be reported");
		} catch (IOException expected) {
			assertTrue(expected.getMessage(), expected.getMessage().contains("pip install pyIsoPEP"));
		}
	}

	@Test
	public void runsD2PEPAndReportsTargetsWithQValuesAndPEPs() throws Exception {
		String pyisopep = locatePyIsoPEP();
		assumeTrue("pyIsoPEP is not installed; set PYISOPEP or put pyisopep on PATH", pyisopep != null);

		File input = folder.newFile("rescored.tsv");
		Random random = new Random(7);
		try (PrintWriter out = new PrintWriter(input, "UTF-8")) {
			out.println("id\tLabel\tscore\tsequence");
			for (int i = 0; i < 40; i++) {
				out.println("T" + i + "\t1\t" + (3.0 + random.nextGaussian()) + "\tPEPTIDE" + i + "K");
			}
			for (int i = 0; i < 40; i++) {
				out.println("D" + i + "\t-1\t" + random.nextGaussian() + "\tDECOYPEP" + i + "K");
			}
		}

		File output = new File(folder.getRoot(), "psm.pyisopep.txt");
		PyIsoPEPRunner.Table table = new PyIsoPEPRunner(pyisopep)
				.runD2PEP(input, output, "score", "Label", "1", "-1");

		assertEquals(40, table.size());
		assertTrue(table.indexOf(PyIsoPEPRunner.PEP_COLUMN) >= 0);
		assertTrue(table.indexOf(PyIsoPEPRunner.Q_VALUE_COLUMN) >= 0);
		assertTrue("input columns must survive", table.indexOf("sequence") >= 0);

		double previousPep = -1.0;
		for (String[] row : table.getRows()) {
			double pep = Double.parseDouble(table.get(row, PyIsoPEPRunner.PEP_COLUMN));
			assertTrue("PEP out of range: " + pep, pep >= 0.0 && pep <= 1.0);
			assertTrue("PEPs must come back sorted", pep >= previousPep - 1e-12);
			previousPep = pep;

			double q = Double.parseDouble(table.get(row, PyIsoPEPRunner.Q_VALUE_COLUMN));
			assertTrue("q-value out of range: " + q, q >= 0.0);
		}
	}

	@Test
	public void q2pepUsesContextMSArgumentsAndD2pepIsUnchanged() {
		File in = new File(folder.getRoot(), "in.tsv");
		File out = new File(folder.getRoot(), "out.tsv");
		java.util.List<String> tail = java.util.Arrays.asList("--cat-file", in.getAbsolutePath(), "--score-col", "score",
				"--label-col", "Label", "--target-label", "1", "--decoy-label", "-1", "--calc-q-from-fdr", "--output",
				out.getAbsolutePath());
		java.util.List<String> q2pep = new java.util.ArrayList<>(java.util.List.of("q2pep"));
		q2pep.addAll(tail);
		java.util.List<String> d2pep = new java.util.ArrayList<>(java.util.List.of("d2pep"));
		d2pep.addAll(tail);
		assertEquals(q2pep, PyIsoPEPRunner.concatenatedArguments("q2pep", in, out, "score", "Label", "1", "-1"));
		assertEquals(d2pep, PyIsoPEPRunner.concatenatedArguments("d2pep", in, out, "score", "Label", "1", "-1"));
	}

	@Test
	public void pepMethodDefaultsToQ2pepAndRejectsUnknownMethods() {
		assertEquals(PyIsoPEPRunner.PepMethod.Q2PEP, PyIsoPEPRunner.PepMethod.parse(null));
		assertEquals(PyIsoPEPRunner.PepMethod.Q2PEP, PyIsoPEPRunner.PepMethod.parse("q2pep"));
		assertEquals(PyIsoPEPRunner.PepMethod.D2PEP, PyIsoPEPRunner.PepMethod.parse(" D2PEP "));
		assertEquals(PyIsoPEPRunner.PepMethod.Q2PEP, new PyIsoPEPRunner(null).getPepMethod());
		assertEquals(PyIsoPEPRunner.PepMethod.Q2PEP, new PyIsoPEPRunner(null, null).getPepMethod());
		for (String value : new String[] { "", "tdc2pep", "qns2pep", "pep" }) {
			try {
				PyIsoPEPRunner.PepMethod.parse(value);
				fail("accepted " + value);
			} catch (IllegalArgumentException expected) {
				assertTrue(expected.getMessage(), expected.getMessage().contains("--pepMethod"));
			}
		}
	}

	/** A stand-in pyisopep that records its arguments, so the chosen method is checked without pyIsoPEP installed. */
	@Test
	public void runPEPCallsTheConfiguredMethod() throws Exception {
		File fake = folder.newFile("fake-pyisopep");
		File recorded = new File(folder.getRoot(), "arguments.txt");
		try (PrintWriter out = new PrintWriter(fake, "UTF-8")) {
			out.println("#!/bin/sh");
			out.println("echo \"$@\" > '" + recorded.getAbsolutePath() + "'");
			out.println("while [ \"$#\" -gt 0 ]; do if [ \"$1\" = --output ]; then output=\"$2\"; fi; shift; done");
			out.println("printf 'id\\t" + PyIsoPEPRunner.Q_VALUE_COLUMN + "\\t" + PyIsoPEPRunner.PEP_COLUMN + "\\nT1\\t0.01\\t0.02\\n' > \"$output\"");
		}
		assertTrue(fake.setExecutable(true));
		File input = write("in.tsv", "id\tLabel\tscore", "T1\t1\t2.5", "D1\t-1\t0.5");

		for (PyIsoPEPRunner.PepMethod method : PyIsoPEPRunner.PepMethod.values()) {
			PyIsoPEPRunner.Table table = new PyIsoPEPRunner(fake.getAbsolutePath(), method)
					.runPEP(input, new File(folder.getRoot(), method + ".tsv"), "score", "Label", "1", "-1");
			assertEquals(1, table.size());
			String arguments = new String(java.nio.file.Files.readAllBytes(recorded.toPath())).trim();
			assertTrue(arguments, arguments.startsWith(method.command() + " --cat-file "));
		}
		new PyIsoPEPRunner(fake.getAbsolutePath()).runPEP(input, new File(folder.getRoot(), "default.tsv"), "score",
				"Label", "1", "-1");
		assertTrue(new String(java.nio.file.Files.readAllBytes(recorded.toPath())).startsWith("q2pep "));
	}

	@Test
	public void runsQ2PEPAndReportsTargetsWithQValuesAndPEPs() throws Exception {
		String pyisopep = locatePyIsoPEP();
		assumeTrue("pyIsoPEP is not installed; set PYISOPEP or put pyisopep on PATH", pyisopep != null);

		File input = folder.newFile("rescored-q2pep.tsv");
		Random random = new Random(11);
		try (PrintWriter out = new PrintWriter(input, "UTF-8")) {
			out.println("id\tLabel\tscore\tsequence");
			for (int i = 0; i < 60; i++) out.println("T" + i + "\t1\t" + (3.0 + random.nextGaussian()) + "\tPEPTIDE" + i + "K");
			for (int i = 0; i < 60; i++) out.println("D" + i + "\t-1\t" + random.nextGaussian() + "\tDECOYPEP" + i + "K");
		}
		PyIsoPEPRunner.Table table = new PyIsoPEPRunner(pyisopep)
				.runQ2PEP(input, new File(folder.getRoot(), "psm.q2pep.txt"), "score", "Label", "1", "-1");

		assertEquals("targets only", 60, table.size());
		assertTrue("input columns must survive", table.indexOf("sequence") >= 0);
		for (String[] row : table.getRows()) {
			double pep = Double.parseDouble(table.get(row, PyIsoPEPRunner.PEP_COLUMN));
			double q = Double.parseDouble(table.get(row, PyIsoPEPRunner.Q_VALUE_COLUMN));
			assertTrue("PEP out of range: " + pep, pep >= 0.0 && pep <= 1.0);
			assertTrue("q-value out of range: " + q, q >= 0.0 && q <= 1.0);
		}
	}

	@Test
	public void surfacesPyIsoPEPFailures() throws Exception {
		String pyisopep = locatePyIsoPEP();
		assumeTrue("pyIsoPEP is not installed; set PYISOPEP or put pyisopep on PATH", pyisopep != null);

		File input = write("bad.tsv", "id\tLabel\tscore", "psm1\tQQQ\t2.5");

		try {
			new PyIsoPEPRunner(pyisopep)
					.runD2PEP(input, new File(folder.getRoot(), "out.tsv"), "score", "Label", "1", "-1");
			fail("expected pyIsoPEP to reject unrecognised labels");
		} catch (IOException expected) {
			assertTrue(expected.getMessage(), expected.getMessage().contains("exited with status"));
		}
	}

	private static String locatePyIsoPEP() {
		String configured = System.getenv("PYISOPEP");
		if (configured != null && new File(configured).canExecute()) return configured;

		String path = System.getenv("PATH");
		if (path == null) return null;
		for (String directory : path.split(File.pathSeparator)) {
			if (directory.isEmpty()) continue;
			File candidate = new File(directory, "pyisopep");
			if (candidate.isFile() && candidate.canExecute()) return candidate.getAbsolutePath();
		}
		return null;
	}

	private File write(String name, String... lines) throws IOException {
		File file = folder.newFile(name);
		try (PrintWriter out = new PrintWriter(file, "UTF-8")) {
			for (String line : lines) {
				out.println(line);
			}
		}
		return file;
	}
}
