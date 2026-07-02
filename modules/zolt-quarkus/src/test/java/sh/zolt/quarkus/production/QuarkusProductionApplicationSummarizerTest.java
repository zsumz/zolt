package sh.zolt.quarkus.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.quarkus.QuarkusAugmentationException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuarkusProductionApplicationSummarizerTest {
    private final QuarkusProductionApplicationSummarizer summarizer = new QuarkusProductionApplicationSummarizer();

    @Test
    void summarizesJarResult() {
        FakeAugmentResult augmentResult = new FakeAugmentResult(
                List.of(new Object(), new Object()),
                new FakeJarResult(
                        Path.of("/repo/target/quarkus-app/quarkus-run.jar"),
                        Path.of("/repo/target/quarkus-app/lib"),
                        false),
                null);

        QuarkusProductionApplicationSummary summary = summarizer.summarize(new QuarkusProductionApplicationHandle(
                augmentResult,
                augmentResult.getClass().getName()));

        assertEquals(FakeAugmentResult.class.getName(), summary.augmentResultClass());
        assertEquals(2, summary.artifactResultCount());
        assertEquals(Path.of("/repo/target/quarkus-app/quarkus-run.jar"), summary.jarPath());
        assertEquals(Path.of("/repo/target/quarkus-app/lib"), summary.libraryDirectory());
        assertFalse(summary.uberJar());
        assertTrue(summary.hasJar());
        assertFalse(summary.hasNativeImage());
    }

    @Test
    void summarizesNullJarAndResults() {
        FakeAugmentResult augmentResult = new FakeAugmentResult(null, null, Path.of("/repo/target/app-runner"));

        QuarkusProductionApplicationSummary summary = summarizer.summarize(new QuarkusProductionApplicationHandle(
                augmentResult,
                augmentResult.getClass().getName()));

        assertEquals(0, summary.artifactResultCount());
        assertNull(summary.jarPath());
        assertNull(summary.libraryDirectory());
        assertFalse(summary.uberJar());
        assertFalse(summary.hasJar());
        assertTrue(summary.hasNativeImage());
        assertEquals(Path.of("/repo/target/app-runner"), summary.nativeImagePath());
    }

    @Test
    void rejectsMissingResultMethods() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> summarizer.summarize(new QuarkusProductionApplicationHandle(
                        new MissingGetResults(),
                        MissingGetResults.class.getName())));

        assertTrue(exception.getMessage().contains("result API is incompatible"));
        assertTrue(exception.getMessage().contains("getResults"));
    }

    @Test
    void rejectsNonCollectionResults() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> summarizer.summarize(new QuarkusProductionApplicationHandle(
                        new NonCollectionResults(),
                        NonCollectionResults.class.getName())));

        assertTrue(exception.getMessage().contains("non-collection results"));
    }

    @Test
    void rejectsNonPathJarPath() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> summarizer.summarize(new QuarkusProductionApplicationHandle(
                        new FakeAugmentResult(List.of(), new NonPathJarResult(), null),
                        FakeAugmentResult.class.getName())));

        assertTrue(exception.getMessage().contains("non-path jar path"));
    }

    @Test
    void rejectsNonBooleanUberJarValue() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> summarizer.summarize(new QuarkusProductionApplicationHandle(
                        new FakeAugmentResult(List.of(), new NonBooleanJarResult(), null),
                        FakeAugmentResult.class.getName())));

        assertTrue(exception.getMessage().contains("non-boolean value"));
        assertTrue(exception.getMessage().contains("isUberJar"));
    }

    @Test
    void reportsInspectionFailures() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> summarizer.summarize(new QuarkusProductionApplicationHandle(
                        new FailingAugmentResult(),
                        FailingAugmentResult.class.getName())));

        assertTrue(exception.getMessage().contains("result inspection failed"));
    }

    public static final class FakeAugmentResult {
        private final List<Object> results;
        private final Object jar;
        private final Path nativeImagePath;

        FakeAugmentResult(List<Object> results, Object jar, Path nativeImagePath) {
            this.results = results;
            this.jar = jar;
            this.nativeImagePath = nativeImagePath;
        }

        public List<Object> getResults() {
            return results;
        }

        public Object getJar() {
            return jar;
        }

        public Path getNativeResult() {
            return nativeImagePath;
        }
    }

    public static final class FakeJarResult {
        private final Path path;
        private final Path libraryDirectory;
        private final boolean uberJar;

        FakeJarResult(Path path, Path libraryDirectory, boolean uberJar) {
            this.path = path;
            this.libraryDirectory = libraryDirectory;
            this.uberJar = uberJar;
        }

        public Path getPath() {
            return path;
        }

        public Path getLibraryDir() {
            return libraryDirectory;
        }

        public boolean isUberJar() {
            return uberJar;
        }
    }

    public static final class MissingGetResults {
    }

    public static final class NonCollectionResults {
        public String getResults() {
            return "bad";
        }
    }

    public static final class NonPathJarResult {
        public String getPath() {
            return "bad";
        }

        public Path getLibraryDir() {
            return null;
        }

        public boolean isUberJar() {
            return false;
        }
    }

    public static final class NonBooleanJarResult {
        public Path getPath() {
            return Path.of("/repo/app.jar");
        }

        public Path getLibraryDir() {
            return null;
        }

        public String isUberJar() {
            return "bad";
        }
    }

    public static final class FailingAugmentResult {
        public List<Object> getResults() {
            throw new IllegalStateException("boom");
        }
    }
}
