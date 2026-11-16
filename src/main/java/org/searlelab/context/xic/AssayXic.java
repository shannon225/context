package org.searlelab.context.xic;

import java.util.List;

/** Per-row extraction evidence. Times are seconds; extraction alone does not imply DETECTED. */
public record AssayXic(int assayRow, String sequence, byte charge, double precursorMz,
		float startSeconds, float stopSeconds, boolean entrapment, String referenceProblem,
		int ms2ScanCount, List<Trace> precursors, List<Trace> fragments) {
	public AssayXic {
		precursors = List.copyOf(precursors);
		fragments = List.copyOf(fragments);
	}

	public record Trace(String name, double mz, float[] seconds, float[] intensities) {
		public Trace {
			seconds = seconds.clone();
			intensities = intensities.clone();
			if (seconds.length != intensities.length) throw new IllegalArgumentException("Mismatched trace arrays");
		}
		@Override public float[] seconds() { return seconds.clone(); }
		@Override public float[] intensities() { return intensities.clone(); }
	}
}
