package org.searlelab.context.encyclopedia;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;

import org.junit.Test;

import edu.washington.gs.maccoss.encyclopedia.algorithms.library.EncyclopediaTwoDecoyStrategy;
import edu.washington.gs.maccoss.encyclopedia.datastructures.AminoAcidConstants;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryEntry;
import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchParameters;
import edu.washington.gs.maccoss.encyclopedia.filereaders.MatchedDecoyAssessment;
import edu.washington.gs.maccoss.encyclopedia.filereaders.MatchedDecoyKey;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;

public class EncyclopediaTwoMatchedDecoyTest {
	@Test
	public void predictedModeUsesMappedSpectrumAndKeepsUnpairedTarget() {
		SearchParameters parameters = SearchParameterParser.getDefaultParametersObject();
		LibraryEntry target = entry("PEPTIDEK", parameters);
		LibraryEntry decoy = entry("PEDITPEK", parameters);
		EncyclopediaTwoDecoyStrategy strategy = EncyclopediaTwoDecoyStrategy.resolve(
				new MatchedDecoyAssessment(true, true, 40, 39, 1, 0), 0f);
		HashMap<MatchedDecoyKey, LibraryEntry> mapped = new HashMap<>();
		mapped.put(new MatchedDecoyKey(target), decoy);
		assertEquals(EncyclopediaTwoDecoyStrategy.Mode.PREDICTED, strategy.getMode());
		assertSame(decoy, EncyclopediaTwo.getTargetAndPrimaryDecoy(target, strategy, mapped, parameters).get(1));
		mapped.clear();
		assertEquals(1, EncyclopediaTwo.getTargetAndPrimaryDecoy(target, strategy, mapped, parameters).size());
	}

	@Test
	public void inadequateCoverageUsesShuffledDecoy() {
		SearchParameters parameters = SearchParameterParser.getDefaultParametersObject();
		LibraryEntry target = entry("PEPTIDEK", parameters);
		EncyclopediaTwoDecoyStrategy strategy = EncyclopediaTwoDecoyStrategy.resolve(
				new MatchedDecoyAssessment(true, true, 40, 38, 2, 0), 0f);
		assertEquals(EncyclopediaTwoDecoyStrategy.Mode.SHUFFLED, strategy.getMode());
		assertEquals(2, EncyclopediaTwo.getTargetAndPrimaryDecoy(target, strategy, new HashMap<>(), parameters).size());
	}

	private static LibraryEntry entry(String sequence, SearchParameters parameters) {
		AminoAcidConstants constants = parameters.getAAConstants();
		byte charge = 2;
		return new LibraryEntry("synthetic Context test", new HashSet<>(), constants.getChargedMass(sequence, charge),
				charge, sequence, 1, 10.0f, 1.0f, new double[] {250.0, 350.0},
				new float[] {10.0f, 5.0f}, Optional.of(1.0f), constants);
	}
}
