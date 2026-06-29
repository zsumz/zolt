package com.zolt.release;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Properties;

public final class NativeUpdateNoticeService {
    private static final Duration DEFAULT_CHECK_INTERVAL = Duration.ofHours(24);
    private static final int NOTICE_CONNECT_TIMEOUT_MILLIS = 2_000;
    private static final int NOTICE_READ_TIMEOUT_MILLIS = 2_000;

    private final ReleaseChannelManifestValidator manifestValidator = new ReleaseChannelManifestValidator();

    public Optional<NativeUpdateNotice> check(NativeUpdateNoticeRequest request) {
        if (request.disabled() || request.offline() || request.ci() || !request.interactive()) {
            return Optional.empty();
        }

        NativeInstalledLayout installed;
        try {
            installed = NativeInstalledLayout.detect(request.installRoot(), request.currentExecutable());
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }

        ReleaseChannelUriPolicy.validate(request.channelUri(), true);

        Properties cache = readCache(request.stateDirectory());
        Duration interval = request.checkInterval() == null ? DEFAULT_CHECK_INTERVAL : request.checkInterval();
        if (!checkDue(cache, request.now(), interval)) {
            return cachedNotice(cache, request, installed);
        }

        try {
            ReleaseChannelManifest manifest = validateManifest(request.channelUri(), downloadText(request.channelUri()));
            writeCache(request, manifest.version(), manifest.channel());
            if (isNewer(installed.version(), manifest.version())) {
                return Optional.of(new NativeUpdateNotice(
                        manifest.channel(),
                        request.target(),
                        installed.version(),
                        manifest.version(),
                        false));
            }
            return Optional.empty();
        } catch (IOException | RuntimeException exception) {
            writeCache(request, cache.getProperty("latestVersion", ""), cache.getProperty("channel", ""));
            return cachedNotice(cache, request, installed);
        }
    }

    private static Optional<NativeUpdateNotice> cachedNotice(
            Properties cache,
            NativeUpdateNoticeRequest request,
            NativeInstalledLayout installed) {
        if (!request.channelUri().toString().equals(cache.getProperty("channelUri"))
                || !request.target().id().equals(cache.getProperty("target"))) {
            return Optional.empty();
        }
        String latestVersion = cache.getProperty("latestVersion", "");
        String channel = cache.getProperty("channel", "");
        if (latestVersion.isBlank() || channel.isBlank() || !isNewer(installed.version(), latestVersion)) {
            return Optional.empty();
        }
        return Optional.of(new NativeUpdateNotice(
                channel,
                request.target(),
                installed.version(),
                latestVersion,
                true));
    }

    private ReleaseChannelManifest validateManifest(URI channelUri, String json) {
        if (ReleaseChannelUriPolicy.isLocalFile(channelUri)) {
            return manifestValidator.validateLocalManifest(json);
        }
        return manifestValidator.validate(json);
    }

    private static boolean checkDue(Properties cache, Instant now, Duration interval) {
        String lastCheckedAt = cache.getProperty("lastCheckedAt", "");
        if (lastCheckedAt.isBlank()) {
            return true;
        }
        try {
            Instant last = Instant.parse(lastCheckedAt);
            return Duration.between(last, now).compareTo(interval) >= 0;
        } catch (DateTimeParseException exception) {
            return true;
        }
    }

    private static Properties readCache(Path stateDirectory) {
        Properties properties = new Properties();
        Path cacheFile = cacheFile(stateDirectory);
        if (!Files.isRegularFile(cacheFile)) {
            return properties;
        }
        try (var input = Files.newInputStream(cacheFile)) {
            properties.load(input);
        } catch (IOException exception) {
            return new Properties();
        }
        return properties;
    }

    private static void writeCache(NativeUpdateNoticeRequest request, String latestVersion, String channel) {
        Properties properties = new Properties();
        properties.setProperty("lastCheckedAt", request.now().toString());
        properties.setProperty("channelUri", request.channelUri().toString());
        properties.setProperty("target", request.target().id());
        if (latestVersion != null && !latestVersion.isBlank()) {
            properties.setProperty("latestVersion", latestVersion);
        }
        if (channel != null && !channel.isBlank()) {
            properties.setProperty("channel", channel);
        }
        try {
            Files.createDirectories(request.stateDirectory());
            try (var output = Files.newOutputStream(cacheFile(request.stateDirectory()))) {
                properties.store(output, "Zolt native update check cache");
            }
        } catch (IOException exception) {
            // Update notices must never fail the user's original command.
        }
    }

    private static Path cacheFile(Path stateDirectory) {
        return stateDirectory.resolve("update-check.properties");
    }

    private static String downloadText(URI uri) throws IOException {
        if ("file".equals(uri.getScheme())) {
            return Files.readString(Path.of(uri), StandardCharsets.UTF_8);
        }
        URLConnection connection = uri.toURL().openConnection();
        connection.setConnectTimeout(NOTICE_CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(NOTICE_READ_TIMEOUT_MILLIS);
        try (var input = connection.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static boolean isNewer(String currentVersion, String availableVersion) {
        return compareVersions(availableVersion, currentVersion) > 0;
    }

    private static int compareVersions(String left, String right) {
        String[] leftParts = left.split("[^A-Za-z0-9]+");
        String[] rightParts = right.split("[^A-Za-z0-9]+");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < length; index++) {
            String leftPart = part(leftParts, index);
            String rightPart = part(rightParts, index);
            int comparison = comparePart(leftPart, rightPart);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static String part(String[] parts, int index) {
        return index < parts.length ? parts[index] : "0";
    }

    private static int comparePart(String left, String right) {
        if (left.matches("\\d+") && right.matches("\\d+")) {
            return new BigInteger(left).compareTo(new BigInteger(right));
        }
        return left.compareToIgnoreCase(right);
    }
}
