package com.zolt.build.classpath;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zolt.classpath.ClasspathSet;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.toml.ZoltLockfileReader;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ClasspathClassifierTest {
    private final ZoltLockfileReader reader = new ZoltLockfileReader();

    @Test
    void runtimeClasspathKeepsClassifierJarPath() {
        ZoltLockfile lockfile = reader.read("""
                version = 1

                [[package]]
                id = "com.example:native-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "runtime"
                direct = false
                jar = "com/example/native-lib/1.0.0/native-lib-1.0.0-linux-x86_64.jar"
                dependencies = []
                """);

        ClasspathSet classpaths = new ClasspathBuilder().build(
                LockfileClasspathPackageConverter.classpathPackages(lockfile));

        assertEquals(
                List.of(Path.of("com/example/native-lib/1.0.0/native-lib-1.0.0-linux-x86_64.jar")),
                classpaths.runtime().entries());
        assertEquals(classpaths.runtime().entries(), classpaths.test().entries());
    }
}
