package sh.zolt.quarkus.annotation;

import sh.zolt.quarkus.QuarkusAugmentationException;
import sh.zolt.quarkus.testworker.descriptor.QuarkusTestRunnerDescriptor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public final class QuarkusAnnotationApiProbe {
    private static final String EXTENSION_CLASS = "io.quarkus.test.junit.QuarkusTestExtension";
    private static final String TEST_PROFILE_CLASS = "io.quarkus.test.junit.QuarkusTestProfile";
    private static final String LAUNCHER_INTERCEPTOR_CLASS =
            "io.quarkus.test.junit.launcher.CustomLauncherInterceptor";
    private static final String SESSION_LISTENER_CLASS = "org.junit.platform.launcher.LauncherSessionListener";
    private static final String DISCOVERY_LISTENER_CLASS = "org.junit.platform.launcher.LauncherDiscoveryListener";
    private static final String EXECUTION_LISTENER_CLASS = "org.junit.platform.launcher.TestExecutionListener";
    private static final String ARTIFACT_LAUNCHER_PROVIDER_CLASS =
            "io.quarkus.test.junit.launcher.ArtifactLauncherProvider";
    private static final String SESSION_LISTENER_SERVICE =
            "META-INF/services/org.junit.platform.launcher.LauncherSessionListener";
    private static final String DISCOVERY_LISTENER_SERVICE =
            "META-INF/services/org.junit.platform.launcher.LauncherDiscoveryListener";
    private static final String ARTIFACT_LAUNCHER_PROVIDER_SERVICE =
            "META-INF/services/io.quarkus.test.junit.launcher.ArtifactLauncherProvider";

    private final ClassLoader classLoader;
    private final Contract contract;

    public QuarkusAnnotationApiProbe() {
        this(defaultClassLoader(), Contract.quarkusDefaults());
    }

    QuarkusAnnotationApiProbe(ClassLoader classLoader, Contract contract) {
        if (classLoader == null) {
            throw new QuarkusAugmentationException("Quarkus annotation API probe classloader is required.");
        }
        if (contract == null) {
            throw new QuarkusAugmentationException("Quarkus annotation API probe contract is required.");
        }
        this.classLoader = classLoader;
        this.contract = contract;
    }

    public QuarkusAnnotationApi probe(QuarkusTestRunnerDescriptor descriptor) {
        if (descriptor == null) {
            throw new QuarkusAugmentationException("Quarkus annotation API probe requires a test runner descriptor.");
        }

        Class<?> extension = load(contract.extensionClass());
        publicNoArgConstructor(extension, "Quarkus test extension");
        load(contract.testProfileClass());

        Class<?> launcherInterceptor = load(contract.launcherInterceptorClass());
        requireAssignable(launcherInterceptor, load(contract.sessionListenerClass()), "launcher session listener");
        requireAssignable(launcherInterceptor, load(contract.discoveryListenerClass()), "launcher discovery listener");
        requireAssignable(launcherInterceptor, load(contract.executionListenerClass()), "test execution listener");
        requireServiceProvider(contract.sessionListenerService(), contract.launcherInterceptorClass());
        requireServiceProvider(contract.discoveryListenerService(), contract.launcherInterceptorClass());

        Class<?> artifactLauncherProvider = load(contract.artifactLauncherProviderClass());
        List<String> artifactLauncherProviders = serviceProviders(contract.artifactLauncherProviderService());
        if (artifactLauncherProviders.isEmpty()) {
            throw new QuarkusAugmentationException(
                    "Quarkus annotation test worker classpath is missing artifact launcher providers. "
                            + "Run `zolt resolve`, then run `zolt test` again.");
        }
        for (String provider : artifactLauncherProviders) {
            requireAssignable(load(provider), artifactLauncherProvider, "artifact launcher provider");
        }

        return new QuarkusAnnotationApi(
                extension.getName(),
                contract.testProfileClass(),
                launcherInterceptor.getName(),
                artifactLauncherProviders);
    }

    private Class<?> load(String className) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException exception) {
            throw new QuarkusAugmentationException(
                    "Quarkus annotation test worker classpath is missing "
                            + className
                            + ". Ensure Quarkus test dependencies were resolved and try again.",
                    exception);
        }
    }

    private static void publicNoArgConstructor(Class<?> type, String label) {
        try {
            type.getConstructor();
        } catch (NoSuchMethodException exception) {
            throw new QuarkusAugmentationException(
                    label
                            + " API is incompatible with Zolt. Missing public no-arg constructor on "
                            + type.getName()
                            + ". Update Zolt or use a supported Quarkus version.",
                    exception);
        }
    }

    private static void requireAssignable(Class<?> implementation, Class<?> expectedType, String label) {
        if (!expectedType.isAssignableFrom(implementation)) {
            throw new QuarkusAugmentationException(
                    "Quarkus annotation test worker classpath has incompatible "
                            + label
                            + " `"
                            + implementation.getName()
                            + "`. Update Zolt or use a supported Quarkus version.");
        }
    }

    private void requireServiceProvider(String service, String expectedProvider) {
        List<String> providers = serviceProviders(service);
        if (!providers.contains(expectedProvider)) {
            throw new QuarkusAugmentationException(
                    "Quarkus annotation test worker classpath is missing service provider `"
                            + expectedProvider
                            + "` in "
                            + service
                            + ". Run `zolt resolve`, then run `zolt test` again.");
        }
    }

    private List<String> serviceProviders(String service) {
        try {
            Enumeration<URL> resources = classLoader.getResources(service);
            List<String> providers = new ArrayList<>();
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        resource.openStream(),
                        StandardCharsets.UTF_8))) {
                    reader.lines()
                            .map(QuarkusAnnotationApiProbe::providerLine)
                            .filter(line -> !line.isBlank())
                            .forEach(providers::add);
                }
            }
            return List.copyOf(providers);
        } catch (IOException exception) {
            throw new QuarkusAugmentationException(
                    "Could not read Quarkus annotation test service provider metadata. "
                            + "Check that the test runtime classpath is readable.",
                    exception);
        }
    }

    private static String providerLine(String line) {
        int comment = line.indexOf('#');
        return (comment == -1 ? line : line.substring(0, comment)).trim();
    }

    record Contract(
            String extensionClass,
            String testProfileClass,
            String launcherInterceptorClass,
            String sessionListenerClass,
            String discoveryListenerClass,
            String executionListenerClass,
            String artifactLauncherProviderClass,
            String sessionListenerService,
            String discoveryListenerService,
            String artifactLauncherProviderService) {
        Contract {
            if (extensionClass == null || extensionClass.isBlank()) {
                throw new QuarkusAugmentationException("Quarkus annotation API contract requires an extension class.");
            }
            if (testProfileClass == null || testProfileClass.isBlank()) {
                throw new QuarkusAugmentationException("Quarkus annotation API contract requires a test profile class.");
            }
            if (launcherInterceptorClass == null || launcherInterceptorClass.isBlank()) {
                throw new QuarkusAugmentationException(
                        "Quarkus annotation API contract requires a launcher interceptor class.");
            }
            if (sessionListenerClass == null || sessionListenerClass.isBlank()) {
                throw new QuarkusAugmentationException(
                        "Quarkus annotation API contract requires a launcher session listener class.");
            }
            if (discoveryListenerClass == null || discoveryListenerClass.isBlank()) {
                throw new QuarkusAugmentationException(
                        "Quarkus annotation API contract requires a launcher discovery listener class.");
            }
            if (executionListenerClass == null || executionListenerClass.isBlank()) {
                throw new QuarkusAugmentationException(
                        "Quarkus annotation API contract requires a test execution listener class.");
            }
            if (artifactLauncherProviderClass == null || artifactLauncherProviderClass.isBlank()) {
                throw new QuarkusAugmentationException(
                        "Quarkus annotation API contract requires an artifact launcher provider class.");
            }
            if (sessionListenerService == null || sessionListenerService.isBlank()) {
                throw new QuarkusAugmentationException(
                        "Quarkus annotation API contract requires a launcher session listener service.");
            }
            if (discoveryListenerService == null || discoveryListenerService.isBlank()) {
                throw new QuarkusAugmentationException(
                        "Quarkus annotation API contract requires a launcher discovery listener service.");
            }
            if (artifactLauncherProviderService == null || artifactLauncherProviderService.isBlank()) {
                throw new QuarkusAugmentationException(
                        "Quarkus annotation API contract requires an artifact launcher provider service.");
            }
        }

        static Contract quarkusDefaults() {
            return new Contract(
                    EXTENSION_CLASS,
                    TEST_PROFILE_CLASS,
                    LAUNCHER_INTERCEPTOR_CLASS,
                    SESSION_LISTENER_CLASS,
                    DISCOVERY_LISTENER_CLASS,
                    EXECUTION_LISTENER_CLASS,
                    ARTIFACT_LAUNCHER_PROVIDER_CLASS,
                    SESSION_LISTENER_SERVICE,
                    DISCOVERY_LISTENER_SERVICE,
                    ARTIFACT_LAUNCHER_PROVIDER_SERVICE);
        }
    }

    private static ClassLoader defaultClassLoader() {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        return contextClassLoader == null ? QuarkusAnnotationApiProbe.class.getClassLoader() : contextClassLoader;
    }
}
