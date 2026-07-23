package sh.zolt.dependency;

/** Semantic magnitude of a version change from a current version to a candidate. */
public enum UpdateClass {
    PATCH,
    MINOR,
    MAJOR
}
