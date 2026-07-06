package sh.zolt.arch;

import static sh.zolt.arch.ArchitectureDiagnostics.describe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/*
 * Single-owner guardrail: every sh.zolt.* package compiled into the workspace
 * must be declared by exactly one lib/app module. Two modules declaring the same
 * package re-creates the cross-lib "in-name split" that forces members public
 * only to cross the boundary. The historical splits are resolved; this test
 * keeps them resolved. Unlike SourceFileParser (which truncates to the 3-segment
 * top-level package and is therefore blind to these splits), this scanner maps
 * the FULL package declaration to its owning module.
 */
final class PackageOwnershipGuardrailTest {
    private static final List<Path> MAIN_SOURCES = RepositoryPaths.mainSourceRoots();
    private static final Path ALLOWLIST = RepositoryPaths.appRoot()
            .resolve("src/test/resources/sh/zolt/arch/package-ownership-allowlist.txt");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;");

    @Test
    void everyPackageHasASingleOwningModule() throws IOException {
        Map<String, Set<String>> owners = packageOwners(MAIN_SOURCES);
        Map<String, AllowlistEntry> allowlist = readAllowlist(ALLOWLIST);
        List<String> violations = new ArrayList<>();

        for (Map.Entry<String, Set<String>> entry : owners.entrySet()) {
            if (entry.getValue().size() > 1 && !allowlist.containsKey(entry.getKey())) {
                violations.add(entry.getKey()
                        + " is declared in multiple modules "
                        + entry.getValue()
                        + "; give each module its own subpackage or add a tracked exception");
            }
        }
        allowlist.keySet().stream()
                .filter(packageName -> owners.getOrDefault(packageName, Set.of()).size() <= 1)
                .sorted()
                .forEach(packageName -> violations.add(
                        packageName + " is no longer split across modules; remove the allowlist entry"));

        assertTrue(
                violations.isEmpty(),
                () -> "Package single-ownership violations:\n"
                        + describe(violations)
                        + "\nKeep each sh.zolt.* package owned by one module.");
    }

    @Test
    void scannerFlagsPackageDeclaredInTwoModules(@TempDir Path tempDir) throws IOException {
        Path libAlpha = tempDir.resolve("modules/zolt-alpha/src/main/java");
        Path libBeta = tempDir.resolve("modules/zolt-beta/src/main/java");
        write(libAlpha.resolve("sh/zolt/shared/Alpha.java"), "package sh.zolt.shared;\nfinal class Alpha {}\n");
        write(libBeta.resolve("sh/zolt/shared/Beta.java"), "package sh.zolt.shared;\nfinal class Beta {}\n");
        write(libAlpha.resolve("sh/zolt/alpha/Owned.java"), "package sh.zolt.alpha;\nfinal class Owned {}\n");

        Map<String, Set<String>> owners = packageOwners(List.of(libAlpha, libBeta));

        assertEquals(Set.of("zolt-alpha", "zolt-beta"), owners.get("sh.zolt.shared"));
        assertEquals(Set.of("zolt-alpha"), owners.get("sh.zolt.alpha"));
    }

    @Test
    void scannerAcceptsCleanSingleOwnerLayout(@TempDir Path tempDir) throws IOException {
        Path libAlpha = tempDir.resolve("modules/zolt-alpha/src/main/java");
        Path libBeta = tempDir.resolve("modules/zolt-beta/src/main/java");
        write(libAlpha.resolve("sh/zolt/shared/Model.java"), "package sh.zolt.shared;\nfinal class Model {}\n");
        write(libBeta.resolve("sh/zolt/shared/toml/Codec.java"),
                "package sh.zolt.shared.toml;\nfinal class Codec {}\n");

        Map<String, Set<String>> owners = packageOwners(List.of(libAlpha, libBeta));

        assertEquals(Set.of("zolt-alpha"), owners.get("sh.zolt.shared"));
        assertEquals(Set.of("zolt-beta"), owners.get("sh.zolt.shared.toml"));
        assertTrue(owners.values().stream().allMatch(modules -> modules.size() == 1));
    }

    @Test
    void allowlistParserReadsTrackedExceptions(@TempDir Path tempDir) throws IOException {
        Path allowlist = tempDir.resolve("allowlist.txt");
        Files.writeString(allowlist, """
                # package|followUp|reason
                sh.zolt.example||split not yet resolved
                """);

        assertEquals(
                Map.of(
                        "sh.zolt.example",
                        new AllowlistEntry("sh.zolt.example", "", "split not yet resolved")),
                readAllowlist(allowlist));
    }

    @Test
    void allowlistParserRejectsMalformedLines(@TempDir Path tempDir) throws IOException {
        Path allowlist = tempDir.resolve("allowlist.txt");
        Files.writeString(allowlist, "sh.zolt.example|\n");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> readAllowlist(allowlist));

        assertEquals("Invalid package ownership allowlist line: sh.zolt.example|", exception.getMessage());
    }

    @Test
    void allowlistParserRejectsDuplicatePackages(@TempDir Path tempDir) throws IOException {
        Path allowlist = tempDir.resolve("allowlist.txt");
        Files.writeString(allowlist, """
                sh.zolt.example||split not yet resolved
                sh.zolt.example||still split
                """);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> readAllowlist(allowlist));

        assertEquals("Duplicate package ownership allowlist entry: sh.zolt.example", exception.getMessage());
    }

    private static Map<String, Set<String>> packageOwners(List<Path> sourceRoots) throws IOException {
        Map<String, Set<String>> owners = new TreeMap<>();
        for (Path sourceRoot : sourceRoots) {
            String module = moduleName(sourceRoot);
            for (Path javaFile : ArchitectureSourceFiles.javaFiles(List.of(sourceRoot))) {
                Optional<String> packageName = packageDeclaration(javaFile);
                packageName.ifPresent(name ->
                        owners.computeIfAbsent(name, ignored -> new TreeSet<>()).add(module));
            }
        }
        return owners;
    }

    private static Optional<String> packageDeclaration(Path javaFile) throws IOException {
        for (String line : Files.readAllLines(javaFile)) {
            Matcher matcher = PACKAGE_PATTERN.matcher(line);
            if (matcher.matches()) {
                String name = matcher.group(1);
                return name.startsWith("sh.zolt.") ? Optional.of(name) : Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static String moduleName(Path sourceRoot) {
        // sourceRoot ends with <module>/src/main/java; the module directory name owns the package.
        Path normalized = sourceRoot.toAbsolutePath().normalize();
        Path moduleDir = normalized.getParent(); // .../<module>/src/main
        for (int i = 0; i < 2 && moduleDir != null; i++) {
            moduleDir = moduleDir.getParent();
        }
        if (moduleDir == null) {
            throw new IllegalStateException("Could not derive module name from source root " + sourceRoot);
        }
        return moduleDir.getFileName().toString();
    }

    private static Map<String, AllowlistEntry> readAllowlist(Path path) throws IOException {
        return ArchitectureAllowlistSupport.readAllowlist(
                path,
                PackageOwnershipGuardrailTest::parseAllowlistLine,
                AllowlistEntry::packageName,
                "Duplicate package ownership allowlist entry: ");
    }

    private static Optional<AllowlistEntry> parseAllowlistLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return Optional.empty();
        }
        String[] parts = trimmed.split("\\|", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid package ownership allowlist line: " + line);
        }
        return Optional.of(new AllowlistEntry(parts[0], parts[1], parts[2]));
    }

    private static void write(Path path, String contents) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
    }

    private record AllowlistEntry(String packageName, String followUp, String reason) {
    }
}
