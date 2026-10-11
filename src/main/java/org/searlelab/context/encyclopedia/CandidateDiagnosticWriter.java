package org.searlelab.context.encyclopedia;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import edu.washington.gs.maccoss.encyclopedia.algorithms.ScoredPSM;
import edu.washington.gs.maccoss.encyclopedia.algorithms.library.EncyclopediaTwoDecoyStrategy;

/** Optional pre-trim diagnostic stream; it never feeds the feature writer. */
public final class CandidateDiagnosticWriter implements AutoCloseable {
	private final BufferedWriter writer;
	private final AtomicReference<IOException> failure = new AtomicReference<>();
	private final EncyclopediaTwoDecoyStrategy.Mode mode;

	public CandidateDiagnosticWriter(File output, EncyclopediaTwoDecoyStrategy.Mode mode) throws IOException {
		if (output.exists()) throw new IOException("Candidate diagnostic already exists: " + output);
		this.writer = Files.newBufferedWriter(output.toPath(), StandardCharsets.UTF_8);
		this.mode = mode;
		writer.write("modifiedSequence\tcharge\tisDecoy\tRTinMin\tprimary\trank\twinner\tmode");
		writer.newLine();
	}

	public synchronized void accept(List<ScoredPSM> candidates) {
		if (failure.get() != null || candidates.isEmpty()) return;
		ArrayList<ScoredPSM> ordered = new ArrayList<>(candidates);
		Collections.sort(ordered);
		Collections.reverse(ordered);
		try {
			for (int i = 0; i < ordered.size(); i++) {
				ScoredPSM candidate = ordered.get(i);
				writer.write(candidate.getLibraryEntry().getPeptideModSeq() + "\t"
						+ candidate.getLibraryEntry().getPrecursorCharge() + "\t"
						+ candidate.getLibraryEntry().isDecoy() + "\t"
						+ candidate.getMSMS().getScanStartTime() / 60f + "\t"
						+ candidate.getPrimaryScore() + "\t" + (i + 1) + "\t" + (i == 0) + "\t" + mode);
				writer.newLine();
			}
		} catch (IOException e) {
			failure.compareAndSet(null, e);
		}
	}

	public synchronized void requireSuccess() throws IOException {
		IOException error = failure.get();
		if (error != null) throw new IOException("Failed to write candidate diagnostics", error);
		writer.flush();
	}

	@Override
	public synchronized void close() throws IOException {
		writer.close();
		IOException error = failure.get();
		if (error != null) throw new IOException("Failed to write candidate diagnostics", error);
	}
}
