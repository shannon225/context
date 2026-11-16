package org.searlelab.context.xic;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.searlelab.context.datastructures.IsolationWindow;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;

public class AssayDetectionReportTest {
	@Rule public TemporaryFolder temporary = new TemporaryFolder();

	@Test public void preservesSevenAndEightColumnRowsAndRefusesOverwrite() throws Exception {
		for (boolean entrapmentColumn : new boolean[] {false, true}) {
			var input = temporary.newFile(entrapmentColumn ? "assay.txt" : "assay.csv").toPath();
			String delimiter = entrapmentColumn ? "\t" : ",";
			String header = "Compound,Formula,Adduct,m/z,z,RT,Window" + (entrapmentColumn ? ",isDecoy" : "");
			String a = "PEPTIDEK,,,500.000,2,1.70,0.10" + (entrapmentColumn ? ",false" : "");
			String b = "PEDITPEK,,,500.010,2,1.71,0.11" + (entrapmentColumn ? ",true" : "");
			Files.write(input, List.of(header.replace(",", delimiter), a.replace(",", delimiter), b.replace(",", delimiter)));
			var output = input.resolveSibling(input.getFileName() + ".detections.tsv");
			var detections = List.of(new AssayCandidateTracker.Detection("DETECTED", "SEARCH_SCORED;TDC_LOSER"),
					new AssayCandidateTracker.Detection("NO_REFERENCE", "NO_EXACT_REFERENCE"));
			AssayDetectionReport.write(input, output, detections);
			var rows = Files.readAllLines(output);
			assertEquals(3, rows.size());
			assertEquals(header.replace(',', '\t') + "\tDetectionStatus\tDetectionReason", rows.get(0));
			assertEquals(a.replace(',', '\t') + "\tDETECTED\tSEARCH_SCORED;TDC_LOSER", rows.get(1));
			assertEquals(b.replace(',', '\t') + "\tNO_REFERENCE\tNO_EXACT_REFERENCE", rows.get(2));
			assertThrows(java.io.IOException.class, () -> AssayDetectionReport.write(input, output, detections));
			assertEquals(rows, Files.readAllLines(output));
		}
	}

	@Test public void incompleteEvidenceNeverPublishesAReport() throws Exception {
		var input = temporary.newFile("assay.tsv").toPath();
		Files.write(input, List.of("Compound\tFormula\tAdduct\tm/z\tz\tRT\tWindow", "PEPTIDEK\t\t\t500\t2\t1.7\t0.1"));
		var output = input.resolveSibling("detections.tsv");
		assertThrows(java.io.IOException.class, () -> AssayDetectionReport.write(input, output, List.of()));
		assertFalse(Files.exists(output));
		assertThrows(java.io.IOException.class, () -> AssayDetectionReport.write(input, output,
				List.of(new AssayCandidateTracker.Detection("DETECTED", ""))));
		assertFalse(Files.exists(output));
	}

	@Test public void statusPrecedenceDoesNotHideScoringFailures() {
		var tracker = new AssayCandidateTracker(List.of(new IsolationWindow("PEPTIDEK", 500, (byte) 2, 100, 102, false)),
				SearchParameterParser.getDefaultParametersObject());
		var row = tracker.rows().get(0);
		assertEquals("NO_REFERENCE", tracker.detection(row, "NO_EXACT_REFERENCE", 0).status());
		assertEquals("NOT_DETECTED", tracker.detection(row, null, 0).status());
		assertThrows(IllegalStateException.class, () -> tracker.detection(row, null, 1));
	}
}
