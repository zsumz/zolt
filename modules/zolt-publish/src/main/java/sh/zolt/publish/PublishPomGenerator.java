package sh.zolt.publish;

import sh.zolt.lockfile.LockPackage;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.project.DependencyExclusionSpec;
import sh.zolt.project.DependencyMetadata;
import sh.zolt.project.DeveloperEntry;
import sh.zolt.project.PackageMode;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.PublicationMetadata;
import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PublishPomGenerator {
    public String generate(ProjectConfig config, ZoltLockfile lockfile) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\" ")
                .append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
                .append("xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n");
        PublicationMetadata metadata = config.packageSettings().metadata();
        element(xml, 1, "modelVersion", "4.0.0");
        element(xml, 1, "groupId", config.project().group());
        element(xml, 1, "artifactId", config.project().name());
        element(xml, 1, "version", config.project().version());
        String packaging = packaging(config.packageSettings().mode());
        if (!packaging.equals("jar")) {
            element(xml, 1, "packaging", packaging);
        }
        if (!metadata.name().isBlank()) {
            element(xml, 1, "name", metadata.name());
        }
        if (!metadata.description().isBlank()) {
            element(xml, 1, "description", metadata.description());
        }
        if (!metadata.url().isBlank()) {
            element(xml, 1, "url", metadata.url());
        }
        if (!metadata.license().isBlank()) {
            indent(xml, 1).append("<licenses>\n");
            indent(xml, 2).append("<license>\n");
            element(xml, 3, "name", metadata.license());
            if (!metadata.licenseUrl().isBlank()) {
                element(xml, 3, "url", metadata.licenseUrl());
            }
            indent(xml, 2).append("</license>\n");
            indent(xml, 1).append("</licenses>\n");
        }
        if (!metadata.developers().isEmpty() || !metadata.developerEntries().isEmpty()) {
            indent(xml, 1).append("<developers>\n");
            for (DeveloperEntry developer : metadata.developerEntries()) {
                developer(xml, developer);
            }
            for (String developer : metadata.developers()) {
                indent(xml, 2).append("<developer>\n");
                element(xml, 3, "name", developer);
                indent(xml, 2).append("</developer>\n");
            }
            indent(xml, 1).append("</developers>\n");
        }
        if (!metadata.scm().isBlank()
                || !metadata.scmConnection().isBlank()
                || !metadata.scmDeveloperConnection().isBlank()
                || !metadata.scmTag().isBlank()) {
            indent(xml, 1).append("<scm>\n");
            if (!metadata.scmConnection().isBlank()) {
                element(xml, 2, "connection", metadata.scmConnection());
            }
            if (!metadata.scmDeveloperConnection().isBlank()) {
                element(xml, 2, "developerConnection", metadata.scmDeveloperConnection());
            }
            if (!metadata.scmTag().isBlank()) {
                element(xml, 2, "tag", metadata.scmTag());
            }
            if (!metadata.scm().isBlank()) {
                element(xml, 2, "url", metadata.scm());
            }
            indent(xml, 1).append("</scm>\n");
        }
        if (!metadata.issues().isBlank()) {
            indent(xml, 1).append("<issueManagement>\n");
            element(xml, 2, "url", metadata.issues());
            indent(xml, 1).append("</issueManagement>\n");
        }

        List<PublishPomDependency> dependencies = dependencies(config, lockfile);
        if (!dependencies.isEmpty()) {
            indent(xml, 1).append("<dependencies>\n");
            for (PublishPomDependency dependency : dependencies) {
                dependency(xml, dependency);
            }
            indent(xml, 1).append("</dependencies>\n");
        }
        xml.append("</project>\n");
        return xml.toString();
    }

    private static List<PublishPomDependency> dependencies(ProjectConfig config, ZoltLockfile lockfile) {
        Map<String, PublishPomDependency> dependencies = new LinkedHashMap<>();
        for (LockPackage lockPackage : lockfile.packages()) {
            if (!lockPackage.direct() || !publishedScope(lockPackage.scope())) {
                continue;
            }
            String coordinate = coordinate(lockPackage.packageId());
            DependencyMetadata metadata = config.dependencyMetadata().get(metadataKey(lockPackage.scope(), coordinate));
            dependencies.put(coordinate, new PublishPomDependency(
                    lockPackage.packageId().groupId(),
                    lockPackage.packageId().artifactId(),
                    lockPackage.version(),
                    mavenScope(lockPackage.scope()),
                    metadata != null && metadata.optional(),
                    metadata == null ? List.of() : metadata.exclusions()));
        }
        for (DependencyMetadata metadata : config.dependencyMetadata().values()) {
            if (!metadata.publishOnly()) {
                continue;
            }
            PackageId packageId = packageId(metadata.coordinate());
            dependencies.put(metadata.coordinate(), new PublishPomDependency(
                    packageId.groupId(),
                    packageId.artifactId(),
                    metadata.version(),
                    mavenScope(metadata.section()),
                    metadata.optional(),
                    metadata.exclusions()));
        }
        return dependencies.values().stream()
                .sorted(Comparator.comparing(PublishPomDependency::coordinate))
                .toList();
    }

    private static boolean publishedScope(DependencyScope scope) {
        return scope == DependencyScope.COMPILE
                || scope == DependencyScope.RUNTIME
                || scope == DependencyScope.PROVIDED;
    }

    private static String metadataKey(DependencyScope scope, String coordinate) {
        return switch (scope) {
            case COMPILE -> DependencyMetadata.key("dependencies", coordinate);
            case RUNTIME -> DependencyMetadata.key("runtime.dependencies", coordinate);
            case PROVIDED -> DependencyMetadata.key("provided.dependencies", coordinate);
            default -> DependencyMetadata.key("dependencies", coordinate);
        };
    }

    private static String mavenScope(DependencyScope scope) {
        return switch (scope) {
            case RUNTIME -> "runtime";
            case PROVIDED -> "provided";
            default -> "compile";
        };
    }

    private static String mavenScope(String section) {
        return switch (section) {
            case "runtime.dependencies" -> "runtime";
            case "provided.dependencies" -> "provided";
            case "test.dependencies" -> "test";
            default -> "compile";
        };
    }

    private static PackageId packageId(String coordinate) {
        String[] parts = coordinate.split(":", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new PublishException("Invalid dependency coordinate `" + coordinate + "` in publication metadata.");
        }
        return new PackageId(parts[0], parts[1]);
    }

    private static String packaging(PackageMode mode) {
        return switch (mode) {
            case WAR, SPRING_BOOT_WAR -> "war";
            default -> "jar";
        };
    }

    private static void developer(StringBuilder xml, DeveloperEntry developer) {
        indent(xml, 2).append("<developer>\n");
        if (!developer.id().isBlank()) {
            element(xml, 3, "id", developer.id());
        }
        if (!developer.name().isBlank()) {
            element(xml, 3, "name", developer.name());
        }
        if (!developer.email().isBlank()) {
            element(xml, 3, "email", developer.email());
        }
        if (!developer.organization().isBlank()) {
            element(xml, 3, "organization", developer.organization());
        }
        if (!developer.url().isBlank()) {
            element(xml, 3, "url", developer.url());
        }
        indent(xml, 2).append("</developer>\n");
    }

    private static void dependency(StringBuilder xml, PublishPomDependency dependency) {
        indent(xml, 2).append("<dependency>\n");
        element(xml, 3, "groupId", dependency.groupId());
        element(xml, 3, "artifactId", dependency.artifactId());
        if (dependency.version() != null && !dependency.version().isBlank()) {
            element(xml, 3, "version", dependency.version());
        }
        if (!dependency.scope().equals("compile")) {
            element(xml, 3, "scope", dependency.scope());
        }
        if (dependency.optional()) {
            element(xml, 3, "optional", "true");
        }
        if (!dependency.exclusions().isEmpty()) {
            indent(xml, 3).append("<exclusions>\n");
            for (DependencyExclusionSpec exclusion : dependency.exclusions()) {
                indent(xml, 4).append("<exclusion>\n");
                element(xml, 5, "groupId", exclusion.group());
                element(xml, 5, "artifactId", exclusion.artifact());
                indent(xml, 4).append("</exclusion>\n");
            }
            indent(xml, 3).append("</exclusions>\n");
        }
        indent(xml, 2).append("</dependency>\n");
    }

    private static void element(StringBuilder xml, int level, String name, String value) {
        indent(xml, level)
                .append('<')
                .append(name)
                .append('>')
                .append(escapeXml(value))
                .append("</")
                .append(name)
                .append(">\n");
    }

    private static StringBuilder indent(StringBuilder xml, int level) {
        return xml.append("  ".repeat(level));
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String coordinate(PackageId packageId) {
        return packageId.groupId() + ":" + packageId.artifactId();
    }

    private record PublishPomDependency(
            String groupId,
            String artifactId,
            String version,
            String scope,
            boolean optional,
            List<DependencyExclusionSpec> exclusions) {
        private PublishPomDependency {
            exclusions = exclusions == null ? List.of() : List.copyOf(new ArrayList<>(exclusions));
        }

        private String coordinate() {
            return groupId + ":" + artifactId;
        }
    }
}
