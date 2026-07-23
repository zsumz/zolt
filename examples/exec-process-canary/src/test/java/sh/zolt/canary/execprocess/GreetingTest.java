package sh.zolt.canary.execprocess;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class GreetingTest {
    @Test
    void processGeneratedSourceCompilesAndResourceIsPackaged() {
        assertEquals("Hello from exec-process-canary", Main.greeting());
        assertEquals("Hello from exec-process-canary", Main.resource().getProperty("canary.message"));
        assertEquals("exec-process", Main.resource().getProperty("canary.source"));
    }
}
