package sh.zolt.config;

public record RepositoryExecutionConfig(int downloadConcurrency, String executionLane) {}
