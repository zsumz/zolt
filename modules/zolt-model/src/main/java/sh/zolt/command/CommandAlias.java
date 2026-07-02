package sh.zolt.command;

import java.util.List;

public record CommandAlias(
        String name,
        List<String> argv) {
    public CommandAlias {
        argv = argv == null || argv.isEmpty() ? List.of() : List.copyOf(argv);
    }
}
