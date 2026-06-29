package com.zolt.resolve.materialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.maven.Coordinate;
import com.zolt.maven.RepositoryArtifact;
import com.zolt.maven.RepositoryAuthentication;
import com.zolt.maven.RepositoryMissingArtifactException;
import com.zolt.resolve.ResolveException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class RepositoryFetchCoordinatorTest {
    private final RepositoryFetchCoordinator coordinator = new RepositoryFetchCoordinator();

    @Test
    void triesRepositoriesInOrderUntilFetchSucceeds() {
        List<RepositoryAccess> repositories = List.of(
                access("https://repo.example/one"),
                access("https://repo.example/two"));
        RepositoryArtifact expected = artifact("https://repo.example/two");
        List<URI> attempted = new ArrayList<>();

        RepositoryArtifact artifact = coordinator.fetch(repositories, access -> {
            attempted.add(access.uri());
            if (access.uri().toString().contains("one")) {
                throw new RepositoryMissingArtifactException("missing from one");
            }
            return expected;
        });

        assertSame(expected, artifact);
        assertEquals(List.of(
                URI.create("https://repo.example/one"),
                URI.create("https://repo.example/two")), attempted);
    }

    @Test
    void throwsLastMissingArtifactWhenAllRepositoriesMiss() {
        RepositoryMissingArtifactException first = new RepositoryMissingArtifactException("missing from one");
        RepositoryMissingArtifactException second = new RepositoryMissingArtifactException("missing from two");

        RepositoryMissingArtifactException exception = assertThrows(
                RepositoryMissingArtifactException.class,
                () -> coordinator.fetch(
                        List.of(access("https://repo.example/one"), access("https://repo.example/two")),
                        access -> {
                            if (access.uri().toString().contains("one")) {
                                throw first;
                            }
                            throw second;
                        }));

        assertSame(second, exception);
    }

    @Test
    void reportsNoRepositoriesClearly() {
        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> coordinator.fetch(List.of(), access -> artifact(access.uri().toString())));

        assertTrue(exception.getMessage().contains("No repositories are configured in zolt.toml"));
        assertTrue(exception.getMessage().contains("Add [repositories]"));
    }

    private static RepositoryAccess access(String uri) {
        return new RepositoryAccess(URI.create(uri), Optional.<RepositoryAuthentication>empty());
    }

    private static RepositoryArtifact artifact(String source) {
        return new RepositoryArtifact(
                new Coordinate("com.example", "app", Optional.of("1.0.0")),
                "com/example/app/1.0.0/app-1.0.0.jar",
                URI.create(source),
                new byte[] {1});
    }
}
