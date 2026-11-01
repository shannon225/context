package org.searlelab.context.io;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

import org.searlelab.context.datastructures.IsolationWindow;
import org.searlelab.msrawjava.io.encyclopedia.EncyclopeDIAFile;
import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.WindowData;

import edu.washington.gs.maccoss.encyclopedia.datastructures.AminoAcidConstants;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryEntry;
import edu.washington.gs.maccoss.encyclopedia.filereaders.LibraryFile;
import edu.washington.gs.maccoss.encyclopedia.utils.Logger;
import edu.washington.gs.maccoss.encyclopedia.utils.math.RandomGenerator;
//import edu.washington.gs.maccoss.encyclopedia.datastructures.Range;

public class TargetedBootstrapper {

	// Entrapment search windows: 10, 20, 40, 80, 160, 320, 640, then 1000 ppm.
	private static final double MAX_ENTRAPMENT_PPM = 1000;

	public void execute(String libraryPath, String rawFilePath, int maxSeed, int numberOfPeptides,
			float halfWindowWidthRT, double halfWindowWidthMz, int trapsPerTarget) throws Throwable {
		Path rawFile = Paths.get(rawFilePath);
		String rawFileName = rawFile.getFileName().toString();
		String baseName = rawFileName.replaceFirst("\\.dia$", "");

		AminoAcidConstants aaConstants = new AminoAcidConstants();

		// Load the library once for all seeds. LibraryFile copies the library to a temp file that only close()
		// deletes, so close it as soon as the entries are in memory.
		ArrayList<LibraryEntry> entries;
		File libraryFile = new File(libraryPath);
		Logger.logLine("Opening the library " + libraryFile.getName() + " (this may take a few minutes)...");
		long loadStart = System.nanoTime();
		LibraryFile library = new LibraryFile();
		try {
			library.openFile(libraryFile);
			entries = library.getAllEntries(false, aaConstants);
		} finally {
			library.close();
		}
		Logger.logLine("Loaded " + entries.size() + " library entries in " + (System.nanoTime() - loadStart) / 1_000_000_000 + " s.");
		// Targets are drawn from library order; entrapments are searched by precursor m/z. The sort is stable.
		ArrayList<LibraryEntry> entriesByMz = new ArrayList<>(entries);
		entriesByMz.sort(Comparator.comparingDouble(LibraryEntry::getPrecursorMZ));

		for (int seed = 1; seed <= maxSeed; seed++) {
			Logger.logLine("Seed " + seed + " of " + maxSeed + ": selecting " + numberOfPeptides + " targets.");
			ArrayList<IsolationWindow> isolationWindows = selectMask(numberOfPeptides, aaConstants, seed, entries,
					entriesByMz, halfWindowWidthRT, halfWindowWidthMz, trapsPerTarget);

			Path maskedFileOutputPath = rawFile.getParent().resolve(baseName + "_masked" + seed + "_assay.dia");
			Path assayOutputPath = rawFile.getParent().resolve(baseName + "_masked" + seed + "_assay.txt");
			Path assignmentOutputPath = rawFile.getParent().resolve(baseName + "_masked" + seed + "_entrapment_assignments.tsv");

			writeMaskedFile(isolationWindows, seed, rawFilePath, maskedFileOutputPath, halfWindowWidthMz);

			writeAssayList(isolationWindows, assayOutputPath);
			writeEntrapmentAssignments(isolationWindows, trapsPerTarget, assignmentOutputPath);
		}
	}

