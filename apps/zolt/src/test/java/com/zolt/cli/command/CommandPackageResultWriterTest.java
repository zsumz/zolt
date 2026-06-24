package com.zolt.cli.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zolt.build.BuildResult;
import com.zolt.build.PackageArtifact;
import com.zolt.build.PackageResult;
import com.zolt.project.PackageMode;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class CommandPackageResultWriterTest {
    private final CommandPackageResultWriter writer = new CommandPackageResultWriter();

    @Test
    void printsRunnableThinJarResult() {
        PackageResult result = new PackageResult(
                buildResult(),
                PackageMode.THIN,
                Path.of("target/demo.jar"),
                Optional.of(Path.of("target/demo.runtime-classpath")),
                Optional.of(Path.of("target/demo.jar.zolt-package.json")),
                2,
                true,
                List.of(new PackageArtifact("sources", Path.of("target/demo-sources.jar"), 1)));

        assertEquals(
                """
                Packaged 2 compiled files as thin jar
                Included Main-Class manifest entry
                Run with: java -jar target/demo.jar
                Run with dependencies: zolt run-package -- [args]
                Thin jar: dependencies are not bundled.
                Wrote runtime classpath to target/demo.runtime-classpath
                Wrote archive to target/demo.jar
                Wrote package evidence to target/demo.jar.zolt-package.json
                Wrote sources jar to target/demo-sources.jar
                """,
                print(result, ""));
    }

    @Test
    void printsWorkspaceMemberResultWithSuffix() {
        PackageResult result = new PackageResult(
                buildResult(),
                PackageMode.UBER,
                Path.of("member/target/demo.jar"),
                Optional.empty(),
                4,
                true);

        assertEquals(
                """
                Packaged 4 compiled files as uber jar in member
                Included Main-Class manifest entry in member
                Wrote archive to member/target/demo.jar
                """,
                print(result, " in member"));
    }

    @Test
    void printsNonRunnableWarResult() {
        PackageResult result = new PackageResult(
                buildResult(),
                PackageMode.WAR,
                Path.of("target/demo.war"),
                Optional.empty(),
                3,
                false);

        assertEquals(
                """
                Packaged 3 compiled files as war war
                WAR is a servlet container deployment artifact; use `spring-boot-war` for java -jar.
                WAR: application classes are under WEB-INF/classes and runtime dependencies are under WEB-INF/lib.
                Wrote archive to target/demo.war
                """,
                print(result, ""));
    }

    private String print(PackageResult result, String suffix) {
        return writer.lines(result, suffix).stream()
                .map(CommandPackageResultWriter.OutputLine::message)
                .reduce("", (left, right) -> left + right + "\n");
    }

    private static BuildResult buildResult() {
        return new BuildResult(
                Optional.empty(),
                1,
                0,
                Path.of("target/classes"),
                "");
    }
}
