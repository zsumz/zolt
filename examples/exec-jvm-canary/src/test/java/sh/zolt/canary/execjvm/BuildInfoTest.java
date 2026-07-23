package sh.zolt.canary.execjvm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Properties;
import org.junit.jupiter.api.Test;

final class BuildInfoTest {
    @Test
    void generatedBuildInfoResourceIsOnTheClasspath() {
        Properties info = Main.buildInfo();
        assertEquals("1.4.2", info.getProperty("canary.version"));
        assertEquals("exec-project", info.getProperty("canary.generator"));
    }
}
