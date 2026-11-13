package org.searlelab.context.percolator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.washington.gs.maccoss.encyclopedia.utils.Logger;

public class PyIsoPEPRunner {
	private static final String EXECUTABLE_NAME = "pyisopep";

	public static final String FDR_COLUMN = "pyIsoPEP FDR";
	public static final String Q_VALUE_COLUMN = "pyIsoPEP q-value from FDR";
	public static final String PEP_COLUMN = "pyIsoPEP PEP";

	/** The pyIsoPEP method the Context Percolator workflow uses ({@code --pepMethod}); q2pep, as in Context-MS, by default. */
	public enum PepMethod {
		Q2PEP("q2pep"), D2PEP("d2pep");

		private final String command;

		PepMethod(String command) {
			this.command = command;
		}

		public String command() {
			return command;
		}

		/** {@code null} (option absent) means {@link #Q2PEP}. */
		public static PepMethod parse(String value) {
			if (value == null) return Q2PEP;
			for (PepMethod method : values()) {
				if (method.command.equalsIgnoreCase(value.trim())) return method;
			}
			throw new IllegalArgumentException("--pepMethod must be q2pep or d2pep, not [" + value + "]");
		}
	}

	private final String executable;
	private final PepMethod pepMethod;

	public PyIsoPEPRunner(String executable) {
		this(executable, PepMethod.Q2PEP);
	}

	public PyIsoPEPRunner(String executable, PepMethod pepMethod) {
		this.executable = executable;
		this.pepMethod = pepMethod == null ? PepMethod.Q2PEP : pepMethod;
	}

	public PepMethod getPepMethod() {
		return pepMethod;
	}

	/** Runs this runner's PEP method ({@link #getPepMethod()}) on a concatenated target+decoy table. */
	public Table runPEP(File concatenatedInput, File outputFile, String scoreColumn, String labelColumn,
			String targetLabel, String decoyLabel) throws IOException, InterruptedException {
		return runConcatenated(pepMethod.command(), concatenatedInput, outputFile, scoreColumn, labelColumn, targetLabel,
				decoyLabel);
	}

	public Table runD2PEP(File concatenatedInput, File outputFile, String scoreColumn, String labelColumn,
			String targetLabel, String decoyLabel) throws IOException, InterruptedException {
		return runConcatenated("d2pep", concatenatedInput, outputFile, scoreColumn, labelColumn, targetLabel, decoyLabel);
	}

	/**
	 * pyIsoPEP {@code q2pep --calc-q-from-fdr} on a concatenated target+decoy table: q-values from target-decoy
	 * FDR, then PEPs from those q-values. These are the arguments Context-MS passes (the Context Percolator
	 * workflow's PEP method since CTX-018).
	 */
	public Table runQ2PEP(File concatenatedInput, File outputFile, String scoreColumn, String labelColumn,
			String targetLabel, String decoyLabel) throws IOException, InterruptedException {
		return runConcatenated("q2pep", concatenatedInput, outputFile, scoreColumn, labelColumn, targetLabel, decoyLabel);
	}

	static List<String> concatenatedArguments(String method, File concatenatedInput, File outputFile,
			String scoreColumn, String labelColumn, String targetLabel, String decoyLabel) {
		return Arrays.asList(
				method,
				"--cat-file", concatenatedInput.getAbsolutePath(),
				"--score-col", scoreColumn,
				"--label-col", labelColumn,
				"--target-label", targetLabel,
				"--decoy-label", decoyLabel,
				"--calc-q-from-fdr",
				"--output", outputFile.getAbsolutePath());
	}

	private Table runConcatenated(String method, File concatenatedInput, File outputFile, String scoreColumn,
			String labelColumn, String targetLabel, String decoyLabel) throws IOException, InterruptedException {

		List<String> arguments = concatenatedArguments(method, concatenatedInput, outputFile, scoreColumn, labelColumn,
				targetLabel, decoyLabel);

		List<String> command = buildCommand(arguments);

		Logger.logLine("Running pyIsoPEP: " + String.join(" ", command));
		run(command, outputFile);

		if (!outputFile.exists() || !outputFile.canRead()) {
			throw new IOException("pyIsoPEP reported success but wrote no output to "
					+ outputFile.getAbsolutePath());
		}

		Table table = Table.read(outputFile);
		for (String required : new String[] { Q_VALUE_COLUMN, PEP_COLUMN }) {
			if (table.indexOf(required) < 0) {
				throw new IOException("pyIsoPEP output " + outputFile.getName() + " has no [" + required
						+ "] column. Found: " + Arrays.toString(table.getHeader()));
			}
		}
		return table;
	}
	
