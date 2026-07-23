package sh.zolt.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import sh.zolt.lockfile.toml.ZoltLockfileReader;
import sh.zolt.project.ProjectConfig;

final class LicensePolicyQualityCheckTest extends QualityCheckServiceTestSupport {
    private final LicensePolicyQualityCheck check = new LicensePolicyQualityCheck(new ZoltLockfileReader());

    @TempDir
    Path tempDir;

    @Test
    void skipsWhenNoLicensePolicyConfigured() throws IOException {
        Path projectDir = tempDir.resolve("no-policy");
        ProjectConfig config = parseProject(projectDir, "");
        writeLockfile(projectDir, pkg("org.example:lib", "1.0.0", true));

        List<QualityCheckResult> results = check.check(
                Optional.empty(), projectDir, config, projectDir.resolve("zolt.lock"), false, tempDir.resolve("cache"));

        assertEquals(1, results.size());
        assertEquals(QualityCheckStatus.SKIPPED, results.getFirst().status());
    }

    @Test
    void failsUnknownLicenseWhenStrictnessIsFail() throws IOException {
        Path projectDir = tempDir.resolve("unknown-fail");
        ProjectConfig config = parseProject(projectDir, """

                [dependencyPolicy.licenses]
                unknown = "fail"
                """);
        writeLockfile(projectDir, pkg("org.example:lib", "1.0.0", true));

        List<QualityCheckResult> results = check.check(
                Optional.empty(), projectDir, config, projectDir.resolve("zolt.lock"), false, tempDir.resolve("empty-cache"));

        assertFalse(ok(results));
        QualityCheckResult failure = failures(results).getFirst();
        assertEquals("org.example:lib:1.0.0", failure.subject());
        assertTrue(failure.message().contains("unknown = fail"));
        assertTrue(failure.nextStep().contains("[dependencyPolicy.licenses].allow"));
    }

    @Test
    void failsDeniedSpdxLicenseNamingTheDependencyAndPolicyLine() throws IOException {
        Path projectDir = tempDir.resolve("deny");
        Path cache = tempDir.resolve("deny-cache");
        writePom(cache, "org.example", "lib", "1.0.0", """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>lib</artifactId>
                  <version>1.0.0</version>
                  <licenses><license><name>Apache License, Version 2.0</name></license></licenses>
                </project>
                """);
        ProjectConfig config = parseProject(projectDir, """

                [dependencyPolicy.licenses]
                deny = ["Apache-2.0"]
                """);
        writeLockfile(projectDir, pkg("org.example:lib", "1.0.0", true));

        List<QualityCheckResult> results = check.check(
                Optional.empty(), projectDir, config, projectDir.resolve("zolt.lock"), false, cache);

        assertFalse(ok(results));
        QualityCheckResult failure = failures(results).getFirst();
        assertEquals("org.example:lib:1.0.0", failure.subject());
        assertTrue(failure.message().contains("Apache-2.0"));
        assertTrue(failure.message().contains("deny"));
    }

    @Test
    void passesWhenLicenseIsAllowed() throws IOException {
        Path projectDir = tempDir.resolve("allow");
        Path cache = tempDir.resolve("allow-cache");
        writePom(cache, "org.example", "lib", "1.0.0", """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>lib</artifactId>
                  <version>1.0.0</version>
                  <licenses><license><name>MIT License</name></license></licenses>
                </project>
                """);
        ProjectConfig config = parseProject(projectDir, """

                [dependencyPolicy.licenses]
                allow = ["MIT"]
                """);
        writeLockfile(projectDir, pkg("org.example:lib", "1.0.0", true));

        List<QualityCheckResult> results = check.check(
                Optional.empty(), projectDir, config, projectDir.resolve("zolt.lock"), false, cache);

        assertTrue(ok(results));
    }

    private static boolean ok(List<QualityCheckResult> results) {
        return results.stream().noneMatch(result -> result.status() == QualityCheckStatus.FAILED);
    }

    private static List<QualityCheckResult> failures(List<QualityCheckResult> results) {
        return results.stream().filter(result -> result.status() == QualityCheckStatus.FAILED).toList();
    }

    private static String pkg(String coordinate, String version, boolean withPom) {
        String group = coordinate.substring(0, coordinate.indexOf(':'));
        String artifact = coordinate.substring(coordinate.indexOf(':') + 1);
        String base = group.replace('.', '/') + "/" + artifact + "/" + version + "/" + artifact + "-" + version;
        String pomLine = withPom ? "pom = \"" + base + ".pom\"\n" : "";
        return "\n[[package]]\n"
                + "id = \"" + coordinate + "\"\n"
                + "version = \"" + version + "\"\n"
                + "source = \"maven-central\"\n"
                + "scope = \"compile\"\n"
                + "direct = true\n"
                + "jar = \"" + base + ".jar\"\n"
                + pomLine
                + "dependencies = []\n";
    }

    private static void writePom(Path cache, String group, String artifact, String version, String xml)
            throws IOException {
        Path pom = cache.resolve(group.replace('.', '/') + "/" + artifact + "/" + version
                + "/" + artifact + "-" + version + ".pom");
        Files.createDirectories(pom.getParent());
        Files.writeString(pom, xml);
    }
}
