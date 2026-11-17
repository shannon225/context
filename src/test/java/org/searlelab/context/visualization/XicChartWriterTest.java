package org.searlelab.context.visualization;

import static org.junit.Assert.*;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.searlelab.context.xic.AssayXic;
import org.searlelab.msrawjava.model.Range;

public class XicChartWriterTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void writesHeadlessPdfWithOriginalWindowAndPeakBoundsWithoutChangingTraces() throws Exception {
        System.setProperty("java.awt.headless", "true");
        var trace = new AssayXic.Trace("y3", 400, new float[] {60, 90, 120}, new float[] {0, 10, 0});
        var xic = new AssayXic(1, "PEPTIDEK", (byte) 2, 500, 150, 180, false, null, 3, List.of(), List.of(trace));
        File output = new File(temp.getRoot(), "xic.pdf");
        XicChartWriter.write(xic, output, Optional.of(new Range(75, 105)));
        assertTrue(Files.size(output.toPath()) > 100);
        assertTrue(new String(Files.readAllBytes(output.toPath()), java.nio.charset.StandardCharsets.ISO_8859_1).startsWith("%PDF"));
        assertArrayEquals(new float[] {60, 90, 120}, trace.seconds(), 0);
        try { XicChartWriter.write(xic, output, Optional.empty()); fail("Must preserve existing PDF"); }
        catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("already exists")); }
    }

    @Test public void rendersMissingReferenceWithoutInventingTraces() throws Exception {
        System.setProperty("java.awt.headless", "true");
        var xic = new AssayXic(1, "PEPTIDEK", (byte) 2, 500, 60, 120, true, "NO_EXACT_REFERENCE", 0, List.of(), List.of());
        File output = new File(temp.getRoot(), "empty.pdf");
        XicChartWriter.write(xic, output, Optional.empty());
        assertTrue(Files.size(output.toPath()) > 100);
    }
}
