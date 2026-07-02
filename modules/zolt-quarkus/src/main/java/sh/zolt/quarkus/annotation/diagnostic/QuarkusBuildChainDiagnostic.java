package sh.zolt.quarkus.annotation.diagnostic;

import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

final class QuarkusBuildChainDiagnostic {
    private static final String TEST_BUILD_CHAIN_FUNCTION = "io.quarkus.test.junit.TestBuildChainFunction";
    private static final String TEST_BUILD_CHAIN_CUSTOMIZER_SPI =
            "io.quarkus.test.junit.buildchain.TestBuildChainCustomizerProducer";
    private static final String TEST_BUILD_CHAIN_CUSTOMIZER_SERVICE =
            "META-INF/services/io.quarkus.test.junit.buildchain.TestBuildChainCustomizerProducer";

    private final PrintStream out;

    QuarkusBuildChainDiagnostic(PrintStream out) {
        this.out = out;
    }

    void write(ClassLoader quarkusRuntimeClassLoader) {
        out.println("Zolt Quarkus build-chain diagnostic:");
        write("system", ClassLoader.getSystemClassLoader());
        if (quarkusRuntimeClassLoader != null
                && quarkusRuntimeClassLoader != ClassLoader.getSystemClassLoader()) {
            write("quarkusRuntime", quarkusRuntimeClassLoader);
        }
    }

    private void write(String label, ClassLoader classLoader) {
        out.println("  " + label + "Loader=" + classLoaderName(classLoader));
        try {
            Class<?> buildChainFunction = Class.forName(TEST_BUILD_CHAIN_FUNCTION, false, classLoader);
            ClassLoader buildChainLoader = buildChainFunction.getClassLoader();
            out.println("    TestBuildChainFunction.loader=" + classLoaderName(buildChainLoader));
            out.println("    serviceResources=" + serviceResources(buildChainLoader));
            out.println("    providers=" + serviceProviders(buildChainLoader));
        } catch (ReflectiveOperationException | LinkageError exception) {
            out.println("    TestBuildChainFunction=<unavailable: "
                    + exception.getClass().getSimpleName()
                    + ">");
        }
    }

    private List<String> serviceResources(ClassLoader classLoader) {
        try {
            Enumeration<URL> resources = classLoader.getResources(TEST_BUILD_CHAIN_CUSTOMIZER_SERVICE);
            List<String> urls = new ArrayList<>();
            while (resources.hasMoreElements()) {
                urls.add(resources.nextElement().toString());
            }
            return urls.isEmpty() ? List.of("<none>") : List.copyOf(urls);
        } catch (java.io.IOException exception) {
            return List.of("<unavailable: " + exception.getClass().getSimpleName() + ">");
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<String> serviceProviders(ClassLoader classLoader) {
        try {
            Class<?> spi = Class.forName(TEST_BUILD_CHAIN_CUSTOMIZER_SPI, false, classLoader);
            ServiceLoader<?> serviceLoader = ServiceLoader.load((Class) spi, classLoader);
            List<String> providers = new ArrayList<>();
            for (Object provider : serviceLoader) {
                providers.add(provider.getClass().getName()
                        + "@"
                        + classLoaderName(provider.getClass().getClassLoader()));
            }
            return providers.isEmpty() ? List.of("<none>") : List.copyOf(providers);
        } catch (ReflectiveOperationException | LinkageError | ServiceConfigurationError exception) {
            return List.of("<unavailable: " + exception.getClass().getSimpleName() + ">");
        }
    }

    private static String classLoaderName(ClassLoader classLoader) {
        if (classLoader == null) {
            return "<bootstrap>";
        }
        return classLoader.getClass().getName() + "@"
                + Integer.toHexString(System.identityHashCode(classLoader));
    }
}
