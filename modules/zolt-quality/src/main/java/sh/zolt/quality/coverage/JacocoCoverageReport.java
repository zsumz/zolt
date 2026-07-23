package sh.zolt.quality.coverage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Reads the report-level coverage totals from a Jacoco {@code jacoco.xml} file. Only the
 * {@code <counter>} elements that are direct children of the root {@code <report>} element are read:
 * those are the whole-report totals. Nested package/class/method counters are ignored.
 */
public final class JacocoCoverageReport {
    private JacocoCoverageReport() {
    }

    public static CoverageMeasurement read(Path jacocoXml) {
        Document document;
        try (InputStream input = Files.newInputStream(jacocoXml)) {
            document = documentBuilder().parse(input);
        } catch (IOException | SAXException | ParserConfigurationException exception) {
            throw new CoverageReportException(
                    "Could not read the Jacoco XML report at " + jacocoXml + ": " + exception.getMessage(),
                    exception);
        }
        Element report = document.getDocumentElement();
        if (report == null || !"report".equals(report.getTagName())) {
            throw new CoverageReportException(
                    "The Jacoco XML report at " + jacocoXml + " is missing its <report> root element.");
        }
        Map<CoverageMetric, CoverageMeasurement.MetricCount> counts = new EnumMap<>(CoverageMetric.class);
        NodeList children = report.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child.getNodeType() != Node.ELEMENT_NODE || !"counter".equals(child.getNodeName())) {
                continue;
            }
            Element counter = (Element) child;
            Optional<CoverageMetric> metric = CoverageMetric.fromJacocoType(counter.getAttribute("type"));
            if (metric.isEmpty()) {
                continue;
            }
            long covered = parseCount(jacocoXml, counter, "covered");
            long missed = parseCount(jacocoXml, counter, "missed");
            counts.put(metric.get(), new CoverageMeasurement.MetricCount(covered, missed));
        }
        return new CoverageMeasurement(counts);
    }

    private static long parseCount(Path jacocoXml, Element counter, String attribute) {
        try {
            return Long.parseLong(counter.getAttribute(attribute));
        } catch (NumberFormatException exception) {
            throw new CoverageReportException(
                    "The Jacoco XML report at " + jacocoXml + " has a non-numeric " + attribute
                            + " attribute on a report <counter>.");
        }
    }

    private static DocumentBuilder documentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Harden against XXE and avoid fetching the external Jacoco DTD (report.dtd), which the file
        // references in its DOCTYPE but is not needed to read report-level counters.
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder();
    }
}
