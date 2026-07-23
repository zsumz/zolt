package sh.zolt.update;

/** A surface with an available update that {@code zolt update} cannot write, and why. */
public record UpdateSkip(OutdatedSurface surface, String identifier, String section, String reason) {
}
