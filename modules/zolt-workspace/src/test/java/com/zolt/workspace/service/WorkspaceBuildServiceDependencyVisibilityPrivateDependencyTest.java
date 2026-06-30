package com.zolt.workspace.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.build.JavacException;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

final class WorkspaceBuildServiceDependencyVisibilityPrivateDependencyTest
        extends WorkspaceBuildServiceDependencyVisibilityTestSupport {

    @Test
    void downstreamMemberCannotCompileAgainstPrivateDependencyOfWorkspaceDependency() throws IOException {
        addJarArtifact(
                "com.example",
                "contract",
                "1.0.0",
                "com.example.contract.Contract",
                """
                package com.example.contract;

                public final class Contract {
                }
                """);
        addJarArtifact(
                "com.example",
                "internal",
                "1.0.0",
                "com.example.internal.Internal",
                """
                package com.example.internal;

                public final class Internal {
                    private Internal() {
                    }

                    public static String value() {
                        return "internal";
                    }
                }
                """);
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["modules/core", "apps/api"]

                [repositories]
                test = "%s"
                """.formatted(baseUri));
        member("modules/core", "core", """

                [api.dependencies]
                "com.example:contract" = "1.0.0"

                [dependencies]
                "com.example:internal" = "1.0.0"
                """);
        source("modules/core/src/main/java/com/acme/core/Core.java", """
                package com.acme.core;

                import com.example.contract.Contract;
                import com.example.internal.Internal;

                public final class Core {
                    private Core() {
                    }

                    public static Contract contract() {
                        Internal.value();
                        return new Contract();
                    }
                }
                """);
        member("apps/api", "api", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);
        source("apps/api/src/main/java/com/acme/api/Api.java", """
                package com.acme.api;

                import com.acme.core.Core;
                import com.example.internal.Internal;

                public final class Api {
                    private Api() {
                    }

                    public static String message() {
                        Core.contract();
                        return Internal.value();
                    }
                }
                """);

        JavacException exception = assertThrows(
                JavacException.class,
                () -> service.build(tempDir.resolve("apps/api"), tempDir.resolve("cache"), false));

        assertTrue(exception.getMessage().contains("javac failed with exit code"));
        assertTrue(exception.getMessage().contains("Api.java"));
        assertTrue(exception.getMessage().contains("com.example.internal"));
        assertTrue(exception.getMessage().contains("move it to [api.dependencies]"));
        assertTrue(Files.exists(tempDir.resolve("modules/core/target/classes/com/acme/core/Core.class")));
        assertFalse(Files.exists(tempDir.resolve("apps/api/target/classes/com/acme/api/Api.class")));
    }
}
