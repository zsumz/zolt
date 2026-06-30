package com.zolt.maven.repository;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Optional;

final class RepositoryHttpRequests {
    private RepositoryHttpRequests() {
    }

    static HttpRequest fetchRequest(
            URI artifactUri,
            Optional<RepositoryAuthentication> authentication,
            RepositoryHttpPolicy httpPolicy) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(artifactUri)
                    .timeout(httpPolicy.requestTimeout())
                    .GET();
            authentication.ifPresent(value -> requestBuilder.header("Authorization", value.basicAuthorizationHeader()));
            return requestBuilder.build();
        } catch (IllegalArgumentException exception) {
            throw invalidRepositoryUri("download from", artifactUri, exception);
        }
    }

    static HttpRequest uploadRequest(
            URI artifactUri,
            HttpRequest.BodyPublisher bodyPublisher,
            Optional<RepositoryAuthentication> authentication,
            RepositoryHttpPolicy httpPolicy) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(artifactUri)
                    .timeout(httpPolicy.requestTimeout())
                    .PUT(bodyPublisher);
            authentication.ifPresent(value -> requestBuilder.header("Authorization", value.basicAuthorizationHeader()));
            return requestBuilder.build();
        } catch (IllegalArgumentException exception) {
            throw invalidRepositoryUri("upload to", artifactUri, exception);
        }
    }

    static String diagnosticUri(URI uri) {
        String userInfo = uri.getRawUserInfo();
        if (userInfo == null || userInfo.isBlank()) {
            return uri.toString();
        }
        return uri.toString().replace(userInfo + "@", "***@");
    }

    private static RepositoryClientException invalidRepositoryUri(
            String operation,
            URI artifactUri,
            IllegalArgumentException cause) {
        return new RepositoryClientException(
                "Could not "
                        + operation
                        + " repository URL "
                        + diagnosticUri(artifactUri)
                        + ". Check the repository URL and remove embedded credentials.",
                cause);
    }
}
