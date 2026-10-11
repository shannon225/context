package org.searlelab.context.encyclopedia;

import java.io.IOException;
import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;

import edu.washington.gs.maccoss.encyclopedia.filereaders.LibraryFile;

/** Preserves DJL's native resource discovery when dependencies share a shaded jar. */
public final class ContextPredictionRuntime implements AutoCloseable {

	private static final String DESCRIPTOR = "native/lib/pytorch.properties";
	private final ClassLoader previous;

	public ContextPredictionRuntime() {
		previous = Thread.currentThread().getContextClassLoader();
		if (!(previous instanceof NativeResources)) {
			Thread.currentThread().setContextClassLoader(new NativeResources(
					previous == null ? ContextPredictionRuntime.class.getClassLoader() : previous));
		}
	}

	@Override
	public void close() {
		Thread.currentThread().setContextClassLoader(previous);
	}

	public static void requireLibraryApi() throws IOException {
		requireLibraryApi(LibraryFile.class);
	}

	static void requireLibraryApi(Class<?> libraryClass) throws IOException {
		try {
			if (libraryClass.getMethod("getUniqueTargetPrecursorMetadata").getReturnType() != ArrayList.class
					|| libraryClass.getMethod("countUniqueTargetPrecursors").getReturnType() != long.class
					|| libraryClass.getMethod("countUniqueDecoyPrecursors").getReturnType() != long.class
					|| libraryClass.getMethod("addDecoyEntriesToExistingTable", ArrayList.class,
							HashMap.class, boolean.class).getReturnType() != void.class) {
				throw new NoSuchMethodException("Incompatible Context LibraryFile method signature");
			}
		} catch (NoSuchMethodException | LinkageError e) {
			CodeSource source = libraryClass.getProtectionDomain().getCodeSource();
			throw new IOException("Context requires the bundled Encyclopedia 6.9.23 API. Loaded "
					+ libraryClass.getName() + " from " + (source == null ? "unknown location" : source.getLocation())
					+ ". Remove conflicting Encyclopedia jars from the runtime/IDE classpath and use the jar under "
					+ "libs/maccoss/encyclopedia/6.9.23/. Missing or incompatible API: " + e.getMessage(), e);
		}
	}

	static void requirePredictionPlatform() throws IOException {
		String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
		String arch = System.getProperty("os.arch");
		if (os.contains("mac") && (arch.equals("x86_64") || arch.equals("amd64"))) {
			throw new IOException("Context prediction is unavailable in an Intel macOS JVM with the pinned "
					+ "JChronologer runtime. Run the Context linux/amd64 container on Intel Macs; "
					+ "on Apple Silicon use an ARM64 Java 17 runtime. See README platform setup.");
		}
	}

	private static final class NativeResources extends ClassLoader {
		NativeResources(ClassLoader parent) {
			super(parent);
		}

		@Override
		public Enumeration<URL> getResources(String name) throws IOException {
			if (!DESCRIPTOR.equals(name)) return super.getResources(name);
			ArrayList<URL> descriptors = new ArrayList<>();
			for (String classifier : new String[] {"osx-aarch64", "linux-x86_64", "win-x86_64"}) {
				URL descriptor = getParent().getResource("context/pytorch/" + classifier + "/" + DESCRIPTOR);
				if (descriptor != null) descriptors.add(descriptor);
			}
			// IDE/unshaded execution already has separate descriptors in dependency jars.
			if (descriptors.isEmpty()) return super.getResources(name);
			if (descriptors.size() != 3) throw new IOException("Incomplete bundled PyTorch platform metadata");
			return Collections.enumeration(descriptors);
		}
	}
}
