package com.zolt.classpath;

public record ClasspathSet(
        Classpath compile,
        Classpath runtime,
        Classpath test,
        Classpath processor,
        Classpath testProcessor,
        Classpath quarkusDeployment) {
}
