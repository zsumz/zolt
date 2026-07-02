package sh.zolt.toml;

import sh.zolt.project.VersionAliasRules;
import sh.zolt.project.VersionPolicy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.tomlj.TomlTable;

final class VersionAliasSectionCodec {
    private VersionAliasSectionCodec() {
    }

    static Map<String, String> parse(TomlTable table) {
        if (table == null) {
            return Map.of();
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (String alias : table.keySet()) {
            validateAliasName(alias);
            Object rawValue = table.get(List.of(alias));
            if (!(rawValue instanceof String value)
                    || VersionPolicy.violation(VersionPolicy.Context.VERSION_ALIAS, value).isPresent()) {
                throw new ZoltConfigException(
                        "Invalid [versions]."
                                + alias
                                + " in zolt.toml. Use a non-empty literal version string; Zolt does not support interpolation, dynamic versions, version ranges, or SNAPSHOTs.");
            }
            values.put(alias, value);
        }
        return values;
    }

    static void write(StringBuilder toml, Map<String, String> values) {
        if (values.isEmpty()) {
            return;
        }

        toml.append("[versions]\n");
        for (Map.Entry<String, String> entry : new TreeMap<>(values).entrySet()) {
            toml.append(quote(entry.getKey())).append(" = ").append(quote(entry.getValue())).append('\n');
        }
        toml.append('\n');
    }

    private static void validateAliasName(String alias) {
        if (!VersionAliasRules.isValidName(alias)) {
            throw new ZoltConfigException(
                    "Invalid [versions] alias `"
                            + alias
                            + "`. Alias names may contain only letters, digits, dot, underscore, and hyphen.");
        }
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
