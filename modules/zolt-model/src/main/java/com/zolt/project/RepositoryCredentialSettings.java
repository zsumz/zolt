package com.zolt.project;

public record RepositoryCredentialSettings(String id, String usernameEnv, String passwordEnv) {
    public RepositoryCredentialSettings {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Repository credential id must be non-empty.");
        }
        if (usernameEnv == null || usernameEnv.isBlank()) {
            throw new IllegalArgumentException("Repository credential usernameEnv must be non-empty.");
        }
        if (passwordEnv == null || passwordEnv.isBlank()) {
            throw new IllegalArgumentException("Repository credential passwordEnv must be non-empty.");
        }
    }
}
