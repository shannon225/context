package edu.washington.gs.maccoss.encyclopedia;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Optional;

import org.junit.Test;

import edu.washington.gs.maccoss.encyclopedia.algorithms.scribe.ScribeTwoPrealignmentToleranceRefinement;
import edu.washington.gs.maccoss.encyclopedia.algorithms.scribe.ScribeTwoSearchParameters;

public class ContextScribeTwoBridgeTest {

	@Test
	public void absentPreAlignmentCannotBecomeUncalibratedSuccess() throws Exception {
		try {
			ContextScribeTwoBridge.requirePrealignment(Optional.empty());
			fail("Required prealignment must fail");
		} catch (IOException expected) {
			assertTrue(expected.getMessage().contains("Full search has not started."));
		}
	}
	
	@Test public void insufficientResidualsPreserveRequestedParameters() {
		var parameters = (ScribeTwoSearchParameters) 
				ScribeTwoSearchParameters.getDefaultParametersObject();
		assertSame(parameters, ContextScribeTwoBridge.refineParameters(parameters, ScribeTwoPrealignmentToleranceRefinement.disabled()));
	}

}
