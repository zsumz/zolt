package sh.zolt.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class TestSelectionCodecTest {
    @Test
    void roundTripsStringValuesWithSeparatorsAndPercentCharacters() {
        List<String> values = List.of("com.example.FastTest", "tag,with,commas", "literal%percent");

        String encoded = TestSelectionCodec.encodeStrings(values);

        assertEquals("com.example.FastTest,tag%2Cwith%2Ccommas,literal%25percent", encoded);
        assertEquals(values, TestSelectionCodec.decodeStrings("--include-tag", encoded));
        assertEquals(List.of(), TestSelectionCodec.decodeStrings("--include-tag", ""));
        assertEquals("", TestSelectionCodec.encodeStrings(null));
    }

    @Test
    void roundTripsMethodSelectorsThroughTheSameWireFormat() {
        List<TestSelection.MethodSelector> selectors = List.of(
                new TestSelection.MethodSelector("com.example.FastTest", "runs"),
                new TestSelection.MethodSelector("com.example.PercentTest", "uses%Value"));

        String encoded = TestSelectionCodec.encodeMethods(selectors);

        assertEquals("com.example.FastTest#runs,com.example.PercentTest#uses%25Value", encoded);
        assertEquals(selectors, TestSelectionCodec.decodeMethods("--test", encoded));
        assertEquals(List.of(), TestSelectionCodec.decodeMethods("--test", null));
        assertEquals("", TestSelectionCodec.encodeMethods(null));
    }

    @Test
    void rejectsEmptyValuesBeforeEncodingOrAfterDecoding() {
        IllegalArgumentException encodeException = assertThrows(
                IllegalArgumentException.class,
                () -> TestSelectionCodec.encodeStrings(List.of("com.example.FastTest", "")));
        assertEquals("Test selection values must be non-empty.", encodeException.getMessage());

        IllegalArgumentException decodeException = assertThrows(
                IllegalArgumentException.class,
                () -> TestSelectionCodec.decodeStrings("--test", "com.example.FastTest,,com.example.SlowTest"));
        assertEquals("--test contains an empty value.", decodeException.getMessage());
    }

    @Test
    void rejectsMalformedPercentEscapesWithTheCallingLabel() {
        IllegalArgumentException shortEscape = assertThrows(
                IllegalArgumentException.class,
                () -> TestSelectionCodec.decodeStrings("--tests", "com.example.FastTest%"));
        assertEquals("--tests contains malformed percent encoding.", shortEscape.getMessage());

        IllegalArgumentException invalidHex = assertThrows(
                IllegalArgumentException.class,
                () -> TestSelectionCodec.decodeStrings("--tests", "com.example.FastTest%XX"));
        assertEquals("--tests contains malformed percent encoding.", invalidHex.getMessage());
        assertTrue(invalidHex.getCause() instanceof NumberFormatException);
    }

    @Test
    void rejectsMalformedMethodSelectorsAfterDecoding() {
        for (String encoded : List.of(
                "com.example.FastTest",
                "com.example.FastTest#",
                "#runs",
                "com.example.FastTest#runs#again")) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> TestSelectionCodec.decodeMethods("--test", encoded));

            assertEquals("--test contains invalid method selector `" + encoded + "`.", exception.getMessage());
        }
    }
}
