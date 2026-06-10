package com.zolt.maven;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public final class MavenRepositoryClient {
    private final HttpClient httpClient;
    private final MavenRepositoryPathBuilder pathBuilder;
    private final RepositoryHttpPolicy httpPolicy;

    public MavenRepositoryClient() {
        this(HttpClient.newHttpClient(), new MavenRepositoryPathBuilder(), RepositoryHttpPolicy.defaults());
    }

    MavenRepositoryClient(HttpClient httpClient, MavenRepositoryPathBuilder pathBuilder) {
        this(httpClient, pathBuilder, RepositoryHttpPolicy.defaults());
    }

    MavenRepositoryClient(
            HttpClient httpClient,
            MavenRepositoryPathBuilder pathBuilder,
            RepositoryHttpPolicy httpPolicy) {
        this.httpClient = httpClient;
        this.pathBuilder = pathBuilder;
        this.httpPolicy = httpPolicy;
    }

    public RepositoryArtifact fetchPom(URI repositoryBaseUri, Coordinate coordinate) {
        return fetch(repositoryBaseUri, coordinate, pathBuilder.pomPath(coordinate));
    }

    public RepositoryArtifact fetchJar(URI repositoryBaseUri, Coordinate coordinate) {
        return fetchArtifact(repositoryBaseUri, ArtifactDescriptor.jar(coordinate));
    }

    public RepositoryArtifact fetchArtifact(URI repositoryBaseUri, ArtifactDescriptor descriptor) {
        return fetch(repositoryBaseUri, descriptor.coordinate(), pathBuilder.artifactPath(descriptor));
    }

    private RepositoryArtifact fetch(URI repositoryBaseUri, Coordinate coordinate, String path) {
        URI artifactUri = artifactUri(repositoryBaseUri, path);
        HttpRequest request = HttpRequest.newBuilder(artifactUri)
                .timeout(httpPolicy.requestTimeout())
                .GET()
                .build();

        IOException lastIoException = null;
        for (int attempt = 1; attempt <= httpPolicy.maxAttempts(); attempt++) {
            HttpResponse<byte[]> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            } catch (IOException exception) {
                lastIoException = exception;
                if (!hasAttemptsRemaining(attempt)) {
                    throw downloadException(coordinate, artifactUri, attempt, exception);
                }
                sleepBeforeRetry(coordinate, artifactUri, attempt);
                continue;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RepositoryClientException(
                        "Download interrupted while fetching "
                                + coordinate
                                + " from "
                                + artifactUri
                                + ". Try again.",
                        exception);
            }

            if (response.statusCode() == 404) {
                throw new RepositoryMissingArtifactException(
                        "Could not find "
                                + coordinate
                                + " at "
                                + artifactUri
                                + ". Check the group, artifact, version, and repository URL.");
            }
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new RepositoryArtifact(coordinate, path, artifactUri, response.body());
            }
            if (!transientStatus(response.statusCode()) || !hasAttemptsRemaining(attempt)) {
                throw statusException(coordinate, artifactUri, response.statusCode(), attempt);
            }
            sleepBeforeRetry(coordinate, artifactUri, attempt);
        }

        throw downloadException(coordinate, artifactUri, httpPolicy.maxAttempts(), lastIoException);
    }

    private boolean hasAttemptsRemaining(int attempt) {
        return attempt < httpPolicy.maxAttempts();
    }

    private static boolean transientStatus(int statusCode) {
        return statusCode == 429 || (statusCode >= 500 && statusCode <= 599);
    }

    private RepositoryClientException statusException(
            Coordinate coordinate,
            URI artifactUri,
            int statusCode,
            int attempts) {
        return new RepositoryClientException(
                "Repository returned HTTP "
                        + statusCode
                        + " for "
                        + coordinate
                        + " at "
                        + artifactUri
                        + attemptsMessage(attempts)
                        + ". Try again or check the repository URL.");
    }

    private static RepositoryClientException downloadException(
            Coordinate coordinate,
            URI artifactUri,
            int attempts,
            IOException cause) {
        return new RepositoryClientException(
                "Could not download "
                        + coordinate
                        + " from "
                        + artifactUri
                        + attemptsMessage(attempts)
                        + ". Check your network, proxy, or repository URL and try again.",
                cause);
    }

    private static String attemptsMessage(int attempts) {
        if (attempts <= 1) {
            return "";
        }
        return " after " + attempts + " attempts";
    }

    private void sleepBeforeRetry(Coordinate coordinate, URI artifactUri, int attempt) {
        if (httpPolicy.retryBackoff().isZero()) {
            return;
        }
        try {
            Thread.sleep(httpPolicy.retryBackoff().toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RepositoryClientException(
                    "Download interrupted while retrying "
                            + coordinate
                            + " from "
                            + artifactUri
                            + " after attempt "
                            + attempt
                            + ". Try again.",
                    exception);
        }
    }

    private static URI artifactUri(URI repositoryBaseUri, String path) {
        String base = repositoryBaseUri.toString();
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        return URI.create(base).resolve(path);
    }
}
