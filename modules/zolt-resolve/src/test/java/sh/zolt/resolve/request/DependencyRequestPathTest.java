package sh.zolt.resolve.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import sh.zolt.dependency.PackageId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DependencyRequestPathTest {
    @Test
    void defensivelyCopiesPackages() {
        List<PackageId> packages = new ArrayList<>(List.of(
                new PackageId("com.example", "root"),
                new PackageId("com.example", "library")));

        DependencyRequestPath path = new DependencyRequestPath(packages);
        packages.add(new PackageId("com.example", "late"));

        assertEquals(
                List.of(
                        new PackageId("com.example", "root"),
                        new PackageId("com.example", "library")),
                path.packages());
        assertThrows(UnsupportedOperationException.class, () ->
                path.packages().add(new PackageId("com.example", "extra")));
    }
}
