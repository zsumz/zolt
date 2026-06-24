package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ArchitectureCommandAttributeKeyTest {
    private static final Path CLI_COMMAND_SOURCES = Path.of("src/main/java/com/zolt/cli/command");
    private static final Pattern RAW_COMMAND_ATTRIBUTE_KEY_PATTERN =
            Pattern.compile("\\battributes\\.put\\s*\\(\\s*\"([^\"]+)\"");

    @Test
    void commandAttributeMapsUseNamedKeys() throws IOException {
        List<RawCommandAttributeKey> violations = findRawCommandAttributeKeys(CLI_COMMAND_SOURCES);

        assertTrue(
                violations.isEmpty(),
                () -> "Command attribute maps must use CommandAttributeKeys constants, not raw output key strings:\n"
                        + describeRawCommandAttributeKeys(violations));
    }

    @Test
    void commandAttributeKeyScannerFindsRawPutKeys(@TempDir Path tempDir) throws IOException {
        write(
                tempDir.resolve("CommandAttributes.java"),
                """
                package com.zolt.cli.command;

                import java.util.LinkedHashMap;
                import java.util.Map;

                final class CommandAttributes {
                    Map<String, String> attributes() {
                        Map<String, String> attributes = new LinkedHashMap<>();
                        attributes.put(
                                "jarDownloadNanos",
                                "0");
                        attributes.put(CommandAttributeKeys.RAW_POM_PARSE_NANOS, "0");
                        return attributes;
                    }
                }
                """);

        assertEquals(
                List.of(new RawCommandAttributeKey(
                        tempDir.resolve("CommandAttributes.java"),
                        9,
                        "jarDownloadNanos")),
                findRawCommandAttributeKeys(tempDir));
    }

    private static List<RawCommandAttributeKey> findRawCommandAttributeKeys(Path sourceRoot) throws IOException {
        List<RawCommandAttributeKey> violations = new ArrayList<>();
        for (Path javaFile : ArchitectureSourceFiles.javaFiles(List.of(sourceRoot))) {
            String source = Files.readString(javaFile);
            Matcher matcher = RAW_COMMAND_ATTRIBUTE_KEY_PATTERN.matcher(source);
            while (matcher.find()) {
                violations.add(new RawCommandAttributeKey(
                        javaFile,
                        lineNumber(source, matcher.start()),
                        matcher.group(1)));
            }
        }
        return List.copyOf(violations);
    }

    private static int lineNumber(String source, int offset) {
        int line = 1;
        for (int index = 0; index < offset; index++) {
            if (source.charAt(index) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static String describeRawCommandAttributeKeys(List<RawCommandAttributeKey> violations) {
        StringBuilder description = new StringBuilder();
        for (RawCommandAttributeKey violation : violations) {
            description.append("- ")
                    .append(violation.path())
                    .append(':')
                    .append(violation.line())
                    .append(" key=")
                    .append(violation.key())
                    .append('\n');
        }
        return description.toString();
    }

    private static void write(Path path, String contents) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
    }

    private record RawCommandAttributeKey(Path path, int line, String key) {
    }
}
