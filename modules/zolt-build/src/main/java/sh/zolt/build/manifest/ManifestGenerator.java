package sh.zolt.build.manifest;

import sh.zolt.build.ManifestGenerationException;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.ProjectMetadata;
import sh.zolt.provenance.BuildProvenance;
import sh.zolt.provenance.BuildProvenanceSource;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.jar.Attributes;

public final class ManifestGenerator {
    private static final int MAX_MANIFEST_LINE_BYTES = 72;
    private static final String CONTINUATION_PREFIX = " ";
    private static final String SOURCE_DATE_EPOCH = "SOURCE_DATE_EPOCH";
    private static final String IMPLEMENTATION_VERSION = "Implementation-Version";
    private static final String SCM_REVISION = "SCM-Revision";
    private static final String BUILD_JDK = "Build-Jdk";
    private static final String BUILD_TIMESTAMP = "Build-Timestamp";
    private static final String CREATED_BY = "Created-By";
    private static final String ZOLT_VERSION = "Zolt-Version";
    private static final String ZOLT_RESOLUTION_FINGERPRINT = "Zolt-Resolution-Fingerprint";
    private static final String MULTI_RELEASE = "Multi-Release";

    private final Clock clock;
    private final BuildProvenanceSource provenanceSource;
    private final Map<String, String> environment;

    public ManifestGenerator() {
        this(Clock.systemUTC(), BuildProvenanceSource.empty(), System.getenv());
    }

    public ManifestGenerator(BuildProvenanceSource provenanceSource) {
        this(Clock.systemUTC(), provenanceSource, System.getenv());
    }

    ManifestGenerator(Clock clock, Map<String, String> environment) {
        this(clock, BuildProvenanceSource.empty(), environment);
    }

    ManifestGenerator(
            Clock clock,
            BuildProvenanceSource provenanceSource,
            Map<String, String> environment) {
        this.clock = clock;
        this.provenanceSource = provenanceSource == null ? BuildProvenanceSource.empty() : provenanceSource;
        this.environment = environment == null ? Map.of() : Map.copyOf(environment);
    }

    public GeneratedManifest generate(ProjectConfig config) {
        return generate(config.project(), config.packageSettings().manifestAttributes());
    }

    public GeneratedManifest generate(Path projectDirectory, ProjectConfig config) {
        return generate(projectDirectory, config, false);
    }

    public GeneratedManifest generate(Path projectDirectory, ProjectConfig config, boolean multiRelease) {
        return generate(
                config.project(),
                multiReleaseAttributes(config.packageSettings().manifestAttributes(), multiRelease),
                true,
                Optional.of(readProvenance(projectDirectory, config)));
    }

    private static Map<String, String> multiReleaseAttributes(Map<String, String> attributes, boolean multiRelease) {
        if (!multiRelease || attributes.keySet().stream().anyMatch(name -> name.equalsIgnoreCase(MULTI_RELEASE))) {
            return attributes;
        }
        Map<String, String> augmented = new LinkedHashMap<>(attributes);
        augmented.put(MULTI_RELEASE, "true");
        return augmented;
    }

    public GeneratedManifest generateWithoutMain(ProjectConfig config) {
        return generate(config.project(), config.packageSettings().manifestAttributes(), false);
    }

    public GeneratedManifest generateWithoutMain(Path projectDirectory, ProjectConfig config) {
        return generate(
                config.project(),
                config.packageSettings().manifestAttributes(),
                false,
                Optional.of(readProvenance(projectDirectory, config)));
    }

    public GeneratedManifest generate(ProjectMetadata project) {
        return generate(project, Map.of());
    }

    public GeneratedManifest generate(ProjectMetadata project, Map<String, String> manifestAttributes) {
        return generate(project, manifestAttributes, true);
    }

    private GeneratedManifest generate(
            ProjectMetadata project,
            Map<String, String> manifestAttributes,
            boolean includeMainClass) {
        return generate(project, manifestAttributes, includeMainClass, Optional.empty());
    }

    private GeneratedManifest generate(
            ProjectMetadata project,
            Map<String, String> manifestAttributes,
            boolean includeMainClass,
            Optional<BuildProvenance> provenance) {
        Map<String, String> attributes = validateAttributes(manifestAttributes);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeAttribute(output, Attributes.Name.MANIFEST_VERSION.toString(), "1.0");
        if (includeMainClass) {
            project.main().ifPresent(mainClass -> writeAttribute(output, Attributes.Name.MAIN_CLASS.toString(), mainClass));
        }
        provenance.ifPresent(buildProvenance -> writeProvenanceAttributes(output, project, buildProvenance));
        attributes.forEach((name, value) -> writeAttribute(output, name, value));
        output.write('\r');
        output.write('\n');

        return new GeneratedManifest(
                GeneratedManifest.DEFAULT_PATH,
                output.toByteArray(),
                includeMainClass ? project.main() : java.util.Optional.empty());
    }

    private BuildProvenance readProvenance(Path projectDirectory, ProjectConfig config) {
        return provenanceSource.read(
                projectDirectory,
                effectiveEnvironment(config.build().metadata().reproducible()),
                clock);
    }

