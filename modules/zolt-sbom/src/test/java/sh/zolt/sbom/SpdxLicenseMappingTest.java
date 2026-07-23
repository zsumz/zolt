package sh.zolt.sbom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

final class SpdxLicenseMappingTest {
    private final SpdxLicenseMapping mapping = new SpdxLicenseMapping();

    @Test
    void mapsCommonNameSpellingsToSpdxId() {
        assertEquals(Optional.of("Apache-2.0"),
                mapping.spdxId(Optional.of("The Apache Software License, Version 2.0"), Optional.empty()));
        assertEquals(Optional.of("MIT"), mapping.spdxId(Optional.of("MIT License"), Optional.empty()));
        assertEquals(Optional.of("EPL-2.0"),
                mapping.spdxId(Optional.of("Eclipse Public License - v 2.0"), Optional.empty()));
    }

    @Test
    void isCaseAndWhitespaceInsensitiveForNames() {
        assertEquals(Optional.of("Apache-2.0"),
                mapping.spdxId(Optional.of("  apache   license,   version 2.0 "), Optional.empty()));
    }

    @Test
    void mapsByUrlWhenNameIsUnknownOrAbsent() {
        assertEquals(Optional.of("Apache-2.0"),
                mapping.spdxId(Optional.empty(), Optional.of("https://www.apache.org/licenses/LICENSE-2.0.txt")));
        assertEquals(Optional.of("MIT"),
                mapping.spdxId(Optional.of("Totally Custom Name"), Optional.of("http://opensource.org/licenses/MIT")));
    }

    @Test
    void normalizesUrlSchemeWwwAndTrailingSlash() {
        assertEquals(Optional.of("MPL-2.0"),
                mapping.spdxId(Optional.empty(), Optional.of("https://www.mozilla.org/MPL/2.0/")));
    }

    @Test
    void leavesUnknownLicensesUnmapped() {
        assertTrue(mapping.spdxId(Optional.of("Weird Proprietary License"), Optional.empty()).isEmpty());
        assertTrue(mapping.spdxId(Optional.empty(), Optional.of("https://example.com/license")).isEmpty());
    }

    @Test
    void mapsGplClasspathExceptionDistinctlyFromPlainGpl() {
        assertEquals(Optional.of("GPL-2.0-with-classpath-exception"),
                mapping.spdxId(Optional.of("GPL2 w/ CPE"), Optional.empty()));
        assertEquals(Optional.of("GPL-2.0-only"),
                mapping.spdxId(Optional.of("GNU General Public License, version 2"), Optional.empty()));
    }
}
