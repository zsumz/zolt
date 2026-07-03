package sh.zolt.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JunitTestProfileCollectorTest {
    @TempDir
    private Path tempDir;

    @Test
    void writesDeterministicEscapedProfileJsonWithContainerCounts() throws Exception {
        Path profile = tempDir.resolve("profile");
        JunitTestProfileCollector collector = new JunitTestProfileCollector(profile);
        ListenerShape listener = (ListenerShape) collector.listener(ListenerShape.class);
        FakeIdentifier betaContainer = FakeIdentifier.container(
                "[engine:junit-jupiter]/[class:com.example.BetaTest]",
                "Beta \"container\"",
                new ClassSource("com.example.BetaTest"));
        FakeIdentifier betaTest = FakeIdentifier.test(
                "[engine:junit-jupiter]/[class:com.example.BetaTest]/[method:runs()]",
                "line\nquote\"tab\tbackslash\\backspace\bformfeed\freturn\rcontrol\u0001",
                new MethodSource("com.example.BetaTest", "runs"));
        FakeIdentifier alphaTest = FakeIdentifier.test(
                "[engine:junit-jupiter]/[class:com.example.AlphaTest]/[method:passes()]",
                "Alpha passes",
                new MethodSource("com.example.AlphaTest", "passes"));

        listener.executionStarted(betaContainer);
        listener.executionFinished(betaContainer, FakeResult.successful());
        listener.executionStarted(betaTest);
        listener.executionFinished(betaTest, FakeResult.successful());
        listener.executionStarted(alphaTest);
        listener.executionFinished(alphaTest, FakeResult.failed());
        collector.write();

        String json = Files.readString(profile.resolve("profile.json"));
        assertTrue(json.contains("\"schemaVersion\": 1"), json);
        assertTrue(json.contains("\"testsFound\": 2"), json);
        assertTrue(json.contains("\"testsSucceeded\": 1"), json);
        assertTrue(json.contains("\"testsFailed\": 1"), json);
        assertTrue(json.contains("\"engineId\": \"junit-jupiter\""), json);
        assertTrue(json.contains(
                "\"displayName\": \"line\\nquote\\\"tab\\tbackslash\\\\backspace\\bformfeed\\freturn\\rcontrol\\u0001\""),
                json);
        assertTrue(json.contains("\"className\": \"com.example.BetaTest\""), json);
        assertTrue(json.contains("\"methodName\": \"runs\""), json);
        assertTrue(json.contains("\"testCount\": 1"), json);
        assertTrue(
                json.indexOf("\"className\": \"com.example.AlphaTest\"")
                        < json.indexOf("\"className\": \"com.example.BetaTest\""),
                json);
    }

    @Test
    void recordsSkippedAbortedAndUnknownSourceEntries() throws Exception {
        Path profile = tempDir.resolve("unknown-source-profile");
        JunitTestProfileCollector collector = new JunitTestProfileCollector(profile);
        ListenerShape listener = (ListenerShape) collector.listener(ListenerShape.class);
        FakeIdentifier skipped = FakeIdentifier.test(
                "[engine:junit-vintage]/[test:skipped]",
                "skipped display",
                new UnknownSource());
        FakeIdentifier aborted = FakeIdentifier.test(
                "no-engine-prefix",
                "aborted display",
                null);
        FakeIdentifier custom = FakeIdentifier.test(
                "[engine:custom]/[test:custom]",
                "custom display",
                new MethodSource("com.example.CustomTest", "customStatus"));

        listener.executionSkipped(skipped, "disabled");
        listener.executionStarted(aborted);
        listener.executionFinished(aborted, FakeResult.aborted());
        listener.executionFinished(custom, FakeResult.custom());
        collector.write();

        String json = Files.readString(profile.resolve("profile.json"));
        assertTrue(json.contains("\"testsFound\": 3"), json);
        assertTrue(json.contains("\"testsSkipped\": 1"), json);
        assertTrue(json.contains("\"testsAborted\": 1"), json);
        assertTrue(json.contains("\"status\": \"skipped\""), json);
        assertTrue(json.contains("\"status\": \"aborted\""), json);
        assertTrue(json.contains("\"status\": \"custom_status\""), json);
        assertTrue(json.contains("\"engineId\": \"junit-vintage\""), json);
        assertTrue(json.contains("\"engineId\": \"\""), json);
        assertTrue(json.contains("\"className\": \"\""), json);
    }

    @Test
    void listenerObjectMethodsUseProxyIdentity() {
        JunitTestProfileCollector collector = new JunitTestProfileCollector(tempDir.resolve("profile"));
        ListenerShape listener = (ListenerShape) collector.listener(ListenerShape.class);
        ListenerShape other = (ListenerShape) collector.listener(ListenerShape.class);

        assertEquals("ZoltTestProfileListener", listener.toString());
        assertTrue(listener.equals(listener));
        assertTrue(!listener.equals(other));
        assertEquals(System.identityHashCode(listener), listener.hashCode());
    }

    interface ListenerShape {
        void executionStarted(Object identifier);

        void executionSkipped(Object identifier, Object reason);

        void executionFinished(Object identifier, Object result);
    }

    public enum FakeStatus {
        SUCCESSFUL,
        ABORTED,
        FAILED,
        CUSTOM_STATUS
    }

    public static final class FakeResult {
        private final FakeStatus status;

        private FakeResult(FakeStatus status) {
            this.status = status;
        }

        static FakeResult successful() {
            return new FakeResult(FakeStatus.SUCCESSFUL);
        }

        static FakeResult aborted() {
            return new FakeResult(FakeStatus.ABORTED);
        }

        static FakeResult failed() {
            return new FakeResult(FakeStatus.FAILED);
        }

        static FakeResult custom() {
            return new FakeResult(FakeStatus.CUSTOM_STATUS);
        }

        public FakeStatus getStatus() {
            return status;
        }
    }

    public static final class FakeIdentifier {
        private final String uniqueId;
        private final String displayName;
        private final Object source;
        private final boolean test;
        private final boolean container;

        private FakeIdentifier(
                String uniqueId,
                String displayName,
                Object source,
                boolean test,
                boolean container) {
            this.uniqueId = uniqueId;
            this.displayName = displayName;
            this.source = source;
            this.test = test;
            this.container = container;
        }

        static FakeIdentifier test(String uniqueId, String displayName, Object source) {
            return new FakeIdentifier(uniqueId, displayName, source, true, false);
        }

        static FakeIdentifier container(String uniqueId, String displayName, Object source) {
            return new FakeIdentifier(uniqueId, displayName, source, false, true);
        }

        public String getUniqueId() {
            return uniqueId;
        }

        public Optional<Object> getSource() {
            return Optional.ofNullable(source);
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean isTest() {
            return test;
        }

        public boolean isContainer() {
            return container;
        }
    }
}

final class MethodSource {
    private final String className;
    private final String methodName;

    MethodSource(String className, String methodName) {
        this.className = className;
        this.methodName = methodName;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }
}

final class ClassSource {
    private final String className;

    ClassSource(String className) {
        this.className = className;
    }

    public String getClassName() {
        return className;
    }
}

final class UnknownSource {
}
