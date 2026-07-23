package sh.zolt.canary.execjvm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The {@code tool = "project"} generator: runs on this example's own compiled classpath and writes a
 * build-info resource into the step's declared output directory. It reads only its declared input and
 * the Zolt-provided {@code ZOLT_PROJECT_ROOT}/{@code ZOLT_OUTPUT_DIR}; no network, no external tool.
 */
public final class BuildInfoGenerator {
    private BuildInfoGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Path root = Path.of(System.getenv("ZOLT_PROJECT_ROOT"));
        Path output = Path.of(System.getenv("ZOLT_OUTPUT_DIR"));
        String version = Files.readString(root.resolve("src/main/exec/version.txt")).strip();
        Files.createDirectories(output);
        Files.writeString(
                output.resolve("build-info.properties"),
                "canary.version=" + version + "\ncanary.generator=exec-project\n");
    }
}