	public ArrayList<IsolationWindow> selectTargets(int numberOfPeptides, AminoAcidConstants aaConstants, int i,
			ArrayList<LibraryEntry> entries, float halfWindowWidthRT, double halfWindowWidthMz,
			HashSet<String> excludedTargetSequences) throws Exception {

		ArrayList<IsolationWindow> targetWindows = new ArrayList<>();

		int randomValue = 1 + i; // Add haliburton's number +1 to get a random number

		HashSet<Integer> simulatedAssaySet = new HashSet<>();
		HashSet<String> targetSequencesSelected = new HashSet<>();

		// Randomly select precursors loop
		try {
			if (numberOfPeptides > entries.size()) {
				throw new IllegalArgumentException(
						"The requested number of targets is larger than the number of library entries. Select a smaller number of peptides to put into each assay.");
			}

			// Select target precursors
			while (simulatedAssaySet.size() < numberOfPeptides) {
				randomValue = RandomGenerator.randomInt(randomValue);
				int index = Math.abs(randomValue) % entries.size();
				simulatedAssaySet.add(index);
			}

			// While loop for selecting targets and decoys
			while (targetSequencesSelected.size() < numberOfPeptides) {
				for (Integer index : simulatedAssaySet) {

					if (targetSequencesSelected.size() >= numberOfPeptides) {
						break;
					}

					// Retrieve the library entry at the random index
					LibraryEntry entry = entries.get(index);

					// Get the m/z, RT and sequence
					double targetMz = entry.getPrecursorMZ();
					float rtCenter = entry.getRetentionTimeInSec();
					String sequence = entry.getAccuratePeptideModSeq(aaConstants);
					byte charge = entry.getPrecursorCharge();

					// Calculate a RT ranges for the isolationWindows object
					float rtMin = (float) (rtCenter - (60f * (halfWindowWidthRT)));
					float rtMax = (float) (rtCenter + (60f * (halfWindowWidthRT)));

					if (!targetSequencesSelected.contains(sequence) && !excludedTargetSequences.contains(sequence)) {

						IsolationWindow window = new IsolationWindow(sequence, targetMz, charge, rtMin, rtMax, false);
						targetWindows.add(window);
						targetSequencesSelected.add(sequence);

						Logger.logLine("Target is " + targetMz + " for " + sequence + " at " + rtCenter / 60);
					}
				} // If there aren't enough peptides selected in the target list, select more
					// indicies

				if (targetSequencesSelected.size() < numberOfPeptides) {

					if (simulatedAssaySet.size() >= entries.size()) {
						throw new IllegalStateException("The library does not contain enough unique peptides to bootstrap from the assay. Select a new library");
					}

					int previousSetSize = simulatedAssaySet.size();

					while (simulatedAssaySet.size() == previousSetSize) {
						randomValue = RandomGenerator.randomInt(randomValue);

						int index = Math.abs(randomValue) % entries.size();
						simulatedAssaySet.add(index);
					}
				}
			}
			
		} catch (Exception e) {
			System.out.println(
					"There was an error with selecting targets. Check the file path for the library. If it fails again, you may not have enough peptides in your dataset to bootstrap a synthetic assay.");
			throw e;
		} finally {
			System.out.println(targetWindows.size() + " Precursors marked for extraction.");
		}
		return targetWindows;
	}

