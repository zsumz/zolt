package sh.zolt.toml;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

public final class ToolchainSectionCodec {
    private static final Set<String> TOOLCHAIN_KEYS = Set.of("zolt");
    private static final Set<String> ZOLT_KEYS = Set.of("version");

    private ToolchainSectionCodec() {
    }

    public static Optional<String> parseZoltVersion(TomlParseResult result, String sourceName) {
        TomlTable toolchain = result.getTable("toolchain");
        if (toolchain == null) {
            return Optional.empty();
        }
        validateKeys("toolchain", toolchain, TOOLCHAIN_KEYS, sourceName);

        TomlTable zolt = toolchain.getTable(List.of("zolt"));
        if (zolt == null) {
            Object raw = toolchain.get(List.of("zolt"));
            if (raw != null) {
                throw new ZoltConfigException(
                        "Invalid value for [toolchain].zolt in " + sourceName + ". Use a table.");
            }
            return Optional.empty();
        }
        validateKeys("toolchain.zolt", zolt, ZOLT_KEYS, sourceName);

        Object rawVersion = zolt.get(List.of("version"));
        if (rawVersion == null) {
            throw new ZoltConfigException(
                    "Missing required field [toolchain.zolt].version in " + sourceName + ".");
        }
        if (!(rawVersion instanceof String version) || version.isBlank()) {
            throw new ZoltConfigException(
                    "Invalid value for [toolchain.zolt].version in " + sourceName + ". Use a non-empty string.");
        }
        return Optional.of(safeVersion(version, sourceName));
    }

    private static void validateKeys(String section, TomlTable table, Set<String> allowedKeys, String sourceName) {
        for (String key : table.keySet()) {
            if (!allowedKeys.contains(key)) {
                throw new ZoltConfigException(
                        "Unknown field [" + section + "]." + key + " in " + sourceName + ". Remove it or check the spelling.");
            }
        }
    }

    private static String safeVersion(String version, String sourceName) {
        String normalized = version.strip();
        if (normalized.contains("/")
                || normalized.contains("\\")
                || normalized.contains("..")
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new ZoltConfigException(
                    "Invalid value for [toolchain.zolt].version in " + sourceName + ". Use one Zolt version string.");
        }
        return normalized;
    }
}
