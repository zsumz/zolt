package com.zolt.cli.command;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.function.Supplier;

final class CommandServiceNullGuardAssertions {
    private CommandServiceNullGuardAssertions() {
    }

    @SafeVarargs
    static void assertRejectsNullCollaborators(Supplier<Object>... factories) {
        for (Supplier<Object> factory : factories) {
            assertThrows(NullPointerException.class, factory::get);
        }
    }
}
