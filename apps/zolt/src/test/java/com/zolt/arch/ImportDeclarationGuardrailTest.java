package com.zolt.arch;

import static com.zolt.arch.ArchitectureDiagnostics.describe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.arch.WorkspaceDependencyDeclarations.ImportSite;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/*
 * Cross-lib import declaration guardrail.
 *
 * scripts/bootstrap-zolt-jvm compiles the whole workspace in one javac pass, so a
 * cross-lib import resolves even when the importing module never declares the
 * owning module as a workspace dependency -- it rides in transitively through a
 * declared sibling. That hides undeclared module edges and lets a lib build that
 * would break the moment its declared deps are trimmed. This guard asserts that
 * every com.zolt.* type a module imports from another module is backed by an
 * explicit { workspace = "modules/<name>" } entry in that module's zolt.toml.
 *
 * Owner resolution is at the fully-qualified-type level (see
 * WorkspaceDependencyDeclarations) so split-named packages never produce a false
 * positive: a module importing its own type from a package another module also
 * publishes into is correctly treated as a self-import.
 */
final class ImportDeclarationGuardrailTest {
    private static final List<Path> MAIN_SOURCES = RepositoryPaths.mainSourceRoots();

    @Test
    void everyCrossLibImportIsBackedByADeclaredWorkspaceDependency() throws IOException {
        List<String> violations = undeclaredCrossModuleEdges(MAIN_SOURCES);

        assertTrue(
                violations.isEmpty(),
                () -> "Cross-lib imports without a declared workspace dependency:\n"
                        + describe(violations)
                        + "\nEach module must declare every other module it imports a com.zolt.* type from "
                        + "in its zolt.toml [dependencies] block.");
    }

    @Test
    void scannerFlagsAnUndeclaredCrossModuleEdge(@TempDir Path tempDir) throws IOException {
        Path quality = tempDir.resolve("modules/zolt-quality/src/main/java");
        Path repository = tempDir.resolve("modules/zolt-repository/src/main/java");
        write(
                repository.resolve("com/zolt/cache/ArtifactCacheException.java"),
                "package com.zolt.cache;\npublic final class ArtifactCacheException extends RuntimeException {}\n");
        write(
                quality.resolve("com/zolt/quality/LockfileQualityCheck.java"),
                "package com.zolt.quality;\nimport com.zolt.cache.ArtifactCacheException;\nfinal class LockfileQualityCheck {}\n");
        writeModuleConfig(tempDir.resolve("modules/zolt-quality"), Set.of());
        writeModuleConfig(tempDir.resolve("modules/zolt-repository"), Set.of());

        List<String> violations = undeclaredCrossModuleEdges(List.of(quality, repository));

        assertEquals(1, violations.size());
        String message = violations.get(0);
        assertTrue(message.contains("zolt-quality"), message);
        assertTrue(message.contains("com.zolt.cache.ArtifactCacheException"), message);
        assertTrue(message.contains("zolt-repository"), message);
        assertTrue(
                message.contains("\"com.zolt:zolt-repository\" = { workspace = \"modules/zolt-repository\" }"),
                message);
    }

    @Test
    void scannerAcceptsADeclaredCrossModuleEdge(@TempDir Path tempDir) throws IOException {
        Path quality = tempDir.resolve("modules/zolt-quality/src/main/java");
        Path repository = tempDir.resolve("modules/zolt-repository/src/main/java");
        write(
                repository.resolve("com/zolt/cache/ArtifactCacheException.java"),
                "package com.zolt.cache;\npublic final class ArtifactCacheException extends RuntimeException {}\n");
        write(
                quality.resolve("com/zolt/quality/LockfileQualityCheck.java"),
                "package com.zolt.quality;\nimport com.zolt.cache.ArtifactCacheException;\nfinal class LockfileQualityCheck {}\n");
        writeModuleConfig(tempDir.resolve("modules/zolt-quality"), Set.of("zolt-repository"));
        writeModuleConfig(tempDir.resolve("modules/zolt-repository"), Set.of());

        assertTrue(undeclaredCrossModuleEdges(List.of(quality, repository)).isEmpty());
    }

