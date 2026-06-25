package com.zolt.quarkus;

import java.lang.reflect.Method;

final class QuarkusAdditionalBeanBuildItemBridge {
    private QuarkusAdditionalBeanBuildItemBridge() {
    }

    static void markBuilderUnremovable(Object builder) throws ReflectiveOperationException {
        Method setUnremovable = builder.getClass().getMethod("setUnremovable");
        setUnremovable.invoke(builder);
    }
}
