package com.zolt.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import org.junit.jupiter.api.Test;

final class MavenRepositoryClientDiagnosticTest {
    @Test
    void diagnosticUriRedactsUrlUserinfo() {
        String diagnostic = RepositoryHttpRequests.diagnosticUri(
                URI.create("https://repo-user:super-secret@repo.example.test/maven2/com/example/app.pom"));

        assertEquals("https://***@repo.example.test/maven2/com/example/app.pom", diagnostic);
        assertFalse(diagnostic.contains("repo-user"));
        assertFalse(diagnostic.contains("super-secret"));
    }
}
