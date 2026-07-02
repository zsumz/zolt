package com.zolt.maven.repository;

import static com.zolt.maven.repository.RepositoryHttpRequests.diagnosticUri;
import static com.zolt.maven.repository.RepositoryHttpRequests.fetchRequest;
import static com.zolt.maven.repository.RepositoryHttpRequests.uploadRequest;

import com.zolt.maven.ArtifactDescriptor;
import com.zolt.maven.Coordinate;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Optional;

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
        return fetchPom(repositoryBaseUri, coordinate, RepositoryAuthentication.none());
    }

    public RepositoryArtifact fetchPom(
            URI repositoryBaseUri,
            Coordinate coordinate,
            Optional<RepositoryAuthentication> authentication) {
        return fetchPom(repositoryBaseUri, coordinate, authentication, RepositoryDownloadListener.NOOP);
    }

    public RepositoryArtifact fetchPom(
            URI repositoryBaseUri,
            Coordinate coordinate,
            Optional<RepositoryAuthentication> authentication,
            RepositoryDownloadListener downloadListener) {
        return fetch(
                repositoryBaseUri,
                new ArtifactDescriptor(coordinate, Optional.empty(), "pom"),
                pathBuilder.pomPath(coordinate),
                authentication,
                downloadListener);
    }

    public RepositoryArtifact fetchJar(URI repositoryBaseUri, Coordinate coordinate) {
        return fetchJar(repositoryBaseUri, coordinate, RepositoryAuthentication.none());
    }

    public RepositoryArtifact fetchJar(
            URI repositoryBaseUri,
            Coordinate coordinate,
            Optional<RepositoryAuthentication> authentication) {
        return fetchJar(repositoryBaseUri, coordinate, authentication, RepositoryDownloadListener.NOOP);
    }

    public RepositoryArtifact fetchJar(
            URI repositoryBaseUri,
            Coordinate coordinate,
            Optional<RepositoryAuthentication> authentication,
            RepositoryDownloadListener downloadListener) {
        return fetchArtifact(repositoryBaseUri, ArtifactDescriptor.jar(coordinate), authentication, downloadListener);
    }

    public RepositoryArtifact fetchArtifact(URI repositoryBaseUri, ArtifactDescriptor descriptor) {
        return fetchArtifact(repositoryBaseUri, descriptor, RepositoryAuthentication.none());
    }

    public RepositoryArtifact fetchArtifact(
            URI repositoryBaseUri,
            ArtifactDescriptor descriptor,
            Optional<RepositoryAuthentication> authentication) {
        return fetchArtifact(repositoryBaseUri, descriptor, authentication, RepositoryDownloadListener.NOOP);
    }

    public RepositoryArtifact fetchArtifact(
            URI repositoryBaseUri,
            ArtifactDescriptor descriptor,
            Optional<RepositoryAuthentication> authentication,
            RepositoryDownloadListener downloadListener) {
        return fetch(
                repositoryBaseUri,
                descriptor,
                pathBuilder.artifactPath(descriptor),
                authentication,
                downloadListener);
    }

    public void uploadPom(URI repositoryBaseUri, Coordinate coordinate, Path source) {
        uploadPom(repositoryBaseUri, coordinate, source, RepositoryAuthentication.none());
    }

    public void uploadPom(
            URI repositoryBaseUri,
            Coordinate coordinate,
            Path source,
            Optional<RepositoryAuthentication> authentication) {
        upload(repositoryBaseUri, coordinate, pathBuilder.pomPath(coordinate), source, authentication);
    }

    public void uploadArtifact(URI repositoryBaseUri, ArtifactDescriptor descriptor, Path source) {
        uploadArtifact(repositoryBaseUri, descriptor, source, RepositoryAuthentication.none());
    }

    public void uploadArtifact(
            URI repositoryBaseUri,
            ArtifactDescriptor descriptor,
            Path source,
            Optional<RepositoryAuthentication> authentication) {
        upload(repositoryBaseUri, descriptor.coordinate(), pathBuilder.artifactPath(descriptor), source, authentication);
    }

    private RepositoryArtifact fetch(
            URI repositoryBaseUri,
            ArtifactDescriptor descriptor,
            String path,
            Optional<RepositoryAuthentication> authentication,
            RepositoryDownloadListener downloadListener) {
        Coordinate coordinate = descriptor.coordinate();
        URI artifactUri = artifactUri(repositoryBaseUri, path);
        HttpRequest request = fetchRequest(artifactUri, authentication, httpPolicy);

        IOException lastIoException = null;
        for (int attempt = 1; attempt <= httpPolicy.maxAttempts(); attempt++) {
            HttpResponse<byte[]> response;
            try {
                response = httpClient.send(request, new CountingByteArrayBodyHandler(descriptor, downloadListener));
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
                                + diagnosticUri(artifactUri)
                                + ". Try again.",
                        exception);
            }

            if (response.statusCode() == 404) {
                throw new RepositoryMissingArtifactException(
                        "Could not find " + coordinate + " at " + diagnosticUri(artifactUri) + ".");
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

    private void upload(
            URI repositoryBaseUri,
            Coordinate coordinate,
            String path,
            Path source,
            Optional<RepositoryAuthentication> authentication) {
        URI artifactUri = artifactUri(repositoryBaseUri, path);
        HttpRequest.BodyPublisher bodyPublisher;
        try {
            bodyPublisher = HttpRequest.BodyPublishers.ofFile(source);
        } catch (IOException exception) {
            throw new RepositoryClientException(
                    "Could not read upload source for "
                            + coordinate
                            + " at "
                            + source
                            + ". Check that the file exists and is readable.",
                    exception);
        }
        HttpRequest request = uploadRequest(artifactUri, bodyPublisher, authentication, httpPolicy);

        IOException lastIoException = null;
        for (int attempt = 1; attempt <= httpPolicy.maxAttempts(); attempt++) {
            HttpResponse<byte[]> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            } catch (IOException exception) {
                lastIoException = exception;
                if (!hasAttemptsRemaining(attempt)) {
                    throw uploadException(coordinate, artifactUri, attempt, exception);
                }
                sleepBeforeRetry("uploading", coordinate, artifactUri, attempt);
                continue;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RepositoryClientException(
                        "Upload interrupted while publishing "
                                + coordinate
                                + " to "
                                + diagnosticUri(artifactUri)
                                + ". Try again.",
                        exception);
            }

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return;
            }
            if (!transientStatus(response.statusCode()) || !hasAttemptsRemaining(attempt)) {
                throw statusException("uploading", coordinate, artifactUri, response.statusCode(), attempt);
            }
            sleepBeforeRetry("uploading", coordinate, artifactUri, attempt);
        }

        throw uploadException(coordinate, artifactUri, httpPolicy.maxAttempts(), lastIoException);
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
        return statusException("fetching", coordinate, artifactUri, statusCode, attempts);
    }

    private RepositoryClientException statusException(
            String operation,
            Coordinate coordinate,
            URI artifactUri,
            int statusCode,
            int attempts) {
        return new RepositoryClientException(
                "Repository returned HTTP "
                        + statusCode
                        + " while "
                        + operation
                        + " "
                        + coordinate
                        + " at "
                        + diagnosticUri(artifactUri)
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
                        + diagnosticUri(artifactUri)
                        + attemptsMessage(attempts)
                        + ". Check your network, proxy, or repository URL and try again.",
                        cause);
    }

    private static RepositoryClientException uploadException(
            Coordinate coordinate,
            URI artifactUri,
            int attempts,
            IOException cause) {
        return new RepositoryClientException(
                "Could not upload "
                        + coordinate
                        + " to "
                        + diagnosticUri(artifactUri)
                        + attemptsMessage(attempts)
                        + ". Check your network, proxy, repository URL, and publish permissions, then try again.",
                cause);
    }

    private static String attemptsMessage(int attempts) {
        if (attempts <= 1) {
            return "";
        }
        return " after " + attempts + " attempts";
    }

    private void sleepBeforeRetry(Coordinate coordinate, URI artifactUri, int attempt) {
        sleepBeforeRetry("fetching", coordinate, artifactUri, attempt);
    }

    private void sleepBeforeRetry(String operation, Coordinate coordinate, URI artifactUri, int attempt) {
        if (httpPolicy.retryBackoff().isZero()) {
            return;
        }
        try {
            Thread.sleep(httpPolicy.retryBackoff().toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RepositoryClientException(
                    "Repository request interrupted while retrying "
                            + operation
                            + " "
                            + coordinate
                            + " from "
                            + diagnosticUri(artifactUri)
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