	public Table runD2PEP(File targetInput,File decoyInput,File outputFile,String scoreColumn) throws IOException, InterruptedException {

	    List<String> arguments = Arrays.asList(
	            "d2pep",
	            "--target-file", targetInput.getAbsolutePath(),
	            "--decoy-file", decoyInput.getAbsolutePath(),
	            "--score-col", scoreColumn,
	            "--calc-q-from-fdr",
	            "--output", outputFile.getAbsolutePath());

	    List<String> command = buildCommand(arguments);
	    Logger.logLine("Running pyIsoPEP: " + String.join(" ", command));
	    run(command, outputFile);

	    if (!outputFile.exists() || !outputFile.canRead()) {
	        throw new IOException(
	                "pyIsoPEP reported success but wrote no output to "
	                        + outputFile.getAbsolutePath()
	        );
	    }

	    Table table = Table.read(outputFile);

	    for (String required : new String[] {Q_VALUE_COLUMN,PEP_COLUMN}) {
	        if (table.indexOf(required) < 0) {
	            throw new IOException("pyIsoPEP output " + outputFile.getName() + " has no [" + required + "] column. Found: " + Arrays.toString(table.getHeader())
	            );
	        }
	    }

	    return table;
	}

	private List<String> buildCommand(List<String> arguments) {
		String resolved = executable != null ? executable : findOnPath();
		if (resolved == null) resolved = EXECUTABLE_NAME;

		ArrayList<String> command = new ArrayList<>();
		command.add(resolved);
		command.addAll(arguments);
		return command;
	}

	private static String findOnPath() {
		String path = System.getenv("PATH");
		if (path == null) return null;
		for (String directory : path.split(File.pathSeparator)) {
			if (directory.isEmpty()) continue;
			File candidate = new File(directory, EXECUTABLE_NAME);
			if (candidate.isFile() && candidate.canExecute()) return candidate.getAbsolutePath();
		}
		return null;
	}

	private void run(List<String> command, File outputFile) throws IOException, InterruptedException {
		File log = logFileFor(outputFile);

		ProcessBuilder builder = new ProcessBuilder(command);
		builder.redirectErrorStream(true);
		builder.redirectOutput(ProcessBuilder.Redirect.to(log));

		Process process;
		try {
			process = builder.start();
		} catch (IOException ioe) {
			throw new IOException("Could not start pyIsoPEP [" + command.get(0) + "]. Install it with "
					+ "`pip install pyIsoPEP` and put it on PATH, or point Context at it with -pyisopep <path>. "
					+ "The Context container image already has it.", ioe);
		}

		int status = process.waitFor();
		if (status != 0) {
			throw new IOException("pyIsoPEP exited with status " + status + ". See " + log.getAbsolutePath()
					+ "\n" + tail(log, 20));
		}
	}

	public static File logFileFor(File outputFile) {
		String path = outputFile.getAbsolutePath();
		int dot = path.lastIndexOf('.');
		int separator = path.lastIndexOf(File.separatorChar);
		String stem = dot > separator + 1 ? path.substring(0, dot) : path;
		return new File(stem + ".log");
	}

	private static String tail(File file, int lines) {
		if (!file.exists() || !file.canRead()) return "";
		ArrayList<String> kept = new ArrayList<>();
		try (BufferedReader in = new BufferedReader(new FileReader(file))) {
			String line;
			while ((line = in.readLine()) != null) {
				kept.add(line);
				if (kept.size() > lines) kept.remove(0);
			}
		} catch (IOException ioe) {
			return "";
		}
		return String.join(System.lineSeparator(), kept);
	}

	public static class Table {
		private final String[] header;
		private final List<String[]> rows;
		private final Map<String, Integer> indexByName;

		Table(String[] header, List<String[]> rows) {
			this.header = header;
			this.rows = rows;
			this.indexByName = new HashMap<>();
			for (int i = 0; i < header.length; i++) {
				indexByName.putIfAbsent(header[i], i);
			}
		}

		public static Table read(File file) throws IOException {
			try (BufferedReader in = new BufferedReader(new FileReader(file))) {
				String headerLine = in.readLine();
				if (headerLine == null) {
					throw new IOException("pyIsoPEP output is empty: " + file.getAbsolutePath());
				}
				String[] header = headerLine.split("\t", -1);

				ArrayList<String[]> rows = new ArrayList<>();
				String line;
				while ((line = in.readLine()) != null) {
					if (line.trim().isEmpty()) continue;
					rows.add(line.split("\t", -1));
				}
				return new Table(header, rows);
			}
		}

		public String[] getHeader() {
			return header;
		}

		public List<String[]> getRows() {
			return rows;
		}

		public int size() {
			return rows.size();
		}

		public int indexOf(String columnName) {
			Integer index = indexByName.get(columnName);
			return index == null ? -1 : index;
		}

		public String get(String[] row, String columnName) {
			int index = indexOf(columnName);
			if (index < 0 || index >= row.length) return "";
			return row[index];
		}
	}
}
