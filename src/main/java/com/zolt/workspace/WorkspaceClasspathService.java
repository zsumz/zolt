package com.zolt.workspace;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.ClasspathSet;
import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WorkspaceClasspathService {
    private final ZoltLockfileReader lockfileReader;
    private final ClasspathBuilder classpathBuilder;

    public WorkspaceClasspathService() {
        this(new ZoltLockfileReader(), new ClasspathBuilder());
    }

    WorkspaceClasspathService(
            ZoltLockfileReader lockfileReader,
            ClasspathBuilder classpathBuilder) {
        this.lockfileReader = lockfileReader;
        this.classpathBuilder = classpathBuilder;
    }

    public ClasspathSet classpathsFor(
            Workspace workspace,
            ZoltLockfile lockfile,
            Path cacheRoot,
            String memberPath) {
        Set<String> dependencyClosure = dependencyClosure(workspace, memberPath);
        Set<String> visibleMembers = new LinkedHashSet<>();
        visibleMembers.add(memberPath);
        visibleMembers.addAll(dependencyClosure);
        ZoltLockfile filteredLockfile = new ZoltLockfile(
                lockfile.version(),
                classpathPackagesFor(lockfile.packages(), dependencyClosure, visibleMembers),
                List.of());
        return classpathBuilder.build(lockfileReader.classpathPackages(
                filteredLockfile,
                cacheRoot,
                workspace.root()));
    }

    private static List<LockPackage> classpathPackagesFor(
            List<LockPackage> packages,
            Set<String> dependencyClosure,
            Set<String> visibleMembers) {
        List<LockPackage> filteredPackages = new ArrayList<>();
        for (LockPackage lockPackage : packages) {
            if (lockPackage.workspace().isPresent()) {
                if (dependencyClosure.contains(lockPackage.workspace().orElseThrow())) {
                    filteredPackages.add(lockPackage);
                }
                continue;
            }

            if (lockPackage.members().isEmpty() || intersects(lockPackage.members(), visibleMembers)) {
                filteredPackages.add(lockPackage);
            }
        }
        return filteredPackages;
    }

    private static boolean intersects(List<String> members, Set<String> visibleMembers) {
        for (String member : members) {
            if (visibleMembers.contains(member)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> dependencyClosure(Workspace workspace, String memberPath) {
        Map<String, List<String>> dependenciesByMember = dependenciesByMember(workspace);
        Set<String> closure = new LinkedHashSet<>();
        for (String dependency : dependenciesByMember.getOrDefault(memberPath, List.of())) {
            includeDependency(dependency, dependenciesByMember, closure);
        }
        return closure;
    }

    private static void includeDependency(
            String memberPath,
            Map<String, List<String>> dependenciesByMember,
            Set<String> closure) {
        if (!closure.add(memberPath)) {
            return;
        }
        for (String dependency : dependenciesByMember.getOrDefault(memberPath, List.of())) {
            includeDependency(dependency, dependenciesByMember, closure);
        }
    }

    private static Map<String, List<String>> dependenciesByMember(Workspace workspace) {
        Map<String, List<String>> dependencies = new LinkedHashMap<>();
        for (WorkspaceMember member : workspace.members()) {
            dependencies.put(member.path(), new ArrayList<>());
        }
        for (WorkspaceProjectEdge edge : workspace.edges()) {
            dependencies.get(edge.from()).add(edge.to());
        }
        return dependencies;
    }
}
