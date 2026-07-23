package sh.zolt.quality.coverage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JacocoCoverageReportTest {
    private static final String FULL_REPORT = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
            <report name="demo">
              <sessioninfo id="s" start="0" dump="0"/>
              <package name="com/example">
                <class name="com/example/Foo" sourcefilename="Foo.java">
                  <method name="bar" desc="()V" line="3">
                    <counter type="INSTRUCTION" missed="1" covered="9"/>
                    <counter type="LINE" missed="0" covered="2"/>
                    <counter type="METHOD" missed="0" covered="1"/>
                  </method>
                  <counter type="INSTRUCTION" missed="1" covered="9"/>
                  <counter type="LINE" missed="0" covered="2"/>
                  <counter type="METHOD" missed="0" covered="1"/>
                </class>
                <counter type="INSTRUCTION" missed="1" covered="9"/>
                <counter type="LINE" missed="0" covered="2"/>
                <counter type="METHOD" missed="0" covered="1"/>
              </package>
              <counter type="INSTRUCTION" missed="10" covered="90"/>
              <counter type="BRANCH" missed="26" covered="74"/>
              <counter type="LINE" missed="12" covered="88"/>
              <counter type="COMPLEXITY" missed="5" covered="45"/>
              <counter type="METHOD" missed="7" covered="93"/>
              <counter type="CLASS" missed="1" covered="99"/>
            </report>
            """;

    @Test
    void readsReportLevelCountersOnly(@TempDir Path dir) throws Exception {
        Path xml = dir.resolve("jacoco.xml");
        Files.writeString(xml, FULL_REPORT);
        CoverageMeasurement measurement = JacocoCoverageReport.read(xml);
        // Report-level totals (90/74/88/93), never the nested class/method counters (which use 9/2/1).
        assertEquals(90.0, measurement.percentage(CoverageMetric.INSTRUCTION).getAsDouble(), 1e-9);
        assertEquals(74.0, measurement.percentage(CoverageMetric.BRANCH).getAsDouble(), 1e-9);
        assertEquals(88.0, measurement.percentage(CoverageMetric.LINE).getAsDouble(), 1e-9);
        assertEquals(93.0, measurement.percentage(CoverageMetric.METHOD).getAsDouble(), 1e-9);
    }

    @Test
    void reportsEmptyForMissingMetric(@TempDir Path dir) throws Exception {
        Path xml = dir.resolve("jacoco.xml");
        Files.writeString(xml, """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <report name="demo">
                  <counter type="LINE" missed="10" covered="90"/>
                </report>
                """);
        CoverageMeasurement measurement = JacocoCoverageReport.read(xml);
        assertEquals(90.0, measurement.percentage(CoverageMetric.LINE).getAsDouble(), 1e-9);
        assertTrue(measurement.percentage(CoverageMetric.BRANCH).isEmpty());
    }

    @Test
    void reportsEmptyForZeroTotal(@TempDir Path dir) throws Exception {
        Path xml = dir.resolve("jacoco.xml");
        Files.writeString(xml, """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <report name="demo">
                  <counter type="BRANCH" missed="0" covered="0"/>
                </report>
                """);
        CoverageMeasurement measurement = JacocoCoverageReport.read(xml);
        assertTrue(measurement.percentage(CoverageMetric.BRANCH).isEmpty());
    }

    @Test
    void throwsOnMalformedXml(@TempDir Path dir) throws Exception {
        Path xml = dir.resolve("jacoco.xml");
        Files.writeString(xml, "not xml at all");
        assertThrows(CoverageReportException.class, () -> JacocoCoverageReport.read(xml));
    }

    @Test
    void throwsOnMissingFile(@TempDir Path dir) {
        assertThrows(CoverageReportException.class, () -> JacocoCoverageReport.read(dir.resolve("absent.xml")));
    }
}
