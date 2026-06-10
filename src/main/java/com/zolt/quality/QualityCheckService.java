package com.zolt.quality;

import com.zolt.cache.ArtifactCacheException;
import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.BuildSettings;
import com.zolt.project.DependencyMetadata;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.PackageId;
import com.zolt.resolve.ResolveException;
import com.zolt.resolve.ResolveService;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.workspace.Workspace;
import com.zolt.workspace.WorkspaceConfigException;
import com.zolt.workspace.WorkspaceDiscoveryService;
import com.zolt.workspace.WorkspaceMemberSelector;
import com.zolt.workspace.WorkspaceSelection;
import com.zolt.workspace.WorkspaceMember;
import com.zolt.workspace.WorkspaceResolveService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class QualityCheckService {
    public static final String COMMAND_SURFACE = "command-surface";
    public static final String LOCKFILE = "lockfile";
    public static final String PROJECT_MODEL = "project-model";
    public static final String DEPENDENCY_METADATA = "dependency-metadata";

    private static final Set<String> IMPLEMENTED_CHECKS = Set.of(
            COMMAND_SURFACE,
            LOCKFILE,
            PROJECT_MODEL,
            DEPENDENCY_METADATA);
    private static final Map<String, String> PLANNED_CHECK_NOTES = Map.of(
            "package-metadata", "followUps/-add-package-and-manifest-checks.md",
            "manifest-metadata", "followUps/-add-package-and-manifest-checks.md",
            "generated-sources", "followUps/-add-generated-source-quality-checks.md");

    private final ZoltTomlParser projectParser;
    private final WorkspaceDiscoveryService workspaceDiscoveryService;
    private final WorkspaceMemberSelector workspaceMemberSelector;
    private final ResolveService resolveService;
    private final WorkspaceResolveService workspaceResolveService;
    private final ZoltLockfileReader lockfileReader;

    public QualityCheckService() {
        this(
                new ZoltTomlParser(),
                new WorkspaceDiscoveryService(),
                new WorkspaceMemberSelector(),
                new ResolveService(),
                new WorkspaceResolveService(),
                new ZoltLockfileReader());
    }

    QualityCheckService(
            ZoltTomlParser projectParser,
            WorkspaceDiscoveryService workspaceDiscoveryService,
            WorkspaceMemberSelector workspaceMemberSelector,
            ResolveService resolveService,
            WorkspaceResolveService workspaceResolveService,
            ZoltLockfileReader lockfileReader) {
        this.projectParser = projectParser;
        this.workspaceDiscoveryService = workspaceDiscoveryService;
        this.workspaceMemberSelector = workspaceMemberSelector;
        this.resolveService = resolveService;
        this.workspaceResolveService = workspaceResolveService;
        this.lockfileReader = lockfileReader;
    }

    public QualityCheckReport check(QualityCheckRequest request) {
        List<String> requestedChecks = requestedChecks(request.checks());
        Path root = request.projectRoot();

        if (request.workspace()) {
            try {
                Optional<Workspace> maybeWorkspace = workspaceDiscoveryService.discover(root);
                if (maybeWorkspace.isEmpty()) {
                    return new QualityCheckReport(root, true, unavailableResults(
                            requestedChecks,
                            "zolt-workspace.toml",
                            "No Zolt workspace was found for `zolt check --workspace`.",
                            "Run from a workspace root or remove --workspace for a single-project check."));
                }
                Workspace workspace = maybeWorkspace.orElseThrow();
                WorkspaceSelection selection = workspaceMemberSelector.select(workspace, request.workspaceSelection());
                return new QualityCheckReport(root, true, runWorkspaceChecks(request, requestedChecks, workspace, selection));
            } catch (WorkspaceConfigException exception) {
                return new QualityCheckReport(root, true, unavailableResults(
                        requestedChecks,
                        "zolt-workspace.toml",
                        exception.getMessage(),
                        "Fix zolt-workspace.toml or run `zolt check` for a single project."));
            }
        }

        try {
            ProjectConfig config = projectParser.parse(root.resolve("zolt.toml"));
            return new QualityCheckReport(root, false, runProjectChecks(request, requestedChecks, config));
        } catch (ZoltConfigException exception) {
            return new QualityCheckReport(root, false, unavailableResults(
                    requestedChecks,
                    "zolt.toml",
                    exception.getMessage(),
                    "Fix zolt.toml, then run `zolt check` again."));
        }
    }

    public static Set<String> supportedChecks() {
        Set<String> supported = new LinkedHashSet<>(IMPLEMENTED_CHECKS);
        supported.addAll(new TreeMap<>(PLANNED_CHECK_NOTES).keySet());
        return Collections.unmodifiableSet(supported);
    }

    private static QualityCheckResult commandSurfaceProjectResult(ProjectConfig config) {
        return QualityCheckResult.passed(
                COMMAND_SURFACE,
                Optional.empty(),
                config.project().name(),
                "zolt check uses typed Zolt project data; no Maven, Gradle, or shell hooks are run.");
    }

    private static QualityCheckResult commandSurfaceWorkspaceResult(Workspace workspace, WorkspaceSelection selection) {
        return QualityCheckResult.passed(
                COMMAND_SURFACE,
                Optional.empty(),
                workspace.root().getFileName().toString(),
                "zolt check selected "
                        + selection.includedMembers().size()
                        + " workspace members using typed Zolt workspace data; no Maven, Gradle, or shell hooks are run.");
    }

    private List<QualityCheckResult> runProjectChecks(
            QualityCheckRequest request,
            List<String> requestedChecks,
            ProjectConfig config) {
        List<QualityCheckResult> results = new ArrayList<>();
        for (String requestedCheck : requestedChecks) {
            switch (requestedCheck) {
                case COMMAND_SURFACE -> results.add(commandSurfaceProjectResult(config));
                case LOCKFILE -> results.add(checkProjectLockfile(request, config));
                case PROJECT_MODEL -> results.add(checkProjectModel(Optional.empty(), request.projectRoot(), config));
                case DEPENDENCY_METADATA -> results.addAll(checkProjectDependencyMetadata(
                        Optional.empty(),
                        request.projectRoot(),
                        config,
                        false));
                default -> results.add(unsupportedOrSkipped(requestedCheck));
            }
        }
        return List.copyOf(results);
    }

    private List<QualityCheckResult> runWorkspaceChecks(
            QualityCheckRequest request,
            List<String> requestedChecks,
            Workspace workspace,
            WorkspaceSelection selection) {
        List<QualityCheckResult> results = new ArrayList<>();
        Map<String, WorkspaceMember> members = membersByPath(workspace);
        for (String requestedCheck : requestedChecks) {
            switch (requestedCheck) {
                case COMMAND_SURFACE -> results.add(commandSurfaceWorkspaceResult(workspace, selection));
                case LOCKFILE -> results.add(checkWorkspaceLockfile(request, workspace));
                case PROJECT_MODEL -> {
                    for (String memberPath : selection.includedMembers()) {
                        WorkspaceMember member = members.get(memberPath);
                        results.add(checkProjectModel(
                                Optional.of(member.path()),
                                member.directory(),
                                member.config()));
                    }
                }
                case DEPENDENCY_METADATA -> results.addAll(checkWorkspaceDependencyMetadata(workspace, selection, members));
                default -> results.add(unsupportedOrSkipped(requestedCheck));
            }
        }
        return List.copyOf(results);
    }

    private QualityCheckResult checkProjectLockfile(QualityCheckRequest request, ProjectConfig config) {
        Path lockfile = request.projectRoot().resolve("zolt.lock");
        if (!Files.isRegularFile(lockfile)) {
            return QualityCheckResult.failed(
                    LOCKFILE,
                    Optional.empty(),
                    "zolt.lock",
                    "zolt.lock is missing.",
                    "Run `zolt resolve`.");
        }
        try {
            resolveService.resolve(request.projectRoot(), config, request.cacheRoot(), true, request.offline());
            return QualityCheckResult.passed(
                    LOCKFILE,
                    Optional.empty(),
                    "zolt.lock",
                    "zolt.lock matches zolt.toml.");
        } catch (ResolveException exception) {
            return QualityCheckResult.failed(
                    LOCKFILE,
                    Optional.empty(),
                    "zolt.lock",
                    exception.getMessage(),
                    "Run `zolt resolve`.");
        } catch (ArtifactCacheException exception) {
            return QualityCheckResult.failed(
                    LOCKFILE,
                    Optional.empty(),
                    "zolt.lock",
                    exception.getMessage(),
                    "Run `zolt resolve` without --offline to seed the cache, then retry `zolt check --check lockfile --offline`.");
        }
    }

    private QualityCheckResult checkWorkspaceLockfile(QualityCheckRequest request, Workspace workspace) {
        Path lockfile = workspace.root().resolve("zolt.lock");
        if (!Files.isRegularFile(lockfile)) {
            return QualityCheckResult.failed(
                    LOCKFILE,
                    Optional.empty(),
                    "zolt.lock",
                    "Workspace zolt.lock is missing.",
                    "Run `zolt resolve --workspace`.");
        }
        try {
            workspaceResolveService.resolve(workspace.root(), request.cacheRoot(), true, request.offline());
            return QualityCheckResult.passed(
                    LOCKFILE,
                    Optional.empty(),
                    "zolt.lock",
                    "Workspace zolt.lock matches zolt-workspace.toml and member zolt.toml files.");
        } catch (ResolveException exception) {
            return QualityCheckResult.failed(
                    LOCKFILE,
                    Optional.empty(),
                    "zolt.lock",
                    exception.getMessage(),
                    "Run `zolt resolve --workspace`.");
        } catch (ArtifactCacheException exception) {
            return QualityCheckResult.failed(
                    LOCKFILE,
                    Optional.empty(),
                    "zolt.lock",
                    exception.getMessage(),
                    "Run `zolt resolve --workspace` without --offline to seed the cache, then retry `zolt check --workspace --check lockfile --offline`.");
        }
    }

    private static QualityCheckResult checkProjectModel(
            Optional<String> member,
            Path projectRoot,
            ProjectConfig config) {
        Optional<QualityCheckResult> invalidPath = firstInvalidPath(member, config);
        if (invalidPath.isPresent()) {
            return invalidPath.orElseThrow();
        }

        Optional<QualityCheckResult> invalidCompilerRelease = invalidCompilerRelease(member, config);
        if (invalidCompilerRelease.isPresent()) {
            return invalidCompilerRelease.orElseThrow();
        }

        return QualityCheckResult.passed(
                PROJECT_MODEL,
                member,
                config.project().name(),
                "Project model is valid for Zolt-owned checks at " + projectRoot.toAbsolutePath().normalize() + ".");
    }

    private static Optional<QualityCheckResult> firstInvalidPath(
            Optional<String> member,
            ProjectConfig config) {
        List<PathField> fields = new ArrayList<>();
        BuildSettings build = config.build();
        fields.add(new PathField("[build].source", build.source()));
        fields.add(new PathField("[build].test", build.test()));
        fields.add(new PathField("[build].output", build.output()));
        fields.add(new PathField("[build].testOutput", build.testOutput()));
        addPathFields(fields, "[test.sources].java", build.testSources());
        addPathFields(fields, "[test.sources].groovy", build.groovyTestSources());
        addPathFields(fields, "[resources].main", build.resourceRoots());
        addPathFields(fields, "[resources].test", build.testResourceRoots());
        fields.add(new PathField("[compiler].generatedSources", config.compilerSettings().generatedSources()));
        fields.add(new PathField("[compiler].generatedTestSources", config.compilerSettings().generatedTestSources()));
        addGeneratedPathFields(fields, "[generated.main]", build.generatedMainSources());
        addGeneratedPathFields(fields, "[generated.test]", build.generatedTestSources());

        for (PathField field : fields) {
            if (!isProjectRelative(field.value())) {
                return Optional.of(QualityCheckResult.failed(
                        PROJECT_MODEL,
                        member,
                        field.name(),
                        "Path `" + field.value() + "` must be project-relative and stay inside the project.",
                        "Edit zolt.toml to use a relative path such as `src/main/java` or `target/classes`."));
            }
        }
        return Optional.empty();
    }

    private static Optional<QualityCheckResult> invalidCompilerRelease(
            Optional<String> member,
            ProjectConfig config) {
        String release = config.compilerSettings().release();
        if (release.isBlank()) {
            return Optional.empty();
        }
        Optional<Integer> releaseVersion = javaFeatureVersion(release);
        Optional<Integer> projectVersion = javaFeatureVersion(config.project().java());
        if (releaseVersion.isEmpty()) {
            return Optional.of(QualityCheckResult.failed(
                    PROJECT_MODEL,
                    member,
                    "[compiler].release",
                    "Compiler release `" + release + "` must be a Java feature version.",
                    "Use a numeric release such as `8`, `11`, `17`, or `21`."));
        }
        if (projectVersion.isPresent() && releaseVersion.orElseThrow() > projectVersion.orElseThrow()) {
            return Optional.of(QualityCheckResult.failed(
                    PROJECT_MODEL,
                    member,
                    "[compiler].release",
                    "Compiler release `" + release + "` is newer than [project].java `" + config.project().java() + "`.",
                    "Lower [compiler].release or raise [project].java in zolt.toml."));
        }
        return Optional.empty();
    }

    private static Optional<Integer> javaFeatureVersion(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static void addPathFields(List<PathField> fields, String name, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            fields.add(new PathField(name + "[" + index + "]", values.get(index)));
        }
    }

    private static void addGeneratedPathFields(
            List<PathField> fields,
            String section,
            List<GeneratedSourceStep> steps) {
        for (GeneratedSourceStep step : steps) {
            fields.add(new PathField(section + "." + step.id() + ".output", step.output()));
            addPathFields(fields, section + "." + step.id() + ".inputs", step.inputs());
        }
    }

    private static boolean isProjectRelative(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        Path path = Path.of(value);
        Path normalized = path.normalize();
        return !path.isAbsolute() && !normalized.startsWith("..");
    }

    private List<QualityCheckResult> checkProjectDependencyMetadata(
            Optional<String> member,
            Path root,
            ProjectConfig config,
            boolean workspaceLockfile) {
        Path lockfilePath = root.resolve("zolt.lock");
        if (!Files.isRegularFile(lockfilePath)) {
            return List.of(QualityCheckResult.failed(
                    DEPENDENCY_METADATA,
                    member,
                    "zolt.lock",
                    (workspaceLockfile ? "Workspace zolt.lock" : "zolt.lock") + " is missing.",
                    workspaceLockfile ? "Run `zolt resolve --workspace`." : "Run `zolt resolve`."));
        }

        ZoltLockfile lockfile;
        try {
            lockfile = lockfileReader.read(lockfilePath);
        } catch (LockfileReadException exception) {
            return List.of(QualityCheckResult.failed(
                    DEPENDENCY_METADATA,
                    member,
                    "zolt.lock",
                    exception.getMessage(),
                    workspaceLockfile ? "Run `zolt resolve --workspace`." : "Run `zolt resolve`."));
        }

        return checkDependencyMetadataDeclarations(member, config, lockfile, workspaceLockfile);
    }

    private List<QualityCheckResult> checkWorkspaceDependencyMetadata(
            Workspace workspace,
            WorkspaceSelection selection,
            Map<String, WorkspaceMember> members) {
        Path lockfilePath = workspace.root().resolve("zolt.lock");
        if (!Files.isRegularFile(lockfilePath)) {
            return List.of(QualityCheckResult.failed(
                    DEPENDENCY_METADATA,
                    Optional.empty(),
                    "zolt.lock",
                    "Workspace zolt.lock is missing.",
                    "Run `zolt resolve --workspace`."));
        }

        ZoltLockfile lockfile;
        try {
            lockfile = lockfileReader.read(lockfilePath);
        } catch (LockfileReadException exception) {
            return List.of(QualityCheckResult.failed(
                    DEPENDENCY_METADATA,
                    Optional.empty(),
                    "zolt.lock",
                    exception.getMessage(),
                    "Run `zolt resolve --workspace`."));
        }

        List<QualityCheckResult> results = new ArrayList<>();
        for (String memberPath : selection.includedMembers()) {
            WorkspaceMember member = members.get(memberPath);
            results.addAll(checkDependencyMetadataDeclarations(
                    Optional.of(member.path()),
                    member.config(),
                    lockfile,
                    true));
            results.addAll(checkWorkspaceApiEdges(workspace, member, lockfile));
        }
        if (results.isEmpty()) {
            results.add(QualityCheckResult.passed(
                    DEPENDENCY_METADATA,
                    Optional.empty(),
                    workspace.config().name(),
                    "No dependency metadata declarations require validation."));
        }
        return List.copyOf(results);
    }

    private List<QualityCheckResult> checkDependencyMetadataDeclarations(
            Optional<String> member,
            ProjectConfig config,
            ZoltLockfile lockfile,
            boolean workspaceLockfile) {
        if (config.dependencyMetadata().isEmpty()) {
            return List.of(QualityCheckResult.passed(
                    DEPENDENCY_METADATA,
                    member,
                    config.project().name(),
                    "No dependency metadata declarations require validation."));
        }

        List<QualityCheckResult> results = new ArrayList<>();
        for (DependencyMetadata metadata : new TreeMap<>(config.dependencyMetadata()).values()) {
            if (metadata.workspace() != null) {
                if (metadata.optional()) {
                    results.add(QualityCheckResult.failed(
                            DEPENDENCY_METADATA,
                            member,
                            metadata.coordinate(),
                            "Workspace dependency `" + metadata.coordinate() + "` declares optional metadata, which is not supported.",
                            "Remove optional = true or use an external dependency coordinate."));
                }
                continue;
            }

            if (metadata.publishOnly()) {
                results.add(checkPublishOnlyMetadata(member, metadata, lockfile));
                continue;
            }

            results.add(checkClasspathMetadata(member, metadata, lockfile, workspaceLockfile));
        }
        return List.copyOf(results);
    }

    private QualityCheckResult checkPublishOnlyMetadata(
            Optional<String> member,
            DependencyMetadata metadata,
            ZoltLockfile lockfile) {
        Optional<LockPackage> lockPackage = findLockPackage(lockfile, packageId(metadata.coordinate()), member);
        if (lockPackage.isPresent()) {
            return QualityCheckResult.failed(
                    DEPENDENCY_METADATA,
                    member,
                    metadata.coordinate(),
                    "Publish-only dependency `" + metadata.coordinate() + "` is present in zolt.lock.",
                    "Run `zolt resolve`; if it remains, remove publishOnly = true or move the dependency to a normal classpath section.");
        }
        return QualityCheckResult.passed(
                DEPENDENCY_METADATA,
                member,
                metadata.coordinate(),
                "Publish-only dependency `" + metadata.coordinate() + "` is kept out of zolt.lock classpaths.");
    }

    private QualityCheckResult checkClasspathMetadata(
            Optional<String> member,
            DependencyMetadata metadata,
            ZoltLockfile lockfile,
            boolean workspaceLockfile) {
        Optional<LockPackage> maybeLockPackage = findLockPackage(lockfile, packageId(metadata.coordinate()), member);
        if (maybeLockPackage.isEmpty()) {
            return QualityCheckResult.failed(
                    DEPENDENCY_METADATA,
                    member,
                    metadata.coordinate(),
                    "Dependency metadata for `" + metadata.coordinate() + "` is not represented in zolt.lock.",
                    workspaceLockfile ? "Run `zolt resolve --workspace`." : "Run `zolt resolve`.");
        }

        LockPackage lockPackage = maybeLockPackage.orElseThrow();
        if (metadata.optional() && !lockPackage.direct()) {
            return QualityCheckResult.failed(
                    DEPENDENCY_METADATA,
                    member,
                    metadata.coordinate(),
                    "Optional direct dependency `" + metadata.coordinate() + "` is not marked direct in zolt.lock.",
                    workspaceLockfile ? "Run `zolt resolve --workspace`." : "Run `zolt resolve`.");
        }

        for (com.zolt.project.DependencyExclusionSpec exclusion : metadata.exclusions()) {
            if (lockPackage.dependencies().contains(exclusion.coordinate())) {
                return QualityCheckResult.failed(
                        DEPENDENCY_METADATA,
                        member,
                        metadata.coordinate(),
                        "Excluded dependency `" + exclusion.coordinate() + "` is still present on direct dependency `" + metadata.coordinate() + "` in zolt.lock.",
                        "Check [" + metadata.section() + "]." + metadata.coordinate() + ".exclusions and run "
                                + (workspaceLockfile ? "`zolt resolve --workspace`." : "`zolt resolve`."));
            }
        }

        return QualityCheckResult.passed(
                DEPENDENCY_METADATA,
                member,
                metadata.coordinate(),
                "Dependency metadata for `" + metadata.coordinate() + "` is represented in zolt.lock.");
    }

    private static List<QualityCheckResult> checkWorkspaceApiEdges(
            Workspace workspace,
            WorkspaceMember member,
            ZoltLockfile lockfile) {
        List<QualityCheckResult> results = new ArrayList<>();
        for (Map.Entry<String, String> dependency : new TreeMap<>(member.config().workspaceApiDependencies()).entrySet()) {
            String coordinate = dependency.getKey();
            String target = normalizeMemberPath(dependency.getValue());
            Optional<com.zolt.workspace.WorkspaceProjectEdge> edge = workspace.edges().stream()
                    .filter(candidate -> candidate.from().equals(member.path())
                            && candidate.to().equals(target)
                            && candidate.coordinate().equals(coordinate))
                    .findFirst();
            if (edge.isEmpty() || !edge.orElseThrow().exported()) {
                results.add(QualityCheckResult.failed(
                        DEPENDENCY_METADATA,
                        Optional.of(member.path()),
                        coordinate,
                        "Workspace API dependency `" + coordinate + "` is not represented as an exported workspace edge.",
                        "Keep public workspace dependencies in [api.dependencies] and run `zolt resolve --workspace`."));
                continue;
            }

            Optional<LockPackage> packageNode = lockfile.packages().stream()
                    .filter(lockPackage -> lockPackage.packageId().equals(packageId(coordinate))
                            && lockPackage.workspace().orElse("").equals(target))
                    .findFirst();
            if (packageNode.isEmpty() || !packageNode.orElseThrow().exportedBy().contains(member.path())) {
                results.add(QualityCheckResult.failed(
                        DEPENDENCY_METADATA,
                        Optional.of(member.path()),
                        coordinate,
                        "Workspace API dependency `" + coordinate + "` is missing exportedBy ownership in zolt.lock.",
                        "Run `zolt resolve --workspace`."));
                continue;
            }

            results.add(QualityCheckResult.passed(
                    DEPENDENCY_METADATA,
                    Optional.of(member.path()),
                    coordinate,
                    "Workspace API dependency `" + coordinate + "` is exported through zolt.lock."));
        }
        return List.copyOf(results);
    }

    private static Optional<LockPackage> findLockPackage(
            ZoltLockfile lockfile,
            PackageId packageId,
            Optional<String> member) {
        return lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(packageId))
                .filter(lockPackage -> member.isEmpty()
                        || lockPackage.members().isEmpty()
                        || lockPackage.members().contains(member.orElseThrow()))
                .findFirst();
    }

    private static PackageId packageId(String coordinate) {
        String[] parts = coordinate.split(":", -1);
        return new PackageId(parts[0], parts[1]);
    }

    private static String normalizeMemberPath(String path) {
        String normalized = Path.of(path).normalize().toString().replace('\\', '/');
        return normalized.isBlank() ? "." : normalized;
    }

    private static Map<String, WorkspaceMember> membersByPath(Workspace workspace) {
        Map<String, WorkspaceMember> members = new LinkedHashMap<>();
        for (WorkspaceMember member : workspace.members()) {
            members.put(member.path(), member);
        }
        return Collections.unmodifiableMap(members);
    }

    private static List<QualityCheckResult> unavailableResults(
            List<String> requestedChecks,
            String subject,
            String message,
            String nextStep) {
        List<QualityCheckResult> results = new ArrayList<>();
        for (String requestedCheck : requestedChecks) {
            if (IMPLEMENTED_CHECKS.contains(requestedCheck)) {
                results.add(QualityCheckResult.failed(
                        requestedCheck,
                        Optional.empty(),
                        subject,
                        message,
                        nextStep));
            } else {
                results.add(unsupportedOrSkipped(requestedCheck));
            }
        }
        return List.copyOf(results);
    }

    private static QualityCheckResult unsupportedOrSkipped(String requestedCheck) {
        if (PLANNED_CHECK_NOTES.containsKey(requestedCheck)) {
            return QualityCheckResult.skipped(
                    requestedCheck,
                    Optional.empty(),
                    requestedCheck,
                    "Quality check `" + requestedCheck + "` is planned but not implemented yet.",
                    "Track " + PLANNED_CHECK_NOTES.get(requestedCheck) + ".");
        }
        return QualityCheckResult.failed(
                "unsupported-check",
                Optional.empty(),
                requestedCheck,
                "Unsupported quality check `" + requestedCheck + "`.",
                "Use one of: " + String.join(", ", supportedChecks()) + ". Zolt does not run Maven goals, Gradle tasks, shell commands, or arbitrary hooks.");
    }

    private static List<String> requestedChecks(List<String> rawChecks) {
        if (rawChecks.isEmpty()) {
            return List.of(COMMAND_SURFACE);
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawCheck : rawChecks) {
            String check = rawCheck == null ? "" : rawCheck.trim();
            if (!check.isEmpty()) {
                normalized.add(check);
            }
        }
        return List.copyOf(normalized);
    }

    private record PathField(String name, String value) {
    }
}
