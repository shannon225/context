package org.searlelab.context.io;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.searlelab.context.mprophet.ContextMProphetExecutor;
import edu.washington.gs.maccoss.encyclopedia.algorithms.scribe.ScribeTwoSearchParameters;

public class ScribeCalibratedWorkflowTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    static File resource(String path) throws Exception {
        return new File(ScribeCalibratedWorkflowTest.class.getResource(path).toURI());
    }

    @Test public void realCalibratedTableSupportsPartitionAndBackgroundModelApplication() throws Exception {
        File features = resource("/org/searlelab/context/scribe/hela-calibrated.features.txt");
        File assay = resource("/org/searlelab/context/scribe/assay.tsv");
        File fasta = temp.newFile("proteins.fasta"); // TSV model fitting uses the row's protein annotations.
        Files.writeString(fasta.toPath(), ">fixture\nLEAAIAEAEER\n");
        String prefix = new File(temp.getRoot(), "full").getAbsolutePath();
        var parameters = ScribeTwoSearchParameters.getDefaultParametersObject();
        var parsed = ContextFeatureScorer.readFeatures(features);
        assertEquals(117, parsed.rows().size()); // Fixed captured table, not a search yield promise.
        assertTrue(parsed.header().contains("\tdeltaRTScore\tms1MassErrorScore\tms2MassErrorScore\t"));
        var partitioned = ContextFeatureScorer.partitionFeatures(features, parameters, prefix, assay);
        assertEquals(49, partitioned.size());
        assertTrue(partitioned.stream().anyMatch(row -> !row.isBackground() && row.isDecoy()));
        assertTrue(partitioned.stream().anyMatch(row -> row.isBackground() && row.isDecoy()));
        var results = ContextMProphetExecutor.trainAndApplyFeature(new File(prefix + "_background.features.txt"),
                new File(prefix + "_reference.features.txt"), fasta, parameters);
        assertSame(results.background().getLDA(), results.reference().getLDA());
        assertEquals(results.background().getFeatureNames(), results.reference().getFeatureNames());
        for (String population : new String[] { "background", "reference" }) {
            var output = new File(prefix + "_" + population + ".features.pep.output.txt");
            assertTrue(output.isFile());
            for (String row : Files.readAllLines(output.toPath())) {
                if (row.startsWith("PSMId") || row.startsWith("pi_0=")) continue;
                String[] values = row.split("\t");
                assertTrue(Float.isFinite(Float.parseFloat(values[1])));
                for (int i = 2; i <= 3; i++) {
                    float probability = Float.parseFloat(values[i]);
                    assertTrue(Float.isFinite(probability) && probability >= 0 && probability <= 1);
                }
            }
        }
    }
}