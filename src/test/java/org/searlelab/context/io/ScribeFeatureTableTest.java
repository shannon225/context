package org.searlelab.context.io;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import edu.washington.gs.maccoss.encyclopedia.algorithms.scribe.ScribeTwoSearchParameters;

public class ScribeFeatureTableTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private static final String HEADER = "id\tLabel\tScanNr\tprimary\tprecursorMz\tRTinMin\tsequence\tProteins";

    private File table(String... rows) throws IOException {
        File file = temp.newFile();
        Files.writeString(file.toPath(), HEADER + "\n" + String.join("\n", rows) + "\n");
        return file;
    }

    @Test public void readsScoresWithoutUnusedEncyclopediaColumnAndPreservesProteinFields() throws Exception {
        var table = ContextFeatureScorer.readFeatures(table("run:1+3\t1\t1\t5\t478.2\t16.2\t-.PEPTIDEK.-\tP1\tP2"));
        assertEquals(1, table.rows().size());
        assertEquals(3, table.rows().get(0).getCharge());
        assertTrue(table.rows().get(0).getOriginalLine().endsWith("P1\tP2"));
    }

    @Test public void rejectsInvalidLabelsAndNonfiniteScoresWithRowContext() throws Exception {
        for (String row : List.of("a+2\t0\t1\t5\t400\t10\tPEPK\tP",
                "a+2\t1\t1\tNaN\t400\t10\tPEPK\tP",
                "a+2\t1")) {
            try {
                ContextFeatureScorer.readFeatures(table(row));
                fail("Should reject " + row);
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("row 2"));
            }
        }
    }

    @Test public void partitionsTargetsAndDecoysAfterBestRowSelectionAndPreservesCharge() throws Exception {
        File input = table("a+3\t1\t1\t5\t478\t16\t-.IGQVHHALDTTIK.-\tP1\tP2",
                "b+2\t1\t2\t2\t478\t16\t-.IGQVHHALDTTIK.-\tP1",
                "c+3\t-1\t3\t4\t478\t16\t-.IITTDLAHHVQGK.-\tDECOY_P1",
                "d+2\t1\t4\t3\t500\t20\t-.PEPTIDEK.-\tP3",
                "e+2\t-1\t5\t1\t500\t20\t-.EDITPEPK.-\tDECOY_P3");
        File assay = temp.newFile("assay.tsv");
        Files.writeString(assay.toPath(), "Compound\tFormula\tAdduct\tm/z\tz\tRT Time (min)\tWindow (min)\tisDecoy\n"
                + "IGQVHHALDTTIK\t\t(no adduct)\t478\t3\t16\t0.5\tfalse\n"
                + "IITTDLAHHVQGK\t\t(no adduct)\t478\t3\t16\t0.5\ttrue\n");
        String prefix = new File(temp.getRoot(), "split").getAbsolutePath();
        var rows = ContextFeatureScorer.partitionFeatures(input, ScribeTwoSearchParameters.getDefaultParametersObject(), prefix, assay);
        assertEquals(4, rows.size());
        assertEquals(2, rows.stream().filter(row -> !row.isBackground()).count());
        assertEquals(3, rows.get(0).getCharge());
        var reference = Files.readAllLines(new File(prefix + "_reference.features.txt").toPath());
        var background = Files.readAllLines(new File(prefix + "_background.features.txt").toPath());
        assertEquals(HEADER, reference.get(0));
        assertEquals(reference.get(0), background.get(0));
        assertEquals(3, reference.size());
        assertEquals(3, background.size());
        assertTrue(reference.get(1).endsWith("P1\tP2"));
        assertTrue(reference.stream().anyMatch(row -> row.startsWith("c+3\t-1")));
    }

    @Test public void missingRuntimeHasActionableError() throws Exception {
        try {
            ScribeTwoFeatureGenerator.requireJavaPot(new ClassLoader(null) {});
            fail("Missing runtime should fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("JavaPot"));
            assertTrue(expected.getMessage().contains("no uncalibrated fallback"));
        }
    }

    @Test public void packagedDependencyIsOnTestClasspath() throws Exception {
        ScribeTwoFeatureGenerator.requireJavaPot(getClass().getClassLoader());
    }
}