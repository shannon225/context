package org.searlelab.context.io;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AssaySchedulePairMatcher {

	static final String DEFAULT_INPUT_PREFIX = "_masked";

	private AssaySchedulePairMatcher() {
	}

	static List<AssayScheduleInputPair> findInputPairs(File folder, String inputPrefix, int startSeed,
			int endSeed) {

		validateInputs(folder, inputPrefix, startSeed, endSeed);

		File[] folderContents = folder.listFiles();
		if (folderContents == null) {
			throw new IllegalArgumentException("Could not list files in: " + folder.getAbsolutePath());
		}

		Pattern seedPattern = Pattern.compile(Pattern.quote(inputPrefix) + "([0-9]+)(?=_|\\.)",
				Pattern.CASE_INSENSITIVE);
		Map<Integer, File> acquisitionBySeed = new TreeMap<>();

		for (File file : folderContents) {
			if (!file.isFile() || !file.getName().toLowerCase(Locale.ROOT).endsWith(".dia")) {
				continue;
			}

			Matcher matcher = seedPattern.matcher(file.getName());
			if (!matcher.find()) {
				continue;
			}

			int seed;
			try {
				seed = Integer.parseInt(matcher.group(1));
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Seed is too large in acquisition filename: " + file.getName(), e);
			}

			if (seed < startSeed || seed > endSeed) {
				continue;
			}

			File previousFile = acquisitionBySeed.putIfAbsent(seed, file);
			if (previousFile != null) {
				throw new IllegalArgumentException("More than one DIA file was found for seed " + seed + " with prefix '"
						+ inputPrefix + "': " + previousFile.getName() + " and " + file.getName());
			}
		}

		List<Integer> missingSeeds = new ArrayList<>();
		List<String> missingAssaySchedules = new ArrayList<>();
		List<AssayScheduleInputPair> inputs = new ArrayList<>();

		for (long candidate = startSeed; candidate <= endSeed; candidate++) {
			int seed = (int) candidate;
			File acquisitionFile = acquisitionBySeed.get(seed);
			if (acquisitionFile == null) {
				missingSeeds.add(seed);
				continue;
			}

			File assayScheduleFile = new File(RawFiles.baseName(acquisitionFile) + ".txt");
			if (!assayScheduleFile.isFile() || !assayScheduleFile.canRead()) {
				missingAssaySchedules.add("seed " + seed + ": " + assayScheduleFile.getAbsolutePath());
				continue;
			}

			inputs.add(new AssayScheduleInputPair(seed, acquisitionFile, assayScheduleFile));
		}

		if (!missingSeeds.isEmpty() || !missingAssaySchedules.isEmpty()) {
			StringBuilder message = new StringBuilder("Input-pair preflight failed for folder ")
					.append(folder.getAbsolutePath()).append(", prefix '").append(inputPrefix).append("', seeds ")
					.append(startSeed).append(" through ").append(endSeed).append('.');
			if (!missingSeeds.isEmpty()) {
				message.append("\nNo DIA file was found for seed(s): ").append(missingSeeds);
			}
			if (!missingAssaySchedules.isEmpty()) {
				message.append("\nAssay-schedule files were not found for:\n  ")
						.append(String.join("\n  ", missingAssaySchedules));
			}
			throw new IllegalArgumentException(message.toString());
		}

		return List.copyOf(inputs);
	}

	private static void validateInputs(File folder, String inputPrefix, int startSeed, int endSeed) {
		if (folder == null || !folder.isDirectory() || !folder.canRead()) {
			throw new IllegalArgumentException("DIA folder is not a readable directory: "
					+ (folder == null ? "null" : folder.getAbsolutePath()));
		}
		if (startSeed < 0 || endSeed < 0) {
			throw new IllegalArgumentException("Seed range cannot contain negative values: " + startSeed + " through "
					+ endSeed);
		}
		if (endSeed < startSeed) {
			throw new IllegalArgumentException("End seed must be greater than or equal to start seed: " + startSeed
					+ " through " + endSeed);
		}
		if (inputPrefix == null || inputPrefix.isBlank()) {
			throw new IllegalArgumentException("Input prefix cannot be blank.");
		}
		if (!inputPrefix.equals(inputPrefix.trim())) {
			throw new IllegalArgumentException("Input prefix cannot have leading or trailing whitespace: '" + inputPrefix
					+ "'");
		}
		if (inputPrefix.indexOf('/') >= 0 || inputPrefix.indexOf('\\') >= 0) {
			throw new IllegalArgumentException("Input prefix cannot contain path separators: " + inputPrefix);
		}
		if (Character.isDigit(inputPrefix.charAt(inputPrefix.length() - 1))) {
			throw new IllegalArgumentException("Input prefix cannot end in a digit: " + inputPrefix);
		}
	}
}
