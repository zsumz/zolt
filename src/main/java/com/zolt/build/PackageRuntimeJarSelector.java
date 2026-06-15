package com.zolt.build;

import com.zolt.classpath.LockfileClasspathPackageConverter;
import com.zolt.classpath.ResolvedClasspathPackage;
import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.lockfile.ZoltLockfile;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PackageRuntimeJarSelector {
    List<PackageRuntimeJar> runtimeJars(ZoltLockfile lockfile, Path cacheRoot) {
        return runtimeJars(packagedClasspathPackages(lockfile, cacheRoot));
    }

    List<PackageRuntimeJar> runtimeJars(List<ResolvedClasspathPackage> classpathPackages) {
        Map<String, PackageRuntimeJar> runtimeJars = new LinkedHashMap<>();
        packagedClasspathPackages(classpathPackages).stream()
                .filter(dependency -> dependency.scope().entersMainRuntimeClasspath())
                .sorted(Comparator.comparing(PackageRuntimeJarSelector::classpathSortKey))
                .map(PackageRuntimeJarSelector::runtimeJar)
                .forEach(runtimeJar -> runtimeJars.putIfAbsent(runtimeJarKey(runtimeJar), runtimeJar));
        return List.copyOf(runtimeJars.values());
    }

    List<PackageRuntimeJar> runtimeJarsWithoutProvidedDuplicates(List<ResolvedClasspathPackage> classpathPackages) {
        Set<PackageId> providedPackageIds = providedPackageIds(classpathPackages);
        return runtimeJars(classpathPackages).stream()
                .filter(runtimeJar -> !providedPackageIds.contains(runtimeJar.packageId()))
                .toList();
    }

    List<PackageRuntimeJar> providedJars(List<ResolvedClasspathPackage> classpathPackages) {
        Map<String, PackageRuntimeJar> providedJars = new LinkedHashMap<>();
        classpathPackages.stream()
                .filter(dependency -> dependency.scope() == DependencyScope.PROVIDED)
                .sorted(Comparator.comparing(PackageRuntimeJarSelector::classpathSortKey))
                .map(PackageRuntimeJarSelector::runtimeJar)
                .forEach(runtimeJar -> providedJars.putIfAbsent(runtimeJarKey(runtimeJar), runtimeJar));
        return List.copyOf(providedJars.values());
    }

    List<ResolvedClasspathPackage> packagedClasspathPackages(ZoltLockfile lockfile, Path cacheRoot) {
        return packagedClasspathPackages(LockfileClasspathPackageConverter.classpathPackages(lockfile, cacheRoot));
    }

    List<ResolvedClasspathPackage> allClasspathPackages(ZoltLockfile lockfile, Path cacheRoot) {
        return LockfileClasspathPackageConverter.classpathPackages(lockfile, cacheRoot);
    }

    private List<ResolvedClasspathPackage> packagedClasspathPackages(List<ResolvedClasspathPackage> classpathPackages) {
        return classpathPackages.stream()
                .filter(dependency -> dependency.scope().packagedByDefault())
                .toList();
    }

    private static Set<PackageId> providedPackageIds(List<ResolvedClasspathPackage> classpathPackages) {
        Set<PackageId> packageIds = new LinkedHashSet<>();
        classpathPackages.stream()
                .filter(dependency -> dependency.scope() == DependencyScope.PROVIDED)
                .map(dependency -> dependency.resolvedPackage().packageId())
                .forEach(packageIds::add);
        return Set.copyOf(packageIds);
    }

    private static PackageRuntimeJar runtimeJar(ResolvedClasspathPackage dependency) {
        return new PackageRuntimeJar(
                dependency.resolvedPackage().packageId(),
                dependency.resolvedPackage().selectedVersion(),
                dependency.resolvedPackage().jarPath());
    }

    private static String classpathSortKey(ResolvedClasspathPackage dependency) {
        return dependency.resolvedPackage().packageId()
                + ":"
                + dependency.resolvedPackage().selectedVersion()
                + ":"
                + dependency.scope();
    }

    private static String runtimeJarKey(PackageRuntimeJar runtimeJar) {
        return runtimeJar.packageId() + ":" + runtimeJar.version() + ":" + runtimeJar.jarPath();
    }
}
