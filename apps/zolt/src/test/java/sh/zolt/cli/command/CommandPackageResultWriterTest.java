package sh.zolt.cli.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import sh.zolt.build.BuildResult;
import sh.zolt.build.packaging.PackageArtifact;
import sh.zolt.build.packaging.PackageResult;
import sh.zolt.cli.command.packaging.CommandPackageResultWriter;
import sh.zolt.project.PackageMode;
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
                SUMMARY Packaged 2 compiled files as thin jar
                DETAIL Included Main-Class manifest entry
                DETAIL Run with: java -jar target/demo.jar
                DETAIL Run with dependencies: zolt run-package -- [args]
                DETAIL Thin jar: dependencies are not bundled.
                POINTER wrote target/demo.jar
                POINTER wrote target/demo.runtime-classpath
                POINTER wrote target/demo.jar.zolt-package.json
                POINTER wrote target/demo-sources.jar
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
                SUMMARY Packaged 4 compiled files as uber jar in member
                DETAIL Included Main-Class manifest entry in member
                POINTER wrote member/target/demo.jar
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
                SUMMARY Packaged 3 compiled files as war war
                DETAIL WAR is a servlet container deployment artifact; use `spring-boot-war` for java -jar.
                DETAIL WAR: application classes are under WEB-INF/classes and runtime dependencies are under WEB-INF/lib.
                POINTER wrote target/demo.war
                """,
                print(result, ""));
    }

    private String print(PackageResult result, String suffix) {
        return writer.lines(result, suffix).stream()
                .map(line -> {
                    String prefix = line.kind() + " ";
                    return switch (line.kind()) {
                        case POINTER -> prefix + line.verb() + " " + line.message();
                        default -> prefix + line.message();
                    };
                })
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
