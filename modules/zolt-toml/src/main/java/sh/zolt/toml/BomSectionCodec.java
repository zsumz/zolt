package sh.zolt.toml;

import sh.zolt.project.BomSettings;
import sh.zolt.project.PackageMode;
import sh.zolt.project.PackageSettings;
import sh.zolt.project.UberDuplicatePolicy;
import sh.zolt.project.VersionPolicy;
import sh.zolt.toml.dependency.DependencySectionCodec;
import sh.zolt.toml.support.TomlScalars;
import sh.zolt.toml.support.TomlValidation;
import sh.zolt.toml.support.TomlVersions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/**
 * Codec for the {@code [bom]} section (and {@code [bom.versions]} / {@code [bom.imports]} subtables)
 * plus the mode reconciliation and validation the BOM contract requires.
 *
 * <p>The section's presence implies {@link PackageMode#BOM}; an explicit {@code mode = "bom"} is a
 * synonym. Every other mode-vs-section combination, and every field a BOM must not carry, is a config
 * error surfaced here.
 */
final class BomSectionCodec {
    private static final Set<String> BOM_KEYS = Set.of("members", "exclude", "versions", "imports");
    private static final Set<String> BOM_VERSION_KEYS = Set.of("version", "versionRef", "classifier", "type");
    private static final Set<String> BOM_IMPORT_KEYS = Set.of("version", "versionRef");

    private BomSectionCodec() {
    }

    static BomSettings parse(TomlTable table, java.util.Map<String, String> versionAliases) {
        if (table == null) {
            return BomSettings.none();
        }
        TomlValidation.validateKeys("bom", table, BOM_KEYS);
        return new BomSettings(
                parseMembers(table),
                parseVersions(table.getTable(List.of("versions")), versionAliases),
                parseImports(table.getTable(List.of("imports")), versionAliases));
    }

    /**
     * Reconciles the parsed {@code [package]} settings against the presence of {@code [bom]} and the
     * BOM's own constraints. Returns package settings whose mode is {@link PackageMode#BOM} with the
     * BOM attached when the section is present, or the unchanged settings otherwise.
     */
    static PackageSettings reconcile(
            PackageSettings packageSettings, TomlTable packageTable, TomlTable bomTable, BomSettings bom) {
        boolean bomPresent = bomTable != null;
        boolean modeExplicit = packageTable != null && packageTable.get(List.of("mode")) != null;
        PackageMode declaredMode = packageSettings.mode();
        if (!bomPresent) {
            if (declaredMode == PackageMode.BOM) {
                throw new ZoltConfigException(
                        "Invalid [package].mode = \"bom\" in zolt.toml without a [bom] section. "
                                + "Add a [bom] section to author a BOM, or choose another package mode.");
            }
            return packageSettings;
        }
        if (modeExplicit && declaredMode != PackageMode.BOM) {
            throw new ZoltConfigException(
                    "Invalid [package].mode = \"" + declaredMode.configValue() + "\" alongside a [bom] section in "
                            + "zolt.toml. A [bom] section implies package mode `bom`; remove the mode or the section.");
        }
        rejectPackageFields(packageSettings);
        return new PackageSettings(
                PackageMode.BOM,
                packageSettings.sources(),
                packageSettings.javadoc(),
                packageSettings.tests(),
                packageSettings.metadata(),
                packageSettings.manifestAttributes(),
                packageSettings.uberDuplicates(),
                bom);
    }

    /** Fails when a BOM member actually declares dependencies in any dependency-bearing section. */
    static void validateNoDependencySections(DependencySectionCodec.ParsedDependencies dependencies) {
        rejectIfDeclared("api.dependencies", dependencies.apiDependencies());
        rejectIfDeclared("dependencies", dependencies.implementationDependencies());
        rejectIfDeclared("runtime.dependencies", dependencies.runtimeDependencies());
        rejectIfDeclared("provided.dependencies", dependencies.providedDependencies());
        rejectIfDeclared("dev.dependencies", dependencies.devDependencies());
        rejectIfDeclared("test.dependencies", dependencies.testDependencies());
        rejectIfDeclared("annotationProcessors", dependencies.annotationProcessors());
        rejectIfDeclared("test.annotationProcessors", dependencies.testAnnotationProcessors());
    }

    private static void rejectIfDeclared(
            String section, DependencySectionCodec.DependencyDeclarations declarations) {
        if (declarations.versioned().isEmpty()
                && declarations.managed().isEmpty()
                && declarations.workspace().isEmpty()) {
            return;
        }
        throw new ZoltConfigException(
                "Invalid [" + section + "] on a BOM member in zolt.toml. A BOM publishes a curated version set "
                        + "only; declare third-party pins under [bom.versions] and composed BOMs under "
                        + "[bom.imports], not dependencies.");
    }

