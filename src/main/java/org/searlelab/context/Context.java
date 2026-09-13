package org.searlelab.context;

import java.util.Arrays;

import org.searlelab.context.io.ContextFeatureScorer;
import org.searlelab.context.io.MassListDecoyGenerator;
import org.searlelab.context.io.ScribeTwoFeatureScorerCLI;
import org.searlelab.context.io.TargetedBootstrapperCLI;
import org.searlelab.context.percolator.ContextPercolatorExecutorCLI;
import org.searlelab.context.io.ContextFeatureScorerCLI;

public class Context {

	public static void main(String[] args) throws Throwable {
		if (args.length == 0 || isHelp(args[0])) {
			printHelp();
			System.exit(args.length == 0 ? 1 : 0);
		}

		String command = args[0];
		String[] remaining = Arrays.copyOfRange(args, 1, args.length);

		switch (command) {

		case "percolator":
			ContextPercolatorExecutorCLI.main(remaining);
			break;
		case "mprophet":
//				ContextMProphetExecutor.main(remaining);
			break;
		case "features":
			ContextFeatureScorer.execute(remaining);
			break;
		case "features-folder":
			ContextFeatureScorerCLI.main(remaining);
			break;
		case "scribe-features":
			ScribeTwoFeatureScorerCLI.run(remaining, false);
			break;
		case "scribe-context":
			ScribeTwoFeatureScorerCLI.run(remaining, true);
			break;
		case "bootstrap":
			TargetedBootstrapperCLI.main(remaining);
			break;
		case "decoys":
			MassListDecoyGenerator.main(remaining);
			break;
		default:
			System.err.println("Unknown command: " + command);
			printHelp();
			System.exit(1);
		}
	}

	private static boolean isHelp(String argument) {
		return "-h".equals(argument) || "-help".equals(argument) || "--help".equals(argument)
				|| "help".equals(argument);
	}

	private static void printHelp() {
		System.out.println("Context: confidence estimation for targeted proteomics, built on EncyclopeDIA.");
		System.out.println();
		System.out.println("Usage: java -jar context.jar <command> [options]");
		System.out.println();
		System.out.println("Commands:");
		System.out.println(
				"  percolator-context   train a Percolator SVM on the background, apply it to the reference peptides");
		System.out.println(
				"  percolator   train a Percolator SVM model and apply it to the dataset using cross-validation");
		System.out
				.println("  mprophet     train an mProphet LDA on the background, apply it to the reference peptides");
		System.out.println("  features     score an acquisition and split features into reference and background");
		System.out.println("  features-folder  score paired _maskedN DIA and mass-list files in a folder");
		System.out.println("  scribe-features  score and partition one ScribeTwo acquisition or a seeded folder");
		System.out.println("  scribe-context   run scribe-features, then train and apply the Context LDA");
		System.out.println("  bootstrap    build a targeted assay / mass list from a library");
		System.out.println("  decoys       add entrapment decoys to an assay that only lists targets");
		System.out.println();
		System.out.println("Both engines take the same arguments and write into <-o>/<engine>/, so");
		System.out.println("running them with the same -o leaves their results side by side. Add");
		System.out.println("-generateDecoys and an assay without decoys gets them built along the way.");
		System.out.println();
		System.out.println("Run a command with -h for its own options, for example:");
		System.out.println("  java -jar context.jar percolator -h");

	}
}
