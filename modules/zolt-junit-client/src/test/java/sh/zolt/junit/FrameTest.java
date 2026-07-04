package sh.zolt.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class FrameTest {
    @Test
    void escapesReservedCharactersAndOmitsNullFields() {
        Frame frame = Frame.command("RUN");
        frame.put("value", "tab\tnewline\nreturn\requals=percent%");
        frame.put("empty", "");
        frame.put("missing", null);

        String rendered = frame.render();
        Frame parsed = Frame.parse(rendered);

        assertEquals("RUN\tvalue=tab%09newline%0Areturn%0Dequals%3Dpercent%25\tempty=", rendered);
        assertEquals("tab\tnewline\nreturn\requals=percent%", parsed.require("value", "value"));
        assertTrue(parsed.optional("empty").isEmpty());
        assertTrue(parsed.optional("missing").isEmpty());
    }

    @Test
    void rejectsEmptyFramesAndMissingCommands() {
        IllegalArgumentException nullLine = assertThrows(
                IllegalArgumentException.class,
                () -> Frame.parse(null));
        IllegalArgumentException blankCommand = assertThrows(
                IllegalArgumentException.class,
                () -> Frame.parse("\tid=value"));

        assertTrue(nullLine.getMessage().contains("empty line"), nullLine.getMessage());
        assertTrue(blankCommand.getMessage().contains("missing command verb"), blankCommand.getMessage());
    }

    @Test
    void rejectsMalformedPercentEncoding() {
        IllegalArgumentException truncated = assertThrows(
                IllegalArgumentException.class,
                () -> Frame.parse("RUN\tvalue=abc%"));
        IllegalArgumentException invalidHex = assertThrows(
                IllegalArgumentException.class,
                () -> Frame.parse("RUN\tvalue=%ZZ"));

        assertTrue(truncated.getMessage().contains("malformed percent encoding"), truncated.getMessage());
        assertTrue(invalidHex.getMessage().contains("malformed percent encoding"), invalidHex.getMessage());
    }

    @Test
    void rejectsBlankAndNamelessFieldsWithFrameContext() {
        IllegalArgumentException blankField = assertThrows(
                IllegalArgumentException.class,
                () -> Frame.parse("RUN\t"));
        IllegalArgumentException namelessField = assertThrows(
                IllegalArgumentException.class,
                () -> Frame.parse("RUN\t=value"));

        assertTrue(blankField.getMessage().contains("RUN frame"), blankField.getMessage());
        assertTrue(blankField.getMessage().contains("not name=value"), blankField.getMessage());
        assertTrue(namelessField.getMessage().contains("RUN frame"), namelessField.getMessage());
        assertTrue(namelessField.getMessage().contains("not name=value"), namelessField.getMessage());
    }

    @Test
    void requiredFieldsRejectBlankValuesWithFieldName() {
        Frame frame = Frame.parse("RUN\tid=");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> frame.require("id", "JUnit worker request id"));

        assertTrue(exception.getMessage().contains("RUN frame"), exception.getMessage());
        assertTrue(exception.getMessage().contains("JUnit worker request id"), exception.getMessage());
        assertTrue(exception.getMessage().contains("`id`"), exception.getMessage());
        assertTrue(exception.getMessage().contains("is required"), exception.getMessage());
    }

    @Test
    void rejectsUnexpectedFieldsWithContext() {
        Frame frame = Frame.parse("QUIT\tv=1\tid=request-1\tout=target/test-classes");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> frame.rejectUnexpected("JUnit worker quit request", List.of("v", "id")));

        assertTrue(exception.getMessage().contains("JUnit worker quit request"), exception.getMessage());
        assertTrue(exception.getMessage().contains("unexpected field `out`"), exception.getMessage());
    }
}
