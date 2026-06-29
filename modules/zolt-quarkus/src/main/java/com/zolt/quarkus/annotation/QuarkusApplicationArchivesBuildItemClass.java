package com.zolt.quarkus.annotation;

import io.quarkus.builder.BuildChainBuilder;
import io.quarkus.builder.item.SimpleBuildItem;
import java.util.Optional;

final class QuarkusApplicationArchivesBuildItemClass {
    private static final String APPLICATION_ARCHIVES_BUILD_ITEM =
            "io.quarkus.deployment.builditem.ApplicationArchivesBuildItem";

    private QuarkusApplicationArchivesBuildItemClass() {
    }

    @SuppressWarnings("unchecked")
    static Optional<Class<? extends SimpleBuildItem>> resolve(BuildChainBuilder builder) {
        try {
            ClassLoader classLoader = ZoltQuarkusTestClassBeanCustomizer.buildChainClassLoader(builder);
            Class<?> buildItemClass = Class.forName(APPLICATION_ARCHIVES_BUILD_ITEM, false, classLoader);
            if (!SimpleBuildItem.class.isAssignableFrom(buildItemClass)) {
                ZoltQuarkusTestClassBeanCustomizer.writeDiagnostic(
                        "applicationArchivesBuildItem.available=false",
                        "applicationArchivesBuildItem.reason=not-simple-build-item",
                        "applicationArchivesBuildItem.loader="
                                + ZoltQuarkusTestClassBeanCustomizer.classLoaderName(buildItemClass.getClassLoader()));
                return Optional.empty();
            }
            ZoltQuarkusTestClassBeanCustomizer.writeDiagnostic(
                    "applicationArchivesBuildItem.available=true",
                    "applicationArchivesBuildItem.loader="
                            + ZoltQuarkusTestClassBeanCustomizer.classLoaderName(buildItemClass.getClassLoader()));
            return Optional.of((Class<? extends SimpleBuildItem>) buildItemClass);
        } catch (ReflectiveOperationException | LinkageError exception) {
            ZoltQuarkusTestClassBeanCustomizer.writeDiagnostic(
                    "applicationArchivesBuildItem.available=false",
                    "applicationArchivesBuildItem.reason=" + exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
