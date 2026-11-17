package org.searlelab.context.io;

import static org.junit.Assert.*;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.searlelab.context.datastructures.IsolationWindow;
import org.searlelab.context.encyclopedia.*;
import org.searlelab.context.xic.AssayIdentityResolver;
import edu.washington.gs.maccoss.encyclopedia.algorithms.prediction.AminoAcidEncoding;
import edu.washington.gs.maccoss.encyclopedia.datastructures.*;
import edu.washington.gs.maccoss.encyclopedia.filereaders.*;
import edu.washington.gs.maccoss.encyclopedia.filewriters.web.DecoyPeptideGenerator;

public class AssaySupplementTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void supplementsFromStoreAndReusesWithoutChangingSharedLibrary() throws Exception {
        var p = SearchParameterParser.getDefaultParametersObject();
        var aa = p.getAAConstants();
        var settings = new ContextWorkingLibraryPreparer.Settings(27, (byte) 2, true);
        var source = new LibraryFile();
        source.openFile();
        try {
            source.addEntries(new ArrayList<>(List.of(entry("AAAAAAAAK", aa))), false);
            source.createDecoyEntriesTable();
            File sharedFile = new File(temp.getRoot(), "shared.elib");
            source.saveAsFile(sharedFile);
            String hash = ContextFeatureCacheIdentity.artifact(sharedFile);
            var shared = new SharedWorkingLibrary.Resolved(sharedFile, new File("unused.tsv"), hash);
            var sourceMetadata = source.getUniqueTargetPrecursorMetadata();
            var identities = new AssayIdentityResolver(sourceMetadata, aa);
            var missing = List.of(new LibraryPrecursorMetadata("PEPTIDEK", (byte) 2, List.of("actual-protein")));
            var assays = List.of(new IsolationWindow("PEPTIDEK", 500, (byte) 2, 100, 102, false));
            String decoy = DecoyPeptideGenerator.getUniqueDecoyPeptideModSeq("PEPTIDEK",
                    AminoAcidEncoding.reverse(AminoAcidEncoding.getAAs("PEPTIDEK", aa).clone()),
                    new HashSet<>(List.of("AAAAAAAAK", "PEPTIDEK")), aa).orElseThrow();
            File storeDir = temp.newFolder("predictions");
            try (var store = ContextPredictionStore.open(storeDir, settings, false)) {
                store.add(new ArrayList<>(List.of(entry("PEPTIDEK", aa), entry(decoy, aa))));
            }
            var first = AssaySupplement.resolve(source, sourceMetadata, missing, assays, identities,
                    shared, "a".repeat(64), storeDir, p, settings, false);
            assertNotNull(first);
            assertTrue(first.failures().isEmpty());
            var second = AssaySupplement.resolve(source, sourceMetadata, missing, assays, identities,
                    shared, "a".repeat(64), storeDir, p, settings, false);
            assertEquals(first, second);
            assertEquals(hash, ContextFeatureCacheIdentity.artifact(sharedFile));
            var supplement = new LibraryFile();
            try {
                supplement.openFile(first.library());
                assertEquals(1, supplement.countUniqueTargetPrecursors());
                assertEquals(1, supplement.countUniqueDecoyPrecursors());
                assertEquals(List.of("actual-protein"), supplement.getUniqueTargetPrecursorMetadata().get(0).getAccessions());
            } finally { supplement.close(); }
            // Exercise the complete raw orchestration with cache-only predictions and the existing DIA fixture.
            try (var store = ContextPredictionStore.open(storeDir, settings, false)) {
                var basePredictions = new ArrayList<>(List.of(entry("AAAAAAAAK", aa)));
                var baseDecoy = DecoyPeptideGenerator.getUniqueDecoyPeptideModSeq("AAAAAAAAK",
                        AminoAcidEncoding.reverse(AminoAcidEncoding.getAAs("AAAAAAAAK", aa).clone()),
                        new HashSet<>(List.of("AAAAAAAAK")), aa);
                if (baseDecoy.isPresent()) basePredictions.add(entry(baseDecoy.get(), aa));
                store.add(basePredictions);
            }
            File raw = new File(temp.getRoot(), "raw.dia");
            Files.copy(new File(getClass().getResource("/org/searlelab/context/io/IL2A_GPFDIA_0combined_masked0_assay.dia").toURI()).toPath(), raw.toPath());
            File fasta = temp.newFile("proteins.fasta");
            Files.writeString(fasta.toPath(), ">actual-protein\nMPEPTIDEKA\n");
            // The approved strict-source rule also applies to automatically generated assay entrapments.
            for (boolean eighthColumn : new boolean[] {false, true}) {
                File targetOnly = temp.newFile("target-only-" + eighthColumn + "_assay.txt");
                Files.writeString(targetOnly.toPath(), "compound\tc1\tc2\tmz\tcharge\trt\twidth"
                        + (eighthColumn ? "\tisDecoy" : "") + "\nPEPTIDEK\t-\t-\t500\t2\t10\t2"
                        + (eighthColumn ? "\tfalse" : "") + "\n");
                String invalidBase = new File(temp.getRoot(), "target-only-" + eighthColumn).getAbsolutePath();
                var error = assertThrows(java.io.IOException.class, () -> ContextFeatureScorer.scoreFeaturesForContext(
                        sharedFile, raw, fasta, invalidBase, targetOnly.getAbsolutePath(), 0,
                        new ContextFeatureScorer.Options(27, (byte) 2, true, false, false, storeDir, false)));
                assertTrue(error.getMessage().contains("absent from both"));
                assertFalse(new File(invalidBase + "_assay.detections.tsv").exists());
            }
            File assayFile = temp.newFile("run_assay.txt");
            Files.writeString(assayFile.toPath(), "compound\tc1\tc2\tmz\tcharge\trt\twidth\tisDecoy\nPEPTIDEK\t-\t-\t"
                    + aa.getChargedMass("PEPTIDEK", (byte) 2) + "\t2\t10\t2\ttrue\n");
            String base = new File(temp.getRoot(), "raw-output").getAbsolutePath();
            for (String suffix : List.of("_reference.features.txt", "_background.features.txt",
                    "_reference.features.txt.pairs.tsv", "_background.features.txt.pairs.tsv")) {
                var existing = new File(base + suffix).toPath();
                Files.writeString(existing, "preserve this output");
                var error = assertThrows(java.io.IOException.class, () -> ContextFeatureScorer.scoreFeaturesForContext(
                        sharedFile, raw, fasta, base, assayFile.getAbsolutePath(), 0,
                        new ContextFeatureScorer.Options(27, (byte) 2, true, false, false, storeDir, false)));
                assertTrue(error.getMessage().contains("Unknown existing split output"));
                assertEquals("preserve this output", Files.readString(existing));
                Files.delete(existing);
            }
            ContextFeatureScorer.scoreFeaturesForContext(sharedFile, raw, fasta, base, assayFile.getAbsolutePath(), 0,
                    new ContextFeatureScorer.Options(27, (byte) 2, true, false, false, storeDir, false));
            String report = Files.readString(new File(base + "_assay.detections.tsv").toPath());
            assertFalse(report, report.contains("NO_REFERENCE"));
            assertTrue(report, report.contains("\tDETECTED\t"));
            assertEquals(hash, ContextFeatureCacheIdentity.artifact(sharedFile));
            for (int charge : new int[] {2, 100}) {
                File emptyAssay = temp.newFile("empty-" + charge + "_assay.txt");
                Files.writeString(emptyAssay.toPath(), "compound\tc1\tc2\tmz\tcharge\trt\twidth\tisDecoy\n"
                        + "PEPTIDEK\t-\t-\t1\t" + charge + "\t10\t2\ttrue\n");
                String emptyBase = new File(temp.getRoot(), "empty-" + charge).getAbsolutePath();
                var options = new ContextFeatureScorer.Options(27, (byte) 2, true, false, false, storeDir, false);
                assertTrue(ContextFeatureScorer.scoreFeaturesForContext(sharedFile, raw, fasta, emptyBase,
                        emptyAssay.getAbsolutePath(), 0, options).isEmpty());
                String emptyReport = Files.readString(new File(emptyBase + "_assay.detections.tsv").toPath());
                assertTrue(emptyReport, emptyReport.contains(charge == 2 ? "NOT_DETECTED\tNO_APPLICABLE_MS2_SCANS"
                        : "NO_REFERENCE\tREFERENCE_PREPARATION_FAILED: unsupported target length or charge"));
                assertEquals(1, Files.readAllLines(new File(emptyBase + ".features.txt.pairs.tsv").toPath()).size());
                assertTrue(new File(emptyBase + ".features.txt.context.sha256").isFile());
                assertTrue(ContextFeatureScorer.scoreFeaturesForContext(sharedFile, raw, fasta, emptyBase,
                        emptyAssay.getAbsolutePath(), 0, options).isEmpty());
            }
            assertNull(AssaySupplement.resolve(source, sourceMetadata, List.of(),
                    List.of(new IsolationWindow("AAAAAAAAK", 500, (byte) 2, 100, 102, false)), identities,
                    shared, "a".repeat(64), storeDir, p, settings, false));
        } finally { source.close(); }
    }

    @Test public void refusesNewAssayTargetThatIsAnExistingShuffledDecoy() throws Exception {
        var p = SearchParameterParser.getDefaultParametersObject();
        var source = new LibraryFile(); source.openFile();
        try {
            var target = entry("PEPTIDEK", p.getAAConstants());
            source.addEntries(new ArrayList<>(List.of(target)), false);
            source.createDecoyEntriesTable();
            File file = new File(temp.getRoot(), "shuffled.elib"); source.saveAsFile(file);
            var decoy = target.getDecoy(p);
            var metadata = source.getUniqueTargetPrecursorMetadata();
            var missing = List.of(new LibraryPrecursorMetadata(decoy.getPeptideModSeq(), (byte) 2, List.of("real-protein")));
            var assay = List.of(new IsolationWindow(decoy.getPeptideModSeq(), decoy.getPrecursorMZ(), (byte) 2, 100, 102, true));
            var error = assertThrows(java.io.IOException.class, () -> AssaySupplement.resolve(source, metadata, missing, assay,
                    new AssayIdentityResolver(metadata, p.getAAConstants()), new SharedWorkingLibrary.Resolved(file,
                    new File("unused"), ContextFeatureCacheIdentity.artifact(file)), "b".repeat(64), temp.getRoot(), p,
                    new ContextWorkingLibraryPreparer.Settings(27, (byte) 2, true), false));
            assertTrue(error.getMessage().contains("existing background search decoy"));
        } finally { source.close(); }
    }

    private static LibraryEntry entry(String sequence, AminoAcidConstants aa) {
        var p = SearchParameterParser.getDefaultParametersObject();
        double[] masses = Arrays.stream(edu.washington.gs.maccoss.encyclopedia.utils.massspec.PeptideUtils
                .getPeptideModel(sequence, aa).getPrimaryIonObjects(p.getFragType(), (byte) 2, true))
                .mapToDouble(ion -> ion.getMass()).filter(mass -> mass > 200).distinct().sorted().limit(4).toArray();
        float[] intensities = new float[masses.length]; Arrays.fill(intensities, 10);
        return new LibraryEntry("synthetic", new HashSet<>(), aa.getChargedMass(sequence, (byte) 2),
                (byte) 2, sequence, 1, 100, 1, masses, intensities, Optional.empty(), aa);
    }
}
