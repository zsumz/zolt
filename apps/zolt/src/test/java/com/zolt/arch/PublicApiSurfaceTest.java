package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PublicApiSurfaceTest {
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^package\\s+([A-Za-z0-9_.]+);");
    private static final Pattern PUBLIC_TYPE_PATTERN = Pattern.compile(
            "(?m)^public\\s+(?:final\\s+|abstract\\s+|sealed\\s+|non-sealed\\s+)?(?:class|record|interface|enum)\\s+([A-Za-z0-9_]+)\\b");

    private static final Set<String> EXPECTED_PUBLIC_TYPES = Set.of(
            "com.zolt.classpath.Classpath",
            "com.zolt.classpath.ClasspathLanePolicy",
            "com.zolt.classpath.ClasspathSet",
            "com.zolt.classpath.ResolvedClasspathPackage",
            "com.zolt.classpath.ResolvedPackage",
            "com.zolt.command.CommandAlias",
            "com.zolt.command.CommandConfig",
            "com.zolt.command.CommandConfigRules",
            "com.zolt.command.CommandTask",
            "com.zolt.dependency.ConflictSelectionReason",
            "com.zolt.dependency.DependencyScope",
            "com.zolt.dependency.PackageId",
            "com.zolt.dependency.VersionComparator",
            "com.zolt.error.ActionableError",
            "com.zolt.error.ActionableException",
            "com.zolt.error.HasActionableError",
            "com.zolt.error.WorkerFailureDiagnostic",
            "com.zolt.framework.FrameworkBuildAugmentationResult",
            "com.zolt.framework.FrameworkBuildAugmenter",
            "com.zolt.framework.FrameworkBuildException",
            "com.zolt.framework.FrameworkCleanTargets",
            "com.zolt.framework.FrameworkPackageAugmenter",
            "com.zolt.framework.FrameworkPackagePlanDependency",
            "com.zolt.framework.FrameworkPackagePlanRules",
            "com.zolt.framework.FrameworkPackageResult",
            "com.zolt.framework.FrameworkRunAugmenter",
            "com.zolt.framework.FrameworkRunException",
            "com.zolt.framework.FrameworkRunResult",
            "com.zolt.framework.FrameworkTestRunRequest",
            "com.zolt.framework.FrameworkTestRunResult",
            "com.zolt.framework.FrameworkTestRunner",
            "com.zolt.framework.FrameworkTestSelection",
            "com.zolt.ide.IdeFrameworkModelProvider",
            "com.zolt.ide.IdeModel",
            "com.zolt.ide.IdeModelJsonWriter",
            "com.zolt.ide.IdeModelService",
            "com.zolt.ide.IdeTimingRecorder",
            "com.zolt.ide.WorkspaceIdeModel",
            "com.zolt.ide.WorkspaceIdeModelJsonWriter",
            "com.zolt.ide.WorkspaceIdeModelService",
            "com.zolt.lockfile.LockConflict",
            "com.zolt.lockfile.LockPackage",
            "com.zolt.lockfile.LockPolicyEffect",
            "com.zolt.lockfile.LockfileFreshnessSummary",
            "com.zolt.lockfile.ZoltLockfile",
            "com.zolt.maven.ArtifactDescriptor",
            "com.zolt.maven.Coordinate",
            "com.zolt.maven.CoordinateParseException",
            "com.zolt.maven.CoordinateParser",
            "com.zolt.project.BuildMetadataSettings",
            "com.zolt.project.BuildSettings",
            "com.zolt.project.CompilerSettings",
            "com.zolt.project.DependencyConstraint",
            "com.zolt.project.DependencyConstraintKind",
            "com.zolt.project.DependencyExclusionSpec",
            "com.zolt.project.DependencyMetadata",
            "com.zolt.project.DependencyPolicyExclusion",
            "com.zolt.project.DependencyPolicySettings",
            "com.zolt.project.DependencySection",
            "com.zolt.project.FrameworkSettings",
            "com.zolt.project.GeneratedSourceKind",
            "com.zolt.project.GeneratedSourceStep",
            "com.zolt.project.JavaPackageValidator",
            "com.zolt.project.NativeSettings",
            "com.zolt.project.OpenApiGenerationSettings",
            "com.zolt.project.PackageMode",
            "com.zolt.project.PackageSettings",
            "com.zolt.project.ProjectConfig",
            "com.zolt.project.ProjectConfigWriteException",
            "com.zolt.project.ProjectConfigs",
            "com.zolt.project.ProjectMetadata",
            "com.zolt.project.ProjectPathException",
            "com.zolt.project.ProjectPaths",
            "com.zolt.project.ProjectVersionOverride",
            "com.zolt.project.ProtobufGenerationSettings",
            "com.zolt.project.PublicationMetadata",
            "com.zolt.project.QuarkusPackageMode",
            "com.zolt.project.QuarkusSettings",
            "com.zolt.project.RepositoryCredentialSettings",
            "com.zolt.project.RepositorySettings",
            "com.zolt.project.RepositoryUrlPolicy",
            "com.zolt.project.ResourceFilteringSettings",
            "com.zolt.project.ResourceMissingTokenPolicy",
            "com.zolt.project.ResourceTokenSettings",
            "com.zolt.project.SpringBootSettings",
            "com.zolt.project.TestRuntimeSettings",
            "com.zolt.project.TestSuiteSettings",
            "com.zolt.project.VersionAliasRules",
            "com.zolt.project.VersionPolicy",
            "com.zolt.provenance.BuildProvenance",
            "com.zolt.provenance.BuildProvenanceReader",
            "com.zolt.provenance.GitProvenance",
            "com.zolt.provenance.GitProvenanceReader",
            "com.zolt.workspace.WorkspaceConfig",
            "com.zolt.workspace.WorkspaceConfigException");

    @Test
    void publicTopLevelTypesRequireIntentionalAllowlistUpdates() throws IOException {
        assertEquals(EXPECTED_PUBLIC_TYPES, publicTypes());
    }

    @Test
    void representativeDownstreamConsumerCompiles(@TempDir Path tempDir) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Public API compile check requires a JDK, not a JRE.");
        Path source = tempDir.resolve("PublicApiConsumer.java");
        Path output = tempDir.resolve("classes");
        Files.createDirectories(output);
        Files.writeString(source, """
                import com.zolt.dependency.PackageId;
                import com.zolt.framework.FrameworkTestSelection;
                import com.zolt.ide.IdeModel;

                public final class PublicApiConsumer {
                    public PackageId packageId() {
                        return new PackageId("com.example", "app");
                    }

                    public FrameworkTestSelection selection() {
                        return FrameworkTestSelection.empty();
                    }

                    public Class<?> ideModelType() {
                        return IdeModel.class;
                    }
                }
                """);

        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        int exitCode = compiler.run(
                null,
                null,
                diagnostics,
                "-classpath",
                System.getProperty("java.class.path"),
                "-sourcepath",
                publicSourcePath(),
                "-d",
                output.toString(),
                source.toString());

        assertEquals(0, exitCode, diagnostics.toString(StandardCharsets.UTF_8));
    }

    private static Set<String> publicTypes() throws IOException {
        Set<String> types = new TreeSet<>();
        for (Path javaFile : ArchitectureSourceFiles.javaFiles(publicSourceRoots())) {
            String source = Files.readString(javaFile);
            Matcher packageMatcher = PACKAGE_PATTERN.matcher(source);
            if (!packageMatcher.find()) {
                continue;
            }
            Matcher typeMatcher = PUBLIC_TYPE_PATTERN.matcher(source);
            while (typeMatcher.find()) {
                types.add(packageMatcher.group(1) + "." + typeMatcher.group(1));
            }
        }
        return types;
    }

    private static List<Path> publicSourceRoots() {
        Path root = RepositoryPaths.root();
        return List.of(
                root.resolve("modules/zolt-model/src/main/java"),
                root.resolve("modules/zolt-framework-api/src/main/java"),
                root.resolve("modules/zolt-ide/src/main/java"));
    }

    private static String publicSourcePath() {
        return publicSourceRoots().stream()
                .map(Path::toString)
                .collect(java.util.stream.Collectors.joining(File.pathSeparator));
    }
}