    @Test
    void scannerTreatsAnOwnedSplitPackageImportAsASelfImport(@TempDir Path tempDir) throws IOException {
        // com.zolt.maven is split: zolt-model declares Coordinate, zolt-repository declares
        // MavenRepositoryClient. zolt-model importing its own Coordinate must NOT be flagged
        // even though zolt-repository also publishes into com.zolt.maven and is undeclared.
        Path model = tempDir.resolve("modules/zolt-model/src/main/java");
        Path repository = tempDir.resolve("modules/zolt-repository/src/main/java");
        write(
                model.resolve("com/zolt/maven/Coordinate.java"),
                "package com.zolt.maven;\npublic final class Coordinate {}\n");
        write(
                model.resolve("com/zolt/model/Pom.java"),
                "package com.zolt.model;\nimport com.zolt.maven.Coordinate;\nfinal class Pom {}\n");
        write(
                repository.resolve("com/zolt/maven/MavenRepositoryClient.java"),
                "package com.zolt.maven;\npublic final class MavenRepositoryClient {}\n");
        writeModuleConfig(tempDir.resolve("modules/zolt-model"), Set.of());
        writeModuleConfig(tempDir.resolve("modules/zolt-repository"), Set.of());

        assertTrue(undeclaredCrossModuleEdges(List.of(model, repository)).isEmpty());
    }

    private static List<String> undeclaredCrossModuleEdges(List<Path> sourceRoots) throws IOException {
        Map<String, String> typeOwners = WorkspaceDependencyDeclarations.typeOwners(sourceRoots);
        Map<String, Set<String>> declarationsByModule = declarationsByModule(sourceRoots);
        Map<String, String> exampleFiles = new TreeMap<>();
        Set<String> reported = new TreeSet<>();

        for (ImportSite site : WorkspaceDependencyDeclarations.zoltImports(sourceRoots)) {
            Optional<String> owner =
                    WorkspaceDependencyDeclarations.resolveOwner(site.importedReference(), typeOwners);
            if (owner.isEmpty()) {
                continue;
            }
            String owningModule = owner.orElseThrow();
            if (owningModule.equals(site.module())) {
                continue; // self-import: the module owns the imported type itself.
            }
            Set<String> declared = declarationsByModule.getOrDefault(site.module(), Set.of());
            if (declared.contains(owningModule)) {
                continue;
            }
            String key = site.module() + "|" + site.importedReference() + "|" + owningModule;
            reported.add(key);
            exampleFiles.putIfAbsent(key, RepositoryPaths.displayPath(site.file()));
        }

        List<String> violations = new ArrayList<>();
        for (String key : reported) {
            String[] parts = key.split("\\|", -1);
            String importingModule = parts[0];
            String importedReference = parts[1];
            String owningModule = parts[2];
            violations.add(importingModule
                    + " imports " + importedReference
                    + " owned by " + owningModule
                    + " (e.g. " + exampleFiles.get(key) + ")"
                    + " but does not declare it; add \"com.zolt:" + owningModule
                    + "\" = { workspace = \"modules/" + owningModule + "\" } to "
                    + moduleConfigPath(importingModule, sourceRoots));
        }
        return violations;
    }

    private static Map<String, Set<String>> declarationsByModule(List<Path> sourceRoots) throws IOException {
        Map<String, Set<String>> declarations = new TreeMap<>();
        for (Path sourceRoot : sourceRoots) {
            String module = WorkspaceDependencyDeclarations.moduleName(sourceRoot);
            Path moduleRoot = WorkspaceDependencyDeclarations.moduleRoot(sourceRoot);
            declarations.put(module, WorkspaceDependencyDeclarations.declaredWorkspaceDependencies(moduleRoot));
        }
        return declarations;
    }

    private static String moduleConfigPath(String module, List<Path> sourceRoots) {
        for (Path sourceRoot : sourceRoots) {
            if (WorkspaceDependencyDeclarations.moduleName(sourceRoot).equals(module)) {
                Path config = WorkspaceDependencyDeclarations.moduleRoot(sourceRoot).resolve("zolt.toml");
                return RepositoryPaths.displayPath(config);
            }
        }
        return module + "/zolt.toml";
    }

    private static void writeModuleConfig(Path moduleRoot, Set<String> workspaceDependencies) throws IOException {
        StringBuilder config = new StringBuilder("[project]\nname = \"")
                .append(moduleRoot.getFileName())
                .append("\"\n\n[dependencies]\n");
        for (String dependency : workspaceDependencies) {
            config.append("\"com.zolt:")
                    .append(dependency)
                    .append("\" = { workspace = \"modules/")
                    .append(dependency)
                    .append("\" }\n");
        }
        write(moduleRoot.resolve("zolt.toml"), config.toString());
    }

    private static void write(Path path, String contents) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
    }
}
