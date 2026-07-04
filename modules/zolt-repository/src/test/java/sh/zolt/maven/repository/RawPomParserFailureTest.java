package sh.zolt.maven.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

final class RawPomParserFailureTest {
    private final RawPomParser parser = new RawPomParser();

    @Test
    void malformedXmlFailsCleanly() {
        RawPomParseException exception = assertThrows(
                RawPomParseException.class,
                () -> parser.parse("<project><artifactId>broken</project>"));

        assertTrue(exception.getMessage().contains("Could not parse POM XML."));
        assertTrue(exception.getMessage().contains("Fix malformed XML"));
    }

    @Test
    void emptyInputFailsAsMalformedPomMetadata() {
        RawPomParseException exception = assertThrows(
                RawPomParseException.class,
                () -> parser.parse(new byte[0]));

        assertTrue(exception.getMessage().contains("Could not parse POM XML."));
        assertTrue(exception.getMessage().contains("Fix malformed XML"));
    }

    @Test
    void nonProjectRootFailsCleanly() {
        RawPomParseException exception = assertThrows(
                RawPomParseException.class,
                () -> parser.parse("<metadata><groupId>com.example</groupId></metadata>"));

        assertEquals(
                "Could not parse POM XML. Expected root <project> element.",
                exception.getMessage());
    }

    @Test
    void doctypeIsRejectedAsMalformedPomMetadata() {
        RawPomParseException exception = assertThrows(
                RawPomParseException.class,
                () -> parser.parse("""
                        <!DOCTYPE project [
                          <!ENTITY secret SYSTEM "file:///etc/passwd">
                        ]>
                        <project>
                          <artifactId>&secret;</artifactId>
                        </project>
                        """));

        assertTrue(exception.getMessage().contains("Could not parse POM XML."));
        assertTrue(exception.getMessage().contains("Fix malformed XML"));
    }

    @Test
    void malformedXmlDoesNotWriteParserNoiseToStderr() {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));

            assertThrows(
                    RawPomParseException.class,
                    () -> parser.parse("<project><artifactId>broken</project>"));
        } finally {
            System.setErr(originalErr);
        }

        assertEquals("", captured.toString(StandardCharsets.UTF_8));
    }

    @Test
    void quietErrorHandlerRethrowsParserWarningsErrorsAndFatalErrors() throws Exception {
        ErrorHandler handler = quietErrorHandler();
        SAXParseException warning = new SAXParseException("warning", null);
        SAXParseException error = new SAXParseException("error", null);
        SAXParseException fatal = new SAXParseException("fatal", null);

        assertSame(warning, assertThrows(SAXParseException.class, () -> handler.warning(warning)));
        assertSame(error, assertThrows(SAXParseException.class, () -> handler.error(error)));
        assertSame(fatal, assertThrows(SAXParseException.class, () -> handler.fatalError(fatal)));
    }

    @Test
    void inputStreamReadFailureIsActionable() {
        IOException readFailure = new IOException("test read failure");
        InputStream failingInput = new InputStream() {
            @Override
            public int read() throws IOException {
                throw readFailure;
            }
        };

        RawPomParseException exception = assertThrows(
                RawPomParseException.class,
                () -> parser.parse(failingInput));

        assertEquals("Could not read POM XML input.", exception.getMessage());
        assertEquals(readFailure, exception.getCause());
    }

    @Test
    void missingRequiredDependencyFieldFailsCleanly() {
        RawPomParseException exception = assertThrows(
                RawPomParseException.class,
                () -> parser.parse("""
                        <project>
                          <artifactId>bad</artifactId>
                          <dependencies>
                            <dependency>
                              <groupId>com.example</groupId>
                            </dependency>
                          </dependencies>
                        </project>
                        """));

        assertEquals(
                "Could not parse POM XML. Missing required <artifactId> in <dependency>.",
                exception.getMessage());
    }

    @Test
    void missingProjectArtifactIdFailsCleanly() {
        RawPomParseException exception = assertThrows(
                RawPomParseException.class,
                () -> parser.parse("""
                        <project>
                          <groupId>com.example</groupId>
                          <version>1.0.0</version>
                        </project>
                        """));

        assertEquals(
                "Could not parse POM XML. Missing required <artifactId> in <project>.",
                exception.getMessage());
    }

    @Test
    void missingRequiredParentFieldFailsCleanly() {
        RawPomParseException exception = assertThrows(
                RawPomParseException.class,
                () -> parser.parse("""
                        <project>
                          <parent>
                            <groupId>com.example</groupId>
                            <version>1.0.0</version>
                          </parent>
                          <artifactId>child</artifactId>
                        </project>
                        """));

        assertEquals(
                "Could not parse POM XML. Missing required <artifactId> in <parent>.",
                exception.getMessage());
    }

    @Test
    void missingParentGroupIdFailsCleanly() {
        RawPomParseException exception = assertThrows(
                RawPomParseException.class,
                () -> parser.parse("""
                        <project>
                          <parent>
                            <artifactId>parent</artifactId>
                            <version>1.0.0</version>
                          </parent>
                          <artifactId>child</artifactId>
                        </project>
                        """));

        assertEquals(
                "Could not parse POM XML. Missing required <groupId> in <parent>.",
                exception.getMessage());
    }

    @Test
    void missingRequiredExclusionFieldFailsCleanly() {
        RawPomParseException exception = assertThrows(
                RawPomParseException.class,
                () -> parser.parse("""
                        <project>
                          <artifactId>bad</artifactId>
                          <dependencies>
                            <dependency>
                              <groupId>com.example</groupId>
                              <artifactId>dep</artifactId>
                              <exclusions>
                                <exclusion>
                                  <artifactId>excluded</artifactId>
                                </exclusion>
                              </exclusions>
                            </dependency>
                          </dependencies>
                        </project>
                        """));

        assertEquals(
                "Could not parse POM XML. Missing required <groupId> in <exclusion>.",
                exception.getMessage());
    }

    private ErrorHandler quietErrorHandler() throws ReflectiveOperationException {
        Class<?> handlerType = java.util.Arrays.stream(RawPomParser.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("QuietErrorHandler"))
                .findFirst()
                .orElseThrow();
        Constructor<?> constructor = handlerType.getDeclaredConstructor();
        constructor.setAccessible(true);
        return (ErrorHandler) constructor.newInstance();
    }
}
