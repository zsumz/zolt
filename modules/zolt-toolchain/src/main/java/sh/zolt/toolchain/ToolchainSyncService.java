package sh.zolt.toolchain;

import sh.zolt.error.ActionableException;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.toolchain.JavaToolchainRequest;
import sh.zolt.toolchain.catalog.BundledJavaToolchainCatalog;
import sh.zolt.toolchain.catalog.JavaToolchainArtifact;
import sh.zolt.toolchain.catalog.JavaToolchainCatalog;
import sh.zolt.toolchain.install.JavaToolchainInstaller;
import sh.zolt.toolchain.lock.LockedJavaToolchain;
import sh.zolt.toolchain.lock.ToolchainLockfileService;
import sh.zolt.toolchain.platform.HostPlatform;
import sh.zolt.toolchain.store.ToolchainStore;
import java.nio.file.Path;
import java.util.Optional;

public final class ToolchainSyncService {
    private final ToolchainConfigReader configReader;
    private final JavaToolchainCatalog catalog;
    private final ToolchainLockfileService lockfiles;
    private final JavaToolchainInstaller installer;

    public ToolchainSyncService() {
        this(
                new ToolchainConfigReader(),
                new BundledJavaToolchainCatalog(),
                new ToolchainLockfileService(),
                new JavaToolchainInstaller());
    }

    ToolchainSyncService(
            ToolchainConfigReader configReader,
            JavaToolchainCatalog catalog,
            ToolchainLockfileService lockfiles,
            JavaToolchainInstaller installer) {
        this.configReader = configReader;
        this.catalog = catalog;
        this.lockfiles = lockfiles;
        this.installer = installer;
    }

    public ToolchainSyncResult sync(
            Path projectRoot,
            ProjectConfig config,
            HostPlatform platform,
            ToolchainStore store) {
        Optional<JavaToolchainRequest> configured = configReader.readJava(projectRoot.resolve("zolt.toml"));
        if (configured.isEmpty()) {
            throw new ActionableException(
                    "Toolchain sync needs an explicit [toolchain.java] table.",
                    "Add [toolchain.java] with version, distribution, and features, then rerun `zolt toolchain sync`.");
        }
        JavaToolchainRequest request = configured.orElseThrow();
        if (request.distribution().isEmpty()) {
            throw new ActionableException(
                    "Toolchain sync needs [toolchain.java].distribution.",
                    "Set distribution to graalvm-community or temurin, then rerun `zolt toolchain sync`.");
        }
        return sync(request, projectRoot.resolve("zolt.lock"), platform, store);
    }

    public ToolchainSyncResult sync(
            JavaToolchainRequest request,
            Path lockfile,
            HostPlatform platform,
            ToolchainStore store) {
        if (request.distribution().isEmpty()) {
            throw new ActionableException(
                    "Toolchain sync needs a Java distribution.",
                    "Set distribution to graalvm-community or temurin, then rerun `zolt toolchain sync`.");
        }
        LockedJavaToolchain locked = catalog.lock(request, platform).orElseThrow(() -> new ActionableException(
                "No bundled Java toolchain catalog entry matches this request.",
                "Use Java 21 with distribution graalvm-community for native-image, or temurin for JVM-only sync."));
        lockfiles.writeJava(lockfile, locked);
        ToolchainStore effectiveStore = store == null ? ToolchainStore.defaults() : store;
        JavaToolchainArtifact artifact = catalog.artifact(locked).orElseThrow(() -> new ActionableException(
                "No downloadable Java toolchain artifact matches this request.",
                "Update Zolt's bundled toolchain catalog or choose a supported [toolchain.java] distribution."));
        boolean downloaded = installer.install(locked, artifact, effectiveStore);
        return new ToolchainSyncResult(
                lockfile,
                locked,
                effectiveStore.javaHome(locked),
                effectiveStore.installed(locked),
                downloaded);
    }
}
