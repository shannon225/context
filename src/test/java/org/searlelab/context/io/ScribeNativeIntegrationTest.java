package org.searlelab.context.io;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import edu.washington.gs.maccoss.encyclopedia.algorithms.scribe.ScribeTwoSearchParameters;
import edu.washington.gs.maccoss.encyclopedia.utils.threading.EmptyProgressIndicator;

/** Opt in with -Dscribe.integrationDir containing input.dia, library.elib, proteins.fasta. */
public class ScribeNativeIntegrationTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void nativePrealignmentAndFullCalibratedExport() throws Exception {
        String directory = System.getProperty("scribe.integrationDir");
        assumeNotNull(directory);
        Path source = Path.of(directory);
        Path inputs = temp.newFolder("inputs").toPath();
        for (String name : new String[] { "input.dia", "library.elib", "proteins.fasta" }) {
            Files.copy(source.resolve(name), inputs.resolve(name));
        }
        var settings = ScribeTwoSearchParameters.getDefaultParameters();
        settings.put("-numberOfThreadsUsed", "2");
        var result = ScribeTwoFeatureGenerator.generate(inputs.resolve("input.dia").toFile(),
                inputs.resolve("library.elib").toFile(), inputs.resolve("proteins.fasta").toFile(),
                temp.newFolder("output").toPath(), ScribeTwoSearchParameters.parseParameters(settings),
                new EmptyProgressIndicator());
        var table = ContextFeatureScorer.readFeatures(result.features());
        assertTrue(table.rows().stream().anyMatch(row -> row.isDecoy()));
        assertTrue(table.rows().stream().anyMatch(row -> !row.isDecoy()));
        assertTrue(table.header().contains("\tdeltaRTScore\t"));
        Properties manifest = new Properties();
        try (var reader = Files.newBufferedReader(result.outputPrefix().toPath().getParent().resolve("generation.properties"))) {
            manifest.load(reader);
        }
        assertEquals("complete", manifest.getProperty("status"));
        assertEquals("ALL", manifest.getProperty("prealignment.trainingScope"));
        assertEquals("true", manifest.getProperty("prealignment.calibrated"));
        assertEquals("true", manifest.getProperty("massTolerancesAdjusted"));
        // The native sample includes peptides in our later reference assay and background.
        Path preliminary;
        try (var paths = Files.walk(result.outputPrefix().toPath().getParent())) {
            preliminary = paths.filter(path -> path.toString().endsWith(".prealign.features.txt")).findFirst().orElseThrow();
        }
        var preliminaryRows = ContextFeatureScorer.readFeatures(preliminary.toFile()).rows();
        assertTrue(preliminaryRows.stream().anyMatch(row -> row.getSequence().contains("LEAAIAEAEER")));
        assertTrue(preliminaryRows.stream().anyMatch(row -> row.getSequence().contains("IVQISGNSMPR")));
        assertTrue(table.rows().size() > preliminaryRows.size());
    }
}