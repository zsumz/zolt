package com.zolt.quarkus.production;

import com.zolt.quarkus.QuarkusAugmentationException;
import com.zolt.quarkus.bootstrap.QuarkusBootstrapDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;

public final class QuarkusProductionOutputVerifier {
    public void verify(QuarkusBootstrapDescriptor descriptor, QuarkusProductionApplicationSummary summary) {
        if (descriptor == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap descriptor is required.");
        }
        if (summary == null) {
            throw new QuarkusAugmentationException("Quarkus production application summary is required.");
        }

        Path packageDirectory = descriptor.packageDirectory().toAbsolutePath().normalize();
        if (!Files.isDirectory(packageDirectory)) {
            throw new QuarkusAugmentationException(
                    "Quarkus production application did not create package directory at "
                            + packageDirectory
                            + ". Check the Quarkus package output layout.");
        }
        Path runnerJar = summary.jarPath().toAbsolutePath().normalize();
        if (!Files.isRegularFile(runnerJar)) {
            throw new QuarkusAugmentationException(
                    "Quarkus production application did not create runner jar at "
                            + runnerJar
                            + ". Check the Quarkus package output layout.");
        }
        if (summary.libraryDirectory() != null) {
            Path libraryDirectory = summary.libraryDirectory().toAbsolutePath().normalize();
            if (!Files.isDirectory(libraryDirectory)) {
                throw new QuarkusAugmentationException(
                        "Quarkus production application reported library directory "
                                + libraryDirectory
                                + " but it does not exist. Check the Quarkus package output layout.");
            }
        }
    }
}
