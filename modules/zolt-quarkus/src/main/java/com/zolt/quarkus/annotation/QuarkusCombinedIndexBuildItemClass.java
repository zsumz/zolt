package com.zolt.quarkus.annotation;

import io.quarkus.builder.BuildChainBuilder;
import io.quarkus.builder.item.SimpleBuildItem;
import java.util.Optional;

final class QuarkusCombinedIndexBuildItemClass {
    private static final String COMBINED_INDEX_BUILD_ITEM =
            "io.quarkus.deployment.builditem.CombinedIndexBuildItem";

    private QuarkusCombinedIndexBuildItemClass() {
    }

    @SuppressWarnings("unchecked")
    static Optional<Class<? extends SimpleBuildItem>> resolve(BuildChainBuilder builder) {
        try {
            ClassLoader classLoader = ZoltQuarkusTestClassBeanCustomizer.buildChainClassLoader(builder);
            Class<?> buildItemClass = Class.forName(COMBINED_INDEX_BUILD_ITEM, false, classLoader);
            if (!SimpleBuildItem.class.isAssignableFrom(buildItemClass)) {
                ZoltQuarkusTestClassBeanCustomizer.writeDiagnostic(
                        "combinedIndexBuildItem.available=false",
                        "combinedIndexBuildItem.reason=not-simple-build-item",
                        "combinedIndexBuildItem.loader="
                                + ZoltQuarkusTestClassBeanCustomizer.classLoaderName(buildItemClass.getClassLoader()));
                return Optional.empty();
            }
            ZoltQuarkusTestClassBeanCustomizer.writeDiagnostic(
                    "combinedIndexBuildItem.available=true",
                    "combinedIndexBuildItem.loader="
                            + ZoltQuarkusTestClassBeanCustomizer.classLoaderName(buildItemClass.getClassLoader()));
            return Optional.of((Class<? extends SimpleBuildItem>) buildItemClass);
        } catch (ReflectiveOperationException | LinkageError exception) {
            ZoltQuarkusTestClassBeanCustomizer.writeDiagnostic(
                    "combinedIndexBuildItem.available=false",
                    "combinedIndexBuildItem.reason=" + exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