    static void write(StringBuilder toml, BomSettings bom) {
        if (bom == null) {
            return;
        }
        toml.append("\n[bom]\n");
        BomSettings.Members members = bom.members();
        if (members.all()) {
            toml.append("members = true\n");
        } else if (!members.paths().isEmpty()) {
            writeStringArray(toml, "members", members.paths());
        }
        if (!members.exclude().isEmpty()) {
            writeStringArray(toml, "exclude", members.exclude());
        }
        writeVersions(toml, bom.versions());
        writeImports(toml, bom.imports());
    }

    private static BomSettings.Members parseMembers(TomlTable table) {
        Object rawMembers = table.get(List.of("members"));
        List<String> exclude = TomlScalars.stringListOrDefault(table, "bom", "exclude", List.of());
        if (rawMembers == null) {
            if (!exclude.isEmpty()) {
                throw new ZoltConfigException(
                        "Invalid [bom].exclude in zolt.toml without [bom].members. Set members = true (or a member "
                                + "path list) before excluding members.");
            }
            return BomSettings.Members.none();
        }
        if (rawMembers instanceof Boolean all) {
            if (!all) {
                throw new ZoltConfigException(
                        "Invalid [bom].members = false in zolt.toml. Use members = true for the whole workspace, an "
                                + "explicit member-path list, or omit it for a standalone BOM.");
            }
            return new BomSettings.Members(true, List.of(), exclude);
        }
        if (rawMembers instanceof TomlArray) {
            List<String> paths = TomlScalars.stringListOrDefault(table, "bom", "members", List.of());
            if (paths.isEmpty()) {
                throw new ZoltConfigException(
                        "Invalid empty [bom].members list in zolt.toml. List member paths, use members = true, or "
                                + "omit it for a standalone BOM.");
            }
            return new BomSettings.Members(false, paths, exclude);
        }
        throw new ZoltConfigException(
                "Invalid value for [bom].members in zolt.toml. Use members = true or a list of member paths.");
    }

    private static List<BomSettings.ManagedVersion> parseVersions(
            TomlTable table, java.util.Map<String, String> versionAliases) {
        if (table == null) {
            return List.of();
        }
        List<BomSettings.ManagedVersion> versions = new ArrayList<>();
        for (String coordinate : table.keySet()) {
            validateCoordinate("bom.versions", coordinate);
            String section = "bom.versions." + coordinate;
            Object rawValue = table.get(List.of(coordinate));
            if (rawValue instanceof String literal) {
                requireNonBlankVersion(section, literal);
                TomlVersions.validateVersion(VersionPolicy.Context.PLATFORM, section, literal);
                versions.add(new BomSettings.ManagedVersion(coordinate, literal, null, null, null));
                continue;
            }
            if (rawValue instanceof TomlTable valueTable) {
                TomlValidation.validateKeysWithVersionRefHint(section, valueTable, BOM_VERSION_KEYS);
                String version = TomlVersions.optionalVersionOrRef(
                                valueTable, section, VersionPolicy.Context.PLATFORM, versionAliases)
                        .orElseThrow(() -> new ZoltConfigException(
                                "Invalid [" + section + "] in zolt.toml. Provide a fixed version or versionRef."));
                String versionRef = versionRef(valueTable);
                String classifier = TomlScalars.nonBlankStringOrDefault(valueTable, section, "classifier", null);
                String type = TomlScalars.nonBlankStringOrDefault(valueTable, section, "type", null);
                versions.add(new BomSettings.ManagedVersion(coordinate, version, versionRef, classifier, type));
                continue;
            }
            throw invalidValue(section);
        }
        return versions;
    }

    private static List<BomSettings.ImportedBom> parseImports(
            TomlTable table, java.util.Map<String, String> versionAliases) {
        if (table == null) {
            return List.of();
        }
        List<BomSettings.ImportedBom> imports = new ArrayList<>();
        for (String coordinate : table.keySet()) {
            validateCoordinate("bom.imports", coordinate);
            String section = "bom.imports." + coordinate;
            Object rawValue = table.get(List.of(coordinate));
            if (rawValue instanceof String literal) {
                requireNonBlankVersion(section, literal);
                TomlVersions.validateVersion(VersionPolicy.Context.PLATFORM, section, literal);
                imports.add(new BomSettings.ImportedBom(coordinate, literal, null));
                continue;
            }
            if (rawValue instanceof TomlTable valueTable) {
                TomlValidation.validateKeysWithVersionRefHint(section, valueTable, BOM_IMPORT_KEYS);
                String version = TomlVersions.optionalVersionOrRef(
                                valueTable, section, VersionPolicy.Context.PLATFORM, versionAliases)
                        .orElseThrow(() -> new ZoltConfigException(
                                "Invalid [" + section + "] in zolt.toml. Provide a fixed version or versionRef."));
                imports.add(new BomSettings.ImportedBom(coordinate, version, versionRef(valueTable)));
                continue;
            }
            throw invalidValue(section);
        }
        return imports;
    }

