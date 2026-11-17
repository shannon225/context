package org.searlelab.context.xic;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.searlelab.context.datastructures.IsolationWindow;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryEntry;
import edu.washington.gs.maccoss.encyclopedia.datastructures.LibraryPrecursorMetadata;
import edu.washington.gs.maccoss.encyclopedia.filereaders.SearchParameterParser;
import edu.washington.gs.maccoss.encyclopedia.utils.massspec.PeptideUtils;

public class AssayIdentityResolverTest {
	private static final String CANONICAL = "[+42.010565]AETLEFNDVYQEVK";
	private static final String EXPORTED = "A[+42.010565]ETLEFNDVYQEVK";
	private LibraryPrecursorMetadata metadata(String sequence, byte charge) {
		return new LibraryPrecursorMetadata(sequence, charge, List.of("P"));
	}

	@Test public void uniqueExportResolvesWithoutChangingOriginalRowOrLibraryIdentity() throws Exception {
		var p = SearchParameterParser.getDefaultParametersObject();
		var resolver = new AssayIdentityResolver(List.of(metadata(CANONICAL, (byte) 2), metadata(CANONICAL, (byte) 2)), p.getAAConstants());
		assertEquals(CANONICAL, resolver.resolve(EXPORTED, (byte) 2));
		assertEquals(CANONICAL, resolver.resolve(CANONICAL, (byte) 2));
		var original = new IsolationWindow(EXPORTED, 800, (byte) 2, 100, 105, true);
		var resolved = resolver.resolve(original);
		assertEquals(EXPORTED, original.getCompound());
		assertEquals(CANONICAL, resolved.getCompound());
		assertEquals(original.getTargetMz(), resolved.getTargetMz(), 0);
		assertEquals(original.getRtMin(), resolved.getRtMin(), 0);
		assertEquals(original.getRtMax(), resolved.getRtMax(), 0);
		assertTrue(resolved.isDecoy());
		double ion = PeptideUtils.getPeptideModel(CANONICAL, p.getAAConstants()).getPrimaryIonObjects(p.getFragType(), (byte) 2, true)[3].getMass();
		var entry = new LibraryEntry("synthetic", new HashSet<>(), 800, (byte) 2, CANONICAL, 1, 102, 1,
				new double[] {ion}, new float[] {1}, Optional.empty(), p.getAAConstants());
		assertEquals(EXPORTED, entry.getAccuratePeptideModSeq(p.getAAConstants()));
		var tracker = new AssayCandidateTracker(List.of(original), p, resolver);
		assertEquals(1, tracker.matching(entry).size());
		assertSame(original, tracker.matching(entry).get(0).window());
		assertEquals(CANONICAL, tracker.matching(entry).get(0).correctedSequence());
		assertNull(AssayXicExtractor.referenceProblem(resolved, entry, p));
		var reader = (org.searlelab.msrawjava.io.StripeFileInterface) java.lang.reflect.Proxy.newProxyInstance(
				getClass().getClassLoader(), new Class<?>[] {org.searlelab.msrawjava.io.StripeFileInterface.class},
				(proxy, method, args) -> new ArrayList<>());
		var extracted = AssayXicExtractor.extract(1, original, entry, reader, p, resolver);
		assertNull(extracted.referenceProblem());
		assertEquals(EXPORTED, extracted.sequence());
		assertEquals(CANONICAL, entry.getPeptideModSeq());
	}

	@Test public void ambiguityIsRejectedEvenWhenOneCandidateIsALiteralMatch() {
		var p = SearchParameterParser.getDefaultParametersObject();
		var resolver = new AssayIdentityResolver(List.of(metadata(CANONICAL, (byte) 2), metadata(EXPORTED, (byte) 2)), p.getAAConstants());
		assertTrue(assertThrows(IllegalArgumentException.class, () -> resolver.resolve(EXPORTED, (byte) 2))
				.getMessage().contains("Ambiguous assay notation"));
	}

	@Test public void chargeAndInternalModificationSitesRemainDistinct() {
		var p = SearchParameterParser.getDefaultParametersObject();
		var resolver = new AssayIdentityResolver(List.of(metadata(CANONICAL, (byte) 2)), p.getAAConstants());
		assertEquals(EXPORTED, resolver.resolve(EXPORTED, (byte) 3));
		String internal = "AET[+42.010565]LEFNDVYQEVK";
		assertEquals(internal, resolver.resolve(internal, (byte) 2));
		assertEquals("AETLEFNDVYQEVK", resolver.resolve("AETLEFNDVYQEVK", (byte) 2));
	}
}