	// decoyEntriesByMz must be sorted by ascending precursor m/z. For each target, entrapments are the closest eligible
	// precursors within 10 ppm, widening by doubling and finally to 1000 ppm.
	public ArrayList<IsolationWindow> selectDecoys(ArrayList<IsolationWindow> targetWindows, AminoAcidConstants aaConstants, int i, ArrayList<LibraryEntry> decoyEntriesByMz, float halfWindowWidthRT, double halfWindowWidthMz, ArrayList<IsolationWindow> unmatchedTargets, int trapsPerTarget) throws Exception {

		for (int j = 1; j < decoyEntriesByMz.size(); j++) {
			if (decoyEntriesByMz.get(j - 1).getPrecursorMZ() > decoyEntriesByMz.get(j).getPrecursorMZ()) {
				throw new IllegalArgumentException("Entrapment candidates must be sorted by precursor m/z.");
			}
		}

		ArrayList<IsolationWindow> decoyWindows = new ArrayList<IsolationWindow>();

		HashSet<String> targetSequencesSelected = new HashSet<>();
		HashSet<String> decoySequencesSelected = new HashSet<>();
		HashMap<String, String> discardedTDPairs = new HashMap<>();

		try {
			for (IsolationWindow targetWindow : targetWindows) {
				targetSequencesSelected.add(targetWindow.getCompound());
			}

			for (IsolationWindow targetWindow : targetWindows) {

				// Get the m/z, RT and sequence for targets
				double targetMz = targetWindow.getTargetMz();
//				double targetMzMin = targetMz - halfWindowWidthMz;
//				double targetMzMax = targetMz + halfWindowWidthMz;
//				Range targetMzRange = new Range(targetMzMin, targetMzMax);
				
				String targetSequence = targetWindow.getCompound();
				byte targetCharge = targetWindow.getCharge();

//				boolean decoyFound = false;
				double mzTolerancePPM = 10;

				ArrayList<IsolationWindow> decoysForTarget = new ArrayList<>();
				HashSet<String> decoysForThisTarget = new HashSet<>();

				while (decoysForTarget.size() < trapsPerTarget && mzTolerancePPM <= MAX_ENTRAPMENT_PPM) {

					double mzToleranceDa = targetMz * mzTolerancePPM / 1_000_000;
					double upperMz = targetMz + mzToleranceDa;
					double lowerMz = targetMz - mzToleranceDa;

					// Loop to find entrapment decoys at a different window from target peptides
					for (LibraryEntry candidate : candidatesInWindow(decoyEntriesByMz, targetMz, lowerMz, upperMz)) {

						double candidateMinMz = candidate.getPrecursorMZ() - halfWindowWidthMz;
						double candidateMaxMz = candidate.getPrecursorMZ() + halfWindowWidthMz;

						float candidateRT = candidate.getRetentionTime();
						float candidateMinRT = candidateRT - (halfWindowWidthRT * 60f);
						float candidateMaxRT = candidateRT + (halfWindowWidthRT * 60f);

						byte decoyCharge = candidate.getPrecursorCharge();
						String decoySequence = candidate.getAccuratePeptideModSeq(aaConstants);
						boolean decoyWindowOverlapsTarget = false;

						for (IsolationWindow comparisonTarget : targetWindows) {

							// Calculate isolation window for each window
							double comparisonTargetMzMin = comparisonTarget.getTargetMz() - (10 * halfWindowWidthMz);
							double comparisonTargetMzMax = comparisonTarget.getTargetMz() + (10 * halfWindowWidthMz);

							// Calculate target windows + buffer range - targets will be selected outside of this window
							float minTargetRTWithBuffer = comparisonTarget.getRtMin() - (halfWindowWidthRT * 60f);
							float maxTargetRTWithBuffer = comparisonTarget.getRtMax() + (halfWindowWidthRT * 60f);

							boolean overlapsThisTargetRT = candidateMinRT <= maxTargetRTWithBuffer && candidateMaxRT >= minTargetRTWithBuffer;
							boolean overlapsThisTargetMz = candidateMinMz <= comparisonTargetMzMax && candidateMaxMz >= comparisonTargetMzMin;

							if (overlapsThisTargetRT && overlapsThisTargetMz) {
								decoyWindowOverlapsTarget = true;
								break;
							}
						}

						// If the candidate RT is outside of the target RT range, and the decoy sequence
						// is different from the target, then add the decoys						
						if (!decoyWindowOverlapsTarget 
								&& decoyCharge == targetCharge
								&& !targetSequencesSelected.contains(decoySequence)  // has the decoy been used? 
								&& !decoySequencesSelected.contains(decoySequence)
								&& !decoysForThisTarget.contains(decoySequence)) {

							double decoyMz = candidate.getPrecursorMZ();

							float decoyRTMin = candidateRT - (60f * (halfWindowWidthRT));
							float decoyRTMax = candidateRT + (60f * (halfWindowWidthRT));

							IsolationWindow decoyWindow = new IsolationWindow(decoySequence, decoyMz, decoyCharge, decoyRTMin, decoyRTMax, true);

							// Add to the running list of decoys for this target
							decoysForTarget.add(decoyWindow);
							decoysForThisTarget.add(decoySequence);

							Logger.logLine("Decoy " + decoysForThisTarget.size() + " of " + trapsPerTarget + " for target " + targetSequence + " at " + candidateRT / 60f
									+ " (" + String.format("%.2f", (decoyMz - targetMz) / targetMz * 1_000_000) + " ppm)");

							if (decoysForTarget.size() == trapsPerTarget) {
								break; // end loop after adding one decoy per target
							}
						}
					}

					if (decoysForTarget.size() < trapsPerTarget) {
						if (mzTolerancePPM >= MAX_ENTRAPMENT_PPM) {
							break;
						}
						mzTolerancePPM = Math.min(mzTolerancePPM * 2, MAX_ENTRAPMENT_PPM);
					}
				}
				
				if (decoysForTarget.size() == trapsPerTarget) {

					// Commit decoys after the complete set was found
					decoyWindows.addAll(decoysForTarget);
					decoySequencesSelected.addAll(decoysForThisTarget);

					for (IsolationWindow decoy : decoysForTarget) {
						String decoySeq = decoy.getCompound();
						discardedTDPairs.put(decoySeq,  targetSequence);
					}

				} else {

					unmatchedTargets.add(targetWindow);
					Logger.logLine("Only found " + decoysForTarget.size() + " of " + trapsPerTarget + " requested entrapment peptides for target " + targetSequence + ".");

				}
			}

			}catch(

	Exception e)
	{
		throw e;
	}finally
	{
		System.out.println(decoyWindows.size() + " decoy windows selected.");
	}

	return decoyWindows;

}

