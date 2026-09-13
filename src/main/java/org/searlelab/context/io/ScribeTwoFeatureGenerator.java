package org.searlelab.context.io;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import edu.washington.gs.maccoss.encyclopedia.ContextScribeTwoBridge;
import edu.washington.gs.maccoss.encyclopedia.algorithms.scribe.ScribeTwoDIAJobData;
import edu.washington.gs.maccoss.encyclopedia.algorithms.scribe.ScribeTwoScoringFactory;
import edu.washington.gs.maccoss.encyclopedia.algorithms.scribe.ScribeTwoSearchParameters;
import edu.washington.gs.maccoss.encyclopedia.filereaders.BlibToLibraryConverter;
import edu.washington.gs.maccoss.encyclopedia.filereaders.LibraryFile;
import edu.washington.gs.maccoss.encyclopedia.filereaders.LibraryInterface;
import edu.washington.gs.maccoss.encyclopedia.utils.threading.ProgressIndicator;

public final class ScribeTwoFeatureGenerator {

	// Library-based DIA library generation with required prealignment over reference and background peptides. 
	private ScribeTwoFeatureGenerator() {}

	public record Result(File features, File outputPrefix, ScribeTwoSearchParameters parameters) {}

	public static Result generate(File raw, File libraryFile, File fasta, Path outputDirectory, ScribeTwoSearchParameters parameters, ProgressIndicator progress) throws Exception {
		if (!raw.exists() || !raw.canRead()) throw new IOException("Unreadable raw input: " + raw);

		requireReadable(libraryFile);
		requireReadable(fasta);
		RawFiles.requireSupported(raw);
		requireJavaPot(ScribeTwoFeatureGenerator.class.getClassLoader());
		Files.createDirectories(outputDirectory);

		Path runDirectory = Files.createTempDirectory(outputDirectory, "scirbe2-");

		File prefix = runDirectory.resolve("full").toFile();
		LibraryInterface library = BlibToLibraryConverter.getFile(libraryFile, fasta, parameters);

		try {
			var job = new ScribeTwoDIAJobData(raw, fasta, library, prefix, new ScribeTwoScoringFactory(parameters), false);

			var result = ContextScribeTwoBridge.generate(progress, job);

			ContextFeatureScorer.readFeatures(result.features());
			Properties metadata = new Properties();
			
			metadata.setProperty("status", "complete");
			metadata.setProperty("engine", "scribe2");
			metadata.setProperty("dependency", "encyclopedia-6.6.24");
			metadata.setProperty("prealignment.trainingScope", "ALL");
			metadata.setProperty("prealignment.calibrated", "true");
			metadata.setProperty("massToleranceAdjusted", Boolean.toString(result.massTolerancesAdjusted()));
			metadata.setProperty("raw", raw.getCanonicalPath());
			metadata.setProperty("library", libraryFile.getCanonicalPath());
			metadata.setProperty("fasta", fasta.getCanonicalPath());
			metadata.setProperty("features", result.features().getCanonicalPath());
			
			parameters.toParameterMap().forEach((key,value) -> metadata.setProperty("requested." + key, value));
			result.parameters().toParameterMap().forEach((key, value) -> metadata.setProperty("effective." + key, value));
		
		try (Writer writer = Files.newBufferedWriter(runDirectory.resolve("generation.properties"))) {
			metadata.store(writer, "Feature geneation only. Context parititon/model stages run afterward");
		}
		return new Result(result.features(), prefix, result.parameters());

		} finally {
			if (library instanceof LibraryFile file) file.close();
		}
	}
	
	
	static void requireJavaPot(ClassLoader loader) throws IOException {
		try {
			Class.forName("org.searlelab.javapot.cli.JavaPotOptions",false, loader);
			
		} catch (ClassNotFoundException | LinkageError e) {
			throw new IOException("Scribe prealingment requires JavaPot runtime compatible with Encyclopedia 6.6.24."
					+ "JavaPotOptions is missing or cannot load. Add the maintainer's compatible JavaPot JAR and depenency to the classPath. No uncalibrated fallback was run.", e);
		}
	}
	
	private static void requireReadable(File file) throws IOException {
		if (!file.isFile() || !file.canRead()) throw new IOException("Unreadable input: " + file);
	}
}
