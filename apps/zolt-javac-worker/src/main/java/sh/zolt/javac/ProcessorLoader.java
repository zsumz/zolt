package sh.zolt.javac;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.processing.Processor;

/**
 * Loads annotation {@link Processor} implementations from a processor classpath the same way javac's
 * own discovery would: a {@link ServiceLoader} over an isolated {@link URLClassLoader}. The class
 * loader is cached per exact processor-classpath so the persistent worker keeps it warm across
 * compiles, but processor instances are always created fresh (they are stateful per compile). Loaders
 * are never shared across different classpaths.
 */
final class ProcessorLoader {
    private static final int MAX_CACHED_LOADERS = 64;
    private static final ConcurrentMap<String, URLClassLoader> LOADERS = new ConcurrentHashMap<>();

    private ProcessorLoader() {
    }

    static List<Processor> load(List<Path> processorPath) {
        if (processorPath.isEmpty()) {
            return List.of();
        }
        URLClassLoader loader = LOADERS.computeIfAbsent(key(processorPath), ignored -> newLoader(processorPath));
        if (LOADERS.size() > MAX_CACHED_LOADERS) {
            LOADERS.clear();
            loader = newLoader(processorPath);
        }
        List<Processor> processors = new ArrayList<>();
        for (Processor processor : ServiceLoader.load(Processor.class, loader)) {
            processors.add(processor);
        }
        return processors;
    }

    private static String key(List<Path> processorPath) {
        StringBuilder key = new StringBuilder();
        for (Path entry : processorPath) {
            key.append(entry.toAbsolutePath().normalize()).append('\n');
        }
        return key.toString();
    }

    private static URLClassLoader newLoader(List<Path> processorPath) {
        List<URL> urls = new ArrayList<>();
        for (Path entry : processorPath) {
            try {
                urls.add(entry.toAbsolutePath().normalize().toUri().toURL());
            } catch (MalformedURLException exception) {
                throw new IllegalStateException("Invalid processor path entry: " + entry, exception);
            }
        }
        return new URLClassLoader(urls.toArray(URL[]::new), ProcessorLoader.class.getClassLoader());
    }
}
