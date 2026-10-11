package org.searlelab.context.percolator;

import java.io.File;
import java.util.HashMap;

import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;
import edu.washington.gs.maccoss.encyclopedia.utils.CommandLineParser;
import edu.washington.gs.maccoss.encyclopedia.utils.Logger;

import org.searlelab.context.io.DirectoryOptions;
import org.searlelab.context.io.RawFiles;

public class ContextPercolatorExecutorCLI {
	private static final float DEFAULT_FDR = ContextPercolator.DEFAULT_FDR;

	public static void main(String[] args) {
		HashMap<String, String> arguments = CommandLineParser.parseArguments(args);

		if (args.length == 0 || arguments.containsKey("-h") || arguments.containsKey("-help")
				|| arguments.containsKey("--help")) {
			printHelp();
			System.exit(args.length == 0 ? 1 : 0);
		}

		try {
			float fdr = Float.parseFloat(arguments.getOrDefault("-fdr", Float.toString(DEFAULT_FDR)));
			PyIsoPEPRunner pyIsoPEP = new PyIsoPEPRunner(arguments.get("-pyisopep"));

			boolean standardPercolator = DirectoryOptions.isEnabled(arguments, "-standardPercolator");
			
			if (standardPercolator) {
				File features = requiredFile(arguments, "-features");
				File fasta = requiredFile(arguments, "-f");
				
				File outputDirectory = DirectoryOptions.outputDirectory(arguments, features.getAbsoluteFile().getParentFile());
				
				String prefix = arguments.getOrDefault("-prefix", stripFeatureSuffix(features));
				
				HashMap<String, String> encyclopediaArgs = encyclopediaArguments(arguments);
				ContextPercolatorExecutor.runStandardPercolator(features, fasta, pyIsoPEP, fdr, outputDirectory, prefix, encyclopediaArgs, prefix, "standard");
				return;
			}
			

			if (arguments.containsKey("-background") && arguments.containsKey("-reference")) {
				File background = requiredFile(arguments, "-background");
				File reference = requiredFile(arguments, "-reference");
				File fasta = requiredFile(arguments, "-f");
				File outputDirectory = DirectoryOptions.outputDirectory(arguments,
						background.getAbsoluteFile().getParentFile());
				String prefix = arguments.getOrDefault("-prefix", DirectoryOptions.stripSplitSuffix(background));

				HashMap<String, String> encyclopediaArgs = encyclopediaArguments(arguments);

				ContextPercolator.trainAndApply(background, reference, fasta, encyclopediaArgs, pyIsoPEP, fdr,
						outputDirectory, prefix);
			} else {
				File dia = requiredFile(arguments, "-i");
				File library = requiredFile(arguments, "-l");
				File fasta = requiredFile(arguments, "-f");
				File massList = requiredFile(arguments, "-massList");
				File outputDirectory = DirectoryOptions.outputDirectory(arguments,
						dia.getAbsoluteFile().getParentFile());
				String prefix = arguments.get("-prefix");
				boolean generateDecoys = DirectoryOptions.isEnabled(arguments, "-generateDecoys");

				HashMap<String, String> encyclopediaArgs = encyclopediaArguments(arguments);

				ContextPercolatorExecutor.runContextPercolator(library, fasta, dia, massList, pyIsoPEP, fdr, outputDirectory, prefix, generateDecoys,
						encyclopediaArgs, 0);
			}
		} catch (Exception e) {
			Logger.errorLine("ContextPercolatorExecutor failed: " + e.getMessage());
			e.printStackTrace();
			System.exit(2);
		}
	}

	private static HashMap<String, String> encyclopediaArguments(HashMap<String, String> arguments) {
		HashMap<String, String> parameters = SearchParameterParser.getDefaultParameters();
		for (String contextFlag : new String[] { "-i", "-l", "-f", "-massList", "-fdr", "-o", "-outdir", "-prefix",
				"-background", "-reference", "-features", "-standardPercolator", "-pyisopep", "-generateDecoys", "-h", "-help", "--help" }) {
			arguments.remove(contextFlag);
		}
		parameters.putAll(arguments);
		return parameters;
	}

	private static String stripFeatureSuffix(File featureFile) {
	    return featureFile.getName()
	            .replaceFirst("\\.features\\.txt$", "");
	}

