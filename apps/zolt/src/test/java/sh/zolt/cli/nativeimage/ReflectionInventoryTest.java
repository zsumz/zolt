package sh.zolt.cli.nativeimage;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Ties Zolt's main-source reflection sites to the native-image reflect-config.
 *
 * <p>Every main-source file under {@code modules/*&#47;src/main/java} or {@code apps/*&#47;src/main/java}
 * that performs reflection must appear in {@code reflection-inventory.json} with a disposition:
 *
 * <ul>
 *   <li>{@code NATIVE_BINARY} - executes inside the native zolt binary; the reflected type(s) it
 *       names must be registered in {@code reflect-config.json}.
 *   <li>{@code WORKER_JVM} - reflects over {@code io.quarkus:*}/{@code org.junit.platform.*} targets
 *       that only exist in the forked worker JVM launched by {@code JunitWorkerProcessLauncher}; the
 *       targets are absent from the native binary, so no reflect-config entry is required.
 *   <li>{@code JAXP_FACTORY} - {@code DocumentBuilderFactory}-style platform factory lookup handled
 *       by GraalVM's built-in JAXP support; no reflect-config entry is required.
 * </ul>
 *
 * <p>Adding or moving a reflection call fails this test until the inventory (and reflect-config, for
 * {@code NATIVE_BINARY}) is updated, so a native build cannot silently break.
 */
final class ReflectionInventoryTest {
    private static final Pattern REFLECTION_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern REFLECTION_CALL = Pattern.compile(
            "Class\\.forName\\(|getDeclaredMethod\\(|getDeclaredConstructor\\(|getMethod\\(|\\.newInstance\\(|getConstructor\\(|setAccessible\\(|getDeclaredField\\(");

    private static final String NATIVE_BINARY = "NATIVE_BINARY";
    private static final String WORKER_JVM = "WORKER_JVM";
    private static final String JAXP_FACTORY = "JAXP_FACTORY";

    @Test
    void everyReflectionSiteIsInventoriedAndCovered() throws IOException {
        Path root = repositoryRoot();
        Map<String, List<String>> reflectingFiles = scanReflectionSites(root);
        Map<String, InventoryEntry> inventory = readInventory(inventoryPath(root));
        Set<String> reflectConfigTypes = reflectionConfigNames(reflectConfigPath(root));

        List<String> problems = new ArrayList<>();

        for (Map.Entry<String, List<String>> reflecting : reflectingFiles.entrySet()) {
            String file = reflecting.getKey();
            InventoryEntry entry = inventory.get(file);
            if (entry == null) {
                problems.add(unlistedReflectionMessage(file, reflecting.getValue()));
                continue;
            }
            if (NATIVE_BINARY.equals(entry.disposition())) {
                List<String> missingTypes = entry.reflectConfigTypes().stream()
                        .filter(type -> !reflectConfigTypes.contains(type))
                        .sorted()
                        .toList();
                if (entry.reflectConfigTypes().isEmpty() || !missingTypes.isEmpty()) {
                    problems.add(nativeBinaryDriftMessage(file, reflecting.getValue(), entry, missingTypes));
                }
            }
        }

        for (Map.Entry<String, InventoryEntry> entry : inventory.entrySet()) {
            String file = entry.getKey();
            if (!Files.isRegularFile(root.resolve(file))) {
                problems.add("Stale inventory entry: " + file + " no longer exists.");
            } else if (!reflectingFiles.containsKey(file)) {
                problems.add("Stale inventory entry: " + file
                        + " no longer performs reflection; remove it from " + inventoryPath(root).getFileName() + ".");
            }
        }

        assertTrue(problems.isEmpty(), () -> driftMessage(root, problems));
    }

    private static Map<String, List<String>> scanReflectionSites(Path root) throws IOException {
        Map<String, List<String>> reflectingFiles = new TreeMap<>();
        for (Path sourceRoot : mainSourceRoots(root)) {
            try (Stream<Path> paths = Files.walk(sourceRoot)) {
                List<Path> javaFiles = paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .sorted()
                        .toList();
                for (Path javaFile : javaFiles) {
                    List<String> reflectionLines = reflectionLines(javaFile);
                    if (!reflectionLines.isEmpty()) {
                        reflectingFiles.put(relativePath(root, javaFile), reflectionLines);
                    }
                }
            }
        }
        return reflectingFiles;
    }

    private static List<String> reflectionLines(Path javaFile) throws IOException {
        List<String> lines = new ArrayList<>();
        List<String> fileLines = Files.readAllLines(javaFile);
        for (int index = 0; index < fileLines.size(); index++) {
            String line = fileLines.get(index);
            if (REFLECTION_CALL.matcher(line).find()) {
                lines.add((index + 1) + ": " + line.strip());
            }
        }
        return lines;
    }

    private static Map<String, InventoryEntry> readInventory(Path inventoryPath) throws IOException {
        Map<String, InventoryEntry> entries = new TreeMap<>();
        String json = Files.readString(inventoryPath);
        Matcher objects = Pattern.compile("\\{[^}]*\\}", Pattern.DOTALL).matcher(json);
        while (objects.find()) {
            String object = objects.group();
            String file = matchString(object, "file");
            String disposition = matchString(object, "disposition");
            if (file == null || disposition == null) {
                throw new IllegalStateException(
                        "Malformed inventory entry (missing file/disposition) in " + inventoryPath + ": " + object);
            }
            if (!Set.of(NATIVE_BINARY, WORKER_JVM, JAXP_FACTORY).contains(disposition)) {
                throw new IllegalStateException("Unknown disposition '" + disposition + "' for " + file
                        + " in " + inventoryPath + "; expected one of NATIVE_BINARY/WORKER_JVM/JAXP_FACTORY.");
            }
            entries.put(file, new InventoryEntry(file, disposition, reflectConfigTypes(object)));
        }
        return entries;
    }

    private static List<String> reflectConfigTypes(String object) {
        Matcher typesBlock = Pattern.compile("\"reflectConfigTypes\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(object);
        if (!typesBlock.find()) {
            return List.of();
        }
        List<String> types = new ArrayList<>();
        Matcher type = Pattern.compile("\"([^\"]+)\"").matcher(typesBlock.group(1));
        while (type.find()) {
            types.add(type.group(1));
        }
        return List.copyOf(types);
    }

    private static String matchString(String object, String key) {
        Matcher matcher = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"").matcher(object);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Set<String> reflectionConfigNames(Path path) throws IOException {
        Set<String> names = new TreeSet<>();
        Matcher matcher = REFLECTION_NAME.matcher(Files.readString(path));
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static List<Path> mainSourceRoots(Path root) throws IOException {
        List<Path> sourceRoots = new ArrayList<>();
        Path appSource = root.resolve("apps/zolt/src/main/java");
        if (Files.isDirectory(appSource)) {
            sourceRoots.add(appSource);
        }
        Path modulesRoot = root.resolve("modules");
        if (Files.isDirectory(modulesRoot)) {
            try (Stream<Path> modules = Files.list(modulesRoot)) {
                modules.map(module -> module.resolve("src/main/java"))
                        .filter(Files::isDirectory)
                        .sorted()
                        .forEach(sourceRoots::add);
            }
        }
        return sourceRoots;
    }

    private static String relativePath(Path root, Path file) {
        return root.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static Path inventoryPath(Path root) {
        return root.resolve("apps/zolt/src/test/resources/nativeimage/reflection-inventory.json");
    }

    private static Path reflectConfigPath(Path root) {
        return root.resolve("apps/zolt/src/main/resources/META-INF/native-image/sh.zolt/zolt/reflect-config.json");
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (isWorkspaceRoot(current)) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root from current working directory.");
    }

    private static boolean isWorkspaceRoot(Path current) {
        Path rootConfig = current.resolve("zolt.toml");
        if (!Files.isRegularFile(rootConfig)) {
            return false;
        }
        try {
            return Files.readString(rootConfig).lines().map(String::trim).anyMatch("[workspace]"::equals);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read root config at " + rootConfig + ".", exception);
        }
    }

    private static String unlistedReflectionMessage(String file, List<String> reflectionLines) {
        List<String> lines = new ArrayList<>();
        lines.add("Unlisted reflection in " + file + ":");
        reflectionLines.forEach(reflectionLine -> lines.add("    " + reflectionLine));
        lines.add("  Record it in reflection-inventory.json with one of:");
        lines.add("    NATIVE_BINARY  - runs in the native binary; also add the reflected type(s) to reflect-config.json"
                + " and list them under \"reflectConfigTypes\".");
        lines.add("    WORKER_JVM     - reflects over io.quarkus:*/org.junit.platform.* in the forked worker JVM"
                + " (JunitWorkerProcessLauncher); no reflect-config entry.");
        lines.add("    JAXP_FACTORY   - DocumentBuilderFactory-style lookup handled by GraalVM JAXP; no reflect-config entry.");
        return String.join(System.lineSeparator(), lines);
    }

    private static String nativeBinaryDriftMessage(
            String file, List<String> reflectionLines, InventoryEntry entry, List<String> missingTypes) {
        List<String> lines = new ArrayList<>();
        lines.add("NATIVE_BINARY reflection in " + file + " is not fully covered by reflect-config.json:");
        reflectionLines.forEach(reflectionLine -> lines.add("    " + reflectionLine));
        if (entry.reflectConfigTypes().isEmpty()) {
            lines.add("  List the reflected type(s) under \"reflectConfigTypes\" in reflection-inventory.json.");
        }
        if (!missingTypes.isEmpty()) {
            lines.add("  Missing from reflect-config.json:");
            missingTypes.forEach(type -> lines.add("    - " + type));
        }
        lines.add("  Add a reflect-config.json entry for each reflected type, or re-classify the file as"
                + " WORKER_JVM/JAXP_FACTORY if the target never enters the native binary.");
        return String.join(System.lineSeparator(), lines);
    }

    private static String driftMessage(Path root, List<String> problems) {
        List<String> lines = new ArrayList<>();
        lines.add("Native-image reflection coverage drift (see " + relativePath(root, inventoryPath(root)) + ").");
        lines.addAll(problems);
        return String.join(System.lineSeparator(), lines);
    }

    private record InventoryEntry(String file, String disposition, List<String> reflectConfigTypes) {
        private InventoryEntry {
            reflectConfigTypes = List.copyOf(reflectConfigTypes);
        }
    }
}
