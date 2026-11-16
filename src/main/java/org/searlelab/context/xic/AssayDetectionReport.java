package org.searlelab.context.xic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Writes completed row evidence beside run outputs; caller holds the run's output lock. */
public final class AssayDetectionReport {
	private AssayDetectionReport() {}

	public static void write(Path effectiveAssay, Path output, List<AssayCandidateTracker.Detection> detections)
			throws IOException {
		if (Files.exists(output)) throw new IOException("Assay detection output already exists: " + output);
		List<String> source = Files.readAllLines(effectiveAssay, StandardCharsets.UTF_8);
		if (source.isEmpty() || source.size() - 1 != detections.size()) {
			throw new IOException("Assay detection count differs from effective assay rows");
		}
		String delimiter = effectiveAssay.toString().toLowerCase(Locale.ROOT).endsWith(".csv") ? "," : "\t";
		int columns = source.get(0).split(delimiter, -1).length;
		if (columns < 7) throw new IOException("Effective assay must have at least seven columns");
		var lines = new ArrayList<String>();
		lines.add(String.join("\t", source.get(0).split(delimiter, -1)) + "\tDetectionStatus\tDetectionReason");
		for (int i = 0; i < detections.size(); i++) {
			String[] fields = source.get(i + 1).split(delimiter, -1);
			if (fields.length != columns) throw new IOException("Inconsistent effective assay columns at row " + (i + 1));
			var detection = detections.get(i);
			if (detection == null || !List.of("DETECTED", "NOT_DETECTED", "NO_REFERENCE").contains(detection.status())
					|| detection.reason() == null || detection.reason().isBlank()
					|| detection.reason().indexOf('\t') >= 0 || detection.reason().indexOf('\n') >= 0
					|| detection.reason().indexOf('\r') >= 0) {
				throw new IOException("Incomplete or invalid assay detection at row " + (i + 1));
			}
			lines.add(String.join("\t", fields) + "\t" + detection.status() + "\t" + detection.reason());
		}
		Path temporary = Files.createTempFile(output.toAbsolutePath().getParent(), ".assay-detections-", ".tmp");
		try {
			Files.write(temporary, lines, StandardCharsets.UTF_8);
			Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}
}
