package sh.zolt.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.test.TestSelection;
import sh.zolt.test.TestSelectionException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class JunitWorkerSelectionModelTest {
    @Test
    void workerSelectionFieldsNormalizeNullInputsAndDefensivelyCopyLists() {
        List<String> classes = new ArrayList<>(List.of("com.example.FastTest"));
        List<TestSelection.MethodSelector> methods = new ArrayList<>(
                List.of(new TestSelection.MethodSelector("com.example.FastTest", "runs")));
        List<String> patterns = new ArrayList<>(List.of("*FastTest"));
        List<String> includedTags = new ArrayList<>(List.of("fast"));
        List<String> excludedTags = new ArrayList<>(List.of("slow"));

        TestSelection selection = TestSelection.fromFields(
                classes,
                methods,
                patterns,
                includedTags,
                excludedTags);
        TestSelection empty = TestSelection.fromFields(null, null, null, null, null);
        classes.add("com.example.LateTest");
        methods.add(new TestSelection.MethodSelector("com.example.LateTest", "runs"));
        patterns.add("*LateTest");
        includedTags.add("late");
        excludedTags.add("flaky");

        assertEquals(List.of("com.example.FastTest"), selection.classSelectors());
        assertEquals(List.of(new TestSelection.MethodSelector("com.example.FastTest", "runs")), selection.methodSelectors());
        assertEquals(List.of("*FastTest"), selection.classNamePatterns());
        assertEquals(List.of("fast"), selection.includedTags());
        assertEquals(List.of("slow"), selection.excludedTags());
        assertEquals(3, selection.explicitSelectorCount());
        assertEquals(2, selection.tagSelectorCount());
        assertTrue(empty.emptySelection());
    }

    @Test
    void workerSelectionRegexesEscapePatternMetacharactersDeterministically() {
        TestSelection selection = TestSelection.fromFields(
                List.of(),
                List.of(),
                List.of("ServiceTest", "com.example.?ser[Spec]+$"),
                List.of(),
                List.of());

        assertEquals(
                List.of(".*ServiceTest", "com\\.example\\..ser\\[Spec\\]\\+\\$"),
                selection.classNameRegexPatterns());
    }

    @Test
    void workerSelectionFieldsRejectMalformedClassAndMethodSelectors() {
        List<TestSelection.MethodSelector> nullMethodSelector = new ArrayList<>();
        nullMethodSelector.add(null);
        TestSelectionException badClass = assertThrows(
                TestSelectionException.class,
                () -> TestSelection.fromFields(List.of("not valid"), List.of(), List.of(), List.of(), List.of()));
        TestSelectionException nullMethod = assertThrows(
                TestSelectionException.class,
                () -> TestSelection.fromFields(
                        List.of(),
                        nullMethodSelector,
                        List.of(),
                        List.of(),
                        List.of()));
        TestSelectionException badMethodName = assertThrows(
                TestSelectionException.class,
                () -> TestSelection.fromFields(
                        List.of(),
                        List.of(new TestSelection.MethodSelector("com.example.FastTest", "not valid")),
                        List.of(),
                        List.of(),
                        List.of()));

        assertTrue(badClass.getMessage().contains("fully qualified Java class name"), badClass.getMessage());
        assertTrue(nullMethod.getMessage().contains("Invalid --test method selector"), nullMethod.getMessage());
        assertTrue(badMethodName.getMessage().contains("Use com.example.UserServiceTest#methodName"), badMethodName.getMessage());
    }

    @Test
    void workerSelectionFieldsRejectMalformedPatternsAndTags() {
        TestSelectionException blankPattern = assertThrows(
                TestSelectionException.class,
                () -> TestSelection.fromFields(List.of(), List.of(), List.of(" "), List.of(), List.of()));
        TestSelectionException patternWithMethod = assertThrows(
                TestSelectionException.class,
                () -> TestSelection.fromFields(
                        List.of(),
                        List.of(),
                        List.of("com.example.FastTest#runs"),
                        List.of(),
                        List.of()));
        TestSelectionException includedTagWithComma = assertThrows(
                TestSelectionException.class,
                () -> TestSelection.fromFields(List.of(), List.of(), List.of(), List.of("fast,unit"), List.of()));
        TestSelectionException excludedTagWithWhitespace = assertThrows(
                TestSelectionException.class,
                () -> TestSelection.fromFields(List.of(), List.of(), List.of(), List.of(), List.of("slow tests")));

        assertTrue(blankPattern.getMessage().contains("--tests requires a non-empty value"), blankPattern.getMessage());
        assertTrue(patternWithMethod.getMessage().contains("Use --test"), patternWithMethod.getMessage());
        assertTrue(includedTagWithComma.getMessage().contains("Tags must not contain"), includedTagWithComma.getMessage());
        assertTrue(excludedTagWithWhitespace.getMessage().contains("Tags must not contain"), excludedTagWithWhitespace.getMessage());
    }

    @Test
    void cliSelectionRejectsAmbiguousWorkerSelectorsBeforeEncoding() {
        TestSelectionException wildcardInTest = assertThrows(
                TestSelectionException.class,
                () -> TestSelection.fromCli(List.of("*ServiceTest"), List.of(), List.of(), List.of()));
        TestSelectionException multipleHashes = assertThrows(
                TestSelectionException.class,
                () -> TestSelection.fromCli(List.of("com.example.FastTest#runs#again"), List.of(), List.of(), List.of()));
        TestSelectionException missingClass = assertThrows(
                TestSelectionException.class,
                () -> TestSelection.fromCli(List.of("#runs"), List.of(), List.of(), List.of()));
        TestSelectionException badExcludedTag = assertThrows(
                TestSelectionException.class,
                () -> TestSelection.fromCli(List.of(), List.of(), List.of(), List.of("slow,flaky")));

        assertTrue(wildcardInTest.getMessage().contains("Use --tests for class-name patterns"), wildcardInTest.getMessage());
        assertTrue(multipleHashes.getMessage().contains("must use one #"), multipleHashes.getMessage());
        assertTrue(missingClass.getMessage().contains("--test requires a non-empty value"), missingClass.getMessage());
        assertTrue(badExcludedTag.getMessage().contains("Tags must not contain"), badExcludedTag.getMessage());
    }

    @Test
    void protocolWrapsMalformedWorkerSelectionDiagnostics() {
        IllegalArgumentException badClass = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.parseRequest(
                        "RUN\tv=1\tid=request-1\tout=target/test-classes\tclasses=not valid"));
        IllegalArgumentException badTag = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.parseRequest(
                        "RUN\tv=1\tid=request-1\tout=target/test-classes\tincludeTags=fast%252Cunit"));

        assertTrue(badClass.getMessage().contains("Malformed JUnit worker test selection"), badClass.getMessage());
        assertTrue(badClass.getMessage().contains("fully qualified Java class name"), badClass.getMessage());
        assertTrue(badTag.getMessage().contains("Malformed JUnit worker test selection"), badTag.getMessage());
        assertTrue(badTag.getMessage().contains("Tags must not contain"), badTag.getMessage());
    }
}
