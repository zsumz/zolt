package sh.zolt.junit;

import sh.zolt.test.TestSelection;
import sh.zolt.test.TestSelectionCodec;
import sh.zolt.test.TestSelectionException;
import java.util.List;

/**
 * The single, shared encoder/decoder for the five {@link TestSelection} dimensions on a worker
 * {@code RUN} frame. Each dimension is one tagged field; an empty dimension is simply omitted.
 *
 * <p>This is the one place the selection shape is written and read, so the parent and worker can
 * never drift, and adding a sixth dimension means adding one field name here — not editing any
 * part-count arithmetic.
 */
final class TestSelectionField {
    private static final String CLASSES = "classes";
    private static final String METHODS = "methods";
    private static final String PATTERNS = "patterns";
    private static final String INCLUDED_TAGS = "includeTags";
    private static final String EXCLUDED_TAGS = "excludeTags";

    private TestSelectionField() {
    }

    static void encode(Frame frame, TestSelection selection) {
        encodeStrings(frame, CLASSES, selection.classSelectors());
        frame.put(METHODS, emptyToNull(TestSelectionCodec.encodeMethods(selection.methodSelectors())));
        encodeStrings(frame, PATTERNS, selection.classNamePatterns());
        encodeStrings(frame, INCLUDED_TAGS, selection.includedTags());
        encodeStrings(frame, EXCLUDED_TAGS, selection.excludedTags());
    }

    static TestSelection decode(Frame frame) {
        try {
            return TestSelection.fromFields(
                    strings(frame, CLASSES, "JUnit worker class selectors"),
                    TestSelectionCodec.decodeMethods("JUnit worker method selectors", frame.optional(METHODS).orElse("")),
                    strings(frame, PATTERNS, "JUnit worker class-name patterns"),
                    strings(frame, INCLUDED_TAGS, "JUnit worker included tags"),
                    strings(frame, EXCLUDED_TAGS, "JUnit worker excluded tags"));
        } catch (IllegalArgumentException | TestSelectionException exception) {
            throw new IllegalArgumentException(
                    "Malformed JUnit worker test selection. " + exception.getMessage(),
                    exception);
        }
    }

    static void encodeStrings(Frame frame, String name, List<String> values) {
        frame.put(name, emptyToNull(TestSelectionCodec.encodeStrings(values)));
    }

    static List<String> events(Frame frame, String name) {
        return TestSelectionCodec.decodeStrings("JUnit worker events", frame.optional(name).orElse(""));
    }

    private static List<String> strings(Frame frame, String name, String label) {
        return TestSelectionCodec.decodeStrings(label, frame.optional(name).orElse(""));
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
