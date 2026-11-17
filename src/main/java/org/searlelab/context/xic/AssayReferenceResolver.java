package org.searlelab.context.xic;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import org.searlelab.context.datastructures.IsolationWindow;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryPrecursorMetadata;
import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchParameters;
import edu.washington.gs.maccoss.encyclopedia.filereaders.FastaReader;
import edu.washington.gs.maccoss.encyclopedia.utils.massspec.PeptideUtils;

/** Resolve real protein annotations for assay precursors absent from the source catalog. */
public final class AssayReferenceResolver {
    private AssayReferenceResolver() {}

    public static List<LibraryPrecursorMetadata> missingMetadata(List<IsolationWindow> assays,
            List<LibraryPrecursorMetadata> source, AssayIdentityResolver identities,
            File fasta, SearchParameters parameters) throws IOException {
        var known = new HashSet<String>();
        for (var precursor : source) known.add(identities.resolve(precursor.getPeptideModSeq(),
                precursor.getPrecursorCharge()) + "|" + precursor.getPrecursorCharge());
        var missing = new ArrayList<LibraryPrecursorMetadata>();
        List<edu.washington.gs.maccoss.encyclopedia.datastructures.FastaEntryInterface> proteins = null;
        for (int i = 0; i < assays.size(); i++) {
            var assay = assays.get(i);
            String sequence = identities.resolve(assay.getCompound(), assay.getCharge());
            String key = sequence + "|" + assay.getCharge();
            if (known.contains(key)) continue;
            if (proteins == null) {
                if (!fasta.isFile() || !fasta.canRead()) throw new IOException("Unreadable assay FASTA: " + fasta);
                // Read first so the dependency parser cannot turn an I/O failure into partial success.
                proteins = FastaReader.readFasta(java.nio.file.Files.readString(fasta.toPath()), fasta.getName(), parameters);
                if (proteins == null || proteins.isEmpty()) throw new IOException("No proteins parsed from assay FASTA: " + fasta);
            }
            String peptide = PeptideUtils.getPeptideSeq(sequence);
            var accessions = new TreeSet<String>();
            if (!peptide.isEmpty()) for (var protein : proteins) {
                if (protein.getSequence().contains(peptide) && protein.getAccession() != null
                        && !protein.getAccession().isBlank()) accessions.add(protein.getAccession());
            }
            if (accessions.isEmpty()) throw new IOException("Assay row " + (i + 1) + " (" + assay.getCompound()
                    + ", charge " + assay.getCharge() + ") is absent from both the source library and supplied FASTA; "
                    + "cannot assign a reference without inventing protein annotations.");
            missing.add(new LibraryPrecursorMetadata(sequence, assay.getCharge(), accessions));
            known.add(key);
        }
        return List.copyOf(missing);
    }
}
