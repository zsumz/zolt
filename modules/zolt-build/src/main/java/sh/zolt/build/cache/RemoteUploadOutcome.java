package sh.zolt.build.cache;

/** Result of a remote build-cache upload. Failures never fail the build; they surface as warnings. */
public enum RemoteUploadOutcome {
    UPLOADED,
    UNAUTHORIZED,
    FAILED
}
