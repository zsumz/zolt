package com.zolt.doctor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record JdkStatus(
        Optional<Path> javaHome,
        Optional<Path> java,
        Optional<Path> javac,
        Optional<Path> jar,
        Optional<String> version,
        String requiredVersion) {
    public JdkStatus {
        javaHome = javaHome == null ? Optional.empty() : javaHome;
        java = java == null ? Optional.empty() : java;
        javac = javac == null ? Optional.empty() : javac;
        jar = jar == null ? Optional.empty() : jar;
        version = version == null ? Optional.empty() : version;
    }

    public boolean complete() {
        return java.isPresent() && javac.isPresent() && jar.isPresent();
    }

    public boolean versionMatches() {
        return version.map(value -> value.equals(requiredVersion)).orElse(false);
    }

    public boolean ok() {
        return complete() && versionMatches();
    }

    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (java.isEmpty()) {
            problems.add("Missing `java`. Install a JDK and set JAVA_HOME or add java to PATH.");
        }
        if (javac.isEmpty()) {
            problems.add("Missing `javac`. Install a JDK and set JAVA_HOME or add javac to PATH.");
        }
        if (jar.isEmpty()) {
            problems.add("Missing `jar`. Install a JDK and set JAVA_HOME or add jar to PATH.");
        }
        if (complete() && version.isEmpty()) {
            problems.add("Could not determine Java version. Check that `java -version` runs successfully.");
        }
        if (version.isPresent() && !versionMatches()) {
            problems.add("Java version mismatch. zolt.toml requires "
                    + requiredVersion
                    + " but detected "
                    + version.orElseThrow()
                    + ". Install Java "
                    + requiredVersion
                    + " or update [project].java.");
        }
        return List.copyOf(problems);
    }
}
