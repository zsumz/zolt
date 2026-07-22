package sh.zolt.explain.verify;

import java.util.List;
import java.util.Optional;

/**
 * The comparison for one module across all four scopes, plus its provenance (Maven reactor directory
 * and Zolt workspace member path when known) and informational notes about scopes that fall outside
 * the compared set.
 */
public record ModuleComparison(
        String moduleKey,
        ModulePresence presence,
        Optional<String> mavenDirectory,
        Optional<String> zoltMember,
        List<ScopeComparison> scopes,
        List<String> notes) {

    public ModuleComparison {
        moduleKey = moduleKey == null ? "" : moduleKey;
        mavenDirectory = mavenDirectory == null ? Optional.empty() : mavenDirectory;
        zoltMember = zoltMember == null ? Optional.empty() : zoltMember;
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    public boolean hasDifferences() {
        if (presence != ModulePresence.BOTH) {
            return true;
        }
        return scopes.stream().anyMatch(ScopeComparison::hasDifferences);
    }
}
