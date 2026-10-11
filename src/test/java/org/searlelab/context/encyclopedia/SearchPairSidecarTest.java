package org.searlelab.context.encyclopedia;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import edu.washington.gs.maccoss.encyclopedia.algorithms.library.EncyclopediaTwoDecoyStrategy;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.PercolatorPeptide;
import edu.washington.gs.maccoss.encyclopedia.datastructures.AminoAcidConstants;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryEntry;
import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchParameters;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;

public class SearchPairSidecarTest {
	@Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void joinsTargetAndShuffledDecoyByExistingFeatureId() throws Exception {
		SearchParameters parameters = SearchParameterParser.getDefaultParametersObject();
		LibraryEntry target = entry("PEPTIDEK", parameters);
		LibraryEntry decoy = target.getDecoy(parameters);
		SearchPairSidecar sidecar = new SearchPairSidecar();
		sidecar.record(new EncyclopediaTwo.SearchSelection(target, List.of(target, decoy),
				EncyclopediaTwoDecoyStrategy.Mode.SHUFFLED));
		String targetId = id(target);
		String decoyId = id(decoy);
		File features = new File(temporaryFolder.getRoot(), "features.tsv");
		Files.writeString(features.toPath(), "id\tLabel\tsequence\n"
				+ targetId + "\t1\t-." + target.getPeptideModSeq() + ".-\n"
				+ decoyId + "\t-1\t-." + decoy.getPeptideModSeq() + ".-\n", StandardCharsets.UTF_8);
		File output = new File(temporaryFolder.getRoot(), "pairs.tsv");
		sidecar.write(features, output, new File("source.elib"), new File("run.dia"));
		SearchPairSidecar.requireMatches(features, output);
		String content = Files.readString(output.toPath());
		assertTrue(content.contains("SEARCH_TARGET_DECOY\tSHUFFLED\tshuffle\tPAIRED\tWINNER"));
		assertTrue(content.contains(targetId));
		assertTrue(content.contains(decoyId));
		File partition = new File(temporaryFolder.getRoot(), "reference.pairs.tsv");
		SearchPairSidecar.partition(output, partition, Set.of(targetId));
		String selected = Files.readString(partition.toPath());
		assertTrue(selected.contains(targetId));
		assertTrue(!selected.contains(decoyId));
		SearchPairSidecar.partition(output, partition, Set.of(decoyId));
		String replaced = Files.readString(partition.toPath());
		assertTrue(replaced.contains(decoyId));
		assertTrue(!replaced.contains(targetId));
		Files.writeString(features.toPath(), Files.readString(features.toPath()).replace(targetId + "\t1", targetId + "\t-1"));
		try {
			SearchPairSidecar.requireMatches(features, output);
		} catch (java.io.IOException expected) {
			assertTrue(expected.getMessage().contains("disagree"));
			return;
		}
		throw new AssertionError("Expected mismatched feature label to fail");
	}

	@Test
	public void rejectsFeatureWithNoSearchSelection() throws Exception {
		SearchPairSidecar sidecar = new SearchPairSidecar();
		LibraryEntry target = entry("PEPTIDEK", SearchParameterParser.getDefaultParametersObject());
		File features = new File(temporaryFolder.getRoot(), "features.tsv");
		Files.writeString(features.toPath(), "id\tLabel\tsequence\n" + id(target) + "\t1\t-.PEPTIDEK.-\n");
		File output = new File(temporaryFolder.getRoot(), "pairs.tsv");
		try {
			sidecar.write(features, output,
					new File("source.elib"), new File("run.dia"));
		} catch (java.io.IOException expected) {
			assertTrue(expected.getMessage().contains("No search-pair relation"));
			assertTrue(!output.exists());
			return;
		}
		throw new AssertionError("Expected a missing-relation error");
	}

