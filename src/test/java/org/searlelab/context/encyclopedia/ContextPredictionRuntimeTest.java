package org.searlelab.context.encyclopedia;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Properties;

import org.junit.Test;

public class ContextPredictionRuntimeTest {
	@Test
	public void pinnedLibraryHasRequiredApiAndMismatchNamesItsOrigin() throws Exception {
		ContextPredictionRuntime.requireLibraryApi();
		IOException error = assertThrows(IOException.class,
				() -> ContextPredictionRuntime.requireLibraryApi(ContextPredictionRuntimeTest.class));
		assertTrue(error.getMessage().contains("bundled Encyclopedia 6.9.23"));
		assertTrue(error.getMessage().contains(getClass().getProtectionDomain().getCodeSource().getLocation().toString()));
		assertTrue(error.getCause() instanceof NoSuchMethodException);
	}

	@Test
	public void allNativeDescriptorsRemainVisibleAndClassLoaderIsRestored() throws Exception {
		ClassLoader original = Thread.currentThread().getContextClassLoader();
		try (ContextPredictionRuntime runtime = new ContextPredictionRuntime()) {
			HashSet<String> classifiers = new HashSet<>();
			Enumeration<URL> resources = Thread.currentThread().getContextClassLoader()
					.getResources("native/lib/pytorch.properties");
			while (resources.hasMoreElements()) {
				Properties properties = new Properties();
				try (var stream = resources.nextElement().openStream()) {
					properties.load(stream);
				}
				classifiers.add(properties.getProperty("classifier"));
			}
			assertEquals(java.util.Set.of("win-x86_64", "linux-x86_64", "osx-aarch64"), classifiers);
			ClassLoader adapted = Thread.currentThread().getContextClassLoader();
			try (ContextPredictionRuntime nested = new ContextPredictionRuntime()) {
				assertSame(adapted, Thread.currentThread().getContextClassLoader());
			}
			assertSame(adapted, Thread.currentThread().getContextClassLoader());
		}
		assertSame(original, Thread.currentThread().getContextClassLoader());
	}

	@Test
	public void partialPackagedMetadataFailsInsteadOfFallingBackToDownloads() throws Exception {
		ClassLoader original = Thread.currentThread().getContextClassLoader();
		ClassLoader partial = new ClassLoader(original) {
			@Override
			public URL getResource(String name) {
				if (name.startsWith("context/pytorch/")) {
					return name.contains("win-x86_64") ? super.getResource("native/lib/pytorch.properties") : null;
				}
				return super.getResource(name);
			}
		};
		try {
			Thread.currentThread().setContextClassLoader(partial);
			try (ContextPredictionRuntime runtime = new ContextPredictionRuntime()) {
				IOException error = assertThrows(IOException.class, () -> Thread.currentThread()
						.getContextClassLoader().getResources("native/lib/pytorch.properties"));
				assertTrue(error.getMessage().contains("Incomplete bundled"));
			}
			assertSame(partial, Thread.currentThread().getContextClassLoader());
		} finally {
			Thread.currentThread().setContextClassLoader(original);
		}
	}

	@Test
	public void intelMacErrorNamesTheApprovedContainerRoute() throws Exception {
		String os = System.getProperty("os.name");
		String arch = System.getProperty("os.arch");
		try {
			System.setProperty("os.name", "Mac OS X");
			System.setProperty("os.arch", "x86_64");
			IOException error = assertThrows(IOException.class, ContextPredictionRuntime::requirePredictionPlatform);
			assertTrue(error.getMessage().contains("linux/amd64 container"));
			System.setProperty("os.arch", "aarch64");
			ContextPredictionRuntime.requirePredictionPlatform();
		} finally {
			System.setProperty("os.name", os);
			System.setProperty("os.arch", arch);
		}
	}
}
