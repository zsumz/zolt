package com.zolt.explain.emit;

/**
 * The result of converting a static migration audit into draft Zolt config.
 *
 * <p>A single-project build maps to one {@link DraftZoltToml}. A multi-module Maven reactor or a
 * Gradle multi-project build maps to a {@link DraftWorkspace}: a root {@code [workspace]} document
 * plus one member draft per module, with inter-module edges rewritten to {@code { workspace = ... }}.
 */
public sealed interface DraftEmit permits DraftZoltToml, DraftWorkspace {}
