package sh.zolt.workspace.service;

import sh.zolt.build.classpath.ClasspathBuilder;
import sh.zolt.classpath.ClasspathSet;
import sh.zolt.dependency.DependencyScope;
import sh.zolt.lockfile.LockPackage;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.lockfile.toml.ZoltLockfileReader;
import sh.zolt.classpath.Classpath;
import sh.zolt.build.classpath.LockfileClasspathPackageConverter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WorkspaceClasspathService {
    private static final Classpath EMPTY_CLASSPATH = new Classpath(List.of());

    private final ZoltLockfileReader lockfileReader;
    private final ClasspathBuilder classpathBuilder;
    private final WorkspaceProcessorClasspathAssembler processorClasspathAssembler;

    public WorkspaceClasspathService() {
        this(new ZoltLockfileReader(), new ClasspathBuilder());
    }

    WorkspaceClasspathService(
            ZoltLockfileReader lockfileReader,
            ClasspathBuilder classpathBuilder) {
        this.lockfileReader = lockfileReader;
        this.classpathBuilder = classpathBuilder;
        this.processorClasspathAssembler = new WorkspaceProcessorClasspathAssembler(classpathBuilder);
    }

    public ClasspathSet classpathsFor(
            Workspace workspace,
            ZoltLockfile lockfile,
            Path cacheRoot,
            String memberPath) {
        return classpathsFor(
                workspace,
                lockfile,
                cacheRoot,
                memberPath,
                dependenciesByMember(workspace));
    }

    public Map<String, ClasspathSet> classpathsForMembers(
            Workspace workspace,
            ZoltLockfile lockfile,
            Path cacheRoot,
            List<String> memberPaths) {
        return classpathsForMembers(
                workspace,
                lockfile,
                cacheRoot,
                memberPaths,
                new LinkedHashSet<>(memberPaths));
    }

    public Map<String, ClasspathSet> classpathsForMembers(
            Workspace workspace,
            ZoltLockfile lockfile,
            Path cacheRoot,
            List<String> memberPaths,
            Set<String> fullClasspathMembers) {
        Map<String, List<String>> dependenciesByMember = dependenciesByMember(workspace);
        Map<String, ClasspathSet> classpathsByMember = new LinkedHashMap<>();
        for (String memberPath : memberPaths) {
            ClasspathSet classpaths = fullClasspathMembers.contains(memberPath)
                    ? classpathsFor(
                            workspace,
                            lockfile,
                            cacheRoot,
                            memberPath,
                            dependenciesByMember)
                    : mainBuildClasspathsFor(
                            workspace,
                            lockfile,
                            cacheRoot,
                            memberPath,
                            dependenciesByMember);
            classpathsByMember.put(memberPath, classpaths);
        }
        return Collections.unmodifiableMap(classpathsByMember);
    }

    private ClasspathSet mainBuildClasspathsFor(
            Workspace workspace,
            ZoltLockfile lockfile,
            Path cacheRoot,
            String memberPath,
            Map<String, List<String>> dependenciesByMember) {
        Set<String> dependencyClosure = dependencyClosure(memberPath, dependenciesByMember);
        ZoltLockfile compileLockfile = new ZoltLockfile(
                lockfile.version(),
                compileClasspathPackagesFor(lockfile.packages(), memberPath, dependencyClosure),
                List.of());
        ClasspathSet compileClasspaths = classpathBuilder.build(LockfileClasspathPackageConverter.classpathPackages(
                compileLockfile,
                cacheRoot,
                workspace.root()));
        Classpath processor = processorClasspathAssembler.mergedProcessorClasspath(
                workspace,
                lockfile,
                cacheRoot,
                memberPath,
                dependenciesByMember,
                "processor",
                DependencyScope.PROCESSOR,
                compileClasspaths.processor());
        return new ClasspathSet(
                compileClasspaths.compile(),
                EMPTY_CLASSPATH,
                EMPTY_CLASSPATH,
                processor,
                EMPTY_CLASSPATH,
                EMPTY_CLASSPATH);
    }

    private ClasspathSet classpathsFor(
            Workspace workspace,
            ZoltLockfile lockfile,
            Path cacheRoot,
            String memberPath,
            Map<String, List<String>> dependenciesByMember) {
        Set<String> dependencyClosure = dependencyClosure(memberPath, dependenciesByMember);
        Set<String> visibleMembers = new LinkedHashSet<>();
        visibleMembers.add(memberPath);
        visibleMembers.addAll(dependencyClosure);
        ZoltLockfile compileLockfile = new ZoltLockfile(
                lockfile.version(),
                compileClasspathPackagesFor(lockfile.packages(), memberPath, dependencyClosure),
                List.of());
        ZoltLockfile runtimeLockfile = new ZoltLockfile(
                lockfile.version(),
                runtimeClasspathPackagesFor(lockfile.packages(), dependencyClosure, visibleMembers),
                List.of());
        ClasspathSet compileClasspaths = classpathBuilder.build(LockfileClasspathPackageConverter.classpathPackages(
                compileLockfile,
                cacheRoot,
                workspace.root()));
        ClasspathSet runtimeClasspaths = classpathBuilder.build(LockfileClasspathPackageConverter.classpathPackages(
                runtimeLockfile,
                cacheRoot,
                workspace.root()));
        Classpath processor = processorClasspathAssembler.mergedProcessorClasspath(
                workspace,
                lockfile,
                cacheRoot,
                memberPath,
                dependenciesByMember,
                "processor",
                DependencyScope.PROCESSOR,
                compileClasspaths.processor());
        Classpath testProcessor = processorClasspathAssembler.mergedProcessorClasspath(
                workspace,
                lockfile,
                cacheRoot,
                memberPath,
                dependenciesByMember,
                "test-processor",
                DependencyScope.TEST_PROCESSOR,
                compileClasspaths.testProcessor());
        return new ClasspathSet(
                compileClasspaths.compile(),
                runtimeClasspaths.runtime(),
                runtimeClasspaths.test(),
                processor,
                testProcessor,
                runtimeClasspaths.quarkusDeployment());
    }

    private static List<LockPackage> compileClasspathPackagesFor(
            List<LockPackage> packages,
            String memberPath,
            Set<String> dependencyClosure) {
        List<LockPackage> filteredPackages = new ArrayList<>();
        for (LockPackage lockPackage : packages) {
            if (lockPackage.workspace().isPresent()) {
                if (dependencyClosure.contains(lockPackage.workspace().orElseThrow())) {
                    filteredPackages.add(lockPackage);
                }
                continue;
            }

            if (lockPackage.members().isEmpty()
                    || lockPackage.members().contains(memberPath)
                    || intersects(lockPackage.exportedBy(), dependencyClosure)) {
                filteredPackages.add(lockPackage);
            }
        }
        return filteredPackages;
    }

    private static List<LockPackage> runtimeClasspathPackagesFor(
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

    private static Set<String> dependencyClosure(
            String memberPath,
            Map<String, List<String>> dependenciesByMember) {
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

    /**
     * Compile/runtime dependency edges only. Processor ({@code processor}/{@code test-processor})
     * edges are deliberately excluded so a workspace-member annotation processor and its transitive
     * dependencies never enter the consumer's compile, runtime, or test classpaths — they are routed
     * exclusively onto the processor path by {@link WorkspaceProcessorClasspathAssembler}.
     */
    private static Map<String, List<String>> dependenciesByMember(Workspace workspace) {
        Map<String, List<String>> dependencies = new LinkedHashMap<>();
        for (WorkspaceMember member : workspace.members()) {
            dependencies.put(member.path(), new ArrayList<>());
        }
        for (WorkspaceProjectEdge edge : workspace.edges()) {
            if (edge.scope().equals("processor") || edge.scope().equals("test-processor")) {
                continue;
            }
            dependencies.get(edge.from()).add(edge.to());
        }
        return dependencies;
    }
}
