package com.zolt.quarkus;

import io.quarkus.builder.BuildChainBuilder;
import io.quarkus.deployment.builditem.TestClassBeanBuildItem;
import io.quarkus.test.junit.buildchain.TestBuildChainCustomizerProducer;
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

    @Override
    public Consumer<BuildChainBuilder> produce(Index index) {
        List<String> testClasses = quarkusTestClasses(index);
        return builder -> {
            if (testClasses.isEmpty()) {
                return;
            }
            builder.addBuildStep(context -> {
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
}