    private Map<String, String> effectiveEnvironment(boolean reproducible) {
        if (!reproducible) {
            Map<String, String> effective = new TreeMap<>(environment);
            effective.remove(SOURCE_DATE_EPOCH);
            return effective;
        }
        if (hasValidSourceDateEpoch(environment)) {
            return environment;
        }
        Map<String, String> effective = new TreeMap<>(environment);
        effective.put(SOURCE_DATE_EPOCH, "0");
        return effective;
    }

    private static boolean hasValidSourceDateEpoch(Map<String, String> environment) {
        String value = environment.get(SOURCE_DATE_EPOCH);
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            Long.parseLong(value.trim());
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static void writeProvenanceAttributes(
            ByteArrayOutputStream output,
            ProjectMetadata project,
            BuildProvenance provenance) {
        writeAttribute(output, IMPLEMENTATION_VERSION, project.version());
        if (!provenance.zoltVersion().isBlank()) {
            writeAttribute(output, CREATED_BY, "Zolt " + provenance.zoltVersion());
            writeAttribute(output, ZOLT_VERSION, provenance.zoltVersion());
        }
        provenance.git().commitSha().ifPresent(commit -> writeAttribute(output, SCM_REVISION, commit));
        provenance.resolutionFingerprint()
                .ifPresent(fingerprint -> writeAttribute(output, ZOLT_RESOLUTION_FINGERPRINT, fingerprint));
        if (!provenance.jdkVersion().isBlank()) {
            writeAttribute(output, BUILD_JDK, provenance.jdkVersion());
        }
        writeAttribute(output, BUILD_TIMESTAMP, DateTimeFormatter.ISO_INSTANT.format(provenance.buildTimestamp()));
    }

    private static Map<String, String> validateAttributes(Map<String, String> manifestAttributes) {
        if (manifestAttributes == null || manifestAttributes.isEmpty()) {
            return Map.of();
        }
        Map<String, String> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, String> entry : manifestAttributes.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            validateName(name);
            validateValue(name, value);
            String previous = sorted.put(name, value);
            if (previous != null) {
                throw new ManifestGenerationException(
                        "Invalid [package.manifest]."
                                + name
                                + " in zolt.toml. Manifest attribute names must be unique ignoring case.");
            }
        }
        return sorted;
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new ManifestGenerationException(
                    "Invalid [package.manifest] attribute name in zolt.toml. Use a non-empty manifest attribute name.");
        }
        if (name.equalsIgnoreCase(Attributes.Name.MANIFEST_VERSION.toString())
                || name.equalsIgnoreCase(Attributes.Name.MAIN_CLASS.toString())) {
            throw new ManifestGenerationException(
                    "Invalid [package.manifest]."
                            + name
                            + " in zolt.toml. Zolt owns Manifest-Version and Main-Class; use [project].main for Main-Class.");
        }
        if (name.equalsIgnoreCase(IMPLEMENTATION_VERSION)
                || name.equalsIgnoreCase(SCM_REVISION)
                || name.equalsIgnoreCase(BUILD_JDK)
                || name.equalsIgnoreCase(BUILD_TIMESTAMP)
                || name.equalsIgnoreCase(CREATED_BY)
                || name.equalsIgnoreCase(ZOLT_VERSION)
                || name.equalsIgnoreCase(ZOLT_RESOLUTION_FINGERPRINT)) {
            throw new ManifestGenerationException(
                    "Invalid [package.manifest]."
                            + name
                            + " in zolt.toml. Zolt owns Implementation-Version, Created-By, Zolt-Version, Zolt-Resolution-Fingerprint, SCM-Revision, Build-Jdk, and Build-Timestamp; use project metadata and build provenance instead.");
        }
        try {
            new Attributes.Name(name);
        } catch (IllegalArgumentException exception) {
            throw new ManifestGenerationException(
                    "Invalid [package.manifest]."
                            + name
                            + " in zolt.toml. Manifest attribute names must contain only letters, digits, underscore, or hyphen and be at most 70 characters.",
                    exception);
        }
    }

    private static void validateValue(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new ManifestGenerationException(
                    "Invalid [package.manifest]."
                            + name
                            + " in zolt.toml. Use a non-empty string value.");
        }
        if (value.contains("\n") || value.contains("\r") || value.indexOf('\0') >= 0) {
            throw new ManifestGenerationException(
                    "Invalid [package.manifest]."
                            + name
                            + " in zolt.toml. Manifest values cannot contain line breaks or NUL characters.");
        }
    }

    private static void writeAttribute(ByteArrayOutputStream output, String name, String value) {
        String prefix = name + ": ";
        StringBuilder line = new StringBuilder(prefix);
        int lineBytes = byteLength(prefix);
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String text = new String(Character.toChars(codePoint));
            int textBytes = byteLength(text);
            if (lineBytes + textBytes > MAX_MANIFEST_LINE_BYTES) {
                writePhysicalLine(output, line.toString());
                line = new StringBuilder(CONTINUATION_PREFIX);
                lineBytes = byteLength(CONTINUATION_PREFIX);
            }
            line.append(text);
            lineBytes += textBytes;
            offset += Character.charCount(codePoint);
        }
        writePhysicalLine(output, line.toString());
    }

    private static int byteLength(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    private static void writePhysicalLine(ByteArrayOutputStream output, String line) {
        output.writeBytes(line.getBytes(StandardCharsets.UTF_8));
        output.write('\r');
        output.write('\n');
    }
}
