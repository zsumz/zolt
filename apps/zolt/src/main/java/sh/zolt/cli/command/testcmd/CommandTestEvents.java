package sh.zolt.cli.command.testcmd;

import sh.zolt.test.runtime.TestRunException;
import sh.zolt.project.TestRuntimeSettings;
import java.util.ArrayList;
import java.util.List;

public final class CommandTestEvents {
    private CommandTestEvents() {
    }

    public static List<String> validated(List<String> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        List<String> validated = new ArrayList<>();
        for (String event : events) {
            try {
                TestRuntimeSettings.validateEvent("--test-event", event);
            } catch (IllegalArgumentException exception) {
                throw new TestRunException(exception.getMessage(), exception);
            }
            if (!validated.contains(event)) {
                validated.add(event);
            }
        }
        return List.copyOf(validated);
    }
}
