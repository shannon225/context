import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.searlelab.context.encyclopedia.ContextEncyclopediaScoringFactory;
import org.searlelab.context.encyclopedia.EncyclopediaTwo;
import org.searlelab.context.encyclopedia.SearchPairSidecar;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.Range;
import edu.washington.gs.maccoss.encyclopedia.algorithms.*;
import edu.washington.gs.maccoss.encyclopedia.algorithms.library.EncyclopediaTwoJobData;
import edu.washington.gs.maccoss.encyclopedia.datastructures.*;
import edu.washington.gs.maccoss.encyclopedia.filereaders.*;
import edu.washington.gs.maccoss.encyclopedia.utils.Nothing;
import edu.washington.gs.maccoss.encyclopedia.utils.threading.EmptyProgressIndicator;

/** Read-only fixture search with one deliberately failed worker; no prediction or source mutation. */
public class ContextScoringFailureProbe {
    public static void main(String[] args) throws Exception {
        File root=new File(args[0]), out=new File(args[1]);
        if (!out.mkdirs()) throw new IllegalArgumentException("Use a new output directory");
        var parameters=SearchParameterParser.getDefaultParametersObject();
        AtomicInteger tasks=new AtomicInteger(), failed=new AtomicInteger();
        var factory=new ContextEncyclopediaScoringFactory(parameters) {
            @Override public AbstractLibraryScoringTask getScoringTask(PSMScorer scorer,
                    ArrayList<LibraryEntry> entries, ArrayList<FragmentScan> stripes, Range range,
                    float duty, PrecursorScanMap precursors, BlockingQueue<AbstractScoringResult> queue) {
                if(tasks.getAndIncrement()!=0) return super.getScoringTask(scorer,entries,stripes,range,duty,precursors,queue);
                return new AbstractLibraryScoringTask(scorer,entries,stripes,precursors,queue,parameters) {
                    @Override protected Nothing process() {
                        failed.incrementAndGet();
                        throw new IllegalStateException("Deliberate review probe: failed scoring worker");
                    }
                };
            }
        };
        var library=new LibraryFile();
        try {
            library.openFile(new File(root,"src/test/resources/org/searlelab/context/io/IL2_and_IL15_Combo.elib"));
            File raw=new File(root,"src/test/resources/org/searlelab/context/io/IL2A_GPFDIA_0combined_masked0_assay.dia");
            File fasta=new File(root,"src/test/resources/org/searlelab/context/mprophet/mus_musculus_reviewed_uniprot.fasta");
            var job=new EncyclopediaTwoJobData(raw,fasta,library,library,new File(out,"probe"),factory);
            var reader=job.getDiaFileReader();
            var pairs=new SearchPairSidecar();
            try {
                EncyclopediaTwo.generateFeatureFile(new EmptyProgressIndicator(false),library,job,reader,
                        Optional.empty(),OptionalInt.empty(),pairs::record);
            } finally {reader.close();}
            File features=job.getPercolatorFiles().getInputTSV();
            try(var lines=Files.lines(features.toPath())) {
                System.out.println("PROBE_RESULT returned_normally=true failed_workers="+failed.get()
                    +" submitted="+tasks.get()+" feature_rows="+(lines.count()-1));
            }
        } finally {library.close();}
    }
}