	// Returns entries with precursor m/z in [lowerMz, upperMz], closest to targetMz first. Equally close entries keep
	// their order in entriesByMz (lower m/z, then library order).
	static ArrayList<LibraryEntry> candidatesInWindow(ArrayList<LibraryEntry> entriesByMz, double targetMz, double lowerMz,
			double upperMz) {
		int low = 0;
		int high = entriesByMz.size();
		while (low < high) {
			int mid = (low + high) >>> 1;
			if (entriesByMz.get(mid).getPrecursorMZ() < lowerMz) {
				low = mid + 1;
			} else {
				high = mid;
			}
		}
		ArrayList<LibraryEntry> candidates = new ArrayList<>();
		for (int j = low; j < entriesByMz.size() && entriesByMz.get(j).getPrecursorMZ() <= upperMz; j++) {
			candidates.add(entriesByMz.get(j));
		}
		candidates.sort(Comparator.comparingDouble(entry -> Math.abs(entry.getPrecursorMZ() - targetMz)));
		return candidates;
	}

// First function - Randomly Selects Precursors from a library, places on a list
	public ArrayList<IsolationWindow> selectMask(int numberOfPeptides, AminoAcidConstants aaConstants, int i,
			ArrayList<LibraryEntry> entries, ArrayList<LibraryEntry> entriesByMz, float halfWindowWidthRT,
			double halfWindowWidthMz, int trapsPerTarget) throws IOException, SQLException, Throwable {

		// START TIMER 1
		long startTime = System.nanoTime();
		HashSet<String> rejectedTargetSequences = new HashSet<>();

		ArrayList<IsolationWindow> targetWindows = selectTargets(numberOfPeptides, aaConstants, i, entries,
				halfWindowWidthRT, halfWindowWidthMz, rejectedTargetSequences);
		ArrayList<IsolationWindow> isolationWindows = new ArrayList<>();
		
		if (trapsPerTarget >= 0) {
			ArrayList<IsolationWindow> decoyWindows;
		
		int replacementRound = 0;

		while (true) {

			ArrayList<IsolationWindow> unmatchedTargets = new ArrayList<>();

			// Select decoys
			decoyWindows = selectDecoys(targetWindows, aaConstants, i, entriesByMz, halfWindowWidthRT,
					halfWindowWidthMz, unmatchedTargets, trapsPerTarget);

			if (unmatchedTargets.isEmpty()) {
				break;
			}

			// Remove targets that could not find appropriate decoys
			for (IsolationWindow unmatchedTarget : unmatchedTargets) {
				targetWindows.remove(unmatchedTarget);

				rejectedTargetSequences.add(unmatchedTarget.getCompound());
			}

			HashSet<String> unavailableTargetSequences = new HashSet<>(rejectedTargetSequences);

			// Exclude the targets that are still in the assay so they can't be selected
			// again
			for (IsolationWindow existingTarget : targetWindows) {
				unavailableTargetSequences.add(existingTarget.getCompound());
			}

			int replacementsNeeded = numberOfPeptides - targetWindows.size();

			replacementRound++;

			ArrayList<IsolationWindow> replacementTargets = selectTargets(replacementsNeeded, aaConstants,
					i + replacementRound, entries, halfWindowWidthRT, halfWindowWidthMz,
					unavailableTargetSequences);

			targetWindows.addAll(replacementTargets);
		}

		int expectedDecoyCount = targetWindows.size() * trapsPerTarget;

		if (decoyWindows.size() != expectedDecoyCount) {
			throw new IllegalStateException("Excepted " + expectedDecoyCount + " decoys for " + targetWindows.size()
					+ " targets, but found " + decoyWindows.size());
		}

		 isolationWindows = new ArrayList<>(targetWindows.size() + decoyWindows.size());
		int decoyIndex = 0;

		for (IsolationWindow targetWindow : targetWindows) {
			isolationWindows.add(targetWindow);
			for (int j = 0; j < trapsPerTarget; j++) {
				isolationWindows.add(decoyWindows.get(decoyIndex));
				decoyIndex++;
			}
		}

		Logger.logLine(isolationWindows.size() + " total target and decoy windows selected.");

		
		long endTime = System.nanoTime();
		System.out.println("selectMask(): Time taken (ms): " + (endTime - startTime) / 1_000_000);
		// Randomly select precursors loop
		return isolationWindows;

		} else {

			
		return targetWindows;
	}
	}
	

// Second function - Uses the IsolationWindow List to mask the raw data
	public EncyclopeDIAFile writeMaskedFile(ArrayList<IsolationWindow> isolationWindows, int i, String diaFilePath,
			Path outputPath, double halfWindowWidthMz) throws Throwable {
		// START TIMER 2
		long startTime = System.nanoTime();

		File rawFile = new File(diaFilePath);
		EncyclopeDIAFile maskedFile = new EncyclopeDIAFile();
		EncyclopeDIAFile rawLibraryFile = new EncyclopeDIAFile();
		File outputFile = outputPath.toFile();

		HashSet<Integer> addedPrecursors = new HashSet<>();
		HashSet<Integer> addedFragments = new HashSet<>();

		// System.out.println("Is the .dia file open? " + rawLibraryFile.isOpen());
		rawLibraryFile.openFile(rawFile);

		try {
			maskedFile.openFile();

			// Add Ranges
			HashMap<Range, WindowData> dutyCycleMap = new HashMap<>();
			System.out.println("Masking DIA file based on the selected precursors...");

			for (IsolationWindow window : isolationWindows) {
				boolean isDecoy = window.isDecoy();

				if (!isDecoy) {

					double windowMz = window.getTargetMz();
					float windowStartTime = window.getRtMin();
					float windowStopTime = window.getRtMax();
					boolean sqrt = false;
					double mzStart = windowMz - halfWindowWidthMz;
					double mzStop = windowMz + halfWindowWidthMz;
					Range mzRange = new Range(mzStart, mzStop);

					ArrayList<org.searlelab.msrawjava.model.FragmentScan> fragmentScansFromWindow = rawLibraryFile
							.getStripes(windowMz, windowStartTime, windowStopTime, sqrt);
					ArrayList<FragmentScan> matchingScans = new ArrayList<>();

					// Add Fragment Scans
					for (FragmentScan scan : fragmentScansFromWindow) {
						double scanMz = scan.getPrecursorMZ();
						int scanIndex = scan.getSpectrumIndex();
						if (mzRange.contains(scanMz) && !addedFragments.contains(scanIndex)) {
							matchingScans.add(scan);
							addedFragments.add(scanIndex);
						}
					}

					for (Entry<org.searlelab.msrawjava.model.Range, WindowData> entry : rawLibraryFile.getRanges()
							.entrySet()) {
						if (mzRange.contains(entry.getKey().getMiddle())) {
							dutyCycleMap.put(entry.getKey(), entry.getValue());
						}
					}

					maskedFile.setRanges(dutyCycleMap);
					maskedFile.addStripe(matchingScans);

					// Add Precursor Scans
					ArrayList<PrecursorScan> precursorScanFromWindow = rawLibraryFile.getPrecursors(windowStartTime,
							windowStopTime);

					ArrayList<PrecursorScan> matchingPrecursors = new ArrayList<>();

					for (PrecursorScan precursor : precursorScanFromWindow) {
						Range precursorRange = new Range(precursor.getIsolationWindowLower(),
								precursor.getIsolationWindowUpper());
						int spectrumIndex = precursor.getSpectrumIndex();
						if ((precursorRange.contains(mzRange) && !addedPrecursors.contains(spectrumIndex))) {
							matchingPrecursors.add(precursor);
							addedPrecursors.add(spectrumIndex);
						}
					}
					maskedFile.addPrecursor(matchingPrecursors);
				}
				maskedFile.setFileName(rawFile.getName(), null, rawFile.getAbsolutePath());
				maskedFile.addMetadata(rawLibraryFile.getMetadata());
				maskedFile.setFractionNames(rawLibraryFile.getFractionNames());
			}
		} catch (IOException e) {
			System.out.println("Unable to open raw file.");
			throw e;
		}

		// END TIMER 2
		long endTime = System.nanoTime();
		long duration = endTime - startTime;
		rawLibraryFile.close();

		System.out.println("maskDIAFileBasedOnIsolationWindows(): Time taken (ms) : " + duration / 1_000_000);

		maskedFile.saveAsFile(outputFile);
		System.out.println("Target mass list for the masked file  was written to " + outputPath
				+ "\n Number of added Precursor scans: " + addedPrecursors.size()
				+ "\n Number of added Fragment scans: " + addedFragments.size());

		return maskedFile;
	}

