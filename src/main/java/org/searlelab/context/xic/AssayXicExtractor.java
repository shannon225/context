package org.searlelab.context.xic;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.DataFormatException;

import org.searlelab.context.datastructures.IsolationWindow;
import org.searlelab.msrawjava.io.StripeFileInterface;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Spectrum;

import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryEntry;
import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchParameters;
import edu.washington.gs.maccoss.encyclopedia.utils.graphing.GraphType;
import edu.washington.gs.maccoss.encyclopedia.utils.massspec.ChromatogramExtractor;
import edu.washington.gs.maccoss.encyclopedia.utils.massspec.FragmentIon;
import edu.washington.gs.maccoss.encyclopedia.utils.massspec.MassConstants;
import edu.washington.gs.maccoss.encyclopedia.utils.massspec.PeptideUtils;

/** Headless adapter to dependency extraction, across the acquired gradient, preserving original schedules as metadata. */
public final class AssayXicExtractor {
	private AssayXicExtractor() {}

	public static AssayXic extract(int row, IsolationWindow window, LibraryEntry reference,
			StripeFileInterface reader, SearchParameters parameters)
			throws IOException, SQLException, DataFormatException {
		return extract(row, window, reference, reader, parameters, null);
	}

	public static AssayXic extract(int row, IsolationWindow window, LibraryEntry reference,
			StripeFileInterface reader, SearchParameters parameters, AssayIdentityResolver identities)
			throws IOException, SQLException, DataFormatException {
		validateSchedule(row, window);
		String problem = referenceProblem(identities == null ? window : identities.resolve(window), reference, parameters);
		if (problem != null) return result(row, window, problem, 0, List.of(), List.of());
		ArrayList<FragmentScan> scans = readFragments(reader, window);
		ArrayList<PrecursorScan> precursors = reader.getPrecursors(-Float.MAX_VALUE, Float.MAX_VALUE);
		for (var scan : precursors) requireFiniteTime(scan);
		precursors.sort(Comparator.comparingDouble(PrecursorScan::getScanStartTime));
		var precursorTraces = new ArrayList<AssayXic.Trace>();
		if (!precursors.isEmpty()) {
			var traces = ChromatogramExtractor.extractPrecursorChromatograms(parameters.getPrecursorTolerance(),
					window.getTargetMz(), window.getCharge(), precursors, false, false);
			for (int i = 0; i < traces.length; i++) {
				precursorTraces.add(trace(traces[i].getName(), MassConstants.getChargedIsotopeMass(
						window.getTargetMz(), window.getCharge(), ChromatogramExtractor.isotopes[i]),
						precursors, traces[i].toArrays().y));
			}
		}
		var fragments = new ArrayList<AssayXic.Trace>();
		if (!scans.isEmpty()) {
			FragmentIon[] ions = scoringIons(reference, parameters);
			var traces = ChromatogramExtractor.extractFragmentChromatograms(parameters.getFragmentTolerance(),
					ions, scans, null, GraphType.line, false, false);
			for (FragmentIon ion : ions) fragments.add(trace(ion.getName(), ion.getMass(), scans, traces.get(ion).toArrays().y));
		}
		return result(row, window, null, scans.size(), precursorTraces, fragments);
	}

	static void validateSchedule(int row, IsolationWindow window) {
		if (row < 1 || !Double.isFinite(window.getTargetMz()) || window.getTargetMz() <= 0
				|| window.getCharge() <= 0 || !Float.isFinite(window.getRtMin())
				|| !Float.isFinite(window.getRtMax()) || window.getRtMin() > window.getRtMax()) {
			throw new IllegalArgumentException("Invalid assay schedule at row " + row);
		}
	}

	public static ArrayList<FragmentScan> readFragments(StripeFileInterface reader, IsolationWindow window)
			throws IOException, SQLException {
		// The m/z overload includes applicable overlapping isolation windows across the entire run.
		var scans = reader.getStripes(window.getTargetMz(), -Float.MAX_VALUE, Float.MAX_VALUE, false);
		scans.removeIf(scan -> !scan.getPrecursorRange().contains((float) window.getTargetMz()));
		for (var scan : scans) requireFiniteTime(scan);
		scans.sort(Comparator.comparingDouble(FragmentScan::getScanStartTime).thenComparingInt(FragmentScan::getSpectrumIndex));
		return scans;
	}

	public static String referenceProblem(IsolationWindow window, LibraryEntry entry, SearchParameters parameters) {
		if (entry == null) return "NO_EXACT_REFERENCE";
		String sequence = PeptideUtils.getCorrectedMasses(window.getCompound(), parameters.getAAConstants());
		if (!sequence.equals(entry.getPeptideModSeq()) || window.getCharge() != entry.getPrecursorCharge()) {
			return "REFERENCE_IDENTITY_MISMATCH";
		}
		double[] masses = entry.getMassArray();
		float[] intensities = entry.getIntensityArray();
		float[] correlations = entry.getCorrelationArray();
		if (masses == null || intensities == null || correlations == null || masses.length == 0
				|| masses.length != intensities.length || masses.length != correlations.length) return "INVALID_REFERENCE_ARRAYS";
		for (int i = 0; i < masses.length; i++) {
			if (!Double.isFinite(masses[i]) || masses[i] <= 0 || !Float.isFinite(intensities[i])
					|| intensities[i] < 0 || !Float.isFinite(correlations[i])
					|| (i > 0 && masses[i] < masses[i - 1])) return "INVALID_REFERENCE_VALUES";
		}
		return scoringIons(entry, parameters).length == 0 ? "NO_POSITIVE_SCORING_IONS" : null;
	}

	public static FragmentIon[] scoringIons(LibraryEntry reference, SearchParameters parameters) {
		var model = PeptideUtils.getPeptideModel(reference.getPeptideModSeq(), parameters.getAAConstants());
		var ions = FragmentIon.getUniqueFragments(model.getPrimaryIonObjects(parameters.getFragType(),
				reference.getPrecursorCharge(), true), parameters.getFragmentTolerance());
		var supported = new ArrayList<FragmentIon>();
		for (FragmentIon ion : ions) {
			for (int index : parameters.getLibraryFragmentTolerance().getIndices(reference.getMassArray(), ion.getMass())) {
				if (reference.getIntensityArray()[index] > 0) { supported.add(ion); break; }
			}
		}
		return supported.toArray(new FragmentIon[0]);
	}

	private static void requireFiniteTime(Spectrum scan) {
		if (!Float.isFinite(scan.getScanStartTime())) throw new IllegalStateException("Nonfinite acquisition time");
	}

	private static AssayXic.Trace trace(String name, double mz, List<? extends Spectrum> scans, double[] intensities) {
		if (scans.size() != intensities.length) throw new IllegalStateException("Extractor returned mismatched scan count");
		float[] times = new float[scans.size()], values = new float[scans.size()];
		for (int i = 0; i < times.length; i++) {
			times[i] = scans.get(i).getScanStartTime();
			values[i] = (float) intensities[i];
			if (!Float.isFinite(values[i])) throw new IllegalStateException("Extractor returned nonfinite intensity");
		}
		return new AssayXic.Trace(name, mz, times, values);
	}

	private static AssayXic result(int row, IsolationWindow window, String problem, int scans,
			List<AssayXic.Trace> precursors, List<AssayXic.Trace> fragments) {
		return new AssayXic(row, window.getCompound(), window.getCharge(), window.getTargetMz(),
				window.getRtMin(), window.getRtMax(), window.isDecoy(), problem, scans, precursors, fragments);
	}
}
