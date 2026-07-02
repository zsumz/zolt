package sh.zolt.explain.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class GradleSourceCommentsTest {
    @Test
    void stripsRealBlockAndLineCommentsOutsideStrings() {
        String content = """
                plugins {
                    id 'java' // line comment with "quotes"
                    /* block comment with 'quotes' and // markers */
                    repositories { mavenCentral() }
                }
                """;

        assertEquals(
                """
                plugins {
                    id 'java'

                    repositories { mavenCentral() }
                }
                """,
                GradleSourceComments.stripComments(content));
    }

    @Test
    void preservesCommentMarkersInsideSingleAndDoubleQuotedStrings() {
        String content = """
                tasks.named('javadoc') {
                    exclude 'io/reactivex/rxjava4/**'
                }
                test {
                    include "**/ExampleTest.class"
                    systemProperty 'message', 'keep // marker'
                    systemProperty "url", "https://repo.example.com/releases"
                }
                """;

        assertEquals(content, GradleSourceComments.stripComments(content));
    }

    @Test
    void preservesCommentMarkersInsideTripleQuotedStrings() {
        String content = "def groovy = '''line /* not a comment */\n"
                + "still // string\n"
                + "tail '''\n"
                + "val kotlin = \"\"\"**/ExampleTest.class\n"
                + "https://repo.example.com/releases\n"
                + "\"\"\"\n";

        assertEquals(content, GradleSourceComments.stripComments(content));
    }

    @Test
    void preservesEscapedQuotesBeforeCommentMarkersInsideStrings() {
        String content = """
                def doubleQuoted = "escaped quote \\" // still a string"
                def singleQuoted = 'escaped quote \\' /* still a string */'
                tasks.register('bundle') // real comment
                """;

        assertEquals(
                """
                def doubleQuoted = "escaped quote \\" // still a string"
                def singleQuoted = 'escaped quote \\' /* still a string */'
                tasks.register('bundle')
                """,
                GradleSourceComments.stripComments(content));
    }

    @Test
    void leavesCommentFreeContentByteIdentical() {
        String content = """
                plugins {
                    id 'java'
                    id("application")
                }

                dependencies {
                    implementation 'org.slf4j:slf4j-api:2.0.16'
                }
                """;

        assertEquals(content, GradleSourceComments.stripComments(content));
    }
}
