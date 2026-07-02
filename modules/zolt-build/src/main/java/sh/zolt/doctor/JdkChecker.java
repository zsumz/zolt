package sh.zolt.doctor;

public interface JdkChecker {
    JdkStatus detect(String requiredVersion);
}
