package org.searlelab.context.io;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import org.searlelab.context.datastructures.IsolationWindow;
import org.searlelab.context.encyclopedia.ContextPredictionStore;
import org.searlelab.context.encyclopedia.ContextWorkingLibraryPreparer;
import org.searlelab.context.xic.AssayIdentityResolver;
import org.searlelab.msrawjava.model.Range;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryPrecursorMetadata;
import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchParameters;
import edu.washington.gs.maccoss.encyclopedia.filereaders.LibraryFile;

/** Cached assay-only references; never modifies the shared search library. */
final class AssaySupplement {
    record Resolved(File library, String artifact, Map<String, String> failures) {}

    static Resolved resolve(LibraryFile source, List<LibraryPrecursorMetadata> sourceMetadata,
            List<LibraryPrecursorMetadata> fastaMetadata, List<IsolationWindow> assays,
            AssayIdentityResolver identities, SharedWorkingLibrary.Resolved shared, String inputIdentity,
            File storeDirectory, SearchParameters parameters, ContextWorkingLibraryPreparer.Settings settings,
            boolean clearStaleLock) throws IOException, SQLException, DataFormatException {
        var metadata = new HashMap<String, LibraryPrecursorMetadata>();
        var reserved = new HashSet<String>();
        for (var item : sourceMetadata) {
            metadata.put(key(item.getPeptideModSeq(), item.getPrecursorCharge()), item);
            reserved.add(item.getPeptideModSeq());
        }
        for (var item : fastaMetadata) metadata.put(key(item.getPeptideModSeq(), item.getPrecursorCharge()), item);
        var requested = new LinkedHashMap<String, LibraryPrecursorMetadata>();
        var blockedKeys = new HashSet<>(metadata.keySet());
        boolean shuffled;
        var prepared = new LibraryFile();
        try {
            prepared.openFile(shared.library());
            var available = new HashSet<String>();
            for (var item : prepared.getUniqueTargetPrecursorMetadata()) available.add(key(item.getPeptideModSeq(), item.getPrecursorCharge()));
            for (var assay : assays) {
                String sequence = identities.resolve(assay.getCompound(), assay.getCharge());
                String key = key(sequence, assay.getCharge());
                if (!available.contains(key)) {
                    var item = metadata.get(key);
                    if (item == null) throw new IOException("Missing validated assay metadata for " + key);
                    requested.putIfAbsent(key, item);
                }
            }
            if (requested.isEmpty()) return null;
            var strategy = edu.washington.gs.maccoss.encyclopedia.algorithms.library.EncyclopediaTwoDecoyStrategy.resolve(prepared, parameters);
            shuffled = !strategy.usesPredictedDecoys();
            var selectedDecoys = new ArrayList<edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryEntry>();
            if (shuffled) {
                for (var target : prepared.getAllEntries(false, parameters.getAAConstants())) selectedDecoys.add(target.getDecoy(parameters));
            } else selectedDecoys.addAll(prepared.getMappedDecoyEntries(new Range(0, Float.MAX_VALUE), false, parameters.getAAConstants()).values());
            for (var decoy : selectedDecoys) {
                String key = key(decoy.getPeptideModSeq(), decoy.getPrecursorCharge());
                if (requested.containsKey(key)) throw new IOException("Assay precursor " + key
                        + " collides with an existing background search decoy; cannot supplement without changing existing pairs.");
                reserved.add(decoy.getPeptideModSeq());
                blockedKeys.add(key);
            }
        } finally { prepared.close(); }
        String identity = ContextFeatureCacheIdentity.withWorkingLibrary(inputIdentity,
                shared.libraryArtifact() + "|assay-supplement-v1");
        var result = SharedWorkingLibrary.resolve(storeDirectory, "assay-reference", identity, (library, assignments) -> {
            try (var store = ContextPredictionStore.open(storeDirectory, settings, clearStaleLock);
                    var built = ContextWorkingLibraryPreparer.prepareAssays(source, new ArrayList<>(requested.values()),
                            reserved, parameters, settings, library, assignments, store)) {
                // Closing the returned library does not alter the saved artifact.
            }
        });
        if (shuffled) {
            var supplement = new LibraryFile();
            try {
                supplement.openFile(result.library());
                for (var target : supplement.getAllEntries(false, parameters.getAAConstants())) {
                    var decoy = target.getDecoy(parameters);
                    String key = key(decoy.getPeptideModSeq(), decoy.getPrecursorCharge());
                    if (!blockedKeys.add(key)) throw new IOException("Supplemental shuffled decoy " + key
                            + " collides with a target or existing search decoy; cannot change the run's decoy strategy.");
                }
            } finally { supplement.close(); }
        }
        return new Resolved(result.library(), result.libraryArtifact(), failures(result.predictions()));
    }

    private static Map<String, String> failures(File file) throws IOException {
        var failures = new HashMap<String, String>();
        try (var reader = java.nio.file.Files.newBufferedReader(file.toPath())) {
            String header = reader.readLine();
            if (header == null || !header.startsWith("targetKey\tdecoyKey\tstatus\treason\t"))
                throw new IOException("Invalid assay preparation report: " + file);
            for (String line; (line = reader.readLine()) != null;) {
                String[] fields = line.split("\t", -1);
                if (fields.length != 9) throw new IOException("Malformed assay preparation row: " + file);
                if (fields[2].equals("EXCLUDED")) failures.put(fields[0], "REFERENCE_PREPARATION_FAILED: " + fields[3]);
            }
        }
        return Map.copyOf(failures);
    }

    private static String key(String sequence, byte charge) { return ContextPredictionStore.key(sequence, charge); }
}
