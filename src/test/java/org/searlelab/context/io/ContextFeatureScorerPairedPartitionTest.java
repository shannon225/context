package org.searlelab.context.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import java.io.File;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.searlelab.context.encyclopedia.EncyclopediaTwo;
import org.searlelab.context.encyclopedia.SearchPairSidecar;

import edu.washington.gs.maccoss.encyclopedia.algorithms.library.EncyclopediaTwoDecoyStrategy;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.PercolatorPeptide;
import edu.washington.gs.maccoss.encyclopedia.datastructures.AminoAcidConstants;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryEntry;
import edu.washington.gs.maccoss.encyclopedia.datastructures.ReverseLibraryEntry;
import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchParameters;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;

public class ContextFeatureScorerPairedPartitionTest {
	@Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void predictedDecoyInheritsItsTargetReferencePopulation() throws Exception {
		SearchParameters parameters = SearchParameterParser.getDefaultParametersObject();
		AminoAcidConstants constants = parameters.getAAConstants();
		byte charge = 2;
		LibraryEntry target = new LibraryEntry("synthetic Context test", new HashSet<>(),
				constants.getChargedMass("PEPTIDEK", charge), charge, "PEPTIDEK", 1, 10f, 1f,
				new double[] {250, 350}, new float[] {10, 5}, Optional.empty(), constants);
		LibraryEntry predictedDecoy = new ReverseLibraryEntry("synthetic Context test", new HashSet<>(),
				constants.getChargedMass("PEDITPEK", charge), charge, "PEDITPEK", 1, 10f, 1f,
				new double[] {250, 350}, new float[] {10, 5}, new float[] {1, 1}, new boolean[] {true, true},
				Optional.empty(), constants);
		File features = new File(temporaryFolder.getRoot(), "raw.features.tsv");
		String targetId = id(target);
		String decoyId = id(predictedDecoy);
		Files.writeString(features.toPath(), "id\tLabel\tprecursorMz\tRTinMin\tprimary\tsequence\tProteins\n"
				+ row(targetId, 1, target) + row(decoyId, -1, predictedDecoy));
		SearchPairSidecar pairs = new SearchPairSidecar();
		pairs.record(new EncyclopediaTwo.SearchSelection(target, List.of(target, predictedDecoy),
				EncyclopediaTwoDecoyStrategy.Mode.PREDICTED));
		pairs.write(features, new File(features.getAbsolutePath() + ".pairs.tsv"),
				new File("source.elib"), new File("run.dia"));
		File massList = new File(temporaryFolder.getRoot(), "assay.txt");
		Files.writeString(massList.toPath(), "compound\tc1\tc2\tmz\tcharge\trt\twidth\n"
				+ "PEPTIDEK\t-\t-\t" + target.getPrecursorMZ() + "\t2\t10\t2\n");
		String base = new File(temporaryFolder.getRoot(), "run").getAbsolutePath();
		assertEquals(2, ContextFeatureScorer.partitionFeatures(features, parameters, base, massList).size());
		File reference = new File(base + "_reference.features.txt");
		File background = new File(base + "_background.features.txt");
		assertEquals(3, Files.readAllLines(reference.toPath()).size());
		assertEquals(1, Files.readAllLines(background.toPath()).size());
		String selectedPairs = Files.readString(new File(reference.getAbsolutePath() + ".pairs.tsv").toPath());
		assertTrue(selectedPairs.contains(targetId));
		assertTrue(selectedPairs.contains(decoyId));
		assertEquals(2, ContextFeatureScorer.partitionFeatures(features, parameters, base, massList).size());
		File rawSidecar = new File(features.getAbsolutePath() + ".pairs.tsv");
		String targetRelation = Files.readAllLines(rawSidecar.toPath()).get(1);
		Files.writeString(rawSidecar.toPath(), "unmatched" + targetRelation.substring(targetRelation.indexOf('\t')) + "\n",
				java.nio.file.StandardOpenOption.APPEND);
		assertTrue(assertThrows(java.io.IOException.class,
				() -> ContextFeatureScorer.partitionFeatures(features, parameters, base, massList))
				.getMessage().contains("ids differ from feature file"));
	}

