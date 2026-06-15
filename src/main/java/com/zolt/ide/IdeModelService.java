package com.zolt.ide;

import com.zolt.cache.ArtifactCacheException;
import com.zolt.generated.GeneratedSourceEvidence;
import com.zolt.generated.GeneratedSourceEvidenceService;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.perf.TimingRecorder;
import com.zolt.project.BuildSettings;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectPathException;
import com.zolt.project.ProjectPaths;
import com.zolt.resolve.ResolveException;
import com.zolt.resolve.ResolveService;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class IdeModelService {
    private static final int SCHEMA_VERSION = 1;

    private final ZoltTomlParser tomlParser;
    private final ZoltLockfileReader lockfileReader;
    private final ResolveService resolveService;
    private final GeneratedSourceEvidenceService generatedSourceEvidenceService;
    private final IdeProjectModelBuilder projectModelBuilder;
    private final IdeClasspathModelBuilder classpathModelBuilder;
    private final IdeDependencyModelBuilder dependencyModelBuilder;
    private final IdeFrameworkModelBuilder frameworkModelBuilder;

    public IdeModelService() {
        this(
                new ZoltTomlParser(),
                new ZoltLockfileReader(),
                new ResolveService(),
                new GeneratedSourceEvidenceService(),
                new IdeProjectModelBuilder(),
                new IdeClasspathModelBuilder(),
                new IdeDependencyModelBuilder(),
                new IdeFrameworkModelBuilder());
    }

    IdeModelService(
            ZoltTomlParser tomlParser,
            ZoltLockfileReader lockfileReader,
            ResolveService resolveService,
            GeneratedSourceEvidenceService generatedSourceEvidenceService,
            IdeProjectModelBuilder projectModelBuilder,
            IdeClasspathModelBuilder classpathModelBuilder,
            IdeDependencyModelBuilder dependencyModelBuilder,
            IdeFrameworkModelBuilder frameworkModelBuilder) {
        this.tomlParser = tomlParser;
        this.lockfileReader = lockfileReader;
        this.resolveService = resolveService;
        this.generatedSourceEvidenceService = generatedSourceEvidenceService;
        this.projectModelBuilder = projectModelBuilder;
        this.classpathModelBuilder = classpathModelBuilder;
        this.dependencyModelBuilder = dependencyModelBuilder;
        this.frameworkModelBuilder = frameworkModelBuilder;
    }

    public IdeModel export(Path projectDirectory, Path cacheRoot) {
        return export(projectDirectory, cacheRoot, false, false);
    }

    public IdeModel export(Path projectDirectory, Path cacheRoot, boolean checkLock, boolean offline) {
        return export(projectDirectory, cacheRoot, checkLock, offline, new TimingRecorder(false));
    }

    public IdeModel export(
            Path projectDirectory,
            Path cacheRoot,
            boolean checkLock,
            boolean offline,
            TimingRecorder timings) {
        Path root = projectDirectory.toAbsolutePath().normalize();
        Path configPath = root.resolve("zolt.toml").normalize();
        Path lockfilePath = root.resolve("zolt.lock").normalize();
        Path normalizedCacheRoot = cacheRoot.toAbsolutePath().normalize();
        List<IdeModel.Diagnostic> diagnostics = new ArrayList<>();

        TimingRecorder recorder = timings == null ? new TimingRecorder(false) : timings;
        ProjectConfig config = recorder.measure(
                "read ide project config",
                () -> readConfig(configPath, diagnostics));
        IdeModel.ClasspathInfo classpaths = recorder.measure(
                "build ide classpaths",
                () -> classpathModelBuilder.build(lockfilePath, normalizedCacheRoot, root, config, diagnostics),
                IdeModelService::ideClasspathAttributes);
        if (checkLock) {
            recorder.measure(
                    "check ide lock freshness",
                    () -> checkLockFreshness(root, lockfilePath, normalizedCacheRoot, config, offline, diagnostics));
        }
        IdeModel.FrameworkInfo frameworkInfo = recorder.measure(
                "build ide framework model",
                () -> frameworkModelBuilder.build(root, normalizedCacheRoot, config, diagnostics));

        return recorder.measure(
                "assemble ide model",
                () -> new IdeModel(
                        SCHEMA_VERSION,
                        projectModelBuilder.projectInfo(config),
                        projectModelBuilder.javaInfo(config),
                        projectModelBuilder.compilerInfo(root, config, diagnostics),
                        projectModelBuilder.testRuntimeInfo(config),
                        projectModelBuilder.packageInfo(root, config, diagnostics),
                        new IdeModel.PathInfo(root, configPath, lockfilePath),
                        sourceRoots(root, config, diagnostics),
                        generatedSources(root, config, diagnostics),
                        resourceRoots(root, config, diagnostics),
                        projectModelBuilder.outputInfo(root, config, diagnostics),
                        dependencyModelBuilder.build(config),
                        classpaths,
                        frameworkInfo,
                        diagnostics),
                IdeModelService::ideModelAttributes);
    }

    private static Map<String, String> ideClasspathAttributes(IdeModel.ClasspathInfo classpaths) {
        return Map.of(
                "compileClasspathEntries", Integer.toString(classpaths.compile().size()),
                "runtimeClasspathEntries", Integer.toString(classpaths.runtime().size()),
                "testClasspathEntries", Integer.toString(classpaths.test().size()));
    }

    private static Map<String, String> ideModelAttributes(IdeModel model) {
        return Map.of(
                "sourceRoots", Integer.toString(model.sourceRoots().size()),
                "resourceRoots", Integer.toString(model.resourceRoots().size()),
                "diagnostics", Integer.toString(model.diagnostics().size()));
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
                projectModelBuilder.projectInfo(config),
                projectModelBuilder.javaInfo(config),
                projectModelBuilder.compilerInfo(root, config, modelDiagnostics),
                projectModelBuilder.testRuntimeInfo(config),
                projectModelBuilder.packageInfo(root, config, modelDiagnostics),
                new IdeModel.PathInfo(root, configPath, lockfilePath.toAbsolutePath().normalize()),
                sourceRoots(root, config, modelDiagnostics),
                generatedSources(root, config, modelDiagnostics),
                resourceRoots(root, config, modelDiagnostics),
                projectModelBuilder.outputInfo(root, config, modelDiagnostics),
                dependencyModelBuilder.build(config),
                classpaths,
                frameworkModelBuilder.build(root, null, config, modelDiagnostics),
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
                    "Run zolt resolve without --offline to seed the cache, then retry zolt ide model --offline."));
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

    private List<IdeModel.SourceRoot> sourceRoots(
            Path root,
            ProjectConfig config,
            List<IdeModel.Diagnostic> diagnostics) {
        if (config == null) {
            return List.of();
        }
        BuildSettings settings = config.build();
        List<IdeModel.SourceRoot> roots = new ArrayList<>();
        addSourceRoot(
                roots,
                "main-java",
                "main",
                "java",
                inputRoot(root, "[build].source", settings.source(), diagnostics),
                false);
        addSourceRoot(
                roots,
                "main-generated-java",
                "main",
                "java",
                outputPath(root, "[compiler].generatedSources", config.compilerSettings().generatedSources(), diagnostics),
                true);
        for (GeneratedSourceRoot generatedRoot : generatedRoots(root, settings.generatedMainSources(), "main", diagnostics)) {
            roots.add(new IdeModel.SourceRoot(
                    generatedRoot.id(),
                    "main",
                    "java",
                    generatedRoot.path(),
                    true));
        }
        for (int index = 0; index < settings.testSources().size(); index++) {
            addSourceRoot(
                    roots,
                    "test-java-" + (index + 1),
                    "test",
                    "java",
                    inputRoot(root, "[build].testSources", settings.testSources().get(index), diagnostics),
                    false);
        }
        for (int index = 0; index < settings.groovyTestSources().size(); index++) {
            addSourceRoot(
                    roots,
                    "test-groovy-" + (index + 1),
                    "test",
                    "groovy",
                    inputRoot(root, "[build].groovyTestSources", settings.groovyTestSources().get(index), diagnostics),
                    false);
        }
        addSourceRoot(
                roots,
                "test-generated-java",
                "test",
                "java",
                outputPath(root, "[compiler].generatedTestSources", config.compilerSettings().generatedTestSources(), diagnostics),
                true);
        for (GeneratedSourceRoot generatedRoot : generatedRoots(root, settings.generatedTestSources(), "test", diagnostics)) {
            roots.add(new IdeModel.SourceRoot(
                    generatedRoot.id(),
                    "test",
                    "java",
                    generatedRoot.path(),
                    true));
        }
        return roots;
    }

    private static void addSourceRoot(
            List<IdeModel.SourceRoot> roots,
            String id,
            String kind,
            String language,
            Path path,
            boolean generated) {
        if (path != null) {
            roots.add(new IdeModel.SourceRoot(id, kind, language, path, generated));
        }
    }

    private static List<GeneratedSourceRoot> generatedRoots(
            Path root,
            List<GeneratedSourceStep> steps,
            String kind,
            List<IdeModel.Diagnostic> diagnostics) {
        return steps.stream()
                .map(step -> generatedRoot(root, kind, step, diagnostics))
                .filter(generatedRoot -> generatedRoot != null)
                .toList();
    }

    private static GeneratedSourceRoot generatedRoot(
            Path root,
            String kind,
            GeneratedSourceStep step,
            List<IdeModel.Diagnostic> diagnostics) {
        Path output = outputPath(
                root,
                "[generated." + kind + "." + step.id() + "].output",
                step.output(),
                diagnostics);
        if (output == null) {
            return null;
        }
        return new GeneratedSourceRoot("generated-" + kind + "-" + step.id(), output);
    }

    private record GeneratedSourceRoot(String id, Path path) {}

    private List<IdeModel.GeneratedSourceInfo> generatedSources(
            Path root,
            ProjectConfig config,
            List<IdeModel.Diagnostic> diagnostics) {
        if (config == null) {
            return List.of();
        }
        BuildSettings build = config.build();
        BuildSettings safeBuild = build.withGeneratedSources(
                safeGeneratedSteps(root, build.generatedMainSources(), "main", diagnostics),
                safeGeneratedSteps(root, build.generatedTestSources(), "test", diagnostics));
        return generatedSourceEvidenceService.evidence(root, safeBuild).stream()
                .map(this::generatedSourceInfo)
                .toList();
    }

    private static List<GeneratedSourceStep> safeGeneratedSteps(
            Path root,
            List<GeneratedSourceStep> steps,
            String scope,
            List<IdeModel.Diagnostic> diagnostics) {
        return steps.stream()
                .filter(step -> generatedStepPathsAreSafe(root, step, scope, diagnostics))
                .toList();
    }

    private static boolean generatedStepPathsAreSafe(
            Path root,
            GeneratedSourceStep step,
            String scope,
            List<IdeModel.Diagnostic> diagnostics) {
        boolean safe = outputPath(
                root,
                "[generated." + scope + "." + step.id() + "].output",
                step.output(),
                diagnostics) != null;
        for (String input : step.inputs()) {
            safe = inputPath(
                    root,
                    "[generated." + scope + "." + step.id() + "].inputs",
                    input,
                    diagnostics) != null && safe;
        }
        return safe;
    }

    private IdeModel.GeneratedSourceInfo generatedSourceInfo(GeneratedSourceEvidence evidence) {
        return new IdeModel.GeneratedSourceInfo(
                evidence.id(),
                evidence.sourceRootId(),
                evidence.scope(),
                evidence.step().kind().configValue(),
                evidence.step().language(),
                evidence.output(),
                evidence.inputs(),
                evidence.step().required(),
                evidence.step().clean(),
                evidence.ownership(),
                evidence.compileLane(),
                evidence.freshness(),
                evidence.outputExists(),
                evidence.inputsPresent(),
                evidence.toolArtifact(),
                evidence.step().openApi().toolVersionRef().orElse(null),
                evidence.toolFingerprint(),
                evidence.optionsFingerprint());
    }

    private List<IdeModel.ResourceRoot> resourceRoots(
            Path root,
            ProjectConfig config,
            List<IdeModel.Diagnostic> diagnostics) {
        if (config == null) {
            return List.of();
        }
        BuildSettings settings = config.build();
        List<IdeModel.ResourceRoot> roots = new ArrayList<>();
        addResourceRoots(roots, root, "main", settings.resourceRoots(), diagnostics);
        addResourceRoots(roots, root, "test", settings.testResourceRoots(), diagnostics);
        return List.copyOf(roots);
    }

    private static void addResourceRoots(
            List<IdeModel.ResourceRoot> roots,
            Path root,
            String kind,
            List<String> configuredRoots,
            List<IdeModel.Diagnostic> diagnostics) {
        String idPrefix = kind + "-resources";
        for (int index = 0; index < configuredRoots.size(); index++) {
            String id = index == 0 ? idPrefix : idPrefix + "-" + (index + 1);
            Path path = inputRoot(root, "[resources]." + kind, configuredRoots.get(index), diagnostics);
            if (path != null) {
                roots.add(new IdeModel.ResourceRoot(id, kind, path));
            }
        }
    }

    private static Path inputPath(
            Path root,
            String key,
            String configuredPath,
            List<IdeModel.Diagnostic> diagnostics) {
        try {
            return ProjectPaths.input(root, key, configuredPath);
        } catch (ProjectPathException exception) {
            pathDiagnostic(root, exception, diagnostics);
            return null;
        }
    }

    private static Path inputRoot(
            Path root,
            String key,
            String configuredPath,
            List<IdeModel.Diagnostic> diagnostics) {
        try {
            return ProjectPaths.existingRoot(root, key, configuredPath);
        } catch (ProjectPathException exception) {
            pathDiagnostic(root, exception, diagnostics);
            return null;
        }
    }

    private static Path outputPath(
            Path root,
            String key,
            String configuredPath,
            List<IdeModel.Diagnostic> diagnostics) {
        try {
            return ProjectPaths.output(root, key, configuredPath);
        } catch (ProjectPathException exception) {
            pathDiagnostic(root, exception, diagnostics);
            return null;
        }
    }

    private static void pathDiagnostic(
            Path root,
            ProjectPathException exception,
            List<IdeModel.Diagnostic> diagnostics) {
        if (diagnostics.stream().anyMatch(diagnostic ->
                "PROJECT_PATH_INVALID".equals(diagnostic.code())
                        && exception.getMessage().equals(diagnostic.message()))) {
            return;
        }
        diagnostics.add(new IdeModel.Diagnostic(
                "error",
                "PROJECT_PATH_INVALID",
                exception.getMessage(),
                root.resolve("zolt.toml").normalize(),
                "Fix the unsafe path in zolt.toml and run zolt ide model --format json again."));
    }

}
