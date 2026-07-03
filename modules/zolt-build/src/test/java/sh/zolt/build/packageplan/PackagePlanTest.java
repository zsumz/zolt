package sh.zolt.build.packageplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.build.PackageException;
import sh.zolt.project.PackageMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

final class PackagePlanTest {
    private static final Path PROJECT_ROOT = Path.of("demo");
    private static final Path ARCHIVE = PROJECT_ROOT.resolve("target/demo-0.1.0.jar");
    private static final Path CLASSES = PROJECT_ROOT.resolve("target/classes");

    @Test
    void defaultsNullableCollectionsModeAndRuntimeClasspathDeterministically() {
        List<PackagePlanDependency> dependencies = new ArrayList<>();
        List<PackagePlanWarning> warnings = new ArrayList<>();

        PackagePlan plan = new PackagePlan(
                PROJECT_ROOT,
                null,
                ARCHIVE,
                CLASSES,
                "archive root",
                null,
                dependencies,
                warnings);
        dependencies.add(null);
        warnings.add(null);

        assertEquals(PackageMode.THIN, plan.mode());
        assertEquals(Optional.empty(), plan.runtimeClasspathPath());
        assertTrue(plan.dependencies().isEmpty());
        assertTrue(plan.warnings().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> plan.dependencies().add(null));
        assertThrows(UnsupportedOperationException.class, () -> plan.warnings().add(null));
    }

    @Test
    void rejectsIncompletePlanWithActionableMessages() {
        assertFailure(
                () -> new PackagePlan(
                        null,
                        PackageMode.THIN,
                        ARCHIVE,
                        CLASSES,
                        "archive root",
                        Optional.empty(),
                        List.of(),
                        List.of()),
                "Package plan requires a project root.");
        assertFailure(
                () -> new PackagePlan(
                        PROJECT_ROOT,
                        PackageMode.THIN,
                        null,
                        CLASSES,
                        "archive root",
                        Optional.empty(),
                        List.of(),
                        List.of()),
                "Package plan requires an archive path.");
        assertFailure(
                () -> new PackagePlan(
                        PROJECT_ROOT,
                        PackageMode.THIN,
                        ARCHIVE,
                        null,
                        "archive root",
                        Optional.empty(),
                        List.of(),
                        List.of()),
                "Package plan requires an application output path.");
        assertFailure(
                () -> new PackagePlan(
                        PROJECT_ROOT,
                        PackageMode.THIN,
                        ARCHIVE,
                        CLASSES,
                        " ",
                        Optional.empty(),
                        List.of(),
                        List.of()),
                "Package plan requires an application layout.");
    }

    private static void assertFailure(Executable executable, String message) {
        PackageException exception = assertThrows(PackageException.class, executable);

        assertEquals(message, exception.getMessage());
    }
}