	@Test
	public void assayedChargeSurvivesHigherScoringUnassayedCharge() throws Exception {
		SearchParameters parameters = SearchParameterParser.getDefaultParametersObject();
		AminoAcidConstants constants = parameters.getAAConstants();
		LibraryEntry assayed = new LibraryEntry("synthetic Context test", new HashSet<>(),
				constants.getChargedMass("PEPTIDEK", (byte) 2), (byte) 2, "PEPTIDEK", 1, 10f, 1f,
				new double[] {250, 350}, new float[] {10, 5}, Optional.empty(), constants);
		LibraryEntry unassayed = new LibraryEntry("synthetic Context test", new HashSet<>(),
				constants.getChargedMass("PEPTIDEK", (byte) 3), (byte) 3, "PEPTIDEK", 1, 10f, 1f,
				new double[] {250, 350}, new float[] {10, 5}, Optional.empty(), constants);
		File features = new File(temporaryFolder.getRoot(), "raw.features.tsv");
		Files.writeString(features.toPath(), "id\tLabel\tprecursorMz\tRTinMin\tprimary\tsequence\tProteins\n"
				+ row(id(assayed), 1, assayed)
				+ row(id(unassayed), 1, unassayed, 2));
		SearchPairSidecar pairs = new SearchPairSidecar();
		pairs.record(new EncyclopediaTwo.SearchSelection(assayed, List.of(assayed),
				EncyclopediaTwoDecoyStrategy.Mode.PREDICTED));
		pairs.record(new EncyclopediaTwo.SearchSelection(unassayed, List.of(unassayed),
				EncyclopediaTwoDecoyStrategy.Mode.PREDICTED));
		pairs.write(features, new File(features.getAbsolutePath() + ".pairs.tsv"),
				new File("source.elib"), new File("run.dia"));
		File massList = new File(temporaryFolder.getRoot(), "assay.txt");
		Files.writeString(massList.toPath(), "compound\tc1\tc2\tmz\tcharge\trt\twidth\n"
				+ "PEPTIDEK\t-\t-\t" + assayed.getPrecursorMZ() + "\t2\t10\t2\n");
		String base = new File(temporaryFolder.getRoot(), "run").getAbsolutePath();
		assertEquals(2, ContextFeatureScorer.partitionFeatures(features, parameters, base, massList).size());
		assertTrue(Files.readString(new File(base + "_reference.features.txt").toPath()).contains(id(assayed)));
		assertTrue(Files.readString(new File(base + "_background.features.txt").toPath()).contains(id(unassayed)));
	}

	@Test public void retainsBestOffScheduleReferenceForPairedAndLegacyInput() throws Exception {
		var parameters = SearchParameterParser.getDefaultParametersObject();
		var constants = parameters.getAAConstants();
		var target = new LibraryEntry("synthetic", new HashSet<>(), constants.getChargedMass("PEPTIDEK", (byte) 2),
				(byte) 2, "PEPTIDEK", 1, 10f, 1f, new double[] {250}, new float[] {10}, Optional.empty(), constants);
		for (boolean paired : new boolean[] {false, true}) {
			File features = new File(temporaryFolder.getRoot(), "outside-" + paired + ".tsv");
			String outsideId = PercolatorPeptide.getPSMID("run.dia", 20f, Optional.empty(), false, "PEPTIDEK", (byte) 2);
			Files.writeString(features.toPath(), "id\tLabel\tprecursorMz\tRTinMin\tprimary\tsequence\tProteins\n"
					+ row(id(target), 1, target, 10)
					+ outsideId + "\t1\t" + target.getPrecursorMZ() + "\t20\t100\t-.PEPTIDEK.-\tP\n");
			if (paired) {
				var pairs = new SearchPairSidecar();
				pairs.record(new EncyclopediaTwo.SearchSelection(target, List.of(target), EncyclopediaTwoDecoyStrategy.Mode.PREDICTED));
				pairs.write(features, new File(features + ".pairs.tsv"), new File("source.elib"), new File("run.dia"));
			}
			File assay = new File(temporaryFolder.getRoot(), "outside-" + paired + ".txt");
			Files.writeString(assay.toPath(), "compound\tc1\tc2\tmz\tcharge\trt\twidth\nPEPTIDEK\t-\t-\t500\t2\t10\t2\n");
			String base = new File(temporaryFolder.getRoot(), "outside-output-" + paired).getAbsolutePath();
			ContextFeatureScorer.partitionFeatures(features, parameters, base, assay);
			assertTrue(Files.readString(new File(base + "_reference.features.txt").toPath()).contains(outsideId));
			assertTrue(!Files.readString(new File(base + "_background.features.txt").toPath()).contains(outsideId));
		}
	}