	public void writeAssayList(ArrayList<IsolationWindow> isolationWindows, Path outputPath) throws IOException {
		try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
			writer.write("Compound\tFormula\tAdduct\tm/z\tz\tRT Time (min)\tWindow (min)\tisDecoy");
			writer.newLine();

			for (IsolationWindow window : isolationWindows) {
				String compound = window.getCompound();
				double targetMz = window.getTargetMz();
				byte charge = window.getCharge();
				boolean isDecoy = window.isDecoy();

				float rtCenterMin = ((window.getRtMin() + window.getRtMax()) / 2.0f) / 60.0f;
				float windowMin = (window.getRtMax() - window.getRtMin()) / 60.0f;

				writer.write(compound + "\t" + "\t" + "(no adduct)" + "\t" + targetMz + "\t" + charge + "\t"
						+ rtCenterMin + "\t" + windowMin + "\t" + isDecoy);
				writer.newLine();
			}
		} catch (Exception e) {
			throw e;
		}
	}

	void writeEntrapmentAssignments(ArrayList<IsolationWindow> isolationWindows, int trapsPerTarget, Path outputPath)
			throws IOException {
		int expectedPerTarget = Math.max(0, trapsPerTarget);
		IsolationWindow target = null;
		int assigned = 0;
		ArrayList<String> rows = new ArrayList<>();
		for (IsolationWindow window : isolationWindows) {
			if (!window.isDecoy()) {
				if (target != null && assigned != expectedPerTarget) {
					throw new IOException("Incomplete entrapment assignments for " + target.getCompound());
				}
				target = window;
				assigned = 0;
			} else {
				if (target == null || assigned >= expectedPerTarget) {
					throw new IOException("Entrapment assignment has no matching assay target: " + window.getCompound());
				}
				rows.add("EMPIRICAL_ASSAY_ENTRAPMENT\t" + target.getCompound() + "\t" + target.getCharge() + "\t"
						+ window.getCompound() + "\t" + window.getCharge());
				assigned++;
			}
		}
		if (target != null && assigned != expectedPerTarget) {
			throw new IOException("Incomplete entrapment assignments for " + target.getCompound());
		}
		try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
			writer.write("relationType\ttargetPeptideModSeq\ttargetCharge\tentrapmentPeptideModSeq\tentrapmentCharge");
			writer.newLine();
			for (String row : rows) {
				writer.write(row);
				writer.newLine();
			}
		}
	}

	@SuppressWarnings("unused")
	private void writeTargetDecoyMap(HashMap<String, String> targetDecoyMap, Path outputPath) throws IOException {

		try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {

			writer.write("decoySequence\ttargetSequence");
			writer.newLine();

			for (Entry<String, String> entry : targetDecoyMap.entrySet()) {
				String decoySequence = entry.getKey();
				String targetSequence = entry.getValue();

				writer.write(decoySequence + "\t" + targetSequence);
				writer.newLine();
			}
		} catch (Exception e) {
			throw e;
		}
	}
}
