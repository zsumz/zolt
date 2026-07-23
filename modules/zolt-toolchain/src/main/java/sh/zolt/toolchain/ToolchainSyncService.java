package sh.zolt.toolchain;

import sh.zolt.error.ActionableException;
import sh.zolt.net.NetworkTransport;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.toolchain.JavaToolchainRequest;
import sh.zolt.toolchain.catalog.BundledJavaToolchainCatalog;
import sh.zolt.toolchain.catalog.JavaToolchainArtifact;
import sh.zolt.toolchain.catalog.JavaToolchainCatalog;
import sh.zolt.toolchain.install.JavaToolchainDownloader;
import sh.zolt.toolchain.install.JavaToolchainInstaller;
import sh.zolt.toolchain.install.ToolchainDownloadMirror;
import sh.zolt.toolchain.lock.LockedJavaToolchain;
import sh.zolt.toolchain.lock.ToolchainLockfileService;
import sh.zolt.toolchain.platform.HostPlatform;
import sh.zolt.toolchain.store.ToolchainStore;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
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

    /**
     * A sync service whose downloader routes JDK archives through the given proxy/CA transport and
     * mirror. The catalog, lockfile, and SHA-256 verification are unchanged, so mirrored downloads
     * are still integrity-checked against the pinned hash and the lock keeps the upstream URL.
     */
    public static ToolchainSyncService withNetwork(NetworkTransport transport, ToolchainDownloadMirror mirror) {
        return new ToolchainSyncService(
                new ToolchainConfigReader(),
                new BundledJavaToolchainCatalog(),
                new ToolchainLockfileService(),
                new JavaToolchainInstaller(new JavaToolchainDownloader(transport, mirror)));
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
        Optional<JavaToolchainRequest> testRequest = configReader.readJavaTest(projectRoot.resolve("zolt.toml"));
        return sync(request, testRequest, projectRoot.resolve("zolt.lock"), platform, store);
    }

    public ToolchainSyncResult sync(
            JavaToolchainRequest request,
            Path lockfile,
            HostPlatform platform,
            ToolchainStore store) {
        return sync(request, Optional.empty(), lockfile, platform, store);
    }

    /**
     * Syncs the main toolchain plus the optional {@code [toolchain.java.test]} runtime toolchain.
     * Every distinct request's per-platform matrix is written to the lock as additive
     * {@code [[toolchain.java]]} entries in one deterministic {@code writeJava}, and the current-host
     * archive is installed for each. An equal-version test request dedups against the main entry, so
     * it neither duplicates the lock nor triggers a second download. Returns the main toolchain's
     * install result.
     */
    public ToolchainSyncResult sync(
            JavaToolchainRequest request,
            Optional<JavaToolchainRequest> testRequest,
            Path lockfile,
            HostPlatform platform,
            ToolchainStore store) {
        HostPlatform effectivePlatform = platform == null ? HostPlatform.current() : platform;
        ToolchainStore effectiveStore = store == null ? ToolchainStore.defaults() : store;
        requireDistribution(request);
        testRequest.ifPresent(this::requireDistribution);

        LinkedHashSet<JavaToolchainRequest> requests = new LinkedHashSet<>();
        requests.add(request);
        testRequest.ifPresent(requests::add);

        LinkedHashSet<LockedJavaToolchain> allLocks = new LinkedHashSet<>();
        for (JavaToolchainRequest each : requests) {
            allLocks.addAll(catalog.locks(each, effectivePlatform));
        }
        lockfiles.writeJava(lockfile, List.copyOf(allLocks));

        ToolchainSyncResult mainResult = null;
        for (JavaToolchainRequest each : requests) {
            ToolchainSyncResult result = install(each, effectivePlatform, effectiveStore, lockfile);
            if (mainResult == null) {
                mainResult = result;
            }
        }
        return mainResult;
    }

    private ToolchainSyncResult install(
            JavaToolchainRequest request,
            HostPlatform platform,
            ToolchainStore store,
            Path lockfile) {
        LockedJavaToolchain locked = catalog.locks(request, platform).stream()
                .filter(candidate -> candidate.platform().equals(platform))
                .findFirst()
                .orElseThrow(() -> new ActionableException(
                        "No bundled Java toolchain catalog entry matches this request for "
                                + platform.id()
                                + ".",
                        "Use Java 21 with distribution graalvm-community for native-image, or temurin for JVM-only sync on linux-x64, linux-aarch64, macos-x64, or macos-aarch64."));
        JavaToolchainArtifact artifact = catalog.artifact(locked).orElseThrow(() -> new ActionableException(
                "No downloadable Java toolchain artifact matches this request.",
                "Update Zolt's bundled toolchain catalog or choose a supported [toolchain.java] distribution."));
        boolean downloaded = installer.install(locked, artifact, store);
        return new ToolchainSyncResult(
                lockfile,
                locked,
                store.javaHome(locked),
                store.installed(locked),
                downloaded);
    }

    private void requireDistribution(JavaToolchainRequest request) {
        if (request.distribution().isEmpty()) {
            throw new ActionableException(
                    "Toolchain sync needs a Java distribution.",
                    "Set distribution to graalvm-community or temurin, then rerun `zolt toolchain sync`.");
        }
    }
}
