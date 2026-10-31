package org.searlelab.context.percolator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchParameters;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;
import edu.washington.gs.maccoss.encyclopedia.utils.Logger;

/**
 * EncyclopeDIA Percolator options that Context's own background training (CTX-018) does not apply. Context accepts
 * every EncyclopeDIA option, but when one of these is moved off its EncyclopeDIA default it is reported as a warning
 * before any training starts, and the Context workflow runs with its own settings. {@code -percolatorVersion} and
 * {@code -percolatorLocation} only choose the binary and are always honored.
 */
public final class ContextTrainingOptions {

	private ContextTrainingOptions() {
	}

	/** One message per conflicting option; empty when the options agree with what Context uses. */
	public static List<String> percolatorConflicts(Map<String, String> encyclopediaArguments, float fdr) {
		SearchParameters defaults = SearchParameterParser.getDefaultParametersObject();
		SearchParameters given = SearchParameterParser.parseParameters(
				encyclopediaArguments == null ? new HashMap<>() : new HashMap<>(encyclopediaArguments));

		List<String> conflicts = new ArrayList<>();
		if (given.getPercolatorTrainingIterations() != defaults.getPercolatorTrainingIterations()) {
			conflicts.add("-percolatorTrainingIterations " + given.getPercolatorTrainingIterations()
					+ ": Context training uses Percolator's default (--maxiter 10)");
		}
		if (Float.compare(given.getPercolatorTrainingSetThreshold(), defaults.getPercolatorTrainingSetThreshold()) != 0) {
			conflicts.add("-percolatorTrainingFDR " + given.getPercolatorTrainingSetThreshold()
					+ ": Context training uses Percolator's default (--trainFDR 0.01)");
		}
		if (given.getPercolatorTrainingSetSize() != defaults.getPercolatorTrainingSetSize()) {
			conflicts.add("-percolatorTrainingSetSize " + given.getPercolatorTrainingSetSize()
					+ ": Context training uses every background row (Percolator's default -N 0)");
		}
		if (given.getPercolatorModelFile().isPresent()) {
			conflicts.add("-percolatorModelFile " + given.getPercolatorModelFile().get()
					+ ": Context always trains a new background model; the file is not used");
		}
		if (Float.compare(given.getPercolatorProteinThreshold(), defaults.getPercolatorProteinThreshold()) != 0) {
			conflicts.add("-percolatorProteinThreshold " + given.getPercolatorProteinThreshold()
					+ ": Context reports peptides and PSMs only; the value is not used");
		}
		if (Float.compare(given.getPercolatorThreshold(), defaults.getPercolatorThreshold()) != 0
				&& Float.compare(given.getPercolatorThreshold(), fdr) != 0) {
			conflicts.add("-percolatorThreshold " + given.getPercolatorThreshold() + ": Context uses -fdr " + fdr);
		}
		return conflicts;
	}

	/** Logs the conflicts as one block of warnings; {@code note} adds workflow-specific context. */
	public static void warn(List<String> conflicts, String note) {
		if (conflicts.isEmpty()) return;
		Logger.errorLine("Warning: these EncyclopeDIA options are not applied by the Context workflows, which run with "
				+ "Context's required settings instead:");
		for (String conflict : conflicts) {
			Logger.errorLine("  " + conflict);
		}
		if (note != null && !note.isEmpty()) Logger.errorLine("  " + note);
	}
}