	@Test
	public void keepsModifiedSequenceAndChargeDistinct() throws Exception {
		SearchParameters parameters = SearchParameterParser.getDefaultParametersObject();
		String modified = "PEPC[+57.0214635]TIDEK";
		LibraryEntry two = entry(modified, (byte) 2, parameters);
		LibraryEntry three = entry(modified, (byte) 3, parameters);
		SearchPairSidecar sidecar = new SearchPairSidecar();
		sidecar.record(new EncyclopediaTwo.SearchSelection(two, List.of(two),
				EncyclopediaTwoDecoyStrategy.Mode.PREDICTED));
		sidecar.record(new EncyclopediaTwo.SearchSelection(three, List.of(three),
				EncyclopediaTwoDecoyStrategy.Mode.PREDICTED));
		File features = new File(temporaryFolder.getRoot(), "modified.tsv");
		Files.writeString(features.toPath(), "id\tLabel\tsequence\n"
				+ id(two) + "\t1\t-." + modified + ".-\n"
				+ id(three) + "\t1\t-." + modified + ".-\n");
		File output = new File(temporaryFolder.getRoot(), "modified.pairs.tsv");
		sidecar.write(features, output, new File("source.elib"), new File("run.dia"));
		var targets = SearchPairSidecar.targetKeysByFeatureId(output);
		assertEquals(modified + "|2", targets.get(id(two)));
		assertEquals(modified + "|3", targets.get(id(three)));
	}

	@Test
	public void rejectsCorruptPairKeysAndNonwinnerRows() throws Exception {
		SearchParameters parameters = SearchParameterParser.getDefaultParametersObject();
		LibraryEntry target = entry("PEPTIDEK", parameters);
		LibraryEntry decoy = target.getDecoy(parameters);
		SearchPairSidecar pairs = new SearchPairSidecar();
		pairs.record(new EncyclopediaTwo.SearchSelection(target, List.of(target, decoy),
				EncyclopediaTwoDecoyStrategy.Mode.SHUFFLED));
		File features = new File(temporaryFolder.getRoot(), "check.features.tsv");
		Files.writeString(features.toPath(), "id\tLabel\tsequence\n"
				+ id(target) + "\t1\t-." + target.getPeptideModSeq() + ".-\n"
				+ id(decoy) + "\t-1\t-." + decoy.getPeptideModSeq() + ".-\n");
		File sidecar = new File(temporaryFolder.getRoot(), "check.pairs.tsv");
		pairs.write(features, sidecar, new File("source.elib"), new File("run.dia"));
		String original = Files.readString(sidecar.toPath());
		assertRejected(features, sidecar, original.replace(target.getPeptideModSeq() + "|2",
				target.getPeptideModSeq() + "|3"));
		assertRejected(features, sidecar, original.replace(decoy.getPeptideModSeq() + "|2",
				decoy.getPeptideModSeq() + "|3"));
		assertRejected(features, sidecar, original.replace("\tWINNER\t", "\tLOSER\t"));
	}

	private static void assertRejected(File features, File sidecar, String corrupted) throws Exception {
		Files.writeString(sidecar.toPath(), corrupted);
		assertThrows(IOException.class, () -> SearchPairSidecar.requireMatches(features, sidecar));
		assertThrows(IOException.class, () -> SearchPairSidecar.targetKeysByFeatureId(sidecar));
	}

	private static String id(LibraryEntry entry) {
		return PercolatorPeptide.getPSMID("run.dia", 10.0f, Optional.empty(), entry.isDecoy(),
				entry.getPeptideModSeq(), entry.getPrecursorCharge());
	}

	private static LibraryEntry entry(String sequence, SearchParameters parameters) {
		return entry(sequence, (byte) 2, parameters);
	}

	private static LibraryEntry entry(String sequence, byte charge, SearchParameters parameters) {
		AminoAcidConstants constants = parameters.getAAConstants();
		return new LibraryEntry("synthetic Context test", new HashSet<>(), constants.getChargedMass(sequence, charge),
				charge, sequence, 1, 10.0f, 1.0f, new double[] {250.0, 350.0},
				new float[] {10.0f, 5.0f}, Optional.of(1.0f), constants);
	}
}
