package sh.zolt.build.generatedsource;

import sh.zolt.doctor.JdkChecker;
import sh.zolt.doctor.JdkStatus;
import sh.zolt.project.ProjectConfig;
import sh.zolt.toml.ZoltTomlParser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Test support for {@code process} runner exec steps: writes small executable fixture scripts into a
 * temp bin directory and builds an {@link ExecGeneratedSourceService} whose curated PATH is that bin
 * directory (plus the real PATH so shell utilities resolve) using the production process runner. No
 * network, no real npm/node.
 */
final class ExecProcessRunnerTestSupport {
    private ExecProcessRunnerTestSupport() {
    }

    static Path writeScript(Path binDirectory, String name, String body) throws IOException {
        Files.createDirectories(binDirectory);
        Path script = binDirectory.resolve(name);
        Files.writeString(script, "#!/bin/sh\n" + body + "\n");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return script;
    }

    static ExecGeneratedSourceService service(Path projectDir, Path binDirectory, Map<String, String> extraAmbient) {
        Map<String, String> ambient = new HashMap<>();
        String realPath = System.getenv("PATH");
        ambient.put("PATH", binDirectory.toString() + (realPath == null ? "" : File.pathSeparator + realPath));
        String home = System.getenv("HOME");
        if (home != null) {
            ambient.put("HOME", home);
        }
        ambient.putAll(extraAmbient);
        UnaryOperator<String> ambientEnv = ambient::get;
        return new ExecGeneratedSourceService(
                jdkChecker(projectDir), File.pathSeparator, ExecGeneratedSourceService::runProcess, ambientEnv);
    }

    static ProjectConfig config(String toml) {
        return new ZoltTomlParser().parse(toml);
    }

    private static JdkChecker jdkChecker(Path projectDir) {
        return requiredVersion -> new JdkStatus(
                Optional.empty(),
                Optional.of(projectDir.resolve("fake-java")),
                Optional.of(Path.of("javac")),
                Optional.of(Path.of("jar")),
                Optional.of(requiredVersion),
                requiredVersion);
    }
}
