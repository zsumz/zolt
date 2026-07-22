package sh.zolt.maven.repository;

import static sh.zolt.maven.repository.RepositoryHttpRequests.diagnosticUri;

import java.io.IOException;
import java.net.URI;

/** Builds actionable, credential-safe messages for repository download and upload failures. */
final class RepositoryTransferErrors {
    private RepositoryTransferErrors() {
    }

    static RepositoryClientException status(String subject, URI artifactUri, int statusCode, int attempts) {
        return status("fetching", subject, artifactUri, statusCode, attempts);
    }

    static RepositoryClientException status(
            String operation,
            String subject,
            URI artifactUri,
            int statusCode,
            int attempts) {
        return new RepositoryClientException(
                "Repository returned HTTP "
                        + statusCode
                        + " while "
                        + operation
                        + " "
                        + subject
                        + " at "
                        + diagnosticUri(artifactUri)
                        + attemptsMessage(attempts)
                        + ". Try again or check the repository URL.");
    }

    static RepositoryClientException download(String subject, URI artifactUri, int attempts, IOException cause) {
        return new RepositoryClientException(
                "Could not download "
                        + subject
                        + " from "
                        + diagnosticUri(artifactUri)
                        + attemptsMessage(attempts)
                        + ". Check your network, proxy, or repository URL and try again.",
                cause);
    }

    static RepositoryClientException upload(String subject, URI artifactUri, int attempts, IOException cause) {
        return new RepositoryClientException(
                "Could not upload "
                        + subject
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
}