    private static void rejectPackageFields(PackageSettings packageSettings) {
        if (packageSettings.sources()) {
            throw bomPackageField("sources");
        }
        if (packageSettings.javadoc()) {
            throw bomPackageField("javadoc");
        }
        if (packageSettings.tests()) {
            throw bomPackageField("tests");
        }
        if (!packageSettings.manifestAttributes().isEmpty()) {
            throw bomPackageField("manifest");
        }
        if (packageSettings.uberDuplicates() != UberDuplicatePolicy.FAIL) {
            throw bomPackageField("uberDuplicates");
        }
    }

    private static ZoltConfigException bomPackageField(String field) {
        return new ZoltConfigException(
                "Invalid [package]." + field + " on a BOM member in zolt.toml. A BOM produces only a "
                        + "<dependencyManagement> POM; it has no compiled sources, javadoc, tests, manifest, or jar.");
    }

    private static String versionRef(TomlTable table) {
        Object rawVersionRef = table.get(List.of("versionRef"));
        return rawVersionRef instanceof String alias ? alias : null;
    }

    private static void validateCoordinate(String section, String coordinate) {
        String[] parts = coordinate.split(":", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new ZoltConfigException(
                    "Invalid coordinate `" + coordinate + "` in [" + section + "] in zolt.toml. "
                            + "Use a `group:artifact` coordinate.");
        }
    }

    private static void requireNonBlankVersion(String section, String value) {
        if (value.isBlank()) {
            throw invalidValue(section);
        }
    }

    private static ZoltConfigException invalidValue(String section) {
        return new ZoltConfigException(
                "Invalid value for [" + section + "] in zolt.toml. Use a fixed version string or a "
                        + "{ version = \"...\" } / { versionRef = \"alias\" } table.");
    }

    private static void writeVersions(StringBuilder toml, List<BomSettings.ManagedVersion> versions) {
        if (versions.isEmpty()) {
            return;
        }
        toml.append("\n[bom.versions]\n");
        List<BomSettings.ManagedVersion> sorted = new ArrayList<>(versions);
        sorted.sort(Comparator.comparing(BomSettings.ManagedVersion::coordinate));
        for (BomSettings.ManagedVersion version : sorted) {
            toml.append(quote(version.coordinate())).append(" = ");
            if (version.versionRef() == null && version.classifier() == null && version.type() == null) {
                toml.append(quote(version.version()));
            } else {
                toml.append("{ ");
                List<String> fields = new ArrayList<>();
                if (version.versionRef() != null) {
                    fields.add("versionRef = " + quote(version.versionRef()));
                } else {
                    fields.add("version = " + quote(version.version()));
                }
                if (version.classifier() != null) {
                    fields.add("classifier = " + quote(version.classifier()));
                }
                if (version.type() != null) {
                    fields.add("type = " + quote(version.type()));
                }
                toml.append(String.join(", ", fields)).append(" }");
            }
            toml.append('\n');
        }
    }

    private static void writeImports(StringBuilder toml, List<BomSettings.ImportedBom> imports) {
        if (imports.isEmpty()) {
            return;
        }
        toml.append("\n[bom.imports]\n");
        List<BomSettings.ImportedBom> sorted = new ArrayList<>(imports);
        sorted.sort(Comparator.comparing(BomSettings.ImportedBom::coordinate));
        for (BomSettings.ImportedBom imported : sorted) {
            toml.append(quote(imported.coordinate())).append(" = ");
            if (imported.versionRef() == null) {
                toml.append(quote(imported.version()));
            } else {
                toml.append("{ versionRef = ").append(quote(imported.versionRef())).append(" }");
            }
            toml.append('\n');
        }
    }

    private static void writeStringArray(StringBuilder toml, String key, List<String> values) {
        List<String> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        toml.append(key).append(" = [");
        for (int index = 0; index < sorted.size(); index++) {
            if (index > 0) {
                toml.append(", ");
            }
            toml.append(quote(sorted.get(index)));
        }
        toml.append("]\n");
    }

    private static String quote(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }
}
