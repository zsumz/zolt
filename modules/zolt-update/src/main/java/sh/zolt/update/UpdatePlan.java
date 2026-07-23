package sh.zolt.update;

import java.util.List;

/**
 * The computed effect of an update run: the edits to write, the surfaces skipped (with reasons), and
 * warnings (such as alias fan-out) the user must see. Deterministically ordered.
 */
public record UpdatePlan(List<UpdateEdit> edits, List<UpdateSkip> skips, List<String> warnings) {
    public UpdatePlan {
        edits = edits == null ? List.of() : List.copyOf(edits);
        skips = skips == null ? List.of() : List.copyOf(skips);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public boolean hasEdits() {
        return !edits.isEmpty();
    }
}
