package org.searlelab.context.visualization;

import java.awt.Color;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;

import org.jfree.chart.plot.ValueMarker;
import org.searlelab.context.io.IsolationWindowReader;
import org.searlelab.context.xic.AssayIdentityResolver;
import org.searlelab.context.xic.AssayXic;
import org.searlelab.context.xic.AssayXicExtractor;
import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;
import org.searlelab.msrawjava.model.Range;

import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;
import edu.washington.gs.maccoss.encyclopedia.filereaders.LibraryFile;
import edu.washington.gs.maccoss.encyclopedia.gui.general.Charter;
import edu.washington.gs.maccoss.encyclopedia.utils.graphing.GraphType;
import edu.washington.gs.maccoss.encyclopedia.utils.graphing.XYTrace;

/** Optional, manually invoked QC; never called by scoring or the CLI dispatcher. */
public final class XicChartWriter {
    private XicChartWriter() {}

    /** Peak bounds, when supplied by a caller, are in seconds; extraction does not infer them. */
    public static void write(AssayXic xic, File output, Optional<Range> peakBounds) throws IOException {
        if (output.exists()) throw new IOException("QC output already exists: " + output);
        var traces = new ArrayList<XYTrace>();
        for (var group : java.util.List.of(xic.precursors(), xic.fragments())) {
            for (var trace : group) {
                float[] minutes = trace.seconds();
                for (int i = 0; i < minutes.length; i++) minutes[i] /= 60f;
                traces.add(new XYTrace(minutes, trace.intensities(), GraphType.line, trace.name()));
            }
        }
        var chart = Charter.getChart("Retention time (min)", "Intensity", true,
                traces.toArray(new XYTrace[0])).getChart();
        String state = xic.referenceProblem() != null ? xic.referenceProblem()
                : xic.ms2ScanCount() == 0 ? "No applicable MS2 scans" : "Extraction only; not a confidence result";
        chart.setTitle(xic.sequence() + " / " + xic.charge() + " — " + state);
        var plot = chart.getXYPlot();
        double start = xic.startSeconds() / 60.0, end = xic.stopSeconds() / 60.0;
        double visibleStart = start, visibleEnd = end;
        for (var group : java.util.List.of(xic.precursors(), xic.fragments())) {
            for (var trace : group) for (float time : trace.seconds()) {
                visibleStart = Math.min(visibleStart, time / 60.0);
                visibleEnd = Math.max(visibleEnd, time / 60.0);
            }
        }
        double margin = Math.max((visibleEnd - visibleStart) * 0.05, 0.01);
        plot.getDomainAxis().setRange(visibleStart - margin, visibleEnd + margin);
        mark(plot, start, "Assay start", Color.GRAY);
        mark(plot, end, "Assay end", Color.GRAY);
        if (peakBounds.isPresent()) {
            Range peak = peakBounds.get();
            if (!Float.isFinite(peak.getStart()) || !Float.isFinite(peak.getStop())
                    || peak.getStart() > peak.getStop()) {
                throw new IllegalArgumentException("Peak bounds must be finite and ordered");
            }
            mark(plot, peak.getStart() / 60.0, "Peak start", Color.RED);
            mark(plot, peak.getStop() / 60.0, "Peak end", Color.RED);
        }
        Charter.writeAsPDF(chart, output, new Dimension(900, 550));
        // The dependency writer does not expose checked errors; do not report a missing PDF as success.
        if (!output.isFile() || Files.size(output.toPath()) == 0) throw new IOException("No PDF written: " + output);
    }

    private static void mark(org.jfree.chart.plot.XYPlot plot, double time, String label, Color color) {
        var marker = new ValueMarker(time);
        marker.setLabel(label);
        marker.setPaint(color);
        plot.addDomainMarker(marker);
    }

    /** Usage: raw.dia assay.txt prepared.elib output-directory [exact assay sequences ...]. */
    public static void main(String[] args) throws Exception {
        if (args.length < 4) throw new IllegalArgumentException("Usage: raw.dia assay.txt prepared.elib output-directory [peptides ...]");
        System.setProperty("java.awt.headless", "true");
        var parameters = SearchParameterParser.getDefaultParametersObject();
        var windows = IsolationWindowReader.parseMassList(args[1]);
        var requested = new HashSet<>(Arrays.asList(args).subList(4, args.length));
        var found = new HashSet<String>();
        File directory = new File(args[3]);
        Files.createDirectories(directory.toPath());
        var raw = new EncyclopeDIAFile();
        var library = new LibraryFile();
        try {
            raw.openFile(new File(args[0]));
            library.openFile(new File(args[2]));
            var identities = new AssayIdentityResolver(library.getUniqueTargetPrecursorMetadata(), parameters.getAAConstants());
            for (int i = 0; i < windows.size(); i++) {
                var window = windows.get(i);
                if (!requested.isEmpty() && !requested.contains(window.getCompound())) continue;
                found.add(window.getCompound());
                var canonical = identities.resolve(window);
                var entries = library.getEntries(canonical.getCompound(), window.getCharge(), false);
                entries.removeIf(e -> e.isDecoy() || !e.getPeptideModSeq().equals(canonical.getCompound()));
                if (entries.size() > 1) throw new IOException("Multiple references at assay row " + (i + 1));
                var xic = AssayXicExtractor.extract(i + 1, window, entries.isEmpty() ? null : entries.get(0), raw, parameters, identities);
                write(xic, new File(directory, "assay-" + (i + 1) + ".pdf"), Optional.empty());
            }
            if (!found.containsAll(requested)) {
                requested.removeAll(found);
                throw new IllegalArgumentException("Requested sequences absent from assay: " + requested);
            }
        } finally {
            try { raw.close(); } finally { library.close(); }
        }
    }
}
