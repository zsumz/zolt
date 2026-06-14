package com.zolt.classpath;

import com.zolt.dependency.DependencyScope;
import com.zolt.resolve.ResolvedClasspathPackage;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;

public final class ClasspathBuilder {
    public ClasspathSet build(List<ResolvedClasspathPackage> packages) {
        List<ResolvedClasspathPackage> sorted = packages.stream()
                .sorted(Comparator.comparing(ClasspathBuilder::sortKey))
                .toList();
        return new ClasspathSet(
                classpath(sorted, scope -> scope.entersMainCompileClasspath()),
                classpath(sorted, scope -> scope.entersMainRuntimeClasspath()),
                classpath(sorted, scope -> scope.entersMainRuntimeClasspath() || scope.entersTestClasspath()),
                classpath(sorted, scope -> scope.entersMainProcessorClasspath()),
                classpath(sorted, scope -> scope.entersTestProcessorClasspath()),
                classpath(sorted, scope -> scope == DependencyScope.QUARKUS_DEPLOYMENT));
    }

    private static Classpath classpath(
            List<ResolvedClasspathPackage> packages,
            Predicate<DependencyScope> includeScope) {
        LinkedHashSet<Path> entries = new LinkedHashSet<>();
        for (ResolvedClasspathPackage dependency : packages) {
            if (includeScope.test(dependency.scope())) {
                entries.add(dependency.resolvedPackage().jarPath());
            }
        }
        return new Classpath(List.copyOf(entries));
    }

    private static String sortKey(ResolvedClasspathPackage dependency) {
        return dependency.resolvedPackage().packageId()
                + ":"
                + dependency.resolvedPackage().selectedVersion()
                + ":"
                + dependency.scope();
    }
}
