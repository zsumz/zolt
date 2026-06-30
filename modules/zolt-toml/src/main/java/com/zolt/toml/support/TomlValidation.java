package com.zolt.toml.support;

import com.zolt.toml.ZoltConfigException;
import java.util.Set;
import org.tomlj.TomlTable;

public final class TomlValidation {
    private TomlValidation() {
    }

    public static void validateKeys(String section, TomlTable table, Set<String> allowedKeys) {
        validateKeys(section, table, allowedKeys, false);
    }

    public static void validateKeysWithVersionRefHint(String section, TomlTable table, Set<String> allowedKeys) {
        validateKeys(section, table, allowedKeys, true);
    }

    private static void validateKeys(
            String section,
            TomlTable table,
            Set<String> allowedKeys,
            boolean versionRefHint) {
        for (String key : table.keySet()) {
            if (!allowedKeys.contains(key)) {
                if (versionRefHint && "versionRef".equals(key)) {
                    throw new ZoltConfigException(
                            "Invalid value for ["
                                    + section
                                    + "].versionRef in zolt.toml. versionRef is only supported for dependency, platform, constraint, and tool artifact versions.");
                }
                throw new ZoltConfigException(
                        "Unknown field [" + section + "]." + key + " in zolt.toml. Remove it or check the spelling.");
            }
        }
    }
}
