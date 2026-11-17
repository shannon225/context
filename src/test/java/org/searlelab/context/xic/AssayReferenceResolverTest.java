package org.searlelab.context.xic;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.searlelab.context.datastructures.IsolationWindow;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryPrecursorMetadata;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;

public class AssayReferenceResolverTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    @Test public void absentLibraryAndFastaIsAnErrorForTargetsAndEntrapments() throws Exception {
        var p = SearchParameterParser.getDefaultParametersObject();
        var fasta = temp.newFile("proteins.fasta");
        Files.writeString(fasta.toPath(), ">protein\nMABCDEFGK\n");
        var identities = new AssayIdentityResolver(List.of(), p.getAAConstants());
        for (boolean entrapment : new boolean[] {false, true}) {
            var assay = new IsolationWindow("PEPTIDEK", 500, (byte) 2, 100, 102, entrapment);
            var error = assertThrows(java.io.IOException.class, () -> AssayReferenceResolver.missingMetadata(
                    List.of(assay), List.of(), identities, fasta, p));
            assertTrue(error.getMessage().contains("Assay row 1 (PEPTIDEK, charge 2)"));
            assertTrue(error.getMessage().contains("absent from both"));
        }
    }
    @Test public void resolvesRealFastaAccessionsAndPreservesChargeIdentity() throws Exception {
        var p = SearchParameterParser.getDefaultParametersObject();
        var fasta = temp.newFile("proteins.fasta");
        Files.writeString(fasta.toPath(), ">first\nMPEPTIDEKA\n>second\nAPEPTIDEKM\n");
        var source = List.of(new LibraryPrecursorMetadata("PEPTIDEK", (byte) 2, List.of("source")));
        var identities = new AssayIdentityResolver(source, p.getAAConstants());
        var assay = new IsolationWindow("PEPTIDEK", 500, (byte) 3, 100, 102, false);
        var result = AssayReferenceResolver.missingMetadata(List.of(assay, assay), source, identities, fasta, p);
        assertEquals(1, result.size());
        assertEquals(3, result.get(0).getPrecursorCharge());
        assertEquals(List.of("first", "second"), result.get(0).getAccessions());
        assertTrue(AssayReferenceResolver.missingMetadata(List.of(new IsolationWindow("PEPTIDEK", 500,
                (byte) 2, 100, 102, false)), source, identities, temp.getRoot(), p).isEmpty());
    }
}
