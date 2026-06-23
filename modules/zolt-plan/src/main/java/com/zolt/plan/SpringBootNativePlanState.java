package com.zolt.plan;

import com.zolt.lockfile.ZoltLockfile;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

record SpringBootNativePlanState(
        Path projectRoot,
        Path lockfilePath,
        Optional<ZoltLockfile> lockfile,
        Optional<String> lockfileError,
        Path aotRoot,
        Path sources,
        Path classes,
        Path resources,
        Path nativeMetadata,
        List<Path> generatedSources,
        List<Path> generatedClasses,
        List<Path> reflectionMetadata,
        List<Path> reachabilityMetadata,
        Path packageJar,
        Path outputDirectory,
        Path image,
        Path nativeImageLog,
        Path springAotEvidence,
        Path nativeImageExecutable,
        boolean nativeImageAvailable,
        Optional<String> springBootVersion,
        SpringBootNativeAotFreshness aotFreshness) {
}
