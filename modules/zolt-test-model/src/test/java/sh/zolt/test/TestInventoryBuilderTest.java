package sh.zolt.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TestInventoryBuilderTest {
    private final TestInventoryBuilder builder = new TestInventoryBuilder();

    @TempDir
    private Path tempDir;

    @Test
    void scansDefaultJUnitAndSpockClassNamesInDeterministicOrder() throws IOException {
        Path output = tempDir.resolve("test-classes");
        writeClass(output, "com/example/ServiceSpec.class");
        writeClass(output, "com/example/UserServiceTest.class");
        writeClass(output, "com/example/UserServiceHelper.class");
        writeClass(output, "com/example/package-info.class");

        TestInventory inventory = builder.scan(output, TestSelection.empty());

        assertEquals(
                List.of("com.example.ServiceSpec", "com.example.UserServiceTest"),
                inventory.entries().stream().map(TestInventoryEntry::className).toList());
        assertEquals(2, inventory.summary().totalEntries());
        assertEquals(TestSelection.defaultScanClassNamePatterns(), inventory.summary().classNamePatterns());
        assertTrue(inventory.entries().getFirst().matchedClassNamePatterns().contains(".*Spec"));
    }

    @Test
    void appliesConfiguredClassNamePatterns() throws IOException {
        Path output = tempDir.resolve("test-classes");
        writeClass(output, "com/example/FastServiceTest.class");
        writeClass(output, "com/example/SlowComponentTest.class");

        TestSelection selection = TestSelection.fromCli(List.of(), List.of("*ServiceTest"), List.of(), List.of());
        TestInventory inventory = builder.scan(output, selection);

        assertEquals(
                List.of("com.example.FastServiceTest"),
                inventory.entries().stream().map(TestInventoryEntry::className).toList());
        assertEquals(List.of(".*ServiceTest"), inventory.summary().classNamePatterns());
        assertEquals(List.of(".*ServiceTest"), inventory.entries().getFirst().matchedClassNamePatterns());
    }

    @Test
    void selectsExplicitClassAndMethodSelectorClasses() throws IOException {
        Path output = tempDir.resolve("test-classes");
        writeClass(output, "com/example/FastServiceTest.class");
        writeClass(output, "com/example/OtherTest.class");

        TestSelection selection = TestSelection.fromCli(
                List.of("com.example.FastServiceTest", "com.example.OtherTest#runs"),
                List.of(),
                List.of(),
                List.of());
        TestInventory inventory = builder.scan(output, selection);

        assertEquals(
                List.of("com.example.FastServiceTest", "com.example.OtherTest"),
                inventory.entries().stream().map(TestInventoryEntry::className).toList());
        assertEquals(List.of("com.example.FastServiceTest"), inventory.summary().classSelectors());
        assertEquals(List.of("com.example.OtherTest#runs"), inventory.summary().methodSelectors());
        assertEquals(List.of(), inventory.summary().classNamePatterns());
    }

    @Test
    void reportsMissingExplicitSelectorsWithoutInventingEntries() throws IOException {
        Path output = tempDir.resolve("test-classes");
        writeClass(output, "com/example/PresentTest.class");

        TestSelection selection = TestSelection.fromCli(
                List.of("com.example.PresentTest", "com.example.MissingTest"),
                List.of(),
                List.of("fast"),
                List.of("slow"));
        TestInventory inventory = builder.scan(output, selection);

        assertEquals(
                List.of("com.example.PresentTest"),
                inventory.entries().stream().map(TestInventoryEntry::className).toList());
        assertEquals(List.of("fast"), inventory.summary().includedTags());
        assertEquals(List.of("slow"), inventory.summary().excludedTags());
        assertEquals(List.of("com.example.MissingTest"), inventory.summary().missingExplicitClassSelectors());
    }

    @Test
    void missingOutputRootProducesEmptyInventory() {
        Path output = tempDir.resolve("missing-test-classes");

        TestInventory inventory = builder.scan(output, TestSelection.empty());

        assertEquals(List.of(), inventory.entries());
        assertEquals(0, inventory.summary().totalEntries());
        assertEquals(List.of(output.toAbsolutePath().normalize()), inventory.summary().outputRoots());
    }

    private static void writeClass(Path outputRoot, String relativePath) throws IOException {
        Path file = outputRoot.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] {0});
    }
}
