package com.zolt.quarkus;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.ClasspathSet;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.ProjectConfig;
import java.nio.file.Path;

public final class QuarkusPlanService {
    private final ZoltLockfileReader lockfileReader;
    private final ClasspathBuilder classpathBuilder;

    public QuarkusPlanService() {
        this(new ZoltLockfileReader(), new ClasspathBuilder());
    }

    QuarkusPlanService(ZoltLockfileReader lockfileReader, ClasspathBuilder classpathBuilder) {
        this.lockfileReader = lockfileReader;
        this.classpathBuilder = classpathBuilder;
    }

    public QuarkusPlan plan(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        ZoltLockfile lockfile = lockfileReader.read(projectDirectory.resolve("zolt.lock"));
        return plan(projectDirectory, config, lockfile, cacheRoot);
    }

    public QuarkusPlan plan(
            Path projectDirectory,
            ProjectConfig config,
            ZoltLockfile lockfile,
            Path cacheRoot) {
        Path root = projectDirectory.toAbsolutePath().normalize();
        ClasspathSet classpaths = classpathBuilder.build(lockfileReader.classpathPackages(lockfile, cacheRoot));
        return new QuarkusPlan(
                root,
                root.resolve(config.build().output()).normalize(),
                classpaths.runtime().entries(),
                classpaths.quarkusDeployment().entries());
    }
}
