package edu.washington.gs.maccoss.encyclopedia;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.zip.DataFormatException;
import java.util.Optional;

import org.searlelab.msrawjava.io.StripeFileInterface;

import edu.washington.gs.maccoss.encyclopedia.ScribeTwo;
import edu.washington.gs.maccoss.encyclopedia.algorithms.scribe.ScribeTwoPrealignmentContext;
import edu.washington.gs.maccoss.encyclopedia.algorithms.scribe.ScribeTwoPrealignmentToleranceRefinement;
import edu.washington.gs.maccoss.encyclopedia.algorithms.scribe.ScribeTwoScoringFactory;
import edu.washington.gs.maccoss.encyclopedia.algorithms.scribe.ScribeTwoSearchData;
import edu.washington.gs.maccoss.encyclopedia.algorithms.scribe.ScribeTwoSearchParameters;
import edu.washington.gs.maccoss.encyclopedia.filewriters.ScribeTwoPinStore;
import edu.washington.gs.maccoss.encyclopedia.utils.threading.ProgressIndicator;

public final class ContextScribeTwoBridge {

	private ContextScribeTwoBridge() {}

	public record Result(File features, ScribeTwoSearchParameters parameters, boolean massTolerancesAdjusted) {}

	public static Result generate(ProgressIndicator progress, ScribeTwoSearchData job) throws IOException, SQLException, DataFormatException, InterruptedException, ExecutionException {

		if (job.isFastaPredictionMode()) {
			throw new IllegalArgumentException("Context Scribe integration requires an existing library");
		}

		// For now we'll use the entire library to do pre-alignment training
		ScribeTwoPrealignmentContext context = requirePrealignment(ScribeTwo.runPrealignmentOnly(progress, job));
		ScribeTwoSearchData calibratedJob = job.withDiaFileSource(context.getCalibratedDiaSource());
		ScribeTwoSearchParameters requested = (ScribeTwoSearchParameters) job.getParameters();
		ScribeTwoSearchParameters effective = refineParameters(requested, context.getToleranceRefinement());
		calibratedJob = calibratedJob.withTaskFactory(new ScribeTwoScoringFactory(effective));
		ScribeTwoScoringFactory factory = calibratedJob.getTaskFactory().withPrealignmentContext(java.util.Optional.of(context)).withOutputFeatureFilter(java.util.Optional.of(context.getCalibratedFilter()));

		ScribeTwoPinStore store = null;
		StripeFileInterface reader = calibratedJob.getDiaFileSource().openReader();
		try {
			store = ScribeTwo.generateFeatureFile(progress, calibratedJob, reader, Optional.empty(), factory, calibratedJob.getPercolatorFiles(), Optional.empty());
			File features = calibratedJob.getPercolatorFiles().getInputTSV();
			if (!features.isFile() || features.length() == 0) {
				throw new IOException("Scribe full search did not export a feature table." + features);
			}

			store.deleteAfterSuccess();
			store = null;
			return new Result(features, effective, effective != requested);

		} finally {
			try {
				if (store != null) store.closeRetainingStore();
			} finally {
				reader.close();

			}
		}
	}

	static ScribeTwoPrealignmentContext requirePrealignment(java.util.Optional<ScribeTwoPrealignmentContext> context) throws IOException {

		return context.orElseThrow(() -> new IOException("Scribe prealignment did not produce a useable calibration/prealignment model."
				+ "\nInspectr preliminary matches and instrument tolerance/library settings."
				+ "\nThe calibrated full search has not run."));

	}

	static ScribeTwoSearchParameters refineParameters(ScribeTwoSearchParameters parameters, ScribeTwoPrealignmentToleranceRefinement refinement) {
		if (!parameters.isAdjustTolerances() || !refinement.hasAdjustedMassTolerances()) return parameters;

		HashMap<String, String> settings = parameters.toParameterMap();
		settings.put("-ptol", Double.toString(refinement.getAdjustedPrecursorTolerance().orElseThrow().getToleranceThreshold()));
		settings.put("-ftol", Double.toString(refinement.getAdjustedFragmentTolerance().orElseThrow().getToleranceThreshold()));
		return ScribeTwoSearchParameters.parseParameters(settings);
	}



}
