package org.searlelab.context.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Properties;

import org.searlelab.context.percolator.TrainingSeeds;

import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;

// This class is designed to record details of seed experiments
final class SeedExperiment {

	private final Path manifest;
	private final Properties values = new Properties();
	
	static Path directory(File root, String prefix, int seed) {
		
		TrainingSeeds.requireValid(seed);
		
		if (root == null) throw new IllegalArgumentException("Output directory must not be null.");
		if (prefix == null || prefix.isBlank() || prefix.equals(".") || prefix.equals("..") || prefix.contains("/") || prefix.contains("\\")) {
			throw new IllegalArgumentException("Experiment prefix must be one non-empty directory name.");
		}
		
		return root.toPath().resolve(prefix).resolve("seed_" + seed);
	}
	
	static void requireFresh(File root, String prefix, int seed) throws IOException {
		Path directory = directory(root, prefix, seed);
		if (Files.exists(directory,  LinkOption.NOFOLLOW_LINKS)) {
			throw new IOException("Seed output already exists: " + directory + ". Use a new output root for repeating the experiment.");	
		}
	}
	
	SeedExperiment(Path directory, int seed, float fdr, File all, File background, File reference, File fasta, HashMap<String, String> parameters) throws IOException {
		
		manifest = directory.resolve("run.properties");
		
		values.setProperty("seed", Integer.toString(seed));
		values.setProperty("fdr", Float.toString(fdr));
		values.setProperty("java.version",System.getProperty("java.version"));
		values.setProperty("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
		values.setProperty("context.version", String.valueOf(AllWorkflowExecutorCLI.class.getPackage().getImplementationVersion()));
		
		HashMap<String, String> effective = SearchParameterParser.getDefaultParameters();
		if (parameters != null) effective.putAll(parameters);;
		effective.put("-percolatorThreshold", Float.toString(fdr));
		effective.forEach((key,Value) -> values.setProperty("parameter." + key,  String.valueOf(Value)));
		
		input("complete", all);
		input("background", background);
		input("reference", reference);
		input("fasta", fasta);
		
		values.setProperty("status", "RUNNING");
		save();
	}
	
	private void input(String name, File file) throws IOException {
		values.setProperty("input." + name, file.getCanonicalPath());
		
		try (InputStream input = Files.newInputStream(file.toPath())) {
			
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[65536];
			int count;
			while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
			values.setProperty("sha256." + name, HexFormat.of().formatHex(digest.digest()));
			
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
}
	
	void complete(AllWorkflowResult result) throws IOException {
		values.setProperty("percolator.version", result.getStandardPercolator().getPercolatorExecutableVersion().orElse("unknown"));
		values.setProperty("mprophet.context.coefficients", Arrays.toString(result.getContextMProphet().getLDA().getCoefficients()));
		values.setProperty("mprophet.standard.coefficients", Arrays.toString(result.getStandardMProphet().getLDA().getCoefficients()));
		values.setProperty("mprophet.target.coefficients", Arrays.toString(result.getTargetMProphet().getLDA().getCoefficients()));
		values.setProperty("status", "COMPLETE");
		save();
	}

	private void save() throws IOException {
		try (var out = Files.newBufferedWriter(manifest)) {
			values.store(out,"Training seed experiment, archive the build and logs alongside this record.");
		}
	}

	 void fail(Exception error) throws IOException {
		values.setProperty("status", "FAILED");
		values.setProperty("error", error.toString());
		save();
		
	}


}
