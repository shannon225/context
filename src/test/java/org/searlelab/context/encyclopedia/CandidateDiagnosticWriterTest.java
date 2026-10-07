package org.searlelab.context.encyclopedia;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.searlelab.msrawjava.model.FragmentScan;

import edu.washington.gs.maccoss.encyclopedia.algorithms.TDCPeptideScoringResult;
import edu.washington.gs.maccoss.encyclopedia.algorithms.library.EncyclopediaTwoDecoyStrategy;
import edu.washington.gs.maccoss.encyclopedia.datastructures.AminoAcidConstants;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryEntry;
import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchParameters;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;

public class CandidateDiagnosticWriterTest {
	@Rule public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void diagnosticDoesNotChangeWinnerOrCandidateOrder() throws Exception {
		SearchParameters parameters = SearchParameterParser.getDefaultParametersObject();
		AminoAcidConstants constants = parameters.getAAConstants();
		String sequence = "PEPTIDEK";
		LibraryEntry target = new LibraryEntry("synthetic CTX-016 diagnostic test", new HashSet<>(),
				constants.getChargedMass(sequence, (byte) 2), (byte) 2, sequence, 1, 10f, 1f,
				new double[] {250, 350}, new float[] {10, 5}, Optional.empty(), constants);
		LibraryEntry decoy = target.getDecoy(parameters);
		FragmentScan scan = new FragmentScan("run.dia", "scan", 1, target.getPrecursorMZ(), 600f, 1,
				null, 400d, 600d, new double[] {250, 350}, new float[] {10, 5}, new float[] {1, 1},
				(byte) 2, 400d, 600d);
		TDCPeptideScoringResult result = new TDCPeptideScoringResult();
		result.addStripe(target, 1f, new float[0], 0f, 0f, scan);
		result.addStripe(decoy, 2f, new float[0], 0f, 0f, scan);
		var first = result.getGoodMSMSCandidates().get(0);
		var second = result.getGoodMSMSCandidates().get(1);
		File output = new File(folder.getRoot(), "candidates.tsv");
		try (CandidateDiagnosticWriter writer = new CandidateDiagnosticWriter(output,
				EncyclopediaTwoDecoyStrategy.Mode.SHUFFLED)) {
			writer.accept(result.getGoodMSMSCandidates());
			writer.requireSuccess();
		}
		assertSame(first, result.getGoodMSMSCandidates().get(0));
		assertSame(second, result.getGoodMSMSCandidates().get(1));
		result.trim(1);
		assertSame(decoy, result.getGoodMSMSCandidates().get(0).getLibraryEntry());
		List<String> lines = Files.readAllLines(output.toPath());
		assertEquals(3, lines.size());
		assertTrue(lines.get(1).contains("\t1\ttrue\tSHUFFLED"));
		assertTrue(lines.get(2).contains("\t2\tfalse\tSHUFFLED"));
	}
}