	@Test public void legacyMatchingPreservesChargeAndModificationIdentity() throws Exception {
		var parameters = SearchParameterParser.getDefaultParametersObject();
		var windows = new java.util.ArrayList<org.searlelab.context.datastructures.IsolationWindow>();
		windows.add(new org.searlelab.context.datastructures.IsolationWindow("PEPTIDEK", 500, (byte) 2, 540, 660, false));
		var wrongCharge = new org.searlelab.context.datastructures.ScoredFeature(500, (byte) 3, false, 1, 10, "PEPTIDEK", "P", "unused");
		var modified = new org.searlelab.context.datastructures.ScoredFeature(500, (byte) 2, false, 1, 10, "PEPT[+79.9663]IDEK", "P", "unused");
		org.junit.Assert.assertNull(ContextFeatureScorer.findMatchingMassListWindow(wrongCharge, windows, parameters));
		org.junit.Assert.assertNull(ContextFeatureScorer.findMatchingMassListWindow(modified, windows, parameters));
	}

	@Test public void uniqueExportAliasKeepsCanonicalTargetAndDecoyOutOfBackground() throws Exception {
		var parameters = SearchParameterParser.getDefaultParametersObject();
		var aa = parameters.getAAConstants();
		String canonical = "[+42.010565]AETLEFNDVYQEVK";
		var target = new LibraryEntry("synthetic", new HashSet<>(), aa.getChargedMass(canonical, (byte) 2),
				(byte) 2, canonical, 1, 10, 1, new double[] {250}, new float[] {10}, Optional.empty(), aa);
		var decoy = target.getDecoy(parameters);
		var identities = new org.searlelab.context.xic.AssayIdentityResolver(List.of(
				new edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryPrecursorMetadata(canonical, (byte) 2, List.of())), aa);
		File features = new File(temporaryFolder.getRoot(), "alias.tsv");
		String content = "id\tLabel\tprecursorMz\tRTinMin\tprimary\tsequence\tProteins\n"
				+ row(id(target), 1, target) + row(id(decoy), -1, decoy);
		Files.writeString(features.toPath(), content);
		var pairs = new SearchPairSidecar();
		pairs.record(new EncyclopediaTwo.SearchSelection(target, List.of(target, decoy), EncyclopediaTwoDecoyStrategy.Mode.PREDICTED));
		pairs.write(features, new File(features + ".pairs.tsv"), new File("library.elib"), new File("run.dia"));
		File assay = new File(temporaryFolder.getRoot(), "alias-assay.txt");
		String assayText = "compound\tc1\tc2\tmz\tcharge\trt\twidth\n" + target.getAccuratePeptideModSeq(aa)
				+ "\t-\t-\t" + target.getPrecursorMZ() + "\t2\t10\t2\n";
		Files.writeString(assay.toPath(), assayText);
		String base = new File(temporaryFolder.getRoot(), "alias-output").getAbsolutePath();
		var rows = ContextFeatureScorer.partitionFeatures(features, parameters, base, assay, identities);
		assertEquals(2, rows.size());
		assertTrue(rows.stream().noneMatch(r -> r.isBackground()));
		assertEquals(new java.util.TreeSet<>(content.lines().toList()),
				new java.util.TreeSet<>(Files.readAllLines(new File(base + "_reference.features.txt").toPath())));
		assertEquals(assayText, Files.readString(assay.toPath()));
	}

	private static String id(LibraryEntry entry) {
		return PercolatorPeptide.getPSMID("run.dia", 10f, Optional.empty(), entry.isDecoy(),
				entry.getPeptideModSeq(), entry.getPrecursorCharge());
	}

	private static String row(String id, int label, LibraryEntry entry) {
		return row(id, label, entry, 1);
	}

	private static String row(String id, int label, LibraryEntry entry, int primary) {
		return id + "\t" + label + "\t" + entry.getPrecursorMZ() + "\t10\t" + primary + "\t-."
				+ entry.getPeptideModSeq() + ".-\tP\n";
	}
}
