package com.zolt.quarkus.annotation;

import java.lang.reflect.Method;

final class QuarkusAdditionalBeanBuildItemBridge {
    private QuarkusAdditionalBeanBuildItemBridge() {
    }

    static void markBuilderUnremovable(Object builder) throws ReflectiveOperationException {
        Method setUnremovable = builder.getClass().getMethod("setUnremovable");
        setUnremovable.invoke(builder);
    }

    static void setBuilderDefaultScope(
            Object builder,
            String scopeName) throws ReflectiveOperationException {
        Object defaultScope = defaultScope(builder, scopeName);
        Method setDefaultScope = defaultScopeSetter(builder, defaultScope);
        setDefaultScope.invoke(builder, defaultScope);
    }

    private static Method defaultScopeSetter(
            Object builder,
            Object defaultScope) throws NoSuchMethodException {
        for (Method method : builder.getClass().getMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (method.getName().equals("setDefaultScope")
                    && parameterTypes.length == 1
                    && parameterTypes[0].isAssignableFrom(defaultScope.getClass())) {
                return method;
            }
        }
        throw new NoSuchMethodException("setDefaultScope");
    }

    private static Object defaultScope(
            Object builder,
            String scopeName) throws ReflectiveOperationException {
        try {
            Class<?> dotNameClass = builder.getClass().getClassLoader().loadClass("org.jboss.jandex.DotName");
            return dotNameClass.getMethod("createSimple", String.class).invoke(null, scopeName);
        } catch (ClassNotFoundException exception) {
            return scopeName;
        }
    }
}
