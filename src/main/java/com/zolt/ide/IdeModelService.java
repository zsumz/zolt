package com.zolt.ide;

import com.zolt.cache.ArtifactCacheException;
import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.ClasspathSet;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import com.zolt.resolve.Classpath;
import com.zolt.resolve.ResolveException;
import com.zolt.resolve.ResolveService;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class IdeModelService {
    private static final int SCHEMA_VERSION = 1;

    private final ZoltTomlParser tomlParser;
    private final ZoltLockfileReader lockfileReader;
    private final ClasspathBuilder classpathBuilder;
    private final ResolveService resolveService;

    public IdeModelService() {
        this(new ZoltTomlParser(), new ZoltLockfileReader(), new ClasspathBuilder(), new ResolveService());
    }

    IdeModelService(
            ZoltTomlParser tomlParser,
            ZoltLockfileReader lockfileReader,
            ClasspathBuilder classpathBuilder,
            ResolveService resolveService) {
        this.tomlParser = tomlParser;
        this.lockfileReader = lockfileReader;
        this.classpathBuilder = classpathBuilder;
        this.resolveService = resolveService;
    }

    public IdeModel export(Path projectDirectory, Path cacheRoot) {
        return export(projectDirectory, cacheRoot, false, false);
    }

    public IdeModel export(Path projectDirectory, Path cacheRoot, boolean checkLock, boolean offline) {
        Path root = projectDirectory.toAbsolutePath().normalize();
        Path configPath = root.resolve("zolt.toml").normalize();
        Path lockfilePath = root.resolve("zolt.lock").normalize();
        Path normalizedCacheRoot = cacheRoot.toAbsolutePath().normalize();
        List<IdeModel.Diagnostic> diagnostics = new ArrayList<>();

        ProjectConfig config = readConfig(configPath, diagnostics);
        IdeModel.ClasspathInfo classpaths = classpaths(lockfilePath, normalizedCacheRoot, root, config, diagnostics);
        if (checkLock) {
            checkLockFreshness(root, lockfilePath, normalizedCacheRoot, config, offline, diagnostics);
        }

        return new IdeModel(
                SCHEMA_VERSION,
                projectInfo(config),
                javaInfo(config),
                new IdeModel.PathInfo(root, configPath, lockfilePath),
                sourceRoots(root, config),
                resourceRoots(root, config),
                outputInfo(root, config),
                dependencyInfo(config),
                classpaths,
                diagnostics);
    }

    IdeModel exportWithClasspaths(
            Path projectDirectory,
            Path lockfilePath,
            IdeModel.ClasspathInfo classpaths,
            List<IdeModel.Diagnostic> diagnostics) {
        Path root = projectDirectory.toAbsolutePath().normalize();
        Path configPath = root.resolve("zolt.toml").normalize();
        List<IdeModel.Diagnostic> modelDiagnostics = new ArrayList<>(diagnostics);
        ProjectConfig config = readConfig(configPath, modelDiagnostics);

        return new IdeModel(
                SCHEMA_VERSION,
                projectInfo(config),
                javaInfo(config),
                new IdeModel.PathInfo(root, configPath, lockfilePath.toAbsolutePath().normalize()),
                sourceRoots(root, config),
                resourceRoots(root, config),
                outputInfo(root, config),
                dependencyInfo(config),
                classpaths,
                modelDiagnostics);
    }

    private void checkLockFreshness(
            Path root,
            Path lockfilePath,
            Path cacheRoot,
            ProjectConfig config,
            boolean offline,
            List<IdeModel.Diagnostic> diagnostics) {
        if (config == null || !Files.isRegularFile(lockfilePath)) {
            return;
        }
        try {
            lockfileReader.read(lockfilePath);
        } catch (LockfileReadException exception) {
            return;
        }
        try {
            resolveService.resolve(root, config, cacheRoot, true, offline);
        } catch (ResolveException exception) {
            diagnostics.add(new IdeModel.Diagnostic(
                    "error",
                    lockDiagnosticCode(exception),
                    exception.getMessage(),
                    lockfilePath,
                    "Run zolt resolve."));
        } catch (ArtifactCacheException exception) {
            diagnostics.add(new IdeModel.Diagnostic(
                    "error",
                    "LOCKFILE_CHECK_UNAVAILABLE",
                    exception.getMessage(),
                    lockfilePath,
                    "Run zolt resolve without --offline to seed the cache, then retry zolt ide model --check-lock --offline."));
        }
    }

    private static String lockDiagnosticCode(ResolveException exception) {
        return exception.getMessage().contains("out of date")
                ? "LOCKFILE_STALE"
                : "LOCKFILE_CHECK_FAILED";
    }

    private ProjectConfig readConfig(Path configPath, List<IdeModel.Diagnostic> diagnostics) {
        try {
            return tomlParser.parse(configPath);
        } catch (ZoltConfigException exception) {
            String code = exception.getMessage().startsWith("Could not read zolt.toml")
                    ? "CONFIG_UNREADABLE"
                    : "CONFIG_INVALID";
            diagnostics.add(new IdeModel.Diagnostic(
                    "error",
                    code,
                    exception.getMessage(),
                    configPath,
                    "Fix zolt.toml and run zolt ide model --format json again."));
            return null;
        }
    }

    private IdeModel.ProjectInfo projectInfo(ProjectConfig config) {
        if (config == null) {
            return new IdeModel.ProjectInfo(null, null, null, null);
        }
        ProjectMetadata project = config.project();
        return new IdeModel.ProjectInfo(
                project.name(),
                project.group(),
                project.version(),
                project.main().orElse(null));
    }

    private IdeModel.JavaInfo javaInfo(ProjectConfig config) {
        return new IdeModel.JavaInfo(config == null ? null : config.project().java(), null, null);
    }

    private List<IdeModel.SourceRoot> sourceRoots(Path root, ProjectConfig config) {
        if (config == null) {
            return List.of();
        }
        BuildSettings settings = config.build();
        List<IdeModel.SourceRoot> roots = new ArrayList<>();
        roots.add(new IdeModel.SourceRoot(
                "main-java",
                "main",
                "java",
                root.resolve(settings.source()).normalize(),
                false));
        roots.add(new IdeModel.SourceRoot(
                "main-generated-java",
                "main",
                "java",
                root.resolve(config.compilerSettings().generatedSources()).normalize(),
                true));
        for (int index = 0; index < settings.testSources().size(); index++) {
            roots.add(new IdeModel.SourceRoot(
                    "test-java-" + (index + 1),
                    "test",
                    "java",
                    root.resolve(settings.testSources().get(index)).normalize(),
                    false));
        }
        roots.add(new IdeModel.SourceRoot(
                "test-generated-java",
                "test",
                "java",
                root.resolve(config.compilerSettings().generatedTestSources()).normalize(),
                true));
        return roots;
    }

    private List<IdeModel.ResourceRoot> resourceRoots(Path root, ProjectConfig config) {
        if (config == null) {
            return List.of();
        }
        BuildSettings settings = config.build();
        List<IdeModel.ResourceRoot> roots = new ArrayList<>();
        addResourceRoots(roots, root, "main", settings.resourceRoots());
        addResourceRoots(roots, root, "test", settings.testResourceRoots());
        return List.copyOf(roots);
    }

    private static void addResourceRoots(
            List<IdeModel.ResourceRoot> roots,
            Path root,
            String kind,
            List<String> configuredRoots) {
        String idPrefix = kind + "-resources";
        for (int index = 0; index < configuredRoots.size(); index++) {
            String id = index == 0 ? idPrefix : idPrefix + "-" + (index + 1);
            roots.add(new IdeModel.ResourceRoot(id, kind, root.resolve(configuredRoots.get(index)).normalize()));
        }
    }

    private IdeModel.OutputInfo outputInfo(Path root, ProjectConfig config) {
        if (config == null) {
            return new IdeModel.OutputInfo(null, null, null);
        }
        return new IdeModel.OutputInfo(
                root.resolve(config.build().output()).normalize(),
                root.resolve(config.build().testOutput()).normalize(),
                root.resolve("target")
                        .resolve(config.project().name() + "-" + config.project().version() + ".jar")
                        .normalize());
    }

    private IdeModel.DependencyInfo dependencyInfo(ProjectConfig config) {
        if (config == null) {
            return new IdeModel.DependencyInfo(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
        return new IdeModel.DependencyInfo(
                dependencyDeclarations(
                        config.apiDependencies(),
                        config.managedApiDependencies(),
                        config.workspaceApiDependencies()),
                dependencyDeclarations(
                        config.dependencies(),
                        config.managedDependencies(),
                        config.workspaceDependencies()),
                dependencyDeclarations(
                        config.runtimeDependencies(),
                        config.managedRuntimeDependencies(),
                        Map.of()),
                dependencyDeclarations(
                        config.providedDependencies(),
                        config.managedProvidedDependencies(),
                        Map.of()),
                dependencyDeclarations(
                        config.devDependencies(),
                        config.managedDevDependencies(),
                        Map.of()),
                dependencyDeclarations(
                        config.testDependencies(),
                        config.managedTestDependencies(),
                        config.workspaceTestDependencies()),
                dependencyDeclarations(
                        config.annotationProcessors(),
                        config.managedAnnotationProcessors(),
                        Map.of()),
                dependencyDeclarations(
                        config.testAnnotationProcessors(),
                        config.managedTestAnnotationProcessors(),
                        Map.of()));
    }

    private static List<IdeModel.DependencyDeclaration> dependencyDeclarations(
            Map<String, String> versioned,
            Set<String> managed,
            Map<String, String> workspace) {
        List<IdeModel.DependencyDeclaration> declarations = new ArrayList<>();
        for (Map.Entry<String, String> entry : versioned.entrySet()) {
            declarations.add(new IdeModel.DependencyDeclaration(entry.getKey(), entry.getValue(), false, null));
        }
        for (String coordinate : managed) {
            declarations.add(new IdeModel.DependencyDeclaration(coordinate, null, true, null));
        }
        for (Map.Entry<String, String> entry : workspace.entrySet()) {
            declarations.add(new IdeModel.DependencyDeclaration(entry.getKey(), null, false, entry.getValue()));
        }
        return declarations.stream()
                .sorted(Comparator.comparing(IdeModel.DependencyDeclaration::coordinate))
                .toList();
    }

    private IdeModel.ClasspathInfo classpaths(
            Path lockfilePath,
            Path cacheRoot,
            Path root,
            ProjectConfig config,
            List<IdeModel.Diagnostic> diagnostics) {
        if (config == null) {
            return emptyClasspaths();
        }
        if (!Files.exists(lockfilePath)) {
            diagnostics.add(new IdeModel.Diagnostic(
                    "error",
                    "LOCKFILE_MISSING",
                    "Could not find zolt.lock.",
                    lockfilePath,
                    "Run zolt resolve."));
            return emptyClasspaths();
        }
        try {
            ZoltLockfile lockfile = lockfileReader.read(lockfilePath);
            ClasspathSet dependencyClasspaths = classpathBuilder.build(lockfileReader.classpathPackages(lockfile, cacheRoot));
            Path mainOutput = root.resolve(config.build().output()).normalize();
            Path testOutput = root.resolve(config.build().testOutput()).normalize();
            return new IdeModel.ClasspathInfo(
                    absoluteEntries(dependencyClasspaths.compile()),
                    withOutputs(List.of(mainOutput), dependencyClasspaths.runtime()),
                    withOutputs(List.of(mainOutput, testOutput), dependencyClasspaths.test()));
        } catch (LockfileReadException exception) {
            diagnostics.add(new IdeModel.Diagnostic(
                    "error",
                    "LOCKFILE_UNREADABLE",
                    exception.getMessage(),
                    lockfilePath,
                    "Run zolt resolve."));
            return emptyClasspaths();
        }
    }

    private static IdeModel.ClasspathInfo emptyClasspaths() {
        return new IdeModel.ClasspathInfo(List.of(), List.of(), List.of());
    }

    private static List<Path> withOutputs(List<Path> outputs, Classpath classpath) {
        List<Path> entries = new ArrayList<>(outputs);
        entries.addAll(absoluteEntries(classpath));
        return entries;
    }

    private static List<Path> absoluteEntries(Classpath classpath) {
        return classpath.entries().stream()
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
    }
}
