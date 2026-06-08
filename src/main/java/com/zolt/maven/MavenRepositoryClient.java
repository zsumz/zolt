package com.zolt.maven;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class MavenRepositoryClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final MavenRepositoryPathBuilder pathBuilder;

    public MavenRepositoryClient() {
        this(HttpClient.newHttpClient(), new MavenRepositoryPathBuilder());
    }

    MavenRepositoryClient(HttpClient httpClient, MavenRepositoryPathBuilder pathBuilder) {
        this.httpClient = httpClient;
        this.pathBuilder = pathBuilder;
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
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException exception) {
            throw new RepositoryClientException(
                    "Could not download "
                            + coordinate
                            + " from "
                            + artifactUri
                            + ". Check your network, proxy, or repository URL and try again.",
                    exception);
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
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RepositoryClientException(
                    "Repository returned HTTP "
                            + response.statusCode()
                            + " for "
                            + coordinate
                            + " at "
                            + artifactUri
                            + ". Try again or check the repository URL.");
        }

        return new RepositoryArtifact(coordinate, path, artifactUri, response.body());
    }

    private static URI artifactUri(URI repositoryBaseUri, String path) {
        String base = repositoryBaseUri.toString();
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        return URI.create(base).resolve(path);
    }
}