	private static File requiredFile(HashMap<String, String> arguments, String flag) {
		String value = arguments.get(flag);
		if (value == null) {
			Logger.errorLine("Missing required argument: " + flag);
			printHelp();
			System.exit(1);
		}
		return new File(value);
	}
	
	
	private static void printHelp() {
		Logger.timelessLogLine("ContextPercolatorExecutor");
		Logger.timelessLogLine("Run either standard Percolator cross-validation or Context-Percolator mode: train the discriminant on background peptides,");
		Logger.timelessLogLine("then transfer it to the targeted reference peptides without retraining.");
		Logger.timelessLogLine("Add -standardPercolator to run standard cross-validation.");
		Logger.timelessLogLine("Without it, the executor runs the Context background/reference workflow.");
		Logger.timelessLogLine("");
		Logger.timelessLogLine("Standard Percolator cross-validation:");
		Logger.timelessLogLine("  -standardPercolator enable standard Percolator mode");
		Logger.timelessLogLine("  -features <file>    complete, unsplit feature TSV");
		Logger.timelessLogLine("  -f        <file>    FASTA protein database");
		Logger.timelessLogLine("");
		Logger.timelessLogLine("End-to-end Context Percolator from a raw file:");
		Logger.timelessLogLine("  -i        <file>   acquisition: " + RawFiles.supportedExtensions());
		Logger.timelessLogLine("  -l        <file>   library (.elib preferred, .dlib accepted)");
		Logger.timelessLogLine("  -f        <file>   FASTA protein database");
		Logger.timelessLogLine("  -massList <file>   assay / mass-list .txt or .csv");
		Logger.timelessLogLine("");
		Logger.timelessLogLine("Or from feature files that were already split:");
		Logger.timelessLogLine("  -background <file> background feature TSV");
		Logger.timelessLogLine("  -reference  <file> reference feature TSV");
		Logger.timelessLogLine("  -f          <file> FASTA protein database");
		Logger.timelessLogLine("");
		Logger.timelessLogLine("Optional:");
		Logger.timelessLogLine("  -fdr      <float>  peptide FDR threshold (default: " + DEFAULT_FDR + ")");
		Logger.timelessLogLine("  -o        <dir>    output directory (default: next to the input)");
		Logger.timelessLogLine("  -prefix   <name>   base name for output files");
		Logger.timelessLogLine("  -generateDecoys    add entrapment decoys when the mass list has none;");
		Logger.timelessLogLine("                     a list that already has decoys is used unchanged");
		Logger.timelessLogLine("");
		Logger.timelessLogLine("pyIsoPEP, which estimates the q-values and PEPs for Context Percolator:");
		Logger.timelessLogLine("  -pyisopep <path>   pyisopep executable (default: look for it on PATH)");
		Logger.timelessLogLine("                     install with `pip install pyIsoPEP`;");
		Logger.timelessLogLine("                     the Context container image already has it");
		Logger.timelessLogLine("");
		Logger.timelessLogLine("Any other flag is passed through to EncyclopeDIA, for example");
		Logger.timelessLogLine("  -percolatorVersion <2|3|/path/to/percolator>");
		Logger.timelessLogLine("");
		Logger.timelessLogLine("Outputs, under <-o>/" + ContextPercolator.ENGINE_NAME + "/:");
		Logger.timelessLogLine("  <prefix>.peptide.reference.txt          peptide-level reference targets");
		Logger.timelessLogLine("  <prefix>.psm.reference.txt              PSM-level reference targets");
		Logger.timelessLogLine("  <prefix>.rescored_features.txt          reference features plus transferred score");
		Logger.timelessLogLine("  model/<prefix>.weights.txt              Percolator's native weights");
		Logger.timelessLogLine("  model/<prefix>.weights.averaged.txt     the same model as feature/weight pairs");
		Logger.timelessLogLine("  work/<prefix>.*.pyisopep.txt            pyIsoPEP's unedited reports");
		Logger.timelessLogLine("");
		Logger.timelessLogLine("and, when -generateDecoys had work to do, in <-o> itself:");
		Logger.timelessLogLine("  <assay>_td_assay.txt                    the assay it built");
	}
}
