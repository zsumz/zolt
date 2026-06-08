package com.zolt.classpath;

import com.zolt.resolve.Classpath;

public record ClasspathSet(
        Classpath compile,
        Classpath runtime,
        Classpath test,
        Classpath processor,
        Classpath testProcessor,
        Classpath quarkusDeployment) {
}
