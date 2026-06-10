package com.zolt.quarkus;

import io.quarkus.builder.BuildChainBuilder;
import io.quarkus.deployment.builditem.TestClassBeanBuildItem;
import io.quarkus.test.junit.buildchain.TestBuildChainCustomizerProducer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;

public final class ZoltQuarkusTestClassBeanCustomizer implements TestBuildChainCustomizerProducer {
    private static final DotName QUARKUS_TEST = DotName.createSimple("io.quarkus.test.junit.QuarkusTest");
    private static final String DIAGNOSTIC_FILE_PROPERTY =
            "zolt.quarkus.test-class-bean-diagnostic-file";

    @Override
    public Consumer<BuildChainBuilder> produce(Index index) {
        List<String> testClasses = quarkusTestClasses(index);
        writeDiagnostic(
                "producer.invoked=true",
                "producer.loader=" + classLoaderName(getClass().getClassLoader()),
                "producer.indexClass=" + (index == null ? "<null>" : index.getClass().getName()),
                "producer.candidates=" + joined(testClasses));
        return builder -> {
            writeDiagnostic(
                    "consumer.accepted=true",
                    "consumer.builderClass=" + builder.getClass().getName(),
                    "consumer.candidates=" + joined(testClasses));
            if (testClasses.isEmpty()) {
                return;
            }
            builder.addBuildStep(context -> {
                        writeDiagnostic(
                                "buildStep.executed=true",
                                "buildStep.testClassBeanBuildItemLoader="
                                        + classLoaderName(TestClassBeanBuildItem.class.getClassLoader()),
                                "buildStep.produced=" + joined(testClasses));
                        for (String testClass : testClasses) {
                            context.produce(new TestClassBeanBuildItem(testClass));
                        }
                    })
                    .produces(TestClassBeanBuildItem.class)
                    .build();
        };
    }

    private static List<String> quarkusTestClasses(Index index) {
        if (index == null) {
            return List.of();
        }
        List<String> testClasses = new ArrayList<>();
        for (AnnotationInstance annotation : index.getAnnotations(QUARKUS_TEST)) {
            AnnotationTarget target = annotation.target();
            if (target.kind() == AnnotationTarget.Kind.CLASS && !target.asClass().isAnnotation()) {
                testClasses.add(target.asClass().name().toString());
            }
        }
        return testClasses.stream()
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private static String joined(List<String> values) {
        return values.isEmpty() ? "<none>" : String.join(",", values);
    }

    private static void writeDiagnostic(String... lines) {
        String diagnosticFile = System.getProperty(DIAGNOSTIC_FILE_PROPERTY, "");
        if (diagnosticFile.isBlank()) {
            return;
        }
        try {
            Path path = Path.of(diagnosticFile).toAbsolutePath().normalize();
            Files.createDirectories(path.getParent());
            StringBuilder content = new StringBuilder();
            for (String line : lines) {
                content.append(line).append(System.lineSeparator());
            }
            Files.writeString(
                    path,
                    content.toString(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException exception) {
            // Diagnostics must not change Quarkus test augmentation behavior.
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
