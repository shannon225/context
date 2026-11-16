package org.searlelab.context.percolator;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

/** CTX018-001: the peptide-level report counts q-values and PEPs separately, at the same threshold. */
public class ContextPercolatorCountTest {

	private static PyIsoPEPRunner.Table table(String[]... rows) {
		return new PyIsoPEPRunner.Table(
				new String[] { "id", PyIsoPEPRunner.Q_VALUE_COLUMN, PyIsoPEPRunner.PEP_COLUMN }, Arrays.asList(rows));
	}

	@Test
	public void countsQValuesAndPEPsIndependentlyIncludingTheThreshold() {
		// q and PEP disagree on purpose: q-values from TDC can sit above PEPs and vice versa.
		PyIsoPEPRunner.Table report = table(
				new String[] { "a", "0.005", "0.0001" },
				new String[] { "b", "0.01", "0.02" },
				new String[] { "c", "0.0137", "0.01" },
				new String[] { "d", "0.2", "0.9" });

		assertEquals(2, ContextPercolator.countAtOrBelow(report, PyIsoPEPRunner.Q_VALUE_COLUMN, 0.01f));
		assertEquals(2, ContextPercolator.countAtOrBelow(report, PyIsoPEPRunner.PEP_COLUMN, 0.01f));
		assertEquals(0, ContextPercolator.countAtOrBelow(report, PyIsoPEPRunner.PEP_COLUMN, 0.00001f));
	}

	@Test
	public void anEmptyReportCountsNothing() {
		assertEquals(0, ContextPercolator.countAtOrBelow(table(), PyIsoPEPRunner.PEP_COLUMN, 0.01f));
	}

	@Test
	public void aNonNumericValueIsSkippedWithAWarningAndTheRunContinues() {
		// Legacy behavior kept on purpose: a bad value in one report must not stop a multi-file experiment.
		PyIsoPEPRunner.Table report = table(
				new String[] { "a", "0.001", "NA?" },
				new String[] { "b", "0.001", "0.001" },
				new String[] { "c", "0.001", "" });
		assertEquals(1, ContextPercolator.countAtOrBelow(report, PyIsoPEPRunner.PEP_COLUMN, 0.01f));
		assertEquals(3, ContextPercolator.countAtOrBelow(report, PyIsoPEPRunner.Q_VALUE_COLUMN, 0.01f));
	}
}
