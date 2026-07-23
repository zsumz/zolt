package sh.zolt.update;

/**
 * Which surfaces {@code zolt update} can write with the existing mutation machinery. Aliases,
 * dependencies, annotation processors, platforms, and constraints are applicable. Literal
 * generated-tool coordinates (exec/protobuf/openapi) are not — exec-tool literal mutation is a later
 * stage, and the others have no literal writer — so they are reported as skipped and can only move
 * through a {@code [versions]} alias.
 */
final class UpdateApplicability {
    private UpdateApplicability() {
    }

    static boolean isApplicable(OutdatedSurface surface) {
        return switch (surface) {
            case VERSION_ALIAS, DEPENDENCY, ANNOTATION_PROCESSOR, PLATFORM, DEPENDENCY_CONSTRAINT -> true;
            case EXEC_TOOL_COORDINATE, PROTOBUF_TOOL, OPENAPI_TOOL -> false;
        };
    }

    static String reason(OutdatedSurface surface) {
        return switch (surface) {
            case EXEC_TOOL_COORDINATE ->
                "Literal exec-tool coordinate mutation is not yet supported; route the version through a "
                        + "[versions] alias or edit zolt.toml manually.";
            case PROTOBUF_TOOL, OPENAPI_TOOL ->
                "Literal generated-tool coordinate mutation is not supported; route the version through a "
                        + "[versions] alias.";
            default -> "";
        };
    }
}
